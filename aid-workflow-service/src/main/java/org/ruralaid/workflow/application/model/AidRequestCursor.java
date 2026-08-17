package org.ruralaid.workflow.application.model;

import org.ruralaid.workflow.domain.AidRequestId;

import java.time.Instant;

public record AidRequestCursor(
        Instant createdAt,
        AidRequestId requestId
) {
    public AidRequestCursor {
        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "Cursor creation time must not be null"
            );
        }

        if (requestId == null) {
            throw new IllegalArgumentException(
                    "Cursor request ID must not be null"
            );
        }
    }
}

