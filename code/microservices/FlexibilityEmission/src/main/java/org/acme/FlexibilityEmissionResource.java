package org.acme;

import java.util.List;

import org.acme.model.FlexibilityEmissionRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

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

@Path("FlexibilityOffer")
public class FlexibilityEmissionResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    @Channel("flexibility-offers-out")
    Emitter<FlexibilityOffer> offerEmitter;

    @Inject
    FlexibilityRuleEngine ruleEngine;
    
    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS flexibility_offers (" +
            "id VARCHAR(100) PRIMARY KEY, " +
            "prosumer_id BIGINT NOT NULL, " +
            "asset_id VARCHAR(100) NOT NULL, " +
            "soc_percent DOUBLE NOT NULL, " +
            "offer_type VARCHAR(50) NOT NULL, " +
            "timestamp BIGINT NOT NULL)";

        client.query(createTableSql).execute()
            .await().indefinitely();
    }

    @GET
    public Multi<FlexibilityOffer> get() {
        return FlexibilityOffer.findAll(client);
    }
    
    @GET
    @Path("{id}")
    public Uni<Response> getSingle(@PathParam("id") String id) { 
        return FlexibilityOffer.findById(client, id)
            .onItem().transform(offer -> offer != null ? Response.ok(offer) : Response.status(Response.Status.NOT_FOUND))
            .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("prosumer/{prosumerId}")
    public Multi<FlexibilityOffer> getByProsumerId(@PathParam("prosumerId") Long prosumerId) {
        return FlexibilityOffer.findByProsumerId(client, prosumerId);
    }
    
    @POST
    @Path("/analyze")
    public Uni<Response> analyzeFlexibilityRequest(FlexibilityEmissionRequest request) {
        if (request == null || request.telemetry == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }
        FlexibilityOffer offer = ruleEngine.processFlexibilityRequest(request);
        
        if (offer == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        return Uni.createFrom().item(Response.ok(offer).build());
    }

    @POST
    @Path("/publish")
    public Uni<Response> publishOffers(List<FlexibilityOffer> offers) {
        if (offers == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }
        if (offers.isEmpty()) {
            return Uni.createFrom().item(Response.ok(offers).build());
        }

        return Multi.createFrom().iterable(offers)
            .onItem().invoke(offer -> {
                if (offer == null || offer.prosumerId == null || offer.assetId == null || offer.offerType == null) {
                    throw new IllegalArgumentException("Invalid flexibility offer");
                }
            })
            .collect().asList()
            .onItem().transformToUni(ruleEngine::emitOffer)
            .onItem().transform(saved -> Response.ok(saved).build())
            .onFailure(IllegalArgumentException.class)
            .recoverWithItem(err -> Response.status(Response.Status.BAD_REQUEST).build());
    }
}