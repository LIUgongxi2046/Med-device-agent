# IMP-23 annotation frontend integration

## Implemented entry point

The existing task document editor now contains a “标注与讨论” panel. No new route, manual annotation identifier, or replacement of the frozen Record/ArtifactRef routes is required.

- The current saved fixed version lists complete annotations, lets a user select one, and reads its complete replies.
- New annotations originate from the document reader's validated `Selection` event. Text, image, and table selection are reverified against the saved baseline body, including its full content digest. An editable draft is never used to verify a stored annotation.
- The reader shows a concise selection summary by default. Exact selection/version/hash details are collapsed.
- New annotations require explicit `annotation.read` and `annotation.write` session-summary scope, a current non-historical saved body, no unconfirmed fields or unsaved edits, no signature records, and no conflicting local write. The server remains authoritative for current status, ACTIVE project, source rights and timed permission checks.
- New replies also require an explicitly selected thread with a verified complete reply listing. Their CAS is the annotation revision, not the document revision.
- New writes are disabled in history, non-current and signed views. Historical recovery of an already registered operation remains available.
- CURRENT_VERSION and ORIGINAL_VERSION are displayed as server-reported location states. ORIGINAL_VERSION never claims an anchor in the latest body. REANCHOR_REQUIRED remains visible. There are no resolve, reanchor, review-confirmation or signing controls.

## Additive contracts and validation

`annotation-domain.ts`, `annotations.ts`, and `annotation-write-view.schema.json` use these independently registered schemas:

- `urn:medical:annotation-threads:1.0.0`
- `urn:medical:annotation-replies:1.0.0`
- `urn:medical:annotation-write-view:1.0.0` (local recovery metadata)

GET/POST `/api/documents/{id}/annotation-threads` use a fixed `document_revision`. Create uses document-head `expected_revision`, operation `RV-04-THREAD-1`, and the existing context/CSRF/idempotency headers.

GET/POST `/api/annotation-threads/{id}/replies` use operation `RV-05-REPLY-1`. The original fixed document revision is retained; the expected revision is the parent annotation's revision. Replies preserve the annotation's status.

Validation checks include exact object shapes, UUID and decimal revision strings, current user/context, safe Unicode and exact whitespace, canonical digests, complete counts, unique chronologically ordered identities, Selection structure and fixed-body semantics, response revision and operation ownership, and original receipt identity. Request bodies are frozen before asynchronous validation. Abort and current-session checks run after digests and immediately before transport dispatch.

## Recovery and draft boundaries

There is one outstanding annotation operation per user/tenant/document in this tab's session storage. Stored values contain identities, original CAS/context and digests only. Comment text, replies, Selection and document bodies are not stored in localStorage/sessionStorage. The medical transport exits before generic request-body deduplication storage.

Raw drafts are retained only in tab memory, keyed by actor/context/document/fixed version. Context invalidation removes them. A page reload may retain recovery metadata, but the user must re-enter the identical original content and Selection if the original request body is no longer in memory. The digest must match before a retry is sent.

Recovery queries the original operation name/key and retries only the exact original operation, key, body and revision. Confirmation requires a valid original receipt, a COMPLETED operation with the expected target, and matching complete readback. Unknown, failed or expired outcomes preserve the original recovery record. There is no abandonment button that could race a delayed registration, and no replacement key is created for an uncertain result.

If the document head has moved, the pending-operation panel opens its original fixed version in the existing history dialog. That dialog includes the reader and annotation panel, enabling original selection reentry and registered-operation recovery. Historical/signed/non-current views only replay when lookup returns FOUND; NOT_FOUND never starts a new historical write. If original permissions, context, body, or operation retention have changed so the server cannot verify the original request, recovery remains blocked visibly with the original metadata retained.

Stopping a wait aborts the active request and rejects late UI results; it does not claim to cancel server work. Original inputs and recovery metadata remain available. Main/history panels own separate busy state. The shared write lock includes the parent's post-create refresh, and the child's AbortSignal propagates into that refresh.

## Parent document preservation

After a verified create, the editor rereads the saved document and dependency basis while holding the coordinated write lock. If its fixed body is unchanged, it advances snapshot/dependency metadata only. It never replaces local draft body, unconfirmed field inputs, reason, or undo history. If a newer fixed body is detected, it retains the original baseline and edits, exposes the new server snapshot, and reports a comparison conflict. A stopped refresh cannot apply late metadata. Refresh is not silently skipped merely because this annotation operation owns the global write flag.

## Verification scope

Run:

```sh
node tests/unit/annotation-frontend-checks.mjs
node tests/unit/document-selection-checks.mjs
npm run typecheck:medical --prefix web
npm run build:prod --prefix web
```

`annotation-frontend-checks.mjs` uses the actual schema/domain/transport modules and compiles the actual Vue SFC setup. It exercises component logic with Vue's DOM-free custom renderer and an in-process mocked request transport. It covers invalid responses and bounds, exact Unicode, body/Selection integrity, scoped permission gates, idempotent uncertain recovery, async predispatch abort/session change, unknown/expired outcomes, create/reply operation verification, history recovery after head movement, stopped waits, coordinated refresh, unsaved body/field preservation and late-refresh cancellation.

The test report explicitly records `actual_http_verified: false`, `browser: NOT_RUN`, and no sockets, databases or provider calls. These checks establish pure and mocked behavior, TypeScript correctness, and production bundling only. They do not establish browser interaction/appearance, real HTTP filters, database/storage integration, current authorization grants, or production readiness. No browser, listener, server, DB, object storage, provider, cloud execution or push was performed for this frontend slice.

## Subsequent additive human-decision slice

The create/list/reply baseline above remains covered by its original 13 regression groups. Explicit human Resolve/Reopen is implemented separately in [IMP-23-ANNOTATION-DECISIONS-FRONTEND.md](IMP-23-ANNOTATION-DECISIONS-FRONTEND.md), with independent review permission, immutable reasons/history, dual CAS and exact-operation recovery. This does not complete REANCHOR, confirmation, signing or the frozen RV-06 Record contract.
