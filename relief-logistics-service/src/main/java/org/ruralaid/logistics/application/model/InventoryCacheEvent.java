package org.ruralaid.logistics.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryCacheEvent(
        long eventId,
        UUID inventoryItemId,
        long inventoryVersion,
        Instant createdAt
) {

    public InventoryCacheEvent {
        if (eventId <= 0) {
            throw new IllegalArgumentException(
                    "Cache event ID must be positive"
            );
        }

        Objects.requireNonNull(
                inventoryItemId,
                "Cache event inventory item ID is required"
        );

        if (inventoryVersion <= 0) {
            throw new IllegalArgumentException(
                    "Cache event inventory version must be positive"
            );
        }

        Objects.requireNonNull(
                createdAt,
                "Cache event creation time is required"
        );
    }
}
