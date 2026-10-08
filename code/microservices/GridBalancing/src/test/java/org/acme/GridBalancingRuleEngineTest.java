package org.acme;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import org.acme.model.BatteryPayload;
import org.acme.model.GridBalancingRequest;
import org.acme.model.GridCellDto;
import org.acme.model.NeighboringZonesDto;
import org.acme.model.Payload;
import org.acme.model.SolarPayload;
import org.acme.model.TelemetryDto;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class GridBalancingRuleEngineTest {

    private GridBalancingRuleEngine engine;
    private MySQLPool client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        engine = new GridBalancingRuleEngine();
        client = Mockito.mock(MySQLPool.class);
        injectField(engine, "client", client);
        injectField(engine, "recommendationEmitter", Mockito.mock(org.eclipse.microprofile.reactive.messaging.Emitter.class));
    }

    // processBalancingRequest

    @Test
    void processBalancingRequest_returnsFalseWhenTelemetryOrGridCellIsNull() {
        GridBalancingRequest request = new GridBalancingRequest();
        assertThat(engine.processBalancingRequest(request).await().indefinitely(), equalTo(false));
    }

    @Test
    void processBalancingRequest_returnsFalseWhenGridCellIsNull() {
        GridBalancingRequest request = new GridBalancingRequest();
        request.telemetry = List.of();
        request.gridCell = null;
        assertThat(engine.processBalancingRequest(request).await().indefinitely(), equalTo(false));
    }

    @Test
    void processBalancingRequest_returnsFalseWhenNoOverloadDetected() {
        GridBalancingRequest request = new GridBalancingRequest();
        request.telemetry = List.of(telemetry("GRID-A", 4.0, "SOLAR"));
        request.gridCell = gridCell("GRID-A", 10.0);
        assertThat(engine.processBalancingRequest(request).await().indefinitely(), equalTo(false));
    }

    @Test
    void processBalancingRequest_returnsTrueWhenOverloadDetected() {
        GridBalancingRequest request = new GridBalancingRequest();
        request.telemetry = List.of(telemetry("GRID-A", 18.5, "BATTERY"));
        request.gridCell = gridCell("GRID-A", 10.0);
        assertThat(engine.processBalancingRequest(request).await().indefinitely(), equalTo(true));
    }

    // evaluateNeighboringZones

    @Test
    void evaluateNeighboringZones_returnsNullWhenInputIsNull() {
        assertThat(engine.evaluateNeighboringZones(null), equalTo(null));
    }

    @Test
    void evaluateNeighboringZones_returnsNullWhenSelectedCellIsNull() {
        NeighboringZonesDto dto = new NeighboringZonesDto();
        dto.selectedGridCell = null;
        assertThat(engine.evaluateNeighboringZones(dto), equalTo(null));
    }

    @Test
    void evaluateNeighboringZones_returnsRecommendationWithDeficitZone() {
        NeighboringZonesDto dto = new NeighboringZonesDto();
        dto.selectedGridCell = gridCell("GRID-A", 10.0);
        dto.telemetry = List.of(
            telemetry("GRID-A", 18.5, "BATTERY"),
            telemetry("GRID-B", 3.0, "BATTERY")
        );
        dto.gridCells = List.of(gridCell("GRID-A", 10.0), gridCell("GRID-B", 12.0));

        GridBalancingRecommendation result = engine.evaluateNeighboringZones(dto);

        assertThat(result, notNullValue());
        assertThat(result.gridCellId, equalTo("GRID-A"));
        assertThat(result.measuredKw, equalTo(18.5));
        assertThat(result.recommendation, containsString("GRID-B"));
    }

    @Test
    void evaluateNeighboringZones_returnsRecommendationWithNoDeficitZone() {
        NeighboringZonesDto dto = new NeighboringZonesDto();
        dto.selectedGridCell = gridCell("GRID-A", 10.0);
        dto.telemetry = List.of(
            telemetry("GRID-A", 18.5, "BATTERY"),
            telemetry("GRID-B", 15.0, "BATTERY")
        );
        dto.gridCells = List.of(gridCell("GRID-A", 10.0), gridCell("GRID-B", 12.0));

        GridBalancingRecommendation result = engine.evaluateNeighboringZones(dto);

        assertThat(result, notNullValue());
        assertThat(result.gridCellId, equalTo("GRID-A"));
        assertThat(result.recommendation, containsString("No neighboring zones"));
    }

    // publishRecommendation

    @Test
    void publishRecommendation_returnsNullWhenInputIsNull() {
        GridBalancingRecommendation result = engine.publishRecommendation(null).await().indefinitely();
        assertThat(result, equalTo(null));
    }

    @Test
    void publishRecommendation_savesAndReturnsRecommendation() {
        stubSaveQuery();

        GridBalancingRecommendation recommendation = new GridBalancingRecommendation();
        recommendation.gridCellId = "GRID-A";
        recommendation.measuredKw = 18.5;
        recommendation.maxCapacityKw = 10.0;
        recommendation.recommendation = "Grid cell GRID-A exceeded capacity";

        GridBalancingRecommendation result = engine.publishRecommendation(recommendation).await().indefinitely();

        assertThat(result, notNullValue());
        assertThat(result.gridCellId, equalTo("GRID-A"));
        assertThat(result.id, notNullValue());
    }

    // GridBalancingRecommendation model

    @Test
    void recommendationSave_setsIdAndTimestampBeforePersisting() {
        stubSaveQuery();

        GridBalancingRecommendation recommendation = new GridBalancingRecommendation();
        recommendation.gridCellId = "GRID-A";
        recommendation.measuredKw = 18.5;
        recommendation.maxCapacityKw = 10.0;
        recommendation.recommendation = "Grid cell GRID-A exceeded capacity: total measured 18.5 kW > max 10.0 kW";

        Boolean saved = recommendation.save(client).await().indefinitely();

        assertThat(saved, equalTo(true));
        assertThat(recommendation.id, notNullValue());
        assertThat(recommendation.timestamp, notNullValue());
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

    private GridCellDto gridCell(String id, Double maxCapacityKw) {
        GridCellDto cell = new GridCellDto();
        cell.id = id;
        cell.maxCapacityKw = maxCapacityKw;
        return cell;
    }

    private TelemetryDto telemetry(String gridCellId, Double measuredKw, String assetType) {
        TelemetryDto t = new TelemetryDto();
        t.seqKey = "SEQ-" + gridCellId;
        t.timestamp = LocalDateTime.parse("2026-05-10T10:00:00");
        t.assetType = assetType;
        t.assetId = assetType + "-001";
        t.gridCellId = gridCellId;
        t.payload = payloadFor(assetType, measuredKw);
        return t;
    }

    private Payload payloadFor(String assetType, Double measuredKw) {
        if ("BATTERY".equals(assetType)) {
            BatteryPayload payload = new BatteryPayload();
            payload.activePowerKw = measuredKw;
            return payload;
        }
        SolarPayload payload = new SolarPayload();
        payload.generationKw = measuredKw;
        return payload;
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
