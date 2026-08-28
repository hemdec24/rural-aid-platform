package org.ruralaid.logistics.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;

import org.ruralaid.logistics.application.model.InventoryCacheCursor;
import org.ruralaid.logistics.application.model.InventoryCacheEvent;
import org.ruralaid.logistics.application.model.InventoryItemSnapshot;
import org.ruralaid.logistics.application.model.InventoryWarmupSnapshot;
import org.ruralaid.logistics.application.port.InventoryCacheCoherenceRepository;

public final class JdbiInventoryCacheCoherenceRepository
        implements InventoryCacheCoherenceRepository {

    private final Jdbi jdbi;

    public JdbiInventoryCacheCoherenceRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(
                jdbi,
                "Database access dependency is required"
        );
    }

    @Override
    public long currentEventWatermark() {
        return jdbi.withHandle(this::readEventWatermark);
    }

    @Override
    public InventoryWarmupSnapshot loadWarmupSnapshot(
            int maximumItems
    ) {
        requirePositive(maximumItems, "Warm-up item limit");

        return jdbi.inTransaction(
                TransactionIsolationLevel.REPEATABLE_READ,
                handle -> {
                    long watermark = readEventWatermark(handle);

                    List<InventoryItemSnapshot> inventoryItems =
                            handle.createQuery("""
                                    SELECT
                                        inventory_item_id,
                                        resource_type,
                                        available_quantity,
                                        version
                                    FROM inventory_items
                                    ORDER BY inventory_item_id
                                    LIMIT :limit
                                    """)
                                    .bind("limit", maximumItems)
                                    .map((resultSet, context) ->
                                            mapInventoryItem(resultSet)
                                    )
                                    .list();

                    return new InventoryWarmupSnapshot(
                            watermark,
                            inventoryItems
                    );
                }
        );
    }

    @Override
    public List<InventoryCacheEvent> readEventsAfter(
            long lastEventId,
            int limit
    ) {
        requireNotNegative(lastEventId, "Last cache event ID");
        requirePositive(limit, "Cache event batch limit");

        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT
                            event_id,
                            inventory_item_id,
                            inventory_version,
                            created_at
                        FROM inventory_cache_events
                        WHERE event_id > :lastEventId
                        ORDER BY event_id
                        LIMIT :limit
                        """)
                        .bind("lastEventId", lastEventId)
                        .bind("limit", limit)
                        .map((resultSet, context) ->
                                mapCacheEvent(resultSet)
                        )
                        .list()
        );
    }

    @Override
    public InventoryCacheCursor loadOrCreateCursor(
            String consumerId,
            long initialEventId
    ) {
        requireConsumerId(consumerId);
        requireNotNegative(initialEventId, "Initial cache event ID");

        return jdbi.inTransaction(handle -> {
            long watermark = readEventWatermark(handle);

            if (initialEventId > watermark) {
                throw new IllegalArgumentException(
                        "Initial cache event ID must not exceed "
                                + "the current event watermark"
                );
            }

            handle.createUpdate("""
                    INSERT INTO inventory_cache_consumer_cursors (
                        consumer_id,
                        last_event_id
                    ) VALUES (
                        :consumerId,
                        :initialEventId
                    )
                    ON CONFLICT (consumer_id) DO NOTHING
                    """)
                    .bind("consumerId", consumerId)
                    .bind("initialEventId", initialEventId)
                    .execute();

            return readCursor(handle, consumerId);
        });
    }

    @Override
    public boolean advanceCursor(
            String consumerId,
            long expectedLastEventId,
            long newLastEventId
    ) {
        requireConsumerId(consumerId);
        requireNotNegative(
                expectedLastEventId,
                "Expected last cache event ID"
        );
        requireNotNegative(
                newLastEventId,
                "New last cache event ID"
        );

        if (newLastEventId < expectedLastEventId) {
            throw new IllegalArgumentException(
                    "A cache cursor must not move backwards"
            );
        }

        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                        UPDATE inventory_cache_consumer_cursors
                        SET last_event_id = :newLastEventId,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE consumer_id = :consumerId
                          AND last_event_id = :expectedLastEventId
                          AND :newLastEventId <= (
                              SELECT last_event_id
                              FROM inventory_cache_event_clock
                              WHERE clock_id = 1
                          )
                        """)
                        .bind("consumerId", consumerId)
                        .bind(
                                "expectedLastEventId",
                                expectedLastEventId
                        )
                        .bind("newLastEventId", newLastEventId)
                        .execute() == 1
        );
    }

    private long readEventWatermark(Handle handle) {
        return handle.createQuery("""
                SELECT last_event_id
                FROM inventory_cache_event_clock
                WHERE clock_id = 1
                """)
                .mapTo(long.class)
                .one();
    }

    private InventoryCacheCursor readCursor(
            Handle handle,
            String consumerId
    ) {
        return handle.createQuery("""
                SELECT
                    consumer_id,
                    last_event_id,
                    updated_at
                FROM inventory_cache_consumer_cursors
                WHERE consumer_id = :consumerId
                """)
                .bind("consumerId", consumerId)
                .map((resultSet, context) ->
                        mapCursor(resultSet)
                )
                .one();
    }

    private InventoryItemSnapshot mapInventoryItem(
            ResultSet resultSet
    ) throws SQLException {
        return new InventoryItemSnapshot(
                resultSet.getObject(
                        "inventory_item_id",
                        java.util.UUID.class
                ),
                resultSet.getString("resource_type"),
                resultSet.getInt("available_quantity"),
                resultSet.getLong("version")
        );
    }

    private InventoryCacheEvent mapCacheEvent(
            ResultSet resultSet
    ) throws SQLException {
        OffsetDateTime createdAt = resultSet.getObject(
                "created_at",
                OffsetDateTime.class
        );

        if (createdAt == null) {
            throw new SQLException(
                    "Stored cache event creation time must not be null"
            );
        }

        return new InventoryCacheEvent(
                resultSet.getLong("event_id"),
                resultSet.getObject(
                        "inventory_item_id",
                        java.util.UUID.class
                ),
                resultSet.getLong("inventory_version"),
                createdAt.toInstant()
        );
    }

    private InventoryCacheCursor mapCursor(
            ResultSet resultSet
    ) throws SQLException {
        OffsetDateTime updatedAt = resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
        );

        if (updatedAt == null) {
            throw new SQLException(
                    "Stored cache cursor update time must not be null"
            );
        }

        return new InventoryCacheCursor(
                resultSet.getString("consumer_id"),
                resultSet.getLong("last_event_id"),
                updatedAt.toInstant()
        );
    }

    private void requireConsumerId(String consumerId) {
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cache consumer ID must not be blank"
            );
        }
    }

    private void requireNotNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + " must not be negative"
            );
        }
    }

    private void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}
