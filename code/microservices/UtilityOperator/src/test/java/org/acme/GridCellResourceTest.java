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
public class GridCellResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @Test
    public void testGetGridCellsReturnsList() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("GC-1");
        Mockito.when(row.getLong("utility_operator_id")).thenReturn(1L);
        Mockito.when(row.getString("address")).thenReturn("Rua A");
        Mockito.when(row.getString("postal_code")).thenReturn("1000-000");
        Mockito.when(row.getDouble("max_capacity_kw")).thenReturn(50.0);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(anyString())).thenReturn(query);

        given()
            .when().get("/GridCell")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("GC-1"));
    }

    @Test
    public void testGetGridCellReturnsOk() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("id")).thenReturn("GC-1");
        Mockito.when(row.getLong("utility_operator_id")).thenReturn(1L);
        Mockito.when(row.getString("address")).thenReturn("Rua A");
        Mockito.when(row.getString("postal_code")).thenReturn("1000-000");
        Mockito.when(row.getDouble("max_capacity_kw")).thenReturn(50.0);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/GridCell/GC-1")
            .then()
            .statusCode(200)
            .body("id", equalTo("GC-1"));
    }

    @Test
    public void testGetGridCellReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf());

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/GridCell/UNKNOWN")
            .then()
            .statusCode(404);
    }

    @Test
    public void testCreateGridCellReturnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "id": "GC-1",
              "utilityOperatorId": 1,
              "address": "Rua A",
              "postalCode": "1000-000",
              "maxCapacityKw": 50.0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/GridCell")
            .then()
            .statusCode(201)
            .header("Location", endsWith("/GridCell/GC-1"));
    }

    @Test
    public void testUpdateGridCellReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "id": "GC-1",
              "utilityOperatorId": 1,
              "address": "Rua A",
              "postalCode": "1000-000",
              "maxCapacityKw": 55.0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().put("/GridCell/GC-1")
            .then()
            .statusCode(204);
    }

    @Test
    public void testUpdateGridCellReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "id": "GC-1",
              "utilityOperatorId": 1,
              "address": "Rua A",
              "postalCode": "1000-000",
              "maxCapacityKw": 55.0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().put("/GridCell/UNKNOWN")
            .then()
            .statusCode(404);
    }

    @Test
    public void testDeleteGridCellReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/GridCell/GC-1")
            .then()
            .statusCode(204);
    }

    @Test
    public void testDeleteGridCellReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/GridCell/UNKNOWN")
            .then()
            .statusCode(404);
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
