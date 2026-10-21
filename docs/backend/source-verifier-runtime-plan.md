# N05 SourceVerifier runtime implementation plan

1. Inspect the N05 review contract and existing N04/076 invocation boundaries.
2. Introduce closed N05 invocation/context types without altering N01–N04 wire contracts.
3. Materialize independently authorized source and rule reads, bind reviewed input and target provenance, and reserve a distinct reviewer session.
4. Revalidate current-input rights and model-send permission at every send; issue a genuine separate model invocation with bounded empty tools and settle its own provenance.
5. Wire Java configuration and HTTP boundaries to host AT07 registration/settlement. Preserve separation of source access and factual support.
6. Add targeted contract tests without executing tests, provider requests, runtime calls, or full builds. Report any incomplete integration explicitly.

## Implemented host slice

- Dedicated N05 lease, host, deployment, compiler, independent supervision, and HTTP classes. N04 payload schemas and identifiers are not re-labelled.
- A host-owned N05 session identity is allocated before context materialization, with explicit rejection of any writer session collision.
- Candidate/body objects and each original/parsed source object are independently consumed and hash checked. Literal source chunks, complete approved rule content and per-session receipt IDs are supplied to the reviewer prompt.
- Immutable context and source/rule read journals bind the original N05 input, session, actual physical bytes, current rule authority, audit and outbox. Revalidation never fabricates a physical Reads capability.
- Typed package/source bridges reuse existing physical-read mechanics. Current source rights and model.send checks are handled separately by the typed invocation chain on every send.
- Empty model tools are deliberate. AT07 remains a genuine host action after registered N05 review output, not a fabricated model tool call.
- Added unexecuted offline runtime contract tests. Compile-only diagnostics are permitted; no tests, provider calls, process launch, runtime calls, network operations, or push performed.

## Remaining integration boundary

The runtime HTTP surface now requires the typed N05 dispatch journal for open-workflow and every worker launch. The dispatch/activity and Python executor/supervisor delivery are being integrated by their owners; production operator provisioning remains necessary. The host slice alone is not an end-to-end execution claim.

## Freeze and compile-only result

The host/runtime slice and migration 116 are frozen. Both context and individual read records carry the actual actor/project/session metadata, classification, database timestamp, and a validated dedicated context-read schema digest. Original and StageFacts recovery recheck those immutable read rows and audit/outbox without reviving current source or model permissions.

The current N05 Java family plus its changed shared typed boundaries passed necessary javac compilation (annotation processing and implicit class emission disabled). Only deprecation notes remained. Tests and external/runtime execution have not been performed. Offline runtime contract test source is supplied for the later combined test phase.
