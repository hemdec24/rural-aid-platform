package org.ruralaid.workflow.application;

import org.ruralaid.workflow.application.exception.AidRequestNotFoundException;
import org.ruralaid.workflow.application.exception.AidRequestVersionConflictException;
import org.ruralaid.workflow.application.model.AidRequestCursor;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.application.port.AidRequestRepository;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.domain.Location;
import org.ruralaid.workflow.domain.NeedCategory;
import org.ruralaid.workflow.domain.Priority;
import org.ruralaid.workflow.domain.CancellationReason;

import java.util.function.Consumer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AidRequestApplicationService {

    private final AidRequestRepository repository;

    public AidRequestApplicationService(
            AidRequestRepository repository
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Aid request repository must not be null"
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
        return applyCommand(
                requestId,
                expectedVersion,
                aidRequest ->
                        aidRequest.markCancelled(reason)
        );
    }

    private VersionedAidRequest applyCommand(
            AidRequestId requestId,
            long expectedVersion,
            Consumer<AidRequest> command
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

        command.accept(stored.aggregate());

        return repository.update(stored);
    }
}