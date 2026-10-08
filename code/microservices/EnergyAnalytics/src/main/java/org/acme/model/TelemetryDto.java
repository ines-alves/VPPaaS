package org.acme.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public class TelemetryDto {
    public String seqKey;

    @JsonAlias({"timestamp", "timeStamp"})
    public LocalDateTime timestamp;

    @JsonProperty("asset_type")
    public String assetType;

    @JsonProperty("asset_id")
    public String assetId;

    @JsonProperty("grid_cell_id")
    public String gridCellId;

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "asset_type",
        visible = true
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = BatteryPayload.class, name = "BATTERY"),
        @JsonSubTypes.Type(value = SolarPayload.class, name = "SOLAR"),
        @JsonSubTypes.Type(value = EvChargerPayload.class, name = "EV_CHARGER")
    })
    public Payload payload;
}
