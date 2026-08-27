package org.ruralaid.workflow.domain;

import java.util.UUID;

public record ReservationId(String id) {

    public ReservationId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Reservation ID must not be blank"
            );
        }

        try {
            id = UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Reservation ID must be a valid UUID",
                    exception
            );
        }
    }

    public static ReservationId generate() {
        return new ReservationId(
                UUID.randomUUID().toString()
        );
    }

    public UUID asUuid() {
        return UUID.fromString(id);
    }
}