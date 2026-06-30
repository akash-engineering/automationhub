# Module: `payment`

Stripe-based subscription billing. Test mode. Live SDK calls guarded by `stripe.api-key`.

## Key classes

- `PaymentController` (JWT) — `GET /plans`, `POST /checkout-sessions`, `GET /subscriptions`, `GET /subscriptions/{id}`.
- `StripeWebhookController` — `POST /webhooks/stripe` (public; signature verified inside; bad signature → 400).
- `PaymentService` — looks up `Plan`, reuses the most-recent subscription's `stripeCustomerId` if present (else creates a new Stripe Customer), creates a Stripe Checkout Session in `SUBSCRIPTION` mode. Customer + session carry metadata `automationhub_owner_id` and `automationhub_plan_id` so the webhook can recover the linkage.
- `StripeClient` — SDK wrapper. Sets `Stripe.apiKey` if configured; live methods call `requireConfigured()`. `constructEvent(payload, sigHeader)` delegates to `com.stripe.net.Webhook.constructEvent` (real signature verification).
- `StripeWebhookService` — `@Transactional`. Verifies signature → inserts `ProcessedStripeEvent` (unique on `stripe_event_id`) → on `DataIntegrityViolationException` returns (already processed) → otherwise dispatches.

## Entities

- `Plan` — `name`, `stripePriceId` (unique `uk_plan_stripe_price_id`), `amount` (BigDecimal), `interval` (`MONTH` / `YEAR`). Table is empty by default — insert rows pointing to real Stripe price IDs.
- `Subscription` — `ownerId`, `stripeCustomerId`, `stripeSubscriptionId` (unique `uk_subscription_stripe_id`), `status` (`INCOMPLETE` / `ACTIVE` / `PAST_DUE` / `CANCELED`), `planId`, `currentPeriodEnd`.
- `Payment` — `ownerId`, `subscriptionId` (nullable), `stripePaymentIntentId`, `amount`, `status` (`SUCCEEDED` / `FAILED`), `paidAt`.
- `ProcessedStripeEvent` — `stripeEventId` (unique `uk_processed_stripe_event_id`), `eventType`.

## Events handled

| Event type | Effect |
|---|---|
| `checkout.session.completed` | Upsert `Subscription(ACTIVE)` keyed on `stripeSubscriptionId`; reads `ownerId` / `planId` from session metadata. |
| `invoice.payment_succeeded` | Record `Payment(SUCCEEDED)`; extend `currentPeriodEnd` from `invoice.period_end`; publish `PaymentSucceededEvent`. |
| `invoice.payment_failed` | `Subscription` → `PAST_DUE`; record `Payment(FAILED)`. |
| `customer.subscription.deleted` | `Subscription` → `CANCELED`. |

Unhandled types are logged and skipped (still recorded in `processed_stripe_events` so retries are no-ops).

## Real vs guarded

- Signature verification — **real** (`com.stripe:stripe-java:28.4.0` `Webhook.constructEvent`). Wrong secret → `SignatureVerificationException` → controller returns 400.
- Idempotency on `stripe_event_id` — **real**, race-safe via unique constraint + DIV catch (same pattern as `workflow.IdempotencyService`).
- Customer / Checkout Session creation — **guarded**. `StripeNotConfiguredException` → 503 when `stripe.api-key` blank. App boots, read endpoints (`/plans`, `/subscriptions`) still respond.

SDK pinned to `28.4.0`; v33+ restructured the Invoice model and dropped `Invoice.getSubscription()` / `getPaymentIntent()` that the handler relies on.

## Tie-in

Publishes `PaymentSucceededEvent(ownerId, paymentId, subscriptionId, amount, occurredAt)`. Consumed by `notification.PaymentEventListener` (AFTER_COMMIT + async).

## Config keys

| Key | Env | Notes |
|---|---|---|
| `stripe.api-key` | `STRIPE_API_KEY` | blank → live calls return 503 |
| `stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | blank → webhook returns 503 |
| `stripe.checkout.success-url` | `STRIPE_CHECKOUT_SUCCESS_URL` | default `http://localhost:8080/checkout/success` |
| `stripe.checkout.cancel-url` | `STRIPE_CHECKOUT_CANCEL_URL` | default `http://localhost:8080/checkout/cancel` |

## Test

`StripeWebhookServiceTest` — real Stripe SDK + mock repos. Computes valid Stripe signature header manually; three cases: valid signature on fresh event id (records + dispatches), duplicate event id via DIV (skips dispatch), wrong secret (throws `SignatureVerificationException`).
