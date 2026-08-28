package org.ruralaid.logistics.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import org.ruralaid.logistics.application.model.ReservationRequestFingerprint;
import org.ruralaid.logistics.application.port.InventoryReservationRepository;
import org.ruralaid.logistics.domain.ReleaseOutcome;
import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

public final class JdbiInventoryReservationRepository implements InventoryReservationRepository {

    private static final String COMPLETED = "COMPLETED";

    private final Jdbi jdbi;

    public JdbiInventoryReservationRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(
                jdbi,
                "Database access dependency is required"
        );
    }

    @Override
    public ReservationOutcome reserve(ReserveInventory command) {
        Objects.requireNonNull(
                command,
                "Reservation command is required"
        );

        String requestFingerprint = ReservationRequestFingerprint.from(command);

        return jdbi.inTransaction(handle -> {
            boolean claimed = claimOperation(
                    handle,
                    command.reservationId(),
                    requestFingerprint
            );

            if (!claimed) {
                return replayOrReject(
                        handle,
                        command.reservationId(),
                        requestFingerprint
                );
            }

            return executeClaimedReservation(
                    handle,
                    command,
                    requestFingerprint
            );
        });
    }

    @Override
    public ReleaseOutcome release(UUID reservationId) {
        Objects.requireNonNull(
                reservationId,
                "Reservation ID is required"
        );

        return jdbi.inTransaction(handle -> {
            Optional<ReleasedReservation> releasedReservation =
                    handle.createQuery("""
                            UPDATE inventory_reservations
                            SET status = 'RELEASED',
                                released_at = CURRENT_TIMESTAMP
                            WHERE reservation_id = :reservationId
                              AND status = 'RESERVED'
                            RETURNING inventory_item_id, quantity
                            """)
                            .bind("reservationId", reservationId)
                            .map((resultSet, context) ->
                                    new ReleasedReservation(
                                            resultSet.getObject(
                                                    "inventory_item_id",
                                                    UUID.class
                                            ),
                                            resultSet.getInt("quantity")
                                    )
                            )
                            .findOne();

            if (releasedReservation.isPresent()) {
                ReleasedReservation reservation =
                        releasedReservation.orElseThrow();

                long inventoryVersion = restoreInventory(
                        handle,
                        reservationId,
                        reservation
                );

                insertCacheEvent(
                        handle,
                        reservation.inventoryItemId(),
                        inventoryVersion
                );

                return ReleaseOutcome.RELEASED;
            }

            return readReleaseReplayOutcome(handle, reservationId);
        });
    }

    @Override
    public Optional<UUID> findInventoryItemIdForReservation(
            UUID reservationId
    ) {
        Objects.requireNonNull(
                reservationId,
                "Reservation ID is required"
        );

        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT inventory_item_id
                        FROM inventory_reservations
                        WHERE reservation_id = :reservationId
                        """)
                        .bind("reservationId", reservationId)
                        .mapTo(UUID.class)
                        .findOne()
        );
    }

    private long restoreInventory(
            Handle handle,
            UUID reservationId,
            ReleasedReservation releasedReservation
    ) {
        Optional<Long> updatedInventoryVersion =
                handle.createQuery("""
                UPDATE inventory_items
                SET available_quantity = available_quantity + :quantity,
                    version = version + 1
                WHERE inventory_item_id = :inventoryItemId
                RETURNING version
                """)
                .bind(
                        "inventoryItemId",
                        releasedReservation.inventoryItemId()
                )
                .bind("quantity", releasedReservation.quantity())
                .mapTo(Long.class)
                .findOne();

        return updatedInventoryVersion.orElseThrow(
                () -> new IllegalStateException(
                    "Released reservation inventory could not be restored; "
                            + "reservationId=" + reservationId
                )
        );
    }

    private ReleaseOutcome readReleaseReplayOutcome(
            Handle handle,
            UUID reservationId
    ) {
        return handle.createQuery("""
                SELECT status
                FROM inventory_reservations
                WHERE reservation_id = :reservationId
                """)
                .bind("reservationId", reservationId)
                .mapTo(String.class)
                .findOne()
                .map(status -> {
                    if ("RELEASED".equals(status)) {
                        return ReleaseOutcome.RELEASED;
                    }

                    throw new IllegalStateException(
                            "Reservation has an unsupported release state; "
                                    + "reservationId=" + reservationId
                                    + ", status=" + status
                    );
                })
                .orElse(ReleaseOutcome.NOT_FOUND);
    }

    private boolean claimOperation(
            Handle handle,
            UUID reservationId,
            String requestFingerprint
    ) {
        int insertedRows = handle.createUpdate("""
                INSERT INTO inventory_reservation_operations (
                    reservation_id,
                    request_fingerprint,
                    operation_status
                ) VALUES (
                    :reservationId,
                    :requestFingerprint,
                    'IN_PROGRESS'
                )
                ON CONFLICT (reservation_id) DO NOTHING
                """)
                .bind("reservationId", reservationId)
                .bind("requestFingerprint", requestFingerprint)
                .execute();

        if (insertedRows != 0 && insertedRows != 1) {
            throw new IllegalStateException(
                    "Unexpected reservation operation claim count; "
                            + "reservationId=" + reservationId
                            + ", affectedRows=" + insertedRows
            );
        }

        return insertedRows == 1;
    }

    private ReservationOutcome replayOrReject(
            Handle handle,
            UUID reservationId,
            String requestFingerprint
    ) {
        StoredReservationOperation storedOperation =
                handle.createQuery("""
                        SELECT
                            request_fingerprint,
                            operation_status,
                            outcome
                        FROM inventory_reservation_operations
                        WHERE reservation_id = :reservationId
                        """)
                        .bind("reservationId", reservationId)
                        .map((resultSet, context) ->
                                new StoredReservationOperation(
                                        resultSet.getString(
                                                "request_fingerprint"
                                        ),
                                        resultSet.getString(
                                                "operation_status"
                                        ),
                                        resultSet.getString("outcome")
                                )
                        )
                        .findOne()
                        .orElseThrow(() -> new IllegalStateException(
                                "Reservation operation is missing after "
                                        + "its identifier was already claimed; "
                                        + "reservationId=" + reservationId
                        ));

        if (!storedOperation.requestFingerprint().equals(requestFingerprint)) {
            return ReservationOutcome.RESERVATION_ID_CONFLICT;
        }

        if (!COMPLETED.equals(storedOperation.operationStatus())) {
            throw new IllegalStateException(
                    "Reservation operation has no terminal result; "
                            + "reservationId=" + reservationId
                            + ", status="
                            + storedOperation.operationStatus()
            );
        }

        return mapStoredOutcome(
                reservationId,
                storedOperation.outcome()
        );
    }

    private ReservationOutcome executeClaimedReservation(
            Handle handle,
            ReserveInventory command,
            String requestFingerprint
    ) {
        Optional<Long> updatedInventoryVersion =
                handle.createQuery("""
                        UPDATE inventory_items
                        SET available_quantity =
                                available_quantity - :quantity,
                            version = version + 1
                        WHERE inventory_item_id = :inventoryItemId
                          AND available_quantity >= :quantity
                        RETURNING version
                        """)
                        .bind(
                                "inventoryItemId",
                                command.inventoryItemId()
                        )
                        .bind("quantity", command.quantity())
                        .mapTo(Long.class)
                        .findOne();

        if (updatedInventoryVersion.isEmpty()) {
            completeOperation(
                    handle,
                    command.reservationId(),
                    requestFingerprint,
                    ReservationOutcome.UNAVAILABLE
            );

            return ReservationOutcome.UNAVAILABLE;
        }

        insertReservation(handle, command);

        completeOperation(
                handle,
                command.reservationId(),
                requestFingerprint,
                ReservationOutcome.RESERVED
        );

        insertCacheEvent(
                handle,
                command.inventoryItemId(),
                updatedInventoryVersion.orElseThrow()
        );

        return ReservationOutcome.RESERVED;
    }

    private void insertCacheEvent(
            Handle handle,
            UUID inventoryItemId,
            long inventoryVersion
    ) {
        long eventId = handle.createQuery("""
                UPDATE inventory_cache_event_clock
                SET last_event_id = last_event_id + 1
                WHERE clock_id = 1
                RETURNING last_event_id
                """)
                .mapTo(long.class)
                .one();

        int insertedRows = handle.createUpdate("""
                INSERT INTO inventory_cache_events (
                    event_id,
                    inventory_item_id,
                    inventory_version
                ) VALUES (
                    :eventId,
                    :inventoryItemId,
                    :inventoryVersion
                )
                """)
                .bind("eventId", eventId)
                .bind("inventoryItemId", inventoryItemId)
                .bind("inventoryVersion", inventoryVersion)
                .execute();

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "Unexpected inventory cache event insert count; "
                            + "inventoryItemId=" + inventoryItemId
                            + ", inventoryVersion=" + inventoryVersion
                            + ", affectedRows=" + insertedRows
            );
        }
    }

    private void insertReservation(
            Handle handle,
            ReserveInventory command
    ) {
        int insertedRows = handle.createUpdate("""
                INSERT INTO inventory_reservations (
                    reservation_id,
                    aid_request_id,
                    inventory_item_id,
                    quantity
                ) VALUES (
                    :reservationId,
                    :aidRequestId,
                    :inventoryItemId,
                    :quantity
                )
                """)
                .bind(
                        "reservationId",
                        command.reservationId()
                )
                .bind(
                        "aidRequestId",
                        command.aidRequestId()
                )
                .bind(
                        "inventoryItemId",
                        command.inventoryItemId()
                )
                .bind("quantity", command.quantity())
                .execute();

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "Unexpected reservation insert count; "
                            + "reservationId=" + command.reservationId()
                            + ", affectedRows=" + insertedRows
            );
        }
    }

    private void completeOperation(
            Handle handle,
            UUID reservationId,
            String requestFingerprint,
            ReservationOutcome outcome
    ) {
        String storedOutcome = toStoredOutcome(outcome);

        int updatedRows = handle.createUpdate("""
                UPDATE inventory_reservation_operations
                SET operation_status = 'COMPLETED',
                    outcome = :outcome,
                    completed_at = CURRENT_TIMESTAMP
                WHERE reservation_id = :reservationId
                  AND request_fingerprint = :requestFingerprint
                  AND operation_status = 'IN_PROGRESS'
                  AND outcome IS NULL
                  AND completed_at IS NULL
                """)
                .bind("outcome", storedOutcome)
                .bind("reservationId", reservationId)
                .bind("requestFingerprint", requestFingerprint)
                .execute();

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Reservation operation could not be completed; "
                            + "reservationId=" + reservationId
                            + ", outcome=" + storedOutcome
                            + ", affectedRows=" + updatedRows
            );
        }
    }

    private ReservationOutcome mapStoredOutcome(
            UUID reservationId,
            String storedOutcome
    ) {
        if (storedOutcome == null) {
            throw new IllegalStateException(
                    "Completed reservation operation has no outcome; "
                            + "reservationId=" + reservationId
            );
        }

        return switch (storedOutcome) {
            case "RESERVED" ->
                    ReservationOutcome.RESERVED;
            case "UNAVAILABLE" ->
                    ReservationOutcome.UNAVAILABLE;
            default -> throw new IllegalStateException(
                    "Reservation operation has an unsupported outcome; "
                            + "reservationId=" + reservationId
                            + ", outcome=" + storedOutcome
            );
        };
    }

    private String toStoredOutcome(ReservationOutcome outcome) {
        return switch (outcome) {
            case RESERVED -> "RESERVED";
            case UNAVAILABLE -> "UNAVAILABLE";
            case RESERVATION_ID_CONFLICT ->
                    throw new IllegalArgumentException(
                            "Reservation ID conflicts are derived "
                                    + "and cannot be stored"
                    );
        };
    }

    private record StoredReservationOperation(
            String requestFingerprint,
            String operationStatus,
            String outcome
    ) {
    }

    private record ReleasedReservation(
            UUID inventoryItemId,
            int quantity
    ) {
    }
}
