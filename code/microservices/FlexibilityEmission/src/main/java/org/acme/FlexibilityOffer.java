package org.acme;

import java.util.UUID;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class FlexibilityOffer {
    
    public String id;
    public Long prosumerId;    
    public String assetId;     
    public double socPercent;
    public String offerType;   
    public Long timestamp;

    public FlexibilityOffer() {
    }

    private static FlexibilityOffer from(Row row) {
        FlexibilityOffer offer = new FlexibilityOffer();
        offer.id = row.getString("id");
        offer.prosumerId = row.getLong("prosumer_id");
        offer.assetId = row.getString("asset_id");
        offer.socPercent = row.getDouble("soc_percent");
        offer.offerType = row.getString("offer_type");
        offer.timestamp = row.getLong("timestamp");
        return offer;
    }
    public static Multi<FlexibilityOffer> findAll(MySQLPool client) {
		return client.query("SELECT id, prosumer_id, asset_id, soc_percent, offer_type, timestamp FROM flexibility_offers ORDER BY timestamp ASC").execute()
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(FlexibilityOffer::from);
	}
	
	public static Uni<FlexibilityOffer> findById(MySQLPool client, String id) {
		return client.preparedQuery("SELECT id, prosumer_id, asset_id, soc_percent, offer_type, timestamp FROM flexibility_offers WHERE id = ?").execute(Tuple.of(id))
				.onItem().transform(RowSet::iterator)
				.onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
	}
    public static Multi<FlexibilityOffer> findByProsumerId(MySQLPool client, Long prosumerId) {
        return client.preparedQuery("SELECT id, prosumer_id, asset_id, soc_percent, offer_type, timestamp FROM flexibility_offers WHERE prosumer_id = ? ORDER BY timestamp ASC").execute(Tuple.of(prosumerId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(FlexibilityOffer::from);
    }
    
    public Uni<Boolean> save(MySQLPool client) {
        if (this.id == null) this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();

        return client.preparedQuery("INSERT INTO flexibility_offers (id, prosumer_id, asset_id, soc_percent, offer_type, timestamp) VALUES (?, ?, ?, ?, ?, ?)")
                .execute(Tuple.of(this.id, this.prosumerId, this.assetId, this.socPercent, this.offerType, this.timestamp))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}