# Module: `workflow`

Owns workflows, their actions, executions, execution logs, the pluggable action executor pipeline, and idempotent execution. **Publishes** `WorkflowCompletedEvent` / `WorkflowFailedEvent` for `notification` (and future modules) to consume.

## Layout

```
workflow/
├── controller/    WorkflowController        (/workflows)
│                  ExecutionController       (/workflows/{id}/execute, /executions, /executions/{eid}/logs)
│                  WebhookController         (/webhooks/workflows/{id} — public, HMAC-signed)
├── service/       WorkflowService           (CRUD + webhook secret rotate/disable)
│                  ExecutionService          (sync entrypoint: ownership, idempotency, schedule async run)
│                  ExecutionRunner           (@Async runner)
│   └── action/    ActionExecutor, ActionResult, ActionExecutorRegistry,
│                  SlackActionExecutor, EmailActionExecutor, HttpActionExecutor
├── webhook/       WebhookSignatureVerifier, WebhookTriggerService,
│                  WebhookAuthenticationException
├── repository/    WorkflowRepository, ActionRepository, ExecutionRepository,
│                  ExecutionLogRepository, IdempotencyKeyRepository
├── entity/        Workflow (incl. webhookSecret), Action, ActionType,
│                  Execution, ExecutionStatus, ExecutionLog
├── dto/           CreateWorkflowRequest, ActionSpec, WorkflowResponse,
│                  ExecutionResponse, ExecutionLogResponse, WebhookCredentialsResponse
├── event/         WorkflowCompletedEvent, WorkflowFailedEvent
└── idempotency/   IdempotencyKey, IdempotencyService
```

## Entities (all extend `BaseEntity`)

- **`Workflow`** — `name : String`, `ownerId : UUID`, `webhookSecret : String` (nullable; presence ⇒ webhook trigger enabled). No `@ManyToOne User`.
- **`Action`** — `workflowId : UUID`, `type : ActionType` (`SLACK | EMAIL | HTTP`, `@Enumerated(STRING)`, column `action_type`), `config : String` (JSON blob, `TEXT` column), `order : int` (column `execution_order` — `order` is a SQL reserved word).
- **`Execution`** — `workflowId : UUID`, `status : ExecutionStatus` (`PENDING | RUNNING | COMPLETED | FAILED`).
- **`ExecutionLog`** — `executionId : UUID`, `actionOrder : int`, `status : ExecutionStatus`, `message : String`.
- **`IdempotencyKey`** (in `idempotency/`) — `key : String` (unique constraint `uk_idempotency_key` on column `key_value`), `ownerId : UUID`, `workflowId : UUID`, `executionId : UUID`.

## DTOs (records, all in `dto/`)

- **`CreateWorkflowRequest(String name, List<ActionSpec> actions)`** — `@NotBlank @Size(max=200)` name, `@NotEmpty @Valid` actions.
- **`ActionSpec(ActionType type, int order, String config)`** — `@NotNull` type, `@PositiveOrZero` order. `static from(Action)`.
- **`WorkflowResponse`** — `static from(Workflow, List<Action>)` builds the full response including ordered actions.
- **`ExecutionResponse`** — `static from(Execution)`.
- **`ExecutionLogResponse`** — `static from(ExecutionLog)`.

## Action executor pipeline (`service/action/`)

Core extension point.

- **`ActionExecutor`** (interface):
  ```java
  boolean supports(ActionType type);
  ActionResult execute(Action action);
  ```
  **Deviation note:** the original sketch had `void execute(Action)`. The actual contract returns `ActionResult(boolean success, String message)` so each executor can supply a log message for `ExecutionLog`. If you'd rather move back to `void` + custom exception, both `ExecutionRunner` and the three executors need to change in lockstep.

- **`ActionResult`** record with `ok(message)` / `failed(message)` factories.

- **`ActionExecutorRegistry`** — `@Component`. Constructor takes `List<ActionExecutor>`, builds an `EnumMap<ActionType, ActionExecutor>` (rejects duplicates with `IllegalStateException`). `resolve(ActionType)` throws if no executor is registered for the type.

- **Concrete executors** (`@Component` each):
  - `SlackActionExecutor`, `EmailActionExecutor` — log the invocation and return success. Real delivery moves to the `notification` module when its listeners are wired.
  - `HttpActionExecutor` — uses Spring `RestClient`. Config is JSON: `{"url": "...", "method": "GET|POST|...", "body": <any json>}`. Non-2xx response or `RestClientException` → `ActionResult.failed`.

**Adding a new action type** = (1) add an enum constant to `ActionType`, (2) add a new `@Component` implementing `ActionExecutor`. The registry picks it up — do not edit it.

## Service contracts

### `WorkflowService`
- `create(CreateWorkflowRequest, UUID ownerId) : WorkflowResponse` — persists the workflow + actions in one TX.
- `list(UUID ownerId, Pageable) : PageResponse<WorkflowResponse>` — owner-scoped paged list (each response is hydrated with its actions).
- `get(UUID id, UUID ownerId) : WorkflowResponse` — `ResourceNotFoundException` if missing or not owned.
- `delete(UUID id, UUID ownerId)` — deletes the workflow's actions then the workflow itself.
- `rotateWebhookSecret(UUID workflowId, UUID ownerId) : String` — `SecureRandom` 32 bytes → base64url, stored on the workflow, returned **once**. Rotating revokes the prior secret.
- `disableWebhook(UUID workflowId, UUID ownerId)` — clears the secret. Subsequent webhook fires get a generic 401.

### `ExecutionService` (sync entry point, called from the controller thread)
1. Look up workflow by `(id, ownerId)`; 404 if missing.
2. If `Idempotency-Key` is present:
   - Call `idempotencyService.findExecutionId(key)`. If a row exists, return the prior `ExecutionResponse` and **stop**.
3. Save `Execution(RUNNING)`.
4. Save the `IdempotencyKey` row (via `IdempotencyService.record`, which catches the unique-constraint violation race and returns the winner's executionId).
5. Register a `TransactionSynchronization.afterCommit` hook that calls `ExecutionRunner.run(executionId, workflowId, ownerId)`.
6. Return `ExecutionResponse(RUNNING)`.

Also:
- `listExecutions(workflowId, ownerId, Pageable) : PageResponse<ExecutionResponse>` (owner-scoped, ordered `createdAt DESC`).
- `listLogs(workflowId, executionId, ownerId) : List<ExecutionLogResponse>` (owner-scoped, ordered by `actionOrder` ASC).

### `ExecutionRunner` (`@Async("automationHubTaskExecutor") @Transactional`)
Runs in a worker thread *after* the entry-point TX commits, so the `Execution(RUNNING)` row is visible.
1. Load `Execution` and the ordered `actions`.
2. For each action: `registry.resolve(type).execute(action)`, write an `ExecutionLog` row with `actionOrder + status + message`. Any thrown exception is caught and converted to a failed `ActionResult`.
3. Stop on first failure.
4. If any step failed → `Execution.status = FAILED`, publish `WorkflowFailedEvent`. Otherwise → `COMPLETED`, publish `WorkflowCompletedEvent`.
5. Both events are published from inside the finalize TX, so `@TransactionalEventListener(AFTER_COMMIT)` consumers fire only after the status flip is durable.

## Idempotency (`idempotency/`)

- **`IdempotencyKey`** entity (see Entities above).
- **`IdempotencyService`**:
  - `findExecutionId(key) : Optional<UUID>` — plain SELECT, joins the caller's TX.
  - `record(key, ownerId, workflowId, executionId) : UUID` — `@Transactional(propagation = REQUIRES_NEW)`. Calls `saveAndFlush`. Lets `DataIntegrityViolationException` propagate; **does not** swallow it.

### Why `REQUIRES_NEW` + caller-side catch

A unique-constraint violation aborts the Postgres transaction it happens in (every subsequent statement returns `25P02 current transaction is aborted`). If `record` ran in the caller's TX, the catch block's recovery `findByKey` would fail, and the outer commit would fail too — the losing thread saw a `JpaSystemException` 500 instead of the winner's execution ID. The integration test `ExecutionServiceRaceIntegrationTest` was the one that surfaced this; do not regress.

Fix shape (live in `ExecutionService.execute`):

```java
Execution execution = executionRepository.save(...);   // queued in caller's TX
try {
    idempotencyService.record(idempotencyKey, ownerId, workflowId, execution.getId());
} catch (DataIntegrityViolationException raceLost) {
    executionRepository.delete(execution);             // clean up the orphan we just queued
    UUID priorId = idempotencyService.findExecutionId(idempotencyKey).orElseThrow(() -> raceLost);
    return ExecutionResponse.from(executionRepository.findById(priorId).orElseThrow(...));
}
```

Known small race window: the inner `REQUIRES_NEW` commits the `idempotency_keys` row before the outer TX commits the `Execution` row. A third caller racing in this exact window can read the idempotency row, then fail to find the execution. Acceptable for now — caller can retry. If this ever bites, the fix is to insert the idempotency row with `executionId = some_pregenerated_uuid` first and create the execution with that UUID after winning.

## Events (records, implement `DomainEvent`)

```java
public record WorkflowCompletedEvent(UUID workflowId, UUID executionId, UUID ownerId, Instant occurredAt)
    implements DomainEvent {}

public record WorkflowFailedEvent(UUID workflowId, UUID executionId, UUID ownerId, String reason, Instant occurredAt)
    implements DomainEvent {}
```

UUIDs + primitives only. No entities. Consumers re-fetch what they need from their own repositories.

## Endpoints

JWT-secured unless marked **public**.

| Method | Path                                                         | Status | Notes |
|--------|--------------------------------------------------------------|--------|-------|
| POST   | `/workflows`                                                 | 201    | body = `CreateWorkflowRequest` |
| GET    | `/workflows?page=N&size=M`                                   | 200    | `PageResponse<WorkflowResponse>` |
| GET    | `/workflows/{id}`                                            | 200    | 404 if not owned |
| DELETE | `/workflows/{id}`                                            | 204    | cascades actions |
| POST   | `/workflows/{id}/execute`                                    | 202    | header `Idempotency-Key` (optional but recommended) |
| GET    | `/workflows/{id}/executions?page=N&size=M`                   | 200    | `PageResponse<ExecutionResponse>`, newest first |
| GET    | `/workflows/{id}/executions/{executionId}/logs`              | 200    | `List<ExecutionLogResponse>`, ordered by `actionOrder` |
| POST   | `/workflows/{id}/webhook`                                    | 200    | rotates secret; returns `WebhookCredentialsResponse(secret, url)` **once** |
| DELETE | `/workflows/{id}/webhook`                                    | 204    | clears the secret |
| POST   | `/webhooks/workflows/{id}` **(public)**                      | 202    | headers `X-Webhook-Timestamp`, `X-Webhook-Signature: sha256=<hex>`, optional `Idempotency-Key`. Returns `ExecutionResponse`. |

## Webhook trigger (`webhook/`, `WebhookController`)

Public, unauthenticated trigger for external systems (payment providers, etc.).

- **`WebhookSignatureVerifier`** — `HMAC-SHA256(secret, timestamp + "." + rawBody)`, hex-encoded. Constant-time compare via `MessageDigest.isEqual`. Timestamp must be within ±5 minutes of server time (replay protection). Returns `boolean` only — never throws and never logs.
- **`WebhookTriggerService`** — loads `Workflow` by id, checks `webhookSecret` is non-null, verifies signature, then delegates to `ExecutionService.execute(workflowId, workflow.getOwnerId(), idempotencyKey)`. The execution runs as the workflow's owner — webhook-triggered runs are indistinguishable from JWT-triggered runs once past the controller.
- **`WebhookAuthenticationException`** — thrown for ALL failure modes (unknown workflow, missing secret, bad timestamp, bad signature). Mapped to a single generic 401 `{"message":"Unauthorized"}` in `GlobalExceptionHandler` so callers can't tell which check failed.
- **Raw body handling** — controller accepts `@RequestBody(required = false) String body`. Never let Jackson parse before HMAC verification, or the bytes you HMAC won't match the bytes the caller signed.

The webhook **does not** pipe its payload to action executors — actions use their stored config. Payload-aware actions are a separate feature.

## What does **not** belong here

- Slack/email transport details — those live in `notification.sender.*` (when implemented). Workflow's action executors decide *that* a Slack action ran; the *notification* module owns *sending* user-facing notifications about workflow outcomes.
- Any reference to `User`.
