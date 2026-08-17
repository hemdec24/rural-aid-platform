package org.ruralaid.workflow.persistence;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.domain.AidRequestStatus;
import org.ruralaid.workflow.domain.CancellationReason;
import org.ruralaid.workflow.domain.DeliveryDetails;
import org.ruralaid.workflow.domain.DispatchDetails;
import org.ruralaid.workflow.domain.Location;
import org.ruralaid.workflow.domain.NeedCategory;
import org.ruralaid.workflow.domain.Priority;
import org.ruralaid.workflow.domain.ReservationFailureReason;
import org.ruralaid.workflow.domain.ReservationId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public final class AidRequestRowMapper
        implements RowMapper<VersionedAidRequest> {

    @Override
    public VersionedAidRequest map(
            ResultSet resultSet,
            StatementContext context
    ) throws SQLException {
        AidRequest aggregate = AidRequest.restore(
                new AidRequestId(
                        resultSet.getString("request_id")
                ),
                new Location(
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude")
                ),
                NeedCategory.valueOf(
                        resultSet.getString("need_category")
                ),
                Priority.valueOf(
                        resultSet.getString("priority")
                ),
                AidRequestStatus.valueOf(
                        resultSet.getString("status")
                ),
                readReservationId(resultSet),
                readReservationFailureReason(resultSet),
                readDispatchDetails(resultSet),
                readDeliveryDetails(resultSet),
                readCancellationReason(resultSet)
        );

        Long version = resultSet.getObject(
                "version",
                Long.class
        );

        OffsetDateTime createdAt = resultSet.getObject(
                "created_at",
                OffsetDateTime.class
        );

        if (version == null) {
            throw new SQLException(
                    "Stored aid request version must not be null"
            );
        }

        if (createdAt == null) {
            throw new SQLException(
                    "Stored aid request creation time must not be null"
            );
        }

        return new VersionedAidRequest(
                aggregate,
                version,
                createdAt.toInstant()
        );
    }

    private ReservationId readReservationId(
            ResultSet resultSet
    ) throws SQLException {
        String value = resultSet.getString("reservation_id");

        return value == null
                ? null
                : new ReservationId(value);
    }

    private ReservationFailureReason readReservationFailureReason(
            ResultSet resultSet
    ) throws SQLException {
        String value = resultSet.getString(
                "reservation_failure_reason"
        );

        return value == null
                ? null
                : new ReservationFailureReason(value);
    }

    private DispatchDetails readDispatchDetails(
            ResultSet resultSet
    ) throws SQLException {
        String reference = resultSet.getString(
                "dispatch_responder_reference"
        );

        OffsetDateTime dispatchedAt = resultSet.getObject(
                "dispatched_at",
                OffsetDateTime.class
        );

        if (reference == null && dispatchedAt == null) {
            return null;
        }

        if (reference == null || dispatchedAt == null) {
            throw new SQLException(
                    "Stored dispatch details are incomplete"
            );
        }

        return new DispatchDetails(
                reference,
                dispatchedAt.toInstant()
        );
    }

    private DeliveryDetails readDeliveryDetails(
            ResultSet resultSet
    ) throws SQLException {
        String reference = resultSet.getString(
                "delivery_confirmation_reference"
        );

        OffsetDateTime deliveredAt = resultSet.getObject(
                "delivered_at",
                OffsetDateTime.class
        );

        if (reference == null && deliveredAt == null) {
            return null;
        }

        if (reference == null || deliveredAt == null) {
            throw new SQLException(
                    "Stored delivery details are incomplete"
            );
        }

        return new DeliveryDetails(
                reference,
                deliveredAt.toInstant()
        );
    }

    private CancellationReason readCancellationReason(
            ResultSet resultSet
    ) throws SQLException {
        String value = resultSet.getString(
                "cancellation_reason"
        );

        return value == null
                ? null
                : new CancellationReason(value);
    }
}

