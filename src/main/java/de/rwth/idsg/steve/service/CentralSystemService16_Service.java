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
package de.rwth.idsg.steve.service;

import de.rwth.idsg.steve.ocpp.OcppProtocol;
import de.rwth.idsg.steve.repository.OcppServerRepository;
import de.rwth.idsg.steve.repository.SettingsRepository;
import de.rwth.idsg.steve.repository.dto.InsertConnectorStatusParams;
import de.rwth.idsg.steve.repository.dto.InsertTransactionParams;
import de.rwth.idsg.steve.repository.dto.UpdateChargeboxParams;
import de.rwth.idsg.steve.repository.dto.UpdateTransactionParams;
import de.rwth.idsg.steve.service.notification.OccpStationBooted;
import de.rwth.idsg.steve.service.notification.OcppStationStatusFailure;
import de.rwth.idsg.steve.service.notification.OcppTransactionEnded;
import de.rwth.idsg.steve.service.notification.OcppTransactionStarted;
import jooq.steve.db.enums.TransactionStopEventActor;
import lombok.extern.slf4j.Slf4j;
import ocpp.cs._2015._10.*;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
public class CentralSystemService16_Service {

    @Autowired
    private OcppServerRepository ocppServerRepository;
    @Autowired
    private SettingsRepository settingsRepository;
    @Autowired
    private OcppTagService ocppTagService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private ChargePointHelperService chargePointHelperService;


    @Autowired
    private ScheduledExecutorService scheduledExecutorService;

    // -------------------------------------------------------------------------
    // Boot Notification
    // -------------------------------------------------------------------------

    @Transactional
    public BootNotificationResponse bootNotification(BootNotificationRequest parameters,
                                                     String chargeBoxIdentity,
                                                     OcppProtocol ocppProtocol) {

        DateTime now = DateTime.now();

        try {
            Optional<RegistrationStatus> status =
                    chargePointHelperService.getRegistrationStatus(chargeBoxIdentity);

            try {
                applicationEventPublisher.publishEvent(
                        new OccpStationBooted(chargeBoxIdentity, status)
                );
            } catch (Exception e) {
                log.error("Boot event publish failed", e);
            }

            if (status.isPresent()) {
                UpdateChargeboxParams params =
                        UpdateChargeboxParams.builder()
                                .ocppProtocol(ocppProtocol)
                                .vendor(parameters.getChargePointVendor())
                                .model(parameters.getChargePointModel())
                                .pointSerial(parameters.getChargePointSerialNumber())
                                .boxSerial(parameters.getChargeBoxSerialNumber())
                                .fwVersion(parameters.getFirmwareVersion())
                                .iccid(parameters.getIccid())
                                .imsi(parameters.getImsi())
                                .meterType(parameters.getMeterType())
                                .meterSerial(parameters.getMeterSerialNumber())
                                .chargeBoxId(chargeBoxIdentity)
                                .heartbeatTimestamp(now)
                                .build();

                ocppServerRepository.updateChargebox(params);
            }

            return new BootNotificationResponse()
                    .withStatus(status.orElse(RegistrationStatus.REJECTED))
                    .withCurrentTime(now)
                    .withInterval(settingsRepository.getHeartbeatIntervalInSeconds());

        } catch (Exception e) {
            log.error("BootNotification fatal error", e);

            return new BootNotificationResponse()
                    .withStatus(RegistrationStatus.REJECTED)
                    .withCurrentTime(now)
                    .withInterval(300);
        }
    }

    // -------------------------------------------------------------------------
    // Firmware Status
    // -------------------------------------------------------------------------

    @Transactional
    public FirmwareStatusNotificationResponse firmwareStatusNotification(
            FirmwareStatusNotificationRequest parameters,
            String chargeBoxIdentity) {

        try {
            ocppServerRepository.updateChargeboxFirmwareStatus(
                    chargeBoxIdentity,
                    parameters.getStatus().value()
            );
        } catch (Exception e) {
            log.error("FirmwareStatusNotification error", e);
        }

        return new FirmwareStatusNotificationResponse();
    }

    // -------------------------------------------------------------------------
    // Status Notification
    // -------------------------------------------------------------------------

    @Transactional
    public StatusNotificationResponse statusNotification(
            StatusNotificationRequest parameters,
            String chargeBoxIdentity) {

        try {
            DateTime timestamp = parameters.isSetTimestamp()
                    ? parameters.getTimestamp()
                    : DateTime.now();

            InsertConnectorStatusParams params =
                    InsertConnectorStatusParams.builder()
                            .chargeBoxId(chargeBoxIdentity)
                            .connectorId(parameters.getConnectorId())
                            .status(parameters.getStatus().value())
                            .errorCode(parameters.getErrorCode().value())
                            .timestamp(timestamp)
                            .errorInfo(parameters.getInfo())
                            .vendorId(parameters.getVendorId())
                            .vendorErrorCode(parameters.getVendorErrorCode())
                            .build();

            ocppServerRepository.insertConnectorStatus(params);

            if (parameters.getStatus() == ChargePointStatus.FAULTED) {
                try {
                    applicationEventPublisher.publishEvent(
                            new OcppStationStatusFailure(
                                    chargeBoxIdentity,
                                    parameters.getConnectorId(),
                                    parameters.getErrorCode().value()
                            )
                    );
                } catch (Exception e) {
                    log.error("Status failure event error", e);
                }
            }

        } catch (Exception e) {
            log.error("StatusNotification fatal error", e);
        }

        return new StatusNotificationResponse();
    }

    // -------------------------------------------------------------------------
    // Meter Values
    // -------------------------------------------------------------------------

    public MeterValuesResponse meterValues(MeterValuesRequest parameters,
                                           String chargeBoxIdentity) {

        try {

            Integer transactionId = getTransactionId(parameters);

            // Run DB insert in background thread
            scheduledExecutorService.submit(() -> {
                try {

                    ocppServerRepository.insertMeterValues(
                            chargeBoxIdentity,
                            parameters.getMeterValue(),
                            parameters.getConnectorId(),
                            transactionId
                    );

                } catch (Exception e) {
                    log.error("Async MeterValues DB insert failed", e);
                }
            });

        } catch (Exception e) {
            log.error("MeterValues error", e);
        }

        // Respond immediately to charger
        return new MeterValuesResponse();
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    @Transactional
    public DiagnosticsStatusNotificationResponse diagnosticsStatusNotification(
            DiagnosticsStatusNotificationRequest parameters,
            String chargeBoxIdentity) {

        try {
            ocppServerRepository.updateChargeboxDiagnosticsStatus(
                    chargeBoxIdentity,
                    parameters.getStatus().value()
            );
        } catch (Exception e) {
            log.error("DiagnosticsStatusNotification error", e);
        }

        return new DiagnosticsStatusNotificationResponse();
    }

    // -------------------------------------------------------------------------
    // Start Transaction
    // -------------------------------------------------------------------------

    @Transactional
    public StartTransactionResponse startTransaction(StartTransactionRequest parameters,
                                                     String chargeBoxIdentity) {

        try {

            IdTagInfo info = ocppTagService.getIdTagInfo(
                    parameters.getIdTag(),
                    true,
                    chargeBoxIdentity,
                    parameters.getConnectorId(),
                    () -> new IdTagInfo().withStatus(AuthorizationStatus.INVALID)
            );

            InsertTransactionParams params =
                    InsertTransactionParams.builder()
                            .chargeBoxId(chargeBoxIdentity)
                            .connectorId(parameters.getConnectorId())
                            .idTag(parameters.getIdTag())
                            .startTimestamp(parameters.getTimestamp())
                            .startMeterValue(Integer.toString(parameters.getMeterStart()))
                            .reservationId(parameters.getReservationId())
                            .eventTimestamp(org.joda.time.DateTime.now())
                            .build();

            int transactionId = ocppServerRepository.insertTransaction(params);

            // publish event (safe)
            try {
                applicationEventPublisher.publishEvent(
                        new OcppTransactionStarted(transactionId, params)
                );
            } catch (Exception e) {
                log.error("Event publish failed", e);
            }


            return new StartTransactionResponse()
                    .withIdTagInfo(info)
                    .withTransactionId(transactionId);

        } catch (Exception e) {

            log.error("StartTransaction fatal error", e);

            IdTagInfo fallback = new IdTagInfo();
            fallback.setStatus(AuthorizationStatus.INVALID);

            return new StartTransactionResponse()
                    .withIdTagInfo(fallback);
        }
    }

    // -------------------------------------------------------------------------
    // Stop Transaction
    // -------------------------------------------------------------------------

    @Transactional
    public StopTransactionResponse stopTransaction(StopTransactionRequest parameters,
                                                   String chargeBoxIdentity) {

        IdTagInfo idTagInfo = null;

        try {

            String stopReason = parameters.isSetReason()
                    ? parameters.getReason().value()
                    : null;

            try {
                idTagInfo = ocppTagService.getIdTagInfo(
                        parameters.getIdTag(),
                        false,
                        chargeBoxIdentity,
                        null,
                        () -> null
                );
            } catch (Exception e) {
                log.error("IdTagInfo fetch failed", e);
            }

            UpdateTransactionParams params =
                    UpdateTransactionParams.builder()
                            .chargeBoxId(chargeBoxIdentity)
                            .transactionId(parameters.getTransactionId())
                            .stopTimestamp(parameters.getTimestamp())
                            .stopMeterValue(Integer.toString(parameters.getMeterStop()))
                            .stopReason(stopReason)
                            .eventTimestamp(DateTime.now())
                            .eventActor(TransactionStopEventActor.station)
                            .build();

            /*
             * 🔹 Run DB updates in background thread
             * to avoid blocking OCPP WebSocket thread
             */
            scheduledExecutorService.submit(() -> {

                try {

                    ocppServerRepository.updateTransaction(params);

                    try {
                        ocppServerRepository.insertMeterValues(
                                chargeBoxIdentity,
                                parameters.getTransactionData(),
                                parameters.getTransactionId()
                        );
                    } catch (Exception e) {
                        log.error("InsertMeterValues failed", e);
                    }

                    try {
                        applicationEventPublisher.publishEvent(
                                new OcppTransactionEnded(params)
                        );
                    } catch (Exception e) {
                        log.error("Event publish failed", e);
                    }

                } catch (Exception e) {
                    log.error("Async StopTransaction processing failed", e);
                }
            });

        } catch (Exception e) {
            log.error("StopTransaction fatal error", e);
        }

        /*
         * 🔹 Return response immediately
         * charger should not wait for DB work
         */
        if (idTagInfo != null) {
            return new StopTransactionResponse().withIdTagInfo(idTagInfo);
        } else {
            IdTagInfo fallback = new IdTagInfo();
            fallback.setStatus(AuthorizationStatus.ACCEPTED);
            return new StopTransactionResponse().withIdTagInfo(fallback);
        }
    }
    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    @Transactional
    public HeartbeatResponse heartbeat(HeartbeatRequest parameters,
                                       String chargeBoxIdentity) {

        DateTime now = DateTime.now();

        try {
            ocppServerRepository.updateChargeboxHeartbeat(chargeBoxIdentity, now);
        } catch (Exception e) {
            log.error("Heartbeat error", e);
        }

        return new HeartbeatResponse().withCurrentTime(now);
    }

    // -------------------------------------------------------------------------
    // Authorize
    // -------------------------------------------------------------------------

    @Transactional
    public AuthorizeResponse authorize(AuthorizeRequest parameters,
                                       String chargeBoxIdentity) {

        try {
            IdTagInfo idTagInfo = ocppTagService.getIdTagInfo(
                    parameters.getIdTag(),
                    false,
                    chargeBoxIdentity,
                    null,
                    () -> new IdTagInfo().withStatus(AuthorizationStatus.INVALID)
            );
            if (idTagInfo == null || idTagInfo.getStatus() == null) {
                idTagInfo = new IdTagInfo().withStatus(AuthorizationStatus.INVALID);
            }

            return new AuthorizeResponse().withIdTagInfo(idTagInfo);

        } catch (Exception e) {

            log.error("Authorize failed", e);

            IdTagInfo fallback = new IdTagInfo();
            fallback.setStatus(AuthorizationStatus.INVALID);

            return new AuthorizeResponse().withIdTagInfo(fallback);
        }
    }

    // -------------------------------------------------------------------------
    // DataTransfer
    // -------------------------------------------------------------------------

    @Transactional
    public DataTransferResponse dataTransfer(DataTransferRequest parameters,
                                             String chargeBoxIdentity) {

        try {
            log.info("[DataTransfer] CP: {}, Vendor: {}",
                    chargeBoxIdentity,
                    parameters.getVendorId());
        } catch (Exception e) {
            log.error("DataTransfer error", e);
        }

        return new DataTransferResponse()
                .withStatus(DataTransferStatus.ACCEPTED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Integer getTransactionId(MeterValuesRequest parameters) {
        Integer transactionId = parameters.getTransactionId();
        if (transactionId == null || transactionId == 0) {
            return null;
        }
        return transactionId;
    }
}