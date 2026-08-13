package org.ruralaid.workflow.domain;

public record CancellationReason(String reason) {
    public CancellationReason {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason cannot be blank");
        }
    }
}
