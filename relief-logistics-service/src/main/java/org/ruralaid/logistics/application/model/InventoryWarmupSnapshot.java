package org.ruralaid.logistics.application.model;

import java.util.List;
import java.util.Objects;

public record InventoryWarmupSnapshot(
        long eventWatermark,
        List<InventoryItemSnapshot> inventoryItems
) {

    public InventoryWarmupSnapshot {
        if (eventWatermark < 0) {
            throw new IllegalArgumentException(
                    "Cache event watermark must not be negative"
            );
        }

        Objects.requireNonNull(
                inventoryItems,
                "Warm-up inventory items are required"
        );

        inventoryItems = List.copyOf(inventoryItems);
    }
}
