package org.ruralaid.workflow.domain;

import java.time.Instant;

public record DispatchDetails(
        String responderReference,
        Instant dispatchedAt
) {
    public DispatchDetails {
        if (responderReference == null || responderReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Responder reference must not be blank"
            );
        }

        if (dispatchedAt == null) {
            throw new IllegalArgumentException(
                    "Dispatch time must not be null"
            );
        }
    }
}