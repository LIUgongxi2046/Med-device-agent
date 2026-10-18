# Stored SIMULATION fixture registration

`POST /api/simulation-fixtures` is an authenticated, bounded source-original registration producer. It does not run a model, create a source, approve a license, approve source governance, or grant execution permission.

The caller supplies the published `SimulationFixtureRegistrationRequest`, plus single-valued `X-Schema-Version: 1.0.0` and `X-Context-Revision` headers. The request selects an immutable SIMULATION model configuration, the two existing source versions and their rights revisions, and request parameters. Source addresses, bytes, hashes and approval booleans are not accepted as authority.

## Existing records required

- A fixed SIMULATION model configuration whose declared case hash and ID match the actual fixture.
- Two genuine previously uploaded immutable originals, containing the complete `SimulationCase` and `ExactInput` JSON documents.
- Current source READ and ENTERPRISE_USE rights for `SIMULATION_EVALUATION`, valid in the deployment environment and region from the existing trusted `RetrievalPolicy`.
- ACTIVE, RECEIVED, APPROVED source governance gates. Session-only sources are refused.
- Current configuration-read permission and `model.configure` permission on the actual configuration for `SIMULATION_EVALUATION`.

The producer reads complete object versions through the existing service-owned SourceUploadTransport, local object endpoint, transfer concurrency leases and S3ObjectReader. It runs outside SQL transactions and rechecks source, grants, configuration, session context and governance before and during the read and before inserting the immutable registration. There is no external URI fallback. Object hashes and lengths are verified separately from canonical JSON hashes.

Each original is limited to 4 MiB; the registration request is limited to 64 KiB. These are technical memory/transport bounds, not token limits or approved quota. Dependency traversal is bounded to 256 versions and 1024 relations, also independently of quota. UTF-8 decoding, duplicate-key detection, trailing-token rejection, published schema validation and SimulationProvider content invariants are applied. No fixture token usage is treated as measured canonical-byte usage.

The registration only maps real source versions to their verified canonical case/input/configuration identities. No initial source, approval, execution authority, context manifest, quota account, run or action rows are seeded by this implementation. Same-binding replay returns the existing immutable registration; changed binding is rejected.

## Invocation consumption

`SimulationInvocationSources.prepare` rereads both originals and mints a private, non-deserializable `VerifiedSources` object. `require` binds that proof to its creating service, authenticated subject, exact request, unchanged registration and current source authority. It verifies the real context input object and actual dependency access set, and checks the retention deadline against database time. The execution authority and canonical invocation ledger remain responsible for run/action/lease/counter/quota controls.

A source registration alone is not evidence of a runnable or completed end-to-end evaluator. Missing independently registered context, run, action, authority or quota records remain blockers; caller JSON cannot supply them.

The context validator reconstructs the complete published SIMULATION context manifest and verifies its canonical hash. Its prompt revision must be a genuine, currently authorized version in the dependency graph. This proves provenance binding for the already-fixed ExactInput; it does not implement typed prompt assembly, prompt rendering, or prompt execution. A genuine context/authority producer is still required for end-to-end evaluation readiness.
