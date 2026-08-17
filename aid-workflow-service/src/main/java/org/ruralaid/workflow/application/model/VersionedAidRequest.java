package org.ruralaid.workflow.application.model;

import org.ruralaid.workflow.domain.AidRequest;

import java.time.Instant;

public record VersionedAidRequest(
        AidRequest aggregate,
        long version,
        Instant createdAt
) {
    public VersionedAidRequest {
        if (aggregate == null) {
            throw new IllegalArgumentException(
                    "Aid request must not be null"
            );
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "Version must not be negative"
            );
        }
        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "Creation time must not be null"
            );
        }
    }
}
