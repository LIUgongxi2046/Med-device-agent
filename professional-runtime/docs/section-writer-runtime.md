# TASK/N04 SectionWriter runtime entry

This is the dedicated `SECTION_WRITER_N04_V1` integration. It shares physical
process isolation with `ProfessionalHost`, not N02 input, object-registration,
completion, or purpose schemas. N01/N02 protocol bytes remain unchanged.

## Actual deployment wiring (operator supplies approved existing configuration)

1. Install this package including `schemas/task-section-writer-invocation.schema.json`
   and paired approved SDK/runtime/profile artifacts. The executor profile selects
   `role: SectionWriter`, `modelToolMode: SECTION_WRITER_N04_V1`. Inventory tools are
   empty. AT06 is a server-owned finish action, never a fabricated model tool call.
2. Instantiate two separately pinned `PinnedHttps` transports for the independent
   supervisor's backend admission reader and host relay. Open them for its lifetime.
   Call `section_writer_deployment.supervisor_service` with actual operator isolation
   slots, private receipt/socket roots and distinct worker/backend certificate pins.
   Serve its `.application()` using an existing client-certificate-required TLS
   listener. It accepts no preloaded caller launch plans.
3. Instantiate `SectionWriterHttpControl` with the existing worker mTLS context.
   Create `host_relay(control, {}, supervisor_certificate_sha256)` and serve its
   `.application()` over existing client-certificate-required TLS. The host adds
   only authenticated prepared session bindings; a caller cannot register them.
   The relay uses the shared private `/internal/v1/professional-runtime/relay`
   transport path but forwards only through N04 control endpoints. `/v1/tools/*`
   is rejected before forwarding.
4. For an all-in-one worker/relay service, invoke `serve_worker_with_relay` with
   the existing server mTLS context, fixed bind IP/port and supervisor certificate pin.
   It starts the real TLS relay before registering the Temporal worker and closes
   both on shutdown. Alternatively, with a separately served relay, invoke
   `serve_worker(client, deployment, control, supervisor_transport, relay,
   stop, concurrency, enabled=True, protocol=..., protocol_sha256=...)`. This entry
   opens worker transports and runs a real Temporal Worker registered as
   `medical.task.section-writer.execute.v1`. No provider/runtime/credentials are
   created by this entry. `enabled` and an exact hash-pinned N04 protocol are mandatory.
5. The workflow calls this activity with its actual `WorkflowProtocol.Reference`
   and `Dispatch`, not an arbitrary actor locator. `/open-workflow` revalidates
   original workflow/dispatch and derives actor, host action and prepared envelope
   server-side. Python compares tenant/run/step/input/generation to that result.
6. `/supervisor-plan` returns `prepared_envelope_base64` and `preparation_receipt`.
   No N02 `registered_envelope_base64` or object-registration receipt is invented.
   Physically read writer content and source chunks are hash-pinned and preserved
   verbatim in the N04 prompt; current rights are rechecked by Java before sending.
7. `finish` returns the exact immutable AT06 saved-candidate receipt with `READY`
   and original candidate/body/audit/event identifiers. It is not a task completion,
   adoption, new body revision, N05/N06 result, or run success. Original completion
   recovery must return that original receipt, never a second model invocation.
   A reused local job directory blocks process relaunch.

## Protocol fields

Worker deployment: `schema_version=1.0.0`, UUID `revision`, `namespace`,
`control_task_queue`, `section_writer_task_queue`, `workflow_type=medical.task.run`,
`activity=medical.task.section-writer.execute.v1`, `execution_kind=TASK`,
`node_kind=N04`, `model_tool_mode=SECTION_WRITER_N04_V1`.

Model gateway uses packaged `section_writer_gateway_entrypoint.py` with isolated
Python and exact existing config/package paths. Configuration is the existing TASK
configuration plus `node_kind=N04` and `model_tool_mode=SECTION_WRITER_N04_V1`.
Its closed enum routes to `/internal/v1/section-writer-invocations`. Recovery reads
and records the original held actual076 receipt and cannot dispatch another send.

## Verification status

Tests were authored, not executed. Only Python AST parsing was performed. No
package installation, full build, runtime startup, provider/network operation,
credential handling, deployment, commit or push was performed for this slice.
End-to-end code closure depends on actual workflow dispatch, C04/input materializer
and Java original candidate recovery integration; do not infer overall A6 completion
from the presence of these adapters.
