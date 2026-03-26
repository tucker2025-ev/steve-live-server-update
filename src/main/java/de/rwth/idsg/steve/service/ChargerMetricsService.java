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

import de.rwth.idsg.steve.service.dto.ChargerMetricsResponse;
import de.rwth.idsg.steve.service.dto.DateRange;
import de.rwth.idsg.steve.service.dto.RangeType;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

import static jooq.steve.db.Tables.*;

@Slf4j
@Service
public class ChargerMetricsService {

    private final DSLContext ctx;

    public ChargerMetricsService(DSLContext ctx) {
        this.ctx = ctx;
    }

    // =========================================================
    // MAIN METHOD
    // =========================================================
    public ChargerMetricsResponse getMetrics(String chargeBoxId, DateRange dateRange) {

        Timestamp rangeStart = new Timestamp(dateRange.getStart().getMillis());
        Timestamp rangeEnd = new Timestamp(dateRange.getEnd().getMillis());

        // 👉 Status fields
        Field<Timestamp> startField =
                CHARGER_STATUS_HISTORY.START_TIME.cast(Timestamp.class);

        Field<Timestamp> endField =
                CHARGER_STATUS_HISTORY.END_TIME.cast(Timestamp.class);

        // 👉 Clip inside range
        Field<Timestamp> effectiveStart =
                DSL.greatest(startField, DSL.val(rangeStart));

        Field<Timestamp> effectiveEnd =
                DSL.least(
                        DSL.coalesce(endField, DSL.val(rangeEnd)),
                        DSL.val(rangeEnd)
                );

        Field<Integer> durationSeconds =
                DSL.field(
                        "TIMESTAMPDIFF(SECOND, {0}, {1})",
                        Integer.class,
                        effectiveStart,
                        effectiveEnd
                );

        // ================= ONLINE =================
        Integer online = ctx
                .select(DSL.coalesce(DSL.sum(durationSeconds), 0))
                .from(CHARGER_STATUS_HISTORY)
                .where(CHARGER_STATUS_HISTORY.CHARGE_BOX_ID.eq(chargeBoxId))
                .and(CHARGER_STATUS_HISTORY.STATUS.eq("ONLINE"))
                .and(startField.lt(rangeEnd))
                .and(DSL.coalesce(endField, DSL.val(rangeEnd)).gt(rangeStart))
                .fetchOne(0, Integer.class);


        Integer offline = ctx
                .select(DSL.coalesce(DSL.sum(durationSeconds), 0))
                .from(CHARGER_STATUS_HISTORY)
                .where(CHARGER_STATUS_HISTORY.CHARGE_BOX_ID.eq(chargeBoxId))
                .and(CHARGER_STATUS_HISTORY.STATUS.eq("OFFLINE"))
                .and(startField.lt(rangeEnd))
                .and(DSL.coalesce(endField, DSL.val(rangeEnd)).gt(rangeStart))
                .fetchOne(0, Integer.class);


        int chargingSeconds = getChargingSeconds(chargeBoxId, rangeStart, rangeEnd);

        // ================= TOTAL =================
        int total = online + offline;

        // ================= METRICS =================
        double utilization = (total == 0) ? 0 : (online * 100.0) / total;
        double effectiveUtilization = (total == 0) ? 0 : (chargingSeconds * 100.0) / total;
        double efficiency = (online == 0) ? 0 : (chargingSeconds * 100.0) / online;

        String rangeStr = dateRange.getStart() + " to " + dateRange.getEnd();

        return new ChargerMetricsResponse(
                chargeBoxId,
                rangeStr,
                online,
                offline,
                total,
                chargingSeconds,
                utilization,
                effectiveUtilization,
                efficiency
        );
    }


    private int getChargingSeconds(String chargeBoxId, Timestamp rangeStart, Timestamp rangeEnd) {

        Field<Timestamp> startField =
                TRANSACTION.START_EVENT_TIMESTAMP.cast(Timestamp.class);

        Field<Timestamp> endField =
                TRANSACTION.STOP_EVENT_TIMESTAMP.cast(Timestamp.class);

        Field<Timestamp> effectiveStart =
                DSL.greatest(startField, DSL.val(rangeStart));

        Field<Timestamp> effectiveEnd =
                DSL.least(
                        DSL.coalesce(endField, DSL.val(rangeEnd)),
                        DSL.val(rangeEnd)
                );

        Field<Integer> durationField =
                DSL.field(
                        "TIMESTAMPDIFF(SECOND, {0}, {1})",
                        Integer.class,
                        effectiveStart,
                        effectiveEnd
                );

        Integer total = ctx
                .select(DSL.coalesce(DSL.sum(durationField), 0))
                .from(TRANSACTION)


                .join(CONNECTOR)
                .on(TRANSACTION.CONNECTOR_PK.eq(CONNECTOR.CONNECTOR_PK))

                .where(CONNECTOR.CHARGE_BOX_ID.eq(chargeBoxId))

                .and(startField.lt(rangeEnd))
                .and(DSL.coalesce(endField, DSL.val(rangeEnd)).gt(rangeStart))

                .fetchOne(0, Integer.class);

        return total != null ? total : 0;
    }

    // =========================================================
    // RANGE BUILDER (REAL-TIME)
    // =========================================================
    public DateRange buildRange(RangeType type) {

        DateTime now = DateTime.now(DateTimeZone.UTC);

        switch (type) {

            case DAY:
                return new DateRange(
                        now.withTimeAtStartOfDay(),
                        now
                );

            case WEEK:
                return new DateRange(
                        now.withDayOfWeek(1).withTimeAtStartOfDay(),
                        now
                );

            case MONTH:
                return new DateRange(
                        now.withDayOfMonth(1).withTimeAtStartOfDay(),
                        now
                );

            default:
                throw new IllegalArgumentException("Invalid RangeType");
        }
    }
}