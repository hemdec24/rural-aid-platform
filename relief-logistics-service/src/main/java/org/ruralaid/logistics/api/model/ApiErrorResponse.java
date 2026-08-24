package org.ruralaid.logistics.api.model;

public record ApiErrorResponse(
        String code,
        String message
) {
}