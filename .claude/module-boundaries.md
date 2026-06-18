# Module boundaries

These rules are the spine of the modular monolith. Breaking them is how a monolith silently becomes a tangled ball — flag any violation rather than working around it.

## The two hard rules

1. **No cross-module service injection.** A bean in `workflow.*` may not inject a bean from `notification.*`, `auth.*`, etc., and vice versa. Repositories are even more off-limits.
2. **No cross-module JPA relations.** Reference foreign entities by their `UUID` id. `Workflow.ownerId : UUID` — never `@ManyToOne User`. `Execution.workflowId : UUID` — never `@ManyToOne Workflow`. (Within the same module, `@ManyToOne` is fine when it pulls its weight.)

## What's allowed across modules

- **Events.** A module publishes a `record … implements DomainEvent` via `ApplicationEventPublisher`. Other modules consume it with `@TransactionalEventListener`.
- **`shared/`** types. Every module may depend on `shared.*` and `infrastructure.*`. Those two never depend on a feature module.
- **`infrastructure.security.CurrentUser`** for reading the authenticated user's UUID from any controller/service.

## Event mechanics

- Events are immutable Java `record`s implementing `com.automationhub.shared.event.DomainEvent`.
- Events carry **UUIDs only** — no entities, no large payloads. Consumers re-fetch what they need from their own repositories or call out via the event source's public API (which today means: they don't, because the source module exposes nothing public).
- Publishers call `eventPublisher.publishEvent(new WorkflowCompletedEvent(...))` from within the transaction that produced the change.
- Consumers annotate listener methods with:

  ```java
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(WorkflowCompletedEvent event) { ... }
  ```

  `AFTER_COMMIT` guarantees the producing transaction is durable before downstream side effects fire; `@Async` decouples the consumer from the producer's request thread.

## Dependency direction

```
shared          ←──── (everything depends on shared)
infrastructure  ←──── (every feature module depends on infrastructure)

auth ─────────┐
workflow ─────┼──── publish events ────▶ notification
              │                          (notification depends on no feature module)
```

`auth`, `workflow`, and `notification` are siblings. They never import each other's packages — the compiler is your enforcement mechanism. If you find yourself wanting `import com.automationhub.workflow.something` from inside `notification`, stop and design an event instead.

## Why UUIDs instead of relations

- Modules can move to separate schemas (or services) later without untangling FK graphs.
- Tests in one module don't need fixtures from another.
- Deletes don't cascade across module boundaries.
- A "missing" reference (orphaned UUID) is a tolerable state; it surfaces as a 404 at read time, not a JPA exception at write time.

## Checklist when reviewing a cross-module change

- [ ] No `@Autowired` / constructor param of a foreign module's bean.
- [ ] No `@ManyToOne` / `@OneToMany` to a foreign module's entity.
- [ ] If new info needs to flow between modules: add a `DomainEvent`, not a method call.
- [ ] The event carries only UUIDs (+ enums/timestamps), no entities.
