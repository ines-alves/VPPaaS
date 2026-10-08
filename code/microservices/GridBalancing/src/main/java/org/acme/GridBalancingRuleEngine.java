package org.acme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.model.BatteryPayload;
import org.acme.model.EvChargerPayload;
import org.acme.model.GridBalancingRequest;
import org.acme.model.GridCellDto;
import org.acme.model.NeighboringZonesDto;
import org.acme.model.Payload;
import org.acme.model.SolarPayload;
import org.acme.model.TelemetryDto;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GridBalancingRuleEngine {

    @Inject
    MySQLPool client;

    @Inject
    @Channel("grid-balancing-recommendation-out")
    Emitter<String> recommendationEmitter;

    public Uni<Boolean> processBalancingRequest(GridBalancingRequest request) {
        if (request.telemetry == null || request.gridCell == null) {
            return Uni.createFrom().item(false);
        }

        // Calculate totals across all the provided telemetry
        Map<String, Double> totalsByGridCell = calculateGridCellTotals(request.telemetry);

        if (request.gridCell.id == null || request.gridCell.maxCapacityKw == null) {
            return Uni.createFrom().item(false);
        }

        Double totalKw = totalsByGridCell.getOrDefault(request.gridCell.id, 0.0);
        boolean exceeded = totalKw > request.gridCell.maxCapacityKw;

        return Uni.createFrom().item(exceeded);
    }

    private Map<String, Double> calculateGridCellTotals(List<TelemetryDto> telemetryList) {
        Map<String, Double> totalsByGridCell = new HashMap<>();
        if (telemetryList == null) return totalsByGridCell;

        for (TelemetryDto telemetry : telemetryList) {
            if (telemetry == null || telemetry.gridCellId == null) {
                continue;
            }

            Double measuredKw = extractMeasuredKw(telemetry.payload);
            if (measuredKw == null) {
                continue;
            }

            totalsByGridCell.merge(telemetry.gridCellId, measuredKw, Double::sum);
        }
        return totalsByGridCell;
    }

    public GridBalancingRecommendation evaluateNeighboringZones(NeighboringZonesDto neighboringZones) {
        if (neighboringZones == null || neighboringZones.selectedGridCell == null || neighboringZones.gridCells == null) {
            return null;
        }

        // Evaluate telemetry to see the current load of our neighboring zones
        Map<String, Double> totals = calculateGridCellTotals(neighboringZones.telemetry);

        GridCellDto analyzedCell = neighboringZones.selectedGridCell;
        if (analyzedCell.id == null || analyzedCell.maxCapacityKw == null) {
            return null;
        }

        Double analyzedTotalKw = Math.round(totals.getOrDefault(analyzedCell.id, 0.0) * 100.0) / 100.0;

        String deficitZoneId = null;
        for (GridCellDto cell : neighboringZones.gridCells) {
            // Ignore the analyzed cell
            if (cell == null || cell.id == null || cell.id.equals(analyzedCell.id) || cell.maxCapacityKw == null) {
                continue;
            }

            // Skip zones with no telemetry — unknown load should not be treated as zero
            if (!totals.containsKey(cell.id)) {
                continue;
            }

            Double totalKw = totals.get(cell.id);

            // If this zone is under maximum capacity, we found a deficit zone
            if (totalKw < cell.maxCapacityKw) {
                deficitZoneId = cell.id;
                break;
            }
        }

        GridBalancingRecommendation recommendation = new GridBalancingRecommendation();
        recommendation.gridCellId = analyzedCell.id;
        recommendation.measuredKw = analyzedTotalKw;
        recommendation.maxCapacityKw = analyzedCell.maxCapacityKw;

        String baseMessage = buildRecommendationMessage(analyzedCell.id, analyzedTotalKw, analyzedCell.maxCapacityKw);

        if (deficitZoneId != null) {
            recommendation.recommendation = baseMessage + ". Recommended to balance with zone: " + deficitZoneId + " which currently has a deficit.";
        } else {
            recommendation.recommendation = baseMessage + ". No neighboring zones with a deficit were found.";
        }

        return recommendation;
    }

    public Uni<GridBalancingRecommendation> publishRecommendation(GridBalancingRecommendation recommendation) {
        if (recommendation == null) {
            return Uni.createFrom().nullItem();
        }

        return recommendation.save(client)
            .onItem().invoke(saved -> recommendationEmitter.send(recommendation.recommendation))
            .replaceWith(recommendation)
            .onFailure().invoke(e -> System.err.println("Error publishing grid balancing: " + e.getMessage()));
    }

    private Double extractMeasuredKw(Payload payload) {
        if (payload instanceof BatteryPayload) {
            return ((BatteryPayload) payload).activePowerKw;
        }
        if (payload instanceof SolarPayload) {
            return -((SolarPayload) payload).generationKw;
        }
        if (payload instanceof EvChargerPayload) {
            return ((EvChargerPayload) payload).chargingPowerKw;
        }
        return null;
    }

    private String buildRecommendationMessage(String gridCellId, Double totalKw, Double maxCapacityKw) {
        return "Grid cell " + gridCellId +
            " exceeded capacity: total measured " + totalKw +
            " kW > max " + maxCapacityKw + " kW";
    }
}