package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class GridCell {

    public String id;
    public Long utilityOperatorId;
    public String address;
    public String postalCode;
    public Double maxCapacityKw;

    public GridCell() {
    }

    public GridCell(String id) {
        this.id = id;
    }

    public GridCell(String id, Long utilityOperatorId) {
        this.id = id;
        this.utilityOperatorId = utilityOperatorId;
    }

    public GridCell(String id, Long utilityOperatorId, String address, String postalCode, Double maxCapacityKw) {
        this.id = id;
        this.utilityOperatorId = utilityOperatorId;
        this.address = address;
        this.postalCode = postalCode;
        this.maxCapacityKw = maxCapacityKw;
    }

    @Override
    public String toString() {
        return "{ id:" + id + ", utilityOperatorId:" + utilityOperatorId + ", address:" + address
                + ", postalCode:" + postalCode + ", maxCapacityKw:" + maxCapacityKw + "}\n";
    }

    private static GridCell from(Row row) {
        return new GridCell(
            row.getString("id"),
            row.getLong("utility_operator_id"),
            row.getString("address"),
            row.getString("postal_code"),
            row.getDouble("max_capacity_kw")
        );
    }

    public static Multi<GridCell> findAll(MySQLPool client) {
        return client.query("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells ORDER BY id ASC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(GridCell::from);
    }

    public static Uni<GridCell> findById(MySQLPool client, String id) {
        return client.preparedQuery("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE id = ?").execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<GridCell> findByUtilityOperatorId(MySQLPool client, Long utilityOperatorId) {
        return client.preparedQuery("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE utility_operator_id = ?").execute(Tuple.of(utilityOperatorId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(GridCell::from);
    }

    public Uni<Boolean> save(MySQLPool client, String id_R, Long utilityOperatorId_R, String address_R, String postalCode_R, Double maxCapacityKw_R) {
        return client.preparedQuery("INSERT INTO grid_cells (id, utility_operator_id, address, postal_code, max_capacity_kw) VALUES (?, ?, ?, ?, ?)")
                .execute(Tuple.of(id_R, utilityOperatorId_R, address_R, postalCode_R, maxCapacityKw_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Uni<Boolean> delete(MySQLPool client, String id_R) {
        return client.preparedQuery("DELETE FROM grid_cells WHERE id = ?").execute(Tuple.of(id_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Uni<Boolean> update(MySQLPool client, String id_R, Long utilityOperatorId_R, String address_R, String postalCode_R, Double maxCapacityKw_R) {
        return client.preparedQuery("UPDATE grid_cells SET utility_operator_id = ?, address = ?, postal_code = ?, max_capacity_kw = ? WHERE id = ?")
                .execute(Tuple.of(utilityOperatorId_R, address_R, postalCode_R, maxCapacityKw_R, id_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Multi<GridCell> findNeighboringCells(MySQLPool client, String cellId) {
        return client.preparedQuery("SELECT id, utility_operator_id, address, postal_code, max_capacity_kw FROM grid_cells WHERE id = ?")
                .execute(Tuple.of(cellId))
                .onItem().transformToMulti(rows -> {
                    java.util.Iterator<Row> it = rows.iterator();
                    if (!it.hasNext()) return Multi.createFrom().empty();
                    String postalCode = it.next().getString("postal_code");
                    if (postalCode == null || postalCode.length() < 4) return Multi.createFrom().empty();
                    try {
                        int district = Integer.parseInt(postalCode.substring(0, 4));
                        String lower = String.format("%04d-000", Math.max(0, district - 1));
                        String upper = String.format("%04d-999", district + 1);
                        return client.preparedQuery(
                                "SELECT id, utility_operator_id, address, postal_code, max_capacity_kw " +
                                "FROM grid_cells WHERE postal_code BETWEEN ? AND ? AND id != ? ORDER BY postal_code ASC")
                                .execute(Tuple.of(lower, upper, cellId))
                                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                                .onItem().transform(GridCell::from);
                    } catch (NumberFormatException e) {
                        return Multi.createFrom().empty();
                    }
                });
    }
}
