package org.ruralaid.logistics.api.model;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveInventoryRequest(
        @NotNull(message = "reservationId is required")
        UUID reservationId,

        @NotNull(message = "aidRequestId is required")
        UUID aidRequestId,

        @NotNull(message = "inventoryItemId is required")
        UUID inventoryItemId,

        @Positive(message = "quantity must be greater than zero")
        int quantity
) {
}