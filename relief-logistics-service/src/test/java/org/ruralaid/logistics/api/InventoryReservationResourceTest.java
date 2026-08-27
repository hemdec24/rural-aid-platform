package org.ruralaid.logistics.api;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.ruralaid.logistics.api.exception.InventoryUnavailableExceptionMapper;
import org.ruralaid.logistics.api.exception.ReservationIdConflictExceptionMapper;
import org.ruralaid.logistics.api.model.ApiErrorResponse;
import org.ruralaid.logistics.api.model.ReserveInventoryRequest;
import org.ruralaid.logistics.api.model.ReserveInventoryResponse;
import org.ruralaid.logistics.application.InventoryReservationService;
import org.ruralaid.logistics.application.exception.InventoryUnavailableException;
import org.ruralaid.logistics.application.exception.ReservationIdConflictException;
import org.ruralaid.logistics.persistence.JdbiInventoryReservationRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class InventoryReservationResourceTest {

    private static Jdbi jdbi;
    private static InventoryReservationResource resource;

    @BeforeAll
    static void setUpDatabaseAccess() {
        jdbi = createJdbi();

        JdbiInventoryReservationRepository repository = new JdbiInventoryReservationRepository(jdbi);

        InventoryReservationService reservationService = new InventoryReservationService(repository);

        resource = new InventoryReservationResource(reservationService);
    }

    /**
     * Replays Reservation response without creating a new reservation.
     */
    @Test
    void returnsCreatedWithoutApplyingExactRetryTwice() {
        UUID inventoryItemId = UUID.randomUUID();
        String aidRequestId = UUID.randomUUID().toString();
        UUID reservationId = UUID.randomUUID();

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        reservationId,
                        aidRequestId,
                        inventoryItemId,
                        4
                );

        insertInventory(inventoryItemId, 10);

        try {
            Response firstResponse = resource.createReservation(request);

            Response retryResponse = resource.createReservation(request);

            assertAll(
                    () -> assertEquals(
                            Response.Status.CREATED.getStatusCode(),
                            firstResponse.getStatus()
                    ),
                    () -> assertEquals(
                            new ReserveInventoryResponse(
                                    reservationId,
                                    "RESERVED"
                            ),
                            firstResponse.getEntity()
                    ),
                    () -> assertEquals(
                            Response.Status.CREATED.getStatusCode(),
                            retryResponse.getStatus()
                    ),
                    () -> assertEquals(
                            new ReserveInventoryResponse(
                                    reservationId,
                                    "RESERVED"
                            ),
                            retryResponse.getEntity()
                    ),
                    () -> assertEquals(
                            6,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "RESERVED",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    @Test
    void returnsSafeConflictWhenInventoryIsUnavailable() {
        UUID inventoryItemId = UUID.randomUUID();
        String aidRequestId = UUID.randomUUID().toString();
        UUID reservationId = UUID.randomUUID();

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        reservationId,
                        aidRequestId,
                        inventoryItemId,
                        4
                );

        insertInventory(inventoryItemId, 3);

        try {
            InventoryUnavailableException exception =
                    assertThrows(
                            InventoryUnavailableException.class,
                            () -> resource.createReservation(request)
                    );

            Response response =
                    new InventoryUnavailableExceptionMapper()
                            .toResponse(exception);

            assertAll(
                    () -> assertEquals(
                            Response.Status.CONFLICT.getStatusCode(),
                            response.getStatus()
                    ),
                    () -> assertEquals(
                            MediaType.APPLICATION_JSON_TYPE,
                            response.getMediaType()
                    ),
                    () -> assertEquals(
                            new ApiErrorResponse(
                                    "INVENTORY_UNAVAILABLE",
                                    "Requested inventory is unavailable"
                            ),
                            response.getEntity()
                    ),
                    () -> assertEquals(
                            3,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            0,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "UNAVAILABLE",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    @Test
    void returnsSafeConflictWhenReservationIdIsReused() {
        UUID inventoryItemId = UUID.randomUUID();
        String aidRequestId = UUID.randomUUID().toString();
        UUID reservationId = UUID.randomUUID();

        ReserveInventoryRequest firstRequest =
                new ReserveInventoryRequest(
                        reservationId,
                        aidRequestId,
                        inventoryItemId,
                        4
                );

        ReserveInventoryRequest changedRequest =
                new ReserveInventoryRequest(
                        reservationId,
                        aidRequestId,
                        inventoryItemId,
                        3 // Changing quantity which is a property used to generate fingerprint
                );

        insertInventory(inventoryItemId, 10);

        try {
            Response firstResponse =
                    resource.createReservation(firstRequest);

            ReservationIdConflictException exception =
                    assertThrows(
                            ReservationIdConflictException.class,
                            () -> resource.createReservation(
                                    changedRequest
                            )
                    );

            Response conflictResponse =
                    new ReservationIdConflictExceptionMapper()
                            .toResponse(exception);

            assertAll(
                    () -> assertEquals(
                            Response.Status.CREATED.getStatusCode(),
                            firstResponse.getStatus()
                    ),
                    () -> assertEquals(
                            Response.Status.CONFLICT.getStatusCode(),
                            conflictResponse.getStatus()
                    ),
                    () -> assertEquals(
                            MediaType.APPLICATION_JSON_TYPE,
                            conflictResponse.getMediaType()
                    ),
                    () -> assertEquals(
                            new ApiErrorResponse(
                                    "RESERVATION_ID_CONFLICT",
                                    "Reservation ID is already associated "
                                            + "with a different request"
                            ),
                            conflictResponse.getEntity()
                    ),
                    () -> assertEquals(
                            6,
                            availableQuantity(inventoryItemId)
                    ),
                    () -> assertEquals(
                            1,
                            reservationCount(reservationId)
                    ),
                    () -> assertEquals(
                            "COMPLETED",
                            operationStatus(reservationId)
                    ),
                    () -> assertEquals(
                            "RESERVED",
                            operationOutcome(reservationId)
                    )
            );
        } finally {
            deleteFixture(inventoryItemId, reservationId);
        }
    }

    @Test
    void rejectsStructurallyInvalidRequest() {
        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        null,
                        null,
                        null,
                        0
                );

        try (
                ValidatorFactory validatorFactory =
                        Validation.buildDefaultValidatorFactory()
        ) {
            Set<String> validationMessages =
                    validatorFactory.getValidator()
                            .validate(request)
                            .stream()
                            .map(violation ->
                                    violation.getMessage()
                            )
                            .collect(Collectors.toSet());

            assertEquals(
                    Set.of(
                            "reservationId is required",
                            "aidRequestId must not be blank",
                            "inventoryItemId is required",
                            "quantity must be greater than zero"
                    ),
                    validationMessages
            );
        }
    }

    private static Jdbi createJdbi() {
        String url = System.getenv(
                "RELIEF_LOGISTICS_IT_DB_URL"
        );
        String user = System.getenv(
                "RELIEF_LOGISTICS_IT_DB_USER"
        );
        String password = System.getenv(
                "RELIEF_LOGISTICS_IT_DB_PASSWORD"
        );

        assumeTrue(
                isPresent(url)
                        && isPresent(user)
                        && isPresent(password),
                "Relief Logistics PostgreSQL integration-test "
                        + "variables are required"
        );

        return Jdbi.create(url, user, password);
    }

    private static void insertInventory(
            UUID inventoryItemId,
            int availableQuantity
    ) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO inventory_items (
                                    inventory_item_id,
                                    resource_type,
                                    available_quantity
                                ) VALUES (
                                    :inventoryItemId,
                                    :resourceType,
                                    :availableQuantity
                                )
                                """)
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .bind("resourceType", "WATER_KIT")
                        .bind(
                                "availableQuantity",
                                availableQuantity
                        )
                        .execute()
        );
    }

    private static int availableQuantity(
            UUID inventoryItemId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT available_quantity
                                FROM inventory_items
                                WHERE inventory_item_id =
                                    :inventoryItemId
                                """)
                        .bind(
                                "inventoryItemId",
                                inventoryItemId
                        )
                        .mapTo(int.class)
                        .one()
        );
    }

    private static int reservationCount(
            UUID reservationId
    ) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*)
                                FROM inventory_reservations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(int.class)
                        .one()
        );
    }

    private static String operationStatus(UUID reservationId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT operation_status
                                FROM inventory_reservation_operations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(String.class)
                        .one()
        );
    }

    private static String operationOutcome(UUID reservationId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT outcome
                                FROM inventory_reservation_operations
                                WHERE reservation_id =
                                    :reservationId
                                """)
                        .bind("reservationId", reservationId)
                        .mapTo(String.class)
                        .one()
        );
    }

    private static void deleteFixture(
            UUID inventoryItemId,
            UUID reservationId
    ) {
        jdbi.useTransaction(handle -> {
            handle.createUpdate("""
                        DELETE FROM inventory_reservation_operations
                        WHERE reservation_id = :reservationId
                        """)
                    .bind("reservationId", reservationId)
                    .execute();

            handle.createUpdate("""
                        DELETE FROM inventory_reservations
                        WHERE inventory_item_id = :inventoryItemId
                        """)
                    .bind("inventoryItemId", inventoryItemId)
                    .execute();

            handle.createUpdate("""
                        DELETE FROM inventory_items
                        WHERE inventory_item_id = :inventoryItemId
                        """)
                    .bind("inventoryItemId", inventoryItemId)
                    .execute();
        });
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}