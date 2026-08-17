package org.ruralaid.workflow.api;

import org.ruralaid.workflow.api.model.AidRequestResponse;
import org.ruralaid.workflow.api.model.LocationRequest;
import org.ruralaid.workflow.api.model.LocationResponse;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.domain.DeliveryDetails;
import org.ruralaid.workflow.domain.DispatchDetails;
import org.ruralaid.workflow.domain.Location;
import org.ruralaid.workflow.domain.NeedCategory;
import org.ruralaid.workflow.domain.Priority;

import java.util.Locale;

public final class AidRequestApiMapper {

    private AidRequestApiMapper() {
    }

    public static Location toDomain(LocationRequest request) {
        return new Location(
                request.latitude(),
                request.longitude()
        );
    }

    public static NeedCategory toNeedCategory(String value) {
        return NeedCategory.valueOf(
                value.trim().toUpperCase(Locale.ROOT)
        );
    }

    public static Priority toPriority(String value) {
        return Priority.valueOf(
                value.trim().toUpperCase(Locale.ROOT)
        );
    }

    public static AidRequestResponse toResponse(
            VersionedAidRequest storedRequest
    ) {
        var aggregate = storedRequest.aggregate();

        DispatchDetails dispatchDetails =
                aggregate.dispatchDetails().orElse(null);

        DeliveryDetails deliveryDetails =
                aggregate.deliveryDetails().orElse(null);

        return new AidRequestResponse(
                aggregate.id().id(),
                new LocationResponse(
                        aggregate.location().latitude(),
                        aggregate.location().longitude()
                ),
                aggregate.needCategory().name(),
                aggregate.priority().name(),
                aggregate.status().name(),
                aggregate.reservationId()
                        .map(value -> value.id())
                        .orElse(null),
                aggregate.reservationFailureReason()
                        .map(value -> value.reason())
                        .orElse(null),
                dispatchDetails == null
                        ? null
                        : dispatchDetails.responderReference(),
                dispatchDetails == null
                        ? null
                        : dispatchDetails.dispatchedAt(),
                deliveryDetails == null
                        ? null
                        : deliveryDetails.confirmationReference(),
                deliveryDetails == null
                        ? null
                        : deliveryDetails.deliveredAt(),
                aggregate.cancellationReason()
                        .map(value -> value.reason())
                        .orElse(null),
                storedRequest.version(),
                storedRequest.createdAt()
        );
    }
}

