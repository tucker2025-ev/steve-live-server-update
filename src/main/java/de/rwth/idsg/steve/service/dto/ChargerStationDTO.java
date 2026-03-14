package de.rwth.idsg.steve.service.dto;

import lombok.Data;

@Data
public class ChargerStationDTO {
    private String chargerId;
    private String chargerQrCode;
    private Integer connectorNo;
    private String stationId;
    private String stationName;
    private String cpoId;
    private String stationCity;
    private String stationState;
}
