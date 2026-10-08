package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;

import org.acme.model.GridBalancingRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;

@QuarkusTest
class GridBalancingResourceTest {

    @InjectMock
    MySQLPool client;

    @InjectMock
    GridBalancingRuleEngine ruleEngine;

    @Test
    void analyzeBalancingRequest_returnsTrue() {
        Mockito.when(ruleEngine.processBalancingRequest(any(GridBalancingRequest.class)))
            .thenReturn(Uni.createFrom().item(true));

        given()
            .contentType("application/json")
            .body(analyzeRequestJson())
            .when().post("/GridBalancing/analyze")
            .then()
            .statusCode(200);
    }

    @Test
    void analyzeBalancingRequest_returnsFalseWhenNoOverload() {
        Mockito.when(ruleEngine.processBalancingRequest(any(GridBalancingRequest.class)))
            .thenReturn(Uni.createFrom().item(false));

        given()
            .contentType("application/json")
            .body(analyzeRequestJson())
            .when().post("/GridBalancing/analyze")
            .then()
            .statusCode(200);
    }

    @Test
    void getAllRecommendations_returnsRecommendationList() {
        Row row1 = recommendationRow("REC-001", "GRID-A", 18.5, 10.0, "Grid cell GRID-A exceeded capacity: total measured 18.5 kW > max 10.0 kW", 1715342400000L);
        Row row2 = recommendationRow("REC-002", "GRID-B", 11.0, 8.0, "Grid cell GRID-B exceeded capacity: total measured 11.0 kW > max 8.0 kW", 1715346000000L);
        stubQuery("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations ORDER BY timestamp DESC", rowSetOfRows(row1, row2));

        given()
            .when().get("/GridBalancing")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].id", equalTo("REC-001"))
            .body("[0].gridCellId", equalTo("GRID-A"))
            .body("[1].gridCellId", equalTo("GRID-B"));
    }

    @Test
    void getRecommendationById_returnsSingleRecommendation() {
        Row row = recommendationRow("REC-010", "GRID-Z", 22.0, 15.0, "Grid cell GRID-Z exceeded capacity: total measured 22.0 kW > max 15.0 kW", 1715349600000L);
        stubPreparedQuery("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations WHERE id = ?", rowSetOfRows(row));

        given()
            .when().get("/GridBalancing/REC-010")
            .then()
            .statusCode(200)
            .body("id", equalTo("REC-010"))
            .body("gridCellId", equalTo("GRID-Z"))
            .body("measuredKw", equalTo(22.0f))
            .body("maxCapacityKw", equalTo(15.0f));
    }

    @Test
    void getRecommendationById_returnsNotFound() {
        stubPreparedQuery("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations WHERE id = ?", rowSetOfRows());

        given()
            .when().get("/GridBalancing/DOES-NOT-EXIST")
            .then()
            .statusCode(404);
    }

    @Test
    void getRecommendationsByGridCell_returnsFilteredList() {
        Row row = recommendationRow("REC-020", "GRID-Q", 16.5, 12.0, "Grid cell GRID-Q exceeded capacity: total measured 16.5 kW > max 12.0 kW", 1715353200000L);
        stubPreparedQuery("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations WHERE grid_cell_id = ? ORDER BY timestamp DESC", rowSetOfRows(row));

        given()
            .when().get("/GridBalancing/gridcell/GRID-Q")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].gridCellId", equalTo("GRID-Q"))
            .body("[0].recommendation", equalTo("Grid cell GRID-Q exceeded capacity: total measured 16.5 kW > max 12.0 kW"));
    }

    @Test
    void createRecommendation_returnsOkWithRecommendation() {
        GridBalancingRecommendation recommendation = new GridBalancingRecommendation();
        recommendation.gridCellId = "GRID-A";
        recommendation.measuredKw = 18.5;
        recommendation.maxCapacityKw = 10.0;
        recommendation.recommendation = "Grid cell GRID-A exceeded capacity. Recommended to balance with zone: GRID-B which currently has a deficit.";
        Mockito.when(ruleEngine.evaluateNeighboringZones(any())).thenReturn(recommendation);

        given()
            .contentType("application/json")
            .body(neighboringZonesJson())
            .when().post("/GridBalancing/recommendation")
            .then()
            .statusCode(200)
            .body("gridCellId", equalTo("GRID-A"))
            .body("measuredKw", equalTo(18.5f));
    }

    @Test
    void createRecommendation_returnsBadRequestWhenSelectedCellIsNull() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "selectedGridCell": null,
                  "telemetry": [],
                  "gridCells": []
                }
                """)
            .when().post("/GridBalancing/recommendation")
            .then()
            .statusCode(400);
    }

    @Test
    void createRecommendation_returnsBadRequestWhenRuleEngineReturnsNull() {
        Mockito.when(ruleEngine.evaluateNeighboringZones(any())).thenReturn(null);

        given()
            .contentType("application/json")
            .body(neighboringZonesJson())
            .when().post("/GridBalancing/recommendation")
            .then()
            .statusCode(400);
    }

    @Test
    void publishRecommendation_returnsAccepted() {
        GridBalancingRecommendation saved = new GridBalancingRecommendation();
        saved.id = "REC-999";
        saved.gridCellId = "GRID-A";
        saved.measuredKw = 18.5;
        saved.maxCapacityKw = 10.0;
        saved.recommendation = "Grid cell GRID-A exceeded capacity";
        Mockito.when(ruleEngine.publishRecommendation(any()))
            .thenReturn(Uni.createFrom().item(saved));

        given()
            .contentType("application/json")
            .body("""
                {
                  "gridCellId": "GRID-A",
                  "measuredKw": 18.5,
                  "maxCapacityKw": 10.0,
                  "recommendation": "Grid cell GRID-A exceeded capacity"
                }
                """)
            .when().post("/GridBalancing/recommendation/publish")
            .then()
            .statusCode(202)
            .body("id", equalTo("REC-999"))
            .body("gridCellId", equalTo("GRID-A"));
    }

    @Test
    void publishRecommendation_returnsBadRequestWhenSaveReturnsNull() {
        Mockito.when(ruleEngine.publishRecommendation(any()))
            .thenReturn(Uni.createFrom().nullItem());

        given()
            .contentType("application/json")
            .body("""
                {
                  "gridCellId": "GRID-A",
                  "measuredKw": 18.5,
                  "maxCapacityKw": 10.0,
                  "recommendation": "Grid cell GRID-A exceeded capacity"
                }
                """)
            .when().post("/GridBalancing/recommendation/publish")
            .then()
            .statusCode(400);
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
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

    private Row recommendationRow(String id, String gridCellId, Double measuredKw, Double maxCapacityKw, String recommendation, Long timestamp) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn(id);
        Mockito.when(row.getString("grid_cell_id")).thenReturn(gridCellId);
        Mockito.when(row.getDouble("measured_kw")).thenReturn(measuredKw);
        Mockito.when(row.getDouble("max_capacity_kw")).thenReturn(maxCapacityKw);
        Mockito.when(row.getString("recommendation")).thenReturn(recommendation);
        Mockito.when(row.getLong("timestamp")).thenReturn(timestamp);
        return row;
    }

    private String analyzeRequestJson() {
        return """
            {
              "telemetry": [
                {
                  "seqKey": "SEQ-001",
                  "timestamp": "2026-05-10T10:00:00",
                  "asset_type": "SOLAR",
                  "asset_id": "SOL-001",
                  "grid_cell_id": "GRID-A",
                  "payload": {
                    "ac_voltage_v": 230.0,
                    "generation_kw": 18.5,
                    "grid_frequency_hz": 50.0,
                    "daily_yield_kwh": 21.4
                  }
                }
              ],
              "gridCell": {
                "id": "GRID-A",
                "utilityOperatorId": 1,
                "address": "Rua Central",
                "postalCode": "1000-000",
                "maxCapacityKw": 10.0
              }
            }
            """;
    }

    private String neighboringZonesJson() {
        return """
            {
              "selectedGridCell": {
                "id": "GRID-A",
                "maxCapacityKw": 10.0
              },
              "telemetry": [
                {
                  "seqKey": "SEQ-001",
                  "timestamp": "2026-05-10T10:00:00",
                  "asset_type": "SOLAR",
                  "asset_id": "SOL-001",
                  "grid_cell_id": "GRID-A",
                  "payload": {
                    "ac_voltage_v": 230.0,
                    "generation_kw": 18.5,
                    "grid_frequency_hz": 50.0,
                    "daily_yield_kwh": 21.4
                  }
                }
              ],
              "gridCells": [
                { "id": "GRID-A", "maxCapacityKw": 10.0 },
                { "id": "GRID-B", "maxCapacityKw": 12.0 }
              ]
            }
            """;
    }
}
