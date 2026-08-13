package org.ruralaid.workflow.domain;

import java.time.Instant;

public record DeliveryDetails(
        String confirmationReference,
        Instant deliveredAt
) {
    public DeliveryDetails {
        if (confirmationReference == null
                || confirmationReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Confirmation reference must not be blank"
            );
        }

        if (deliveredAt == null) {
            throw new IllegalArgumentException(
                    "Delivery time must not be null"
            );
        }
    }
}