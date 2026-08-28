package org.ruralaid.logistics.application.port;

import java.util.List;

import org.ruralaid.logistics.application.model.InventoryCacheCursor;
import org.ruralaid.logistics.application.model.InventoryCacheEvent;
import org.ruralaid.logistics.application.model.InventoryWarmupSnapshot;

public interface InventoryCacheCoherenceRepository {

    long currentEventWatermark();

    InventoryWarmupSnapshot loadWarmupSnapshot(int maximumItems);

    List<InventoryCacheEvent> readEventsAfter(
            long lastEventId,
            int limit
    );

    InventoryCacheCursor loadOrCreateCursor(
            String consumerId,
            long initialEventId
    );

    boolean advanceCursor(
            String consumerId,
            long expectedLastEventId,
            long newLastEventId
    );
}
