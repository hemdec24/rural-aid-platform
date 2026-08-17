package org.ruralaid.workflow.api.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.ruralaid.workflow.api.model.ApiError;
import org.ruralaid.workflow.application.exception.AidRequestNotFoundException;

@Provider
public final class AidRequestNotFoundExceptionMapper implements ExceptionMapper<AidRequestNotFoundException> {

    @Override
    public Response toResponse(
            AidRequestNotFoundException exception
    ) {
        return Response
                .status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(
                        "AID_REQUEST_NOT_FOUND",
                        exception.getMessage()
                ))
                .build();
    }
}

