package org.acme.model;

import java.util.List;

public class ConsumedByProsumerAnalyticsRequest {
    public Long prosumerId;
    public List<TelemetryDto> telemetry;

    public ConsumedByProsumerAnalyticsRequest() {}
}
