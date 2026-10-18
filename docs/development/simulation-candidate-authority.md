# Simulation evaluation candidate authority

The simulation invocation consumer requires an existing, persisted execution authority whose `evaluation_definition_sha256` fixes an actual evaluation definition and whose `agent_version_id` selects exactly one AGENT candidate in that definition. A caller-supplied role or a read permission alone is insufficient.

`SimulationEvaluationCandidateAuthority` uses the actual `EvaluationSchema`, evaluation definition and candidate SQL, and canonical definition hash. It compares the fixed candidate snapshot against current immutable AGENT metadata and its version-registry hash. Only open QUEUED/RUNNING evaluation batches can admit another invocation.

## Explicit candidate graph format

For this restricted simulation consumer, the candidate's immutable `dependency_manifest` must validate as `SimulationCandidateDependencyManifest` in the versioned simulation-execution extension:

- `schema_version`: `1.0.0`
- `manifest_kind`: `SIMULATION_CANDIDATE_DEPENDENCIES`
- `dependencies`: complete transitive upstream `{version_id, content_sha256}` entries, excluding the AGENT root

This is a new, explicit interpretation; older arbitrary dependency objects are not assumed to follow it. Unknown or missing manifest formats fail closed. No candidate producer or approval workflow is created by this helper. Until a real authorized producer persists this manifest and complete corresponding lineage, legacy candidates cannot execute simulation.

The manifest must equal the complete bounded, cycle-checked `DependencyLineage` graph. SQL resolves actual capability kinds and configuration-head/version status. The requested ROLE and SIMULATION model revision and the actual context's PROMPT revision must all occur in that graph with their actual kinds. Disabled configuration versions or heads, missing rows, duplicate identities, EXTERNAL model revisions, changed hashes, omitted or injected dependencies are rejected. Prepublication DRAFT/VALIDATING/EVALUATING/REVIEW_PENDING states are permitted; a PUBLISHED release is not required.

`EvaluationReferences` then traverses and authorizes the whole graph with purpose `SIMULATION_EVALUATION`, including `VersionResourceAuthority`, current source rights, artifact profiles and ownership. Its existing `EVALUATION_RESULTS` default remains unchanged. This helper never grants a production TASK token or release authority, creates approvals, or substitutes a fixture receipt for permissions.

Verification is pure schema/graph/stored-metadata and MyBatis SQL testing. No live database, server, socket, paid provider call, or runtime execution is claimed by these tests.
