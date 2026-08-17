package org.ruralaid.workflow.api.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.ruralaid.workflow.api.model.ApiError;

@Provider
public final class AidRequestStateConflictExceptionMapper
        implements ExceptionMapper<IllegalStateException> {

    @Override
    public Response toResponse(IllegalStateException exception) {
        return Response
                .status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(
                        "AID_REQUEST_STATE_CONFLICT",
                        exception.getMessage()
                ))
                .build();
    }
}

