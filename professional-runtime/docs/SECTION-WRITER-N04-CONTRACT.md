# N04 SectionWriter boundary

This lane is TASK/N04, not PLAN_DRAFT/N01 or Researcher/N02. It uses the shared professional session and supervision tables and process inspection protocol, while keeping separate input, package, invocation, producer, prepared-envelope and candidate authorities.

## Authority and reads

- `FormalTaskNodeAuthority` reconstructs the current N04 input from actual publication, policy, retention, replay, predecessor and input registration facts.
- `TaskSectionWriterWorkerAccess` admits the real signed action token and mTLS identity, then binds that current input to the actual lease and action. Input control proofs are not treated as model-send admissions.
- `TaskSectionWriterHostAccess` obtains the registered N04 input and packages with bounded exact-version object reads. `TaskSectionWriterContextReads` separately reads the baseline body, N03 field check, N02 evidence bundles, original source objects and parsed objects. Literal chunks must match the fixed evidence references.
- The compiled document contains `section_writer_input` and `writer_content`; the latter is physically verified content, not worker assertions. Its provenance records object and source pins. MODEL_CALL authorization repeats source/model-send checks.

## Runtime

Deployment `model_tool_mode` is `SECTION_WRITER_N04_V1`; role is `SectionWriter`, purpose `TASK`, node `N04`, host tool `SECTION_WRITER_HOST_V1`. Model tool inventory is empty. The model emits one bounded textual SectionDraft; AT06 is a host action after physical body and candidate registration, not a fabricated SDK tool call.

The prepared launch is persisted with its actual envelope hash, launch hash, audit and outbox records. PREPARE never admits a process. Independent supervisor ADMITTED/F1 evidence must identify the actual process unit; F2 closure must include independently fetched session-export bytes and successful owned exit. Historical F1/F2 validation cannot recreate export bytes or authorize a new model request.

Routes are under `/internal/v1/section-writer-runtime/`. Worker and supervisor identities are different. `supervisor-plan` and `supervisor-admission` revalidate actual Writer098/093 original governance and live TASK/lease facts without impersonating the worker. Package/deployment/profile hashes and the existing process-unit evidence remain pinned.

No test, model/provider execution, deployment, credential setup, network operation, or process launch is implied by adding these sources. Enablement requires explicit operator-owned configuration and the normal published role/policy/producer grants.
