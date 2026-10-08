package org.acme;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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

class AssetLinkResourceUnitTest {

    private AssetLinkResource resource;
    private MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new AssetLinkResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
    }

    @Test
    void getLinks_returnsList() {
        Row row1 = assetLinkRow(1L, 1L, 10L);
        Row row2 = assetLinkRow(2L, 2L, 11L);
        Row row3 = assetLinkRow(3L, 3L, 12L);
        stubQuery("SELECT * FROM asset_links", rowSetWithRows(row1, row2, row3));

        List<AssetLink> result = resource.getLinks().collect().asList().await().indefinitely();

        assertThat(result, hasSize(3));
        assertThat(result.get(0).id, equalTo(1L));
        assertThat(result.get(0).prosumerId, equalTo(1L));
        assertThat(result.get(0).utilityOperatorId, equalTo(10L));
        assertThat(result.get(1).id, equalTo(2L));
        assertThat(result.get(2).id, equalTo(3L));
    }

    @Test
    void getSingle_returnsAssetLink() {
        Row row = assetLinkRow(1L, 7L, 21L);
        stubPreparedQuery("SELECT * FROM asset_links WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(1L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(200));
        AssetLink entity = (AssetLink) response.getEntity();
        assertThat(entity, notNullValue());
        assertThat(entity.id, equalTo(1L));
        assertThat(entity.prosumerId, equalTo(7L));
        assertThat(entity.utilityOperatorId, equalTo(21L));
    }

    @Test
    void getSingle_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM asset_links WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(999L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    @Test
    void createLink_returnsCreated() {
        AssetLink link = new AssetLink();
        link.prosumerId = 4L;
        link.utilityOperatorId = 15L;
        stubPreparedQuery("INSERT INTO asset_links (prosumer_id, utility_operator_id) VALUES (?, ?)", rowSetWithRowCount(1));

        Response response = resource.createLink(link).await().indefinitely();

        assertThat(response.getStatus(), equalTo(201));
        assertThat(response.getLocation(), notNullValue());
        assertThat(response.getLocation().getPath(), containsString("/AssetLink/"));
    }

    @Test
    void deleteLink_returnsNoContent() {
        stubPreparedQuery("DELETE FROM asset_links WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.deleteLink(1L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(204));
    }

    @Test
    void deleteLink_returnsNotFound() {
        stubPreparedQuery("DELETE FROM asset_links WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.deleteLink(999L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    @Test
    void getLinkByProsumerAndOperator_returnsAssetLink() {
        Row row = assetLinkRow(1L, 1L, 10L);
        stubPreparedQuery("SELECT * FROM asset_links WHERE prosumer_id = ? AND utility_operator_id = ?", rowSetWithRows(row));

        Response response = resource.getLinkByProsumerAndOperator(1L, 10L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(200));
        AssetLink entity = (AssetLink) response.getEntity();
        assertThat(entity, notNullValue());
        assertThat(entity.id, equalTo(1L));
        assertThat(entity.prosumerId, equalTo(1L));
        assertThat(entity.utilityOperatorId, equalTo(10L));
    }

    @Test
    void getLinkByProsumerAndOperator_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM asset_links WHERE prosumer_id = ? AND utility_operator_id = ?", rowSetWithRows());

        Response response = resource.getLinkByProsumerAndOperator(1L, 999L).await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    private void injectClient(AssetLinkResource target, MySQLPool pool) {
        try {
            Field field = AssetLinkResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(AssetLinkResource target, boolean value) {
        try {
            Field field = AssetLinkResource.class.getDeclaredField("schemaCreate");
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

    private RowSet<Row> rowSetWithRowCount(int rowCount) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(rowCount);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Collections.emptyList())));
        return rowSet;
    }

    private Row assetLinkRow(Long id, Long prosumerId, Long utilityOperatorId) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumer_id")).thenReturn(prosumerId);
        Mockito.when(row.getLong("utility_operator_id")).thenReturn(utilityOperatorId);
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
