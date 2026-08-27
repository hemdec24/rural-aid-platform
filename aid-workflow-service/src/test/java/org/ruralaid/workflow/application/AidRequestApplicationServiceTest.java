package org.ruralaid.workflow.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import org.ruralaid.workflow.application.exception.ReservationCallException;
import org.ruralaid.workflow.application.model.ReleaseResult;
import org.ruralaid.workflow.application.model.ReservationResult;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.application.port.AidRequestRepository;
import org.ruralaid.workflow.application.port.InventoryReservationPort;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.domain.AidRequestStatus;
import org.ruralaid.workflow.domain.CancellationReason;
import org.ruralaid.workflow.domain.InventoryItemId;
import org.ruralaid.workflow.domain.Location;
import org.ruralaid.workflow.domain.NeedCategory;
import org.ruralaid.workflow.domain.Priority;
import org.ruralaid.workflow.domain.ReservationId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class AidRequestApplicationServiceTest {

    private static final AidRequestId REQUEST_ID =
            new AidRequestId("AR-COORDINATOR-01");

    private static final InventoryItemId INVENTORY_ITEM_ID =
            new InventoryItemId(
                    UUID.fromString(
                            "00000000-0000-0000-0000-000000000101"
                    )
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-27T12:00:00Z");

    @Test
    void commitsPendingBeforeCallingLogisticsAndFencesCompletion() {
        AidRequestRepository repository =
                mock(AidRequestRepository.class);

        InventoryReservationPort reservationPort =
                mock(InventoryReservationPort.class);

        AidRequest aidRequest = validatedAidRequest();

        when(repository.findById(REQUEST_ID))
                .thenReturn(
                        Optional.of(
                                new VersionedAidRequest(
                                        aidRequest,
                                        7,
                                        CREATED_AT
                                )
                        )
                );

        AtomicInteger updates = new AtomicInteger();

        when(repository.update(any()))
                .thenAnswer(invocation -> {
                    VersionedAidRequest argument =
                            invocation.getArgument(0);

                    if (updates.getAndIncrement() == 0) {
                        assertEquals(
                                AidRequestStatus.MATCH_PENDING,
                                argument.aggregate().status()
                        );
                        assertEquals(7, argument.version());

                        return new VersionedAidRequest(
                                argument.aggregate(),
                                8,
                                CREATED_AT
                        );
                    }

                    assertEquals(
                            AidRequestStatus.RESERVED,
                            argument.aggregate().status()
                    );
                    assertEquals(8, argument.version());

                    return new VersionedAidRequest(
                            argument.aggregate(),
                            9,
                            CREATED_AT
                    );
                });

        when(reservationPort.reserve(any()))
                .thenReturn(ReservationResult.RESERVED);

        AidRequestApplicationService service =
                new AidRequestApplicationService(
                        repository,
                        reservationPort
                );

        VersionedAidRequest result = service.reserveInventory(
                REQUEST_ID,
                7,
                INVENTORY_ITEM_ID,
                4
        );

        assertEquals(AidRequestStatus.RESERVED, result.aggregate().status());
        assertEquals(9, result.version());

        InOrder order = inOrder(repository, reservationPort);
        order.verify(repository).findById(REQUEST_ID);
        order.verify(repository).update(any());
        order.verify(reservationPort).reserve(any());
        order.verify(repository).update(any());
    }

    @Test
    void technicalReservationFailureLeavesDurablePendingState() {
        AidRequestRepository repository =
                mock(AidRequestRepository.class);

        InventoryReservationPort reservationPort =
                mock(InventoryReservationPort.class);

        AidRequest aidRequest = validatedAidRequest();

        when(repository.findById(REQUEST_ID))
                .thenReturn(
                        Optional.of(
                                new VersionedAidRequest(
                                        aidRequest,
                                        3,
                                        CREATED_AT
                                )
                        )
                );

        when(repository.update(any()))
                .thenAnswer(invocation -> {
                    VersionedAidRequest argument =
                            invocation.getArgument(0);

                    assertEquals(
                            AidRequestStatus.MATCH_PENDING,
                            argument.aggregate().status()
                    );

                    return new VersionedAidRequest(
                            argument.aggregate(),
                            4,
                            CREATED_AT
                    );
                });

        when(reservationPort.reserve(any()))
                .thenThrow(
                        new ReservationCallException(
                                "simulated timeout"
                        )
                );

        AidRequestApplicationService service =
                new AidRequestApplicationService(
                        repository,
                        reservationPort
                );

        assertThrows(
                ReservationCallException.class,
                () -> service.reserveInventory(
                        REQUEST_ID,
                        3,
                        INVENTORY_ITEM_ID,
                        4
                )
        );

        assertEquals(AidRequestStatus.MATCH_PENDING, aidRequest.status());
        verify(repository, times(1)).update(any());
    }

    @Test
    void cancellationCommitsReleasePendingBeforeExternalRelease() {
        AidRequestRepository repository =
                mock(AidRequestRepository.class);

        InventoryReservationPort reservationPort =
                mock(InventoryReservationPort.class);

        AidRequest aidRequest = reservedAidRequest();

        when(repository.findById(REQUEST_ID))
                .thenReturn(
                        Optional.of(
                                new VersionedAidRequest(
                                        aidRequest,
                                        11,
                                        CREATED_AT
                                )
                        )
                );

        AtomicInteger updates = new AtomicInteger();

        when(repository.update(any()))
                .thenAnswer(invocation -> {
                    VersionedAidRequest argument =
                            invocation.getArgument(0);

                    if (updates.getAndIncrement() == 0) {
                        assertEquals(
                                AidRequestStatus.RELEASE_PENDING,
                                argument.aggregate().status()
                        );

                        return new VersionedAidRequest(
                                argument.aggregate(),
                                12,
                                CREATED_AT
                        );
                    }

                    assertEquals(
                            AidRequestStatus.CANCELLED,
                            argument.aggregate().status()
                    );
                    assertEquals(12, argument.version());

                    return new VersionedAidRequest(
                            argument.aggregate(),
                            13,
                            CREATED_AT
                    );
                });

        ReservationId reservationId = aidRequest.reservationId()
                .orElseThrow();

        when(reservationPort.release(reservationId))
                .thenReturn(ReleaseResult.RELEASED);

        AidRequestApplicationService service =
                new AidRequestApplicationService(
                        repository,
                        reservationPort
                );

        VersionedAidRequest result = service.cancel(
                REQUEST_ID,
                11,
                new CancellationReason("Aid no longer required")
        );

        assertEquals(AidRequestStatus.CANCELLED, result.aggregate().status());

        InOrder order = inOrder(repository, reservationPort);
        order.verify(repository).findById(REQUEST_ID);
        order.verify(repository).update(any());
        order.verify(reservationPort).release(reservationId);
        order.verify(repository).update(any());
    }

    private AidRequest validatedAidRequest() {
        AidRequest aidRequest = new AidRequest(
                REQUEST_ID,
                new Location(32.7767, -96.7970),
                NeedCategory.WATER,
                Priority.URGENT
        );

        aidRequest.markValidated();
        return aidRequest;
    }

    private AidRequest reservedAidRequest() {
        AidRequest aidRequest = validatedAidRequest();

        aidRequest.markMatchingStarted(
                new ReservationId(
                        "00000000-0000-0000-0000-000000000001"
                ),
                INVENTORY_ITEM_ID,
                4
        );
        aidRequest.markReserved();

        return aidRequest;
    }
}
