package org.ruralaid.logistics.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import org.ruralaid.logistics.application.model.InventoryItemSnapshot;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LocalInventoryItemCacheTest {

    @Test
    void cachesPositiveAndNegativeResultsWithDifferentTtls() {
        MutableClock clock = new MutableClock();
        MetricRegistry metrics = new MetricRegistry();
        LocalInventoryItemCache cache = newCache(
                10,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                clock,
                metrics
        );
        UUID presentId = UUID.randomUUID();
        UUID absentId = UUID.randomUUID();
        InventoryItemSnapshot present = snapshot(presentId, 8, 1);
        AtomicInteger presentLoads = new AtomicInteger();
        AtomicInteger absentLoads = new AtomicInteger();

        assertEquals(
                Optional.of(present),
                cache.get(presentId, () -> {
                    presentLoads.incrementAndGet();
                    return Optional.of(present);
                })
        );
        assertEquals(
                Optional.of(present),
                cache.get(presentId, () -> {
                    presentLoads.incrementAndGet();
                    return Optional.empty();
                })
        );

        assertTrue(cache.get(absentId, () -> {
            absentLoads.incrementAndGet();
            return Optional.empty();
        }).isEmpty());
        assertTrue(cache.get(absentId, () -> {
            absentLoads.incrementAndGet();
            return Optional.of(snapshot(absentId, 1, 1));
        }).isEmpty());

        clock.advance(Duration.ofSeconds(6));

        assertEquals(Optional.of(present), cache.get(
                presentId,
                () -> {
                    presentLoads.incrementAndGet();
                    return Optional.empty();
                }
        ));
        assertEquals(Optional.of(snapshot(absentId, 1, 1)), cache.get(
                absentId,
                () -> {
                    absentLoads.incrementAndGet();
                    return Optional.of(snapshot(absentId, 1, 1));
                }
        ));

        assertAll(
                () -> assertEquals(1, presentLoads.get()),
                () -> assertEquals(2, absentLoads.get()),
                () -> assertEquals(
                        1,
                        metrics.counter(
                                LocalInventoryItemCache.EXPIRATION_METRIC
                        ).getCount()
                )
        );
    }

    @Test
    void evictsLeastRecentlyUsedEntryAtTheConfiguredBound() {
        LocalInventoryItemCache cache = newCache(
                2,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                Clock.systemUTC(),
                new MetricRegistry()
        );
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        AtomicInteger secondLoads = new AtomicInteger();

        cache.get(firstId, () -> Optional.of(snapshot(firstId, 1, 1)));
        cache.get(secondId, () -> {
            secondLoads.incrementAndGet();
            return Optional.of(snapshot(secondId, 1, 1));
        });
        cache.get(firstId, Optional::empty);
        cache.get(thirdId, () -> Optional.of(snapshot(thirdId, 1, 1)));

        cache.get(secondId, () -> {
            secondLoads.incrementAndGet();
            return Optional.of(snapshot(secondId, 2, 2));
        });

        assertEquals(2, secondLoads.get());
    }

    @Test
    void coalescesConcurrentMissesForTheSameInventoryItem()
            throws Exception {
        LocalInventoryItemCache cache = newCache(
                10,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                Clock.systemUTC(),
                new MetricRegistry()
        );
        UUID inventoryItemId = UUID.randomUUID();
        InventoryItemSnapshot expected = snapshot(
                inventoryItemId,
                15,
                4
        );
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Optional<InventoryItemSnapshot>>> results =
                    new ArrayList<>();

            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> cache.get(
                        inventoryItemId,
                        () -> {
                            loads.incrementAndGet();
                            loaderEntered.countDown();

                            try {
                                if (!releaseLoader.await(
                                        5,
                                        TimeUnit.SECONDS
                                )) {
                                    throw new IllegalStateException(
                                            "Test loader was not released"
                                    );
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }

                            return Optional.of(expected);
                        }
                )));
            }

            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            releaseLoader.countDown();

            for (Future<Optional<InventoryItemSnapshot>> result : results) {
                assertEquals(
                        Optional.of(expected),
                        result.get(5, TimeUnit.SECONDS)
                );
            }

            assertEquals(1, loads.get());
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidationIsVersionAwareAndFencesAnInFlightStaleLoad()
            throws Exception {
        LocalInventoryItemCache cache = newCache(
                10,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                Clock.systemUTC(),
                new MetricRegistry()
        );
        UUID inventoryItemId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();

        cache.get(
                inventoryItemId,
                () -> Optional.of(snapshot(inventoryItemId, 2, 2))
        );
        cache.invalidate(inventoryItemId, 1);

        assertEquals(
                2,
                cache.get(inventoryItemId, () -> {
                    loads.incrementAndGet();
                    return Optional.empty();
                }).orElseThrow().version()
        );

        cache.invalidate(inventoryItemId, 2);
        assertEquals(
                3,
                cache.get(inventoryItemId, () -> {
                    loads.incrementAndGet();
                    return Optional.of(snapshot(inventoryItemId, 3, 3));
                }).orElseThrow().version()
        );

        cache.clear();

        CountDownLatch readCompleted = new CountDownLatch(1);
        CountDownLatch returnLoadedValue = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Optional<InventoryItemSnapshot>> staleResult =
                    executor.submit(() -> cache.get(
                            inventoryItemId,
                            () -> {
                                loads.incrementAndGet();
                                readCompleted.countDown();

                                try {
                                    returnLoadedValue.await(
                                            5,
                                            TimeUnit.SECONDS
                                    );
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(exception);
                                }

                                return Optional.of(snapshot(
                                        inventoryItemId,
                                        3,
                                        3
                                ));
                            }
                    ));

            assertTrue(readCompleted.await(5, TimeUnit.SECONDS));
            cache.invalidate(inventoryItemId, 4);
            returnLoadedValue.countDown();
            assertEquals(3, staleResult.get().orElseThrow().version());

            assertEquals(
                    5,
                    cache.get(inventoryItemId, () -> {
                        loads.incrementAndGet();
                        return Optional.of(snapshot(
                                inventoryItemId,
                                5,
                                5
                        ));
                    }).orElseThrow().version()
            );
        } finally {
            returnLoadedValue.countDown();
            executor.shutdownNow();
        }

        assertEquals(3, loads.get());
    }

    @Test
    void warmupRetainsANewerCachedVersion() {
        LocalInventoryItemCache cache = newCache(
                3,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                Clock.systemUTC(),
                new MetricRegistry()
        );
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();

        cache.get(
                firstId,
                () -> Optional.of(snapshot(firstId, 9, 9))
        );
        cache.warm(List.of(
                snapshot(firstId, 3, 3),
                snapshot(secondId, 4, 4),
                snapshot(thirdId, 5, 5)
        ));

        AtomicInteger firstReloads = new AtomicInteger();
        AtomicInteger secondReloads = new AtomicInteger();

        assertEquals(
                9,
                cache.get(firstId, () -> {
                    firstReloads.incrementAndGet();
                    return Optional.empty();
                }).orElseThrow().version()
        );
        assertEquals(
                4,
                cache.get(secondId, () -> {
                    secondReloads.incrementAndGet();
                    return Optional.empty();
                }).orElseThrow().version()
        );

        assertAll(
                () -> assertEquals(0, firstReloads.get()),
                () -> assertEquals(0, secondReloads.get())
        );
    }

    private LocalInventoryItemCache newCache(
            int maximumEntries,
            Duration positiveTtl,
            Duration negativeTtl,
            Clock clock,
            MetricRegistry metrics
    ) {
        return new LocalInventoryItemCache(
                maximumEntries,
                positiveTtl,
                negativeTtl,
                clock,
                metrics
        );
    }

    private InventoryItemSnapshot snapshot(
            UUID inventoryItemId,
            int availableQuantity,
            long version
    ) {
        return new InventoryItemSnapshot(
                inventoryItemId,
                "WATER",
                availableQuantity,
                version
        );
    }

    private static final class MutableClock extends Clock {

        private final AtomicLong millis = new AtomicLong();

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
