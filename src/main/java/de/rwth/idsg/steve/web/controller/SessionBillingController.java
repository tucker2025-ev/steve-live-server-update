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

import de.rwth.idsg.steve.service.SessionBillingDetails;
//import de.rwth.idsg.steve.service.WalletTrackSettlementService;
import de.rwth.idsg.steve.web.dto.ApiResponseDTO;
import de.rwth.idsg.steve.web.dto.SessionBillingDTO;
import de.rwth.idsg.steve.web.dto.WalletSettlementDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/settlement")
public class SessionBillingController {

    @Autowired
    private SessionBillingDetails sessionBillingDetails;

    @GetMapping("/records")
    public ApiResponseDTO getSettlementRecords(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String cpoId,
            @RequestParam(required = false) String chargerQrCode,
            @RequestParam(required = false) Integer transactionId,
            @RequestParam(required = false) String startTimestamp,
            @RequestParam(required = false) String stopTimestamp) {

        try {
            List<SessionBillingDTO> records = sessionBillingDetails.getSettlementRecords(
                    stationId, cpoId, chargerQrCode, transactionId, startTimestamp, stopTimestamp);

            if (records.isEmpty()) {
                return new ApiResponseDTO("false", List.of());
            }

            return new ApiResponseDTO("true", records);

        } catch (Exception e) {
            log.error("Error fetching settlement data", e);
            return new ApiResponseDTO("false", List.of());
        }
    }

}
