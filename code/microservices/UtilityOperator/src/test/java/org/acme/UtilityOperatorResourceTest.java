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
public class UtilityOperatorResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @Test
    public void testGetUtilityOperatorsReturnsList() {
        Row row1 = Mockito.mock(Row.class);
        Mockito.when(row1.getLong("id")).thenReturn(1L);
        Mockito.when(row1.getString("name")).thenReturn("Operator A");
        Mockito.when(row1.getString("location")).thenReturn("Lisbon");

        Row row2 = Mockito.mock(Row.class);
        Mockito.when(row2.getLong("id")).thenReturn(2L);
        Mockito.when(row2.getString("name")).thenReturn("Operator B");
        Mockito.when(row2.getString("location")).thenReturn("Porto");

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row1, row2));

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(anyString())).thenReturn(query);

        given()
            .when().get("/UtilityOperator")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].id", equalTo(1))
            .body("[1].id", equalTo(2));
    }

    @Test
    public void testGetUtilityOperatorReturnsOk() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(1L);
        Mockito.when(row.getString("name")).thenReturn("Operator A");
        Mockito.when(row.getString("location")).thenReturn("Lisbon");

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/UtilityOperator/1")
            .then()
            .statusCode(200)
            .body("id", equalTo(1))
            .body("name", equalTo("Operator A"));
    }

    @Test
    public void testGetUtilityOperatorReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf());

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/UtilityOperator/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testGetGridCellsForOperator() {
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
            .when().get("/UtilityOperator/1/gridcells")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("GC-1"));
    }

    @Test
    public void testCreateUtilityOperatorReturnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "id": 42,
              "name": "Operator A",
              "location": "Lisbon"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/UtilityOperator")
            .then()
            .statusCode(201)
            .header("Location", endsWith("/UtilityOperator/42"));
    }

    @Test
    public void testUpdateUtilityOperatorReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "name": "Operator A",
              "location": "Lisbon"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().put("/UtilityOperator/1")
            .then()
            .statusCode(204);
    }

    @Test
    public void testUpdateUtilityOperatorReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "name": "Operator A",
              "location": "Lisbon"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().put("/UtilityOperator/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testDeleteUtilityOperatorReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/UtilityOperator/1")
            .then()
            .statusCode(204);
    }

    @Test
    public void testDeleteUtilityOperatorReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/UtilityOperator/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testGetNeighboringCellsReturnsList() {
        Row targetRow = Mockito.mock(Row.class);
        Mockito.when(targetRow.getString("id")).thenReturn("LISBON-DOWNTOWN");
        Mockito.when(targetRow.getLong("utility_operator_id")).thenReturn(1L);
        Mockito.when(targetRow.getString("address")).thenReturn("Rua Central");
        Mockito.when(targetRow.getString("postal_code")).thenReturn("1000-100");
        Mockito.when(targetRow.getDouble("max_capacity_kw")).thenReturn(50.0);

        RowSet<Row> targetRowSet = Mockito.mock(RowSet.class);
        Mockito.when(targetRowSet.iterator()).thenReturn(rowIteratorOf(targetRow));

        PreparedQuery<RowSet<Row>> cellQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(cellQuery.execute(any())).thenReturn(Uni.createFrom().item(targetRowSet));
        Mockito.when(mysqlPool.preparedQuery(
            "SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE id = ?"))
            .thenReturn(cellQuery);

        Row neighborRow = Mockito.mock(Row.class);
        Mockito.when(neighborRow.getString("id")).thenReturn("LOURES-01");
        Mockito.when(neighborRow.getLong("utility_operator_id")).thenReturn(2L);
        Mockito.when(neighborRow.getString("address")).thenReturn("Avenida Norte");
        Mockito.when(neighborRow.getString("postal_code")).thenReturn("1001-000");
        Mockito.when(neighborRow.getDouble("max_capacity_kw")).thenReturn(70.0);

        RowSet<Row> neighborRowSet = Mockito.mock(RowSet.class);
        Mockito.when(neighborRowSet.iterator()).thenReturn(rowIteratorOf(neighborRow));

        PreparedQuery<RowSet<Row>> neighborQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(neighborQuery.execute(any())).thenReturn(Uni.createFrom().item(neighborRowSet));
        Mockito.when(mysqlPool.preparedQuery(
            "SELECT id, utility_operator_id, address, postal_code, max_capacity_kw " +
            "FROM grid_cells WHERE postal_code BETWEEN ? AND ? AND id != ? ORDER BY postal_code ASC"))
            .thenReturn(neighborQuery);

        given()
            .when().get("/UtilityOperator/gridcells/LISBON-DOWNTOWN/neighbors")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo("LOURES-01"))
            .body("[0].postalCode", equalTo("1001-000"));
    }

    @Test
    public void testCreateGridCellViaUtilityOperatorReturnsCreated() {
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
            .when().post("/UtilityOperator/createGridCell")
            .then()
            .statusCode(201)
            .header("Location", endsWith("/UtilityOperator/1/gridcells/GC-1"));
    }

    @Test
    public void testCreateGridCellViaUtilityOperatorReturnsInternalServerError() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "id": "GC-999",
              "utilityOperatorId": 1,
              "address": "Rua X",
              "postalCode": "9999-999",
              "maxCapacityKw": 10.0
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/UtilityOperator/createGridCell")
            .then()
            .statusCode(500);
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
