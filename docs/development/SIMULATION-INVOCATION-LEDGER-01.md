# SIMULATION invocation ledger and restricted evaluation consumer

## Scope and current state

This slice adds a concrete Java/JDBC consumer of migration 060. It uses the existing
`invocation` and `step_action_binding` identities, not a second dispatch queue. It
does not claim a completed C07 execution, production readiness, or database/process
concurrency acceptance. No database, object-store endpoint, server, socket, paid
provider, customer content, deployment, or schema migration was executed for this
slice.

The new fixture registration path reads existing approved, immutable source
originals through the real source/S3 authorization implementation. No source,
approval, fixture response, grant, role publication, token, quota approval, or
retention approval is fabricated. The user must already have the separately
required source/configuration permissions and approved source lifecycle records.

## Authority boundaries

`SimulationInvocationAuthority` requires the exact registered action
`model.simulation.execute`, purpose `SIMULATION_EVALUATION`, and actual EVALUATION
batch scope. Existing evaluation create/read/score/finalize grants do not grant
execution. It verifies a current application session, tenant/member identity,
current mTLS service, immutable persisted authority proof, live matching grant
revision, real SYSTEM_JOB run, principal, step lease, action origin and binding,
fixed SIMULATION configuration, actual evaluation candidate membership, and
current approved quota/retention records.

The dedicated `SimulationRunTokenVerifier` consumes an actual signed token and its
persisted `principal_token` row. It requires audience
`urn:medical:simulation-evaluation` and exactly one action scope,
`model.simulation.execute`. The trusted policy comes from the explicit protected
`medical.simulation-execution.configuration-directory`; it is never resolved from
client data or silently borrowed from the production bean. Signature verification
uses existing vetted RunToken primitives, while production `RunTokenAuthority`
and its PUBLISHED ROLE requirement remain unchanged. The verifier cannot sign,
issue, renew, or claim a lease.

Proofs require a real evaluation definition/candidate, token JTI, finite byte and
call caps, current source/configuration bindings, two distinct accounts, and a
real retention-policy row. A matching client JSON hash or boolean is never an
authority. Admission rereads the canonical proof and compares every persisted
identity. Candidate dependencies must match their immutable complete manifest;
missing or altered graph members fail closed.

## Actual input and source integrity

`SimulationInvocationSources.prepare` performs full existing object reads outside
the SQL transaction and constructs a private, non-deserializable source proof.
`require` revalidates current source rights, uploaded/approved lifecycle, exact
immutable mapping, configuration, session and tenant watermarks inside admission
and dispatch transactions. Caller-supplied fixture and ExactInput values must
equal those actual source bytes after strict JSON validation.

The strict SIMULATION ContextManifest is reconstructed from stored fields,
including ObjectRef, ordered CASE/EXACT_INPUT sources, prompt, dependency set and
creation metadata. Its canonical hash must match the actual version registry.
The complete current dependency-access set is checked; source IDs and rights are
derived from the registration, not a caller-provided dependency list.

`SimulationInvocationSchema` recomputes canonical UTF-8 bytes and all exact-input,
parameter, settings, case and local-request hashes. The counter must equal the
packaged identity derived from the actual Python counter source and measurement
schema. Byte limits and local-call quotas never substitute model tokens, provider
usage, currency, or financial settlement. All arithmetic remains within exact
JSON integer range. Unicode is not normalized.

## Transaction and dispatch ownership

Admission takes the current tenant transaction, canonical action identity and
both authoritative accounts. It locks the enterprise account before the parent
account, checks current approvals/caps and reserves one call plus measured input
bytes and maximum output bytes on both accounts. Canonical invocation, immutable
provenance, both reservation rows, action state, audit and outbox facts are written
in that same transaction. Any failure rolls all of those changes back.

`beginSend` performs a RESERVED/generation-0/revision CAS and the corresponding
action-state transition. Only a successful committed CAS returns a private
process-local `DispatchPermit`. The permit is created after `EnterpriseAccess`
returns, therefore after its `TransactionTemplate` commit. Its payload can be
consumed once. It is not a serializable bearer token and no HTTP route returns it.

Reading an existing DISPATCHING, IN_FLIGHT, UNKNOWN, CANCEL_REQUESTED or terminal
record never grants a permit. A lost commit acknowledgment is ambiguous; rereading
the original action returns its state, without another dispatch. There is no
timeout-based reset, generation rollover, retry alias, or UNKNOWN quota release.
The consumer rejects non-original action aliases. The missing job/action producer
must additionally bind each evaluation cell/repetition to its original action;
this consumer does not infer whether a newly invented run is a disguised retry.

Unsent RESERVED cancellation releases exactly the two original held reservations
and records CANCELLED atomically. Cancellation after dispatch records
CANCEL_REQUESTED and keeps quota held. Observations may record IN_FLIGHT/UNKNOWN
but cannot create or reset dispatch permission.

## Late facts and deliberately missing finalization

`recordLateFact` authenticates the original registered service and derives the
actor from the stored original authority. It does not require a still-live user
execution grant or step lease, so revocation cannot erase an already incurred
fact. It verifies canonical result/provenance bytes, exact original identity and
fixed fixture output, nullable fixture usage, receipt identity, output cap and the
original retention deadline/record-size bound. Identical receipts are idempotent;
conflicting original receipts fail.

Late facts remain `RECORDED_UNSETTLED`. They never authorize dispatch, release or
consume quota, create an artifact ID, mark the canonical invocation SUCCEEDED,
or pretend to settle provider charges. The original canonical successful-result
artifact constraint remains intact. A real registered artifact writer/rereader
and complete receipt/operation settlement transaction are still required before
backend success or quota consumption can be implemented.

## Entry points and deployment prerequisites

- POST `/api/simulation-fixtures`: genuine bounded source-original registration.
- POST `/internal/v1/simulation-invocations`: canonical action-idempotent admission.
- POST `/internal/v1/simulation-invocations/status`: current restricted status.
- POST `/internal/v1/simulation-invocations/cancel`: unsent cancellation or request.
- Java `beginSend`: once-only in-process dispatch ownership; no worker is invented.
- Java `recordLateFact`: original-service late evidence ingestion.

Internal routes require current session identity, actual mTLS service verification,
`X-Schema-Version: 1.0.0` and a single `X-Simulation-Run-Token`. They are enabled only
with explicit `medical.simulation-execution.enabled=true` and protected simulation
policy/trust configuration. This configuration is not an approval or a token
issuer.

Still missing: the genuine restricted evaluation job/principal/lease/authority
producer and signer; authorized quota and SIMULATION retention policy creation;
the complete context construction/persistence producer; a real Java-to-Python
dispatch consumer; registered result/provenance artifact persistence and
settlement; actual database transaction/concurrency/crash tests. These consumers
fail closed when those records or paths are absent. Existing manually asserted
UUIDs, production tokens, draft metadata and local Python files cannot substitute
for them.

## Verification boundaries

The Java tests use real strict schema/canonical validation and actual persisted
bytes produced by the existing Python SimulationExecutor for their fixture.
Service tests fault-inject transaction-return, CAS, source and ledger boundaries
to prove ordering, no-permit-on-commit-error, no replay dispatch, aggregate held
quota checks and cancellation behavior. These mocks do not prove MySQL rollback,
cross-process locking, S3 availability, or a deployed execution path. The separate
migration bundle and three-language publication checks cover their own stated
offline scope.
