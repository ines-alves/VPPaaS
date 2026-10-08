package org.acme;

import org.acme.model.GridBalancingRequest;
import org.acme.model.NeighboringZonesDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

@Path("GridBalancing")
public class GridBalancingResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    GridBalancingRuleEngine ruleEngine;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS grid_balancing_recommendations (" +
            "id VARCHAR(100) PRIMARY KEY, " +
            "grid_cell_id VARCHAR(100) NOT NULL, " +
            "measured_kw DOUBLE NOT NULL, " +
            "max_capacity_kw DOUBLE NOT NULL, " +
            "recommendation TEXT NOT NULL, " +
            "timestamp BIGINT NOT NULL)";

        client.query(createTableSql).execute()
            .await().indefinitely();
    }

    @POST
    @Path("/analyze")
    public Uni<java.util.Map<String, Object>> analyzeBalancingRequest(GridBalancingRequest request) {
        if (request == null) {
            return Uni.createFrom().item(java.util.Map.of("value", false));
        }

        return ruleEngine.processBalancingRequest(request)
            .map(result -> java.util.Map.<String, Object>of("value", result))
            .onFailure().recoverWithItem(java.util.Map.of("value", false));
    }

    @POST
    @Path("/recommendation")
    public Uni<Response> createRecommendation(NeighboringZonesDto neighboringZones) {
        if (neighboringZones == null || neighboringZones.selectedGridCell == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        GridBalancingRecommendation recommendation = ruleEngine.evaluateNeighboringZones(neighboringZones);
        if (recommendation == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        return Uni.createFrom().item(Response.ok(recommendation).build());
    }

    @POST
    @Path("/recommendation/publish")
    public Uni<Response> publishRecommendation(GridBalancingRecommendation recommendation) {
        return ruleEngine.publishRecommendation(recommendation)
            .onItem().transform(saved -> saved != null
                ? Response.accepted(saved).build()
                : Response.status(Response.Status.BAD_REQUEST).build())
            .onFailure().recoverWithItem(e -> Response.serverError()
                .entity(java.util.Map.of("error", e.getMessage()))
                .build());
    }

    @GET
    public Multi<GridBalancingRecommendation> getAllRecommendations() {
        return GridBalancingRecommendation.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getRecommendationById(@PathParam("id") String id) {
        return GridBalancingRecommendation.findById(client, id)
            .onItem().transform(rec -> rec != null ? Response.ok(rec) : Response.status(Response.Status.NOT_FOUND))
            .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("gridcell/{gridCellId}")
    public Multi<GridBalancingRecommendation> getRecommendationsByGridCell(@PathParam("gridCellId") String gridCellId) {
        return GridBalancingRecommendation.findByGridCellId(client, gridCellId);
    }
}
