package org.ruralaid.logistics.application.port;

import java.util.Optional;
import java.util.UUID;

import org.ruralaid.logistics.application.model.InventoryItemSnapshot;

public interface InventoryItemQueryRepository {

    Optional<InventoryItemSnapshot> findById(UUID inventoryItemId);
}
