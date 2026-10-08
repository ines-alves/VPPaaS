package org.acme;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import static io.restassured.RestAssured.given;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;

@QuarkusTest
public class AssetResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @Test
    public void testGetAssetsReturnsList() {
        Row row1 = assetRow("BATTERY-01", 1L, "BATTERY");
        Row row2 = assetRow("SOLAR-01", 1L, "SOLAR");
        Row row3 = assetRow("EV-01", 2L, "EV_CHARGER");
        stubQuery("SELECT * FROM assets", rowSetWithRows(row1, row2, row3));

        given()
            .when().get("/Asset")
            .then()
            .statusCode(200)
            .body("$", hasSize(3))
            .body("[0].id", equalTo("BATTERY-01"))
            .body("[1].assetType", equalTo("SOLAR"))
            .body("[2].assetType", equalTo("EV_CHARGER"));
    }

    @Test
    public void testGetAssetsReturnsEmptyList() {
        stubQuery("SELECT * FROM assets", rowSetWithRows());

        given()
            .when().get("/Asset")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    public void testGetAssetReturnsOk() {
        Row row = assetRow("EV-99", 7L, "EV_CHARGER");
        stubPreparedQuery("SELECT * FROM assets WHERE id = ?", rowSetWithRows(row));

        given()
            .when().get("/Asset/EV-99")
            .then()
            .statusCode(200)
            .body("id", equalTo("EV-99"))
            .body("assetType", equalTo("EV_CHARGER"));
    }

    @Test
    public void testGetAssetReturnsNotFound() {
        stubPreparedQuery("SELECT * FROM assets WHERE id = ?", rowSetWithRows());

        given()
            .when().get("/Asset/UNKNOWN")
            .then()
            .statusCode(404);
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetWithRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(List.of(rows))));
        return rowSet;
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
