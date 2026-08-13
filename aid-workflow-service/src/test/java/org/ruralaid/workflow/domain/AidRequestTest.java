package org.ruralaid.workflow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AidRequestTest {
    UUID id = UUID.randomUUID();

    @Test
    void newAidRequestBeginsInReceivedState() {

        AidRequest aidRequest = new AidRequest(
                new AidRequestId("AR-01"),
                new Location(0.0, 0.0),
                NeedCategory.WATER,
                Priority.STANDARD
        );

        assertEquals(AidRequestStatus.RECEIVED, aidRequest.status());
    }

    @Test
    void aidRequestRejectsMissingRequiredCreationInputs() {

        IllegalArgumentException ex = assertThrows( IllegalArgumentException.class, () ->
                new AidRequest(
                        null,
                        new Location(0.0, 0.0),
                        NeedCategory.WATER,
                        Priority.STANDARD
                )
        );
        assertEquals("Aid request ID must not be null", ex.getMessage());

        ex = assertThrows( IllegalArgumentException.class, () ->
                new AidRequest(
                        new AidRequestId("AR-01"),
                        null,
                        NeedCategory.WATER,
                        Priority.STANDARD
                )
        );
        assertEquals("Location must not be null", ex.getMessage());

        ex = assertThrows( IllegalArgumentException.class, () ->
                new AidRequest(
                        new AidRequestId("AR-01"),
                        new Location(0.0, 0.0),
                        null,
                        Priority.STANDARD
                )
        );
        assertEquals("Need category must not be null", ex.getMessage());

        ex = assertThrows( IllegalArgumentException.class, () ->
                new AidRequest(
                        new AidRequestId("AR-01"),
                        new Location(0.0, 0.0),
                        NeedCategory.WATER,
                        null
                )
        );
        assertEquals("Priority must not be null", ex.getMessage());
    }

    @Test
    void validRequestCanCompleteMainLifecycle() {
        AidRequest aidRequest = newAidRequest();

        ReservationId reservationId =
                new ReservationId("RSV-01");

        DispatchDetails dispatchDetails =
                new DispatchDetails(
                        "RESPONDER-01",
                        Instant.parse("2026-08-12T15:00:00Z")
                );

        DeliveryDetails deliveryDetails =
                new DeliveryDetails(
                        "CONFIRMATION-01",
                        Instant.parse("2026-08-12T16:00:00Z")
                );

        aidRequest.markValidated();
        aidRequest.markMatchingStarted();
        aidRequest.markReserved(reservationId);
        aidRequest.markDispatched(dispatchDetails);
        aidRequest.markDelivered(deliveryDetails);
        aidRequest.markCompleted();

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.COMPLETED,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(reservationId),
                        aidRequest.reservationId()
                ),
                () -> assertEquals(
                        Optional.of(dispatchDetails),
                        aidRequest.dispatchDetails()
                ),
                () -> assertEquals(
                        Optional.of(deliveryDetails),
                        aidRequest.deliveryDetails()
                )
        );
    }

    @Test
    void illegalReservationLeavesRequestUnchanged() {
        AidRequest aidRequest = newAidRequest();

        assertThrows(
                IllegalStateException.class,
                () -> aidRequest.markReserved(
                        new ReservationId("RSV-01")
                )
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.RECEIVED,
                        aidRequest.status()
                ),
                () -> assertTrue(
                        aidRequest.reservationId().isEmpty()
                )
        );
    }

    @Test
    void retryingMatchingClearsPreviousFailureReason() {
        AidRequest aidRequest = newAidRequest();
        ReservationFailureReason reason =
                new ReservationFailureReason("No water available");

        aidRequest.markValidated();
        aidRequest.markMatchingStarted();
        aidRequest.markReservationFailed(reason);

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.RESERVATION_FAILED,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(reason),
                        aidRequest.reservationFailureReason()
                )
        );

        aidRequest.markMatchingRetried();

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.MATCH_PENDING,
                        aidRequest.status()
                ),
                () -> assertTrue(
                        aidRequest.reservationFailureReason().isEmpty()
                )
        );
    }

    @Test
    void deliveryBeforeDispatchTimeIsRejectedWithoutChangingRequest() {
        Instant dispatchedAt =
                Instant.parse("2026-08-12T15:00:00Z");

        AidRequest aidRequest =
                dispatchedAidRequest(dispatchedAt);

        DeliveryDetails invalidDelivery =
                new DeliveryDetails(
                        "CONFIRMATION-01",
                        dispatchedAt.minusSeconds(60)
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> aidRequest.markDelivered(invalidDelivery)
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.DISPATCHED,
                        aidRequest.status()
                ),
                () -> assertTrue(
                        aidRequest.deliveryDetails().isEmpty()
                )
        );
    }

    @Test
    void requestCanBeCancelledBeforeDispatch() {
        AidRequest aidRequest = newAidRequest();
        CancellationReason reason =
                new CancellationReason("Aid no longer required");

        aidRequest.markCancelled(reason);

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.CANCELLED,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(reason),
                        aidRequest.cancellationReason()
                )
        );
    }

    @Test
    void dispatchedRequestCannotBeCancelled() {
        AidRequest aidRequest = dispatchedAidRequest(
                Instant.parse("2026-08-12T15:00:00Z")
        );

        assertThrows(
                IllegalStateException.class,
                () -> aidRequest.markCancelled(
                        new CancellationReason("Aid no longer required")
                )
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.DISPATCHED,
                        aidRequest.status()
                ),
                () -> assertTrue(
                        aidRequest.cancellationReason().isEmpty()
                )
        );
    }

    @Test
    void terminalRequestsRejectFurtherLifecycleOperations() {
        AidRequest cancelled = newAidRequest();
        cancelled.markCancelled(
                new CancellationReason("Duplicate request")
        );

        AidRequest completed = dispatchedAidRequest(
                Instant.parse("2026-08-12T15:00:00Z")
        );
        completed.markDelivered(
                new DeliveryDetails(
                        "CONFIRMATION-01",
                        Instant.parse("2026-08-12T16:00:00Z")
                )
        );
        completed.markCompleted();

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        cancelled::markValidated
                ),
                () -> assertEquals(
                        AidRequestStatus.CANCELLED,
                        cancelled.status()
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> completed.markCancelled(
                                new CancellationReason("Too late")
                        )
                ),
                () -> assertEquals(
                        AidRequestStatus.COMPLETED,
                        completed.status()
                )
        );
    }

    @Test
    void locationCanBeCorrectedOnlyBeforeValidation() {
        AidRequest aidRequest = newAidRequest();
        Location correctedLocation =
                new Location(32.7767, -96.7970);

        aidRequest.correctLocation(correctedLocation);

        assertEquals(
                correctedLocation,
                aidRequest.location()
        );

        aidRequest.markValidated();

        assertThrows(
                IllegalStateException.class,
                () -> aidRequest.correctLocation(
                        new Location(33.0, -97.0)
                )
        );

        assertEquals(
                correctedLocation,
                aidRequest.location()
        );
    }

    private AidRequest newAidRequest() {
        return new AidRequest(
                new AidRequestId("AR-01"),
                new Location(0.0, 0.0),
                NeedCategory.WATER,
                Priority.STANDARD
        );
    }

    private AidRequest dispatchedAidRequest(Instant dispatchedAt) {
        AidRequest aidRequest = newAidRequest();

        aidRequest.markValidated();
        aidRequest.markMatchingStarted();
        aidRequest.markReserved(
                new ReservationId("RSV-01")
        );
        aidRequest.markDispatched(
                new DispatchDetails(
                        "RESPONDER-01",
                        dispatchedAt
                )
        );

        return aidRequest;
    }
}
