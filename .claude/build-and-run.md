# Build & run

## Toolchain

- **JDK 21** (Temurin recommended).
- **Maven 3.9+** (project uses the Spring Boot parent — no wrapper checked in yet).
- **Docker + Docker Compose** for the local Postgres + app combo.

If your shell's `java` is older, point Maven at 21 just for the call:

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot" \
PATH="$JAVA_HOME/bin:$PATH" \
mvn -q -DskipTests compile
```

## Common commands

```bash
mvn -q -DskipTests compile     # fast compile-only sanity check
mvn clean package              # full build (runs tests)
mvn spring-boot:run            # run app against whatever DB env vars are exported
```

Docker:

```bash
cp .env.example .env           # then edit secrets
docker compose up --build      # builds the app image, starts Postgres + app
docker compose down            # stop; add -v to wipe the postgres-data volume
```

## Environment variables (required by app + compose)

| Var             | Example                                              |
|-----------------|------------------------------------------------------|
| `DB_URL`        | `jdbc:postgresql://postgres:5432/automationhub`      |
| `DB_USER`       | `automationhub`                                      |
| `DB_PASSWORD`   | (any non-empty)                                      |
| `JWT_SECRET`    | random ≥32-byte string                               |
| `JWT_EXPIRATION`| `3600000` (ms)                                       |

Inside compose, `DB_URL` uses host `postgres` (the service name). For local-without-docker runs against a Postgres on the host, switch to `localhost:5432`.

## URLs

- App: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Actuator health: <http://localhost:8080/actuator/health>

## Test conventions

- Spring Boot test starters already on the classpath (`spring-boot-starter-test`, `spring-security-test`).
- Unit tests: plain JUnit 5 + Mockito. No Spring context.
- Slice tests: `@WebMvcTest`, `@DataJpaTest`, `@JsonTest` — prefer these over full `@SpringBootTest` when a single layer is under test.
- Integration tests that need real Postgres: Testcontainers (not yet wired up — add the dependency when the first integration test lands).
- Test class names mirror the type under test with a `Test` suffix; one behavior per `@Test` method.
