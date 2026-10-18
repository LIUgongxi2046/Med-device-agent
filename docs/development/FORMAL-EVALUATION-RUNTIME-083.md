# 083 bounded backend handover, 2026-10-18

## Verified result
30 Java tests pass (8 matrix, 12 coverage, 10 actual observation-service tests), plus 4 real JSQLParser/SQLGlot migration checks. Production Java compiles against actual staged 076/078/079/080, J's minimal EvaluationService patch and N's current shared ScopeSnapshotStore. The SQL/external collaborators in Java service tests are explicit Mockito doubles. No MySQL execution, live supervisor, paid model call, actual S3/mTLS/HTTP, or successful full evaluation was tested.

## Exact new-file mapping
Every java/*.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/<same basename>.
Every tests/*Test.java -> business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/<same basename>.
RunEvaluationRuntimeTests.java and run-tests.sh are isolated launcher tooling only; do not copy into production.
storage/083-formal-evaluation-runtime.sql -> business/medical-data/src/main/resources/db/migration/083-formal-evaluation-runtime.sql (DRAFT; review before registration/application).
tests/test_migration_083.py is isolated real-parser tooling, requiring ROOT/tests on PYTHONPATH. It validates syntax/additivity/tenant FK boundaries, not complete parent FK compatibility.

## Shared narrow changes and ownership
1. patches/evaluation-execution-definition.patch is a byte-for-byte copy of J's frozen patch. Apply its additive ExecutionDefinitionSnapshot API to A's current EvaluationService; do not replace the entire service. It independently requires evaluation.run on original unfinished definition. Do not apply twice.
2. N owns shared ScopeSnapshotStore.java at /tmp/stage22-task-runtime/n-verification-preparation/shared/ScopeSnapshotStore.java. Consumers call package-private saveForEvaluation/readForEvaluation with actual project/profile/full SourceScope/evaluation ID/business purpose. No copied competing core is delivered here.
3. If migration is accepted, add one include db/migration/083-formal-evaluation-runtime.sql in numeric order to master.xml and native_sql_migrations in coverage.json; register its six tables under required_tables. No registration was modified here.
4. Add only formal_evaluation_execution, formal_evaluation_cell, formal_evaluation_node, formal_evaluation_node_result, professional_runtime_observation to ProtectedTenantInterceptor.IMMUTABLE_TABLES. professional_runtime_session remains mutable solely for CLOSED CAS. No existing table should be removed or weakened.
5. Services have explicit constructors and no auto-wiring configuration in this slice. No default-on bean/gate is supplied. The observation controller is conditional on medical.professional-runtime.observations-enabled=true, default absent/false. Keep all runtime gates off until complete wiring and original dependencies exist.

## Actual implemented boundaries
- FormalEvaluationExecutionAdmission: original evaluation.run, fixed candidate and independently approved policy, full selected-source purpose and real budget checks.
- FormalEvaluationExecutionInputs: actual 079 private GET proof, original registration/ledger/current-rights verification outside transaction.
- FormalEvaluationExecutionScopes: actual independent evaluation snapshot persistence/read via N's single shared core, requiring fixed-source selection and original sample source membership.
- FormalEvaluationOriginalObjects: real immutable artifact/object GET and hash/current-read evidence.
- FormalEvaluationExecutionLedger/Coverage/Evidence: complete matrix ledger verification and two-phase private object proof API. Historical read proof is a separate type and cannot be used for acceptance.
- ProfessionalRuntimeObservations: authenticated original-service close/failure/finish observations, idempotent SQL persistence and admission CLOSED CAS. Failure/finish observations never mark run/step/model success. Finish route returns DEPENDENCY_UNAVAILABLE after durable observation until genuine completion exists.

## Explicit unfinished work / honest blockers
No execution/cell/node/result producer yet; no session creator/open/heartbeat/bridge handler; no eight RunActivities implementation; no independent EVAL C07 admission/dispatch/settlement adapter. FormalEvaluationProfessionalCompletions is deliberately unimplemented. Evidence.prepare fails before object I/O without this authority, so SQL records or public booleans cannot fabricate acceptance. The migration alone cannot make executions. These classes must not be advertised as a complete professional runtime or all matrix nodes supported. UDS sandbox EPERM remains blocked and was not bypassed.

## Consumer APIs
FormalEvaluationExecutionEvidence.prepare(subject, context, evaluationId, candidate, policy) outside transaction -> private Prepared.
require(admission, evaluationId, candidate, policy, prepared) -> private VerifiedEvaluationExecutionEvidence with records(), authorizations(), originalReadReceiptIds(), requireConsumer(...), requireUnchanged().
prepareHistorical(...) and requireHistorical(...,prepared) -> separate HistoricalEvaluationExecutionEvidence, read-only original approved policy checks; cannot convert to executable/acceptance proof.
records() returns List<FormalEvaluationExecutionProtocol.Record>, using J's exact protocol file.
All external models/source sends remain subject to separate model.send/external-processing checks. No automatic approval or fabricated success is included.

## Freeze
frozen-083 contains the tested source/test/SQL/log/launcher snapshot. FROZEN-SHA256.json records each frozen byte. Subsequent C07 work must be delivered separately as an incremental patch and must not alter frozen-083.
