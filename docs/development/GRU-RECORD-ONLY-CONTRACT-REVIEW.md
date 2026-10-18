# Gru RECORD_ONLY bounded contract review

Reviewed 2026-10-18. This is a source/contract review and staged test specification. No compilation, JUnit, Maven, database, HTTP, model, commit, or push was performed by this review. All changes made by this review remain outside compiled roots pending the central stage-14 integration gate.

## Decision and authorization boundary

The proposed local record-only slice can reuse `conversation.use` and `conversation.read` on the existing real `DOCUMENT` resource with purpose `DOCUMENT_BODY`; it does not require changing the frozen permission schema or introducing a `CONVERSATION` permission kind.

- `permission-decisions.schema.json#/$defs/Action` uses `^[a-z][a-z0-9_.-]{0,63}$`; both action names already satisfy it. `Condition.purposes` accepts `DOCUMENT_BODY` under its existing uppercase-purpose pattern. Syntax compatibility alone grants no authority.
- `backend-design/接口补充设计.md`, FRONT-I07, already specifies `conversation.read/use`, owner or authorized operator, and message-bound cancellation. FRONT-I14 shares the existing `SourceScope` and `Selection` definitions with GR-02.
- `PermissionMapper.resourceOwner("DOCUMENT", documentId)` resolves the actual `document_head`, including its project. `ResourceReadAuthority` verifies tenant, actor, real ownership, scope and current grants. A document-revision UUID, conversation UUID, version registry entry, or permission-shaped JSON must not substitute for that domain resource.
- Staged `GruContextAuthority.authorize` requires current `project.read`, `document.read`, and the separate `conversation.use` or `conversation.read`, all for `DOCUMENT_BODY`. It must not synthesize any missing grant. Existing underlying document/artifact/source/collection permission and rights checks still apply; conversation permission never substitutes for them.
- Staged `GruService` is narrower than the full FRONT-I07 audience: it verifies the exact stored owner and accepts only ACTIVE conversations. This does not implement delegated operators, archived/deleted conversation workflows, or their broader public contract.

## Exact fixed context and selection

Create binds the actual project, document entity, document revision, existing scope snapshot, and complete frozen `SourceScope`. Append repeats the same revision, snapshot and scope and must compare them with stored create context. A conversation does not retarget when the document head, project sources or page selection changes.

The four existing modes remain `PROJECT_APPROVED`, `COLLECTION_VERSIONS`, `SOURCE_VERSIONS`, and `COMBINED`. Network must be false, with both network revision fields explicitly null. An otherwise valid network-enabled scope is outside this slice and is rejected as `SCHEMA_UNSUPPORTED`; structurally inconsistent scopes are `SCHEMA_INVALID`.

An explicit empty collection/source list remains empty. The staged authority consumes the already-existing document-bound snapshot and verifies its full manifest, registry digest, profile, watermarks and ordered members. `PROJECT_APPROVED` means that saved snapshot here; it must not enumerate today's project again. Explicit source mode must match the saved members. Collection and combined modes additionally verify the real fixed collections and exact union with explicit sources. This slice does not create snapshots, approve sources or provide dynamic selection.

`DocumentReadService.readFixed` supplies the complete verified body and projections through the existing document/artifact/source-aware path. `requireCurrent(Admission, FixedRead)` rechecks that evidence inside the existing tenant transaction, without nesting another `EnterpriseAccess` transaction. The staged authority checks fixed tenant/project/document/revision/snapshot/access-set identity and current source rights again. Its additional source checks currently use FORMAL; this review does not claim general historical-source or archived-transcript support.

Selection is either explicitly null or the frozen `interfaces#/$defs/Selection`. `DocumentSelectionSemantics.verify` binds it to the exact saved document revision and full verified body. Text offsets are Unicode code points; full-node and exact quote digests must match. Image identity/digest and table geometry/ordered cells remain governed by the existing shared verifier. Unknown nodes, duplicate/reversed/overlapping segments, out-of-bounds ranges and stale hashes are not repaired, relocated, normalized or silently dropped. Complete image/table semantics remain covered by the existing `DocumentSelectionSemanticsTest`; the new Gru tests verify delegation with actual text/body and stale/invalid boundaries.

## Local record contract and immutable shared values

The staged `gru-records.schema.json` contains closed local Create and Append requests. It is an additive local contract, not a rewrite of the frozen public ConversationSnapshot or Message.

Append permits only `QUERY`, `RETRIEVE`, `CHECK`, or `PROPOSE_EDIT` with `execution_mode=RECORD_ONLY`. The exact submitted user text is retained, including meaningful whitespace, combining characters and CR/LF/tab; blank text, non-text controls, malformed Unicode, unknown fields, duplicate keys and trailing JSON are rejected. Revision strings are checked against uint64, not converted to an inexact JSON number. Raw request capacity is 65,536 bytes; message text is at most 16,384 Unicode code points and reason at most 2,000, with the existing reason normalization restrictions.

The frozen `resource-details#/$defs/Message` remains exactly its twelve fields: message_id, conversation_id, project_id, document_revision, selection, scope, sender_kind, content, run_id, operation_id, candidate_ids and created_at. Records use USER, null run_id, and an empty candidate_ids array. Local intent/execution metadata stays outside that frozen Message. A real persistence operation can be completed while message execution remains `NOT_EXECUTED`; no generated answer, run, candidate, professional check, signature, approval, pending action or model call is implied.

The request hash includes POST, the actual local route, full request, context revision, session identity and schema manifest. Object key ordering is canonicalized; text, selection, array order, target route and every request field remain part of identity. The manifest binds the raw local schema digest plus the original interfaces and resource-details digests. These checks prevent accidental identity reuse; they do not themselves prove runtime idempotency, transactional uniqueness or current authorization on replay.

The reviewed staged service uses `/api/gru-conversations` as its local route identity and operation names `GRU-RECORD-CREATE` / `GRU-RECORD-APPEND`. The frozen public routes remain `/api/conversations`. This review does not establish controller exposure or public GR/BX compatibility merely from service methods.

## Evidence and acceptance still needed

`tests/GruSchemaTest.java` contains 30 pure local contract tests. They cover Create/Append required and unknown fields; UUID/schema version rules; all four SourceScope modes and explicit empties; valid-network rejection; record-only intents; uint64 string limits; exact text and code-point/byte limits; strict malformed/duplicate/trailing JSON; frozen Selection binding and fixed-body validation; exact USER Message with no invented run/candidate; canonical request identity; and the combined schema manifest. Eight added response regressions cover closed Context/Receipt/View/Read/List payloads, nested shared SourceScope and USER Message validation, closed record-only sidecar/paging fields, matching message/metadata identities and array lengths, cross-context/duplicate-message rejection, network-disabled response scopes and 100-item bounds. UUID fixtures use the same format as `EvaluationFixture.id`. No test result is claimed until the central runner executes them.

Static review found three local input gaps in the initial staged schema: uint64 overflow, append-text blank/control checks, and mapping malformed-surrogate validation failure to `SCHEMA_INVALID`. The parent updated the staged source with those checks; the tests retain the corresponding regression assertions. Their runtime success remains unverified.

`tests/GruMapperTest.java` adds 9 integration-time mapper/DDL contract tests: all 18 statements require the verified tenant transaction and exact subject; every joined table and insert receives the server tenant; reads are uncached; the 10/19/25 constructor fields match their records; owner/current-revision CAS cannot retarget the conversation; operation/audit/outbox and lineage identities remain bound; run_id is literal NULL and no execution table is touched; both new record tables reject update/delete; and migration 066 has tenant-qualified keys, fixed document/revision FK, bounded unique sequence, NOT_EXECUTED/intent checks, and one changelog inclusion. These tests instantiate the existing MyBatis/JSqlParser configuration without a database connection when centrally run.

Mapper integration gates: register GruMapper in `TenantScope.requireSubjectParameter`, add both new record tables to `ProtectedTenantInterceptor.IMMUTABLE_TABLES`, install migration 066 and add its changelog inclusion. These edits were deliberately not made in compiled roots by this review. The initial context DDL linked document and revision independently; the staged DDL now uses the existing four-column document/revision identity after this review raised the gap. The tests retain that exact relation assertion.

Before acceptance, central integration still needs compilation and test execution, actual persistence/schema/tenant isolation checks, rollback and concurrent/idempotent append checks, revocation/current-rights checks, exact saved-record/audit/outbox verification, fixed paging behavior and tamper rejection. New runtime or UI exposure requires its own verified controller and frontend integration. This review is not a service or customer acceptance record.

Full GR-01/02/03 behavior, GR-04 decisions and execution, preferences and exports (GR-05–08), BX-17 list semantics, BX-18 `ConversationSnapshot` with an actual messages ArtifactRef and StreamCursor, BX-19 public SSE/reconnection, BX-20 message/run cancellation, complete candidate reads, agent/model/tool execution, and end-to-end frontend behavior remain separate requirements. An inline bounded Message list is not a frozen ConversationSnapshot, ArtifactRef, cursor producer or completed Gru workflow.

## Reviewed unchanged reference identities

- `backend-design/interfaces.schema.yaml`: `0e2dcc451ca58cefdc4432ec126936a142237000ca786dd69fb82792da433444`
- `backend-design/resource-details.schema.yaml`: `4ae03c093dbb980c8bff967e988384ee1d4c08e0a33a1ee6cf089d48b095fa5c`
- `business/medical-persistence/src/main/resources/permission-decisions.schema.json`: `fca270edf7d9697f340333c15992ecaa77fff4a8f1ac7bc51d2ffa8d56094843`

Read-only authority API follow-up: the actual ResourceReadAuthority, ScopeSnapshotSchema, SourceReadAuthority.ReadRequirement and CollectionAuthority signatures/types match the staged calls. Source reads use the real fixed project/snapshot/parse with FORMAL and a null external provider. The initial collection union used every authorized upstream source rather than selected collection members; staged authority now uses `manifest.members`, verifies each selected parse version against the fixed snapshot, and rejects a non-null collection owner project different from the document project. Upstream authorization still occurs inside CollectionAuthority. Response validation also now rejects network-enabled scopes and mismatched/duplicate/cross-context messages; regressions cover these changes. No runtime success is claimed.

Other source references: `LLD-后端.md` §4.6 GR-01–08; `backend-design/interfaces.openapi.yaml` BX-17–20; `PermissionAuthority.java`; `ResourceReadAuthority.java`; `DocumentReadService.java`; `ScopeSnapshotSchema.java`; `DocumentSelectionSemantics.java`; and the staged `GruSchema`, `GruProtocol`, `GruContextAuthority`, and `GruService`.
