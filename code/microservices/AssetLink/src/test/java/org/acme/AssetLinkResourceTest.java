package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
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
public class AssetLinkResourceTest {

    @InjectMock
    MySQLPool mysqlPool;

    @Test
    public void testGetLinksReturnsList() {
        Row row1 = Mockito.mock(Row.class);
        Mockito.when(row1.getLong("id")).thenReturn(1L);
        Mockito.when(row1.getLong("prosumer_id")).thenReturn(1L);
        Mockito.when(row1.getLong("utility_operator_id")).thenReturn(10L);

        Row row2 = Mockito.mock(Row.class);
        Mockito.when(row2.getLong("id")).thenReturn(2L);
        Mockito.when(row2.getLong("prosumer_id")).thenReturn(2L);
        Mockito.when(row2.getLong("utility_operator_id")).thenReturn(11L);

        Row row3 = Mockito.mock(Row.class);
        Mockito.when(row3.getLong("id")).thenReturn(3L);
        Mockito.when(row3.getLong("prosumer_id")).thenReturn(3L);
        Mockito.when(row3.getLong("utility_operator_id")).thenReturn(12L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row1, row2, row3));

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.query(anyString())).thenReturn(query);

        given()
            .when().get("/AssetLink")
            .then()
            .statusCode(200)
            .body("$", hasSize(3))
            .body("[0].id", equalTo(1))
            .body("[1].id", equalTo(2))
            .body("[2].id", equalTo(3));
    }

    @Test
    public void testGetSingleReturnsOk() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(1L);
        Mockito.when(row.getLong("prosumer_id")).thenReturn(1L);
        Mockito.when(row.getLong("utility_operator_id")).thenReturn(10L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/AssetLink/1")
            .then()
            .statusCode(200)
            .body("id", equalTo(1));
    }

    @Test
    public void testGetSingleReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf());

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/AssetLink/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testCreateLinkReturnsCreated() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);
        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        String payload = """
            {
              "prosumerId": 5,
              "utilityOperatorId": 10
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when().post("/AssetLink")
            .then()
            .statusCode(201);
    }

    @Test
    public void testDeleteLinkReturnsNoContent() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(1);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/AssetLink/1")
            .then()
            .statusCode(204);
    }

    @Test
    public void testDeleteLinkReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(0);

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().delete("/AssetLink/999")
            .then()
            .statusCode(404);
    }

    @Test
    public void testGetLinkByProsumerAndOperatorReturnsOk() {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(1L);
        Mockito.when(row.getLong("prosumer_id")).thenReturn(1L);
        Mockito.when(row.getLong("utility_operator_id")).thenReturn(10L);

        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf(row));

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/AssetLink/1/10")
            .then()
            .statusCode(200)
            .body("id", equalTo(1))
            .body("prosumerId", equalTo(1))
            .body("utilityOperatorId", equalTo(10));
    }

    @Test
    public void testGetLinkByProsumerAndOperatorReturnsNotFound() {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.iterator()).thenReturn(rowIteratorOf());

        PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
        Mockito.when(query.execute(any())).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(mysqlPool.preparedQuery(anyString())).thenReturn(query);

        given()
            .when().get("/AssetLink/1/999")
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
