# Restricted simulation worker actor admission

Stage 8 adds session-free worker admission alongside the Stage 7 session consumer.
It does not fabricate or reconstruct an `ApplicationSessionIdentity.Verified`, and
it does not change production `RunTokenAuthority` or its PUBLISHED ROLE gate.

`SimulationWorkerAccess` derives the initiator from the persisted SYSTEM_JOB row,
then opens the tenant transaction using current tenant epochs. It locks/rechecks
the real execution/principal, exact simulation evaluation resource and action,
registered single service/audience, active identity binding and backing account,
current active tenant membership, project status, current execute grant, and current mTLS certificate.
Both the initial and return boundaries recheck these facts. The private worker
proof has no public constructor and cannot be deserialized from request JSON.

`EnterpriseAccess.Admission` retains the original session path. Worker admission
exposes only actor/binding/proof accessors to the necessary shared read helpers;
calling `subject()` for a worker fails closed. Session-only writes retain their
existing session requirements. Worker resource reads are limited to simulation
purpose (and the existing configuration-read purpose); SESSION-owned sources are
rejected.

The session-free ledger overloads accept an actual restricted signed run token.
Before reading any fixture bytes they run the existing immutable authority and
persisted token checks. Reserve and dispatch repeat these checks in the mutation
transaction, with original action, current execution grant, lease, exact token
JTI, service, auth epoch, quota and retention checks unchanged. Claim/issue
bootstrap uses the separate worker admission, including its current execute
grant; a bootstrap proof alone is not a dispatch permit.

Source preparation uses the real approved immutable source-original records and
existing bounded S3 reader. Its concurrency lease is charged to the actual worker
initiator. Every current-source check re-enters worker admission and checks source
rights, configuration and tenant watermarks. Ledger reads additionally revalidate
the signed token and lease at every source guard. Prepared source proofs bind the
actual worker run/principal/service/binding/member identity plus current state;
no source response, permission, lease, token or session is fabricated.

Focused tests verify session absence, bounded worker execution, principal and
binding revocation, wrong registered service, and return-boundary revocation with
mocked SQL/transaction boundaries. They do not establish live database locking,
rollback, crash behavior, object storage, signing deployment, or runtime readiness.
