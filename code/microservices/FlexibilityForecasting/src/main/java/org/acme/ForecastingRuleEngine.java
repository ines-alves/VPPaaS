package org.acme;

import org.acme.model.BatteryPayload;
import org.acme.model.EvChargerPayload;
import org.acme.model.ForecastRequest;
import org.acme.model.SolarPayload;
import org.acme.model.TelemetryDto;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ForecastingRuleEngine {

    private static final Logger LOG = Logger.getLogger(ForecastingRuleEngine.class);

    @Inject
    MySQLPool client;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Builds and returns the Ollama prompt from the given ForecastRequest.
     * Called by the BPMN "Build Prompt" service task.
     */
    public String buildPrompt(ForecastRequest request) {
        Long prosumerId = request.prosumerId;
        String assetId = request.flexibilityEvent != null ? request.flexibilityEvent.assetId : "unknown";
        double socPercent = request.flexibilityEvent != null ? request.flexibilityEvent.socPercent : 0.0;
        String offerType = request.flexibilityEvent != null ? request.flexibilityEvent.offerType : "UNKNOWN";
        String telemetrySummary = buildTelemetrySummary(request.telemetry);

        return String.format("""
            You are an energy flexibility forecasting assistant for a Virtual Power Plant (VPP).

            ## Asset Context
            Prosumer ID  : %d
            Asset ID     : %s
            Current SoC  : %.1f%%
            Offer Type   : %s

            ## Telemetry
            %s

            ## Task
            Analyse the provided telemetry and flexibility event data.
            Based on this analysis, forecast the prosumer's flexibility availability.
            Respond ONLY with a valid JSON object in this exact format:
            {
            "prosumer_id": %d,
            "asset_id": "%s",
            "forecast_type": "<HIGH_FLEXIBILITY|LOW_FLEXIBILITY|UNAVAILABLE>",
            "predicted_soc_percent": <number 0-100>,
            "recommendation": "<one sentence summarising the pattern observed>",
            "confidence_score": <number 0.0-1.0>
            }
            """, prosumerId, assetId, socPercent, offerType, telemetrySummary, prosumerId, assetId);
    }

    /**
     * Parses the Ollama response, creates a FlexibilityForecast, and persists it to the DB.
     * Called by the BPMN "Create Flexibility Forecast" service task.
     */
    public Uni<FlexibilityForecast> createForecastFromOllamaResponse(JsonNode forecastNode) {
        String rawText = extractRawText(forecastNode);
        FlexibilityForecast forecast = parseLlmResponse(rawText);

        return forecast.save(client)
            .map(saved -> forecast)
            .onFailure().invoke(e -> LOG.error("Error saving forecast: " + e.getMessage()));
    }

    private String buildTelemetrySummary(TelemetryDto telemetry) {
        if (telemetry == null) return "No telemetry data available.";
        StringBuilder sb = new StringBuilder();
        if (telemetry.assetId != null)   sb.append("Asset ID: ").append(telemetry.assetId).append("\n");
        if (telemetry.assetType != null) sb.append("Asset Type: ").append(telemetry.assetType).append("\n");
        if (telemetry.timestamp != null) sb.append("Timestamp: ").append(telemetry.timestamp).append("\n");
        if (telemetry.payload != null) {
            if (telemetry.payload instanceof BatteryPayload b) {
                sb.append("SoC: ").append(b.socPercent).append("%\n");
                sb.append("Max Discharge: ").append(b.maxDischargePowerKw).append(" kW\n");
                sb.append("Energy Available: ").append(b.energyAvailableKwh).append(" kWh\n");
                sb.append("Connection Status: ").append(b.connectionStatus).append("\n");
            } else if (telemetry.payload instanceof SolarPayload s) {
                sb.append("Generation: ").append(s.generationKw).append(" kW\n");
                sb.append("Daily Yield: ").append(s.dailyYieldKwh).append(" kWh\n");
            } else if (telemetry.payload instanceof EvChargerPayload ev) {
                sb.append("Charging Power: ").append(ev.chargingPowerKw).append(" kW\n");
                sb.append("EV SoC: ").append(ev.evSocPercent).append("%\n");
                sb.append("Connector Status: ").append(ev.connectorStatus).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractRawText(JsonNode node) {
        if (node == null) return "";
        // Ollama native response: { "response": "...generated text..." }
        if (node.has("response")) return node.get("response").asText();
        // Plain text node
        if (node.isTextual()) return node.asText();
        // Already a JSON object (LLM returned JSON directly)
        return node.toString();
    }

    private FlexibilityForecast parseLlmResponse(String rawText) {
        FlexibilityForecast f = new FlexibilityForecast();
        f.recommendation      = "No recommendation available.";
        f.forecastType        = "LOW_FLEXIBILITY";
        f.confidenceScore     = 0.0;
        f.predictedSocPercent = 0.0;

        try {
            JsonNode root = objectMapper.readTree(rawText);

            if (root.has("prosumer_id"))           f.prosumerId          = root.get("prosumer_id").asLong();
            if (root.has("asset_id"))              f.assetId             = root.get("asset_id").asText();
            if (root.has("forecast_type"))         f.forecastType        = root.get("forecast_type").asText();
            if (root.has("predicted_soc_percent")) f.predictedSocPercent = root.get("predicted_soc_percent").asDouble();
            if (root.has("recommendation"))        f.recommendation      = root.get("recommendation").asText();
            if (root.has("confidence_score"))      f.confidenceScore     = root.get("confidence_score").asDouble();
        } catch (Exception e) {
            LOG.errorf("Failed to parse LLM JSON response. Raw output was: %s", rawText);
        }

        return f;
    }
}
