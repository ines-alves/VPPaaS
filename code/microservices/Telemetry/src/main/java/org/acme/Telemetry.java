package org.acme;

import java.util.List;

import org.acme.model.BatteryPayload;
import org.acme.model.EvChargerPayload;
import org.acme.model.Payload;
import org.acme.model.SolarPayload;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;

public class Telemetry {

    // Allows Jackson to serialize/deserialize LocalDateTime
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public String seqKey;

    @JsonAlias({"timestamp", "timeStamp"})
    public java.time.LocalDateTime timestamp;

    @JsonProperty("asset_type")
    public String assetType;

    @JsonProperty("asset_id")
    public String assetId;

    @JsonProperty("grid_cell_id")
    public String gridCellId;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "asset_type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = BatteryPayload.class, name = "BATTERY"),
        @JsonSubTypes.Type(value = SolarPayload.class, name = "SOLAR"),
        @JsonSubTypes.Type(value = EvChargerPayload.class, name = "EV_CHARGER")
    })
    public Payload payload;

    public Telemetry() {}

    private static Telemetry from(Row row) {
        Telemetry t = new Telemetry();
        t.seqKey = row.getString("seqKey");
        t.timestamp = row.getLocalDateTime("timestamp");
        t.assetType = row.getString("asset_type");
        t.assetId = row.getString("asset_id");
        t.gridCellId = row.getString("grid_cell_id");

        String payloadJson = row.getString("payload");
        if (payloadJson != null && !payloadJson.isBlank() && t.assetType != null) {
            try {
                if ("BATTERY".equalsIgnoreCase(t.assetType)) {
                    t.payload = MAPPER.readValue(payloadJson, BatteryPayload.class);
                } else if ("SOLAR".equalsIgnoreCase(t.assetType)) {
                    t.payload = MAPPER.readValue(payloadJson, SolarPayload.class);
                } else if ("EV_CHARGER".equalsIgnoreCase(t.assetType)) {
                    t.payload = MAPPER.readValue(payloadJson, EvChargerPayload.class);
                }
            } catch (Exception e) {
                System.err.println("Error deserializing payload: " + e.getMessage());
            }
        }

        return t;
    }
    
    public static Multi<Telemetry> findAll(MySQLPool client) {
        return client.query("SELECT * FROM telemetry").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }
    
    public static Uni<Telemetry> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT * FROM telemetry WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(rowSet -> {
                    var it = rowSet.iterator();
                    return it.hasNext() ? from(it.next()) : null;
                });
    }

    public static Multi<Telemetry> findByAssetId(MySQLPool client, String assetId) {
        return client.preparedQuery("SELECT * FROM telemetry WHERE asset_id = ?")
                .execute(Tuple.of(assetId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }

    public static Uni<Telemetry> findLatestByAssetId(MySQLPool client, String assetId) {
        return client.preparedQuery("SELECT * FROM telemetry WHERE asset_id = ? ORDER BY timestamp DESC LIMIT 1")
                .execute(Tuple.of(assetId))
                .onItem().transform(rowSet -> {
                    var it = rowSet.iterator();
                    return it.hasNext() ? from(it.next()) : null;
                });
    }

    public static Multi<Telemetry> findByGridCellId(MySQLPool client, String gridCellId) {
        return client.preparedQuery("SELECT * FROM telemetry WHERE grid_cell_id = ?")
                .execute(Tuple.of(gridCellId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }

        public static Multi<Telemetry> findLatestByGridCellId(MySQLPool client, String gridCellId) {
        String sql = "SELECT t.* FROM telemetry t "
            + "JOIN (SELECT asset_id, MAX(timestamp) AS max_ts FROM telemetry "
            + "WHERE grid_cell_id = ? GROUP BY asset_id) m "
            + "ON t.asset_id = m.asset_id AND t.timestamp = m.max_ts "
            + "WHERE t.grid_cell_id = ?";

        return client.preparedQuery(sql)
            .execute(Tuple.of(gridCellId, gridCellId))
            .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
            .onItem().transform(Telemetry::from);
        }

    public static Multi<Telemetry> findLatestByAssetIds(MySQLPool client, List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Multi.createFrom().empty();
        }

        String placeholders = String.join(",", assetIds.stream().map(id -> "?").toList());
        String sql = "SELECT t.* FROM telemetry t "
                + "JOIN (SELECT asset_id, MAX(timestamp) AS max_ts FROM telemetry "
                + "WHERE asset_id IN (" + placeholders + ") GROUP BY asset_id) m "
                + "ON t.asset_id = m.asset_id AND t.timestamp = m.max_ts";

        Tuple params = Tuple.tuple();
        for (String assetId : assetIds) {
            params.addString(assetId);
        }

        return client.preparedQuery(sql)
                .execute(params)
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }

    public static Multi<Telemetry> findLatestByGridZoneIds(MySQLPool client, List<String> gridZoneIds) {
        if (gridZoneIds == null || gridZoneIds.isEmpty()) {
            return Multi.createFrom().empty();
        }

        String placeholders = String.join(",", gridZoneIds.stream().map(id -> "?").toList());
        String sql = "SELECT t.* FROM telemetry t "
                + "JOIN (SELECT grid_cell_id, asset_id, MAX(timestamp) AS max_ts FROM telemetry "
                + "WHERE grid_cell_id IN (" + placeholders + ") GROUP BY grid_cell_id, asset_id) m "
                + "ON t.grid_cell_id = m.grid_cell_id AND t.asset_id = m.asset_id AND t.timestamp = m.max_ts";

        Tuple params = Tuple.tuple();
        for (String gridZoneId : gridZoneIds) {
            params.addString(gridZoneId);
        }

        return client.preparedQuery(sql)
                .execute(params)
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }
    public Uni<Boolean> save(MySQLPool client) {
        String payloadJson = "";
        try {
            payloadJson = MAPPER.writeValueAsString(this.payload);
        } catch (Exception e) {
            System.err.println("Error serializing payload: " + e.getMessage());
        }

        return client.preparedQuery("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES (?, ?, ?, ?, ?, ?)")
                .execute(Tuple.of(this.seqKey, this.timestamp, this.assetType, this.assetId, this.gridCellId, payloadJson))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}