package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class AssetLink {

    public Long id;
    public Long prosumerId;
    public Long utilityOperatorId;

    public AssetLink() {
    }

    public AssetLink(Long id, Long prosumerId, Long utilityOperatorId) {
        this.id = id;
        this.prosumerId = prosumerId;
        this.utilityOperatorId = utilityOperatorId;
    }

    @Override
    public String toString() {
        return "{id:" + id + ", prosumerId:" + prosumerId + ", utilityOperatorId:" + utilityOperatorId + "}\n";
    }

    private static AssetLink from(Row row) {
        return new AssetLink(
            row.getLong("id"),
            row.getLong("prosumer_id"),
            row.getLong("utility_operator_id")
        );
    }

    public static Multi<AssetLink> findAll(MySQLPool client) {
        return client.query("SELECT * FROM asset_links").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AssetLink::from);
    }

    public static Uni<AssetLink> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT * FROM asset_links WHERE id = ?").execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Uni<AssetLink> findById2(MySQLPool client, Long prosumerId_R, Long utilityOperatorId_R) {
        return client.preparedQuery("SELECT * FROM asset_links WHERE prosumer_id = ? AND utility_operator_id = ?")
                .execute(Tuple.of(prosumerId_R, utilityOperatorId_R))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public Uni<Boolean> save(MySQLPool client, Long prosumerId_R, Long utilityOperatorId_R) {
        return client.preparedQuery("INSERT INTO asset_links (prosumer_id, utility_operator_id) VALUES (?, ?)")
                .execute(Tuple.of(prosumerId_R, utilityOperatorId_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Uni<Boolean> delete(MySQLPool client, Long id_R) {
        return client.preparedQuery("DELETE FROM asset_links WHERE id = ?").execute(Tuple.of(id_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Uni<Boolean> update(MySQLPool client, Long id_R, Long prosumerId_R, Long utilityOperatorId_R) {
        return client.preparedQuery("UPDATE asset_links SET prosumer_id = ?, utility_operator_id = ? WHERE id = ?")
                .execute(Tuple.of(prosumerId_R, utilityOperatorId_R, id_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}
