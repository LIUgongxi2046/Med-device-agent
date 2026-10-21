# 前端路由与状态覆盖交接清单（2026-10-20）

## 口径与结论

这是源码覆盖清单，不是浏览器验收报告，也不是新的工单分母。初始复核 HEAD `eb3b03b`；工作树中的并行改动可能晚于该提交。仅阅读 PRD、LLD、Router、Vue 组件及客户端 API；未启动服务、联网、构建或浏览器，未复验此前测试结果。下表“有实现”仅表示可定位的实现，不表示真实成功、拒绝、故障、视觉或键盘流程通过。

原范围包括业务路由、既有十八项框架管理、十三类文档及五类扩展场景。九组展示层或若干补丁完成，不能取代此范围。正式业务缺少权威服务结果时，前端保持阻断是正确状态，不应再堆积模拟成功界面。

## 共通保护及其限制

- [permission.js](../../web/src/permission.js)：受保护医疗路由先读取当前会话；框架深链必须属于本次服务端菜单注册表，否则到 `/access/denied`。路径或按钮不授予权限。
- [main.js](../../web/src/main.js)：`medical-failure`、页面隐藏与 `pagehide` 失效会话；路由异常进入 `/session-recovery`；Vue 未处理错误进入全局安全错误路径。此机制**不等于每个页面已有局部错误和重试体验**。
- [session-panel.vue](../../web/src/views/medical/session-panel.vue)、[session-recovery.vue](../../web/src/views/medical/session-recovery.vue)、[access-denied.vue](../../web/src/views/medical/access-denied.vue)：全局错误、身份/企业缺失、原目标恢复及拒绝后的安全入口。公开拒绝页不预读被拒绝对象。
- [route-read.ts](../../web/src/api/medical/route-read.ts)：路由离开/更新触发 clear，重新匹配后激活。它不是 dirty 确认器；消费者必须在清理前处理普通未保存输入。
- [request-metadata.ts](../../web/src/api/medical/request-metadata.ts)、[operations.ts](../../web/src/api/medical/operations.ts)：原请求身份/摘要与操作读取基础。不能由公共 helper 的存在推断每种写入已验证；各业务页的 pending/recover 才是接入证据。

## 组件族状态证据

标记：L=读取中；E=空/未读取；F=失败；D=拒绝/未获准；S=陈旧响应、版本、游标或上下文；U=未提交内容；W=已提交但结果未知。`共享 F/D` 表示依赖上面的安全会话链，**不是局部错误验收通过**。只读页 U/W 为不适用，不应伪造写入恢复。

| 范围与主要源码 | 已定位状态实现 | 未完成/交接边界 |
|---|---|---|
| 工作台 `views/index.vue`；项目 `projects.vue`、`project.vue`；任务列表 `tasks.vue` | L/E；分区错误/项目 error/任务 readError；按许可筛选；generation、AbortController、固定分页；首页 waitingTaskLocation | 项目待办逐项定位为本轮修复范围；资料完整度与申请节点统计未由接口提供，属后端投影，不推算百分比。 |
| 企业 `enterprise-members.vue`、`enterprise-roles.vue`、`member-governance-write.vue`、`enterprise-registration*.vue`、`invitation-accept.vue` | L/E/D；字段错误与共享 F；固定版本/会话隔离；pending、lookup/recover；岗位未保存退出对话框、登记离开守卫 | 邀请通知不自动发送；真实权限矩阵及未知请求回读未在本次运行。 |
| 账户 `views/login.vue`、`views/system/user/profile/{index,userInfo,userAvatar,resetPwd}.vue` | 原表单校验、登录重定向核验、本人资料/密码固定请求；身份改变清理 | 头像上传未接入时禁用，不能因原组件存在判全部可用。 |
| 项目写入 `project-write.vue`、`project-attributes.vue` | schema/字段错误、权限/状态准入、原请求恢复及固定项目回读；U 离开处理 | 普通读失败有共享链；真实 CAS/跨身份恢复需实际验证。 |
| 资料目录/队列 `source-catalogue.vue`、`source-processing.vue`、`artifact-catalogue.vue` | L、el-empty、next_after；generation+会话+active+hidden+abort 防旧响应；共享 F/D；成果转存字段错误与原请求 | 当前页空不能解释成全部无数据；七格式 Worker 终态不由界面证明。 |
| 上传 `source-uploads.vue`、`enterprise-source-uploads.vue`、`source-upload-result.vue` | 文件/分段/完成与原操作、逐文件结果、权限与来源版本检查 | 实际重试、重复文件与七格式解析终态仍需服务验收。 |
| 来源 `source-governance.vue`、`source-original-reader.vue`、`source-file-preview.vue`、`source-rights.vue`、`source-exact-fields.vue` | 固定版本、原件/解析、空页、处理中/失败、授权与摘要核验；写入 pending/恢复；原内容及读取 generation 隔离 | 引用失败局部重试、来源返回正文、章节引用关联为本轮明确工作；不得把定位成功说成支持陈述。 |
| 检索 `source-search.vue`、`source-exact-search.vue`、`source-index-builds.vue` | EMPTY 与结果状态分离；原检索 pending/EXPIRED/恢复；固定范围、游标、会话与 generation | DENSE/HYBRID、采集证明及非空多页真实终态需要后端/服务证据。 |
| 资料集 `collections.vue` | L、明确空成员、权限说明、Schema/导入错误、固定版本、pending 与原请求查询 | 空范围不能扩大为全库；真实创建/版本回读未在本次执行。 |
| 模板/规则 `template-create.vue`、`template-approval.vue`、`template-applicability.vue`、`rule-create.vue`、`rule-management.vue` | L/E/D；字段/导入错误；固定依据与版本；pending、操作回读；离开/更新守卫 | 十三专业文档的专属结果不能以模板 Schema 编辑能力代替。 |
| 新建任务 `task-create.vue` | D、busy、字段错误、真实 owner/source/collection 选择、generation/取消、pending 原创建及服务端草稿重建 | **发现明确 U 缺口**：普通未提交输入在 route-read clear 时直接清空，无离开确认；详见下节。正式已发布默认配置内容不可前端编造。 |
| 配置/计划/研究输入 `task-detail.vue`、`task-plan-editor.vue`、`task-plan-return.vue`、`task-field-target-*`、`task-node-producer-rules.vue`、`task-professional-packages.vue` | L/未读取、缺项/权限；固定快照；计划和提议映射原请求恢复；领域 helper 核验当前上下文 | PROPOSED 映射/消费授权不是 N03 医疗事实确认。共享 F 并非所有页面局部重试。 |
| 运行 `task-run-create.vue`、`run-detail.vue`、`task-run-recovery.vue` | 首次运行准入、预算与固定计划、原请求；L/空步骤、真实 waiting/inflight；SSE/取消与未知费用 | 本轮补完整只读详情/局部错误；正式继续、输入变更准入仍属后端，不允许从 allowed_resolution_actions 直接执行。 |
| 正文 `document-editor.vue`、`document-body-reader.vue`、`document-node-editor.vue`、`document-field-editor.vue` | L/无正文节点；只读审查与 dirty 提示；撤销/重做；三方比较；原保存 pending；内存文稿草稿与当前/历史版本分离 | `document-session-drafts.ts` 的内存保留不是刷新后恢复承诺。来源跳转、章节证据、焦点是本轮前端工作。 |
| 批注/退回 `document-annotations.vue`、`document-reanchors.vue`、`document-review-return.vue`、`document-review-assignments.vue` | 空分派/批注、权限、固定选区、旧锚点、失败、pending 与回读；独立退回恢复；不将新正文等同已处理 | `document-review-workspace.vue` 的重新提交摘要/签发步骤是本地准备，正式提交禁用；不是后端交付。 |
| 核验/候选 `document-field-verification.vue`、`document-chapter-candidates.vue`、`document-professional-checks.vue`、`document-technical-check.vue` | 明确无结果/未知专业覆盖、dirty/stale 阻断、版本绑定、本地草稿、技术检查真实请求恢复 | N03–N07 专业结果、一次采用、完整检查有效性仍待权威接口。局部选择不是采用成功。 |
| Gru `document-gru-records.vue`、`gru-record-export.vue` | 可用性/权限/空会话/局部 error；固定正文上下文；pending、NOT_FOUND、未应用证明；内存草稿；记录与执行明确区分 | 全局入口、IME/快捷键、焦点及发送期间新草稿是本轮前端工作；执行/采纳/习惯不是 UI 可独立完成。 |
| 导出/审计 `document-export.vue`、`document-save-audit.vue`、`saved-document-comparison.vue`、`document-revision-directory.vue` | 固定用途与版本、历史页、格式独立状态、下载核验、原请求、差异/历史版本 | 正式签发后的产出、真实转换版面/许可验证待完整后端链；不能降级用途冒充正式正文。 |
| 预算 `budgets.vue`、`budget.vue`、`budget-history.vue`、`budget-write.vue` | L/E/D、固定账户/批准版本、分页、generation；共享 F；W 原批准恢复，U 写入表单 | 隐藏由 main.js 失效会话触发清理，不应仅因子页无 visibility listener 误判缺失。 |
| 模型/Agent/策略 `agents.vue`、`model-configurations.vue`、`model-runtime.vue`、`formal-agent-drafts.vue`、`formal-capability-*`、`execution-policy-*`、`runtime-retention.vue` | 目录 L/E/F，未启用与权限区别；固定定义、游标过期显式重启；pending、恢复与版本回读；相关表单离开处理 | 配置目录不等于生产资格；正式能力只返回当前支持类型，不虚构工具目录/统计。 |
| 评测/发布 `evaluation-results.vue`、`formal-evaluation-context-bindings.vue`、`formal-publication.vue`、`simulation-*.vue` | error、pending/恢复、空样本/未评分、权限/独立验收人、固定范围、Schema；generation/取消；离开守卫 | 生产通过必须有真实专业执行及固定评分证明；不能由 SIMULATION 通过推出。 |
| 场景/连接 `scenarios.vue`、`ScenarioPlanningWorkspace.vue`、`connections.vue`、`source-candidates.vue` | 真实目录空/失败与规格缺失；连接 unknown/blocked、必填错误/焦点及本地草稿清理；网络候选明确无采集执行 | 五场景正式流程、连接验证/同步及网络采集属于后端/客户规格依赖；现有路由不是缺页，也不是业务完成。 |
| 框架十八项管理 | `framework-routes.js` 注册、撤销和过滤当前服务菜单；原 system/monitor/tool 组件 | 不用静态导航制造授权；未逐项执行管理写入、导出/删除/生产开发工具拒绝。 |

## 本轮明确前端问题与验收边界

父任务已经分派的工作：固定证据局部失败/重试；运行真实字段与错误呈现；项目待办逐项定位；固定来源返回原文；章节引用关联；Gru 入口及输入法交互；相关焦点修复。它们对应旧需求，不构成“七项即全产品”的新分母。各实现完成后由协调者补实际提交/测试证据，本清单不预写通过。

新增确认问题：`task-create.vue` 的 `clear()` 清空 goal/documentType/releaseId/ownerId/来源；`useMedicalRouteRead('MedicalTaskCreate', clear)` 直接在离开/更新时调用。当前无 dirty 判定、离开确认或 beforeunload 保护；`pendingTaskCreate` 只覆盖已经提交的请求。应为普通未提交输入提供继续编辑/明确放弃；若提供保存，必须仍经真实创建表单和用户明确提交，不能导航时自动创建任务。已发出的未知请求不得因放弃输入而移除原操作证据。权限/会话失效清理优先于普通离开守卫。验收须覆盖普通点击离开、同组件路由更新、浏览器离开提示、未知提交与身份失效，不持久保存客户草稿。

来源返回的精确未覆盖项：`source-return-navigation.ts` 的 `SourceReturnOrigin` 只有 path/documentRevision/chapterId/focusNodeId；`document-annotations.vue` 的 `selectedId` 是组件内部状态，当前仅发出 busy/read-original 事件，不提供来源返回选中态恢复。LLD §5.2 第 4 点要求返回恢复原正文版本、标注、章节与焦点；§2.1 还要求 S09/S11 往返保留标注筛选和证据展开。当前可以登记版本/章节/焦点返回切片，不可登记完整标注状态恢复。后续若补齐，应只保留受当前身份约束的标注 ID/筛选与固定版本，重新授权读回后才选中；版本变动、标注不可读或不存在应明确失效，不复制旧正文/意见，不恢复已失效选区。这是确定的前端状态要求，未在本审计扩展为后端问题。

另有明确“状态覆盖尚未逐页验收”而非新增已证实缺陷：若干早期消费者依赖全局 F/D，目录/schema/来源失败会进入安全会话恢复。表内对此如实标明，不能把全局错误处理计作各页面所有本地错误分支均通过；是否需更细局部恢复应按具体失败及敏感数据约束测试，不凭 grep 结果新造全面重写任务。

## 路由索引

下列为实际 `medicalRoutes` 全量叶子路由，按复用组件分组。静态 `/index` 工作台和登录别名另列；动态管理路由只由当前服务端菜单提供，不硬编码成固定可访问路径。路径存在不意味着有相应业务权威服务。

| 实际路由 | 消费组件 |
|---|---|
| `/session-recovery` | [session-recovery.vue](../../web/src/views/medical/session-recovery.vue) |
| `/enterprises/members` | [enterprise-members.vue](../../web/src/views/medical/enterprise-members.vue) |
| `/member-invitations/acceptances` | [invitation-accept.vue](../../web/src/views/medical/invitation-accept.vue) |
| `/enterprises/roles` | [enterprise-roles.vue](../../web/src/views/medical/enterprise-roles.vue) |
| `/sources/uploads` | [enterprise-source-uploads.vue](../../web/src/views/medical/enterprise-source-uploads.vue) |
| `/library/search/exact` | [source-exact-search.vue](../../web/src/views/medical/source-exact-search.vue) |
| `/library/versions/:sourceVersion/exact-fields` | [source-exact-fields.vue](../../web/src/views/medical/source-exact-fields.vue) |
| `/library/versions/:sourceVersion/rights` | [source-rights.vue](../../web/src/views/medical/source-rights.vue) |
| `/library` | [source-catalogue.vue](../../web/src/views/medical/source-catalogue.vue) |
| `/library/candidates` | [source-candidates.vue](../../web/src/views/medical/source-candidates.vue) |
| `/library/collections`<br>`/library/collections/new`<br>`/library/collections/:collectionId` | [collections.vue](../../web/src/views/medical/collections.vue) |
| `/library/search` | [source-search.vue](../../web/src/views/medical/source-search.vue) |
| `/library/processing` | [source-processing.vue](../../web/src/views/medical/source-processing.vue) |
| `/library/artifacts` | [artifact-catalogue.vue](../../web/src/views/medical/artifact-catalogue.vue) |
| `/library/versions/:sourceVersion` | [source-governance.vue](../../web/src/views/medical/source-governance.vue) |
| `/enterprises/registration-reviews`<br>`/enterprises/registration-reviews/:registrationId` | [enterprise-registration-review.vue](../../web/src/views/medical/enterprise-registration-review.vue) |
| `/enterprises/registrations` | [enterprise-registration.vue](../../web/src/views/medical/enterprise-registration.vue) |
| `/templates/new` | [template-create.vue](../../web/src/views/medical/template-create.vue) |
| `/templates`<br>`/templates/:templateId/approval` | [template-approval.vue](../../web/src/views/medical/template-approval.vue) |
| `/rules/new` | [rule-create.vue](../../web/src/views/medical/rule-create.vue) |
| `/rules`<br>`/rules/:ruleId` | [rule-management.vue](../../web/src/views/medical/rule-management.vue) |
| `/document-save-audit` | [document-save-audit.vue](../../web/src/views/medical/document-save-audit.vue) |
| `/evaluation`<br>`/evaluation/new`<br>`/evaluation/:evaluationId` | [evaluation-results.vue](../../web/src/views/medical/evaluation-results.vue) |
| `/simulation-model-configurations` | [simulation-model-configurations.vue](../../web/src/views/medical/simulation-model-configurations.vue) |
| `/model-configurations` | [model-configurations.vue](../../web/src/views/medical/model-configurations.vue) |
| `/access/denied` | [access-denied.vue](../../web/src/views/medical/access-denied.vue) |
| `/access` | [login.vue](../../web/src/views/login.vue) |
| `/account` | [index.vue](../../web/src/views/system/user/profile/index.vue) |
| `/projects` | [projects.vue](../../web/src/views/medical/projects.vue) |
| `/projects/new`<br>`/projects/:projectId/profile`<br>`/projects/:projectId/status` | [project-write.vue](../../web/src/views/medical/project-write.vue) |
| `/projects/:projectId/tasks` | [tasks.vue](../../web/src/views/medical/tasks.vue) |
| `/projects/:projectId/tasks/new` | [task-create.vue](../../web/src/views/medical/task-create.vue) |
| `/projects/:projectId/tasks/:taskId/config`<br>`/projects/:projectId/tasks/:taskId/documents/:documentId/write`<br>`/projects/:projectId/tasks/:taskId/documents/:documentId/review` | [task-detail.vue](../../web/src/views/medical/task-detail.vue) |
| `/projects/:projectId/tasks/:taskId/field-targets` | [task-field-target-preparation.vue](../../web/src/views/medical/task-field-target-preparation.vue) |
| `/projects/:projectId/tasks/:taskId/node-producer-rules` | [task-node-producer-rules.vue](../../web/src/views/medical/task-node-producer-rules.vue) |
| `/projects/:projectId/tasks/:taskId/field-target-capabilities` | [task-field-target-capabilities.vue](../../web/src/views/medical/task-field-target-capabilities.vue) |
| `/projects/:projectId/templates` | [template-applicability.vue](../../web/src/views/medical/template-applicability.vue) |
| `/projects/:projectId/uploads` | [source-uploads.vue](../../web/src/views/medical/source-uploads.vue) |
| `/projects/:projectId/task-professional-packages` | [task-professional-packages.vue](../../web/src/views/medical/task-professional-packages.vue) |
| `/projects/:projectId` | [project.vue](../../web/src/views/medical/project.vue) |
| `/connections` | [connections.vue](../../web/src/views/medical/connections.vue) |
| `/scenarios` | [scenarios.vue](../../web/src/views/medical/scenarios.vue) |
| `/agents/execution-policies/new` | [execution-policy-create.vue](../../web/src/views/medical/execution-policy-create.vue) |
| `/agents/execution-policies/:versionId` | [execution-policy-detail.vue](../../web/src/views/medical/execution-policy-detail.vue) |
| `/agents/execution-policies` | [execution-policy-list.vue](../../web/src/views/medical/execution-policy-list.vue) |
| `/agents/retention` | [runtime-retention.vue](../../web/src/views/medical/runtime-retention.vue) |
| `/agents/model-runtime` | [model-runtime.vue](../../web/src/views/medical/model-runtime.vue) |
| `/agents/context-bindings` | [formal-evaluation-context-bindings.vue](../../web/src/views/medical/formal-evaluation-context-bindings.vue) |
| `/agents/publication` | [formal-publication.vue](../../web/src/views/medical/formal-publication.vue) |
| `/agents/drafts` | [formal-agent-drafts.vue](../../web/src/views/medical/formal-agent-drafts.vue) |
| `/agents/capabilities/new` | [formal-capability-create.vue](../../web/src/views/medical/formal-capability-create.vue) |
| `/agents/capabilities/:recordId` | [formal-capability-detail.vue](../../web/src/views/medical/formal-capability-detail.vue) |
| `/agents/capabilities` | [formal-capability-list.vue](../../web/src/views/medical/formal-capability-list.vue) |
| `/agents` | [agents.vue](../../web/src/views/medical/agents.vue) |
| `/agents/simulation-preparation` | [simulation-preparation.vue](../../web/src/views/medical/simulation-preparation.vue) |
| `/agents/simulation-rules/:ruleVersion?` | [simulation-rule-management.vue](../../web/src/views/medical/simulation-rule-management.vue) |
| `/agents/budgets` | [budgets.vue](../../web/src/views/medical/budgets.vue) |
| `/agents/budgets/new`<br>`/agents/budgets/:budgetId/decisions` | [budget-write.vue](../../web/src/views/medical/budget-write.vue) |
| `/agents/budgets/:budgetId/revisions`<br>`/agents/budgets/:budgetId/revisions/:budgetRevision` | [budget-history.vue](../../web/src/views/medical/budget-history.vue) |
| `/agents/budgets/:budgetId` | [budget.vue](../../web/src/views/medical/budget.vue) |

- `/index` → [views/index.vue](../../web/src/views/index.vue)，根布局重定向到该工作台。LLD 的 `/workbench` 是设计身份，当前实现未注册该别名；正常导航已使用 `/index`，不将文档名称差异计为业务缺页。
- `/access` 别名 `/auth/login`、`/local-auth/login.html`；医疗 `/login` 同样复用 [views/login.vue](../../web/src/views/login.vue)。`/register`、`/lock`、框架个人资料和 `/401` 属原框架条件路由，不等于获得医疗业务权限。
- 十八项管理的范围是用户、角色、菜单、部门、岗位、字典、参数、通知公告；在线用户、定时任务、操作日志、登录日志、服务监控、缓存监控、连接池监视；代码生成、系统接口、表单构建器。实际可达路径和动作必须取 `/api/framework/navigation`，由 [framework-routes.js](../../web/src/components/Medical/framework-routes.js) 与 [permission store](../../web/src/store/modules/permission.js) 处理。

## 交接用语

允许：某个前端消费者/导航/恢复切片已实现，附对应测试与提交；现有服务能力范围的前端实现已盘点，专业闭环和环境验收另列。

不允许：仅凭路由存在、预览、静态检查或通过若干测试宣布全前端/全业务完成；把后端阻断按钮的本地演示称为正式签发、发布、继续运行或模型执行成功。本次浏览器/视觉/真实服务验证始终为未执行，浏览器仍受此前拒绝阻断，未重试或绕过。

## 09:19 UTC 实施更新

上述两项已确认代码缺口已补齐：task-create 的普通离开/更新确认及 beforeunload 保护已实现；来源返回已扩展到原选中标注 ID，在固定版本和当前权限下重新读取并核验成员关系后恢复。新回复草稿、未知请求、版本或身份变化会阻断恢复，不重放写入。前述表格保留审计时证据，不能将其当作本次更新后仍未修复的清单。

字段失败重试、项目待办定位、运行详情、章节与证据双向定位、Gru 入口/键盘以及任务草稿保护均已实施并有针对性测试。本轮全量自动化门禁待执行；浏览器视觉、窄屏与真实服务联调仍未执行。
