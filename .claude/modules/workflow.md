# Module: `workflow`

Owns workflows, actions, executions, logs, the `ActionExecutor` strategy, idempotency. Publishes `WorkflowCompletedEvent` / `WorkflowFailedEvent`.

HMAC webhook trigger lives in `.claude/modules/workflow-webhook.md`.

## Key classes

- `WorkflowController` (`/workflows`), `ExecutionController` (`/workflows/{id}/execute`, executions, logs).
- `WorkflowService` — CRUD + `rotateWebhookSecret` / `disableWebhook`.
- `ExecutionService` — sync entry: ownership check, idempotency, schedules runner via `TransactionSynchronization.afterCommit`.
- `ExecutionRunner` — `@Async("automationHubTaskExecutor") @Transactional`. Loops actions in order, writes `ExecutionLog` per step, publishes terminal event.

## Action executors

- `ActionExecutor` (SPI): `boolean supports(ActionType)`, `ActionResult execute(Action)`.
- `ActionExecutorRegistry` — constructor takes `List<ActionExecutor>`, builds `EnumMap<ActionType, ActionExecutor>`, rejects duplicates.

| Type | Class | Real? |
|---|---|---|
| `HTTP` | `HttpActionExecutor` | Real — Spring `RestClient` |
| `SLACK` | `SlackActionExecutor` | Real when `slack.webhook-url` set; simulated success otherwise |
| `EMAIL` | `EmailActionExecutor` | **Stub** — always returns `ok("email: simulated send")` |
| `DOCUMENT` | `document.service.action.DocumentActionExecutor` | Real — see document module |

## Idempotency

- `IdempotencyKey` — unique constraint `uk_idempotency_key` on column `key_value`.
- `IdempotencyService.record` — `@Transactional(propagation = REQUIRES_NEW)` + `saveAndFlush`. Lets `DataIntegrityViolationException` propagate.
- `ExecutionService.execute` catches DIV, deletes orphan `Execution`, returns winner via `findExecutionId`.
- Race-tested in `ExecutionServiceRaceIntegrationTest` (two threads, latch-coordinated, Testcontainers Postgres). The `REQUIRES_NEW` shape exists because a unique-constraint violation aborts the Postgres TX it happens in — if `record` ran in the caller's TX, the recovery query would fail too.

## Adding a new action type

1. Add the enum value to `ActionType`.
2. Add a `@Component implements ActionExecutor` anywhere. Registry picks it up — do not edit it.

## DDL gotcha

`ddl-auto: update` does not update existing CHECK constraints. After adding an enum value, drop the stale check on the running DB:

```
ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_action_type_check;
```

JPA still enforces via `@Enumerated(STRING)`. Long-term: migrations.

## Endpoints (JWT-secured)

| Method | Path | Notes |
|---|---|---|
| POST / GET / DELETE | `/workflows[/{id}]` | CRUD; paginated list |
| POST | `/workflows/{id}/execute` | header `Idempotency-Key` (recommended); 202 |
| GET | `/workflows/{id}/executions` | paged, newest first |
| GET | `/workflows/{id}/executions/{eid}/logs` | ordered by `actionOrder` |
| POST / DELETE | `/workflows/{id}/webhook` | rotate / disable webhook secret |
