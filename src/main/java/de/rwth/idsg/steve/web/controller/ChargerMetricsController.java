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
package de.rwth.idsg.steve.web.controller;

import de.rwth.idsg.steve.service.ChargerMetricsService;
import de.rwth.idsg.steve.service.dto.ChargerMetricsResponse;
import de.rwth.idsg.steve.service.dto.DateRange;
import de.rwth.idsg.steve.service.dto.RangeType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/charger")
public class ChargerMetricsController {

    private final ChargerMetricsService service;

    public ChargerMetricsController(ChargerMetricsService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public ChargerMetricsResponse getMetrics(
            @RequestParam String chargeBoxId,
            @RequestParam(required = false) RangeType rangeType,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {

        DateRange range;
        DateTime now = DateTime.now(DateTimeZone.UTC);

        if (start != null && end != null) {

            DateTime startTime = DateTime.parse(start);
            DateTime endTime = DateTime.parse(end);

            if (endTime.isAfter(now)) {
                endTime = now;
            }

            range = new DateRange(startTime, endTime);
        }

        else if (rangeType != null) {

            range = service.buildRange(rangeType); // already uses NOW
        }

        // ================= DEFAULT =================
        else {
            range = service.buildRange(RangeType.DAY);
        }

        return service.getMetrics(chargeBoxId, range);
    }

}