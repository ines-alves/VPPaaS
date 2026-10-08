package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.sqlclient.RowIterator;

@QuarkusTest
public class ProsumerResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @Test
    public void testGetProsumersReturnsList() {
        Row row1 = Mockito.mock(Row.class);
        Mockito.when(row1.getLong("id")).thenReturn(1L);
        Mockito.when(row1.getString("name")).thenReturn("Prosumer A");
        Mockito.when(row1.getLong("FiscalNumber")).thenReturn(12345L);
        Mockito.when(row1.getString("location")).thenReturn("Lisbon");

        Row row2 = Mockito.mock(Row.class);
        Mockito.when(row2.getLong("id")).thenReturn(2L);
        Mockito.when(row2.getString("name")).thenReturn("Prosumer B");
        Mockito.when(row2.getLong("FiscalNumber")).thenReturn(67890L);
        Mockito.when(row2.getString("location")).thenReturn("Porto");

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row1, row2));

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(anyString())).thenReturn(query);

        given()
            .when().get("/Prosumer")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].id", equalTo(1))
            .body("[1].id", equalTo(2));
    }

    @Test
    public void testGetProsumerReturnsOk() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(1L);
        Mockito.when(row.getString("name")).thenReturn("Prosumer A");
        Mockito.when(row.getLong("FiscalNumber")).thenReturn(12345L);
        Mockito.when(row.getString("location")).thenReturn("Lisbon");

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/Prosumer/1")
            .then()
            .statusCode(200)
            .body("id", equalTo(1))
            .body("FiscalNumber", equalTo(12345));
    }

    @Test
    public void testGetProsumerReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf());

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/Prosumer/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testCreateProsumerReturnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(5L);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "name": "Prosumer A",
              "FiscalNumber": 12345,
              "location": "Lisbon"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/Prosumer")
            .then()
            .statusCode(201)
            .header("Location", endsWith("/Prosumer/5"));
    }


    @Test
    public void testDeleteProsumerReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/Prosumer/1")
            .then()
            .statusCode(204);
    }

    @Test
    public void testDeleteProsumerReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/Prosumer/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testGetAssetsForProsumerReturnsList() {
        Row row = assetRow("BATTERY-01", 1L, "BATTERY");

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/Prosumer/1/assets")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("BATTERY-01"));
    }

    @Test
    public void testGetAssetsByTypeReturnsList() {
        Row row = assetRow("BATTERY-01", 1L, "BATTERY");

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/Prosumer/1/BATTERY")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("BATTERY-01"));
    }

    @Test
    public void testGetAssetsByTypeReturnsBadRequest() {
        given()
            .when().get("/Prosumer/1/INVALID")
            .then()
            .statusCode(400);
    }

    @Test
    public void testCreateAssetForProsumerReturnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "id": "BATTERY-01",
              "prosumerId": 1,
              "assetType": "BATTERY"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/Prosumer/1/assets")
            .then()
            .statusCode(201);
    }

    @Test
    public void testCreateAssetViaCreateAssetReturnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "prosumerId": 1,
              "assetType": "BATTERY"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/Prosumer/createAsset")
            .then()
            .statusCode(201);
    }

    @Test
    public void testCreateAssetViaCreateAssetReturnsConflict() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "prosumerId": 1,
              "assetType": "SOLAR"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/Prosumer/createAsset")
            .then()
            .statusCode(409);
    }

    @Test
    public void testCreateAssetViaCreateAssetReturnsBadRequestForInvalidType() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "prosumerId": 1,
                  "assetType": "INVALID"
                }
                """)
            .when().post("/Prosumer/createAsset")
            .then()
            .statusCode(400);
    }

    @Test
    public void testDeleteAssetReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/Prosumer/1/assets/BATTERY-01")
            .then()
            .statusCode(204);
    }

    @Test
    public void testDeleteAssetReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/Prosumer/1/assets/UNKNOWN")
            .then()
            .statusCode(404);
    }

    private Row assetRow(String id, Long prosumerId, String assetType) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumer_id")).thenReturn(prosumerId);
        Mockito.when(row.getString("asset_type")).thenReturn(assetType);
        return row;
    }

    private static io.vertx.mutiny.sqlclient.RowIterator<Row> rowIteratorOf(Row... rows) {
        java.util.Iterator<Row> iterator = List.of(rows).iterator();
        RowIterator<Row> delegate = new RowIterator<Row>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Row next() {
                return iterator.next();
            }
        };
        return new io.vertx.mutiny.sqlclient.RowIterator<>(delegate);
    }
}
