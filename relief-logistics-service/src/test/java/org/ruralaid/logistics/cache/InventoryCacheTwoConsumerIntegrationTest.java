package org.ruralaid.logistics.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.codahale.metrics.MetricRegistry;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.ruralaid.logistics.application.InventoryItemQueryService;
import org.ruralaid.logistics.application.InventoryReservationService;
import org.ruralaid.logistics.domain.ReserveInventory;
import org.ruralaid.logistics.persistence.JdbiInventoryCacheCoherenceRepository;
import org.ruralaid.logistics.persistence.JdbiInventoryItemQueryRepository;
import org.ruralaid.logistics.persistence.JdbiInventoryReservationRepository;

final class InventoryCacheTwoConsumerIntegrationTest {

    private static Jdbi jdbi;

    @BeforeAll
    static void setUpDatabaseAccess() {
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

        jdbi = Jdbi.create(url, user, password);
    }

    @Test
    void independentPodCachesConvergeAfterACommittedReservation() {
        UUID inventoryItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String consumerA = "cache-pod-a-" + UUID.randomUUID();
        String consumerB = "cache-pod-b-" + UUID.randomUUID();

        insertInventory(inventoryItemId, 10);

        LocalInventoryItemCache cacheA = newCache();
        LocalInventoryItemCache cacheB = newCache();
        JdbiInventoryCacheCoherenceRepository coherenceRepository =
                new JdbiInventoryCacheCoherenceRepository(jdbi);
        InventoryCacheCoherenceManager managerA = manager(
                coherenceRepository,
                cacheA,
                consumerA
        );
        InventoryCacheCoherenceManager managerB = manager(
                coherenceRepository,
                cacheB,
                consumerB
        );
        JdbiInventoryItemQueryRepository queryRepository =
                new JdbiInventoryItemQueryRepository(jdbi);
        InventoryItemQueryService queryA = new InventoryItemQueryService(
                queryRepository,
                cacheA,
                managerA::isCacheSafe
        );
        InventoryItemQueryService queryB = new InventoryItemQueryService(
                queryRepository,
                cacheB,
                managerB::isCacheSafe
        );

        try {
            managerA.synchronizeOnce();
            managerB.synchronizeOnce();

            assertEquals(
                    10,
                    queryA.findById(inventoryItemId)
                            .orElseThrow()
                            .availableQuantity()
            );
            assertEquals(
                    10,
                    queryB.findById(inventoryItemId)
                            .orElseThrow()
                            .availableQuantity()
            );

            InventoryReservationService reservationService =
                    new InventoryReservationService(
                            new JdbiInventoryReservationRepository(jdbi),
                            cacheA
                    );

            reservationService.reserve(
                    new ReserveInventory(
                            reservationId,
                            "AR-cache-two-pod",
                            inventoryItemId,
                            3
                    )
            );

            assertEquals(
                    7,
                    queryA.findById(inventoryItemId)
                            .orElseThrow()
                            .availableQuantity()
            );
            assertEquals(
                    10,
                    queryB.findById(inventoryItemId)
                            .orElseThrow()
                            .availableQuantity()
            );

            managerB.synchronizeOnce();

            assertTrue(managerB.isCacheSafe());
            assertEquals(0, managerB.eventLag());
            assertEquals(
                    7,
                    queryB.findById(inventoryItemId)
                            .orElseThrow()
                            .availableQuantity()
            );
        } finally {
            deleteFixture(
                    inventoryItemId,
                    reservationId,
                    consumerA,
                    consumerB
            );
        }
    }

    private InventoryCacheCoherenceManager manager(
            JdbiInventoryCacheCoherenceRepository repository,
            LocalInventoryItemCache cache,
            String consumerId
    ) {
        return new InventoryCacheCoherenceManager(
                repository,
                cache,
                consumerId,
                100,
                100,
                10,
                250,
                2_000,
                Clock.systemUTC(),
                new MetricRegistry()
        );
    }

    private LocalInventoryItemCache newCache() {
        return new LocalInventoryItemCache(
                100,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                Clock.systemUTC(),
                new MetricRegistry()
        );
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

    private static void deleteFixture(
            UUID inventoryItemId,
            UUID reservationId,
            String consumerA,
            String consumerB
    ) {
        jdbi.useTransaction(handle -> {
            handle.createUpdate("""
                    DELETE FROM inventory_cache_consumer_cursors
                    WHERE consumer_id IN (:consumerA, :consumerB)
                    """)
                    .bind("consumerA", consumerA)
                    .bind("consumerB", consumerB)
                    .execute();

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
