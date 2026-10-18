# IMP-23：旧位置批注显式核对实现

## 边界

新增独立 POST `/api/annotation-threads/{id}/anchor-reconciliations`，独立 `annotation-anchor-reconciliations.schema.json` 和操作 `RV-06-ANCHOR-RECONCILE-1`。既有锚点 Schema、059 原锚点回填及058决定历史均不修改。

只有原锚点、pointer revision=1、无既有 INVALIDATE/REANCHOR、实际旧版本的 OPEN/RESOLVED 可以首次核对。必须有真实 `review.decide / DOCUMENT_REVIEW` 权限、有效完整生命周期、所有历史来源及当前正文证明。当前项目 ACTIVE、正文未签署；人明确确认并提供原因。请求携带正文、批注、头和 pointer 的 CAS。

核对只追加“此刻核对”的 INVALIDATE：当前操作人、数据库时间，原因为 `LEGACY_ANCHOR_RECONCILIATION: ` 加明确输入的原因。不能假造升级前作者或时间。批注、文档头和 issue 水位各加一，evidence 水位不变；锚点和 pointer 不动，原评论、Selection、制品、作者/时间和决定均不改。

相关当前全文 CONFIRM 失效。trigger_version 使用此批注真实注册的原 Selection 制品版本，reason_code 为 ANNOTATION_ANCHOR_RECONCILED；不创建假版本号。每条原锚点生命周期仅能核对一次，避免跨批注使用同一个正文版本引发失效唯一键碰撞。

## 幂等和证据

操作记录、INVALIDATE、确认失效、回执、审计和 Outbox 在一个事务提交；没有外部对象写入或悬挂的持久 PROCESSING。任一 CAS/写入/时间权限失败回滚全部业务状态。

完成后的重试在首次可核对条件之前返回原回执，仍重新检查当前权限、原始/有效/请求目标/实际当前正文及全部历史锚点来源。回执固定 original anchor 摘要、真实前状态、执行身份和时间；与保存的 INVALIDATE、操作记录、审计、Outbox、确认失效逐项校验。后续回复、决定、再次定位不得刷新旧回执或增加水位。

审计和 Outbox 不包含用户原因原文，仅保存原因摘要。回执可供该用户核对原因；Selection 和原批注正文不被写入新审计事件。

## 验证范围

本实现使用离线真实 JSON Schema、MyBatis SQL 解析及租户/主体 guard、真实权限和生命周期校验，数据库/事务/对象读取边界使用测试替身。测试结果由执行日志确认后汇总；不宣称真实数据库、对象服务、HTTP、浏览器或生产部署验证。

### 2026-10-18 后端验证结果

- 命令范围：`Annotation*Test,DocumentRevision*Test`，依赖模块通过官方 Maven wrapper、显式 Mockito agent、`forkCount=0` 执行。
- 158 tests，0 failures，0 errors，0 skipped；7 个 reactor 模块 BUILD SUCCESS。
- 新核对覆盖 28 tests：Service 13、Schema 7、Controller 6、Mapper 2。
- 已覆盖：真实旧 OPEN/RESOLVED 经核对后正常重新 OPEN；旧作者/时间与当前操作者/数据库时间分离；完整原信息保留；3 次后续 reanchor、回复、决定及新正文后的原回执；原始、中间与当前来源撤权；权限撤销和时间边界；各 CAS、问题水位、确认失效与 Outbox 回滚；签署/非活动/当前位置/错误原锚点/已核对拒绝；请求、审计、INVALIDATE 作者/时间、缺失历史篡改拒绝；UTF-8 和 unsigned64 边界。
- 原因原文不进入审计或 Outbox；没有新的对象写入。全部为上述离线边界，不代表真实数据库、对象服务、HTTP 或浏览器验收。
