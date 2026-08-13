package org.ruralaid.workflow.domain;

import java.util.Optional;

public final class AidRequest {
    private final AidRequestId id;
    private final NeedCategory needCategory;
    private final Priority priority;
    private Location location;
    private AidRequestStatus status;
    private ReservationId reservationId;
    private ReservationFailureReason reservationFailureReason;
    private DispatchDetails dispatchDetails;
    private DeliveryDetails deliveryDetails;
    private CancellationReason cancellationReason;

    public AidRequest(
            AidRequestId id,
            Location location,
            NeedCategory needCategory,
            Priority priority
    ) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Aid request ID must not be null"
            );
        }
        if (location == null) {
            throw new IllegalArgumentException(
                    "Location must not be null"
            );
        }
        if (needCategory == null) {
            throw new IllegalArgumentException(
                    "Need category must not be null"
            );
        }
        if (priority == null) {
            throw new IllegalArgumentException(
                    "Priority must not be null"
            );
        }

        this.id = id;
        this.location = location;
        this.needCategory = needCategory;
        this.priority = priority;
        this.status = AidRequestStatus.RECEIVED;
    }

    public AidRequestId id() {
        return id;
    }

    public Location location() {
        return location;
    }

    public NeedCategory needCategory() {
        return needCategory;
    }

    public Priority priority() {
        return priority;
    }

    public AidRequestStatus status() {
        return status;
    }

    public Optional<ReservationId> reservationId() {
        return Optional.ofNullable(reservationId);
    }

    public Optional<ReservationFailureReason> reservationFailureReason() {
        return Optional.ofNullable(reservationFailureReason);
    }

    public Optional<DispatchDetails> dispatchDetails() {
        return Optional.ofNullable(dispatchDetails);
    }

    public Optional<DeliveryDetails> deliveryDetails() {
        return Optional.ofNullable(deliveryDetails);
    }

    public Optional<CancellationReason> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    public void correctLocation(Location newLocation) {
        if (newLocation == null) {
            throw new IllegalArgumentException(
                    "New location must not be null"
            );
        }

        if (status != AidRequestStatus.RECEIVED
                && status != AidRequestStatus.REQUIRES_REVIEW) {
            throw new IllegalStateException(
                    "Location cannot be corrected while request is " + status
            );
        }

        this.location = newLocation;
    }

    public void markValidated() {
        requireStatus(
                AidRequestStatus.RECEIVED,
                "mark request as validated"
        );

        this.status = AidRequestStatus.VALIDATED;
    }

    public void markReviewRequired() {
        requireStatus(
                AidRequestStatus.RECEIVED,
                "mark request as requiring review"
        );

        this.status = AidRequestStatus.REQUIRES_REVIEW;
    }

    public void markReviewApproved() {
        requireStatus(
                AidRequestStatus.REQUIRES_REVIEW,
                "approve request review"
        );

        this.status = AidRequestStatus.VALIDATED;
    }

    public void markMatchingStarted() {
        requireStatus(
                AidRequestStatus.VALIDATED,
                "start resource matching"
        );

        this.status = AidRequestStatus.MATCH_PENDING;
    }

    public void markReserved(ReservationId reservationId) {
        requireStatus(
                AidRequestStatus.MATCH_PENDING,
                "record reservation"
        );

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "Reservation ID must not be null"
            );
        }

        this.reservationId = reservationId;
        this.status = AidRequestStatus.RESERVED;
    }

    public void markReservationFailed(
            ReservationFailureReason reason
    ) {
        requireStatus(
                AidRequestStatus.MATCH_PENDING,
                "record reservation failure"
        );

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Reservation failure reason must not be null"
            );
        }

        this.reservationFailureReason = reason;
        this.status = AidRequestStatus.RESERVATION_FAILED;
    }

    public void markMatchingRetried() {
        requireStatus(
                AidRequestStatus.RESERVATION_FAILED,
                "retry resource matching"
        );

        this.reservationFailureReason = null;
        this.status = AidRequestStatus.MATCH_PENDING;
    }

    public void markDispatched(DispatchDetails dispatchDetails) {
        requireStatus(
                AidRequestStatus.RESERVED,
                "record dispatch"
        );

        if (dispatchDetails == null) {
            throw new IllegalArgumentException(
                    "Dispatch details must not be null"
            );
        }

        this.dispatchDetails = dispatchDetails;
        this.status = AidRequestStatus.DISPATCHED;
    }

    public void markDelivered(DeliveryDetails deliveryDetails) {
        requireStatus(
                AidRequestStatus.DISPATCHED,
                "record delivery"
        );

        if (deliveryDetails == null) {
            throw new IllegalArgumentException(
                    "Delivery details must not be null"
            );
        }

        if (deliveryDetails.deliveredAt()
                .isBefore(dispatchDetails.dispatchedAt())) {
            throw new IllegalArgumentException(
                    "Delivery time must not be before dispatch time"
            );
        }

        this.deliveryDetails = deliveryDetails;
        this.status = AidRequestStatus.DELIVERED;
    }

    public void markCompleted() {
        requireStatus(
                AidRequestStatus.DELIVERED,
                "complete request"
        );

        this.status = AidRequestStatus.COMPLETED;
    }

    public void markCancelled(CancellationReason reason) {
        requireCancellableStatus();

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Cancellation reason must not be null"
            );
        }

        this.cancellationReason = reason;
        this.status = AidRequestStatus.CANCELLED;
    }

    private void requireCancellableStatus() {
        boolean cancellable = switch (status) {
            case RECEIVED,
                 REQUIRES_REVIEW,
                 VALIDATED,
                 MATCH_PENDING,
                 RESERVATION_FAILED,
                 RESERVED -> true;
            default -> false;
        };

        if (!cancellable) {
            throw new IllegalStateException(
                    "Cannot cancel request while request is " + status
            );
        }
    }

    private void requireStatus(
            AidRequestStatus expectedStatus,
            String operation
    ) {
        if (status != expectedStatus) {
            throw new IllegalStateException(
                    "Cannot " + operation
                            + " while request is " + status
                            + "; expected " + expectedStatus
            );
        }
    }
}
