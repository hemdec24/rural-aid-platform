package org.ruralaid.logistics.application;

import java.util.Objects;
import java.util.UUID;

import org.ruralaid.logistics.application.exception.InventoryUnavailableException;
import org.ruralaid.logistics.application.exception.ReservationIdConflictException;
import org.ruralaid.logistics.application.port.InventoryReservationRepository;
import org.ruralaid.logistics.application.port.InventoryCacheInvalidator;
import org.ruralaid.logistics.domain.ReleaseOutcome;
import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

public final class InventoryReservationService {

    private final InventoryReservationRepository repository;
    private final InventoryCacheInvalidator cacheInvalidator;

    public InventoryReservationService(InventoryReservationRepository repository) {
        this(repository, inventoryItemId -> { });
    }

    public InventoryReservationService(
            InventoryReservationRepository repository,
            InventoryCacheInvalidator cacheInvalidator
    ) {
        this.repository = Objects.requireNonNull(
                repository, "Inventory reservation repository is required"
        );
        this.cacheInvalidator = Objects.requireNonNull(
                cacheInvalidator,
                "Inventory cache invalidator is required"
        );
    }

    public UUID reserve(ReserveInventory command) {
        Objects.requireNonNull(
                command,
                "Reservation command is required"
        );

        ReservationOutcome outcome = repository.reserve(command);

        return switch (outcome) {
            case RESERVED -> {
                cacheInvalidator.invalidate(command.inventoryItemId());
                yield command.reservationId();
            }

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

        ReleaseOutcome outcome = repository.release(reservationId);

        if (outcome == ReleaseOutcome.RELEASED) {
            repository.findInventoryItemIdForReservation(reservationId)
                    .ifPresent(cacheInvalidator::invalidate);
        }

        return outcome;
    }
}
