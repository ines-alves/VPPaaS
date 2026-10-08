package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;

import org.acme.model.FlexibilityEmissionRequest;
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
public class FlexibilityEmissionResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @InjectMock
    FlexibilityRuleEngine ruleEngine;

    @Test
    public void testGetAllOffersReturnsList() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("OFFER-1");
        Mockito.when(row.getLong("prosumer_id")).thenReturn(123L);
        Mockito.when(row.getString("asset_id")).thenReturn("BATT-001");
        Mockito.when(row.getDouble("soc_percent")).thenReturn(85.0);
        Mockito.when(row.getString("offer_type")).thenReturn("SELL_ARBITRAGE");
        Mockito.when(row.getLong("timestamp")).thenReturn(1778430000000L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(row)));

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(anyString())).thenReturn(query);

        given()
            .when().get("/FlexibilityOffer")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].id", equalTo("OFFER-1"))
            .body("[0].offerType", equalTo("SELL_ARBITRAGE"));
    }

    @Test
    public void testGetSingleOfferReturnsOk() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("OFFER-1");
        Mockito.when(row.getLong("prosumer_id")).thenReturn(123L);
        Mockito.when(row.getString("asset_id")).thenReturn("BATT-001");
        Mockito.when(row.getDouble("soc_percent")).thenReturn(85.0);
        Mockito.when(row.getString("offer_type")).thenReturn("SELL_ARBITRAGE");
        Mockito.when(row.getLong("timestamp")).thenReturn(1778430000000L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(row)));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/FlexibilityOffer/OFFER-1")
            .then()
            .statusCode(200)
            .body("id", equalTo("OFFER-1"));
    }

    @Test
    public void testGetSingleOfferReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf()));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/FlexibilityOffer/UNKNOWN-ID")
            .then()
            .statusCode(404);
    }

    @Test
    public void testGetByProsumerIdReturnsList() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("OFFER-2");
        Mockito.when(row.getLong("prosumer_id")).thenReturn(123L);
        Mockito.when(row.getString("asset_id")).thenReturn("BATT-001");
        Mockito.when(row.getDouble("soc_percent")).thenReturn(75.0);
        Mockito.when(row.getString("offer_type")).thenReturn("UNAVAILABLE");
        Mockito.when(row.getLong("timestamp")).thenReturn(1778430000000L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(row)));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/FlexibilityOffer/prosumer/123")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].id", equalTo("OFFER-2"))
            .body("[0].prosumerId", equalTo(123));
    }

    @Test
    public void testAnalyzeFlexibilityRequestReturnsOk() {
        FlexibilityOffer offer = new FlexibilityOffer();
        offer.id = "OFFER-1";
        offer.prosumerId = 123L;
        offer.assetId = "BATT-1";
        offer.socPercent = 85.0;
        offer.offerType = "SELL_ARBITRAGE";
        Mockito.when(ruleEngine.processFlexibilityRequest(any(FlexibilityEmissionRequest.class)))
            .thenReturn(offer);

        given()
            .contentType("application/json")
            .body("{\"prosumerId\": 123, \"telemetry\": {\"asset_type\": \"BATTERY\", \"asset_id\": \"BATT-1\", \"grid_cell_id\": \"ZONE-1\", \"payload\": {\"soc_percent\": 85.0}}}")
            .when().post("/FlexibilityOffer/analyze")
            .then()
            .statusCode(200)
            .body("offerType", equalTo("SELL_ARBITRAGE"))
            .body("prosumerId", equalTo(123));
    }

    @Test
    public void testAnalyzeFlexibilityRequestReturnsBadRequestWhenNull() {
        given()
            .contentType("application/json")
            .when().post("/FlexibilityOffer/analyze")
            .then()
            .statusCode(400);
    }

    @Test
    public void testAnalyzeFlexibilityRequestReturnsBadRequestWhenOfferIsNull() {
        Mockito.when(ruleEngine.processFlexibilityRequest(any(FlexibilityEmissionRequest.class)))
            .thenReturn(null);

        given()
            .contentType("application/json")
            .body("{\"prosumerId\": 123, \"telemetry\": {\"asset_type\": \"BATTERY\", \"asset_id\": \"BATT-1\", \"grid_cell_id\": \"ZONE-1\", \"payload\": {\"soc_percent\": 45.0}}}")
            .when().post("/FlexibilityOffer/analyze")
            .then()
            .statusCode(400);
    }

    @Test
    public void testPublishOffersReturnsOk() {
        FlexibilityOffer saved = new FlexibilityOffer();
        saved.id = "OFFER-1";
        saved.prosumerId = 123L;
        saved.assetId = "BATT-1";
        saved.socPercent = 85.0;
        saved.offerType = "SELL_ARBITRAGE";
        Mockito.when(ruleEngine.emitOffer(any()))
            .thenReturn(Uni.createFrom().item(List.of(saved)));

        given()
            .contentType("application/json")
            .body("[{\"id\": \"OFFER-1\", \"prosumerId\": 123, \"assetId\": \"BATT-1\", \"socPercent\": 85.0, \"offerType\": \"SELL_ARBITRAGE\"}]")
            .when().post("/FlexibilityOffer/publish")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].id", equalTo("OFFER-1"));
    }

    @Test
    public void testPublishOffersReturnsBadRequestWithInvalidOffer() {
        given()
            .contentType("application/json")
            .body("[{\"assetId\": \"BATT-1\", \"socPercent\": 85.0, \"offerType\": \"SELL_ARBITRAGE\"}]")
            .when().post("/FlexibilityOffer/publish")
            .then()
            .statusCode(400);
    }

    private static io.vertx.sqlclient.RowIterator<Row> rowIteratorOf(Row... rows) {
        java.util.Iterator<Row> iterator = List.of(rows).iterator();
        return new RowIterator<Row>() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public Row next() { return iterator.next(); }
        };
    }
}
