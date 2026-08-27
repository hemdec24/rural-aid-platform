package org.ruralaid.logistics.api;

import java.util.Objects;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.ruralaid.logistics.api.model.ApiErrorResponse;
import org.ruralaid.logistics.api.model.ReleaseReservationResponse;
import org.ruralaid.logistics.api.model.ReserveInventoryRequest;
import org.ruralaid.logistics.api.model.ReserveInventoryResponse;
import org.ruralaid.logistics.application.InventoryReservationService;
import org.ruralaid.logistics.domain.ReserveInventory;
import org.ruralaid.logistics.domain.ReleaseOutcome;

@Path("/inventory-reservations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class InventoryReservationResource {

    private final InventoryReservationService reservationService;

    public InventoryReservationResource(
            InventoryReservationService reservationService
    ) {
        this.reservationService = Objects.requireNonNull(
                reservationService,
                "Inventory reservation service is required"
        );
    }

    @POST
    public Response createReservation(
            @Valid
            @NotNull(message = "Reservation request is required")
            ReserveInventoryRequest request
    ) {
        ReserveInventory command = new ReserveInventory(
                request.reservationId(),
                request.aidRequestId(),
                request.inventoryItemId(),
                request.quantity()
        );

        UUID reservationId =
                reservationService.reserve(command);

        return Response.status(Response.Status.CREATED)
                .entity(
                        new ReserveInventoryResponse(
                                reservationId,
                                "RESERVED"
                        )
                )
                .build();
    }

    @POST
    @Path("/{reservationId}/release")
    public Response releaseReservation(
            @PathParam("reservationId") UUID reservationId
    ) {
        ReleaseOutcome outcome =
                reservationService.release(reservationId);

        if (outcome == ReleaseOutcome.NOT_FOUND) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(
                            new ApiErrorResponse(
                                    "RESERVATION_NOT_FOUND",
                                    "Reservation does not exist"
                            )
                    )
                    .build();
        }

        return Response.ok(
                new ReleaseReservationResponse(
                        reservationId,
                        "RELEASED"
                )
        ).build();
    }
}
