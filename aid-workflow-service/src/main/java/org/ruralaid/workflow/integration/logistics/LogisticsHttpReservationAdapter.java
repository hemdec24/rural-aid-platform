package org.ruralaid.workflow.integration.logistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.ruralaid.workflow.application.exception.ReservationCallException;
import org.ruralaid.workflow.application.model.ReservationCommand;
import org.ruralaid.workflow.application.model.ReservationResult;
import org.ruralaid.workflow.application.port.InventoryReservationPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class LogisticsHttpReservationAdapter implements InventoryReservationPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI reservationEndpoint;
    private final Duration requestTimeout;

    public LogisticsHttpReservationAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI logisticsBaseUri,
            Duration requestTimeout
    ) {
        this.httpClient = Objects.requireNonNull(
                httpClient,
                "HTTP client is required"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "Object mapper is required"
        );
        this.reservationEndpoint = Objects.requireNonNull(
                logisticsBaseUri,
                "Logistics base URI is required"
        ).resolve("/inventory-reservations");
        this.requestTimeout = Objects.requireNonNull(
                requestTimeout,
                "Request timeout is required"
        );
    }

    @Override
    public ReservationResult reserve(ReservationCommand command) {
        Objects.requireNonNull(
                command,
                "Reservation command is required"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(reservationEndpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                serialize(command),
                                StandardCharsets.UTF_8
                        )
                )
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            return mapResponse(command, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ReservationCallException(
                    "Logistics reservation call was interrupted",
                    exception
            );
        } catch (IOException exception) {
            throw new ReservationCallException(
                    "Logistics reservation call failed",
                    exception
            );
        }
    }

    private String serialize(ReservationCommand command) {
        LogisticsReservationRequest request =
                new LogisticsReservationRequest(
                        command.reservationId().asUuid(),
                        command.aidRequestId().id(),
                        command.inventoryItemId().id(),
                        command.quantity()
                );

        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new ReservationCallException(
                    "Could not serialize the reservation request",
                    exception
            );
        }
    }

    private ReservationResult mapResponse(
            ReservationCommand command,
            HttpResponse<String> response
    ) {
        if (response.statusCode() == 201) {
            LogisticsReservationResponse body = readBody(
                    response.body(),
                    LogisticsReservationResponse.class
            );

            if (!command.reservationId().asUuid()
                    .equals(body.reservationId())
                    || !"RESERVED".equals(body.outcome())) {
                throw new ReservationCallException(
                        "Logistics returned an invalid reservation response"
                );
            }

            return ReservationResult.RESERVED;
        }

        if (response.statusCode() == 409) {
            LogisticsErrorResponse error = readBody(
                    response.body(),
                    LogisticsErrorResponse.class
            );

            if ("INVENTORY_UNAVAILABLE".equals(error.code())) {
                return ReservationResult.UNAVAILABLE;
            }

            if ("RESERVATION_ID_CONFLICT".equals(error.code())) {
                return ReservationResult.RESERVATION_ID_CONFLICT;
            }
        }

        throw new ReservationCallException(
                "Unexpected Logistics response status: "
                        + response.statusCode()
        );
    }

    private <T> T readBody(String body, Class<T> responseType) {
        try {
            return objectMapper.readValue(body, responseType);
        } catch (JsonProcessingException exception) {
            throw new ReservationCallException(
                    "Could not read the Logistics response",
                    exception
            );
        }
    }

    private record LogisticsReservationRequest(
            UUID reservationId,
            String aidRequestId,
            UUID inventoryItemId,
            int quantity
    ) {
    }

    private record LogisticsReservationResponse(
            UUID reservationId,
            String outcome
    ) {
    }

    private record LogisticsErrorResponse(
            String code,
            String message
    ) {
    }
}

