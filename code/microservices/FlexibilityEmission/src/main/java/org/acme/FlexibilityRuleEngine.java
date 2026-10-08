package org.acme;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.acme.model.BatteryPayload;
import org.acme.model.FlexibilityEmissionRequest;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FlexibilityRuleEngine {

    @Inject
    MySQLPool client;

    @Inject
    @Channel("flexibility-offers-out")
    Emitter<FlexibilityOffer> offerEmitter;

    public FlexibilityOffer processFlexibilityRequest(FlexibilityEmissionRequest request) {

        LocalTime now = LocalTime.now();
        // Defined Peak Hours (10am to 5pm) for Arbitrage
        boolean isPeakHour = now.isAfter(LocalTime.of(10, 0)) && now.isBefore(LocalTime.of(17, 0));

        BatteryPayload battery = (BatteryPayload) request.telemetry.payload;

        // Ignore if battery is not online
        if (battery.connectionStatus != null && !battery.connectionStatus.equalsIgnoreCase("ONLINE")) {
            return generateOffer(request.prosumerId, request.telemetry.assetId, battery.socPercent != null ? battery.socPercent : 0.0, "UNAVAILABLE");
        }
        
        // If State of Health < 70%, do not offer aggressive trading
        if (battery.sohPercent != null && battery.sohPercent < 70.0) {
            return generateOffer(request.prosumerId, request.telemetry.assetId, battery.socPercent, "EXCLUDE_AGGRESSIVE_TRADING");
        }

        // If battery is healthy and has high SoC during peak hours, offer to sell energy back to the grid
        if (battery.socPercent != null && battery.socPercent > 90.0 && isPeakHour) {
            return generateOffer(request.prosumerId, request.telemetry.assetId, battery.socPercent, "SELL_ARBITRAGE");
        } 

        // If battery is weak, do not offer to discharge to the grid
        else if (battery.socPercent != null && battery.socPercent < 20.0) {
            return generateOffer(request.prosumerId, request.telemetry.assetId, battery.socPercent, "UNAVAILABLE");
        }

        return null;
    }

    private FlexibilityOffer generateOffer(Long prosumerId, String assetId, double socPercent, String offerType) {
        FlexibilityOffer offer = new FlexibilityOffer();
        offer.id = UUID.randomUUID().toString();
        offer.prosumerId = prosumerId;
        offer.assetId = assetId;
        offer.socPercent = socPercent;
        offer.offerType = offerType;
        return offer;
    }

    public Uni<List<FlexibilityOffer>> emitOffer(List<FlexibilityOffer> offers) {
        if (offers == null || offers.isEmpty()) {
            return Uni.createFrom().item(offers);
        }

        return Multi.createFrom().iterable(offers)
            .onItem().transformToUniAndConcatenate(offer -> offer.save(client)
                .onItem().invoke(saved -> offerEmitter.send(offer))
                .replaceWith(offer))
            .collect().asList();
    }
}