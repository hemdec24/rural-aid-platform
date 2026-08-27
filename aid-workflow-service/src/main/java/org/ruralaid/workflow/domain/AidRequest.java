package org.ruralaid.workflow.domain;

import java.util.Optional;

public final class AidRequest {

    private final AidRequestId id;
    private final NeedCategory needCategory;
    private final Priority priority;

    private Location location;
    private AidRequestStatus status;
    private ReservationId reservationId;
    private InventoryItemId reservationInventoryItemId;
    private Integer reservationQuantity;
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
        this(
                id,
                location,
                needCategory,
                priority,
                AidRequestStatus.RECEIVED,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static AidRequest restore(
            AidRequestId id,
            Location location,
            NeedCategory needCategory,
            Priority priority,
            AidRequestStatus status,
            ReservationId reservationId,
            InventoryItemId reservationInventoryItemId,
            Integer reservationQuantity,
            ReservationFailureReason reservationFailureReason,
            DispatchDetails dispatchDetails,
            DeliveryDetails deliveryDetails,
            CancellationReason cancellationReason
    ) {
        return new AidRequest(
                id,
                location,
                needCategory,
                priority,
                status,
                reservationId,
                reservationInventoryItemId,
                reservationQuantity,
                reservationFailureReason,
                dispatchDetails,
                deliveryDetails,
                cancellationReason
        );
    }

    private AidRequest(
            AidRequestId id,
            Location location,
            NeedCategory needCategory,
            Priority priority,
            AidRequestStatus status,
            ReservationId reservationId,
            InventoryItemId reservationInventoryItemId,
            Integer reservationQuantity,
            ReservationFailureReason reservationFailureReason,
            DispatchDetails dispatchDetails,
            DeliveryDetails deliveryDetails,
            CancellationReason cancellationReason
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

        if (status == null) {
            throw new IllegalArgumentException(
                    "Aid request status must not be null"
            );
        }

        this.id = id;
        this.location = location;
        this.needCategory = needCategory;
        this.priority = priority;
        this.status = status;
        this.reservationId = reservationId;
        this.reservationInventoryItemId =
                reservationInventoryItemId;
        this.reservationQuantity = reservationQuantity;
        this.reservationFailureReason =
                reservationFailureReason;
        this.dispatchDetails = dispatchDetails;
        this.deliveryDetails = deliveryDetails;
        this.cancellationReason = cancellationReason;

        validateRestoredState();
    }

    private void validateRestoredState() {
        boolean hasNoReservationAttempt =
                reservationId == null
                        && reservationInventoryItemId == null
                        && reservationQuantity == null;

        boolean hasCompleteReservationAttempt =
                reservationId != null
                        && reservationInventoryItemId != null
                        && reservationQuantity != null
                        && reservationQuantity > 0;

        if (!hasNoReservationAttempt
                && !hasCompleteReservationAttempt) {
            throw new IllegalArgumentException(
                    "Stored reservation attempt facts are incomplete"
            );
        }

        boolean valid = switch (status) {
            case RECEIVED,
                 REQUIRES_REVIEW,
                 VALIDATED ->
                    hasNoReservationAttempt
                            && reservationFailureReason == null
                            && dispatchDetails == null
                            && deliveryDetails == null
                            && cancellationReason == null;

            case MATCH_PENDING,
                 RESERVED ->
                    hasCompleteReservationAttempt
                            && reservationFailureReason == null
                            && dispatchDetails == null
                            && deliveryDetails == null
                            && cancellationReason == null;

            case RELEASE_PENDING ->
                    hasCompleteReservationAttempt
                            && reservationFailureReason == null
                            && dispatchDetails == null
                            && deliveryDetails == null
                            && cancellationReason != null;

            case RESERVATION_FAILED ->
                    hasCompleteReservationAttempt
                            && reservationFailureReason != null
                            && dispatchDetails == null
                            && deliveryDetails == null
                            && cancellationReason == null;

            case DISPATCHED ->
                    hasCompleteReservationAttempt
                            && reservationFailureReason == null
                            && dispatchDetails != null
                            && deliveryDetails == null
                            && cancellationReason == null;

            case DELIVERED,
                 COMPLETED ->
                    hasCompleteReservationAttempt
                            && reservationFailureReason == null
                            && dispatchDetails != null
                            && deliveryDetails != null
                            && cancellationReason == null;

            case CANCELLED ->
                    dispatchDetails == null
                            && deliveryDetails == null
                            && cancellationReason != null
                            && (
                            reservationFailureReason == null
                                    || hasCompleteReservationAttempt
                    );
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Stored aid request facts are inconsistent with status "
                            + status
            );
        }

        if (deliveryDetails != null
                && deliveryDetails.deliveredAt()
                .isBefore(dispatchDetails.dispatchedAt())) {
            throw new IllegalArgumentException(
                    "Delivery time must not be before dispatch time"
            );
        }
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

    public Optional<InventoryItemId> reservationInventoryItemId() {
        return Optional.ofNullable(
                reservationInventoryItemId
        );
    }

    public Optional<Integer> reservationQuantity() {
        return Optional.ofNullable(reservationQuantity);
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

    public void markMatchingStarted(
            ReservationId plannedReservationId,
            InventoryItemId inventoryItemId,
            int quantity
    ) {
        requireStatus(
                AidRequestStatus.VALIDATED,
                "start resource matching"
        );

        validateReservationAttempt(
                plannedReservationId,
                inventoryItemId,
                quantity
        );

        this.reservationId = plannedReservationId;
        this.reservationInventoryItemId = inventoryItemId;
        this.reservationQuantity = quantity;
        this.status = AidRequestStatus.MATCH_PENDING;
    }

    public void markReserved() {
        requireStatus(
                AidRequestStatus.MATCH_PENDING,
                "record reservation"
        );

        this.status = AidRequestStatus.RESERVED;
    }

    public void markReleasePending(
            CancellationReason reason
    ) {
        requireStatus(
                AidRequestStatus.RESERVED,
                "begin reservation release"
        );

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Cancellation reason must not be null"
            );
        }

        this.cancellationReason = reason;
        this.status = AidRequestStatus.RELEASE_PENDING;
    }

    public void markReleasedAndCancelled() {
        requireStatus(
                AidRequestStatus.RELEASE_PENDING,
                "complete cancellation after reservation release"
        );

        this.status = AidRequestStatus.CANCELLED;
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

    public void startNewMatchingAttempt(
            ReservationId newReservationId,
            InventoryItemId inventoryItemId,
            int quantity
    ) {
        requireStatus(
                AidRequestStatus.RESERVATION_FAILED,
                "start a new resource matching attempt"
        );

        validateReservationAttempt(
                newReservationId,
                inventoryItemId,
                quantity
        );

        if (newReservationId.equals(this.reservationId)) {
            throw new IllegalArgumentException(
                    "A new matching attempt requires a new reservation ID"
            );
        }

        this.reservationId = newReservationId;
        this.reservationInventoryItemId = inventoryItemId;
        this.reservationQuantity = quantity;
        this.reservationFailureReason = null;
        this.status = AidRequestStatus.MATCH_PENDING;
    }

    public void markDispatched(
            DispatchDetails dispatchDetails
    ) {
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

    public void markDelivered(
            DeliveryDetails deliveryDetails
    ) {
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

    public void markCancelled(
            CancellationReason reason
    ) {
        requireCancellableStatus();

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Cancellation reason must not be null"
            );
        }

        this.cancellationReason = reason;
        this.status = AidRequestStatus.CANCELLED;
    }

    private void validateReservationAttempt(
            ReservationId reservationId,
            InventoryItemId inventoryItemId,
            int quantity
    ) {
        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "Reservation ID must not be null"
            );
        }

        if (inventoryItemId == null) {
            throw new IllegalArgumentException(
                    "Inventory item ID must not be null"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be greater than zero"
            );
        }
    }

    private void requireCancellableStatus() {
        boolean cancellable = switch (status) {
            case RECEIVED,
                 REQUIRES_REVIEW,
                 VALIDATED,
                 RESERVATION_FAILED -> true;

            default -> false;
        };

        if (!cancellable) {
            throw new IllegalStateException(
                    "Cannot cancel request while request is "
                            + status
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
