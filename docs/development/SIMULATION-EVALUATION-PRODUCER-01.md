# Restricted SIMULATION evaluation producer

## What this slice implements

`SimulationEvaluationService` adds an authenticated, bounded start path from an
existing immutable EVALUATION definition and registered fixture. It checks the
actual fixed sample input version/hash, candidate membership, selected ROLE,
SIMULATION model and PROMPT, exact repeat bound, current source rights, and the
separate execute, enterprise/evaluation quota and evaluation retention grants.
It creates no grants, source approvals, role publications or evaluation scores.

The producer constructs and persists a real context, source access set and
candidate dependency manifest. It creates canonical `execution_registry`
SYSTEM_JOB, `system_job_run`, `run_principal`, `run_step` and self-origin
`step_action_binding` rows. A separately registered scope manifest contains actual
scope JSON. The system job has no Temporal workflow ID and does not claim a
workflow was started. Start creates READY work; only a successful mTLS claim
makes the step and job RUNNING.

Migration 061 binds one original run/action/invocation identity to the immutable
(tenant, evaluation, sample, candidate, repeat index) cell. The invocation UUID is
allocated at start; the canonical invocation row is created only by real ledger
admission. A changed request for that cell cannot create an alternative run.
`evaluation_batch.revision = score_count + 1` is untouched. Job state uses its own
READY/CLAIMED/ISSUED revisions 1/2/3.

## Authority and signing

Start requires a genuine current application session and context revision.
Claim/issue use genuine registered mTLS identity and current persisted initiator
binding, account, membership, project status and tenant epochs. They never create
or hydrate an application-session Verified object. The principal is explicitly
limited to one EVALUATION resource, `model.simulation.execute`,
`SIMULATION_EVALUATION`, one registered service and the separate simulation
audience.

Claim is READY-only revision/generation CAS. It has no expired-lease takeover,
generation rollover or timeout reset. Issue rereads all current candidate,
context, source and grant bindings, validates the claimed service/lease, and uses
the protected simulation directory's actual RSA private key. The real JWS is
verified against that directory's current policy before the `principal_token`,
immutable execution proof and canonical invocation admission are committed.
Production `RunTokenAuthority` is unchanged.

Token issue is once-only. Raw tokens are not stored in the job, outbox or audit.
If issue commits but its acknowledgment is lost, the status API can show ISSUED;
it cannot recover the raw bearer, mint a replacement, extend the lease or invent
a new action. This is deliberately fail-closed and is an operational limitation.
Replaying start returns its original job only after current authorization checks.
The status-only worker route does not require reading source bytes again and
returns no source content, raw token or admission payload.

The existing consumer now supports private worker actor admission for actual
reserve/beginSend, while preserving its commit-winner-only process-local dispatch
permit. No HTTP route serializes that permit. A worker's source-read guard checks
current token/lease/grant authority on every authorized S3 read boundary.

## Additive entry points

All request bodies use `simulation-evaluation-start.schema.json`, a new local
strict API contract. Frozen published base/evaluation/simulation schema bytes and
the c383 simulation publication are not modified by this producer.

- POST `/api/simulation-evaluations/enterprise-quota`: explicit enterprise quota
  account creation, with the actual current enterprise quota grant.
- POST `/api/simulation-evaluations/retention`: explicit evaluation retention
  policy creation, with the actual current evaluation retention grant.
- POST `/api/simulation-evaluations`: fixed-cell start. Parent quota creation is
  explicitly requested in this body and uses the actual evaluation quota grant.
- POST `/internal/v1/simulation-evaluations/claim`: READY-only service lease.
- POST `/internal/v1/simulation-evaluations/issue`: once-only real signed token
  and canonical admission, returned only after successful transaction completion.
- POST `/internal/v1/simulation-evaluations/status`: original job status only.

Public routes require `X-Schema-Version: 1.0.0` and current
`X-Context-Revision`. All bodies are bounded to 64 KiB. Internal token consumers
also require a single `X-Simulation-Run-Token`. Responses are no-store.

The exact internal POST route list has a default-disabled, stateless mTLS Spring
Security chain ahead of browser chains. No browser session/cookie, Origin,
Sec-Fetch-Site, insecure request or missing certificate can establish service
identity. Only those literal worker routes receive the separate CSRF policy;
public browser routes keep the existing local/OIDC session and CSRF checks.

The separately implemented local bridge/execute route is described in its own
handoff. It does not change these authority or issuance guarantees.

## Verification and remaining prerequisites

Unit service tests exercise actual producer-created scope/principal/step/action,
claim, real protected-key signing and strict generated admission validation,
including current-source/candidate rejection, fixed sample/repeat, CAS, expiry,
idempotency and fault-injected transactional rollback. Sources, grants and SQL
transactions in those tests are explicit doubles. They do not prove MySQL
rollback/locking or live S3 availability. RSA tests use actual protected on-disk
key/policy files, signature verification, invalid permissions and policy changes.
Security tests use real exact matchers/filter-chain construction without a server.

No database migrations, database/S3 endpoints, HTTP server, deployment, customer
content or paid provider calls were executed for this producer work.

An existing genuine fixed AGENT/ROLE/PROMPT dependency graph, source approvals,
registered fixture, grants and protected deployment configuration are still
required. At this checkpoint, draft AGENT/capability authoring has no complete
producer in this slice; arbitrary IDs or hand-seeded records are not an accepted
substitute. A user-launchable complete CP-02/RN-01 flow, registered result-artifact
writer/rereader and settlement, full scoring, publication, and actual database
concurrency/crash acceptance remain separate unfinished work. Simulation results
never count as production eligibility or provider financial settlement.
