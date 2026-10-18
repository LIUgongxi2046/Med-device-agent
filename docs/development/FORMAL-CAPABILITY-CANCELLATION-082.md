# 082 Formal capability original-request cancellation (staging)

Only the existing 073 FORMAL_CAPABILITY_AUTHOR namespace is cancellable. This is not a general operation cancellation and does not include 080/081 authors.

- POST `/api/agent-authoring/capability-cancellations` HTTP200; existing schema/context/CSRF headers, a separate cancellation Idempotency-Key.
- GET `/api/agent-authoring/capability-cancellation-operations` read-only, cancellation key in Idempotency-Key; no query/body.
- Both use immutable response revision `1`, existing `medical.agent-authoring.enabled` deployment gate.
- Body `{schema_version:'1.0.0',original_kind:'FORMAL_CAPABILITY_AUTHOR',original_key,original_request:<entire original 073 declaration>}`. 2MiB bounded wrapper, original canonical request <= actual073 1MiB limit. The original key retains the exact old073 key contract; the new cancel key accepts ASCII printable16–128.
- Response CancelReceipt is strictly bound to tenant, actor, original kind/key/body SHA, cancellation body SHA and its own operation/cancellation identities/time. CANCELLED has null receipt. COMPLETED returns only the original typed073 receipt, never package contents or fixed definition.
- Current exact owner `configuration.read` / `AGENT_PUBLICATION` is required. Cancellation neither requires capability.configure nor reauthorizes revoked package/source dependencies. Completed original journal is fully verified through actual AgentPublicationJournal.prior.
- Original author narrow patch checks immutable cancellation before new writes or configure grant-dependent work. TenantTransaction serializes both commands under the actual tenant watermark lock. Tombstone and operation/audit/outbox commit together. A completed tombstone requires the original real journal still present and its receipt identical. Missing/changed ledger is CONTENT_MISMATCH.

Root integration requirements: register exact FormalCapabilityCancellationMapper subject prefix, formal_capability_cancellation tenant-owned and immutable table; register migration082 and local frontend schema. Never exempt mapper from subject validation. Do not alter073 original success schema.

The targeted implementation and tests are complete at the checkpoint below. No liveDB/service deployment has run.

## Verified staging checkpoint

- JDK17 actual compilation; 35 distinct assertions through standalone runner: 10 new cancellation/actual073 author+journal cases, 3 new controller cases, 1 new MyBatis/AST case covering7 real statements, and21 existing073 author/bean graph regressions. This is the same set of JUnit methods, not an additional35 tests. Stateful persistence/permission/dependency boundaries are explicit doubles; no MySQL execution claim.
- New guard is wired before original configure/dependency work. Tests cover late original POST after committed CANCELLED, completed-original response, missing completed original journal, original body mutation, private fixed witness rewrite with recomputed row hash, missing independent outbox, read-only cancellation after configure/package revocation, no-read/stale-context refusal, actor isolation and NOT_FOUND.
- Frontend: full actual API bundles + compiled Vue setup tests, 56/56 (original41 plus new15). Narrow strict Vue typecheck passed. No browser or visual acceptance was performed.
- UI uses one atomic sessionStorage entry that wraps the complete original pending request and its own cancellation key/body. Original completion callbacks cannot overwrite/remove this wrapper. All failures preserve it. NOT_FOUND is never clearing proof. User must export a validated CANCELLED/COMPLETED receipt and the entire original draft, then explicitly acknowledge before closure. Export/removal failures retain recovery state. New context clears in-memory proof, and requires real recovery.
- UI write lock has owned token and synchronous reset listener; a late finally cannot unlock a later owner. Identity/hidden/unmount responses cannot publish receipts. No automatic cancellation or automatic retry.
- Public local schema SHA256: be1da291d1ee015cd400acf440cb1619051ff29e458a4b5fdd7026dd1eed83d3. Only21 reachable defs retained; original073 published schema unchanged.

Publish maps: COPY-MAP-082.json. Shared author and two existing Java test files require the documented narrow dependency addition; patch files are generated beside them. Root owns schema registration, exact tenant subject prefix, immutable table list, master migration and aggregate validation. New frontend helpers preserve existing test behavior and add optional staging path/global fixture overrides; no hardcoded/tmp runtime paths in published tests.

Transport merge is mandatory: shared/request.js.patch adds only the two exact cancellation endpoint paths to the existing strict raw capability JSON reader. Its real interceptor test now checks seven closed endpoint shapes and rejects cancellation suffix/path variants. Do not rely on ordinary Axios JSON parsing for these immutable receipts.
