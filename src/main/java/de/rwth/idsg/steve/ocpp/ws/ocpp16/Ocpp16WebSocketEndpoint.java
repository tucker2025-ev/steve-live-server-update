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
package de.rwth.idsg.steve.ocpp.ws.ocpp16;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import de.rwth.idsg.ocpp.jaxb.RequestType;
import de.rwth.idsg.ocpp.jaxb.ResponseType;
import de.rwth.idsg.steve.ocpp.OcppProtocol;
import de.rwth.idsg.steve.ocpp.OcppVersion;
import de.rwth.idsg.steve.ocpp.soap.CentralSystemService16_SoapServer;
import de.rwth.idsg.steve.ocpp.ws.AbstractWebSocketEndpoint;
import de.rwth.idsg.steve.ocpp.ws.FutureResponseContextStore;
import de.rwth.idsg.steve.ocpp.ws.flutter.FlutterWebSocketHandler;
import de.rwth.idsg.steve.ocpp.ws.flutter.OcppMessageStore;
import de.rwth.idsg.steve.ocpp.ws.pipeline.AbstractCallHandler;
import de.rwth.idsg.steve.ocpp.ws.pipeline.Deserializer;
import de.rwth.idsg.steve.ocpp.ws.pipeline.IncomingPipeline;
import lombok.RequiredArgsConstructor;
import ocpp.cs._2015._10.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

@Component
public class Ocpp16WebSocketEndpoint extends AbstractWebSocketEndpoint {

    @Autowired
    private CentralSystemService16_SoapServer server;

    @Autowired
    private OcppMessageStore messageStore;

    @Autowired
    private FutureResponseContextStore futureResponseContextStore;

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JodaModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @PostConstruct
    public void init() {

        Deserializer deserializer =
                new Deserializer(futureResponseContextStore, Ocpp16TypeStore.INSTANCE);

        IncomingPipeline pipeline =
                new IncomingPipeline(deserializer, new Ocpp16CallHandler(server, this));

        super.init(pipeline);
    }

    @Override
    public OcppVersion getVersion() {
        return OcppVersion.V_16;
    }

    @RequiredArgsConstructor
    private static class Ocpp16CallHandler extends AbstractCallHandler {

        private final CentralSystemService16_SoapServer server;
        private final Ocpp16WebSocketEndpoint parent;

        @Override
        protected ResponseType dispatch(RequestType params, String chargeBoxId) {


            if (params instanceof BootNotificationRequest req) {
                parent.sendToFlutter("BootNotification", chargeBoxId, req);
                return server.bootNotificationWithTransport(req, chargeBoxId, OcppProtocol.V_16_JSON);
            }

            if (params instanceof StatusNotificationRequest req) {
                parent.sendToFlutter("StatusNotification", chargeBoxId, req);
                return server.statusNotification(req, chargeBoxId);
            }

            if (params instanceof MeterValuesRequest req) {
                parent.sendToFlutter("MeterValues", chargeBoxId, req);
                return server.meterValues(req, chargeBoxId);
            }

            if (params instanceof StartTransactionRequest req) {
                parent.sendToFlutter("StartTransaction", chargeBoxId, req);
                return server.startTransaction(req, chargeBoxId);
            }

            if (params instanceof StopTransactionRequest req) {
                parent.sendToFlutter("StopTransaction", chargeBoxId, req);
                return server.stopTransaction(req, chargeBoxId);
            }

            if (params instanceof HeartbeatRequest req) {
                parent.sendToFlutter("Heartbeat", chargeBoxId, req);
                return server.heartbeat(req, chargeBoxId);
            }

            if (params instanceof AuthorizeRequest req) {
                parent.sendToFlutter("Authorize", chargeBoxId, req);
                return server.authorize(req, chargeBoxId);
            }

            throw new IllegalArgumentException("Unknown request");
        }
    }

    // =========================================================
    // SEND TO FLUTTER
    // =========================================================

    public void sendToFlutter(String event, String chargeBoxId, Object payload) {

        try {

            String json = mapper.writeValueAsString(
                    Map.of(
                            "event", event,
                            "chargeBoxId", chargeBoxId,
                            "data", payload
                    )
            );

            messageStore.add(chargeBoxId, json);

            FlutterWebSocketHandler.sendToChargePoint(chargeBoxId, json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}