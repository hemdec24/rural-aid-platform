package org.ruralaid.workflow.application.model;

import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.domain.InventoryItemId;
import org.ruralaid.workflow.domain.ReservationId;

/**
 * Complete facts identifying one logical reservation attempt.
 *
 * An ambiguous retry must reuse every field unchanged.
 */
public record ReservationCommand(
        ReservationId reservationId,
        AidRequestId aidRequestId,
        InventoryItemId inventoryItemId,
        int quantity
) {

    public ReservationCommand {
        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "Reservation ID must not be null"
            );
        }

        if (aidRequestId == null) {
            throw new IllegalArgumentException(
                    "Aid request ID must not be null"
            );
        }

        if (inventoryItemId == null) {
            throw new IllegalArgumentException(
                    "Inventory item ID must not be null"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be greater than zero"
            );
        }
    }
}