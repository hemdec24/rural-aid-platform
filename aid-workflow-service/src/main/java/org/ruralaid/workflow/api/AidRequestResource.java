package org.ruralaid.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.ruralaid.workflow.api.model.AidRequestResponse;
import org.ruralaid.workflow.api.model.CreateAidRequestRequest;
import org.ruralaid.workflow.application.AidRequestApplicationService;
import org.ruralaid.workflow.application.model.AidRequestCursor;
import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.api.model.CancelAidRequestRequest;
import org.ruralaid.workflow.api.model.CorrectLocationRequest;
import org.ruralaid.workflow.api.model.ExpectedVersionRequest;
import org.ruralaid.workflow.domain.CancellationReason;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Path("/aid-requests")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class AidRequestResource {

    private final AidRequestApplicationService applicationService;

    public AidRequestResource(
            AidRequestApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(applicationService);
    }

    @POST
    public Response create(
            @NotNull @Valid CreateAidRequestRequest request
    ) {
        var storedRequest = applicationService.create(
                AidRequestApiMapper.toDomain(request.location()),
                AidRequestApiMapper.toNeedCategory(
                        request.needCategory()
                ),
                AidRequestApiMapper.toPriority(
                        request.priority()
                )
        );

        return Response
                .status(Response.Status.CREATED)
                .entity(AidRequestApiMapper.toResponse(storedRequest))
                .build();
    }

    @GET
    @Path("/{requestId}")
    public AidRequestResponse getById(
            @PathParam("requestId") String requestId
    ) {
        var storedRequest = applicationService.getById(
                new AidRequestId(requestId)
        );

        return AidRequestApiMapper.toResponse(storedRequest);
    }

    @GET
    public List<AidRequestResponse> list(
            @QueryParam("limit")
            @DefaultValue("20")
            @Min(1)
            @Max(100)
            int limit,

            @QueryParam("cursorCreatedAt")
            String cursorCreatedAt,

            @QueryParam("cursorRequestId")
            String cursorRequestId
    ) {
        Optional<AidRequestCursor> cursor = parseCursor(
                cursorCreatedAt,
                cursorRequestId
        );

        return applicationService.list(limit, cursor)
                .stream()
                .map(AidRequestApiMapper::toResponse)
                .toList();
    }

    @POST
    @Path("/{requestId}/commands/correct-location")
    public AidRequestResponse correctLocation(
            @PathParam("requestId") String requestId,
            @NotNull @Valid CorrectLocationRequest request
    ) {
        var updatedRequest = applicationService.correctLocation(
                new AidRequestId(requestId),
                request.expectedVersion(),
                AidRequestApiMapper.toDomain(request.location())
        );

        return AidRequestApiMapper.toResponse(updatedRequest);
    }

    @POST
    @Path("/{requestId}/commands/validate")
    public AidRequestResponse validate(
            @PathParam("requestId") String requestId,
            @NotNull @Valid ExpectedVersionRequest request
    ) {
        var updatedRequest = applicationService.markValidated(
                new AidRequestId(requestId),
                request.expectedVersion()
        );

        return AidRequestApiMapper.toResponse(updatedRequest);
    }

    @POST
    @Path("/{requestId}/commands/require-review")
    public AidRequestResponse requireReview(
            @PathParam("requestId") String requestId,
            @NotNull @Valid ExpectedVersionRequest request
    ) {
        var updatedRequest = applicationService.markReviewRequired(
                new AidRequestId(requestId),
                request.expectedVersion()
        );

        return AidRequestApiMapper.toResponse(updatedRequest);
    }

    @POST
    @Path("/{requestId}/commands/approve-review")
    public AidRequestResponse approveReview(
            @PathParam("requestId") String requestId,
            @NotNull @Valid ExpectedVersionRequest request
    ) {
        var updatedRequest = applicationService.markReviewApproved(
                new AidRequestId(requestId),
                request.expectedVersion()
        );

        return AidRequestApiMapper.toResponse(updatedRequest);
    }

    @POST
    @Path("/{requestId}/commands/cancel")
    public AidRequestResponse cancel(
            @PathParam("requestId") String requestId,
            @NotNull @Valid CancelAidRequestRequest request
    ) {
        var updatedRequest = applicationService.cancel(
                new AidRequestId(requestId),
                request.expectedVersion(),
                new CancellationReason(request.reason())
        );

        return AidRequestApiMapper.toResponse(updatedRequest);
    }

    private static Optional<AidRequestCursor> parseCursor(
            String createdAt,
            String requestId
    ) {
        boolean createdAtMissing =
                createdAt == null || createdAt.isBlank();

        boolean requestIdMissing =
                requestId == null || requestId.isBlank();

        if (createdAtMissing && requestIdMissing) {
            return Optional.empty();
        }

        if (createdAtMissing || requestIdMissing) {
            throw new BadRequestException(
                    "cursorCreatedAt and cursorRequestId "
                            + "must be provided together"
            );
        }

        try {
            return Optional.of(
                    new AidRequestCursor(
                            Instant.parse(createdAt),
                            new AidRequestId(requestId)
                    )
            );
        } catch (
                DateTimeParseException | IllegalArgumentException exception
        ) {
            throw new BadRequestException(
                    "Cursor values are invalid"
            );
        }
    }
}

