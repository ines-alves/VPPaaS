package org.acme;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;

public class AnalyticsRecord {

    public Long id;
    
    @JsonProperty("metric_name")
    public String metricName;
    
    @JsonProperty("metric_value")
    public Double metricValue;
    
    @JsonProperty("zone_id")
    public String zoneId;
    
    @JsonProperty("prosumer_id")
    public String prosumerId;
    
    public LocalDateTime timestamp;

    public AnalyticsRecord() {}

    private static AnalyticsRecord from(Row row) {
        AnalyticsRecord record = new AnalyticsRecord();
        record.id = row.getLong("id");
        record.metricName = row.getString("metric_name");
        record.metricValue = row.getDouble("metric_value");
        record.zoneId = row.getString("zone_id");
        record.prosumerId = row.getString("prosumer_id");
        record.timestamp = row.getLocalDateTime("timestamp");
        return record;
    }

    public static Multi<AnalyticsRecord> findAll(MySQLPool client) {
        return client.query("SELECT * FROM analytics_record").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AnalyticsRecord::from);
    }

    public static Uni<AnalyticsRecord> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT * FROM analytics_record WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(rowSet -> {
                    var it = rowSet.iterator();
                    return it.hasNext() ? from(it.next()) : null;
                });
    }

    public Uni<Boolean> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO analytics_record (metric_name, metric_value, zone_id, prosumer_id, timestamp) VALUES (?, ?, ?, ?, ?)")
                .execute(Tuple.of(this.metricName, this.metricValue, this.zoneId, this.prosumerId, this.timestamp != null ? this.timestamp : LocalDateTime.now()))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}
