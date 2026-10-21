# IMP-23 当前正文检查、人工审查与签发

## 记录范围

本记录于 2026-10-21 补齐缺失的主记录；不是恢复出的历史验收回执。实施任务计划仍为 IN_PROGRESS，父验收 IC-23 未由本文件宣称完成。依赖 IMP-15、IMP-16，当前详细任务以[后端与 Agent 账本](backend-agent-task-ledger-20261020.md)的 B07–B10、A7–A9 为准。

## 当前实现入口

- DocumentTechnicalCheckService 与 ProfessionalCheckService 分别处理技术结构检查和专业检查，二者不能相互替代。
- TaskSourceVerifierReviewService、DocumentReviewLaunchService、DocumentReviewInvocationService、DocumentReviewResultService 接通候选及已保存正文的真实输入、输出和恢复。
- HumanEvidenceReviewService 保存真实人工证据决定；DocumentReviewConfirmationService、DocumentReviewReturnService 处理同版确认及退回。
- DocumentSigningService 保留同版签发、签名及当前有效性门禁；具体源码范围见[B10 签发交接](B10-signing-code-handoff-20261020.md)。

以上类位于 business/medical-persistence/src/main/java/com/ruoyi/medical/persistence。当前已补显式版本化 IMAGE/HTML 引用消费，但来源权利、专业批准和人工签名仍必须是真实输入，模拟提供方回执不构成这些批准。

## 验证状态

按最新用户要求，模拟 API 开发后正在执行完整测试，发现的源代码缺陷与陈旧测试夹具分别修复。单项测试通过不代表 IC-23 整链、真实数据库、人工专业审查或签发验收通过。完整测试命令与边界见[测试计划](FULL-TEST-PLAN-20261021.md)，阶段状态见[开发状态](DEVELOPMENT-STATUS-20261021.md)。不得引用缺失的旧部署或回执作为当前通过证明。
