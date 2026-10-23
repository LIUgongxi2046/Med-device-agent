# PR-03 评测与运营页面

## 任务身份与依据

任务来源 PROTOTYPE-RESTORATION-20261022，当前 IN_PROGRESS，文档 CURRENT。对应 S15、PRD FR-06、AC-12/13，采用原型 final-product.js 的质量评测、反馈改进、用量与成本、审计分区。已检索 B30-OPERATIONS-FEEDBACK-20261021 并核查当前 BusinessOperationsController、OperationsFeedbackController、KnowledgeFeedbackController 和服务实际字段。

## 当前功能与实现要求

运营页面提供组织范围与项目选择、周期选择、实际统计、采纳率和退回率的分子分母、费用币种及证据状态、同条件CSV导出；反馈目录、登记、处理记录和各阶段人工决定使用持久化服务。保留质量评测及保存审计入口。页面采用原型标题、分区、筛选、统计卡片与表格，敏感版本字段由已授权目录选择。

## 决策与约束

现有 report.read/report.export 和 feedback.create/read/manage 授权保持生效。零分母或缺少结算证据显示未形成统计结果。费用保留十进制字符串。不得创建替代专业运行或发布证明。读取取消及组织变化清除旧结果；写入保留固定请求身份及版本。

业务资料分类与权限元数据分类分别校验。非标准业务分类必须登记企业、资源类型、固定版本、内容摘要及用途说明；登记仅适用于所列版本。当前公开素材编号规则按用户授权作为内部测试资料管理。未登记版本报告 DEPENDENCY_UNAVAILABLE，内容摘要变化报告 CONTENT_MISMATCH。配置保存在受保护运行文件，不修改原规则的权限分类。

## 验证与证据

PR03-01：Given有效本地账号，When打开运营分区，Then可以选择项目和周期，读取真实报告及完整数据状态。

PR03-02：Given实际已授权来源或规则，When登记反馈并重读，Then目录和处理记录保留本次问题及来源。

PR03-03：Given当前反馈版本，When登记允许的处理决定，Then服务器重读显示新状态及理由；不允许越过必要阶段。

PR03-04：Given有效筛选，When导出，Then文件对应相同范围与周期；组织切换清除原结果。

PR03-05：Given已提交的反馈请求，When使用相同请求身份查询及重发，Then返回同一记录；修改参数重发报告 IDEMPOTENCY_CONFLICT。

636项固定Schema引用及479项页面、组件、接口Schema调用检查通过，严格TypeScript检查通过。2026-10-23 实际浏览器完成运营四分区、真实统计、同条件CSV下载、规则反馈登记、相同请求身份查询及重发、变更参数409拒绝、人工处理决定及处理记录重读。PR03-01/02/03/04/05通过本次规则反馈场景，反馈126fe582-5fe5-4e81-b5f3-9eb9c996ba11。证据 .implementation-work/prototype-operations-keyboard-green.log、prototype-operations-checks.json。没有执行视觉检查。

## 关联与未完成内容

提案、审核及发布依据需要完整业务选择器，来源与Agent反馈完整生命周期、人员筛选和组织隔离分支继续验证。任务保持 IN_PROGRESS，外部服务缺少的决定保留具体原因。
