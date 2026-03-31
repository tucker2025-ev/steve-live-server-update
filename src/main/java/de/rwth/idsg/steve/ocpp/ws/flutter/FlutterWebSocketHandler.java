/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2026 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.ocpp.ws.flutter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.rwth.idsg.steve.service.TestAppService;
import de.rwth.idsg.steve.service.remote.OcppRemoteCommandExecutor;
import de.rwth.idsg.steve.service.testmobiledto.ResponseDTO;
import de.rwth.idsg.steve.web.dto.ocpp.RemoteStartTransactionParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;

@Component
public class FlutterWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private FlutterSessionManager sessionManager;

    @Autowired
    private OcppMessageStore messageStore;

    @Autowired
    private OcppEventService eventService;

    @Autowired
    private OcppRemoteCommandExecutor commandExecutor;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TestAppService testAppService;

    // =====================================================
    // CONNECT
    // =====================================================
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String cpId = extractCpId(session);
            session.getAttributes().put("cpId", cpId);

            sessionManager.add(cpId, session);

            // ✅ Send history
            List<String> history = messageStore.getRecent(cpId);
            for (String msg : history) {
                session.sendMessage(new TextMessage(msg));
            }

            // ✅ Notify connected
            sendSafe(session, buildSuccess("CONNECTED", cpId, "Flutter Connected"));

        } catch (Exception e) {
            sendSafe(session, buildError("CONNECTION_ERROR", e.getMessage()));
        }
    }

    // =====================================================
    // MESSAGE HANDLING (SAFE)
    // =====================================================
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        try {
            Map<String, Object> data = mapper.readValue(message.getPayload(), Map.class);

            String action = getString(data.get("action"));
            String cpId = (String) session.getAttributes().get("cpId");

            if (action == null) {
                sendSafe(session, buildError("MISSING_ACTION", "Action is required"));
                return;
            }

            switch (action) {

                // ===========================
                // REMOTE START
                // ===========================
                case "RemoteStartTransaction" -> {

                    Integer connectorId = getInt(data.get("connectorId"));
                    String idTag = getString(data.get("idTag"));

                    if (connectorId == null || idTag == null) {
                        sendSafe(session,
                                buildError("VALIDATION_FAILED", "connectorId & idTag required"));
                        return;
                    }

                    try {
                        RemoteStartTransactionParams params = new RemoteStartTransactionParams();
                        params.setIdTag(idTag);
                        params.setConnectorId(connectorId);

                        final boolean started = commandExecutor
                                .sendRemoteStart(cpId, connectorId, params, idTag)
                                .join();

                        ResponseDTO responseDTO = this.testAppService.buildFinalStartResponse(started, "c", connectorId, cpId);

                        eventService.publish("RemoteStartTransactionResponse", cpId, responseDTO, "SERVER");


                    } catch (Exception e) {
                        sendSafe(session, buildError("START_FAILED", e.getMessage()));
                    }
                }

                case "RemoteStopTransaction" -> {

                    Integer transactionId = getInt(data.get("transactionId"));

                    if (transactionId == null) {
                        sendSafe(session,
                                buildError("VALIDATION_FAILED", "transactionId required"));
                        return;
                    }

                    try {


                        ResponseDTO stopResponse =
                                testAppService.stopTransaction(transactionId, "Mobile");
                        eventService.publish("RemoteStopTransaction", cpId, stopResponse, "SERVER");

                    } catch (Exception e) {
                        sendSafe(session, buildError("STOP_FAILED", e.getMessage()));
                    }
                }

                default -> sendSafe(session,
                        buildError("UNKNOWN_ACTION", "Unsupported action: " + action));
            }

        } catch (Exception e) {
            e.printStackTrace();

            // ❗ NEVER THROW → Always respond
            sendSafe(session, buildError("SERVER_ERROR", e.getMessage()));
        }
    }

    // =====================================================
    // DISCONNECT
    // =====================================================
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String cpId = (String) session.getAttributes().get("cpId");
        sessionManager.remove(cpId, session);
    }

    // =====================================================
    // HANDLE TRANSPORT ERROR (IMPORTANT)
    // =====================================================
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        //exception.printStackTrace();

        sendSafe(session, buildError("WS_ERROR", exception.getMessage()));
    }

    // =====================================================
    // SAFE HELPERS
    // =====================================================

    private void sendSafe(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {
        }
    }

    private String buildSuccess(String event, String cpId, Object data) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "status", true,
                    "event", event,
                    "chargeBoxId", cpId,
                    "data", data
            ));
        } catch (Exception e) {
            return "{\"status\":true}";
        }
    }

    private String buildError(String code, String message) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "status", false,
                    "error", Map.of(
                            "code", code,
                            "message", message
                    )
            ));
        } catch (Exception e) {
            return "{\"status\":false}";
        }
    }

    private Integer getInt(Object value) {
        try {
            return value != null ? Integer.parseInt(value.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getString(Object value) {
        return value != null ? value.toString() : null;
    }

    private String extractCpId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf("/") + 1);
    }
}