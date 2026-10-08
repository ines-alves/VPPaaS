package org.acme;

import java.util.List;

import org.acme.model.Topic;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

@Path("Telemetry")
public class KafkaProvisioningResource {

        @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;
    
    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true") 
    boolean schemaCreate ;

    @ConfigProperty(name = "kafka.bootstrap.servers") 
    String kafka_servers;
    
    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }
    
    private void initdb() {
        // In a production environment this configuration SHOULD NOT be used
        client.query("DROP TABLE IF EXISTS telemetry").execute()
        .flatMap(r -> client.query("CREATE TABLE telemetry (id SERIAL PRIMARY KEY, "
                                                            + " seqKey TEXT, "
                                                            + " timestamp DATETIME, "
                                                            + " asset_type TEXT NOT NULL, "
                                                            + " asset_id TEXT, "
                                                            + " grid_cell_id TEXT NOT NULL, "
                                                            + " payload TEXT)").execute())
        // BATTERY telemetry
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('b0002dd9-3ab0-4a0b-9b4c-74790d7e3849', '2026-02-20 13:39:53', 'BATTERY', 'BATTERY-1001', 'LISBON-RES-01', '{\"energy_available_kwh\":13.94,\"max_discharge_power_kw\":2.95,\"active_power_kw\":-7.22,\"connection_status\":\"CONNECTED\",\"soh_percent\":31.94,\"soc_percent\":92.23}')").execute())
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('c1113ee0-4bc1-5b1c-ac5d-858001e8f495', '2026-02-20 14:15:10', 'BATTERY', 'BATTERY-3001', 'OPORTO-RES-01', '{\"energy_available_kwh\":8.50,\"max_discharge_power_kw\":1.80,\"active_power_kw\":-3.10,\"connection_status\":\"CONNECTED\",\"soh_percent\":88.5,\"soc_percent\":57.0}')").execute())
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('d2224ff1-5cd2-6c2d-bd6e-969120f9a506', '2026-02-20 14:50:22', 'BATTERY', 'BATTERY-4001', 'FARO-RES-01', '{\"energy_available_kwh\":6.20,\"max_discharge_power_kw\":1.20,\"active_power_kw\":-1.80,\"connection_status\":\"MAINTENANCE\",\"soh_percent\":95.0,\"soc_percent\":62.0}')").execute())
        // SOLAR telemetry
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('f516b174-2c5f-405d-9891-450ed19c4e4c', '2026-02-20 15:00:30', 'SOLAR', 'SOLAR-1002', 'LISBON-RES-01', '{\"ac_voltage_v\":254.69,\"generation_kw\":0.56,\"grid_frequency_hz\":49.85,\"daily_yield_kwh\":63.80}')").execute())
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('e3335aa2-6de3-7d3e-ce7f-070238a0b617', '2026-02-20 15:05:15', 'SOLAR', 'SOLAR-2001', 'SETUBAL-RES-01', '{\"ac_voltage_v\":231.50,\"generation_kw\":1.23,\"grid_frequency_hz\":50.02,\"daily_yield_kwh\":45.67}')").execute())
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('a7890bb5-9fa7-4f7a-bf90-292450c2d839', '2026-02-20 15:10:45', 'SOLAR', 'SOLAR-4002', 'FARO-RES-01', '{\"ac_voltage_v\":228.30,\"generation_kw\":2.10,\"grid_frequency_hz\":49.97,\"daily_yield_kwh\":18.90}')").execute())
        // EV_CHARGER telemetry
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('440ce0b6-8ac7-4926-8650-36d43ca360fb', '2026-02-20 15:20:49', 'EV_CHARGER', 'EV_CHARGER-2002', 'SETUBAL-RES-01', '{\"charging_power_kw\":0.96,\"connector_status\":\"AVAILABLE\",\"session_energy_kwh\":13.63,\"ev_soc_percent\":50.50}')").execute())
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('550df1c7-9bd8-5037-9761-47e54db471ac', '2026-02-20 15:25:33', 'EV_CHARGER', 'EV_CHARGER-3002', 'OPORTO-RES-01', '{\"charging_power_kw\":7.40,\"connector_status\":\"OCCUPIED\",\"session_energy_kwh\":22.50,\"ev_soc_percent\":68.0}')").execute())
        .flatMap(r -> client.query("INSERT INTO telemetry (seqKey, timestamp, asset_type, asset_id, grid_cell_id, payload) VALUES ('660ea2d8-0ce9-6148-0872-58f65ec582bd', '2026-02-20 15:30:17', 'EV_CHARGER', 'EV_CHARGER-4003', 'FARO-RES-01', '{\"charging_power_kw\":0.80,\"connector_status\":\"OCCUPIED\",\"session_energy_kwh\":9.80,\"ev_soc_percent\":82.0}')").execute())
        .await().indefinitely();
    }

    @POST
    @Path("Consume")
    public String ProvisioningConsumer(Topic topic) {
        Thread worker = new DynamicTopicConsumer(topic.TopicName , kafka_servers , client);
        worker.start();
        return "New worker started";
    }

    @GET
    public Multi<Telemetry> get() {
        return Telemetry.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return Telemetry.findById(client, id)
                .onItem().transform(telemetry -> telemetry != null ? Response.ok(telemetry) : Response.status(Response.Status.NOT_FOUND)) 
                .onItem().transform(ResponseBuilder::build); 
    }

    @GET
    @Path("asset/{assetId}")
    public Uni<Telemetry> getByAssetId(@PathParam("assetId") String assetId) {
        return Telemetry.findLatestByAssetId(client, assetId);
    }

    @GET
    @Path("grid-zone/{gridZoneId}")
    public Multi<Telemetry> getByGridZoneId(@PathParam("gridZoneId") String gridZoneId) {
        return Telemetry.findLatestByGridCellId(client, gridZoneId);
    }

    @POST
    @Path("assets")
    @Consumes(MediaType.APPLICATION_JSON)
    public Multi<Telemetry> getLatestByAssets(List<String> assetIds) {
        return Telemetry.findLatestByAssetIds(client, assetIds);
    }

    @POST
    @Path("grid-zones")
    @Consumes(MediaType.APPLICATION_JSON)
    public Multi<Telemetry> getLatestByGridZones(List<String> gridZoneIds) {
        return Telemetry.findLatestByGridZoneIds(client, gridZoneIds);
    }

}

