# Module: `notification`

Downstream-only module. Listens to events published by `workflow` and dispatches user-facing notifications, persisting an audit row per attempt. Depends on `shared` and `infrastructure`; depends on **no other feature module**. Communication is **events only** — never inject a workflow service here.

## Layout

```
notification/
├── controller/   NotificationController        (GET /notifications/executions/{executionId})
├── listener/     WorkflowEventListener         (@TransactionalEventListener(AFTER_COMMIT) + @Async)
├── service/      NotificationService
├── sender/       Sender (interface), SenderException, SlackSender, EmailSender
├── repository/   NotificationDeliveryRepository
├── entity/       NotificationDelivery, NotificationChannel, DeliveryStatus
└── dto/          NotificationRequest, NotificationDeliveryResponse
```

## Entities

- **`NotificationDelivery`** — `executionId : UUID`, `workflowId : UUID`, `ownerId : UUID`, `channel : NotificationChannel` (`SLACK | EMAIL`), `status : DeliveryStatus` (`SENT | FAILED`), `recipient : String`, `message : String` (TEXT). One row per `(execution, channel)` attempt — audit log of who got what.
- **`NotificationChannel`** — enum `SLACK`, `EMAIL`. Add a value here when introducing a new channel.
- **`DeliveryStatus`** — enum `SENT`, `FAILED`.

## Events consumed

Published by `workflow.ExecutionRunner` from inside the finalize TX:

```java
public record WorkflowCompletedEvent(UUID workflowId, UUID executionId, UUID ownerId, Instant occurredAt)
    implements DomainEvent {}

public record WorkflowFailedEvent(UUID workflowId, UUID executionId, UUID ownerId, String reason, Instant occurredAt)
    implements DomainEvent {}
```

## Listener — `WorkflowEventListener`

```java
@Async("automationHubTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCompleted(WorkflowCompletedEvent event) { ... }

@Async("automationHubTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onFailed(WorkflowFailedEvent event) { ... }
```

- **`AFTER_COMMIT`** — fires only after the workflow's terminal-status row is durable. Never `BEFORE_COMMIT` or default phase.
- **`@Async`** — runs on `automationHubTaskExecutor`, never the producer's request thread (which already returned 202 long before this fires).
- Wraps each handler in MDC `executionId` so log lines carry the correlation.
- Calls `NotificationService.send(...)` once per `NotificationChannel.values()`. Recipient is currently `"owner:" + ownerId` — placeholder until a real recipient resolver lands (likely via a `UserCreatedEvent` from `auth`).

## Service — `NotificationService`

- Constructor takes `List<Sender>`, builds an `EnumMap<NotificationChannel, Sender>` (rejects duplicates with `IllegalStateException`). Missing channel is allowed — `send` records a `FAILED` row with reason `"no sender registered for channel ..."`.
- `send(NotificationRequest) : NotificationDeliveryResponse` — `@Transactional`. Resolves sender, invokes it, catches `Exception` (including `SenderException`), persists `NotificationDelivery` with `SENT`/`FAILED` + message. **A sender failure never propagates back to the listener** — the audit row is the contract.
- `listForExecution(executionId, ownerId)` — owner-scoped lookup used by the controller.

## Senders — `sender/`

- **`Sender`** (interface): `boolean supports(NotificationChannel)` + `void send(NotificationRequest) throws SenderException`.
- **`SlackSender`**, **`EmailSender`** — currently log-only (`[slack] to=… subject=… body=…`). Real transport is a one-class swap: replace the body of `send(...)` with the Slack webhook / SMTP call. Configuration via `application.yml` + env vars.
- A new channel (SMS, Discord, …) = add to the `NotificationChannel` enum + add a new `@Component` implementing `Sender`. The service picks it up.

## Controller

| Method | Path                                                  | Auth   | Notes |
|--------|-------------------------------------------------------|--------|-------|
| GET    | `/notifications/executions/{executionId}`             | bearer | Returns `List<NotificationDeliveryResponse>` for the current user only |

## What does **not** belong here

- Workflow domain logic, action execution, idempotency.
- Direct repository access to anything outside `notification`.
- Synchronous calls into `workflow` or `auth`.
- Any code path that propagates a sender failure back into the workflow execution.
