package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;

@QuarkusTest
public class TelemetryResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @Test
    public void testGetReturnsTelemetryList() {
        Row row1 = telemetryRow("SEQ-001", LocalDateTime.parse("2026-05-10T10:00:00"), "BATTERY", "BAT-01", "GRID-A", batteryPayloadJson());
        Row row2 = telemetryRow("SEQ-002", LocalDateTime.parse("2026-05-10T10:05:00"), "SOLAR", "SOL-02", "GRID-B", solarPayloadJson());
        Row row3 = telemetryRow("SEQ-003", LocalDateTime.parse("2026-05-10T10:10:00"), "EV_CHARGER", "EV-03", "GRID-C", evPayloadJson());
        stubQuery("SELECT * FROM telemetry", rowSetOfRows(row1, row2, row3));

        given()
            .when().get("/Telemetry")
            .then()
            .statusCode(200)
            .body("$", hasSize(3))
            .body("[0].seqKey", equalTo("SEQ-001"))
            .body("[0].asset_type", equalTo("BATTERY"))
            .body("[1].asset_type", equalTo("SOLAR"))
            .body("[2].asset_type", equalTo("EV_CHARGER"));
    }

    @Test
    public void testGetSingleReturnsTelemetry() {
        Row row = telemetryRow("SEQ-010", LocalDateTime.parse("2026-05-10T11:00:00"), "BATTERY", "BAT-10", "GRID-Z", batteryPayloadJson());
        stubPreparedQuery("SELECT * FROM telemetry WHERE id = ?", rowSetOfRows(row));

        given()
            .when().get("/Telemetry/10")
            .then()
            .statusCode(200)
            .body("seqKey", equalTo("SEQ-010"))
            .body("asset_type", equalTo("BATTERY"))
            .body("asset_id", equalTo("BAT-10"))
            .body("grid_cell_id", equalTo("GRID-Z"))
            .body("payload.energy_available_kwh", equalTo(12.5f))
            .body("payload.connection_status", equalTo("ONLINE"));
    }

    @Test
    public void testGetSingleReturnsNotFound() {
        stubPreparedQuery("SELECT * FROM telemetry WHERE id = ?", rowSetOfRows());

        given()
            .when().get("/Telemetry/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testGetByAssetIdReturnsNotFoundWhenNoMatch() {
        stubPreparedQuery("SELECT * FROM telemetry WHERE asset_id = ? ORDER BY timestamp DESC LIMIT 1", rowSetOfRows());

        given()
            .when().get("/Telemetry/asset/UNKNOWN-ASSET")
            .then()
            .statusCode(204);
    }

    @Test
    public void testGetByAssetIdReturnsOnlyMatchingTelemetry() {
        Row row = telemetryRow("SEQ-020", LocalDateTime.parse("2026-05-10T12:00:00"), "SOLAR", "ASSET-77", "GRID-Q", solarPayloadJson());
        stubPreparedQuery("SELECT * FROM telemetry WHERE asset_id = ? ORDER BY timestamp DESC LIMIT 1", rowSetOfRows(row));

        given()
            .when().get("/Telemetry/asset/ASSET-77")
            .then()
            .statusCode(200)
            .body("asset_id", equalTo("ASSET-77"))
            .body("payload.generation_kw", equalTo(9.8f));
    }

    @Test
    public void testGetByGridZoneIdReturnsEmptyListWhenNoMatch() {
        RowSet<Row> emptyRows = rowSetOfRows();
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(emptyRows));
        Mockito.when(mysqlPool.preparedQuery(Mockito.anyString())).thenReturn(preparedQuery);

        given()
            .when().get("/Telemetry/grid-zone/GRID-UNKNOWN")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    public void testGetByGridZoneIdReturnsOnlyMatchingTelemetry() {
        Row row = telemetryRow("SEQ-030", LocalDateTime.parse("2026-05-10T13:00:00"), "EV_CHARGER", "ASSET-88", "GRID-R", evPayloadJson());
        RowSet<Row> rows = rowSetOfRows(row);
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rows));
        Mockito.when(mysqlPool.preparedQuery(Mockito.anyString())).thenReturn(preparedQuery);

        given()
            .when().get("/Telemetry/grid-zone/GRID-R")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].grid_cell_id", equalTo("GRID-R"))
            .body("[0].payload.connector_status", equalTo("CONNECTED"));
    }

    @Test
    public void testGetLatestByAssetsReturnsList() {
        Row row1 = telemetryRow("SEQ-040", LocalDateTime.parse("2026-05-10T14:00:00"), "BATTERY", "BATT-001", "GRID-A", batteryPayloadJson());
        Row row2 = telemetryRow("SEQ-041", LocalDateTime.parse("2026-05-10T14:05:00"), "SOLAR", "SOL-001", "GRID-B", solarPayloadJson());
        RowSet<Row> rows = rowSetOfRows(row1, row2);
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rows));
        Mockito.when(mysqlPool.preparedQuery(Mockito.anyString())).thenReturn(preparedQuery);

        given().contentType("application/json").body("[\"BATT-001\",\"SOL-001\"]")
            .when().post("/Telemetry/assets")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].seqKey", equalTo("SEQ-040"));
    }

    @Test
    public void testGetLatestByAssetsReturnsEmptyForEmptyInput() {
        given().contentType("application/json").body("[]")
            .when().post("/Telemetry/assets")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    public void testGetLatestByGridZonesReturnsList() {
        Row row = telemetryRow("SEQ-050", LocalDateTime.parse("2026-05-10T14:10:00"), "EV_CHARGER", "EV-001", "GRID-R", evPayloadJson());
        RowSet<Row> rows = rowSetOfRows(row);
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rows));
        Mockito.when(mysqlPool.preparedQuery(Mockito.anyString())).thenReturn(preparedQuery);

        given().contentType("application/json").body("[\"GRID-R\"]")
            .when().post("/Telemetry/grid-zones")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].grid_cell_id", equalTo("GRID-R"))
            .body("[0].payload.connector_status", equalTo("CONNECTED"));
    }

    @Test
    public void testGetLatestByGridZonesReturnsEmptyForEmptyInput() {
        given().contentType("application/json").body("[]")
            .when().post("/Telemetry/grid-zones")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetOfRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(rows)));
        return rowSet;
    }

    private RowIterator<Row> rowIteratorOf(Row... rows) {
        List<Row> list = List.of(rows);
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