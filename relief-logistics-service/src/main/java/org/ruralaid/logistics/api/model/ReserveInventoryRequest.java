package org.ruralaid.logistics.api.model;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReserveInventoryRequest(
        @NotNull(message = "reservationId is required")
        UUID reservationId,

        @NotBlank(message = "aidRequestId must not be blank")
        String aidRequestId,

        @NotNull(message = "inventoryItemId is required")
        UUID inventoryItemId,

        @Positive(message = "quantity must be greater than zero")
        int quantity
) {
}