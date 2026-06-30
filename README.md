# AutomationHub

A production-grade backend platform built with **Spring Boot 3 · Java 21 · PostgreSQL**.  
Three self-contained portfolio projects, one deployable — run everything with `docker compose up`.

---

## Portfolio Project 1 — Workflow Automation Engine

**What it does:** Users define multi-step workflows and trigger them via a JWT-secured API or a public HMAC-signed webhook. Each step runs one of four action types. Execution is asynchronous, race-safe, and fully auditable.

**Technical highlights:**
- **Pluggable action executor** strategy — `EnumMap<ActionType, ActionExecutor>` resolved at startup; adding a new action type requires one `@Component`, nothing else.
- **Async execution** — `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` ensures the runner fires only after the initiating transaction is durable, so no partial state is observable.
- **Race-safe idempotency** — unique constraint on `idempotency_key`, `REQUIRES_NEW` propagation, `DataIntegrityViolationException` catch + orphan cleanup. Verified by a concurrent latch-based integration test against a real Postgres container.
- **HMAC-signed public webhook** — `HmacSHA256`, constant-time `MessageDigest.isEqual`, ±5 min timestamp skew window. Webhook secret is `SecureRandom` 32-byte base64url; returned once on creation, rotation revokes the prior secret. All failure modes (missing secret, bad signature, expired timestamp) return a single generic 401 — callers cannot distinguish which check failed.
- **Event-driven notifications** — on completion/failure, publishes `WorkflowCompletedEvent` / `WorkflowFailedEvent`; `notification` module consumes them asynchronously and dispatches via Slack / Email senders, persisting a `NotificationDelivery` audit row regardless of sender outcome.

**Action types:**

| Type | Reality |
|---|---|
| `HTTP` | Real — Spring `RestClient`, any method/body/headers |
| `SLACK` | Real when `SLACK_WEBHOOK_URL` is set; log-only fallback otherwise |
| `DOCUMENT` | Real — generates a PDF (see Portfolio Project 2) |
| `EMAIL` | Stub — logs only; SMTP not wired |

**Key endpoints (JWT-secured):**

| Method | Path | Notes |
|---|---|---|
| `POST` | `/workflows` | Create with inline action list |
| `POST` | `/workflows/{id}/execute` | `Idempotency-Key` header recommended; `202` |
| `GET` | `/workflows/{id}/executions` | Paged, newest first |
| `GET` | `/workflows/{id}/executions/{eid}/logs` | Per-step log, ordered |
| `POST` | `/workflows/{id}/webhook` | Rotate secret — returned once |
| `POST` | `/webhooks/workflows/{id}` | **Public**, HMAC-signed trigger |

---

## Portfolio Project 2 — PDF Invoice Generation

**What it does:** A `DOCUMENT` action inside any workflow generates a PDF invoice and stores it. Documents are owner-scoped and streamable via a dedicated endpoint. Storage backend is pluggable.

**Technical highlights:**
- **OpenPDF rendering** — A4 page, title / date / recipient / two-column line-item table with a Total row. Currency-formatted with `BigDecimal.setScale(2, HALF_UP)`.
- **Pluggable `StorageService`** — `@ConditionalOnProperty` selects `LocalFileStorageService` (active, `Files.write` with path-traversal guard) or `S3StorageService` (stubbed — throws `UnsupportedOperationException`). Swap backends by setting `automationhub.document.storage.provider=s3`.
- **Two integration paths** — `DocumentActionExecutor` runs inside a workflow step; an opt-in `@TransactionalEventListener` auto-generates a post-run summary PDF on every workflow completion (disabled by default, toggle `automationhub.document.auto-summary.enabled=true`).
- **Streaming download** — `GET /documents/{id}/download` sets `Content-Type`, `Content-Disposition`, and `Content-Length`; streams raw bytes.

**Action config shape:**
```json
{
  "title": "Invoice #001",
  "recipient": "Acme Corp",
  "currency": "USD",
  "lines": [
    { "description": "Consulting", "amount": 1500.00 },
    { "description": "Travel",     "amount":  320.00 }
  ]
}
```

**Key endpoints (JWT-secured):**

| Method | Path | Notes |
|---|---|---|
| `GET` | `/documents` | Owner-scoped list, paged |
| `GET` | `/documents/{id}` | Metadata |
| `GET` | `/documents/{id}/download` | Streams PDF bytes |

---

## Portfolio Project 3 — Stripe Subscription Billing

**What it does:** Full subscription lifecycle backed by Stripe. Users check out via a hosted Stripe Checkout page; incoming Stripe webhooks update subscription state and trigger downstream notifications — all idempotently and with real signature verification.

**Technical highlights:**
- **Real Stripe signature verification** — `com.stripe:stripe-java:28.4.0` `Webhook.constructEvent`; wrong secret → `SignatureVerificationException` → `400`. No roll-your-own crypto.
- **Idempotent webhook dispatch** — `ProcessedStripeEvent` entity with unique constraint `uk_processed_stripe_event_id`. Same race-safe `REQUIRES_NEW` + `DataIntegrityViolationException` pattern as the workflow idempotency service — Stripe can retry freely.
- **Guarded live calls** — `StripeNotConfiguredException` → `503` when `STRIPE_API_KEY` is blank. App boots, read endpoints (`/plans`, `/subscriptions`) stay live; no cascading failure.
- **Event-driven payment notifications** — `invoice.payment_succeeded` publishes `PaymentSucceededEvent`; `notification.PaymentEventListener` consumes it `AFTER_COMMIT` + `@Async` and dispatches to configured channels.

**Webhook events handled:**

| Event | Effect |
|---|---|
| `checkout.session.completed` | Upsert `Subscription(ACTIVE)` from session metadata |
| `invoice.payment_succeeded` | Record `Payment(SUCCEEDED)`, extend `currentPeriodEnd`, publish `PaymentSucceededEvent` |
| `invoice.payment_failed` | `Subscription → PAST_DUE`, record `Payment(FAILED)` |
| `customer.subscription.deleted` | `Subscription → CANCELED` |

**Key endpoints:**

| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/plans` | JWT | List available plans |
| `POST` | `/checkout-sessions` | JWT | Returns Stripe Checkout URL |
| `GET` | `/subscriptions` | JWT | Owner's subscriptions |
| `POST` | `/webhooks/stripe` | Public | Stripe → verified + idempotent |

---

## Stack

| Layer | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Persistence | PostgreSQL 16, Spring Data JPA, Hibernate 6 |
| Auth | JWT (jjwt 0.12.6), BCrypt |
| Payments | Stripe Java SDK 28.4.0 |
| PDF | OpenPDF 1.4.2 |
| HTTP client | Spring `RestClient` |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers (Postgres 16), MockWebServer, Awaitility |
| API docs | SpringDoc OpenAPI 3 |

---

## Running locally

```bash
cp .env.example .env          # fill in DB_PASSWORD and JWT_SECRET at minimum
docker compose up --build -d  # starts Postgres 16 + app on :8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Stripe and Slack are **off by default** — set `STRIPE_API_KEY` / `SLACK_WEBHOOK_URL` in `.env` to enable live calls. Everything else works without them.

---

## Tests

```bash
mvn test
```

Unit tests run without Docker. Integration tests spin up a shared Testcontainers Postgres container automatically — no manual setup needed.
