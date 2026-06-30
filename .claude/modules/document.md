# Module: `document`

Generates PDFs and stores them; exposes owner-scoped retrieval. Two integration paths into the workflow engine.

## Key classes

- `DocumentController` — `GET /documents`, `/{id}`, `/{id}/download` (streams bytes with `Content-Type` / `Content-Disposition` / `Content-Length`).
- `DocumentService` — `generate / list / get / download`. Owner-scoped on every read.
- `PdfInvoiceRenderer` — OpenPDF (`com.github.librepdf:openpdf:1.4.2`). A4: title / date / recipient / two-column table (Description / Amount) closing with a Total row. Money: `<currency> <amount.setScale(2, HALF_UP)>`.
- `InvoiceData`, `InvoiceLine` — records.
- `StorageService` (interface): `put(key, bytes, contentType) → StorageLocation`, `byte[] get(key)`.
- `LocalFileStorageService` — `@ConditionalOnProperty(provider=local, matchIfMissing=true)`. `Files.write` with `Path.normalize().startsWith(root)` escape guard. Root: `automationhub.document.storage.local.root` (default `./var/documents`).
- `S3StorageService` — `@ConditionalOnProperty(provider=s3)`. **Stub**: both `put` and `get` throw `UnsupportedOperationException`.
- `DocumentActionExecutor` (`document.service.action`) — implements `workflow.service.action.ActionExecutor` for `ActionType.DOCUMENT`. Reads `workflow.WorkflowRepository` to resolve `ownerId` from `Action.workflowId` — the one documented cross-module repository dep.
- `WorkflowCompletedListener` — `@TransactionalEventListener(AFTER_COMMIT) @Async`; gated by `automationhub.document.auto-summary.enabled` (default `false`). Generates a summary PDF post-run; failures never propagate back into the workflow execution.

## Entity

`Document` — `ownerId`, `executionId` (nullable; action-path leaves null, listener-path populates), `filename`, `contentType`, `storageKey`, `sizeBytes`.

## `DOCUMENT` action config shape

```json
{
  "title": "Invoice #001",
  "recipient": "Acme Corp",
  "currency": "USD",
  "lines": [
    {"description": "Consulting", "amount": 1500.00},
    {"description": "Travel",     "amount": 320.00}
  ]
}
```

Missing fields fall back to defaults (`"Invoice"`, `"USD"`, empty recipient, empty lines).

## Config keys

| Key | Default |
|---|---|
| `automationhub.document.storage.provider` | `local` |
| `automationhub.document.storage.local.root` | `./var/documents` |
| `automationhub.document.auto-summary.enabled` | `false` |
| `automationhub.document.storage.s3.bucket` / `.region` | (empty — S3 stub) |
