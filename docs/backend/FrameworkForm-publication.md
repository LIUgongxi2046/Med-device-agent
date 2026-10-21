# Controlled form publication (B30)

Authored, NOT_RUN. No tests/build/services/migrations/providers/network calls were executed. No grants, approvals, artifact receipts or binding releases are seeded.

New standalone UI: `web/src/views/tool/build/FrameworkForm.vue`, component reference `tool/build/FrameworkForm`. Embed in the medical-only tool builder or route through the approved FORM_BUILDER manifest. It uses the existing session, CSRF and envelope request path, validates the pinned contract and canonical content digest, renders only five fixed controls, and retains uncertain write requests in sessionStorage for same-key replay. No form submission/storage of entered business data is introduced. Preview is disabled/read-only, but authoring and independent publication are real writes.

Endpoints (all require original `tool:build:list`, PLATFORM_ADMIN, current tenant membership/context and exact approved framework binding):
- GET /api/tool/build/schemas: last 100 draft/publication metadata records, management.discover.
- POST /api/tool/build/schemas: {form, reason}, management.configure, DESIGN/DEVELOPMENT only. Generates immutable form UUID + registry hash, synchronous audit and idempotent receipt.
- GET /api/tool/build/schemas/{id}: original immutable form, fixed digest and author, explicitly DRAFT_VIEW rather than claiming publication.
- POST /api/tool/build/schemas/{id}/approvals: {content_sha256, schema_artifact_id, reason}, management.approve, DESIGN/DEVELOPMENT only. Requires a different human than the author and existing enterprise-owned application/json artifact with exact canonical content SHA-256. Artifact ownership, registry and artifact.read are rechecked. Approval is the immutable publication record.
- GET /api/tool/build/schemas/{id}/published: consumer revalidates form contract/hash, both registry rows, independent approval hash/author/form/artifact and artifact permission. No approval means no publication. Runtime binding revocation disables consumption. No mutable published forms or script execution.

Schema URN: urn:medical:framework-form:1.0.0. Local file bytes are hashed and included in the binding action catalogue pin. The exact copy in the Vue directory is also hashed before a preview can render. Contract supports TEXT, NUMBER, BOOLEAN, DATE, SELECT only; no HTML, arbitrary JSON Schema references, URLs, callbacks or executable code. Keys are unique and prototype-reserved keys are forbidden. SELECT requires bounded options, other controls forbid options.

All calls require singleton X-Schema-Version: 1.0.0 and X-Context-Revision, no query parameters. Writes require Idempotency-Key; idempotency binds original body, target form, actor and tenant. Unknown write outcomes retain their original key; confirmed error envelopes allow correction. Context changes prohibit replay under another context. All tables are tenant owned, creator/time/classification/schema-manifest annotated and immutable. Actual bytes must first be registered using the existing artifact workflow; UI download alone does not fabricate or register an artifact.

Root must register migration178 and tables framework_form_schema, framework_form_approval, framework_form_operation in master changelog/tenant coverage. Existing infrastructure version_registry/artifact/audit is reused; new tables have tenant/member/version/artifact FKs, unique publication per form and database self-approval guard.

Deferred: FrameworkFormSchemaTest. Still required before deployment: HTTP original permission/role/CSRF/context rejection, exact binding operation/hash pins, concurrent approval and idempotency races, transaction rollback with audit failure, cross-tenant/owner/artifact mismatch, self-approval, corrupted stored digests/approval, revoked binding, 65KB boundary/replay, UI refresh/reload/context changes, and real MySQL migration constraints/triggers. Neither tests nor production readiness are claimed.
