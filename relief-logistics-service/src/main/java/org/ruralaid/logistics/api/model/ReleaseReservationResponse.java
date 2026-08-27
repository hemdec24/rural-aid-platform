package org.ruralaid.logistics.api.model;

import java.util.UUID;

public record ReleaseReservationResponse(
        UUID reservationId,
        String outcome
) {
}
