# Simulation dependency-use implementation plan

Status: **design only, not implemented or verified**. Prepared 2026-10-18 UTC for the backend phase after the current frontend slice. This document authorizes no grant fabrication, permission expansion, production eligibility, deployment or publication.

## Problem and invariants

EvaluationService currently consumes dependencies through EvaluationReferences' default EVALUATION_RESULTS purpose. Simulation contexts and candidate dependencies use SIMULATION_EVALUATION. SourceRightsSchema requires the exact purpose on the current fixed rights revision. Separately, rule authoring, reading and approval use TemplateDependencyAuthority with TEMPLATE_MAINTENANCE and SourceReadProtocol.Use.TEMPLATE. Changing only EvaluationRuleAuthority cannot close this chain.

Preserve these invariants:

- Existing evaluation and rule governance actions and permission requirements stay unchanged. Reading an input never confers write, approve or execute permission. Actual current grants, memberships, scope and revocation checks remain mandatory.
- Never treat a source right as supporting two purposes, introduce a generic purpose fallback, or automatically manufacture approvals/rights.
- An evaluation environment label alone does not establish simulation evidence.
- Frozen shared Schema bytes, catalogue entries and manifests remain unchanged, including published evaluation-results and rule-expressions contracts. Existing local workflow contracts should remain unchanged; additions use separate extension documents.
- Simulation rule authoring, approval and consumption must be explicitly bound to the immutable dependency use and isolated from legacy template rules and ordinary evaluations.
- Simulation evidence/results never establish production eligibility or provider financial settlement.

## 1. Derive evaluation dependency use from validated immutable evidence

Introduce a narrow EvaluationDependencyUse helper. It may return SIMULATION_EVALUATION only after EvaluationSchema.create or EvaluationSchema.definition has validated the frozen request, exact sample-manifest digest, expected-items digests and other existing consistency checks, and when samples are nonempty and every sample has evidence_class=SIMULATION. Also require environment=SIMULATION for this branch. Ordinary evaluations that observe SIMULATION evidence in another environment keep EVALUATION_RESULTS for backwards compatibility; the restricted simulation execution producer rejects them rather than silently switching their purpose.

Mixed and ordinary evidence keep EVALUATION_RESULTS. Empty lists must not satisfy an allMatch shortcut. A caller-supplied purpose, header, query parameter, selected sample, or environment alone cannot select the simulation branch. On every later operation, derive from the validated stored definition, never from mutable client observations. This is dependency-use selection, not a grant.

EvaluationService call sites requiring the same derived use:

- create: candidates, checkReferences and approved rule lookup;
- append: original definition and rule revalidation plus new scoreRefs authorization;
- finalize: candidates and approved rule lookup;
- target/read: stored candidate snapshots, rule, samples and existing score dependency checks;
- candidates: configuration.read over each actual owner;
- checkReferences: EvaluationRuleAuthority.require and EvaluationReferences.require.

Keep evaluation.create/read/append/finalize governance permissions under their existing semantics. Do not switch all actions merely because dependency consumption uses SIMULATION_EVALUATION. Changing only create would allow a record to be written but fail its subsequent read.

## 2. Independent simulation rule API and extension Schema

Add SimulationRuleVersionController, SimulationRuleVersionSchema and explicit simulation service/store/approval components, or narrowly typed internal equivalents with equivalent isolation. Suggested routes:

- POST /api/simulation-rule-versions
- GET /api/simulation-rule-versions/{id}
- GET /api/simulation-rule-versions/{id}/approval-baseline
- POST /api/simulation-rule-versions/{id}/approval-decisions

Add simulation-rule-version-workflow.schema.json with strict request/receipt/baseline/decision shapes, dependency_use fixed to SIMULATION_EVALUATION, and production_eligible=false. Validate the embedded rule content through the existing frozen RuleVersionSchema.CONTENT_REF. Do not insert extension fields into the frozen four-field rule content or alter its content digest calculation.

New receipts and approval baselines must expose and bind the exact immutable dependency-use identity/hash. Existing rule routes reject the extension and must not operate on simulation rules. Match current header, session, CSRF, request-size, context-revision, idempotency and no-store handling. Register new permissions only if required by an independently reviewed action vocabulary; never synthesize grants.

## 3. Additive immutable storage and approval binding

Proposed sidecar rule_dependency_use fields include tenant_id, rule_version, rule_content_sha256, dependency_use, canonical binding JSON/hash, binding Schema identity, creator and timestamp. Foreign keys bind the actual rule/version-registry identity and digest. Creation of rule, definition, sidecar and lineage is atomic. Provide no update operation for dependency_use.

Use the binding hash in creation request identity, read verification, approval baseline, approval request hash, audit/outbox and replay verification. Keep existing rule content SHA unchanged. A SIM approval needs its own extension binding record or independent decision record linking the exact rule, operation/decision and immutable binding hash; an old approval label or unrelated legacy decision is insufficient.

Existing rows without sidecars retain an explicitly recognized legacy-template interpretation. A simulation route or simulation consumer encountering a missing sidecar must fail closed, never fall back to legacy. Old routes explicitly reject simulation bindings, including creation replay and approval/replay paths.

Migration allocation as of this plan:

- 063 is reserved for the independent SOURCE-to-input-ARTIFACT bridge.
- 064 is reserved for simulation rule dependency-use/approval binding.
- Neither allocation is claimed landed by this plan. Recheck files, master.xml and concurrent workers immediately before implementing; do not overwrite another migration or edit already existing 055 history.

Update master.xml, coverage.json, tenant table/statement registrations and mapper coverage with the agreed new migration. SQL constraints enforce fixed purpose, positive identities/revisions where applicable, consistent binding fields and tenant-bound references. Immutable application APIs and transactional rereads remain necessary; a CHECK constraint alone does not establish immutable authorization.

## 4. Rule authoring, approval, reading and consumption as one path

Today RuleVersionStore.save/target/originalCreation and RuleVersionApprovalService.prepare/decide/replay rely on TemplateDependencyAuthority.requireRule. The latter uses TEMPLATE_MAINTENANCE and Use.TEMPLATE. Do not add an arbitrary purpose-string argument and assume the source-use semantics and scope are now correct.

The simulation path must validate dependencies under SIMULATION_EVALUATION and Use.REVIEW, covering current source rights, registered artifacts, profiles, complete lineage, tenant/project ownership and limits. Author rule.write and approver rule.approve remain actual existing governance requirements. Revalidate at creation, approval preparation and completion, baseline recomputation, read and idempotent replay. Same-purpose compatible scope/hash is required for any simulation base_version.

EvaluationRuleAuthority.require receives a narrow validated dependency-use value. The simulation branch requires the immutable binding plus the matching approval chain when approval is required; the ordinary branch rejects simulation rules. Preserve rule content recomputation, registry identity, actual RULE_VERSION type, parents and base-parent checks, and exact expected-item binding.

TemplateDependencyAuthority and other legacy rule readers explicitly reject simulation bindings so they cannot be consumed by templates or promoted through an ordinary evaluation merely by sharing a content digest. Do not weaken ordinary rule semantics to make simulation work.

## 5. Adjacent input-artifact dependency

The separate input bridge must also close the existing mismatch: EvaluationService requires sample.input_ref to name ARTIFACT, while SimulationEvaluationService historically compared its ID directly to the fixture input SOURCE version. Resolve through a registered, independently identified ExactInput artifact and verified artifact-to-source binding, schema, bytes/hash, rights revision, scope and current authorization. Never reuse a SOURCE ID as an ARTIFACT ID or treat an unregistered planned ID as a registered artifact.

This bridge belongs to the separate 063 slice. Coordinate its consumer checks with the dependency-use integration; this plan does not claim that its pending service/controller/migration work is complete.

## 6. Regression and acceptance matrix

- EvaluationSchema/EvaluationService: all-SIM, empty, mixed, ordinary and environment-only spoof; validated manifest mismatch; consistent create/read/append/finalize use; stored-definition tampering.
- SIM rule extension: required/fixed dependency use, unknown fields, strict schema identity and receipts; frozen shared bytes and old local workflow identity unchanged.
- Source rights: exact-purpose mismatch fails; updated or revoked rights revision fails; SIM rights cannot authorize EVALUATION_RESULTS or TEMPLATE_MAINTENANCE.
- Storage: altered binding hash, rule ID/content hash/scope, missing sidecar, wrong base, cross-tenant access, duplicate insertion and transaction failure.
- Approval: stale baseline, changed purpose binding, reused legacy approval, current grant/source revocation, post-decision reread, idempotency conflict and exact original receipt replay.
- Consumers: ordinary evaluation/template rejects SIM rule; legacy rule cannot impersonate SIM; complete lineage and exact expected-item bindings remain enforced.
- SQL: actual mapper statements/parameters, tenant interception, additive migration/static checks. Real MySQL DDL/locking/rollback and HTTP/runtime checks must be reported separately from tests using doubles.
- Input bridge: wrong SOURCE/ARTIFACT linkage, content/schema mismatch, wrong current rights and repeated registration; no generated permissions.

Existing relevant tests include EvaluationSchemaTest, EvaluationServiceBoundaryTest, EvaluationRuleAuthorityTest, EvaluationMapperTest, EvaluationCatalogueCompatibilityTest, RuleVersionSchemaTest, RuleVersionServiceTest and RuleVersionMapperTest. Add simulation-specific suites rather than relabeling existing mocked tests as integration proof.

## 7. Two-worker boundaries and root integration

### Worker A: evaluation dependency selection

Own new EvaluationDependencyUse helper and its focused tests, plus EvaluationService changes and corresponding EvaluationServiceBoundaryTest/EvaluationSchemaTest additions. Avoid modifying EvaluationSchema frozen resources or shared catalogues. Use validated Create/Definition objects only. Initially stage the intended EvaluationRuleAuthority call contract as a proposal to root, not concurrent edits to that class.

Worker A does not edit rule authoring/approval/store, migrations, rule mappers, TemplateDependencyAuthority or input-artifact bridge files. Ordinary governance action purpose remains unchanged.

### Worker B: independent simulation rule workflow

Own new SimulationRuleVersion* components, new local extension Schema, new simulation mapper/XML and tests, and proposed 064 migration. Own simulation binding/approval implementation and its focused storage/approval tests. Coordinate integration changes to RuleVersionStore/Service/ApprovalService, legacy rejection hooks and TemplateDependencyAuthority with root before editing any shared file.

Worker B does not edit EvaluationService, EvaluationDependencyUse, EvaluationRuleAuthority, the 063 input-artifact bridge, frozen shared Schemas/manifests or existing 055 migration. Any new migration registration or tenant registry edit must be coordinated with root because the input bridge is concurrent work.

### Root-owned integration

Root owns EvaluationRuleAuthority and its combined ordinary/SIM regressions; shared consumer/legacy rejection hooks; migration master.xml/coverage.json and tenant registration merges; any common protocol changes; and frozen shared-file protection. Root confirms the typed dependency-use contract before both workers depend on it. Root integrates 063 input-artifact consumer changes separately, serializes Maven/build execution, runs cross-slice regressions, and verifies exact final files before publication.

Do not declare the workflow complete from one worker's isolated pass. Acceptance requires author → approve → evaluation create → reread → execute/reconcile → result read to agree on the same immutable dependency use, with actual missing deployment/grants/runtime evidence still disclosed.
