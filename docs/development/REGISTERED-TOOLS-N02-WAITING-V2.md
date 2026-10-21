# Registered N02 tools and truthful waiting control V2

Status: integrable component implementation, not closure of A2 for every published role. Full regression and integration execution are deferred at the user's request. No deployment, provider request, runtime launch, model result, approval or migration execution is claimed by this document.

## Implemented boundaries

| Node / role | Tool | Actual content / effect | Required authority beyond a name or UUID |
|---|---|---|---|
| N02 Researcher / NetworkResearcher | AT-03 `read_artifact` | Full exact predecessor `EvidenceBundle`; empty `node_ids`, null cursor only | Frozen TASK `task.read` at replay business purpose intersects node/role/tool; separately A1 private predecessor GET, original output and completion, current `HOST_PREDECESSOR_CONSUME`, exact input predecessor pin and current source rights |
| N02 Researcher / NetworkResearcher | AT-04 `load_skill` | Exact released SKILL source artifact bytes, bounded UTF-8 JSON/plain text/Markdown | Current released capability, `configuration.read` at `AGENT_PUBLICATION`, role/node/tool intersection, governed package registration/lineage and actual verified object GET |
| N02 Researcher / NetworkResearcher | AT-08 `ask_user_question` | One bounded `waiting_item`, operation, audit and outbox plus immutable tool receipt | Actual final exclusive settled C07 ask call; current TASK `task.run` at `TASK_EXECUTION`; same node/role/tool intersection; frozen responsible owner, section and requirements; genuine accepted and configured V2 protocol |

AT-03 TASK read is business admission only. It never grants arbitrary artifact bytes, metadata-derived access, fragments, sibling results, a dynamic UUID grant or a result-artifact fallback. Every actual returned bundle must match the frozen `evidence_artifact_id` and hash before and after physical reading. No evidence bundle is synthesized by AT-08.

AT-04 reads actual governed bytes, not the metadata describing a package. Subsequent C07 use separately checks current `MODEL_SEND` authorization for the actual provider and the exact current input's snapshot, parse version and rights revision. Historical read permission is not transmission permission.

AT-08 currently accepts `NEED_EVIDENCE` or `CLARIFY_APPLICABILITY`, one exact frozen target section, at most 64 frozen requirement IDs, no evidence refs, and a nonblank question of at most 4096 UTF-8 bytes. It does not select an arbitrary owner, confirm a plan, resolve an issue, adopt text, approve anything or resume a run. Same session/call derives the same action/item/receipt. Creating the item and final tool receipt share one transaction; original replay validates audit/outbox and exact original call/context. A canceled/stale run or lease cannot produce a new effect. Current use rejects resolved/STALE items; original recovery retains the immutable creation receipt after resolution.

AT-01/02 and public SDK schemas and tool catalogue bytes remain unchanged.

## Private waiting completion and actual route

1. Default-off `medical.professional-runtime.waiting-v2-enabled` plus `task-enabled` wires the complete waiting graph. The gate is absent otherwise. Presence of a flag is not run authorization.
2. `/internal/v1/professional-runtime/open-v2` checks the exact authenticated original dispatch, real memo-verified V2 launch and configured protocol digest before preparing the existing isolated N02 host.
3. Python `ProfessionalActivitiesV2` registers only `medical.professional.step.v2`; `ProfessionalHostV2` uses explicit `HttpControlV2`. The pinned deployment includes exact load/recovery/undispatched-failure/execute-stage/commit-stage/professional/waiting activity names and version marker. V1 activity rejects the union.
4. AT-08 writes a real waiting item only after actual settled C07 output proves the last response consists of the sole matching ask call. Prior duplicate asks, raw call IDs, arguments mismatch, pending invocations, concurrent sibling calls or later model dispatch do not qualify.
5. `/internal/v1/professional-runtime/finish-v2` records the host observation, obtains independent supervisor archive/closure, and creates a private waiting producer receipt only if the native recorded call/result and actual completed turn match the immutable AT-08 receipt. It does not turn SDK `completed` into professional `SUCCEEDED`.
6. `ProfessionalOutcomeV2` is the closed `RESULT` / `WAITING_CONTROL` union. The waiting branch has a `WaitingCompletionV2` producer receipt locator, not an artifact. `outputs` is empty. The existing V1 completion remains untouched.
7. B01 consumes `TaskRegisteredToolWaitingCompletion.Produced` under current node authority and exact dispatch/lease/revision checks, then performs the separate waiting V2 CAS/journal. Its `Original` is recovery-only and never mints a current grant, lease or model request. A different original dispatch cannot reuse the locator.
8. Any saved AT-08 question prevents subsequent tool/model history continuation in the old session, even after its item is later marked STALE. Recovery discovers original producer/commit journals through B01's read-only V2 recovery activity; a recovered RUNNING dispatch is never rescheduled to the professional activity. New-run answer linkage below does not resume the old run. Pending stage obligations remain in the genuine waiting workflow/lifecycle implementation, not a fabricated successful output.

The private producer body binds tenant/project/actor/classification, original node input and receipt, session/deployment/dispatch, protocol digest, original admitted and closed supervision, exact settled invocation/accounting/capture/result pins, original AT-08 receipt, and actual archive bytes. Original verification checks independent immutable operation/audit/outbox journals, opened session and closed supervisor journals, exact final C07 call and native trace. Full archive bytes are retained with their hash to prove the selected JSONL is a member of the original exported archive, not an unrelated log carrying the same claimed archive hash. Bounds are 2 MiB for the selected native log, 4 MiB archive, 20,000 events and 16 MiB producer document.

## Human answer and actual new-run linkage

The default-off V2 graph plus `medical.task-run.waiting-v2.server-enabled` includes these normal current-session endpoints. The answer factories share the commit reader's explicit server gate; host-only V2 does not accidentally enable them. Existing application-session authentication, CSRF/Origin checks and tenant context are required; no worker credential or caller-supplied grant is accepted.

- `GET /api/work-items/{waiting}/registered-question` returns the exact original question, responsible owner, cause digest, status/revision and decision requirements. It does not return arbitrary predecessor/package/source content.
- `POST /api/work-items/{waiting}/registered-question/answers` requires the standard context headers and idempotency key. The closed JSON request is `{schema_version:"1.0.0", waiting_item_id, expected_revision:"1", cause_sha256, decision:"LINK_CONFIRMED_REVISION_AND_NEW_RUN", answer_text, plan_confirmation:{decision_id,receipt_sha256}, target_run:{run_id,command_id,creation_operation_id,creation_receipt_sha256,replay_sha256}, reason}`.
- `POST /api/work-items/{waiting}/registered-question/answers/recovery` accepts only `{schema_version:"1.0.0", original_idempotency_key, original_request}` and returns the immutable original receipt. A missing original never creates an answer.

The caller must be the exact frozen section assignee, currently active, with existing `work_item.read` / `WORK_ITEM_READING`, `work_item.resolve` / `WORK_ITEM_RESOLUTION` and `task.read` / `TASK_READING` for this task. No permission is implicitly registered or granted. A private `CurrentQuestion` validates the independently generated ASK_USER_QUESTION receipt; a separate private `Original` joins the genuine final C07 call, session archive/closure, original dispatch, accepted V2 protocol and immutable waiting commit. The current human admission is never replaced with the original run actor. The latter is used only to verify factual original metadata.

Before an answer can be linked, the backend consumes the actual existing TK-04 CONFIRM receipt and RN-01 creation receipt/START_RUN command. It independently checks operation/audit/outbox, confirmed plan and task-input/version hashes, descendant task revisions, original run scope/principal/replay and matching new task/plan/snapshot/budget. Confirmation must follow the question and creation must follow confirmation. The target run must be different and belong to the same tenant/project/task. NEED_EVIDENCE requires changed real snapshot members; CLARIFY_APPLICABILITY requires changed confirmed plan or profile bytes. The target section must remain explicit. These checks attest a genuine business change and new-run association, not medical correctness or satisfaction of the question.

Free text is only a bounded answer record. It is never parsed into facts, adopted as content, used as plan approval, or treated as an execution grant. The existing normal business workflow must independently produce the revised inputs, confirmation and real new run; this endpoint neither invokes those commands nor bypasses their admission requirements. For example, any necessary cancellation/supersession of the old run remains its actual lifecycle command, not a side effect of answering.

One transaction saves the immutable answer/link operation, audit, outbox and registry hash and performs the exact WAITING_INPUT revision-1 to STALE revision-2 CAS. STALE alone is not a resume proof. Replaying the same key validates the original journals, link receipts, request bytes and current owner rights without new model/provider calls or a second run. The response explicitly sets `can_resume_frozen_run:false`, `answer_is_approved_fact:false` and `human_checkpoint_approved:false` and identifies the actual target run.

Frontend integration must retain the question's cause/revision, obtain the exact CONFIRM and RN-01 receipt hashes from their existing business responses, submit these typed pins and preserve the idempotency key for recovery. It must not display “old run resumed” or mark answer text as an approved fact. A stale item links to the actual new run rather than waking the old workflow.

## Actual native session encoding provenance

`registered-tool-runtime-format-provenance.json` contains full official URLs, package versions, archive integrity values, SHA-256s, source lengths and byte-identical offsets inside the verified runtime binary. No runtime binary or credentials are committed.

Pinned Python SDK/runtime-bin: `0.2.1rc2`. Matching npm packages: `0.2.1-rc.2`, not the unrelated installed alpha packages. Physical path is `<job>/home/sessions/<projectKey(<job>/workspace)>/<session UUID>/session.v4.jsonl`. The pinned writer uses UTF-8 newline-terminated physical V4 header and event records; `sourceEventSeqs` can contain integers or inclusive ranges. Header and event time/sequence, exact session/cwd, final ask call, canonical result message, closed step and genuine `turn/end` reason `completed` are checked. There is no persisted `concludesTurn` field. An unfinished/torn suffix, future timestamp, post-ask event/call, wrong result, unknown required event or mismatched archive/session fails closed.

The fixture generator in `professional-runtime/tests/fixtures/session-v4` uses only explicit synthetic logical test records and the authentic hash-pinned encoder. It never starts a harness, worker, socket or provider, and its fixture is not evidence of a real completed run. Fixture generation is separate from executing tests.

## Closed adapter entrance for subsequent nodes

`TaskRegisteredToolAuthority.requireNode(nodeKind, roleName, toolId)` is an explicit closed N02 triple. The private `Checked`, `WaitingToolCall`, `WaitingTerminal`, `Produced` and `Original` constructors are not public context factories. The central route does not treat catalogue membership as authority. Future node implementations must introduce their own real node-specific private proof/adapters and be deliberately registered by that node's configuration; they cannot pass raw node JSON or a metadata UUID into an N02 proof.

Each new node adapter must provide all four independently checked interfaces before its route is enabled:

- Current action admission: exact authenticated node/role/lease/input, released tool/schema, real settled invocation/call/arguments, current scoped permission, cancellation and frozen owner/target checks.
- Content: exact input-bound artifact kind and source proof before I/O, actual verified bytes and original registry/completion receipt, with no generic metadata fallback.
- Immutable original replay and current transmission: exact original action/session/context/audit/outbox with no live grant minted; separate current provider-specific `MODEL_SEND` and source rights on reuse.
- Waiting outcome: a genuine node-owned waiting cause and immutable receipt, actual completed SDK trace/closure and exact settled call, explicit V2 union, current commit proof and recovery-only original proof. No evaluator cause or fabricated result artifact may be substituted.

## Remaining node/content gaps

- N01 Planner: real plan input/context, template/attributes/source-list reads, plan-specific targets/owner and supported question kinds. Catalogue AT-03/04/08 declarations do not complete these adapters.
- N03 FieldVerifier: real strict field target/input/lease/call adapter and authoritative field/check artifacts; AT-05 integration belongs to its dedicated implementation.
- N04 SectionWriter: exact assigned-section/candidate/body context and artifact readers; text adoption remains human governed.
- N05/N07 SourceVerifier: exact review targets, source/check/findings context and independent-review artifact readers.
- N06 Assembler: exact fixed chapters/facts/relationships and assembly/check manifest content proofs.
- N02 NetworkResearcher beyond the common predecessor path: actual adopted C10 acquisition/record content is not authorized merely because AT-03 is published. It needs its own exact governed reader.
- AT-03 same-step result kinds, general node/fragment slicing and cursor paging are not implemented. AT-08 additional question kinds/evidence refs need their actual governed target/owner contracts.

These gaps remain open in A2 and the corresponding node ledger items. This component is not advertised as a universal artifact or role adapter.
