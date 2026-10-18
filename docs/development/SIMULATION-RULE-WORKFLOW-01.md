# Simulation-only rule authoring and approval

## Scope and isolation

This additive slice implements independent creation, read, approval-baseline and approval-decision routes at `/api/simulation-rule-versions`. The controller is absent unless `medical.simulation-rule.enabled=true`; the property defaults disabled. Requests use the ordinary verified application session, existing version/context headers, original idempotency key for writes, no query strings, strict duplicate/unknown JSON rejection and 64 KiB request limits. Responses are no-store.

The new local `simulation-rule-version-workflow.schema.json` requires `dependency_use=SIMULATION_EVALUATION` and `production_eligible=false`. Existing rule-expressions content and frozen shared contracts/manifests are unchanged. Rule content still has exactly its old canonical representation and digest. The separate workflow manifest identifies the extension.

Governance remains explicit: `rule.write` on the actual enterprise and `rule.read`/`rule.approve` on the rule use the existing TEMPLATE_MAINTENANCE governance purpose. Dependency reads independently use only SIMULATION_EVALUATION and SourceReadProtocol.Use.REVIEW. Readability creates no write or approval authority. This slice supports enterprise-level rules and enterprise-owned dependencies only; a project-owned root or ancestor fails rather than silently losing scope.

## Durable immutable binding

Migration `064-simulation-rule-binding.sql` adds:

- `simulation_rule_binding`: tenant, actual rule/content digest, fixed dependency use, canonical binding JSON/hash, independent workflow manifest and creation metadata. Creation is atomic with the original rule/definition/lineage records. No binding update/delete API or mapper statement exists.
- `simulation_rule_approval_binding`: exact decision-to-rule/binding digest relation. Approval consumption requires this record as well as the original decision, operation, CAS revision and audit/outbox chain.

Every stored read reconstructs the binding from tenant, rule ID and verified rule content digest, checks its hash/JSON/workflow manifest, rereads fixed lineage/base relations and revalidates current complete dependency authorization. Approvals include the same binding digest in their baseline and receipt. Original-request idempotency hashes include the separate route and complete extension request. Replay rereads the actual rule, authorization, operation, original audit/outbox, original receipt and approval binding; no new decision is inferred from a status label.

Writes use separate operation names `SIM-RULE-CREATE` and `SIM-RULE-APPROVE` and events `simulation.rule.draft_created` and `simulation.rule.approval_decided`. Approval retains existing CAS and governance/context epoch advancement. Failed insert/reread/audit/outbox checks throw inside the existing EnterpriseAccess tenant transaction. Tests with doubles do not prove live database rollback.

## Dependencies and consumers

SimulationRuleDependencies traverses complete bounded lineage and validates current source rights, gates, artifact profiles, registrations, owners and fixed rights members under SIMULATION_EVALUATION/REVIEW. Artifact profiles remain explicit deployment configuration under the existing medical.templates policy; this slice does not invent a permissive default. Base/ancestor RULE_VERSION records must have a matching simulation binding and current complete approved workflow, so a legacy rule cannot masquerade as a simulation base.

`SimulationRuleVersionStore.requireEvaluation(admission, ref, writing)` returns the existing `RuleVersionProtocol.Validated` only after an actual rule.read grant for SIMULATION_EVALUATION consumption, immutable binding/content/lineage validation and current simulation dependency checks. When writing, it also requires APPROVED plus the simulation-specific decision/operation/audit/outbox/binding chain. This method throws IOException or existing domain runtime failures, not an unconstrained checked exception.

Legacy RuleVersionStore reads/approval targets/creation replay reject simulation bindings. TemplateDependencyAuthority rejects any simulation rule ancestor. The parent-owned EvaluationRuleAuthority must also reject bindings on its ordinary branch and delegate its validated all-SIM branch to the new store. EvaluationDependencyUse selection belongs to that coordinated integration and must never depend only on environment.

## Integration checklist

The parent owns registration of the new mapper statements and tables in TenantScope/ProtectedTenantInterceptor, and registration of migration 064 in master.xml/coverage.json. All new mapper operations require userId in the tenant transaction; the legacy mapper adds `simulationBinding`. Input-artifact bridge migration 063 is separately owned. Neither migration execution nor runtime deployment is implied by adding these files.

## Verification

Added suites: SimulationRuleVersionSchemaTest, SimulationRuleVersionServiceTest, SimulationRuleVersionMapperTest and SimulationRuleDependenciesTest. They cover fixed-purpose/production flags, unchanged old content digest, tenant/rule/content binding tampering, legacy schema rejection, author/read/approve/replay, original governance grants, stale baseline/CAS, missing approval binding, audit/outbox failure, mapper tenant/subject requirements, source-use dispatch, missing artifact profiles, project ancestor refusal, and mutual isolation from legacy/template rule paths. Old RuleVersionServiceTest and RuleVersionMapperTest include legacy guard coverage.

At handoff, XML syntax and `git diff --check` were checked. Maven compilation/test execution is deliberately serialized by the parent; results must be recorded only after those runs. No live database, HTTP server, grants, source approvals, production data, provider call, commit or push were executed by this slice. The service tests use explicit authority/storage doubles and make no real approval or rollback claim.

### Approval evidence integrity and worker-read follow-up

Consumption additionally uses `SimulationRuleApprovalEvidence` over a complete `approvedEvidence` projection. It recomputes baseline, dependency-authorizations and receipt digests; compares the operation response to the exact stored receipt; binds sidecar creator/time/classification; and reconstructs the complete audit transition and outbox decision. Merely finding matching IDs/status/binding in SQL EXISTS is insufficient. Root rule consumption, approved public read and approved RULE ancestors call this verifier. Fault-injection tests preserve IDs/status while corrupting individual ledger payloads/digests, audit before/after or outbox fields.

Historical approval replay verifies the before/after snapshots for that specific transition, including operation ID and immutable rule binding. It does not require an old after-snapshot to equal the latest state after a legitimate RETURN/DISABLE transition.

The read-only store and dependency mapper helpers use admission.actorId(), so a genuine worker Admission need not expose a user session. The focused regression uses an explicitly mocked worker Proof and ResourceReadAuthority; it does not establish real mTLS/grant admission. The shared ResourceReadAuthority's narrow worker purpose allowlist must be coordinated by the parent with the retained rule.read governance purpose before runtime worker consumption is claimed working.

The coordinated worker integration keeps the global worker allowlist unchanged. Only requireEvaluation consumes rule.read under SIMULATION_EVALUATION; authoring, public detail read and approval retain TEMPLATE_MAINTENANCE governance purpose. An additional test uses the actual ResourceReadAuthority and PermissionAuthority over explicit permission-row doubles, proving that the SIM read grant is required and cannot confer write/approve or authorize worker TEMPLATE_MAINTENANCE/EVALUATION_RESULTS reads.
