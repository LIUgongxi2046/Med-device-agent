# Gru 固定文档会话持久化基础（阶段 14）

本切片提供 GR01/GR03 的持久化基础。不是完整 Gru 执行系统，也不宣称 BX17–20 已实现。独立 `/api/gru-conversations` 返回本地固定视图，不构造冻结 ConversationSnapshot、MessageList ArtifactRef、公开流游标或假 resource_manifest。

## 实际行为

- 基于已存在文档版本、真实 ScopeSnapshot 及原固定 SourceScope 创建 owner-only 会话。
- 保存 USER 原文和合法已有正文 Selection，按会话 revision CAS 追加；不修改正文，不创建候选、不检索、不调用模型、不排队执行。
- 所有消息明确 `RECORD_ONLY` / `NOT_EXECUTED`。四种 intent 仅记录用户意图。持久化 operation 的 COMPLETED 只表示保存完成。
- 现有四种 SourceScope 模式使用原固定快照；network_enabled 必须 false。PROJECT_APPROVED 不重新枚举新来源；显式集合以真实固定 manifest 成员及 parse_version 对齐快照，完整祖先继续授权。
- 来源/文档依赖当前权限、rights、快照绑定在创建、追加、读取、原回执恢复均重新核验。集合项目只能企业或当前文档项目。
- `conversation.use` 与 `conversation.read` 是已有 action，作用于真实 DOCUMENT；同时需要 project.read/document.read，purpose DOCUMENT_BODY。不使用读取 grant 替代 use，也不生成任何 grant。

## 接口与固定 DTO

本地 schema：`gru-records.schema.json` 的 Create、Append、Context、Receipt、View、Read、List；共享 SourceScope/Selection/Resource.Message 继续使用冻结定义。

- GET `/api/gru-conversations/context?document_id=&document_revision=`：真实固定文档上下文，返回 Context；客户端不得猜 snapshot 或 scope。
- POST `/api/gru-conversations`：Context 加 reason；Idempotency-Key。
- GET `/api/gru-conversations?project_id=`：当前 owner、当前可读的会话，最多 100；空列表仍校验 project.read。
- POST `/api/gru-conversations/{id}/messages`：固定 revision/snapshot/scope、expected_revision、selection、intent、execution_mode、text、reason；Idempotency-Key。
- GET `/api/gru-conversations/{id}?page=1&page_size=50&dataset_watermark=`：固定视图和冻结 USER Message。每页最多 100，整会话最多 500 消息。watermark 绑定当前身份、context/epochs、会话版本和全历史摘要。
- GET `/api/gru-conversations/{id}/receipts/{operationId}`：当前完整权限下返回原创建/消息 Receipt；不要求历史 revision 等于最新。
- GET `/api/gru-feature-availability`：始终注册且需要当前认证/企业 context。闭合 `{schema_version,current_context,features:{gru_records:boolean}}`。仅查询 GruController 的实际 bean 注册，不实例化、不暴露设置/秘密，不授予权限。

均使用既有 Envelope、X-Schema-Version / X-Context-Revision；写入须 CSRF 与幂等键。Context/List envelope revision=1；Read/Receipt 为自身 revision；feature availability 为当前 context revision。

## 恢复与完整性

复用 operation_record 和现有通用 lookup：`GRU-RECORD-CREATE` / `GRU-RECORD-APPEND`，target_id 为 conversation_id。unknown 后按原 operation_name/key 查询，得到 resource_id+operation_id，再读取 typed receipt；不可仅见 COMPLETED 就声称已核对消息。

Receipt 必填 request_body_sha256，绑定完整原 Create/Append canonical 请求（包括 text/selection/intent/reason），不绑定恢复时的新 session。客户端初次提交和 unknown 恢复都必须与 pending 原请求 digest 相等，才可清除 pending。

同一 key/body/context/session/manifest 返回原回执，变化冲突。目录仅验证 owner/当前固定上下文、创建账本与两表消息计数/head revision 一致；不取历史消息正文。单次目录请求对相同固定文档复用已读 proof，但每项重新 requireCurrent，不跨请求缓存。详情与原回执完整历史回读校验原请求、原文、固定上下文、序列、冻结消息摘要、version_registry、lineage、operation response/hash、完整 audit/outbox。恢复不绕过当前权限或复用历史 creator 身份。数据库事务和 CAS 支持并发拒绝；测试内存账本不是数据库事务证明。

`content_ref` 为闭合的本地 INLINE_CANONICAL_MESSAGE JSON，不是 ObjectRef，不伪造存储对象。复用 conversation/conversation_message；066 新增 immutable gru_conversation_context/gru_message_record，包含 schema/version/hash/creator/time/classification。共享冻结 contracts 不变。

## 开关与边界

`medical.gru-records.enabled` 缺省关闭。需要真实现有文档及其依赖、固定快照、当前 grant、可读对象，才能开展 live 验收。没有提供凭据或建立授权。真实 DB/HTTP/对象存储端到端验收仍须在合规环境另行完成。

尚未实现：模型答案、真实检索/检查/编辑执行、候选确认、任务/agent 上下文、学习偏好、摘要生产、公开 ConversationSnapshot/事件流和完整 GR01–08/BX17–20。界面文案必须显示“消息保存 / 未执行”，不得显示“回答完成”。

## 集成与测试

root 集成 GruMapper 21 statements 的真实主体约束、两表 tenant+immutable 保护、066 changelog 和覆盖清单及精确路由；本切片不修改这些共享文件。

测试：GruSchemaTest、GruMapperTest、GruServiceTest、GruContextAuthorityTest、GruFeatureAvailabilityTest。纯 schema/真实 SQL 构建、真实权限 evaluator 配显式测试 ledger、服务内存持久化替身分别标注。未把 mock 执行说成真实权限或数据库验收。Maven 由 root 统一执行。

## 并发旧 CAS 的只读恢复判定

POST `/api/gru-conversations/{id}/append-recovery` 使用 schema/context/CSRF headers，不带新 Idempotency-Key；body 为 RecoveryRequest：原 idempotency_key + 完整原 Append。恢复外层请求单独有界 128 KiB，内层原 Append 的 canonical 字节仍严格不超过 64 KiB；普通 Create/Append 容量不扩大。此 POST 仅当前锁定读取，不创建 operation、audit、outbox、消息或取消记录。

同事务锁定真实 owner 会话 head，再按原 actor/operation-name/key 做 FOR UPDATE 当前读。原操作存在则严格验证完整历史、原 body 摘要和固定回执后返回 FOUND；原 key 确实不存在且当前单调 revision 大于原 expected_revision 时，返回 NOT_APPLIED_STALE；其它保持 UNKNOWN。它不是提交或取消成功。响应绑定当前 actor/tenant/context、原 key、完整请求 hash、预期与当前 revision、检查时间及可空真实 receipt。

前端不得依据普通 HTTP stale 或 operation NOT_FOUND 清 pending。用户明确保存旧证明及原草稿后，才能结束该待处理项、重新读取当前会话 CAS 并生成新 key。context/identity 重置后仍须以当前权限重新查证；旧证明可保存审计展示，不能当作授权。原请求的迟到 CAS 由于 head 已单调超过原 revision，将永久失败。

数据库锁顺序与迟到提交在单元测试通过显式账本/锁读取替身建模；真实多连接 MySQL 并发验证仍属于未完成的部署验收，不能把 mock 当成数据库锁证明。
