# Independent document return for revision

## Meaning and boundary

This is an independent human request to revise the current saved document. It is **not BX-13 formal ReviewBatch**, does not write review_batch/review_batch_item/review_decision, and does not add to formal review_ids. It creates no confirmation, passing check, reauthentication receipt or signature. Frozen ReviewBaseline/ReviewBatch retain their real nonempty check requirement and complete currentness requirements.

A reviewer may request revision to remedy missing checks or evidence. Therefore this independent command does not require fabricated check IDs. The basis exposes actual stored check records only as information, with formal_review_available=false and formal_check_currentness=NOT_VERIFIED. No status is interpreted as clinical approval.

Only current unsigned saved documents in ACTIVE projects and tasks actually in REVIEW_PENDING are eligible. Task input revision must equal the fixed document's task revision. Actual signature_record rows also block, even if a document head inconsistently says unsigned. SIGNED, CANCELLED, FAILED, running and all other task states are rejected. Signed history is never changed.

## Exact APIs

Authenticated GET `/api/document-review-availability` uses X-Schema-Version and X-Context-Revision, no query/body. Envelope data is `{schema_version,current_context,review_return_enabled}`. This is server deployment state, not permission. The business controller has `medical.review-return.enabled=true`, default disabled.

Document endpoints require X-Schema-Version=1.0.0, X-Context-Revision, X-Document-Revision, each exactly once. No query string:

- GET `/api/documents/{id}/revision-return-basis`
- GET `/api/documents/{id}/revision-return-history`
- GET `/api/documents/{id}/revision-return-assignees`
- GET `/api/documents/{id}/revision-return-assignments`
- POST `/api/documents/{id}/revision-returns`, with Idempotency-Key
- GET `/api/documents/{id}/revision-return-operations`, with original Idempotency-Key

POST returns HTTP 200. Request is exactly `{document_revision,expected_revision,expected_task_revision,reason,assignee_id,basis}`. The explicit reason is preserved, not normalized. Basis fixes document/body/task/head revisions, current identity and authorization watermarks, actual complete fixed-document read proof and informational checks. Unknown JSON properties, duplicate keys, excessive bodies, malformed Unicode/control characters and blank reasons are rejected.

Basis, submission, recovery and assignee selection require current document.read through DocumentReadService and review.decide / DOCUMENT_REVIEW. History requires the actual fixed-document read proof. Assignment reading also requires task.read / TASK_READING and work_item.read / WORK_ITEM_READING at the real TASK scope. The caller remains the current authenticated actor throughout.

Assignee selection verifies active member/identity and exact-task task.read through TaskPlanService.creationOwner, plus document.read / DOCUMENT_BODY and TASK work_item.read / WORK_ITEM_READING through target-only grant queries and PermissionAuthority. All three grants are rechecked at transaction end; nothing is granted or impersonated. Existing shared member data lacks a common approved local/OIDC name projection, so display_name is explicitly null. Names are never invented.

Lists are complete and bounded at 100 rows, with a 101st overflow sentinel. Larger datasets return CONTEXT_LIMIT rather than a misleading incomplete list. All responses are no-store.

## Immutable records, CAS and invalidation

067 adds only document_revision_return and document_revision_return_assignment. It does not alter frozen SQL or formal review tables. The independent decision contains the complete original request, request digest, original receipt, actor, assignee and reason. The assignment uses a composite tenant/project/return/document/task/assignee FK to prevent cross-scope rebinding. Both are immutable.

Within one EnterpriseAccess transaction, submission writes original operation, independent decision, actual assignment, invalidations of currently valid formal confirmations, audit and outbox. Document head revision is CAS-incremented without changing its existing status enum. Task head is CAS-transitioned REVIEW_PENDING→RETURNED, without changing its fixed input revision. CAS, eligibility or outbox failure rolls everything back.

Current formal confirmations are invalidated through the existing review_invalidation table only in this eligible unsigned scope. ExportMapper already excludes invalidated formal decisions. FORMAL_BODY continues requiring a nonempty all-CONFIRM formal snapshot plus a real current signature; an independent revision request cannot satisfy these gates. Ordinary unsigned exports remain governed by their existing purpose policies.

Operation identity is DOCUMENT-RETURN-FOR-REVISION-1 + actor + original key. Recovery validates current document access and review authority, then verifies original request/receipt, joined independent decision/assignment, audit and outbox. It does not re-run original eligibility or create a second decision after task state changed. Different request/same key is an idempotency conflict. Null recovery data means NOT_FOUND. The request digest includes backend schema/permission identity and is not the plain request-body SHA.

## Assignment lifecycle and honest remaining gap

The immutable assignment stores original WAITING_INPUT. Listing derives:
- OPEN: original document still current and task remains RETURNED on the fixed task input.
- REVISION_AVAILABLE: a real newer document exists, while the same task remains RETURNED.
- INACTIVE: task status/input has moved outside this assignment's active context.

None means completed. A saved new document is never automatic completion. Existing DocumentRevisionService saves a new DRAFT and invalidates old checks but does not move RETURNED to REVIEW_PENDING.

LLD-后端 lines 694–696 and LLD-前端 line 341 require completed current checks before that task transition. No deployed producer/currentness verifier exists for check_run.input_watermarks/rule_manifest. This slice therefore exposes resubmission_available=false and CURRENT_CHECK_VERIFIER_UNAVAILABLE. It does not create a resubmission record, reopen a task, or claim a completed revision-review loop. A future real verifier and append-only resubmission record must establish this transition. The earlier proposed unchecked transition was rejected and is not implemented.

`document.write` / DOCUMENT_BODY is the real editing grant; `document.edit` is only the existing derived UI action. No unrelated task.create grant is used.

## Validation limits

Isolated javac/JUnit and real JSqlParser/sqlglot tests are run from staging. Unit tests exercise the actual service/schema with mocked storage and an explicit rollback model, not a live database. Root owns publication, migration master/coverage, TenantScope/immutable-table registration and full Maven validation. Old formal-RETURN-path test results are marked SUPERSEDED and do not count as acceptance of this independent route.

## Personal revision assignment queue

`GET /api/my/document-revision-assignments` accepts `X-Schema-Version: 1.0.0`, the current `X-Context-Revision`, and only optional `limit` (1–20, default 10) and `cursor` query parameters. It is guarded by the same default-disabled `medical.review-return.enabled` registration. No document header, assignee override or project-context fiction is accepted.

The independent `document-revision-assignment-queue.schema.json` (`urn:medical:document-revision-assignment-queue:1.0.0`) returns a closed QueuePage with current tenant/actor/context/auth/governance identity, requested limit, full validated Decision and AssignmentView per item, actual project/task/document references, next_cursor, complete and canonical listing_sha256. It does not alter the immutable return-operation schema manifest. Links use the original fixed document revision; current revision availability remains a separate fact.

A page queries at most limit+1 (maximum 21) metadata candidates belonging to the current actor in the current tenant. It scans at most limit candidates and checks task.read, TASK work_item.read and source-aware document.read at the actual project scope. ACCESS_DENIED candidates are omitted. No ID or count of omitted records is exposed; an empty page may have a next cursor. Missing/corrupt lineage, schema or ledger remains a failure rather than an empty success. UUID keyset ordering is a live traversal, not a frozen historical snapshot; refresh from the first page to see concurrent additions sorting before the cursor.

Opaque random cursors contain no client-readable assignment identity. The server cache is limited to 1024 entries, has a five-minute lifetime, and binds tenant, actor, session, context revision, auth/governance epochs and page limit. Expiry, capacity eviction or process restart returns CURSOR_EXPIRED. An otherwise valid token replayed under different ownership/watermarks/limit returns CURSOR_INVALID. Either requires explicit first-page reload; no silent reset or false “record does not exist” result.

DocumentReadService.MetadataRead is an internal authorization-only proof. It reuses the existing complete metadata, registry, snapshot, provenance and source authorization prepare path without calling load or object transport. It is not FixedRead and cannot attest downloaded body bytes. Each unique fixed document/revision is authorized once per page and revalidated before returning; task/work-item grants are also rechecked. No new HTTP body-read bypass exists. The original readFixed byte-validation path is unchanged.

History, document-scoped assignments and the personal queue validate immutable request/receipt, exact joined Decision, original operation and audit/outbox consistency. The current reader remains the admission actor. Original operation/audit creator is selected only through the immutable return record's actor_id=created_by relation, allowing the genuine assignee to read without impersonating the reviewer. Original-key recovery remains actor-scoped and source-aware document.read-only; loss of review.decide does not block checking a previously committed outcome. New writes and write replays still require review.decide.

Verification: focused Java tests cover recipient reads, corrupt/missing ledger, loss of write grant during read-only recovery, bounded candidate reads, denied empty pages, currentness failure propagation, no body read, cursor actor/session/epoch/limit replay, expiration/eviction/restart and malformed endpoint inputs. Actual DocumentReadService authorization denial is tested with no object-transport invocation. The queue's late document-watermark rejection uses a deliberately failing metadata-currentness collaborator; database/runtime source-currentness integration is not claimed by these offline tests. Offline parser checks cover the exact actor/keyset SQL and creator-bound ledger joins, plus frozen migrations 001–062.


## 067 内部账本完整性补强

公开 Request/Receipt/Queue 契约保持不变。`document_revision_return.canonical_request` 保存闭合内部包：原公开 request、完整原 review grant 与三类 assignee grant 见证、见证 SHA256 和内部 schema_version。原 request_sha256 仍仅绑定原公开请求及原路由/schema身份。见证仅保存在内部不可变记录与 audit，不返回个人队列或普通文稿读取者。

历史恢复与队列重算原见证摘要并重建完整 audit，验证整对象相等；历史grant无需等于今天的grant，当前读取权限仍独立核验。审核人/创建人、原审计trace/schema/classification和assignment元数据绑定均在SQL回读中核查。

回执中的 invalidated_review_ids 必须与本次实际 review_invalidation 全集合一致，严格绑定tenant/project、原文稿版本、原actor、原创建时间、DOCUMENT_RETURNED_FOR_REVISION、classification，以及真实原CONFIRM。缺失、重复、多余、被篡改的失效记录均失败，不把回执声明当实际失效。

个人队列仅对明确正常不可展示的 ACCESS_DENIED、SOURCE_NOT_READY、SOURCE_MISSING 跳过候选，继续同一有界候选页，不为填满页扩大扫描，也不返回被跳过的ID/原因。OBJECT_UNAVAILABLE、CONTENT_MISMATCH及账本错误仍终止页面。live UUID keyset需要刷新首页查看并发新增到游标之前的事项。

隔离验证：正式service/schema配显式内存账本替身37项通过；此结果不代表真实MySQL事务、HTTP或正式用户权限验收。正式Maven仍由root统一执行。
