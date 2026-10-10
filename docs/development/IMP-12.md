# IMP-12 项目任务、来源快照与计划决定

## 任务创建

任务创建的资料与资料集选择代码及必要集成登记IMPLEMENTED，完整任务创建继续IN_PROGRESS。依据PRD§8.4、LLD-前端FRTE-07、原SourceScope及CollectionAuthority。Given当前项目创建许可和当前获授权固定资料；When明确查询并选择来源；Then返回完整固定版本标识、当前正式来源资格和同一查询水位，保存选择时保持原四种来源模式。检索并复用ScopeSelectionAuthority、SourceReadAuthority、CollectionAuthority和CollectionMapper，TaskScopeSelectionService、TaskScopeSelectionSchema提供GET `/api/projects/{projectId}/task-creation-sources`与`task-creation-collections`。来源沿用完整正式候选资格，资料集重新核验固定成员及当前正式来源；没有构造专业核准或正式资料。查询使用当前依赖容量限制，超限明确拒绝；水位包含全部当前授权候选内容、本人、项目、上下文及企业许可版本，未知参数、旧上下文、跨项目及撤权拒绝。task-scope-selection.ts核对封闭响应、查询摘要、原水位、固定类型及版本顺序。task-create.vue从实际响应选择资料与资料集，查询和下一页由用户明确操作；保留四种来源模式、独立清空、关闭网络及原创建输入恢复。

资料集的source_scope_sha256固定当前正式成员的来源、权利、解析、索引、核准、项目绑定及Gate修订；成员范围的当前资格变化参与完整查询水位。当前验证：实际Spring、MySQL、Redis运行两项公开查询，当前获授权项目没有正式资料及资料集时返回明确空集合；同一原水位重复读取返回相同数据，原集合以外的游标返回CURSOR_INVALID。未知主体查询、缺少水位的游标、旧上下文、其他项目、错误水位及撤权均拒绝。完整草稿保存、重复提交、重新读取和创建失败后的保留继续通过。新增完整资料集版本SQL在受保护企业事务执行；原ScopeSelectionAuthority入口委托同一完整处理方法，任务创建、范围保存与新查询保持一致。页面strict类型、原生Schema加载和PyYAML完整OpenAPI读取通过。原始证据：`.implementation-work/IC-12-PLAN-WRITE-20261008-TASK-SCOPE-HTTP-02/receipt.json`，摘要29c244e0d7f848f044df581355a941592c9ecf820882e03d0030effe535e512b；`.implementation-work/IC-12-PLAN-FRONT-20261008-TASK-SCOPE-FRONT-02/receipt.json`，摘要1439781a0733c70750ef5d74c00103cf866c59f6d5e82743243237ee2896087d；均成功终止且inputs_unchanged=true。有效初始缺少入口断言保留于`IC-12-PLAN-WRITE-20261008-TASK-SCOPE-RED-01`。当前未提交源码，CURRENT，last_verified=2026-10-08；正式资料成功选择、多页操作、完整页面及新部署尚未验收。

任务创建的受控责任人员查询登记IMPLEMENTED。Given当前企业身份、项目创建许可及实际有效成员；When查询当前项目的责任人员；Then仅返回具有当前有效身份和项目任务读取许可的成员，保持企业隔离、有限分页和明确水位；旧上下文、未知查询、水位变化及撤权拒绝。创建和计划仍使用原人员资格核验，查询结果不能延续过期许可。检索TaskPlanService.creationOwner、PermissionAuthority.findResource、tenant_member、identity_binding和ownerGrants，人员查询与最终保存复用同一成员身份判断及完整许可条件。TaskCreationSelectionService、TaskCreationSelectionSchema及creationOwnerCandidates提供GET `/api/projects/{projectId}/task-creation-owners`；按实际成员UUID排序，有限候选页逐项核验，空页可以具有明确下一页游标，不扩大结果。页面只选择服务器返回的人员，当前本人仅在查询确认资格后作为默认值，下一页由用户明确请求。auth_epoch/governance_epoch、上下文、本人、项目和容量构成查询水位，变化返回CURSOR_EXPIRED。PlatformMemberIdentityStatus在同一事务读取原平台账号或OIDC身份的当前有效状态，企业成员与许可继续使用受保护Mapper；该组件只返回有效状态，不授予企业许可或认证身份。IdentityErrorResponseWriter提供明确的游标错误响应。

当前验证：实际Spring、MySQL、Redis接口返回当前本人及有效成员修订；缺少许可、未知查询、超限容量、旧上下文及错误水位拒绝。撤销项目任务读取许可后返回空人员集合，撤销创建许可后查询拒绝；原输入草稿保存、重新读取、同键重复提交和创建失败后的完整保留仍然通过。页面strict类型及原生Schema加载通过。原始证据：`.implementation-work/IC-12-PLAN-WRITE-20261008-TASK-OWNERS-HTTP-04/receipt.json`，摘要498311dcb4ac718648d334d5334646ed54c1fc610ad71b0c9890062390021d50；`.implementation-work/IC-12-PLAN-FRONT-20261008-TASK-OWNERS-FRONT-01/receipt.json`，摘要34c3056920dffcd385eb47d8b393c481e6a9786d20011140f8ced88284c3834d；两项成功终止且inputs_unchanged=true。有效初始行为断言保存于`IC-12-PLAN-WRITE-20261008-TASK-OWNERS-RED-01`。当前源码尚未部署，完整页面操作及多人员连续分页尚未验证；完整IMP-12继续IN_PROGRESS。

C06待补充定义：评测通过决定与授权发布决定的完整保存字段及相互引用；模型配置limits/data_policy、真实连接回执和当前凭据资格的封闭类型；source_scope_policy及已发布默认模板/资料集/人员的完整内容定义。当前AgentRelease Manifest提供固定版本引用，AgentReleaseStore提供原元数据；这些定义与消费者仍继续开发。正式样本、模型许可和客户批准分别记录，不阻止责任人员选择及其他已定义功能开发。

完整输入草稿保存与恢复登记IMPLEMENTED，任务创建整体继续IN_PROGRESS。依据PRD§8.4、现有operation_record、当前创建页、TaskInputSchema及本人原操作读取。Given完整已填写Create和当前项目创建许可；When保存输入草稿，随后创建请求失败；Then服务器保留本人输入，刷新后按原键明确读取，重复保存取得原响应，异参及当前撤权拒绝。草稿不产生任务修订、专业核准或发布资格；完整创建继续原准入。TaskCreateDraftService、TaskCreateDraftSchema及TaskPlanWriteMapper提供POST/GET `/api/projects/{projectId}/task-create-drafts`；沿用原企业事务和本人TK-01-DRAFT操作记录，读取时核查完整输入摘要、本人身份、项目、创建时间、审计及Outbox。独立Schema使用原封闭加载器和Create定义，保持既有Catalogue摘要。审计及Outbox只保存身份和摘要；完整输入只保存于本人操作记录。页面提交前保存草稿，刷新后由用户明确读取原创建结果和输入草稿；恢复全部输入后保留原键，继续提交重新核验创建准入。

当前草稿验证：实际Spring、MySQL、Redis中的完整输入保存、原201重新读取及同键重复提交通过；同键异参、缺少许可、旧上下文、未知查询、缺少字段和撤权均拒绝。相同键的创建请求失败后，原草稿完整保留，没有TK-01写入，原任务修订保持不变。审计及Outbox核验身份及摘要且不含正文，生成数据清理及应用关闭通过。页面strict类型和原生Schema编译/加载通过；OpenAPI经PyYAML完整读取。完整页面DOM、成功创建和部署尚未验证。

草稿原始证据：`.implementation-work/IC-12-PLAN-WRITE-20261008-TASK-DRAFT-HTTP-01/receipt.json`，摘要12afe64b8f7452b9a31e98a111157c317ead5cd6c03095302876e6411a6a8b93；`.implementation-work/IC-12-PLAN-FRONT-20261008-TASK-DRAFT-FRONT-01/receipt.json`，摘要bf174a21e031bb21ca164af50850575d65fc35a640ac5565dde8fa5aa290aa49。两项执行成功终止，inputs_unchanged=true；原JUnit与output.log保存于各自目录。初始草稿行为断言保留于`IC-12-PLAN-WRITE-20261008-TASK-DRAFT-RED-01`。适用当前未提交源码、CURRENT、last_verified=2026-10-08；源码尚未部署。

BACK-TASK-CREATE-01及FRONT-TASK-CREATE-01继续本唯一记录，需求来自PRD§8.4、AC-07/08/18/37、LLD-后端TK-01及LLD-前端FRTE-07。Given当前企业成员、有效项目及完整Create；When提交原操作键；Then同一企业事务保存固定快照、任务头、任务修订、完整输入、缺项、原响应、审计和Outbox；同键原请求恢复原结果，同键异参拒绝，来源范围保持显式选择。本次检索并核实TaskService、ScopeSnapshotStore、TemplateApplicabilityService、TaskPlanWriteMapper、TaskInputSchema、任务页面及当前SIMULATION记录，当前源码均为未提交工作区内容，版本引用下方回执所保存的输入摘要。

当前实现：TaskCreateService提供POST `/api/tasks`和本人GET `/api/projects/{projectId}/task-create-operations`，复用原EnterpriseAccess事务、Create定义、模板适用性、来源选择、ScopeSnapshotStore、AgentReleaseStore和AgentReleaseScopeAuthority。新任务修订摘要固定完整输入、资料范围、缺项、初始状态和版本；完整task_input_revision按原操作保存。保存流程检查当前任务创建及读取许可、责任人员资格、ACTIVE项目、核准模板、实际发布及全部依赖，建立无环lineage，保存原201响应、task.created审计与Outbox，并重新读取。历史恢复核查完整固定修订、输入、快照、任务及来源当前HISTORY许可、审计及原事件，保留原响应和任务身份。创建来源读取明确使用TASK_CREATION用途，计划维护继续使用PLAN_MAINTENANCE。

页面入口 `/projects/:projectId/tasks/new`已经连接任务列表及Spring直接入口。task-create.vue提供完整兼容模板分页和定义阅读、目标文档、发布版本、责任人员、目标、原签发版本、四种显式来源模式、独立清空及关闭网络操作；网络字段保存实际策略和检索词批准版本。task-create.ts验证完整Create与201回执，校验Location和原项目/责任人员；浏览器只保存本人、企业、项目、原键和正文摘要，完整请求通过服务器输入草稿持久保存。失败后继续原键，刷新后由用户明确读取原结果及完整草稿。恢复目标文档、固定模板标识、Agent、人员、目标、原签发版本、四种来源范围和全部网络批准字段，不构造模板核准记录。保存后重新读取原回执及当前任务，再清除原操作身份。尚未取得发布配置的默认范围时，恢复默认按钮保持不可用。OIDC允许浏览器读取Location。当前没有页面DOM、视觉、成功创建事务或部署验证。

当前验证：Java定向编译、完整计划及决定相关检查、实际Spring/MySQL/Redis接口检查通过；企业、成员、项目和任务使用已授权SIMULATION记录，没有构造专业核准。公开创建拒绝缺少字段、缺少创建许可及模板读取许可，原操作读取拒绝未知查询、旧上下文和撤权；合法当前许可下未登记操作返回200及明确空结果。拒绝请求没有TK-01业务写入，生成数据清理与临时应用关闭通过。页面严格类型及原生Schema编译/加载检查通过，OpenAPI使用原PyYAML完整读取。上述证据不证明成功创建、事务并发、异参重放、全部来源选择页面或正式业务验收。

原始证据仅引用本处：`.implementation-work/IC-12-PLAN-WRITE-20261008-TASK-CREATE-HTTP-03/receipt.json`，摘要86fac94d6b6863376123decf934b5399b57fb057219b4955159f98bbd821a333；原JUnit与output.log保留同目录。`.implementation-work/IC-12-PLAN-FRONT-20261008-TASK-CREATE-FRONT-03/receipt.json`，摘要f78a38819f71c9c2b3546ec43cd23419384ead4f4deb5609812e3d84b542ec82；两项原执行均成功终止，inputs_unchanged=true。公开入口的初始行为断言与原输出保留在`IC-12-PLAN-WRITE-20261008-TASK-CREATE-RED-01`；当前源码没有全量构建及部署。

剩余开发：C06实际发布消费者、评测决定及当前模型/能力/检索/网络依赖读取；必要输入和预算的完整准入；已发布配置的模板、资料集和人员默认值查询及页面自动带入；原签发文稿继续修订的文稿保存；成功创建、固定快照和输入保存、同键原结果、异参、并发、撤权及页面操作集成。C06的TaskCreateReleaseAdmission和TaskPlanReleaseAdmission当前均无实现，相关请求明确返回DEPENDENCY_UNAVAILABLE；不能将接口定义当作消费者完成。IMP-12保持IN_PROGRESS，任务创建尚未登记IMPLEMENTED。正式资料、客户批准和生产环境分别记录，其他已定义功能继续开发。

## 人工确认计划

BACK-TASK-PLAN-CONFIRM-01关联FRONT-TASK-PLAN-CONFIRM-01，继续本唯一记录。Given当前PLAN_PENDING任务、完整输入和同一草稿；When提交CONFIRM、当前Baseline及理由；Then确认事务建立新的task_revision、复制固定task_input_revision、建立完整CONFIRMED plan_revision及章节/问题关系，绑定实际决定，任务和计划头使用CAS更新；原输入和草稿保持固定。新计划引用原任务输入，新任务引用新确认计划，lineage保持无环。原键读取完整决定、任务输入、确认计划、审计和Outbox。

当前代码补充TaskPlanConfirmationStore及TaskPlanService.confirmation，连接同一TK-04公开接口。当前核准模板、完整条件、任务缺项、正式来源、任务/章节负责人与完成规则在写入之前检查；AgentReleaseStore及AgentReleaseScopeAuthority核查完整固定发布、PUBLISHED状态及当前实际范围。TaskPlanReleaseAdmission继续提供实际评测发布决定、当前配置与全部依赖资格、来源范围交集、检索和网络批准以及模型连接资格；证明必须覆盖完整发布清单及实际范围的版本依赖。当前C06尚未提供该消费者，实现完成之前明确返回DEPENDENCY_UNAVAILABLE，不产生任务或计划写入。该项为待开发代码，独立于客户正式验收条件。页面提供确认/退回选择，确认读取使用X-Plan-Decision=CONFIRM，完整原请求、明确恢复与保存后读取沿用当前操作机制；OIDC CORS允许该固定Header。固定计划恢复允许CONFIRMED或SUPERSEDED，保持原输入与内容摘要，不产生新执行许可。

当前验证：严格页面类型、原生Schema加载及OpenAPI Header定义通过；当前Java编译、确认请求/回执的新版本和READY约束、原计划图检查通过。真实Spring应用、MySQL和Redis检查当前身份、任务读取、原操作空结果、非法决定用途、缺少草稿、旧上下文及撤权，拒绝路径没有业务写入；固定决定31列与任务修订18列的实际查询及企业保护通过。临时应用正常关闭，全部检查已经取得终态。完整成功保存和恢复尚未运行，需要C06消费者及完整模板/草稿关系；当前证据不证明确认成功、并发CAS成功或页面完整业务完成。

当前原始记录仅引用本处：`.implementation-work/IC-12-PLAN-WRITE-20261008-PLAN-CONFIRM-HTTP-03/receipt.json`，摘要a2351af645ac063effca57d7bbcad3dc3279f64300cc4111548f8ba0b53984da，原JUnit及output.log保留同目录；`.implementation-work/IC-12-PLAN-FRONT-20261008-PLAN-CONFIRM-FRONT-03/receipt.json`，摘要006eddb2b0116f0ef6bae267132e7d66e2403d47b003c7128c5815e86e8274d6。各自inputs_unchanged=true。源码尚未部署，没有全量构建。

剩余开发：C06完整发布消费者与评测决定保存/读取；TK-01当前源码及剩余功能读取本记录的任务创建部分；完整计划保存、确认和退回的成功事务与恢复集成。企业、成员、项目和任务沿用SIMULATION授权，正式资料和客户批准独立登记。IMP-12保持IN_PROGRESS，人工确认当前尚未登记IMPLEMENTED。

## 人工退回计划

BACK-TASK-PLAN-RETURN-01依据PRD§8.4、LLD-后端TK-04及036-task-plan-relations.xml，维护本唯一记录，关联FRONT-TASK-PLAN-RETURN-01。当前实现提供POST plan-decisions的RETURN、GET plan-decision-basis及GET plan-decision-operations。TaskPlanReturnService使用真实EnterpriseAccess事务、当前task.read与plan.confirm、固定计划和历史来源读取；退回保留任务输入、任务状态和计划内容，任务头CAS增加一项修订，保存完整task_plan_decision、原operation_record、审计和Outbox，并重新读取验证。同键异参拒绝，同键原结果按照当前许可读取。CONFIRM、完整任务创建及发布准入继续原工单范围，当前不登记完整TK-04完成。

Given当前企业和任务、同一固定草稿及当前人工计划许可；When提交完整依据与退回理由；Then保留固定内容，写入唯一决定和原回执。原任务修订、计划头、内容摘要、输入及范围摘要、缺项摘要、授权水位改变时拒绝新决定；并发旧任务头不能再次写入。恢复只读取本人原操作并核对完整决定、回执、审计和事件，当前撤权继续拒绝。NEEDS_MATERIALS与PLAN_PENDING保持原状态，退回不授予运行许可。

当前TaskPlanDecisionProtocol保存Baseline、Request、Receipt、完整31列Row及固定任务修订；TaskPlanDecisionSchema使用原Jackson/networknt及RFC8785，元数据身份绑定自身Schema、TaskInput和Permission身份，不修改共享Catalogue字节。页面task-plan-return.vue连接当前依据、理由提交、明确读取原结果、同参继续及未登记操作重新填写；确认行为读取本记录人工确认节。浏览器仅保存本人/企业/项目/任务、幂等键、修订与摘要，完整理由和依据保留于内存；切换、隐藏和离线清除内存并保留原操作身份。保存后重新读取原决定，检查过程无轮询。

当前验证：后端输入、完整决定查询映射和企业保护通过；页面严格类型及原生Schema加载通过。真实Spring应用、MySQL和Redis的登录、当前任务读取、本人原操作空结果、缺少草稿、无效输入、禁止查询参数、旧上下文和撤销权限通过；拒绝路径保留原任务修订与状态，没有新增决定或TK-04操作。错误响应按定义返回PLAN_STALE及计划维护错误，任务接口使用对应403/409/422。身份清单绑定当前Catalogue，页面提交前核验当前企业、上下文与授权水位。

当前共同代码与RETURN拒绝路径的原始记录引用本记录人工确认节，不重复保存当前检查结果。

剩余问题：合法退回事务的完整保存、同键重读、冲突和页面操作尚未验证，需要完整固定草稿模板及对应内容关系；当前SIMULATION任务仅提供不完整DRAFT模板元数据，不能据此执行合法计划读取。CONFIRM的完整准入、新任务输入及确认计划事务和页面、TK-01创建接口及页面继续开发。企业、成员、项目和任务沿用SIMULATION授权；资料与专业批准保持各自来源。当前新增代码未部署，IMP-12继续IN_PROGRESS。客户批准、正式资料及生产环境独立登记；代码和必要集成通过后登记IMPLEMENTED，完整验收通过后登记ACCEPTED。

## 当前业务开发批次

本工单当前集中开发任务创建、计划编辑和人工决定。已有AgentReleaseStore、AgentReleaseScopeAuthority、ScopeSnapshotStore和TaskPlanService继续连接公开接口、保存后读取和页面；RETURN当前范围读取本记录顶部。剩余代码为完整TK-01创建、TK-04 CONFIRM及对应页面，以及当前发布准入的完整依赖和评测决定。正式业务验收独立保留，实施安排读取实施任务计划§6.4。

## Agent当前发布范围准入

BACK-AGENT-RELEASE-SCOPE-01依据LLD-Agent§6.3、LLD-后端§5.6、PRD AC-03/AC-04/AC-07及CP-03/TK-01，继续本唯一记录。已检索固定发布读取、ReleaseScope完整定义、release_scope、成员岗位来源、接受邀请和当前权限处理。目标为核查已批准完整范围、当前企业/项目/人群/资料分类和岗位真实来源，禁止范围扩大；新任务采用继续需要实际评测决定、全部当前依赖及运行准入。

Given固定发布清单及当前企业主体；When取得新任务或运行所需发布范围；Then只接受同一固定清单内的当前ACTIVE范围，核对版本登记、完整范围摘要和Schema身份。成员模式核对当前user_id；岗位模式核对实际接受邀请、原岗位版本及当前有效的对应动作许可。项目、分类、岗位失效或Scope变更立即拒绝；重新核验取得同一原范围和当前许可。完整IMP-12继续IN_PROGRESS。

AgentReleaseScopeAuthority.require已经提供ENTERPRISE/PROJECTS及MEMBERS/ROLES/MEMBERS_OR_ROLES完整范围核查。当前ACTIVE Scope内容必须逐项等于原发布清单，具有相同Scope摘要、版本登记、Schema身份和固定元数据。原项目必须ACTIVE，当前主体须另外具备目标动作许可；新任务使用PROJECT目标，计划确认和启动运行使用TASK目标。岗位来源读取member_role_grant、原接受邀请、原岗位定义和当前对应动作grant，核对同一企业、完整条件、原依据与有效期；使用原PermissionAuthority核查当前身份、用途及资源范围。明确成员和岗位均不能扩大发布分类及项目范围。全部候选使用明确容量，超限直接CONTEXT_LIMIT；requireUnchanged重新取得原固定发布和全部当前范围及许可。

| 文件与identifier | 当前职责及调用方 |
| --- | --- |
| AgentReleaseScopeAuthority.require/VerifiedScope/requireUnchanged | 当前完整范围及岗位来源核查，结果仅由本组件构造 |
| AgentReleaseManifestSchema.bindScope | 同一原发布Schema的完整ReleaseScope绑定，未增加或修改共享Schema字节 |
| AgentReleaseStoredProtocol.ScopeRow/RoleOrigin；AgentReleaseMapper.scopes/roleOrigins | Scope完整17列及实际岗位来源4列，当前主体和企业保护 |
| AgentReleaseStoreTest；check_agent_release_manifest.py --store-only | 实际SQL完整列、拒绝读取及实际资料不能声明Scope的定向检查 |

有效Red引用原57658的currentReleaseScopeRequiresActualAudienceAndScopeQueries及固定读取映射断言，两项因缺失范围语句失败；原回执`.implementation-work/IC-12-AGENT-MANIFEST-20261008-RELEASE-SCOPE-RED-01/receipt.json`摘要07ff49d5fd607e0331a95d4d8f03a97e0d4db5f3e22342c843ef1b3a23a2cf48。原报告中的数据库连接错误不作为业务Red证据。当前Green原62157终态0，命令`.lld-data-work/venv/bin/python scripts/check_agent_release_manifest.py --execution 20261008-RELEASE-SCOPE-GREEN-02 --store-only`，四项通过，错误/失败/跳过零项；Maven40.335秒、JUnit8.801秒，回执af44330b4da968ecd642259f8a5e9da7355313d130bcc879be4d0b1735dd7c51，inputs_unchanged=true。原JUnit摘要bd0a5b3980975bd2871c74f88af7927b03fbaf5a03d4ebdda3f701acd6c9a4a2，十三份实际class独立保存于同一目录classes。实际MySQL五组完整列和无企业事务拒绝、现有平台账户不能取得业务发布或岗位来源、实际参考资料不能声明发布范围通过；没有新增业务数据或模拟服务。

当前Scope源码摘要189d3b925b2d8a85c961989f661e57533171fccd2e71fe8e5d64491560d308f3、完整协议源码摘要61b69fb95c4774806c487d159d86016bb7874ec40542724b078590af32919d6b、Mapper XML摘要3a8fe4a0065d409d2044d4361d9a84f9a657ba8b5f38afde4aa297e632492283。CURRENT，last_verified=2026-10-08仅适用编译、实际查询/拒绝及结构绑定；合法发布成员、岗位成功、撤权及完整任务采用尚未验收。该组件不判定评测通过或当前模型、连接和秘密可用；当前启用、实际发布决定、完整依赖与任务创建仍是完整消费者的必需检查，全部42项工单继续原范围。所有当前检查已结束，没有全量构建或部署。

## Agent固定发布记录读取

BACK-AGENT-RELEASE-STORE-01依据LLD-Agent§6.3、LLD-后端§3及CP-03/TK-01，继续本唯一记录。已核查AgentReleaseManifestSchema、发布及Agent/评测完整DDL、ArtifactRegistryMapper、ResourceReadAuthority和TenantScope。固定发布记录读取负责完整清单、原摘要、Agent和评测固定引用、制品登记及当前读取许可；任务采用还需要实际发布决定、当前启用状态、发布范围和完整依赖资格，不能通过本读取组件取得执行许可。

Given真实持久化发布及当前读取许可；When读取固定release_id；Then完整清单按同一发布Schema绑定，核对发布/版本/制品摘要、Agent和评测身份，保留所有原字段，当前许可变化拒绝。平台账户身份不能代替业务发布或评测记录，未建立企业事务的Mapper读取立即拒绝。开发期间使用实际MySQL和当前平台账户进行只读检查，不创建业务批准或评测记录；合法完整发布读取验收仍待对应实际记录。

AgentReleaseStore.read已经提供完整固定元数据读取，Stored构造函数由本组件维护，调用方不能构造读取结果代替当前数据库核验。read重新绑定原完整清单；release、Agent及evaluation分别核对企业、版本登记、归属、摘要和元数据。清单与Agent精确引用和完整限制一致，发布制品ObjectRef及登记摘要核对，当前configuration.read/evaluation.read/artifact.read分别取得实际许可。requireUnchanged重新读取全部记录与许可。该组件没有公开任务准入、模型调用或评测通过判定；历史DISABLED Agent可进行获准固定读取，实际新任务采用继续核验当前启用状态。

| 文件与identifier | 当前职责及调用方 |
| --- | --- |
| AgentReleaseStore.read/Stored/requireUnchanged/manifestSha256 | 固定发布元数据读取和重新核验，供C06/任务准入消费者使用 |
| AgentReleaseStoredProtocol.ReleaseRow/AgentRow/EvaluationRow | 保存完整发布、Agent及评测数据库字段 |
| AgentReleaseMapper；medical-mappers/AgentReleaseMapper.xml；TenantScope | 三项完整查询及当前主体/企业保护；发布18列、Agent19列、评测24列 |
| AgentReleaseStoreTest；scripts/check_agent_release_manifest.py --store-only | 实际MySQL字段、缺失业务身份和受保护读取检查 |

有效Red原63443终态1，两项缺失固定读取断言失败，错误/跳过零项；回执`.implementation-work/IC-12-AGENT-MANIFEST-20261008-RELEASE-STORE-RED-01/receipt.json`摘要f7e6423a8ca6fa8ff154ec9717e5084f25f4733382c6d9896be9acc5b7f3db53。当前Green原93717终态0，两项通过，错误/失败/跳过零项；命令`.lld-data-work/venv/bin/python scripts/check_agent_release_manifest.py --execution 20261008-RELEASE-STORE-GREEN-02 --store-only`，Maven25.278秒、JUnit3.554秒，回执`.implementation-work/IC-12-AGENT-MANIFEST-20261008-RELEASE-STORE-GREEN-02/receipt.json`摘要28d8eae02b1c0e347c04f99bca920036f6c010d9f634f0f1d9b57d301cc5d2b6，inputs_unchanged=true；原JUnit独立保存。实际查询使用现有平台账户身份作为查询目标，没有新增企业、Agent发布或评测数据；三组完整列和字段顺序、当前平台账户未取得业务发布、无企业事务拒绝均通过。

原固定读取Green-02的AgentReleaseStore源码摘要4ccf5049317701e1513a2063fe467895a7496dce1501d01fd8f0423fd2c5621f、完整协议源码摘要64f7682aae015581180fe4ab5bb6d89464bb6ebbcc9593789da002d1b3fdcf6c、Mapper XML摘要1b4300b7dadf543f9c0e8a7ccefe57ea2d77f1b7a25e702999339d8a501ee436。七份原class独立保存于对应Green目录classes，AgentReleaseStore.class摘要abb58c2c5f9d96f34df17605a480d4518d1bdb2aad9c332b647b6caafe75e3cd。当前完整协议与Mapper已经包含Scope及岗位来源字段，当前证据采用本记录的RELEASE-SCOPE-GREEN-02；原固定读取结果保持其原输入范围。

读取Schema身份为RFC8785的schema_version、agent-release.schema.json原SHA及permission_manifest元数据摘要，当前绑定器与后续发布写入必须使用同一身份。原Agent及评测内容Schema解释、当前完整依赖、发布范围、实际评测决定及新任务采用仍需后续完整业务服务完成。当前状态IN_PROGRESS，last_verified=2026-10-08仅适用实际源码编译、Mapper和只读拒绝范围，合法完整固定发布读取尚未验收；完整42项工单和全部后续需求继续原范围。没有执行全量构建或部署。

## 计划草稿公开保存与重读

BACK-TASK-PLAN-WRITE-01继续本唯一记录。依据TK-03、PRD AC-07/AC-08/AC-18及LLD-后端任务内容/完整关系/CAS/幂等规定，核查TaskController、TaskService、TaskPlanWriteMapper、TaskPlanRelationStore、TaskInputSchema、TaskPlanPolicy、TemplateApplicabilityService、SourceReadAuthority和PermissionMapper。本轮补齐公开计划保存、固定版本读取、当前任务头与原草稿检查、完整关系保存和事务审计/Outbox。Agent当前发布准入和TK-01完整创建继续原责任，人工确认/退回另行实现，全部IMP-12状态保持IN_PROGRESS。

Given当前主体能够维护任务且固定输入、模板、Profile、正式来源、责任人员和规则有效；When按expected_revision与base_version保存完整PlanCandidate；Then保存完整章节和问题关系，任务头与计划头使用CAS更新，原操作保存相同请求及响应，当前许可变化立即拒绝。同键请求正文改变拒绝；同键重读重新核查当前权限及保存版本，不创建第二份计划。固定版本读取返回原完整内容及实际维护头，负责人空值和待确认条件在草稿中保留，循环立即拒绝。

当前TaskPlanService提供公开保存与固定读取，完整输入、来源、模板、规则和责任人员检查、全部关系、两项CAS、同步审计与Outbox参加当前企业事务。有效Red原95018终态1，缺失公开保存入口；回执摘要d1a02816ac8056cd1524c42cafa7ad65795cdacf18f16fec8c8c292ce26743be。Green原26847终态0，42项通过，错误/失败/跳过为零；命令`scripts/check_task_plan_write.py --execution 20261008-PLAN-WRITE-GREEN-02`，回执`.implementation-work/IC-12-PLAN-WRITE-20261008-PLAN-WRITE-GREEN-02/receipt.json`摘要0f42d93b13c3d78a5e08d36817f68937c00b8675b26083cd5b1e2e7fcaef3761，inputs_unchanged=true。原JUnit独立保存，实际MySQL检查原ownerGrants全部列、未登记主体无许可及受保护Mapper拒绝；完整合法业务保存尚未验收。

共享语言执行26594终态0，Python十二项及TypeScript严格类型/三十项通过，回执`.implementation-work/IC-03-SHARED-LANGUAGES-20261008-PLAN-VIEW-SHARED-01/receipt.json`摘要93ad416dc6c58a19a4490c19710184f321a6369b703546411d7e0a74f46496d2，inputs_unchanged=true。当前task-plan-view的唯一View定义完整引用PlanCandidate；共享集合304项定义、317项发布引用。旧证据保留其原源码范围；整个Catalogue新增项影响持久Schema身份，历史记录兼容检查继续必需，不放宽内容验证。

当前维护计划读取和原保存操作读取进入同一服务及Controller。维护读取在没有计划时返回data=null和实际任务头修订；原操作仅查当前本人、当前企业和TK-03幂等身份，返回已保存原响应，重新读取对应固定版本及当前许可，不产生第二项写入。页面刷新、原请求恢复和完整章节/问题编辑使用同一固定版本路径。有效Red原24968终态1，当前维护读取缺失；回执摘要c8695f18dbd5c6b5a0043f6ee09f6b8109f00803fe6b37b1348c68970cde710e。相关入口代码继续定向验证，完整业务页面验收、任务初次创建与人工决定保持未完成。

## 计划维护页面

FRONT-TASK-PLAN-WRITE-01继续本主记录，依据TK-03和FRTE-07，核查原task-detail.vue、route-read、共享Ajv、Axios、原操作查询及TemplateContent。task-plan-editor.vue已经连接公开计划读取、编辑依据、保存、固定版本重新读取和原保存恢复。全部章节与研究问题保留目录归属、目标、负责人空值、处理方法、完成规则和两类依赖；顺序可以调整，待确认条件独立保留。初次人工计划按照实际完整模板目录建立页面草稿，新增UUID只作为实际用户新章节身份，不建立已保存或已确认业务记录。历史读取不自动取得当前编辑依据，当前核准及正式来源只在用户请求编辑时核查。

Given页面已取得当前任务与许可；When读取维护计划、取得当前编辑依据并保存全部字段；Then请求绑定同一任务头修订和维护基准，保存后按固定plan_revision重新读取完整内容。受限正文和理由只保存于页面内存，sessionStorage仅保存本人、企业、任务、原请求身份、版本和摘要。刷新后由原plan-operations取得实际结果；未登记结果可以通过再次核查和相同任务修订结束浏览器请求身份。所有恢复动作由用户明确触发，无轮询。企业、页面、身份、离线或隐藏变化停止旧读取并清除页面内容，旧结果不能覆盖新任务。

| 文件及identifier | 当前职责与使用方 |
| --- | --- |
| TaskPlanService.save/read/currentPlan/savedOperation/editBasis；TaskController | 完整计划事务、固定/维护/原操作/当前编辑依据读取 |
| TaskPlanWriteProtocol.View/Basis；task-plan-view.schema.json；contracts/catalogue.json | 完整PlanCandidate和实际TemplateContent/ConditionResult；当前305项定义、318项发布引用 |
| TaskPlanWriteMapper.ownerGrants；TaskService.fixedTask；TemplateVersionStore.historicalTarget | 当前责任人员资格、完整任务和历史模板固定读取 |
| web/src/api/medical/task-plans.ts；task-plan-write-view.schema.json；protocol.ts | 完整输入/响应、摘要、当前许可及原操作身份校验 |
| web/src/views/medical/task-plan-editor.vue；task-detail.vue；task-detail.ts | 全部计划字段与顺序、初次人工计划、保存后重读及恢复 |
| backend-design/interfaces.openapi.yaml；LLD-后端.md；LLD-前端.md | 五项公开操作的字段、Header、当前/固定版本和原操作含义 |

原53384已取得exit0终态，`npm run typecheck:medical`通过，适用该次原输入；后续页面当前编辑读取调整继续定向复验。当前未执行完整应用构建、部署、视觉检查或完整业务页面验收；人工确认/退回、TK-01完整创建及TK-05/06保持原全部责任。CURRENT，last_verified=2026-10-08仅适用当前代码核查与已保存的局部证据。

2026-10-08当前代码的BACK-TASK-PLAN-WRITE-01定向Green原18949终态0，42项通过，错误/失败/跳过为零；回执`.implementation-work/IC-12-PLAN-WRITE-20261008-PLAN-CURRENT-GREEN-01/receipt.json`摘要063de06d983ce5a035fa51af27c97bbfaa61c94da7baaa8714cfca455f533311、inputs_unchanged=true。公开保存、维护读取、固定读取、编辑依据和原操作恢复进入当前编译；原JUnit及相关class独立保存。共享原3671终态0，Python十二项、TypeScript严格类型/三十项通过；回执`.implementation-work/IC-03-SHARED-LANGUAGES-20261008-PLAN-BASIS-SHARED-01/receipt.json`摘要0f775144f53cb43d577b7b2c801c2d2f7ae7557a8536308dc6042ef6f68b7897、inputs_unchanged=true，当前305项定义/318项发布引用。

FRONT-TASK-PLAN-WRITE-01当前Green原3600终态0，命令`.lld-data-work/venv/bin/python scripts/check_task_plan_frontend.py --execution 20261008-PLAN-FRONT-GREEN-03`。完整medical strict类型通过，实际Vite runnerImport加载当前共享Ajv并核查五项必需字段/空值拒绝；Vite原生环境完成关闭，检查正常退出。回执`.implementation-work/IC-12-PLAN-FRONT-20261008-PLAN-FRONT-GREEN-03/receipt.json`摘要ac3cac6a873ea28e815cc987d88c8a05246a17511683f7d1d68c1bf3eafa95b0、inputs_unchanged=true。Schema身份6b7938f35b68ea79711eaa1bb9c27ac57d1786865e546404f6d974c9d50dcf61；未使用HTTP替代服务或模型结果。当前全部检查已经退出，无待观察句柄；完整合法保存/刷新/原操作恢复业务尚未验收，新代码未部署，全部父工单状态保持原来源。

## 来源快照保存事务

BACK-SCOPE-SNAPSHOT-STORE-01继续本记录，依据PRD AC-01/AC-27、LLD-后端TK-01及data-design/core.sql。已核查ScopeSelectionAuthority、SourceReadAuthority、TaskInputSchema、ScopeSnapshotSchema、TaskMapper、TenantScope及现有版本引用。目标是在当前企业事务内保存完整固定快照、成员、版本登记和全部引用，并在同一事务重新读取核验；空显式范围保持空集合。当前检验入口为scripts/check_scope_snapshot.py，公开任务创建仍待Agent当前发布准入及完整输入事务贯通，IMP-12保持IN_PROGRESS。

Given当前项目许可、完整固定来源选择及原授权水位；When任务服务保存范围；Then重新校验输入和当前来源，按source_version稳定顺序保存同一完整成员，重新读取JSON与关联表，核对摘要及版本登记，当前权限变化立即拒绝。成员、索引、核准、适用、权利、资料集、Profile及网络批准的固定版本进入引用关系。全部写入参加调用方当前企业事务，失败立即抛出，事务整体取消。检查仅使用获准SIMULATION企业与项目及真实MySQL；不生成来源批准、模型结果或Agent发布批准。

ScopeSnapshotStore.save已经提供当前输入重新绑定、task.create许可、当前ACTIVE项目/Profile、完整来源选择、排序成员、当前授权水位、快照结构、全部依赖容量、版本登记、成员及引用保存、数据库时间和保存后完整读取。ScopeSnapshotWriteMapper五项语句使用原企业拦截器，TenantScope对全部语句要求同一当前userId；没有客户端tenant_id写入。依赖引用包括Profile、资料集、成员的六项固定版本及显式网络Policy/Query版本。网络外发批准和Agent采用仍由完整任务准入负责，本组件不启动模型或联网请求。

| 文件与identifier | 当前职责与调用方 |
| --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ScopeSnapshotStore.java：save/Stored | 任务创建事务保存范围，完整重新读取和当前状态核验 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/ScopeSnapshotWriteMapper.java；src/main/resources/medical-mappers/ScopeSnapshotWriteMapper.xml | 版本、快照、成员、引用写入及完整父版本读取 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TenantScope.java | ScopeSnapshotWriteMapper全部语句的当前主体参数核查 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/ScopeSnapshotStoreTest.java；scripts/check_scope_snapshot.py | 缺失业务写入的Red、实际映射拒绝、MySQL空范围保存重读、事务取消及生成数据清理 |

2026-10-08有效Red执行9603终态1：一项业务断言失败，错误及跳过为零，缺少来源快照完整写入；回执`.implementation-work/IC-12-SNAPSHOT-20261008-SNAPSHOT-RED-01/receipt.json`摘要fa965a4d3ce97fb5971a9379a7c5775639fc064ecacbb5ab544e28a2a4050238。Green执行69102终态0，命令`.lld-data-work/venv/bin/python scripts/check_scope_snapshot.py --execution 20261008-SNAPSHOT-GREEN-02`，15项通过，错误/失败/跳过为零，16.853秒。包括ScopeSnapshotStoreTest两项、TenantAccessTest六项和TaskInputSchemaTest七项；实际MySQL保存空范围、版本及原水位，重新读取JSON与摘要，成员保持空集合；不同主体写入拒绝，事务取消后生成企业数量为零。回执`.implementation-work/IC-12-SNAPSHOT-20261008-SNAPSHOT-GREEN-02/receipt.json`摘要950fc58b6006840942a699ae247341003eee1267ee87ab048d7ef8ef3e9ae1e2，inputs_unchanged=true；本次JUnit具有独立副本，快照JUnit摘要bd75ee42e8191f0010ba33379cb55f8aa033b6a3c6db79948dfbfab5b474fd73。

当前未提交ScopeSnapshotStore源码摘要d6e1e21019e859e851c10eb1cc79fb55ecda018c5f252af9abffd30b830acece、Mapper源码摘要0ee3b97934332843a98f3e686ba1faa68ccf5e54952f4ca7072b7e1b874734f2、XML摘要b233b57600d22e62b2a7ad27957e7e03b879767756cefc5523a83e3cc0aed676；实际编译ScopeSnapshotStore.class摘要f8ce32c1a8c6f0e9af59b5e96b80f1859928cec48b35a5ab721cd1d177cac2f6。CURRENT，last_verified=2026-10-08，适用范围为当前代码编译、访问控制和实际空范围保存。非空正式来源的完整service保存、Agent当前发布准入、公开TK-01及任务页面写入尚未验收；原全部需求和IMP-12的IN_PROGRESS保持。本批没有执行全量构建或部署，其他功能继续开发。

## Agent发布清单与任务准入依赖

BACK-AGENT-RELEASE-MANIFEST-01沿用本工单唯一主记录，依据LLD-Agent§6.3、LLD-后端§5.6、PRD§8.4及TK-01—06，状态来源为实施任务计划IMP-12的IN_PROGRESS。已读取本记录、索引、当前TaskInputSchema/SchemaCatalogue、原交接及完整发布清单定义，公开组件已经提供并编译。用户要求优先全部开发，本项开发为任务创建和后续运行提供完整清单表示；真实评测、人工发布批准、当前依赖/权利、持久化和公开页面继续各自完整责任，不以清单格式通过登记发布成功。

Given调用方提供全部固定版本、实际制品摘要、当前共享Schema、限制、人工检查点、评测引用及明确范围；When绑定完整AgentReleaseManifest；Then保存全部输入和RFC8785摘要，拒绝缺项、未知字段、未登记Schema、冲突版本、插件缺失或循环、限制不一致和不完整AS-01—15/C07/C12。结构验证直接使用networknt、Jackson、SchemaCatalogue、WireValues及JGraphT；不执行网络加载或模型请求，不生成发布批准。当前技术检查使用实际项目文件字节及既有共享Schema，技术UUID只标识检查输入，没有创建Agent发布或评测记录。

| 文件及identifier | 当前职责及使用方 |
| --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AgentReleaseProtocol.java | 完整Manifest、RuntimeManifest、ReleaseScope及不可变列表；任务准入与C06/C12共享 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/AgentReleaseManifestSchema.java | 完整结构、固定Schema摘要、依赖版本、制品身份、插件DAG与限制核查 |
| business/medical-persistence/src/main/resources/agent-release.schema.json；contracts/catalogue.json | 完整封闭Schema与唯一发布引用，classpath和project消费者相同 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/AgentReleaseManifestSchemaTest.java；scripts/check_agent_release_manifest.py | 针对性BDD/TDD及原JUnit证据；完整构建集中执行 |

当前源码未提交，使用原检查回执的文件SHA-256绑定版本。有效Red为20261008-AGENT-MANIFEST-RED-02，原81040已取得exit1终态，一项实际完整清单缺失断言失败，错误/跳过零项；回执f89753b735b6735ea2b846a9011f50e17da62cfece86fc174d1758585376a500，inputs_unchanged=true。Green为20261008-AGENT-MANIFEST-GREEN-02，原78105终态exit0；28项完整清单与19项原共享绑定检查通过，失败/错误/跳过零项，回执367a9146649b3d0f30fb594c2fb7247fa092f4522b43446591746fa26c7e95b1，inputs_unchanged=true。当前公共协议和绑定器的18份class、全部四份JUnit及原日志独立保存。检查包含全部20项必需字段、三种人群与两种项目范围、未知/重复/尾随JSON、错误UTF8、插件循环/未知依赖、版本冲突、角色与工具双向Schema、全部15项规则与两类适配器、U64/金额及原时限关系。

共享语言原90855终态exit0，Python12项、TypeScript严格类型及30项检查通过，回执7f8867df5cc02c15ef190f7c9e4e7c138984f36205530e993b7e41544f634f86，inputs_unchanged=true。新增25项封闭定义后共享定义合计303项，包含根文档的发布引用315项；所有原定义保持，三个消费者登记同一实际文件摘要a73b5bca37580065fccf7ac23340dd276fcf3fc32f313ccceed2de7c5813008a。独立证明AGENT-MANIFEST-20261008-evidence.json摘要955bb9d09daa36f0b191cf8f62bb73b8059c427db72cba03548fb33dc64b5273核验原Red、当前Green/共享输入及原始字节，之后仅更新开发记录和执行说明。没有新增迁移、秘密、模型调用或业务发布。

本清单结构组件已实现，完整IMP-12仍为IN_PROGRESS；公开Agent保存/发布、当前评测/依赖/权利核查、任务创建及人工决定均未验收。完整任务写入、来源快照、计划保存/确认/修改、幂等/CAS、同步审计/Outbox和后续业务继续保持。TaskInputSchema及其他现有输入绑定包含原Catalogue身份，后续当前发布准入和完整任务事务必须核验固定Schema身份及历史任务兼容，不以当前清单结构验证替代这些消费者的业务检查。

2026-10-08用户要求获授权的并行聊天完成当前结构核验后暂停，由本聊天继续完整开发。交接已读取`.implementation-work/task-write-development/handover-20261008.md`；本聊天接管任务创建、来源快照、计划维护、完整Agent发布及当前准入，TK-01—06与全部下游要求保持。当前公开AgentReleaseProtocol、AgentReleaseManifestSchema及agent-release.schema.json已经提供完整表示和针对性验证；实际发布及采用继续独立业务要求。

当前036实际迁移及完整备份已由[IMP-03](IMP-03.md)维护，实际登记249项，原237项保持。独立只读完整结构核验为`check_database_structure.py --execution 20261008-ACTUAL-03`，原88064已取得exit0终态，inputs_unchanged=true；回执`.implementation-work/IC-12-PLAN-STRUCTURE-20261008-ACTUAL-03/receipt.json`，摘要4d4dc8f4ae9eb572f52ab5758bdfd853cb78a33d928211116395a01c27f434e2。十二张相关表155个字段、34项主键及唯一键、35项外键和13项启用Check均符合迁移定义，完整expected/actual/result与日志已经保存；字段顺序、类型、可空、默认值、字符集、排序规则及生成表达式逐项比较。此结果证明实际结构，公开任务写入和客户成功仍为false。当前RUN-TOKEN-UPLOAD-TASK-PLAN-BUILD-FINAL-02原8916完整448项Java和前端检查全部通过，inputs_unchanged=true；本地新制品验证由[IMP-06](IMP-06.md)继续维护。

## 任务指定模板准入

BACK-TASK-TEMPLATE-TARGET-01沿用IMP-12及TK-01、TK-03、TK-04完整范围。TemplateApplicabilityService.requireTarget在当前EnterpriseAccess事务中读取固定项目属性、当前已核准模板及完整依赖，复用TP-05的同一evaluate路径计算模板、全部节点、字段和附件条件。文档类型必须相同，明确NOT_APPLICABLE立即拒绝，UNKNOWN完整保留条件及缺项。结果保存当前Admission、用途、固定属性、完整TemplateView、实际模板许可和规范依赖授权内容；requireUnchanged在同一Admission中重新执行完整准入并逐项比较。该入口不授予任务创建或计划确认权限，公开写入继续需要完整Agent发布、来源范围及事务核查。

Given当前实际MySQL、本机原账户及原迁移记录；When使用JUnit Platform1.12.2在独立classes中重新编译TemplateApplicabilityService和原TemplateApplicabilityTest并执行全部检查；Then六项原表达式、三态缺项、封闭Schema、查询字段及企业访问检查通过。实际执行为`check_drafts.py --execution PLAN-TEMPLATE-DATABASE-01 --applicability`，原74444终态exit_code=0，JUnit用时10.864秒，六项成功且失败、跳过及中止均为零。结果`.implementation-work/task-write-development/checks/PLAN-TEMPLATE-DATABASE-01/result.json`的SHA-256为`af11cd883836f82d3edc12f7c924a2c017a2785847b41065e7c50056fa32f483`，inputs_unchanged=true；同目录保存实际class、四份日志及applicability.json，三十七项MyBatis方法和Liquibase定义检查同时通过。没有使用共享Maven产物输出目录，没有创建业务数据；完整公开指定模板成功准入及任务写入尚未验收。customer_success_verified=false，visual=NOT_RUN，last_verified=2026-10-08。

## 计划关系与版本保存

BACK-TASK-PLAN-RELATIONS-01依据PRD§8.4、§8.9和TK-01—06，沿用唯一IMP-12，保持IN_PROGRESS。当前公共源码包含TaskPlanProjection、TaskPlanRelationStore、TaskPlanWriteProtocol、TaskPlanRelationMapper及TaskPlanWriteMapper；公开计划保存与完整关系已经连接TaskPlanService，公开任务创建和人工计划决定继续完整领域服务要求。当前实施不替代来源与能力准入、完整业务保存、真实人工决定或客户验收。

Given同一企业与项目中的实际任务、固定完整计划内容、来源快照及当前动作许可；When在原事务中保存并重新读取计划关系；Then全部章节、每章全部节点、负责人空值、方法、规则、顺序、研究问题、稳定问题身份及两类依赖与原PlanCandidate一致。TaskPlanProjection恢复完整候选并要求与输入相等；TaskPlanRelationStore核查原计划、Schema、版本登记与快照摘要，写入完整关系后逐项比较数据和创建元数据，并重新核查当前授权。任务中的完整question_key与实际UUID逐项对应，摘要索引命中后仍比较完整键值。`plan.write`来源为TK-03计划维护动作的明确命名，确认继续使用原`plan.confirm`。

追加迁移`036-task-plan-relations.xml`使用238—249编号，登记完整章节节点、章节规则、计划问题版本、问题节点、问题规则、问题依赖、当前维护计划头及人工决定八张表。问题节点同时引用本计划的问题章节和本章节节点；依赖两端必须属于同一计划。原章节负责人允许为空，方法和顺序明确保存；原章节、依赖或问题记录存在时，相关结构变更立即终止，不推断历史值。研究问题首次登记依据保留，各计划的问题文本与章节归属按版本保存。

TaskPlanWriteMapper提供原幂等操作、新计划及完整内容、当前计划头CAS、确认所需新任务修订与完整输入复制、当前任务头CAS、实际决定及版本引用。确认决定完整关联目标草稿、原摘要、原任务修订、新任务修订与新确认计划；原任务修订及原计划内容保持不变。操作、决定、审计及事件必须由后续完整领域服务在同一事务中调用和重读。TenantScope已为两份新增Mapper登记当前主体参数核查，企业SQL处理继续使用原ProtectedTenantInterceptor。

公共离线编译为`scripts/check_task_plan_compile.py --execution 20261008-PUBLIC-01`，原执行84766已终态exit_code=0、inputs_unchanged=true，Maven test-compile用时33.788秒。回执`.implementation-work/IC-12-PLAN-COMPILE-20261008-PUBLIC-01/receipt.json`的SHA-256为`98e9a00e57c77d04ee200fbfd5298d376226d906b333413ddb88e508ec9eefb6`，同目录compiled.json及classes保存二十一份实际编译制品。

当前迁移覆盖执行`20261008-TASK-PLAN-RELATIONS-COVERAGE-04`原13070已终态exit_code=0、inputs_unchanged=true；十七项检查16.805秒通过，198张物理表中161张为业务表，全部XML的CREATE与ALTER使用JSQLParser和SQLGlot原生结构参与约束核查。回执`.implementation-work/IC-03-MIGRATION-COVERAGE-20261008-TASK-PLAN-RELATIONS-COVERAGE-04/receipt.json`的SHA-256为`2e46e11c35d4da88c62fc397591db046984911719440056f60e19f7e34a6da0c`。公共路径原生校验`PLAN-RELATIONS-NATIVE-05`原38968已终态exit_code=0，Java17编译、MyBatis三十七项方法登记及Liquibase严格定义校验通过，inputs_unchanged=true；结果`.implementation-work/task-write-development/checks/PLAN-RELATIONS-NATIVE-05/result.json`的SHA-256为`b971761b2c43a0ba0e66994415b4a889af5e2ca3aedef6a751a66fc7a2d36517`。

原实际数据库前置核查为`scripts/check_task_plan_database.py --execution 20261008-PRECONDITIONS-01`，原42996已终态exit_code=0、inputs_unchanged=true；回执`.implementation-work/IC-12-PLAN-DATABASE-20261008-PRECONDITIONS-01/receipt.json`的SHA-256为`05a46135f47e6a3ca185813da382764619d38efa518e43110cd5788922a9c2ef`。database.json保存迁移前的237项记录、原表字段及完整约束，原六张业务表计数均为零，前置核查只执行读取。当前迁移及完整物理结构采用本文当前249项实际证据，各项回执分别保持自身原输入范围。真实写入与重读、公开接口、幂等及撤权业务验收继续未完成；customer_success_verified=false，visual=NOT_RUN。本节CURRENT，last_verified=2026-10-08。

## 任务当前状态页面

FRONT-TASK-DETAIL-01关联PRD§8.4、LLD-前端FRTE-07/S07与TK-02，使用已经提供的`GET /api/tasks/{taskId}`。当前API为`web/src/api/medical/task-detail.ts`，页面为`web/src/views/medical/task-detail.vue`，项目任务列表按实际task.read范围提供“读取当前状态”入口。Router登记完整任务config路径，原Ajv登记共享TaskDetail，原Axios使用有界JSON读取TK-02。唯一工单仍为IMP-12，状态继续读取实施任务计划；本节CURRENT，last_verified=2026-10-08。项目任务创建、完整来源快照保存、计划修订与确认、启动运行及客户业务验收保持原范围。

Given当前已认证主体、明确task.read范围及项目中的实际任务；When从列表进入`/projects/{projectId}/tasks/{taskId}/config`或重新读取页面；Then通过TK-02重新核查当前任务修订、完整缺项、已确认计划和固定来源快照，并只显示当前获授权文稿、运行与待办。企业、项目或单个任务许可按现有ResourceReadAuthority范围处理，任务响应必须与路由项目和任务一致；读取结果不授予写入、计划确认或启动许可。

页面沿用原Layout、Router、Pinia、request、Ajv和Element Plus。完整缺项保留节点、字段、原因及当前允许动作；关联对象使用实际标识。身份、路由、页面隐藏、断网和离开时清理当前结果并终止读取，过期异步结果不进入页面。`route-read.ts`通过原Vue Router组件离开和更新守卫管理读取资格，导航期间停止旧页面新读取，afterEach仅恢复当前页面；页面卸载移除监听。独立检查文件为`tests/browser/task-detail-checks.js`与`scripts/check_task_detail_browser.py`，实际账户的访问限制、刷新、离开和退出清理与客户任务成功读取分别记录；当前没有新增客户记录或许可。主聊天维护Spring入口、共享索引、统一构建与部署。

独立Red回执为`.implementation-work/IC-12-TASK-DETAIL-BROWSER-20261008-PARALLEL-RED-01/command/receipt.json`，SHA-256为`7ad76a12639670bee6395f7166d8d20cbb40c519b7408582c85d7e4064b2c0fc`，exit_code=1、inputs_unchanged=true，检查实际本机账户登录后的任务页面入口。Green为`.implementation-work/IC-12-TASK-DETAIL-BROWSER-20261008-PARALLEL-GREEN-02/receipt.json`，SHA-256为`ef9b555fc75944ab31abdebf058169179c84ac91f0fce7f0f1ac13e81aeda30f`；command回执SHA-256为`ffd7fc018834e70fcb4a0529c2c28b77bd9b5db6cd0c2716dc6eaccc7e279aed`。原执行56905已终态exit_code=0、inputs_unchanged=true，8938项输入、235项产物。vue-tsc严格检查、medical生产构建、开发与生产各五项Chrome检查通过，十项实际MySQL业务表数量前后均为零，该次检查绑定原PID86534。浏览器验证实际无企业账户的读取限制、刷新、不可用动作、离开与退出清理；visual=NOT_RUN，原回执保持自身执行范围。

成功读取检查模块为`tests/browser/task-detail-simulation-checks.js`，由原`task-simulation-checks.js`调用并登记在`scripts/check_task_list.py`的capture输入。模块逐项比较当前TK-02响应中的任务与项目、固定版本、当前修订、责任人员、完整缺项和获授权关联标识，继续检查刷新、手动重读、不同项目范围和返回列表；原任务列表的六项检查保留，每个环境共十项。完整任务详情流程要求恰好四次TK-02读取和一次返回列表读取，pageerror必须为空。当前`.implementation-work/IC-12-LIST-20261008-TASK-DETAIL-SIMULATION-GREEN-02/receipt.json`使用上述PARALLEL-GREEN-02生产制品，SHA-256为`ab087ecf6dcd2e11ff5ce107002cc841e50d2cac09093e482f34b1beef997810`，command回执SHA-256为`40f09d7af00d7b3e219461876b17993d1efd088a76a936920001244276bbf11f`。原执行42760已终态exit_code=0、inputs_unchanged=true，1266项输入、249项产物，六十二项Java及开发与生产各十项Chrome检查通过，完整JUnit具有独立副本。使用原SimulationTaskData的范围由主聊天维护，customer_success_verified=false。

Spring入口采用`.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261008-TASK-DETAIL-SPRING-ENTRY-GREEN-02/receipt.json`，SHA-256为`d1eae9c8cc2fe94b0de7415aabdd28fb75c51b702286720e3e2bd6f31060e190`；command回执SHA-256为`6173f711c08b4eddcef5882f003688a715b8a85ab93af1ee464ebdb7d947e4df`。原80660已终态exit_code=0、inputs_unchanged=true，9724项输入、241项产物，三项Java、五项Chrome及231份正式静态字节通过，任务config路径直接访问与刷新通过。唯一Spring入口维护于`FrameworkFrontendConfiguration`，对应检查使用实际完整应用。三份原回执的独立复核为`.implementation-work/TASK-DETAIL-ENTRY-20261008-final-evidence.json`，SHA-256为`255d5dbb6652c4030dbdd073022170e202ac31b04fa4ff65ac13b9c921ba873a`，原88163已终态exit_code=0；原输入、产物、六十五项JUnit、三十五项浏览器检查和231份静态字节一致。当前常驻PID16562已经包含任务详情页面，448项构建及八项页面入口、任务config刷新由唯一[IMP-06](IMP-06.md)维护，组合证据为RUN-TOKEN-TASK-PLAN-20261008-release-evidence.json。后续运行Policy组件的完整构建和新部署继续由主聊天维护，原回执保持自身源码范围；完整任务创建和客户成功验收继续未完成。

2026-10-08关联SOURCE-COMPLETION-LEASE-01，当前完整400项Java、前端strict及生产构建通过，回执e67ade702483141b82417d2c4388debabef8e21f6fa16f9f76afe785fa157eae、inputs_unchanged=true。原SimulationTaskData仅生成获准SIMULATION企业、岗位、项目和任务，实际完整HTTP结束后业务数量保持零；租约测试不建立运行主体、模型结果或专业批准。当前PID61813的部署及原操作核验由唯一IMP-06维护，来源租约与完整剩余业务由唯一IMP-08维护。下方集合保持原输入范围，完整TK-01创建、来源快照、计划保存和业务采用继续原要求，IMP-12保持IN_PROGRESS。

## 2026-10-07 虚拟任务读取验收

当前关联检查采用IMP-06的CURRENT-APPLICATION-RESTORE-SHARED-02：165项Java、131项Chrome、strict类型检查和medical构建通过，任务HTTP与开发/生产各六项任务浏览器检查再次通过。管理人员通过实际页面邀请新账户，该账户接受并明确选择企业后，项目及任务读取200；原成员停用与授权记录重读由IMP-04维护。当前共享与十二项解析制品的全部字节证明为`.implementation-work/DOCX-ORDER-20261007-shared-evidence.json`，SHA-256为27f6448dda4f7ecd86d5a71ebf14bf51315d7d9e97611be8d5f9a5f6d6790cc1。完整339项Java构建、当前常驻应用及对象服务由IMP-06/05维护，八份当前回执证明为`.implementation-work/DOCX-ORDER-20261007-associated-evidence.json`，SHA-256为7f5b08d42d5a539a9cb813f45417de89b24117e916bd4d31a239713b82f2c1c0，解析顺序及安装制品由唯一IMP-08维护。完整TK-01创建、来源快照和计划保存继续按BIZ-06实施，IMP-12保持IN_PROGRESS；本节CURRENT、last_verified=2026-10-07，以下回执保留原执行范围。

原MEMBER-MANAGEMENT-SHARED-FINAL-01通过165项Java、107项Chrome、strict类型检查及medical构建，任务HTTP和开发/生产各六项浏览器检查通过；对应证明`.implementation-work/MEMBER-MANAGEMENT-20261007-final-evidence.json`只适用其原执行输入。本人接受邀请与明确企业选择后的实际任务/项目读取、当前会话、项目操作、岗位和管理页面继续由IMP-04/06维护。

执行标识 BIZ01-SIMULATION-01；本次继续维护 IMP-12，状态引用实施任务计划 §4.2。用户明确授权企业、成员岗位、项目与任务使用虚拟数据。验收记录使用 SIMULATION 标识，分类字段沿用 INTERNAL，数据保存到实际 MySQL；当前平台账户、Cookie、CSRF、Redis、企业选择接口与 TaskController 使用当前完整应用。虚拟 Agent 关联只服务于任务读取，保持 DRAFT，不登记模型评测、生成成功或专业批准。

Given 两个企业、同标识的跨企业任务、两个项目、两名责任人员、两项可读取任务及其他未授权任务；When 使用正式企业选择接口后读取任务列表、详情、过滤和固定水位分页；Then 列表只返回当前企业和项目中的授权任务，数量与分页一致，缺项动作根据当前许可返回。任务更新时间变化使原分页返回 REVISION_CONFLICT；撤销任务许可后数量减少且详情拒绝，撤销项目列表许可后整个列表拒绝。

相关位置为 `business/ruoyi-admin/src/test/java/com/ruoyi/medical/persistence/SimulationTaskData.java`、`business/ruoyi-admin/src/test/java/com/ruoyi/medical/runtime/TaskListHttpTest.java` 与 `scripts/check_task_list.py`。数据创建使用实际约束与关联表，输入和快照通过当前 Schema 绑定及 RFC 8785 摘要；关联 manifest 保存到本地文件并计算实际字节摘要。测试结束仅清理本次生成的企业与文件，并核验既有业务表数量。

当前虚拟任务成功路径已经通过实际数据库、正式 HTTP、开发环境及生产构建的 Chrome 验证。`20261007-BIZ01-SIMULATION-GREEN-05` 执行六十二项 Java 检查，失败、错误与跳过均为零；Chrome 155.0.8059.39 在开发和生产各完成六项操作检查。二十三项虚拟任务验证项目入口与同范围数量、完整任务和缺项动作、二十项第一页及三项第二页、状态与责任人员筛选、刷新、项目变更和会话失效清理。正式 HTTP 另行验证准确时间区间、跨企业同标识隔离、分页修订冲突、任务及项目撤权，当前业务表数量恢复到原值，临时应用关闭。

回执为 `.implementation-work/IC-12-LIST-20261007-BIZ01-SIMULATION-GREEN-05/command/receipt.json`，SHA-256 为 `86e0a9d6f06f4f3de63bc158a151fd09a96cfe4e82de19792d66150f80c8464f`，执行时 inputs_unchanged=true。固定 `task-list-result.json`、`task-browser-result.json` 和完整输出位于同一检查目录；原生生产构建采用 `BIZ01-SIMULATION-GREEN-04/production`，strict 类型检查和构建已通过。共享 Java 的当前 JUnit 使用 IMP-06 中最新关联回归的回执，原报告以各自执行时记录的字节为准。

共享 Schema 由 Ajv 登记原资料定位定义，完整 EvidenceRef 使用发布引用；项目属性仅在 Element Plus 实际记录插槽执行 JSON 编码，原完整数值校验保持。有效浏览器 Red 使用 `BIZ01-SIMULATION-BROWSER-03/command/receipt.json`，SHA-256 `8e84404054a7f8929e596ece4ad685312b4ff92975dd436cc87b0bd141bc5b5b`，以及 `BIZ01-SIMULATION-BROWSER-RED-05/command/receipt.json`，SHA-256 `d75013257bfd624ca4c758f67a9a75f0e1ad88810a414407880e56881b6f1d4c`；执行时均保持 inputs_unchanged=true。

本节的代码实现与运行验证指向上述结果，虚拟业务验收范围为 BIZ-01；customer_success_verified=false。完整 TK-01、来源快照保存、计划修订与确认继续按 BIZ-06 实施，IMP-12 保持 IN_PROGRESS。源码未提交，常驻应用尚未更新，没有视觉检查。文档本节 CURRENT，last_verified=2026-10-07。

2026-10-06FRONT-TASK-LIST-01使用当前未提交源码接入原Pagination、Element Plus及Axios。原任务详情和发布目录保持定义，项目动作使用project.task.read，展示数量采用相同当前授权范围。任务页面、项目详情入口和同源刷新已经编写，完整关联检查PROJECT-TASK-FRONT-GREEN-01正在执行；合法客户集合、分页和撤权业务尚未验收，IMP-12保持IN_PROGRESS。原登录与管理组件由唯一[IMP-06](IMP-06.md)维护。

## 项目任务列表页面

FRONT-TASK-LIST-01沿用本主记录，状态来源为实施任务计划IMP-12，保持IN_PROGRESS。依据PRD§8.4、FRONT-I01/BX-01、当前TaskDetail/TaskPage和用户明确的框架复用要求。已检索IMP-04/06/12、TaskService、TaskMapper、ProjectService、ProjectMapper、原Pagination及ElForm/ElTable/ElDialog；真实客户项目和许可仍等待原输入，不创建替代记录。

Given真实当前账户和已选择企业；When打开项目任务列表、过滤和分页；Then按project.task.read及每项task.read读取当前授权集合，显示真实目标、文档类型、责任人、状态、缺项、固定版本和更新时间。原Pagination和Element Plus处理列表、表单、展开及完整信息；第二页绑定原水位与过滤摘要，过滤或页大小变化重新取得第一页。当前身份变化、页面隐藏及路由变化清除旧内容和停止旧读取，异常保留具体结果。

项目详情增加project.task.read动作入口，展示数量使用与BX-01相同的当前获授权任务范围；项目决定所需内部依赖集合保持原要求。TaskPage补充项组合原TaskDetail及真实输入目标、文档类型、任务头更新时间，原共享TaskDetail和发布目录保持原定义。责任人员保留真实UUID，十一项任务状态、十三类文档及完整关联标识使用原业务定义。过滤日期从原时间组件转换为UTC，第二页绑定原水位、过滤摘要和数量；筛选及页大小变化重新读取第一页。页面身份、路由、隐藏与卸载清除旧内容并取消读取，过期响应不显示。数量超出原分页或接口范围时明确失败。

| 文件及identifier | 当前职责与关联 |
| --- | --- |
| web/src/views/medical/tasks.vue、api/medical/tasks.ts | 原表单、列表、分页和信息弹窗；完整响应及版本、数量、归属、过滤和排序核验 |
| web/src/api/medical/protocol.ts、utils/request.js | 成熟Ajv加载原共享Schema，原Axios按任务接口读取有界JSON数值 |
| web/src/router/index.js、views/medical/project.vue、FrameworkFrontendConfiguration | 任务路由、项目许可动作及后端同源页面刷新 |
| TaskService、TaskMapper、TaskProtocol、ProjectService、ProjectMapper | 已核查固定输入目标/类型、真实更新时间、同一授权查询及项目任务展示数量 |
| task-list.schema.json、projects.schema.json、OpenAPI、接口补充设计、LLD-前端 | 补充Item字段与查询参数、项目动作及页面行为；发布共享Schema保持原内容 |
| TaskReadTest、TaskListSchemaTest、ProjectPaginationTest、framework-navigation-checks、frontend-entry-checks | 实际MySQL、Schema、权限拒绝、开发/生产及同源正式构建入口 |

| 业务字段 | 查询、序列化与前端使用 | 当前约束 |
| --- | --- | --- |
| goal、document_type | task_input_revision原固定输入→VerifiedTask→Item→目标及文档类型 | 完整输入、摘要和版本通过原读取核查后返回 |
| updated_at | task_head原列→TaskProtocol.Head.updatedAt→UTC字符串→列表和信息弹窗 | DATETIME与UTC关联；按更新时间及任务标识降序 |
| project.task.read、children.tasks | 原资源许可及taskSelection→同一listScope计数→项目入口和数量 | 不授予企业或项目许可；内部治理仍使用完整依赖集合 |
| page_info及过滤 | 原PageHelper/Schema→原Axios有界数值读取→原Pagination | U64保持字符串；固定水位、摘要及授权数量一致 |

有效Red来自PROJECT-TASK-METADATA-RED-01/command/receipt.json，摘要23c48f70f7dd7ae59deb9078cab1133f086814e58830b4c7b3ca38107307dfcd，实际三项行为断言失败，错误及跳过为零。页面Red使用PROJECT-TASK-FRONT-RED-02/command/receipt.json，摘要2a9c5843a1b91a7a0b0893add0146309337fb88243b64a3e9281eb21c898949d，实际项目任务标题尚未提供，原浏览器与JUnit失败各由完整输出证明，inputs_unchanged=true。相关原始JUnit已复制到各执行目录，字节身份继续以原回执为准。当前strict类型及生产构建29.43秒通过，完整Java、原管理/账户与Chrome关联检查尚在执行。

合法客户分页、当前授权变更、完整TK-01/来源快照/计划与全部工单保持原验收范围。没有建立客户资料或替代许可，没有视觉检查，源码尚未常驻部署。文档当前实施部分CURRENT、last_verified=2026-10-06；运行结论须读取本次终态回执，较早整体输入证明按原范围为STALE。

2026-10-06原请求共享关联由唯一[IMP-06](IMP-06.md)的RUOYI-REQUEST-GREEN-03维护，当前156项Java、38组Chrome及数据库原响应检查通过。TaskListSchemaTest三项、TaskReadTest八项和正式TaskListHttpTest包含于该集合；实际任务读取应用端口51785，临时应用关闭，客户业务保持零。任务列表前端、合法客户授权集合分页、完整TK-01/来源快照/计划决定和业务成功继续原验收，IMP-12保持IN_PROGRESS；较早整体输入或被重写JUnit证明为STALE，新源码未常驻部署。

2026-10-06原个人信息共享关联由唯一[IMP-06](IMP-06.md)维护。当前RUOYI-PROFILE-GREEN-03实际156项Java、开发十六项/生产十五项Chrome和MySQL重读通过；任务列表的三项Schema、八项原读取及实际HTTP仍包含在该集合，应用端口64858，临时应用关闭。新源码未部署，原任务列表前端、合法授权集合分页、完整TK-01/计划决定及客户业务仍未验收，IMP-12保持IN_PROGRESS。

## 项目任务列表

BACK-TASK-LIST-01沿用本主记录和实施任务计划IMP-12，保持IN_PROGRESS。已检索IMP-04/06/11/12、索引、PRD§8.4、BX-01、共享PageInfo/TaskDetail、TaskService/Mapper、ResourceReadAuthority、PermissionAuthority、EnterpriseAccess及原PageHelper。列表需要项目列表许可与每项任务读取许可，total只统计当前授权任务；状态、责任人和更新时间过滤采用固定排序，后续分页绑定水位与过滤摘要。全部项目任务、来源创建、计划决定、生成及原42工单继续保持完整要求。

当前实现通过原TaskInputSchema读取补充Schema和许可条件，SchemaCatalogue的封闭加载器解析共享PageInfo及TaskDetail。TaskController统一返回非法Schema或时间参数422，实际企业准入继续由EnterpriseAccess执行；PageHelper用于授权范围内计数及分页。TaskReadTest使用实际MySQL和原DefaultParameterHandler检查列表SQL，TaskListSchemaTest读取实际本人身份、数据库时间与空表计数验证参数及共享Schema；TaskListHttpTest在完整当前应用中检查无企业403、非法参数422和匿名401。合法客户分页、许可撤销、并发与完整任务创建仍需真实业务证据，当前源码不表示IC-12验收完成。

当前关联Green由唯一IMP-06的RUOYI-PASSWORD-GREEN-03维护，156项JUnit全部通过，输入9592项、产物271项，回执摘要f55c4ff1676bc234cb34add1ae84cacdb5d2d9ac45085c0cc3995dc0dce204ab。TaskListSchemaTest三项及TaskReadTest八项通过，44次实际列表/水位SQL按全部十一种状态和两种范围执行；原PageHelper在未经企业准入的计数失败后清除线程分页状态。正式完整TaskListHttpTest端口62471验证全部十一项状态、非法参数/Schema422、无企业403及匿名401，业务表计数保持，临时应用关闭。原任务列表Red为IC-12-LIST-20261006-PROJECT-TASK-LIST-RED-01，实际入口断言及回执由原证据目录维护；当前文档CURRENT、last_verified=2026-10-06，仅适用于这些读取、Schema、SQL和准入检查。正式前端列表、真实授权集合成功分页、完整TK-01及客户任务业务尚未验收，IMP-12保持IN_PROGRESS，新源码未部署。

Given真实本人账户、MySQL、Redis与当前完整MedicalApplication；When读取项目任务列表及其分页查询；Then实际企业准入、Schema和过滤约束有效，全部列及分页使用成熟组件，记录没有变化。没有获授权客户项目时，合法成功和分页变更业务保留待验收，测试不创建客户记录或模拟许可。当前源代码本地未提交，实际Red/Green及源文件身份读取关联原始回执；当前个人信息共享组件变更以后，整体输入证明为STALE，受影响检查由唯一IMP-06维护。客户成功与完整工单仍未验收。

2026-10-06当前原管理方法权限及119项关联Java由唯一[IMP-06](IMP-06.md)维护，包含原TaskInputSchema、模板适用及固定项目属性检查；业务UUID、当前会话和原框架身份保持既有定义。原Spring Method Security不能提供企业任务许可，没有客户任务写入；完整TK-01、能力发布、范围快照、计划确认及后续生成继续实施。完整Java构建受实际S3空间保护未通过，新源码尚未部署，新部署任务HTTP证据未产生；较早集合保持各自原始范围。

2026-10-06BACK-RUOYI-NAVIGATION-01的原菜单、角色权限和Vue会话读取由唯一[IMP-06](IMP-06.md)维护；当前118项Java检查包含38项模板/项目与两个真实Spring HTTP，开发七项/生产六项DOM通过。TP-05消费者保持原项目属性、规则及来源核查；上述工程检查没有完成TK-01。固定Agent发布与检索/网络批准、范围交集、负责人资格、完整快照保存、原操作/审计/Outbox和已签发文稿继续修订仍须完整实施及真实验收。新源码尚未部署，全部任务与业务目标保持原范围。

2026-10-06TP-05固定项目属性、完整核准模板及规则适用查询由唯一[IMP-11](IMP-11.md)的BACK-TEMPLATE-APPLICABILITY-01维护。Spring SpEL、PageHelper及原来源/版本/资源许可直接复用，114项工程及实际HTTP检查通过；未知条件与明确缺项进入完整响应，条件结果不授予人工适用批准。公开TK-01仍须完整Agent发布、批准检索与网络策略、负责人、范围交集、缺项、快照保存、原操作/审计/Outbox和已签发文稿继续修订。当前完整业务目标和验收状态保持原范围。

2026-10-06项目治理及变更影响中的任务、运行列表直接采用原PageHelper；主记录为[IMP-04](IMP-04.md)的BACK-PROJECT-PAGINATION-01。固定属性版本、原企业协调行锁、任务和运行的独立许可、实际数量及complete保持完整。当前七项实际数据库及原项目Schema检查通过，新Java尚未部署；公开TK-01及全部原业务要求继续实施，原整体输入证明按受影响范围为STALE。

2026-10-06资料集正式创建、完整成员修订、固定历史与原操作核查读取唯一[IMP-10](IMP-10.md)的FRONT-COLLECTION-VERSIONS-01，关联S04和AC-31/37。当前开发/生产各五项资料集DOM及各七项原模板回归通过，均采用真实无企业账户，不登记客户成功。资料集后端尚未部署；ScopeSelectionAuthority继续使用原专业资料集权限和完整成员核查，公开任务创建及后续任务固定使用保持原全部验收要求。

2026-10-06当前源码ScopeSelectionAuthority调用[IMP-10](IMP-10.md)的CollectionAuthority，完整核查固定资料集专业清单、实际成员及全部上游版本/来源许可。原PageHelper账户分页由[IMP-04](IMP-04.md)维护；COLLECTION-NATIVE-05完整构建因存储容量保护未通过，新代码尚未部署到当前SCOPE-SELECTION制品。原范围308项构建的任务读取六项1.842秒、资料集八项1.222秒及原完整关系核查通过，整体证据为STALE；当前身份及共享检查使用各自新的回执。公开TK-01、批准检索策略、Agent范围交集、网络批准、项目/属性/模板/Agent发布/负责人核查、实际缺项计算、快照/任务保存与操作/审计/Outbox、计划保存和人工确认、稳定问题及正式页面继续完整实施，客户成功尚未验收。

## 原部署与任务检查的原始范围

| 字段 | 当前内容 |
| --- | --- |
| task_id / 增量 | IMP-12 / BACK-TASK-PLAN-01、BACK-TASK-STORAGE-01、BACK-TASK-SOURCE-CANDIDATE-01、BACK-TASK-READ-01、BACK-SCOPE-MANIFEST-01、BACK-SCOPE-SELECTION-01 |
| 唯一状态来源 | [实施任务计划](../../实施任务计划.md)，IC-12；当前 IN_PROGRESS，2026-10-06 |
| 需求与验收 | PRD §8.4/8.9，AC-07/08/18/37/40；LLD-后端 TK-01—06、§5.2；LLD-Agent AS-02/03；接口补充设计 SourceScope、PLAN_DRAFT |
| 代码与环境 | 当前本地 main，源码未提交；实际源码、产物和执行身份由原生构建回执登记 |
| 文档有效性 | CURRENT；last_verified=2026-10-06，适用于已读取的设计和当前代码；业务成功验收未执行 |
| 检索字段 | TaskRevision、ScopeSnapshot、SourceScope、PlanCandidate、PlanSection、ResearchQuestion、PLAN_CYCLE、CONDITION_UNKNOWN、JGraphT |

当前SCOPE-SELECTION-NATIVE-01完整300项Java通过，contracts42/data1/identity32/persistence225，失败、错误和跳过为零，构建2分43秒；635项输入及8项产物绑定实际字节。JavaPID31485、UID501、部署339ea611-75dd-4718-a50d-b4a11e69a20c，0400制品摘要9ed4566e2048d6592ae7030317f1d1eb0231bcdbac33bdf02e390eab51cd67c0，启动16.038秒。实际任务HTTP五项5.604秒、账户三十一项33.771秒、原框架五项51.813秒及部署字节核查通过。新ScopeSelectionAuthority、完整来源/资料集成员查询、原任务/专业快照/文稿依据、三项身份类、两项原框架类、共享与独立迁移资源均为同一制品。共享八份Schema、257项定义与265个固定引用保持原发布；MySQL保持226项迁移、152张企业表、17张平台基表、10张框架表及一项兼容View。本次没有数据库结构变更或客户实体写入，原对象服务PID62380和D0保持身份；完整Native包含十四项实际写入22.08秒和十五项传输6.282秒。内部本地选择组件及公开读取已经构建和运行，客户候选成功、完整快照/任务创建和IC-12尚未验收。较早集合按原始输入与制品范围读取，原整体集合对当前Java为STALE。身份原服务由唯一[IMP-04](IMP-04.md)维护。

## 检索与输入依据

已读取实施任务计划 IMP-12、PRD §8.4、LLD-后端 TK 与任务状态转换、接口补充设计 SourceScope 和 PLAN_DRAFT、LLD-Agent AS-02/03，以及原 Task/Plan 迁移。索引已有 IMP-12 路径，检索未发现本工单主记录。关联 [IMP-04](IMP-04.md)、[IMP-05](IMP-05.md)、[IMP-06](IMP-06.md)、[IMP-11](IMP-11.md)；当前已有项目、模板、企业授权、版本、审计和 Outbox 组件。

现有 CommonBindings.SOURCE_SCOPE 和 ArtifactBindings.PLAN_CANDIDATE 是唯一共享定义及 Java 绑定。JSON 使用现有 Jackson、networknt 和 RFC 8785；依赖图使用已安装 JGraphT。账户、菜单权限和数据范围继续调用原 RuoYi 服务。

## 当前功能与实现

BACK-SCOPE-SELECTION-01依据BR-12、AC-07/31/37和TK-01推进本地来源选择。已检索本记录、IMP-04/05、原SourceReadAuthority、SourceScope、VersionResourceAuthority、DependencyLineage、实际资料集成员及当前解析/索引/适用表。ScopeSelectionAuthority直接使用现有领域授权与来源核查组件；原MyBatis-Plus处理所有企业条件与主体核查，原RuoYi服务保持账户、角色、菜单和数据范围职责。没有新增基础权限算法、JSON解析或数据库结构。

PROJECT_APPROVED查询本项目当前核准且完整就绪版本；显式模式分别保持来源集合、固定资料集成员及两者并集，空范围返回空集合。资料集版本及全部已登记上游通过原VersionResourceAuthority核查当前许可，物理成员固定解析版本；来源仍由SourceReadAuthority.requireCandidate核查当前许可、权利及全部固定版本。明确容量用于完整集合与关系，超过容量立即拒绝；多个可用解析、索引或者适用决定、资料集解析冲突和成员缺少均返回SOURCE_NOT_READY。完整任务消费者仍需接入批准检索策略、Agent范围交集、资料集专业清单、网络批准、缺项计算、快照/任务保存及操作/审计/Outbox；正式创建和客户成功尚未验收。

Given真实MySQL、本人账户和原Mapper注册；When执行四种范围模式的空集合及显式版本查询，并读取固定资料集成员；Then所有列、完整ResultMap和锁定SQL有效，实际平台UUID无法取得企业来源，未经原企业准入的查询返回ACCESS_DENIED。SCOPE-SELECTION-RED-01十二项中两项查询缺失断言失败，错误/跳过为零，3.419秒；回执`.implementation-work/IC-05-SOURCE-AUTHORITY-20261006-SCOPE-SELECTION-RED-01/receipt.json`，SHA-256为d439df13f60b861573207e62507b843a7f3082bee25c34080afc9dbf583d3300。最终Green由当前完整Native中的十二项来源检查证明，获授权资料的成功选择和完整消费者继续业务验收；没有创建客户资料或者合成成功记录。

| 当前检查与实际范围 | 当前回执与SHA-256 |
| --- | --- |
| 完整300项Java；SourceReadAuthorityTest十二项1.112秒、TaskReadTest六项1.175秒；失败/错误/跳过为零 | `.implementation-work/IC-03-JAVA-20261006-SCOPE-SELECTION-NATIVE-01/receipt.json`；ab9edd47f5b9cd6b21ca0f124b39ffae91a68fcb5e2302e06df63df310b1f7c1 |
| 当前实际账户三十一项33.771秒，登录、密码、旧会话和撤权 | `.implementation-work/IC-04-LOCAL-HTTP-20261006-SCOPE-SELECTION-IDENTITY-01/receipt.json`；b88b9e95f08e1263a190601cf74df5aabc19ac4e52bc6a41c83ff366ab02aa8c |
| 原RuoYi五项51.813秒，实际账户/Redis/Servlet及角色、菜单和数据范围；临时应用31660已经关闭 | `.implementation-work/IC-04-RUOYI-20261006-SCOPE-SELECTION-RUOYI-01/receipt.json`；e60d093e693df98895899bc1055785eeaeb3f4996b3dbf831bc1af7e5fc52e6b |
| 当前任务HTTP五项5.604秒，本人无企业403、非法输入422、匿名401，业务计数保持零 | `.implementation-work/IC-12-READ-HTTP-20261006-SCOPE-SELECTION-HTTP-01/receipt.json`；b77e36c3527b20bd06a4f28eae0be32dd2c035ca0d96fce310ba4626d8f9d3ef |
| 635项Native输入、8项产物和实际PID31485一致，新选择/资料集成员及原完整组件和迁移资源字节一致 | `.implementation-work/IC-12-DEPLOYMENT-20261006-SCOPE-SELECTION-DEPLOYMENT-01/receipt.json`；6cd35bff292eac9c0cd2862651aa99442b148097ecd1265b7d462f503649a127 |
| 最终输入与唯一开发索引五项0.307秒，原件引用、权限声明及主记录链接通过 | `.implementation-work/IC-02-LOCAL-20261006-SCOPE-SELECTION-INPUTS-01/receipt.json`；3159d35726e37d6ca7327ce23a9bb7779c3bb5bae638b9fac309c033ada236ab |

独立登录完整HTML返回200，保存于`.implementation-work/SCOPE-SELECTION-20261006-login.html`；实际Jar权限0400。DeepSeek本地check返回configured=true，秘密仍由原0600文件与0700目录保存，没有模型调用。全部以上检查、构建与应用启动句柄已经终态；正式应用31485继续运行。源码未提交，完整客户流程、批准阈值、恢复与接收均未验收。以下专业快照集合分别保持其注明的原输入和制品范围。

BACK-SCOPE-MANIFEST-01依据PRD §8.4、BR-12、AC-07/31/37、LLD-数据的ScopeSnapshot/ScopeMember及接口补充设计推进完整清单核查。检索本记录、IMP-04/05、TaskService、DocumentUploadBasisAuthority、共享SourceScope和实际成员表；原Jackson、networknt、RFC8785、MyBatis-Plus、企业事务及授权组件继续复用。ScopeSnapshotManifest保存项目属性版本、四种选择模式、资料集与显式来源版本、网络批准版本、授权水位和完整成员；成员固定原件摘要、解析、索引、核准、项目适用与权利版本。任务读取和文稿上传采用同一专业Schema核查清单及关联表，保持唯一实现。完整构建、共享语言、部署字节和受影响运行检查通过，客户成功业务、来源范围选择与创建保存继续按完整IC-12验收。

Given实际MySQL、现有本人账户和当前共享发布集合；When核查固定快照定义与完整成员查询；Then专业定义存在且空对象按SCHEMA_INVALID拒绝，所有查询使用真实列和完整映射，未经原企业事务的查询被拒绝。SNAPSHOT-MANIFEST-RED-01实际五项中三项行为断言失败，错误和跳过为零，7.118秒；回执`.implementation-work/IC-12-READ-20261006-SNAPSHOT-MANIFEST-RED-01/receipt.json`，SHA-256为6be093629e9d3df6215653b95efc26f16ec2a4b7dec365ffe500a95fbfc539ff。获授权完整客户快照与后续文稿成功另行验收，工程资料不会登记为客户记录。

| 检查与实际范围 | 当前证据与SHA-256 |
| --- | --- |
| 完整Java构建298项，错误/失败/跳过为零；其中TaskReadTest六项1.234秒，真实查询与Spring注册、固定发布和不完整清单拒绝 | `.implementation-work/IC-03-JAVA-20261006-SNAPSHOT-MANIFEST-NATIVE-02/receipt.json`；ca61d3e57f34c30cddeccd477e7027773036c79db267dc3ff61a777c5bd3d2b9 |
| Python十二项、TypeScript严格类型及三十项通过；全部固定定义、原件及任务类型拒绝 | `.implementation-work/IC-03-SHARED-LANGUAGES-20261006-SNAPSHOT-MANIFEST-LANGUAGES-01/receipt.json`；aff580ad514dcd32daae72834afbdfe91737a01702a32bdadcb048360a37f8f3 |
| 实际账户三十一项31.454秒，独立登录、密码、撤权、会话及原回执 | `.implementation-work/IC-04-LOCAL-HTTP-20261006-SNAPSHOT-MANIFEST-IDENTITY-01/receipt.json`；5b3c61c587fdf3cdddca6949b132ab0ff7b6e10c87e10060213ddfaffa2ffbb4 |
| 原RuoYi五项51.160秒，实际账户、Redis、完整Servlet、角色菜单及数据范围；工程应用29544已关闭 | `.implementation-work/IC-04-RUOYI-20261006-SNAPSHOT-MANIFEST-RUOYI-01/receipt.json`；c50bcc050f28093519394f5d8c527ba5574f494b3812d5d685d213ad0f089820 |
| TK-02实际HTTP五项5.951秒；本人无企业返回403，非法Header/查询/标识422，匿名401；业务计数保持零 | `.implementation-work/IC-12-READ-HTTP-20261006-SNAPSHOT-MANIFEST-HTTP-01/receipt.json`；4bf1bf800f22abfca163ed49ab0ac1bbe1a28ef67a5ef68f9e6c9516ec2a758d |
| 实际PID、634项Native输入、8项产物、专业快照/成员/文稿依据类及共享与迁移字节一致 | `.implementation-work/IC-12-DEPLOYMENT-20261006-SNAPSHOT-MANIFEST-DEPLOYMENT-01/receipt.json`；9c868c0e956d0b7814bbe8d5814e97c46e57edd3c11ff98ff82bf995130025f2 |
| 当前输入与开发索引五项0.240秒，原件引用、权限声明及唯一开发记录链接通过 | `.implementation-work/IC-02-LOCAL-20261006-SNAPSHOT-MANIFEST-INPUTS-01/receipt.json`；00468e38f82da8ba79fa8bf32db1b20ab1c179bfb49350b876f68160955f5e92 |

登录完整HTML实际返回200，保存于`.implementation-work/SNAPSHOT-MANIFEST-20261006-login.html`；没有视觉检查。DeepSeek原秘密引用本地检查configured=true，没有新增模型请求。工程校验不登记客户业务PASS或工单ACCEPTED。清单核查的成功路径、完整资料集选择与网络批准、任务创建/保存/恢复和后续文稿处理仍需获授权客户资料，全部原范围继续保留。

BACK-TASK-READ-01依据TK-02、ResourceBindings.TASK_DETAIL及当前完整实施授权，连接TaskController、TaskService、TaskMapper。检索原任务/输入/计划/快照、文稿、运行、等待表，以及EnterpriseAccess、ResourceReadAuthority、PermissionAuthority和原MyBatis-Plus企业控制；所有查询核查当前服务端主体参数。固定输入完整核查共享Schema、请求、范围、项目属性、模板、Agent发布、负责人和目标文稿；快照与已确认计划完整重读并核查摘要。033保存原Requirement数组及独立摘要，读取不使用默认值；文稿、运行、待办和缺项动作分别经过当前授权。可选许可查询与强制准入使用同一原算法，没有新增账户、登录、会话或权限算法。客户成功读取保持独立验收。

Given实际认证账户与有效企业中的固定任务；When执行TK-02；Then返回唯一共享TaskDetail及当前head revision，固定输入、快照、计划和子资源逐项核查，撤权及时拒绝。没有企业的实际本人账户应在读取业务内容以前收到403；缺少Header、未知参数及非法标识应明确拒绝。技术查询与真实权限拒绝分别验证，合法业务成功保持独立验收。

实际数据库Red使用TASK-READ-RED-02，三项检查均在缺少查询或缺项字段时断言失败，错误和跳过为零，耗时2.246秒，回执57f3e8d06b974f5e3877dd59406f577f44574254c05279652387c6e7bc1f264b。Green使用TASK-READ-GREEN-02，三项真实MySQL查询/字段/拦截检查通过，回执65826310bc2bb8409fe5824f0f8bdb772d57baffef6e09c5e856e958b32bd3d0；后续状态校验使用完整Native取得当前证据。HTTP Red使用TASK-READ-HTTP-RED-02，实际部署中的本人账户请求返回404，五项断言失败，匿名请求保持原AUTHENTICATION_REQUIRED，回执34c2b85c11fb2728b85dc919f5cbd6152c386dc3fd758098f589fa7fccfb0d42。实际资料均来自当前账户和真实服务，没有创建客户实体。

033已经通过原Liquibase解析和真实MySQL迁移，当前共226项changeset；迁移前备份由原local_runtime.migrate生成并绑定实际数据库身份。TASK-READ-COVERAGE-01十五项原迁移解析检查11.274秒通过，回执885ee3aa84bf287a37b9b0eeb20fe48f9b847f0c2f0084d46d97c5a997123cce；TASK-READ-STORAGE-01七项真实存储字段、复合引用和ENFORCED约束1.308秒通过，回执03c9c8c57f811a1390b1516af65bc1764f2059925685d5b809289aa0ef0357a2。固定输入表必须没有既有记录才能执行本迁移，读取不能用迁移填充内容代替缺项计算。完整客户保存与读取、恢复和交接尚未验收。

实现位置：TaskController为GET /api/tasks/{taskId}；TaskService核查完整内容并绑定原TaskDetail/Envelope；mapper/TaskMapper.java及medical-mappers/TaskMapper.xml提供八项真实查询；TaskProtocol增加固定存储投影，运行和等待投影直接复用ProjectProtocol。ScopeSnapshotSchema由任务读取与文稿上传依据共同调用。TaskReadTest及tests/test_task_read_runtime.py分别验证实际数据库和真实Servlet，scripts/check_task_read.py提供native/runtime证据入口。权限共享变更关联IMP-04/05/06/11，迁移关联IMP-03，全部业务成功范围保持原要求。

原MapperScan注册与实际DataSource检查使用TASK-MAPPER-RED-02和TASK-MAPPER-GREEN-01。Red四项中一项注册断言失败，错误及跳过为零，耗时3.244秒，回执5efcf4f22bf42fdf7f572fb4223459d9a1701b29b7376cef9dcfbec782192230；Green四项3.400秒通过，回执9f62275966ff77b72eee710dbc5c17b2a22033bdf5db66d7a4932a3f6de01858。TaskMapper使用原@Mapper及原MedicalPersistenceConfiguration扫描，真实Spring容器取得原Mapper代理，无企业事务时原拦截器明确拒绝。

033迁移前备份为`.implementation-work/runtime/migration-backups/569de74b-2f2a-4dcd-9eee-db4c10824937.sql.gz`，SHA-256为411f7c56ab13cee873950bb9e637ac15b5aeddb44d77bd2ff4b02c19b700791c。原JSON回执绑定medical_local及5ecfb746-be18-11f1-95e4-d822203bc633，实际gzip完整性和摘要检查通过；备份及回执权限0600，目录0700。完整数据库恢复、批准RTO/RPO与接收保持未验收。

| 业务字段 | 原数据库及查询 | 共享响应 | 当前检查 |
| --- | --- | --- | --- |
| 任务与固定输入 | task_head及当前task_revision、task_input_revision | task_id、task_revision、project_id、owner_id、status | 完整请求Schema、固定版本归属、负责人、摘要及head revision |
| 来源与已确认计划 | scope_snapshot、plan_revision、plan_content_revision及version_registry | snapshot_id、plan_revision | 完整规范JSON摘要、固定任务/模板/快照及CONFIRMED状态 |
| 文稿引用 | document_head的当前任务归属 | document_ids | 每份文稿当前document.read许可 |
| 进行中的运行 | task_run的真实任务归属与当前状态 | active_run_ids | 当前run.read许可；包含其他任务修订中仍在进行的运行 |
| 待人工处理 | waiting_item关联真实任务运行或任务修订 | waiting_item_ids | 当前work_item.read许可、原Schema和WAITING_INPUT状态 |
| 输入缺项 | missing_requirements及requirements_sha256 | missing_requirements | 原Requirement五项完整字段、数组摘要、稳定文本身份及每项当前处理许可 |

BACK-TASK-SOURCE-CANDIDATE-01依据LLD-数据ScopeSnapshot/ScopeMember、LLD-后端TK-01和SourceScope规则，接入创建快照之前的实际固定来源核查。检索并读取原SourceReadAuthority、SourceRightsSchema、SourceReadProtocol、SourceReadMapper、ResourceReadAuthority及原MyBatis-Plus企业拦截器；沿用同一权限、权利期限和正式使用条件检查。Given实际获授权来源具有固定原件、解析、索引、核准和项目适用版本；When创建任务来源快照；Then在真实企业事务中逐项重新核查当前条件，完整结果保持全部固定版本，尚未创建的快照使用空引用，不构造快照身份。该增量不替代任务创建、快照保存或IC-12业务验收。

当前任务输入校验已经连接TaskService，专业快照核查已经连接任务读取和文稿上传依据。下列Given–When–Then保留完整接口验收行为；TK-01—06和IA-02的公开保存、人工决定及客户成功业务尚未完整连接。

Given：任务引用固定模板和项目属性，规划候选包含完整目录、章节、研究问题及其依赖；When：保存候选和请求人工确认；Then：校验完整节点覆盖、当前计划内引用、独立无环依赖，以及负责人、适用条件和完成规则。缺少负责人或存在待确认条件时保留草稿，禁止确认。结构校验不产生授权、批准或业务身份。

| 文件路径 | identifier | 职责和使用方 |
| --- | --- | --- |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/CommonBindings.java | SOURCE_SCOPE | TK-01 与后续范围使用方复用唯一共享来源结构 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/ArtifactBindings.java | PLAN_CANDIDATE | TK-03 与 IA-02 复用唯一规划候选定义 |
| business/medical-persistence/src/main/resources/tasks.schema.json | Create、PlanVersionInput、ScopeSnapshotManifest、ScopeSnapshotMember | 固定任务输入、计划及完整快照结构，直接引用共享类型；Catalogue 发布唯一身份及文件摘要 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TaskProtocol.java | Create、ValidatedCreate、PlanVersionInput | 固定任务版本、网络范围及计划输入的 Java 类型 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TaskInputSchema.java | create、plan | 复用成熟 JSON 和共享 Catalogue 校验，返回完整规范请求及范围摘要；不授予权限 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TaskPlanPolicy.java | inspect、requireConfirmable、dependencyOrder | 完整节点覆盖、章节归属、固定规则引用、独立依赖图、缺项和确认条件 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/TaskPlanGraphTest.java | completeOfficialDirectoryPreservesEveryDependency | 使用现有六章38节点目录验证完整技术依赖、自身循环、循环、重复与越界 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/TaskInputSchemaTest.java | explicitEmptyScopeKeepsTheNetworkDisabledAndBothSelectionsEmpty | 空范围、联网关闭、封闭输入和原始资料类型拒绝的工程检查 |
| scripts/check_task_plan.py | IC-12-PLAN | 原生技术检查、输入摘要及实际输出/XML回执 |
| business/medical-data/src/main/resources/db/migration/032-task-plan-content.sql | task_input_revision、plan_content_revision | 追加完整任务输入、独立来源范围及完整规划候选保存结构，保留真实归属、依据及原摘要 |
| tests/test_task_storage_runtime.py、scripts/check_task_storage.py | IC-12-STORAGE | 使用实际MySQL核查完整字段、企业/项目/任务/原摘要复合引用及ENFORCED约束，不创建客户记录 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceReadProtocol.java | SnapshotRequirement | 固定原件、权利、项目、解析、索引、核准和适用版本及当前使用环境；不携带尚未保存的快照身份 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceReadAuthority.java | requireCandidate | 调用原来源授权、权利核验和requireReady；锁定实际候选，返回前再次核查当前来源、权利及许可 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ScopeSelectionAuthority.java | require、Selection | 原公共组件核查四种本地来源选择和固定资料集成员，完整授权与就绪条件及显式容量 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceReadProtocol.java、medical-mappers/SourceReadMapper.xml | CollectionMember、scopeCandidates、collectionMembers | tenant_id/collection_version/source_version/parse_version直接映射原表；复用原SelectionRow全部字段，主体核验和锁定查询 |
| business/medical-persistence/src/main/resources/medical-mappers/SourceReadMapper.xml | snapshotCandidate | 查询真实固定版本的完整投影并执行FOR UPDATE；沿用MyBatis-Plus企业条件和当前主体参数核验 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ScopeSnapshotSchema.java | require、requireSource | 核查专业清单、完整有序成员、固定选择范围及原件/权利版本；由TaskService和DocumentUploadBasisAuthority共同调用 |
| business/medical-persistence/src/main/resources/medical-mappers/TaskMapper.xml | snapshot、members | 原MyBatis映射读取完整快照身份、水位、登记摘要与全部成员；成员投影直接复用原DocumentUploadBasisMapper.memberRow |

## 决策与约束

四种来源模式及网络批准字段完整保留；空范围不能扩大到全部资料。正式快照只包含当前授权且通过来源核查的固定版本。人工计划确认创建新的任务修订，既有修订和快照保持原记录。PLAN_DRAFT 使用 SYSTEM_JOB，不依赖已确认计划。重复请求沿用原操作身份，同键异参拒绝。

章节和问题分别校验依赖；不自行编写图算法、JSON parser 或来源文件 parser。计划结构检查不能替代实际权限、来源许可、负责人资格、规则批准、内容读取及数据库事务验证。

requireCandidate仅供真实企业事务中的快照创建使用。当前来源及项目读取许可先由ResourceReadAuthority核查，SourceRightsSchema核查用途、环境、地区、分类、期限和ENTERPRISE_USE；固定候选继续使用原requireReady核查上传、核准、解析、索引、适用决定和项目状态。锁定读取以后重新核查许可和权利，来源或权利发生变化返回SCOPE_STALE；固定版本不再具备完整条件返回SOURCE_NOT_READY。SESSION来源沿用原限制，不能进入正式快照。结果中的snapshotId为空，快照保存以后仍须使用原FORMAL入口核查实际成员。

当前已核查的框架复用为：RuoYi的ISysUserService、UserDetailsServiceImpl、SysPasswordService、SysPermissionService、PermissionService与DataScopeAspect；Spring Security管理登录、CSRF与Session；Spring Session管理Redis会话；MyBatis-Plus/JSQLParser处理企业SQL条件。新增候选入口组合原医疗来源组件，没有重新实现这些基础能力。完整管理Controller、菜单和全部新增基础功能仍需按原工单逐项核查，当前不声明全项目复用要求已验收。

ValidatedCreate.request_sha256固定创建请求内容；公开操作的完整摘要还包含实际方法、路径、Schema、上下文和会话身份，继续采用现有ProjectSchema.hash及原操作保存规则，不能以输入内容摘要替代操作身份。

## 验证与证据

当前完整构建回执为`.implementation-work/IC-03-JAVA-20261006-TASK-READ-NATIVE-02/receipt.json`，SHA-256为609d132810922abdaede1bfe511f5c30d86f4451885899773831c85a33c7985b；其中TaskReadTest四项1.233秒通过，实际MySQL、原Spring MapperScan和企业拦截共同验证。HTTP回执为`.implementation-work/IC-12-READ-HTTP-20261006-TASK-READ-HTTP-GREEN-01/receipt.json`，SHA-256为d1cb697fc497a67c9412ea7f2ecef31b9564f5a6a92b73166f774a74c02bb81e，五项7.748秒通过，固定实际部署和本人账户；业务表重读仍为零，没有制造任务或许可。部署回执为`.implementation-work/IC-12-DEPLOYMENT-20261006-TASK-READ-DEPLOYMENT-01/receipt.json`，SHA-256为7d3df3ca7bf4489d9c55537d07f1bf4a708ecec9e20f55a27dd7cc63f536c8d9，633项Native输入和8项产物、实际进程及所列组件字节相同。账户回归回执为`.implementation-work/IC-04-LOCAL-HTTP-20261006-TASK-READ-IDENTITY-01/receipt.json`，SHA-256为6905ae8f00257e71db73c96ba4a2abcbe9dcf6f6cdf3461a04a09fe4cf2d6d55，31项30.729秒通过。上述结果仅证明已运行的工程行为，客户成功验收仍为NOT_RUN。

当前原框架实际运行回执为`.implementation-work/IC-04-RUOYI-20261006-TASK-READ-RUOYI-01/receipt.json`，SHA-256为9abcbf5b9b82a83a62aa9e852785e17b929abdaca8231b8ff3811dab1bd373f8，五项50.799秒通过。本人原账户、角色菜单、数据范围、密码失败限制与原SysPasswordService解除均在真实Servlet、MySQL和Redis中核查，实际检查应用已经关闭。31项账户回归更新了本人密码，后续重新读取受保护owner-credentials.json。当前完整登录HTML实际返回HTTP200；DeepSeek原model-gateway-venv检查返回configured=true，没有发送生成请求。

TASK-READ-INPUTS-01五项0.297秒通过，回执`.implementation-work/IC-02-LOCAL-20261006-TASK-READ-INPUTS-01/receipt.json`摘要9f266d4d09c75fd106e25eb4b86d9ab0056eeec50f50bc0bfb653feb68adf122，当前索引、环境与实施计划的主记录引用和真实原件身份核查通过。完整清单重新读取57业务、17非功能、18边界、12EVAL和30SEC；42工单保持9 IN_PROGRESS、1 IMPLEMENTED、32 TODO，验收36 NOT_RUN、38 BLOCKED，客户业务PASS与工单ACCEPTED为零。更新本记录及续接没有改变上述检查输入。

| check_id | 场景 | 当前结果 |
| --- | --- | --- |
| IC-12-PLAN-GRAPH | 根据实际完整规范目录建立技术依赖关系，检查顺序、循环和不属于当前集合的引用 | PASS，当前TASK-SOURCE-CANDIDATE-NATIVE-01中的七项工程检查通过；不代表客户计划采用或IC-12通过 |
| IC-12-PLAN-RED | 规范目录实际依赖形成循环时必须返回 PLAN_CYCLE | 有效 Red：20261006-PLAN-RED-03，五项中四项通过，一项错误代码断言失败，错误和跳过为零；回执 bc2cabe1ada236e675f7d313e9eb889cbafa534f0ae439bef1122174c56637d4 |
| IC-12-INPUT-RED | 任务输入必须具有封闭定义并复用共享范围与计划类型 | 有效 Red：20261006-INPUT-RED-01，九项中六项通过，三项定义缺失断言失败，错误和跳过为零；回执 21345e7c98081247f9ca583d96c6bd5141cd641516f990bd21ca7d806db34228 |
| IC-12-PLAN-GREEN | 任务输入与规范目录依赖的工程检查 | 20261006-PLAN-GREEN-01，十一项通过；回执 56111d3e4c8f622b5cbab07ec4520fe7524340d8f121214aa8a18b792dc7c35d；适用该集合实际执行输入，当前完整结果继续复验 |
| IC-12 | 真实项目创建、固定范围、唯一任务、计划保存与决定、当前授权和后续运行 | NOT_RUN，保留全部必需验收 |
| IC-12-STORAGE Red | 完整任务输入与计划候选需要实际保存字段及企业/项目/任务/摘要约束 | 20261006-TASK-STORAGE-RED-01：六项检查中九项断言因缺少结构失败，错误为零；回执5718bf6fc083da398c5a679c78dc69a323f6489dd05c77d6b991577e306463a2 |
| IC-12-STORAGE Green | 实际225项迁移后的字段、复合外键、人员/操作/权限依据及ENFORCED约束 | PASS，六项0.897秒；回执8384c52b13a6cee0098dcc74e8a6e3c4b87b424dbe07a59e9f8f6fc0e017630f；仅证明实际结构 |
| IC-12-SOURCE-CANDIDATE Red | 创建快照之前需要真实固定版本查询及原企业/主体拦截 | 20261006-TASK-SOURCE-CANDIDATE-RED-01：九项中两项因查询缺少而失败，错误和跳过为零；回执fe89c51022ba3a8d814d146631f7e8bac7b91ca403c8fcd87b61afe63f78606c |
| IC-12-SOURCE-CANDIDATE Green | 实际MySQL执行固定版本查询、完整结果映射、无企业事务拒绝及必需固定输入 | 当前NATIVE-01中的十项0.528秒通过；原GREEN-02九项回执aef625747e237629ebf38230f63152872a188a804ead055cb0a3043143441940保留原输入范围。实际获授权候选返回与快照保存继续NOT_RUN |

当前来源增量的真实回执均位于`.implementation-work/`下同名集合目录中的`receipt.json`：

| 集合 | 实际结果及回执SHA-256 |
| --- | --- |
| IC-03-JAVA-20261006-TASK-SOURCE-CANDIDATE-NATIVE-01 | 完整292项，626项输入、8项产物；41464b5eef927735326a346e8d74be8d875aef019b4e1ae6921625cd23647ce2 |
| IC-04-LOCAL-HTTP-20261006-TASK-SOURCE-CANDIDATE-RUNTIME-01 | 31项实际账户、会话、权限拒绝、操作和数据库检查32.121秒；b1ff485a1be64674843904785e9d15f7f71f61f08ca6a2a93ce44190040f49e7 |
| IC-12-DEPLOYMENT-20261006-TASK-SOURCE-CANDIDATE-FINAL-01 | 当前进程、626项Native输入、8项产物、七项组件及任务/来源Mapper资源字节和独立迁移资源核查通过；b03379f7b1f0aed73344c5025c538e54c94f56f8d80e2073803a33fb258de1e3 |
| IC-02-LOCAL-20261006-TASK-SOURCE-CANDIDATE-INPUTS-01 | 当前真实来源、许可分类、环境、唯一索引和实施计划五项0.284秒通过；a46ce0656fa6a10c937640baba94434dea3ca47e79f5defdbd4c58b1fe8e6d7a |

原TASK-STORAGE-NATIVE-01和RUNTIME-01按原输入和部署范围读取，对当前修改的来源组件及制品为STALE；存储结构没有更改，原032结构证据保留其原范围。当前十项来源检查只证明真实查询、字段映射、固定输入与拒绝行为，没有真实企业来源时不制造核准或许可记录。requireCandidate的完整合法成功、四种模式的集合选择及其保存采用继续保留原验收。

存储增量的独立真实集合按以下原输入范围读取；TASK-PLAN-NATIVE-02及TASK-PLAN-FINAL-02对032后的源码和产物为STALE，当前来源组件及部署采用上方TASK-SOURCE-CANDIDATE集合：

| 集合 | 实际结果及回执SHA-256 |
| --- | --- |
| IC-03-JAVA-20261006-TASK-STORAGE-NATIVE-01 | 289项，contracts42/data1/identity32/persistence214；626项输入、8项产物；9667a18b9547b33d37fd8e4e7c054c24afe1568a555f4df5386a71988e4a3492 |
| IC-03-MIGRATION-COVERAGE-20261006-TASK-STORAGE-STATIC-02 | 完整152张企业表及其他平台/框架表的15项定义、字段、唯一键和外键检查；54c4dbcac7b2db477edf193ed673b79c52a45c67b43e99b8930f252a40b84843 |
| IC-03-MIGRATION-CLI-20261006-TASK-STORAGE-CLI-01 | 当前Library/CLI的全部资源、实际Liquibase启动及参数检查通过；866dcadb6ec3f9102d7db87cbe6cd8a3c7b563e8fad8a4393c259d5850e62dd8 |
| IC-03-CROSS-20261006-TASK-STORAGE-CROSS-01 | 当前Java快照与共享表示比较通过；dc1804d24d4381b76cc1a44d429cbc7b0619ab2fe03edf3fa9743be4426537e2 |
| IC-12-STORAGE-20261006-TASK-STORAGE-GREEN-01 | 实际存储六项通过；8384c52b13a6cee0098dcc74e8a6e3c4b87b424dbe07a59e9f8f6fc0e017630f |
| IC-04-LOCAL-HTTP-20261006-TASK-STORAGE-RUNTIME-01 | 当前制品31项实际账户/会话/拒绝/数据库检查31.937秒通过；4a4bdc7b40e92c76d913eafcaa5444309eac240c3dafeeb995f5aff302874ebc |
| IC-02-LOCAL-20261006-TASK-STORAGE-INPUTS-01 | 当前设计、环境、唯一索引及实施计划输入检查通过；4d7e80002b988c2e7eabc0fbb6e2d1d02ceb3311432b336f60831619d2e1d49a |
| IC-12-DEPLOYMENT-20261006-TASK-STORAGE-FINAL-02 | 当前应用身份、626项Native输入、8项产物、任务组件和独立迁移Library/CLI全部资源字节核验通过；20681b09929b6f8073b6fd89b663776ad5065844ae370fcd6d9724b564eccd92 |

这些回执位于`.implementation-work/`下同名集合目录中的`receipt.json`。032只追加三个changeset，由原Liquibase制品在实际MySQL执行成功；原222项迁移保持字节。迁移前备份为`.implementation-work/runtime/migration-backups/061f4041-7468-4019-806b-d2686d09f78c.sql.gz`，SHA-256为debf4c34084bdaa15aa0fb2570935a26d8f172f9ef8a1f340eb9ef426365c47a，同名JSON绑定原数据库身份，文件0600、目录0700。完整恢复和批准阈值未验收。

当前部署核验使用`PYTHONPATH=scripts .lld-data-work/venv/bin/python .implementation-work/task-plan-artifacts.py --native .implementation-work/IC-03-JAVA-20261006-TASK-SOURCE-CANDIDATE-NATIVE-01/receipt.json --execution 20261006-TASK-SOURCE-CANDIDATE-FINAL-01`，由原capture_check保存真实回执。应用和独立迁移Library/CLI按现有POM分别核查，标准ZipFile比较七项组件、全部对应资源及唯一core.sql；范围为DEPLOYED_TASK_COMPONENTS_AND_MIGRATION_ARTIFACTS。此范围不包含获授权候选成功、客户任务保存成功或完整IC-12。

工程检查使用当前真实公开规范目录，仅核查依赖算法；不建立客户企业、岗位、资料、许可或批准记录，不登记客户业务 PASS。

TASK-PLAN-NATIVE-02完整289项Java检查通过，contracts42/data1/identity32/persistence214，失败、错误、跳过为零，构建2分24秒；任务输入七项0.063秒、依赖七项0.046秒。回执为`.implementation-work/IC-03-JAVA-20261006-TASK-PLAN-NATIVE-02/receipt.json`，SHA-256为b96b5f6cb61ab84781784a2543747c92091f74b12339756aad6213073fdb40a5。对应PID15196、UID501、部署ea293517-0885-433b-8d64-3fd18160a7ea、0400制品1eadc4e8ba8b271454823ab91763a26a48d26b62a67392105ae39478ca2f289e，实际启动13.669秒。

TASK-PLAN-FINAL-02完整30组本地回归通过，起止2026-10-05T19:51:46.148977+00:00至2026-10-05T20:04:36.059245+00:00，完整摘要5e243dd624a14e7f0d57fde69f4e76c59ff19671bdeaabc6ed2704b3ffc9839e。31项账户HTTP29.363秒、四项原框架24.770秒、九项模板HTTP6.156秒，以及正式页面、操作、对象、预算、共享表示、迁移和输入检查通过。30组与Native合计31份当前输入、实际产物及Java/对象服务身份核查通过：[verified-evidence.json](../../.implementation-work/FULL-REGRESSION-20261006-TASK-PLAN-FINAL-02/verified-evidence.json)，SHA-256为f7fe2b88b09dfe16b09a26dee0d6afc56b680648fb452661bdc1b112bb6c19ff。该证明适用于本次执行时的完整输入；后续源码、迁移或产物变化按受影响范围取得新证据。实际部署的三个任务组件、共享Catalogue、tasks资源、身份Manifest及JGraphT制品由`.implementation-work/task-plan-artifacts.py`使用标准ZipFile和原verify_application_deployment核查，625项输入与8项产物匹配，工程范围DEPLOYED_TASK_VALIDATION_COMPONENTS通过。

共享语言TASK-PLAN-SHARED-01通过Python十二项、TypeScript三十项及严格类型检查，回执`.implementation-work/IC-03-SHARED-LANGUAGES-20261006-TASK-PLAN-SHARED-01/receipt.json`，SHA-256为ca205a30a1ee7e1fcfff96b0676c6881c958640fea07c77e43da40e669478914。八份Schema、255项定义、263个固定引用及实际文件摘要已登记。客户成功候选的完整规则、负责人、授权与数据库采用尚未验证，不能用当前工程检查登记IC-12通过。

原始有效回执位于 `.implementation-work/IC-12-PLAN-20261006-PLAN-RED-03/receipt.json`、`.implementation-work/IC-12-PLAN-20261006-INPUT-RED-01/receipt.json` 和 `.implementation-work/IC-12-PLAN-20261006-PLAN-GREEN-01/receipt.json`，各自保留实际输入范围。最终源码包含自身循环和缺少依赖清单断言，由TASK-PLAN-NATIVE-02完整覆盖；这些技术检查不能关闭客户业务验收。

## 关联影响与后续使用

设计使用方为 TK-01—06、IA-02 和后续 Temporal 调度；实际业务接入、任务状态、来源快照和完整配置页面按本工单继续实施。模板核准和项目属性沿用 IMP-11 与现有 ProjectService，授权、审计、幂等和 Outbox 沿用 IMP-04/05。共享组件变化按实际使用范围复验。

## 未完成内容与继续条件

任务创建、四种模式的实际范围选择、完整输入及快照保存、计划修订与人工决定、研究问题身份保存、正式页面及全部 IC-12 尚未完成；TK-02代码已经连接，客户成功读取未验收。客户真实企业、成员、项目、已授权资料、批准版本及验收条件仍需原待答输入。内部编码继续推进；工单未登记 IMPLEMENTED 或 ACCEPTED，完整目标保持全部范围。

已核查004任务/计划迁移、005文档引用关系及007发布关联；032已经提供完整输入和候选内容的版本保存结构。原Task/Plan、文档及发布引用继续使用既有固定版本。章节关系投影、研究问题稳定身份及问题间依赖的关系保存尚未连接，后续使用完整候选内容建立逐项一致的关系，并连接原企业事务、当前主体参数、固定版本、CAS、原操作/审计/Outbox和完整重读。已应用迁移保持原字节；完整章节覆盖不能因单个node_id投影而减少。实际规则、来源许可和成员资格逐次核查，结构检查不能作为授权或批准证明。

BACK-TASK-STORAGE-01继续本记录，依据LLD-数据§4的固定任务输入与新版本要求、LLD-后端TK-01/03及共享Create/PlanCandidate。完整创建内容和四种范围由task_input_revision按原任务修订保存，保留原操作、目标文档、真实负责人、权限依据、水位、Schema及摘要。完整PlanCandidate由plan_content_revision按原计划保存，包含全部节点、章节、问题标识、问题依赖、负责人空值和待确认条件；基准计划必须属于相同企业、项目和任务，并绑定原摘要。原plan_revision需要完整内容摘要的唯一引用键。数据库结构、Schema校验、授权和实际业务采用分别验证；不存在客户业务数据时不制造正向记录。真实存储检查使用information_schema和原MySQL服务，只执行SELECT。
