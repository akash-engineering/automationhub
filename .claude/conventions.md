# Conventions

## Java

- Java 21. Records, pattern matching, `var` for obvious locals, text blocks for SQL/JSON literals.
- One public type per file. No wildcard imports.

## DTOs

- Always `record`s. Validation annotations on components (`@NotBlank`, `@Email`, `@Size`, `@NotNull`).
- Response records expose `static from(Entity)` for non-trivial mapping; trivial 1:1 records may skip the factory.

## Entities

- Extend `BaseEntity` for `id` / `createdAt` / `updatedAt`. Don't redeclare these.
- Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. Never `@Data` (breaks JPA equality).
- Cross-module refs: `UUID` columns only.
- `@Enumerated(EnumType.STRING)` for every enum column.
- `@Table(name = "snake_plural")`.

## DI

- Constructor injection only. `private final` fields. Single explicit constructor. **No** `@Autowired`, no setter injection, no `@RequiredArgsConstructor` — every component in this codebase declares its constructor explicitly.

## Exceptions

- Throw `ResourceNotFoundException` for 404. Module-specific exceptions wire up in `GlobalExceptionHandler`.
- Services throw; repositories may return `Optional`.
- Never catch `Exception` and swallow it — let `GlobalExceptionHandler` map.

## External integrations

- Read config via `@Value("${prop:}")` with blank default.
- Blank config → warn (`log.warn(...)`) + fall back to log-only / simulated success / typed exception. Never silently fake success without logging.
- Live SDK calls behind a guard method (e.g., `StripeClient.requireConfigured()`) that throws a typed exception mapped in `GlobalExceptionHandler`.

## Idempotency

- Webhook entry points must be idempotent. Pattern: unique constraint on an idempotency column + `REQUIRES_NEW` insert + caller-side catch of `DataIntegrityViolationException`. See `workflow.IdempotencyService` and `payment.StripeWebhookService`.

## Other

- `@Valid` on controller request bodies.
- SLF4J via `LoggerFactory.getLogger(...)`. Parameterized messages, no string concat. `correlationId` MDC key is set by `MdcCorrelationFilter` — don't manage manually.
- No comments unless the *why* is non-obvious.
