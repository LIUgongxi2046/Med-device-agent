# B08 人工证据审查：实现交接（2026-10-20）

## 已实现的生产路径

- `HumanEvidenceReviewController` / `HumanEvidenceReviewConfiguration` 接入普通认证会话；随 `medical.professional-runtime.document-review-enabled=true` 启用。没有模型或后台 worker 代填人工支持结论的入口。
- BX-09 使用原 `EvidenceDecisionBatch` / `DecisionBatch` 1.0.0 契约，服务器完整读取固定正文、逐条验证 Selection；要求全部引用各出现一次，Selection 覆盖该引用全部绑定节点。
- `HumanEvidenceCitationReads` 通过既有来源权限与处理回执检查，真实重读原文件及解析对象，校验完整 SHA-256，使用 `reviewLiteral` 核验引用定位。客户端 access/location 必须与服务器结果一致；来源访问失败直接拒绝，不能以来源核准推导支持。定位失败只允许明确 INSUFFICIENT。
- 显式保存人工 support/reason；NOT_APPLICABLE 必须指定已批准、当前 N07 规则清单中的规则，且真实 N07 该规则结果与引用、目标节点一致，不能将一般来源核准当作规则依据。
- 复用 B07 `ProfessionalCheckService.Verified.requireValid` 核验真实 N07 完整覆盖、正文/输入/规则与水位。完整批次、operation、物理决定记录、正文 CAS、证据水位推进、既有确认失效、audit/outbox 在同一 EnterpriseAccess 事务内完成。失败回滚，不产生部分批次。
- 原请求重放先恢复原 operation；不会因自身已经推进的水位再次执行写入。恢复校验物理 batch/decision/operation/audit/outbox/失效记录及摘要，当前撤权仍阻断读取。
- migration 134 新增真实文档/引用/用户/operation 外键的 `human_evidence_batch`、`human_evidence_decision`；保留原 Selection 字节的规范 JSON，不虚构 selection artifact 或 check-manifest artifact。

## 公开读取契约

- POST `/api/documents/{id}/evidence-review-decisions`：`X-Schema-Version: 1.0.0`，`X-Context-Revision`，`Idempotency-Key` 与正常 CSRF。
- GET 同路径：新增封闭 inline 版本 `X-Schema-Version: 1.1.0`，必须带 `X-Document-Revision` 与 `X-Context-Revision`，支持 page/page_size/watermark。data 是 `{document_id, document_revision, items: EvidenceDecisionDetail[], page_info}`。
- 原 1.0.0 GET 所需的真实 detail artifact 生产者不存在，明确返回 SCHEMA_UNSUPPORTED，而不是伪造 ArtifactRef；旧 schema/摘要未修改。新增契约见 `backend-design/human-evidence-review.openapi.yaml` 与同名 schema JSON。
- GET `/api/documents/{id}/evidence-review-operations`，版本 1.0.0，固定正文与原 Idempotency-Key，返回原响应或 data null。
- 未找到 web/contracts/ui-delivery 中已有该路由调用，因此没有需要修改的现有前端 header/解码器。

## 正式消费者约束

`HumanEvidenceReviewService.requireCurrentBatch(admission, fixedRead)` 是 B09/签发等正式消费者入口。它验证完整当前批次、真实物理行、当前正文/来源授权、当前规则及输入；返回不可构造的 `CurrentBatch`，含 decisions()/sha256()/requireCurrent(admission)。不能直接查旧 `evidence_review_decision` 表替代本入口。

批次消费的是提交前 N07 检查；自身成功提交推进 evidence_watermark 一次。正常 N07 → B08 → B09 可由 `ProfessionalCheckService.Verified.requireValidForHumanEvidence(admission, CurrentBatch)` 消费确切已记录的人工决定 +1 transition：仅当失效原因为 EVIDENCE_WATERMARK_CHANGED，且 CurrentBatch.requirePrecedingCheck 证明原 check 回执身份、原/新水位恰好 +1、当前问题水位/正文/完整输入/规则未变化、真实批次及 operation/audit/outbox 完整时允许。其他失效原因仍拒绝。当前新 N07 同样可用。该 API 不把单独旧检查或来源核准升级为专业 PASS；一般 requireValid 不变。

历史记录不改写人工原结论；读取按正文、证据水位、问题水位、授权/治理、输入、规则、检查失效/回执变化派生明确失效原因。

## 验证边界

仅对四个新增生产 Java 类和其直接 B07 authority 依赖进行窄范围 javac 编译，结果通过。按照开发完再测试的安排，没有运行测试、全量构建、数据库迁移、服务、provider、网络或 push。

待统一测试：并发 CAS/重复 key、原请求恢复、事务回滚、撤权、Selection 变动、定位失败、N/A 规则不足、部分引用批次、历史物理行缺失/损坏、当前批次与新 N07/B09 联调、schema version 协商及 134 实库迁移。
