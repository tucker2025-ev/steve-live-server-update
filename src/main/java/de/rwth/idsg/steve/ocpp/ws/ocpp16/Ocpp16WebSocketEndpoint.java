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
/*
 * SteVe - SteckdosenVerwaltung
 */
package de.rwth.idsg.steve.ocpp.ws.ocpp16;

import de.rwth.idsg.ocpp.jaxb.RequestType;
import de.rwth.idsg.ocpp.jaxb.ResponseType;
import de.rwth.idsg.steve.ocpp.OcppProtocol;
import de.rwth.idsg.steve.ocpp.OcppVersion;
import de.rwth.idsg.steve.ocpp.soap.CentralSystemService16_SoapServer;
import de.rwth.idsg.steve.ocpp.ws.AbstractWebSocketEndpoint;
import de.rwth.idsg.steve.ocpp.ws.FutureResponseContextStore;
import de.rwth.idsg.steve.ocpp.ws.flutter.OcppEventService;
import de.rwth.idsg.steve.ocpp.ws.pipeline.AbstractCallHandler;
import de.rwth.idsg.steve.ocpp.ws.pipeline.Deserializer;
import de.rwth.idsg.steve.ocpp.ws.pipeline.IncomingPipeline;
import lombok.RequiredArgsConstructor;
import ocpp.cs._2015._10.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class Ocpp16WebSocketEndpoint extends AbstractWebSocketEndpoint {

    @Autowired
    private CentralSystemService16_SoapServer server;

    @Autowired
    private FutureResponseContextStore futureResponseContextStore;

    @Autowired
    private OcppEventService eventService;

    @PostConstruct
    public void init() {

        Deserializer deserializer =
                new Deserializer(futureResponseContextStore, Ocpp16TypeStore.INSTANCE);

        IncomingPipeline pipeline =
                new IncomingPipeline(deserializer,
                        new Ocpp16CallHandler(server, eventService));

        super.init(pipeline);
    }

    @Override
    public OcppVersion getVersion() {
        return OcppVersion.V_16;
    }

    // =========================================================
    // CLEAN CALL HANDLER (CORE LOGIC)
    // =========================================================
    @RequiredArgsConstructor
    private static class Ocpp16CallHandler extends AbstractCallHandler {

        private final CentralSystemService16_SoapServer server;
        private final OcppEventService eventService;

        @Override
        protected ResponseType dispatch(RequestType params, String chargeBoxId) {

            String event = params.getClass().getSimpleName();

            eventService.publish(event, chargeBoxId, params, "IN");

            ResponseType response;

            try {

                // =====================================================
                // OCPP MESSAGE ROUTING
                // =====================================================

                if (params instanceof BootNotificationRequest req) {

                    response = server.bootNotificationWithTransport(
                            req, chargeBoxId, OcppProtocol.V_16_JSON);

                } else if (params instanceof AuthorizeRequest req) {

                    response = server.authorize(req, chargeBoxId);

                } else if (params instanceof StartTransactionRequest req) {

                    response = server.startTransaction(req, chargeBoxId);

                } else if (params instanceof StopTransactionRequest req) {

                    response = server.stopTransaction(req, chargeBoxId);

                } else if (params instanceof MeterValuesRequest req) {

                    response = server.meterValues(req, chargeBoxId);

                } else if (params instanceof StatusNotificationRequest req) {

                    response = server.statusNotification(req, chargeBoxId);

                } else if (params instanceof HeartbeatRequest req) {

                    response = server.heartbeat(req, chargeBoxId);

                } else if (params instanceof FirmwareStatusNotificationRequest req) {

                    response = server.firmwareStatusNotification(req, chargeBoxId);

                } else if (params instanceof DiagnosticsStatusNotificationRequest req) {

                    response = server.diagnosticsStatusNotification(req, chargeBoxId);

                } else if (params instanceof DataTransferRequest req) {

                    response = server.dataTransfer(req, chargeBoxId);

                } else {
                    throw new IllegalArgumentException(
                            "Unsupported OCPP Request: " + params.getClass().getSimpleName());
                }

            } catch (Exception ex) {

                // 🔴 ERROR LOGGING (VERY IMPORTANT)
                eventService.publish(
                        "ERROR",
                        chargeBoxId,
                        ex.getMessage(),
                        "SYSTEM"
                );

                throw ex;
            }


            return response;
        }
    }
}