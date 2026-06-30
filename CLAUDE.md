# AutomationHub

Spring Boot 3 modular monolith. Java 21, PostgreSQL, JPA, JWT. Base package `com.automationhub`.

## Modules

- `shared` — `DomainEvent` marker, `ApiError`, common exceptions, `PageResponse`, MDC filter.
- `infrastructure` — `BaseEntity` + auditing, `JwtService` / `JwtAuthFilter` / `CurrentUser`, BCrypt, async executor, OpenAPI, Jackson.
- `auth` — register / login / `/auth/me`.
- `workflow` — CRUD, async execution, `ActionExecutor` strategy (HTTP / Slack / Email / DOCUMENT), idempotency, HMAC-signed public webhook trigger.
- `notification` — consumes `WorkflowCompleted/Failed` and `PaymentSucceeded`; dispatches via senders; persists `NotificationDelivery`.
- `document` — OpenPDF rendering, pluggable `StorageService`, `DOCUMENT` action + optional post-run summary listener.
- `payment` — Stripe Checkout; signed webhook with idempotent dispatch on `stripe_event_id`. Publishes `PaymentSucceededEvent`.

## Integration status

| Integration | Status | Notes |
|---|---|---|
| HTTP action | **Real** | Spring `RestClient`. |
| HMAC workflow webhook | **Real** | HMAC-SHA256, constant-time compare, ±5 min skew. |
| PDF generation | **Real** | OpenPDF 1.4.2. |
| Local file storage | **Real** | `Files.write` + path-escape guard. |
| Stripe webhook signature + idempotency | **Real** | `Webhook.constructEvent` (SDK 28.4.0) + unique constraint on `stripe_event_id`. |
| Slack — sender + action | **Fallback** | POSTs to `slack.webhook-url` when set; warn + log-only / simulated success when blank. |
| Stripe live calls (customer / checkout) | **Guarded** | `StripeNotConfiguredException` → 503 when `stripe.api-key` blank; app still boots. |
| Email — sender + action | **Stub** | Always log-only. No SMTP. Delivery row still recorded as `SENT`. |
| S3 storage | **Stub** | Both `put` / `get` throw `UnsupportedOperationException`. |

## Docs

- Architecture + event flow → `.claude/architecture.md`
- Cross-module rules → `.claude/module-boundaries.md`
- Coding conventions → `.claude/conventions.md`
- Infrastructure wiring → `.claude/infrastructure.md`
- Build / env / smoke tests → `.claude/build-and-run.md`
- Modules → `.claude/modules/{auth, shared, workflow, workflow-webhook, notification, document, payment}.md`

Diagrams (rendered on GitHub) → `docs/architecture/modules.md`.
