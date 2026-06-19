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
docker compose up --build -d   # builds the app image, starts Postgres + app
docker compose logs -f app     # tail app logs
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

## Endpoint quick reference

| Method | Path                                                         | Auth   |
|--------|--------------------------------------------------------------|--------|
| POST   | `/auth/register`                                             | public |
| POST   | `/auth/login`                                                | public |
| GET    | `/auth/me`                                                   | bearer |
| POST   | `/workflows`                                                 | bearer |
| GET    | `/workflows?page=&size=`                                     | bearer |
| GET    | `/workflows/{id}`                                            | bearer |
| DELETE | `/workflows/{id}`                                            | bearer |
| POST   | `/workflows/{id}/execute`                                    | bearer + `Idempotency-Key` header |
| GET    | `/workflows/{id}/executions`                                 | bearer |
| GET    | `/workflows/{id}/executions/{eid}/logs`                      | bearer |
| POST   | `/workflows/{id}/webhook`                                    | bearer — rotates the webhook secret, returns it **once** |
| DELETE | `/workflows/{id}/webhook`                                    | bearer — disables the webhook |
| POST   | `/webhooks/workflows/{id}`                                   | **public**, HMAC-signed (`X-Webhook-Timestamp`, `X-Webhook-Signature: sha256=<hex>`) |
| GET    | `/notifications/executions/{executionId}`                    | bearer |

## End-to-end smoke test

```bash
EMAIL="smoke-$(date +%s)@example.com"
PASS="StrongPass123!"

curl -sf -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}"

TOKEN=$(curl -sf -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" \
  | python -c "import sys,json; print(json.load(sys.stdin)['token'])")

WF=$(curl -sf -X POST http://localhost:8080/workflows \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo","actions":[{"type":"HTTP","order":0,"config":"{\"url\":\"https://httpbin.org/post\",\"method\":\"POST\",\"body\":{\"hi\":\"there\"}}"}]}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['id'])")

KEY="run-$(date +%s)"
curl -sf -X POST http://localhost:8080/workflows/$WF/execute \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY"

sleep 5
curl -sf -H "Authorization: Bearer $TOKEN" http://localhost:8080/workflows/$WF/executions
```

Re-running the `POST .../execute` with the same `Idempotency-Key` returns the original execution and does not create a new run.

## Webhook smoke test

```bash
# (assumes TOKEN + WF from the previous block)
ENABLE=$(curl -sf -X POST http://localhost:8080/workflows/$WF/webhook -H "Authorization: Bearer $TOKEN")
SECRET=$(echo "$ENABLE" | python -c "import sys,json;print(json.load(sys.stdin)['secret'])")

TS=$(date +%s); BODY='{"event":"invoice.paid"}'
SIG=$(printf "%s.%s" "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -r | cut -d' ' -f1)

curl -sf -X POST http://localhost:8080/webhooks/workflows/$WF \
  -H "X-Webhook-Timestamp: $TS" -H "X-Webhook-Signature: sha256=$SIG" \
  -H "Idempotency-Key: hook-001" -H "Content-Type: application/json" \
  -d "$BODY"
```

The HMAC covers `timestamp + "." + rawBody`. Replays with the same `Idempotency-Key` (and a fresh signature, since the timestamp changes) return the original `executionId`. Any signature/timestamp/secret failure → generic 401 `{"message":"Unauthorized"}`.

## Test conventions

- Run with `mvn test`.
- Unit tests: plain JUnit 5 + Mockito + AssertJ. No Spring context. Examples: `ActionExecutorRegistryTest`, `ExecutionServiceIdempotencyTest`.
- HTTP-touching unit tests: `okhttp3:mockwebserver` (already a test dep). Never call real external URLs from tests.
- Slice tests when only one layer is under test: `@WebMvcTest`, `@DataJpaTest`, `@JsonTest`.
- Integration tests that need real Postgres: extend `com.automationhub.testsupport.PostgresTestBase`. That base class spins up a singleton `PostgreSQLContainer("postgres:16-alpine")` (shared across test classes in the same JVM) and wires `spring.datasource.*` via `@DynamicPropertySource`. It also forces `spring.jpa.hibernate.ddl-auto=create-drop` so each fresh JVM gets a clean schema.
- Integration tests must clear tables in `@BeforeEach` (Spring's auto-rollback doesn't apply when the service under test opens its own TX). Use `repository.deleteAllInBatch()` for speed.
- Async + `AFTER_COMMIT` listeners run in the real executor pool during tests — use `org.awaitility.Awaitility` to wait for terminal state instead of `Thread.sleep`.
- Test class names mirror the type under test with `Test` for unit, `IntegrationTest` for Testcontainers-backed.
