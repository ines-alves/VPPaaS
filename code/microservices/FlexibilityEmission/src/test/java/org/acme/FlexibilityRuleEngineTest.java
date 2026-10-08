package org.acme;

import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.List;

import org.acme.model.BatteryPayload;
import org.acme.model.FlexibilityEmissionRequest;
import org.acme.model.TelemetryDto;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Assumptions;
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

class FlexibilityRuleEngineTest {

    private FlexibilityRuleEngine engine;
    private MySQLPool client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        engine = new FlexibilityRuleEngine();
        client = Mockito.mock(MySQLPool.class);
        injectField(engine, "client", client);
        injectField(engine, "offerEmitter", Mockito.mock(org.eclipse.microprofile.reactive.messaging.Emitter.class));
    }

    // processFlexibilityRequest

    @Test
    void processFlexibilityRequest_returnsUnavailableWhenBatteryOffline() {
        FlexibilityOffer result = engine.processFlexibilityRequest(request(85.0, "OFFLINE", 90.0));
        assertThat(result, notNullValue());
        assertThat(result.offerType, equalTo("UNAVAILABLE"));
        assertThat(result.prosumerId, equalTo(123L));
        assertThat(result.assetId, equalTo("BATT-001"));
    }

    @Test
    void processFlexibilityRequest_returnsUnavailableWhenConnectionStatusNotOnline() {
        FlexibilityOffer result = engine.processFlexibilityRequest(request(50.0, "MAINTENANCE", null));
        assertThat(result, notNullValue());
        assertThat(result.offerType, equalTo("UNAVAILABLE"));
    }

    @Test
    void processFlexibilityRequest_returnsExcludeAggressiveTradingWhenLowSoH() {
        FlexibilityOffer result = engine.processFlexibilityRequest(request(85.0, null, 65.0));
        assertThat(result, notNullValue());
        assertThat(result.offerType, equalTo("EXCLUDE_AGGRESSIVE_TRADING"));
        assertThat(result.socPercent, equalTo(85.0));
    }

    @Test
    void processFlexibilityRequest_returnsUnavailableWhenLowSoC() {
        FlexibilityOffer result = engine.processFlexibilityRequest(request(15.0, null, 80.0));
        assertThat(result, notNullValue());
        assertThat(result.offerType, equalTo("UNAVAILABLE"));
        assertThat(result.socPercent, equalTo(15.0));
    }

    @Test
    void processFlexibilityRequest_returnsNullWhenMiddleRangeSoC() {
        FlexibilityOffer result = engine.processFlexibilityRequest(request(50.0, null, 80.0));
        assertThat(result, nullValue());
    }

    @Test
    void processFlexibilityRequest_returnsSellArbitrageWhenHighSocDuringPeakHour() {
        LocalTime now = LocalTime.now();
        Assumptions.assumeTrue(
            now.isAfter(LocalTime.of(10, 0)) && now.isBefore(LocalTime.of(17, 0)),
            "Skipped: not peak hours (10:00-17:00)"
        );
        FlexibilityOffer result = engine.processFlexibilityRequest(request(95.0, null, 90.0));
        assertThat(result, notNullValue());
        assertThat(result.offerType, equalTo("SELL_ARBITRAGE"));
        assertThat(result.socPercent, equalTo(95.0));
    }

    @Test
    void processFlexibilityRequest_returnsNullWhenHighSocOutsidePeakHour() {
        LocalTime now = LocalTime.now();
        Assumptions.assumeFalse(
            now.isAfter(LocalTime.of(10, 0)) && now.isBefore(LocalTime.of(17, 0)),
            "Skipped: currently peak hours"
        );
        FlexibilityOffer result = engine.processFlexibilityRequest(request(95.0, null, 90.0));
        assertThat(result, nullValue());
    }

    // emitOffer

    @Test
    void emitOffer_returnsNullWhenInputIsNull() {
        List<FlexibilityOffer> result = engine.emitOffer(null).await().indefinitely();
        assertThat(result, nullValue());
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void emitOffer_returnsEmptyListWhenInputIsEmpty() {
        List<FlexibilityOffer> result = engine.emitOffer(List.of()).await().indefinitely();
        assertThat(result, notNullValue());
        assertThat(result, hasSize(0));
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void emitOffer_savesAndReturnsOffers() {
        stubSaveQuery();

        FlexibilityOffer offer = new FlexibilityOffer();
        offer.id = "OFFER-1";
        offer.prosumerId = 123L;
        offer.assetId = "BATT-001";
        offer.socPercent = 85.0;
        offer.offerType = "SELL_ARBITRAGE";

        List<FlexibilityOffer> result = engine.emitOffer(List.of(offer)).await().indefinitely();

        assertThat(result, notNullValue());
        assertThat(result, hasSize(1));
        assertThat(result.get(0).id, equalTo("OFFER-1"));
        assertThat(result.get(0).offerType, equalTo("SELL_ARBITRAGE"));
    }

    // FlexibilityOffer model

    @Test
    void offerSave_setsIdAndTimestampBeforePersisting() {
        stubSaveQuery();

        FlexibilityOffer offer = new FlexibilityOffer();
        offer.prosumerId = 123L;
        offer.assetId = "BATT-001";
        offer.socPercent = 85.0;
        offer.offerType = "SELL_ARBITRAGE";

        Boolean saved = offer.save(client).await().indefinitely();

        assertThat(saved, equalTo(true));
        assertThat(offer.id, notNullValue());
        assertThat(offer.timestamp, notNullValue());
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

    private FlexibilityEmissionRequest request(Double socPercent, String connectionStatus, Double sohPercent) {
        BatteryPayload battery = new BatteryPayload();
        battery.socPercent = socPercent;
        battery.connectionStatus = connectionStatus;
        battery.sohPercent = sohPercent;

        TelemetryDto telemetry = new TelemetryDto();
        telemetry.assetId = "BATT-001";
        telemetry.payload = battery;

        FlexibilityEmissionRequest req = new FlexibilityEmissionRequest();
        req.prosumerId = 123L;
        req.telemetry = telemetry;
        return req;
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
