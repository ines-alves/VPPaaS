package org.acme;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.acme.model.CreateGridCellRequest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;


class UtilityOperatorResourceUnitTest {

    private UtilityOperatorResource resource;
    private MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new UtilityOperatorResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
    }

    @Test
    void getUtilityOperators_returnsList() {
        Row row1 = utilityOperatorRow(1L, "Operator Norte", "Braga");
        Row row2 = utilityOperatorRow(2L, "Operator Centro", "Coimbra");
        Row row3 = utilityOperatorRow(3L, "Operator Sul", "Faro");
        stubQuery("SELECT id, name, location FROM utility_operators ORDER BY id ASC", rowSetWithRows(row1, row2, row3));

        List<UtilityOperator> result = resource.get().collect().asList().await().indefinitely();

        assertThat(result, hasSize(3));
        assertThat(result.get(0).id, equalTo(1L));
        assertThat(result.get(0).name, equalTo("Operator Norte"));
        assertThat(result.get(0).location, equalTo("Braga"));
        assertThat(result.get(1).name, equalTo("Operator Centro"));
        assertThat(result.get(2).name, equalTo("Operator Sul"));
    }

    @Test
    void getUtilityOperatorById_returnsEntity() {
        Row row = utilityOperatorRow(7L, "Operator Atlantic", "Aveiro");
        stubPreparedQuery("SELECT id, name, location FROM utility_operators WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(7L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(200));
        UtilityOperator entity = (UtilityOperator) response.getEntity();
        assertThat(entity.id, equalTo(7L));
        assertThat(entity.name, equalTo("Operator Atlantic"));
        assertThat(entity.location, equalTo("Aveiro"));
    }

    @Test
    void getUtilityOperatorById_returnsNotFound() {
        stubPreparedQuery("SELECT id, name, location FROM utility_operators WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(999L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    @Test
    void getGridCells_returnsList() {
        Row row1 = gridCellRow("LISBON-01", 1L, "Rua Central", "1000-000", 55.0);
        Row row2 = gridCellRow("LISBON-02", 1L, "Rua Nova", "1000-100", 65.0);
        Row row3 = gridCellRow("PORTO-01", 1L, "Avenida Norte", "4000-000", 80.0);
        stubPreparedQuery("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE utility_operator_id = ?", rowSetWithRows(row1, row2, row3));

        List<GridCell> result = resource.getGridCells(1L).collect().asList().await().indefinitely();

        assertThat(result, hasSize(3));
        assertThat(result.get(0).id, equalTo("LISBON-01"));
        assertThat(result.get(1).id, equalTo("LISBON-02"));
        assertThat(result.get(2).id, equalTo("PORTO-01"));
    }

    @Test
    void getNeighboringCells_returnsEmptyWhenCellNotFound() {
        stubPreparedQuery("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE id = ?", rowSetWithRows());

        List<GridCell> result = resource.getNeighboringCells("NONEXISTENT").collect().asList().await().indefinitely();

        assertThat(result, hasSize(0));
    }

    @Test
    void getNeighboringCells_returnsNeighborsByDistrict() {
        // Target cell has postal code "1000-100" → district=1000, lower="0999-000", upper="1001-999"
        Row targetRow = gridCellRow("LISBON-DOWNTOWN", 1L, "Rua Central", "1000-100", 50.0);
        stubPreparedQuery("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE id = ?", rowSetWithRows(targetRow));

        Row row1 = gridCellRow("ALMADA-01", 2L, "Rua Sul", "0999-500", 60.0);
        Row row2 = gridCellRow("LISBON-UPTOWN", 1L, "Rua Norte", "1000-200", 65.0);
        Row row3 = gridCellRow("LOURES-01", 3L, "Avenida Norte", "1001-100", 70.0);
        stubPreparedQuery(
            "SELECT id, utility_operator_id, address, postal_code, max_capacity_kw " +
            "FROM grid_cells WHERE postal_code BETWEEN ? AND ? AND id != ? ORDER BY postal_code ASC",
            rowSetWithRows(row1, row2, row3));

        List<GridCell> result = resource.getNeighboringCells("LISBON-DOWNTOWN").collect().asList().await().indefinitely();

        assertThat(result, hasSize(3));
        assertThat(result.get(0).id, equalTo("ALMADA-01"));
        assertThat(result.get(0).postalCode, equalTo("0999-500"));
        assertThat(result.get(1).id, equalTo("LISBON-UPTOWN"));
        assertThat(result.get(1).postalCode, equalTo("1000-200"));
        assertThat(result.get(2).id, equalTo("LOURES-01"));
        assertThat(result.get(2).postalCode, equalTo("1001-100"));
    }

    @Test
    void createUtilityOperator_returnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(45L);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("INSERT INTO utility_operators (name, location) VALUES (?, ?)")).thenReturn(query);

        UtilityOperator operator = new UtilityOperator();
        operator.id = 45L;
        operator.name = "Operator Delta";
        operator.location = "Setubal";

        Response response = resource.create(operator).await().indefinitely();

        assertThat(response.getStatus(), equalTo(201));
        assertThat(response.getLocation(), notNullValue());
        assertThat(response.getLocation().getPath(), equalTo("/UtilityOperator/45"));
    }

    @Test
    void createUtilityOperator_returnsCreatedWithProvidedId() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(91L);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("INSERT INTO utility_operators (name, location) VALUES (?, ?)")).thenReturn(query);

        UtilityOperator operator = new UtilityOperator();
        operator.id = 91L;
        operator.name = "Operator Echo";
        operator.location = "Portalegre";

        Response response = resource.create(operator).await().indefinitely();

        assertThat(response.getStatus(), equalTo(201));
        assertThat(response.getLocation().getPath(), equalTo("/UtilityOperator/91"));
    }

    @Test
    void deleteUtilityOperator_returnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("DELETE FROM utility_operators WHERE id = ?")).thenReturn(query);

        Response response = resource.delete(12L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(204));
    }

    @Test
    void deleteUtilityOperator_returnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("DELETE FROM utility_operators WHERE id = ?")).thenReturn(query);

        Response response = resource.delete(999L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    @Test
    void updateUtilityOperator_returnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("UPDATE utility_operators SET name = ?, location = ? WHERE id = ?")).thenReturn(query);

        UtilityOperator operator = new UtilityOperator(12L, "Operator Updated", "Lisbon");
        Response response = resource.update(12L, operator).await().indefinitely();

        assertThat(response.getStatus(), equalTo(204));
    }

    @Test
    void updateUtilityOperator_returnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("UPDATE utility_operators SET name = ?, location = ? WHERE id = ?")).thenReturn(query);

        UtilityOperator operator = new UtilityOperator(999L, "Missing Operator", "Faro");
        Response response = resource.update(999L, operator).await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    @Test
    void createGridCell_returnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES (?, ?, ?, ?, ?)")).thenReturn(query);

        CreateGridCellRequest request = new CreateGridCellRequest();
        request.id = "GC-1";
        request.utilityOperatorId = 1L;
        request.address = "Rua A";
        request.postalCode = "1000-000";
        request.maxCapacityKw = 50.0;

        Response response = resource.createGridCell(request).await().indefinitely();

        assertThat(response.getStatus(), equalTo(201));
        assertThat(response.getLocation(), notNullValue());
        assertThat(response.getLocation().getPath(), equalTo("/UtilityOperator/1/gridcells/GC-1"));
    }

    @Test
    void createGridCell_returnsInternalServerError() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES (?, ?, ?, ?, ?)")).thenReturn(query);

        CreateGridCellRequest request = new CreateGridCellRequest();
        request.id = "GC-999";
        request.utilityOperatorId = 1L;
        request.address = "Rua X";
        request.postalCode = "9999-999";
        request.maxCapacityKw = 10.0;

        Response response = resource.createGridCell(request).await().indefinitely();

        assertThat(response.getStatus(), equalTo(500));
    }

    private void injectClient(UtilityOperatorResource target, MySQLPool pool) {
        try {
            Field field = UtilityOperatorResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(UtilityOperatorResource target, boolean value) {
        try {
            Field field = UtilityOperatorResource.class.getDeclaredField("schemaCreate");
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
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Arrays.asList(rows))));
        return rowSet;
    }

    private Row utilityOperatorRow(Long id, String name, String location) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("name")).thenReturn(name);
        Mockito.when(row.getString("location")).thenReturn(location);
        return row;
    }

    private Row gridCellRow(String id, Long utilityOperatorId, String address, String postalCode, Double maxCapacityKw) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn(id);
        Mockito.when(row.getLong("utility_operator_id")).thenReturn(utilityOperatorId);
        Mockito.when(row.getString("address")).thenReturn(address);
        Mockito.when(row.getString("postal_code")).thenReturn(postalCode);
        Mockito.when(row.getDouble("max_capacity_kw")).thenReturn(maxCapacityKw);
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
