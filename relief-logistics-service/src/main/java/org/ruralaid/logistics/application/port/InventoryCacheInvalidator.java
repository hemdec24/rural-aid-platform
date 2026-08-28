package org.ruralaid.logistics.application.port;

import java.util.UUID;

public interface InventoryCacheInvalidator {

    void invalidate(UUID inventoryItemId);
}
