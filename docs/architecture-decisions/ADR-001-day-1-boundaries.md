# ADR-001: Day 1 service and ownership boundaries

* **Status:** Accepted
* **Date:** 2026-08-10

## Context

The Rural Aid Platform will receive aid requests, process voice recordings and coordinate the dispatch of relief resources.

These responsibilities have different data, scaling and failure characteristics:

* Aid-request state changes must remain consistent and traceable.
* Resource availability and dispatch decisions belong to a separate operational area.
* Voice processing is slower and more compute-intensive than ordinary API handling.
* The web application is one possible client and must not become the owner of business rules.
* Services will eventually communicate asynchronously through durable messages.

Day 1 establishes the service boundaries and runnable application skeletons. It does not implement business entities, database schemas, Kafka consumers or production voice-processing workflows.

## Decision

The platform will begin with the following bounded services:

### Aid Workflow Service

`aid-workflow-service` will own the lifecycle of an aid request, including its state, invariants and allowed state transitions.

It will eventually own the `aid_workflow` database. Other services must not directly read from or write to its tables.

### Relief Logistics Service

`relief-logistics-service` will own relief resources, availability, assignment and dispatch-related behavior.

It will eventually own the `relief_logistics` database. Other services must interact with it through defined APIs or messaging contracts rather than accessing its tables.

### Voice Service

`voice-service` will provide the boundary for receiving voice-processing requests.

The future Voice API will validate and accept a request, create a durable job and return a job identifier. A separately deployed worker will asynchronously perform language identification, transcription and related processing.

The durable queue and worker are intentionally not implemented on Day 1.

### Coordinator Web

`coordinator-web` will be a thin client responsible for presentation, collecting user input and calling backend APIs.

Backend services will own:

* Input validation
* Authorization
* API contracts and versions
* Business invariants
* State transitions
* Idempotency

No frontend implementation is required on Day 1.

### Messaging contracts

`messaging-contracts` will contain shared Java representations of messages exchanged between services. It must not contain service-specific business logic.

AsyncAPI will serve as the language-neutral contract for asynchronous service interactions, with message payloads defined using JSON Schema. Java and Python representations will be derived from or validated against these contracts.

### Day 1 implementation boundary

Day 1 will provide only:

* Runnable Dropwizard service skeletons
* A runnable FastAPI service skeleton
* Health endpoints
* Docker images and Docker Compose orchestration
* PostgreSQL and Kafka infrastructure
* A pretrained language-identification feasibility spike
* Documentation of service ownership and future boundaries

Business APIs, domain objects, database migrations, Kafka producers and consumers, durable job processing and frontend behavior will be implemented incrementally on later days.

## Why this decision

Separating Aid Workflow from Relief Logistics prevents aid-request lifecycle rules from becoming coupled to resource inventory and dispatch behavior.

Giving each service ownership of its data allows it to enforce its own invariants. It also prevents one service from silently depending on another service’s internal database structure.

Separating the Voice API from its worker prevents slow model execution from holding HTTP requests open. The API and worker can later scale and recover independently.

Keeping the web application thin ensures that the same business rules apply to every client. A caller cannot bypass an invariant simply by avoiding the web interface.

Deferring business implementation on Day 1 keeps the first milestone focused on proving that the development environment, builds, containers, service lifecycle and health checks work correctly.

## Alternatives considered

### One application containing all responsibilities

A single application would be simpler to deploy initially. It was rejected because Aid Workflow, Relief Logistics and voice processing represent distinct ownership and scaling boundaries.

This does not imply that every future capability must become a separate microservice. New boundaries will be introduced only when ownership, reliability or scaling requirements justify them.

### One shared database

A shared database would make early joins and cross-service access easier. It was rejected because it would allow services to bypass one another’s APIs and invariants, creating tight coupling between their schemas.

Separate PostgreSQL databases are created from Day 1 even though business tables are not added yet.

### Synchronous voice processing inside the HTTP request

This would require fewer components initially. It was rejected as the target architecture because model execution may be slow, variable or temporarily unavailable. Long-running synchronous requests are more vulnerable to timeouts and client retries.

The Day 1 model spike remains synchronous because it is only a local feasibility experiment, not the production processing design.

### Business rules in `coordinator-web`

Client-side validation can improve user experience, but it cannot be authoritative. Clients may be outdated, modified or bypassed entirely.

The frontend may repeat simple validation for usability, but backend services remain the source of truth.

### Implementing business entities and Kafka consumers on Day 1

This would demonstrate more visible functionality, but it would mix infrastructure troubleshooting with domain-design decisions. It was rejected so that later business implementation can begin on a stable, verified foundation.

## Consequences

### Positive consequences

* Each business capability has a clear owner.
* Domain invariants can be enforced within the service that owns the data.
* Services cannot depend on another service’s private tables.
* API and worker workloads can scale independently.
* Voice-processing failures do not need to keep an HTTP request open.
* Additional clients can use the backend without duplicating business logic.
* Day 1 produces a small but verifiable foundation.

### Costs and trade-offs

* Separate services introduce more build, configuration, deployment and monitoring work.
* Cross-service operations cannot rely on a single database transaction.
* Asynchronous processing introduces retries, duplicate delivery, idempotency and eventual-consistency concerns.
* Messaging contracts require explicit versioning and compatibility management.
* The system contains infrastructure that is not yet used by business functionality.
* Service boundaries may need revision as the domain becomes better understood.

## Evidence

The following Day 1 evidence supports this decision:

* The root Maven reactor successfully builds `messaging-contracts`, `aid-workflow-service` and `relief-logistics-service`.
* Aid Workflow Service returns HTTP `200` from its Dropwizard health endpoint on port `8081`.
* Relief Logistics Service returns HTTP `200` from its Dropwizard health endpoint on port `8091`.
* PostgreSQL contains separate `aid_workflow` and `relief_logistics` databases.
* Kafka is reachable through the Docker Compose environment.
* The Voice Service contains a FastAPI health boundary and a separate language-identification spike.
* The natural Telugu fixture was identified as `te: Telugu` by `speechbrain/lang-id-voxlingua107-ecapa` with a recorded confidence of approximately `0.9873` and an elapsed inference time of `0.335` seconds.
* The model result establishes feasibility for one fixture; it does not establish production accuracy.
* Final Day 1 smoke-test results are recorded through the root build, Compose status and application health checks.

