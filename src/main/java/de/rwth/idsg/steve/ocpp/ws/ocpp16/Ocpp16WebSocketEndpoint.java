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
    private FutureResponseContextStore futureResponseContextStore;

    @Autowired
    private OcppMessageStore messageStore;

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

            if (params instanceof AuthorizeRequest req) {
                parent.sendToFlutter("Authorize", chargeBoxId, req);
                return server.authorize(req, chargeBoxId);
            }

            if (params instanceof StartTransactionRequest req) {
                parent.sendToFlutter("StartTransaction", chargeBoxId, req);
                return server.startTransaction(req, chargeBoxId);
            }

            if (params instanceof StopTransactionRequest req) {
                parent.sendToFlutter("StopTransaction", chargeBoxId, req);
                return server.stopTransaction(req, chargeBoxId);
            }

            if (params instanceof MeterValuesRequest req) {
                parent.sendToFlutter("MeterValues", chargeBoxId, req);
                return server.meterValues(req, chargeBoxId);
            }

            if (params instanceof StatusNotificationRequest req) {
                parent.sendToFlutter("StatusNotification", chargeBoxId, req);
                return server.statusNotification(req, chargeBoxId);
            }

            if (params instanceof HeartbeatRequest req) {
                parent.sendToFlutter("Heartbeat", chargeBoxId, req);
                return server.heartbeat(req, chargeBoxId);
            }

            if (params instanceof FirmwareStatusNotificationRequest req) {
                parent.sendToFlutter("FirmwareStatusNotification", chargeBoxId, req);
                return server.firmwareStatusNotification(req, chargeBoxId);
            }

            if (params instanceof DiagnosticsStatusNotificationRequest req) {
                parent.sendToFlutter("DiagnosticsStatusNotification", chargeBoxId, req);
                return server.diagnosticsStatusNotification(req, chargeBoxId);
            }

            if (params instanceof DataTransferRequest req) {
                parent.sendToFlutter("DataTransfer", chargeBoxId, req);
                return server.dataTransfer(req, chargeBoxId);
            }

            throw new IllegalArgumentException("Unexpected RequestType: " + params.getClass());
        }
    }

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