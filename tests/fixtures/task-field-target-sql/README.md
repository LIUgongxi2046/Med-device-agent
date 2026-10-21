# Actual 096 JDBC SQL shape evidence (offline, no database)

No hand-written statement fixture is accepted as runtime evidence. The Java tests
capture SQL that the real production classes actually pass to their explicit JDBC
doubles. The optional system property `medical.taskFieldSqlEvidence` names the
JSONL output; the coordinated runner sets it to
`.continuation-validation/task-field-target/actual-sql.jsonl` and removes stale
output before a run. The Java helper uses JSQLParser and checks JDBC bind counts
before the doubles process each call.

The capture contains only SQL and binding metadata: producer, positional/named
mode, argument count, placeholder names and Java argument class names. It does
not contain bound argument values, source content, receipts, secrets or grants.
Repeated identical SQL/bind-shape records are deduplicated within one test JVM.

## Captured execution boundaries

- `TaskFieldMappingServiceTest.Memory`: mapping revision/head queries and writes,
  immutable journal rows, version registry and tenant epoch updates.
- `TaskFieldSourceOrchestrationTest.jdbc`: genuine source reader/receipt-store
  positional queries/audit writes and named source receipt insertion.
- `TaskFieldTargetLedgerTest.Fixture`: real intent/entry/attempt queries and
  writes, original recovery, operation/audit/event witnesses. The registration
  fixture reuses this boundary for registered target artifacts, versions, exact
  lineage, dependency manifests/members, and content binding SQL.
- `TaskFieldTargetRegistrationTest.Fixture`: separate hook for operation
  completion, which overrides the ledger double's generic update method.
- `TaskFieldTargetInputLifecycleTest.Fixture`: define/read/approve/revoke,
  capability/state/decision SQL, journal witnesses and lineage verification.

The validator requires actual INSERT and SELECT coverage for all ten 096 tables,
all five producer labels, the journal/registration INSERT tables, and mapping,
capability, operation-completion and tenant UPDATE shapes. Missing capture is a
failure, never a fallback to an invented SQL fixture. The report lists exact
captured table sets and record counts.

## Validation and reproduction

After the parent-coordinated Java test run (no separate Maven invocation needed):

```sh
/tmp/med-validation-venv/bin/python tests/validate_task_field_target_sql_capture.py \
  .continuation-validation/task-field-target/actual-sql.jsonl \
  --report .continuation-validation/task-field-target/actual-sql-validation.json
/tmp/med-validation-venv/bin/python -m unittest discover -s tests \
  -p test_task_field_target_sql_capture.py -v
```

The script reuses the complete master-changelog parser, including core,
structured/native XML, native ALTER projections and pinned 076 compatibility.
It reconstructs added/modified/generated columns and dropped defaults, not just
CREATE statements. The existing exact-token 076 parser also expands the unchanged
hash-pinned 068 DROP CHECK action into a full AST; no opaque Command is accepted.
SQLGlot parses every actual captured statement. The validator checks table and
column references, qualifiers/ambiguity, exact positional/named bind shape,
explicit INSERT columns and VALUES arity, mandatory non-default columns, no
writes to generated columns, and tenant predicates for SELECT/UPDATE. These are
bounded shape checks, not proof of authorization or semantic tenant isolation.

The twelve validator self-tests intentionally use synthetic SQL only to test
rejection of bad shapes. They do not write the Java capture and cannot satisfy
the runtime evidence coverage requirements.

## Explicitly uncovered and unproven

- MyBatis `ArtifactRegistryMapper`, `SourceReadMapper`, `VersionResourceMapper`,
  `SourceExactMapper` and `PermissionMapper` calls are mocked at interface
  boundaries in these fixtures. Their generated mapper SQL is **not** captured
  by these hooks, even when an independent mapper/schema test covers it.
- Preparation/controller/session routing and production configuration do not
  automatically add JDBC capture coverage. Only calls reaching the boundaries
  above are in the evidence. The corpus is exercised-path coverage, not every
  possible branch of every producer.
- No database connection, MySQL statement execution, migration application,
  data-level foreign-key/CHECK enforcement, JDBC coercion, transaction isolation,
  concurrency, real S3 transfer, credentials, grants or business approvals are
  established by this static evidence. Parameter values are intentionally
  unavailable, so it cannot validate their relational or business semantics.
