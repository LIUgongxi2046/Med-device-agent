# 剩余前端清单（2026-10-18 源码复核）

## 结论与边界

本清单对照 `docs/前端实施计划.md`、`LLD-前端.md`、当前 `web/src/views/medical/` / `web/src/api/medical/` / Router，以及 `business/**/src/main/java/**/*Controller.java` 全量映射。Controller 清单见附录；额外检查了非 Controller 注册的运行 SSE 和上传分段入口。**不修改原 42 项工单状态，不把接口定义、按钮、测试夹具或内部接口当作完整业务交付。**

三类标记按“具体子能力”使用，同一 FE 项可以同时具有三类子能力：

- **A：已实现但待联调**：已有页面逻辑、真实 API 消费与对应后端入口；还需当前运行环境的成功、拒绝、并发、撤权、恢复和持久结果验证。不是宣布以前所有验证无效，也不是声明该 FE 全部实现。
- **B：可以用现有接口继续开发**：接口及必要语义存在，可补前端选择、分页、反馈、导航、可访问性或恢复交互；不必等整个后端父工单完成。
- **C：必须补后端后才能完成**：完整闭环缺少公开端点或权威状态/持久结果；部分还需客户规格和授权。可以先做静态安全说明，但不能伪造可执行业务流程。

接口盘点阶段仅做静态源码核查与文档编写，没有启动服务、调用业务 API 或浏览器验收；后续第 0 节明确标为已实施的前端切片另有 focused tests，详见 `workbench-access-acceptance.md`。根协调者此前报告完整医疗前端 typecheck 和 focused tests 通过；这不能替代端到端业务验收。当前文档不依据旧的 `frontend-integration-gaps.md` 直接判定缺接口，该文档的模型配置、导出、评测等描述已落后于现有代码。

## 0. 已落实的前端功能切片（17:24 UTC更新，待联调）

| 顺序 | 分类 / 功能 | 现有接口与精确缺口 | 可验收结果与边界 |
| --- | --- | --- | --- |
| 1 | A：完整导出历史翻页与切换固定记录（已实施） | `ExportController#history` 已支持 `document_revision/page/size`；本轮 `exports.ts#readExportHistory` 与页面已消费真实 `page/size/has_more`，切页清除旧详情，新增记录不伪插入服务端页。 | 真实超过 20 条时能逐页读取，切换同版历史记录后执行/重试/下载仍绑定选定 export/item；会话或文稿变化废弃旧页。无需新后端接口。 |
| 2 | A：待办与实际任务的关联定位（本轮已实施，待联调） | `GET /api/work-items` 返回 `waiting_item_id/project_id/run_id`；`GET /api/projects/{projectId}/tasks` 返回每个 `TaskDetail.waiting_item_ids/active_run_ids`；本轮首页已携带定位上下文，任务页只匹配当前返回页，区分待办直接关联和活动运行关联；对应 helper/setup tests 已补入。 | 可传递已获授权的待办标识，在用户选择/翻阅的任务页内匹配真实关联并明确显示“当前页未定位”，匹配后按独立 `task.read` 权限进入任务。禁止把 run_id 当 task_id、全企业批量扫描或声明未匹配即不存在。 |
| 3 | A：场景查询选择真实项目并保持范围（已实施） | `GET /api/projects` 已提供获授权集合；本轮 `scenarios.vue` 已复用真实获授权项目选择器；分页保留范围，切换/撤权清理旧数据。 | 有限分页/显式选择项目，目录查询保留当前项目及文档条件、水位与游标，切换后清除旧目录。此功能是实际查询输入能力；不开放尚无 `task_entry` 的运行。 |
| 4 | A：导出核验信息的实际业务阅读（已实施） | `ExportView.items[].validation` 已给固定输入/文件摘要、`verified_node_ids`、plan、渠道与版面检查状态；本轮已展开实际核验覆盖与固定输入，未知/未做检查保持原状态。 | 直接用已返回字段展开“核验覆盖范围/固定输入/未做检查”，不推算完整性或版面通过。**不能承诺直接打开 validation_artifact_id**：当前 `ArtifactContentController` 注释及服务只公开 domain-verified 文稿 body/field/evidence 制品，核验报告读取资格需另行验证/扩展。 |

已由协调者确认的增量：FE-08 当前 focused suite 为 20 项；RN-01 运行启动已补入并在独立复核。这里不重复登记为“待实现”，只保留现有切片的当前服务联调及完整原范围未完成项。上述四个切片现已有源码及隔离测试，仍待真实服务/浏览器联调，不意味着其他前端责任都已完成。

## 1. 优先项

### 1.1 Gru（FE-25）

**A：固定文稿范围的持久会话和用户消息记录（066，待真实联调）。**

- 已有 GruController 会话创建、列表、详情、消息追加、固定上下文及原操作只读恢复；前端 document-gru-records.vue 已接入文稿工作区。
- 只保存 USER 消息，状态明确为 RECORD_ONLY / NOT_EXECUTED。固定上下文经实际文稿读取权限复核；原请求体摘要和 CAS 冲突恢复已实现。
- 第14批验证包含该切片；不把消息记录视作已执行的模型问答、检索、检查或修改建议。

**C：实际问答与取消、修订建议/部分采纳、摘要、个人习惯、会话导出，以及完整多范围会话。**

- 现有正文修订和检索入口不能冒充一次采纳、消息流或模型执行结果。后续仍需真实执行、固定候选及唯一采用回执、摘要来源、习惯版本/删除语义和导出授权。

### 1.2 导出和成果转存（FE-26）

**A：现有固定导出消费已经接通代码，不能继续写成“缺导出 Controller”。**

- `ExportController#create/read/history/basis/execute/retry/content`：
  - `POST /api/documents/{documentId}/exports`
  - `GET /api/exports/{exportId}`
  - `GET /api/documents/{documentId}/exports`（`document_revision/page/size`）
  - `GET /api/documents/{documentId}/export-basis`
  - `POST /api/export-items/{exportItemId}/executions`
  - `POST /api/export-items/{exportItemId}/attempts`
  - `GET /api/export-items/{exportItemId}/content`
- `web/src/api/medical/exports.ts` 有对应请求、固定输入核验、原请求恢复和下载摘要校验；`document-export.vue` 调用 `readExportBasis/readExportHistory/createExport/executeExportItem/retryExportItem/downloadExportItem`，并区别逐格式状态、渠道核验及 `visual_check`。
- `SourceGovernanceController` 的 `POST /api/artifacts/{id}/source-submissions` 已由 `source-governance.ts#submitArtifactAsSource` 和 `artifact-catalogue.vue` 消费。成果转存不是只有设计按钮。
- 待联调：同一版本 DOCX/PDF/内部 Markdown、单格式失败不覆盖成功项、重复下载字节、未知写入恢复、撤权后下载拒绝、转存唯一待核验来源；不能用执行接口受理代替转换成功。

**A：导出历史完整分页、可读核验摘要与错误恢复体验（本轮已实施，待联调）。**

- Controller `history` 的 `page/size/has_more` 已被消费，每页20项；翻页与失败重读隔离旧记录，不能把首批历史当成全量历史。
- 可用已有 `ExportItem.validation` / 固定核验制品完善“核验了什么、哪些未做”的阅读。没有实际版面检查证据时保持 `NOT_RUN`。

**C：从未签发文稿走通正式签发后导出。**

- `ExportService#create` 对 `FORMAL_BODY` 查询签发记录并核查授权/治理水位；`basis` 返回 `formal_signature_ready`。这证明导出依赖真实签发，**不证明系统已有创建签发的公开入口**。
- 不得前端改签发状态、改为审查包冒充正式正文，或以夹具预置签发证明正式闭环完成。

### 1.3 审查、签发（FE-23）

**A：已保存全文只读审查、批注/回复/重新定位/人工处理。**

- `task-detail.vue` review 阶段读取实际已保存正文，明确写明“签发操作尚未接入本工作区”。
- `DocumentReadController`：`GET /api/documents/{documentId}`、`/body`、`/revisions/{documentRevision}`、`/dependency-access-set`。
- `AnnotationThreadController`：`GET/POST /api/documents/{id}/annotation-threads`；`AnnotationReplyController`：`GET/POST /api/annotation-threads/{id}/replies`；`AnnotationDecisionController`：`GET/POST /api/annotation-threads/{id}/decisions`；`AnnotationAnchorController`：anchors / reanchors；`AnnotationAnchorReconciliationController`：`POST /api/annotation-threads/{id}/anchor-reconciliations`。
- 前端证据：`document-annotations.vue`、`document-reanchors.vue`、对应 `annotation-*.ts`。待联调当前版本、过期锚点、回复不等于解决、并发重开和撤权。

**A：独立退回修改及当前用户待修改队列（067，离线集成核验通过、待真实联调）。**

- DocumentReviewReturnController / DocumentRevisionAssignmentQueueController 已提供独立文稿退回、分派、历史和只读原操作恢复，以及按当前用户授权过滤的有界队列。前端退回面板已接入审查工作区，首页队列和历史版本定位已接入并通过定向测试。
- 只允许 REVIEW_PENDING → RETURNED，保留已保存正文，不伪造 formal ReviewBatch / ReviewDecision。已签发内容拒绝退回；原确认失效记录必须可核对。专业检查缺失时不绕过原正式确认门槛。
- 修改后重新提交尚未实现，不把新正文版本视为自动完成待修改项。状态分别为 OPEN / REVISION_AVAILABLE / INACTIVE。

**C：当前正文完整专业检查、正式审查批次确认、再次提交、预签发检查、再次认证、固定签发与历史。**

- 独立退回、批注决定和技术结构检查均不能代替正式审查确认或签署。完整流程仍需 BE-23 的门槛、一次认证回执、检查/意见水位和固定签发读回；专业检查依赖 BE-17—22。
- 068 固定正文技术结构检查已接入真实作业、制品与前端，离线集成核验通过；它只核结构及内部引用，professional_coverage=UNKNOWN、overall_review_ready=false，不能证明专业就绪或替代完整专业检查。真实数据库/对象存储/浏览器联调仍待完成。

### 1.4 客户连接（FE-30 / S16）

**C：连接配置、秘密引用、身份/资料映射、验证、同步、逐项结果和恢复。**

- LLD 指定 `/connections`、CT-01—03；当前业务 Router 未注册对应连接页面，全部 Controller 映射未见客户连接/验证/同步端点。
- RuoYi 的数据库、定时任务、监控或通用配置接口不是客户 B/S 同步接口；不能拿它们代替双端业务结果。
- 必须先交付客户协议/读写授权、秘密引用契约、受限目标校验、持久同步任务与逐项冲突/失败回读。此后前端才能进行连接编辑、明确范围确认和重试。现阶段只可提供诚实的 BLOCKED 说明。

### 1.5 场景与五类扩展（FE-29、FE-31—35）

**A：当前获授权配置目录和规划缺项说明。**

- `ScenarioCatalogueController#read`：`GET /api/scenarios`，由 `scenarios.ts#listScenarios` 和 `scenarios.vue` 消费，带项目、文档类型、游标、水位校验。
- `ScenarioCatalogueService` 明确给现有记录设置 `definition_status=UNVERIFIED`、`task_entry=null`，缺项包括 `SCENARIO_SPECIFICATION_UNVERIFIED` 与 `RELEASE_ADMISSION_UNAVAILABLE`；规划项同样无任务入口。

**B：当前场景目录项目选择与可读版本导航。**

- 当前 `scenarios.vue` 项目条件仍是标识输入；可复用已有获授权项目列表及选择组件，保留首批/分页、会话和权限范围。目录已有 API，不必等待专属场景算法。

**C：规格维护、正式发布决定、固定规格任务采用，以及五类正式流程。**

- 现有场景接口是只读目录，不含完整规格写入/核验/发布/正式采用接口。
- 法规比较：两侧固定版本、差异证据、审核与固定成果。
- 报告生成：专属输入/模板/检查、候选审核与同版导出。
- 翻译：原译文对应、语言术语、数值/格式核验及人工修订。
- 市场分析：时间口径、研究边界、证据/局限与停止条件。
- 多模态问答：批准媒介、原始位置与引用、无答案/冲突与采用。
- 这些专属公开 Controller 当前均未发现；需要对应 BE-31—35 和 PLN-D08 完整规格。通用注册文档任务、模拟 Agent 草稿或场景卡片不等于五类业务已实现。

## 2. 其余前端责任与可推进项

| 计划范围 | 分类 | 当前证据与剩余工作 |
| --- | --- | --- |
| FE-01/03 工程和契约 | A | 当前医疗 TS/SFC/API 与 Schema 消费存在；根协调者已做 typecheck。继续最终制品/目标部署字节核验和全部新增响应负例；本核查不宣称 build 或部署完成。 |
| FE-04 身份、企业、岗位、成员、项目 | A | `BusinessSessionController` session/tenant-selection-options/tenant-selections；企业申请审查、岗位版本、邀请、permission-decisions；`ProjectController` 项目读取/创建/profile-revisions/state-decisions。对应 session、enterprise-*、project* 视图已有消费者；需完整真实角色矩阵及不确定写入恢复。 |
| FE-05 原操作与固定资源 | A | `OperationController` POST `/api/operation-lookups`、GET `/api/operations/{id}`；`ArtifactContentController` GET `/api/artifacts/{id}/content`（目前限 domain-verified 文稿 body/field/evidence 制品）；`operations.ts` / `request-metadata.ts` 已消费。逐业务验证 EXPIRED/NOT_FOUND/PROCESSING，不能以一次公共工具测试代替所有写入恢复。 |
| FE-05 业务通知/审计中心 | C | 当前 Controller 的 RuoYi notice/operlog 属框架管理，未发现计划所需医疗对象通知/业务审计公开投影及目标权限重验入口；需明确业务契约。 |
| FE-06 工作台和访问拒绝 | A | `index.vue` 消费 GET projects/work-items（当前 assignee）及选定项目 tasks；`access-denied.vue` 已集成 `/access/denied`，返回实际 `/index`；待当前服务下分页、部分失败、撤权、深链、窄屏/键盘联调。 |
| FE-06 十八管理入口 | A | `FrameworkNavigationController` GET `/api/framework/navigation` + 全量 RuoYi Controller（附录）。前端动态绑定不等于十八模块每个动作已验收；逐项核对当前身份和环境菜单，尤其导出/删除/开发工具。 |
| FE-07 模型草稿、预算 | A | `ModelConfigurationController` POST/GET `/api/model-config-revisions`、`/api/simulation-model-config-revisions`；Discovery GET 列表；`BudgetController` 预算/版本/decisions。对应 model-configurations、simulation-model-configurations、budget* 视图消费；旧文档“无模型 Controller”已不准确。 |
| FE-07 正式模型验证/启停/生产采用 | C | 创建固定草稿和读回不等于外部连接验证、完整生命周期或生产发布。当前模型 Controller 只有 POST 版本和 GET 固定版本；仍需明确验证结果、秘密引用及正式采用准入。不得用 SIMULATION 资格替代生产。 |
| FE-08 上传、处理、原件 | A | SourceUploadController 创建/完成/读取，SourceUploadStatusController 状态；`SourceUploadPartConfiguration` 分段过滤器；source-uploads/source-upload-result/source-file-preview/source-original-reader。需当前七格式实际持久解析终态、失败/重复文件、断点和撤权组合联调。 |
| FE-09 检索、索引和 EXACT | A | RetrievalController POST/GET retrievals；SourceExactController exact-fields 与 retrievals/exact；SourceIndexViewController index-builds。source-search/source-exact-* / source-index-builds 有消费者；非空多页、固定原文、索引实际结果需验证。 |
| FE-09 网络采集与完整来源证明 | C | 当前检索与来源读取不是联网采集授权/结果接口；未发现对应公开采集 Controller。DENSE/HYBRID 的实际可用配置/消费者结果需后端运行证据，不由前端推算。 |
| FE-10 来源、目录、组织与成果 | A | SourceGovernanceController source-versions/content/usages、source-folders、organization-decisions、lifecycle、binding、processing、source-submissions；SourceRightsController 权利审查/决定；相关 catalogue/governance/rights/processing/collections 视图存在。待当前授权及删除回执/影响完整性联调。 |
| FE-11 模板与规则 | A | TemplateController 创建/approval-decisions/baseline；TemplateApplicabilityController 项目适用模板；RuleVersionController 创建/读取/approval-decisions/baseline + Discovery。对应 template-* / rule-* 视图存在，需用完整适用条件、依据、并发版本验证。 |
| FE-12 任务与计划 | A | TaskController 创建候选/草稿/原操作、POST tasks、plan-revisions/plan-decisions/decision-basis、任务列表/详情；task-create/task-plan-editor/task-plan-return/task-detail 有消费。需真实固定输入、退回和确认的全组合。 |
| FE-16 正文保存与阅读 | A | DocumentReadController + DocumentRevisionController；document-editor/text/node/field/body-reader/evidence-panel 有实际读取/保存。待完整选区、引用、并发修订、未保存离开、撤权。 |
| FE-16 候选一次采用 | C | 未发现文稿候选一次采用公开 Controller；普通 revisions 不等于 DC-03 原子采用。需要固定候选、采用子集、原版本和唯一修订回执。 |
| FE-17—22 专业文稿 | A/C | 通用模板/正文编辑和十三类 taskDocumentLabels 已存在（A，仅通用部分）。技术要求、综述、研究/风险/基本原则、临床/免评、说明书/独立标签、IVD/变更/延续的专属规则/核查/关联证据闭环仍属 C；未发现相应专业检查 Controller。枚举和模板编辑器不能证明专业验收已完。 |
| FE-24 事实/依据变更影响 | A/C | 已有 source usages、文稿 dependency-access-set、项目 profile-revisions 的局部消费（A）；完整共享事实修订、完整跨对象影响水位、影响确认后继续及签发失效闭环缺公开业务投影/决定接口（C）。 |
| FE-27 启动/快照/SSE/取消 | A | RunCreateController POST `/api/tasks/{taskId}/runs`；RunReadController GET `/api/runs/{runId}`、POST cancel-requests；RunEventConfiguration `/api/runs/{runId}/events`；run-create/runs/run-events/run-cancellation 和 run-detail 已消费。需真实持久步骤、断连恢复和取消竞争。 |
| FE-27 人工继续/输入变更 | C | 当前公开 Run Controller 未提供人工继续、输入变更确认和相应原操作入口；内部 Workflow 信号/模拟执行器不等于浏览器公开 API。待 BE-13/27 完整公开投影与控制接口。 |
| FE-28 配置/评测 | A | ConfigurationAuthoringController capability-versions / simulation-agent-versions；EvaluationController POST evaluation-batches、scores、finalize，GET batch/scores；现有 configuration-authoring/evaluation-* 已消费。评分编辑/同样本比较必须按真实批次证据联调。 |
| FE-28 工具目录、生产发布与运营 | C | agents.vue tools 标签明确为空目录、模拟草稿无生产资格。未发现完整工具登记/正式发布/停用恢复/按 Agent 调用汇总/反馈处理/医疗审计导出公开 Controller。框架日志与模拟评测不能代替运营统计分母或生产准入。 |
| FE-36—39 环境、测试、安全、NFR | A（现有功能的验证任务） | 可继续检查现有接口消费者、拒绝/错误/中断、可访问性和制品；全范围仍依赖未交付业务、实际服务及批准测量条件，不能用工程测试宣布安全/NFR 全通过。 |
| FE-40—42 SaaS/私有化/岗位交接 | C（外部交付条件） | 代码说明和制品准备可推进；正式完成依赖目标部署、客户/服务岗位权限与实际接收。没有相应环境及全产品范围，不登记正式交接完成。 |

IMP-02 是输入清单，IMP-13—15 的内部调度/Harness/专业节点通过 FE-07/12/16/23/27/28 消费，不新增独立前端工单。

## 3. 推荐下一步（不改变主状态）

1. **先完成第 0 节可用接口功能切片，再联调当前代码**：工作台/任务/稿件/批注/导出/成果转存/模型与评测的现有公开流程，保留具体接口和终态证据。
2. **前端独立可推进**：导出历史分页、场景项目选择、核验摘要与局部错误提示、现有流程键盘/焦点/窄屏及未知结果恢复体验。以上限于已定义响应，不能增加虚构业务字段。
3. **后端优先补正式审查签发**：它直接阻断正式成品闭环；与当前导出能力衔接。
4. **并行明确 Gru 与客户连接契约**：Gru 固定建议及一次采纳语义；客户协议、秘密引用、双端同步和授权。
5. **场景按完整规格逐个交付**：先规格/发布/固定任务采用，再五类专属执行；不能仅把目录按钮开放为“可运行”。

## 附录：当前 Controller 映射证据

以下扫描全部 `business/**/src/main/java/**/*Controller.java`，列出真实源码路径与映射注解。`RequestMapping` 为类/方法原注解；框架控制器的类前缀须与方法路径组合。列表只证明源码有映射，**不证明目标环境已启用、认证通过或业务终态正确**。无映射的 BaseController 也保留记录。内部 `/internal/` 条目仅供边界核查，禁止前端调用。

额外公开非 Controller 入口：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RunEventConfiguration.java` 注册运行事件 `/api/runs/{runId}/events`；`SourceUploadPartConfiguration.java` 注册 `/api/uploads/*` 分段处理过滤器。不能因 Controller 清单没有事件方法而误判 SSE 缺失。

扫描到 72 个 Controller 源文件。

### `FrameworkNavigationController.java`

路径：`business/medical-identity/src/main/java/com/ruoyi/medical/identity/FrameworkNavigationController.java`

- L25 `GET /api/framework/navigation`

### `LocalAccountManagementController.java`

路径：`business/medical-identity/src/main/java/com/ruoyi/medical/identity/LocalAccountManagementController.java`

- L32 `GET /api/system/user/profile`
- L41 `POST /api/local-account/profile-revisions`
- L48 `GET /api/local-accounts`
- L61 `POST /api/local-accounts`
- L67 `POST /api/local-accounts/{id}/state-decisions`
- L73 `POST /api/local-accounts/{id}/profile-revisions`
- L79 `POST /api/local-accounts/{id}/credential-revisions`
- L85 `POST /api/local-account/credential-revisions`

### `LocalIdentityController.java`

路径：`business/medical-identity/src/main/java/com/ruoyi/medical/identity/LocalIdentityController.java`

- L25 `GET /`
- L28 `GET /auth/login`
- L31 `GET /auth/setup`
- L34 `GET /auth/csrf`
- L41 `POST /auth/setup`
- L48 `GET /auth/account`

### `AnnotationAnchorController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AnnotationAnchorController.java`

- L27 `GET /api/annotation-threads/{id}/anchors`
- L34 `POST /api/annotation-threads/{id}/reanchors`
- L50 `GET /api/documents/{id}/annotation-anchors`

### `AnnotationAnchorReconciliationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AnnotationAnchorReconciliationController.java`

- L27 `POST /api/annotation-threads/{id}/anchor-reconciliations`

### `AnnotationDecisionController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AnnotationDecisionController.java`

- L27 `GET /api/annotation-threads/{id}/decisions`
- L34 `POST /api/annotation-threads/{id}/decisions`

### `AnnotationReplyController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AnnotationReplyController.java`

- L27 `GET /api/annotation-threads/{id}/replies`
- L34 `POST /api/annotation-threads/{id}/replies`

### `AnnotationThreadController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AnnotationThreadController.java`

- L27 `GET /api/documents/{id}/annotation-threads`
- L38 `POST /api/documents/{id}/annotation-threads`

### `ArtifactContentController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ArtifactContentController.java`

- L32 `GET /api/artifacts/{id}/content`

### `BudgetController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/BudgetController.java`

- L23 `GET /api/budgets`
- L30 `GET /api/budgets/{budgetId}`
- L37 `GET /api/budgets/{budgetId}/revisions`
- L45 `GET /api/budgets/{budgetId}/revisions/{budgetRevision}`
- L53 `POST /api/budgets`
- L62 `POST /api/budgets/{budgetId}/decisions`

### `BusinessSessionController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/BusinessSessionController.java`

- L40 `GET /api/session/tenant-selection-options`
- L56 `GET /api/session/identity-binding`
- L73 `POST /api/session/tenant-selections`
- L94 `GET /api/session`

### `CollectionController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/CollectionController.java`

- L23 `POST /api/collections`
- L32 `POST /api/collections/{id}/versions`
- L41 `GET /api/collections/{id}/versions`

### `ConfigurationAuthoringController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ConfigurationAuthoringController.java`

- L22 `POST /api/capability-versions`
- L32 `GET /api/capability-versions/{revisionId}`
- L41 `POST /api/simulation-agent-versions`
- L51 `GET /api/simulation-agent-versions/{revisionId}`

### `ConfigurationDiscoveryController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ConfigurationDiscoveryController.java`

- L22 `GET /api/model-config-revisions`
- L29 `GET /api/simulation-model-config-revisions`
- L36 `GET /api/rule-versions`

### `DocumentReadController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentReadController.java`

- L31 `GET /api/documents/{documentId}`
- L39 `GET /api/documents/{documentId}/body`
- L56 `GET /api/documents/{documentId}/revisions/{documentRevision}`
- L65 `GET /api/documents/{documentId}/dependency-access-set`

### `DocumentRevisionController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentRevisionController.java`

- L30 `POST /api/documents/{documentId}/revisions`

### `DocumentUploadController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentUploadController.java`

- L34 `POST /api/artifact-upload-grants/{grantId}/completions`
- L52 `POST /api/artifact-upload-grants`

### `EnterpriseRegistrationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/EnterpriseRegistrationController.java`

- L25 `GET /api/enterprise-registrations`
- L37 `GET /api/enterprise-registrations/{id}`
- L44 `POST /api/enterprise-registrations`

### `EnterpriseRegistrationReviewController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/EnterpriseRegistrationReviewController.java`

- L21 `GET /api/enterprise-registration-reviews`
- L34 `GET /api/enterprise-registration-reviews/{id}`
- L40 `POST /api/enterprise-registration-reviews/{id}/decisions`

### `EnterpriseRoleController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/EnterpriseRoleController.java`

- L23 `GET /api/enterprise-roles`
- L37 `GET /api/enterprise-roles/{roleId}/versions/{versionId}`
- L46 `POST /api/enterprise-roles/{roleId}/versions`
- L57 `POST /api/enterprise-roles/{roleId}/versions/{versionId}/revocations`

### `EvaluationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/EvaluationController.java`

- L18 `POST /api/evaluation-batches`
- L25 `POST /api/evaluation-batches/{id}/scores`
- L32 `POST /api/evaluation-batches/{id}/finalize`
- L38 `GET /api/evaluation-batches/{id}`
- L43 `GET /api/evaluation-batches/{id}/scores`

### `ExportController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ExportController.java`

- L37 `POST /api/export-items/{exportItemId}/executions`
- L48 `POST /api/documents/{documentId}/exports`
- L58 `GET /api/exports/{exportId}`
- L65 `GET /api/documents/{documentId}/exports`
- L76 `GET /api/documents/{documentId}/export-basis`
- L86 `POST /api/export-items/{exportItemId}/attempts`
- L96 `GET /api/export-items/{exportItemId}/content`

### `MemberGovernanceReadController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/MemberGovernanceReadController.java`

- L25 `GET /api/enterprise-members, /api/permission-grants, /api/member-invitations`

### `MemberInvitationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/MemberInvitationController.java`

- L28 `POST /api/member-invitations`
- L37 `POST /api/member-invitations/{id}/acceptances`

### `ModelConfigurationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ModelConfigurationController.java`

- L22 `POST /api/model-config-revisions`
- L32 `GET /api/model-config-revisions/{revisionId}`
- L41 `POST /api/simulation-model-config-revisions`
- L51 `GET /api/simulation-model-config-revisions/{revisionId}`

### `OperationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/OperationController.java`

- L25 `POST /api/operation-lookups`
- L33 `GET /api/operations/{id}`

### `PermissionDecisionController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/PermissionDecisionController.java`

- L27 `POST /api/permission-decisions`

### `ProjectController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ProjectController.java`

- L23 `GET /api/projects`
- L30 `GET /api/projects/{projectId}`
- L37 `POST /api/projects`
- L46 `POST /api/projects/{projectId}/profile-revisions`
- L54 `POST /api/projects/{projectId}/state-decisions`
- L62 `GET /api/work-items`

### `RetrievalController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RetrievalController.java`

- L33 `POST /api/retrievals`
- L43 `GET /api/retrievals/{id}`

### `RuleVersionController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RuleVersionController.java`

- L25 `POST /api/rule-versions`
- L39 `POST /api/rule-versions/{id}/approval-decisions`
- L53 `GET /api/rule-versions/{id}`
- L65 `GET /api/rule-versions/{id}/approval-baseline`

### `RunCreateController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RunCreateController.java`

- L20 `POST /api/tasks/{taskId}/runs`

### `RunReadController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RunReadController.java`

- L24 `POST /api/runs/{runId}/cancel-requests`
- L38 `GET /api/runs/{runId}`

### `ScenarioCatalogueController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ScenarioCatalogueController.java`

- L26 `GET /api/scenarios`

### `SimulationEvaluationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SimulationEvaluationController.java`

- L18 `POST /api/simulation-evaluations`
- L22 `POST /api/simulation-evaluations/enterprise-quota`
- L26 `POST /api/simulation-evaluations/retention`
- L30 `POST /internal/v1/simulation-evaluations/claim`
- L32 `POST /internal/v1/simulation-evaluations/status`
- L34 `POST /internal/v1/simulation-evaluations/issue`

### `SimulationEvaluationDispatchController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SimulationEvaluationDispatchController.java`

- L18 `POST /internal/v1/simulation-evaluations/execute`

### `SimulationFixtureController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SimulationFixtureController.java`

- L26 `POST /api/simulation-fixtures`

### `SimulationInvocationController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SimulationInvocationController.java`

- L25 `POST /internal/v1/simulation-invocations`
- L30 `POST /internal/v1/simulation-invocations/status`
- L35 `POST /internal/v1/simulation-invocations/cancel`

### `SimulationLocalRecoveryController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SimulationLocalRecoveryController.java`

- L28 `POST /internal/v1/simulation-evaluations/reconcile`
- L36 `POST /internal/v1/simulation-evaluations/settle`

### `SourceExactController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceExactController.java`

- L33 `POST /api/source-versions/{id}/exact-fields`
- L41 `GET /api/source-versions/{id}/exact-fields/{revision}`
- L49 `GET /api/source-versions/{id}/exact-fields`
- L58 `POST /api/retrievals/exact`
- L66 `GET /api/retrievals/exact/{id}`

### `SourceGovernanceController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceGovernanceController.java`

- L33 `GET /api/source-versions`
- L41 `GET /api/processing-attempts`
- L50 `GET /api/artifacts`
- L59 `POST /api/artifacts/{id}/source-submissions`
- L72 `GET /api/source-versions/{id}`
- L80 `GET /api/source-versions/{id}/content`
- L88 `GET /api/source-versions/{id}/usages`
- L96 `GET /api/source-folders`
- L108 `POST /api/source-folders`
- L118 `POST /api/source-folders/{id}/decisions`
- L129 `POST /api/source-organization-decisions`
- L139 `POST /api/deletion-items/{id}/receipts`
- L150 `GET /api/deletion-jobs/{id}`
- L158 `POST /api/source-versions/{id}/approval-decisions`
- L168 `POST /api/source-versions/{id}/processing-attempts`
- L179 `POST /api/source-versions/{id}/lifecycle-decisions`
- L190 `POST /api/projects/{projectId}/source-bindings`

### `SourceIndexViewController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceIndexViewController.java`

- L26 `GET /api/source-versions/{id}/index-builds`

### `SourceRightsController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceRightsController.java`

- L29 `POST /api/source-versions/{id}/rights-reviews`
- L36 `POST /api/source-versions/{id}/rights-decisions`

### `SourceUploadController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceUploadController.java`

- L24 `POST /api/uploads`
- L33 `GET /api/uploads/{uploadId}`
- L46 `POST /api/uploads/{uploadId}/completions`

### `SourceUploadStatusController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceUploadStatusController.java`

- L27 `GET /api/uploads/{uploadId}/status`

### `TaskController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TaskController.java`

- L47 `GET /api/projects/{projectId}/task-creation-owners`
- L56 `GET /api/projects/{projectId}/task-creation-sources, /api/projects/{projectId}/task-creation-collections`
- L66 `POST /api/projects/{projectId}/task-create-drafts`
- L75 `GET /api/projects/{projectId}/task-create-drafts`
- L89 `POST /api/tasks`
- L98 `GET /api/projects/{projectId}/task-create-operations`
- L113 `POST /api/tasks/{taskId}/plan-decisions`
- L122 `GET /api/tasks/{taskId}/plan-decision-basis`
- L131 `GET /api/tasks/{taskId}/plan-decision-operations`
- L149 `POST /api/tasks/{taskId}/plan-revisions`
- L161 `GET /api/tasks/{taskId}/plan-revisions/{planRevision}`
- L172 `GET /api/tasks/{taskId}/plan-revisions`
- L181 `GET /api/tasks/{taskId}/plan-operations`
- L191 `GET /api/tasks/{taskId}/plan-edit-basis`
- L209 `GET /api/projects/{projectId}/tasks`
- L221 `GET /api/tasks/{taskId}`

### `TemplateApplicabilityController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TemplateApplicabilityController.java`

- L22 `GET /api/projects/{id}/applicable-templates`

### `TemplateController.java`

路径：`business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TemplateController.java`

- L25 `POST /api/template-versions`
- L39 `POST /api/template-versions/{id}/approval-decisions`
- L53 `GET /api/template-versions/{id}/approval-baseline`

### `CaptchaController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/common/CaptchaController.java`

- L45 `GET /captchaImage`

### `CommonController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/common/CommonController.java`

- L29 `REQUEST /common`
- L45 `GET /download`
- L74 `POST /upload`
- L100 `POST /uploads`
- L137 `GET /download/resource`

### `CacheController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/monitor/CacheController.java`

- L31 `REQUEST /monitor/cache`
- L50 `GET `
- L74 `GET /getNames`
- L81 `GET /getKeys/{cacheName}`
- L89 `GET /getValue/{cacheName}/{cacheKey}`
- L98 `DELETE /clearCacheName/{cacheName}`
- L107 `DELETE /clearCacheKey/{cacheKey}`
- L115 `DELETE /clearCacheAll`

### `ServerController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/monitor/ServerController.java`

- L16 `REQUEST /monitor/server`
- L20 `GET `

### `SysLogininforController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/monitor/SysLogininforController.java`

- L29 `REQUEST /monitor/logininfor`
- L39 `GET /list`
- L49 `POST /export`
- L59 `DELETE /{infoIds}`
- L67 `DELETE /clean`
- L76 `GET /unlock/{userName}`

### `SysOperlogController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/monitor/SysOperlogController.java`

- L28 `REQUEST /monitor/operlog`
- L35 `GET /list`
- L45 `POST /export`
- L55 `DELETE /{operIds}`
- L63 `DELETE /clean`

### `SysUserOnlineController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/monitor/SysUserOnlineController.java`

- L32 `REQUEST /monitor/online`
- L42 `GET /list`
- L77 `DELETE /{tokenId}`

### `SysConfigController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysConfigController.java`

- L31 `REQUEST /system/config`
- L41 `GET /list`
- L51 `POST /export`
- L63 `GET /{configId}`
- L72 `GET /configKey/{configKey}`
- L115 `DELETE /{configIds}`
- L127 `DELETE /refreshCache`

### `SysDeptController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysDeptController.java`

- L32 `REQUEST /system/dept`
- L42 `GET /list`
- L53 `GET /list/exclude/{deptId}`
- L65 `GET /{deptId}`
- L119 `PUT /updateSort`
- L133 `DELETE /{deptId}`

### `SysDictDataController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysDictDataController.java`

- L34 `REQUEST /system/dict/data`
- L44 `GET /list`
- L54 `POST /export`
- L66 `GET /{dictCode}`
- L75 `GET /type/{dictType}`
- L115 `DELETE /{dictCodes}`

### `SysDictTypeController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysDictTypeController.java`

- L31 `REQUEST /system/dict/type`
- L38 `GET /list`
- L48 `POST /export`
- L60 `GET /{dictId}`
- L103 `DELETE /{dictIds}`
- L115 `DELETE /refreshCache`
- L125 `GET /optionselect`

### `SysIndexController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysIndexController.java`

- L34 `REQUEST /`
- L43 `POST /unlockscreen`

### `SysLoginController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysLoginController.java`

- L56 `POST /login`
- L72 `GET getInfo`
- L101 `GET getRouters`

### `SysMenuController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysMenuController.java`

- L31 `REQUEST /system/menu`
- L41 `GET /list`
- L52 `GET /{menuId}`
- L61 `GET /treeselect`
- L71 `GET /roleMenuTreeselect/{roleId}`
- L138 `PUT /updateSort`
- L152 `DELETE /{menuId}`

### `SysNoticeController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysNoticeController.java`

- L32 `REQUEST /system/notice`
- L45 `GET /list`
- L56 `GET /{noticeId}`
- L89 `GET /listTop`
- L104 `POST /markRead`
- L116 `POST /markReadAll`
- L130 `GET /readUsers/list`
- L144 `DELETE /{noticeIds}`

### `SysPostController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysPostController.java`

- L31 `REQUEST /system/post`
- L41 `GET /list`
- L51 `POST /export`
- L63 `GET /{postId}`
- L114 `DELETE /{postIds}`
- L123 `GET /optionselect`

### `SysProfileController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysProfileController.java`

- L35 `REQUEST /system/user/profile`
- L92 `PUT /updatePwd`
- L125 `POST /avatar`

### `SysRegisterController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysRegisterController.java`

- L28 `POST /register`

### `SysRoleController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysRoleController.java`

- L38 `REQUEST /system/role`
- L57 `GET /list`
- L67 `POST /export`
- L79 `GET /{roleId}`
- L141 `PUT /dataScope`
- L154 `PUT /changeStatus`
- L168 `DELETE /{roleIds}`
- L178 `GET /optionselect`
- L188 `GET /authUser/allocatedList`
- L200 `GET /authUser/unallocatedList`
- L213 `PUT /authUser/cancel`
- L224 `PUT /authUser/cancelAll`
- L235 `PUT /authUser/selectAll`
- L246 `GET /deptTree/{roleId}`

### `SysUserController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysUserController.java`

- L41 `REQUEST /system/user`
- L60 `GET /list`
- L70 `POST /export`
- L80 `POST /importData`
- L90 `POST /importTemplate`
- L101 `GET /, /{userId}`
- L179 `DELETE /{userIds}`
- L194 `PUT /resetPwd`
- L209 `PUT /changeStatus`
- L222 `GET /authRole/{userId}`
- L238 `PUT /authRole`
- L251 `GET /deptTree`

### `TestController.java`

路径：`business/ruoyi-admin/src/main/java/com/ruoyi/web/controller/tool/TestController.java`

- L29 `REQUEST /test/user`
- L39 `GET /list`
- L47 `GET /{userId}`
- L62 `POST /save`
- L74 `PUT /update`
- L92 `DELETE /{userId}`

### `BaseController.java`

路径：`business/ruoyi-common/src/main/java/com/ruoyi/common/core/controller/BaseController.java`

- 无直接映射。

### `GenController.java`

路径：`business/ruoyi-generator/src/main/java/com/ruoyi/generator/controller/GenController.java`

- L46 `REQUEST /tool/gen`
- L59 `GET /list`
- L71 `GET /{tableId}`
- L88 `GET /db/list`
- L100 `GET /column/{tableId}`
- L115 `POST /importTable`
- L130 `POST /createTable`
- L180 `DELETE /{tableIds}`
- L191 `GET /preview/{tableId}`
- L203 `GET /download/{tableName}`
- L215 `GET /genCode/{tableName}`
- L231 `GET /synchDb/{tableName}`
- L243 `GET /batchGenCode`

### `SysJobController.java`

路径：`business/ruoyi-quartz/src/main/java/com/ruoyi/quartz/controller/SysJobController.java`

- L36 `REQUEST /monitor/job`
- L46 `GET /list`
- L59 `POST /export`
- L71 `GET /{jobId}`
- L154 `PUT /changeStatus`
- L167 `PUT /run`
- L179 `DELETE /{jobIds}`

### `SysJobLogController.java`

路径：`business/ruoyi-quartz/src/main/java/com/ruoyi/quartz/controller/SysJobLogController.java`

- L28 `REQUEST /monitor/jobLog`
- L38 `GET /list`
- L51 `POST /export`
- L63 `GET /{jobLogId}`
- L75 `DELETE /{jobLogIds}`
- L86 `DELETE /clean`
