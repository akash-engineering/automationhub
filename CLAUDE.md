# AutomationHub

Spring Boot 3 modular monolith. Java 21, Maven, PostgreSQL, JPA, Spring Security + JWT, springdoc-openapi, Lombok, Docker Compose.

Base package: `com.automationhub`. Modules: `shared`, `infrastructure`, `auth`, `workflow`, `notification`. Future (do not create yet): `document`, `payment`, `sync`.

## Hard rules (always apply)

- **Constructor injection only.** No `@Autowired` on fields. No setter injection. `final` fields + single constructor.
- **All DTOs are Java `record`s.** No Lombok DTOs. Responses expose a `static from(...)` factory where mapping is non-trivial.
- **Cross-module communication is event-driven.** A module's service must never inject another module's service. Use `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT) @Async` consumers.
- **Cross-module references use UUIDs, not JPA relations.** `Workflow.ownerId` is `UUID`, never `@ManyToOne User`. Same for `Execution.workflowId`, `ExecutionLog.executionId`, etc. This is intentional — don't "fix" it.
- **Entities extend `BaseEntity`** for `UUID id`, `createdAt`, `updatedAt`, and auditing.
- **No business logic in controllers.** Controllers parse/validate input, delegate to services, shape responses.
- **Stubs may `throw new UnsupportedOperationException(...)`** while the skeleton is being filled in — they must still compile and the app must start.

## Routing — load on demand

Each file below is small and self-contained. Read only what's relevant to the task:

- **Architecture & package layout** → `.claude/architecture.md`
- **Coding conventions (records, Lombok, naming, validation, exceptions)** → `.claude/conventions.md`
- **Module boundary rules & event flow** → `.claude/module-boundaries.md`
- **Infrastructure (persistence, security, async, OpenAPI, Jackson)** → `.claude/infrastructure.md`
- **Build / run / env vars / URLs** → `.claude/build-and-run.md`

Feature modules:

- **`shared`** primitives (events, exceptions, web utils) → `.claude/modules/shared.md`
- **`auth`** (registration, login, JWT) → `.claude/modules/auth.md`
- **`workflow`** (CRUD, executor pattern, idempotency, events) → `.claude/modules/workflow.md`
- **`notification`** (event listeners, senders) → `.claude/modules/notification.md`

## When in doubt

If a rule above conflicts with what you find in the code, the rule wins — flag the divergence rather than silently aligning to the code. If a rule isn't covered here, ask before inventing one; project conventions are still solidifying.
