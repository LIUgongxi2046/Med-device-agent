# Exact missing server integration surface

`control.py` is a real aiohttp TLS client, not a mock service. Every path below is a new **missing** server-side handler. Do not enable the host until original-ledger implementations exist. Each request has `Content-Type: application/json`; no cookie, browser identity, redirect, caller-selected origin, alternate provider or new persistent credential. The existing deployment supplies its service mTLS context. Responses are direct private protocol JSON (not the public business envelope), except bridge streaming.

Binding (present in all requests):
`{tenant_id,run_id,step_id,action_id,lease_generation,input_sha256,step_envelope_sha256}`. UUIDs, integer generation>=1, lowercase SHA256. Original identity/lease/envelope re-read must match; client fields do not confer authority.

1. POST `/internal/v1/professional-runtime/open`
Request: `{binding,reference,dispatch}`; reference and dispatch are the existing Java WorkflowProtocol JSON (camelCase). Verify workflow owner, immutable dispatch/envelope, approved node/policy/principal/runtime, current lease and isolated job allocation. Persist unique session_id atomically once per step/generation. Duplicate request returns existing registered completion or blocks/reconciles the original session; never a fresh paid run.
Response START:
- binding (exact echo)
- state: START
- purpose: TASK or EVALUATION_VERIFICATION (explicitly rejects MODEL_VERIFICATION/SIMULATION)
- session_id: server-owned UUID
- node_kind and role: N01 Planner; N02 Researcher/NetworkResearcher; N03 FieldVerifier; N04 SectionWriter; N05 SourceVerifier; N06 Assembler; N07 SourceVerifier. N00/N08/N09 are dedicated non-model handlers, not this SDK path.
- isolation_receipt_id: actual approved deployment isolation supervisor record (must not be self-reported)
- runtime_manifest_sha256: exact original approved runtime manifest
- job_directory: exact deployment jobsRoot/tenant/run/step/generation
- prompt: original authorized exact step input after source/context checks, never browser-supplied prompt override
- profile_files: list of `{source,relative,sha256}`; source is a server-deployment immutable file; relative begins profiles/ and cannot traverse. Hash is checked before optional literal host path substitution. Dependency bundles must actually exist; the host does no package install.
- sdk: closed object `{model,reasoning_effort,max_tokens,initialize_timeout_seconds,request_timeout_seconds,shutdown_timeout_seconds,dsh_bin,sdk_version,runtime_version}`. enterprise-model is fixed by host. SDK/runtime versions must match approved paired artifacts; 0.0.0.dev0 and SIMULATION are forbidden.
- expected_inventory: `{plugin_names,tool_schema_hashes,providers,models,model_stream_schema_ref}` from actual fixed runtime. Providers must be exactly enterprise-model/企业模型接口. The host compares actual emitted inventory and rejects extra/duplicate/missing plugins/tools/models.
Response COMPLETED: `{binding,state:COMPLETED,purpose,session_id,completion}`. completion must be exact existing registered WorkflowProtocol.Completion. The host does not launch a child for this branch.

2. POST `/internal/v1/professional-runtime/heartbeat`
Request `{binding,session_id}`. Recheck current grants/policy/lease and original session ownership; use actual RunTokenAuthority.renewStep/renew if appropriate, without returning secrets to Python/SDK logs.
Response `{binding,session_id,admission:CURRENT}`. Any other response stops processing. Heartbeat loop is at most 15 seconds, Java activity heartbeat timeout patch is 45 seconds. Provider state remains distinct from process liveness.

3. POST `/internal/v1/professional-runtime/close-admission`
Request `{binding,session_id,reason}`. Idempotently stop new actions for the original attempt even when user access has been revoked. Authenticate original service+session ownership. Response `{binding,session_id,admission:CLOSED}`. A closed socket/process never proves remote provider cancellation.

4. POST `/internal/v1/professional-runtime/bridge`
Request `{binding,session_id,method,route,body,idempotency_key}`. UDS handler allows only:
- POST /v1/model-contexts → original IA-06 preparation
- POST /v1/model-streams → original IA-07, bound original action, no extra sends
- POST /v1/model-cancellations → original current/original invocation cancellation route
- GET /v1/model-results/{UUID} → original IA-01, exact invocation/result relation
- POST /v1/tools/AT-01 through AT-10 → server role-policy mapped business action
Discard/reject caller attempts to override any bound identity. Derive action_id from original model call mapping and body SHA. AT-08 saves an actual waiting item; it does not grant immediate approval. Enforce current scopes, actual authorized args and complete output schema at the backend.
Response: direct original schema JSON, or validated IA-07 SSE (`text/event-stream; charset=utf-8`, `Cache-Control: no-store`). Never fabricate an accepted stream, completed invocation or ModelResult. D/E need independent EVALUATION_VERIFICATION admission before these routes can execute evaluation models.

5. POST `/internal/v1/professional-runtime/finish`
Request `{binding,session_id,observation,files}`. observation is untrusted SDK `{session_id,binding,finish_reason,final_response}`; files list actual `{path,sha256,size_bytes}` for retained local job files. Runtime inventory is retained as runtime-inventory.json. These values are **not** original-object registration or business success proofs.
Required backend work: acquire real isolation supervisor exit evidence, retrieve/upload original bounded session export and required artifact bytes through actual object service, read registered objects back, verify SDK/runtime/profile/config/session identity; re-read run_step/action/invocation/provider terminal+settlement/result/check/waiting ledger. Apply all AS/V/N node requirements and current lease CAS. `completed` only prompts checking. No SDK token counts or self-reported judgments count. Missing originals = failure/blocker, never synthetic SUCCESS.
Response: existing WorkflowProtocol.Completion `{runId,stepId,inputSha256,leaseGeneration,status,resultArtifactId,resultSha256}`. Only actual registered StepResult reference, status SUCCEEDED/FAILED/WAITING_INPUT; current server ledger determines it.

6. POST `/internal/v1/professional-runtime/failure`
Request `{binding,session_id,reason,host_process_exited}`. Record original observation and reconciliation requirement under original service authority; host_process_exited describes local child only and cannot cancel providers or commit business versions. Do not overwrite a previously committed original Completion after an uncertain response. Response is bounded JSON acknowledgment.

## Required integration test gates still not run
Real service mTLS end-to-end; original ledger/objects; approved isolated runtime and same-hash wheel/profile; UDS in a permitted environment; actual Temporal Java→Python round trip; provider calls only with explicit existing authorized configuration; 083 original EVALUATION_VERIFICATION evidence and 080/081 consumption. No test fixture in this package can satisfy those gates.
