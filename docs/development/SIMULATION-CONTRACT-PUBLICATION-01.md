# Fixed SIMULATION contract publication

The additive `contracts/extensions/simulation-execution.catalogue.json` is independently SHA-256 pinned in Java, Python, and TypeScript. Every loader still verifies the exact original catalogue and evaluation extension bytes. It loads only explicit relative entries, verifies their source digest, and never resolves remote schemas. Java packages the same source files and verifies their bytes in its classpath tests.

Publication identities:
- `simulation-execution`: `urn:medical:simulation-execution:1.0.0` (admission, authority proof, counter/measurements, state, cancel request, and late fact)
- `local-simulation-provenance`: existing `urn:medical:local-simulation-provenance:1.0.0`, preserving the original gateway sidecar bytes

Admission requires exact input, request parameters, simulation fixture and fixed simulation provider settings, alongside their binding and an explicit execution-authority proof. The authority action is `model.simulation.execute`, purpose `SIMULATION_EVALUATION`, evaluation scope `EVALUATION/evaluation_id`; evaluation CRUD permissions do not grant execution. Evaluation and evaluation-run identities are distinct. The required evaluation_definition_sha256 binds the canonical immutable evaluation definition; agent_version_id selects its fixed candidate. The backend must recompute the definition and current candidate content identities and check membership, rather than accept echoed hash labels. Required token_jti refers to the separately verified restricted SIMULATION token; it does not enable production run-token authority. Authority must be produced and reread through current backend grant, membership, lease and tenant-scope checks. A schema-valid client proof, hash, or boolean grants nothing.

The source-original fixture registration request requires existing case/input source-version and rights-revision UUIDs plus request parameters. The backend must read and verify those originals before producing an immutable fixture mapping; a request is never a fixture approval.

The generated structural definitions preserve gateway field names. All wire identifiers use canonical lowercase UUID syntax; integer bounds stay in the safe JSON range. Provider settings require SIMULATION model identities. Counter units are CANONICAL_UTF8_BYTE, measured from RFC8785 UTF-8 canonical bytes, not tokens. Runtime must recompute input, parameters, settings, fixture, request and result hashes and verify the exact pinned counter implementation. Schema validation cannot perform these cross-document checks or prove stored-object origin.

Unknown usage remains null. Local zero provider charge has the explicit reason LOCAL_SIMULATION_NO_PROVIDER_REQUEST and external_request_sent=false. It is not a financial receipt, estimate of token cost, production authorization, or evidence of production eligibility. Mutable proposals and PURE_SIMULATION_POLICY_COMPONENT snapshots are not backend commits. Only durable authoritative reservation/dispatch state and verified stored provenance may justify results; cancel and late-fact envelopes do not independently change execution state.

Tests here establish offline contract publication and packaging. They do not establish a running service, database migration deployment, immutable fixture registration, integrated provider invocation, or an integrated SIMULATION run.

## Context manifest identity

`SimulationContextManifest` is the canonical projection of real persisted context columns: tenant/project/context identity, exact input ObjectRef, ordered source-manifest entries (CASE then EXACT_INPUT), prompt revision, dependency access set, creator/time/classification and explicit SIMULATION/nonproduction markers. Each source entry includes the original source version, rights revision, raw original digest and SIMULATION_EVALUATION purpose. The backend must reconstruct this complete projection from stored columns, validate it, canonicalize it and compare its computed SHA-256 to both context and version-registry content identities. Merely comparing two stored hash labels is insufficient. Prompt identity must resolve to an authorized actual version.

`contracts/fixtures/simulation-context.synthetic.json` is only an offline structural test fixture. Its identifiers and hash strings do not refer to real registered sources, contexts, approvals or execution evidence.

## Fixed candidate dependency interpretation

`SimulationCandidateDependencyManifest` requires schema_version 1.0.0 and manifest_kind SIMULATION_CANDIDATE_DEPENDENCIES; legacy arbitrary dependency JSON is not this manifest. Its closed dependency entries contain canonical version IDs and content digests, with 3–1024 entries and no identical duplicates. The candidate verifier additionally rejects repeated version IDs with differing digests, verifies the complete transitive registered graph excluding the AGENT root, enforces any stricter configured graph limits, and rereads current permissions. Structural membership or caller hashes alone never prove lineage.
