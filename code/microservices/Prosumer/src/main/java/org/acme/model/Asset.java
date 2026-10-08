package org.acme.model;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class Asset {
    public String id;
    public Long prosumerId;
    public String assetType;

    public Asset() {}

    public Asset(String id, Long prosumerId, String assetType) {
        this.id = id;
        this.prosumerId = prosumerId;
        this.assetType = assetType;
    }

    public static Asset from(Row row) {
        return new Asset(row.getString("id"), row.getLong("prosumer_id"), row.getString("asset_type"));
    }

    public static Multi<Asset> findAll(MySQLPool client) {
        return client.query("SELECT * FROM assets").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Asset::from);
    }

    public static Uni<Asset> findById(MySQLPool client, String id) {
        return client.preparedQuery("SELECT * FROM assets WHERE id = ?").execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<Asset> findByType(MySQLPool client, String assetType) {
        return client.preparedQuery("SELECT * FROM assets WHERE asset_type = ?").execute(Tuple.of(assetType))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Asset::from);
    }

    public static Multi<Asset> findByProsumerId(MySQLPool client, Long prosumerId) {
        return client.preparedQuery("SELECT * FROM assets WHERE prosumer_id = ?").execute(Tuple.of(prosumerId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Asset::from);
    }

    public static Multi<Asset> findByProsumerIdAndType(MySQLPool client, Long prosumerId, String assetType) {
        return client.preparedQuery("SELECT * FROM assets WHERE prosumer_id = ? AND asset_type = ?")
                .execute(Tuple.of(prosumerId, assetType))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Asset::from);
    }

    public Uni<Boolean> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO assets (id, prosumer_id, asset_type) VALUES (?, ?, ?)")
                .execute(Tuple.of(id, prosumerId, assetType))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Uni<Boolean> delete(MySQLPool client, String id) {
        return client.preparedQuery("DELETE FROM assets WHERE id = ?").execute(Tuple.of(id))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}
