# AutomationHub

Spring Boot 3 modular monolith. Java 21, Maven, PostgreSQL, JPA, Spring Security + JWT, springdoc-openapi, Lombok, Docker Compose.

Base package: `com.automationhub`. Modules: `shared`, `infrastructure`, `auth`, `workflow`, `notification`. Future (do not create yet): `document`, `payment`, `sync`.

## Module status

| Module        | State                                                                                  |
|---------------|----------------------------------------------------------------------------------------|
| `shared`      | Implemented. Marker `DomainEvent`, `ApiError`, common exceptions, `PageResponse`, MDC. |
| `infrastructure` | Implemented. Persistence (`BaseEntity` + auditing), security (JWT filter, `CurrentUser`, BCrypt), async executor, OpenAPI, Jackson. |
| `auth`        | Implemented end-to-end. `POST /auth/register`, `POST /auth/login`, `GET /auth/me`.     |
| `workflow`    | Implemented end-to-end. CRUD, async execution on `automationHubTaskExecutor`, per-step `ExecutionLog`, idempotency via `Idempotency-Key` header (race-safe via `REQUIRES_NEW` + DIV catch), HMAC-signed public webhook trigger, publishes `WorkflowCompletedEvent` / `WorkflowFailedEvent`. |
| `notification`| Implemented. `@TransactionalEventListener(AFTER_COMMIT) @Async` listener dispatches to `SlackSender` / `EmailSender` (log-only senders), persists `NotificationDelivery` audit rows; sender failure never propagates back to the workflow. |
| **Tests**     | Engine slice in place. JUnit 5 + Mockito for unit; Testcontainers Postgres + `okhttp3:mockwebserver` for integration. Base class: `com.automationhub.testsupport.PostgresTestBase` (singleton container). Run with `mvn test`. |

## Hard rules (always apply)

- **Constructor injection only.** No `@Autowired` on fields. No setter injection. `final` fields + single constructor.
- **All DTOs are Java `record`s.** No Lombok DTOs. Responses expose a `static from(...)` factory where mapping is non-trivial.
- **Cross-module communication is event-driven.** A module's service must never inject another module's service. Use `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT) @Async` consumers.
- **Cross-module references use UUIDs, not JPA relations.** `Workflow.ownerId` is `UUID`, never `@ManyToOne User`. Same for `Execution.workflowId`, `ExecutionLog.executionId`, `Action.workflowId`. This is intentional — don't "fix" it.
- **Entities extend `BaseEntity`** for `UUID id`, `createdAt`, `updatedAt`, and auditing.
- **No business logic in controllers.** Controllers parse/validate input, delegate to services, shape responses.
- **Stubs may `throw new UnsupportedOperationException(...)`** while the skeleton is being filled in — they must still compile and the app must start.

## Routing — load on demand

Each file below is small and self-contained. Read only what's relevant to the task:

- **Architecture & package layout** → `.claude/architecture.md`
- **Coding conventions (records, Lombok, naming, validation, exceptions)** → `.claude/conventions.md`
- **Module boundary rules & event flow** → `.claude/module-boundaries.md`
- **Infrastructure (persistence, security, async, OpenAPI, Jackson)** → `.claude/infrastructure.md`
- **Build / run / env vars / URLs / smoke tests** → `.claude/build-and-run.md`

Feature modules:

- **`shared`** primitives (events, exceptions, web utils) → `.claude/modules/shared.md`
- **`auth`** (registration, login, JWT, `/auth/me`) → `.claude/modules/auth.md`
- **`workflow`** (CRUD, executor pipeline, idempotency, events) → `.claude/modules/workflow.md`
- **`notification`** (event listeners, senders — not yet implemented) → `.claude/modules/notification.md`

## When in doubt

If a rule above conflicts with what you find in the code, the rule wins — flag the divergence rather than silently aligning to the code. If a rule isn't covered here, ask before inventing one; project conventions are still solidifying.
