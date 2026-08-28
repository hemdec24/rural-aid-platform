package org.ruralaid.logistics.cache;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.ruralaid.logistics.application.model.InventoryCacheCursor;
import org.ruralaid.logistics.application.model.InventoryCacheEvent;
import org.ruralaid.logistics.application.model.InventoryWarmupSnapshot;
import org.ruralaid.logistics.application.port.InventoryCacheCoherenceRepository;

/**
 * Keeps one process-local inventory cache coherent with the durable database
 * event log. The manager never makes inventory mutations authoritative; it
 * only decides whether the L1 cache is safe enough to serve read traffic.
 */
public final class InventoryCacheCoherenceManager implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            InventoryCacheCoherenceManager.class
    );

    private static final String EVENT_METRIC =
            "inventory.cache.coherence.eventsProcessed";
    private static final String FAILURE_METRIC =
            "inventory.cache.coherence.pollFailures";
    private static final String TRANSITION_METRIC =
            "inventory.cache.coherence.safetyTransitions";
    private static final String LAST_EVENT_GAUGE =
            "inventory.cache.coherence.lastProcessedEventId";
    private static final String WATERMARK_GAUGE =
            "inventory.cache.coherence.eventWatermark";
    private static final String LAG_GAUGE =
            "inventory.cache.coherence.eventLag";
    private static final String SAFE_GAUGE =
            "inventory.cache.coherence.safe";

    private final InventoryCacheCoherenceRepository repository;
    private final LocalInventoryItemCache cache;
    private final String consumerId;
    private final int maximumWarmupItems;
    private final int eventBatchSize;
    private final int maximumCatchupBatches;
    private final long pollIntervalMillis;
    private final long maximumPollStalenessMillis;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean cacheSafe = new AtomicBoolean();
    private final AtomicLong lastProcessedEventId = new AtomicLong();
    private final AtomicLong observedWatermark = new AtomicLong();
    private final AtomicLong lastSuccessfulPollEpochMillis =
            new AtomicLong();
    private final Counter processedEvents;
    private final Counter pollFailures;
    private final Counter safetyTransitions;

    private volatile boolean initialized;
    private volatile String lastFailure;

    public InventoryCacheCoherenceManager(
            InventoryCacheCoherenceRepository repository,
            LocalInventoryItemCache cache,
            String consumerId,
            int maximumWarmupItems,
            int eventBatchSize,
            int maximumCatchupBatches,
            long pollIntervalMillis,
            long maximumPollStalenessMillis,
            Clock clock,
            MetricRegistry metricRegistry
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Cache coherence repository is required"
        );
        this.cache = Objects.requireNonNull(
                cache,
                "Local inventory cache is required"
        );

        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cache consumer ID must not be blank"
            );
        }

        this.consumerId = consumerId;
        this.maximumWarmupItems = requirePositive(
                maximumWarmupItems,
                "Maximum warm-up items"
        );
        this.eventBatchSize = requirePositive(
                eventBatchSize,
                "Cache event batch size"
        );
        this.maximumCatchupBatches = requirePositive(
                maximumCatchupBatches,
                "Maximum cache catch-up batches"
        );
        this.pollIntervalMillis = requirePositive(
                pollIntervalMillis,
                "Cache poll interval"
        );
        this.maximumPollStalenessMillis = requirePositive(
                maximumPollStalenessMillis,
                "Maximum cache poll staleness"
        );

        if (maximumPollStalenessMillis < pollIntervalMillis) {
            throw new IllegalArgumentException(
                    "Maximum cache poll staleness must not be shorter "
                            + "than the poll interval"
            );
        }
        this.clock = Objects.requireNonNull(
                clock,
                "Cache coherence clock is required"
        );

        MetricRegistry metrics = Objects.requireNonNull(
                metricRegistry,
                "Metric registry is required"
        );

        this.processedEvents = metrics.counter(EVENT_METRIC);
        this.pollFailures = metrics.counter(FAILURE_METRIC);
        this.safetyTransitions = metrics.counter(TRANSITION_METRIC);

        metrics.register(
                LAST_EVENT_GAUGE,
                (Gauge<Long>) lastProcessedEventId::get
        );
        metrics.register(
                WATERMARK_GAUGE,
                (Gauge<Long>) observedWatermark::get
        );
        metrics.register(
                LAG_GAUGE,
                (Gauge<Long>) this::eventLag
        );
        metrics.register(
                SAFE_GAUGE,
                (Gauge<Integer>) () -> isCacheSafe() ? 1 : 0
        );

        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "inventory-cache-coherence-" + consumerId
                    );
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleWithFixedDelay(
                this::synchronizeSafely,
                0,
                pollIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void stop() {
        setCacheSafe(false);
        scheduler.shutdownNow();
    }

    public boolean isCacheSafe() {
        if (!cacheSafe.get()) {
            return false;
        }

        if (lastSuccessfulPollEpochMillis.get() == 0
                || pollStalenessMillis()
                > maximumPollStalenessMillis) {
            setCacheSafe(false);
            return false;
        }

        return true;
    }

    public String consumerId() {
        return consumerId;
    }

    public long lastProcessedEventId() {
        return lastProcessedEventId.get();
    }

    public long observedWatermark() {
        return observedWatermark.get();
    }

    public long eventLag() {
        return Math.max(
                0,
                observedWatermark.get() - lastProcessedEventId.get()
        );
    }

    public long lastSuccessfulPollEpochMillis() {
        return lastSuccessfulPollEpochMillis.get();
    }

    public long pollStalenessMillis() {
        long lastSuccessfulPoll =
                lastSuccessfulPollEpochMillis.get();

        if (lastSuccessfulPoll == 0) {
            return Long.MAX_VALUE;
        }

        return Math.max(0, clock.millis() - lastSuccessfulPoll);
    }

    public long maximumPollStalenessMillis() {
        return maximumPollStalenessMillis;
    }

    public String lastFailure() {
        return lastFailure;
    }

    synchronized void synchronizeOnce() {
        if (!initialized) {
            initializeFromSnapshot();
        }

        int processedBatches = 0;

        while (processedBatches < maximumCatchupBatches) {
            long watermark = repository.currentEventWatermark();
            observedWatermark.set(watermark);

            if (lastProcessedEventId.get() >= watermark) {
                lastSuccessfulPollEpochMillis.set(clock.millis());
                lastFailure = null;
                setCacheSafe(true);
                return;
            }

            setCacheSafe(false);

            long expectedCursor = lastProcessedEventId.get();
            List<InventoryCacheEvent> events =
                    repository.readEventsAfter(
                            expectedCursor,
                            eventBatchSize
                    );

            if (events.isEmpty()) {
                throw new IllegalStateException(
                        "Cache event log has no event after cursor "
                                + expectedCursor
                                + " while watermark is " + watermark
                );
            }

            long newCursor = applyEvents(expectedCursor, events);

            if (!repository.advanceCursor(
                    consumerId,
                    expectedCursor,
                    newCursor
            )) {
                throw new IllegalStateException(
                        "Cache cursor advancement was fenced; consumerId="
                                + consumerId
                                + ", expectedEventId=" + expectedCursor
                                + ", newEventId=" + newCursor
                );
            }

            lastProcessedEventId.set(newCursor);
            processedEvents.inc(events.size());
            processedBatches++;
        }

        observedWatermark.set(repository.currentEventWatermark());
        lastSuccessfulPollEpochMillis.set(clock.millis());
        lastFailure = null;
        setCacheSafe(
                lastProcessedEventId.get() >= observedWatermark.get()
        );
    }

    void synchronizeSafely() {
        try {
            synchronizeOnce();
        } catch (RuntimeException failure) {
            pollFailures.inc();
            setCacheSafe(false);
            lastFailure = failure.getClass().getSimpleName()
                    + ": " + failure.getMessage();

            LOGGER.warn(
                    "Inventory cache coherence failed; consumerId={}, "
                            + "lastProcessedEventId={}, observedWatermark={}",
                    consumerId,
                    lastProcessedEventId.get(),
                    observedWatermark.get(),
                    failure
            );
        }
    }

    private void initializeFromSnapshot() {
        InventoryWarmupSnapshot snapshot =
                repository.loadWarmupSnapshot(maximumWarmupItems);

        cache.clear();
        cache.warm(snapshot.inventoryItems());

        InventoryCacheCursor cursor = repository.loadOrCreateCursor(
                consumerId,
                snapshot.eventWatermark()
        );

        long storedEventId = cursor.lastEventId();
        long snapshotWatermark = snapshot.eventWatermark();

        if (storedEventId > snapshotWatermark) {
            throw new IllegalStateException(
                    "Stored cache cursor exceeds the warm-up watermark; "
                            + "consumerId=" + consumerId
                            + ", storedEventId=" + storedEventId
                            + ", snapshotWatermark=" + snapshotWatermark
            );
        }

        if (storedEventId < snapshotWatermark
                && !repository.advanceCursor(
                consumerId,
                storedEventId,
                snapshotWatermark
        )) {
            throw new IllegalStateException(
                    "Warm-up cache cursor advancement was fenced; "
                            + "consumerId=" + consumerId
                            + ", storedEventId=" + storedEventId
                            + ", snapshotWatermark=" + snapshotWatermark
            );
        }

        lastProcessedEventId.set(snapshotWatermark);
        observedWatermark.set(snapshotWatermark);
        initialized = true;
    }

    private long applyEvents(
            long expectedCursor,
            List<InventoryCacheEvent> events
    ) {
        long lastEventId = expectedCursor;

        for (InventoryCacheEvent event : events) {
            if (event.eventId() <= lastEventId) {
                throw new IllegalStateException(
                        "Cache events must be strictly ordered after cursor; "
                                + "cursor=" + lastEventId
                                + ", eventId=" + event.eventId()
                );
            }

            cache.invalidate(
                    event.inventoryItemId(),
                    event.inventoryVersion()
            );
            lastEventId = event.eventId();
        }

        return lastEventId;
    }

    private void setCacheSafe(boolean safe) {
        boolean previous = cacheSafe.getAndSet(safe);

        if (previous != safe) {
            safetyTransitions.inc();
        }
    }

    private int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }

        return value;
    }

    private long requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }

        return value;
    }
}
