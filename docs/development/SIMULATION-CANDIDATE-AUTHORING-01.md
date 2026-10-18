# Simulation candidate draft authoring

Status: bounded draft-authoring implementation and offline verification complete; full business/runtime acceptance remains pending. This is a bounded CP-05 / CP-01 extension, not full capability review, publication, production adoption, or an executed evaluation.

## Interfaces and authority

- POST `/api/capability-versions` creates a new ROLE or PROMPT identity and immutable DRAFT version. GET `/api/capability-versions/{versionId}` reads that authored version.
- POST `/api/simulation-agent-versions` creates a new named AGENT identity and immutable DRAFT simulation candidate. GET `/api/simulation-agent-versions/{versionId}` reads it.
- All routes require the current application session, one `X-Schema-Version: 1.0.0`, one `X-Context-Revision`, and no query string. POST additionally requires one `Idempotency-Key`; bodies are bounded to 65,536 bytes after session verification.
- Explicit actions are `capability.configure` and `agent.configure`, purpose `CONFIGURATION_AUTHORING`. New identity creation requires an actual current enterprise/project grant. Reading and dependency consumption require `configuration.read` on the actual configuration owner and current permission over every upstream source/artifact/resource under `SIMULATION_EVALUATION`. Write intent and dependency use remain separate; the request explicitly declares `execution_mode: "SIMULATION"`. No read-to-write inference, generated grants, or publish actions exist.
- Simulation workers cannot author or use these authoring reads. Worker execution retains its existing separate simulation authority.
- Writes use the existing enterprise transaction, project status checks, immutable registry entries, typed lineage, operation records, audit/outbox, and governance/session-context advancement. Exact replay rechecks current write/read/dependency permissions and returns the original receipt; changed path/session/context/body conflicts.

## Closed request contracts

The authoritative request, fixed identity, view, receipt, and envelope definitions are `business/medical-persistence/src/main/resources/configuration-authoring.schema.json`.

Capability fields:
- `schema_version`, `execution_mode: "SIMULATION"`, nullable `project_id`, `kind` (`ROLE` or `PROMPT`).
- `source_artifact`: actual registered artifact `version_id` and `content_sha256`, checked through `EvaluationReferences`/`ArtifactRegistry` with the complete transitive graph and deployment artifact profiles.
- `input_schema_ref` and `output_schema_ref`: `schema_ref` plus `schema_sha256`, resolved against the published local contract catalogue. Arbitrary URLs are never fetched or accepted as publication evidence.
- `tool_scope`: explicitly empty for this restricted no-tool authoring slice. It does not install or execute anything.

Agent fields:
- `schema_version`, `execution_mode: "SIMULATION"`, nullable `project_id`, nonblank `name` of at most 100 characters.
- Distinct hash-bound `role_version`, `prompt_revision`, and `model_config_revision` bases.
- `limits`: the existing closed release Limits structure, restricted to `max_cost_amount: "0"` and `max_tool_calls: "0"`; input-plus-output token limits cannot exceed total tokens, and heartbeat interval must be below timeout. All other required limits remain explicit.

Selected ROLE/PROMPT versions must be genuinely authored through this contract: the service rereads/reconstructs their canonical immutable identities and actual source-artifact provenance. The selected model is reread through the existing simulation configuration service, including its original canonical definition and SIMULATION mode. This deliberately does not accept arbitrary legacy capability rows as authored definitions.

POST returns a closed envelope containing operation_id, tenant/project/config/version IDs, digest, DRAFT status, revision `1`, `production_eligible: false`, and the advanced current_context. GET returns a closed envelope with the full fixed definition, digest, DRAFT status, revision `1`, and production_eligible false. This slice is create-only; there is no append/edit/delete/state/publish endpoint.

## Persistence and exact lineage

No migration is required. The existing migration 007 tables hold all semantic fields; `version_registry`, `lineage_edge`, `operation_record`, `audit_event`, and `outbox_event` retain their existing roles.

- Capability source is a typed direct `CAPABILITY_SOURCE_ARTIFACT` edge. `artifact_digest` is the verified artifact hash. Input/output bindings and empty tool_scope occupy their intended columns. No authored content is hidden inside unrelated JSON columns.
- AGENT direct edges are `SIMULATION_ROLE`, `SIMULATION_PROMPT`, `SIMULATION_MODEL`.
- Canonical identity contains schema_version, tenant/config/version IDs, the complete reconstructed request, creator, creation time, and (for AGENT) the server-generated dependency manifest. Every identity field is reconstructible from concrete columns plus typed direct edges and registry hashes. All direct edges are read without filtering, so extra/unknown relationships fail closed.
- The AGENT manifest is exactly the existing `SimulationCandidateDependencyManifest`: schema_version `1.0.0`, manifest_kind `SIMULATION_CANDIDATE_DEPENDENCIES`, sorted unique transitive version/hash dependencies excluding only the newly generated root. No additional manifest semantics are introduced. Graph capacities account for the new root and direct edges.
- After persistence, `DependencyLineage` and `SimulationEvaluationCandidateAuthority.requireFrozenGraph/requireConfigurationGraph` recheck actual registered closure and concrete kinds/states. Missing, extra, changed, cyclic, disabled, external-model, or out-of-scope dependencies fail closed.
- All upstream ARTIFACT classifications must equal the deployment's authored configuration classification. This restricted authoring policy prevents higher-classification capability content from being hidden behind a lower-classification configuration row. Complete source rights and current owners remain independently checked.

`EvaluationReferences` retains its existing purpose allowlist. This simulation-only authoring slice consumes dependencies under `SIMULATION_EVALUATION`, while configure actions remain under `CONFIGURATION_AUTHORING`. Source rights still match the exact intended purpose; no multipurpose rights or automatic conversion is introduced. All existing current resource, source, artifact policy, ownership, scope, and transitive checks remain intact. Missing deployment artifact profiles, source registrations/rights, or real configure grants remain blockers. No endpoint synthesizes them.

## Verification and remaining scope

2026-10-18 final targeted Java reactor compile succeeded (396 persistence main source files). All 28 tests passed with zero failures, errors, or skips: ConfigurationAuthoringSchemaTest (7), ConfigurationAuthoringLineageTest (3), ConfigurationAuthoringMapperTest (2), ConfigurationAuthoringServiceTest (16). Evidence: `.implementation-work/logs/configuration-authoring-28-test.log` (25.054 seconds).

Service tests exercise real schema/current-grant/source-rights/artifact-registration/version-owner/complete-lineage authorities over explicitly isolated in-memory mapper, transaction, JDBC, and identity doubles; selected model read is a bounded dependency double. They cover capability and AGENT create/read/exact replay after governance/context advancement, wrong role/prompt types, revoked grants/source rights, purge gates, classification mismatch, stale/unverified context, worker rejection, session-bound replay, corrupt content/lineage/receipt/operation metadata, and atomic head/governance/context/outbox rollback. They do not substitute for real DB/S3 acceptance.

No actual database/service execution, real source registration, candidate creation, evaluation execution, customer acceptance, production publication, deployment, or paid provider call was performed. Full CP-01 dependency groups (skills, tools, templates, rules, human checkpoints and production scope), CP-05 review, UI, and lifecycle editing remain outside this deliberately restricted slice.
