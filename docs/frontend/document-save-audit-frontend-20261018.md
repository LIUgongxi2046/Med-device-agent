# Document-save audit frontend (staging)

## Files and integration

- Copy `api/business-audit.ts` to `web/src/api/medical/business-audit.ts`.
- Copy `components/document-save-audit.vue` to `web/src/views/medical/document-save-audit.vue`.
- Proposed authenticated business route: `/document-save-audit`, name `MedicalDocumentSaveAudit`; component above. Root owns router, SPA fallback and navigation. The page uses `useMedicalRouteRead` and actual session/project permissions.
- Root registers backend `business-audit.schema.json` with existing strict protocol AJV. Only public `Availability`, `Query`, `Page` definitions belong in the allowlist. Do not expose `SavedEvent`. Run the existing exhaustive schema compilation gate after registration.
- No framework request interceptor change is needed. This API uses a private fixed-path Fetch GET reader to bound actual stream bytes before strict JSON parsing, then the existing envelope/error/header validation.

## Behavior and boundaries

`GET /api/business-audit-feature-availability` discovers the actual deployment switch and capacities. Disabled is not zero events. A real paginated `listProjects` picker precedes explicit user query. Query always includes project, exact `document.revision.saved` action, UTC from/to and limit. Optional known actor/document UUIDs are not resolved to invented names. Timestamps are UTC Z with microsecond precision; normalized query comparison matches Java Instant fraction grouping.

The title and result wording cover only 文稿保存审计. An empty page can still expose a valid next cursor. Complete describes this specified live authorized scan, never all business history. CURSOR_EXPIRED/INVALID are local only after strict standard error/header validation. All other errors retain existing fail-closed behavior. Filter, route, actor/context/action-summary changes clear rows and body; independent request generations plus AbortSignals isolate late responses. Hidden/unmounted views abort and clear. No automatic cursor replay, write, approval, export or task ID inference exists.

Each actual row can invoke existing `readSavedDocumentRevision` with its real project/document/revision. The existing full fixed-document read rechecks current document/source permission and renders the actual body with DocumentBodyReader. Its bounds reuse the editor's 1,000 page / 100 MiB / page-size 100 policy; availability audit capacities do not imply body-read authority.

## Transport safety

Only the two fixed same-origin `/api/` endpoints are accepted. Fetch uses credentials `same-origin`, cache `no-store`, redirect `error`, schema/context headers and no Authorization. A 60-second linked timeout and caller AbortSignal stop reads; every acquired stream reader is cancelled/released. Bootstrap availability is capped at 64 KiB. Audit responses use min(server maximum_response_bytes, independent 4 MiB client cap); declared Content-Length and actual Uint8Array byte totals are checked before fatal UTF-8 decoding / duplicate-key and unsafe-number checks / JSON parsing. Capacity rejection is a local typed error and does not clear a valid session. Returned envelope and current context are checked before exposing data. Full query, fixed read URL, per-page IDs and descending microsecond/audit-ID order (also across pages, including empty pages) are verified.

## Focused validation

From repository root:

```sh
node --test /tmp/stage21-business-audit/frontend/tests/business-audit.test.mjs
```

Set `MEDICAL_REPO` when running elsewhere. Tests exercise actual transpiled API and actual compiled SFC setup with deferred responses. They include strict public schema compilation, malicious extras/JSON/URLs, local invalid input, secure Fetch options, streaming caps/cancellation, timeout, expiry vs authorization errors, UTC precision, explicit selection/query, empty pages, stale filters, identity/revocation and fixed body reads. `tests/results.txt` contains latest result. Staging-only type diagnostics use `validation/tsconfig.json` against the two staged entry files and their actual imported repository dependencies, recorded in `tests/typecheck.txt`.

These are offline Node/setup/compiler checks, not browser rendering, live HTTP, database, S3, deployment query-plan or end-to-end acceptance. Formal registration/build and route integration remain root-owned.

Focused strict Vue/TypeScript check command (staging overlay, only the two new entry files plus real imported dependencies):

```sh
node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p /tmp/stage21-business-audit/frontend/validation/tsconfig.json
```

2026-10-18: this check exited 0 after fixing one `noUncheckedIndexedAccess` pathname split index assertion. Its overlay symlinks resolve existing schema/contracts; it is not a replacement for the root-owned complete medical build.

Final focused result (2026-10-18 18:29 UTC): 24/24 tests passed, 948.818 ms; final strict staged Vue/TypeScript exited 0. The strict AJV gate found a missing explicit object type in Availability's conditional features schema; backend author corrected it without changing public shape. Validated schema SHA-256: `b22dd8279f18297d9d8fa13b299d814f31194de013480bfc39dde349557734b4`.
