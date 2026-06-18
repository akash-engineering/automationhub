# Module: `shared`

Cross-cutting primitives. No business logic. Everything here is depended on by every feature module; nothing here depends on a feature module.

## Sub-packages

### `shared/event`

- **`DomainEvent`** — empty marker interface. All cross-module events implement it.
- **`EventType`** — enum tag used when events need to be classified at a glance (currently `WORKFLOW_COMPLETED`, `WORKFLOW_FAILED`). Adding a new event type → add a value here.

### `shared/exception`

- **`ApiError`** — response record: `Instant timestamp, int status, String message, String path`. The wire format for **all** error responses.
- **`ResourceNotFoundException extends RuntimeException`** — throw this for 404s. Module-specific 404s should still use this class (with a descriptive message) unless they need a distinct HTTP shape.
- **`GlobalExceptionHandler`** — `@RestControllerAdvice`. Maps `ResourceNotFoundException` → 404 + `ApiError`, fallback `Exception` → 500 + `ApiError`. Extend this when adding new exception types; keep mappings centralized here.

### `shared/web`

- **`PageResponse<T>`** — record wrapper for paginated responses: `List<T> content, int page, int size, long totalElements, int totalPages`. Build with `PageResponse.from(Page<T>)` if/when that factory is added.
- **`MdcCorrelationFilter`** (`OncePerRequestFilter`, highest precedence) — puts a fresh UUID into SLF4J MDC under key `correlationId` per request. Don't replace or remove this — every log line in the request flow inherits the id automatically.

## Adding things here

Only add to `shared` when **two or more modules** need the thing and it has no business meaning of its own. If only one module uses it, keep it in that module. If it carries domain semantics (e.g., a `Workflow`-shaped concept), it does not belong in `shared`.
