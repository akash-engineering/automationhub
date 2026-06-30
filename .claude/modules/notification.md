# Module: `notification`

Downstream consumer. Listens to events and dispatches notifications, persisting an audit row per attempt. Depends on `shared` / `infrastructure` only. Consumes events from `workflow` and `payment`.

## Key classes

- `NotificationController` — `GET /notifications/executions/{executionId}` (owner-scoped).
- `WorkflowEventListener` — `@TransactionalEventListener(AFTER_COMMIT) @Async("automationHubTaskExecutor")`. Consumes `WorkflowCompletedEvent` / `WorkflowFailedEvent`.
- `PaymentEventListener` — same wiring. Consumes `PaymentSucceededEvent`.
- `NotificationService` — resolves sender via `EnumMap<NotificationChannel, Sender>`; catches all sender exceptions; persists `NotificationDelivery` with `SENT` / `FAILED`. **Sender failure never propagates** — the audit row is the contract.
- `Sender` (interface), `SlackSender`, `EmailSender`.

## Entities

- `NotificationDelivery` — `ownerId` (NOT NULL), `executionId` / `workflowId` (**nullable** — workflow path populates them, payment path leaves null), `channel`, `status`, `recipient`, `message` (TEXT).
- `NotificationChannel` — `SLACK`, `EMAIL`. `DeliveryStatus` — `SENT`, `FAILED`.

## Senders — reality

- `SlackSender` — real POST to `slack.webhook-url` (Spring `RestClient`, `{"text": subject + "\n" + body}`) when set. Blank → `log.warn(...)` + early return. Non-2xx → `SenderException` → row marked `FAILED`. Blank-config path returns without throwing, so the row is marked `SENT` even though nothing left the machine.
- `EmailSender` — **stub**. `log.info("[email] ...")` and returns. No SMTP, no env var. Delivery row says `SENT` regardless.

## Recipient

Currently `"owner:" + ownerId` placeholder for both listeners. A real recipient resolver lands when there's a reason.
