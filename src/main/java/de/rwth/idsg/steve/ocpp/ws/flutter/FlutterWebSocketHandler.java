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
import de.rwth.idsg.steve.ocpp.ws.ocpp16.Ocpp16WebSocketEndpoint;
import de.rwth.idsg.steve.repository.ChargePointRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FlutterWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private OcppMessageStore messageStore;

    @Autowired
    private OcppRemoteCommandExecutor commandExecutor;

    @Autowired
    private TestAppService testAppService;

    @Autowired
    private Ocpp16WebSocketEndpoint ocpp16WebSocketEndpoint;

    @Autowired
    private ChargePointRepository chargePointRepository;

    private static final Map<String, Set<WebSocketSession>> sessionsByChargePoint = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    // ==========================
    // CONNECTION
    // ==========================
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        String path = session.getUri().getPath();
        String chargePointId = path.substring(path.lastIndexOf("/") + 1).trim();

        try {
            Optional<String> statusOpt = chargePointRepository.getRegistrationStatus(chargePointId);

            if (statusOpt.isEmpty()) {
                sendError(session, "CHARGEPOINT_NOT_FOUND", "ChargePoint not found", null);
                return;
            }

            if (!"Accepted".equalsIgnoreCase(statusOpt.get())) {
                sendError(session, "CHARGEPOINT_NOT_ACCEPTED", "ChargePoint not accepted", null);
                return;
            }

            sessionsByChargePoint
                    .computeIfAbsent(chargePointId, k -> ConcurrentHashMap.newKeySet())
                    .add(session);

            session.getAttributes().put("chargeBoxId", chargePointId);

            // Send history messages
            List<String> history = messageStore.getDataUseChargeBoxId(chargePointId);
            for (String msg : history) {
                session.sendMessage(new TextMessage(msg));
            }

            // Send connected event
            sendSuccess(session, Map.of(
                    "event", "CONNECTED",
                    "chargeBoxId", chargePointId
            ));

        } catch (Exception e) {
            sendError(session, "CONNECTION_ERROR", e.getMessage(), null);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        String chargePointId = (String) session.getAttributes().get("chargeBoxId");

        if (chargePointId != null) {
            Set<WebSocketSession> sessions = sessionsByChargePoint.get(chargePointId);
            if (sessions != null) {
                sessions.remove(session);
            }
        }
    }

    // ==========================
    // MESSAGE HANDLING
    // ==========================
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        String payload = message.getPayload();
        String chargeBoxId = (String) session.getAttributes().get("chargeBoxId");

        try {
            Map<String, Object> data;

            // 🔴 JSON Parse Handling
            try {
                data = mapper.readValue(payload, Map.class);
            } catch (Exception e) {
                sendError(session, "INVALID_JSON", "Malformed JSON", payload);
                return;
            }

            String action = (String) data.get("action");

            if (action == null) {
                sendError(session, "MISSING_ACTION", "Action is required", payload);
                return;
            }

            String idTag = (String) data.get("idTag");
            String connectorQrCode = (String) data.get("connectorQrCode");

            Integer connectorId = parseInteger(data.get("connectorId"), "connectorId", session, payload);
            if (connectorId == null && data.get("connectorId") != null) return;

            Integer transactionId = parseInteger(data.get("transactionId"), "transactionId", session, payload);
            if (transactionId == null && data.get("transactionId") != null) return;

            switch (action) {

                case "RemoteStartTransaction":

                    if (idTag == null || connectorId == null) {
                        sendError(session, "VALIDATION_FAILED",
                                "idTag & connectorId required", payload);
                        return;
                    }

                    RemoteStartTransactionParams params = new RemoteStartTransactionParams();
                    params.setIdTag(idTag);
                    params.setConnectorId(connectorId);

                    boolean started = commandExecutor
                            .sendRemoteStart(chargeBoxId, connectorId, params, idTag)
                            .join();

                    ResponseDTO startResponse =
                            testAppService.buildFinalStartResponse(
                                    started, connectorQrCode, connectorId, chargeBoxId);

                    // ✅ FULL RESPONSE SEND
                    sendResponse(session, startResponse);
                    break;

                case "RemoteStopTransaction":

                    if (transactionId == null) {
                        sendError(session, "VALIDATION_FAILED",
                                "transactionId required", payload);
                        return;
                    }

                    ResponseDTO stopResponse =
                            testAppService.stopTransaction(transactionId, "Mobile");

                    sendResponse(session, stopResponse);
                    break;

                default:
                    sendError(session, "UNKNOWN_ACTION",
                            "Unsupported action: " + action, payload);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(session, "SERVER_ERROR", e.getMessage(), payload);
        }
    }

    // ==========================
    // COMMON METHODS
    // ==========================

    private Integer parseInteger(Object value, String field,
                                 WebSocketSession session, String payload) {
        try {
            return value != null ? Integer.parseInt(value.toString()) : null;
        } catch (Exception e) {
            sendError(session,
                    "INVALID_" + field.toUpperCase(),
                    field + " must be number",
                    payload);
            return null;
        }
    }

    private void sendResponse(WebSocketSession session, ResponseDTO dto) {
        try {
            session.sendMessage(new TextMessage(
                    mapper.writeValueAsString(dto)
            ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSuccess(WebSocketSession session, Object data) {
        ResponseDTO dto = new ResponseDTO();
        dto.setStatus(true);
        dto.setData(data);
        sendResponse(session, dto);
    }

    private void sendError(WebSocketSession session,
                           String code,
                           String message,
                           Object originalRequest) {

        ErrorResponseDTO error = new ErrorResponseDTO();
        error.setMessage(message);
        ResponseDTO dto = new ResponseDTO();
        dto.setStatus(false);
        dto.setData(error);

        sendResponse(session, dto);
    }

    // ==========================
    // PUSH TO ALL FLUTTER CLIENTS
    // ==========================
    public static void sendToChargePoint(String chargePointId, String message) {

        Set<WebSocketSession> sessions = sessionsByChargePoint.get(chargePointId);

        if (sessions == null) return;

        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}