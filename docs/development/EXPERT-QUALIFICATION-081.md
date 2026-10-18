# 081: human expert qualification and exact original-score binding

Status: 73/73 isolated Java tests and 18/18 real SQL contract tests passed; 5 tables, 29 FKs, 34 mapper statements. Isolated implementation; no formal copy, Maven, live permission creation, or clinical qualification assertion. The independent human governance decision and the actual original score are recorded; formal execution evidence remains a separate 083/080 requirement.

## Exact copy list

From this directory:
- Eleven top-level `*.java` files to `business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/`.
- `mapper/ExpertQualificationMapper.java` to that Java package's `mapper/`.
- `mapper/ExpertQualificationMapper.xml` to `business/medical-persistence/src/main/resources/medical-mappers/`.
- `expert-qualification.schema.json` to `business/medical-persistence/src/main/resources/`.
- `081-expert-qualification.sql` to `business/medical-data/src/main/resources/db/migration/`.
- `tests/Expert*Test.java`, `tests/ExpertFixture.java`, and `tests/ExpertEvaluationFixture.java` to the persistence test package. The isolated tests reuse the real 079 `FormalInputFixture` and frozen `EvaluationFixture` test helpers; do not create duplicate helper classes.
- Apply `patches/TenantScope.patch` and `patches/ProtectedTenantInterceptor.patch` as minimal hunks, not whole-file replacements. They add exactly this mapper's current-subject boundary and four immutable tables. Their baseline is root's 079 staging shared patch; retain any later unrelated changes.
- Apply `patches/EvaluationService.patch`, or integrate the exact currently compiled source at `../patches/EvaluationService.java`. This delta includes the already agreed private AcceptanceSnapshot and the new synchronous optional original-score witness. Review against the latest formal source rather than overwriting unrelated changes.

The SQL migration adds exactly five tables, 34 mapper statements, and no permission grants. Only `expert_qualification_state` is mutable. There is no universal version-owner domain registration: `EXPERT_QUALIFICATION` and `EXPERT_EVIDENCE_RECORD` are exact registry identities consumed by the genuine 081 authority. Other generic lineage consumers must not treat them as CAPABILITY or ARTIFACT.

## Public bounded API and authorization

Feature property `medical.expert-qualification.enabled` defaults off. Availability remains readable and reports whether its controller is registered, without instantiating production dependencies or claiming professional qualification.

- POST `/api/expert-qualifications`: human submission, current `agent.configure / AGENT_PUBLICATION`, ENTERPRISE scope for null project or exact PROJECT scope.
- POST `/api/expert-qualifications/decisions`: current `agent.publish / AGENT_PUBLICATION`; APPROVE must be a different actor from both submitter and expert. The human explicitly confirms review of the exact ordered evidence bases and professional scope. DRAFT may become APPROVED or REJECTED; APPROVED may become REVOKED. No state reversal or implicit extension.
- POST `/api/expert-qualifications/score-bindings`: actual original scorer only, current `evaluation.score / EVALUATION_RESULTS` on the real evaluation. It creates no score.
- GET `/api/expert-qualifications/{qualificationId}` and `/operations/{operationId}`: current `configuration.read / AGENT_PUBLICATION` on the actual governance owner plus all current evidence reads; operation recovery is original-creator only. Score-binding recovery additionally rechecks real evaluation reads and exact bound score.
- GET `/api/expert-qualifications/availability`: current session context; explicitly false `creates_professional_licence`, `creates_scores`, `proves_formal_execution`.

Every endpoint uses exact schema/context headers and no-store envelopes. Writes require original Idempotency-Key and CSRF header, reject unknown/query/body/header forms, and enforce 64 KiB before identity or transfer. Original operation identity includes tenant, actor, operation kind, full canonical request and local schema SHA, not the current session/context. A repeated original key recovers the original receipt after current checks; changing its body conflicts. Rebinding the same score/qualification under another key gives ACTION_STALE. Each mutation has actual generic operation, immutable typed journal, version row, complete audit and outbox snapshots and exact readback. Every mapper Map supplies the current actor as userId; creatorId is only a historical filter.

## Real evidence path

Qualifications require an active tenant expert, nonblank bounded domain, one or more frozen EVAL-01..12 dimensions, explicit purpose, finite valid interval, and 1..32 exact SOURCE/ARTIFACT bases. Merely naming a credential is insufficient.

The reader uses real source/current rights/owner/lineage authority and trusted configured artifact schema descriptors. It performs actual versioned raw-object GET outside SQL transactions, with before/during/after current checks, expected raw SHA, tenant object ownership, 16 MiB per object and 32 MiB aggregate bounds. SOURCE must be active, received and approved. ARTIFACT requires its exact configured published schema and real primary SOURCE linkage. No caller-defined descriptor or substituted purpose is accepted. The full combined dependency graph is bounded before per-root snapshots. Classification is the actual maximum across supplied originals and their source closure, limited to PUBLIC/INTERNAL/RESTRICTED/PERSONAL_HEALTH.

Author and APPROVE both read the genuine originals. REJECT and REVOKE deliberately need only actual governance permission and intact immutable metadata so withdrawal is possible when credential read rights have been removed. Historical governance grants are validated at their original timestamp and identity mode; later grant rotation is not retroactive deletion of a human decision. Current consumers separately recheck current reads and exact fixed source/rights identity. These mechanisms verify provenance and authority, not the substantive truth of a qualification or sampling assumption.

## Original score witness and transaction boundary

The exact staged EvaluationService append path calls an optional `OriginalScoreWitnessSink` after the original score and EV-02 operation/audit/outbox are written, still inside the same EnterpriseAccess transaction. It passes a privately constructed, callback/thread/transaction-bound `CreatedScoreWitness` with the actual Score, original Definition, actual GrantRow, original identity mode and actual receipt. The sink rereads the saved score and persists `expert_score_authority_witness` plus independent audit/outbox anchored to the original EV-02 operation. Failure propagates to the original transaction; no exception is swallowed. With the feature off, no sink bean is present and the old score path remains unchanged. SIM definitions/workers are not recorded as formal authority witnesses.

Old scores without that genuine contemporaneous witness cannot be bound. Today's grant or identity mode is never used to manufacture missing history. The frozen public Score/Result schema is unchanged, and Result.production_eligible remains false.

`EvaluationService.AcceptanceSnapshot` is private, same-Admission and transaction bound, performs the existing full load(false), verifies the original result by recomputation, and supports `requireConsumer(a, evaluationId)` and `requireUnchanged()`. Historical reading does not require today's evaluation.score. Existing exact EVALUATION_RESULTS dependency-purpose checks remain unchanged. Missing mixed-purpose formal-reader support is an explicit blocker rather than a bypass.

## 080 private consumer contract

Call inside the current real EnterpriseAccess transaction:

`ExpertQualificationAuthority.requireQualifiedScore(Admission a, PermissionProtocol.Basis binding) throws IOException`

It returns private-constructor `VerifiedQualifiedScore`:
- `binding()`, `score()`, `qualification()`.
- `requireConsumer(a, evaluationId, scoreId)` checks the exact original Admission and score identity, then rereads.
- `requireUnchanged()` reruns actual original result/score, witness, full journal/audit/outbox, current reads, qualification head and evidence checks; it rejects reuse in another transaction.

Nested private `VerifiedExpertQualification` exposes exact immutable `qualification()` and `approval()` bases; `expertId()`, `domain()`, actual nullable owner `projectId()`, `purpose()`, `approvedAt()`, `validFrom()`, `validTo()`, `authorizations()` (defensive copy); `asOf()` is the actual original Score.created_at, never a caller-selected date. Its `requireConsumer(a, expertId, domain, targetProject, purpose)` binds the actual consumer target separately from nullable enterprise owner; `requireUnchanged()` performs the same full checks.

A valid bound score must be the exact current effective SCORED observation for a REAL/ACCEPTANCE sample in the recomputed original Result. Its scorer must equal the qualification expert. Domain/dimension/project/purpose must match. APPROVE must precede the actual score timestamp and that timestamp must be within the approved validity interval. The current head must still be APPROVED; REVOKED always rejects. Definition SHA and complete frozen Score bind candidate/sample/repeat/dimension/rule and input/output/evidence refs. Save binding + qualification + approval bases in 080 acceptance.

081 does not attest actual EVALUATION_VERIFICATION execution. 080 must additionally consume J/083's genuine same-cell private run/execution proof, including exact evaluation/definition/candidate/sample/repeat and actual execution artifacts. Until it exists and matches, production acceptance remains blocked.

## Reproducible isolated verification

`bash /tmp/stage22-task-runtime/a/expert-evidence/tests/run-tests.sh`

`PYTHONPATH=tests .implementation-work/python-contracts/bin/python /tmp/stage22-task-runtime/a/expert-evidence/tests/test_expert_qualification_migration.py`

Run the Python command from the repo root. Java uses the existing isolated real dependency classpath and javac, not Maven. Logs are `tests/test-results.txt` and `tests/sql-results.txt`.

Fixtures explicitly substitute DB/session transports and evaluation rule/dependency storage boundaries. The credential path runs actual current SourceReadAuthority/ResourceReadAuthority/VersionResourceAuthority/dependency validation and a loopback versioned S3 GET; scoring tests execute the actual EvaluationService append/load and deterministic verdict engine plus real 081 sink. SQL tests use real JSQLParser and SQLGlot, forbid the 068-only compatibility helper, check every foreign-key parent's complete uniqueness/types, metadata, full-cell linkage, insert columns, current-subject guards, no-cache reads and immutable/CAS boundaries. No MySQL commit/rollback or production credential/HTTP deployment success is claimed by these isolated tests.
