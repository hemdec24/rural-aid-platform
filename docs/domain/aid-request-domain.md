# Aid Request domain model and responsibility definitions

## 1. AidRequest aggregate

An `AidRequest` represents one request for relief assistance. It is the aggregate root that owns the request's local lifecycle and protects its invariants.

The aggregate records facts produced by the workflow and by external services. It does not itself perform persistence, messaging, resource allocation, dispatch, network communication, or distributed coordination.

## 2. Use case: Receive Aid Request

### Business intention

Register one new request for relief assistance so that it can enter the aid-coordination workflow.

### Required input

- A valid `AidRequestId`
- A valid `Location`
- One supported `NeedCategory`
- One `Priority`

Duplicate detection requires persistence and is outside the Day 2 aggregate. For now, it is an application-level precondition.

### Success condition

A valid `AidRequest` is created with:

- The supplied identity and descriptive values
- Status `RECEIVED`
- No reservation reference or reservation-failure reason
- No dispatch details
- No delivery details
- No cancellation reason

### Failure condition

If a required value is missing or invalid:

- Creation is rejected with a clear domain error.
- No partially valid `AidRequest` is returned.
- No lifecycle state exists to recover or modify.

## 3. Ubiquitous language

### Aid Request

One request for relief assistance. `AidRequest` is the aggregate root that owns the request's local lifecycle and protects its invariants.

### Request identity

`AidRequestId` is a unique, immutable identifier assigned to one aid request. For the Day 2 model, it wraps a non-blank identifier value and remains unchanged throughout the request's lifecycle.

### Request location
`Location` is the place where assistance is required. For the Day 2 model, it is an immutable value object containing a (double latitude, double longitude).

The aggregate may replace its `Location` with another complete, valid `Location` through `correctLocation(newLocation)`. A location may be corrected only while the request is `RECEIVED` or `REQUIRES_REVIEW`.

### Need category

`NeedCategory` identifies the type of assistance requested. Each request has exactly one of the following MVP values:

- `MEDICAL`
- `FOOD`
- `SHELTER`
- `WATER`

### Priority

`Priority` may influence processing order and resource matching, but it does not bypass lifecycle rules. The MVP values are:

- `STANDARD`
- `URGENT`

### Current status

`AidRequestStatus` is the request's current lifecycle stage. Exactly one non-null status exists at any time, and it changes only through an allowed domain operation.

### Validation

Validation determines whether a received request contains sufficient and trustworthy information to proceed.

- `RECEIVED -> VALIDATED`
- `RECEIVED -> REQUIRES_REVIEW`
- `REQUIRES_REVIEW -> VALIDATED`

### Review requirement

`REQUIRES_REVIEW` means the request cannot proceed automatically and requires a human decision. Approval moves it to `VALIDATED`.

### Match pending

`MATCH_PENDING` means the request is awaiting a reservation outcome from Relief Logistics.

- `VALIDATED -> MATCH_PENDING`

### Reservation

`ReservationId` is an immutable, non-blank external reference to a reservation owned by Relief Logistics.

A reservation attempt has one of two recorded outcomes:

- Success: `MATCH_PENDING -> RESERVED`
- Failure: `MATCH_PENDING -> RESERVATION_FAILED`

`ReservationFailureReason` is an immutable value object containing a non-blank explanation of why a reservation could not be made. A failed request may later be retried.

### Dispatch

Dispatch means Relief Logistics has confirmed that a responder carrying the reserved aid is en route.

- `RESERVED -> DISPATCHED`

`DispatchDetails` is an immutable value object that records the confirmed dispatch fact. For the MVP, it contains:

| Field | Required content |
| --- | --- |
| `responderReference` | A non-blank identifier or reference for the dispatched responder |
| `dispatchedAt` | A non-null `Instant` recording when dispatch occurred |

Both fields are required. The value object is invalid if either field is absent or if the responder reference is blank.

### Delivery

Delivery means confirmation has been received that the aid reached its destination.

- `DISPATCHED -> DELIVERED`

`DeliveryDetails` is an immutable value object that records the delivery evidence. For the MVP, it contains:

| Field | Required content |
| --- | --- |
| `confirmationReference` | A non-blank identifier or reference for the delivery confirmation |
| `deliveredAt` | A non-null `Instant` recording when delivery occurred |

Both fields are required. The value object is invalid if either field is absent or if the confirmation reference is blank. At the aggregate level, `deliveredAt` must not be earlier than `DispatchDetails.dispatchedAt`.

### Completion

Completion means delivery has been recorded and the workflow has been formally closed.

- `DELIVERED -> COMPLETED`

`markCompleted()` records that closure. It does not itself send a message or perform another external action.

### Cancellation

Cancellation records that processing stopped before dispatch. `CancellationReason` is an immutable value object containing a non-blank explanation of why the request was cancelled.

`CANCELLED` is terminal. Cancelling a `RESERVED` request records the cancellation locally, but the application workflow must separately coordinate release of the external reservation.

## 4. Classification table

| Concept | Classification | Mutable? | Reason |
| --- | --- | ---: | --- |
| `AidRequest` | Entity and aggregate root | Controlled mutation | Has stable identity and owns the request lifecycle |
| `AidRequestId` | Value object | No | Typed, immutable, non-blank request identifier |
| `Location` | Value object | No | A complete location value is replaced rather than mutated |
| `NeedCategory` | Enum | No | Closed MVP vocabulary for the requested aid |
| `Priority` | Enum | No | Closed MVP vocabulary for processing priority |
| `AidRequestStatus` | Enum | No | Closed set of lifecycle states |
| `ReservationId` | Value object and external reference | No | Identifies a reservation owned by Relief Logistics |
| `ReservationFailureReason` | Value object | No | Records a non-blank explanation of a failed reservation |
| `DispatchDetails` | Value object | No | Records the responder reference and dispatch time as one fact |
| `DeliveryDetails` | Value object | No | Records the confirmation reference and delivery time as one fact |
| `CancellationReason` | Value object | No | Records a non-blank explanation of cancellation |

## 5. Invariant list

Each invariant below is an independently testable domain rule.

1. An `AidRequest` can be created only with a valid, non-null `AidRequestId`, `Location`, `NeedCategory`, and `Priority`.
2. Every successfully created `AidRequest` begins in `RECEIVED`.
3. An `AidRequest` always has exactly one non-null status.
4. Status may change only through an operation allowed by the transition matrix.
5. A rejected operation leaves the status and every supporting field unchanged.
6. `RESERVATION_FAILED` requires a valid `ReservationFailureReason` and has no `ReservationId`.
7. Retrying matching clears the previous reservation-failure reason.
8. `RESERVED`, `DISPATCHED`, `DELIVERED`, and `COMPLETED` require a valid `ReservationId`.
9. `DISPATCHED`, `DELIVERED`, and `COMPLETED` require valid `DispatchDetails`.
10. `DELIVERED` and `COMPLETED` require valid `DeliveryDetails`.
11. Delivery time cannot be earlier than dispatch time.
12. `CANCELLED` requires a valid `CancellationReason`.
13. Cancellation is permitted only before dispatch.
14. `COMPLETED` and `CANCELLED` are terminal and permit no further lifecycle transition.
15. `Location` is replaced as one complete immutable value and may be corrected only while the request is `RECEIVED` or `REQUIRES_REVIEW`.
16. Repeating an already-recorded lifecycle operation is rejected by the aggregate; command or message idempotency belongs at the application boundary.

## 6. Transition matrix

| Current state | Requested operation | Next state | Allowed? | Reason |
| --- | --- | --- | :---: | --- |
| `RECEIVED` | `markValidated()` | `VALIDATED` | Yes | The request contains sufficient valid information to proceed. |
| `RECEIVED` | `markReviewRequired()` | `REQUIRES_REVIEW` | Yes | The request requires human review before validation. |
| `RECEIVED` | `markCancelled(reason)` | `CANCELLED` | Yes | An active request may be cancelled before dispatch. |
| `RECEIVED` | Any other lifecycle operation | Unchanged | No | Validation or review must occur before further processing. |
| `REQUIRES_REVIEW` | `markReviewApproved()` | `VALIDATED` | Yes | Human review has approved the request. |
| `REQUIRES_REVIEW` | `markCancelled(reason)` | `CANCELLED` | Yes | A request under review may be cancelled. |
| `REQUIRES_REVIEW` | Any other lifecycle operation | Unchanged | No | Review must be approved before matching can begin. |
| `VALIDATED` | `markMatchingStarted()` | `MATCH_PENDING` | Yes | A validated request is eligible for resource matching. |
| `VALIDATED` | `markCancelled(reason)` | `CANCELLED` | Yes | The request has not yet been dispatched. |
| `VALIDATED` | Any other lifecycle operation | Unchanged | No | Matching must begin before a reservation outcome can be recorded. |
| `MATCH_PENDING` | `markReserved(reservationId)` | `RESERVED` | Yes | Relief Logistics successfully created a reservation. |
| `MATCH_PENDING` | `markReservationFailed(reason)` | `RESERVATION_FAILED` | Yes | Relief Logistics could not reserve suitable resources. |
| `MATCH_PENDING` | `markCancelled(reason)` | `CANCELLED` | Yes | The request may be cancelled while matching is underway. |
| `MATCH_PENDING` | Any other lifecycle operation | Unchanged | No | A reservation outcome must be recorded before dispatch. |
| `RESERVATION_FAILED` | `markMatchingRetried()` | `MATCH_PENDING` | Yes | The workflow has authorized another matching attempt. |
| `RESERVATION_FAILED` | `markCancelled(reason)` | `CANCELLED` | Yes | The failed request may be closed instead of retried. |
| `RESERVATION_FAILED` | Any other lifecycle operation | Unchanged | No | The request must either retry matching or be cancelled. |
| `RESERVED` | `markDispatched(dispatchDetails)` | `DISPATCHED` | Yes | A reservation exists and external dispatch has been confirmed. |
| `RESERVED` | `markCancelled(reason)` | `CANCELLED` | Yes | The request has not been dispatched, but its external reservation must be released. |
| `RESERVED` | Any other lifecycle operation | Unchanged | No | Delivery or completion cannot occur before dispatch. |
| `DISPATCHED` | `markDelivered(deliveryDetails)` | `DELIVERED` | Yes | Valid delivery confirmation has been received. |
| `DISPATCHED` | `markCancelled(reason)` | Unchanged | No | The responder and aid are already en route; cancellation is no longer the correct business operation. |
| `DISPATCHED` | Any other lifecycle operation | Unchanged | No | Delivery must be confirmed before completion. |
| `DELIVERED` | `markCompleted()` | `COMPLETED` | Yes | Delivery has been confirmed and the workflow can be closed. |
| `DELIVERED` | Any other lifecycle operation | Unchanged | No | A delivered request may only proceed to completion. |
| `COMPLETED` | Any lifecycle operation | Unchanged | No | `COMPLETED` is terminal. |
| `CANCELLED` | Any lifecycle operation | Unchanged | No | `CANCELLED` is terminal. |

### Non-lifecycle operation

| Current state | Requested operation | Status after operation | Allowed? | Reason |
| --- | --- | --- | :---: | --- |
| `RECEIVED` | `correctLocation(newLocation)` | `RECEIVED` | Yes | Intake information may still be corrected. |
| `REQUIRES_REVIEW` | `correctLocation(newLocation)` | `REQUIRES_REVIEW` | Yes | Review may identify a location correction. |
| Any other state | `correctLocation(newLocation)` | Unchanged | No | Location is fixed once the request has been validated. |

## 7. State-supporting data rules

These rules make the transition methods unambiguous for implementation:

- `markReservationFailed(reason)` stores the valid failure reason and leaves the reservation reference absent.
- `markMatchingRetried()` clears the previous failure reason before returning to `MATCH_PENDING`.
- `markReserved(reservationId)` stores the valid reservation reference and leaves the failure reason absent.
- `markDispatched(dispatchDetails)` stores valid dispatch details.
- `markDelivered(deliveryDetails)` stores valid delivery details after checking their time against the dispatch time.
- `markCancelled(reason)` stores the valid cancellation reason. Existing historical reservation information, if present, is retained for audit purposes.
- A transition method validates all preconditions before mutating any field.

## 8. Rejected alternatives and boundary decisions

### Combining Aid Workflow and Relief Logistics

The project retains separate Aid Workflow and Relief Logistics services because they own different authoritative data and consistency boundaries:

- Aid Workflow owns workflow and `AidRequest` lifecycle.
- Relief Logistics owns inventory, reservation, assignment, and dispatch operations.
- Their workloads, scaling needs, and failure modes differ.

This separation limits ownership ambiguity and blast radius, but it does not by itself prevent lifecycle bugs or distributed failures. The `AidRequest` aggregate protects local transitions. Later application and messaging mechanisms will address retries, idempotency, reconciliation, and recovery across services.

### Generic status mutation

A public `setStatus(...)` or unrestricted `transitionTo(...)` operation was rejected. Named, fact-recording operations such as `markReserved(...)` and `markDelivered(...)` express business intention and give the aggregate one place to enforce each transition's rules.

### Mutable location

A mutable `Location` object was rejected. The aggregate replaces one complete immutable location value through a controlled correction operation, preventing partially updated location data.