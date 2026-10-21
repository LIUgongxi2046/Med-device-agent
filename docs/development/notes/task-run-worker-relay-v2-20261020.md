# TASK V2 worker and immutable outbox relay

Code-first integration slice. No deployment, credential provisioning, live service,
regression run, commit or RN01 enablement is performed by this change.

## Components

- `TaskRunWorkerConfigurationV2` registers the actual V2 workflow and RunActivities,
  waiting, recovery and stage HTTP clients on the pinned V2 control queue.
- `TaskRunWorkerLifecycleV2` owns only that worker factory. Startup failure cannot
  advertise running; shutdown is bounded and does not close the shared client.
- `TaskRunRelayConfigurationV2` wires the existing replay bootstrap, actual Temporal
  V2 delivery and durable workflow consumer. No alternative launch logic exists.
- `TaskRunOutboxRelayV2` selects original events. START_RUN validates the original
  outbox event ID and delegates durable attempt/recovery/CAS to the original consumer.
  An existing attempt remains query-only even after Temporal NOT_FOUND.
- Cancellation uses real RN-04 decision, operation and audit verification. Existing
  consumed receipt recovery precedes current cancellation authority. Signal acceptance
  is explicitly not a consumed receipt. The exact accepted original Temporal run and
  started-history V2 memo pins are checked before query/signal.
- `/internal/task-run/waiting-v2/outbox` is an exact POST-only service route. It rejects
  browser cookie/origin/Authorization headers, unknown request fields and oversized
  bodies. No caller-supplied workflow input, actor or fresh command is accepted.
- `TaskRunRelayClientConfigurationV2` independently enables one-tenant bounded
  polling. Each call obtains fresh protected TLS material, requires TLS 1.3/HTTPS,
  forbids redirects and bounds connect, request, total response time and bytes.
  A failed/ambiguous result retains the same original event. In-memory cursors are
  scan optimizations only; crash/restart rescans originals, never invents a receipt.
  Consecutive transport/rejection failures stop the poller at the configured bound.

## Explicit operator inputs, no defaults supplied

Common prerequisites: task-enabled and waiting-v2-enabled; a protected pinned V2
launch/protocol file; existing authority/receipt services; independently approved
mTLS activity client configuration. The in-repository `TaskRunTemporalConfigurationV2` creates the named
`taskRunWorkflowClientV2` only with the explicit temporal-enabled switch and the
operator's existing protected TLS/trust references. Namespace must match both
Pinned.protocol and Pinned.launch. No credential material is created.

Temporal opt-in: `medical.task-run.waiting-v2.temporal-enabled`. Required settings
under `medical.task-run.waiting-v2.temporal.` are namespace, origin (HTTPS with an
explicit port), service-uri, certificate-sha256, trust-revision,
tls.configuration-directory/owner/maximum-configuration-bytes (existing protected
`trust.json` and `client-tls.json` format), connect-timeout (100 ms..30 s),
maximum-call-timeout (at least both configured RPC bounds, at most 3 min),
maximum-message-bytes (4096..16777216), maximum-concurrent-calls (1..1024),
rpc-timeout (100 ms..60 s), long-poll-timeout (60 s..2 min).

`TaskRunTemporalChannelV2` bridges the existing SSLContext to gRPC's Java17
JdkSslContext with TLS 1.3, HTTPS hostname checks and ALPN h2. Every RPC obtains a
fresh context and isolated connection, bounded by capacity, timeout and response
bytes; no stale TLS session is reused. Per-channel transport retries are disabled,
SDK RPC retries are capped at one attempt, and no plaintext/authority override/API
key fallback exists. Channel shutdown closes only owned connections and waits a
bounded interval. Factory construction does not connect a remote network service;
actual worker RPC use is a separately enabled lifecycle action. Missing protected
material blocks bean construction clearly.

Worker opt-in: `medical.task-run.waiting-v2.worker-enabled`, activities.client-enabled,
waiting-v2.client-enabled, worker.workflow-threads and worker.activity-threads
(each 1..256), worker.shutdown-timeout (100 ms..2 min).

Server relay opt-in: `medical.task-run.waiting-v2.relay-enabled`,
relay.maximum-page-size (1..100). Missing real dependencies fail enabled construction.

Poller opt-in: `medical.task-run.waiting-v2.relay-client-enabled`,
activities.client-enabled; relay.tenant-id; relay.http-timeout (100 ms..2 min),
relay.maximum-response-bytes (4096..1048576), relay.poll-interval (1 s..5 min),
relay.shutdown-timeout (100 ms..2 min), relay.maximum-consecutive-failures (1..100).
All abbreviated relay/worker settings above share `medical.task-run.waiting-v2.`.
The poller uses the explicitly pinned activities.origin, not an arbitrary endpoint.

All switches are default-off. No sample secret, identity, approval, readiness claim,
V1 queue reassignment, workflow history rewrite or RN01 admission is added.
Old-run RESOLVE_WAITING remains unsupported: actual answer-linked new runs are a
separate producer path. Never treat status, a UUID or signal delivery as resume proof.

## Verification scope

Test sources cover default-off gates, exact POST matching, browser-header rejection,
original event/command selection, unsupported resume rejection, lifecycle bounds and
restart prohibition. Tests are written but intentionally not executed in this phase.
Java 17 diagnostic compilation is not integration or deployment certification.
