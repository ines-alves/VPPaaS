package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Tuple;
import java.util.UUID;

/**
 * Domain model for a Flexibility Forecast.
 * Mirrors the structure of FlexibilityOffer in FlexibilityEmission.
 */
public class FlexibilityForecast {

    public String id;
    public Long prosumerId;
    public String assetId;
    public String forecastType;        // e.g. "HIGH_FLEXIBILITY", "LOW_FLEXIBILITY", "UNAVAILABLE"
    public Double predictedSocPercent; // Predicted SoC at next evaluation window
    public String recommendation;      // Natural-language summary produced by LLM
    public Double confidenceScore;     // 0.0–1.0 confidence from sentiment/success analysis
    public Long timestamp;

    public FlexibilityForecast() {}

    /**
     * Persists this forecast to the database and returns the generated ID.
     */
    public Uni<Boolean> save(MySQLPool client) {
        this.id        = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();

        return client.preparedQuery(
                "INSERT INTO flexibility_forecasts " +
                "(id, prosumer_id, asset_id, forecast_type, predicted_soc_percent, recommendation, confidence_score, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
            .execute(Tuple.wrap(new Object[]{
                this.id,
                this.prosumerId,
                this.assetId,
                this.forecastType,
                this.predictedSocPercent,
                this.recommendation,
                this.confidenceScore,
                this.timestamp}))
            .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}