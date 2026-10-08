package org.acme;

import java.net.URI;
import java.util.UUID;

import org.acme.model.CreateAssetRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

@Path("Prosumer")
public class ProsumerResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS assets").execute()
            .flatMap(r -> client.query("DROP TABLE IF EXISTS Prosumer").execute())
            .flatMap(r -> client.query("CREATE TABLE Prosumer (id SERIAL PRIMARY KEY, name TEXT NOT NULL, FiscalNumber BIGINT UNSIGNED, location TEXT NOT NULL)").execute())
            .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client1','123456','Lisbon')").execute())
            .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client2','987654','Setúbal')").execute())
            .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client3','123987','OPorto')").execute())
            .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client4','987123','Faro')").execute())
            .flatMap(r -> client.query("CREATE TABLE assets (id VARCHAR(50) PRIMARY KEY, prosumer_id BIGINT NOT NULL, asset_type VARCHAR(20) NOT NULL, max_capacity_kw DOUBLE, charging_rate_kw DOUBLE)").execute())
            // client1 (Lisbon): BATTERY + SOLAR
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type, max_capacity_kw) VALUES ('BATTERY-1001', 1, 'BATTERY', 20.0)").execute())
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type) VALUES ('SOLAR-1002', 1, 'SOLAR')").execute())
            // client2 (Setúbal): SOLAR + EV_CHARGER
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type) VALUES ('SOLAR-2001', 2, 'SOLAR')").execute())
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type, charging_rate_kw) VALUES ('EV_CHARGER-2002', 2, 'EV_CHARGER', 0.96)").execute())
            // client3 (OPorto): BATTERY + EV_CHARGER
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type, max_capacity_kw) VALUES ('BATTERY-3001', 3, 'BATTERY', 15.0)").execute())
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type, charging_rate_kw) VALUES ('EV_CHARGER-3002', 3, 'EV_CHARGER', 7.40)").execute())
            // client4 (Faro): BATTERY + SOLAR + EV_CHARGER
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type, max_capacity_kw) VALUES ('BATTERY-4001', 4, 'BATTERY', 10.0)").execute())
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type) VALUES ('SOLAR-4002', 4, 'SOLAR')").execute())
            .flatMap(r -> client.query("INSERT INTO assets (id, prosumer_id, asset_type, charging_rate_kw) VALUES ('EV_CHARGER-4003', 4, 'EV_CHARGER', 3.60)").execute())
            .await().indefinitely();
    }

    @GET
    public Multi<Prosumer> get() {
        return Prosumer.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return Prosumer.findById(client, id)
                .onItem().transform(prosumer -> prosumer != null ? Response.ok(prosumer) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @POST
    public Uni<Response> create(Prosumer prosumer) {
        return prosumer.save(client, prosumer.name, prosumer.FiscalNumber, prosumer.location)
                .onItem().transform(id -> URI.create("/Prosumer/" + id))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @DELETE
    @Path("{id}")
    public Uni<Response> delete(Long id) {
        return Prosumer.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("/{id}/{name}/{FiscalNumber}/{location}")
    public Uni<Response> update(Long id, String name, Long FiscalNumber, String location) {
        return Prosumer.update(client, id, name, FiscalNumber, location)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @GET
    @Path("{id}/assets")
    public Multi<org.acme.model.Asset> getAssets(Long id) {
        return org.acme.model.Asset.findByProsumerId(client, id);
    }

    @GET
    @Path("{id}/{assetType}")
    public Multi<org.acme.model.Asset> getAssetsByType(Long id, String assetType) {
        String normalizedType = assetType == null ? null : assetType.toUpperCase();
        if (!isValidAssetType(normalizedType)) {
            throw new BadRequestException("Invalid assetType: " + assetType);
        }
        return org.acme.model.Asset.findByProsumerIdAndType(client, id, normalizedType);
    }

    @POST
    @Path("{id}/assets")
    public Uni<Response> createAsset(Long id, org.acme.model.Asset asset) {
        asset.prosumerId = id;
        return asset.save(client)
            .onItem().transform(success -> success ? Response.Status.CREATED : Response.Status.CONFLICT)
            .onItem().transform(status -> Response.status(status).build());
    }

    @POST
    @Path("createAsset")
    public Uni<Response> createAsset(CreateAssetRequest request) {
        if (request == null || request.prosumerId == null) {
            throw new BadRequestException("prosumerID is required");
        }
        String normalizedType = request.assetType == null ? null : request.assetType.toUpperCase();
        if (!isValidAssetType(normalizedType)) {
            throw new BadRequestException("Invalid assetType: " + request.assetType);
        }
        org.acme.model.Asset asset = new org.acme.model.Asset();
        asset.id = normalizedType + "-" + UUID.randomUUID();
        asset.prosumerId = request.prosumerId;
        asset.assetType = normalizedType;
        return asset.save(client)
            .onItem().transform(success -> success
                ? Response.status(Response.Status.CREATED).entity(asset).build()
                : Response.status(Response.Status.CONFLICT).build());
    }

    @DELETE
    @Path("{id}/assets/{assetId}")
    public Uni<Response> deleteAsset(Long id, String assetId) {
        return org.acme.model.Asset.delete(client, assetId)
            .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
            .onItem().transform(status -> Response.status(status).build());
    }

    private boolean isValidAssetType(String assetType) {
        return "BATTERY".equals(assetType) || "SOLAR".equals(assetType) || "EV_CHARGER".equals(assetType);
    }

}
