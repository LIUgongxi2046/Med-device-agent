# TASK creation publication admission, 2026-10-19

## Implemented scope

`PublishedTaskCreateAdmission` consumes the existing 086 `AgentPublicationAuthority` private proof and 078 approved execution policy. Publication object verification happens before the creation write transaction. The transaction revalidates the exact publication, actual publication manifest, selected template membership, current policy/model/retrieval dependencies, source-policy intersection and release scope. There is no provider call, fabricated acceptance, default permit or new business grant.

`TaskCreateService` first recovers a completed original key under current creation/read authority. A completed request does not need a newly enabled publication or object preparation. A new request prepares outside the transaction, then rechecks the original key in the write transaction to handle concurrent completion. Existing snapshot, task version, lineage, audit and event writes remain atomic.

An unprepared admission call fails closed. Missing 086 optional wiring remains `DEPENDENCY_UNAVAILABLE`; external network metadata is not treated as query approval. The TK-01 creation body is unchanged; the companion picker query/Page adds the selected release ID and binds it into pagination. No database migration is added.

## Explicit limitations

This is a strictly bounded creation foundation, not a completed Task/Plan/Run chain. Empty-source admission still causes the existing service to add `task:sources` / `SOURCE_MISSING` and create `NEEDS_MATERIALS`, never `READY`. `TaskPlanReleaseAdmission` and `RunCreateAdmission` remain unimplemented. The professional evaluation/completion-proof production dependencies of 086 also still need their own genuine implementation/deployment.

The pre-existing non-empty-source blocker was an exact-one-purpose contradiction:

- `medical-mappers/SourceReadMapper.xml` source query selects `source_version.rights_revision` as the single current rights revision.
- `SourceRightsSchema.require` requires source current revision, rights row revision and requested revision to be identical, then requires exact equality between requested purpose and `RightsRow.purpose`.
- `TaskCreateService` / `ScopeSelectionAuthority` and snapshot creation pass `TASK_CREATION` for selected sources.
- `AgentExecutionPolicyDependencies.requireChecked` verifies those same selected source versions/current rights using `definition.businessPurpose()`.
- `AgentExecutionPolicySchema` fixes the first approved business purpose to `DOCUMENT_BODY`.

Extra resource grants alone could not satisfy both checks. The coordinated implementation now separates task entry authorization from actual data use:

- The prepared genuine 086 consumer resolves the exact approved 078 `VerifiedPolicy` before selecting sources. No request field can choose a purpose.
- New task-specific selection/snapshot methods derive `DOCUMENT_BODY` from that proof and retain `TASK_CREATION` project/source/collection entry grants as additional controls. Exact fixed source version/hash membership is enforced. Generic selection/snapshot methods are unchanged.
- Snapshot identity already pins each exact source/rights revision. No rights schema or grant is changed.
- Historical task source reads resolve only the immutable policy identity: original task input release ID → actual 086 registration/acceptance join → verified accepted manifest matching stored release/candidate → exact 078 policy version/hash and original decision chain. Approved or disabled policy metadata can identify historical data use; no current execution approval is inferred.
- This metadata resolver exposes no execution/publication proof, performs no physical object I/O, and does not require a new live professional evidence check when recovering a completed request. Current source rights/read permissions and original entry-purpose source/project grants remain enforced.
- The normal source/collection picker in the companion slice binds the selected release. Its required query release ID is not a user-selectable source-purpose field.

No multi-purpose rights migration, automatic grant, historical approval fabrication or relaxation of `SourceRightsSchema` was introduced. This resolves the source-purpose wiring only; absent real published evidence still blocks production admission and the Task/Plan/Run chain remains incomplete.

## Verification

Verified at 2026-10-19 22:36 UTC: seven-module Maven compile/test reactor succeeded; 20 selected suites (three method-scoped) ran 186 tests with zero failures, errors or skips. Persistence/contracts source SHA-256 lists before and after validation were identical. JVM test heap was 2 GiB with Mockito explicitly attached at JVM startup. The first 512 MiB wider run exhausted heap; a subsequent wider run included an existing real-DB TaskListSchemaTest and failed because its service-secrets/MySQL setup is absent. That DB test remains unchanged and is excluded from the explicitly offline final selection, not reported as passed. Three new collection fixture failures were corrected to use the actual PROJECT resource-owner mapping; assertions were retained.

Unit fixtures exercise real orchestration, schemas, source-rights validation, selection and snapshot logic with explicit SQL/publication/policy boundaries. They are not evidence of a real published agent, actual database rollback, deployed object service, provider call or complete professional task execution.

## Compatibility and rollback

Deploy the picker frontend and backend together because picker queries now require the selected release ID. The creation request body and persisted snapshot schema are unchanged. No migration or new grant is installed. An old backend still has the original source-purpose contradiction and must not be assumed able to consume newly created non-empty tasks; a rollback must retain the corrected historical source-purpose reader or explicitly make those operations unavailable rather than mislabel their rights.

Exact final selector, current reports and retained failure logs: `.continuation-validation/backend-stage26/`. Its `selector.txt` enumerates all 20 suite/method selections; `summary.json` records per-suite counts.

## Full reactor packaging checkpoint

At 2026-10-19 22:38:50 UTC, all 12 Maven reactor modules packaged successfully (33.802 seconds). Command from `business/`: `/tmp/apache-maven-3.9.9/bin/mvn -s /tmp/med-maven-settings.xml -B -Dmaven.repo.local=/tmp/med-m2 -DskipTests package`. Tests were not rerun in this packaging command; the separate frozen 186-test offline selection above is the test evidence. Existing POM resource copying and Spring Boot repackaging were used without changes.

`business/ruoyi-admin/target/ruoyi-admin.jar` SHA-256: `383e4c902ce446b3c300b31bee188ca1edea751b565a54544c8f9795af8ed208`. All 499 final `web/dist` files exactly match `BOOT-INF/classes/static/` inside that jar, with identical file sets and bytes, no missing or stale extra static files. See `package-full-reactor.log` and `package-static-verification.json` in `.continuation-validation/backend-stage26/` for the complete evidence and per-file SHA-256 manifest. This is packaging/static verification, not a service startup or end-to-end runtime result.
