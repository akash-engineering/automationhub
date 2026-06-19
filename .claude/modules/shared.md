# Module: `shared`

Cross-cutting primitives. No business logic. Everything here is depended on by every feature module; nothing here depends on a feature module.

## Sub-packages

### `shared/event`

- **`DomainEvent`** — empty marker interface. All cross-module events implement it.
- **`EventType`** — enum tag used when events need to be classified at a glance (currently `WORKFLOW_COMPLETED`, `WORKFLOW_FAILED`). Adding a new event type → add a value here.

### `shared/exception`

- **`ApiError`** — response record: `Instant timestamp, int status, String message, String path`. The wire format for **all** error responses.
- **`ResourceNotFoundException extends RuntimeException`** — throw for 404s.
- **`EmailAlreadyExistsException extends RuntimeException`** — thrown by `auth` on duplicate registration. Mapped to 409.
- **`InvalidCredentialsException extends RuntimeException`** — thrown by `auth` on bad email or bad password. Message is the generic literal `"Invalid credentials"` — **do not** vary it by field, or you leak which side was wrong. Mapped to 401.
- **`GlobalExceptionHandler`** — `@RestControllerAdvice`. Current mappings:
  - `ResourceNotFoundException` → 404
  - `EmailAlreadyExistsException` → 409
  - `InvalidCredentialsException` → 401 (message kept generic — see auth rule)
  - `WebhookAuthenticationException` → 401 with body `{"message":"Unauthorized"}`. Used for ALL webhook auth failures (bad signature, missing secret, expired timestamp, unknown workflow) — the handler overwrites the exception's message to a single literal so callers can't tell which check failed.
  - `MethodArgumentNotValidException` → 400 (joins all field errors with `;`)
  - fallback `Exception` → 500

  All responses use `ApiError`. Extend this when adding new exception types; keep mappings centralized here.

### `shared/web`

- **`PageResponse<T>`** — record wrapper for paginated responses: `List<T> content, int page, int size, long totalElements, int totalPages`. Built inline today from `Page<T>` — add a `static from(Page)` factory when the pattern repeats often enough to feel boilerplatey.
- **`MdcCorrelationFilter`** (`OncePerRequestFilter`, highest precedence) — puts a fresh UUID into SLF4J MDC under key `correlationId` per request. Don't replace or remove this — every log line in the request flow inherits the id automatically.

## Adding things here

Only add to `shared` when **two or more modules** need the thing and it has no business meaning of its own. If only one module uses it, keep it in that module. If it carries domain semantics (e.g., a `Workflow`-shaped concept), it does not belong in `shared`.
