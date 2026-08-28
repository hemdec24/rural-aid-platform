package org.ruralaid.logistics.application.model;

import java.util.Objects;
import java.util.UUID;

public record InventoryItemSnapshot(
        UUID inventoryItemId,
        String resourceType,
        int availableQuantity,
        long version
) {

    public InventoryItemSnapshot {
        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );

        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException(
                    "Resource type is required"
            );
        }

        if (availableQuantity < 0) {
            throw new IllegalArgumentException(
                    "Available quantity must not be negative"
            );
        }

        if (version < 0) {
            throw new IllegalArgumentException(
                    "Inventory version must not be negative"
            );
        }
    }
}
