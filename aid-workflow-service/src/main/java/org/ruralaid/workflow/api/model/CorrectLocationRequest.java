package org.ruralaid.workflow.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CorrectLocationRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @Valid LocationRequest location
) {
}

