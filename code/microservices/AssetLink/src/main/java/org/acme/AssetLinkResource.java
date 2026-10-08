package org.acme;

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

@Path("AssetLink")
public class AssetLinkResource {

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
        client.query("DROP TABLE IF EXISTS asset_links").execute()
            .flatMap(r -> client.query("CREATE TABLE asset_links (id SERIAL PRIMARY KEY, prosumer_id BIGINT UNSIGNED, utility_operator_id BIGINT UNSIGNED, CONSTRAINT UC_Loyal UNIQUE (prosumer_id, utility_operator_id))").execute())
            .flatMap(r -> client.query("INSERT INTO asset_links (prosumer_id, utility_operator_id) VALUES (1,1)").execute())
            .flatMap(r -> client.query("INSERT INTO asset_links (prosumer_id, utility_operator_id) VALUES (2,2)").execute())
            .flatMap(r -> client.query("INSERT INTO asset_links (prosumer_id, utility_operator_id) VALUES (3,3)").execute())
            .flatMap(r -> client.query("INSERT INTO asset_links (prosumer_id, utility_operator_id) VALUES (4,4)").execute())
            .await().indefinitely();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<AssetLink> getLinks() {
        return AssetLink.findAll(client);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getSingle(@PathParam("id") Long id) {
        return AssetLink.findById(client, id)
            .onItem().transform(link -> link != null ? Response.ok(link) : Response.status(Response.Status.NOT_FOUND))
            .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("/{prosumerID}/{utilityOperatorID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getLinkByProsumerAndOperator(
            @PathParam("prosumerID") Long prosumerID,
            @PathParam("utilityOperatorID") Long utilityOperatorID) {
        return AssetLink.findById2(client, prosumerID, utilityOperatorID)
            .onItem().transform(link -> link != null ? Response.ok(link) : Response.status(Response.Status.NOT_FOUND))
            .onItem().transform(ResponseBuilder::build);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> createLink(AssetLink link) {
        return link.save(client, link.prosumerId, link.utilityOperatorId)
            .onItem().transform(saved -> URI.create("/AssetLink/"))
            .onItem().transform(uri -> Response.created(uri).build())
            .onFailure().recoverWithItem(t -> Response.status(Response.Status.CONFLICT).build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteLink(@PathParam("id") Long id) {
        return AssetLink.delete(client, id)
            .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
            .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("/{id}/{prosumerId}/{utilityOperatorId}")
    public Uni<Response> update(
            @PathParam("id") Long id,
            @PathParam("prosumerId") Long prosumerId,
            @PathParam("utilityOperatorId") Long utilityOperatorId) {
        return AssetLink.update(client, id, prosumerId, utilityOperatorId)
            .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
            .onItem().transform(status -> Response.status(status).build());
    }
}
