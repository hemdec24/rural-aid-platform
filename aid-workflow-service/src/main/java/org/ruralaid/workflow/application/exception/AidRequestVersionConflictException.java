package org.ruralaid.workflow.application.exception;

import org.ruralaid.workflow.domain.AidRequestId;

public final class AidRequestVersionConflictException
        extends RuntimeException {

    private final AidRequestId requestId;
    private final long expectedVersion;

    public AidRequestVersionConflictException(
            AidRequestId requestId,
            long expectedVersion
    ) {
        super(
                "Aid request " + requestId.id()
                        + " is no longer at expected version "
                        + expectedVersion
        );

        this.requestId = requestId;
        this.expectedVersion = expectedVersion;
    }

    public AidRequestId requestId() {
        return requestId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}

