package org.ruralaid.workflow.persistence;

import org.jdbi.v3.core.Jdbi;
import org.ruralaid.workflow.application.model.AidRequestCursor;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.application.port.AidRequestRepository;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;

import org.ruralaid.workflow.application.exception.AidRequestVersionConflictException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbiAidRequestRepository
        implements AidRequestRepository {

    private static final String INSERT_SQL = """
            INSERT INTO aid_requests (
                request_id,
                latitude,
                longitude,
                need_category,
                priority,
                status,
                reservation_id,
                reservation_failure_reason,
                dispatch_responder_reference,
                dispatched_at,
                delivery_confirmation_reference,
                delivered_at,
                cancellation_reason
            )
            VALUES (
                :requestId,
                :latitude,
                :longitude,
                :needCategory,
                :priority,
                :status,
                :reservationId,
                :reservationFailureReason,
                :dispatchResponderReference,
                :dispatchedAt,
                :deliveryConfirmationReference,
                :deliveredAt,
                :cancellationReason
            )
            """;

    private static final String FIND_BY_ID_SQL = """
        SELECT
            request_id,
            latitude,
            longitude,
            need_category,
            priority,
            status,
            reservation_id,
            reservation_failure_reason,
            dispatch_responder_reference,
            dispatched_at,
            delivery_confirmation_reference,
            delivered_at,
            cancellation_reason,
            version,
            created_at
        FROM aid_requests
        WHERE request_id = :requestId
        """;

    private static final String UPDATE_SQL = """
        UPDATE aid_requests
        SET
            latitude = :latitude,
            longitude = :longitude,
            need_category = :needCategory,
            priority = :priority,
            status = :status,
            reservation_id = :reservationId,
            reservation_failure_reason =
                :reservationFailureReason,
            dispatch_responder_reference =
                :dispatchResponderReference,
            dispatched_at = :dispatchedAt,
            delivery_confirmation_reference =
                :deliveryConfirmationReference,
            delivered_at = :deliveredAt,
            cancellation_reason = :cancellationReason,
            version = version + 1
        WHERE request_id = :requestId
          AND version = :expectedVersion
        RETURNING *
        """;

    private static final String LIST_FIRST_PAGE_SQL = """
        SELECT *
        FROM aid_requests
        ORDER BY created_at DESC, request_id DESC
        LIMIT :limit
        """;

    private static final String LIST_AFTER_CURSOR_SQL = """
        SELECT *
        FROM aid_requests
        WHERE (created_at, request_id)
            < (:cursorCreatedAt, :cursorRequestId)
        ORDER BY created_at DESC, request_id DESC
        LIMIT :limit
        """;

    private final Jdbi jdbi;

    public JdbiAidRequestRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(
                jdbi,
                "Jdbi must not be null"
        );
    }

    @Override
    public VersionedAidRequest insert(AidRequest aidRequest) {
        Objects.requireNonNull(
                aidRequest,
                "Aid request must not be null"
        );

        return jdbi.withHandle(handle ->
                handle.createUpdate(INSERT_SQL)
                        .bind(
                                "requestId",
                                aidRequest.id().id()
                        )
                        .bind(
                                "latitude",
                                aidRequest.location().latitude()
                        )
                        .bind(
                                "longitude",
                                aidRequest.location().longitude()
                        )
                        .bind(
                                "needCategory",
                                aidRequest.needCategory().name()
                        )
                        .bind(
                                "priority",
                                aidRequest.priority().name()
                        )
                        .bind(
                                "status",
                                aidRequest.status().name()
                        )
                        .bind(
                                "reservationId",
                                aidRequest.reservationId()
                                        .map(value -> value.id())
                        )
                        .bind(
                                "reservationFailureReason",
                                aidRequest.reservationFailureReason()
                                        .map(value -> value.reason())
                        )
                        .bind(
                                "dispatchResponderReference",
                                aidRequest.dispatchDetails()
                                        .map(value ->
                                                value.responderReference()
                                        )
                        )
                        .bind(
                                "dispatchedAt",
                                aidRequest.dispatchDetails()
                                        .map(value -> value.dispatchedAt())
                        )
                        .bind(
                                "deliveryConfirmationReference",
                                aidRequest.deliveryDetails()
                                        .map(value ->
                                                value.confirmationReference()
                                        )
                        )
                        .bind(
                                "deliveredAt",
                                aidRequest.deliveryDetails()
                                        .map(value -> value.deliveredAt())
                        )
                        .bind(
                                "cancellationReason",
                                aidRequest.cancellationReason()
                                        .map(value -> value.reason())
                        )
                        .executeAndReturnGeneratedKeys()
                        .map(new AidRequestRowMapper())
                        .one()
        );
    }

    @Override
    public Optional<VersionedAidRequest> findById(
            AidRequestId requestId
    ) {
        Objects.requireNonNull(
                requestId,
                "Aid request ID must not be null"
        );
        return jdbi.withHandle(handle ->
                handle.createQuery(FIND_BY_ID_SQL)
                        .bind("requestId", requestId.id())
                        .map(new AidRequestRowMapper())
                        .findOne()
        );
    }

    @Override
    public VersionedAidRequest update(
            VersionedAidRequest storedRequest
    ) {
        Objects.requireNonNull(
                storedRequest,
                "Stored aid request must not be null"
        );

        AidRequest aidRequest = storedRequest.aggregate();

        Optional<VersionedAidRequest> updatedRequest =
                jdbi.withHandle(handle ->
                        handle.createQuery(UPDATE_SQL)
                                .bind(
                                        "requestId",
                                        aidRequest.id().id()
                                )
                                .bind(
                                        "latitude",
                                        aidRequest.location().latitude()
                                )
                                .bind(
                                        "longitude",
                                        aidRequest.location().longitude()
                                )
                                .bind(
                                        "needCategory",
                                        aidRequest.needCategory().name()
                                )
                                .bind(
                                        "priority",
                                        aidRequest.priority().name()
                                )
                                .bind(
                                        "status",
                                        aidRequest.status().name()
                                )
                                .bind(
                                        "reservationId",
                                        aidRequest.reservationId()
                                                .map(value -> value.id())
                                )
                                .bind(
                                        "reservationFailureReason",
                                        aidRequest.reservationFailureReason()
                                                .map(value -> value.reason())
                                )
                                .bind(
                                        "dispatchResponderReference",
                                        aidRequest.dispatchDetails()
                                                .map(value ->
                                                        value.responderReference()
                                                )
                                )
                                .bind(
                                        "dispatchedAt",
                                        aidRequest.dispatchDetails()
                                                .map(value ->
                                                        value.dispatchedAt()
                                                )
                                )
                                .bind(
                                        "deliveryConfirmationReference",
                                        aidRequest.deliveryDetails()
                                                .map(value ->
                                                        value.confirmationReference()
                                                )
                                )
                                .bind(
                                        "deliveredAt",
                                        aidRequest.deliveryDetails()
                                                .map(value ->
                                                        value.deliveredAt()
                                                )
                                )
                                .bind(
                                        "cancellationReason",
                                        aidRequest.cancellationReason()
                                                .map(value -> value.reason())
                                )
                                .bind(
                                        "expectedVersion",
                                        storedRequest.version()
                                )
                                .map(new AidRequestRowMapper())
                                .findOne()
                );

        return updatedRequest.orElseThrow(
                () -> new AidRequestVersionConflictException(
                        aidRequest.id(),
                        storedRequest.version()
                )
        );
    }

    @Override
    public List<VersionedAidRequest> list(
            int limit,
            Optional<AidRequestCursor> cursor
    ) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "List limit must be positive"
            );
        }

        Objects.requireNonNull(
                cursor,
                "Aid request cursor must not be null"
        );

        if (cursor.isEmpty()) {
            return jdbi.withHandle(handle ->
                    handle.createQuery(LIST_FIRST_PAGE_SQL)
                            .bind("limit", limit)
                            .map(new AidRequestRowMapper())
                            .list()
            );
        }

        AidRequestCursor cursorValue = cursor.orElseThrow();

        return jdbi.withHandle(handle ->
                handle.createQuery(LIST_AFTER_CURSOR_SQL)
                        .bind(
                                "cursorCreatedAt",
                                cursorValue.createdAt()
                        )
                        .bind(
                                "cursorRequestId",
                                cursorValue.requestId().id()
                        )
                        .bind("limit", limit)
                        .map(new AidRequestRowMapper())
                        .list()
        );
    }
}