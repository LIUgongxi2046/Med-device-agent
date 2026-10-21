# A12 independent supervisor and physical context development slice

## Implemented
- Dedicated EVAL deployment, supervisor Request/Inspection/Admitted/Closed/ModelRequest private proofs. No TASK admission conversion. HTTPS inspection uses fresh client TLS contexts, current certificate chain/trust/service pins, independent supervisor identity, bounded reads and no redirects.
- Admission and closure bind original session, lease, action, exact input, envelope, deployment and runtime manifest. Closure requires owned waitid, exact PID/start-time membership, empty cgroup and independently fetched complete export bytes. Successful completion separately requires normal exit status zero.
- F1/F2 supervisor gate uses the independently authenticated supervisor ControlAccess route, verifies original preparation and journals, live lease, complete physical-context receipt and current authorizations. Worker and model credentials cannot mint supervisor proofs.
- Fixed physical context reads the registered original sample, all role/prompt/assembler artifact closure packages, complete fixed original and parsed source objects, and approved rule contents. Bounds, full byte counts and SHA-256 are checked with per-chunk current authorization checks. Package-only source ancestors outside the approved fixed scope fail closed.
- Current read authorization does not grant model sending. The separate provider-bound operation rechecks model-send source rights and role permission narrowing. Expected answer artifacts are not read as prompt content.
- Immutable context receipts in migration 146 include owner/project/job/node bindings, audit/outbox witnesses, schema metadata and update/delete rejection. Fresh reads on restart validate original immutable pins and exact physical content while ignoring only changing physical-read receipt IDs.
- Candidate manifest is reloaded under current original authorization and runtime manifest SHA pinned for deployment cross-checking.

## Development verification
Narrow production javac only passed using the restored dependency caches; no tests, database migrations, workflow execution, supervisor/network calls or provider calls were run. This is not an integration acceptance result.

## Deferred tests
FormalEvaluationSupervisorTest is written but not run. Integration cases remain: worker certificate on F1/F2; expired or changed supervisor trust; grant/approval/rights revocation between every source chunk; missing or truncated originals/parsed content; expected-answer non-disclosure; package source absent from fixed snapshot; wrong provider or missing external-processing role grant; altered admission/closure audit or outbox; mismatched process PID/start ticks/cgroup; killed/nonzero waitid; mismatched export object bytes; fresh-read versus original receipt recovery; wrong candidate runtime profile; full application startup bean graph and all schema/SQL constraints.
