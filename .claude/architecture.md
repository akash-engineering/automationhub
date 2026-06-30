# Architecture

Modular monolith. One Spring Boot deployable. Each module under `com.automationhub.*` owns its controllers / services / repositories / entities / DTOs / events.

For the rendered module graph + sequence diagram, see `docs/architecture/modules.md`.

## Layering inside a module

`controller → service → repository → entity`. Controllers stay thin (parse / validate / delegate / shape). `@Transactional` lives on service methods.

## Async boundary

Long-running work (workflow execution, listener dispatch) runs on `automationHubTaskExecutor` via `@Async`. Sync HTTP handlers return promptly.

## Event flow

Events are published from inside the producing TX. Consumers use `@TransactionalEventListener(AFTER_COMMIT) @Async("automationHubTaskExecutor")` so they fire only after the producer's state change is durable.

- `workflow.ExecutionRunner` publishes `WorkflowCompletedEvent` / `WorkflowFailedEvent`
  - → `notification.WorkflowEventListener` → senders + `NotificationDelivery`
  - → `document.WorkflowCompletedListener` (gated by `automationhub.document.auto-summary.enabled`, default off) → PDF summary
- `payment.StripeWebhookService` publishes `PaymentSucceededEvent` on `invoice.payment_succeeded`
  - → `notification.PaymentEventListener` → senders + `NotificationDelivery`

Events carry UUIDs + primitives + enums only. Consumers re-fetch from their own repos.

## Dependency direction

`shared` and `infrastructure` are foundation — every feature module depends on them, never the reverse. Feature modules communicate via events only, with one documented exception: `document.DocumentActionExecutor` imports `workflow.service.action.ActionExecutor` (SPI) and `workflow.repository.WorkflowRepository` (read-only owner lookup). See `.claude/module-boundaries.md`.
