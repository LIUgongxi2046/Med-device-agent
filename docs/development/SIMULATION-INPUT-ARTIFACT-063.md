# SOURCE original → ExactInput ARTIFACT bridge (063)

Baseline 063 passed root’s part12 aggregate tests/package. The following lookup and cross-scope binding increment is pending serialized Maven verification; no real DB/S3/HTTP acceptance is claimed.

## Interface and integration ownership

- POST `/api/simulation-fixtures/{caseId}/input-artifact`.
- Required single headers: X-Schema-Version=1.0.0, X-Context-Revision, Idempotency-Key (16–128 ASCII token characters), X-CSRF-TOKEN. Existing session security filters validate CSRF token and current session. No internal worker bypass. No query fields. Body capped at 65536 bytes, including unknown Content-Length.
- Body is the separate `simulation-input-artifact.schema.json` Request: schema_version/project_id/model_config_revision/request_parameters. Caller supplies no object address, rights, purpose, artifact ID or authorization assertion.
- Receipt exposes input_ref with independent ARTIFACT UUID and original-byte SHA, source_binding, SIMULATION and production_eligible=false. It exposes no object address.
- Root consumer call: `requireRegisteredInput(admission, prepared.inputArtifactPreparation(), sample.input_ref())` using root-owned opaque Prepared accessor. No producer grant is needed for this read; current source.read AND artifact.read are required.
- Root owns SimulationEvaluationService, EvaluationService/EvaluationReferences and rule integration. Do not compare SOURCE UUID with sample ARTIFACT UUID.

## Required root integration increments

1. `business/medical-data/src/main/resources/db/changelog/master.xml`:
   `<include file="db/migration/063-simulation-input-artifact.sql" relativeToChangelogFile="false"/>`
2. coverage.json native_sql_migrations: append `db/migration/063-simulation-input-artifact.sql`; appropriate tenant table coverage group adds `simulation_fixture_input_artifact` and `simulation_input_artifact_operation`.
3. TenantScope.requireSubjectParameter mapper prefix list: add `com.ruoyi.medical.persistence.mapper.SimulationInputArtifactMapper.`. Every statement receives userId; tenant remains server interceptor-injected. No explicit caller tenant SQL parameter.
4. Root integrates fixed profile ONLY for derived SIMULATION_EVALUATION + exact ExactInput schema. `SimulationInputArtifactProfile.profile()` describes schema digest / CONFIGURATION owner / configuration.read semantics (or the explicit legacy same-scope SOURCE/source.read profile); it is not a grant. Other artifact profiles remain deployment-configured. Keep scope, environment, region, source rights and artifact.read gates.
5. Service and controller enabled only by `medical.simulation-input-artifact.enabled=true`. Enabling code is not permission provisioning; deployment, actual grants and source eligibility remain separate.

## Immutable bytes, rights and lineage

Registration calls existing opaque prepared-fixture flow: full immutable object read, raw hash verification, ExactInput schema and fixture/config/parameters validation; rechecks current preparation inside transaction before and after binding verification. ARTIFACT ObjectRef equals original SOURCE ObjectRef including key/version/raw SHA/size/media type. No object copy or upload. Canonical fixture input_sha256 does NOT substitute for raw original SHA.

Access-set members cover complete CASE and INPUT ancestor closure, with every current source's rights. Lineage equality rejects dropped/extra ancestors, and reference authority validates actual owners/scope. Fixed known ExactInput profile or configured upstream artifact profile is carried into dependency verification. No blanket profile, scope, purpose or grant fallback.

The read verifier performs no registration/audit writes. It revalidates preparation, exact fixture link/tenant/project/source/rights, independent ARTIFACT identity, raw digest, original ObjectRef, schema, source and artifact read grants, full lineage/access manifest, and current preparation again.

## Idempotency and recovery

Business identity: tenant + generated non-null project_key + case + model config; fixture row is locked before lookup/creation. New artifact/access set/link are inserted once. Additive 063 UNIQUE/FK constraints bind actual tenant-scoped fixture/source-rights/artifact/creator. No updates/deletes/upserts.

Request recovery identity: tenant + actor + Idempotency-Key. Request SHA binds route/case, canonical request body, context revision, session ID and extension schema digest. A changed body/session/context cannot reuse the old key. Client must retain original key/body/context; if context/session changes, first re-establish authority and deliberately issue a new operation key, never silently rewrite a pending operation. Same business link remains unique.

Replay rereads original source bytes and current authority, verifies stored receipt against recomputed current immutable binding, and returns identical receipt data. HTTP envelope request_id is per response. First creation emits one audit event and one outbox event in the same transaction; replay/new key for existing link does not duplicate domain creation events. Audit failure must roll back all inserts. Mock tests prove error propagation, not database rollback or concurrency.

## Tests and remaining verification

Focused suites: SimulationInputArtifactServiceTest and SimulationInputArtifactBoundaryTest. Cover raw versus canonical SHA, ObjectRef version, SOURCE/artifact confusion, current rights/tenant/project, revocation, read-versus-producer grants, replay/conflict, lineage failure, audit failure, strict body/headers/size, fixed profile and actual mapper XML parsing.

Root runs serialized Maven. Real MySQL DDL/FKs/locking/concurrent unique registration/rollback, actual S3 object verification and HTTP session/CSRF chain are unavailable and must be reported separately. Existing shared-contract bytes and 055 remain unchanged. 064 belongs to independent simulation-rule dependency-use work.


## Lookup and cross-scope increment (2026-10-18)

POST `/api/simulation-fixtures/{caseId}/input-artifact-lookups` accepts `{schema_version, original_key, original_request}` with current Schema/context/CSRF headers, no new Idempotency-Key header, no query, and a 128 KiB lookup-envelope limit; the nested original_request canonical bytes remain capped at the original 64 KiB. Registration itself remains capped at 64 KiB. The separate `simulation-input-artifact-lookup.schema.json` preserves original registration Schema bytes and its write request-hash algorithm. `original_request` is also validated by the existing registration Request definition.

Envelope data is `{schema_version, status: FOUND|NOT_FOUND, receipt}`. FOUND returns the original registered Receipt after fresh verification; NOT_FOUND returns null. No universal operation UUID and no fictional expiration state exist. NOT_FOUND is only absence in the current actor/tenant key namespace; it is not permission to automatically issue a new registration key.

063 operation now stores canonical_request_json (case_id + full original Request) and request_body_sha256. MySQL JSON may reformat/reorder its serialized representation: reread parses strictly and recomputes canonical content/hash, not equality to database JSON whitespace. Original context/session-bound request_sha256 remains unchanged and still protects write replay. Lookup verifies body identity without requiring the former session/context to remain active. It rereads operation identity before and after actual source preparation, verifies full current source/artifact/config permissions, raw object identity/rights/lineage, then recomputes and compares the entire original Receipt. There are no insert/update/delete/audit/producer-grant calls in lookup.

New ARTIFACT bindings use actual prepared CONFIGURATION/config_id, with fixture project P unchanged. SOURCE producer/read permission scopes use the source's real ownerProjectId (which may be null for enterprise sources), while current source rights and project.read still apply through the source authority. This allows enterprise source inputs for project fixtures without erasing project scope or relaxing ArtifactRegistry consistency. `profile()` defaults to CONFIGURATION; `profile(resourceKind)` accepts only CONFIGURATION and legacy SOURCE. Legacy SOURCE binding read requires source owner project == fixture project, plus exact source identity. Any other kind fails closed. Owner configuration revision/ID/project are checked against the opaque prepared configuration, never caller assertions.

New focused suite: SimulationInputArtifactLookupTest. Tests cover changed session/context, missing key, wrong actor/tenant, changed request, database JSON formatting, body hash corruption, revocation/object change, enterprise source with project configuration owner, legacy same-scope-only binding, and strict HTTP/Schema. Scope-call tests use doubles; they do not claim full real-DB authorization-chain integration.
