package org.ruralaid.workflow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DomainValueTest {

    @Test
    void identifiersRejectMissingValues() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AidRequestId(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AidRequestId("   ")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ReservationId(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ReservationId("   ")
                )
        );
    }

    @Test
    void locationRejectsInvalidCoordinates() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Location(-90.1, 0.0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Location(90.1, 0.0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Location(0.0, -180.1)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Location(0.0, 180.1)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Location(Double.NaN, 0.0)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Location(
                                0.0,
                                Double.POSITIVE_INFINITY
                        )
                )
        );
    }

    @Test
    void dispatchDetailsRequireResponderAndTime() {
        Instant time = Instant.parse("2026-08-12T15:00:00Z");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DispatchDetails(null, time)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DispatchDetails("   ", time)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DispatchDetails("RESPONDER-01", null)
                )
        );
    }

    @Test
    void deliveryDetailsRequireConfirmationAndTime() {
        Instant time = Instant.parse("2026-08-12T16:00:00Z");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DeliveryDetails(null, time)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DeliveryDetails("   ", time)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DeliveryDetails("CONFIRMATION-01", null)
                )
        );
    }

    @Test
    void equivalentValueObjectsAreEqual() {
        assertEquals(
                new AidRequestId("AR-01"),
                new AidRequestId("AR-01")
        );

        assertNotEquals(
                new AidRequestId("AR-01"),
                new AidRequestId("AR-02")
        );
    }
}