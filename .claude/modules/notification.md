# Module: `notification`

A downstream-only module. Listens to events published by `workflow` (and future modules) and dispatches user-facing notifications. It depends on `shared` and `infrastructure`; it depends on **no other feature module**.

> **Status:** package scaffolding exists, but no listeners, services, or senders are implemented yet. The `workflow` module already publishes `WorkflowCompletedEvent` and `WorkflowFailedEvent` — this module is the next thing to build.

## Layout (target)

```
notification/
├── listener/   WorkflowEventListener      (@Component; @TransactionalEventListener + @Async)
├── service/    NotificationService
├── sender/     SlackSender, EmailSender
└── dto/        NotificationRequest
```

## Events to consume

These are already published by `workflow.ExecutionRunner` from inside the finalize TX:

```java
public record WorkflowCompletedEvent(UUID workflowId, UUID executionId, UUID ownerId, Instant occurredAt)
    implements DomainEvent {}

public record WorkflowFailedEvent(UUID workflowId, UUID executionId, UUID ownerId, String reason, Instant occurredAt)
    implements DomainEvent {}
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

    @Async("automationHubTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(WorkflowFailedEvent event) {
        // include event.reason() in the notification body
    }
}
```

Rules:

- **`AFTER_COMMIT`** — never `BEFORE_COMMIT` or default phase; we only react to durable changes.
- **`@Async`** — never block the producer's request thread (which already returned 202 to the client well before this listener fires).
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
