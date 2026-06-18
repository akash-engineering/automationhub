# Module: `workflow`

Owns workflows, their actions, executions, execution logs, idempotent webhook triggering, and the pluggable action executor pipeline. **Publishes** `WorkflowCompletedEvent` / `WorkflowFailedEvent` for `notification` (and future modules) to consume.

## Layout

```
workflow/
├── controller/    WorkflowController (/workflows), WebhookController (/webhooks)
├── service/       WorkflowService, WorkflowExecutionService
│   └── action/    ActionExecutor, ActionExecutorRegistry,
│                  SlackActionExecutor, EmailActionExecutor, HttpActionExecutor
├── repository/    WorkflowRepository, ExecutionRepository, ExecutionLogRepository
├── entity/        Workflow, Action, ActionType, Execution, ExecutionStatus, ExecutionLog
├── dto/           WorkflowRequest, WorkflowResponse, ExecutionResponse
├── event/         WorkflowCompletedEvent, WorkflowFailedEvent
└── idempotency/   IdempotencyKey, IdempotencyService
```

## Entities

- **`Workflow`** — `name : String`, `ownerId : UUID`. **No `@ManyToOne User`.** Owner is referenced by UUID only; resolve via `auth` API only if you genuinely need user data (today: don't).
- **`Action`** — `type : ActionType` (`SLACK | EMAIL | HTTP`, `@Enumerated(STRING)`), `config : String` (JSON blob, `TEXT` column), `order : int` (column `execution_order` to avoid the SQL reserved word).
- **`Execution`** — `workflowId : UUID`, `status : ExecutionStatus` (`PENDING | RUNNING | COMPLETED | FAILED`).
- **`ExecutionLog`** — `executionId : UUID`, `message : String`, `status : ExecutionStatus` (or its own log-level enum once implemented).

All extend `BaseEntity`.

## DTOs (records)

- **`WorkflowRequest`** — payload for create/update.
- **`WorkflowResponse`** with `static from(Workflow)` (and a paginated variant via `PageResponse`).
- **`ExecutionResponse`** with `static from(Execution)`.

## Action executor pipeline

The core extension point of the module.

- **`ActionExecutor`** (interface):
  ```java
  boolean supports(ActionType type);
  void execute(Action action);
  ```
- **`ActionExecutorRegistry`** — `@Component`, constructor takes `List<ActionExecutor>` (Spring auto-injects every bean), eagerly builds a `Map<ActionType, ActionExecutor>` in the constructor (rejects duplicates), and exposes `resolve(ActionType) : ActionExecutor`.
- Three concrete `@Component` executors today: `SlackActionExecutor`, `EmailActionExecutor`, `HttpActionExecutor`. Each `supports()` returns its single `ActionType`; `execute()` is stubbed for now.

**Adding a new action type** = (1) add an enum constant to `ActionType`, (2) add a new `@Component` implementing `ActionExecutor`. The registry picks it up automatically — do not touch the registry.

## Service contracts

- **`WorkflowService`** — CRUD over `Workflow` (+ its `Action`s). Owns `@Transactional` boundaries. Authorizes against `CurrentUser.getId()` matching `Workflow.ownerId`.
- **`WorkflowExecutionService`** — entrypoint for execution. Method that starts a run is `@Async("automationHubTaskExecutor")`. Steps:
  1. Create `Execution` row in `PENDING` → flip to `RUNNING`.
  2. Load actions ordered by `order`.
  3. For each: `registry.resolve(action.type).execute(action)`, append `ExecutionLog`.
  4. On success → status `COMPLETED`, publish `WorkflowCompletedEvent`. On failure → status `FAILED`, publish `WorkflowFailedEvent`. Publishing happens **inside the transaction** that finalizes the execution row; consumers see it `AFTER_COMMIT`.

## Events (records)

```java
public record WorkflowCompletedEvent(UUID workflowId, UUID executionId) implements DomainEvent {}
public record WorkflowFailedEvent(UUID workflowId, UUID executionId, String reason) implements DomainEvent {}
```

UUIDs (and small primitives) only. No entities.

## Idempotency

- **`IdempotencyKey`** entity: `key` column with a **unique constraint** + index. Records key + (eventually) request hash + response snapshot.
- **`IdempotencyService`** — `WebhookController` calls this first. Contract (when implemented): "given key K and a supplier, run the supplier exactly once across retries; subsequent calls return the cached result". Use a unique-constraint insert + read-back pattern to make it race-safe.
- `WebhookController` accepts a header (`Idempotency-Key` is the convention) and delegates.

## Endpoints

- `GET/POST/PUT/DELETE /workflows` — owner-scoped CRUD.
- `POST /webhooks/{workflowId}` — external trigger; requires `Idempotency-Key` header; kicks off async execution.

## What does **not** belong here

- Slack/email transport details — those live in `notification.sender.*`. Workflow's action executors decide *that* a Slack action ran; the *notification* module owns *sending* user-facing notifications about workflow outcomes. (If "Slack action" and "Slack notification" diverge in transport, share a small `infrastructure.slack` adapter later — don't cross-import.)
- Any reference to `User`.
