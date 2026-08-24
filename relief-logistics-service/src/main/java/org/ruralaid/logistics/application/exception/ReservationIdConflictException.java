package org.ruralaid.logistics.application.exception;

import java.util.Objects;
import java.util.UUID;

public final class ReservationIdConflictException extends RuntimeException {

    private final UUID reservationId;

    public ReservationIdConflictException(UUID reservationId) {
        super(
                "Reservation ID is already associated with "
                        + "different reservation facts; reservationId="
                        + reservationId
        );

        this.reservationId = Objects.requireNonNull(
                reservationId,
                "Reservation ID is required for conflict reporting"
        );
    }

    public UUID reservationId() {
        return reservationId;
    }
}