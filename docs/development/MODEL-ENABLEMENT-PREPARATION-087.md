# 087: independent MODEL_ENABLEMENT policy preparation

## Result and exact wire contract

This is a separate producer for the existing B077 `ModelPolicyBasisAuthority.Evidence`. It never enables a model, creates a grant, publishes approval, configures credentials, calls a provider or converts085 evidence/purpose.

- GET `/api/model-enablement-policy-preparation-candidates/{modelRevision}` returns one actual current project/model/profile/policy SOURCE/right/parse tuple, with `original_validation=NOT_READ`. It performs authenticated bounded metadata reads only. Missing permission/readiness uses the real structured error; no invented candidate or missing-reason placeholder.
- POST `/api/model-enablement-policy-preparations` accepts the closed `Register` schema (7 fields) and original `Idempotency-Key`/`X-Context-Revision`.
- GET `/api/model-enablement-policy-preparations/{preparationId}` returns `{receipt}` after actual current rights/model/profile/source/FORMAL snapshot validation and bounded original GET.
- GET `/api/model-enablement-policy-preparations/operations/{operationId}` is creator-only typed recovery: receipt, original_request, original_idempotency_key, original_context_revision. The current authenticated session/context must still be valid; originals are never replaced with current caller metadata.
- Generic operation lookup name is exactly `MODEL-ENABLEMENT-POLICY-PREPARE`; operation_record.target_id/resource_id is preparation_id. Original generic lookup followed by typed operation read is the recovery path. Receipt.data_policy_evidence is exactly B077's source_version/rights_revision/project_id/snapshot_id/parse_version. No B077 or076 schema change.
- Wire schema: `resources/model-enablement-policy-preparation.schema.json`. Candidate, Register, Receipt, Read and Recovery reject additional properties. All four controller routes use existing strict authenticated header checks/no-store envelopes; POST is bounded8192 bytes.

## Real entry and persistence

Actual current external project-owned ModelConfigurationService definition must declare exactly the requested policy_basis; actual CONFIGURATION model.enable/MODEL_ENABLEMENT authorization is mandatory. Active current project profile, source ownership (same PROJECT or ENTERPRISE), approved MODEL_ENABLEMENT source rights and exact selected parse/index/approval/binding are independently required. Source policy original is bounded2MiB, physically version/hash pinned and read with the real S3ObjectReader. First profile permits only a standalone SOURCE with no hidden upstream ancestry. The policy bytes need no invented document format; existing approved parse/index remains separately authoritative.

One private ScopeSnapshotStore persistence core remains. New package-private save/readForModelEnablement methods hardcode action/purpose; they repeat real current grants and selection before/after persistence. They cannot accept a caller-supplied action, grant or success boolean. Existing task.create, model.verify and evaluation.run entrypoints remain intact. A newly saved snapshot must immediately satisfy actual SourceReadAuthority FORMAL/MODEL_ENABLEMENT and B077 ModelPolicyBasisAuthority.

Preparation, original canonical request/key/context/session, actual object reference, current authorization witness digest, immutable scope basis, receipt, operation/audit/outbox are transactionally persisted. Original-key retry returns the original receipt and does not create another snapshot. Historical operation metadata, original authorizations at creation time, exact event/audit payloads and immutable row/source/object identities are independently checked on reads and recovery.

Classification is the maximum of actual SOURCE owner, rights, model_version, model_configuration_definition and project_profile_revision metadata, with each identity/hash verified. Parse/index/approval/binding have no separate classification columns in the real catalogue; their actual source/snapshot relationship is checked by selection and FORMAL authorities. No synthetic classification rows are created. The stored witness contains model identity only, not its credential reference or provider configuration.

## Exact copy map

Base: `/tmp/stage22-task-runtime/n-verification-preparation/incremental-enablement`.

1. java/ModelEnablementPolicy{Controller,Preparation,Protocol,Schema}.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ (4 new classes)
2. mapper/ModelEnablementPolicyMapper.java -> same module Java mapper/ package
3. resources/medical-mappers/ModelEnablementPolicyMapper.xml -> persistence src/main/resources/medical-mappers/
4. resources/model-enablement-policy-preparation.schema.json -> persistence src/main/resources/
5. storage/087-model-enablement-policy-preparation.sql -> business/medical-data/src/main/resources/db/migration/
6. shared/ScopeSnapshotStore.java.patch -> apply only the087 independent entry/helper additions to root's latest file. Do not overwrite concurrent083 fixes.
7. shared/TenantScope.java.patch -> add ModelEnablementPolicyMapper subject gate to root's latest file.
8. shared/ProtectedTenantInterceptor.java.patch -> add model_enablement_policy_preparation to the actual immutable-table set.
9. tests/ModelEnablementPolicy{Preparation,Http,Mapper}Test.java -> persistence test package. Three unique new test classes. They reuse the already-delivered085 VerificationInputFixture; no duplicate fixture/class copy.
10. SQL migration tests at tests/test_enablement_migration.py; delivery notes as appropriate.

Root adds087 to migration master/catalogue gate. Do not overwrite frozen076/077/085 schema/catalogue bytes. Conditional `medical.model-enablement-policy-preparation.enabled` defaults off; no deployment setting has been changed. Existing real storage DocumentUploadPolicy/transport configuration is a prerequisite, but no provider or tokenizer runtime is required for preparation itself.

## Verification and boundaries

Isolated Java17 javac/JUnit run: tests/run.sh. The five existing ScopeSnapshotLegacyEntryTest cases are rerun against the087 shared core but are NOT new tests. New unique tests are only the three classes in copy map9. Results are in tests/results.txt. SQL tests/results will be frozen with the mapper delivery.

Actual Permission/Resource/Source/rights/selection/lineage/version/snapshot authorities, schemas, B077 FORMAL evidence consumer, bounded physical GET, controller/Spring gates and MyBatis/TenantScope/immutable SQL rewriting are exercised. Test substitutes are explicit mapper storage/session/model/project domain boundaries and transport factory; the original GET goes to an actual local range/version HTTP endpoint. No real MySQL migration/concurrency/rollback or configured production model is claimed. The existing EnterpriseAccess transaction owns rollback; mapper doubles do not emulate rollback. No global Maven, commit/push, secrets, real provider calls, deployment enablement or user-computer access occurred.

### Unique Java test enumeration (final)

- ModelEnablementPolicyPreparationTest:21 new cases.
- ModelEnablementPolicyHttpTest:8 new cases.
- ModelEnablementPolicyMapperTest:6 new cases.
- ScopeSnapshotLegacyEntryTest:5 existing cases rerun, previously delivered with tokenizer; do not copy/count again.
- Total isolated run:40 passed/40,0 skipped. Unique new087 Java tests:35 across3 classes. Latest log tests/results.txt includes actual mapper XML compilation and tenant rewrite tests.

### Final storage verification / freeze

SQL worker31/31 and producer-owner recheck31/31 passed (sql-results.txt, sql-owner-recheck.txt). Actual JSQLParser and SQLGlot validate every mapper statement, actual referenced columns,16 complete foreign keys and parent unique keys/types, metadata/original-key anchors, bounded closed JSON and null-safe required-object fields. No MySQL execution claimed. XML and DDL are frozen with Java at this delivery; source SHA256 inventory is SHA256SUMS. SQL worker implemented storage and tests, producer owner reviewed mapping/constraints and reran; this is not represented as a separate independent whole-package audit.
