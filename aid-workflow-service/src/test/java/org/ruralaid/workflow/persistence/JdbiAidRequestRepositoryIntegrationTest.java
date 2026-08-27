package org.ruralaid.workflow.persistence;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.ruralaid.workflow.application.exception.AidRequestVersionConflictException;
import org.ruralaid.workflow.application.model.AidRequestCursor;
import org.ruralaid.workflow.application.model.VersionedAidRequest;
import org.ruralaid.workflow.domain.AidRequest;
import org.ruralaid.workflow.domain.AidRequestId;
import org.ruralaid.workflow.domain.AidRequestStatus;
import org.ruralaid.workflow.domain.InventoryItemId;
import org.ruralaid.workflow.domain.Location;
import org.ruralaid.workflow.domain.NeedCategory;
import org.ruralaid.workflow.domain.Priority;
import org.ruralaid.workflow.domain.ReservationFailureReason;
import org.ruralaid.workflow.domain.ReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class JdbiAidRequestRepositoryIntegrationTest {

    private static Jdbi jdbi;
    private static JdbiAidRequestRepository repository;

    @BeforeAll
    static void setUpDatabaseAccess() {
        jdbi = createJdbi();
        repository = new JdbiAidRequestRepository(jdbi);
    }

    private static Jdbi createJdbi() {
        String url = System.getenv(
                "AID_WORKFLOW_IT_DB_URL"
        );
        String user = System.getenv(
                "AID_WORKFLOW_IT_DB_USER"
        );
        String password = System.getenv(
                "AID_WORKFLOW_IT_DB_PASSWORD"
        );

        assumeTrue(
                isPresent(url)
                        && isPresent(user)
                        && isPresent(password),
                "PostgreSQL integration-test variables are required"
        );

        return Jdbi.create(url, user, password);
    }

    @Test
    void roundTripsReceivedRequestThroughPostgresql() {
        AidRequestId requestId = new AidRequestId(
                "integration-" + UUID.randomUUID()
        );

        AidRequest original = new AidRequest(
                requestId,
                new Location(32.7767, -96.7970),
                NeedCategory.FOOD,
                Priority.URGENT
        );

        try {
            VersionedAidRequest inserted =
                    repository.insert(original);

            VersionedAidRequest loaded =
                    repository.findById(requestId)
                            .orElseThrow();

            assertAll(
                    () -> assertEquals(
                            requestId,
                            loaded.aggregate().id()
                    ),
                    () -> assertEquals(
                            original.location(),
                            loaded.aggregate().location()
                    ),
                    () -> assertEquals(
                            original.needCategory(),
                            loaded.aggregate().needCategory()
                    ),
                    () -> assertEquals(
                            original.priority(),
                            loaded.aggregate().priority()
                    ),
                    () -> assertEquals(
                            AidRequestStatus.RECEIVED,
                            loaded.aggregate().status()
                    ),
                    () -> assertTrue(
                            loaded.aggregate().reservationId().isEmpty()
                    ),
                    () -> assertTrue(
                            loaded.aggregate()
                                    .reservationInventoryItemId()
                                    .isEmpty()
                    ),
                    () -> assertTrue(
                            loaded.aggregate()
                                    .reservationQuantity()
                                    .isEmpty()
                    ),
                    () -> assertTrue(
                            loaded.aggregate()
                                    .reservationFailureReason()
                                    .isEmpty()
                    ),
                    () -> assertTrue(
                            loaded.aggregate()
                                    .dispatchDetails()
                                    .isEmpty()
                    ),
                    () -> assertTrue(
                            loaded.aggregate()
                                    .deliveryDetails()
                                    .isEmpty()
                    ),
                    () -> assertTrue(
                            loaded.aggregate()
                                    .cancellationReason()
                                    .isEmpty()
                    ),
                    () -> assertEquals(
                            0L,
                            inserted.version()
                    ),
                    () -> assertNotNull(
                            inserted.createdAt()
                    ),
                    () -> assertEquals(
                            inserted.version(),
                            loaded.version()
                    ),
                    () -> assertEquals(
                            inserted.createdAt(),
                            loaded.createdAt()
                    )
            );
        } finally {
            deleteRequest(requestId);
        }
    }

    @Test
    void roundTripsReservationAttemptAndRetainsFactsOnFailure() {
        AidRequestId requestId = new AidRequestId(
                "reservation-" + UUID.randomUUID()
        );

        ReservationId reservationId =
                ReservationId.generate();

        InventoryItemId inventoryItemId =
                new InventoryItemId(UUID.randomUUID());

        ReservationFailureReason failureReason =
                new ReservationFailureReason(
                        "Requested inventory is unavailable"
                );

        AidRequest original = new AidRequest(
                requestId,
                new Location(32.7767, -96.7970),
                NeedCategory.WATER,
                Priority.URGENT
        );

        original.markValidated();
        original.markMatchingStarted(
                reservationId,
                inventoryItemId,
                4
        );

        try {
            VersionedAidRequest inserted =
                    repository.insert(original);

            inserted.aggregate()
                    .markReservationFailed(failureReason);

            VersionedAidRequest updated =
                    repository.update(inserted);

            VersionedAidRequest loaded =
                    repository.findById(requestId)
                            .orElseThrow();

            assertAll(
                    () -> assertEquals(
                            AidRequestStatus.RESERVATION_FAILED,
                            loaded.aggregate().status()
                    ),
                    () -> assertEquals(
                            Optional.of(reservationId),
                            loaded.aggregate().reservationId()
                    ),
                    () -> assertEquals(
                            Optional.of(inventoryItemId),
                            loaded.aggregate()
                                    .reservationInventoryItemId()
                    ),
                    () -> assertEquals(
                            Optional.of(4),
                            loaded.aggregate().reservationQuantity()
                    ),
                    () -> assertEquals(
                            Optional.of(failureReason),
                            loaded.aggregate()
                                    .reservationFailureReason()
                    ),
                    () -> assertEquals(
                            1L,
                            updated.version()
                    ),
                    () -> assertEquals(
                            updated.version(),
                            loaded.version()
                    )
            );
        } finally {
            deleteRequest(requestId);
        }
    }

    @Test
    void rejectsStaleUpdateWithoutOverwritingWinner() {
        AidRequestId requestId = new AidRequestId(
                "concurrency-" + UUID.randomUUID()
        );

        AidRequest original = new AidRequest(
                requestId,
                new Location(32.7767, -96.7970),
                NeedCategory.FOOD,
                Priority.STANDARD
        );

        try {
            repository.insert(original);

            VersionedAidRequest writerA =
                    repository.findById(requestId)
                            .orElseThrow();

            VersionedAidRequest writerB =
                    repository.findById(requestId)
                            .orElseThrow();

            Location winningLocation =
                    new Location(32.7800, -96.8000);

            Location staleLocation =
                    new Location(32.7900, -96.8100);

            writerA.aggregate().correctLocation(
                    winningLocation
            );

            VersionedAidRequest winner =
                    repository.update(writerA);

            writerB.aggregate().correctLocation(
                    staleLocation
            );

            AidRequestVersionConflictException conflict =
                    assertThrows(
                            AidRequestVersionConflictException.class,
                            () -> repository.update(writerB)
                    );

            VersionedAidRequest authoritative =
                    repository.findById(requestId)
                            .orElseThrow();

            assertAll(
                    () -> assertEquals(
                            1L,
                            winner.version()
                    ),
                    () -> assertEquals(
                            winningLocation,
                            winner.aggregate().location()
                    ),
                    () -> assertEquals(
                            requestId,
                            conflict.requestId()
                    ),
                    () -> assertEquals(
                            0L,
                            conflict.expectedVersion()
                    ),
                    () -> assertEquals(
                            1L,
                            authoritative.version()
                    ),
                    () -> assertEquals(
                            winningLocation,
                            authoritative.aggregate().location()
                    )
            );
        } finally {
            deleteRequest(requestId);
        }
    }

    @Test
    void listsRequestsDeterministicallyAcrossCursor() {
        String prefix = "cursor-" + UUID.randomUUID();

        AidRequestId requestA =
                new AidRequestId(prefix + "-a");

        AidRequestId requestB =
                new AidRequestId(prefix + "-b");

        AidRequestId requestC =
                new AidRequestId(prefix + "-c");

        List<AidRequestId> requestIds = List.of(
                requestA,
                requestB,
                requestC
        );

        Instant sharedCreationTime =
                Instant.parse("9999-01-01T00:00:00Z");

        try {
            for (AidRequestId requestId : requestIds) {
                repository.insert(
                        new AidRequest(
                                requestId,
                                new Location(32.7767, -96.7970),
                                NeedCategory.FOOD,
                                Priority.STANDARD
                        )
                );
            }

            jdbi.useHandle(handle -> {
                for (AidRequestId requestId : requestIds) {
                    handle.createUpdate("""
                                UPDATE aid_requests
                                SET created_at = :createdAt
                                WHERE request_id = :requestId
                                """)
                            .bind(
                                    "createdAt",
                                    sharedCreationTime
                            )
                            .bind(
                                    "requestId",
                                    requestId.id()
                            )
                            .execute();
                }
            });

            List<VersionedAidRequest> firstPage =
                    repository.list(
                            2,
                            Optional.empty()
                    );

            List<AidRequestId> firstPageIds =
                    firstPage.stream()
                            .map(result ->
                                    result.aggregate().id()
                            )
                            .toList();

            assertEquals(
                    List.of(requestC, requestB),
                    firstPageIds
            );

            VersionedAidRequest lastFirstPageItem =
                    firstPage.get(1);

            AidRequestCursor cursor = new AidRequestCursor(
                    lastFirstPageItem.createdAt(),
                    lastFirstPageItem.aggregate().id()
            );

            List<VersionedAidRequest> secondPage =
                    repository.list(
                            2,
                            Optional.of(cursor)
                    );

            assertFalse(secondPage.isEmpty());

            assertAll(
                    () -> assertEquals(
                            requestA,
                            secondPage.get(0).aggregate().id()
                    ),
                    () -> assertTrue(
                            secondPage.stream().noneMatch(
                                    result -> firstPageIds.contains(
                                            result.aggregate().id()
                                    )
                            )
                    )
            );
        } finally {
            for (AidRequestId requestId : requestIds) {
                deleteRequest(requestId);
            }
        }
    }

    private static void deleteRequest(
            AidRequestId requestId
    ) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                            DELETE FROM aid_requests
                            WHERE request_id = :requestId
                            """)
                        .bind("requestId", requestId.id())
                        .execute()
        );
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}