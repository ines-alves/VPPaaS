package org.acme;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;

import org.acme.model.ForecastRequest;
import org.acme.model.FlexibilityOfferDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;

@QuarkusTest
public class FlexibilityForecastingResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @InjectMock
    ForecastingRuleEngine ruleEngine;

    @Test
    public void testGetAllForecastsReturnsList() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("FORECAST-1");
        Mockito.when(row.getLong("prosumer_id")).thenReturn(123L);
        Mockito.when(row.getString("asset_id")).thenReturn("BATT-001");
        Mockito.when(row.getString("forecast_type")).thenReturn("HIGH_FLEXIBILITY");
        Mockito.when(row.getDouble("predicted_soc_percent")).thenReturn(85.0);
        Mockito.when(row.getString("recommendation")).thenReturn("Sell energy during peak hours.");
        Mockito.when(row.getDouble("confidence_score")).thenReturn(0.95);
        Mockito.when(row.getLong("timestamp")).thenReturn(1778430000000L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(row)));
        Mockito.when(rowSet.rowCount()).thenReturn(1);
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
            consumer.accept(row);
            return null;
        }).when(rowSet).forEach(Mockito.any());

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(anyString())).thenReturn(query);

        RestAssured.given()
            .when().get("/FlexibilityForecasting")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("FORECAST-1"))
            .body("[0].forecastType", equalTo("HIGH_FLEXIBILITY"));
    }

    @Test
    public void testGetForecastsByProsumerReturnsList() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("FORECAST-2");
        Mockito.when(row.getLong("prosumer_id")).thenReturn(123L);
        Mockito.when(row.getString("asset_id")).thenReturn("BATT-001");
        Mockito.when(row.getString("forecast_type")).thenReturn("LOW_FLEXIBILITY");
        Mockito.when(row.getDouble("predicted_soc_percent")).thenReturn(30.0);
        Mockito.when(row.getString("recommendation")).thenReturn("Battery low, avoid discharge.");
        Mockito.when(row.getDouble("confidence_score")).thenReturn(0.75);
        Mockito.when(row.getLong("timestamp")).thenReturn(1778430000000L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(row)));
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
            consumer.accept(row);
            return null;
        }).when(rowSet).forEach(Mockito.any());

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(preparedQuery);

        RestAssured.given()
            .when().get("/FlexibilityForecasting/123")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("FORECAST-2"))
            .body("[0].prosumerId", equalTo(123));
    }

    @Test
    public void testBuildPromptReturnsOk() {
        Mockito.when(ruleEngine.buildPrompt(any(ForecastRequest.class)))
            .thenReturn("You are an energy forecasting assistant...");

        RestAssured.given()
            .contentType("application/json")
            .body("{\"prosumerId\": 123, \"flexibilityEvent\": {\"assetId\": \"BATT-1\", \"socPercent\": 85.0, \"offerType\": \"SELL_ARBITRAGE\"}}")
            .when().post("/FlexibilityForecasting/buildPrompt")
            .then()
            .statusCode(200);
    }

    @Test
    public void testBuildPromptReturnsBadRequestWhenFlexibilityEventIsNull() {
        RestAssured.given()
            .contentType("application/json")
            .body("{\"prosumerId\": 123, \"flexibilityEvent\": null}")
            .when().post("/FlexibilityForecasting/buildPrompt")
            .then()
            .statusCode(400);
    }

    @Test
    public void testBuildPromptReturnsBadRequestWhenProsumerIdIsNull() {
        RestAssured.given()
            .contentType("application/json")
            .body("{\"prosumerId\": null, \"flexibilityEvent\": {\"assetId\": \"BATT-1\"}}")
            .when().post("/FlexibilityForecasting/buildPrompt")
            .then()
            .statusCode(400);
    }

    @Test
    public void testCreateForecastReturnsCreated() {
        FlexibilityForecast forecast = new FlexibilityForecast();
        forecast.id = "FC-1";
        forecast.prosumerId = 123L;
        forecast.assetId = "BATT-1";
        forecast.forecastType = "HIGH_FLEXIBILITY";
        forecast.predictedSocPercent = 85.0;
        forecast.confidenceScore = 0.9;
        forecast.recommendation = "Sell during peak hours.";
        Mockito.when(ruleEngine.createForecastFromOllamaResponse(any()))
            .thenReturn(Uni.createFrom().item(forecast));

        RestAssured.given()
            .contentType("application/json")
            .body("{\"forecast\": {\"prosumer_id\": 123, \"asset_id\": \"BATT-1\", \"forecast_type\": \"HIGH_FLEXIBILITY\", \"predicted_soc_percent\": 85.0, \"recommendation\": \"Sell during peak hours.\", \"confidence_score\": 0.9}}")
            .when().post("/FlexibilityForecasting/createForecast")
            .then()
            .statusCode(201)
            .body("id", equalTo("FC-1"))
            .body("forecastType", equalTo("HIGH_FLEXIBILITY"));
    }

    private static io.vertx.sqlclient.RowIterator<Row> rowIteratorOf(Row... rows) {
        java.util.Iterator<Row> iterator = List.of(rows).iterator();
        return new RowIterator<Row>() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public Row next() { return iterator.next(); }
        };
    }
}
