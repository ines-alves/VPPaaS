package org.acme.model;

import java.util.List;

public class NeighboringZonesDto {
    public GridCellDto selectedGridCell;
    public List<TelemetryDto> telemetry;
    public List<GridCellDto> gridCells;

    public NeighboringZonesDto() {}
}
