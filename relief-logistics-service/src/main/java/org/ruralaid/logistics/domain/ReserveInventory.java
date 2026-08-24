package org.ruralaid.logistics.domain;

import java.util.Objects;
import java.util.UUID;

public record ReserveInventory(
        UUID reservationId,
        UUID aidRequestId,
        UUID inventoryItemId,
        int quantity
) {
    public ReserveInventory {
        Objects.requireNonNull(
                reservationId,
                "Reservation ID is required"
        );

        Objects.requireNonNull(
                aidRequestId,
                "Aid request ID is required"
        );

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

