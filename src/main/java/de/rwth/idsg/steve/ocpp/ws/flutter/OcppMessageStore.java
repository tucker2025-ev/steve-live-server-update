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

import org.joda.time.DateTime;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static jooq.steve.db.Tables.TEST_BENCH_LOG;

@Component
public class OcppMessageStore {

    private final Map<String, List<String>> history = new ConcurrentHashMap<>();

    @Autowired
    private DSLContext dslContext;

    public void add(String chargeBoxId, String message) {

//        history.computeIfAbsent(chargeBoxId, k -> new ArrayList<>()).add(message);
//
//        if (history.get(chargeBoxId).size() > 100) {
//            history.get(chargeBoxId).remove(0);
//        }
        dslContext.insertInto(TEST_BENCH_LOG)
                .set(TEST_BENCH_LOG.CHARGE_BOX_ID, chargeBoxId)
                .set(TEST_BENCH_LOG.MESSAGE, message)
                .set(TEST_BENCH_LOG.TIME_STAMP, DateTime.now())
                .execute();

    }

//    public List<String> get(String chargeBoxId) {
//        return history.getOrDefault(chargeBoxId, Collections.emptyList());
//    }

    public List<String> getDataUseChargeBoxId(final String chargeBoxId) {
        return dslContext
                .select(TEST_BENCH_LOG.MESSAGE)
                .from(TEST_BENCH_LOG)
                .where(TEST_BENCH_LOG.CHARGE_BOX_ID.eq(chargeBoxId))
                .orderBy(TEST_BENCH_LOG.TIME_STAMP.desc())   // Latest first
                .limit(100)                           // Only 100 records
                .fetch(TEST_BENCH_LOG.MESSAGE);
    }

}