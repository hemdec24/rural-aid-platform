# Rural Aid Coordination Platform

A multilingual, voice-first platform that converts rural emergency calls into trackable aid requests and coordinates the reservation, dispatch and delivery of relief resources.

The primary actors are:

- Aid requesters
- Aid providers, such as NGOs, medical professionals and government agencies
- Aid coordinators
- Aid deliverers

## Repository structure

- `aid-workflow-service`: owns the aid-request lifecycle and workflow coordination
- `relief-logistics-service`: owns relief inventory, reservations, assignments and delivery state
- `voice-service`: provides the voice-processing boundary and model feasibility spikes
- `messaging-contracts`: contains shared Java message representations
- `coordinator-web`: reserved for the future thin web client
- `infrastructure`: contains local infrastructure and future deployment configuration
- `docs/architecture-decisions`: contains architecture decision records

## Prerequisites

Install the following tools:

* JDK 21 or a compatible newer JDK; the current development environment uses JDK 25
* Apache Maven
* Python 3
* Docker Desktop, including Docker Compose
* Git
* `curl` for testing HTTP endpoints
* FFmpeg only if audio files need conversion to WAV

Verify the installation:

```bash
java -version
mvn -version
python3 --version
docker version
docker compose version
git --version
```

## Build

Run the Java build and tests:

```bash
mvn test
```

Build all Docker images:

```bash
docker compose build
```

## Start the local stack

Build and start all services in the background:

```bash
docker compose up -d --build
```

Check their status:

```bash
docker compose ps
```

## Health endpoints

Check the Aid Workflow Service:

```bash
curl -i http://localhost:8081/healthcheck
```

Check the Relief Logistics Service:

```bash
curl -i http://localhost:8091/healthcheck
```

Check the Voice Service:

```bash
curl -i http://localhost:8000/health
```

Each endpoint should return HTTP `200`.

## Stop the local stack

Stop and remove the containers and Compose network:
This preserves named volumes.

```bash
docker compose down
```

To intentionally delete local volume data as well, run:

```bash
docker compose down --volumes
```

## Runtime principles

- Containers hold no authoritative local state.
- Configuration is supplied through configuration files or environment variables.
- Each container runs one foreground process.
- Health and graceful shutdown are required from the beginning.

## Ownership boundaries

Service boundaries are based on ownership of business decisions and authoritative state.

- Aid Workflow Service: owns request validation, lifecycle, workflow coordination and request history.
- Relief Logistics Service: owns inventory, reservations, responder assignments and delivery state.
- Voice Service: owns audio intake and voice-processing jobs, but does not own aid-request or inventory decisions.
- The future Voice API and Voice Worker will be separate deployments within one Voice bounded context.
- Coordinator Web: owns presentation only. Backend services remain authoritative for validation, API contracts and versions, authorization, business invariants and idempotency.
- Each service owns its database. Cross-service table access is prohibited.

See `docs/architecture-decisions/ADR-001-day-1-boundaries.md` for the complete decision and its trade-offs.

## Current Day 1 scope

Day 1 establishes a reliable development skeleton consisting of:

* Aid Workflow Service built with Dropwizard
* Relief Logistics Service built with Dropwizard
* Voice Service skeleton built with FastAPI
* PostgreSQL and Kafka running through Docker Compose
* Health endpoints for all three application services
* A shared `messaging-contracts` Maven module
* A language-identification feasibility spike using a pretrained model

Business entities, database schemas, Kafka producers and consumers, durable voice jobs, API resources and frontend functionality are intentionally postponed.

## Future system boundaries

`coordinator-web` will be a thin client responsible for presenting information, collecting user input and calling backend APIs. Backend services will remain responsible for validation, authorization, API contracts and versions, business invariants and idempotency. This ensures that business rules are enforced consistently regardless of which client calls an API.

The future Voice API will accept voice-processing requests and create durable jobs. A separate worker will process those jobs asynchronously. This boundary allows HTTP request handling and compute-intensive voice processing to scale, fail and recover independently.

The durable queue and worker are intentionally not implemented on Day 1. No frontend implementation is expected on Day 1.

## Language-identification spike

Instructions for running the spike are available in:

```text
voice-service/spikes/language-id/README.md
```

The spike was tested with a locally recorded fixture:

```text
test-audio/telugu-emergency-request.wav
```

Recorded result:

```json
{
  "audio_path": "test-audio/telugu-emergency-request.wav",
  "model_id": "speechbrain/lang-id-voxlingua107-ecapa",
  "predicted_language": "te: Telugu",
  "class_index": 92,
  "log_score": -0.012756885960698128,
  "confidence": 0.9873241186141968,
  "elapsed_seconds": 0.335
}
```

This result demonstrates technical feasibility for one natural Telugu recording. It does not establish production accuracy across speakers, dialects, recording conditions or languages.
