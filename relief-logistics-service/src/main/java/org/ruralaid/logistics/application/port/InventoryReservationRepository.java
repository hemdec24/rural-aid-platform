package org.ruralaid.logistics.application.port;

import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

public interface InventoryReservationRepository {

    ReservationOutcome reserve(ReserveInventory command);
}

