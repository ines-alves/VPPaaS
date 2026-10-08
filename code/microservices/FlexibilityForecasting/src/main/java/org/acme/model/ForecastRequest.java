package org.acme.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ForecastRequest {
    @JsonAlias("prosumerID")
    public Long prosumerId;
    public TelemetryDto telemetry;
    public FlexibilityOfferDto flexibilityEvent;

    public ForecastRequest() {}
}
