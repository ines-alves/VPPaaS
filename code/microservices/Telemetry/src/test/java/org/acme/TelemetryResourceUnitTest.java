package org.acme;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.acme.model.BatteryPayload;
import org.acme.model.EvChargerPayload;
import org.acme.model.SolarPayload;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;

class TelemetryResourceUnitTest {

    @Test
    void findAll_returnsMixedPayloadTypes() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row battery = telemetryRow("SEQ-101", LocalDateTime.parse("2026-05-10T14:00:00"), "BATTERY", "BAT-101", "GRID-1", batteryPayloadJson());
        Row solar = telemetryRow("SEQ-102", LocalDateTime.parse("2026-05-10T14:05:00"), "SOLAR", "SOL-102", "GRID-2", solarPayloadJson());
        Row ev = telemetryRow("SEQ-103", LocalDateTime.parse("2026-05-10T14:10:00"), "EV_CHARGER", "EV-103", "GRID-3", evPayloadJson());
        stubQuery(client, "SELECT * FROM telemetry", rowSetOfRows(battery, solar, ev));

        List<Telemetry> result = Telemetry.findAll(client).collect().asList().await().indefinitely();

        assertThat(result, hasSize(3));
        assertThat(result.get(0).payload, instanceOf(BatteryPayload.class));
        assertThat(result.get(1).payload, instanceOf(SolarPayload.class));
        assertThat(result.get(2).payload, instanceOf(EvChargerPayload.class));
        assertThat(((BatteryPayload) result.get(0).payload).socPercent, equalTo(78.0));
        assertThat(((SolarPayload) result.get(1).payload).generationKw, equalTo(9.8));
        assertThat(((EvChargerPayload) result.get(2).payload).connectorStatus, equalTo("CONNECTED"));
    }

    @Test
    void findById_returnsTelemetryWithBatteryPayload() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = telemetryRow("SEQ-201", LocalDateTime.parse("2026-05-10T15:00:00"), "BATTERY", "BAT-201", "GRID-4", batteryPayloadJson());
        stubPreparedQuery(client, "SELECT * FROM telemetry WHERE id = ?", rowSetOfRows(row));

        Telemetry telemetry = Telemetry.findById(client, 201L).await().indefinitely();

        assertThat(telemetry, notNullValue());
        assertThat(telemetry.seqKey, equalTo("SEQ-201"));
        assertThat(telemetry.payload, instanceOf(BatteryPayload.class));
        assertThat(((BatteryPayload) telemetry.payload).energyAvailableKwh, equalTo(12.5));
    }

    @Test
    void findLatestByAssetId_returnsSolarTelemetry() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = telemetryRow("SEQ-202", LocalDateTime.parse("2026-05-10T15:05:00"), "SOLAR", "SOL-202", "GRID-4", solarPayloadJson());
        stubPreparedQuery(client, "SELECT * FROM telemetry WHERE asset_id = ? ORDER BY timestamp DESC LIMIT 1", rowSetOfRows(row));

        Telemetry result = Telemetry.findLatestByAssetId(client, "SOL-202").await().indefinitely();

        assertThat(result, notNullValue());
        assertThat(result.seqKey, equalTo("SEQ-202"));
        assertThat(result.payload, instanceOf(SolarPayload.class));
        assertThat(((SolarPayload) result.payload).generationKw, equalTo(9.8));
    }

    @Test
    void findLatestByGridCellId_returnsEvTelemetry() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = telemetryRow("SEQ-203", LocalDateTime.parse("2026-05-10T15:10:00"), "EV_CHARGER", "EV-203", "GRID-9", evPayloadJson());
        String sql = "SELECT t.* FROM telemetry t "
            + "JOIN (SELECT asset_id, MAX(timestamp) AS max_ts FROM telemetry "
            + "WHERE grid_cell_id = ? GROUP BY asset_id) m "
            + "ON t.asset_id = m.asset_id AND t.timestamp = m.max_ts "
            + "WHERE t.grid_cell_id = ?";
        stubPreparedQuery(client, sql, rowSetOfRows(row));

        List<Telemetry> result = Telemetry.findLatestByGridCellId(client, "GRID-9").collect().asList().await().indefinitely();

        assertThat(result, hasSize(1));
        assertThat(result.get(0).seqKey, equalTo("SEQ-203"));
        assertThat(result.get(0).payload, instanceOf(EvChargerPayload.class));
        assertThat(((EvChargerPayload) result.get(0).payload).connectorStatus, equalTo("CONNECTED"));
    }

    @Test
    void save_serializesPayloadAndReturnsTrue() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        AtomicReference<io.vertx.mutiny.sqlclient.Tuple> capturedTuple = new AtomicReference<>();
        Mockito.when(preparedQuery.execute(Mockito.any())).thenAnswer(invocation -> {
            capturedTuple.set(invocation.getArgument(0));
            return Uni.createFrom().item(rowSet);
        });
        Mockito.when(client.preparedQuery("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES (?, ?, ?, ?, ?, ?)")).thenReturn(preparedQuery);

        Telemetry telemetry = new Telemetry();
        telemetry.seqKey = "SEQ-301";
        telemetry.timestamp = LocalDateTime.parse("2026-05-10T16:00:00");
        telemetry.assetType = "SOLAR";
        telemetry.assetId = "SOL-301";
        telemetry.gridCellId = "GRID-5";
        SolarPayload payload = new SolarPayload();
        payload.acVoltageV = 230.0;
        payload.generationKw = 9.8;
        payload.gridFrequencyHz = 50.0;
        payload.dailyYieldKwh = 21.4;
        telemetry.payload = payload;

        Boolean result = telemetry.save(client).await().indefinitely();

        assertThat(result, equalTo(true));
        assertThat(capturedTuple.get(), notNullValue());
        assertThat(capturedTuple.get().getString(0), equalTo("SEQ-301"));
        assertThat(capturedTuple.get().getString(2), equalTo("SOLAR"));
        assertThat(capturedTuple.get().getString(3), equalTo("SOL-301"));
        assertThat(capturedTuple.get().getString(4), equalTo("GRID-5"));
        assertThat(capturedTuple.get().getString(5), equalTo("{\"ac_voltage_v\":230.0,\"generation_kw\":9.8,\"grid_frequency_hz\":50.0,\"daily_yield_kwh\":21.4}"));
    }

    @Test
    void save_returnsFalseWhenInsertDoesNotAffectRows() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES (?, ?, ?, ?, ?, ?)")).thenReturn(preparedQuery);

        Telemetry telemetry = new Telemetry();
        telemetry.seqKey = "SEQ-401";
        telemetry.timestamp = LocalDateTime.parse("2026-05-10T17:00:00");
        telemetry.assetType = "BATTERY";
        telemetry.assetId = "BAT-401";
        telemetry.gridCellId = "GRID-6";
        telemetry.payload = new BatteryPayload();

        Boolean result = telemetry.save(client).await().indefinitely();

        assertThat(result, equalTo(false));
    }

    @Test
    void findLatestByAssetIds_returnsMultipleItems() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row1 = telemetryRow("SEQ-501", LocalDateTime.parse("2026-05-10T16:00:00"), "BATTERY", "BATT-001", "GRID-A", batteryPayloadJson());
        Row row2 = telemetryRow("SEQ-502", LocalDateTime.parse("2026-05-10T16:05:00"), "SOLAR", "SOL-001", "GRID-B", solarPayloadJson());
        String sql = "SELECT t.* FROM telemetry t "
            + "JOIN (SELECT asset_id, MAX(timestamp) AS max_ts FROM telemetry "
            + "WHERE asset_id IN (?,?) GROUP BY asset_id) m "
            + "ON t.asset_id = m.asset_id AND t.timestamp = m.max_ts";
        stubPreparedQuery(client, sql, rowSetOfRows(row1, row2));

        List<Telemetry> result = Telemetry.findLatestByAssetIds(client, List.of("BATT-001", "SOL-001"))
            .collect().asList().await().indefinitely();

        assertThat(result, hasSize(2));
        assertThat(result.get(0).seqKey, equalTo("SEQ-501"));
    }

    @Test
    void findLatestByAssetIds_returnsEmptyForEmptyList() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        List<Telemetry> result = Telemetry.findLatestByAssetIds(client, List.of())
            .collect().asList().await().indefinitely();
        assertThat(result, hasSize(0));
    }

    @Test
    void findLatestByGridZoneIds_returnsMatchingItems() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = telemetryRow("SEQ-601", LocalDateTime.parse("2026-05-10T17:00:00"), "EV_CHARGER", "EV-001", "GRID-R", evPayloadJson());
        String sql = "SELECT t.* FROM telemetry t "
            + "JOIN (SELECT grid_cell_id, asset_id, MAX(timestamp) AS max_ts FROM telemetry "
            + "WHERE grid_cell_id IN (?) GROUP BY grid_cell_id, asset_id) m "
            + "ON t.grid_cell_id = m.grid_cell_id AND t.asset_id = m.asset_id AND t.timestamp = m.max_ts";
        stubPreparedQuery(client, sql, rowSetOfRows(row));

        List<Telemetry> result = Telemetry.findLatestByGridZoneIds(client, List.of("GRID-R"))
            .collect().asList().await().indefinitely();

        assertThat(result, hasSize(1));
        assertThat(result.get(0).gridCellId, equalTo("GRID-R"));
        assertThat(result.get(0).payload, instanceOf(EvChargerPayload.class));
    }

    @Test
    void findLatestByGridZoneIds_returnsEmptyForEmptyList() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        List<Telemetry> result = Telemetry.findLatestByGridZoneIds(client, List.of())
            .collect().asList().await().indefinitely();
        assertThat(result, hasSize(0));
    }

    private void stubQuery(MySQLPool client, String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(MySQLPool client, String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetOfRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(rows)));
        return rowSet;
    }

    private RowIterator<Row> rowIteratorOf(Row... rows) {
        List<Row> list = Arrays.asList(rows);
        java.util.Iterator<Row> iterator = list.iterator();
        return new RowIterator<Row>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Row next() {
                return iterator.next();
            }
        };
    }

    private Row telemetryRow(String seqKey, LocalDateTime timestamp, String assetType, String assetId, String gridCellId, String payloadJson) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("seqKey")).thenReturn(seqKey);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("asset_type")).thenReturn(assetType);
        Mockito.when(row.getString("asset_id")).thenReturn(assetId);
        Mockito.when(row.getString("grid_cell_id")).thenReturn(gridCellId);
        Mockito.when(row.getString("payload")).thenReturn(payloadJson);
        return row;
    }

    private String batteryPayloadJson() {
        return """
            {
              "energy_available_kwh": 12.5,
              "max_discharge_power_kw": 4.5,
              "active_power_kw": 2.2,
              "connection_status": "ONLINE",
              "soh_percent": 96.0,
              "soc_percent": 78.0
            }
            """;
    }

    private String solarPayloadJson() {
        return """
            {
              "ac_voltage_v": 230.0,
              "generation_kw": 9.8,
              "grid_frequency_hz": 50.0,
              "daily_yield_kwh": 21.4
            }
            """;
    }

    private String evPayloadJson() {
        return """
            {
              "charging_power_kw": 11.0,
              "connector_status": "CONNECTED",
              "session_energy_kwh": 5.6,
              "ev_soc_percent": 64.0
            }
            """;
    }
}