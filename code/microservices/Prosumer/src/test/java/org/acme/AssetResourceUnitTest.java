package org.acme;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.acme.model.Asset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;


class AssetResourceUnitTest {

    private AssetResource resource;
    private MySQLPool mysqlPool;

    @BeforeEach
    void setup() {
        resource = new AssetResource();
        mysqlPool = Mockito.mock(MySQLPool.class);
        injectClient(resource, mysqlPool);
    }

    @Test
    void get_returnsDifferentAssetTypes() {
        Row battery = assetRow("BATTERY-01", 11L, "BATTERY");
        Row solar = assetRow("SOLAR-02", 12L, "SOLAR");
        Row ev = assetRow("EV-03", 13L, "EV_CHARGER");
        stubQuery("SELECT * FROM assets", rowSetWithRows(battery, solar, ev));

        List<Asset> result = resource.get().collect().asList().await().indefinitely();

        assertThat(result, hasSize(3));
        assertThat(result.get(0).id, equalTo("BATTERY-01"));
        assertThat(result.get(0).assetType, equalTo("BATTERY"));
        assertThat(result.get(1).id, equalTo("SOLAR-02"));
        assertThat(result.get(1).assetType, equalTo("SOLAR"));
        assertThat(result.get(2).id, equalTo("EV-03"));
        assertThat(result.get(2).assetType, equalTo("EV_CHARGER"));
    }

    @Test
    void getSingle_returnsBatteryAsset() {
        Row row = assetRow("BATTERY-POCKET-02", 22L, "BATTERY");
        stubPreparedQuery("SELECT * FROM assets WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle("BATTERY-POCKET-02").await().indefinitely();

        assertThat(response.getStatus(), equalTo(200));
        Asset entity = (Asset) response.getEntity();
        assertThat(entity, notNullValue());
        assertThat(entity.id, equalTo("BATTERY-POCKET-02"));
        assertThat(entity.prosumerId, equalTo(22L));
        assertThat(entity.assetType, equalTo("BATTERY"));
    }

    @Test
    void getSingle_returnsSolarAsset() {
        Row row = assetRow("SOLAR-ROOF-17", 21L, "SOLAR");
        stubPreparedQuery("SELECT * FROM assets WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle("SOLAR-ROOF-17").await().indefinitely();

        assertThat(response.getStatus(), equalTo(200));
        Asset entity = (Asset) response.getEntity();
        assertThat(entity.assetType, equalTo("SOLAR"));
        assertThat(entity.prosumerId, equalTo(21L));
    }

    @Test
    void getSingle_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM assets WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle("MISSING-ASSET").await().indefinitely();

        assertThat(response.getStatus(), equalTo(404));
    }

    private void injectClient(AssetResource target, MySQLPool pool) {
        try {
            Field field = AssetResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetWithRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(rowIteratorOf(Arrays.asList(rows))));
        return rowSet;
    }

    private Row assetRow(String id, Long prosumerId, String assetType) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumer_id")).thenReturn(prosumerId);
        Mockito.when(row.getString("asset_type")).thenReturn(assetType);
        return row;
    }

    private RowIterator<Row> rowIteratorOf(List<Row> rows) {
        java.util.Iterator<Row> iterator = rows.iterator();
        return new RowIterator<Row>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Row next() {
                return iterator.next();
            }
        };
    }
}
