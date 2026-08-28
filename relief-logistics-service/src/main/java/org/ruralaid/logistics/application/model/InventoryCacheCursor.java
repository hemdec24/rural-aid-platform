package org.ruralaid.logistics.application.model;

import java.time.Instant;
import java.util.Objects;

public record InventoryCacheCursor(
        String consumerId,
        long lastEventId,
        Instant updatedAt
) {

    public InventoryCacheCursor {
        Objects.requireNonNull(
                consumerId,
                "Cache consumer ID is required"
        );

        if (consumerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cache consumer ID must not be blank"
            );
        }

        if (lastEventId < 0) {
            throw new IllegalArgumentException(
                    "Last cache event ID must not be negative"
            );
        }

        Objects.requireNonNull(
                updatedAt,
                "Cache cursor update time is required"
        );
    }
}
