# Architecture

AutomationHub is a modular monolith. One Spring Boot deployable, split internally by business module. Modules communicate **only via Spring `ApplicationEvent`s** — never by injecting another module's service — with one narrow exception called out below.

## Module graph

```mermaid
flowchart TB
    classDef foundation fill:#eef,stroke:#557,color:#113
    classDef feature fill:#efe,stroke:#575,color:#131
    classDef future fill:#f8f8f8,stroke:#aaa,color:#666,stroke-dasharray:4 4

    subgraph Foundation
        shared[shared]
        infra[infrastructure]
    end

    auth[auth]
    workflow[workflow]
    notification[notification]
    document[document]

    payment[payment]:::future
    sync[sync]:::future

    shared:::foundation
    infra:::foundation
    auth:::feature
    workflow:::feature
    notification:::feature
    document:::feature

    auth --> shared
    auth --> infra
    workflow --> shared
    workflow --> infra
    notification --> shared
    notification --> infra
    document --> shared
    document --> infra

    workflow -. "publishes WorkflowCompletedEvent / WorkflowFailedEvent" .-> notification
    workflow -. "publishes WorkflowCompletedEvent" .-> document

    document == "implements ActionExecutor SPI<br/>+ reads WorkflowRepository for ownerId" ==> workflow
```

**Reading the diagram:**

- **Solid arrow** → compile-time dependency (`module A → module B` means A imports types from B).
- **Dotted arrow** → runtime event flow (publisher to consumer), wired via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`.
- **Bold solid arrow** → the one strategic-pattern dependency: `document` implements the `ActionExecutor` SPI defined in `workflow`, and reads `WorkflowRepository` to resolve `ownerId` from an `Action.workflowId`. This is the only cross-module compile dep between feature modules; documented in [`.claude/modules/document.md`](../../.claude/modules/document.md).
- **Dashed boxes** are not yet implemented — do not scaffold preemptively.

## Runtime event flow

What happens when a workflow execution fires, including both webhook and JWT entry paths, the async runner, and the two AFTER_COMMIT consumers.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Ctl as WorkflowController / WebhookController
    participant Svc as ExecutionService<br/>(@Transactional)
    participant DB as Postgres
    participant Run as ExecutionRunner<br/>(@Async on automationHubTaskExecutor)
    participant Bus as Spring event bus
    participant NL as notification.<br/>WorkflowEventListener
    participant DL as document.<br/>WorkflowCompletedListener
    participant NS as NotificationService
    participant DS as DocumentService
    participant ST as StorageService<br/>(Local / S3)

    Client->>Ctl: POST /workflows/{id}/execute<br/>or /webhooks/workflows/{id} (HMAC)
    Ctl->>Svc: execute(workflowId, ownerId, idempotencyKey)
    Svc->>DB: save Execution(RUNNING)
    Svc->>DB: insert idempotency_key (REQUIRES_NEW)
    Note over Svc,DB: TX commit
    Svc-->>Client: 202 ExecutionResponse(RUNNING)
    Svc->>Run: afterCommit hook fires
    activate Run

    loop for each action in order
        Run->>Run: resolve ActionExecutor for type<br/>(SLACK / EMAIL / HTTP / DOCUMENT)
        Run->>DB: insert ExecutionLog row
        alt action fails
            Note over Run: stop on first failure
        end
    end

    Run->>DB: update Execution → COMPLETED / FAILED
    Run->>Bus: publish WorkflowCompletedEvent / WorkflowFailedEvent
    deactivate Run

    par AFTER_COMMIT + @Async (notification)
        Bus->>NL: onCompleted / onFailed
        NL->>NS: send(NotificationRequest) per channel
        NS->>NS: SlackSender / EmailSender (log-only today)
        NS->>DB: insert NotificationDelivery (SENT / FAILED)
    and AFTER_COMMIT + @Async (document, gated)
        Bus->>DL: onCompleted (if auto-summary enabled)
        DL->>DS: generate summary PDF
        DS->>ST: put(key, bytes, "application/pdf")
        DS->>DB: insert Document row
    end
```

**Key properties to preserve:**

- `ExecutionService.execute` returns `202` before any action runs — the runner is `@Async` on `automationHubTaskExecutor` and only fires `afterCommit`, so the `Execution(RUNNING)` row is visible to anyone polling.
- Events are published from inside the runner's finalize TX, so `AFTER_COMMIT` consumers only see durable terminal states.
- The two consumers (`notification`, `document`) are isolated: a sender failure or PDF render failure never propagates back into the workflow execution.
- The idempotency insert uses `REQUIRES_NEW` so a unique-constraint race rolls back only that nested TX, not the caller's. See [`.claude/modules/workflow.md`](../../.claude/modules/workflow.md#idempotency-idempotency) for the full rationale.
