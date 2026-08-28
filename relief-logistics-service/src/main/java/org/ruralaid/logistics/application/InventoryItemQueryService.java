package org.ruralaid.logistics.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.ruralaid.logistics.application.model.InventoryItemSnapshot;
import org.ruralaid.logistics.application.port.InventoryItemQueryRepository;
import org.ruralaid.logistics.cache.LocalInventoryItemCache;

public final class InventoryItemQueryService {

    private final InventoryItemQueryRepository repository;
    private final LocalInventoryItemCache cache;
    private final BooleanSupplier cacheSafe;

    public InventoryItemQueryService(
            InventoryItemQueryRepository repository,
            LocalInventoryItemCache cache
    ) {
        this(repository, cache, () -> true);
    }

    public InventoryItemQueryService(
            InventoryItemQueryRepository repository,
            LocalInventoryItemCache cache,
            BooleanSupplier cacheSafe
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Inventory query repository is required"
        );
        this.cache = Objects.requireNonNull(
                cache,
                "Local inventory cache is required"
        );
        this.cacheSafe = Objects.requireNonNull(
                cacheSafe,
                "Cache safety check is required"
        );
    }

    public Optional<InventoryItemSnapshot> findById(
            UUID inventoryItemId
    ) {
        Objects.requireNonNull(
                inventoryItemId,
                "Inventory item ID is required"
        );

        if (!cacheSafe.getAsBoolean()) {
            return cache.bypass(
                    inventoryItemId,
                    () -> repository.findById(inventoryItemId)
            );
        }

        return cache.get(
                inventoryItemId,
                () -> repository.findById(inventoryItemId)
        );
    }
}
