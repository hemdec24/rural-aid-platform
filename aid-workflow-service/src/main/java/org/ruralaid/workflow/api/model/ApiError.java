package org.ruralaid.workflow.api.model;

public record ApiError(
        String code,
        String message
) {
}

