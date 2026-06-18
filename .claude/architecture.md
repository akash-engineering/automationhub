# Architecture

## Style

Modular monolith. One deployable, internally split by **business module**. Each module is a top-level package under `com.automationhub` and owns its controllers, services, repositories, entities, DTOs, and (where relevant) events. Modules talk to each other **only via Spring `ApplicationEvent`s** — never by injecting another module's service.

## Package layout

```
com.automationhub
├── AutomationHubApplication           — @SpringBootApplication, main()
│
├── shared/                            — cross-cutting primitives, no business logic
│   ├── event/                         — DomainEvent (marker), EventType
│   ├── exception/                     — ApiError, ResourceNotFoundException, GlobalExceptionHandler
│   └── web/                           — PageResponse<T>, MdcCorrelationFilter
│
├── infrastructure/                    — framework wiring, no business logic
│   ├── config/                        — AsyncConfig, OpenApiConfig, JacksonConfig
│   ├── security/                      — SecurityConfig, JwtService, JwtAuthFilter, CurrentUser
│   └── persistence/                   — BaseEntity, JpaAuditingConfig
│
├── auth/                              — users, registration, login, token issuance
│   ├── controller/ service/ repository/ entity/ dto/
│
├── workflow/                          — workflows, actions, executions, idempotency
│   ├── controller/ service/ repository/ entity/ dto/ event/ idempotency/
│   └── service/action/                — pluggable ActionExecutor implementations
│
└── notification/                      — downstream consumer of workflow events (NOT YET IMPLEMENTED)
    └── listener/ service/ sender/ dto/  (planned)
```

## Future modules (not yet created)

`document/`, `payment/`, `sync/` will be added later. Do **not** scaffold them preemptively.

## Layering inside a module

Standard top-to-bottom flow per module:

```
controller  →  service  →  repository  →  entity
                  │
                  └─ publishes events for other modules
```

- **Controllers** are thin: deserialize, validate, delegate, serialize. No DB access, no executor logic.
- **Services** hold transactions (`@Transactional` at the service method, not the repository).
- **Repositories** are Spring Data interfaces. Custom queries via `@Query` or `JpaSpecificationExecutor`.
- **Entities** never leak past the service layer — services map entity ↔ DTO.

## Async boundary

Long-running work (workflow execution, notification dispatch) runs on the named executor `automationHubTaskExecutor` via `@Async`. Sync HTTP handlers must return promptly; queue the work and respond.

## Event flow at a glance

```
workflow.ExecutionRunner            ──publish──▶  WorkflowCompletedEvent / WorkflowFailedEvent
  (inside the finalize TX)                              │
                                                       │ @TransactionalEventListener(AFTER_COMMIT) + @Async
                                                       ▼
                                          notification.WorkflowEventListener   (not yet implemented)
                                                       │
                                                       ▼
                                          notification.NotificationService
                                                       │
                                                       ▼
                                          notification.sender.{Slack,Email}Sender
```

`ExecutionRunner` lives on `automationHubTaskExecutor`; events are published inside the transaction that flips status to `COMPLETED`/`FAILED`, so `AFTER_COMMIT` consumers fire only once the state change is durable.

See `.claude/module-boundaries.md` for the rules that enforce this.
