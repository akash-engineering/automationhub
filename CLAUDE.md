# AutomationHub

Spring Boot 3 modular monolith. Java 21, Maven, PostgreSQL, JPA, Spring Security + JWT, springdoc-openapi, Lombok, Docker Compose.

Base package: `com.automationhub`. Modules: `shared`, `infrastructure`, `auth`, `workflow`, `notification`, `document`, `payment`. Future (do not create yet): `sync`.

## Module status

| Module        | State                                                                                  |
|---------------|----------------------------------------------------------------------------------------|
| `shared`      | Implemented. Marker `DomainEvent`, `ApiError`, common exceptions, `PageResponse`, MDC. |
| `infrastructure` | Implemented. Persistence (`BaseEntity` + auditing), security (JWT filter, `CurrentUser`, BCrypt), async executor, OpenAPI, Jackson. |
| `auth`        | Implemented end-to-end. `POST /auth/register`, `POST /auth/login`, `GET /auth/me`.     |
| `workflow`    | Implemented end-to-end. CRUD, async execution on `automationHubTaskExecutor`, per-step `ExecutionLog`, idempotency via `Idempotency-Key` header (race-safe via `REQUIRES_NEW` + DIV catch), HMAC-signed public webhook trigger, publishes `WorkflowCompletedEvent` / `WorkflowFailedEvent`. `SlackActionExecutor` posts to Slack when `slack.webhook-url` is set (simulated success otherwise); `EmailActionExecutor` is log-only; `HttpActionExecutor` and `DocumentActionExecutor` do real work. |
| `notification`| Implemented. `@TransactionalEventListener(AFTER_COMMIT) @Async` listener dispatches to `SlackSender` / `EmailSender`, persists `NotificationDelivery` audit rows; sender failure never propagates back to the workflow. **Slack is live** when `slack.webhook-url` (`SLACK_WEBHOOK_URL`) is set — POSTs to a Slack Incoming Webhook; blank → log-only fallback. Email remains log-only. |
| `document`    | Implemented. Two integration paths: `DOCUMENT` action type (`DocumentActionExecutor`) renders an invoice-shaped PDF mid-run; `WorkflowCompletedListener` (AFTER_COMMIT + @Async, off by default) generates a post-run summary. Pluggable `StorageService` — `LocalFileStorageService` active, `S3StorageService` wired but stubbed. PDF via OpenPDF 1.4.2. |
| `payment`     | Implemented. Stripe Checkout for subscriptions: `Plan` / `Subscription` / `Payment` / `ProcessedStripeEvent` entities; `PaymentController` exposes `GET /plans`, `POST /checkout-sessions`, `GET /subscriptions`. `StripeWebhookController` at `POST /webhooks/stripe` verifies signatures via `Webhook.constructEvent`. Webhook idempotency uses the `REQUIRES_NEW`-style unique-constraint pattern on `stripe_event_id`. Publishes `PaymentSucceededEvent` on `invoice.payment_succeeded`; consumed by `notification.PaymentEventListener` (AFTER_COMMIT + @Async). All live Stripe calls guarded by `stripe.api-key` — app boots and tests pass with it unset. SDK: `com.stripe:stripe-java:28.4.0`. |
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
- **`notification`** (event listeners, senders, delivery audit) → `.claude/modules/notification.md`
- **`document`** (PDF generation, storage abstraction, DOCUMENT action + completion listener) → `.claude/modules/document.md`

## When in doubt

If a rule above conflicts with what you find in the code, the rule wins — flag the divergence rather than silently aligning to the code. If a rule isn't covered here, ask before inventing one; project conventions are still solidifying.
