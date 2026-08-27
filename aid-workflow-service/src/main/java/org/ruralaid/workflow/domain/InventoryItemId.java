package org.ruralaid.workflow.domain;

import java.util.UUID;

public record InventoryItemId(UUID id) {

    public InventoryItemId {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Inventory item ID must not be null"
            );
        }
    }
}