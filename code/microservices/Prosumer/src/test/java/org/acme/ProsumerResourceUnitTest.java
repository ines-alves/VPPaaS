package org.acme;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.acme.model.CreateAssetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;

class ProsumerResourceUnitTest {

    ProsumerResource resource;
    private MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new ProsumerResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
    }

    @Test
    void getProsumers_returnsList() {
        Row row1 = prosumerRow(1L, "Alice Johnson", 111222333L, "Lisbon");
        Row row2 = prosumerRow(2L, "Bob Silva", 444555666L, "Porto");
        Row row3 = prosumerRow(3L, "Carol Santos", 777888999L, "Faro");
        stubQuery("SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC", rowSetWithRows(row1, row2, row3));

        List<Prosumer> result = resource.get().collect().asList().await().indefinitely();
        
        assertThat(result, hasSize(3));
        assertThat(result.get(0).id, is(1L));
        assertThat(result.get(0).name, is("Alice Johnson"));
        assertThat(result.get(0).location, is("Lisbon"));
        assertThat(result.get(0).FiscalNumber, is(111222333L));
        assertThat(result.get(1).name, is("Bob Silva"));
        assertThat(result.get(2).name, is("Carol Santos"));
    }

    @Test
    void getProsumerById_returnsEntity() {
        Row row = prosumerRow(5L, "Diana Costa", 555666777L, "Setubal");
        stubPreparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(5L).await().indefinitely();
        
        assertThat(response.getStatus(), is(200));
        Prosumer entity = (Prosumer) response.getEntity();
        assertThat(entity, notNullValue());
        assertThat(entity.id, is(5L));
        assertThat(entity.name, is("Diana Costa"));
        assertThat(entity.FiscalNumber, is(555666777L));
        assertThat(entity.location, is("Setubal"));
    }

    @Test
    void getProsumerById_returnsNotFound() {
        stubPreparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(999L).await().indefinitely();
        
        assertThat(response.getStatus(), is(404));
    }

    @Test
    void getProsumersEmptyList() {
        stubQuery("SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC", rowSetWithRows());

        List<Prosumer> result = resource.get().collect().asList().await().indefinitely();

        assertThat(result, hasSize(0));
    }

    @Test
    void createProsumer_returnsCreated() {
        Prosumer prosumer = new Prosumer();
        prosumer.name = "Emma Wilson";
        prosumer.FiscalNumber = 888999000L;
        prosumer.location = "Aveiro";
        stubPreparedQuery("INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)", rowSetWithRowCount(1));

        Response response = resource.create(prosumer).await().indefinitely();
        
        assertThat(response.getStatus(), is(201));
        assertThat(response.getLocation(), notNullValue());
        assertThat(response.getLocation().getPath(), startsWith("/Prosumer/"));
    }

    @Test
    void createMultipleProsumers() {
        Prosumer p1 = new Prosumer();
        p1.name = "Frank Miller";
        p1.FiscalNumber = 111333555L;
        p1.location = "Braga";
        
        Prosumer p2 = new Prosumer();
        p2.name = "Grace Lee";
        p2.FiscalNumber = 222444666L;
        p2.location = "Covilha";
        
        stubPreparedQuery("INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)", rowSetWithRowCount(1));

        Response r1 = resource.create(p1).await().indefinitely();
        Response r2 = resource.create(p2).await().indefinitely();
        
        assertThat(r1.getStatus(), is(201));
        assertThat(r2.getStatus(), is(201));
    }

    @Test
    void deleteProsumer_returnsNoContent() {
        stubPreparedQuery("DELETE FROM Prosumer WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.delete(10L).await().indefinitely();
        
        assertThat(response.getStatus(), is(204));
    }

    @Test
    void deleteProsumer_returnsNotFound() {
        stubPreparedQuery("DELETE FROM Prosumer WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.delete(999L).await().indefinitely();
        
        assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateProsumer_returnsNoContent() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.update(3L, "Updated Name", 999111333L, "Updated City")
            .await().indefinitely();

        assertThat(response.getStatus(), is(204));
    }

    @Test
    void updateProsumer_returnsNotFound() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.update(999L, "Missing", 111222333L, "Unknown")
            .await().indefinitely();

        assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateProsumerLocation() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.update(7L, "Henry Brown", 333444555L, "Coimbra").await().indefinitely();
        
        assertThat(response.getStatus(), is(204));
    }

    @Test
    void getAssets_returnsList() {
        Row row1 = assetRow("SOLAR-01", 1L, "SOLAR");
        Row row2 = assetRow("BATTERY-01", 1L, "BATTERY");
        stubPreparedQuery("SELECT * FROM assets WHERE prosumer_id = ?", 
            rowSetWithRows(row1, row2));

        List<org.acme.model.Asset> result = resource.getAssets(1L).collect().asList().await().indefinitely();
        
        assertThat(result, hasSize(2));
        assertThat(result.get(0).id, is("SOLAR-01"));
        assertThat(result.get(0).prosumerId, is(1L));
        assertThat(result.get(1).id, is("BATTERY-01"));
    }

    @Test
    void getAssets_returnsEmptyList() {
        stubPreparedQuery("SELECT * FROM assets WHERE prosumer_id = ?", 
            rowSetWithRows());

        List<org.acme.model.Asset> result = resource.getAssets(5L).collect().asList().await().indefinitely();
        
        assertThat(result, hasSize(0));
    }

    @Test
    void createAsset_returnsCreated() {
        org.acme.model.Asset asset = new org.acme.model.Asset("BATTERY-NEW", 2L, "BATTERY");
        stubPreparedQuery("INSERT INTO assets (id, prosumer_id, asset_type) VALUES (?, ?, ?)",
            rowSetWithRowCount(1));

        Response response = resource.createAsset(2L, asset).await().indefinitely();
        
        assertThat(response.getStatus(), is(201));
    }

    @Test
    void deleteAsset_returnsNoContent() {
        stubPreparedQuery("DELETE FROM assets WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.deleteAsset(1L, "SOLAR-01").await().indefinitely();
        
        assertThat(response.getStatus(), is(204));
    }

    @Test
    void deleteAsset_returnsNotFound() {
        stubPreparedQuery("DELETE FROM assets WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.deleteAsset(1L, "UNKNOWN").await().indefinitely();
        
        assertThat(response.getStatus(), is(404));
    }

    @Test
    void createMultipleAssets_bothCreated() {
        org.acme.model.Asset asset1 = new org.acme.model.Asset("SOLAR-NEW-1", 3L, "SOLAR");
        org.acme.model.Asset asset2 = new org.acme.model.Asset("EV-NEW-1", 3L, "EV_CHARGER");

        stubPreparedQuery("INSERT INTO assets (id, prosumer_id, asset_type) VALUES (?, ?, ?)",
            rowSetWithRowCount(1));

        Response r1 = resource.createAsset(3L, asset1).await().indefinitely();
        Response r2 = resource.createAsset(3L, asset2).await().indefinitely();
        
        assertThat(r1.getStatus(), is(201));
        assertThat(r2.getStatus(), is(201));
    }

    @Test
    void getAssetsByType_returnsList() {
        Row row1 = assetRow("BATTERY-01", 1L, "BATTERY");
        Row row2 = assetRow("BATTERY-02", 1L, "BATTERY");
        stubPreparedQuery("SELECT * FROM assets WHERE prosumer_id = ? AND asset_type = ?", rowSetWithRows(row1, row2));

        List<org.acme.model.Asset> result = resource.getAssetsByType(1L, "battery").collect().asList().await().indefinitely();

        assertThat(result, hasSize(2));
        assertThat(result.get(0).id, is("BATTERY-01"));
        assertThat(result.get(1).id, is("BATTERY-02"));
    }

    @Test
    void getAssetsByType_returnsEmptyList() {
        stubPreparedQuery("SELECT * FROM assets WHERE prosumer_id = ? AND asset_type = ?", rowSetWithRows());

        List<org.acme.model.Asset> result = resource.getAssetsByType(1L, "solar").collect().asList().await().indefinitely();

        assertThat(result, hasSize(0));
    }

    @Test
    void getAssetsByType_throwsBadRequest_forInvalidType() {
        assertThrows(jakarta.ws.rs.BadRequestException.class,
            () -> resource.getAssetsByType(1L, "INVALID"));
    }

    @Test
    void createAssetViaRequest_returnsCreated() {
        stubPreparedQuery("INSERT INTO assets (id, prosumer_id, asset_type) VALUES (?, ?, ?)",
            rowSetWithRowCount(1));

        CreateAssetRequest request = new CreateAssetRequest();
        request.prosumerId = 2L;
        request.assetType = "BATTERY";

        Response response = resource.createAsset(request).await().indefinitely();

        assertThat(response.getStatus(), is(201));
        org.acme.model.Asset entity = (org.acme.model.Asset) response.getEntity();
        assertThat(entity, notNullValue());
        assertThat(entity.prosumerId, is(2L));
        assertThat(entity.assetType, is("BATTERY"));
    }

    @Test
    void createAssetViaRequest_returnsConflict() {
        stubPreparedQuery("INSERT INTO assets (id, prosumer_id, asset_type) VALUES (?, ?, ?)",
            rowSetWithRowCount(0));

        CreateAssetRequest request = new CreateAssetRequest();
        request.prosumerId = 2L;
        request.assetType = "SOLAR";

        Response response = resource.createAsset(request).await().indefinitely();

        assertThat(response.getStatus(), is(409));
    }

    @Test
    void createAssetViaRequest_throwsBadRequest_forNullProsumerId() {
        CreateAssetRequest request = new CreateAssetRequest();
        request.assetType = "BATTERY";

        assertThrows(jakarta.ws.rs.BadRequestException.class,
            () -> resource.createAsset(request));
    }

    @Test
    void createAssetViaRequest_throwsBadRequest_forInvalidType() {
        CreateAssetRequest request = new CreateAssetRequest();
        request.prosumerId = 1L;
        request.assetType = "INVALID";

        assertThrows(jakarta.ws.rs.BadRequestException.class,
            () -> resource.createAsset(request));
    }

    private void injectClient(ProsumerResource target, MySQLPool pool) {
        try {
            Field field = ProsumerResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(ProsumerResource target, boolean value) {
        try {
            Field field = ProsumerResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetWithRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Arrays.asList(rows)));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private RowSet<Row> rowSetWithRowCount(int rowCount) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(rowCount);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn((long) rowCount);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Collections.emptyList()));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private Row prosumerRow(Long id, String name, Long FiscalNumber, String location) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("name")).thenReturn(name);
        Mockito.when(row.getString("location")).thenReturn(location);
        Mockito.when(row.getLong("FiscalNumber")).thenReturn(FiscalNumber);
        return row;
    }

    private Row assetRow(String id, Long prosumerId, String assetType) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumer_id")).thenReturn(prosumerId);
        Mockito.when(row.getString("asset_type")).thenReturn(assetType);
        return row;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        private ListRowIterator(List<Row> rows) {
            this.iterator = rows.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Row next() {
            return iterator.next();
        }
    }
}
