# Infrastructure

Framework wiring lives under `com.automationhub.infrastructure.*`. No business logic here.

## Persistence — `infrastructure.persistence`

- **`BaseEntity`** (`@MappedSuperclass`, `AuditingEntityListener`): provides `UUID id` (auto-generated, `updatable = false`), `Instant createdAt` (`@CreatedDate`), `Instant updatedAt` (`@LastModifiedDate`). Every entity extends this.
- **`JpaAuditingConfig`**: enables auditing globally (`@EnableJpaAuditing`).
- Hibernate config in `application.yml`:
  - `spring.jpa.hibernate.ddl-auto: update` for now. Switch to Flyway/Liquibase before any non-trivial schema change.
  - `spring.jpa.open-in-view: false` — lazy loading outside services is a bug, surface it loudly.
- Transactions belong on **service methods** (`@Transactional`), not repositories.

## Security — `infrastructure.security`

- **`SecurityConfig`**: stateless filter chain. `permitAll` for `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`. Everything else `authenticated()`. CSRF disabled (stateless API). `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`.
- **`JwtService`**: HMAC-SHA signing key built from `automationhub.jwt.secret` (≥32 bytes; UTF-8 bytes of a base64-ish random string is fine). `generateToken(subject)`, `validateToken(token)`, `extractSubject(token)`. Uses jjwt 0.12.x API (`Jwts.builder()`, `Jwts.parser().verifyWith(...)`).
- **`JwtAuthFilter`** (`OncePerRequestFilter`): when filled in, extract bearer token, validate, set `UsernamePasswordAuthenticationToken` with the user UUID (string) as principal. Current stub just delegates — implement before any non-`/auth/**` endpoint goes live.
- **`CurrentUser`**: reads the principal from `SecurityContextHolder` and returns it as `UUID`. Inject into services/controllers that need the caller's id.
- **`PasswordEncoder`** bean: BCrypt. Inject this from `auth`; do not new up encoders elsewhere.

## Async — `infrastructure.config.AsyncConfig`

- `@EnableAsync`, single named executor bean `automationHubTaskExecutor` (`ThreadPoolTaskExecutor`, core 4 / max 16 / queue 100, thread prefix `automationhub-async-`).
- `@Async` methods (workflow execution, notification listeners) should specify the bean name: `@Async("automationHubTaskExecutor")` — keeps it discoverable when more executors land.

## OpenAPI — `infrastructure.config.OpenApiConfig`

- One `OpenAPI` bean titled **"AutomationHub API"**.
- Per-endpoint docs via springdoc annotations (`@Operation`, `@ApiResponses`) at controller level when the contract is non-obvious. Don't decorate trivial CRUD just to decorate.
- Swagger UI at `/swagger-ui.html`, JSON at `/v3/api-docs`.

## Jackson — `infrastructure.config.JacksonConfig`

- `JavaTimeModule` registered; `WRITE_DATES_AS_TIMESTAMPS` disabled — `Instant` serializes as ISO-8601 strings.
- Both an `@Primary ObjectMapper` bean and a `Jackson2ObjectMapperBuilderCustomizer` are exposed so the same settings apply whether code asks for an `ObjectMapper` directly or uses Spring's auto-built one.

## Application properties

Everything secret/environmental lives in env vars consumed by `application.yml`:

| Key                          | Env var          | Notes                                   |
|------------------------------|------------------|-----------------------------------------|
| `spring.datasource.url`      | `DB_URL`         | jdbc URL                                |
| `spring.datasource.username` | `DB_USER`        |                                         |
| `spring.datasource.password` | `DB_PASSWORD`    |                                         |
| `automationhub.jwt.secret`   | `JWT_SECRET`     | ≥32 bytes UTF-8                         |
| `automationhub.jwt.expiration` | `JWT_EXPIRATION` | milliseconds                            |

All have safe **local** defaults; production must override. Never hard-code secrets.
