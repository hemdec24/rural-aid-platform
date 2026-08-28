package org.ruralaid.logistics.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jdbi.v3.core.Jdbi;

import org.ruralaid.logistics.application.model.InventoryItemSnapshot;
import org.ruralaid.logistics.application.port.InventoryItemQueryRepository;

public final class JdbiInventoryItemQueryRepository
        implements InventoryItemQueryRepository {

    private static final String FIND_BY_ID_SQL = """
            SELECT
                inventory_item_id,
                resource_type,
                available_quantity,
                version
            FROM inventory_items
            WHERE inventory_item_id = :inventoryItemId
            """;

    private final Jdbi jdbi;

    public JdbiInventoryItemQueryRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(
                jdbi,
                "Database access dependency is required"
        );
    }

    @Override
    public Optional<InventoryItemSnapshot> findById(
            UUID inventoryItemId
    ) {
        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );

        return jdbi.withHandle(handle ->
                handle.createQuery(FIND_BY_ID_SQL)
                        .bind("inventoryItemId", inventoryItemId)
                        .map((resultSet, context) ->
                                new InventoryItemSnapshot(
                                        resultSet.getObject(
                                                "inventory_item_id",
                                                UUID.class
                                        ),
                                        resultSet.getString(
                                                "resource_type"
                                        ),
                                        resultSet.getInt(
                                                "available_quantity"
                                        ),
                                        resultSet.getLong("version")
                                )
                        )
                        .findOne()
        );
    }
}
