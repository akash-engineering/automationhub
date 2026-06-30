# Workflow webhook trigger

Public, unauthenticated entry point for external systems to fire a workflow. HMAC-signed.

## Endpoint

`POST /webhooks/workflows/{id}` — public via `SecurityConfig` (`permitAll` on `/webhooks/**`).

Headers:
- `X-Webhook-Timestamp` — Unix seconds
- `X-Webhook-Signature: sha256=<hex>` — HMAC-SHA256 over `timestamp + "." + rawBody`
- `Idempotency-Key` — optional

Returns `202 ExecutionResponse`.

## Classes

- `WebhookController` — accepts `@RequestBody(required=false) String body` so the raw bytes match what the caller signed. Never let Jackson parse before verification.
- `WebhookSignatureVerifier` — `Mac.getInstance("HmacSHA256")` + `MessageDigest.isEqual` (constant-time). ±5 min skew (`MAX_SKEW = Duration.ofMinutes(5)`). Returns `boolean` only; never throws, never logs.
- `WebhookTriggerService` — loads `Workflow`, checks `webhookSecret` is non-null, verifies signature, delegates to `ExecutionService.execute(workflowId, workflow.getOwnerId(), idempotencyKey)`. Webhook-triggered runs are indistinguishable from JWT-triggered runs past this point.
- `WebhookAuthenticationException` → mapped in `GlobalExceptionHandler` to a single generic `401 {"message":"Unauthorized"}` for **all** failure modes (unknown workflow, missing secret, bad signature, expired timestamp). Callers can't tell which check failed.

## Secret lifecycle

- `POST /workflows/{id}/webhook` — `SecureRandom` 32 bytes → base64url; stored on workflow; returned **once**. Rotating revokes the prior secret.
- `DELETE /workflows/{id}/webhook` — clears the secret.

## Payload

The webhook does not pipe its body to action executors. Actions use their stored config; the payload is signature-bound only.
