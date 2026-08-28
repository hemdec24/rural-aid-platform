package org.ruralaid.logistics.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import org.ruralaid.logistics.application.model.InventoryCacheCursor;
import org.ruralaid.logistics.application.model.InventoryCacheEvent;
import org.ruralaid.logistics.application.model.InventoryItemSnapshot;
import org.ruralaid.logistics.application.model.InventoryWarmupSnapshot;
import org.ruralaid.logistics.application.port.InventoryCacheCoherenceRepository;

final class InventoryCacheCoherenceManagerTest {

    private static final Instant NOW =
            Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void warmsThenInvalidatesFromTheDurableEventLog() {
        UUID inventoryItemId = UUID.randomUUID();
        InventoryItemSnapshot initial = new InventoryItemSnapshot(
                inventoryItemId,
                "WATER",
                10,
                0
        );
        FakeCoherenceRepository repository =
                new FakeCoherenceRepository(List.of(initial));
        LocalInventoryItemCache cache = newCache();
        InventoryCacheCoherenceManager manager = manager(
                repository,
                cache
        );

        manager.synchronizeOnce();

        assertTrue(manager.isCacheSafe());
        assertEquals(
                initial,
                cache.get(
                        inventoryItemId,
                        () -> {
                            throw new AssertionError(
                                    "Warm value should avoid a database load"
                            );
                        }
                ).orElseThrow()
        );

        repository.appendEvent(
                new InventoryCacheEvent(
                        1,
                        inventoryItemId,
                        1,
                        NOW
                )
        );

        manager.synchronizeOnce();

        assertTrue(manager.isCacheSafe());
        assertEquals(1, manager.lastProcessedEventId());
        assertEquals(1, repository.cursor);

        InventoryItemSnapshot updated = new InventoryItemSnapshot(
                inventoryItemId,
                "WATER",
                9,
                1
        );

        assertEquals(
                updated,
                cache.get(
                        inventoryItemId,
                        () -> java.util.Optional.of(updated)
                ).orElseThrow()
        );
    }

    @Test
    void pollFailureMakesTheCacheUnsafe() {
        FakeCoherenceRepository repository =
                new FakeCoherenceRepository(List.of());
        InventoryCacheCoherenceManager manager = manager(
                repository,
                newCache()
        );

        manager.synchronizeOnce();
        assertTrue(manager.isCacheSafe());

        repository.failWatermarkRead = true;
        manager.synchronizeSafely();

        assertFalse(manager.isCacheSafe());
        assertTrue(manager.lastFailure().contains("database unavailable"));
    }

    @Test
    void stalledPollerLeaseExpiresAndMakesTheCacheUnsafe() {
        MutableClock clock = new MutableClock(NOW);
        InventoryCacheCoherenceManager manager = manager(
                new FakeCoherenceRepository(List.of()),
                newCache(),
                clock
        );

        manager.synchronizeOnce();
        assertTrue(manager.isCacheSafe());

        clock.advance(Duration.ofMillis(2_001));

        assertFalse(manager.isCacheSafe());
        assertEquals(2_001, manager.pollStalenessMillis());
    }

    @Test
    void replaysAnEventWhenCursorAdvanceFailedAfterInvalidation() {
        UUID inventoryItemId = UUID.randomUUID();
        FakeCoherenceRepository repository =
                new FakeCoherenceRepository(List.of(
                        new InventoryItemSnapshot(
                                inventoryItemId,
                                "WATER",
                                10,
                                0
                        )
                ));
        LocalInventoryItemCache cache = newCache();
        InventoryCacheCoherenceManager manager = manager(
                repository,
                cache
        );

        manager.synchronizeOnce();
        repository.appendEvent(new InventoryCacheEvent(
                1,
                inventoryItemId,
                1,
                NOW
        ));
        repository.failNextAdvance = true;

        manager.synchronizeSafely();

        assertFalse(manager.isCacheSafe());
        assertEquals(0, manager.lastProcessedEventId());

        manager.synchronizeOnce();

        assertTrue(manager.isCacheSafe());
        assertEquals(1, manager.lastProcessedEventId());
        assertEquals(1, repository.cursor);
    }

    @Test
    void restartWarmupAdvancesAStoredCursorToItsSnapshotWatermark() {
        UUID inventoryItemId = UUID.randomUUID();
        InventoryItemSnapshot current = new InventoryItemSnapshot(
                inventoryItemId,
                "WATER",
                7,
                2
        );
        FakeCoherenceRepository repository =
                new FakeCoherenceRepository(List.of(current));
        repository.appendEvent(new InventoryCacheEvent(
                1,
                inventoryItemId,
                1,
                NOW
        ));
        repository.appendEvent(new InventoryCacheEvent(
                2,
                inventoryItemId,
                2,
                NOW
        ));
        repository.storeCursor(1);
        LocalInventoryItemCache cache = newCache();
        InventoryCacheCoherenceManager restarted = manager(
                repository,
                cache
        );

        restarted.synchronizeOnce();

        assertTrue(restarted.isCacheSafe());
        assertEquals(2, restarted.lastProcessedEventId());
        assertEquals(2, repository.cursor);
        assertEquals(
                current,
                cache.get(
                        inventoryItemId,
                        () -> {
                            throw new AssertionError(
                                    "Restart should serve the warm snapshot"
                            );
                        }
                ).orElseThrow()
        );
    }

    private InventoryCacheCoherenceManager manager(
            InventoryCacheCoherenceRepository repository,
            LocalInventoryItemCache cache
    ) {
        return manager(
                repository,
                cache,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private InventoryCacheCoherenceManager manager(
            InventoryCacheCoherenceRepository repository,
            LocalInventoryItemCache cache,
            Clock clock
    ) {
        return new InventoryCacheCoherenceManager(
                repository,
                cache,
                "pod-a",
                10,
                10,
                2,
                250,
                2_000,
                clock,
                new MetricRegistry()
        );
    }

    private LocalInventoryItemCache newCache() {
        return new LocalInventoryItemCache(
                10,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new MetricRegistry()
        );
    }

    private static final class FakeCoherenceRepository
            implements InventoryCacheCoherenceRepository {

        private final List<InventoryItemSnapshot> warmItems;
        private final List<InventoryCacheEvent> events =
                new ArrayList<>();
        private long cursor;
        private boolean cursorCreated;
        private boolean failWatermarkRead;
        private boolean failNextAdvance;

        private FakeCoherenceRepository(
                List<InventoryItemSnapshot> warmItems
        ) {
            this.warmItems = List.copyOf(warmItems);
        }

        @Override
        public long currentEventWatermark() {
            if (failWatermarkRead) {
                throw new IllegalStateException("database unavailable");
            }

            return events.isEmpty()
                    ? 0
                    : events.get(events.size() - 1).eventId();
        }

        @Override
        public InventoryWarmupSnapshot loadWarmupSnapshot(
                int maximumItems
        ) {
            return new InventoryWarmupSnapshot(
                    currentEventWatermark(),
                    warmItems.stream().limit(maximumItems).toList()
            );
        }

        @Override
        public List<InventoryCacheEvent> readEventsAfter(
                long lastEventId,
                int limit
        ) {
            return events.stream()
                    .filter(event -> event.eventId() > lastEventId)
                    .limit(limit)
                    .toList();
        }

        @Override
        public InventoryCacheCursor loadOrCreateCursor(
                String consumerId,
                long initialEventId
        ) {
            if (!cursorCreated) {
                cursor = initialEventId;
                cursorCreated = true;
            }

            return new InventoryCacheCursor(
                    consumerId,
                    cursor,
                    NOW
            );
        }

        @Override
        public boolean advanceCursor(
                String consumerId,
                long expectedLastEventId,
                long newLastEventId
        ) {
            if (failNextAdvance) {
                failNextAdvance = false;
                return false;
            }

            if (cursor != expectedLastEventId) {
                return false;
            }

            cursor = newLastEventId;
            return true;
        }

        private void appendEvent(InventoryCacheEvent event) {
            events.add(event);
        }

        private void storeCursor(long eventId) {
            cursor = eventId;
            cursorCreated = true;
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicLong millis;

        private MutableClock(Instant initialTime) {
            millis = new AtomicLong(initialTime.toEpochMilli());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        private void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }
    }
}
