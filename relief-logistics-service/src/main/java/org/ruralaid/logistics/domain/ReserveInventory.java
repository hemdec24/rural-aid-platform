package org.ruralaid.logistics.domain;

import java.util.Objects;
import java.util.UUID;

public record ReserveInventory(
        UUID reservationId,
        String aidRequestId,
        UUID inventoryItemId,
        int quantity
) {
    public ReserveInventory {
        Objects.requireNonNull(
                reservationId,
                "Reservation ID is required"
        );

        if (aidRequestId == null || aidRequestId.isBlank()) {
            throw new IllegalArgumentException("Aid request must not be blank");
        }

        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be greater than zero when creating "
                            + "ReserveInventory; received: " + quantity
            );
        }
    }
}

