package org.ruralaid.workflow.domain;

public record ReservationId(String id) {
    public ReservationId{
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Reservation ID must not be blank");
        }
    }
}
