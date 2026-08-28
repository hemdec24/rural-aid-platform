package org.ruralaid.logistics.api.model;

import java.util.UUID;

public record InventoryItemResponse(
        UUID inventoryItemId,
        String resourceType,
        int availableQuantity,
        long version
) {
}
