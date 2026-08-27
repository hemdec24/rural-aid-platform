package org.ruralaid.workflow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AidRequestTest {

    private static final ReservationId RESERVATION_ID =
            new ReservationId(
                    "00000000-0000-0000-0000-000000000001"
            );

    private static final InventoryItemId INVENTORY_ITEM_ID =
            new InventoryItemId(
                    UUID.fromString(
                            "00000000-0000-0000-0000-000000000101"
                    )
            );

    private static final int QUANTITY = 4;

    @Test
    void newAidRequestBeginsInReceivedState() {
        AidRequest aidRequest = newAidRequest();

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.RECEIVED,
                        aidRequest.status()
                ),
                () -> assertTrue(aidRequest.reservationId().isEmpty()),
                () -> assertTrue(
                        aidRequest.reservationInventoryItemId().isEmpty()
                ),
                () -> assertTrue(
                        aidRequest.reservationQuantity().isEmpty()
                )
        );
    }

    @Test
    void aidRequestRejectsMissingRequiredCreationInputs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AidRequest(
                        null,
                        new Location(0.0, 0.0),
                        NeedCategory.WATER,
                        Priority.STANDARD
                )
        );

        assertEquals(
                "Aid request ID must not be null",
                exception.getMessage()
        );

        exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AidRequest(
                        new AidRequestId("AR-01"),
                        null,
                        NeedCategory.WATER,
                        Priority.STANDARD
                )
        );

        assertEquals(
                "Location must not be null",
                exception.getMessage()
        );

        exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AidRequest(
                        new AidRequestId("AR-01"),
                        new Location(0.0, 0.0),
                        null,
                        Priority.STANDARD
                )
        );

        assertEquals(
                "Need category must not be null",
                exception.getMessage()
        );

        exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AidRequest(
                        new AidRequestId("AR-01"),
                        new Location(0.0, 0.0),
                        NeedCategory.WATER,
                        null
                )
        );

        assertEquals(
                "Priority must not be null",
                exception.getMessage()
        );
    }

    @Test
    void validRequestCanCompleteMainLifecycle() {
        AidRequest aidRequest = newAidRequest();

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
        aidRequest.markMatchingStarted(
                RESERVATION_ID,
                INVENTORY_ITEM_ID,
                QUANTITY
        );
        aidRequest.markReserved();
        aidRequest.markDispatched(dispatchDetails);
        aidRequest.markDelivered(deliveryDetails);
        aidRequest.markCompleted();

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.COMPLETED,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(RESERVATION_ID),
                        aidRequest.reservationId()
                ),
                () -> assertEquals(
                        Optional.of(INVENTORY_ITEM_ID),
                        aidRequest.reservationInventoryItemId()
                ),
                () -> assertEquals(
                        Optional.of(QUANTITY),
                        aidRequest.reservationQuantity()
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
                () -> aidRequest.markMatchingStarted(
                        RESERVATION_ID,
                        INVENTORY_ITEM_ID,
                        QUANTITY
                )
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.RECEIVED,
                        aidRequest.status()
                ),
                () -> assertTrue(aidRequest.reservationId().isEmpty()),
                () -> assertTrue(
                        aidRequest.reservationInventoryItemId().isEmpty()
                ),
                () -> assertTrue(
                        aidRequest.reservationQuantity().isEmpty()
                )
        );
    }

    @Test
    void newMatchingAttemptReplacesAttemptFactsAndClearsFailure() {
        AidRequest aidRequest = newAidRequest();

        ReservationId newReservationId =
                new ReservationId(
                        "00000000-0000-0000-0000-000000000002"
                );

        InventoryItemId newInventoryItemId =
                new InventoryItemId(
                        UUID.fromString(
                                "00000000-0000-0000-0000-000000000102"
                        )
                );

        ReservationFailureReason reason =
                new ReservationFailureReason(
                        "No water available"
                );

        aidRequest.markValidated();
        aidRequest.markMatchingStarted(
                RESERVATION_ID,
                INVENTORY_ITEM_ID,
                QUANTITY
        );
        aidRequest.markReservationFailed(reason);

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.RESERVATION_FAILED,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(RESERVATION_ID),
                        aidRequest.reservationId()
                ),
                () -> assertEquals(
                        Optional.of(INVENTORY_ITEM_ID),
                        aidRequest.reservationInventoryItemId()
                ),
                () -> assertEquals(
                        Optional.of(QUANTITY),
                        aidRequest.reservationQuantity()
                ),
                () -> assertEquals(
                        Optional.of(reason),
                        aidRequest.reservationFailureReason()
                )
        );

        aidRequest.startNewMatchingAttempt(
                newReservationId,
                newInventoryItemId,
                2
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.MATCH_PENDING,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(newReservationId),
                        aidRequest.reservationId()
                ),
                () -> assertEquals(
                        Optional.of(newInventoryItemId),
                        aidRequest.reservationInventoryItemId()
                ),
                () -> assertEquals(
                        Optional.of(2),
                        aidRequest.reservationQuantity()
                ),
                () -> assertTrue(
                        aidRequest.reservationFailureReason().isEmpty()
                )
        );
    }

    @Test
    void matchingStartStoresCompleteReservationAttempt() {
        AidRequest aidRequest = newAidRequest();

        aidRequest.markValidated();
        aidRequest.markMatchingStarted(
                RESERVATION_ID,
                INVENTORY_ITEM_ID,
                QUANTITY
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.MATCH_PENDING,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(RESERVATION_ID),
                        aidRequest.reservationId()
                ),
                () -> assertEquals(
                        Optional.of(INVENTORY_ITEM_ID),
                        aidRequest.reservationInventoryItemId()
                ),
                () -> assertEquals(
                        Optional.of(QUANTITY),
                        aidRequest.reservationQuantity()
                )
        );
    }

    @Test
    void matchingStartRejectsInvalidAttemptWithoutChangingRequest() {
        AidRequest aidRequest = newAidRequest();
        aidRequest.markValidated();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> aidRequest.markMatchingStarted(
                                null,
                                INVENTORY_ITEM_ID,
                                QUANTITY
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> aidRequest.markMatchingStarted(
                                RESERVATION_ID,
                                null,
                                QUANTITY
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> aidRequest.markMatchingStarted(
                                RESERVATION_ID,
                                INVENTORY_ITEM_ID,
                                0
                        )
                )
        );

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.VALIDATED,
                        aidRequest.status()
                ),
                () -> assertTrue(aidRequest.reservationId().isEmpty()),
                () -> assertTrue(
                        aidRequest.reservationInventoryItemId().isEmpty()
                ),
                () -> assertTrue(
                        aidRequest.reservationQuantity().isEmpty()
                )
        );
    }

    @Test
    void reservationSuccessPreservesCompleteAttempt() {
        AidRequest aidRequest = newAidRequest();

        aidRequest.markValidated();
        aidRequest.markMatchingStarted(
                RESERVATION_ID,
                INVENTORY_ITEM_ID,
                QUANTITY
        );
        aidRequest.markReserved();

        assertAll(
                () -> assertEquals(
                        AidRequestStatus.RESERVED,
                        aidRequest.status()
                ),
                () -> assertEquals(
                        Optional.of(RESERVATION_ID),
                        aidRequest.reservationId()
                ),
                () -> assertEquals(
                        Optional.of(INVENTORY_ITEM_ID),
                        aidRequest.reservationInventoryItemId()
                ),
                () -> assertEquals(
                        Optional.of(QUANTITY),
                        aidRequest.reservationQuantity()
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
                new CancellationReason(
                        "Aid no longer required"
                );

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
                        new CancellationReason(
                                "Aid no longer required"
                        )
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

    private AidRequest dispatchedAidRequest(
            Instant dispatchedAt
    ) {
        AidRequest aidRequest = newAidRequest();

        aidRequest.markValidated();
        aidRequest.markMatchingStarted(
                RESERVATION_ID,
                INVENTORY_ITEM_ID,
                QUANTITY
        );
        aidRequest.markReserved();
        aidRequest.markDispatched(
                new DispatchDetails(
                        "RESPONDER-01",
                        dispatchedAt
                )
        );

        return aidRequest;
    }
}