# 080 first delivery: fixed candidate and TOOL/SKILL authoring

Freeze target: 2026-10-18 21:05 UTC. This directory is the ONLY 080 copy source. The parent `publication/` directory includes unfinished later publication work and must not be copied wholesale.

## Exact copy map
- `java/*.java` → medical-persistence main Java package; only `FormalAgentCandidateMapper.java` belongs in `.mapper`.
- `mapper/FormalAgentCandidateMapper.xml` → medical-persistence resources/medical-mappers.
- Both `contracts/*.json` → medical-persistence resources, as independent local schemas. Frozen AgentRelease schema remains unchanged.
- `storage/080-agent-publication.sql` → medical-data migration directory. Exactly ONE immutable table: `formal_agent_candidate_binding`.
- Four package test Java sources in `tests/` → persistence tests; RunCandidateTests is isolated runner only, do not copy as application code.
- `tests/test_candidate_migration.py` is the isolated migration verification; adapt its D path for formal test placement.

Root shared changes: register exact FormalAgentCandidateMapper prefix under current-subject guard; register immutable formal_agent_candidate_binding; append 080 migration and coverage one table. Mapper IDs: candidate, parents, list, insertCandidate, runtimeAuthor. Existing AGENT and CAPABILITY_VERSION registry domains are used; no new domain needed. All mapper userId values are current actor; historical creator remains only historical filtering.

## Real dependencies
Existing 073 AgentPublicationJournal/Mapper/Witness, FormalCapabilityIdentityAuthority and their genuine immutable original records; 078 AgentExecutionPolicyAuthority/Schema including actual requireAuthored, requireByVersion and requireHistoricalByVersion. The actual AgentPublicationDependencies complete TypedUse/requireByUse implementation and corresponding VersionResourceAuthority per-version purpose patch are required. Source files are `/tmp/stage22-task-runtime/a/java/AgentPublicationDependencies.java` and `/tmp/stage22-task-runtime/a/patches/VersionResourceAuthority.java`; root must merge the narrow shared delta, not overwrite newer shared code. Existing TemplateVersionStore, EvaluationRuleAuthority, ArtifactRegistryMapper, EvaluationReferences and published frozen schemas are used unchanged.

This first copy deliberately excludes AgentPublicationAuthority, acceptance, publication service/lifecycle/object ledger/registration/transport changes and all evidence-adapter files. No RuntimeObjectAttempt sealed-interface or generic artifact guard patch is required by this package. Those remain separate additive 086 work.

## API and scope
Default-off `medical.formal-agent-authoring.enabled=true` exposes:
- POST /api/formal-agents
- POST /api/formal-runtime-capabilities
- GET /api/formal-agent-records/{recordId}
- GET /api/formal-runtime-capability-records/{recordId}
- GET either record route + /operation-receipts/{operationId}
Headers use existing real session, CSRF for POST, X-Schema-Version=1.0.0, X-Context-Revision, and POST Idempotency-Key. Request maximum remains 1 MiB.

Actual agent.configure / capability.configure plus exact current typed dependency reads are required. ROLE/PROMPT remain in 073; this package adds TOOL/SKILL only. Candidate is DRAFT and production_eligible=false. Record recovery rechecks original immutable operation/audit/outbox and current read permission. No expert score, qualification, acceptance or release grant is created.

List/discovery and feature-availability UI integration are not yet implemented for this first slice; do not advertise a complete normal publication workflow. The list Mapper is internal and has no public route yet. Existing package artifact producer/configuration is genuinely required.

## Verification and limits
`run-tests.sh` compiles this isolated source set without any publication/lifecycle/registration classes and runs only candidate and runtime-capability authoring tests. See test-results.log for exact count. Migration checks use actual JSQLParser plus SQLGlot; see migration-tests.log. JDBC/MyBatis/current authority boundaries are explicit fixtures, not proof of deployed database or real object storage.

084 may consume the genuine candidate identity/dependency classes. Three ordinary TASK admissions must remain disabled until separate 086 publication and its private current/historical evidence chain are complete. Missing genuine 083 professional completion implementation remains an additional blocker; MODEL_VERIFICATION or SIM results cannot replace it.
