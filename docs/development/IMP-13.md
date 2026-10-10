# IMP-13 Temporal调度、等待、取消与恢复

## 任务身份与依据

唯一任务来源为[实施任务计划](../../实施任务计划.md)的IMP-13，当前状态IN_PROGRESS。开发期限为2026-10-10上午09:00（Asia/Shanghai）；业务输入使用SIMULATION，Temporal历史、服务调用和传输检查使用实际服务。代码及必要集成通过后登记IMPLEMENTED，正式客户验收独立登记。

依据为HLD ADR-002/007、LLD-后端§5.2/5.7/6、LLD-Agent§4.5/5及IC-13。Java维护Workflow和业务Activity，Python维护有界专业Activity，MySQL维护业务事实；TaskWorkflow不直接读取数据库、文件或调用模型。跨语言消息保持固定身份、版本、Schema和产物引用。

## 当前代码

RunReadController/RunReadService/RunReadMapper提供RN-02实际公开读取。run-view.schema.json封闭定义完整运行、步骤、待办、活动或未结算调用、固定预算及事件游标；同一EnterpriseAccess事务核查当前身份、任务和run.read，独立work_item.read限定待办内容及获准动作。六项持久查询关闭缓存并使用记录锁；预算原批准Schema、企业/项目归属、版本登记及内容摘要逐项核验。单次调用金额与Token数量保持原空值，预算账户spent/reserved和固定批准limit分别表示。

RunEventService/RunEventConfiguration/RunEventTransport提供RN-03持久事件与标准SSE。每次读取重新核查原会话、当前任务、运行与关联资源权限；完整PublicEvent由共享Schema校验，游标绑定原事件、原流及连续序号。Servlet使用Spring标准异步适配，原SecurityContext进入有界读取线程；Redis维护企业服务容量及会话连接名额，断开后释放本次名额。公开事件不包含正文，前端使用eventsource-parser及严格UTF-8解码，事件驱动完整运行快照更新。输入UUID通过共享Schema和标准形式校验。

`business/medical-workflow`使用Temporal Java SDK 1.40.0及现有JGraphT 1.5.2；父Maven工程已经登记模块。

| identifier | 当前行为 |
| --- | --- |
| WorkflowProtocol / WorkflowPlan | 固定TASK/SYSTEM_JOB归属、输入摘要、计划版本、完整步骤依赖、并发与截止限制；拒绝缺少依赖、重复身份、循环及未经核对的活动步骤 |
| TaskWorkflowImpl | 按依赖派发独立步骤；复用已有成功回执；人工等待结束Activity；Signal以原command_id去重并保存独立消费者回执；等待输入及终态步骤的原回执保持不可变 |
| RunActivities / ProfessionalActivity | 明确划分C04业务动作与专业执行；控制动作有界重试；收费专业Activity maximum_attempts=1 |
| WorkflowWorker | 明确登记Workflow、实际RunActivities及执行容量；输入异常直接终止相应Workflow |
| WorkflowDelivery | 稳定workflow_id=tenant_id/run_id，拒绝重复建立运行；重新读取实际Temporal首项历史并核查完整固定输入；Signal绑定原Temporal run，接收证明与消费者回执分别读取 |
| scripts/check_task_workflow.py | 限时捕获当前输入、原始日志及JUnit；结构检查与实际传输检查分别运行 |
| scripts/local_temporal.py | 管理本地官方持久服务、健康、身份核验和正常关闭 |

取消先关闭派发，全部调用终态、进程退出及无继续派发能力具有独立证明后才能提交CANCELLED。派发准入返回后再次消费已收到的取消命令；取消期间仅等待持久Signal或既有Activity结果。恢复必须核查当前授权、固定资料、配置、未知动作及累计预算；未知收费结果沿用原调用核对。

## 当前验证

RN-02使用当前源码的独立Spring应用、实际MySQL和Redis，读取持久SIMULATION运行、步骤、等待事项和精确预算，重复读取保持相同业务快照，未知运行、未定义查询、旧上下文及当前撤权分别拒绝。回执为`.implementation-work/IC-13-RUN-READ-20261009-ACTUAL-HTTP-03/receipt.json`，SHA-256为65a310fb8c243fc36f94492cb954e979a9a20a24c96df1d3ee2b8e854340519a，exit_code=0、inputs_unchanged=true。完整运行快照、企业/项目标识及records_retained=true保存在run-read-http-result.json，模拟记录持续保存，正式客户验收为false。初始两项行为断言及原日志由`.implementation-work/run-read-red.log`和独立JUnit维护；实际HTTP覆盖当前运行服务初始化和六项查询。

实际HTTP运行与事件检查通过：`.implementation-work/IC-13-RUN-READ-20261009-EVENT-ACTUAL-HTTP-06/receipt.json`，SHA-256为83556be905e7c6cbe7341510d47db0c8fd3020d4bfe65d4b35bb8f437eff9f52。检查完整运行快照、首次事件、持久步骤更新、Last-Event-ID续读原流第二项事件、最新快照及游标、重复查询拒绝和当前撤权。完整输入校验当前检查已经通过：`.implementation-work/IC-13-RUN-READ-20261009-EVENT-INPUT-GREEN-01/receipt.json`，SHA-256为fc7591237dc221b035eee862131540b6268284991e1d185f3cc83fddb718a846，exit_code=0、inputs_unchanged=true；增加无效游标、运行标识及上下文输入的实际422断言。

全部事件关联查询通过实际MySQL检查：`.implementation-work/IC-13-RUN-READ-20261009-EVENT-AUTHORITY-SQL-03/receipt.json`，SHA-256为6d5b036a47927acedf5ad323d8e7d0a2d25ca56273ef997b7080f89f68a22bbc，exit_code=0、inputs_unchanged=true、两项JUnit通过。覆盖运行读取、原事件游标、完整事件以及WAITING_ITEM/CANDIDATE/DOCUMENT/SOURCE_VERSION/EXPORT/NOTIFICATION实际查询；缺少EnterpriseAccess时全部拒绝。SIMULATION记录持续保存。

Given完整固定运行与依赖；When验证DAG、运行身份、成功回执与等待身份；Then保留原结果并拒绝冲突身份和非终态最终回执。五项检查已经通过：`.implementation-work/IC-13-WORKFLOW-20261009-RUN-IDENTITY-GREEN-02/receipt.json`，SHA-256为8e7dc5421dcea4fc3e2faf55a247d4d876c46a532a17c6938350ab26a93697eb，exit_code=0、inputs_unchanged=true。

Given明确标记的SIMULATION运行与独立队列；When实际启动Temporal、重复投递原命令、提交异参请求及Signal；Then原运行身份保持一致、异参被拒绝、实际历史仅有一次启动及一次合法Signal，完整消息与原内容一致。实际服务检查已经通过：`.implementation-work/IC-13-WORKFLOW-20261009-ACTUAL-DELIVERY-02/receipt.json`，SHA-256为f97e4480f2050306a88d3718d07d8af9f7d26e3219b8c23ce933ab1157db7c60，exit_code=0、inputs_unchanged=true。该检查不启动专业Worker，不产生模型发送；完成后仅终止本次自有模拟运行。

本地服务位于127.0.0.1:17233、Namespace medical-local，独立SQLite历史保持持久化。服务生命周期检查由`.implementation-work/IC-13-TEMPORAL-DEV-20261008-PERSISTENT-SERVICE-GREEN-01`维护；原健康、正常关闭、完整性及同一Namespace重新读取已经通过。

## 剩余开发

RN-04公开接口、RunCancellationService及RunControlMapper已进入代码。原会话、当前task.run/RUN_CANCELLATION及ACTIVE/PAUSED项目逐项核查；运行与活动控制行锁定，原版本CAS保存CANCEL_REQUESTED和dispatch_closed_at，当前活动任务同步保存取消状态。取消命令、完整人工请求与授权基线、审计、原操作回执及PublicEvent同事务保存。重复同键同参读取原回执，并重新核查当前权限；同键异参拒绝。已终态运行返回原状态。请求完成不会提交CANCELLED，外部终止证明由后续消费流程处理。实际公开业务检查通过，证据为`.implementation-work/IC-13-RUN-READ-20261009-CANCEL-ACTUAL-GREEN-01/receipt.json`，SHA-256为cb1d0c48145cba9d7567eca4d3cfa4ca2ee62d6f98c7b2d86df98f54d918d227，exit_code=0、inputs_unchanged=true。使用SIMULATION记录及实际MySQL、Redis与HTTP，覆盖原请求重复、异参及旧版本409、任务取消状态、关闭派发、唯一Outbox命令和PublicEvent，以及当前task.run撤权403。

C04的持久RunActivities、RN-01/04/05、IN-01/07/12、execution_registry/task_run/run_step/run_principal写入、当前逐动作授权、令牌续期、Outbox及消费者回执仍需连接。RN-02保留全部要求，持久事件游标与并发写入继续完整集成；Python专业Activity、Harness、完整生成与模型结算继续IMP-07/14/15；运行页面继续IMP-27。

实际Worker的全流程、人工决定、新尝试、取消与完成竞争、历史重放、未知调用核对和全部IC-13尚未通过。生产TLS、历史数据库、容量、备份恢复及跨版本兼容继续IMP-36/39—42。现有检查不作为完整业务调度或客户验收证明；last_verified=2026-10-09。
