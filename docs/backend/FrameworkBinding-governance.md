# BX25 framework binding governance

Implementation status: authored, NOT_RUN. No build, test, migration, service or provider was run. No release, source commit, approval, environment, artifact, grant or user account has been fabricated or seeded. Default consumption is disabled.

## Scope and contracts

The additive `framework-binding.schema.json` leaves published ManagementBindings v1 unchanged. Every declared manifest carries exactly eighteen distinct known modules. UNBOUND/DISABLED modules have no operations and retain a reason. BOUND modules require a source commit identical to the manifest; route/component strings cannot be URLs or traversal. Operations are exact server-owned tuples of module, operation ID, method, relative path, request schema, response schema, permission, side effect and deployment environment. Catalogue hashing includes the complete tuple list and actual bytes of the framework, native schema/action catalogue, form and admin-owned job schemas. No manifest executes a route or invokes arbitrary code.

The catalogue declares 128 fixed actions spanning all eighteen modules, including 103 original native descriptors, adapted monitor/tool reads, controlled job lifecycle/commands/exports and controlled form publication. Native body advice enforces 26 exact JSON handler contracts before DTO mapping. Parameter advice enforces 102 native query/form handler contracts, rejecting unknown keys and arbitrary BaseEntity.params. Generator column updates additionally verify actual table ownership. Full-entity editor round trips are projected into these closed mutable fields by FrameworkBindingNativeProjection; server validation is independent of that client helper. Original permission guards and validators remain in place.

Native response DTO schemas describe original read envelopes separately from write projections. Some adapted read responses intentionally retain NativeReadResponse rather than claiming fully normalized payloads; response schemas are descriptive, not runtime response validation. Medical-only JSON advice removes credential fields and redacts historical operation-log operParam/jsonResult/errorMsg. Authorized configValue and noticeContent editor responses remain usable. Original spreadsheet import retains its existing multipart parser and possible partial persistence on row failure; this increment does not claim atomic import or new file-content schema validation. Arbitrary SQL createTableSave and filesystem genCode remain explicitly unsupported; ZIP preview/download/batch generation are fixed declared development-only actions. No manifest serves as an arbitrary router.

## Authenticated producer

All calls use the actual current session, a single X-Schema-Version: 1.0.0 and X-Context-Revision. Writes also use Idempotency-Key. No tenant can be selected by body or deployment configuration. All producer calls require PLATFORM_ADMIN and active current tenant membership. Enterprise-scoped grants have purpose MANAGEMENT_BINDING:

- GET /api/management-bindings/catalogue: management.discover. Returns exact action catalogue, schema digest, current safe configuration projection and its digest.
- POST /api/management-bindings/drafts: management.configure; body {manifest: Manifest}. Caller chooses a new release UUID and supplies real source/environment pins and all eighteen modules. Response returns canonicalizable manifest and digest. This creates only an immutable DRAFT and matching version registry entry.
- GET /api/management-bindings/drafts/{releaseId}: management.discover; retrieves the immutable draft, exact canonical JSON bytes, author and actual state for independent review.
- POST /api/management-bindings/approvals: management.approve; body {release_id, manifest_sha256, manifest_artifact_id, reason}. An independent authenticated human must approve. The artifact must already be an enterprise-owned registered application/json artifact containing the exact canonical manifest bytes, with matching artifact and registry SHA-256. The approver must hold artifact.read for that artifact, purpose MANAGEMENT_BINDING. This implementation does not synthesize object-storage receipts or bypass the existing artifact publication workflow. Provisioning/publishing this artifact is a real deployment prerequisite.
- POST /api/management-bindings/revocations: management.approve; body {release_id, reason}. Revokes ACTIVE release, never deletes history or reactivates it. Repeated same idempotency request returns its original receipt.

Approval locks the tenant before activating, so another active release for that environment is rejected. Revoke first to replace it. Approval produces an immutable independent decision, registry hash, synchronous audit and the actual existing management_binding_release row. Configuration does not constitute approval. Existing historical release rows without the new manifest/decision records cannot pass this consumer.

## Consumer and deployment

GET /api/management-bindings requires management.discover and returns the existing envelope/ManagementBindings structure from an active, pinned release. Runtime interface is com.ruoyi.medical.identity.FrameworkBindingAuthority.require(operationId, method, path), returning Binding(releaseId, environmentId, manifestSha256). It checks the actual request method and platform path, session/context, platform role, enterprise grant, manifest digest, source/configuration/action pins, real approval and artifact registration. Native permission checks, CSRF, business authorization, idempotency and synchronous side-effect auditing remain mandatory at the actual endpoint.

Deployment properties, with no generated defaults:
- medical.framework-bindings.enabled=false by default
- medical.framework-bindings.release-id: actual approved release UUID
- medical.framework-bindings.environment-id: actual environment UUID
- medical.framework-bindings.source-commit: real deployed source commit, 40 or 64 lowercase hexadecimal characters
- medical.framework-bindings.runtime-environment: DESIGN, DEVELOPMENT, STAGING or PRODUCTION

The configuration digest is computed from actual medical.identity.mode, medical.framework.job-dispatch.enabled, medical.framework.generator.enabled and both the binding runtime environment and actual medical.framework.environment. Secrets are never included or returned. Generator declaration is DEVELOPMENT-only. Any missing approval, configuration mismatch, schema change or source mismatch denies consumption. Authoring/catalogue remain reachable when disabled. A platform administrator without current tenant membership/context cannot use these gates; no default governing tenant is inferred.

## Deferred verification

FrameworkBindingCatalogueTest and FrameworkBindingNativeSchemaTest were authored, NOT_RUN. Before deployment also execute transaction-backed verification for: duplicate/unknown modules, forged source/config/action/schema digests; arbitrary paths and permissions; self-approval; missing/wrong-scope artifact; cross-tenant IDs; approval registry tamper; revoke then consume; concurrent approvals; idempotent write/replay mismatch; audit rollback; absent deployment pins; original native permission and CSRF rejection. Migration176 adds three tenant-owned immutable metadata/history tables and guarded ACTIVE -> REVOKED release transition. Root orchestration must register the migration and tables in the migration/tenant coverage manifests before deployment.

## Normal publisher UI

The existing tool/build page has an explicit medical-mode “框架绑定发布管理” toggle for FrameworkBindingPublisher. Its server authoring path does not require an already-approved binding or enabled runtime, avoiding bootstrap deadlock. Existing navigation permission and current tenant context remain required. Users supply genuine environment/source pins; preparation creates eighteen UNBOUND modules, not fabricated authority. Users edit declared operations, submit immutable drafts, load and hash-check canonical bytes, download those exact bytes for the existing artifact registration workflow, and independently approve using the genuine artifact UUID. Approval/revocation require explicit UI confirmation. Uncertain mutations retain the original idempotency key for manual reconciliation; they are not silently resubmitted with new keys.

Migration176 and all Java/Vue/schema changes remain source-only NOT_RUN. Real approval, registered artifact, deployment configuration, migrations and runtime verification remain pending. Native existing endpoints retain original authorization rather than being globally disabled merely because this new manifest catalogue is not deployed.
