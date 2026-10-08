package org.acme;

import java.util.UUID;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class GridBalancingRecommendation {

    public String id;
    public String gridCellId;
    public Double measuredKw;
    public Double maxCapacityKw;
    public String recommendation;
    public Long timestamp;

    public GridBalancingRecommendation() {}

    public Uni<Boolean> save(MySQLPool client) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();

        return client.preparedQuery("INSERT INTO grid_balancing_recommendations (id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp) VALUES (?, ?, ?, ?, ?, ?)")
            .execute(Tuple.tuple()
                .addString(this.id)
                .addString(this.gridCellId)
                .addDouble(this.measuredKw)
                .addDouble(this.maxCapacityKw)
                .addString(this.recommendation)
                .addLong(this.timestamp))
            .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    private static GridBalancingRecommendation from(Row row) {
        GridBalancingRecommendation rec = new GridBalancingRecommendation();
        rec.id = row.getString("id");
        rec.gridCellId = row.getString("grid_cell_id");
        rec.measuredKw = row.getDouble("measured_kw");
        rec.maxCapacityKw = row.getDouble("max_capacity_kw");
        rec.recommendation = row.getString("recommendation");
        rec.timestamp = row.getLong("timestamp");
        return rec;
    }

    public static Multi<GridBalancingRecommendation> findAll(MySQLPool client) {
        return client.query("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations ORDER BY timestamp DESC").execute()
            .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
            .onItem().transform(GridBalancingRecommendation::from);
    }

    public static Uni<GridBalancingRecommendation> findById(MySQLPool client, String id) {
        return client.preparedQuery("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations WHERE id = ?").execute(Tuple.of(id))
            .onItem().transform(RowSet::iterator)
            .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<GridBalancingRecommendation> findByGridCellId(MySQLPool client, String gridCellId) {
        return client.preparedQuery("SELECT id, grid_cell_id, measured_kw, max_capacity_kw, recommendation, timestamp FROM grid_balancing_recommendations WHERE grid_cell_id = ? ORDER BY timestamp DESC").execute(Tuple.of(gridCellId))
            .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
            .onItem().transform(GridBalancingRecommendation::from);
    }
}
