package org.ruralaid.logistics.application.exception;

import java.util.Objects;
import java.util.UUID;

public final class InventoryUnavailableException extends RuntimeException {

    private final UUID reservationId;

    public InventoryUnavailableException(
            UUID reservationId,
            UUID inventoryItemId,
            int quantity
    ) {
        super(
                "Requested inventory is unavailable; reservationId="
                        + reservationId
                        + ", inventoryItemId=" + inventoryItemId
                        + ", quantity=" + quantity
        );

        this.reservationId = Objects.requireNonNull(
                reservationId,
                "Reservation ID is required for unavailable-inventory reporting"
        );

        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required for unavailable-inventory reporting"
        );
    }

    public UUID reservationId() {
        return reservationId;
    }
}