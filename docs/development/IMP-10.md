# IMP-10 核准、资料集、来源关系与生命周期

## 当前人工权利决定

2026-10-14来源权利审查、APPROVE/RETURN/REVOKE、实际授权Artifact及完整来源关系核验、固定S3版本与完整字节证明、权利指针及修订CAS、原操作重放、审计及Outbox已进入当前固定制品。实现及17项Java、20项完整迁移、真实对象恢复与数据库重读证据由唯一[IMP-08](IMP-08.md)维护；050及当前286项登记由[IMP-03](IMP-03.md)维护。该权利决定独立于source_use_gate、解析、索引及项目适用决定，当前没有新增批准记录。明确服务器政策和真实客户许可缺少时审批保持关闭；全部合法核准、完整来源采用、六类存储删除与永久处理继续本工单原验收要求，状态保持IN_PROGRESS。

## FRONT-SOURCE-RIGHTS-01

2026-10-14；状态来源为实施任务计划IMP-10的IN_PROGRESS；文档有效性CURRENT。依据前端实施计划FE-08/10、PRD的FR-03及BR-13、LLD-前端S04/S05和接口补充设计SOURCE-RIGHTS-DECISION-01。已读取当前SourceRightsController、SourceRightsService、SourceRightsDecisionSchema、完整source-rights-decisions.schema.json、原请求元数据、资料目录、会话及原操作查询。用户授权SIMULATION资料用于开发，正式客户核准仍须独立验收。

本项接入rights-reviews和rights-decisions，完整提供APPROVE、RETURN、REVOKE及所有Proposal字段。审查固定原来源版本、权利版本和摘要、修订、授权Artifact及完整决定；决定提交使用原请求键与原上下文，未知结果通过原操作查询及同一请求恢复，理由和许可正文只保留受控内存。保存以后重新读取当前会话、实际原操作和来源元数据，核查权利指针与状态。身份、企业、页面及输入变化清理原审查，不创建替代成功结果。

Given原公开Schema及实际页面；When提交完整范围或原请求恢复；Then必需字段、READ/STORE、外发提供方、UTC时间、唯一授权身份和U64修订保持完整，审查变化以后原决定不能继续提交。组件、真实接口、浏览器和受影响工程验证分别登记实际结果，当前尚未登记本项验收通过。关联IMP-04/05/08/36/37，主记录保持本文件，索引维护检索入口。

页面入口为`/library/versions/:sourceVersion/rights`，资料目录提供当前许可对应的审查链接。实现文件为`web/src/api/medical/source-rights.ts`、`web/src/views/medical/source-rights.vue`，原`protocol`登记全部公开定义，`request-metadata`登记KN-RIGHTS-01。原操作查询使用FOUND/NOT_FOUND/EXPIRED，只有原参数摘要一致时恢复同一请求；页面真实离开产生的取消进入完整浏览器检查，没有替代网络响应。Spring页面入口同步提供资料版本、权利、检索、处理及成果路径。

有效Red为`.implementation-work/source-rights-schema-red.log`：原接口已加载，完整批准请求因缺少权利定义被拒绝。当前医疗strict类型、16组计划/Schema检查、正文原字节与编辑回归、完整medical生产构建通过，回执`.implementation-work/IC-FRONT-CONTINUE-RELEASE-20261014-RIGHTS-01/receipt.json`，SHA-256为`aceaff9b3e332d997472208a1bde78bcd6aa48354d0d19d3e507a11337e076e4`，exit_code=0、inputs_unchanged=true，8719项输入及344项产物。读取源码时一并固定全部身份及持久模块的API Schema，当前完整浏览器与业务结果继续验证。

实际业务入口为`scripts/check_source_rights_frontend.py`、`SourceRightsSimulationHttpTest`与`tests/browser/source-rights-checks.js`。模拟企业、来源、待审权利及独立授权文本使用完整当前数据库字段；授权文本由原TextContent定义校验、实际对象服务保存、完整版本与字节再次核验。服务端仅在临时检查应用显式配置LOCAL环境、区域及完整证据政策，常驻审批配置保持原值；模拟输入不能替代正式客户许可和岗位分离。原对象、操作、CAS、审计及事件均由实际生产组件处理。

原请求查询、证据字节核查及最终提交分别核验当前上下文。查询NOT_FOUND以后若原请求完成并改变上下文，重复提交可以取得真实CONTEXT_CHANGED；页面显示实际错误，安全元数据继续保存。明确重新读取会话和查询原操作后，使用相同幂等键、原输入及原上下文取得COMPLETED原回执，随后核对当前来源。浏览器检查同时保留该并发分支的拒绝及明确恢复动作，没有自动写入重试。

权利组件的开发和生产各六项检查通过，回执为`.implementation-work/IC-FRONT-CONTINUE-SOURCE-RIGHTS-20261014-GREEN-02/receipt.json`，SHA-256为`c5b4ec1270f19aff00f32f3b665d23069fa0cb827af2a44d33959dd3fd5354ca`，8884项输入和348项产物，exit_code=0、inputs_unchanged=true。后续上传结果消费更新共享protocol，该回执证明执行时的完整输入；当前共享源码由完整工程及真实浏览器检查继续核验。

本轮真实业务验证使用`medical-isolated-check` Maven Profile；显式`medical.check.output-root`将每个模块的生成物保存到本次独立目录，全部依赖、组件及原断言继续执行。该参数未提供时使用原工程输出。SourceGovernanceService按UTC Instant输出完整date-time，保留原时间精度；实际决定回读和Schema验证共同核查该字段。

来源生命周期的读取及更新查询通过正式`project.status`取得所属项目状态；企业来源保持空项目状态。SourceGovernanceMapper维持完整企业条件、原件身份、生命周期修订及更新锁，权利页面和上传结果共同消费该查询。该接口的真实决定后重读、全部字段及原操作继续按本项完整检查验证。

NATIVE-10实际JUnit一项通过，失败、错误、跳过均为零。临时应用执行RETURN、APPROVE、REVOKE三项决定；每项原键重放、异参冲突、原修订冲突、实际SourceGovernance及COMPLETED原Operation重读通过。实际S3授权全文摘要、三项决定/操作/审计/Outbox数量及撤销source.approve后的403通过，本次项目计数恢复，临时应用已关闭。输入标记SIMULATION，customer_success_verified=false及professional_source_adoption_verified=false。

原始结果为`.implementation-work/IC-10-SOURCE-RIGHTS-20261014-NATIVE-10/result.json`和该目录独立保存的JUnit。原命令回执SHA-256为`c4edb3316d9c1569930d86194883bee8f585a3cfbeaa3fa1b877ea07906c4297`，exit_code=0、inputs_unchanged=false；共享后端源码在执行期间变化，因此没有登记整体当前源码通过。当前完整前端构建和开发/生产浏览器仍继续执行。

当前完整前端EXACT-RECOVERY-10通过：strict、十八项计划/Schema及UUID、正文编辑及原字节回归、九项上传结果、七项索引关系、三十三项EXACT参数与完整medical生产构建。回执为`.implementation-work/IC-FRONT-CONTINUE-RELEASE-20261014-EXACT-RECOVERY-10/receipt.json`，SHA-256为`1ead72ad39ff5acf2315b0ce8a8ae3906306f0ef0f547444cf0d54e07a244509`，8738项输入及358项产物，exit_code=0、inputs_unchanged=true；构建1分34秒。当前真实浏览器使用这份制品。

BROWSER-31实际原生及开发/生产浏览器组合通过，回执及完整五十项范围由[IMP-09](IMP-09.md)维护；两种环境各八项权利断言、三个唯一原决定键、完整授权Artifact及原操作恢复、RETURN/APPROVE/REVOKE与当前来源重读均通过。真实资料退回/重新核准和项目适用决定的完整版本登记及摘要也已核验；六类存储删除、备份治理、完整生命周期及客户采用继续原验收范围。

两家本轮模拟企业的清理需要明确批准。只读清单`.implementation-work/source-simulation-21-inventory.json`记录三个项目、五个任务、一份文稿和一份来源资料；脚本`.implementation-work/cleanup-source-rights-simulation-21.py`限定清单企业及其关联记录。当前数据保留，审批与数据清理独立于已通过的BROWSER-31及新建项目计数恢复检查。

## FE-10 资料目录与当前页面

2026-10-13资料目录读取当前获授权文件夹、筛选结果及固定游标；重复资料和没有前进的游标立即拒绝。页面离开或会话变化后清理资料、选择、文件夹输入及批量维护参数。文件夹和批量资料维护在每次等待及写入完成后核查原会话及当前页面；旧结果无法更新新的企业页面。代码为source-catalogue.vue，输入清理检查由[IMP-04](IMP-04.md)维护。核准、关系、资料集、完整生命周期和逐存储清理继续保持本工单原验收范围。

## BACK-SOURCE-DELETION-01 删除执行与持久 Worker

2026-10-13；任务来源为[后端实施计划](../后端实施计划.md) BE-10及[实施任务计划](../../实施任务计划.md) IMP-10，状态保持 IN_PROGRESS。需求引用 AC-36、NFR-15、LLD-数据§6.5及当前六类删除目标。

当前代码提供 SourceDeletionWorker、SourceDeletionAccess、SourceDeletionPolicy、SourceDeletionStores、SourceDeletionTarget、SourceDeletionConfiguration 与 SourceDeletionMapper。Worker 使用原删除作业建立者，逐次核查当前本地或 OIDC 身份、成员、source.purge、SOURCE_PURGE、批准保存期限、引用及保留限制；企业由服务端名单确定。每项操作通过原 TenantTransaction 与 TenantScope 执行，租约具有 owner、generation、expires_at 及 revision，续期和回执写入使用 CAS。配置缺少或策略未批准时关闭永久删除，错误直接传播。

ORIGINAL 核查全部对象版本及固定摘要，并清理同一 object_upload_session 的实际原件分段；PARSED 清理固定解析对象、source_chunk 原文与搜索文本、content_node 内容和定位；FULLTEXT 清理全文并撤回索引；VECTOR 使用官方 Qdrant Java Client，按企业、来源、解析和索引四项过滤删除并查询精确数量；CACHE 查询批准命名空间中的实际键；DIAGNOSTIC 清理版本化对象、解析和处理失败信息、阅读定位。每项删除后查询实际剩余内容；作业恢复和最后一项确认前重新核查已确认目标。

当前ORIGINAL同时查询source_upload_result中属于固定来源版本的全部原件副本和对应上传分段，每个对象继续核验固定版本、容量与独占引用。其他来源版本仍引用同一对象时拒绝清理；原件删除及不存在确认覆盖完整查询集合。此关联代码由当前上传完成制品消费，完整客户删除及永久清理仍未执行，批准保存与备份政策继续原要求。

备份证明采用封闭 source-deletion.schema.json、RFC8785 和 HMAC-SHA256，必须覆盖固定企业、来源、存储与完整副本目录，证明时间覆盖原删除查询。Worker 核查批准期限和实际对象版本不存在，共享备份由备份治理服务依批准期限处理。当前本地真实数据库备份、向量快照及恢复检查副本继续登记；完整副本范围与保存期限未核定时，不产生 complete=true 的证明，不确认 PURGED。

领取、原存储查询确认与最终回执均保存审计及 Outbox。最终回执包含 deleted_at、query_confirmed_at、backup_confirmed_at 与 evidence_sha256；公开回执接口只接受已由 Worker 保存的实际证明。全部删除项确认后复查引用，提交 COMPLETED、PURGED 和治理水位，并重新读取最终状态。

| 当前验证 | 证据与范围 |
| --- | --- |
| SourceDeletionBootstrapTest | 三项通过：关闭未配置 Worker、拒绝缺少企业上下文的 SQL、使用实际 MyBatis 解析全部删除 Mapper；`.implementation-work/deletion-current-package-20261013.log` |
| SourceDeletionVectorConnectionTest | 两项实际官方 Java gRPC 检查通过：授权连接取得版本和集合目录，缺少身份返回 UNAUTHENTICATED；`.implementation-work/deletion-current-package-20261013.log` |
| 应用与迁移制品 | 当前十模块构建通过；MigrationBundleTest 解析264项 changeset，实际数据库新增269、270、271并重读五项 Worker 字段与租约约束；运行及恢复证据由 [IMP-36](IMP-36.md) 维护 |
| 本地删除输入 | 当前实际数据库来源1项、删除作业0项、批准保存策略0项；永久删除关闭，真实六类删除、重复、竞争、撤权及业务恢复仍待验证 |

Data 迁移和工程构建已有当前证据。Backend 与 Worker 已提供上述代码，六类客户业务删除、完整签名备份目录、批准保存与备份期限、恢复及全部 IC-10 继续 PENDING。历史 SIMULATION 内容不作为本次真实业务验收输入。处理 Worker 由 IMP-08 维护，索引 Worker 由 IMP-09 维护；工作目录保留既有未提交内容，基准提交为 `2fb17df5a46bc7fcaf8cb4ae434c4ac1c9a0c8a3`。

## BACK-SOURCE-GOVERNANCE-01 资料列表、固定资料版本与人工核准

2026-10-11；状态保持IN_PROGRESS。`GET /api/source-versions/{id}`返回原件身份、权利、同一资料的全部历史版本、解析版本、索引版本、当前门槛、历史决定及审核清单摘要。历史版本包含版本编号、发布版本、原件摘要、权利版本、生命周期和建立时间，S05可以进入任一固定版本。`POST /api/source-versions/{id}/approval-decisions`支持APPROVE、RETURN和DISABLE，分别核查资料读取、项目读取及`source.approve`。

`GET /api/source-versions`已经提供KN-05资料列表，支持项目范围、归属、核准状态、生命周期、当前用户收藏状态、类别、标签、资料集版本和游标分页。分页前逐项核查当前`source.read`，总数和下一页游标仅来自当前可读取集合。S04资料中心提供全部资料、收藏、已停用和回收区常用视图，并提供继续分页、全文检索、资料集和固定版本详情入口；身份、企业或页面可见性改变时终止读取并清除内容。

`POST /api/projects/{id}/source-bindings`已经提供KN-10项目适用决定，使用当前项目属性版本及摘要，支持APPLICABLE、NOT_APPLICABLE和UNKNOWN。每次决定保存`applicability_decision`与新的`project_source_binding`历史版本，当前标记通过唯一索引保持单值；旧数据存在多个绑定时迁移不会代选当前版本。任务范围与S04检索只读取当前标记，决定更新后旧适用记录不再参与选择。S05页面显示当前可见项目绑定，并读取项目属性版本后保存决定。

`GET /api/source-versions/{id}/content`已经提供KN-07固定内容读取。原件按字节范围返回BASE64分段，服务端核验固定对象版本、全量SHA-256、分段摘要和每次当前授权。解析内容按序号分页或按`chunk_id`与`locator_sha256`精确读取，服务端使用已发布Locator Schema、`quote_hash`和`node_hash`核验固定内容。每次成功读取更新`source_read_history`并写入`source.content.read`审计记录。S05页面提供解析分页、定位复验、原件分段、前后分段切换与当前分段保存。

`POST /api/source-versions/{id}/processing-attempts`已经提供KN-08重新处理请求。请求固定执行项目、已发布`TOOL`配置版本、`PARSE_ONLY/PARSE_AND_INDEX`范围和原因；服务端核验`source.write`、项目读取及配置读取许可，在同一事务建立`SYSTEM_JOB`运行、QUEUED解析身份、待核验使用门槛、`processing_attempt`、幂等回执、审计记录和`source.processing.requested`事件。S05页面可以提交请求，并重新读取新解析版本的排队状态。处理Worker执行与结果提交继续由IMP-08实施。

`GET /api/processing-attempts`已经提供S04处理队列读取，支持项目、状态和固定游标筛选。服务端逐项复查当前`source.read`，总数与下一页游标只计算当前可读取记录；返回固定资料版本、解析版本、执行配置、运行编号、请求清单、失败信息、任务状态和解析状态。S04处理队列页面提供筛选、继续分页及固定资料版本入口。

`GET /api/artifacts`已经提供S04任务成果目录，支持项目、成果类型和固定游标筛选。服务端逐项复查当前`artifact.read`，总数与下一页游标只计算当前可读取成果；返回成果编号、项目、运行、文稿版本、类型、对象摘要、字节数、媒体类型和建立时间。S04页面提供筛选与继续分页，为KN-18从固定成果选择转存对象提供读取入口。

`POST /api/artifacts/{id}/source-submissions`已经提供KN-18成果转存。服务端复查成果读取、成果提交、来源共享、目标资料写入、上游资料当前权利及项目读取许可，检查完整来源图和原始证据清单；提交在同一企业事务中建立待核验资料、固定资料版本、待审权利、排队解析身份、使用门槛、生命周期、共享决定、来源边、引用保护、幂等回执、审计和Outbox。同一成果与目标范围只保留一项待核验提交，重复请求返回已有资料身份。新资料保留成果对象版本及全部原始资料版本关系，原成果内容和状态保持不变。S04任务成果页面提供完整提交表单和新资料版本入口。

`GET /api/source-versions/{id}/usages`已经提供KN-16资料引用查询。接口返回当前项目适用、资料集成员、任务范围快照、文稿证据、版本依赖和保留限制，支持关系类型、项目、固定游标及治理水位。来源维护许可决定关系读取，使用方读取许可决定对象名称是否返回。迁移为项目适用、资料集成员和任务范围补充来源反向索引。S05页面提供关系筛选、影响状态、总数和前后分页。

`POST /api/source-versions/{id}/lifecycle-decisions`已经提供KN-17生命周期决定。DISABLE立即阻止新使用；RECYCLE复查全部引用与项目状态；RESTORE重新核验归属、当前许可、原件对象版本和完整SHA-256，并恢复为待核验；PURGE额外要求`source.purge`、已批准且已生效的保存策略、期限满足、无有效引用和保留限制，进入PURGE_PENDING并建立原件、解析、全文、向量、缓存及诊断删除项。`GET /api/deletion-jobs/{id}`返回删除项、目标引用、目标摘要、备份期限、回执和修订，S05页面显示作业及逐项状态。`POST /api/deletion-items/{id}/receipts`核查删除时间、查询确认时间、备份确认时间、目标摘要及双重修订；全部删除项确认以后再次核查引用，将作业更新为COMPLETED并将资料更新为PURGED。决定、状态、原操作、审计和Outbox在同一企业事务中保存。

`POST /api/source-organization-decisions`、`POST /api/source-folders`及`POST /api/source-folders/{id}/decisions`已经提供KN-20至KN-22资料组织维护。资料可以移动到同归属文件夹、更新标签、收藏及取消收藏；文件夹支持企业或项目归属、父级检查、创建、重命名、移动和空文件夹删除。每项决定核查当前来源写入许可、组织修订、范围一致性和循环关系，并保存原操作、审计和Outbox。S04页面提供208px文件夹导航、文件夹维护、资料批量选择、移动、标签、收藏操作与常用视图，窄屏使用单列布局。

APPROVE要求原件已接收、解析COMPLETE、索引READY、索引数量一致、权利有效且生命周期ACTIVE。人工决定使用当前门槛修订和审核清单摘要防止旧页面提交，保存`approval_decision`，CAS更新`source_use_gate`，增加治理水位，并在同一企业事务中保存原操作、审计和Outbox。S05页面展示固定版本、处理状态、审核清单和决定表单，S04检索命中可进入该页面。

| 检查 | 结果 |
| --- | --- |
| Java 17 Maven编译 | PASS，八个模块成功 |
| `SourceGovernanceSchemaTest` | PASS，13项检查通过，包含列表常用视图、处理队列、任务成果目录、成果转存、文件夹与资料组织Mapper、详情、内容读取、重新处理、资料引用、生命周期、删除作业与回执、阅读记录、项目绑定构造参数和响应Schema闭合性 |
| `RetrievalSchemaTest` | PASS，2项检查通过 |
| `MigrationBundleTest` | PASS，263项迁移定义可读取 |
| `npm run typecheck:medical` | PASS |
| `npm run build:prod` | PASS，生成`source-catalogue`与`source-governance`发布资源 |

尚需完成六类存储的删除执行器和客户业务验收。处理Worker执行与结果提交继续由IMP-08实施。

2026-10-06项目分页源码及开发索引变化，BACK-PROJECT-PAGINATION-01由唯一[IMP-04](IMP-04.md)维护。资料集前端及其专业后端源码保持原字节，较早包含整体源码或索引的输入证明为STALE；下列DOM、构建及共享结果只证明各自原始范围。新Java尚未部署，完整资料集与客户业务保持未验收。

## FRONT-COLLECTION-VERSIONS-01 正式资料集页面

2026-10-06；状态来源为实施任务计划IMP-10，保持IN_PROGRESS。依据FR-03、AC-31/37、LLD-后端KN-11—13、LLD-前端FRTE-04及界面规范的资料集详情要求，连接完整资料集创建、新版本和固定历史。检索index、IMP-06/10/12、当前CollectionController/Service/Schema、原template-create/template-approval、Router、Pinia、Axios、Ajv及真实浏览器检查；沿用全部既有框架组件和原操作查询。

Given实际本机账户没有企业成员和资料集许可；When进入资料中心、打开固定资料集、刷新及注销；Then页面显示当前权限，禁止读取资料集和发送创建请求，浏览器不登记业务请求或正文。使用真实账户、原Java服务及开发和生产Vue，通过DOM和HTTP验证，视觉检查未执行。获授权客户创建、成员修订、固定历史读取、并发冲突、撤权及原请求恢复分别需要实际企业和获授权来源验证，不能以当前无企业路径代替。

完整页面要求：明确企业公共或项目归属，名称、用途、成员固定source_version/parse_version及理由；空成员保持空值；完整新版本保留旧成员历史；新版本携带原内容摘要和expected_revision；历史使用原PageHelper返回的水位及Element Plus分页。原请求身份及摘要保存在sessionStorage，正文只保存在当前页面；查询原操作以后使用完整原参数核对，未知结果不能生成新的请求。身份、企业、权限或页面可见性变化清除受限内容，未保存编辑离开需要明确选择。

当前资料集页面严格类型、正式构建和真实开发/生产DOM通过；当前Java仍使用原部署，资料集后端尚未发布。共享前端源码变化以后，旧前端集合仅证明各自原始输入范围。完整KN-14—22、BX-33、核准及生命周期与IC-10保持原验收要求。

| 当前文件 | identifier及职责 | 调用方与关联 |
| --- | --- | --- |
| web/src/views/medical/collections.vue | 创建、完整成员修订、固定历史和原请求核查；原Element Plus组件 | S04、KN-11—13；原Router及Layout |
| web/src/api/medical/collections.ts | 当前范围及严格输入、固定清单/成员/Schema/摘要、水位、CAS和原完成操作核查 | 原Axios、Ajv、BX-31/BX-32及CollectionController |
| web/src/api/medical/collection-view.schema.json、protocol.ts | 封闭原请求身份、九项专业定义及原共享定义URI | 原sessionStorage和全部医疗页面 |
| web/src/router/index.js、LLD-前端.md§13 | 正式资料中心、创建及固定详情路由；完整交互定义 | 原Layout及业务页面 |
| tests/browser/collection-checks.js、scripts/check_collection_browser.py | 真实本人账户、开发/生产DOM、原MySQL前后计数、当前部署及实际输入/产物摘要 | IC-10-COLLECTION-BROWSER；视觉NOT_RUN |

有效BDD Red采用COLLECTION-FRONT-RED-02：实际账户登录及原会话读取已经通过，正式资料集入口的标题断言未满足；原回执SHA-256为562e67aaa3277b0a58f4d216b12ccebd5094b1ccbab2415ca5bdbdee82963426，inputs_unchanged=true，读取`.implementation-work/IC-10-COLLECTION-BROWSER-20261006-COLLECTION-FRONT-RED-02/command/receipt.json`。完整资料集成功路径、并发、恢复、撤权和任务采用保留原业务验收要求。

当前Green为COLLECTION-FRONT-GREEN-02：严格TypeScript及27.43秒正式构建通过，开发和生产各五项DOM检查通过。实际本人账户没有企业成员，资料集入口、固定编号访问、刷新、创建入口及注销均保持当前权限，未发送资料集读写请求、未登记业务请求身份、未显示受限表单或内容。MySQL企业、成员、项目、资料集、版本、成员关联、申请、决定、操作及Outbox前后均为零；实际应用和浏览器身份保持一致，视觉NOT_RUN。回执`.implementation-work/IC-10-COLLECTION-BROWSER-20261006-COLLECTION-FRONT-GREEN-02/receipt.json`，SHA-256 afb05a3c290977ca5455799123b454edfbc5114dc6c842ce96cba80f39e4894f，9173项输入及216项产物，inputs_unchanged=true。

共享protocol影响的原模板入口已执行COLLECTION-FRONT-TEMPLATE-01：严格类型、29.11秒正式构建、开发和生产各七项DOM检查通过。当前模板创建、人工审查、固定编号访问、刷新及注销维持原真实身份和企业准入，未增加客户记录。回执`.implementation-work/IC-11-APPROVAL-BROWSER-20261006-COLLECTION-FRONT-TEMPLATE-01/receipt.json`，SHA-256 6b32c4e76b866f28e2ba0e6ef399bf7a3e24aab0180973bd8495792238ef6395，9174项输入及216项产物，inputs_unchanged=true。

当前只读环境证据保存于`.implementation-work/COLLECTION-FRONT-20261006-environment.json`：原JavaPID31485和对象PID62380、部署身份保持；226项迁移及业务计数零，完整登录HTML200。此次读取报告可用5189070848字节、1.0378906%，两个原卷报告ReadOnly=false；该元数据检查没有重新验证对象写入、完整Java构建或者新部署。77份原历史制品和8250328906字节保持完整，历史归档及清理仍等待明确批准。DeepSeek本地check实际返回configured=true，没有新增供应商请求。

| 字段 | 当前内容 |
| --- | --- |
| task_id / 增量 | IMP-10 / BACK-COLLECTION-VERSIONS-01 |
| 唯一状态来源 | [实施任务计划](../../实施任务计划.md)，IC-10；IN_PROGRESS，2026-10-06 |
| 需求与验收 | FR-03；AC-05/31/34/36/57；LLD-后端KN-11—13、BX-33；LLD-数据§4.4—4.6/6.5 |
| 文档有效性 | CURRENT，last_verified=2026-10-06；完整业务未验收 |
| 代码与环境 | 当前本地main，未提交；真实源码、制品、数据库与进程由实际回执绑定 |
| 检索字段 | CollectionService、CollectionAuthority、CollectionMapper、collections.schema.json、collection_head/version/member、PageHelper、lineage、KN-11/12/13 |

## 检索与输入依据

已读取实施计划IMP-10、PRD资料范围要求、LLD-后端KN-11—13、LLD-数据资料集字段、原collection_head/version/member与version_registry、原来源及版本授权、企业事务、操作/审计/Outbox、RuoYi及PageHelper现有组件。索引保留本工单原行，检索不存在旧主记录；关联[IMP-03](IMP-03.md)、[IMP-04](IMP-04.md)、[IMP-05](IMP-05.md)、[IMP-06](IMP-06.md)、[IMP-12](IMP-12.md)、[IMP-37](IMP-37.md)。

## 当前功能与实现

实施完整资料集创建、成员版本修订与历史读取，固定企业/项目归属及原件、解析、权利和完整来源关系。版本内容使用封闭Schema和原Jackson/networknt/RFC8785；分页使用框架已有PageHelper，权限与身份使用现有公共组件。旧资料集与任务快照保持固定版本，保存采用原企业事务、CAS、操作记录、审计和Outbox并完整重读。

KN-11创建首个固定版本；KN-12核查expected_revision和base_version以后保存完整新版本；KN-13使用原PageHelper、当前资料集版本水位及逐项许可读取历史。原CollectionAuthority同时核查专业清单、原件/解析/权利摘要、主资源及全部上游版本、实际成员和精确来源关系；ScopeSelectionAuthority已经调用此路径。重复请求使用原操作记录，返回前重新核查当前身份与全部依赖。当前源码已实现这些入口，尚未发布到运行应用。

| 文件路径 | identifier / 接口 | 当前职责与使用方 |
| --- | --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/CollectionController.java | KN-11/12/13 | 原会话与Schema、上下文、幂等键、CSRF及错误响应 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/CollectionService.java | create/version/history | 原企业事务、CAS、固定操作响应、审计/Outbox及历史分页 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/CollectionAuthority.java | prepare/require/view | 当前成员许可、完整专业清单及全部来源；资料集和任务来源共同使用 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/CollectionSchema.java、CollectionProtocol.java、CollectionPolicy.java | Create/VersionInput/Manifest/Page | 原Jackson、networknt和JCS；明确本地容量与批准Artifact读取Profile |
| business/medical-persistence/src/main/resources/medical-mappers/CollectionMapper.xml | CollectionMapper | 原数据表完整查询、版本/成员/关系保存及操作/审计/事件重读 |
| business/medical-persistence/src/main/resources/collections.schema.json、contracts/catalogue.json | urn:medical:collections:1.0.0 | 九份共享Schema、270项定义、279个固定引用；十三项资料集定义 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/MedicalPersistenceConfiguration.java、TenantScope.java | PageInterceptor、CollectionMapper | 框架分页与原企业/主体保护，包括PageHelper计数查询 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/CollectionTest.java、scripts/check_collections.py、tests/test_collection_runtime.py | IC-10-COLLECTION / HTTP | 真实查询与输入保护；新部署的HTTP检查尚未执行 |

## 决策与约束

资料集成员选择固定source_version/parse_version，服务器补齐原件、解析摘要及权利身份；同一版本仅选择一个解析。用途使用已批准来源许可对应的用途代码，读取和后续任务分别重查当前许可。企业资料集只接受企业来源，项目资料集接受本项目及企业来源，会话附件继续遵守独立转存要求。容量属于明确本地运行配置，不能代替批准性能、恢复或质量阈值。

## 验证与证据

Given真实MySQL及现有本人账户；When检查完整发布定义、真实查询及原企业准入；Then缺少实现的断言产生有效Red，实际查询、分页插件、身份与版本结构取得对应工程证据。客户资料集创建、更新、重放、撤权和旧任务版本保持需要获授权真实资料，保持未验收；不创建客户样本或模拟记录。

| 检查与实际范围 | 命令、结果及原始证据 |
| --- | --- |
| IC-10-COLLECTION Red | `scripts/check_collections.py --execution 20261006-COLLECTION-RED-01`；四项行为断言失败，错误/跳过为零；[回执](../../.implementation-work/IC-10-COLLECTION-20261006-COLLECTION-RED-01/receipt.json)，SHA-256 f3d00739191df3c35e7a61ccb2566dea106bbccdff1022f96b4db2319feea57d |
| 资料集工程及原输入范围完整构建 | `scripts/check_migration.py --check build --execution 20261006-COLLECTION-NATIVE-04`；308项通过，42contracts/1data/32identity/233persistence，2分29秒；资料集八项1.222秒、对象写入十四项16.745秒、传输十五项6.210秒、任务读取六项1.842秒、企业保护六项0.418秒；[回执](../../.implementation-work/IC-03-JAVA-20261006-COLLECTION-NATIVE-04/receipt.json)，SHA-256 fe46cd33feba801dae6e3c50e160f1ff1517b4721bc8c1dba50ffa341b66de4e，647项输入和8项产物。后续账户分页改动使整体证据对当前源码为STALE；资料集源码保持原字节，重新完整构建及发布仍需执行 |
| 当前共享表示 | COLLECTION-SHARED-01，Python十二项、TypeScript严格类型及三十项通过；[回执](../../.implementation-work/IC-03-SHARED-LANGUAGES-20261006-COLLECTION-SHARED-01/receipt.json)，SHA-256 a18935ff88757cc11578aa2a11f4fb14a9d22564f5de881f907da085c597d5f2；当前48项输入仍一致 |
| 当前跨语言范围 | COLLECTION-CROSS-02，Java/Python/TypeScript、270项定义、68项错误码、十一份来源、十二项定位、五项原文范围及56项标量一致；[回执](../../.implementation-work/IC-03-CROSS-20261006-COLLECTION-CROSS-02/receipt.json)，SHA-256 23e7f4d9a3adcda0496de7ca24a58438f409df2c2f40b2ac8dd99391c2005f9c；79项输入和两项产物。实际范围为共享表示，不能代表完整业务或Java应用构建通过 |
| 真实客户及当前部署 | 创建/修订/重放/撤权/旧任务保持尚未验收；新应用的七项资料集HTTP、账户三十一项、原框架五项、任务HTTP五项及部署字节核查未执行 |
| 输入与开发索引原执行范围 | COLLECTION-INPUTS-01五项通过，0.230秒；[回执](../../.implementation-work/IC-02-LOCAL-20261006-COLLECTION-INPUTS-01/receipt.json)，SHA-256 56e155c6e520db0e1d350b2b26e6b6bb5c6c2f6833cc6c1771e8f467fea02229；当前索引及实施计划已经更新，该集合只证明其原始输入范围 |

当前运行仍为SCOPE-SELECTION部署339ea611-75dd-4718-a50d-b4a11e69a20c、PID31485、0400制品9ed4566e2048d6592ae7030317f1d1eb0231bcdbac33bdf02e390eab51cd67c0。MySQL保持226项迁移，当前资料集、成员、任务及业务操作没有客户记录。原对象服务PID62380、D0及备份保持身份；原默认1%保留空间规则保持有效，最新只读环境证据为`.implementation-work/COLLECTION-FRONT-20261006-environment.json`，报告5189070848字节、1.0378906%可用和两个卷ReadOnly=false，实际写入及完整构建尚未重新验证。77份历史制品归档和清理等待明确批准，尚未执行。

只读审核清单为`.implementation-work/retired-applications-20261006-inventory.json`，77份、8250328906字节，全部原Jar SHA-256及0400权限实际核查；清单SHA-256 523e737d2c63d0393a808c80f60f9be2768ed1bd796aa905d2ea799146a5fa5a，排除当前运行制品。清单核查只产生约数十KB元数据，没有创建归档或移除原文件；自动审批拒绝的高容量操作继续等待明确批准。

当前六份身份、共享语言、跨语言、新输入、资料集DOM及模板DOM回执的全部输入与产物实际SHA-256逐项一致，证明为`.implementation-work/COLLECTION-FRONT-20261006-final-evidence.json`，SHA-256 2852ef92d56b0b29d00e5fb901c763dbcafc52e0a4c032a9d2d9f4473692a401。当前输入/索引检查COLLECTION-FRONT-INPUTS-02五项0.230秒通过，回执ca24b1154c3a78698bf1aa5ce81b9102ecf754a43b7eb42f2136f8d42a6e902f，19项输入及一项产物。范围限上述工程检查，完整Java构建、新Java部署及客户成功均为false。完整注册业务、额外场景、部署和交接继续按全部原要求实施。

COLLECTION-NATIVE-05是当前整体失败检查，42项共享、35项身份及资料集八项通过，233项persistence存在两项失败及八项错误，原对象写入返回HTTP500；完整制品缺少，构建回执未产生。原输出为`.implementation-work/IC-03-JAVA-20261006-COLLECTION-NATIVE-05/output.log`，SHA-256 d1c4fd9256583fc7ddacfb579bbde99b4f5a09432bb9492130ad4645b0000eae。容量恢复并保留构建与发布空间以前不重新发布；全部新版本HTTP及部署字节核查继续未执行。

## 关联影响与后续使用

ScopeSelectionAuthority继续使用资料集完整清单及固定成员，任务保存和后续生成仍由IMP-12推进。共享Catalogue与身份清单、前端类型、真实原文读取、更新影响及治理消费者按实际修改范围核查。当前工程结果与业务验收分别登记。

## 未完成内容与继续条件

账户分页修改后的完整构建、足够存储容量、新应用发布与真实HTTP验证继续执行。人工核准、项目适用、文件夹标签、影响、引用保护、回收恢复、逐存储删除、完整资料集页面、BX-33及IC-10全部业务继续原计划；全部42工单、57业务、17非功能、18边界、12EVAL、30SEC及部署/恢复/交接保持原要求。
