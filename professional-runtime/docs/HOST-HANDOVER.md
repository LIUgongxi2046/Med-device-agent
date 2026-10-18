# Implemented host slice

Copy `python/medical_professional_runtime/` to a new installed Python package `professional-runtime/src/medical_professional_runtime/`. Do not copy isolated venv/pip-cache. Apply `java/professional-heartbeat.patch` to the D-frozen TaskWorkflowImpl at integration time; do not overwrite that full class.

Implemented: real independently launched SDK child, private per-lease home/workspace/session, sanitized child environment, fixed official enterprise-model profile, official SDK `DeepSeekHarness.run`, outer deadline supervisor (SDK session idle wait is otherwise unbounded), current lease heartbeat, process-group shutdown, retained files/inventory hashing, no paid-call retries, exact returned registered Completion checks; actual Temporal Python Activity registration matching Java name/two arguments; concrete bounded aiohttp mTLS control and private UDS routes compatible with TypeScript PrivateBridge. No provider key is read or sent by this host.

Deployment must supply approved immutable profile files and an isolated runtime. Hash-pinned profile templates may contain only `@@MEDICAL_SOCKET_PATH@@`, `@@MEDICAL_TOKEN_PATH@@`, `@@MEDICAL_INVENTORY_PATH@@` substitutions. The runtime template/config identity and materialized actual inventory are separate recorded facts. SDK/runtime paired version is rechecked before process startup. Missing actual profile packages, bridge or approved runtime fails closed.

This is NOT a complete professional backend: server control handlers, original session-export object registration, container/cgroup isolation attestation, formal evaluation producer and E's independent evaluation admission are missing. Local process-group kill is not proof of provider cancellation or all isolated descendants' terminal status. Backend finish must require its isolation supervisor's actual closure evidence.

# Actual verification

- 19 host tests PASS: real bounded fixture child processes (not real providers/professional outputs).
- 4 actual installed Temporal ActivityEnvironment/API tests PASS, including real host subprocess; no Temporal server workflow run was claimed.
- 8 socket-free route rejection tests PASS. These do not replace UDS tests.
- 10 live UDS tests ERROR/BLOCKED: socket(AF_UNIX,SOCK_STREAM) denied EPERM by current execution environment. No escalation or bypass attempted. They remain in test_bridge.py and do not silently skip or pass.
- Official PyPI isolated test installation: temporalio 1.34.0; deepseek-harness-sdk and deepseek-harness-runtime-bin 0.2.1rc2. Their API matches host usage. This is not the original frozen source commit or a deployment publication proof; production version/bytes must match the approved runtime manifest.
- No paid provider called; no shared source edit, global Maven/build, migration application, commit or push.

Commands (from repo parent):
`PYTHONPATH=/tmp/stage22-task-runtime/j-professional-runtime/python /tmp/stage22-task-runtime/j-professional-runtime/venv/bin/python -m unittest discover -s /tmp/stage22-task-runtime/j-professional-runtime/tests -p test_host.py -v`
Repeat with test_temporal_adapter.py and test_bridge_routes.py. The full discovery includes the blocked live test_bridge.py and is not all-green.

Final freeze: source digests are in FROZEN-SHA256.json, machine-readable test scope in VERIFICATION.json. Final logs are logs/final-test_host.py.log (19), logs/final-test_temporal_adapter.py.log (4), logs/final-test_bridge_routes.py.log (8). Both isolated Java compilation commands completed successfully. The full D TaskWorkflowImpl.java copy in java/ was only for compilation; integrate only its one-line heartbeat patch. Likewise, apply only evaluation/patches/evaluation-execution-definition.patch to A's current EvaluationService; the full evaluation/java/EvaluationService.java was only for compilation. No 083 DDL was created or used. FormalEvaluationExecutionProtocol is data shape only, not a private evidence proof.

The host installs an inventory guard on the UDS bridge before child startup. Each model/tool/result bridge request requires the actual plugin inventory to match the approved expected inventory before any upstream forwarding, not merely after SDK completion. Missing inventory fails closed. Existing TS startup tool/plugin guard is preserved. Actual deployment runtime-file integrity and isolation still require the original approved deployment proof and must not be inferred from this local inventory check.
