package org.ruralaid.logistics.persistence;

import java.util.List;
import java.util.UUID;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.ruralaid.logistics.application.model.InventoryCacheCursor;
import org.ruralaid.logistics.application.model.InventoryCacheEvent;
import org.ruralaid.logistics.application.model.InventoryWarmupSnapshot;
import org.ruralaid.logistics.domain.ReleaseOutcome;
import org.ruralaid.logistics.domain.ReservationOutcome;
import org.ruralaid.logistics.domain.ReserveInventory;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class JdbiInventoryCacheCoherenceRepositoryIntegrationTest {

    private static Jdbi jdbi;
    private static JdbiInventoryReservationRepository reservationRepository;
    private static JdbiInventoryCacheCoherenceRepository coherenceRepository;

    @BeforeAll
    static void setUpDatabaseAccess() {
        jdbi = createJdbi();
        reservationRepository =
                new JdbiInventoryReservationRepository(jdbi);
        coherenceRepository =
                new JdbiInventoryCacheCoherenceRepository(jdbi);
    }

    @Test
    void commitsOneVersionedEventPerSuccessfulInventoryMutation() {
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
                    reservationRepository.reserve(command)
            );

            assertEquals(
                    ReservationOutcome.RESERVED,
                    reservationRepository.reserve(command)
            );

            assertAll(
                    () -> assertEquals(
                            1L,
                            inventoryVersion(inventoryItemId)
                    ),
                    () -> assertEquals(
                            List.of(1L),
                            eventVersions(inventoryItemId)
                    )
            );

            assertEquals(
                    ReleaseOutcome.RELEASED,
                    reservationRepository.release(reservationId)
            );

            assertEquals(
                    ReleaseOutcome.RELEASED,
                    reservationRepository.release(reservationId)
            );

            assertAll(
                    () -> assertEquals(
                            2L,
                            inventoryVersion(inventoryItemId)
                    ),
                    () -> assertEquals(
                            List.of(1L, 2L),
                            eventVersions(inventoryItemId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId, null);
        }
    }

    @Test
    void unavailableReservationDoesNotCreateCacheEvent() {
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
            assertEquals(
                    ReservationOutcome.UNAVAILABLE,
                    reservationRepository.reserve(command)
            );

            assertAll(
                    () -> assertEquals(
                            0L,
                            inventoryVersion(inventoryItemId)
                    ),
                    () -> assertEquals(
                            List.of(),
                            eventVersions(inventoryItemId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId, null);
        }
    }

    @Test
    void rollsBackInventoryMutationWhenCacheEventCannotBeInserted() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        ReserveInventory command = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                4
        );

        insertInventory(inventoryItemId, 10);
        insertCacheEventFixture(inventoryItemId, 1L);
        long eventWatermarkBeforeFailure =
                coherenceRepository.currentEventWatermark();

        try {
            assertThrows(
                    UnableToExecuteStatementException.class,
                    () -> reservationRepository.reserve(command)
            );

            assertAll(
                    () -> assertEquals(
                            10,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            0L,
                            inventoryVersion(inventoryItemId)
                    ),
                    () -> assertEquals(
                            0,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            0,
                            operationCount(reservationId)
                    ),
                    () -> assertEquals(
                            List.of(1L),
                            eventVersions(inventoryItemId)
                    ),
                    () -> assertEquals(
                            eventWatermarkBeforeFailure,
                            coherenceRepository.currentEventWatermark()
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId, null);
        }
    }

    @Test
    void readsOrderedEventsAndFencesCursorAdvancement() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String consumerId = "cache-it-" + UUID.randomUUID();

        ReserveInventory command = new ReserveInventory(
                reservationId,
                UUID.randomUUID().toString(),
                inventoryItemId,
                2
        );

        insertInventory(inventoryItemId, 8);
        long beforeMutation = coherenceRepository.currentEventWatermark();

        try {
            assertEquals(
                    ReservationOutcome.RESERVED,
                    reservationRepository.reserve(command)
            );

            List<InventoryCacheEvent> events =
                    coherenceRepository.readEventsAfter(
                            beforeMutation,
                            100
                    );

            InventoryCacheEvent event = events.stream()
                    .filter(value -> value.inventoryItemId()
                            .equals(inventoryItemId))
                    .findFirst()
                    .orElseThrow();

            InventoryWarmupSnapshot warmupSnapshot =
                    coherenceRepository.loadWarmupSnapshot(10_000);

            InventoryCacheCursor cursor =
                    coherenceRepository.loadOrCreateCursor(
                            consumerId,
                            beforeMutation
                    );

            boolean advanced = coherenceRepository.advanceCursor(
                    consumerId,
                    cursor.lastEventId(),
                    event.eventId()
            );

            boolean staleAdvance = coherenceRepository.advanceCursor(
                    consumerId,
                    cursor.lastEventId(),
                    event.eventId()
            );

            InventoryCacheCursor reloaded =
                    coherenceRepository.loadOrCreateCursor(
                            consumerId,
                            0
                    );

            assertAll(
                    () -> assertEquals(
                            1L,
                            event.inventoryVersion()
                    ),
                    () -> assertTrue(
                            warmupSnapshot.eventWatermark()
                                    >= event.eventId()
                    ),
                    () -> assertTrue(
                            warmupSnapshot.inventoryItems().stream()
                                    .anyMatch(item ->
                                            item.inventoryItemId()
                                                    .equals(inventoryItemId)
                                                    && item.version() == 1L
                                                    && item.availableQuantity()
                                                    == 6
                                    )
                    ),
                    () -> assertTrue(advanced),
                    () -> assertFalse(staleAdvance),
                    () -> assertEquals(
                            event.eventId(),
                            reloaded.lastEventId()
                    )
            );
        } finally {
            deleteFixture(
                    inventoryItemId,
                    reservationId,
                    consumerId
            );
        }
    }

    private static Jdbi createJdbi() {
        String url = System.getenv("RELIEF_LOGISTICS_IT_DB_URL");
        String user = System.getenv("RELIEF_LOGISTICS_IT_DB_USER");
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
                            'WATER_KIT',
                            :availableQuantity
                        )
                        """)
                        .bind("inventoryItemId", inventoryItemId)
                        .bind("availableQuantity", availableQuantity)
                        .execute()
        );
    }

    private static long inventoryVersion(UUID inventoryItemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT version
                        FROM inventory_items
                        WHERE inventory_item_id = :inventoryItemId
                        """)
                        .bind("inventoryItemId", inventoryItemId)
                        .mapTo(long.class)
                        .one()
        );
    }

    private static int availableQuantity(UUID inventoryItemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT available_quantity
                        FROM inventory_items
                        WHERE inventory_item_id = :inventoryItemId
                        """)
                        .bind("inventoryItemId", inventoryItemId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static void insertCacheEventFixture(
            UUID inventoryItemId,
            long inventoryVersion
    ) {
        jdbi.useTransaction(handle -> {
            long eventId = handle.createQuery("""
                    UPDATE inventory_cache_event_clock
                    SET last_event_id = last_event_id + 1
                    WHERE clock_id = 1
                    RETURNING last_event_id
                    """)
                    .mapTo(long.class)
                    .one();

            handle.createUpdate("""
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
        });
    }

    private static List<Long> eventVersions(UUID inventoryItemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT inventory_version
                        FROM inventory_cache_events
                        WHERE inventory_item_id = :inventoryItemId
                        ORDER BY event_id
                        """)
                        .bind("inventoryItemId", inventoryItemId)
                        .mapTo(Long.class)
                        .list()
        );
    }

    private static int reservationCount(UUID reservationId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(*)
                        FROM inventory_reservations
                        WHERE reservation_id = :reservationId
                        """)
                        .bind("reservationId", reservationId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static int operationCount(UUID reservationId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(*)
                        FROM inventory_reservation_operations
                        WHERE reservation_id = :reservationId
                        """)
                        .bind("reservationId", reservationId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static void deleteFixture(
            UUID inventoryItemId,
            UUID reservationId,
            String consumerId
    ) {
        jdbi.useTransaction(handle -> {
            if (consumerId != null) {
                handle.createUpdate("""
                        DELETE FROM inventory_cache_consumer_cursors
                        WHERE consumer_id = :consumerId
                        """)
                        .bind("consumerId", consumerId)
                        .execute();
            }

            handle.createUpdate("""
                    DELETE FROM inventory_reservation_operations
                    WHERE reservation_id = :reservationId
                    """)
                    .bind("reservationId", reservationId)
                    .execute();

            handle.createUpdate("""
                    DELETE FROM inventory_reservations
                    WHERE reservation_id = :reservationId
                    """)
                    .bind("reservationId", reservationId)
                    .execute();

            handle.createUpdate("""
                    DELETE FROM inventory_items
                    WHERE inventory_item_id = :inventoryItemId
                      AND NOT EXISTS (
                          SELECT 1
                          FROM inventory_cache_events
                          WHERE inventory_item_id = :inventoryItemId
                      )
                    """)
                    .bind("inventoryItemId", inventoryItemId)
                    .execute();
        });
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
