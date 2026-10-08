package org.acme.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BatteryPayload extends Payload {
    @JsonProperty("energy_available_kwh")
    public Double energyAvailableKwh;

    @JsonProperty("max_discharge_power_kw")
    public Double maxDischargePowerKw;

    @JsonProperty("active_power_kw")
    public Double activePowerKw;

    @JsonProperty("connection_status")
    public String connectionStatus;

    @JsonProperty("soh_percent")
    public Double sohPercent;

    @JsonProperty("soc_percent")
    public Double socPercent;
}
