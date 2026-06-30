# Module: `shared`

Cross-cutting primitives. No business logic.

## Contents

- `shared/event/DomainEvent` — marker interface for all cross-module events.
- `shared/event/EventType` — vestigial enum, currently unreferenced. Safe to delete when convenient.
- `shared/exception/ApiError` — wire format for all error responses.
- `shared/exception/ResourceNotFoundException` — 404.
- `shared/exception/EmailAlreadyExistsException` — 409 (thrown by `auth`).
- `shared/exception/InvalidCredentialsException` — 401 generic (thrown by `auth`).
- `shared/exception/GlobalExceptionHandler` — `@RestControllerAdvice` mapping the above plus `WebhookAuthenticationException` (→ generic 401), `StripeNotConfiguredException` (→ 503), `StripeException` (→ 502), `MethodArgumentNotValidException` (→ 400), fallback `Exception` (→ 500).
- `shared/web/PageResponse<T>` — paginated response wrapper.
- `shared/web/MdcCorrelationFilter` — `OncePerRequestFilter`, highest precedence; sets `correlationId` MDC key per request.

## Rule

Only add here when two or more modules need it and it has no business meaning.
