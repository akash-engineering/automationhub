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

- **`SecurityConfig`**: stateless filter chain. `permitAll` for:
  - `POST /auth/register`, `POST /auth/login`
  - `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`
  - `/actuator/health`

  Everything else (including `GET /auth/me`, all `/workflows*`) requires authentication. CSRF disabled (stateless API). `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`.

  Missing/invalid token → 401 via `exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`. Without this, Spring Security 6 defaults to 403 for missing auth, which we don't want.

- **`JwtService`**: HMAC-SHA signing key built from `automationhub.jwt.secret` (≥32 UTF-8 bytes). Uses jjwt 0.12.x API (`Jwts.builder().subject().claims().signWith()` / `Jwts.parser().verifyWith().parseSignedClaims()`).
  - `generateToken(UUID userId, String email, Role role) : String` — sub = userId, claims `email` + `role`.
  - `validateToken(String) : boolean`
  - `extractUserId(String) : UUID`, `extractEmail(String) : String`, `extractRole(String) : Role`

- **`JwtAuthFilter`** (`OncePerRequestFilter`, `@Component`): if the `Authorization` header starts with `Bearer ` and no auth is already set, validate the token; on success put a `UsernamePasswordAuthenticationToken` into the context with the user UUID as principal and `ROLE_<role>` as the authority. Invalid token = silently skip (the entry point returns 401 downstream).

- **`CurrentUser`** (`@Component`): reads the principal from `SecurityContextHolder` and exposes it as `UUID`.
  - `id() : Optional<UUID>` — `Optional.empty()` if unauthenticated.
  - `requireId() : UUID` — throws `IllegalStateException` if absent (use from controllers behind `authenticated()`).

- **`PasswordEncoder`** bean: BCrypt, declared by `SecurityConfig`. Inject from `auth`; don't new up encoders elsewhere.

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

| Key                            | Env var          | Notes                                   |
|--------------------------------|------------------|-----------------------------------------|
| `spring.datasource.url`        | `DB_URL`         | jdbc URL                                |
| `spring.datasource.username`   | `DB_USER`        |                                         |
| `spring.datasource.password`   | `DB_PASSWORD`    |                                         |
| `automationhub.jwt.secret`     | `JWT_SECRET`     | ≥32 bytes UTF-8                         |
| `automationhub.jwt.expiration` | `JWT_EXPIRATION` | milliseconds                            |

All have safe **local** defaults; production must override. Never hard-code secrets. `.env` is git-ignored; `.env.example` ships with placeholder values.
