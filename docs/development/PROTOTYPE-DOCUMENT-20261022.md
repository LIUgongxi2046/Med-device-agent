# PR-05 文稿检查、候选采用与审查签发

## 任务身份与依据

任务来源 PROTOTYPE-RESTORATION-20261022，状态 IN_PROGRESS，文档 CURRENT。对应 S09/S11、LLD-前端 FRONT-D05/06、FRONT-I03/06、FRONT-Q09/15，以及原型连续正文、节点比较、检查清单、章节和总稿人工确认、签发门槛。

## 当前接口与实施范围

已核查 DocumentCandidateAdoptionController、DocumentCandidateAdoptionEntryService、DocumentReviewExecutionController、DocumentReviewAvailabilityController、document-candidate-adoption.schema.json，以及 document-editor 中三个工作区的调用。当前候选比较组件没有接收真实清单，专业检查和审查组件仍有固定禁用操作。继续接入实际目录、读取、完整正文与引用比较、采用预览和明确确认、检查运行、当前性、人工决定及签发读取。

候选采用预览固定候选摘要、正文版本、记录修订与准确节点；最终确认使用服务端 pending_action_id、arguments_sha256、action_revision。采用结果需要重新读取正文，独立专业检查及人工决定分别显示。签发需要真实部署策略、资格和再次认证，不产生本地替代回执。

## 验收与验证

PR05-01：Given存在实际完成的文稿候选，When打开比较，Then显示完整原正文、候选正文及引用变化，读取权限和固定摘要一致。

PR05-02：Given当前已保存正文和获授权候选，When选择节点、取得服务端预览并明确确认，Then生成新正文，原正文保留，检查意图可追踪；版本变化拒绝旧预览。

PR05-03：Given当前正文，When准备并执行专业检查，Then展示实际运行、检查覆盖、问题及有效性；服务缺少时显示具体原因。

PR05-04：Given有效检查与完整人工审查依据，When确认章节、确认总稿或退回修订，Then读取实际决定和当前状态；缺项阻止继续。

PR05-05：Given当前签发门槛及有效资格，When完成再次认证与明确签发确认，Then读取不可变签发记录；未配置策略时保持明确阻止原因。

2026-10-23 新增实际文稿服务状态读取、获授权候选目录、完整正文比较、准确节点选择、引用变化、服务端预览、明确确认及原参数继续请求。候选页接入文稿工作区，沿用实际结构化正文组件与Element Plus。完整严格类型检查通过，636项固定Schema引用及479项调用检查通过；对应日志 .implementation-work/prototype-document-workspace-typecheck.log。

实际会话服务状态核查通过，证据 .implementation-work/document-workspace-contract-checks.json。当前部署的候选运行、专业检查、人工确认及签发服务均未启用，页面读取实际状态并展示原因。候选采用完整业务链、独立专业输出、人工确认和签发仍为 NOT_RUN；不使用模拟专业输出、候选完成记录或身份回执。候选页面状态验证与完整采用流程分别记录。
