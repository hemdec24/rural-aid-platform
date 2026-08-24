package org.ruralaid.logistics.api.model;

import java.util.UUID;

public record ReserveInventoryResponse(
        UUID reservationId,
        String outcome
) {
}