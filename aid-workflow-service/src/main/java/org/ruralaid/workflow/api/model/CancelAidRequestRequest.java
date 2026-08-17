package org.ruralaid.workflow.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CancelAidRequestRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank String reason
) {
}

