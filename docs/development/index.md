# 开发记录索引

2026-10-18 重新定位和旧数据兼容切片：见[正常生命周期](IMP-23-REANCHOR-PLAN.md)、[重新定位前端](IMP-23-REANCHOR-FRONTEND.md)、[旧数据显式核对](IMP-23-LEGACY-ANCHOR-RECONCILIATION-IMPLEMENTATION.md)及[对应前端](IMP-23-LEGACY-ANCHOR-RECONCILIATION-FRONTEND.md)。最终55套376项Java组合测试及12模块package通过；前端15+20+9+9组检查、类型和构建通过，全部为离线/替身范围，非真实数据库或浏览器验收。

2026-10-18 当前续接状态与可恢复源码检查点以[本轮开发记录](../接管开发记录-2026-10-18.md)为准。新增[人工解决/重开批注](../annotation-decisions-implementation.md)及[对应前端](IMP-23-ANNOTATION-DECISIONS-FRONTEND.md)、模拟配置与前端、评测前端、模拟执行/控制纯组件、Selection语义、[批注创建与列表](IMP-23-ANNOTATION-THREADS.md)、[批注回复](IMP-23-ANNOTATION-REPLIES.md)和[批注前端](IMP-23-ANNOTATION-FRONTEND.md)已分别记录离线验证。真实数据库、对象服务、HTTP及浏览器联调仍BLOCKED/NOT_RUN；下面历史运行记录不代表当前工作区服务可用，也不改变原任务验收状态。

2026-10-16 FRONT-HANDOFF-20261016、[前端开发交接](../前端开发交接.md)维护接收范围、源码与接口、完整剩余任务、环境与复验入口、原始证据适用性及待批准的数据清理。关联IMP-04/05/08/09/10/11/16/36/37/40/41/42。当前只读核查确认EXACT-RECOVERY-10登记文件保持一致；BROWSER-31原始50项浏览器通过保留，当前Chrome两项文件身份变化，浏览器组合适用性为STALE，继续使用当前工具复验。功能任务状态仍按原计划维护。

2026-10-16后端接续资料读取[后端开发交接文档](../后端开发交接文档.md)，包含全部后端责任、当前源码与登记制品关系、历史证据、证书期限、环境及继续开发入口；任务状态仍由实施任务计划维护。

2026-10-14 MODEL-CONFIGURATION-01、model_config_revision、CP-01—03、供应商/模型/秘密版本/limits/data_policy/价格及请求协议、完整固定配置和实际发布依赖由唯一[MODEL-CONFIGURATION-01](MODEL-CONFIGURATION-01.md)维护。关联BE-07/12/13/14/28及[IMP-07](IMP-07.md)，预算预留、实际调用及结算继续完整后端范围。

2026-10-14 RUN-AUTHORITY-LOCAL-CONFIG-01、prepare_run_authority_configuration、activate_run_authority_configuration、实际CA/客户端证书/签名CRL/RSA、0700/0600、受保护公钥/私钥绑定、当前C02配置启动及PID45581部署由唯一[RUN-AUTHORITY-LOCAL-CONFIG-01](RUN-AUTHORITY-LOCAL-CONFIG-01.md)维护。关联BE-04/13/36及[RUN-TOKEN-AUTHORITY-01](RUN-TOKEN-AUTHORITY-01.md)、[IMP-36](IMP-36.md)，秘密值仅保存于受保护配置。

2026-10-14 FRONT-SESSION-RECOVERY-01、医疗路由读取错误、当前内存目标、显式会话重读及完整领域错误响应由唯一[IMP-04](IMP-04.md)维护；关联session-recovery、router.onError、IdentityErrorResponseWriter及TemplateErrorResponseTest。当前公开错误六项检查通过，组合实际导航恢复继续核验。

2026-10-14 RUN-TOKEN-AUTHORITY-01、RunTokenAuthority/Store/Schema/Configuration、claimStep/renewStep/issue/renew/execute、持久jti与原签名摘要、当前Policy和mTLS服务绑定、步骤租约与代际、当前资源动作许可、053迁移及实际数据库检查由唯一[RUN-TOKEN-AUTHORITY-01](RUN-TOKEN-AUTHORITY-01.md)维护。关联BE-04/07/09/13/14及[IMP-04](IMP-04.md)、[IMP-13](IMP-13.md)；内部HTTP、生产调度与真实客户成功路径保持原验收范围。

2026-10-14 FRONT-EXACT-FIELDS-01、五类结构化字段、完整原值与人工别名、PublicationInput/Manifest/PublicationReceipt、SearchInput/Hit/Page、固定版本及等值查询前端消费由唯一[IMP-09](IMP-09.md)维护；关联SourceExactController和FE-09。check_source_exact_frontend三十三项参数检查、EXACT-RECOVERY-10完整制品及BROWSER-31原生/开发/生产五十项组合通过，涵盖权利、真实上传、空索引列表、核准、固定清单、五类查询、别名、原文展开、完整修订、历史、恢复及撤权；当前输入和产物字节重新核验通过。完整七格式Worker、非空索引、实际多页及正式采用继续原范围。

2026-10-14 RUN-TOKEN-SIGNING-KEY-01、RunTokenSigningKeyStore、独立PKCS8私钥配置、原openat/fstat保护、当前公钥Policy修订及字节绑定、Nimbus签名和读取前后配置核查由唯一[IMP-04](IMP-04.md)维护；关联IMP-07/13/14/36。当前实际RSA及登记原件密码算法核验继续技术范围，完整生产令牌、持久运行归属、mTLS、当前动作、续期及客户业务保持原验收要求。

2026-10-14 SOURCE-EXACT-01、SourceExactController/Service/Schema/Protocol/Policy/Mapper、完整原文字段及人工别名核准、固定字段版本、CAST AS BINARY等值查询、完整结果保存及分页、来源关系/ReferenceHold、五项公开方法和二十二项稳定输入Java/MySQL/S3检查由唯一[IMP-09](IMP-09.md)维护。052、292项迁移、178张企业表、五张新增表及原287项checksum由[IMP-03](IMP-03.md)维护，实际备份和应用构建/运行由[IMP-36](IMP-36.md)维护；关联IMP-05/08/10。正式快照、内部工具、DENSE/HYBRID、网络采集及客户业务继续后端计划原范围。

2026-10-14 FRONT-INDEX-VIEW-01、source-index、source-index-result、source-index-builds、固定来源/项目索引历史、完整清单摘要及七项参数检查由[IMP-09](IMP-09.md)维护。关联SourceIndexViewController/Service/Schema和FE-08/09，真实空列表及完整当前制品继续核验。

2026-10-14 SOURCE-INDEX-FULLTEXT-01、SourceIndexWorker/Access/Manifest/Policy/Configuration/Protocol/Mapper、完整逐片段核验、原事件及索引身份、租约恢复、READY/FAILED与消费者回执，以及KN-INDEX-STATUS的SourceIndexViewController/Service/Policy/Schema和十三项稳定输入Java检查由[IMP-09](IMP-09.md)维护。051、287项迁移、173张企业表、七项外键及六项CHECK由[IMP-03](IMP-03.md)维护，实际备份、保持关闭的消费者及构建/运行由[IMP-36](IMP-36.md)维护。完整DENSE/HYBRID、EXACT、内部检索、网络候选和客户业务继续后端计划原范围。

2026-10-14 FRONT-SOURCE-RIGHTS-01、source-rights、rights-reviews/rights-decisions、完整许可与授权文件、KN-RIGHTS-01原请求恢复、来源权利与操作重读、SourceRightsSimulationHttpTest及真实浏览器检查由[IMP-10](IMP-10.md)维护，关联IMP-04/05/08/36/37。FRONT-UPLOAD-RESULT-01、source-upload-result、readSourceUploadResult、完整逐文件解析/索引/核准/失败清单、SourceUploadStatusController及固定来源与权利查询由[IMP-08](IMP-08.md)维护；当前完整Schema消费及九项参数检查已执行，完整工程与真实流程继续核验。

SOURCE-UPLOAD-STATUS-01、SourceUploadStatusController/Service/Policy/Schema、KN-04实际接收/解析/索引/核准/权利及失败读取、原Schema和配置保持、二十项Java检查由[IMP-08](IMP-08.md)维护；当前PID7124、固定Jar08d0fc6afb0ad44fc247877efbeeda62a02c2a5825dbb01a814d81d3f30e6baa、149项class字节、十八份资源、恢复后的实际S3/Qdrant/Temporal和完整备份由[IMP-36](IMP-36.md)维护。完整后端任务继续[后端实施计划](../后端实施计划.md)原范围，当前业务验收保持IN_PROGRESS。

SOURCE-RIGHTS-DECISION-01、SourceRightsController/Service/Policy/DecisionSchema/EvidenceAuthority/EvidenceBytes/Mapper、人工APPROVE/RETURN/REVOKE、完整授权Artifact和实际对象读取证明由[IMP-08](IMP-08.md)维护，核准及生命周期引用[IMP-10](IMP-10.md)。BACK-SOURCE-COMPLETION-01、SourceCompletionWorker/Access/Execution/CheckpointStore/Registration、scan_command、真实扫描与格式核验、来源登记及终态回执，连同BACK-SOURCE-PROCESSING-01和原件分段重组由[IMP-08](IMP-08.md)维护；047—050迁移由[IMP-03](IMP-03.md)维护；当前286项登记、十九份源码配置、完整解析包及固定版本恢复、构建和部署由[IMP-36](IMP-36.md)维护。真实官方原件使用LOCAL_PARSER_CHECK，客户上传及完整处理继续独立验收，三个消费者及人工权利审批均关闭。

前后端执行责任分别见[前端实施计划](../前端实施计划.md)和[后端实施计划](../后端实施计划.md)。两端沿用同号IMP主记录，完整任务状态继续读取实施任务计划§4.2。

2026-10-13结构化模板定义、template-definition-editor、全部ValueType及完整字段浏览器检查由[IMP-11](IMP-11.md)维护；document-body-reader、document-selection、全文及固定历史目录、Unicode和合并表格选区由[IMP-16](IMP-16.md)维护；check_frontend_continuation固定当前输入、组件开发/生产制品和完整医疗构建，verify_frontend_continuation重新核对全部当前字节，由[IMP-36](IMP-36.md)与[IMP-37](IMP-37.md)分别维护制品及适用验证。所有父工单继续按原完整业务验收判定。

当前[前端操作说明](../frontend-operation-guide.md)及[制品环境核验说明](../frontend-release-guide.md)供[IMP-40](IMP-40.md)、[IMP-41](IMP-41.md)和[IMP-42](IMP-42.md)引用；正式SaaS、私有化及岗位接收分别使用其原完整验收和真实部署条件。

2026-10-13前端原件阅读、VerifiedByteAssembly、source-original-reader、source-file-preview及七格式浏览器检查由[IMP-08](IMP-08.md)维护；KaTeX公式和document-evidence-panel由[IMP-16](IMP-16.md)维护；固定导出字节核验和当前页面响应检查由[IMP-26](IMP-26.md)维护；七格式兼容与内容清理由[IMP-39](IMP-39.md)维护。本轮用户明确授权SIMULATION资料，正式客户验收继续独立登记。

request-metadata、封闭本地Schema、检索及导出原请求恢复由[IMP-05](IMP-05.md)维护；固定检索历史及连续分页由[IMP-09](IMP-09.md)维护；资料目录、处理队列、成果目录及全文检索的当前页面与会话清理由[IMP-04](IMP-04.md)维护；本轮公开接口集成条件读取[IMP-37](IMP-37.md)。

当前按[后端实施计划](../后端实施计划.md)继续执行剩余后端工作。BACK-SOURCE-DELETION-01、SourceDeletionWorker/Access/Policy/Stores/Target/Configuration/Mapper及046迁移由[IMP-10](IMP-10.md)维护；BACK-LOCAL-DELETION-STORAGE-01、local_vector_storage、local_deletion_runtime、check_source_deletion_runtime、check_local_database_restore、当前本地服务和恢复证据由[IMP-36](IMP-36.md)维护。既有未提交内容继续保留，真实业务验收按当前输入规则执行。

BACK-RUN-READ-01、RunReadController/Service/Mapper、run-view.schema.json、RN-02、运行一致性快照、当前撤权和check_run_read由唯一[IMP-13](IMP-13.md)维护。FRONT-RUN-READ-01、runs.ts、run-detail.vue、task-detail.vue及check_run_frontend由唯一[IMP-27](IMP-27.md)维护；运行状态、步骤、等待事项、预算与费用未知状态共同关联IMP-07/12/13。

HARNESS-MEDICAL-AGENT-01、官方SDK/runtime发行、medical-agent profile、professional-executor、EnterpriseModelAdapter、ModelStreamVerifier、PrivateBridge、registerTools、tool-inputs/schema、tool-success/schema、实际插件与工具清单、check_professional_executor和AT-01—10由唯一[IMP-14](IMP-14.md)维护。当前严格构建、实际SDK初始化及六项模型/Unix Socket工程集成检查通过；本地兼容条件、接口和完整剩余开发读取主记录。业务开发继续使用已授权SIMULATION输入。

MODEL-SIMULATION-01、SimulationCase、SimulationProvider、SimulationCompletion及test_model_simulation，由唯一[IMP-07](IMP-07.md)维护。完整固定输入、参数、模拟身份、文本及工具事件检查已经通过；C07持久化与结算继续主记录要求。

BACK-TASK-WORKFLOW-01、WorkflowProtocol、WorkflowPlan、TaskWorkflowImpl、WorkflowWorker、WorkflowDelivery、RunActivities、ProfessionalActivity、固定运行输入、依赖调度、持久Signal及消费者回执；唯一主记录[IMP-13](IMP-13.md)，关联IMP-04/07/08/12/14/15/27及IN-01/07/12。当前代码、实际验证和剩余业务接入直接读取主记录。

DOCUMENT-FIXED-EXPORT-01、ExportRenderer、ExportInput、ExportFile、FixedAsset、FontBinding、ConverterBinding、实际DOCX/PDF/Markdown、OMML、Fontconfig及check_document_export；唯一主记录[IMP-26](IMP-26.md)，关联IMP-08/16/23、C09、EX-01—04及IC-26。当前实现、实际文件验证与剩余公开导出直接读取主记录。

BACK-DOCUMENT-BODY-01、DocumentBodySemantics、DocumentReadController/Service/Mapper、DocumentRevisionController/Service/Mapper、DocumentUploadController/Service/Policy、DocumentUploadIssuance、DocumentUploadPartService、DocumentUploadCompletionService、DocumentBodyDomainAuthority、SourceUploadTransport、DC-01/02、BX-02/03/04、dependency-access-set、document-editor.vue、document-node-editor.vue、document-text-editor.vue、document-field-editor.vue、document-tables.ts、document-comparison.ts、document-session-drafts.ts、DocumentEditBuffer、PendingDocumentSave、稳定节点与精确数值、三版本比较、历史正文、表格编辑、撤销恢复及会话草稿；唯一主记录[IMP-16](IMP-16.md)，关联BIZ-07、AC-22/38、IC-16、FRONT-I02/I03/I14、迁移037—039及[IMP-05](IMP-05.md)。当前代码、实际验证及剩余开发直接读取主记录。

BACK-SCENARIO-CATALOGUE-01及FRONT-SCENARIO-CATALOGUE-01：ScenarioCatalogueController、ScenarioCatalogueService、ScenarioCatalogueSchema、ScenarioCatalogueMapper、GET /api/scenarios、scenarios.ts、scenarios.vue及MedicalScenarios页面；唯一主记录[IMP-29](IMP-29.md)，关联PRD§8.7、AC-14/15和CP-07。实际配置元数据查询、规划要求页面、验证结果及正式场景采用的剩余开发直接读取主记录。

BACK-TASK-CREATE-01及FRONT-TASK-CREATE-01：TaskCreateService、TaskCreateReleaseAdmission、TaskCreateDraftService、TaskCreateDraftSchema、TaskCreationSelectionService、TaskCreationSelectionSchema、PlatformMemberIdentityStatus、TK-01、TK-01-DRAFT、task-create-operations、task-create-drafts、task-creation-owners、task-create.vue、task-create.ts、固定输入/快照、输入草稿恢复和MedicalTaskCreate页面；唯一主记录[IMP-12](IMP-12.md)，需求PRD AC-07/08/18/37及FRTE-07。当前实现、验证与剩余开发直接读取主记录。

BACK-TASK-SCOPE-SELECTION-01及FRONT-TASK-SCOPE-SELECTION-01：TaskScopeSelectionService、TaskScopeSelectionSchema、ScopeSelectionAuthority.require、CollectionMapper.selectionVersions、task-creation-sources、task-creation-collections、task-scope-selection.ts及task-create.vue的来源选择；唯一主记录[IMP-12](IMP-12.md)，关联PRD§8.4、FRTE-07、SourceScope与CollectionAuthority。当前实现、集成范围、正式资料验收与剩余开发直接读取主记录。

BACK-TASK-PLAN-CONFIRM-01及FRONT-TASK-PLAN-CONFIRM-01：TaskPlanConfirmationStore、TaskPlanService.confirmation、TaskPlanReleaseAdmission、TK-04 CONFIRM、新任务输入和确认计划、两项CAS、原操作恢复及确认/退回页面；唯一主记录[IMP-12](IMP-12.md)，当前实现、验证和剩余发布消费者直接读取主记录。

BACK-TASK-PLAN-RETURN-01及FRONT-TASK-PLAN-RETURN-01：TaskPlanReturnService、TaskPlanDecisionSchema、TK-04 RETURN、plan-decision-basis、plan-decision-operations、TaskPlanWriteMapper.decision、task-plan-return.vue、原操作恢复；需求PRD§8.4及AC-07/08，唯一主记录[IMP-12](IMP-12.md)，关联IMP-04/11/13。当前实现及验证结果直接读取主记录。

BACK-AGENT-RELEASE-SCOPE-01、AgentReleaseScopeAuthority、VerifiedScope、AgentReleaseMapper.scopes/roleOrigins、ReleaseScope完整企业/项目/成员/岗位/分类由唯一[IMP-12](IMP-12.md)维护，关联CP-03/TK-01、原岗位邀请及动作许可。当前定向编译和四项实际SQL/访问拒绝/Schema检查通过，原JUnit及十三份class独立保存；合法完整采用和真实发布/评测决定继续未验收，父工单状态引用实施任务计划。

BACK-AGENT-RELEASE-STORE-01、AgentReleaseStore.read/Stored/requireUnchanged、AgentReleaseStoredProtocol和AgentReleaseMapper由唯一[IMP-12](IMP-12.md)维护，关联CP-03/TK-01及发布固定元数据。完整发布、Agent和评测字段读取与当前配置/评测/制品许可核验进入源码；当前定向编译和两项实际MySQL/拒绝检查通过。发布范围、实际评测决定和任务准入继续完整业务要求，父工单状态引用实施任务计划。

BACK-TASK-PLAN-WRITE-01、TaskPlanService、TaskController.plan-revisions/plan-operations、PlanVersionInput、TaskPlanWriteProtocol.View及task-plan-view由唯一[IMP-12](IMP-12.md)维护，关联TK-03、TaskPlanRelationStore、来源和责任人员资格。当前公开保存与固定读取编译及42项检查通过，共享Python/TypeScript检查通过；维护计划读取、原操作恢复与页面编辑继续同一范围。完整合法保存、人工决定和公开任务创建仍待业务验证，状态引用实施任务计划。

FRONT-TASK-PLAN-WRITE-01、task-plan-editor.vue、task-plans.ts、PlanEditBasis、当前维护/固定/原操作读取、完整章节和研究问题、首次人工计划及恢复由唯一[IMP-12](IMP-12.md)维护，关联FRTE-07、TK-03及TaskPlanService.editBasis。全部字段已经连接公开接口，保存后按固定版本核验原内容；当前编辑依据使用实际核准模板目录和全部适用条件，原请求恢复由用户明确触发。当前strict类型、五项实际Schema加载及正常退出通过，后端42项与共享语言检查通过；全部当前检查已退出，没有全量构建或部署。当前来源、Agent、人工决定和完整业务验收继续各自主记录范围。

BACK-SCOPE-SNAPSHOT-STORE-01、ScopeSnapshotStore.save、ScopeSnapshotWriteMapper、ScopeSnapshotStoreTest及check_scope_snapshot由唯一[IMP-12](IMP-12.md)维护，关联TK-01、ScopeSelectionAuthority、SourceReadAuthority和TenantScope。当前事务代码保存完整快照、成员、版本与引用，并重新核查当前项目、来源和许可；15项局部检查通过，实际MySQL空范围保存重读、主体拒绝、事务取消及生成数据清理通过。完整公开任务创建、非空正式来源和Agent当前发布准入继续主记录范围，开发优先级采用用户最新要求。

BACK-AGENT-RELEASE-MANIFEST-01、AgentReleaseProtocol/Manifest、AgentReleaseManifestSchema、完整RuntimeManifest/ReleaseScope、版本引用、插件DAG、AS-01—15、C07/C12、限制和固定Schema由唯一[IMP-12](IMP-12.md)维护，关联IMP-07/13/14/28。完整结构组件已提供，28项清单与19项原Java共享检查通过，Python12项和TypeScript严格类型/30项通过；25项新定义共同登记至303项共享定义，原字节及18份编译制品独立核验。开发按用户最新要求优先补齐全部代码；实际评测、发布批准、持久化与任务采用继续原范围，完整工单状态保持。

BACK-CURRENT-RUN-TOKEN-POLICY-01、RunTokenPolicyStore、ProtectedConfigurationFile、run-token-policy.json及当前公钥/发行者/受众/原期限由唯一[IMP-04](IMP-04.md)维护，关联IMP-08/13/38。当前60项配置/签名及141项完整身份关联检查通过，原TLS/Servlet行为、原子更新、当前私有文件拒绝及实际凭据清理保持，独立原始字节和22份公开证书/CRL签名核验通过；完整应用构建与新部署继续验证。此前448项及PID16562的证据保持自身原输入范围，完整内部运行归属和Worker仍未完成。

036-task-plan-relations.xml、check_task_plan_migration、实际十二项迁移/249项登记、完整备份、原Liquibase制品及161张企业表隔离由唯一[IMP-03](IMP-03.md)维护迁移执行范围，完整计划关系由唯一[IMP-12](IMP-12.md)维护。原71412成功终止，原237项登记保持、备份0600/摘要/GZIP完整性通过；ACTUAL-03原88064的十二张表完整结构核验通过。RUN-TOKEN-UPLOAD-TASK-PLAN-BUILD-FINAL-02原8916的448项Java和前端检查通过，新制品PID16562的八项入口、完整HTTP静态字节与六项原操作通过，由唯一[IMP-06](IMP-06.md)维护当前部署。原获授权聊天已按用户要求暂停，完整IMP-12与Agent发布准入由本聊天继续。

BACK-RUN-TOKEN-SIGNATURE-01、RunTokenSignature、原Nimbus9.37.4、封闭JWT字段、明确Policy及原签名/期限检查由唯一[IMP-04](IMP-04.md)维护，关联IMP-08/13/14/15/38与run_principal/principal_token/execution_registry。当前Green-02原84973已取得exit0终态，42项实际RSA/JUnit检查通过，原输入保持一致；完整运行归属、当前授权、令牌续期、终态回执及Worker消费者继续实施，完整业务继续原要求。

BACK-UPLOAD-PERMIT-DEADLINE-01、TransferConcurrency原permit_id及截止核查、ServletUploadInputTest停止发送期限、SOURCE/DOCUMENT_BODY共享消费者由唯一[IMP-05](IMP-05.md)维护，关联IMP-08/06。实际服务Green原76636的73项通过，包含原32次停止发送期限；原输入及独立JUnit字节核验通过。实际Redis许可身份失效返回LEASE_STALE；当前448项完整构建、实际部署和六项原操作通过。

FRONT-TASK-DETAIL-SPRING-ENTRY-01、FrameworkFrontendConfiguration的/projects/{projectId}/tasks/{taskId}/config、FrontendEntryHttpTest直接进入/刷新及正式字节检查由唯一[IMP-06](IMP-06.md)维护，关联[IMP-12](IMP-12.md)的FRONT-TASK-DETAIL-01、TK-02与FRTE-07。原Spring与SIMULATION Green-02均取得成功终态，独立证据覆盖对应65项JUnit、35项浏览器检查及231份字节。当前448项完整构建、新制品八项入口与任务config刷新通过；完整任务创建、来源快照、计划、运行与后续业务保持原验收。

官方Temporal CLI、本地持久服务、local_temporal、check_temporal_development及后续Workflow、Worker、Signal、execution_registry、system_job_run和run_step由唯一[IMP-13](IMP-13.md)维护，关联IMP-04/07/08/12/14/15/27/36/39。当前官方制品摘要、实际CLI版本、健康、正常关闭、SQLite完整性及同一Namespace持久重读通过，PID95932运行本地服务；完整业务调度、取消与恢复尚未完成。

CURRENT-TRUST-SOURCE-UPLOAD-BUILD-FINAL-01的完整403项Java、前端strict/生产构建、十九份运行JSON、当前PID86534不可变制品、229份HTTP静态字节、七项入口及六项原操作由唯一[IMP-06](IMP-06.md)维护，关联[IMP-04](IMP-04.md)服务配置读取器与[IMP-08](IMP-08.md)上传页面。当前源码已部署；运行令牌、Worker、完整来源处理与业务验收继续主记录原要求。下方检查和部署保留各自原输入范围，任务状态引用实施任务计划。

BACK-CURRENT-SERVICE-TRUST-01、ServiceCertificateTrustStore、ServiceCertificateTrustStoreTest、ServiceCertificateIdentity.TrustSource及实际Servlet消费者由唯一[IMP-04](IMP-04.md)维护，关联IMP-08/13/38。JNR POSIX/FFI原生openat/fstat、受保护配置、封闭字段、实际证书/签名CRL、原子发布、逐次重新读取与失败传播已经通过81项关联检查；独立证明CURRENT-SERVICE-TRUST-20261008-final-evidence.json核验544项输入、原始JUnit及22份公开资产。当前生产部署仍为原402项制品，新读取器和业务Worker按主记录继续实施；任务状态引用实施任务计划。

FRONT-SOURCE-UPLOAD-SPRING-ENTRY-01、FrameworkFrontendConfiguration.ROUTES、FrontendEntryHttpTest、实际/projects/{projectId}/uploads直接进入/刷新及完整静态字节由唯一[IMP-06](IMP-06.md)维护，关联IMP-08/04/37。有效Red与Green均有原始回执，三项Java、五项实际浏览器行为、31项页面入口及229份正式字节通过；完整构建与部署继续执行，完整上传与处理业务继续IMP-08原验收。

FRONT-SOURCE-UPLOAD-01、source-uploads.ts/vue、source-declaration.vue、source-upload-view.schema.json、原FileUpload/ElUpload、protocol/router、check_source_upload_browser和source-upload-checks由唯一[IMP-08](IMP-08.md)维护，关联IMP-04/06/09/10/12/37及KN-01—04。当前严格类型检查、229份静态构建及十三项真实账户/原件浏览器检查通过，独立证明SOURCE-UPLOAD-BROWSER-20261008-final-evidence.json核验8937项输入与233项产物；获授权成功上传、处理终态及完整KN-04继续原要求，源码发布由IMP-06维护。

FRONT-TEMPLATE-SPRING-ENTRY-01、FrameworkFrontendConfiguration.ROUTES、FrontendEntryHttpTest和实际项目模板路径的直接进入/刷新由唯一[IMP-06](IMP-06.md)维护，关联IMP-11/04/37。当前完整402项Java、前端strict、生产构建与PID74481部署通过，225份HTTP静态字节、六项入口及六项原操作通过；当前组合入口为SERVICE-CERTIFICATE-TEMPLATE-20261008-release-evidence.json。完整模板业务与任务状态引用IMP-11及实施任务计划。

FRONT-TEMPLATE-APPLICABILITY-01、template-applicability.ts/vue、protocol.ts、project.vue、项目/projects/:projectId/templates路由、check_template_applicability_browser和template-applicability-checks由唯一[IMP-11](IMP-11.md)维护，关联IMP-04/06/12/37、TP-05、FRTE-07/18。并行聊天按用户授权维护页面；strict、独立生产构建及开发五项/生产五项实际账户DOM通过，完整合法模板查询、分页、人工适用决定和任务采用继续原验收。当前证据与状态读取主记录及实施任务计划。

BACK-SERVICE-CERTIFICATE-01、ServiceCertificateIdentity.require/Trust/Verified、ServiceCertificateNativeTest、ServiceCertificateServletTest、prepare_service_certificates、check_service_certificate和verify_service_certificate_evidence由唯一[IMP-04](IMP-04.md)维护，关联IMP-08/13/37/38与C02。实际TLS/Servlet、证书路径、当前登记及签名CRL进入当前402项完整构建和本地制品；独立80项/503项/22份公开证书证明保留原输入范围。当前完整制品由IMP-06维护，信任配置生产发布、运行令牌和业务消费者继续原要求。状态和当前证据读取主记录与实施任务计划。

SOURCE-COMPLETION-LEASE-01、SourceCompletionLeaseStore.claim/renew/Lease、SourceUploadMapper.claimCompletionDelivery/renewCompletionDelivery、TenantScope.subjectId、SourceCompletionLeaseNative、SourceUploadHttpTest.verifyCompletionLeases及TenantAccessTest由唯一[IMP-08](IMP-08.md)维护，关联IMP-03/04/05/06/13/37。当前实际HTTP、完整400项Java与前端构建核验30秒原数据库租约、六次领取、续租、旧代际拒绝、固定命令与原响应、当前撤权和范围清理；调用器仅存在于src/test。当前PID61813部署核验222份HTTP静态字节、五项入口与六项原操作，由唯一[IMP-06](IMP-06.md)维护运行证据；九份组合入口为SOURCE-COMPLETION-LEASE-20261008-final-evidence.json，结果读取实际文件。Worker服务身份、Temporal、消费回执及最终业务结果仍待实现，下方DELIVERY及其他集合保持原输入范围；完整状态引用实施任务计划。

SOURCE-COMPLETION-DELIVERY-01、SourceUploadProtocol.COMPLETION_CONSUMER/CompletionDelivery、SourceUploadMapper.saveCompletionDelivery/completionDelivery、SourceUploadService.requestCompletion/completionReplay和SourceUploadHttpTest.assertPendingCompletionDelivery由唯一[IMP-08](IMP-08.md)维护，关联IMP-03/04/05/06/13/37。完成命令、原事件及消费者投递采用同一企业事务；重复请求核验持久身份，完整Worker及来源结果继续原要求。当前独立HTTP及完整400项Java与前端构建通过，制品已在PID53689部署，222份HTTP静态字节、五项入口和六项原操作分别通过。当前九份组合入口为SOURCE-COMPLETION-DELIVERY-20261008-final-evidence.json，实际结果读取原始文件；下方较早构建、部署与九份组合只保持原输入范围，完整状态读取实施任务计划。

SOURCE-NATIVE-SCAN-PARSER-01、NativeParser.scan_and_parse/_source_content、ScannedDocument、ClamAVScanner、ScannerRelease/ScannerArtifact/SignedDatabase、NativeScanResult和check_source_scan_native由唯一[IMP-08](IMP-08.md)维护，关联IMP-03/05/06/09/10/13/26/37。当前安装制品五项扫描/解析和十二项七格式回归通过；固定发布文件、三个官方数字签名、实际原件扫描、私有快照清理与拒绝配对均有原始证据。400项Java与PID47930部署保持对应范围；当前九份组合入口为SOURCE-COMPLETION-SCANNER-20261008-final-evidence.json，实际状态读取主记录和实施任务计划，Worker及完整来源业务继续原要求。

SOURCE-COMPLETION-INPUT-01、SOURCE-COMPLETION-COMMAND-01、SourceUploadSchema.completion/ValidatedCompletion/CompletionPart、SourceUploadService.requestCompletion/completionParts/completionReplay、SourceUploadMapper.completionOperation/startCompletionOperation/completionCommand/completionAudit、POST /api/uploads/{id}/completions及SourceUploadHttpTest.acceptsTheOriginalSourceCompletionCommandAndReopensItsPendingOperation由唯一[IMP-08](IMP-08.md)维护，关联IMP-03/04/05/06/09/10/12/26/37。当前完整400项Java、前端strict及生产构建通过，完整JUnit与十七项运行JSON独立保存；十九项输入检查和六项来源实际HTTP包含202原命令保存、重读、重复原字节、唯一操作/审计/Outbox和当前授权拒绝。新制品在PID47930运行，Jar48c61f2e71e7d959683451548ad7c7d6b41bcd856e85809ef0ac73d997125d2e；222份HTTP静态字节、五项入口和六项原始操作通过，部署证据由唯一[IMP-06](IMP-06.md)维护。Worker、扫描、格式、去重、来源版本和最终回执仍未完成；下方390项及较早组合分别保持原输入范围，完整任务状态读取实施任务计划。

SQL-PARSE-CACHE-01、FailFastSqlParseCache、MedicalPersistenceConfiguration、SqlParseCacheTest和TenantAccessTest由唯一[IMP-04](IMP-04.md)维护，关联IMP-08原上传容量期限。成熟MyBatis-Plus/JDK序列化与Caffeine负责独立AST、32MiB权重和5秒期限，单语句及多语句分别保存，当前权限逐次核验，错误立即报告；当前390项完整构建、独立五项上传HTTP及本地部署共同验证相关消费者。IC-08-SOURCE-SCAN-20261008-OFFICIAL-01已经完成三个官方数据库独立数字签名与登记DOCX原件扫描，scope=LOCAL_PARSER_CHECK_OFFICIAL_ORIGINAL_SCAN；KN-03公开消费者与恶意上传拒绝仍未完成，唯一IMP-08维护原始回执。

BACK-SOURCE-UPLOAD-01、KN-01—04、SourceUploadSchema、SourceUploadController/Service/Policy/Protocol、SourceUploadService.partReplay/PartReplay、SourceUploadMapper、source_upload_request、SourceUploadPartConfiguration、SourceUploadTransport.checkingSubject/Policy、UploadPartReceiver.receiveRequest/Reception/receive/recoverHeld、SourceUploadNetworkInterruption、SourceUploadHttpTest.recoversOriginalPartAfterActualS3ResponseInterruption/rejectsExhaustedConfiguredCapacityWithoutWaitingForExistingInput/expiresStoppedActualInputAndResumesTheOriginalOperation、IncomingUploadPart.captureRequest、ServletUploadInputTest.failed/stoppedActualSenderIsTerminatedByOriginalTotalDeadline、DocumentUploadTransferService.receiveRequest、TransferConcurrency.tryPart/PartLease、OperationSchema、完整Provenance/RightsDeclaration、source.write、归属/版本意图、分段与进度重读由唯一[IMP-08](IMP-08.md)维护，关联IMP-03/05/06/09/10/12/26/37。当前SQL-CACHE-BUILD-FINAL-01完整390项Java、70项共享接收、10项缓存/当前身份及前端构建通过，十六项运行JSON与完整JUnit独立保存。原20/60秒容量、30秒停止输入清理、相同请求继续、固定对象、唯一事件、并发、取消、撤权与实际S3响应中断恢复保持原断言。当前PID40465与Jar667dc87510bc0b2d3eaaf91f8212536ff79028c2c6d5fb579ca2cd993a4791ca的222份HTTP静态字节、五项入口及六项原始操作通过。ClamAV1.5.4官方完整源码构建、安装、版本检查、三个官方数据库数字签名与登记DOCX扫描已验证；扫描仅证明LOCAL_PARSER_CHECK_OFFICIAL_ORIGINAL_SCAN。持续输入期限、多人企业容量、排队取消、全部WRITING条件、DOCUMENT_BODY共享消费、KN-03/04、页面、Worker及后续业务继续必需范围；完整状态读取实施任务计划，原始回执由主记录维护。

FRONT-RUOYI-LOGIN-01、BACK-RUOYI-ACCOUNT-ACTIONS-01、AsyncManager、scheduledExecutorService、完整应用生命周期、本地应用恢复、build_business、完整clean verify、check_application_deployment、ApplicationDeploymentRuntimeChecks、全部静态文件的封装/HTTP字节、GET /assets/**与独立JUnit副本由唯一[IMP-06](IMP-06.md)维护，关联IMP-03/04/05/08/12/36/37；当前390项完整Java、前端strict与生产构建、222份部署静态字节及五项页面入口读取该记录；165项Java与131项Chrome等较早证据保持各自原始输入范围。任务状态继续引用实施任务计划。

BACK-OBJECT-STORAGE-01、本地对象服务恢复、原D0固定版本重读、AES256、私有ACL、匿名拒绝及全部本机监听由唯一[IMP-05](IMP-05.md)维护，关联IMP-06/08/26/36；原数据、技术身份、加密秘密、备份与容量保护保持完整，客户业务验收继续引用实施任务计划。

BACK-MEMBER-GOVERNANCE-READ-01、FRONT-MEMBER-MANAGEMENT-01、MEMBER-READ-01—03、ID-07/09、FR-01、AC-01/02、EnterpriseAccess、PermissionAuthority、member-governance.ts、member-governance-write.ts/vue、member-governance-write-view.schema.json、enterprise-members.vue、当前企业成员/授权/邀请读取、完整邀请/授予/撤销/停用、原请求恢复及分页水位由唯一 [IMP-04](IMP-04.md) 维护，关联 IMP-06/12/37；开发/生产二十四项管理写入检查通过，当前165项 Java/131项 Chrome 关联证据由 IMP-06 维护。任务状态读取实施任务计划，正式客户和完整父工单验收继续原范围。

FRONT-MEMBER-ACCEPT-01、ID-08、本人邀请接受、原请求重放、凭据内存保护、实际成员重读及明确企业选择由唯一[IMP-04](IMP-04.md)维护，关联 IMP-06/12/37；开发/生产二十二项浏览器检查通过，当前关联回归由 IMP-06 维护。完整状态读取实施任务计划。

当前执行依据为[实施任务计划](../../实施任务计划.md) V1.1.0（2026-10-07）§4.4—4.6。BIZ-01—20引用原42项工单及其唯一主记录，业务操作与具体前置能力决定执行顺序；索引不维护独立完成状态。BIZ-02 的项目操作、岗位管理、本人接受和完整成员管理页面已有 SIMULATION 成功证据；客户验收与完整父工单继续原范围。按 P0 前置能力继续上传处理、资料核准、模板及任务创建/计划保存。[IMP-12](IMP-12.md)维护任务入口及创建消费者，[IMP-08](IMP-08.md)维护七格式解析组件。

BIZ02-SIMULATION-01、ProjectSimulationHttpTest、project-simulation-checks、项目创建/属性修订/四种状态决定、原请求重放/版本冲突/撤权及审计由唯一[IMP-04](IMP-04.md)维护；开发/生产十项项目浏览器检查通过。`readMedical()` 返回当前 Pinia 会话引用，关联页面由[IMP-06](IMP-06.md)维护，完整成员治理及专业岗位继续原验收。

BIZ02-ROLE-SIMULATION-01、ROLE-01—04、EnterpriseRoleSimulationHttpTest、enterprise-roles.ts/vue、role-write-view.schema.json、岗位发布/历史读取/修订/撤销、原操作恢复与 role-simulation-checks 由唯一[IMP-04](IMP-04.md)维护，关联 IMP-06/37。当前接口和页面连接实际数据库；完整成员邀请、岗位授权传播及客户验收继续原要求。

BIZ02-MEMBER-SIMULATION-01、ID-07—09、MemberGovernanceSimulationHttpTest、SimulationAccountData、成员接受/独立授权/岗位授权传播及停用由唯一[IMP-04](IMP-04.md)维护，关联 IMP-06/12/37；当前检查连接原账户创建与真实企业事务，完整成员界面及客户验收继续原要求。

BIZ01-SIMULATION-01、用户授权的虚拟企业/成员/项目/任务、SimulationTaskData、当前应用企业选择与 TaskListHttpTest 正式成功路径由唯一[IMP-12](IMP-12.md)维护，关联 IMP-04/06/37；十二项任务浏览器检查和正式 HTTP 成功通过，当前共享165项 Java 与131项浏览器回归引用[IMP-06](IMP-06.md)。虚拟数据验收与客户业务验收分别登记，完整状态读取实施任务计划。

DOCUMENT-NATIVE-PARSE-01、C09、NativeParser、Document.iter_inner_content、BODY_CONTENT、DOCUMENT-NATIVE-PARSE-2、七格式 Locator、原文摘要、固定 parse_version 与来源身份由唯一[IMP-08](IMP-08.md)维护，关联 IMP-05/09/10/12/26/37；十二项解析与当前安装制品检查通过，原五个DOCX表格逐项保留正文顺序。当前八份关联回执的字节核验引用该主记录，上传及完整处理状态继续按原范围实施，完整状态读取实施任务计划。

FRONT-TASK-LIST-01、FRONT-I01/BX-01、项目任务过滤/原Pagination/固定水位/完整TaskDetail、project.task.read和授权计数由唯一[IMP-12](IMP-12.md)维护，关联IMP-04/06/37；完整状态读取实施任务计划。

FRONT-RUOYI-REQUEST-01、原utils/request、api/system、api/monitor、Cookie/CSRF、原管理响应及身份失效由唯一[IMP-06](IMP-06.md)维护，关联IMP-04/37；完整框架和业务验收读取实施任务计划。

FRONT-RUOYI-PROFILE-01、原system/user/profile/index、userInfo、userAvatar、resetPwd、SysProfileController、本人资料版本及原请求恢复由唯一[IMP-06](IMP-06.md)维护，关联IMP-04/37；全部框架和业务验收继续读取实施任务计划。

FRONT-RUOYI-PASSWORD-01、原system/user/profile/resetPwd.vue、api/system/user.updateUserPwd、LOCAL-06、账户版本/幂等/审计/会话撤销及medical-account-checks由唯一[IMP-06](IMP-06.md)维护，关联IMP-04/37；完整原个人信息和管理模块读取实施任务计划。

BACK-TASK-LIST-01、BX-01、TaskService、TaskMapper、任务状态/责任人/更新时间过滤、PageHelper、PageInfo、固定授权集合与分页摘要由唯一[IMP-12](IMP-12.md)维护，关联IMP-04/06/11/37；完整业务状态读取实施任务计划。

FRONT-RUOYI-LOGIN-01、views/login.vue、user.login、原登录表单、Cookie/CSRF、同源Vue发布、FrontendEntryHttpTest、FrameworkNavigationHttpTest及framework-navigation-checks由唯一[IMP-06](IMP-06.md)维护，关联IMP-04/37；完整框架及客户验收读取实施任务计划。

BACK-RUOYI-ACCOUNT-ACTIONS-01、LOCAL-02—06、FrameworkAccountOperations、RuoYiAccountOperations、原SysUserController/SysProfileController及医疗UUID/版本/会话/审计由唯一[IMP-04](IMP-04.md)维护，关联IMP-06/37。原Controller执行账户动作，全部业务验收与页面要求读取实施任务计划。

BACK-RUOYI-NATIVE-ROLE-01、FRONT-R03/BX-25、原SysRoleController十五个动作、TokenService、RuoYiManagementConfiguration、MEDICAL_RUOYI_TOKEN_SECRET、local_runtime.framework-token、实际受保护配置及正式HTTP由唯一[IMP-06](IMP-06.md)维护，关联IMP-01/03/04/05/37。原角色CRUD、授权、数据范围、导出及分页直接使用原实现；合法业务、当前撤权、环境绑定、同步审计、幂等和页面继续原验收范围。

BACK-RUOYI-NATIVE-CONTROLLERS-01、FRONT-R03/BX-25、RuoYiManagementConfiguration、原十个管理Controller、LogAspect、ThreadPoolConfig、原PreAuthorize及FrameworkMethodSecurityHttpTest正式路由检查由唯一[IMP-06](IMP-06.md)维护，关联IMP-01/03/04/05/11/12/37。原CRUD、分页、数据范围和权限直接使用框架实现；完整环境绑定、企业授权、同步审计、幂等和正式页面继续按原要求验收。

BACK-RUOYI-MANAGEMENT-DATA-01、FRONT-R03/BX-25、034-ruoyi-management-tables.sql、原二十张框架表、InspectFrameworkTables、test_ruoyi_management_data、RuoYiManagementMapperTest及真实迁移/完整备份由唯一[IMP-03](IMP-03.md)维护，关联IMP-01/04/06/37；原Mapper及Service当前关联检查读取IMP-06，验收状态读取实施任务计划。

BACK-RUOYI-MANAGEMENT-SERVICES-01、FRONT-R03/BX-25、RuoYiAccountConfiguration、原system十三项Service、RuoYiManagementServicesTest及check_framework_navigation的native-services模式由唯一[IMP-06](IMP-06.md)维护，关联IMP-03/04/37；原Controller、环境绑定、企业授权、同步审计和页面保持全部要求，验收状态读取实施任务计划。

BACK-RUOYI-METHOD-SECURITY-01、FRONT-R03、BX-25、RuoYiAccountConfiguration、原EnableMethodSecurity/PermissionService/SysUserController、FrameworkMethodSecurityHttpTest及实际Cookie方法权限由唯一[IMP-06](IMP-06.md)维护，关联IMP-03/04/05/11/12/37；完整Java原始JUnit与HTTP结果通过build_business及check_migration保存，验收状态读取实施任务计划。

BACK-RUOYI-NAVIGATION-01、FRONT-R03、FrameworkNavigation/Controller、framework-navigation.schema.json、framework-navigation.ts、user.frameworkNavigation、permission.frameworkRoutes、原SysPermissionService/ISysMenuService/filterAsyncRouter、当前Cookie与空权限及BX-25完整绑定要求由唯一[IMP-06](IMP-06.md)维护，关联IMP-03/04/11/12/37；验收状态读取实施任务计划。

BACK-TEMPLATE-APPLICABILITY-01、TP-05、TK-01、TemplateApplicabilityService/Controller/Schema、TemplateApplicabilityHttpTest、RuleExpression、Spring Expression Language、PageHelper、固定项目属性、条件三态及完整规则依据由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/04/05/06/12/37；验收状态读取实施任务计划。

BACK-PROJECT-PAGINATION-01、ProjectService.list/waiting/impact、ProjectMapper、PageHelper、PJ-01/04/05/06、ProjectPaginationTest及check_project_pagination由唯一[IMP-04](IMP-04.md)维护，关联IMP-03/05/06/12/37；框架分页及原业务筛选、计数和完整性保持完整，验收状态读取实施任务计划。

FRONT-COLLECTION-VERSIONS-01、资料中心、KN-11—13、collections.ts、collections.vue、固定成员版本、原操作核查、并发修订及Element Plus分页由唯一[IMP-10](IMP-10.md)维护，关联IMP-03/04/05/06/12/37；完整验收状态读取实施任务计划。

BACK-RUOYI-ACCOUNT-PAGINATION-01、LocalAccountManagement.list、LocalAccountMapper.selectAccounts、PageHelper、LOCAL-01及check_account_pagination由唯一[IMP-04](IMP-04.md)维护，关联IMP-03/06/37。账户及身份扩展读取使用原MyBatis/PageHelper，原RuoYi账户与数据范围保持既有实现；状态读取实施任务计划。

BACK-COLLECTION-VERSIONS-01、KN-11/12/13、CollectionService/Authority/Schema/Protocol/Policy/Mapper、collections.schema.json、collection_head/version/member、CollectionTest及check_collections由唯一[IMP-10](IMP-10.md)维护，关联IMP-03/04/05/06/12/37；ScopeSelectionAuthority共同使用完整专业清单，分页采用原PageHelper，整体状态读取实施任务计划。

BACK-SCOPE-SELECTION-01、ScopeSelectionAuthority、SourceReadMapper.scopeCandidates/collectionMembers、SourceReadProtocol.CollectionMember、四种本地来源模式及固定资料集成员由唯一[IMP-12](IMP-12.md)维护，关联IMP-04/05/11/37。原SourceReadAuthority、VersionResourceAuthority、DependencyLineage、MyBatis-Plus及RuoYi账户组件直接复用；完整公开创建、客户成功和全部业务状态读取唯一实施任务计划。

BACK-TASK-READ-01、TK-02、TaskController、TaskService、TaskMapper、TaskDetail、Requirement、missing_requirements、requirements_sha256、033-task-missing-requirements.xml、TaskReadTest及check_task_read由唯一[IMP-12](IMP-12.md)维护，关联IMP-03/04/05/06/11/13/37；客户业务与全部任务状态读取实施任务计划。

BACK-RUOYI-RETRY-AUTHORITY-01、SysPasswordService.validate/clearLoginRecordCache、LocalPrincipal、实际密码锁定和解除、审核快照及RuoYiPasswordRetryIT由唯一[IMP-04](IMP-04.md)维护，关联IMP-06/37；状态读取实施任务计划。

BACK-TASK-PLAN-01、BACK-TASK-STORAGE-01、TaskInputSchema、TaskProtocol、tasks.schema.json、TaskPlanPolicy、TaskPlanGraphTest、TaskInputSchemaTest、check_task_plan、check_task_storage、task_input_revision、plan_content_revision、SourceScope、PlanCandidate、章节与问题依赖、PLAN_CYCLE、CONDITION_UNKNOWN及JGraphT由唯一[IMP-12](IMP-12.md)维护，关联IMP-03/04/05/06/11/13/15/37；完整业务与客户验收状态读取实施任务计划。

BACK-RUOYI-DATA-SCOPE-01、DataScopeAspect、RuoYiAccountConfiguration、EnableAspectJAutoProxy、ISysUserService.selectUserList/checkUserDataScope、原角色/部门数据范围及RuoYiNativeIdentityIT由唯一[IMP-04](IMP-04.md)维护，关联IMP-06/37；全部管理模块及客户业务状态读取实施任务计划。

FRONT-ENTERPRISE-REVIEW-01、enterprise-registration-review.vue、enterprise-registration-review.ts、LOCAL-REG-04—06、正式企业人工审查、完整治理条件、固定决定回执及原请求恢复由唯一[IMP-06](IMP-06.md)维护，关联IMP-04/37；任务IN_PROGRESS，完整客户业务和全部管理模块继续按原范围验收。

BACK-RUOYI-NATIVE-IDENTITY-01、LoginUser、SecurityUtils、PermissionService、LocalPrincipal、Spring Session principalName及当前原管理身份接入由唯一[IMP-04](IMP-04.md)维护，关联IMP-06/37；全部管理模块和业务验收状态读取唯一实施任务计划。

FRONT-ENTERPRISE-REGISTRATION-01、enterprise-registration.vue、medical/enterprise-registration.ts、LOCAL-REG-01—03、正式企业登记表单、真实管理员候选、原登记请求跨入口核验及enterprise-registration-view.schema.json由唯一[IMP-06](IMP-06.md)维护，关联IMP-03/04/05/37；完整审查、客户企业和项目业务继续按原范围验收。

FRONT-TEMPLATE-CREATE-01、template-create.vue、medical/template-create.ts、TP-01正式创建入口、完整VersionInput编辑与导入、template-create-view.schema.json、固定草稿回执及原操作恢复由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/04/06/10/12/23/26/37；完整业务验收及全部管理交互继续按原范围执行。

FRONT-TEMPLATE-APPROVAL-01、template-approval.vue、medical/template-approval.ts、模板人工审查路由、完整目录/字段/附件、TP-02固定审查清单、原请求查询、template-approval-view.schema.json及check_template_approval_browser由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/04/10/12/23/26/37；客户成功及完整后续使用保持原验收范围。

BACK-TEMPLATE-APPROVAL-01、TemplateApprovalService/Schema/Protocol、TP-02、approval-baseline、031-template-approval、人工APPROVE/RETURN/DISABLE、固定检查清单、CAS及原操作/审计/Outbox由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/04/10/12/23/26/37；完整受控制品读取、项目采用和客户业务分别按原验收范围验证。

BACK-RUOYI-AUTHENTICATION-01、UserDetailsServiceImpl、SysPermissionService、SysRoleServiceImpl、SysMenuServiceImpl、RuoYiAuthenticationConfigurationTest、030-ruoyi-authentication-menu.sql及原账户验证复用由唯一[IMP-04](IMP-04.md)维护，关联IMP-01/03/05/06/37。

BACK-TEMPLATE-SCHEMA-PUBLICATION-01、原$id URN、七份Schema、253项定义、260个发布引用、TemplatePublishedSchemaTest及三语言发布校验由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/04/05/10/12/23/26/37；完整模板核准、受控读取和客户业务继续按原要求实施。

BACK-TEMPLATE-CREATE-01、TemplateController、TemplateService、TemplateDependencyAuthority、TemplateDependencyPolicy、TemplateOperationSchema、template-operations.schema.json、TP-01、template.draft_created及check_template_runtime由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/04/05/10/12/23/26/37。公开保存使用既有完整定义、企业事务和权限，模板核准、项目采用及正式页面继续按完整计划实施。

BACK-RUOYI-ACCOUNT-01、BACK-RUOYI-PASSWORD-01、RuoYiAccountConfiguration、ISysUserService、SysUserServiceImpl、SysPasswordService、RedisConfig、SpringUtils、AuthenticationContextHolder、pwd_err_cnt、sys_user、platform_local_identity_metadata、platform_local_account View、ruoyi_user_id、check_ruoyi_accounts 及框架账户运行检查由唯一[IMP-04](IMP-04.md)维护，关联 IMP-01/02/03/05/06/07/37。完整任务状态读取实施任务计划，检查范围及当前证据读取主记录。

BACK-REGISTRATION-DIRECTORY-01、RegistrationDirectory/Catalogue/Entry/requireComplete、registration-directory-2021-121.json、RegistrationDirectoryTest及REGISTRATION-DIRECTORY-NATIVE-01/RUNTIME-01由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/05/10/12/17—23/26/37。完整六章38节点与真实固定原文逐项核查，当前247项原生及六项实际HTTP通过；目录规范、运行制品与全部未完成模板流程分别登记，状态读取实施任务计划。

BACK-UPLOAD-WAIT-ADMISSION-01、原完整准入的repeatWhen/delayElements、独立checks Scheduler及UPLOAD-WAIT-ADMISSION-NATIVE-01/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/37。非IVD首次注册完整目录、RegistrationDirectory及后续完整模板工作由唯一[IMP-11](IMP-11.md)维护，关联IMP-03/10/12/17—23/26；任务和验收状态继续读取实施任务计划。

BACK-UPLOAD-SERVLET-INPUT-01、IncomingUploadPart.captureRequest、DocumentUploadTransferService.receiveRequest、Spring Servlet非阻塞输入、原总期限、receiver/deadlines Scheduler及HTTP-05/NATIVE-01/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/37。240项完整原生、十二项Servlet及当前PID47704六项实际HTTP通过，341/275项输入和8/1项产物SHA相等；完整制品/运行与全部未完成范围由主记录维护，全部任务状态读取实施任务计划。以下集合保留各自原始范围。

BACK-UPLOAD-OBJECT-DEADLINE-01、ObjectRequestDeadline、S3ObjectReader.TimedAdmission、TransferConcurrency.Lease.remainingTime/guard、SDK apiCallTimeout/apiCallAttemptTimeout及UPLOAD-OBJECT-DEADLINE-NATIVE-02/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/08/12/16/26/37。228项原生、67项独立上传工程及当前PID43011六项实际HTTP通过，334/274项输入与8/1项产物SHA已经核查；实际Get/Head/Put服务暂停与恢复、原对象版本及权限变化由主记录维护，全部任务及验收状态读取实施任务计划。以下集合只保留各自原始范围。

BACK-UPLOAD-COMPLETION-CONCURRENCY-01、TransferConcurrency.Lease.guard、DocumentUploadTransferService.recoverPart/complete/completeHeld/recoverCompletion/inspectCompletion/requireCompletionTransportCurrent及UPLOAD-COMPLETION-CONCURRENCY-GREEN-01/NATIVE-01/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/08/12/16/26/37。224项原生、63项独立上传工程和当前PID39752六项实际HTTP回归通过，输入/产物SHA已核查；全部工单及客户业务状态读取实施任务计划。以下集合仅保留各自原始范围。

BACK-UPLOAD-TRANSFER-CONCURRENCY-01、TransferConcurrency.acquire/Limits/Lease/RetryConfiguration、原RPermitExpirableSemaphore/RBucket、receiveStream及UPLOAD-CONCURRENCY-GREEN-03/NATIVE-01/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/08/12/16/26/37。220项原生及当前PID37756六项实际HTTP回归通过，实际SDK/输入/产物SHA已核查；59项独立上传集合保留原执行范围。以下集合仅保留各自原始范围，当前完整制品/运行由主记录维护，全部工单和业务状态读取实施任务计划。

BACK-UPLOAD-INCOMING-PART-01、IncomingUploadPart.capture/path/close、DocumentUploadTransferService.receiveStream/inspectIncoming/UploadIdentity及UPLOAD-INCOMING-GREEN-02/NATIVE-01/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/08/12/16/26/37。213项完整原生和当前PID35424六项实际HTTP回归通过，输入/产物SHA已核查；独立52项上传集合保留原始范围。以下各集合仅保留原始执行范围，当前制品/运行证据由主记录维护，全部工单和业务状态读取实施任务计划。

BACK-DOCUMENT-UPLOAD-ISSUANCE-01、DocumentUploadIssuance.issue/replay、UploadOperationRow、UploadIssuanceMapper、原同事务许可/幂等/审计/Outbox及UPLOAD-ISSUANCE-NATIVE-01/RUNTIME-01由唯一[IMP-05](IMP-05.md)维护，关联IMP-01/03/04/06/07/12/16/26/37。205项当前原生、实际PID32289和六项HTTP回归通过，完整输入/产物摘要已核查；44项独立上传集合保留原执行范围，全部工单和业务状态继续读取实施任务计划。

BACK-DOCUMENT-UPLOAD-PREPARATION-01、DocumentUploadPreparation.request/prepare、Settings/Prepared、TypedSchemaBindings、原GrantRow/SessionRow/BindingRow及UPLOAD-PREPARATION-NATIVE-01/RUNTIME-01的原始200项和原PID29906范围由唯一[IMP-05](IMP-05.md)维护，关联IMP-03/04/06/07/12/16/26/37。当前完整制品和实际运行使用UPLOAD-ISSUANCE-NATIVE-01/RUNTIME-01；全部客户业务状态仍读取实施任务计划。

UPLOAD-BASIS-BINDING-NATIVE-01/RUNTIME-01的原始196项、原PID27548及六项HTTP回归由唯一[IMP-05](IMP-05.md)维护，保留该次原适用范围；当前完整源码和运行使用UPLOAD-ISSUANCE-NATIVE-01/RUNTIME-01。完整任务与业务状态读取实施任务计划。

BACK-UPLOAD-BASIS-01、BACK-UPLOAD-BASIS-BINDING-01、DocumentUploadBasisProtocol/Mapper/Authority、require/bind/requireBinding、document_basis及UPLOAD-BASIS-BINDING-GREEN-03由唯一[IMP-05](IMP-05.md)维护，关联IMP-03/04/06/07/12/16/26/37。当前任务指针、确认计划、固定快照、完整来源与服务器Settings进入上传写入依据；原保存对象读取保持独立当前读取权限。公开Schema和前端输入保持原字段，完整许可创建、登记及全部客户业务继续按原计划实施。

UPLOAD-IO-NATIVE-01、191项原生及UPLOAD-IO-RUNTIME-01的原应用/六项HTTP回归由唯一[IMP-05](IMP-05.md)维护，保持该次原始输入和制品范围。当前完整依据、制品与运行读取UPLOAD-BASIS-BINDING-NATIVE-01/RUNTIME-01，全部任务和业务验收读取实施任务计划。

BACK-UPLOAD-IO-01、DocumentUploadTransferService、UploadObjectAssembly、partIfPresent、requireVisibleBoundDocument及check_upload_io由唯一[IMP-05](IMP-05.md)维护，关联IMP-03/04/07/08/14/16/26/37。实际分片写入/原写入恢复、固定范围重组、完整大小/摘要、当前权限和完成后原回执属于完整上传消费者；许可创建、产物登记及全部业务入口继续按原计划实施。

UPLOAD-AUTHORIZATION-NATIVE-03/RUNTIME-02、184项原生、当前不可变Jar及实际账户/操作/登录DOM证据由唯一[IMP-05](IMP-05.md)维护；共享SchemaCatalogue及完整发布身份由唯一[IMP-03](IMP-03.md)维护。完整42工单及所有业务、安全、非功能和交接范围继续读取实施任务计划。

BACK-CLASSPATH-SCHEMA-01、SchemaCatalogue.fromClasspath、PublishedSchemaReferencesTest及实际公开原件并发校验由唯一[IMP-03](IMP-03.md)维护，关联IMP-01/04/05/07/37。同一不可变制品共享完整已核查发布Schema，项目读取保留独立身份。

UploadAuthorizationAuthority.requireDocument/requireBoundDocument、UploadAuthorizationSchema.requireBinding、DocumentRow、UploadTransferMapper.document、document.write及UPLOAD-AUTHORIZATION-GREEN-03由唯一[IMP-05](IMP-05.md)维护。当前文稿与原绑定授权核查供完整上传服务使用，合法客户上传和全部业务验收继续保持原要求。

BACK-UPLOAD-AUTHORIZATION-01、UploadAuthorizationProtocol/Schema/Authority、upload-authorization.schema.json、UploadAuthorizationTest及check_upload_authorization由唯一[IMP-05](IMP-05.md)维护，关联IMP-03/04/07/08/14/16/26/37。完整服务器来源、领域及上游要求、原绑定/Schema/依赖和当前文稿版本/签发状态继续按完整上传流程实施。

UploadTransferObjects、storedSize、createSourceSession、createArtifactSession、reserve、beginPart/storePart/failPart、beginCompletion/complete/cancel/expire及原件/数据库执行计划检查由唯一[IMP-05](IMP-05.md)维护。固定分片、原许可/来源身份、全部字段、当前企业事务和主体核查供领域上传继续使用；客户成功与完整业务仍按原验收实施。

BACK-UPLOAD-TRANSFER-01、UploadTransferProtocol/Mapper/Store、UploadTransferTest及check_upload_transfer由唯一[IMP-05](IMP-05.md)维护，关联IMP-03/04/07/08/14/16/26/37。原许可、来源、固定分片及主体/原版本/CAS、未知写入核对和完成登记保持全部原要求。

BACK-UPLOAD-STORAGE-01、object_upload_session、object_upload_part、artifact_upload_binding、026-upload-storage、check_upload_storage及test_upload_storage_runtime由IMP-05维护，关联IMP-03/04/07/08/16/26/37。共用分片保留原资料批次或Artifact许可的独立身份、完整项目/主体、固定范围和版本要求；实际业务入口及客户验收保持原要求。

UPLOAD-STORAGE-NATIVE-02、UPLOAD-STORAGE-GREEN-03、UPLOAD-STORAGE-RUNTIME-01、原生SHOW CREATE TABLE/MySQL XML/ElementTree/SQLGlot、十一组上传复合外键和146企业表的TenantAccessTest检索入口为唯一[IMP-05](IMP-05.md)。原生制品、实际迁移、完整字段/约束、独立账户/原操作及DOM回执分别保持工程范围，完整业务服务和全部客户验收继续按原要求实施。

BACK-OBJECT-WRITE-01、S3ObjectWriter、WriteIntent、writeVerified、recoverVerified、S3ObjectWriterTest及check_object_writer由IMP-05维护，关联IMP-03/07/08/16/26/37。完整上传对象写入及原固定版本恢复必须核验实际内容，受限许可、分片和领域登记继续保持原要求。

BACK-DEPENDENCY-ACCESS-AUTHORITY-01、DependencyAccessAuthority/Protocol/Mapper、DependencySourceAuthority、DependencyAccessAuthorityTest及check_dependency_access_authority由IMP-05维护，关联IMP-03/07/08/14/16/26/37。已有依赖清单通过原主体、完整成员及传递关系独立核验，ArtifactSourceAuthority共同使用完整来源和36类版本授权。合法客户成功、受限上传及登记写入继续保持原要求。

BACK-ARTIFACT-CONTENT-READ-01、ArtifactContentService、ContentReadAuthorization、ContentReadProtocol、ContentReadMapper、ContentReadReceiptTest、IC-05-CONTENT-READ及check_content_read_receipts由IMP-05维护，关联IMP-03/07/08/14/16/26/37。完整对象消费连接每阶段当前准入、每页真实范围、header/range/审计保存和重读；公开发送、TaskToken/lease、受限上传及全部客户成功验收保持原要求。

BACK-VERSION-RESOURCE-AUTHORITY-01、VersionResourceAuthority/Protocol/Mapper、VersionResourceAuthorityTest及check_version_resource_authority由IMP-05维护，关联IMP-03/04/07/08/10/16/23/26/37。36类实际领域实体关联原版本与资源UUID，逐项核验当前读取许可、版本/资源项目、会话范围及上游Artifact固定读取要求；ArtifactSourceAuthority已经接入。真实业务成功、共享决定、实际读取回执和完整内容入口继续按原要求实施。

BACK-SOURCE-READ-AUTHORITY-01、SourceReadAuthority/Protocol/Mapper、SourceRightsSchema、source-rights.schema.json、DependencyLineage、ArtifactSourceAuthority、SourceReadAuthorityTest及check_source_read_authority由IMP-05维护，关联IMP-03/04/07/08/10/16/23/26/37。来源读取固定原范围与解析版本，当前权利、生命周期、会话和正式使用门槛共同核查；每次来源准入重新核查主资源/制品以及全部传递关系。派生资源核验使用VersionResourceAuthority，跨项目共享、读取回执及完整内容入口仍保持原实施要求。

BACK-RESOURCE-READ-AUTHORITY-01、ResourceReadAuthority、PermissionAuthority.requireResource、PermissionMapper.resourceGrants、ArtifactRegistry.requireRegistration、DependencyManifestSchema、ArtifactRegistryMapper及ResourceReadAuthorityTest/ArtifactRegistryReadTest由IMP-05维护，关联IMP-03/04/07/08/16/26/37。具体资源授权及固定登记核验供完整内容服务使用，来源权利与实际读取回执仍按原业务要求实施。

BACK-ARTIFACT-SCHEMA-01、SchemaCatalogue.requirePublished/bindPublished、PublishedSchema、PublishedSchemaReferencesTest及check_published_schema_references由IMP-05维护，关联IMP-03/07/08/16/26/37。固定发布引用及文件摘要共同核验完整UTF8 JSON；客户授权、来源依赖、对象完整字节及读取回执继续按原要求实施。

BACK-ARTIFACT-REGISTRY-01、025-artifact-content-registry、artifact_content_binding、dependency_access_manifest、content_read_range、IC-05-ARTIFACT-REGISTRY及check_artifact_registry由IMP-05维护，关联IMP-03/07/08/16/26/37。主资源、固定Schema、完整依赖及实际字节范围使用原企业/项目复合关联；完整业务授权与客户成功继续按原验收要求实施。

日期：2026-10-05。任务和验收状态统一读取[实施任务计划](../../实施任务计划.md)。本索引登记每项任务的唯一开发记录路径；实施开始时创建相应记录，并将路径改为记录链接。已有 IMP-01/02/03/04/05/06/07/37 的实施登记记录，其余预约路径不表示实现或验收已经完成。

FRONT-WORKBENCH-01、S01、默认首页、PJ-01/PJ-06、当前人员待办、重点项目进度及六项场景入口由唯一[FRONT-WORKBENCH-01](FRONT-WORKBENCH-01.md)维护，关联IMP-04/06/12/29/37；代码状态IMPLEMENTED，完整客户业务验收读取实施任务计划。

当前350项完整构建和本地新Jar部署由IMP-06维护；BACK-SOURCE-UPLOAD-01的公开创建、原声明保存/读取与原响应验证由IMP-08维护，237项迁移和153张企业业务表关联IMP-03。完整KN-02—04、页面、Worker及全部业务验收继续原工单要求。

检索顺序：按任务编号、业务名称、模块和关联需求寻找记录，再读取当前代码及设计依据。每项记录维护具体实现、测试与证据，索引只维护入口和关联关系。

BACK-OBJECT-PERSISTENCE-01、StoredObjectReferences、S3ObjectReader.readStored、StoredObjectReferencesTest、IC-05-OBJECT-PERSISTENCE及check_stored_object_references由IMP-05维护，关联IMP-03/07/08/16/26/37。存储JSON与原五标量逐项核查，精确U64转换及当前MySQL和固定原件读取保持完整要求；完整Artifact授权和业务回执继续实施。

BACK-OBJECT-ASSEMBLY-01、ObjectContentAssembly、ObjectContentPaging.readComplete、完整内容重组、CONTEXT_LIMIT和CONTENT_MISMATCH由IMP-05维护，关联IMP-03/07/08/14/16/26/37。内部内容消费固定原版本、连续范围、完整摘要及当前准入，完整Artifact业务及读取回执继续按照原要求实施。

BACK-OBJECT-PAGING-01、ObjectContentPaging、ObjectContentPagingTest、IC-05-OBJECT-PAGING、check_object_paging、BASE64、Coverage、Nimbus JWE及字节范围摘要由IMP-05维护，关联IMP-03/07/08/16/26/37。当前实现提供固定对象的实际字节分页，企业许可、Artifact资源绑定、Schema及读取回执继续按完整业务要求实施。

企业登记申请、LOCAL-REG-01—06、EnterpriseRegistrationService/Schema、EnterpriseRegistrationReviewService/Schema、EnterpriseProvisioningMapper、platform_enterprise_registration_decision及首位管理员治理均由IMP-04维护；实际公司、人员及批准依据由用户通过产品入口填写，完整审批成功、角色配置与项目授权继续验收。

企业空间选择、enterprise-context.html/js、ID-03/04、BACK-CONTEXT-01、BACK-SELECTION-OPTIONS-01、TenantSelectionOptions/Schema、GET /api/session/tenant-selection-options、IC-04-CONTEXT-BROWSER及check_enterprise_context_browser由IMP-04维护，关联IMP-01/03/06及后续角色、项目治理。真实成员成功选择与正式Vue页面保持原验收条件。

企业岗位治理、BACK-ROLE-GOVERNANCE-01、ROLE-01—04、EnterpriseRoleController/Service/Schema/Mapper、enterprise_role_head/publication、member_role_grant、enterprise-roles.html/js、check_enterprise_roles、check_enterprise_role_browser及邀请授权来源重读由IMP-04维护，关联IMP-03/05/06/24/37；完整真实岗位发布、撤销及项目业务保持原验收条件。

预算前端、FRONT-BUDGET-MANAGEMENT-01、/agents/budgets、budgets.ts、budget-write-view.schema.json、budget-account.vue、budget-write.vue、budgetActionAllowed、prepareBudgetWrite、submitBudgetWrite、confirmBudgetOperation及BD-01—04由IMP-07维护，关联IMP-06/05；正式Layout/Router/Pinia/Axios/Element Plus、完整六项限制、固定批准、原请求查询与当前版本核查保持完整要求，真实客户成功、并发及撤权继续验收。

预算批准历史、BACK-BUDGET-HISTORY-01、BD-05/06、BudgetHistorySchema、budget-history.schema.json、historyRevision、listBudgetHistory/readBudgetHistory、budget-history.vue和budget-history-entry.vue由IMP-07维护，关联IMP-06/05。原预算manifest、当前权限、原批准定义与来源摘要保持完整；错误编码由IdentityErrorResponseWriter及OidcSecurityTest核查，check_budget_errors保存原生Red。

项目治理、BACK-PROJECT-SERVICE-01、PJ-01—06、ProjectController/Service/Schema/Mapper、projects.schema.json、project_record、project_state_decision、project_profile_revision、check_projects及check_project_sql_columns由IMP-04维护，关联IMP-03/05/06/12/13/24/37；名称和负责人、UNKNOWN属性、原依据、CAS、原响应、审计及Outbox保持完整，正式S03/S18及真实客户成功路径继续验收。

本地私有对象存储、BACK-OBJECT-STORAGE-01、BACK-OBJECT-READ-01、S3ObjectReader、VerifiedObject、ObjectRef、StoredObjectReferenceTest、S3ObjectReaderTest、LOCAL-OBJECT-STORAGE-01、SeaweedFS 4.48、S3、SSE-S3、对象版本及公开原件检查由IMP-05维护，关联IMP-03/07/08/16/26/36。完整对象业务授权、读取回执、分片上传、审计、KMS、恢复和真实客户流程保持原验收要求。

| task_id | 业务名称 | 模块与关键词 | 关联需求 | development_record |
| --- | --- | --- | --- | --- |
| IMP-01 | 正式工程与版本登记 | RuoYi、SDK、构建、命令、WEB-DEPENDENCY-01、check_web、web-compatibility-checks、SvgIcon、IconSelect、createCacheChartOptions、ECharts、SVGO、Redis INFO | 全部需求；IC-01 | [IMP-01.md](IMP-01.md) |
| IMP-02 | 真实输入、岗位和参数登记 | D0—D8、授权、UNKNOWN | 全部需求；IC-02 | [IMP-02.md](IMP-02.md) |
| IMP-03 | 共享 Schema 与完整迁移 | MySQL、DTO、Schema、Pydantic、DatasetBindings、ModelStreamBindings、ResourceBindings、artifacts.py、interfaces.py、OpenDomainModel、JsonValue、ErrorCode、ERROR_CODES、SchemaRejection、SchemaValidationError、published_error_codes、TypedPayload、DomainModel、configuration_head、invocation、stream_registry、bindModelStream、ModelProtocol、CommonProtocol、ResourceProtocol、DatasetProtocol、InterfaceProtocol、InterfaceBindings、ArtifactProtocol、ArtifactBindings、OpenObject、TypedSchemaBindings、bindResourceDetails、bindArtifact、ProfessionalArtifact、bindInterface、RepresentationVisitor、run_migration | FR-01—09；NFR-07 | [IMP-03.md](IMP-03.md) |
| IMP-04 | 独立身份、企业和项目授权 | C02、BACK-LOCAL-01、BACK-CONTEXT-01、ID-03、ID-04、TenantSelectionService、TenantSelectionMapper、SelectionOperationRow、context_id、identity-schema-manifest、ApplicationSessionIdentity、BusinessSessionProtocol、BusinessSessionSchema、BusinessSessionController、BusinessSessionDirectory、MemberDiscovery、TenantMemberMapper、platform_member_route、platform_session_context、IdentityMode、LocalAccountDirectory、LocalPrincipal、LocalAuthenticationProvider、LocalIdentitySecurityConfiguration、LocalIdentityController、platform_local_account、platform_local_session、platform_local_bootstrap、MedicalApplication、local_runtime、check_local_identity、recover_owner、credential_revision、failed_attempts、flock、OIDC、PKCE、CSRF、Origin、medical-identity、OidcPolicy、RequiredOidcProofs、MedicalSessionConfiguration、PlatformIdentityDirectory、PlatformOidcSessions、BackchannelLogoutVerifier、VerifiedLogoutToken、CanonicalOidcKey、OidcClientLedger、BackchannelLogoutPersistence、LogoutPersistencePolicy、platform_logout_receipt、platform_logout_session、medical-persistence、TenantScope、ProtectedTenantInterceptor、medicalSqlSessionFactory | FR-01、FR-08；AC-01/02/16 | [IMP-04.md](IMP-04.md) |
| IMP-05 | 幂等、对象读取、事件与审计基础 | BACK-DOCUMENT-UPLOAD-ISSUANCE-01、DocumentUploadIssuance.issue/replay、UploadOperationRow、UploadIssuanceMapper、DocumentUploadPreparation、UploadTransferStore、TenantScope、BX-03/04/KN-02、BACK-OBJECT-READ-01、S3ObjectReader、VerifiedObject、ObjectRef、StoredObjectReferenceTest、S3ObjectReaderTest、BX-37、content_read_receipt、BACK-OBJECT-STORAGE-01、LOCAL-OBJECT-STORAGE-01、SeaweedFS、Boto3、SSE-S3、local_object_storage、check_object_storage、test_object_storage_recovery、BACK-OPERATION-READ-01、BX-31、OP-01、OperationController/Service/Schema/Mapper、operation.read、OPERATION_READING、dedup_expires_at、dedup_policy_revision、check_operations、Outbox | FR-03—05、FR-09；NFR-02/06 | [IMP-05.md](IMP-05.md) |
| IMP-06 | 若依组件、页面框架及管理模块接入 | C01、Router、Pinia、Cookie、CSRF、Axios、MedicalSession、S03/S17/S18、IC-06-SESSION-BROWSER、FRONT-ACCOUNT-01、LOCAL-06、OwnAccount、PendingCredential、changeMedicalCredential、IC-06-ACCOUNT-BROWSER、FRONT-PROJECT-JSON-01、project-json、readProjectJson、formatProjectJson、writeProjectJson、LosslessNumber、jsonc-parser、json-custom-numbers、FRONT-PROJECT-WRITE-01、project-write.ts、project-write-view.schema.json、PendingProjectWrite、project-write.vue、project-attributes.vue、operations.ts、schemaIssues、PJ-02/04/05、BX-31/OP-01 | FR-01—09；NFR-04/05 | [IMP-06.md](IMP-06.md) |
| IMP-07 | 模型配置、预算、调用与结算 | C07、invocation、DeepSeek、medical_model_gateway、SDK、密钥、StreamEvent、MODEL-PRICING-02、pricing.py、upper_amount、actual_amount、缓存写入、舍入规则、未知金额、BACK-BUDGET-MANAGEMENT-01、BD-01—04、BudgetController/Service/Schema/Protocol/Mapper、budgets.schema.json、budget.approve、BUDGET_APPROVAL、BUDGET_READING、check_budgets | FR-02、FR-09；AC-03/04/24/28 | [IMP-07.md](IMP-07.md) |
| IMP-08 | 批量上传、七格式解析和原件阅读 | C03、C09、locator、OCR | FR-03；AC-05/06/29/30 | [IMP-08.md](IMP-08.md) |
| IMP-09 | 完整索引、授权检索与网络候选 | RetrievalController、RetrievalService、RetrievalMapper、retrieval_query、retrieval_hit、source-search.vue、FULLTEXT、C03、C10、Qdrant | FR-03；AC-05/30/32/33 | [IMP-09.md](IMP-09.md) |
| IMP-10 | 核准、资料集、来源关系与生命周期 | SourceGovernanceController、SourceGovernanceService、source-catalogue.vue、source-governance.vue、KN-05/06/07/08/09/10/16/17/20/21/22、source_folder、source_organization、user_source_bookmark、processing_attempt、project_source_binding、source_lifecycle_state、source_lifecycle_decision、retention_policy_revision、deletion_job、deletion_item、collection_member、scope_member、evidence_link、lineage_edge、reference_hold、CollectionService、CollectionAuthority、CollectionMapper、collections.schema.json、PageHelper、KN-11/12/13、治理 | FR-03；AC-05/06/31/34/36/57 | [IMP-10.md](IMP-10.md) |
| IMP-11 | 模板目录、字段及适用条件 | RequirementNode、模板、UNKNOWN、BACK-TEMPLATE-DEFINITION-01、TemplateProtocol、TemplateDefinitionSchema、templates.schema.json、十三文档、十一字段、重复行、附件、TP-01/02 | FR-05、FR-09；AC-18/45/46/50/57 | [IMP-11.md](IMP-11.md) |
| IMP-12 | 项目任务、来源快照与计划决定 | C04、TaskRevision、ScopeSnapshot、SourceScope、PlanCandidate、task_input_revision、plan_content_revision、BACK-TASK-PLAN-01、BACK-TASK-STORAGE-01、BACK-TASK-SOURCE-CANDIDATE-01、BACK-SCOPE-MANIFEST-01、ScopeSnapshotSchema、ScopeSnapshotManifest、ScopeSnapshotMember、SnapshotRequirement、requireCandidate、snapshotCandidate | FR-04、FR-09；AC-07/08/18/31/37 | [IMP-12.md](IMP-12.md) |
| IMP-13 | Temporal 调度、等待、取消与恢复 | C11、Temporal、local_temporal、Signal、lease、run、execution_registry、system_job_run | FR-02、FR-09；AC-04/20/21/39/41/44 | [IMP-13.md](IMP-13.md) |
| IMP-14 | Harness profile、工具与内部适配 | C12、medical-agent、TypeScript | FR-02、FR-09；AC-23/24/26/27/28 | docs/development/IMP-14.md |
| IMP-15 | 规划、研究、生成和独立检查节点 | N00—N09、EvidenceBundle、guard | FR-04、FR-09；AC-18/19/20/25/39 | docs/development/IMP-15.md |
| IMP-16 | 同版正文、候选比较及连续编辑 | S09、Editor、CAS、AST | FR-05、FR-09；AC-22/38 | docs/development/IMP-16.md |
| IMP-17 | 产品技术要求及指标方法 | PTR、型号、指标、方法 | FR-05；AC-09/10/47/48/49 | docs/development/IMP-17.md |
| IMP-18 | 注册目录、监管综述及质量体系 | 六章、38节点、QMS | FR-05；AC-45 | docs/development/IMP-18.md |
| IMP-19 | 非临床研究、基本原则与风险 | 研究、基本原则、风险 | FR-05；AC-50/51/52 | docs/development/IMP-19.md |
| IMP-20 | 临床评价与免评价资料 | 临床、免评价、对比 | FR-05；AC-53 | docs/development/IMP-20.md |
| IMP-21 | 说明书与独立标签样稿 | IFU、标签、警示 | FR-05；AC-49/54 | docs/development/IMP-21.md |
| IMP-22 | IVD、变更和延续路径 | IVD、注册路径、模板 | FR-05；AC-46 | docs/development/IMP-22.md |
| IMP-23 | 当前正文检查、人工审查与签发 | CHECK_DOCUMENT、S11、SG | FR-05、FR-09；AC-09/11/19/25/48 | docs/development/IMP-23.md |
| IMP-24 | 共享事实、依据更新与撤权影响 | FactUsage、权限水位、影响 | FR-01、FR-03、FR-05；AC-35/40/54/55/57 | docs/development/IMP-24.md |
| IMP-25 | Gru、显式采纳、摘要和习惯 | Gru、建议、选区、记忆 | FR-04、FR-09；AC-37/38/43 | docs/development/IMP-25.md |
| IMP-26 | 固定成品导出与成果转存 | C08、DOCX、PDF、转存 | FR-03、FR-05、FR-09；AC-34/42/47/56 | docs/development/IMP-26.md |
| IMP-27 | 运行工作区、公开流与人工继续 | S06、SSE、Last-Event-ID、待办 | FR-04、FR-09；AC-08/21/39/40/41/44 | docs/development/IMP-27.md |
| IMP-28 | 评测、运营、反馈与能力发布 | C06、EVAL、CP、发布清单 | FR-02、FR-06；AC-03/12/13/27 | docs/development/IMP-28.md |
| IMP-29 | 场景目录与未定义场景控制 | 场景、入口、规格 | FR-07；AC-14/15 | docs/development/IMP-29.md |
| IMP-30 | 客户 B/S 连接与真实同步 | B/S、映射、同步、SEC-28 | FR-08；AC-17 | docs/development/IMP-30.md |
| IMP-31 | 法规比较场景 | 法规、版本、比较 | FR-07；AC-14 | docs/development/IMP-31.md |
| IMP-32 | 报告生成场景 | 报告、用途、成品 | FR-07；AC-14 | docs/development/IMP-32.md |
| IMP-33 | 翻译场景 | 翻译、语种、术语 | FR-07；AC-14 | docs/development/IMP-33.md |
| IMP-34 | 市场分析场景 | 市场、口径、证据 | FR-07；AC-14 | docs/development/IMP-34.md |
| IMP-35 | 多模态问答场景 | 多模态、问答、定位 | FR-07；AC-14 | docs/development/IMP-35.md |
| IMP-36 | 环境、监控、制品与恢复准备 | 部署、秘密、trace、备份、BACK-LOCAL-DELETION-STORAGE-01、local_vector_storage、local_deletion_runtime、check_local_database_restore | FR-08；NFR-02/06/07/15 | [IMP-36.md](IMP-36.md) |
| IMP-37 | 注册业务与边界集成验收 | TEST、EDGE、QA、下游使用、check_full_function_inventory、full-functional-report、IC-04-AUTH-ERRORS | AC-01—57 中本项55项；IC-37 | [IMP-37.md](IMP-37.md) |
| IMP-38 | 安全配对与制品检查 | SEC、TH、合法与拒绝 | NFR-03/11/13；IC-38 | docs/development/IMP-38.md |
| IMP-39 | 非功能、兼容与恢复验收 | NTEST、SLO、RTO、RPO | NFR-01—17 中本项14项；IC-39 | docs/development/IMP-39.md |
| IMP-40 | SaaS 发布与使用交接 | SaaS、迁移、用户说明 | R08、FR-08；IC-40 | docs/development/IMP-40.md |
| IMP-41 | 私有化部署与维护交接 | 私有化、升级、运维 | R08、FR-08；IC-41 | docs/development/IMP-41.md |
| IMP-42 | 配套服务与责任交接 | 服务、权限、专业审核 | R08、FR-08；IC-42 | docs/development/IMP-42.md |

IMP-04的本地账户管理检索字段为BACK-LOCAL-MANAGEMENT-01、LOCAL-01—06、LocalAccountManagement、LocalAccountManagementController、LocalAccountSchema、LocalAccountProtocol、LocalManagementPolicy、platform_local_operation、platform_local_audit、management-request.js、hmacKeyId；主记录路径为docs/development/IMP-04.md，关联IMP-01/02/03/05/06。

IMP-04的真实浏览器检查检索字段为IC-04-LOCAL-BROWSER、check_local_browser、local-account-checks.js、browser_identity、storageHasNoSecrets、headless Chrome、Playwright、原请求及审计重读；沿用当前主记录，关联IMP-01/02/06。

IMP-04的本人邀请身份检索字段为BACK-IDENTITY-BINDING-01、AuthenticatedIdentityBinding、IdentityBindingSchema、identity-binding.schema.json、identity-binding-manifest.json、GET /api/session/identity-binding、data-identity、data-mode；沿用当前主记录，关联ID-03及ID-07/08、IMP-01/03/06。

同一任务的修复与复验继续更新同一记录。关联前置和后续任务使用实施计划的 `depends_on` 及验收责任表检索，避免复制另一份依赖清单。

IMP-04的企业成员认证绑定检索字段为BACK-MEMBER-IDENTITY-01、mode、local_user_id、017-member-identity-mode.sql、readBinding、matches(View)、MemberInvitationBindingIT、check_member_binding、check_member_identity、IC-04-MEMBER-BINDING、IC-04-MEMBER-IDENTITY；沿用当前主记录，关联IMP-01/03及ID-07/08。

IMP-04的邀请保存检索字段为BACK-MEMBER-INVITATION-01、member_invitation、platform_invitation_route、018-member-invitation.sql、InvitationCredential、credential_sha256、pending_user_id、authorization_manifest、check_member_invitation、IC-04-MEMBER-INVITATION；沿用当前主记录，关联IMP-01/03/05/06及ID-07/08/09。

IMP-04的企业权限决定检索字段为BACK-PERMISSION-01、ID-09、EnterpriseAccess、PermissionProtocol、PermissionSchema、PermissionAuthority、PermissionDecisionService、PermissionDecisionController、PermissionMapper、permission-decisions.schema.json、GRANT、REVOKE、DISABLE_MEMBER、PERMISSION_ADMINISTRATION、check_permission_decision、IC-04-PERMISSION、PermissionStructureTest、check_permission_sql_columns；沿用当前主记录，关联IMP-01/03/05/06及ID-07/08。

IMP-04的邀请服务检索字段为BACK-INVITATION-SERVICE-01、ID-07/08、MemberInvitationService、MemberInvitationController、InvitationAccess、InvitationMapper、InvitationSchema、InvitationProtocol、InvitationResponseCipher、InvitationSecurityTest、INVITATION_ADMINISTRATION、enterprise_role_version、019-enterprise-role-version.sql、member-invitations.schema.json、invitation_secret、check_member_invitation_flow、IC-04-INVITATION-FLOW、check_invitation_sql_columns；沿用当前主记录，关联IMP-01/02/03/05/06。

## 2026-10-18 前端优先增量与发布安排

- [当前前端剩余清单](../frontend/remaining-frontend-20261018.md)：按真实Controller区分可立即消费、待联调和后端缺口；不替代42项主工单状态。
- [工作台与访问恢复](../frontend/workbench-access-acceptance.md)：S01/S17，权限与异步竞态复核。
- [上传队列与逐文件操作](../FE-08-upload-workspace-2026-10-18.md)：真实上传/恢复及逐文件确认。
- [首次任务启动](FRONT-RUN-CREATE-01.md)：RN-01、固定计划/快照、实际预算及未知结果恢复。
- [指定时间main提交预检](SCHEDULED-MAIN-PUBLISH-20261019.md)：北京时间2026-10-19 06:30，尚未执行提交或推送。
- [当日整合开发记录](../接管开发记录-2026-10-18.md)：验证范围、恢复包和环境阻塞。

- [SIM输入制品登记063](SIMULATION-INPUT-ARTIFACT-063.md)：固定ObjectRef/raw SHA、当前授权与原请求。
- [SIM规则独立流程064](SIMULATION-RULE-WORKFLOW-01.md)：不可变用途binding、批准证据和legacy隔离。
- [评测依赖用途集成方案](SIMULATION-DEPENDENCY-USE-PLAN.md)：方案与当日整合记录联合阅读，不以早期计划替代当前实现证据。

第十三阶段增量：真实原请求lookup与跨scope输入登记见 [SIMULATION-INPUT-ARTIFACT-063.md](SIMULATION-INPUT-ARTIFACT-063.md)；企业评测材料见 [SIMULATION-EVALUATION-PREPARATION-01.md](SIMULATION-EVALUATION-PREPARATION-01.md)；SIM规则界面见 [FRONT-SIMULATION-RULE-MANAGEMENT-01.md](FRONT-SIMULATION-RULE-MANAGEMENT-01.md)；[授权用途见证](../backend/simulation-authorized-use-evidence-20261018.md)、[准备页面](../frontend/simulation-preparation-20261018.md)、[企业上传](../frontend/enterprise-source-upload-20261018.md)记录独立边界。整体仍为部分开发检查点，不是全部功能/端到端验收通过。

第十四阶段：固定文稿记录见 [GRU-RECORD-ONLY-01.md](GRU-RECORD-ONLY-01.md)、[本地合同复核](GRU-RECORD-ONLY-CONTRACT-REVIEW.md)、[066迁移验证](GRU-066-MIGRATION-VALIDATION.md) 和 [Gru前端](../frontend/gru-records-20261018.md)；历史结果当前用途见 [SIM结果legal-use](../backend/simulation-result-current-legal-use-20261018.md)。这些是局部功能及离线验证，不是完整Gru模型执行或全项目验收。

后续正式增量： [073正式能力草稿](FORMAL-CAPABILITY-AUTHORING-073.md)、[074保存策略](RUNTIME-RETENTION-074.md)、[供应商原始响应捕获](PROVIDER-RESPONSE-CAPTURE-01.md)。完整42项差距以 [真实剩余清单](REMAINING-FUNCTIONS-20261018.md) 为准，不以测试数量替代业务完成判断。
