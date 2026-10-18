# IMP-23 / RV-06 bounded annotation disposition

This is the independently versioned `/api/annotation-threads/{id}/decisions` extension (schema 1.0.0). It implements the original RV-06 human RESOLVE/REOPEN subset, not the original full route/Record contract. Frozen shared schemas, interface catalogue and original RV-06 remain unchanged. REANCHOR with human location evidence, issue decisions, checks, document confirmations, task transitions and signatures remain outside this slice.

## Authority and immutable identity

- POST requires the actual `review.decide` action on the real DOCUMENT owner with `DOCUMENT_REVIEW` purpose. Effective ENTERPRISE/PROJECT inheritance is evaluated by existing ResourceReadAuthority/PermissionAuthority. `annotation.write` grants no decision authority. GET requires `annotation.read`.
- Existing `DocumentReadService.readFixed` loads the complete source-aware immutable body. The existing `AnnotationThreadService.readSelection` verifies complete immutable Selection bytes, object registration, lineage, binding, digest and anchor against that fixed body. No fake IDs, placeholder reader ports or manufactured provenance.
- Fresh decisions require ACTIVE project, current unsigned fixed document, exact annotation and document-head revisions, and only OPEN→RESOLVED or RESOLVED→OPEN. RETURNED/REANCHOR_REQUIRED are refused.
- The short tenant transaction rechecks body/source/context proof, exact annotation including Selection identity and reply count, actual action authorization and grant/source time-boundary clock. A reply during reading fails closed. Final clock recheck failure rolls back all writes.

## Atomic state and history

The transaction CAS-advances annotation and document head by one, increments issue watermark by one, preserves evidence watermark and appends author/time/reason/old-new status and revision history. Relevant currently valid whole-document CONFIRM records receive ANNOTATION_DECIDED invalidations; the trigger is the actual registered fixed document revision, not an invented version. No confirming/signing/task status is manufactured.

Original review_decision stores only CONFIRM/RETURN and requires real review_batch_item rows. Migration 058 therefore adds separate annotation_decision with exact annotation/document FK, actor and author membership FKs, unique annotation+revision and transition constraints. Production mapper exposes INSERT/read only; ProtectedTenantInterceptor also rejects UPDATE/DELETE to this table. DB-admin bypass protection or database triggers are not claimed.

History is complete and bounded at 100 decisions; each reason ≤16000 Unicode code points and each POST ≤65536 UTF-8 bytes. This keeps maximum history text near 6.4MiB plus metadata. Overflows reject; no truncation or silent pagination. Exact Unicode and allowed whitespace are retained.

## Retry and confidentiality

RV-06-DECISION-1 idempotency identity includes method/path/body, context, session and independent manifest. Original 201 envelope/request/operation/decision IDs and original state/revisions replay after subsequent replies or opposite decisions, with current authority/source/Selection verification and exact immutable history/audit/outbox verification. Replays do not write. Current signing or history does not rewrite the original receipt. A response describes the original operation, not necessarily today's status; clients reload current annotations after success/replay.

Audit/outbox contain safe identities, state, revision, invalidated review IDs and digest, never human reason or document/Selection text. The classified history and operation receipt retain the human reason.

## Verification boundary

Offline tests cover strict protocol/servlet behavior, real tenant SQL compilation, service authority and CAS/rollback/expiry/replay histories with deterministic isolated storage fixtures, and offline migration invariants. The service tests mock the existing production body/Selection reader at its boundary and do not claim a live object read. Existing reader tests cover that path separately. No live database, S3, socket server or browser execution is claimed; these remain blocked by the environment. No commit, push, deployment or paid provider call is performed.
