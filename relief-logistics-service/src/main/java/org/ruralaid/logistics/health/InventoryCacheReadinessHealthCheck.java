package org.ruralaid.logistics.health;

import java.util.Objects;

import com.codahale.metrics.health.HealthCheck;

import org.ruralaid.logistics.cache.InventoryCacheCoherenceManager;

public final class InventoryCacheReadinessHealthCheck extends HealthCheck {

    private final InventoryCacheCoherenceManager coherenceManager;

    public InventoryCacheReadinessHealthCheck(
            InventoryCacheCoherenceManager coherenceManager
    ) {
        this.coherenceManager = Objects.requireNonNull(
                coherenceManager,
                "Inventory cache coherence manager is required"
        );
    }

    @Override
    protected Result check() {
        if (!coherenceManager.isCacheSafe()) {
            return Result.unhealthy(
                    "Inventory cache is bypassed; consumerId=%s, "
                            + "lastEventId=%d, watermark=%d, "
                            + "pollStalenessMillis=%d, "
                            + "maximumPollStalenessMillis=%d, failure=%s",
                    coherenceManager.consumerId(),
                    coherenceManager.lastProcessedEventId(),
                    coherenceManager.observedWatermark(),
                    coherenceManager.pollStalenessMillis(),
                    coherenceManager.maximumPollStalenessMillis(),
                    coherenceManager.lastFailure()
            );
        }

        return Result.healthy(
                "Inventory cache is ready; consumerId=%s, "
                        + "lastEventId=%d, watermark=%d",
                coherenceManager.consumerId(),
                coherenceManager.lastProcessedEventId(),
                coherenceManager.observedWatermark()
        );
    }
}
