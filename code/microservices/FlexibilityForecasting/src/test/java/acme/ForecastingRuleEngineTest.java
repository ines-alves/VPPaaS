package org.acme;

import java.lang.reflect.Field;

import org.acme.model.BatteryPayload;
import org.acme.model.FlexibilityOfferDto;
import org.acme.model.ForecastRequest;
import org.acme.model.TelemetryDto;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class ForecastingRuleEngineTest {

    private ForecastingRuleEngine engine;
    private MySQLPool client;

    @BeforeEach
    void setup() {
        engine = new ForecastingRuleEngine();
        client = Mockito.mock(MySQLPool.class);
        injectField(engine, "client", client);
        injectField(engine, "objectMapper", new ObjectMapper());
    }

    // buildPrompt

    @Test
    void buildPrompt_returnsPromptContainingProsumerIdAndAssetId() {
        String prompt = engine.buildPrompt(forecastRequest(123L, "BATT-001", 75.0, "SELL_ARBITRAGE", null));
        assertThat(prompt, notNullValue());
        assertThat(prompt, containsString("123"));
        assertThat(prompt, containsString("BATT-001"));
        assertThat(prompt, containsString("SELL_ARBITRAGE"));
    }

    @Test
    void buildPrompt_includesNoTelemetryLabelWhenTelemetryIsNull() {
        String prompt = engine.buildPrompt(forecastRequest(123L, "BATT-001", 75.0, "SELL_ARBITRAGE", null));
        assertThat(prompt, containsString("No telemetry data available."));
    }

    @Test
    void buildPrompt_includesBatteryDetailsInTelemetrySection() {
        BatteryPayload battery = new BatteryPayload();
        battery.socPercent = 80.0;
        battery.maxDischargePowerKw = 5.0;
        battery.connectionStatus = "ONLINE";

        TelemetryDto telemetry = new TelemetryDto();
        telemetry.assetId = "BATT-001";
        telemetry.assetType = "BATTERY";
        telemetry.payload = battery;

        String prompt = engine.buildPrompt(forecastRequest(123L, "BATT-001", 80.0, "SELL_ARBITRAGE", telemetry));
        assertThat(prompt, containsString("80.0%"));
        assertThat(prompt, containsString("5.0 kW"));
        assertThat(prompt, containsString("ONLINE"));
    }

    // createForecastFromOllamaResponse

    @Test
    void createForecastFromOllamaResponse_parsesOllamaResponseWrapper() throws Exception {
        stubSaveQuery();
        JsonNode body = new ObjectMapper().readTree(
            "{\"response\": \"{\\\"forecast_type\\\": \\\"HIGH_FLEXIBILITY\\\", \\\"prosumer_id\\\": 123, " +
            "\\\"asset_id\\\": \\\"BATT-1\\\", \\\"predicted_soc_percent\\\": 85.0, " +
            "\\\"recommendation\\\": \\\"Sell energy.\\\", \\\"confidence_score\\\": 0.9}\"}");

        FlexibilityForecast result = engine.createForecastFromOllamaResponse(body).await().indefinitely();

        assertThat(result, notNullValue());
        assertThat(result.forecastType, equalTo("HIGH_FLEXIBILITY"));
        assertThat(result.prosumerId, equalTo(123L));
        assertThat(result.confidenceScore, equalTo(0.9));
    }

    @Test
    void createForecastFromOllamaResponse_parsesPlainJsonForecast() throws Exception {
        stubSaveQuery();
        JsonNode body = new ObjectMapper().readTree(
            "{\"forecast_type\": \"LOW_FLEXIBILITY\", \"prosumer_id\": 456, \"asset_id\": \"SOLAR-1\", " +
            "\"predicted_soc_percent\": 20.0, \"recommendation\": \"Low generation.\", \"confidence_score\": 0.6}");

        FlexibilityForecast result = engine.createForecastFromOllamaResponse(body).await().indefinitely();

        assertThat(result, notNullValue());
        assertThat(result.forecastType, equalTo("LOW_FLEXIBILITY"));
        assertThat(result.prosumerId, equalTo(456L));
        assertThat(result.recommendation, equalTo("Low generation."));
    }

    @Test
    void createForecastFromOllamaResponse_usesDefaultsOnUnparseableInput() throws Exception {
        stubSaveQuery();
        JsonNode body = new ObjectMapper().readTree("{\"response\": \"this is not valid json\"}");

        FlexibilityForecast result = engine.createForecastFromOllamaResponse(body).await().indefinitely();

        assertThat(result, notNullValue());
        assertThat(result.forecastType, equalTo("LOW_FLEXIBILITY"));
        assertThat(result.recommendation, equalTo("No recommendation available."));
    }

    // FlexibilityForecast model

    @Test
    void forecastSave_setsIdAndTimestampBeforePersisting() {
        stubSaveQuery();

        FlexibilityForecast forecast = new FlexibilityForecast();
        forecast.prosumerId = 123L;
        forecast.assetId = "BATT-001";
        forecast.forecastType = "HIGH_FLEXIBILITY";
        forecast.predictedSocPercent = 85.0;
        forecast.confidenceScore = 0.9;
        forecast.recommendation = "Sell during peak hours.";

        Boolean saved = forecast.save(client).await().indefinitely();

        assertThat(saved, equalTo(true));
        assertThat(forecast.id, notNullValue());
        assertThat(forecast.timestamp, notNullValue());
    }

    // helpers

    @SuppressWarnings("unchecked")
    private void stubSaveQuery() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(anyString())).thenReturn(preparedQuery);
    }

    private ForecastRequest forecastRequest(Long prosumerId, String assetId, double socPercent,
            String offerType, TelemetryDto telemetry) {
        FlexibilityOfferDto event = new FlexibilityOfferDto();
        event.assetId = assetId;
        event.socPercent = socPercent;
        event.offerType = offerType;

        ForecastRequest request = new ForecastRequest();
        request.prosumerId = prosumerId;
        request.flexibilityEvent = event;
        request.telemetry = telemetry;
        return request;
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Injection failed for " + fieldName, e);
        }
    }
}
