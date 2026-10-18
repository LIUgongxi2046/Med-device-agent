# 071 technical-check recovery hardening

## Published scope

Service, existing mapper/XML, two Java test files, new 071 DDL and `tests/test_document_technical_check_recovery_migration.py` are published. Public schemas and frozen 068 DDL are unchanged. Root separately integrates master/coverage, exact table counts and immutable-table protection. Source publication alone does not enable a deployable feature.

The migration adds exactly `document_technical_check_intent` and `document_technical_check_attempt`. Both require ProtectedTenantInterceptor immutable protection before enablement. Existing DocumentTechnicalCheckMapper actor admission remains applicable; its statement count is now 39.

## Verified defect and fix

Before the fix, the actual 068 service with its real test fixture accepted invented output references and completed with zero stored objects. Resetting attempted in mutable operation JSON also reissued a first write after uncertainty. Historical reproduction evidence: `/tmp/stage20-technical-check-recovery/baseline/probe-results.txt` (controlled storage/admission/SQL doubles).

The initial complete immutable command includes both original output identities, content and write intents. It is committed with operation/job/check creation and bound to an independent version digest and started audit/outbox evidence. Each output receives its own immutable attempt, committed atomically with operation CAS before I/O. Exact operation stage additionally prevents deleted attempts plus reset flags from producing a new first-write authorization. Retries recover only.

Every existing ObjectRef is actually recovered through the original write intent, including full objectVersion equality. Completion compares the original locked operation with this invocation's locally verified command. Reads validate current authorization, independent original intent/attempts, artifact registration and actual object bytes.

Original key identity is independently stored under tenant/creator/key uniqueness with ASCII binary collation. Started audit/event binds its digest. Operation and intentByKey must both exist or both be absent; mismatch never becomes NOT_FOUND. Typed completed reads also verify the original key. Historical timestamps require original start <= every attempt <= job end < original deadline, with job start equal to original creation.

## Existing data

No backfill is installed. Old pending and completed operations lacking independent evidence are refused. This patch does not fabricate old attempt timestamps, original storage versions or started events and never automatically retries an old write. Any future legacy recovery requires a separately approved, independently verified provenance process.

## Tests and repeatable commands

Before publication, isolated javac/JUnit passed **43/43 tests**; actual JSQLParser plus SQLGlot contracts passed **5/5**. Independent review probes also confirmed deleted-operation lookup and altered historical attempt time are rejected. These results use explicit test doubles, not a live database or S3.

Formal Python migration test:

```sh
.implementation-work/python-contracts/bin/python -m unittest discover -s tests -p test_document_technical_check_recovery_migration.py
```

The real parser has a 60-second subprocess timeout. All 071 statements are parsed, FK columns are checked against actual parent PK/unique definitions and matching types/charsets, and all 39 mapper statements are parsed. Original key uniqueness/collation, immutable metadata constraints and frozen 068 SHA-256 are checked. No 068-specific parser workaround is used.

Root-owned aggregate Java run must include `DocumentTechnicalCheckServiceTest`, `DocumentTechnicalCheckRecoveryLedgerTest`, `DocumentTechnicalCheckMapperTest`, tenant/interceptor tests and `MigrationBundleTest`. Maven has not been run by this author. After root registration/package, run the full offline migration suite:

```sh
.implementation-work/python-contracts/bin/python -m unittest discover -s tests -p 'test_*migration*.py'
```

Real transaction rollback, locking/isolation, S3 recovery and deployed authorization remain integration acceptance requirements.
