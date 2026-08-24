package org.ruralaid.logistics.api.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.ruralaid.logistics.api.model.ApiErrorResponse;
import org.ruralaid.logistics.application.exception.ReservationIdConflictException;

@Provider
public final class ReservationIdConflictExceptionMapper
        implements ExceptionMapper<ReservationIdConflictException> {

    @Override
    public Response toResponse(ReservationIdConflictException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(
                        new ApiErrorResponse(
                                "RESERVATION_ID_CONFLICT",
                                "Reservation ID is already associated "
                                        + "with a different request"
                        )
                )
                .build();
    }
}