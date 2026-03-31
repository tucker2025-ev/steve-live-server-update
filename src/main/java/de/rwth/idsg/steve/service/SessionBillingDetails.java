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

import de.rwth.idsg.steve.service.dto.ChargerStationDTO;
import de.rwth.idsg.steve.web.dto.SessionBillingDTO;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static jooq.steve.db.Tables.CONNECTOR;
import static jooq.steve.db2.tables.SessionBillingDetails.SESSION_BILLING_DETAILS;

@Slf4j
@Service
public class SessionBillingDetails {

    @Autowired
    @Qualifier("secondary")
    private DSLContext secondary;

    @Autowired
    @Qualifier("php")
    private DSLContext php;

    private static final BigDecimal CGST_RATE = new BigDecimal("0.09");
    private static final BigDecimal SGST_RATE = new BigDecimal("0.09");
    private static final BigDecimal IGST_RATE = new BigDecimal("0.18");

    private static final Table<?> CHARGE_POINT_VIEW = DSL.table("bigtot_cms.view_charger_station");

    private static final Field<String> CHARGER_ID = DSL.field("charger_id", String.class);
    private static final Field<Integer> CONNECTOR_NO = DSL.field("con_no", Integer.class);
    private static final Field<String> CONNECTOR_ID = DSL.field("con_id", String.class);
    private static final Field<String> CHARGER_QR_CODE = DSL.field("charger_qr_code", String.class);
    private static final Field<String> STATION_ID = DSL.field("station_id", String.class);
    private static final Field<String> STATION_NAME = DSL.field("station_name", String.class);
    private static final Field<String> CPO_ID = DSL.field("cpo_id", String.class);
    private static final Field<String> STATION_CITY = DSL.field("station_city", String.class);
    private static final Field<String> STATION_STATE = DSL.field("station_state", String.class);

    public List<SessionBillingDTO> getSettlementRecords(String stationId,
                                                        String cpoId,
                                                        String chargerQrCode,
                                                        Integer transactionId,
                                                        String startTimestamp,
                                                        String stopTimestamp) {
        try {
            Condition condition = DSL.noCondition();

            if (startTimestamp != null && stopTimestamp != null &&
                    !startTimestamp.isEmpty() && !stopTimestamp.isEmpty()) {
                try {

                    LocalDate startDate = LocalDate.parse(startTimestamp);
                    LocalDate endDate = LocalDate.parse(stopTimestamp);

                    LocalDateTime startDateTime = startDate.atStartOfDay();
                    LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

                    org.joda.time.DateTime jodaStart = new org.joda.time.DateTime(
                            startDateTime.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli());

                    org.joda.time.DateTime jodaEnd = new org.joda.time.DateTime(
                            endDateTime.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli());

                    condition = condition.and(
                            SESSION_BILLING_DETAILS.SESSION_START_AT.between(jodaStart, jodaEnd));

                } catch (Exception e) {
                    log.error("Invalid date format. startTimestamp={}, stopTimestamp={}",
                            startTimestamp, stopTimestamp, e);
                }
            }

            if (stationId != null && !stationId.isEmpty()) {
                condition = condition.and(SESSION_BILLING_DETAILS.STATION_ID.eq(stationId));
            }

            if (cpoId != null && !cpoId.isEmpty()) {
                condition = condition.and(SESSION_BILLING_DETAILS.CPO_ID.eq(cpoId));
            }

            if (chargerQrCode != null && !chargerQrCode.isEmpty()) {
                condition = condition.and(SESSION_BILLING_DETAILS.CHARGER_QR_CODE.eq(chargerQrCode));
            }

            if (transactionId != null) {
                condition = condition.and(SESSION_BILLING_DETAILS.TRANSACTION_ID.eq(transactionId));
            }

            return secondary
                    .select(
                            SESSION_BILLING_DETAILS.ID,
                            SESSION_BILLING_DETAILS.TRANSACTION_ID,
                            SESSION_BILLING_DETAILS.SLAB_NO,
                            SESSION_BILLING_DETAILS.STATION_ID,
                            SESSION_BILLING_DETAILS.CPO_ID,
                            SESSION_BILLING_DETAILS.STATION_NAME,
                            SESSION_BILLING_DETAILS.STATION_CITY,
                            SESSION_BILLING_DETAILS.STATION_STATE,
                            SESSION_BILLING_DETAILS.CUSTOMER_ID_TAG,
                            SESSION_BILLING_DETAILS.CHARGER_ID,
                            SESSION_BILLING_DETAILS.CHARGER_QR_CODE,
                            SESSION_BILLING_DETAILS.CONNECTOR_ID,
                            SESSION_BILLING_DETAILS.START_ENERGY,
                            SESSION_BILLING_DETAILS.UNIT_FARE,
                            SESSION_BILLING_DETAILS.END_ENERGY,
                            SESSION_BILLING_DETAILS.ENERGY_KWH,
                            SESSION_BILLING_DETAILS.CUSTOMER_WALLET_AMOUNT,
                            SESSION_BILLING_DETAILS.CGST_AMOUNT,
                            SESSION_BILLING_DETAILS.SGST_AMOUNT,
                            SESSION_BILLING_DETAILS.IGST_AMOUNT,
                            SESSION_BILLING_DETAILS.SESSION_AMOUNT,
                            SESSION_BILLING_DETAILS.SESSION_TOTAL_AMOUNT,
                            SESSION_BILLING_DETAILS.SERVICE_FEE_PER_KWH,
                            SESSION_BILLING_DETAILS.SERVICE_FEE_AMOUNT,
                            SESSION_BILLING_DETAILS.ENERGY_COST_EXCL_TAX,
                            SESSION_BILLING_DETAILS.ENERGY_COST_INCL_TAX,
                            SESSION_BILLING_DETAILS.SETTLEMENT_AMOUNT_OWNER_WITH_GST_CLIENT,
                            SESSION_BILLING_DETAILS.SETTLEMENT_AMOUNT_OWNER_NON_GST_CLIENT,
                            SESSION_BILLING_DETAILS.TOTAL_AMOUNT_OWNER_WITH_GST,
                            SESSION_BILLING_DETAILS.TOTAL_AMOUNT_OWNER_NON_GST,
                            SESSION_BILLING_DETAILS.SESSION_START_AT,
                            SESSION_BILLING_DETAILS.SESSION_END_AT,
                            SESSION_BILLING_DETAILS.SESSION_STATUS
                    )
                    .from(SESSION_BILLING_DETAILS)
                    .where(condition)
                    .orderBy(SESSION_BILLING_DETAILS.SESSION_START_AT.desc())
                    .fetch(record -> {

                        SessionBillingDTO dto = record.into(SessionBillingDTO.class);

                        org.joda.time.DateTime start = record.get(SESSION_BILLING_DETAILS.SESSION_START_AT);
                        org.joda.time.DateTime stop = record.get(SESSION_BILLING_DETAILS.SESSION_END_AT);

                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                        try {
                            if (start != null) {

                                String istTime = start.toGregorianCalendar()
                                        .toZonedDateTime()
                                        .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                                        .format(formatter);

                                dto.setSessionStartAt(istTime);
                            }

                            if (stop != null) {

                                String istTime = stop.toGregorianCalendar()
                                        .toZonedDateTime()
                                        .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                                        .format(formatter);

                                dto.setSessionEndAt(istTime);
                            }
                        } catch (Exception e) {
                            log.error("Error converting timestamp for record id={}",
                                    record.get(SESSION_BILLING_DETAILS.ID), e);
                        }

                        return dto;
                    });
        } catch (Exception e) {
            log.error("Error while fetching settlement records", e);
            throw new RuntimeException("Failed to fetch settlement records", e);
        }
    }

    public void updateSettlementService(final Integer transactionId, final double unitFare,
                                        final double lastEnergy, final double updateConsumedAmount, final double totalConsumedAmount,
                                        final DateTime lastTimeStamp, final double walletAmount) {

        try {
            double startEnergy = getStartEnergy(transactionId);
            double consumedEnergy = (lastEnergy - startEnergy) / 1000;

            BigDecimal lastEnergyBD = BigDecimal.valueOf(lastEnergy).setScale(3, RoundingMode.HALF_UP);
            BigDecimal energyKwhBD = BigDecimal.valueOf(consumedEnergy).setScale(6, RoundingMode.HALF_UP);
            BigDecimal unitFareBD = BigDecimal.valueOf(unitFare);
            BigDecimal energyCostExclBD = energyKwhBD.multiply(unitFareBD);
            BigDecimal cgstBD = energyCostExclBD.multiply(CGST_RATE);
            BigDecimal sgstBD = energyCostExclBD.multiply(SGST_RATE);
            BigDecimal igstBD = energyCostExclBD.multiply(IGST_RATE);

            BigDecimal energyCostInclBD = energyCostExclBD.add(igstBD);
            BigDecimal previousTotal = getPreviousSessionTotal(transactionId);
            BigDecimal sessionTotalAmountBD = previousTotal.add(energyCostInclBD)
                    .setScale(2, RoundingMode.HALF_UP);

            secondary.update(SESSION_BILLING_DETAILS)
                    .set(SESSION_BILLING_DETAILS.END_ENERGY, lastEnergyBD)
                    .set(SESSION_BILLING_DETAILS.ENERGY_KWH, energyKwhBD)
                    .set(SESSION_BILLING_DETAILS.ENERGY_COST_EXCL_TAX, energyCostExclBD)
                    .set(SESSION_BILLING_DETAILS.ENERGY_COST_INCL_TAX, energyCostInclBD)
                    .set(SESSION_BILLING_DETAILS.CGST_AMOUNT, cgstBD)
                    .set(SESSION_BILLING_DETAILS.SGST_AMOUNT, sgstBD)
                    .set(SESSION_BILLING_DETAILS.IGST_AMOUNT, igstBD)
                    .set(SESSION_BILLING_DETAILS.SESSION_AMOUNT, energyCostInclBD)
                    .set(SESSION_BILLING_DETAILS.SESSION_TOTAL_AMOUNT, sessionTotalAmountBD)
                    .set(SESSION_BILLING_DETAILS.SESSION_END_AT, lastTimeStamp)
                    .where(SESSION_BILLING_DETAILS.TRANSACTION_ID.eq(transactionId))
                    .and(SESSION_BILLING_DETAILS.SESSION_STATUS.eq(true))
                    .execute();

        } catch (Exception e) {
            log.error("Error updating settlement for transactionId: {}", transactionId, e);
            throw new RuntimeException("Settlement update failed for transactionId: " + transactionId, e);
        }
    }

    private BigDecimal getPreviousSessionTotal(Integer transactionId) {

        BigDecimal prevTotal = secondary
                .select(SESSION_BILLING_DETAILS.SESSION_TOTAL_AMOUNT)
                .from(SESSION_BILLING_DETAILS)
                .where(SESSION_BILLING_DETAILS.TRANSACTION_ID.eq(transactionId))
                .and(SESSION_BILLING_DETAILS.SESSION_STATUS.eq(false))
                .orderBy(SESSION_BILLING_DETAILS.SESSION_START_AT.desc())
                .limit(1)
                .fetchOneInto(BigDecimal.class);

        return prevTotal != null ? prevTotal : BigDecimal.ZERO;
    }

    private double getStartEnergy(final Integer transactionId) {
        Double startEnergy = secondary
                .select(SESSION_BILLING_DETAILS.START_ENERGY)
                .from(SESSION_BILLING_DETAILS)
                .where(SESSION_BILLING_DETAILS.TRANSACTION_ID.eq(transactionId))
                .orderBy(SESSION_BILLING_DETAILS.SESSION_START_AT.desc())
                .limit(1)
                .fetchOneInto(Double.class);

        return startEnergy != null ? startEnergy : 0.0;
    }

    public void insertChargerTariffAmountSettlementService(final Integer transactionId,
                                                           final double startEnergy,
                                                           final double lastEnergy,
                                                           final String idTag,
                                                           final double unitFare,
                                                           final double walletAmount,
                                                           final double consumedEnergy,
                                                           final double consumedAmountNew,
                                                           final double totalConsumedAmountNew,
                                                           final DateTime startTime,
                                                           final String chargerId,
                                                           final Integer connectorNo) {

        try {
            BigDecimal startEnergyBD = BigDecimal.valueOf(startEnergy);
            BigDecimal lastEnergyBD = BigDecimal.valueOf(lastEnergy);
            BigDecimal energyKwhBD = BigDecimal.valueOf(consumedEnergy);
            BigDecimal unitFareBD = BigDecimal.valueOf(unitFare);
            BigDecimal walletBD = BigDecimal.valueOf(walletAmount);

            BigDecimal energyCostExclBD = energyKwhBD.multiply(unitFareBD);

            BigDecimal cgstBD = energyCostExclBD.multiply(CGST_RATE);
            BigDecimal sgstBD = energyCostExclBD.multiply(SGST_RATE);
            BigDecimal igstBD = energyCostExclBD.multiply(IGST_RATE);

            BigDecimal energyCostInclBD = energyCostExclBD.add(igstBD);

            BigDecimal sessionAmountBD = BigDecimal.valueOf(consumedAmountNew);
            BigDecimal sessionTotalAmountBD = BigDecimal.valueOf(totalConsumedAmountNew);

            Integer connectorId = secondary
                    .select(CONNECTOR.CONNECTOR_ID)
                    .from(CONNECTOR)
                    .where(CONNECTOR.CHARGE_BOX_ID.eq(chargerId))
                    .and(CONNECTOR.CONNECTOR_PK.eq(connectorNo))
                    .fetchOneInto(Integer.class);

            if (connectorId == null) {
                log.warn("Connector not found for chargerId={} connectorNo={}", chargerId, connectorNo);
                return;
            }

            ChargerStationDTO chargerStationDTO = php
                    .select(
                            CHARGER_ID.as("chargerId"),
                            CHARGER_QR_CODE.as("chargerQrCode"),
                            CONNECTOR_NO.as("connectorNo"),
                            STATION_ID.as("stationId"),
                            STATION_NAME.as("stationName"),
                            CPO_ID.as("cpoId"),
                            STATION_CITY.as("stationCity"),
                            STATION_STATE.as("stationState")
                    )
                    .from(CHARGE_POINT_VIEW)
                    .where(DSL.upper(CHARGER_ID).eq(chargerId.trim().toUpperCase()))
                    .and(CONNECTOR_NO.eq(connectorId))
                    .fetchOneInto(ChargerStationDTO.class);

            if (chargerStationDTO == null) {
                log.warn("ChargerStationDTO not found");
                return;
            }

            secondary.insertInto(SESSION_BILLING_DETAILS)
                    .set(SESSION_BILLING_DETAILS.TRANSACTION_ID, transactionId)
                    .set(SESSION_BILLING_DETAILS.CUSTOMER_ID_TAG, idTag)
                    .set(SESSION_BILLING_DETAILS.START_ENERGY, startEnergyBD)
                    .set(SESSION_BILLING_DETAILS.END_ENERGY, lastEnergyBD)
                    .set(SESSION_BILLING_DETAILS.ENERGY_KWH, energyKwhBD)
                    .set(SESSION_BILLING_DETAILS.UNIT_FARE, unitFareBD)
                    .set(SESSION_BILLING_DETAILS.ENERGY_COST_EXCL_TAX, energyCostExclBD)
                    .set(SESSION_BILLING_DETAILS.ENERGY_COST_INCL_TAX, energyCostInclBD)
                    .set(SESSION_BILLING_DETAILS.CGST_AMOUNT, cgstBD)
                    .set(SESSION_BILLING_DETAILS.SGST_AMOUNT, sgstBD)
                    .set(SESSION_BILLING_DETAILS.IGST_AMOUNT, igstBD)
                    .set(SESSION_BILLING_DETAILS.CUSTOMER_WALLET_AMOUNT, walletBD)
                    .set(SESSION_BILLING_DETAILS.SESSION_AMOUNT, sessionAmountBD)
                    .set(SESSION_BILLING_DETAILS.SESSION_TOTAL_AMOUNT, sessionTotalAmountBD)
                    .set(SESSION_BILLING_DETAILS.SESSION_START_AT, startTime)
                    .set(SESSION_BILLING_DETAILS.SESSION_END_AT, startTime)
                    .set(SESSION_BILLING_DETAILS.SESSION_STATUS, true)
                    .execute();

        } catch (Exception e) {
            log.error("Error inserting tariff record", e);
        }
    }

    public void startNewTariffInterval(Integer transactionId,  DateTime now) {
        secondary.update(SESSION_BILLING_DETAILS)
                .set(SESSION_BILLING_DETAILS.SESSION_STATUS, false)
                .set(SESSION_BILLING_DETAILS.SESSION_END_AT, now)
                .where(SESSION_BILLING_DETAILS.TRANSACTION_ID.eq(transactionId))
                .and(SESSION_BILLING_DETAILS.SESSION_STATUS.eq(true))
                .execute();

    }

}
