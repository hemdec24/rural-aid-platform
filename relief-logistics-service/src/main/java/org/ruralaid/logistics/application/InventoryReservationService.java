package org.ruralaid.logistics.application;

import java.util.Objects;
import java.util.UUID;

import org.ruralaid.logistics.application.exception.InventoryUnavailableException;
import org.ruralaid.logistics.application.exception.ReservationIdConflictException;
import org.ruralaid.logistics.application.port.InventoryReservationRepository;
import org.ruralaid.logistics.domain.ReleaseOutcome;
import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

public final class InventoryReservationService {

    private final InventoryReservationRepository repository;

    public InventoryReservationService(InventoryReservationRepository repository) {
        this.repository = Objects.requireNonNull(
                repository, "Inventory reservation repository is required"
        );
    }

    public UUID reserve(ReserveInventory command) {
        Objects.requireNonNull(
                command,
                "Reservation command is required"
        );

        ReservationOutcome outcome = repository.reserve(command);

        return switch (outcome) {
            case RESERVED -> command.reservationId();

            case UNAVAILABLE ->
                    throw new InventoryUnavailableException(
                            command.reservationId(),
                            command.inventoryItemId(),
                            command.quantity()
                    );

            case RESERVATION_ID_CONFLICT ->
                    throw new ReservationIdConflictException(
                            command.reservationId()
                    );
        };
    }

    public ReleaseOutcome release(UUID reservationId) {
        Objects.requireNonNull(
                reservationId,
                "Reservation ID is required"
        );

        return repository.release(reservationId);
    }
}
