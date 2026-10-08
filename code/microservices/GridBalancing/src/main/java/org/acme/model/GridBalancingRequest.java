package org.acme.model;

import java.util.List;

public class GridBalancingRequest {
    public List<TelemetryDto> telemetry;
    public GridCellDto gridCell;

    public GridBalancingRequest() {}
}
