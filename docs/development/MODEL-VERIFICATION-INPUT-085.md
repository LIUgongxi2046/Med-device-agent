# 085: independent model verification input preparation

Status: implemented and isolated tests passed. This delivery does not enable the feature, deploy, create credentials, call a model, publish an Agent, create a Task, or qualify a model. It does not change frozen076 requests or published catalogue bytes.

## What now has a real producer

A currently authorized project model verifier can select an existing real PROJECT-owned SOURCE original containing canonical published ExactInput JSON, the actual current project profile, and an explicit approved source scope. Preparation performs the original versioned object GET, validates the actual bytes and schema, and atomically registers a new project EXACT_INPUT artifact with SOURCE binding and a genuine immutable ScopeSnapshot. It does not relabel SIMULATION_EXACT_INPUT or enterprise FORMAL_EVALUATION_INPUT.

The business purpose must explicitly be DOCUMENT_BODY or MODEL_VERIFICATION and match current source rights. The model.verify grant is independently checked on the actual CONFIGURATION owner with MODEL_VERIFICATION purpose. Source READ and ENTERPRISE_USE, current project/source grants, parse/index/approval/applicability and lifecycle, artifact.register and artifact.read remain separate. External MODEL_SEND, provider, region, source.external.process, budget admission, tokenization and provider dispatch remain076 responsibilities.

The current MODEL_CONFIG is obtained through ModelConfigurationService, must be project-owned, and must equal the requested current immutable revision. The actual active fixed profile is checked through ProjectService. No UUIDs, profile revisions, parse IDs or source approval IDs are invented. The scope snapshot UUID is a newly created output identity.

This first source input profile supports one standalone original SOURCE ancestry. Unknown governance/source ancestors and upstream ARTIFACT profiles fail closed. The snapshot can contain the separately selected authorized source set. Preparation requires its chosen ExactInput source to be in that set. Network-enabled preparation is explicitly unsupported by the085 schema.

## Stable HTTP contract

All routes use the existing authenticated session and CSRF protection. Exactly one X-Schema-Version: 1.0.0 and X-Context-Revision are required. POST additionally requires the original Idempotency-Key and X-CSRF-TOKEN. No query parameters or GET bodies. Replies are no-store standard envelopes.

- GET /api/model-verification-input-candidates/{modelRevision}/{businessPurpose}
- POST /api/model-verification-inputs
- GET /api/model-verification-inputs/{artifactId}
- GET /api/model-verification-inputs/operations/{operationId}

The complete closed schema is resources/model-verification-input-workflow.schema.json. POST Register fields:

- schema_version
- project_id
- model_config_revision
- profile_revision
- source: {version_id, content_sha256}
- rights_revision
- business_purpose: DOCUMENT_BODY | MODEL_VERIFICATION
- scope_selection: {mode, collection_versions, source_versions, network_enabled:false, network_policy_revision:null, approved_query_revision:null}

For a single explicit candidate use SOURCE_VERSIONS with that source.version_id. The supported scope modes are the actual shared values PROJECT_APPROVED, COLLECTION_VERSIONS, SOURCE_VERSIONS and COMBINED.

Candidates returns the actual project and fixed profile_basis, selected model_config_revision and purpose, plus sources[{source_id, source Basis, rights_revision, size_bytes, media_type, content_validation:NOT_READ}]. It performs no object GET, snapshot/artifact write or model request. A candidate is metadata eligibility only; original bytes still need validation. missing_input_reasons is exactly one of NO_CURRENT_PROJECT_SOURCE_CANDIDATE or EXACT_INPUT_CONTENT_VALIDATION_REQUIRED. The bounded enumeration rejects excess scope rather than truncating and claiming completeness.

Receipt fields include the actual exact_input ArtifactRef with frozen076 coverage names selection_digest/delivered_digest, scope_snapshot_basis, source/right/purpose, project/model/profile, request_body_sha256, operation_id and original creator/time. These two references can be copied directly into an otherwise genuinely populated076 VerificationRequest. No other076 fields are invented.

Read returns {receipt, document}; document is the currently reauthorized original ExactInput. Creator-only operation recovery additionally returns original_request, original_idempotency_key and original_context_revision. It returns the persisted originals, never a substituted request/key from the current session. The generic POST /api/operation-lookups accepts operation_id MODEL-VERIFICATION-INPUT-PREPARE with the original key; operation.resource_id is artifact_id. A completed result's real operation_id is the typed recovery route input.

Bounds: original canonical UTF-8 input <=2MiB and <=configured document maximum; request <=8192 bytes; selected sources <=deployment maximum; candidates <=128; stored authorization <=4MiB; receipt <=16384 bytes. Current reference rechecks wrap the actual S3 range GET. No mutable cursor cache or client boolean is treated as authorization.

## Transaction and original witnesses

After actual original GET, a new admitted transaction locks the current actor and selected SOURCE, rechecks current authorization, and looks up original actor/name/key. Same key/request returns the original receipt and does not create another snapshot. Changed request conflicts. The operation hash binds operation name, tenant, actor, workflow hash and canonical request. Original context/session are retained separately, allowing legitimate current-session recovery without rewriting history.

A new transaction saves the immutable snapshot, dependency manifest/set/members, original-object artifact, SOURCE binding, exact source-only lineage, COMPLETED201 operation, audit, outbox and immutable085 registration. Readback checks request/key hashes, model/source ownership, original source/register/model.verify witnesses at original time, original operation/audit/outbox, artifact and dependency identity, snapshot manifest/member/parent identity and all current authorizations. Artifact.read is checked by the actual registry; no permission is created.

## ONE shared snapshot persistence core

shared/ScopeSnapshotStore.java preserves the old save(Admission, ValidatedCreate, ...) task entry and moves its persistence into one private persist method. New package-private domain entries:

- saveForModelVerification(a, project, profileRevision, SourceScope, configId, businessPurpose, environment, region, limits)
- saveForEvaluation(a, project, profileRevision, SourceScope, evaluationId, businessPurpose, environment, region, limits)
- readForModelVerification(same args, PermissionProtocol.Basis)
- readForEvaluation(same args, PermissionProtocol.Basis)

Return type is existing package-private Stored(snapshot, manifest, selection, dependencyVersions). Entries independently authorize hardcoded model.verify/MODEL_VERIFICATION or evaluation.run/EVALUATION_RESULTS, real active fixed profile and complete source selection. No caller-supplied grant, action string or success boolean is accepted. Consumers additionally bind config/evaluation identity to their real domain definition and approved policy before/after calling. L083 was given this exact API and owns its evaluation definition/policy proof; any083 network approval remains its consumer's responsibility.

Read methods use three new bounded ScopeSnapshotWriteMapper selects and verify exact current parse/index/approval/binding, not only source and rights. Persistence stays private. There is no duplicate evaluation/model snapshot implementation.

## Exact copy map

From this delivery directory:

1. java/ModelVerificationInput*.java (7 files) -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/
2. mapper/ModelVerificationInputMapper.java -> same /mapper/ Java package
3. mapper/ModelVerificationInputMapper.xml -> business/medical-persistence/src/main/resources/medical-mappers/
4. resources/model-verification-input-workflow.schema.json -> persistence resources root
5. storage/085-model-verification-input.sql -> business/medical-data/src/main/resources/db/migration/
6. shared/ScopeSnapshotStore.java -> persistence Java package
7. shared/ScopeSnapshotWriteMapper.java and .xml -> mapper Java package and medical-mappers resources respectively
8. shared/TenantScope.patch: add only ModelVerificationInputMapper subject-parameter prefix to root's latest file
9. shared/ProtectedTenantInterceptor.patch: add only model_verification_input_preparation to root's latest immutable-table set
10. tests/{VerificationInputFixture,ModelVerificationInputServiceTest,ModelVerificationInputSchemaTest,ModelVerificationInputMapperTest,ModelVerificationInputHttpTest}.java -> persistence test package
11. DELIVERY-085.md -> appropriate project delivery documentation

ScopeSnapshotStore and its mapper patches are also supplied for review. The TenantScope/interceptor full copies were refreshed from root's current075..082 integration solely for isolated compilation; prefer the one-line patches if root changes those files again. Never replace newer unrelated shared work.

Root-owned integration: add085 to the master changelog and add its one new table to migration coverage. No existing migration or permission seed changes. Default feature remains off via conditional medical.model-verification-input.enabled; this delivery has not set it. Existing actual DocumentUploadPolicy/object-storage transport must be configured separately; artifact schema manifest is read from that real configured policy, while schema_document_sha256 is the frozen published ExactInput schema digest.

## Verification

- tests/run.sh: 50/50 focused Java tests passed, 0 skipped.
- tests/test_verification_migration.py: 23/23 SQLGlot/JSQLParser migration and mapping checks passed.
- All new actual Java classes and shared patches compile with the existing isolated Java17 classpath.
- Real PermissionAuthority, ResourceReadAuthority, SourceReadAuthority, SourceRightsSchema, ScopeSelectionAuthority, DependencyLineage, ArtifactRegistry, VersionResourceAuthority, shared snapshot persistence/schema, request schemas, TenantScope/interceptor and actual MyBatis XML parsing are exercised.
- GET uses the real S3ObjectReader against a local loopback HTTP Range service with exact object version/hash and cleanup. Source ownership, rights/purpose separation, revoked permissions, absent source/index, changed bytes, current profile/snapshot identity, extra snapshot parents, original key/session recovery, audit/outbox corruption, no task/agent requirement, evaluation/model entry separation, no provider permission creation, no-side-effect candidate reads, strict HTTP headers/body bounds, conditional feature wiring and schema rejection are covered.
- Explicit test substitutes: DB mapper storage, session admission, ModelConfigurationService and ProjectService domain reads, and transport factory. Actual current source/selection authorities and physical GET are not stubbed. Tests therefore do not prove a real MySQL transaction/migration/concurrency run or a real configured model/profile deployment. Rollback remains the existing EnterpriseAccess transaction boundary; simulated mapper storage does not emulate rollback.
- No global Maven/build, formal repository write, commit, push, external storage call, provider call, credential operation, deployment or user computer access occurred in this delivery.

Root fixed the discovered076 coverage consumer bug in its formal source; frozen E staging still has the old CommonProtocol *_sha256 comparison. Do not overwrite root's fix with frozen E staging.

## Remaining boundaries

085 resolves the first-use ExactInput/snapshot producer cycle. It is not a complete model-verification request generator. Actual tokenizer executable, ROLE/PROMPT, nonempty approved rules, enterprise/project budget revisions, DeepSeek policy and reasoning=none parameters, and enablement policy tuples still come from their real domain readers and076 admission. If those current candidates are missing, the UI must keep the corresponding action disabled with the actual missing input; neither this service nor its candidate response manufactures them.
