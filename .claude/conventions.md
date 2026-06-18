# Coding conventions

## Java & language

- Target **Java 21**. Prefer records, pattern matching, `var` for obvious local types, text blocks for SQL/JSON literals.
- One public type per file. File name matches type name.
- No wildcard imports.

## DTOs

- **Always `record`s.** Request DTOs in `*/dto/`, never reused as entity types.
- Validation annotations (`@NotBlank`, `@Email`, `@Size`, …) go on record components.
- Response records expose `static from(Entity e)` (and `static from(Page<Entity> p)` where paginated) for non-trivial mapping. Trivial 1:1 records may skip the factory.

## Entities

- Extend `BaseEntity` (gives `UUID id`, `createdAt`, `updatedAt`, auditing). Never declare these fields yourself.
- Lombok on entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` is fine. Do **not** add `@Data` (breaks JPA equality).
- Cross-module references are `UUID` columns, never `@ManyToOne` across modules. Within a module, JPA relations are allowed if genuinely useful — default is still UUID + lookup-by-id.
- `@Enumerated(EnumType.STRING)` for every enum column.
- Use `@Table(name = "snake_plural")` (`users`, `workflows`, `executions`, …).

## Dependency injection

- **Constructor injection only.** No `@Autowired` on fields or setters.
- Single constructor → Spring auto-wires it; no annotation needed.
- Fields are `private final`.
- Lombok `@RequiredArgsConstructor` is **allowed** on services/components to cut boilerplate, but explicit constructors are also fine and preferred when there's any custom init logic.

## Exceptions

- Throw `ResourceNotFoundException` for 404s. Add module-local exceptions when you need a distinct HTTP mapping; wire them up in `GlobalExceptionHandler`.
- Never catch `Exception` and swallow it. Either handle a specific subtype or let `GlobalExceptionHandler` map it.
- Service methods do not return `Optional` for "not found" — they throw. Repositories may return `Optional`.

## Validation

- `@Valid` on controller request bodies. Validation errors propagate to `GlobalExceptionHandler` (extend it if you need a custom shape for `MethodArgumentNotValidException`).

## Logging

- SLF4J via Lombok `@Slf4j`. Use parameterized messages: `log.info("created workflow {} for {}", id, ownerId)`. No string concatenation.
- The `correlationId` MDC key is populated by `MdcCorrelationFilter`. Don't set it manually — just log normally and it appears in the pattern.

## Naming

- Packages lowercase. Classes `UpperCamel`. Methods/fields `lowerCamel`. Constants `UPPER_SNAKE`.
- Controllers: `XxxController`, request mapping at class level (`/workflows`), nothing duplicated in method paths if the verb alone suffices.
- Services: `XxxService`. Event classes: `XxxEvent` (past tense for things that happened — `WorkflowCompletedEvent`).
- Repository methods: Spring Data naming (`findByEmail`, `existsByKey`).

## Comments

- Default to no comments. The code and identifiers should explain the *what*. Comment only the *why* when it's non-obvious — a constraint, a workaround, a deliberate divergence from convention.
