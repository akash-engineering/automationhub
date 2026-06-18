# AutomationHub

A modular monolith automation platform built with Spring Boot 3 and Java 21.

## Modules

- **shared** — Cross-cutting primitives: domain events, exception handling, web utilities (correlation-ID filter, page wrapper).
- **infrastructure** — Framework configuration: async executor, OpenAPI, Jackson, Spring Security + JWT, JPA base entity / auditing.
- **auth** — User registration, login, and JWT issuance.
- **workflow** — Workflow CRUD, webhook triggers, asynchronous execution, pluggable action executors (Slack/Email/HTTP), and idempotency.
- **notification** — Listens for workflow domain events and dispatches notifications via Slack/Email senders.

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
