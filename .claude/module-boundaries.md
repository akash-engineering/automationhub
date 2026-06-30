# Module boundaries

## Hard rules

- **No cross-module service injection.** A bean in `workflow.*` may not inject a bean from `notification.*`, `payment.*`, `auth.*`, etc.
- **No cross-module JPA relations.** References by `UUID` columns (e.g., `Workflow.ownerId : UUID`, never `@ManyToOne User`).
- **Events only between feature modules.** Publish a `record implements DomainEvent` via `ApplicationEventPublisher`; consume with `@TransactionalEventListener(AFTER_COMMIT) @Async`.
- **Events carry UUIDs + primitives + enums only.** No entities, no large payloads. Consumers re-fetch from their own repos.

## Allowed across modules

- `shared.*` and `infrastructure.*` — depended on by every module; never depend back.
- `infrastructure.security.CurrentUser` — read the authenticated UUID anywhere.
- Cross-module event-class imports (e.g., `notification.listener.PaymentEventListener` importing `payment.event.PaymentSucceededEvent`). The event class itself is the contract.

## Documented exception

`document.service.action.DocumentActionExecutor` imports from `workflow`:
- `workflow.service.action.ActionExecutor` — implements the SPI to register a new action type.
- `workflow.repository.WorkflowRepository` — read-only lookup to resolve `ownerId` from `Action.workflowId`.

Rationale + alternatives in `.claude/modules/document.md`.

## Adding cross-module communication

New info needs to flow between modules → add a `DomainEvent`. Never a service call. Never a foreign repository injection without a written justification.

## Why UUIDs instead of relations

Modules can split into separate schemas (or services) later without untangling FK graphs. Tests in one module don't need fixtures from another. A missing reference surfaces as a 404 at read time, not a JPA exception at write time.
