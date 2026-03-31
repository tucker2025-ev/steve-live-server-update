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
package de.rwth.idsg.steve.web.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SessionBillingDTO {

    private Integer id;
    private Integer transactionId;
    private Integer slabNo;
    private String cpoId;
    private String stationId;
    private String stationName;
    private String stationCity;
    private String stationState;
    private String customerIdTag;
    private BigDecimal customerWalletAmount;
    private String chargerId;
    private String chargerQrCode;
    private Integer connectorId;
    private BigDecimal startEnergy;
    private BigDecimal endEnergy;
    private BigDecimal energyKwh;
    private BigDecimal unitFare;
    private BigDecimal energyCostExclTax;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal energyCostInclTax;
    private BigDecimal serviceFeePerKwh;
    private BigDecimal serviceFeeAmount;
    private BigDecimal sessionAmount;
    private BigDecimal sessionTotalAmount;
    private BigDecimal settlementAmountOwnerWithGstClient;
    private BigDecimal settlementAmountOwnerNonGstClient;
    private BigDecimal totalAmountOwnerWithGst;
    private BigDecimal totalAmountOwnerNonGst;
    private String sessionStartAt;
    private String sessionEndAt;
    private Boolean sessionStatus;
}
