package org.acme;

import java.net.URI;

import org.acme.model.CreateGridCellRequest;
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

@Path("UtilityOperator")
public class UtilityOperatorResource {

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
        client.query("DROP TABLE IF EXISTS utility_operators").execute()
            .flatMap(r -> client.query("CREATE TABLE utility_operators (id SERIAL PRIMARY KEY, name TEXT NOT NULL, location TEXT NOT NULL)").execute())
            .flatMap(r -> client.query("INSERT INTO utility_operators (name, location) VALUES ('ArcoCegoLisbon', 'Lisboa')").execute())
            .flatMap(r -> client.query("INSERT INTO utility_operators (name, location) VALUES ('PracadeBocage', 'Setubal')").execute())
            .flatMap(r -> client.query("INSERT INTO utility_operators (name, location) VALUES ('PracadaBoavista', 'Porto')").execute())
            .flatMap(r -> client.query("INSERT INTO utility_operators (name, location) VALUES ('PracaDomFranciscoGomes', 'Faro')").execute())
            .flatMap(r -> client.query("DROP TABLE IF EXISTS grid_cells").execute())
            .flatMap(r -> client.query("CREATE TABLE grid_cells (id VARCHAR(50) PRIMARY KEY, utility_operator_id BIGINT NOT NULL, address TEXT, postal_code VARCHAR(20), max_capacity_kw DOUBLE)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('LISBON-RES-01', 1, 'Avenida da Liberdade, Lisboa', '1100-048', 15.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('SETUBAL-RES-01', 1, 'Rua do Comércio, Setúbal', '1099-476', 5.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('OPORTO-RES-01', 2, 'Avenida dos Aliados, Porto', '4000-421', 3.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('FARO-RES-01', 2, 'Rua de Santo António, Faro', '4001-372', 3.0)").execute())
            // Event producer grid cells
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('TOKYO-NW', 1, 'Shinjuku, Tokyo', '163-0000', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('BERLIN-CE', 1, 'Mitte, Berlin', '10115', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('AUSTIN-DT', 1, 'Downtown, Austin TX', '78701', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('LONDON-SE', 1, 'Southwark, London', 'SE1 0AA', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('MUMBAI-WP', 1, 'Worli, Mumbai', '400018', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('SYDNEY-HB', 2, 'Harbour, Sydney NSW', '2000', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('MADRID-NE', 2, 'Hortaleza, Madrid', '28043', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('DENVER-MT', 2, 'Mountain View, Denver CO', '80202', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('SEOUL-IT', 2, 'Gangnam, Seoul', '06000', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('LAGOS-VI', 2, 'Victoria Island, Lagos', '101241', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('PARIS-RG', 3, 'Rive Gauche, Paris', '75006', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('MEXICO-SZ', 3, 'Santa Fe, Mexico City', '05109', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('DUBAI-MR', 3, 'Marina, Dubai', '00000', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('CAIRO-ND', 3, 'New Downtown, Cairo', '11511', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('LISBON-EX', 3, 'Expo, Lisboa', '1990-096', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('TORONTO-QU', 4, 'Queen West, Toronto ON', 'M6J 1E6', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('VIENNA-BZ', 4, 'Bezirk 1, Vienna', '1010', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('SANTOS-IN', 4, 'Industrial, Santos SP', '11015-000', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('OSLO-FJ', 4, 'Fjord, Oslo', '0150', 500.0)").execute())
            .flatMap(r -> client.query("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES ('SINGA-GP', 4, 'Greater Ports, Singapore', '018989', 500.0)").execute())
            .await().indefinitely();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<UtilityOperator> get() {
        return UtilityOperator.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(@PathParam("id") Long id) {
        return UtilityOperator.findById(client, id)
            .onItem().transform(operator -> operator != null ? Response.ok(operator) : Response.status(Response.Status.NOT_FOUND))
            .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("{id}/gridcells")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<GridCell> getGridCells(@PathParam("id") Long id) {
        return GridCell.findByUtilityOperatorId(client, id);
    }

    @GET
    @Path("gridcells/{cellId}/neighbors")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<GridCell> getNeighboringCells(@PathParam("cellId") String cellId) {
        return GridCell.findNeighboringCells(client, cellId);
    }

    @POST
    @Path("createGridCell")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> createGridCell(CreateGridCellRequest request) {
        GridCell cell = new GridCell(request.id, request.utilityOperatorId, request.address, request.postalCode, request.maxCapacityKw);
        return cell.save(client, cell.id, cell.utilityOperatorId, cell.address, cell.postalCode, cell.maxCapacityKw)
            .onItem().transform(inserted -> inserted
                ? Response.created(URI.create("/UtilityOperator/" + request.utilityOperatorId + "/gridcells/" + request.id)).build()
                : Response.status(Response.Status.INTERNAL_SERVER_ERROR).build())
            .onFailure().recoverWithItem(t -> Response.status(Response.Status.CONFLICT).build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> create(UtilityOperator operator) {
        return operator.save(client, operator.name, operator.location)
            .onItem().transform(id -> URI.create("/UtilityOperator/" + id))
            .onItem().transform(uri -> Response.created(uri).build());
    }

    @DELETE
    @Path("{id}")
    public Uni<Response> delete(@PathParam("id") Long id) {
        return UtilityOperator.delete(client, id)
            .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
            .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{id}")
    public Uni<Response> update(@PathParam("id") Long id, UtilityOperator operator) {
        return UtilityOperator.update(client, id, operator.name, operator.location)
            .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
            .onItem().transform(status -> Response.status(status).build());
    }
}
