package org.ruralaid.workflow.application;

import org.ruralaid.workflow.application.exception.AidRequestNotFoundException;
import org.ruralaid.workflow.application.exception.AidRequestVersionConflictException;
import org.ruralaid.workflow.application.model.AidRequestCursor;
import org.ruralaid.workflow.application.model.ReleaseResult;
import org.ruralaid.workflow.application.model.ReservationCommand;
import org.ruralaid.workflow.application.model.ReservationResult;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.application.port.AidRequestRepository;
import org.ruralaid.workflow.application.port.InventoryReservationPort;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.domain.AidRequestStatus;
import org.ruralaid.workflow.domain.InventoryItemId;
import org.ruralaid.workflow.domain.Location;
import org.ruralaid.workflow.domain.NeedCategory;
import org.ruralaid.workflow.domain.Priority;
import org.ruralaid.workflow.domain.CancellationReason;
import org.ruralaid.workflow.domain.ReservationFailureReason;
import org.ruralaid.workflow.domain.ReservationId;
import org.ruralaid.workflow.application.exception.ReservationCallException;

import java.util.function.Consumer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AidRequestApplicationService {

    private final AidRequestRepository repository;
    private final InventoryReservationPort inventoryReservationPort;

    public AidRequestApplicationService(
            AidRequestRepository repository,
            InventoryReservationPort inventoryReservationPort
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Aid request repository must not be null"
        );
        this.inventoryReservationPort = Objects.requireNonNull(
                inventoryReservationPort,
                "Inventory reservation port must not be null"
        );
    }

    public VersionedAidRequest create(
            Location location,
            NeedCategory needCategory,
            Priority priority
    ) {
        AidRequestId requestId = new AidRequestId(
                UUID.randomUUID().toString()
        );

        AidRequest aidRequest = new AidRequest(
                requestId,
                location,
                needCategory,
                priority
        );

        return repository.insert(aidRequest);
    }

    public VersionedAidRequest getById(
            AidRequestId requestId
    ) {
        return repository.findById(requestId)
                .orElseThrow(
                        () -> new AidRequestNotFoundException(
                                requestId
                        )
                );
    }

    public List<VersionedAidRequest> list(
            int limit,
            Optional<AidRequestCursor> cursor
    ) {
        return repository.list(limit, cursor);
    }

    public VersionedAidRequest correctLocation(
            AidRequestId requestId,
            long expectedVersion,
            Location newLocation
    ) {
        return applyCommand(
                requestId,
                expectedVersion,
                aidRequest -> aidRequest.correctLocation(newLocation)
        );
    }

    public VersionedAidRequest markValidated(
            AidRequestId requestId,
            long expectedVersion
    ) {
        return applyCommand(
                requestId,
                expectedVersion,
                AidRequest::markValidated
        );
    }

    public VersionedAidRequest markReviewRequired(
            AidRequestId requestId,
            long expectedVersion
    ) {
        return applyCommand(
                requestId,
                expectedVersion,
                AidRequest::markReviewRequired
        );
    }

    public VersionedAidRequest markReviewApproved(
            AidRequestId requestId,
            long expectedVersion
    ) {
        return applyCommand(
                requestId,
                expectedVersion,
                AidRequest::markReviewApproved
        );
    }

    public VersionedAidRequest cancel(
            AidRequestId requestId,
            long expectedVersion,
            CancellationReason reason
    ) {
        VersionedAidRequest stored = getAtVersion(
                requestId,
                expectedVersion
        );

        if (stored.aggregate().status()
                == AidRequestStatus.RELEASE_PENDING) {
            return completeRelease(stored);
        }

        if (stored.aggregate().status()
                == AidRequestStatus.RESERVED) {
            stored.aggregate().markReleasePending(reason);

            VersionedAidRequest releasePending =
                    repository.update(stored);

            return completeRelease(releasePending);
        }

        stored.aggregate().markCancelled(reason);
        return repository.update(stored);
    }

    public VersionedAidRequest reserveInventory(
            AidRequestId requestId,
            long expectedVersion,
            InventoryItemId inventoryItemId,
            int quantity
    ) {
        VersionedAidRequest stored = getAtVersion(
                requestId,
                expectedVersion
        );

        stored.aggregate().markMatchingStarted(
                ReservationId.generate(),
                inventoryItemId,
                quantity
        );

        VersionedAidRequest matchPending =
                repository.update(stored);

        return completeReservation(matchPending);
    }

    public VersionedAidRequest retryReservation(
            AidRequestId requestId,
            long expectedVersion
    ) {
        VersionedAidRequest matchPending = getAtVersion(
                requestId,
                expectedVersion
        );

        if (matchPending.aggregate().status()
                != AidRequestStatus.MATCH_PENDING) {
            throw new IllegalStateException(
                    "Reservation can be retried only while request is "
                            + AidRequestStatus.MATCH_PENDING
            );
        }

        return completeReservation(matchPending);
    }

    private VersionedAidRequest applyCommand(
            AidRequestId requestId,
            long expectedVersion,
            Consumer<AidRequest> command
    ) {
        VersionedAidRequest stored = getAtVersion(
                requestId,
                expectedVersion
        );

        command.accept(stored.aggregate());

        return repository.update(stored);
    }

    private VersionedAidRequest completeReservation(
            VersionedAidRequest matchPending
    ) {
        AidRequest aidRequest = matchPending.aggregate();

        ReservationCommand command = new ReservationCommand(
                aidRequest.reservationId().orElseThrow(),
                aidRequest.id(),
                aidRequest.reservationInventoryItemId().orElseThrow(),
                aidRequest.reservationQuantity().orElseThrow()
        );

        ReservationResult result =
                inventoryReservationPort.reserve(command);

        switch (result) {
            case RESERVED -> aidRequest.markReserved();
            case UNAVAILABLE -> aidRequest.markReservationFailed(
                    new ReservationFailureReason(
                            "Requested inventory is unavailable"
                    )
            );
            case RESERVATION_ID_CONFLICT ->
                    throw new ReservationCallException(
                            "Logistics rejected the persisted "
                                    + "reservation identity"
                    );
        }

        return repository.update(matchPending);
    }

    private VersionedAidRequest completeRelease(
            VersionedAidRequest releasePending
    ) {
        AidRequest aidRequest = releasePending.aggregate();

        ReleaseResult result = inventoryReservationPort.release(
                aidRequest.reservationId().orElseThrow()
        );

        if (result == ReleaseResult.NOT_FOUND) {
            throw new ReservationCallException(
                    "Logistics could not find the reservation to release"
            );
        }

        aidRequest.markReleasedAndCancelled();
        return repository.update(releasePending);
    }

    private VersionedAidRequest getAtVersion(
            AidRequestId requestId,
            long expectedVersion
    ) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "Expected version must not be negative"
            );
        }

        VersionedAidRequest stored = getById(requestId);

        if (stored.version() != expectedVersion) {
            throw new AidRequestVersionConflictException(
                    requestId,
                    expectedVersion
            );
        }

        return stored;
    }
}
