package org.ruralaid.workflow.api.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.ruralaid.workflow.api.model.ApiError;
import org.ruralaid.workflow.application.exception.AidRequestVersionConflictException;

@Provider
public final class AidRequestVersionConflictExceptionMapper
        implements ExceptionMapper<AidRequestVersionConflictException> {

    @Override
    public Response toResponse(
            AidRequestVersionConflictException exception
    ) {
        return Response
                .status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(
                        "AID_REQUEST_VERSION_CONFLICT",
                        exception.getMessage()
                ))
                .build();
    }
}

