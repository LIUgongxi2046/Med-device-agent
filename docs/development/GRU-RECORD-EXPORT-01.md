# 070 Gru USER-record export

Implementation source is published; runtime flags remain unchanged. This local extension exports real 066 owner-only RECORD_ONLY/NOT_EXECUTED USER records. It does not provide model execution or claim completion of the full official GR-08 `/api/conversations` workflow.

## Scope and API

See [local API](GRU-RECORD-EXPORT-API-01.md). Basis selects the current real conversation revision and complete ordered prefix, maximum 500 messages. Creation accepts that exact closed basis plus purpose, JSON format and reason. The resulting canonical JSON includes real USER text, intent, timestamp and original message hash, with minimal fixed document/snapshot identifiers. Full SourceScope, Selection, source content, ObjectRef, private grants and assistant replies are excluded.

The original message hash covers the complete original shared Message, not just exported text. The export artifact SHA covers the actual downloaded UTF-8 canonical bytes. Full Request SHA binds original basis/reason/purpose/format for readonly recovery. These digests are distinct.

## Authorization

`PermissionSchema` supports action `conversation.export` and purpose `GRU_RECORD_EXPORT` through its existing restricted string patterns. The real `ResourceReadAuthority` still verifies resource ownership, project, exact action/purpose, active grant, identity mode and validity time.

All operations recheck current owner, conversation.read, fixed document/source rights and the additional DOCUMENT conversation.export/GRU_RECORD_EXPORT grant. Original source authorization keeps DOCUMENT_BODY; the new function permission creates no source license. Download additionally requires ARTIFACT artifact.read/GRU_RECORD_EXPORT. No grants are created by export.

The generic artifact access-set is the actual fixed document source dependency closure. Complete original message versions/content hashes/operation/audit/outbox evidence is an independent narrow Gru verifier obligation at every export/read/recovery/download. No invented message rights revisions or generic artifact-reader fallback is used.

## Persistence and uncertainty

070 adds three immutable tables:

- `gru_record_export_intent`: original key, canonical complete request, fixed basis/manifest/content, actual content SHA/size, independent storage write intent, original session/context and immutable createdAt/deadline
- `gru_record_export_attempt`: unique durable first-write claim
- `gru_record_export`: completed verified artifact/ObjectRef, receipt and independent audit/outbox identities

Initial operation and intent commit together. A first object write is authorized only after this call successfully commits a unique attempt insertion plus operation transition. Existing or uncertain attempt outcomes permit only verified storage recovery. No marker, deadline or operation-key reset is provided. S3 transfer occurs outside the database transaction with repeated current domain authorization.

Final artifact/version/content binding/lineage/audit/outbox/registration/operation completion commit together. If a concurrent caller already completed, the full ObjectRef it registered must equal this call's verified I/O ObjectRef, including storage version. A version mismatch fails closed and leaves the independently valid original receipt intact.

Readonly original-key lookup uses the `Idempotency-Key` header, accepting the original printable ASCII16–128 contract. Independent intent lookup prevents a mutated/missing operation row from masquerading as NOT_FOUND. PROCESSING is an incomplete persisted operation, not a worker status. Expiry preserves the unresolved request; it does not authorize a fresh key or deadline. Cross-session/context readonly recovery always reauthorizes current identity and binds the original full request digest.

## Deployment requirements

Both `medical.gru-records.enabled` and `medical.gru-record-export.enabled` must explicitly be true for controller/service registration. No application configuration was enabled. Authenticated availability reflects actual controller registration, separately from business permission and resource eligibility.

Enabled export requires explicit positive non-secret values:

- `medical.gru-record-export.maximum-bytes`, maximum 33554432
- `medical.gru-record-export.maximum-seconds`
- `medical.gru-record-export.maximum-nodes`
- `medical.gru-record-export.maximum-relationships`

The existing real SourceUploadTransport/object-store configuration, versioned storage, secure temporary directory, current identity/domain database and source rights are prerequisites. Prospective export-root nodes and relationships are checked before an attempt/external write. Enabling flags alone is not a deployment or authorization acceptance result.

Integration must register the 070 migration, add all three tables to immutable protection, and add `GruRecordExportMapper` to subject-parameter enforcement. Root owns shared interceptor, migration master and coverage integration; this slice does not overwrite their implementation.

## Verification evidence

Isolated existing-JDK compilation passed. Standalone focused runs passed 28 actual-service cases (using actual GruService history verification and explicit storage doubles), five controller/availability cases, five actual permission authority cases, and 15 schema assertions. The formal JUnit classes represent 28 dynamic tests plus three aggregate tests; standalone assertion/case counts must not be reported as 53 JUnit methods.

Formal tests use classpath schemas and existing repository fixtures; they do not depend on a `/tmp` validation directory. SQL parser checks cover 23 mapper statements; Liquibase parses three new changesets. These are not real MySQL/FK transaction or S3 deployment acceptance. Aggregate Maven, shared tenant isolation regression, runtime deployment and browser verification belong to subsequent integration acceptance.

The immutable attempt also persists required schema_version, schema_manifest_sha256, classification and created_at without defaults. They are copied from the verified original intent; created_at equals actual attempted_at. Readback verifies all four fields, actor and original deadline, rejecting altered schema, manifest, classification or creation time. This updates only undeployed 070; older migrations and public JSON remain unchanged.
