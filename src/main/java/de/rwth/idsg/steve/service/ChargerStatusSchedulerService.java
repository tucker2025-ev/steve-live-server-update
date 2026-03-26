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

import de.rwth.idsg.steve.web.dto.OcppJsonStatus;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static jooq.steve.db.Tables.CHARGER_STATUS;
import static jooq.steve.db.Tables.CHARGER_STATUS_HISTORY;

@Service
@RequiredArgsConstructor
public class ChargerStatusSchedulerService {

    private final DSLContext ctx;
    private final HomeService homeService;


    @Scheduled(fixedRate = 180000)
    public void updateChargerStatus() {

        DateTime now = DateTime.now();


        List<OcppJsonStatus> onlineChargePoint = homeService.onlineChargePoint();

        Set<String> onlineIds = onlineChargePoint.stream()
                .map(OcppJsonStatus::getChargeBoxId)
                .collect(Collectors.toSet());

        for (String cpId : onlineIds) {

            String status = ctx.select(CHARGER_STATUS.STATUS)
                    .from(CHARGER_STATUS)
                    .where(CHARGER_STATUS.CHARGE_BOX_ID.eq(cpId))
                    .fetchOneInto(String.class);


            if ("OFFLINE".equals(status)) {
                handleOnlineTransition(cpId, now);
            }

            upsertStatus(cpId, now, "ONLINE");
        }

        List<String> dbChargers = ctx
                .select(CHARGER_STATUS.CHARGE_BOX_ID)
                .from(CHARGER_STATUS)
                .fetchInto(String.class);

        for (String dbId : dbChargers) {

            if (!onlineIds.contains(dbId)) {

                String status = ctx.select(CHARGER_STATUS.STATUS)
                        .from(CHARGER_STATUS)
                        .where(CHARGER_STATUS.CHARGE_BOX_ID.eq(dbId))
                        .fetchOneInto(String.class);


                if ("ONLINE".equals(status)) {

                    handleOfflineTransition(dbId, now);
                }
            }
        }
    }

    private void upsertStatus(String chargeBoxId, DateTime ts, String status) {

        ctx.insertInto(CHARGER_STATUS,
                        CHARGER_STATUS.CHARGE_BOX_ID,
                        CHARGER_STATUS.FIRST_SEEN_TIME,
                        CHARGER_STATUS.LAST_HEARTBEAT_TIME,
                        CHARGER_STATUS.STATUS
                )
                .values(chargeBoxId, ts, ts, status)
                .onDuplicateKeyUpdate()
                .set(CHARGER_STATUS.LAST_HEARTBEAT_TIME, ts)
                .set(CHARGER_STATUS.STATUS, status)
                .execute();
    }


    private void handleOnlineTransition(String chargeBoxId, DateTime now) {

        ctx.update(CHARGER_STATUS_HISTORY)
                .set(CHARGER_STATUS_HISTORY.END_TIME, now)
                .set(CHARGER_STATUS_HISTORY.DURATION_SECONDS,
                        org.jooq.impl.DSL.field(
                                "TIMESTAMPDIFF(SECOND, {0}, {1})",
                                Integer.class,
                                CHARGER_STATUS_HISTORY.START_TIME,
                                org.jooq.impl.DSL.val(now.toDate())
                        )
                )
                .where(CHARGER_STATUS_HISTORY.CHARGE_BOX_ID.eq(chargeBoxId))
                .and(CHARGER_STATUS_HISTORY.STATUS.eq("OFFLINE"))
                .and(CHARGER_STATUS_HISTORY.END_TIME.isNull())
                .execute();


        ctx.insertInto(CHARGER_STATUS_HISTORY,
                        CHARGER_STATUS_HISTORY.CHARGE_BOX_ID,
                        CHARGER_STATUS_HISTORY.STATUS,
                        CHARGER_STATUS_HISTORY.START_TIME
                )
                .values(chargeBoxId, "ONLINE", now)
                .execute();
    }


    private void handleOfflineTransition(String chargeBoxId, DateTime now) {

        ctx.update(CHARGER_STATUS_HISTORY)
                .set(CHARGER_STATUS_HISTORY.END_TIME, now)
                .set(CHARGER_STATUS_HISTORY.DURATION_SECONDS,
                        org.jooq.impl.DSL.field(
                                "TIMESTAMPDIFF(SECOND, {0}, {1})",
                                Integer.class,
                                CHARGER_STATUS_HISTORY.START_TIME,
                                org.jooq.impl.DSL.val(now.toDate())
                        )
                )
                .where(CHARGER_STATUS_HISTORY.CHARGE_BOX_ID.eq(chargeBoxId))
                .and(CHARGER_STATUS_HISTORY.STATUS.eq("ONLINE"))
                .and(CHARGER_STATUS_HISTORY.END_TIME.isNull())
                .execute();


        ctx.insertInto(CHARGER_STATUS_HISTORY,
                        CHARGER_STATUS_HISTORY.CHARGE_BOX_ID,
                        CHARGER_STATUS_HISTORY.STATUS,
                        CHARGER_STATUS_HISTORY.START_TIME
                )
                .values(chargeBoxId, "OFFLINE", now)
                .execute();


        ctx.update(CHARGER_STATUS)
                .set(CHARGER_STATUS.STATUS, "OFFLINE")
                .where(CHARGER_STATUS.CHARGE_BOX_ID.eq(chargeBoxId))
                .execute();
    }

}