package org.acme;

import org.acme.model.Asset;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

@Path("Asset")
public class AssetResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @GET
    public Multi<Asset> get() {
        return Asset.findAll(client);
    }

    @GET
    @Path("battery")
    public Multi<Asset> getBatteries() {
        return Asset.findByType(client, "BATTERY");
    }

    @GET
    @Path("solar")
    public Multi<Asset> getSolarInverters() {
        return Asset.findByType(client, "SOLAR");
    }

    @GET
    @Path("ev")
    public Multi<Asset> getEvChargers() {
        return Asset.findByType(client, "EV_CHARGER");
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(@PathParam("id") String id) {
        return Asset.findById(client, id)
            .onItem().transform(asset -> asset != null ? Response.ok(asset) : Response.status(Response.Status.NOT_FOUND))
            .onItem().transform(ResponseBuilder::build);
    }

}
