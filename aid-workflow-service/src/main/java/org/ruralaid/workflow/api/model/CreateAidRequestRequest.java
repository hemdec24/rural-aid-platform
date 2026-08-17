package org.ruralaid.workflow.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAidRequestRequest(
        @NotNull @Valid LocationRequest location,
        @NotBlank String needCategory,
        @NotBlank String priority
) {
}

