package org.acme.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SolarPayload extends Payload {
    @JsonProperty("ac_voltage_v")
    public Double acVoltageV;

    @JsonProperty("generation_kw")
    public Double generationKw;

    @JsonProperty("grid_frequency_hz")
    public Double gridFrequencyHz;

    @JsonProperty("daily_yield_kwh")
    public Double dailyYieldKwh;
}
