package org.ruralaid.workflow.api.model;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ReserveAidRequestRequest(
        @NotNull @PositiveOrZero
        Long expectedVersion,

        @NotNull
        UUID inventoryItemId,

        @NotNull @Positive
        Integer quantity
) {
}
