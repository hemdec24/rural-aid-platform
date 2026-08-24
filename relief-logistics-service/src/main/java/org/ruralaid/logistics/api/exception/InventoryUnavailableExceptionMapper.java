package org.ruralaid.logistics.api.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.ruralaid.logistics.api.model.ApiErrorResponse;
import org.ruralaid.logistics.application.exception.InventoryUnavailableException;

@Provider
public final class InventoryUnavailableExceptionMapper
        implements ExceptionMapper<InventoryUnavailableException> {

    @Override
    public Response toResponse(
            InventoryUnavailableException exception
    ) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(
                        new ApiErrorResponse(
                                "INVENTORY_UNAVAILABLE",
                                "Requested inventory is unavailable"
                        )
                )
                .build();
    }
}