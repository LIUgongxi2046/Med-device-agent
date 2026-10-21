# Closed N05 independent SourceVerifier runtime

This is an additive explicit `SOURCE_VERIFIER_N05_V1` mode. It does not relabel an
N04 result, writer source receipt, or author session. The existing physical process
host, independently deployed F1/F2 supervisor, bounded private UDS bridge and original
request recorder are shared; business context and completion are N05-specific.

## Operator entry points

- `source_verifier_deployment.serve_worker_with_relay(...)` starts an mTLS relay and
  the `medical.task.source-verifier.execute.v1` Temporal activity. Configuration,
  TLS identities, namespace, queues and pinned isolation slots must already exist.
- `source_verifier_deployment.supervisor_service(...).application()` supplies the
  independently authenticated supervisor. Start its server with mandatory client
  certificates. Backend and host relay use distinct `PinnedHttps` transports.
- Worker entries additionally require the original Java `workflow_extension` and
  `workflow_extension_sha256`, cross-pinning its professional queue and activity.
  This separate extension preserves the base and N04 protocol digests.
- `source_verifier_deployment.require_deployment(...)` requires a closed, SHA-pinned
  protocol with N05 activity, queue, node and mode. It never alters N01/N02/N04 queues.
- The immutable published executor profile must explicitly select role
  `SourceVerifier` and `modelToolMode: SOURCE_VERIFIER_N05_V1`; no default profile or
  installed package is silently modified. Inventory must contain zero model tools.

## Authority and recovery

The worker opens only `/internal/v1/source-verifier-runtime/open-workflow` with the
original workflow reference and dispatch. Backend derives the actor and checks the
workflow journal. A completed original AT07 is returned before live deadline/lease
preparation; no fresh bridge, process or model send is allowed for that recovery.

The launch compiler is `N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1`. Host
validation requires the complete candidate/body bytes, all frozen rule definitions
and their independent read receipts, and exactly the evidence set used by the
candidate. Source literal hashes and physical object pins are checked. Authenticated
Java authority owns registration, source rights, writer-session separation and
model-send reauthorization; Python checks do not fabricate that authority.

A verified physical read with a locator mismatch remains explicitly
`LOCATOR_MISMATCH`, empty literal, located=false and complete=false. It is not a
successful read or evidence for PASS. Missing/unauthorized object access is blocked.

The independent request recorder retains the original SDK projection schema:
`urn:medical:task-source-verifier-invocation:1.0.0#/$defs/SdkProjection`, actual resource
SHA-256 `769dfe349a15bc44e820c21911923332af9a3bdc5aaa03b0bc8bef13dca8e4d1`.
It validates exact received bytes and independent peer identity. No schema weakening,
provider endpoint, caller actor, or writer proof is accepted as a substitute.

## Completion and remaining integration

Only the exact original AT07 receipt is returned. `WAITING_INPUT` and incomplete
coverage are preserved. `SUCCEEDED` means the independent check completed, not
professional approval, adoption, publication, or success of the entire task.
`professional_approval` must remain false.

Java must provide matching authenticated open-workflow, completed-original,
supervisor-plan/admission, bridge, admit/heartbeat, finish and closure routes; its
finish path must persist genuine ReviewFindings and coverage then submit AT07 using
the original signed action. Deployment still requires approved immutable profile,
release/invocation pins, separate certificates, isolation slots and queues. Source
implementation alone is not evidence of live integration or production readiness.

Source tests are in `tests/test_source_verifier_runtime.py`. They have been written
but not run, in accordance with the request to finish development before testing.
