# Aid Request Persistence Design

## Storage scope

PostgreSQL stores the authoritative durable state owned by `aid-workflow-service`. The database preserves the current state of each aid request independently of any service process, while the `AidRequest` aggregate remains responsible for determining which business changes are legal. 
Persistence records valid outcomes produced by the aggregate; it does not independently perform lifecycle transitions.

The design includes the request’s identity, location, need category, priority, current status, lifecycle-supporting facts, optimistic version, 
and creation timestamp. It must preserve enough information to reconstruct the same valid aggregate state later.

## Table purpose

The `aid_requests` table stores the current snapshot of each `AidRequest`. One row represents one aggregate, identified by `request_id`. Immutable value objects are flattened into scalar columns, while optional reservation, dispatch, delivery, and cancellation facts are represented by nullable column groups.

The table also stores a persistence version for concurrent-update detection and a creation timestamp for deterministic newest-first pagination. It is a current-state table rather than a complete lifecycle-event history.

## Ownership and exclusions

The `aid_requests` table belongs exclusively to `aid-workflow-service`. Other services must interact with Aid Workflow through defined APIs or messaging contracts rather than querying or modifying its database directly. References such as `reservation_id` identify externally owned Logistics data but do not create cross-service foreign keys.

This design does not include Logistics inventory or reservation tables, lifecycle-history tables, Kafka events, outbox or inbox records, a generic idempotency ledger, API DTOs, or serialized Java objects. Those concerns remain outside the Day 3 persistence boundary and will be introduced only when their corresponding use cases require them.


## AidRequest aggregate to Table-column mapping
Required means every stored row must contain the value—not that the API caller must provide it.

| Aggregate field            | Nested value            | Column                            | PostgreSQL type    | Required?   | Reason                                           |
| -------------------------- | ----------------------- | --------------------------------- | ------------------ | ----------- | ------------------------------------------------ |
| `location`                 | `latitude`              | `latitude`                        | `DOUBLE PRECISION` | Always      | Reconstructs the request location                |
| `location`                 | `longitude`             | `longitude`                       | `DOUBLE PRECISION` | Always      | Reconstructs the request location                |
| `needCategory`             | enum name               | `need_category`                   | `TEXT`             | Always      | Identifies the type of aid needed                |
| `priority`                 | enum name               | `priority`                        | `TEXT`             | **Always**  | Reconstructs the request priority                |
| `status`                   | enum name               | `status`                          | `TEXT`             | Always      | Identifies the request’s current lifecycle state |
| `reservationId`            | `id`                    | `reservation_id`                  | `TEXT`             | Conditional | Identifies a successful reservation              |
| `reservationFailureReason` | reason value            | `reservation_failure_reason`      | `TEXT`             | Conditional | Explains why reservation failed                  |
| `dispatchDetails`          | `responderReference`    | `dispatch_responder_reference`    | `TEXT`             | Conditional | Identifies the responder who dispatched the aid  |
| `dispatchDetails`          | `dispatchedAt`          | `dispatched_at`                   | `TIMESTAMPTZ`      | Conditional | Records when aid was dispatched                  |
| `deliveryDetails`          | `confirmationReference` | `delivery_confirmation_reference` | `TEXT`             | Conditional | Stores proof or reference for the delivery       |
| `deliveryDetails`          | `deliveredAt`           | `delivered_at`                    | `TIMESTAMPTZ`      | Conditional | Records when aid was delivered                   |
| `cancellationReason`       | `reason`                | `cancellation_reason`             | `TEXT`             | Conditional | Explains why the request was cancelled           |
| Persistence metadata       | optimistic version      | `version`                         | `BIGINT`           | **Always**  | Detects concurrent updates                       |
| Persistence metadata       | creation time           | `created_at`                      | `TIMESTAMPTZ`      | **Always**  | Supports ordering and deterministic pagination   |


## Define nullability and named constraints
| Constraint name                                 | Rule                                                                                                                                         | Reason                                                         |
| ----------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `pk_aid_requests`                               | `request_id` must be unique and non-null                                                                                                     | Preserves aggregate identity                                   |
| `ck_aid_requests_request_id_not_blank`          | `request_id` cannot be blank                                                                                                                 | Preserves a valid `AidRequestId`                               |
| `ck_aid_requests_latitude`                      | Latitude must be between −90 and 90                                                                                                          | Preserves a valid `Location`                                   |
| `ck_aid_requests_longitude`                     | Longitude must be between −180 and 180                                                                                                       | Preserves a valid `Location`                                   |
| `ck_aid_requests_need_category`                 | Must be `MEDICAL`, `FOOD`, `SHELTER`, or `WATER`                                                                                             | Preserves a valid `NeedCategory`                               |
| `ck_aid_requests_priority`                      | Must be `STANDARD` or `URGENT`                                                                                                               | Preserves a valid `Priority`                                   |
| `ck_aid_requests_status`                        | Must be a recognized `AidRequestStatus` value                                                                                                | Prevents an unknown lifecycle state                            |
| `ck_aid_requests_version`                       | Version must be zero or greater                                                                                                              | Preserves valid optimistic-locking metadata                    |
| `ck_aid_requests_reservation_id_not_blank`      | When present, `reservation_id` cannot be blank                                                                                               | Preserves a valid `ReservationId`                              |
| `ck_aid_requests_failure_reason_not_blank`      | When present, reservation failure reason cannot be blank                                                                                     | Preserves a valid failure reason                               |
| `ck_aid_requests_dispatch_reference_not_blank`  | When present, responder reference cannot be blank                                                                                            | Preserves valid dispatch details                               |
| `ck_aid_requests_delivery_reference_not_blank`  | When present, confirmation reference cannot be blank                                                                                         | Preserves valid delivery details                               |
| `ck_aid_requests_cancellation_reason_not_blank` | When present, cancellation reason cannot be blank                                                                                            | Preserves a valid cancellation reason                          |
| `ck_aid_requests_dispatch_details_complete`     | Dispatch reference and dispatch time must either both be present or both be absent                                                           | Prevents partial `DispatchDetails`                             |
| `ck_aid_requests_delivery_details_complete`     | Delivery confirmation and delivery time must either both be present or both be absent                                                        | Prevents partial `DeliveryDetails`                             |
| `ck_aid_requests_delivery_time`                 | Delivery time cannot be earlier than dispatch time                                                                                           | Preserves valid lifecycle ordering                             |
| `ck_aid_requests_reservation_state`             | Reservation ID is required for `RESERVED`, `DISPATCHED`, `DELIVERED`, and `COMPLETED`; it may also remain after cancellation from `RESERVED` | Ensures successful reservation states have supporting evidence |
| `ck_aid_requests_reservation_failure_state`     | Failure reason is required for `RESERVATION_FAILED`; it may also remain after cancellation from that state                                   | Ensures reservation failure has supporting evidence            |
| `ck_aid_requests_reservation_outcome_exclusive` | Reservation ID and reservation failure reason cannot both be present                                                                         | Prevents contradictory reservation outcomes                    |
| `ck_aid_requests_dispatch_state`                | Dispatch details are present exactly for `DISPATCHED`, `DELIVERED`, and `COMPLETED`                                                          | Ensures dispatched states have supporting evidence             |
| `ck_aid_requests_delivery_state`                | Delivery details are present exactly for `DELIVERED` and `COMPLETED`                                                                         | Ensures delivered states have supporting evidence              |
| `ck_aid_requests_cancellation_state`            | Cancellation reason is present exactly when status is `CANCELLED`                                                                            | Ensures cancellation has supporting evidence                   |


## Define Versioning

Optimistic versioning
Every new row begins with: version = 0

UPDATE aid_requests
SET version = version + 1
WHERE request_id = :requestId
AND version = :expectedVersion;

## Pagination
Deterministic pagination

List requests using:

ORDER BY created_at DESC, request_id DESC

created_at provides chronological ordering. request_id breaks ties when multiple requests have the same creation time.

The cursor contains both values:

(created_at, request_id)

The next page uses:

WHERE (created_at, request_id)
< (:cursorCreatedAt, :cursorRequestId)
ORDER BY created_at DESC, request_id DESC
LIMIT :pageSize

This avoids unstable and increasingly expensive offset pagination.

## Initial indexes
Index	                            Columns	                           Purpose
pk_aid_requests	                    request_id	                       Direct lookup and aggregate identity
idx_aid_requests_created_request	created_at DESC, request_id DESC   Supports deterministic newest-first pagination

