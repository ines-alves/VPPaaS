package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class AnalyticsKafkaProducer {

    @Inject
    @Channel("Average-SoC")
    Emitter<AnalyticsRecord> averageSocEmitter;

    @Inject
    @Channel("Energy-Discharged-by-Zone")
    Emitter<AnalyticsRecord> dischargedByZoneEmitter;

    @Inject
    @Channel("Generated-Energy-by-Prosumer")
    Emitter<AnalyticsRecord> generatedEnergyEmitter;

    @Inject
    @Channel("Consumed-Energy-by-Prosumer")
    Emitter<AnalyticsRecord> consumedEnergyEmitter;

    public void sendAnalyticsToDashboard(AnalyticsRecord record) {
        if ("Average-SoC".equals(record.metricName)) {
            averageSocEmitter.send(record);
        } else if ("Energy-Discharged-by-Zone".equals(record.metricName)) {
            dischargedByZoneEmitter.send(record);
        } else if ("Generated-Energy-by-Prosumer".equals(record.metricName)) {
            generatedEnergyEmitter.send(record);
        } else if ("Consumed-Energy-by-Prosumer".equals(record.metricName)) {
            consumedEnergyEmitter.send(record);
        }
    }
}
