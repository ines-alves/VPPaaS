package org.acme;

import java.util.ArrayList;
import java.util.List;

import org.acme.model.ForecastRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("FlexibilityForecasting")
public class FlexibilityForecastingResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    ForecastingRuleEngine ruleEngine;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        String forecastsTable =
            "CREATE TABLE IF NOT EXISTS flexibility_forecasts (" +
            "id VARCHAR(100) PRIMARY KEY, " +
            "prosumer_id BIGINT NOT NULL, " +
            "asset_id VARCHAR(100) NOT NULL, " +
            "forecast_type VARCHAR(50) NOT NULL, " +
            "predicted_soc_percent DOUBLE, " +
            "recommendation TEXT, " +
            "confidence_score DOUBLE NOT NULL, " +
            "timestamp BIGINT NOT NULL)";

        client.query(forecastsTable).execute()
              .await().indefinitely();
    }

    @GET
    public Uni<List<FlexibilityForecast>> getRecentForecasts() {
        return client.query("SELECT * FROM flexibility_forecasts ORDER BY timestamp DESC LIMIT 50")
            .execute()
            .onItem().transform(rows -> {
                List<FlexibilityForecast> list = new ArrayList<>();
                rows.forEach(row -> {
                    FlexibilityForecast f = new FlexibilityForecast();
                    f.id = row.getString("id");
                    f.prosumerId = row.getLong("prosumer_id");
                    f.assetId = row.getString("asset_id");
                    f.forecastType = row.getString("forecast_type");
                    f.predictedSocPercent = row.getDouble("predicted_soc_percent");
                    f.recommendation = row.getString("recommendation");
                    f.confidenceScore = row.getDouble("confidence_score");
                    f.timestamp = row.getLong("timestamp");
                    list.add(f);
                });
                return list;
            });
    }

    @GET
    @Path("/{prosumerId}")
    public Uni<List<FlexibilityForecast>> getForecastsByProsumer(@PathParam("prosumerId") Long prosumerId) {
        return client.preparedQuery(
                "SELECT * FROM flexibility_forecasts WHERE prosumer_id = ? ORDER BY timestamp DESC LIMIT 50")
            .execute(io.vertx.mutiny.sqlclient.Tuple.of(prosumerId))
            .onItem().transform(rows -> {
                List<FlexibilityForecast> list = new ArrayList<>();
                rows.forEach(row -> {
                    FlexibilityForecast f = new FlexibilityForecast();
                    f.id = row.getString("id");
                    f.prosumerId = row.getLong("prosumer_id");
                    f.assetId = row.getString("asset_id");
                    f.forecastType = row.getString("forecast_type");
                    f.predictedSocPercent = row.getDouble("predicted_soc_percent");
                    f.recommendation = row.getString("recommendation");
                    f.confidenceScore = row.getDouble("confidence_score");
                    f.timestamp = row.getLong("timestamp");
                    list.add(f);
                });
                return list;
            });
    }

    /**
     * Receives ForecastRequest (flexibilityEvent + telemetry + prosumerID),
     * delegates to ForecastingRuleEngine to build and return the Ollama prompt.
     * Called by the BPMN "Build Prompt" service task.
     */
    @POST
    @Path("/buildPrompt")
    public Response buildPrompt(ForecastRequest request) {
        if (request == null || request.prosumerId == null || request.flexibilityEvent == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("prosumerId and flexibilityEvent are required.")
                .build();
        }
        String prompt = ruleEngine.buildPrompt(request);
        return Response.ok(prompt).build();
    }

    /**
     * Receives the Ollama forecast response body ({ "forecast": <ollama_response> }),
     * delegates to ForecastingRuleEngine to parse, create, and persist the FlexibilityForecast.
     * Called by the BPMN "Create Flexibility Forecast" service task.
     */
    @POST
    @Path("/createForecast")
    public Uni<Response> createForecast(JsonNode body) {
        JsonNode forecastNode = (body != null && body.has("forecast")) ? body.get("forecast") : body;

        return ruleEngine.createForecastFromOllamaResponse(forecastNode)
            .onItem().transform(forecast ->
                Response.status(Response.Status.CREATED).entity(forecast).build())
            .onFailure().recoverWithItem(e ->
                Response.serverError().entity(java.util.Map.of("error", e.getMessage())).build());
    }
}
