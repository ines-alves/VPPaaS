package org.acme.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EvChargerPayload extends Payload {
    @JsonProperty("charging_power_kw")
    public Double chargingPowerKw;

    @JsonProperty("connector_status")
    public String connectorStatus;

    @JsonProperty("session_energy_kwh")
    public Double sessionEnergyKwh;

    @JsonProperty("ev_soc_percent")
    public Double evSocPercent;
}
