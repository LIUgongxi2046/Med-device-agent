# Incremental 083: original preparation + reader corrections

Verified 2026-10-18: 51 Java tests, 0 skipped/failed; 5 real JSQLParser/SQLGlot checks, including every FK's complete unique parent key and exact data type. No actual database/provider/object/lease integration claim. Authority and SQL collaborators in new service tests are explicit doubles.

Apply after frozen-083:
- Three patches/*.patch apply only to FormalEvaluationExecutionEvidence, FormalEvaluationProfessionalCompletions, FormalEvaluationExecutionLedger. Matching full replacement files included for inspection, not shared repository overwrite.
- New java/FormalEvaluationExecutionRegistrations.java and FormalEvaluationModelPreparation.java -> business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/.
- New tests/FormalEvaluationExecutionRegistrationsTest.java and FormalEvaluationModelPreparationTest.java -> matching medical-persistence src/test/java package.
- RunEvaluationRuntimeTests.java, run-tests.sh and test_migration_083.py are isolated launch/test tools only.
- Migration 083 bytes are UNCHANGED from frozen-083. N's shared ScopeSnapshotStore now additionally requires its ScopeSnapshotWriteMapper.java/XML; integrate from N's own delivery, not from this package.

Reader corrections:
1. Its strict JSON decoder now uses SNAKE_CASE, matching PermissionSchema serialization of Java records. Without this correction stored Start/Cell/Node/Record cannot be decoded.
2. Event expected_cells/expected_nodes use exact int values, matching JSON reparse types. The previous LongNode versus IntNode comparison incorrectly rejected matching original audit events. Registration recovery tests exercise the event path.
3. Both current and historical private evidence types expose requireProfessionalOutput(EvaluationProtocol.Cell, Basis), requiring unchanged originals before mandatory professional completion verification. There is no default verifier; missing real completion remains DEPENDENCY_UNAVAILABLE. Public nodeKind/technicalOnly flags cannot certify scored output.

New concrete boundaries:
FormalEvaluationModelPreparation.require consumes original Inputs.Prepared + Scopes.Prepared + exact matrix Node. It independently checks real 077 model eligibility under RUN_CREATION, exact model basis and region/data policy/parameters; current model.send with EVALUATION_RESULTS; real ROLE permission narrowing; every approved selected source under MODEL_SEND with its original business purpose/provider/region/fixed snapshot and parse; source.external.process and project.read role permissions. The resulting private Prepared is same-admission bound and fully rechecked. The real connection verification ID is model eligibility only, never EVAL job identity. This proof has no worker lease/dispatch authority and does not yet prove executable tokenizer/ExactInput materialization.

FormalEvaluationExecutionRegistrations.register(actor,context,key,Start) performs original input GET before tenant transaction, serializes original actor idempotency, persists shared actual source snapshot, requires each model-node preparation, then atomically inserts 083 root admission, original audit and outbox, rereads exact originals. Retry uses original registration without duplicate writes; conflict/missing audit/event/tampering/revoked authorities fail. The returned Registration is data only.

IMPORTANT: This is preparation-root registration, not the complete run producer. It writes NO execution_registry/run_principal/system_job_run/run_step/action/invocation, and does not call a provider. The existing evaluation.execution_started event identifies the immutable preparation root only; DO NOT connect a dispatcher to it or advertise a running job. No controller or default-on bean configuration is supplied. If root integration requires only a complete runnable producer, defer this registration service until the cell/run/node transaction is added.

Remaining blockers: exact independently bound per-node PROMPT/context/ExactInput/tokenizer object producer; genuine run/node/lease/action admission; distinct EVAL C07 dispatch and settlement lifecycle (076 currently closes a MODEL_VERIFICATION whole job); missing open/heartbeat/bridge; original isolation-supervisor completion and session/result registration; eight RunActivities. Live UDS remains EPERM/BLOCKED, without bypass. No professional completion proof can currently succeed.
