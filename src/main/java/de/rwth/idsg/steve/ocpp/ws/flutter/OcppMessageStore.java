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

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static jooq.steve.db.Tables.TEST_BENCH_LOG;

@Component
public class OcppMessageStore {

    @Autowired
    private DSLContext dsl;

    public void add(String chargeBoxId, String event, String direction, String message) {

        dsl.insertInto(TEST_BENCH_LOG)
                .set(TEST_BENCH_LOG.CHARGE_BOX_ID, chargeBoxId)
                .set(TEST_BENCH_LOG.EVENT, event)
                .set(TEST_BENCH_LOG.DIRECTION, direction)
                .set(TEST_BENCH_LOG.MESSAGE, message)
                .execute();
    }

    public List<String> getRecent(String chargeBoxId) {

        List<String> result = dsl
                .select(TEST_BENCH_LOG.MESSAGE)
                .from(TEST_BENCH_LOG)
                .where(TEST_BENCH_LOG.CHARGE_BOX_ID.eq(chargeBoxId))
                .orderBy(TEST_BENCH_LOG.TIME_STAMP.desc())
                .limit(100)
                .fetch(TEST_BENCH_LOG.MESSAGE);

        Collections.reverse(result);

        return result;
    }

}