# 086 formal acceptance, publication and original score-binding preparation

Frozen 2026-10-18 UTC. Copy only this directory, not older a/publication/storage/080 draft. 080 remains the one-table candidate package. 086 adds five independent immutable tables.

## Verified and remaining boundaries
- Isolated JDK17 compilation and joint JUnit: **143/143**, 17 classes. Includes24 existing080 and21 existing081 tests; **14 new classes / 98 new tests**.
- Actual service, schema, journal, intent/attempt, registration, lifecycle, physical transport proof and HTTP projections exercised. Storage/current-authority fixtures explicitly named. No deployed DB/S3/HTTP acceptance claim.
- Independent audit:5 tables,25 FK references with actual parent unique keys/types/charset/collation;42 static statements plus expanded dynamic registration columns. Evidence JSON included.
- **FormalEvaluationProfessionalCompletions has no production implementation.** No genuine083 completed professional proof is currently available, so production ACCEPT/publish cannot succeed. This is remaining runtime code, not merely credentials. CP09 connection tests, SIM, technical N00 or public Records cannot replace it.
- Ordinary TASK admissions remain unenabled until parent integration and genuine publication exist.
- Default-disabled controllers. Availability distinguishes registered routes from professional completion. Route flags are not grants or proof that all inputs exist.
- New086 normal frontend screens are not included. Existing080 directory/UI belongs to C and is not overwritten.

## Exact copy
COPY-MAP.json lists **21 new production Java**.
- java/FormalAgentAcceptanceMapper.java to business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/.
- Other java/*.java to persistence/.
- mapper/FormalAgentAcceptanceMapper.xml to src/main/resources/medical-mappers/.
- Three contracts/*.json to src/main/resources/.
- storage/086-agent-publication-lifecycle.sql to business/medical-data/src/main/resources/db/migration/.
- Fourteen tests/*Test.java to src/test/java/com/ruoyi/medical/persistence/.
- Existing required fixtures: PublicationLedgerFixture, PublicationManifestFixture(080); ExpertFixture, ExpertEvaluationFixture, ExpertQualificationServiceTest, ExpertScoreAuthorityTest(081); FormalInputFixture and EvaluationFixture. No class-only substitute.
- Apply five narrow patches under patches/. Do not wholesale-copy shared Java. EvaluationService patch preserves root's current ExecutionDefinitionSnapshot, complete14field grant projection, nested copy and exact scope/thread guards.
- No frozen073/080/081/084 schema or old DDL modifications.

## Root registrations
Add migration086; root coordinates ordering with separately accepted087/088.
Five new immutable tenant tables: formal_agent_acceptance, formal_agent_publication_intent, formal_agent_publication_attempt, formal_agent_publication_registration, formal_agent_publication_disable.
Coverage increases by5 business and5 total tables against actual current master; do not use stale absolute counts.
Mapper subject prefix com.ruoyi.medical.persistence.mapper.FormalAgentAcceptanceMapper.
IDs: acceptance, insertAcceptance, decideCandidate.
Maps carry actual current userId; creator only historical filtering. JDBC uses explicit tenant/original creator/key/project ownership/current permission.

Extend sealed RuntimeObjectAttempt permits only with FormalAgentPublicationObjectLedger.Attempt. Existing SourceUploadTransport.writeRuntimeObject supplies actual approved store, bounded concurrency/fresh guards. No new credentials.
ArtifactRegistry.read must call FormalPublicationArtifactBoundary before allowing release-manifest kind/schema. Typed publication reads independently validate complete current governance/source/object ledger. Never automatically grant new artifact.read.

## Public routes
Always authenticated GET /api/formal-agent-publication-feature-availability.
Closed fields schema_version,current_context,features:
acceptance_routes,publication_routes,professional_completion_registered.
Exact bean registration inspection, allowEagerInit=false, no arbitrary settings/grants.

medical.formal-agent-publication.enabled=false:
- POST /api/formal-agent-publications (frozen080 Publish)
- GET /api/formal-agent-publication-operations/{originalKey}
- POST /api/formal-agent-publications/disable (frozen080 Disable)

Both medical.formal-agent-acceptance.enabled=false AND medical.expert-qualification.enabled=false must be explicitly enabled:
- POST /api/formal-agent-acceptance-decisions (frozen080 Accept)
- GET /api/formal-agent-acceptance-records/{recordId}
- GET /api/formal-agent-acceptance-records/{recordId}/operation-receipts/{operationId}
- POST /api/formal-evaluation-score-bindings
- POST /api/formal-evaluation-score-bindings/recovery

All actual authenticated session, X-Schema-Version=1.0.0, X-Context-Revision. Mutations original Idempotency-Key and existing CSRF middleware. No query parameters. Body1MiB; recovery2MiB with inner1MiB. Recovery rejects Idempotency-Key header and uses original_key in closed body, creates no operation/audit/cancel. Root adds exact routes to authenticated protocol/router metadata, no security exemption.
Dependency unavailable503; schema/content422; CAS/context/key409; access403. Cache-control no-store.

Publication receipt independent closed local schema:
PROCESSING: schema_version,state,operation_id,release_id,request_body_sha256.
COMPLETED additionally artifact_id,manifest_sha256.
Read-only lookup never finishes/writes uncertain operations. Original mutation retry may recover existing physical attempt, never second write. Original5minute intent cannot reset.

Formal score binding request: schema_version,evaluation VersionRef,statistics_policy VersionRef,binding=unchanged081 BindScore.
Recovery: schema_version,original_key,request=entire original outer body.
Receipt: schema_version,original_key,request_body_sha256,receipt=unchanged real081 receipt.
Same key/same inner score with changed outer policy conflicts. Old081 without formal_preparation cannot become new formal-binding success.

## Proof and authorization
Original EvaluationService.load(false) behavior preserved. New actual evaluation.read metadata-only method exposes no result/score/acceptance proof. Outside transaction084/083 obtain original objects. Same final Admission consumes private ReadPermit bound to entire frozen Definition and current typed roots/rights, then original score/verdict/result recomputation.
Every output needs083 same-cell requireProfessionalOutput.081 still verifies original scorer, atomic original score authority witness, independent qualification/domain/time/current read. No scores/qualifications created.
Private formal_preparation is atomically included in original081 authorizations hash/audit/outbox, binding exact outer body/definition/policy/execution witnesses. Historical recovery may lose score-write grant but needs current read/qualification. Legacy081 entry unchanged.
Publication requires independent actual agent.publish, artifact.register on actual owner, current complete dependencies. Candidate author cannot publish. Current/historical084/083 proofs distinct. Execution onlyPUBLISHED; disabled history checks original object/registration/disable journal. Disable one-wayCAS, no enable.

## Reproduction
Staging runner /tmp/stage22-task-runtime/a/publication/run-086-tests.sh.
Final run all16 local test classes plus original ExpertScoreAuthorityTest:143/143.
Root Maven must compile delivered source plus formal073/078/079/080/081/083/084, no staging class dependency.
No Maven/commit/push/deployment/credentials/grants changed by this worker.
