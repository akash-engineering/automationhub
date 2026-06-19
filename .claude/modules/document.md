# Module: `document`

Generates PDFs (invoice/receipt-shaped), stores them via a swappable backend, and exposes owner-scoped retrieval. Plugs into the workflow engine in two ways: as a new `DOCUMENT` action type (synchronous step inside a run) and as an `AFTER_COMMIT` listener on `WorkflowCompletedEvent` (post-run summary, off by default).

Depends on `shared` and `infrastructure`. Reads from `workflow` for two things only: the `ActionExecutor` SPI (path A) and `WorkflowRepository` to resolve `ownerId` from an `Action.workflowId` (also path A). The listener path is purely event-driven.

## Layout

```
document/
├── controller/   DocumentController            (GET /documents, /{id}, /{id}/download)
├── service/      DocumentService               (generate / list / get / download)
│                 PdfInvoiceRenderer            (OpenPDF — title, date, table, total)
│                 InvoiceData, InvoiceLine      (records)
│   └── action/   DocumentActionExecutor        (supports DOCUMENT, parses InvoiceData from Action.config)
├── listener/     WorkflowCompletedListener     (AFTER_COMMIT + @Async, gated by config)
├── storage/      StorageService                (interface: put / get)
│                 LocalFileStorageService       (@ConditionalOnProperty provider=local, matchIfMissing=true)
│                 S3StorageService              (@ConditionalOnProperty provider=s3 — inactive stub)
│                 StorageLocation               (record)
├── repository/   DocumentRepository
├── entity/       Document
└── dto/          DocumentResponse
```

## Entity

- **`Document`** — `ownerId : UUID`, `executionId : UUID` (nullable — set by listener path, null by action path), `filename : String`, `contentType : String`, `storageKey : String`, `sizeBytes : long`. Extends `BaseEntity`.

## PDF rendering

- **`PdfInvoiceRenderer`** — wraps OpenPDF (`com.github.librepdf:openpdf:1.4.2`). Renders A4 with title, date, recipient line, and a two-column table (Description / Amount) closing with a Total row. Money is `<currency> <amount with 2dp half-up>`. Code-driven; no templating engine. Swap to PDFBox if you ever need finer typography control — interface stays the same.
- **`InvoiceData(title, recipient, currency, lines)`** + **`InvoiceLine(description, amount : BigDecimal)`**. Both records.

**Why OpenPDF over PDFBox:** OpenPDF gives a high-level `Document` / `PdfWriter` / `PdfPTable` API; an invoice fits in ~30 lines. PDFBox is glyph-positioning low-level — same output, 3× the code. License (LGPL) is fine for this project.

## Storage

```java
public interface StorageService {
    StorageLocation put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
}
public record StorageLocation(String key, String provider) {}
```

- **`LocalFileStorageService`** — registered by `@ConditionalOnProperty(provider=local, matchIfMissing=true)`. Reads `automationhub.document.storage.local.root` (default `./var/documents`). Lazily creates the dir on first write. Guards against path-escape (`..` keys) via `Path.normalize().startsWith(root)`.
- **`S3StorageService`** — registered by `@ConditionalOnProperty(provider=s3)`. Wired but **inactive**: both `put` and `get` log a warning and throw `UnsupportedOperationException("S3 storage stub — real AWS integration arrives in Increment 4")`. Reads `automationhub.document.storage.s3.bucket` and `...s3.region` for future use.

Only one of the two beans is loaded per JVM, decided by `automationhub.document.storage.provider` (default `local`).

## Service contract — `DocumentService`

- `generate(InvoiceData, UUID ownerId, UUID executionId) : Document` — `@Transactional`. Renders, stores (random UUID-named `*.pdf` key), persists the row. The `filename` shown to the user is a sanitized form of `data.title()` + `.pdf`; the `storageKey` is opaque.
- `list(UUID ownerId, Pageable) : PageResponse<DocumentResponse>` — owner-scoped, newest first (controller passes `Sort.by(DESC, "createdAt")`).
- `get(UUID id, UUID ownerId) : DocumentResponse` — `ResourceNotFoundException` if not owned or missing.
- `download(UUID id, UUID ownerId) : DownloadResult(Document, byte[])` — owner-scoped, reads bytes from storage. Caller controls the response shape; see `DocumentController`.

## Integration path A — `DocumentActionExecutor`

Auto-registered into `workflow.ActionExecutorRegistry` purely by being a `@Component implements ActionExecutor` — the strategy pattern in `workflow` was designed for exactly this. Adding the executor required **zero** edits to existing executors or to `ActionExecutorRegistry`.

Flow:

1. `supports(DOCUMENT) → true`; for the other three types, returns `false` so the registry doesn't claim it.
2. `execute(Action)`:
   - Validate `action.config` non-blank → `failed("document: missing config")`.
   - Parse `config` JSON → `InvoiceData`. Bad JSON → `failed("document: invalid config json: …")`.
   - Resolve `ownerId` via `workflowRepository.findById(action.getWorkflowId())` → if missing, `failed("document: workflow owner not found …")`.
   - `documentService.generate(data, ownerId, executionId=null)`.
   - Return `ok("document: generated id=… key=… bytes=…")` so the message ends up in the `ExecutionLog` row.

**Cross-module note:** importing `WorkflowRepository` is the one deviation from the strict "no cross-module deps" rule. The Action executor receives only `Action`, which carries `workflowId` but not `ownerId`. The alternatives all touched the workflow module (extending the SPI to pass `Execution`, or adding `ownerId` to MDC inside `ExecutionRunner`); injecting a Spring Data repository for a pure read is the smallest acceptable compromise. If we ever want to remove it, publish a tiny `WorkflowOwnerLookup` SPI from the workflow module and consume the interface here.

### Config shape

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

Missing fields fall back to sensible defaults (`title="Invoice"`, `currency="USD"`, empty recipient, empty lines).

## Integration path B — `WorkflowCompletedListener`

`@TransactionalEventListener(phase = AFTER_COMMIT) @Async("automationHubTaskExecutor")` on `WorkflowCompletedEvent`. Same wiring as the notification module — runs after the workflow's terminal-status row is durable, off the request thread, with `MDC.executionId` set for the duration of the call.

Gated by `automationhub.document.auto-summary.enabled` (default `false`). When enabled, builds a minimal `InvoiceData` (workflow + execution IDs as lines, zero amounts) and persists the Document with `executionId = event.executionId()`. Defaulting off keeps the existing engine integration tests deterministic — they expected exactly one execution and one log row per run, and an auto-summary doc would change downstream counts.

Failures are caught and logged; they **never** propagate back into the workflow execution. The contract is the same as the notification module's senders.

## Controller

| Method | Path                            | Auth   | Notes |
|--------|---------------------------------|--------|-------|
| GET    | `/documents?page=&size=`        | bearer | owner-scoped, `PageResponse<DocumentResponse>`, newest first |
| GET    | `/documents/{id}`               | bearer | metadata (`DocumentResponse`), 404 if not owned |
| GET    | `/documents/{id}/download`      | bearer | streams bytes with `Content-Type`, `Content-Disposition: attachment; filename=…`, `Content-Length` |

Owner scope is enforced in `DocumentService` on every read path — the controller passes `currentUser.requireId()` through unchanged.

## Configuration keys

| Key                                              | Default          | Notes |
|--------------------------------------------------|------------------|-------|
| `automationhub.document.storage.provider`        | `local`          | `local` or `s3` (s3 active in Increment 4) |
| `automationhub.document.storage.local.root`      | `./var/documents`| Absolute path resolved relative to app CWD |
| `automationhub.document.storage.s3.bucket`       | (empty)          | reserved for Increment 4 |
| `automationhub.document.storage.s3.region`       | (empty)          | reserved for Increment 4 |
| `automationhub.document.auto-summary.enabled`    | `false`          | toggle for path B (listener) |

## What does **not** belong here

- Workflow domain logic, action execution orchestration, idempotency.
- Notification/email/Slack delivery — that's the `notification` module's job.
- Templating engines, branding assets, multi-tenant theming — the renderer is intentionally simple. Add a `DocumentRenderer` interface and multiple impls when there are at least two real templates to support.
