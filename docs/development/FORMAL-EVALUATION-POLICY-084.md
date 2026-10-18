# 084 formal statistical policy producer

Staged only in this directory. Default off. No Maven, shared repository edits, commit, push, migration application, real provider, qualified reviewer, medical threshold or production acceptance was performed/generated.

## Exact copy map

From java/ to business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/:
- EvaluationAcceptancePolicyService.java (new safe replacement; do NOT use the paused a/java version)
- FormalEvaluationPolicyInputs.java
- FormalEvaluationPolicySchema.java
- FormalEvaluationPolicyController.java

From java/FormalEvaluationPolicyMapper.java to the persistence mapper package.
From mapper/FormalEvaluationPolicyMapper.xml to business/medical-persistence/src/main/resources/medical-mappers/.
From resources/formal-evaluation-policy.schema.json to persistence resources.
From storage/084-formal-evaluation-policy.sql to business/medical-data/src/main/resources/db/migration/ and append only 084 to the migration master/coverage.

No frozen catalogue, existing 001–074 migration or old073 schema is changed. Statistical business Policy remains the existing immutable evidence schema. Its wrapper is the new closed urn:medical:formal-evaluation-policy:1.0.0 schema.

### Required actual dependencies (not substitutes)

- A /inputs/EvaluationAcceptanceInputSchema.java and evaluation-acceptance-inputs.schema.json.
- A /typed-inputs/EvaluationAcceptanceObjectReader.java, real bounded transaction-external original GET and private recheck.
- A /java/AgentPublicationDependencies.java complete TypedUse tree, plus A /patches/VersionResourceAuthority.java per-version exact purpose patch.
- A /formal-input/FormalEvaluationInputService.java final private VerifiedInput change: readVerified(subject,context,id), document(), receipt(), requireCurrent(a). The original079 registration/GET/operation/audit/outbox are verified again in the final084 transaction. Public read JSON is never treated as an authority. 079 schema/helper/mapper and original producer must be present; the optional service must be enabled to author with079 inputs.
- Actual080 FormalAgentCandidateAuthority AND FormalAgentCandidateDependencies. The first verifies original candidate identity, while the second verifies every typed capability/template/rule/runtime source dependency.
- Actual078 AgentExecutionPolicyAuthority including HistoricalPolicy.authorizations() and dependencyVersions(). Current writing requires independently approved current policy; historical inspection uses historical read authority.
- Actual EvaluationRuleAuthority/RuleVersion records; full approved expected binding and recursively original base-rule lineage.
- Existing actual AgentPublicationJournal/mapper and073 immutable operation, audit, outbox/decision tables.
- Correct configured EvaluationDependencyPolicy artifact schema profiles for actual frozen SampleManifest, ExpectedItems and079 input. Missing profiles fail closed; no accepting fallback was installed.

### Merge shared changes, do not overwrite files

The patches/ Java files are focused test copies from root-registry079, not authoritative full files to replace ongoing shared work.
1. TenantScope.requireSubjectParameter mapper prefix allowlist: append com.ruoyi.medical.persistence.mapper.FormalEvaluationPolicyMapper. Both methods require current userId, never historical creator substitution.
2. ProtectedTenantInterceptor immutable set: append formal_evaluation_policy_input. Preserve all other workers' entries. Real tests exercise tenant injection and deny UPDATE/DELETE/explicit tenant spoof.
3. Register authenticated API routes and leave Spring CSRF validation enabled for both POST routes. A header presence check is supplementary, not token validation. Preserve existing authentication exception policy.
4. Keep medical.formal-evaluation-policy.enabled absent/false. No deployment configuration or provider access is enabled here.

## New public API

POST /api/formal-evaluation-policies → Author, 201 receipt.
POST /api/formal-evaluation-policy-decisions → Decide, 201 receipt.
GET /api/formal-evaluation-policies/{id} → current-authorized historical read, no production eligibility.
GET /api/formal-evaluation-policies/{id}/operation-receipts/{operation} → creator-only original receipt after full current read, original operation exact.

All use authenticated current session, X-Schema-Version:1.0.0, X-Context-Revision, no query parameters, no-store. Writes require the original bounded Idempotency-Key, actual Spring CSRF and a maximum 1 MiB UTF8 body with duplicate/trailing JSON rejection. No new list/search/availability endpoint is claimed.

Author is closed:
- schema_version:1.0.0
- specification: 084-local closed PolicyAuthor, with required expert_domains in addition to the frozen statistical fields (all thresholds and domain requirements supplied by the human before evaluation)
- root_uses: nonempty max512 [{version:{version_id,content_sha256},purpose}]; exactly one explicit fixed purpose per actual root
- sampling_assessment: human reason/assessment, not a machine independence assertion

Root uses must cover exact dataset/design/manifest/input/expected/source/evidence/rule(+base-rule/basis) roots. Extra, missing, duplicate, conflicting versions/hashes/purposes fail. Candidate purposes are governed by the real078 policy and complete080 dependency authority. Inputs must use the approved078 businessPurpose. Governance source uses remain explicit independently approved purposes; the service never infers permission from a source rights row. Scope/runtime hashes remain human fixed targets and runtime matches actual candidate. No mutable SIM artifact or generic CAPABILITY_VERSION can substitute for a typed dataset, manifest, design, input, candidate or rule.

Decide is closed: schema_version,draft_id,draft_sha256,decision(APPROVE/REJECT),sampling_assessment,reason. The decision binds the exact draft and immutable typed snapshots. Reviewer cannot be the policy author or any candidate author. One immutable decision per draft. This records a real authorized human decision about statistical assumptions; distinct families/UUIDs alone do not establish independence or qualifications. Expert qualification/score approval remains081.

## Original typed evidence

Actual dataset/design SOURCE objects use exact original objectKey/objectVersion/raw SHA and parsed closed typed content. Pretty JSON is allowed and its raw SHA is kept distinct from canonical SHA. Frozen SampleManifest/ExpectedItems must be canonical exact originals. Actual079 input read proofs recheck full registration/rights/GET/audit/outbox. All sample source ancestors must exactly equal declared dataset sources, with cross-family source raw-hash alias rejection. Complete current typed graph, candidate closure and rule/base-rule witnesses are rechecked in transaction.

Aggregate downloaded canonical content is limited 16 MiB, final immutable snapshot 32 MiB, individual document 2 MiB, predeclared evaluation matrix 10,000 cells. These are bounded offline verification limits, not external execution approval.

New supplemental table stores canonical snapshot, snapshot hash, exact policy record/content registry identity and both new schema hashes. Same journal content/audit/outbox binds snapshot hash, root uses and human assessment. Journal original-key recovery compares the complete canonical original request. Changed source rights/object versions during GET/final recheck abort; no public success flag constructs the private proof.

Historical read performs current content/permission checks, including historical078 dependency authority, without demanding current execution eligibility. Approved acceptance/execution preparation uses current approved authority. A read result never becomes a production permit.

## Exact two-phase consumer API

EvaluationAcceptancePolicyService.prepare(ApplicationSessionIdentity.Verified subject, String context, String id, String hash) throws Exception → private-constructor PreparedPolicy. Call outside a transaction; it performs actual original GET.

EvaluationAcceptancePolicyService.require(EnterpriseAccess.Admission a, String id, String hash, PreparedPolicy prepared) throws IOException → private-constructor VerifiedPolicy. Call within the current tenant transaction. It compares original decision/draft/typed snapshots and rechecks all current input proofs.

VerifiedPolicy exposes policy():EvaluationAcceptanceStatisticsProtocol.Policy, decisionId(), contentSha256(), classification(), expertDomain(String dimension), authorizations(), dependencyVersions(), requireUnchanged().

Mandatory consumer: requireConsumer(a, EvaluationProtocol.Definition). This is the only public consumer-binding method. It requires approval strictly before original evaluation.created_at; exact tenant/project/dataset/manifest/rule/candidate list/samples/dimensions/repeats; PRODUCTION environment. It must be called by080 or083 against the actual original definition snapshot. There is deliberately no public project-only binding that skips predeclaration chronology.

contentSha256 identifies the wrapper decision record (id/hash used for prepare/require). The statistical business policy digest required by the pure engine is the canonical hash of policy(), not the wrapper hash. The wrapper fixes the exact business policy object. No executed run or final score is inferred from it.

## Verification

run-tests.sh compiles actual staged production dependencies with JDK17 and runs new focused tests; test-results.txt. The tests explicitly distinguish:
- Real bounded GET/domain/source-rights tests using actual reader and actual graph authorities, synthetic DB/local HTTP fixture.
- Real immutable policy/journal tests over explicit storage/private-proof substitutes.
- Composition tests joining typed documents/current proof paths using explicit private-authority test doubles. These are not full database+079+078+080 E2E tests.
- Real MyBatis XML/tenant guard and HTTP boundary tests.

7 SQL tests in sql-results.txt use actual JSQLParser and SQLGlot, check additive DDL, FK parent uniqueness/types, immutable columns and SQL mappings. No MySQL execution is claimed.

Underlying A suites remain required: typed input 50, original GET 38, mixed purpose 37,079 private-proof 116,078 92, statistics 39. Root should rerun combined Java/SQL/HTTP/security coverage after integrating all shared changes. No complete formal runtime/evaluation or production acceptance is claimed by084 alone.

Original focused baseline: 61/61 Java tests and 7/7 SQL tests passed. Copy the new test classes/PolicyStorageFixture into the test package if integrating tests; they additionally require A EvaluationAcceptanceInputSchemaTest, AgentPublicationMixedPurposeTest and EvaluationAcceptanceObjectReaderTest fixture sources. The standalone runner uses those exact real sources. Mapper tests load the actual classpath mapper resource.

## 2026-10-18 084 follow-up freeze

Local schema SHA256: d772e6f7a2392ddb0e3d54fb139ef0f6cbf9f513a449d8ced09165f8583e772f.

The required specification.expert_domains object maps each declared EVAL-01 through EVAL-12 dimension to an exact nonblank qualification domain (1–128 Unicode codepoints). Its keys must equal the declared dimension set: no missing, additional, unknown or duplicated dimensions; no null/non-string/blank/control/unpaired-surrogate domain. Domain text is preserved exactly. This is a predeclared requirement, not a grant of expertise. The independent decision binds the complete original draft hash; the decision request cannot supply replacement domains. expertDomain rejects an absent dimension with no default.

The original statistical Rule/Policy and frozen073 PolicyAuthor bytes are unchanged. Only after validating the complete084 wrapper does the input composer remove expert_domains before invoking the old pure input checker; the statistical Policy projection removes expert_domains and reason. The immutable original author and its audit/hash retain the domains.

Historical API (new distinct authority type):
- prepareHistorical(Verified subject,String context,String id,String hash) throws Exception -> private-constructor PreparedPolicy, explicitly historical.
- requireHistorical(Admission a,String id,String hash,PreparedPolicy prepared) throws IOException -> private-constructor HistoricalPolicy.
- HistoricalPolicy exposes policy(), decisionId(), contentSha256(), classification(), expertDomain(String), authorizations(), dependencyVersions(), requireConsumer(a,EvaluationProtocol.Definition), requireUnchanged().

Historical preparation performs the actual input GET outside a transaction, uses inputs.prepare(false), and rechecks complete original journal/draft/APPROVE/snapshot plus current content permissions within the consumer transaction. It tolerates current execution ineligibility, never missing original approval, current read revocation, changed input snapshot, wrong hash/admission or post-evaluation approval. Active require rejects historical PreparedPolicy; historical require rejects active PreparedPolicy. HistoricalPolicy is unrelated to VerifiedPolicy and cannot enter a new acceptance path. Public read JSON is never its proof input.

Classification uses actual metadata across all checked SOURCE/ARTIFACT/RULE versions, actual SOURCE-owner and rights-witness classification, and candidate metadata. The exact classifications are fixed in the snapshot and rechecked on every current read. New dependencyClassification mapper SQL joins exact version/hash/tenant, disables caches and requires current userId. FormalEvaluationPolicyInputs now additionally injects FormalEvaluationPolicyMapper. Journal classification is max(configured lower bound, actual input snapshot, original draft for a decision); content and historical row classification are checked on read. Only PUBLIC/INTERNAL/RESTRICTED/PERSONAL_HEALTH are accepted. Consumers that include policy authorization data must include proof.classification() in their own maximum classification.

### Exact incremental copy mapping

Copy these modified files from this staging directory, replacing their084 counterparts only:
- java/EvaluationAcceptancePolicyService.java, java/FormalEvaluationPolicyInputs.java and java/FormalEvaluationPolicySchema.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/.
- java/FormalEvaluationPolicyMapper.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/.
- mapper/FormalEvaluationPolicyMapper.xml -> business/medical-persistence/src/main/resources/medical-mappers/.
- resources/formal-evaluation-policy.schema.json -> business/medical-persistence/src/main/resources/.
- tests/PolicyStorageFixture.java, tests/FormalEvaluationPolicyServiceTest.java, tests/FormalEvaluationPolicySchemaTest.java, tests/FormalEvaluationPolicyInputsCompositionTest.java, tests/FormalEvaluationPolicyTypedReferenceTest.java, tests/FormalEvaluationPolicyMapperTest.java -> corresponding persistence test package.
- tests/test_policy_migration.py remains an isolated staging SQL test; its explicit staging root must be adapted for permanent repository integration.

No084 DDL change, no081/shared/frozen statistical source change, no Maven or deployment. Mapper has exactly three actual statements: snapshot, insertSnapshot, dependencyClassification. Test fixtures distinguish real policy/journal/schema/guard code from explicit typed-authority and DB doubles; they do not establish external expert qualification or production execution.

Final follow-up focused run: 86/86 Java tests and 7/7 SQL tests passed. Logs: test-results.txt and sql-results.txt. Domains-only checkpoint 70/70; historical checkpoint 77/77.
