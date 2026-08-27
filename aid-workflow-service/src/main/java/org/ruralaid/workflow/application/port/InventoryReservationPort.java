package org.ruralaid.workflow.application.port;

import org.ruralaid.workflow.application.exception.ReservationCallException;
import org.ruralaid.workflow.application.model.ReservationCommand;
import org.ruralaid.workflow.application.model.ReservationResult;
import org.ruralaid.workflow.application.model.ReleaseResult;
import org.ruralaid.workflow.domain.ReservationId;

public interface InventoryReservationPort {

    /**
     * Attempts or safely retries one logical reservation operation.
     *
     * @throws ReservationCallException when Logistics does not return
     *         a trustworthy business outcome
     */
    ReservationResult reserve(ReservationCommand command);

    /**
     * Releases or safely replays release of one reservation.
     */
    ReleaseResult release(ReservationId reservationId);
}
