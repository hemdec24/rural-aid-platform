package org.ruralaid.workflow.domain;

public enum AidRequestStatus {
    RECEIVED,
    REQUIRES_REVIEW,
    VALIDATED,
    MATCH_PENDING,
    RESERVATION_FAILED,
    RESERVED,
    DISPATCHED,
    DELIVERED,
    COMPLETED,
    CANCELLED
}