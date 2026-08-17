package org.ruralaid.workflow.api.model;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

public record AidRequestResponse(
        String requestId,
        LocationResponse location,
        String needCategory,
        String priority,
        String status,
        String reservationId,
        String reservationFailureReason,

        String dispatchResponderReference,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant dispatchedAt,

        String deliveryConfirmationReference,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant deliveredAt,

        String cancellationReason,
        long version,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {
}

