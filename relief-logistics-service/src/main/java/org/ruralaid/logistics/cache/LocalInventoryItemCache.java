package org.ruralaid.logistics.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.ruralaid.logistics.application.model.InventoryItemSnapshot;
import org.ruralaid.logistics.application.port.InventoryCacheInvalidator;

/**
 * A bounded cache owned by one Logistics process. PostgreSQL loading remains
 * outside this class and is supplied by the query service.
 */
public final class LocalInventoryItemCache
        implements InventoryCacheInvalidator {

    public static final String HIT_METRIC =
            "inventory.cache.hits";
    public static final String MISS_METRIC =
            "inventory.cache.misses";
    public static final String LOAD_METRIC =
            "inventory.cache.loads";
    public static final String SHARED_LOAD_METRIC =
            "inventory.cache.sharedLoads";
    public static final String LOAD_FAILURE_METRIC =
            "inventory.cache.loadFailures";
    public static final String EXPIRATION_METRIC =
            "inventory.cache.expirations";
    public static final String EVICTION_METRIC =
            "inventory.cache.capacityEvictions";
    public static final String INVALIDATION_METRIC =
            "inventory.cache.invalidations";
    public static final String BYPASS_METRIC =
            "inventory.cache.databaseBypasses";

    private final int maximumEntries;
    private final long positiveTtlMillis;
    private final long negativeTtlMillis;
    private final long ttlJitterSalt;
    private final Clock clock;
    private final Map<UUID, CacheEntry> entries =
            new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentMap<UUID, LoadFlight> inFlightLoads =
            new ConcurrentHashMap<>();

    private final Counter hits;
    private final Counter misses;
    private final Counter loads;
    private final Counter sharedLoads;
    private final Counter loadFailures;
    private final Counter expirations;
    private final Counter capacityEvictions;
    private final Counter invalidations;
    private final Counter databaseBypasses;

    public LocalInventoryItemCache(
            int maximumEntries,
            Duration positiveTtl,
            Duration negativeTtl,
            Clock clock,
            MetricRegistry metricRegistry
    ) {
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException(
                    "Maximum cache entries must be positive"
            );
        }

        this.maximumEntries = maximumEntries;
        this.positiveTtlMillis = requirePositiveDuration(
                positiveTtl,
                "Positive cache TTL"
        );
        this.negativeTtlMillis = requirePositiveDuration(
                negativeTtl,
                "Negative cache TTL"
        );
        this.ttlJitterSalt = ThreadLocalRandom.current().nextLong();
        this.clock = Objects.requireNonNull(
                clock,
                "Cache clock is required"
        );

        MetricRegistry metrics = Objects.requireNonNull(
                metricRegistry,
                "Metric registry is required"
        );

        this.hits = metrics.counter(HIT_METRIC);
        this.misses = metrics.counter(MISS_METRIC);
        this.loads = metrics.counter(LOAD_METRIC);
        this.sharedLoads = metrics.counter(SHARED_LOAD_METRIC);
        this.loadFailures = metrics.counter(LOAD_FAILURE_METRIC);
        this.expirations = metrics.counter(EXPIRATION_METRIC);
        this.capacityEvictions = metrics.counter(EVICTION_METRIC);
        this.invalidations = metrics.counter(INVALIDATION_METRIC);
        this.databaseBypasses = metrics.counter(BYPASS_METRIC);
    }

    public Optional<InventoryItemSnapshot> get(
            UUID inventoryItemId,
            Supplier<Optional<InventoryItemSnapshot>> loader
    ) {
        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );
        Objects.requireNonNull(loader, "Inventory loader is required");

        CacheLookup cached = readCached(inventoryItemId);

        if (cached.hit()) {
            hits.inc();
            return cached.value();
        }

        misses.inc();

        LoadFlight newFlight = new LoadFlight();
        LoadFlight existingFlight = inFlightLoads.putIfAbsent(
                inventoryItemId,
                newFlight
        );

        if (existingFlight != null) {
            sharedLoads.inc();
            return await(existingFlight.result());
        }

        try {
            // A previous leader can finish between the first cache lookup and
            // this flight claim. Rechecking prevents a needless second load.
            CacheLookup completedRace = readCached(inventoryItemId);

            if (completedRace.hit()) {
                hits.inc();
                newFlight.result().complete(completedRace.value());
                return completedRace.value();
            }

            loads.inc();

            Optional<InventoryItemSnapshot> loaded =
                    Objects.requireNonNull(
                            loader.get(),
                            "Inventory loader result is required"
                    );

            newFlight.cacheIfAllowed(
                    loaded,
                    () -> write(inventoryItemId, loaded)
            );

            newFlight.result().complete(loaded);
            return loaded;
        } catch (RuntimeException | Error failure) {
            loadFailures.inc();
            newFlight.result().completeExceptionally(failure);
            throw rethrow(failure);
        } finally {
            inFlightLoads.remove(inventoryItemId, newFlight);
        }
    }

    public Optional<InventoryItemSnapshot> bypass(
            UUID inventoryItemId,
            Supplier<Optional<InventoryItemSnapshot>> loader
    ) {
        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );
        Objects.requireNonNull(loader, "Inventory loader is required");

        databaseBypasses.inc();
        return Objects.requireNonNull(
                loader.get(),
                "Inventory loader result is required"
        );
    }

    /**
     * An old event cannot evict a value loaded from a newer database version.
     * A negative entry is always evicted because an event may represent the
     * creation of the previously absent item.
     */
    public void invalidate(UUID inventoryItemId, long eventVersion) {
        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );

        if (eventVersion < 0) {
            throw new IllegalArgumentException(
                    "Inventory event version must not be negative"
            );
        }

        LoadFlight flight = inFlightLoads.get(inventoryItemId);

        if (flight != null) {
            flight.recordInvalidation(eventVersion);
        }

        boolean removed;

        synchronized (entries) {
            CacheEntry cached = entries.get(inventoryItemId);
            removed = cached != null
                    && cached.value()
                    .map(snapshot -> snapshot.version() <= eventVersion)
                    .orElse(true);

            if (removed) {
                entries.remove(inventoryItemId);
            }
        }

        if (removed) {
            invalidations.inc();
        }
    }

    @Override
    public void invalidate(UUID inventoryItemId) {
        invalidate(inventoryItemId, Long.MAX_VALUE);
    }

    public void clear() {
        inFlightLoads.values().forEach(
                flight -> flight.recordInvalidation(Long.MAX_VALUE)
        );

        int removed;

        synchronized (entries) {
            removed = entries.size();
            entries.clear();
        }

        if (removed > 0) {
            invalidations.inc(removed);
        }
    }

    /**
     * Preloads a bounded database snapshot without treating it as a cache
     * miss or an authoritative load. Newer cached versions are retained.
     */
    public void warm(Iterable<InventoryItemSnapshot> snapshots) {
        Objects.requireNonNull(
                snapshots,
                "Inventory warm-up snapshots are required"
        );

        for (InventoryItemSnapshot snapshot : snapshots) {
            InventoryItemSnapshot requiredSnapshot =
                    Objects.requireNonNull(
                            snapshot,
                            "Inventory warm-up snapshot is required"
                    );

            write(
                    requiredSnapshot.inventoryItemId(),
                    Optional.of(requiredSnapshot)
            );
        }
    }

    private CacheLookup readCached(UUID inventoryItemId) {
        long now = clock.millis();

        synchronized (entries) {
            CacheEntry entry = entries.get(inventoryItemId);

            if (entry == null) {
                return CacheLookup.miss();
            }

            if (entry.expiresAtMillis() <= now) {
                entries.remove(inventoryItemId);
                expirations.inc();
                return CacheLookup.miss();
            }

            return CacheLookup.hit(entry.value());
        }
    }

    private void write(
            UUID inventoryItemId,
            Optional<InventoryItemSnapshot> value
    ) {
        long ttlMillis = value.isPresent()
                ? positiveTtlMillis
                : negativeTtlMillis;
        CacheEntry newEntry = new CacheEntry(
                value,
                saturatedAdd(
                        clock.millis(),
                        jitteredTtl(inventoryItemId, ttlMillis)
                )
        );

        synchronized (entries) {
            CacheEntry existing = entries.get(inventoryItemId);

            if (existing != null
                    && existing.expiresAtMillis() > clock.millis()
                    && isNewer(existing.value(), value)) {
                return;
            }

            entries.put(inventoryItemId, newEntry);

            while (entries.size() > maximumEntries) {
                Iterator<UUID> iterator = entries.keySet().iterator();
                iterator.next();
                iterator.remove();
                capacityEvictions.inc();
            }
        }
    }

    private boolean isNewer(
            Optional<InventoryItemSnapshot> existing,
            Optional<InventoryItemSnapshot> candidate
    ) {
        if (existing.isEmpty()) {
            return false;
        }

        if (candidate.isEmpty()) {
            return true;
        }

        return existing.orElseThrow().version()
                > candidate.orElseThrow().version();
    }

    private Optional<InventoryItemSnapshot> await(
            CompletableFuture<Optional<InventoryItemSnapshot>> future
    ) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            throw rethrow(cause == null ? exception : cause);
        }
    }

    private RuntimeException rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }

        if (failure instanceof Error error) {
            throw error;
        }

        return new IllegalStateException(
                "Inventory cache loader failed",
                failure
        );
    }

    private long requirePositiveDuration(
            Duration duration,
            String fieldName
    ) {
        Objects.requireNonNull(duration, fieldName + " is required");

        long millis = duration.toMillis();

        if (millis <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be at least one millisecond"
            );
        }

        return millis;
    }

    private long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }

        return left + right;
    }

    /**
     * Per-process, per-key downward jitter prevents a large warm-up batch
     * from expiring at one instant and de-correlates replicas while keeping
     * the configured TTL as a strict upper bound.
     */
    private long jitteredTtl(UUID inventoryItemId, long ttlMillis) {
        long maximumJitter = ttlMillis / 10;

        if (maximumJitter == 0) {
            return ttlMillis;
        }

        long keyHash = inventoryItemId.getMostSignificantBits()
                ^ inventoryItemId.getLeastSignificantBits()
                ^ ttlJitterSalt;
        long jitter = Math.floorMod(keyHash, maximumJitter + 1);

        return ttlMillis - jitter;
    }

    private record CacheEntry(
            Optional<InventoryItemSnapshot> value,
            long expiresAtMillis
    ) {

        private CacheEntry {
            Objects.requireNonNull(value, "Cached value is required");
        }
    }

    private record CacheLookup(
            boolean hit,
            Optional<InventoryItemSnapshot> value
    ) {

        private static CacheLookup hit(
                Optional<InventoryItemSnapshot> value
        ) {
            return new CacheLookup(true, value);
        }

        private static CacheLookup miss() {
            return new CacheLookup(false, Optional.empty());
        }
    }

    private static final class LoadFlight {

        private final CompletableFuture<Optional<InventoryItemSnapshot>>
                result = new CompletableFuture<>();
        private long invalidatedVersion = -1;

        private CompletableFuture<Optional<InventoryItemSnapshot>> result() {
            return result;
        }

        private synchronized void recordInvalidation(long eventVersion) {
            invalidatedVersion = Math.max(
                    invalidatedVersion,
                    eventVersion
            );
        }

        private synchronized void cacheIfAllowed(
                Optional<InventoryItemSnapshot> loaded,
                Runnable writer
        ) {
            boolean cacheable = invalidatedVersion < 0
                    || loaded
                    .map(snapshot ->
                            snapshot.version() > invalidatedVersion
                    )
                    .orElse(false);

            if (cacheable) {
                writer.run();
            }
        }
    }
}
