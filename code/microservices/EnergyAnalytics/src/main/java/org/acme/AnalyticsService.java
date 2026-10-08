package org.acme;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.model.BatteryPayload;
import org.acme.model.ConsumedByProsumerAnalyticsRequest;
import org.acme.model.DischargedByZoneAnalyticsRequest;
import org.acme.model.EvChargerPayload;
import org.acme.model.GeneratedByProsumerAnalyticsRequest;
import org.acme.model.SoCAnalyticsRequest;
import org.acme.model.SolarPayload;
import org.acme.model.TelemetryDto;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AnalyticsService {

    @Inject
    MySQLPool client;

    @Inject
    AnalyticsKafkaProducer kafkaProducer;

    public Uni<Void> calculateSoCAnalytics(SoCAnalyticsRequest request) {
        if (request == null || request.telemetry == null || request.telemetry.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        double sum = 0.0;
        int count = 0;
        for (TelemetryDto telemetry : request.telemetry) {
            if (telemetry != null && telemetry.payload instanceof BatteryPayload) {
                BatteryPayload payload = (BatteryPayload) telemetry.payload;
                if (payload.connectionStatus != null && !"ONLINE".equalsIgnoreCase(payload.connectionStatus)) {
                    continue;
                }
                Double soc = payload.socPercent;
                if (soc != null) {
                    sum += soc;
                    count++;
                }
            }
        }

        if (count == 0) {
            return Uni.createFrom().voidItem();
        }

        double avg = sum / count;
        return emitAndSave("Average-SoC", avg, "ALL", "ALL");
    }

    public Uni<Void> calculateDischargedByZone(DischargedByZoneAnalyticsRequest request) {
        if (request == null || request.telemetry == null || request.telemetry.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        Map<String, Double> totals = new HashMap<>();
        for (TelemetryDto telemetry : request.telemetry) {
            if (telemetry == null || !(telemetry.payload instanceof BatteryPayload)) {
                continue;
            }
            BatteryPayload payload = (BatteryPayload) telemetry.payload;
            if (payload.connectionStatus != null && !"ONLINE".equalsIgnoreCase(payload.connectionStatus)) {
                continue;
            }
            if (payload.socPercent == null || payload.socPercent <= 0 || payload.energyAvailableKwh == null) {
                continue;
            }
            if (telemetry.gridCellId == null) {
                continue;
            }

            double totalEnergy = payload.energyAvailableKwh / (payload.socPercent / 100.0);
            double discharged = totalEnergy - payload.energyAvailableKwh;

            if (payload.activePowerKw != null && payload.activePowerKw > 0) {
                double dischargeCap = payload.activePowerKw;
                if (payload.maxDischargePowerKw != null) {
                    dischargeCap = Math.min(dischargeCap, payload.maxDischargePowerKw);
                }
                discharged = Math.min(discharged, dischargeCap);
            }
            totals.merge(telemetry.gridCellId, discharged, Double::sum);
        }

        if (totals.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<Uni<Void>> unis = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            unis.add(emitAndSave("Energy-Discharged-by-Zone", entry.getValue(), entry.getKey(), "ALL"));
        }

        return Uni.combine().all().unis(unis).discardItems();
    }

    public Uni<Void> calculateConsumedByProsumer(ConsumedByProsumerAnalyticsRequest request) {
        if (request == null || request.telemetry == null || request.telemetry.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        double sum = 0.0;
        for (TelemetryDto telemetry : request.telemetry) {
            if (telemetry != null && telemetry.payload instanceof EvChargerPayload) {
                Double val = ((EvChargerPayload) telemetry.payload).sessionEnergyKwh;
                if (val != null) {
                    sum += val;
                }
            }
        }

        if (sum == 0.0) {
            return Uni.createFrom().voidItem();
        }

        return emitAndSave("Consumed-Energy-by-Prosumer", sum, "ALL", String.valueOf(request.prosumerId));
    }

    public Uni<Void> calculateGeneratedByProsumer(GeneratedByProsumerAnalyticsRequest request) {
        if (request == null || request.telemetry == null || request.telemetry.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        double sum = 0.0;
        for (TelemetryDto telemetry : request.telemetry) {
            if (telemetry != null && telemetry.payload instanceof SolarPayload) {
                Double val = ((SolarPayload) telemetry.payload).dailyYieldKwh;
                if (val != null) {
                    sum += val;
                }
            }
        }

        if (sum == 0.0) {
            return Uni.createFrom().voidItem();
        }

        return emitAndSave("Generated-Energy-by-Prosumer", sum, "ALL", String.valueOf(request.prosumerId));
    }

    private Uni<Void> emitAndSave(String metricName, Double value, String zoneId, String prosumerId) {
        AnalyticsRecord rec = new AnalyticsRecord();
        rec.metricName = metricName;
        rec.metricValue = value;
        rec.zoneId = zoneId;
        rec.prosumerId = prosumerId;
        rec.timestamp = LocalDateTime.now();
        kafkaProducer.sendAnalyticsToDashboard(rec);
        return rec.save(client).replaceWithVoid();
    }

    public Multi<AnalyticsRecord> getAllAnalytics() {
        return AnalyticsRecord.findAll(client);
    }
}