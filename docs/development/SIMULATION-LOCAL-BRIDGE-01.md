# Private local simulation pipe bridge

`SimulationLocalProcessBridge.execute` accepts only the private `DispatchPermit`
returned after a successful committed ledger send CAS. It consumes that permit
before validation or process creation. It never retries; every process, timeout,
I/O or validation failure is `SIMULATION_BRIDGE_UNKNOWN`. The caller must retain
the original reservation and use the existing observation/fact paths rather than
create another run or reset a dispatch. There is no HTTP dispatch endpoint here.

The trusted deployment configuration selects an absolute Python executable,
schema root, pre-existing private store, finite record limit and timeout. The
executable runs `-I -m medical_model_gateway.simulation_bridge` without a shell,
with a cleared environment. The selected interpreter must have the built gateway
and contracts packages installed. The schema root is not an import-path fallback.
Stdin/stdout/stderr are bounded and concurrent; request content is not placed in
command arguments or logs. Process errors are redacted, including their causes.

The Python adapter validates the published request schema, actual byte counter,
fixed fixture/input/parameters/settings hashes and local request binding, then
executes only the existing `SimulationProvider` and private immutable local file
store. It checks the finite output cap before storage. No model credentials,
remote endpoint, network server or paid provider is used. The adapter is a private
process component, not an authorization service: JSON validity is never an
execution approval. Java remains the authenticated ledger/dispatch owner.

Exact canonical result and provenance bytes return through a closed base64
envelope. Java revalidates them against the original fixed request and returns an
immutable `LocalFact`. This is not registered artifact success, quota settlement,
an evaluation score, or production eligibility. A genuine worker must record the
fact through the original-service path; database artifact finalization and
settlement remain separate work.

The optional `POST /internal/v1/simulation-evaluations/execute` connects the
session-free authenticated ledger reserve/send methods to this bridge, then writes
the local fact with original-service authentication. It requires both explicit
`medical.simulation-execution.enabled=true` and `local-bridge.enabled=true`, and
the exact worker mTLS route plus restricted signed token. The bridge configuration
requires `python-executable`, `project-root`, `store-directory`,
`maximum-record-bytes`, and `deadline-seconds`; no paths or shell commands come
from the request. Replay without a new committed permit returns the original
invocation state without executing. A bridge failure attempts an UNKNOWN
observation; even if current authorization prevents that observation, the original
DISPATCHING state/reservation stays intact. The endpoint returns the existing
`INVOCATION_UNKNOWN` error, never releases funds/units or creates another run.
Successful fact ingestion reports `RECORDED_UNSETTLED`, with the canonical
invocation still unfinalized and settlement PENDING.

Tests distinguish three boundaries: local Python execution/CLI with actual files;
Java permit and failure unit tests with explicit runner doubles; an explicitly
configured Java-to-installed-Python process check with a permit double. None proves
database commit/locking, HTTP service identity, or end-to-end evaluation execution.

The private local store is an immutable execution journal, not the registered
artifact repository. Automated reconciliation and retention cleanup of these local
records are not implemented by this component. Deployment remains default-disabled;
a production retention lifecycle must be connected alongside artifact finalization
before treating this as an operational release.
