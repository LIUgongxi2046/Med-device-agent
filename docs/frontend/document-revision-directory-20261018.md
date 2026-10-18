# Saved document revision directory frontend

Published after the part16 archive. The two backend schemas are registered in protocol.ts; document-editor.vue mounts the directory and routes explicit selection to its existing openAnnotationOriginal fixed-version reader. No global route or body-read permission is changed.

## Published integration

- API: web/src/api/medical/document-revision-directory.ts
- Component: web/src/views/medical/document-revision-directory.vue
- Tests: web/tests/document-revision-directory/history.test.mjs and editor-integration.test.mjs
- Backend contract: docs/backend/document-revision-directory-20261018.md

protocol.ts registers the two published backend JSON Schemas, including these precise refs:

- urn:medical:document-revision-directory:1.0.0#/$defs/Id
- urn:medical:document-revision-directory:1.0.0#/$defs/Page
- urn:medical:document-history-feature-availability:1.0.0

Availability is an authenticated real feature registration read; it does not imply document authorization. The component selects min(20, actual maximum_page_size), always sending explicit limit.

## Editor mounting

DocumentRevisionDirectory is mounted when saved exists, with session and saved snapshot, active="active && !historyVisible", and disabled="writing || loading || comparing || annotationBusy || user.medicalWriting || historyLoading". Its select event calls the existing openAnnotationOriginal handler.

The select event has only an actual document_revision from the currently displayed authorized page. The component never calls body-reading APIs, changes saved/body/draft or starts editing. The parent calls its existing readSavedDocumentRevision path into historyDocument/historyVisible, preserving the current working draft and the full permission, fixed revision/hash and byte verification chain.

The directory does not fetch each item's read_url; it validates the URL is exactly the known local fixed-version endpoint, then emits the UUID. Thus it never follows server-supplied arbitrary links. Names/titles and revision sequence numbers are not invented; creator UUID and hash are inside optional details.

## Pagination semantics

Each displayed page contains the actual server authorized projection. No global total is shown. An empty page with next_cursor remains navigable. complete means this live traversal is exhausted, not an immutable complete historical snapshot. Refresh explicitly from the first page to discover changes. Related check/review/signature counts are record counts, not pass/approval assertions.

The server uses created_at descending then document_revision descending. The adapter checks ordering without collapsing microseconds into JavaScript milliseconds. It checks current marker, exact document and read path, unique revisions, envelope/header revision, page bound and cursor consistency.

Only a fully validated expected CURSOR_INVALID/CURSOR_EXPIRED error for a request that actually carried a cursor becomes a local CURSOR_STALE result. The panel clears the stale page and offers explicit first-page refresh; it does not retry automatically. Source/identity/schema/hash/authorization failures continue through the strict protocol path. Session, actor, context, action-summary, snapshot/head and activity changes abort and clear directory state. Generation guards prevent old response/finally from restoring state or clearing a newer busy state. Hidden/unmounted panels clear and stop consuming results.

## Verification

Published focused commands, from the repository root:

- node --test web/tests/document-revision-directory/*.test.mjs tests/frontend/document-history-entry-checks.mjs
- node --test web/tests/protocol-schemas/lazy-compilation.test.mjs

Publication verification: 25 focused history/API/setup/parent-child integration tests and four protocol lazy-compilation tests passed, with zero failures/skips. Logs: .implementation-work/logs/part18-document-history-frontend.log and part18-history-protocol-focused.log.

Tests use the actual published schemas and compiled Vue setup/template. The parent-child integration mounts the actual child with Vue's renderer and executes the real parent setup/event handler, verifying dirty draft identity preservation, global/local busy guards, abort on deactivation and late-result suppression. Actual parent template bindings and compilation are checked separately. API tests cover malformed responses, current markers, microsecond ordering, arbitrary URLs, availability and local cursor errors versus strict security failures. Component tests cover scope/head/permission changes, old finally, empty pages with next cursors, explicit selection and visibility/unmount cancellation.

Root reported the formal aggregate on 2026-10-18: 129 Java suites / 997 tests, 295 frontend tests, typecheck, 480 schema checks and build (55.79s) passed. Package was still running when this document was updated; no package completion is asserted here.

HTTP/application store boundaries in focused tests are mocked. Real browser/backend/database/object-storage integration has not been run; passing these checks does not establish deployment availability, actual user permissions or successful live body reads.
