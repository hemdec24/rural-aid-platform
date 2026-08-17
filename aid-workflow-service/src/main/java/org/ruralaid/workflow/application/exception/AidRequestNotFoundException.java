package org.ruralaid.workflow.application.exception;

import org.ruralaid.workflow.domain.AidRequestId;

public final class AidRequestNotFoundException
        extends RuntimeException {

    private final AidRequestId requestId;

    public AidRequestNotFoundException(
            AidRequestId requestId
    ) {
        super(
                "Aid request not found: "
                        + requestId.id()
        );

        this.requestId = requestId;
    }

    public AidRequestId requestId() {
        return requestId;
    }
}

