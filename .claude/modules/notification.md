# Module: `notification`

A downstream-only module. Listens to events published by `workflow` (and future modules) and dispatches user-facing notifications. It depends on `shared` and `infrastructure`; it depends on **no other feature module**.

## Layout

```
notification/
├── listener/   WorkflowEventListener      (@Component; @TransactionalEventListener + @Async)
├── service/    NotificationService
├── sender/     SlackSender, EmailSender
└── dto/        NotificationRequest
```

## Listener pattern

```java
@Component
public class WorkflowEventListener {
    private final NotificationService notificationService;

    public WorkflowEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("automationHubTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(WorkflowCompletedEvent event) {
        // fetch what you need by UUID, build NotificationRequest, call service
    }
}
```

Rules:

- **`AFTER_COMMIT`** — never `BEFORE_COMMIT` or default phase; we only react to durable changes.
- **`@Async`** — never block the producer's request thread.
- The listener is the only thing that knows the event type. The service should accept a neutral DTO (`NotificationRequest`), not a domain event.

## Service contract

- **`NotificationService`** — `send(NotificationRequest)`. Picks a `Sender` (Slack/Email) based on the request, delegates. No persistence today; add a `notifications` table later if delivery history is needed.

## Senders

- **`SlackSender`**, **`EmailSender`** — `@Component`s exposing `send(NotificationRequest)`. Each owns its transport (Slack webhook, SMTP/SES). Configuration via `application.yml` + env vars.
- A new channel (SMS, Discord, …) = a new `Sender` + a discriminator on `NotificationRequest`. Don't fork `NotificationService` per channel.

## DTOs (records)

- **`NotificationRequest`** — channel (enum), recipient, subject, body, and whatever per-channel metadata makes sense. Build it in the listener, not in the event.

## What does **not** belong here

- Workflow domain logic, action execution, idempotency.
- Direct repository access to anything outside `notification`.
- Synchronous calls into `workflow` or `auth`.
