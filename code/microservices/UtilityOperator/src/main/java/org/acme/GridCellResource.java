package org.acme;

import java.net.URI;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.MediaType;

@Path("GridCell")
public class GridCellResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<GridCell> get() {
        return GridCell.findAll(client);
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getSingle(@PathParam("id") String id) {
        return GridCell.findById(client, id)
                .onItem()
                .transform(cell -> cell != null ? Response.ok(cell) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> create(GridCell cell) {
        return cell.save(client, cell.id, cell.utilityOperatorId, cell.address, cell.postalCode, cell.maxCapacityKw)
                .onItem().transform(inserted -> URI.create("/GridCell/" + cell.id))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @DELETE
    @Path("{id}")
    public Uni<Response> delete(@PathParam("id") String id) {
        return GridCell.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> update(@PathParam("id") String id, GridCell cell) {
        return GridCell.update(client, id, cell.utilityOperatorId, cell.address, cell.postalCode, cell.maxCapacityKw)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }
}
