package org.ruralaid.workflow.domain;

public record ReservationFailureReason(String reason) {

    public ReservationFailureReason {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Reservation failure reason must not be blank"
            );
        }
    }
}