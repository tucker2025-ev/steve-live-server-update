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

import de.rwth.idsg.steve.service.dto.TransactionMeterValues;
import lombok.extern.slf4j.Slf4j;
import ocpp.cs._2015._10.MeterValue;
import ocpp.cs._2015._10.SampledValue;
import org.joda.time.DateTime;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

import static jooq.steve.db.Tables.TRANSACTION_CONNECTOR_ENERGY;
import static jooq.steve.db.Tables.TRANSACTION_METER_VALUES;
import static jooq.steve.db.tables.Connector.CONNECTOR;
import static jooq.steve.db.tables.TransactionStart.TRANSACTION_START;
import static jooq.steve.db2.Tables.*;
import static org.jooq.impl.DSL.iif;
import static org.jooq.impl.DSL.val;

@Slf4j
@Service
public class RetrieveTransactionMeterValues {

    @Autowired
    private DSLContext dslContext;
    @Autowired
    @Qualifier("secondary")
    private DSLContext secondary;
    @Autowired
    private TariffAmountCalculation tariffAmountCalculation;
    @Autowired
    private LiveChargingData liveChargingData;
    @Autowired
    private TestChargingData testChargingData;


    public TransactionMeterValues buildTransactionMeterValues(
            List<MeterValue> list,
            int connectorPk,
            Integer transactionId, final DateTime valueTimestamp) {
        String chargeBoxId = dslContext.select(CONNECTOR.CHARGE_BOX_ID)
                .from(CONNECTOR)
                .where(CONNECTOR.CONNECTOR_PK.eq(connectorPk))
                .fetchOne(CONNECTOR.CHARGE_BOX_ID);

        String idTag = dslContext.select(TRANSACTION_START.ID_TAG)
                .from(TRANSACTION_START)
                .where(TRANSACTION_START.TRANSACTION_PK.eq(transactionId))
                .fetchOne(TRANSACTION_START.ID_TAG);
        TransactionMeterValues tmv = new TransactionMeterValues();
        tmv.setConnectorPk(connectorPk);
        tmv.setTransactionPk(transactionId);

        double voltageSum = 0;
        int voltageCount = 0;
        double currentSum = 0;
        double powerSum = 0;

        for (MeterValue mv : list) {
            for (SampledValue sv : mv.getSampledValue()) {

                if (!sv.isSetMeasurand() || !sv.isSetValue()) continue;

                String measurand = sv.getMeasurand().value();
                String location = sv.isSetLocation()
                        ? sv.getLocation().value()
                        : "Outlet";

                double value;
                try {
                    value = Double.parseDouble(sv.getValue());
                } catch (Exception e) {
                    continue;
                }

                if (sv.isSetUnit() && "kW".equalsIgnoreCase(sv.getUnit().value())) {
                    value *= 1000;
                }

                // ENERGY (common)
                if ("Energy.Active.Import.Register".equals(measurand)
                        && "Outlet".equalsIgnoreCase(location)) {
                    tmv.setEnergy(value);
                }

                // DC (non-phase)
                if (!sv.isSetPhase()) {
                    if ("Voltage".equals(measurand)) tmv.setVoltage(value);
                    if ("Current.Import".equals(measurand)) tmv.setCurrent(value);
                    if ("Power.Active.Import".equals(measurand)) tmv.setPower(value);
                    if ("SoC".equals(measurand)) tmv.setSoc(value);
                }

                // AC (phase-wise)
                if (sv.isSetPhase()) {
                    if ("Voltage".equals(measurand)) {
                        voltageSum += value;
                        voltageCount++;
                    }
                    if ("Current.Import".equals(measurand)) {
                        currentSum += value;
                    }
                    if ("Power.Active.Import".equals(measurand)) {
                        powerSum += value;
                    }
                }
            }
        }


        if (voltageCount > 0) tmv.setVoltage(voltageSum / voltageCount);
        if (currentSum > 0) tmv.setCurrent(currentSum);
        if (powerSum > 0) tmv.setPower(powerSum);
        DateTime latestTimestamp = list.stream()
                .map(MeterValue::getTimestamp)
                .reduce((first, second) -> second)
                .orElse(null);
        tariffAmountCalculation.sets(idTag, tmv.getEnergy(), chargeBoxId, latestTimestamp, transactionId, connectorPk);

        dslContext.insertInto(TRANSACTION_METER_VALUES)
                .set(TRANSACTION_METER_VALUES.TRANSACTION_PK, transactionId)
                .set(TRANSACTION_METER_VALUES.OCPP_TAG_ID, idTag)
                .set(TRANSACTION_METER_VALUES.CHARGE_BOX_ID, chargeBoxId)
                .set(TRANSACTION_METER_VALUES.CONNECTOR_PK, connectorPk)
                .set(TRANSACTION_METER_VALUES.VOLTAGE, tmv.getVoltage())
                .set(TRANSACTION_METER_VALUES.CURRENT, tmv.getCurrent())
                .set(TRANSACTION_METER_VALUES.POWER, tmv.getPower())
                .set(TRANSACTION_METER_VALUES.ENERGY, tmv.getEnergy())
                .set(TRANSACTION_METER_VALUES.SOC, tmv.getSoc())
                .execute();

        insertTransactionLiveData(transactionId, tmv, chargeBoxId, connectorPk, idTag, valueTimestamp);
        try {
            transactionEnergyValues(transactionId, idTag, chargeBoxId, tmv);
        } catch (Exception e) {
            log.error("Exception Occur RetrieveTransactionMeterValues Class{}", e.getMessage());
        }
        return tmv;
    }


    private void insertTransactionLiveData(final Integer transactionId, final TransactionMeterValues transactionMeterValues, final String chargeBoxId, final Integer connectorPk, final String idTag, final DateTime valueTimeStamp) {
        boolean ans = isAlreadyInserted(transactionId);
        Integer connectorId = dslContext.select(CONNECTOR.CONNECTOR_ID)
                .from(CONNECTOR)
                .where(CONNECTOR.CONNECTOR_PK.eq(connectorPk))
                .execute();
        if (!ans) {

            liveChargingData.liveChargingData(
                    chargeBoxId,
                    connectorId,
                    transactionId,
                    idTag
            );

            insertConnectorEnergy(chargeBoxId, connectorId, transactionId, tariffAmountCalculation.retrieveStartEnergy(transactionId).toString(), valueTimeStamp);
        }

        insertConnectorEnergy(chargeBoxId, connectorId, transactionId, String.valueOf(transactionMeterValues.getEnergy()), valueTimeStamp);
        try {
            Field<Double> incomingSoc = val(transactionMeterValues.getSoc());

            Field<Double> startEnergyCondition =
                    iif(LIVE_CHARGING_DATA.START_ENERGY.isNull(),
                            val(tariffAmountCalculation.retrieveStartEnergy(transactionId)),
                            LIVE_CHARGING_DATA.START_ENERGY);

            Field<Double> startCurrentCondition =
                    iif(LIVE_CHARGING_DATA.START_CURRENT.isNull(),
                            val(transactionMeterValues.getCurrent()),
                            LIVE_CHARGING_DATA.START_CURRENT);

            Field<Double> startPowerCondition =
                    iif(LIVE_CHARGING_DATA.START_POWER.isNull(),
                            val(transactionMeterValues.getPower()),
                            LIVE_CHARGING_DATA.START_POWER);

            Field<Double> startVoltageCondition =
                    iif(LIVE_CHARGING_DATA.START_VOLTAGE.isNull(),
                            val(transactionMeterValues.getVoltage()),
                            LIVE_CHARGING_DATA.START_VOLTAGE);

            Field<Double> startSocCondition =
                    iif(LIVE_CHARGING_DATA.START_SOC.isNull(),
                            incomingSoc,
                            LIVE_CHARGING_DATA.START_SOC);

            Field<Double> endSocCondition =
                    iif(incomingSoc.gt(0.0),
                            incomingSoc,
                            LIVE_CHARGING_DATA.END_SOC);

            secondary.update(LIVE_CHARGING_DATA)

                    .set(LIVE_CHARGING_DATA.START_ENERGY, startEnergyCondition)
                    .set(LIVE_CHARGING_DATA.START_CURRENT, startCurrentCondition)
                    .set(LIVE_CHARGING_DATA.START_POWER, startPowerCondition)
                    .set(LIVE_CHARGING_DATA.START_VOLTAGE, startVoltageCondition)
                    .set(LIVE_CHARGING_DATA.START_SOC, startSocCondition)

                    .set(LIVE_CHARGING_DATA.END_ENERGY, transactionMeterValues.getEnergy())
                    .set(LIVE_CHARGING_DATA.END_CURRENT, transactionMeterValues.getCurrent())
                    .set(LIVE_CHARGING_DATA.END_POWER, transactionMeterValues.getPower())
                    .set(LIVE_CHARGING_DATA.END_VOLTAGE, transactionMeterValues.getVoltage())

                    .set(LIVE_CHARGING_DATA.END_SOC, endSocCondition)
                    .where(LIVE_CHARGING_DATA.TRANSACTION_ID.eq(transactionId))
                    .execute();

        } catch (Exception e) {
            log.error("insertTransactionLiveData Method Exception Occur : " + e.getMessage());
        }


    }


    public void insertTran(final Integer transactionPk, final Integer connectorPk, final double energy, final String idTag, final String chargerBoxId) {
        try {
            dslContext.insertInto(TRANSACTION_METER_VALUES)
                    .set(TRANSACTION_METER_VALUES.TRANSACTION_PK, transactionPk)
                    .set(TRANSACTION_METER_VALUES.CONNECTOR_PK, connectorPk)
                    .set(TRANSACTION_METER_VALUES.VOLTAGE, retrieveCurrentTransactionPreviewsVoltage(connectorPk, transactionPk))
                    .set(TRANSACTION_METER_VALUES.POWER, retrieveCurrentTransactionPreviewsPower(connectorPk, transactionPk))
                    .set(TRANSACTION_METER_VALUES.ENERGY, energy)
                    .set(TRANSACTION_METER_VALUES.SOC, retrieveCurrentTransactionPreviewsSoc(connectorPk, transactionPk))
                    .set(TRANSACTION_METER_VALUES.CURRENT, retrieveCurrentTransactionPreviewsCurrent(connectorPk, transactionPk))
                    .set(TRANSACTION_METER_VALUES.OCPP_TAG_ID, idTag)
                    .set(TRANSACTION_METER_VALUES.CHARGE_BOX_ID, chargerBoxId)
                    .execute();
        } catch (Exception e) {
            log.error("Exception Occur in Retrieve Transaction Meter Values : " + e.getMessage());
        }
    }

    private boolean isAlreadyInserted(final Integer transactionId) {
        return secondary.fetchExists(
                secondary.selectFrom(LIVE_CHARGING_DATA)
                        .where(LIVE_CHARGING_DATA.TRANSACTION_ID.eq(transactionId))
        );
    }


    public Double retrieveCurrentTransactionPreviewsVoltage(Integer connectorPk, Integer transactionPk) {
        Double lastEnergy = dslContext
                .select(TRANSACTION_METER_VALUES.VOLTAGE)
                .from(TRANSACTION_METER_VALUES)
                .where(TRANSACTION_METER_VALUES.CONNECTOR_PK.eq(connectorPk))
                .and(TRANSACTION_METER_VALUES.TRANSACTION_PK.eq(transactionPk))
                .orderBy(TRANSACTION_METER_VALUES.EVENT_TIMESTAMP.desc())
                .limit(1)
                .fetchOneInto(Double.class);

        return lastEnergy;
    }


    public Double retrieveCurrentTransactionPreviewsPower(Integer connectorPk, Integer transactionPk) {
        Double lastEnergy = dslContext
                .select(TRANSACTION_METER_VALUES.POWER)
                .from(TRANSACTION_METER_VALUES)
                .where(TRANSACTION_METER_VALUES.CONNECTOR_PK.eq(connectorPk))
                .and(TRANSACTION_METER_VALUES.TRANSACTION_PK.eq(transactionPk))
                .orderBy(TRANSACTION_METER_VALUES.EVENT_TIMESTAMP.desc())
                .limit(1)
                .fetchOneInto(Double.class);

        return lastEnergy;
    }

    public Double retrieveCurrentTransactionPreviewsSoc(Integer connectorPk, Integer transactionPk) {
        Double lastEnergy = dslContext
                .select(TRANSACTION_METER_VALUES.SOC)
                .from(TRANSACTION_METER_VALUES)
                .where(TRANSACTION_METER_VALUES.CONNECTOR_PK.eq(connectorPk))
                .and(TRANSACTION_METER_VALUES.TRANSACTION_PK.eq(transactionPk))
                .orderBy(TRANSACTION_METER_VALUES.EVENT_TIMESTAMP.desc())
                .limit(1)
                .fetchOneInto(Double.class);

        return lastEnergy;
    }

    public Double retrieveCurrentTransactionPreviewsCurrent(Integer connectorPk, Integer transactionPk) {
        Double lastEnergy = dslContext
                .select(TRANSACTION_METER_VALUES.CURRENT)
                .from(TRANSACTION_METER_VALUES)
                .where(TRANSACTION_METER_VALUES.CONNECTOR_PK.eq(connectorPk))
                .and(TRANSACTION_METER_VALUES.TRANSACTION_PK.eq(transactionPk))
                .orderBy(TRANSACTION_METER_VALUES.EVENT_TIMESTAMP.desc())
                .limit(1)
                .fetchOneInto(Double.class);

        return lastEnergy;
    }

    public void insertConnectorEnergy(final String chargeBoxId, final Integer connectorId, final Integer transactionId, final String value, final DateTime valueTimeStamp) {
        try {
            dslContext.insertInto(TRANSACTION_CONNECTOR_ENERGY)
                    .set(TRANSACTION_CONNECTOR_ENERGY.CHARGE_BOX_ID, chargeBoxId)
                    .set(TRANSACTION_CONNECTOR_ENERGY.CONNECTOR_ID, connectorId)
                    .set(TRANSACTION_CONNECTOR_ENERGY.TRANSACTION_ID, transactionId)
                    .set(TRANSACTION_CONNECTOR_ENERGY.ENERGY_VALUE, normalizeMeterValue(value))
                    .set(TRANSACTION_CONNECTOR_ENERGY.VALUE_TIMESTAMP, valueTimeStamp)
                    .execute();
        } catch (Exception e) {
            log.error("Exception Occur In  insertConnectorEnergy {}", e.getMessage());
        }
    }

    private String normalizeMeterValue(String value) {

        if (value == null) {
            return "0";
        }

        double d = Double.parseDouble(value);

        if (d == (long) d) {
            return String.valueOf((long) d);
        }

        return String.valueOf(d);
    }


    public void transactionEnergyValues(Integer transactionId,
                                        String idTag,
                                        String chargeBoxId,
                                        TransactionMeterValues tmv) {

        // Fetch latest WALLET_TRACK row
        Record record = secondary.select(
                        WALLET_TRACK.START_ENERGY,
                        WALLET_TRACK.TARIFF_AMOUNT,
                        WALLET_TRACK.LAST_ENERGY,
                        WALLET_TRACK.WALLET_AMOUNT,
                        WALLET_TRACK.CONSUMED_ENERGY,
                        WALLET_TRACK.CONSUMED_AMOUNT,
                        WALLET_TRACK.TOTAL_CONSUMED_AMOUNT,
                        WALLET_TRACK.START_TIMESTAMP,
                        WALLET_TRACK.STOP_TIMESTAMP,
                        WALLET_TRACK.IS_ACTIVE_TRANSACTION
                )
                .from(WALLET_TRACK)
                .where(WALLET_TRACK.TRANSACTION_ID.eq(transactionId))
                .orderBy(WALLET_TRACK.START_TIMESTAMP.desc())
                .limit(1)
                .fetchOne();

        if (record == null) return;

        boolean isStopFlow = (tmv == null);

        // Fetch as Double / Boolean / DateTime explicitly
        Double newTariff = record.get(WALLET_TRACK.TARIFF_AMOUNT, Double.class);
        DateTime newStartTs = record.get(WALLET_TRACK.START_TIMESTAMP, DateTime.class);

        // STEP 1: Get latest existing row in TRANSACTION_ENERGY_VALUES
        Record lastRow = secondary.select()
                .from(TRANSACTION_ENERGY_VALUES)
                .where(TRANSACTION_ENERGY_VALUES.TRANSACTION_ID.eq(transactionId))
                .orderBy(TRANSACTION_ENERGY_VALUES.START_TIMESTAMP.desc())
                .limit(1)
                .fetchOne();

        boolean insertNewRow = false;

        if (lastRow == null) {
            insertNewRow = true;
        } else {
            Double lastTariff = lastRow.get(TRANSACTION_ENERGY_VALUES.TARIFF_AMOUNT, Double.class);
            if (lastTariff == null || !lastTariff.equals(newTariff)) {
                insertNewRow = true;
            } else {
                newStartTs = lastRow.get(TRANSACTION_ENERGY_VALUES.START_TIMESTAMP, DateTime.class);
            }
        }

        if (insertNewRow) {
            if (lastRow != null) {
                DateTime lastStartTs = lastRow.get(TRANSACTION_ENERGY_VALUES.START_TIMESTAMP, DateTime.class);

                secondary.update(TRANSACTION_ENERGY_VALUES)
                        .set(TRANSACTION_ENERGY_VALUES.IS_ACTIVE_TRANSACTION, false)
                        .set(TRANSACTION_ENERGY_VALUES.STOP_TIMESTAMP, record.get(WALLET_TRACK.STOP_TIMESTAMP, DateTime.class))
                        .where(TRANSACTION_ENERGY_VALUES.TRANSACTION_ID.eq(transactionId))
                        .and(TRANSACTION_ENERGY_VALUES.START_TIMESTAMP.eq(lastStartTs))
                        .execute();
            }

            var insert = secondary.insertInto(TRANSACTION_ENERGY_VALUES)
                    .set(TRANSACTION_ENERGY_VALUES.TRANSACTION_ID, transactionId)
                    .set(TRANSACTION_ENERGY_VALUES.ID_TAG, idTag)
                    .set(TRANSACTION_ENERGY_VALUES.CHARGE_BOX_ID, chargeBoxId)

                    // Energy / tariff / wallet fields
                    .set(TRANSACTION_ENERGY_VALUES.START_ENERGY, record.get(WALLET_TRACK.START_ENERGY, Double.class))
                    .set(TRANSACTION_ENERGY_VALUES.TARIFF_AMOUNT, newTariff)
                    .set(TRANSACTION_ENERGY_VALUES.LAST_ENERGY, record.get(WALLET_TRACK.LAST_ENERGY, Double.class))
                    .set(TRANSACTION_ENERGY_VALUES.WALLET_AMOUNT, record.get(WALLET_TRACK.WALLET_AMOUNT, Double.class))
                    .set(TRANSACTION_ENERGY_VALUES.CONSUMED_ENERGY, record.get(WALLET_TRACK.CONSUMED_ENERGY, Double.class))
                    .set(TRANSACTION_ENERGY_VALUES.CONSUMED_AMOUNT, record.get(WALLET_TRACK.CONSUMED_AMOUNT, Double.class))
                    .set(TRANSACTION_ENERGY_VALUES.TOTAL_CONSUMED_AMOUNT, record.get(WALLET_TRACK.TOTAL_CONSUMED_AMOUNT, Double.class))

                    // Timestamps and flags
                    .set(TRANSACTION_ENERGY_VALUES.START_TIMESTAMP, newStartTs)
                    .set(TRANSACTION_ENERGY_VALUES.STOP_TIMESTAMP, record.get(WALLET_TRACK.STOP_TIMESTAMP, DateTime.class))
                    .set(TRANSACTION_ENERGY_VALUES.IS_ACTIVE_TRANSACTION, true);

            if (!isStopFlow) {
                insert.set(TRANSACTION_ENERGY_VALUES.LATEST_SOC, tmv.getSoc())
                        .set(TRANSACTION_ENERGY_VALUES.LAST_POWER, tmv.getPower())
                        .set(TRANSACTION_ENERGY_VALUES.LAST_CURRENT, tmv.getCurrent())
                        .set(TRANSACTION_ENERGY_VALUES.LAST_VOLTAGE, tmv.getVoltage())
                        .set(TRANSACTION_ENERGY_VALUES.LAST_ENERGY_VALUE, tmv.getEnergy());
            }

            insert.execute();
            return;
        }

        // STEP 3: UPDATE CURRENT ROW
        var update = secondary.update(TRANSACTION_ENERGY_VALUES)
                .set(TRANSACTION_ENERGY_VALUES.LAST_ENERGY, record.get(WALLET_TRACK.LAST_ENERGY, Double.class))
                .set(TRANSACTION_ENERGY_VALUES.WALLET_AMOUNT, record.get(WALLET_TRACK.WALLET_AMOUNT, Double.class))
                .set(TRANSACTION_ENERGY_VALUES.CONSUMED_ENERGY, record.get(WALLET_TRACK.CONSUMED_ENERGY, Double.class))
                .set(TRANSACTION_ENERGY_VALUES.CONSUMED_AMOUNT, record.get(WALLET_TRACK.CONSUMED_AMOUNT, Double.class))
                .set(TRANSACTION_ENERGY_VALUES.TOTAL_CONSUMED_AMOUNT, record.get(WALLET_TRACK.TOTAL_CONSUMED_AMOUNT, Double.class))
                .set(TRANSACTION_ENERGY_VALUES.STOP_TIMESTAMP, record.get(WALLET_TRACK.STOP_TIMESTAMP, DateTime.class))
                .set(TRANSACTION_ENERGY_VALUES.IS_ACTIVE_TRANSACTION, record.get(WALLET_TRACK.IS_ACTIVE_TRANSACTION, Boolean.class));

        if (!isStopFlow) {
            update.set(TRANSACTION_ENERGY_VALUES.LATEST_SOC, tmv.getSoc())
                    .set(TRANSACTION_ENERGY_VALUES.LAST_POWER, tmv.getPower())
                    .set(TRANSACTION_ENERGY_VALUES.LAST_CURRENT, tmv.getCurrent())
                    .set(TRANSACTION_ENERGY_VALUES.LAST_VOLTAGE, tmv.getVoltage())
                    .set(TRANSACTION_ENERGY_VALUES.LAST_ENERGY_VALUE, tmv.getEnergy());
        }

        update.where(TRANSACTION_ENERGY_VALUES.TRANSACTION_ID.eq(transactionId))
                .and(TRANSACTION_ENERGY_VALUES.START_TIMESTAMP.eq(newStartTs))
                .execute();
    }

}


