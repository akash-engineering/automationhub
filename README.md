# AutomationHub

A modular monolith automation platform built with Spring Boot 3 and Java 21.

See [`docs/architecture/modules.md`](docs/architecture/modules.md) for the module graph and runtime event flow (Mermaid, renders on GitHub).

## Modules

- **shared** — Cross-cutting primitives: domain events, exception handling, web utilities (correlation-ID filter, page wrapper).
- **infrastructure** — Framework configuration: async executor, OpenAPI, Jackson, Spring Security + JWT, JPA base entity / auditing.
- **auth** — User registration, login, and JWT issuance.
- **workflow** — Workflow CRUD, JWT-protected execute + HMAC-signed public webhook trigger, asynchronous execution on a named executor, pluggable action executors (Slack / Email / HTTP / Document), and race-safe idempotency.
- **notification** — `@TransactionalEventListener(AFTER_COMMIT)` consumer of workflow events, dispatches via Slack / Email senders, persists a `NotificationDelivery` audit row per attempt; sender failures never propagate back to the workflow.
- **document** — Generates invoice-shaped PDFs (OpenPDF) and stores them via a pluggable `StorageService` (`local` active, `s3` stubbed). Two integration paths: a `DOCUMENT` action type that runs inside a workflow, and an opt-in completion listener that produces a post-run summary.

## Tests

`mvn test` runs the engine slice: JUnit 5 + Mockito for unit coverage and Testcontainers (PostgreSQL 16) for integration tests, including a concurrent-Idempotency-Key race test that asserts exactly one execution row under contention.

## Build & Run

Build:

```
mvn clean package
```

Run with Docker Compose (after copying `.env.example` to `.env`):

```
cp .env.example .env
docker compose up --build
```

## URLs

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Actuator health: http://localhost:8080/actuator/health
