package org.acme;

import java.lang.reflect.Field;
import java.util.List;

import org.acme.model.BatteryPayload;
import org.acme.model.ConsumedByProsumerAnalyticsRequest;
import org.acme.model.DischargedByZoneAnalyticsRequest;
import org.acme.model.EvChargerPayload;
import org.acme.model.GeneratedByProsumerAnalyticsRequest;
import org.acme.model.Payload;
import org.acme.model.SoCAnalyticsRequest;
import org.acme.model.SolarPayload;
import org.acme.model.TelemetryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class AnalyticsServiceTest {

    private AnalyticsService service;
    private MySQLPool client;
    private AnalyticsKafkaProducer kafkaProducer;

    @BeforeEach
    void setup() {
        service = new AnalyticsService();
        client = Mockito.mock(MySQLPool.class);
        kafkaProducer = Mockito.mock(AnalyticsKafkaProducer.class);
        injectField(service, "client", client);
        injectField(service, "kafkaProducer", kafkaProducer);
    }

    // calculateSoCAnalytics

    @Test
    void calculateSoCAnalytics_nullRequest_noSideEffects() {
        service.calculateSoCAnalytics(null).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateSoCAnalytics_emptyTelemetry_noSideEffects() {
        SoCAnalyticsRequest request = new SoCAnalyticsRequest();
        request.telemetry = List.of();
        service.calculateSoCAnalytics(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateSoCAnalytics_offlineBatterySkipped_noSideEffects() {
        BatteryPayload payload = new BatteryPayload();
        payload.socPercent = 80.0;
        payload.connectionStatus = "OFFLINE";
        SoCAnalyticsRequest request = new SoCAnalyticsRequest();
        request.telemetry = List.of(batteryTelemetry(payload));
        service.calculateSoCAnalytics(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateSoCAnalytics_validBatteries_emitsAverage() {
        stubSaveQuery();
        BatteryPayload p1 = new BatteryPayload();
        p1.socPercent = 60.0;
        BatteryPayload p2 = new BatteryPayload();
        p2.socPercent = 80.0;
        SoCAnalyticsRequest request = new SoCAnalyticsRequest();
        request.telemetry = List.of(batteryTelemetry(p1), batteryTelemetry(p2));

        service.calculateSoCAnalytics(request).await().indefinitely();

        Mockito.verify(kafkaProducer).sendAnalyticsToDashboard(
            ArgumentMatchers.argThat(r -> "Average-SoC".equals(r.metricName) && r.metricValue == 70.0));
    }

    // calculateDischargedByZone

    @Test
    void calculateDischargedByZone_nullRequest_noSideEffects() {
        service.calculateDischargedByZone(null).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateDischargedByZone_batteryWithNullSoc_isSkipped() {
        BatteryPayload payload = new BatteryPayload();
        payload.socPercent = null;
        payload.energyAvailableKwh = 5.0;
        TelemetryDto t = batteryTelemetry(payload);
        t.gridCellId = "ZONE-1";
        DischargedByZoneAnalyticsRequest request = new DischargedByZoneAnalyticsRequest();
        request.telemetry = List.of(t);
        service.calculateDischargedByZone(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateDischargedByZone_batteryWithNullGridCell_isSkipped() {
        BatteryPayload payload = new BatteryPayload();
        payload.socPercent = 50.0;
        payload.energyAvailableKwh = 5.0;
        DischargedByZoneAnalyticsRequest request = new DischargedByZoneAnalyticsRequest();
        request.telemetry = List.of(batteryTelemetry(payload));
        service.calculateDischargedByZone(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateDischargedByZone_validBattery_emitsDischarged() {
        stubSaveQuery();
        BatteryPayload payload = new BatteryPayload();
        payload.socPercent = 50.0;
        payload.energyAvailableKwh = 5.0;
        TelemetryDto t = batteryTelemetry(payload);
        t.gridCellId = "ZONE-1";
        DischargedByZoneAnalyticsRequest request = new DischargedByZoneAnalyticsRequest();
        request.telemetry = List.of(t);

        service.calculateDischargedByZone(request).await().indefinitely();

        Mockito.verify(kafkaProducer).sendAnalyticsToDashboard(
            ArgumentMatchers.argThat(r -> "Energy-Discharged-by-Zone".equals(r.metricName) && "ZONE-1".equals(r.zoneId)));
    }

    // calculateConsumedByProsumer

    @Test
    void calculateConsumedByProsumer_emptyTelemetry_noSideEffects() {
        ConsumedByProsumerAnalyticsRequest request = new ConsumedByProsumerAnalyticsRequest();
        request.prosumerId = 1L;
        request.telemetry = List.of();
        service.calculateConsumedByProsumer(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateConsumedByProsumer_nullSessionEnergy_noSideEffects() {
        EvChargerPayload payload = new EvChargerPayload();
        payload.sessionEnergyKwh = null;
        ConsumedByProsumerAnalyticsRequest request = new ConsumedByProsumerAnalyticsRequest();
        request.prosumerId = 1L;
        request.telemetry = List.of(telemetryWith("EV_CHARGER", payload));
        service.calculateConsumedByProsumer(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateConsumedByProsumer_validEVCharger_emitsConsumed() {
        stubSaveQuery();
        EvChargerPayload payload = new EvChargerPayload();
        payload.sessionEnergyKwh = 5.0;
        ConsumedByProsumerAnalyticsRequest request = new ConsumedByProsumerAnalyticsRequest();
        request.prosumerId = 42L;
        request.telemetry = List.of(telemetryWith("EV_CHARGER", payload));

        service.calculateConsumedByProsumer(request).await().indefinitely();

        Mockito.verify(kafkaProducer).sendAnalyticsToDashboard(
            ArgumentMatchers.argThat(r -> "Consumed-Energy-by-Prosumer".equals(r.metricName) && "42".equals(r.prosumerId)));
    }

    // calculateGeneratedByProsumer

    @Test
    void calculateGeneratedByProsumer_emptyTelemetry_noSideEffects() {
        GeneratedByProsumerAnalyticsRequest request = new GeneratedByProsumerAnalyticsRequest();
        request.prosumerId = 1L;
        request.telemetry = List.of();
        service.calculateGeneratedByProsumer(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateGeneratedByProsumer_nullDailyYield_noSideEffects() {
        SolarPayload payload = new SolarPayload();
        payload.dailyYieldKwh = null;
        GeneratedByProsumerAnalyticsRequest request = new GeneratedByProsumerAnalyticsRequest();
        request.prosumerId = 1L;
        request.telemetry = List.of(telemetryWith("SOLAR", payload));
        service.calculateGeneratedByProsumer(request).await().indefinitely();
        Mockito.verifyNoInteractions(client, kafkaProducer);
    }

    @Test
    void calculateGeneratedByProsumer_validSolar_emitsGenerated() {
        stubSaveQuery();
        SolarPayload payload = new SolarPayload();
        payload.dailyYieldKwh = 15.0;
        GeneratedByProsumerAnalyticsRequest request = new GeneratedByProsumerAnalyticsRequest();
        request.prosumerId = 7L;
        request.telemetry = List.of(telemetryWith("SOLAR", payload));

        service.calculateGeneratedByProsumer(request).await().indefinitely();

        Mockito.verify(kafkaProducer).sendAnalyticsToDashboard(
            ArgumentMatchers.argThat(r -> "Generated-Energy-by-Prosumer".equals(r.metricName) && "7".equals(r.prosumerId)));
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

    private TelemetryDto batteryTelemetry(BatteryPayload payload) {
        TelemetryDto t = new TelemetryDto();
        t.assetType = "BATTERY";
        t.payload = payload;
        return t;
    }

    private TelemetryDto telemetryWith(String assetType, Payload payload) {
        TelemetryDto t = new TelemetryDto();
        t.assetType = assetType;
        t.payload = payload;
        return t;
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Injection failed for " + fieldName, e);
        }
    }
}
