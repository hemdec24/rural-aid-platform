package org.ruralaid.workflow.application.port;

import java.util.List;
import java.util.Optional;

import org.ruralaid.workflow.application.model.AidRequestCursor;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;

public interface AidRequestRepository {
    VersionedAidRequest insert(AidRequest aidRequest);

    Optional<VersionedAidRequest> findById(AidRequestId requestId);

    VersionedAidRequest update(VersionedAidRequest storedRequest);

    List<VersionedAidRequest> list(int limit, Optional<AidRequestCursor> cursor);
}
