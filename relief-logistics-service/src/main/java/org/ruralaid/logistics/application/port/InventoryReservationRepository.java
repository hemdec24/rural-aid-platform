package org.ruralaid.logistics.application.port;

import java.util.UUID;
import java.util.Optional;

import org.ruralaid.logistics.domain.ReleaseOutcome;
import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

public interface InventoryReservationRepository {

    ReservationOutcome reserve(ReserveInventory command);

    ReleaseOutcome release(UUID reservationId);

    Optional<UUID> findInventoryItemIdForReservation(UUID reservationId);
}
