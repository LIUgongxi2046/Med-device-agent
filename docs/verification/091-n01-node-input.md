# 091: bounded N01 planning input preparation

This increment prepares and registers an immutable EVAL-owned input object. It does not create a run, call a model, issue a human decision, complete an evaluation, score a sample, or approve publication. Every returned identity/content projection states `runnable=false` and `professional_completion=false`.

## Required independently approved inputs

The existing 083 execution registration and 088 context are reused. A new context must explicitly bind the separately published `urn:medical:evaluation-planning-context-compiler:1.0.0` ABI, new immutable assembler and prompt packages, and the corresponding candidate/policy lineage. Existing DOCUMENT_ONLY packages, ABI, approvals and executions are not upgraded in place. Package bytes, implementation class hash, approved component dependencies, tokenizer executable and actual token count are rechecked. ExactInput uses the existing closed message format only; that format does not confer SIM/N00 authority.

The operation takes the exact original 090 request/key and receipt from the same execution, sample and repeat. Its dedicated current authority, original journal/registration and bounded versioned object GET must agree. Original document, template content, fixed profile attributes, plan candidate, conditions and blockers are compiled without filling owners, completion rules or UNKNOWN conditions. Neither expected answers nor scoring data enter this compiler.

Template/profile source ancestors must be represented in a real independently checked EVALUATION_RESULTS project source snapshot with eligible project selections and approved parse, index and rights identities, and require actual `MODEL_SEND` rights for `EVALUATION_RESULTS`, provider/environment/region restrictions and the ROLE's explicit `source.read` plus `source.external.process` narrowing. Readability is not permission to send externally. The input preparation itself performs no provider request.

## Ownership and entry

091 owns three append-only intent/attempt/registered tables. Original-key retries retain the single immutable physical attempt. Recovery is original-object verification, never another PUT. Generic ArtifactRegistry rejects either the new kind or immutable091 ownership, including kind renaming. The consumer checks the dedicated090 proof rather than bypassing090 by feeding it through generic artifact reads.

The feature is separately opt-in (`medical.formal-evaluation-node-input.enabled` and an actual private `object-directory` with deployed transport/write dependencies). No credentials, grants, package approvals or model results are seeded. The normal N01 plan-preparation screen exposes explicit input preparation and original status/content/recovery operations; it persists identity before sending and fences session/context/storage changes. Metadata/history reads are independently wired without model/budget/write dependencies; content additionally requires the original object GET transport. Availability means initialized service, not authorization or acceptance.

## Deliberately blocked downstream

There is no EVAL-native human-decision policy in this change. The owner's choice between per-sample confirmation and independently approved fixed regression answers/rules is still pending. No TK04 decision permission or scoring authority is borrowed. N02–N09, professional completion, result/C07 acceptance and086 publication are not enabled. Real approved inputs and deployment-level runtime/model evidence remain required; local mock tests cannot supply them.

## Verification scope

Focused compiler/schema/ledger/registration/service/controller/wiring tests use explicit upstream/JDBC/S3 mocks. Frontend tests execute the actual bundled transport helpers with request/session-storage doubles. SQL evidence is emitted by actual production ledger/registration methods exercised through the named JDBC fixture; SQL parsers do not execute migrations. No live credentials, provider calls, database writes, grants or push are performed.

### Retained integration blocker

The 2026-10-19 test3 selection ran 127 tests across contracts and persistence: 126 passed and one errored. The error was the existing credential-dependent `ArtifactRegistryReadTest.actualReadOnlyDatabase` setup, which could not find `.implementation-work/runtime/service-secrets.json` in this isolated worktree. Its database checks did not execute. The failed run is retained as `/tmp/eval091-test3.log`; credentials were neither synthesized nor borrowed. Later pure-unit selections explicitly omit `ArtifactRegistryReadTest`, not as a substitute for actual database/tenant-isolation acceptance.

### Local evidence available

- Frontend original N01 plus091 transport/component selection: 100/100, `/tmp/eval091-web-final2.log`.
- Frontend strict TypeScript check: `/tmp/eval091-ts-final2.log`; schema gate compiled 612 fixed references (`/tmp/eval091-web-schema.log`).
- TypeScript contracts: 45/45 (`/tmp/eval091-contracts-ts2.log`). Python pinned contracts: 49/49 (`/tmp/eval091-python-contracts2.log`), saved-replay contracts: 6/6 (`/tmp/eval091-python-replay.log`).
- Scoped091 migration dual-parser/actual-emitted-SQL checks: 10/10 (`/tmp/eval091-migration2.log`), not the full migration-bundle suite and not DB execution.

The code requires independently approved new compiler packages/context/candidate, current model eligibility and tokenizer, existing genuine083/090 originals, actual EVAL source snapshots, permissions and deployed object services. None of those real approvals or runtime capabilities is created by these tests.

A subsequent test4 run compiled the expanded selection but the 2 GB test JVM exhausted its heap while entering the new preparation suite. `/tmp/eval091-test4.log` is retained; it is not a passing run. The test fixture was changed to reuse the actual cached classpath catalogue rather than instantiate a duplicate full project catalogue, without changing compiler or authorization assertions. Review also corrected real snapshot time-field serialization using the existing JavaTime-aware witness encoder.

The single real capture/compiler/materialize/private-proof consume regression then passed with the unchanged 2 GB heap (`/tmp/eval091-test5-single.log`). It uses a real timestamp-bearing Stored scope snapshot and the actual canonical comparison, not a replacement fixture mapper. Production now encodes that snapshot using the existing JavaTime-aware `AgentPublicationWitness.tree` before canonicalizing the full witness through `PermissionSchema.canonical`; strict provenance equality is unchanged.

### Frozen Java unit selection

Final aggregate `/tmp/eval091-test6-final.log`: 164/164 passed (contracts 11, persistence 153), no failures, errors or skips, unchanged 2 GB heap. This includes all 10 real preparation tests, 8 historical read tests and the 27 old DOCUMENT_ONLY compiler tests. The exact Surefire selector was:

`BuiltinEvaluationPlanningContextCompilerTest,BuiltinEvaluationDocumentContextCompilerTest,FormalEvaluationNodeInput*Test,FormalEvaluationPlanningSourceTest,FormalEvaluationPlanningInputPreparationTest,FormalEvaluationExactInputArtifactBoundaryTest,FormalEvaluationPlanArtifactBoundaryTest,SharedContractTest,ArtifactBindingsTest`

Run from `business` using Maven 3.9.9, `-s /tmp/med-maven-settings.xml -o -Dmaven.repo.local=/tmp/med-m2 -pl medical-persistence -am -Dsurefire.failIfNoSpecifiedTests=false`, `-Dmedical.testJvmMemory=-Xms128m -Xmx2048m -javaagent:/tmp/med-m2/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar`, and `-Dmedical.nodeInputSqlEvidence=/tmp/eval091-actual-sql.json`. Explicitly omitted: `ArtifactRegistryReadTest.actualReadOnlyDatabase`, for the isolated credential prerequisite documented above.

Final scoped091 migration check passed 10/10 with `MEDICAL_EVAL_NODE_INPUT_SQL_EVIDENCE=/tmp/eval091-actual-sql.json /tmp/med-validation-venv/bin/python tests/test_evaluation_node_input_object_migration.py` (`/tmp/eval091-migration-final.log`). Checked-in SQL provenance hashes match frozen source and the fresh Java-emitted SQL. Full combined migration inventory/AST must be revalidated after integration with the separately maintained run checkpoint and historical076 parser compatibility fix; this baseline does not contain that fix.

The attempted full baseline bundle run `/tmp/eval091-migration-bundle-final.log` ended with `MigrationBundleChecks.setUpClass` error `076_EXACT_WORKFLOW_REPLACEMENT_REQUIRED` in the existing `tests/migration_076_compat.py`; 3 tests ran before this setup error. No091 failure was reported there. The separately reviewed historical076 parser fix is intentionally not duplicated in this slice. This full run is retained as failed, pending combined validation.
