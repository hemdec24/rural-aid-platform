package org.ruralaid.workflow.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ExpectedVersionRequest(
        @NotNull @PositiveOrZero Long expectedVersion
) {
}

