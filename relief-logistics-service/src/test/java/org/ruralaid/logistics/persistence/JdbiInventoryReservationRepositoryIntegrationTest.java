package org.ruralaid.logistics.persistence;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReleaseOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class JdbiInventoryReservationRepositoryIntegrationTest {

    private static Jdbi jdbi;
    private static JdbiInventoryReservationRepository repository;

    @BeforeAll
    static void setUpDatabaseAccess() {
        jdbi = createJdbi();
        repository = new JdbiInventoryReservationRepository(jdbi);
    }

    private static Jdbi createJdbi() {
        String url = System.getenv(
                "RELIEF_LOGISTICS_IT_DB_URL"
        );
        String user = System.getenv(
                "RELIEF_LOGISTICS_IT_DB_USER"
        );
        String password = System.getenv(
                "RELIEF_LOGISTICS_IT_DB_PASSWORD"
        );

        assumeTrue(
                isPresent(url)
                        && isPresent(user)
                        && isPresent(password),
                "Relief Logistics PostgreSQL integration-test "
                        + "variables are required"
        );

        return Jdbi.create(url, user, password);
    }

    /*
     * 6A — An exact retry must replay RESERVED without applying
     * the inventory effect twice.
     */
    @Test
    void replaysReservedOutcomeWithoutApplyingInventoryTwice() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        ReserveInventory command = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                4
        );

        insertInventory(inventoryItemId, 10);

        try {
            ReservationOutcome firstOutcome =
                    repository.reserve(command);

            ReservationOutcome retryOutcome =
                    repository.reserve(command);

            assertAll(
                    () -> assertEquals(
                            ReservationOutcome.RESERVED,
                            firstOutcome
                    ),
                    () -> assertEquals(
                            ReservationOutcome.RESERVED,
                            retryOutcome
                    ),
                    () -> assertEquals(
                            6,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            4,
                            reservationQuantity(reservationId)
                    ),
                    () -> assertEquals(
                            1,
                            operationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "RESERVED",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    /*
     * 6B — Reusing an operation identity for different business
     * input must return a conflict without applying another effect.
     */
    @Test
    void rejectsReservationIdReuseWhenRequestChanges() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        ReserveInventory firstCommand = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                4
        );

        ReserveInventory changedCommand = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                4
        );

        insertInventory(inventoryItemId, 10);

        try {
            ReservationOutcome firstOutcome =
                    repository.reserve(firstCommand);

            ReservationOutcome retryOutcome =
                    repository.reserve(changedCommand);

            assertAll(
                    () -> assertEquals(
                            ReservationOutcome.RESERVED,
                            firstOutcome
                    ),
                    () -> assertEquals(
                            ReservationOutcome
                                    .RESERVATION_ID_CONFLICT,
                            retryOutcome
                    ),
                    () -> assertEquals(
                            6,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            4,
                            reservationQuantity(reservationId)
                    ),
                    () -> assertEquals(
                            1,
                            operationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "RESERVED",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    /*
     * 6C — UNAVAILABLE is a terminal result for this operation
     * identity and must be replayed even if inventory later changes.
     */
    @Test
    void replaysUnavailableOutcomeAfterInventoryIsReplenished() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        ReserveInventory command = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                4
        );

        insertInventory(inventoryItemId, 3);

        try {
            ReservationOutcome firstOutcome =
                    repository.reserve(command);

            setAvailableQuantity(inventoryItemId, 10);

            ReservationOutcome retryOutcome =
                    repository.reserve(command);

            assertAll(
                    () -> assertEquals(
                            ReservationOutcome.UNAVAILABLE,
                            firstOutcome
                    ),
                    () -> assertEquals(
                            ReservationOutcome.UNAVAILABLE,
                            retryOutcome
                    ),
                    () -> assertEquals(
                            10,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            0,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            1,
                            operationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "UNAVAILABLE",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    /*
     * A pre-existing reservation without an operation row simulates
     * legacy/inconsistent data. The repository claims the operation
     * and decrements inventory before the duplicate reservation insert
     * fails. The transaction must roll both earlier changes back.
     */
    @Test
    void rollsBackClaimAndDecrementWhenReservationInsertFails() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        insertInventory(inventoryItemId, 8);

        insertReservationFixture(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                2
        );

        try {
            ReserveInventory command = new ReserveInventory(
                    reservationId,
                    UUID.randomUUID().toString(),
                    inventoryItemId,
                    3
            );

            assertThrows(
                    UnableToExecuteStatementException.class,
                    () -> repository.reserve(command)
            );

            assertAll(
                    () -> assertEquals(
                            8,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            2,
                            reservationQuantity(reservationId)
                    ),
                    () -> assertEquals(
                            0,
                            operationCount(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    /**
     * Concurrency test using reserveConcurrently()
     * Scheduling of threads concurrency using CountDownLatch
     * @throws Exception
     */
    @Test
    void preventsOverdrawWhenTwoReservationsCompete()
            throws Exception {
        UUID inventoryItemId = UUID.randomUUID();
        UUID firstReservationId = UUID.randomUUID();
        UUID secondReservationId = UUID.randomUUID();

        ReserveInventory firstCommand =
                new ReserveInventory(
                        firstReservationId,
                        UUID.randomUUID().toString(),
                        inventoryItemId,
                        4
                );

        ReserveInventory secondCommand =
                new ReserveInventory(
                        secondReservationId,
                        UUID.randomUUID().toString(),
                        inventoryItemId,
                        4
                );

        insertInventory(inventoryItemId, 5);

        try {
            List<ReservationOutcome> outcomes =
                    reserveConcurrently(
                            List.of(
                                    firstCommand,
                                    secondCommand
                            )
                    );

            long reservedCount = outcomes.stream()
                    .filter(
                            outcome ->
                                    outcome
                                            == ReservationOutcome.RESERVED
                    )
                    .count();

            long unavailableCount = outcomes.stream()
                    .filter(
                            outcome ->
                                    outcome
                                            == ReservationOutcome.UNAVAILABLE
                    )
                    .count();

            assertAll(
                    () -> assertEquals(
                            1L,
                            reservedCount
                    ),
                    () -> assertEquals(
                            1L,
                            unavailableCount
                    ),
                    () -> assertEquals(
                            1,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(firstReservationId)
                                    + reservationCount(
                                    secondReservationId
                            )
                    ),
                    () -> assertEquals(
                            4,
                            reservedQuantityForItem(
                                    inventoryItemId
                            )
                    ),
                    () -> assertEquals(
                            1,
                            operationCount(firstReservationId)
                    ),
                    () -> assertEquals(
                            1,
                            operationCount(secondReservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(firstReservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(secondReservationId)
                    ),
                    () -> assertEquals(
                            outcomes.get(0).name(),
                            operationOutcome(firstReservationId)
                    ),
                    () -> assertEquals(
                            outcomes.get(1).name(),
                            operationOutcome(secondReservationId)
                    )
            );
        } finally {
            deleteFixture(
                    inventoryItemId,
                    firstReservationId,
                    secondReservationId
            );
        }
    }

    /**
     * Concurrency test using reserveConcurrently()
     * Scheduling of threads concurrency using CountDownLatch
     * @throws Exception
     */
    @Test
    void appliesConcurrentExactRetryOnlyOnce()
            throws Exception {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        ReserveInventory command =
                new ReserveInventory(
                        reservationId,
                        UUID.randomUUID().toString(),
                        inventoryItemId,
                        4
                );

        insertInventory(inventoryItemId, 10);

        try {
            List<ReservationOutcome> outcomes =
                    reserveConcurrently(
                            List.of(
                                    command,
                                    command
                            )
                    );

            assertAll(
                    () -> assertEquals(
                            List.of(
                                    ReservationOutcome.RESERVED,
                                    ReservationOutcome.RESERVED
                            ),
                            outcomes
                    ),
                    () -> assertEquals(
                            6,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            4,
                            reservationQuantity(reservationId)
                    ),
                    () -> assertEquals(
                            1,
                            operationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "RESERVED",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(
                    inventoryItemId,
                    reservationId
            );
        }
    }

    @Test
    void repeatedReleaseRestoresInventoryOnlyOnce() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        ReserveInventory command = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                4
        );

        insertInventory(inventoryItemId, 10);

        try {
            assertEquals(
                    ReservationOutcome.RESERVED,
                    repository.reserve(command)
            );

            ReleaseOutcome firstRelease =
                    repository.release(reservationId);

            ReleaseOutcome replayedRelease =
                    repository.release(reservationId);

            assertAll(
                    () -> assertEquals(
                            ReleaseOutcome.RELEASED,
                            firstRelease
                    ),
                    () -> assertEquals(
                            ReleaseOutcome.RELEASED,
                            replayedRelease
                    ),
                    () -> assertEquals(
                            10,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            "RELEASED",
                            reservationStatus(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    private static List<ReservationOutcome> reserveConcurrently(
            List<ReserveInventory> commands
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(commands.size());

        CountDownLatch readyLatch = new CountDownLatch(commands.size());

        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<ReservationOutcome>> futures = new ArrayList<>();

        try {
            for (ReserveInventory command : commands) {
                futures.add(
                        executor.submit(() -> {
                            readyLatch.countDown();

                            boolean released = startLatch.await(
                                    5,
                                    TimeUnit.SECONDS
                            );

                            if (!released) {
                                throw new IllegalStateException(
                                        "Concurrent reservation start "
                                                + "was not released"
                                );
                            }

                            return repository.reserve(command);
                        })
                );
            }

            boolean allWorkersReady = readyLatch.await(
                    5,
                    TimeUnit.SECONDS
            );

            if (!allWorkersReady) {
                throw new IllegalStateException(
                        "Reservation workers did not become ready"
                );
            }

            startLatch.countDown();

            List<ReservationOutcome> outcomes = new ArrayList<>();

            for (Future<ReservationOutcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }

            return List.copyOf(outcomes);
        } finally {
            // Prevent waiting workers from being stranded if setup fails.
            startLatch.countDown();

            executor.shutdownNow();
            executor.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            );
        }
    }

    private static void insertInventory(
            UUID inventoryItemId,
            int availableQuantity
    ) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO inventory_items (
                                    inventory_item_id,
                                    resource_type,
                                    available_quantity
                                ) VALUES (
                                    :inventoryItemId,
                                    :resourceType,
                                    :availableQuantity
                                )
                                """)
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .bind("resourceType", "WATER_KIT")
                        .bind(
                                "availableQuantity",
                                availableQuantity
                        )
                        .execute()
        );
    }

    private static void insertReservationFixture(
            UUID reservationId,
            String aidRequestId,
            UUID inventoryItemId,
            int quantity
    ) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
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
                        .bind("reservationId", reservationId)
                        .bind("aidRequestId", aidRequestId)
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .bind("quantity", quantity)
                        .execute()
        );
    }

    private static void setAvailableQuantity(
            UUID inventoryItemId,
            int availableQuantity
    ) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE inventory_items
                                SET available_quantity =
                                    :availableQuantity
                                WHERE inventory_item_id =
                                    :inventoryItemId
                                """)
                        .bind(
                                "availableQuantity",
                                availableQuantity
                        )
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .execute()
        );
    }

    private static int availableQuantity(
            UUID inventoryItemId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT available_quantity
                                FROM inventory_items
                                WHERE inventory_item_id =
                                    :inventoryItemId
                                """)
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .mapTo(int.class)
                        .one()
        );
    }

    private static int reservedQuantityForItem(
            UUID inventoryItemId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                            SELECT COALESCE(SUM(quantity), 0)
                            FROM inventory_reservations
                            WHERE inventory_item_id =
                                :inventoryItemId
                            """)
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .mapTo(int.class)
                        .one()
        );
    }

    private static int reservationCount(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*)
                                FROM inventory_reservations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static int reservationQuantity(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT quantity
                                FROM inventory_reservations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static String reservationStatus(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT status
                                FROM inventory_reservations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(String.class)
                        .one()
        );
    }

    private static int operationCount(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*)
                                FROM inventory_reservation_operations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static String operationStatus(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT operation_status
                                FROM inventory_reservation_operations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(String.class)
                        .one()
        );
    }

    private static String operationOutcome(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT outcome
                                FROM inventory_reservation_operations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(String.class)
                        .one()
        );
    }

    private static void deleteFixture(
            UUID inventoryItemId,
            UUID... reservationIds
    ) {
        jdbi.useTransaction(handle -> {
            for (UUID reservationId : reservationIds) {
                handle.createUpdate("""
                        DELETE FROM inventory_reservation_operations
                        WHERE reservation_id = :reservationId
                        """)
                        .bind("reservationId", reservationId)
                        .execute();
            }

            handle.createUpdate("""
                    DELETE FROM inventory_reservations
                    WHERE inventory_item_id = :inventoryItemId
                    """)
                    .bind("inventoryItemId", inventoryItemId)
                    .execute();

            handle.createUpdate("""
                    DELETE FROM inventory_items
                    WHERE inventory_item_id = :inventoryItemId
                    """)
                    .bind("inventoryItemId", inventoryItemId)
                    .execute();
        });
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
