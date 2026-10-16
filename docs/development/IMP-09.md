# IMP-09 索引、授权检索与网络候选

状态：IN_PROGRESS  
更新日期：2026-10-14
关联范围：FR-03；AC-05、AC-30、AC-32、AC-33；KN-14、KN-15；S04

## 2026-10-16证据适用性核查

当前只读核查逐项重算EXACT-RECOVERY-10与BROWSER-31登记的输入及产物。完整构建回执为CURRENT；浏览器组合回执为STALE，变化为`/Applications/Google Chrome.app/Contents/Info.plist`及浏览器可执行文件，其余登记文件字节保持一致。当前Chrome为155.0.8059.82。原2026-10-14五十项浏览器及原生流程通过继续证明各自执行范围；本次没有重新执行业务检查。结果位于`.implementation-work/handoff-20261016/evidence-audit.json`，当前接手及复验说明读取[前端开发交接](../前端开发交接.md)。

## FRONT-INDEX-VIEW-01

2026-10-14；对应FE-09及原IC-09，状态保持IN_PROGRESS。依据完整SourceIndexViewController/Service/Schema、source-index-view.schema.json及固定来源页面，接入`GET /api/source-versions/{id}/index-builds?project_id={id}`。固定来源页面提供明确项目编号、完整索引记录及所有构建、当前索引、投递、数量、期限、修订和实际处理回执；已撤回索引保留原构建结果。

实现为`source-index.ts`、`source-index-result.ts`及`source-index-builds.vue`，原`protocol.ts`登记完整服务端定义。前端核查来源、项目、完整总数、唯一索引身份、U64数量和修订、微秒时间顺序、BUILDING/READY/FAILED与独立WITHDRAWN及投递状态，并通过原RFC8785摘要再次核验清单。当前项目输入改变、身份或路由变化清理原结果，原AbortController、generation及会话身份限制迟到响应。

当前共享protocol完整登记`urn:medical:templates:1.0.0#/$defs/Id`，来源详情、权利、索引、目录及维护接口使用同一公开UUID校验。工程检查同时核查合法UUID通过和非法标识拒绝，相关医疗接口保留完整读取前校验。

当前strict类型通过，`node scripts/check_source_index_frontend.mjs`七项参数检查通过，日志`.implementation-work/source-index-domain-02.log`，输入标记SIMULATION，actual_index_worker_verified=false。真实浏览器及原生检查已补充固定来源/项目的实际空列表、项目输入变化和刷新；当前完整制品、非空构建终态及后续正式采用继续原验收条件。

当前完整制品为EXACT-RECOVERY-10：strict、十八项计划/Schema及UUID、正文回归、九项上传结果、七项索引关系、三十三项EXACT参数与medical构建全部通过。完整回执、输入与产物由本记录FRONT-EXACT-FIELDS-01及IMP-36维护，当前浏览器采用该目录production。

## FRONT-EXACT-FIELDS-01

2026-10-14；对应FE-09、原IC-09和AC-05/30/32/33，状态来源保持实施任务计划IMP-09的IN_PROGRESS。检索本记录、IMP-08/10、LLD-前端、SourceExactController/Service/Schema、完整source-exact.schema.json和接口补充设计。当前服务提供五类结构化字段核准、当前及固定字段清单、等值查询和固定集合分页，052已应用。

前端完整消费所有PublicationInput、Field、Manifest、PublicationReceipt、SearchInput、Hit、FieldMatch及Page字段。原文字段及查询保留空白、大小写和Unicode表示；别名必须单独核查并提供理由。核准绑定项目、来源、解析、索引、资料核准、权利、适用版本、完整原文位置及expected_revision。原请求恢复保存安全身份与摘要，正文只保留当前受控内存，未知结果查询原操作。字段读取、检索结果、原文位置、分页与原固定版本一致，身份、页面、输入或可见状态改变清理当前结果。

Given当前完整公开定义；When核验五类字段、完整核准输入及原查询字节；Then全部定义可被当前前端校验器消费，额外字段拒绝。入口为`scripts/check_source_exact_frontend.mjs`，实际接口及开发/生产浏览器待执行；完整字段核准、别名、非空等值、分页、撤权和原请求恢复继续本项验收范围。

实现为`source-exact-domain.ts`、`source-exact.ts`、`source-exact-fields.vue`和`source-exact-search.vue`，原Router、SessionPanel、ElForm/ElTable、Ajv、Axios及安全请求元数据继续使用。`content-digest.ts`承载原Web Crypto SHA-256及json-canonicalize的RFC8785摘要，source-uploads保持原公开导出与调用行为；纯领域校验没有HTTP或组件环境依赖。当前三十三项字段、完整清单、U64、Unicode、别名、身份、摘要及分页关系检查通过，实际HTTP标志为false。

真实浏览器使用同一已授权SIMULATION来源的原TXT字节。`prepare_source_exact_simulation.py`通过现有NativeParser和DatasetBindings取得实际节点、原文、Locator、摘要及processor_manifest；SimulationSourceRightsData重新核验原件SHA、原文字节和固定来源/解析身份后保存content_node，原source_chunk关联实际解析节点。读取继续使用真实SourceGovernance接口及当前许可，没有替代HTTP响应。此项仅验证TXT原文消费，完整七格式Worker及正式资料采用保留原要求。

SourceExactMapper的版本登记使用version_registry完整四项业务字段，来源关系使用lineage_edge的固定上下游身份、关系类型、actor_id及created_at；tenant_id继续由ProtectedTenantInterceptor写入。字段版本及查询本体保留完整Schema、主体、分类和时间元数据。SourceRightsSimulationHttpTest增加真实核准HTTP201、六项完整字段、当前及固定清单重读、五类等值命中、人工别名、大小写与空白查询、固定历史重复读取和撤权HTTP403；全部断言通过以后登记实际证据。

NATIVE-EXACT-26真实原生HTTP检查通过，一项JUnit包含完整三次权利决定、真实授权对象字节核验、TXT原文及阅读记录修订、六项字段核准、五类命中与人工别名、空结果、固定历史及撤权拒绝。回执`.implementation-work/IC-10-SOURCE-RIGHTS-20261014-NATIVE-EXACT-26/receipt.json`，SHA-256为`d7cacb724538e38f31f733e6b11a4ea012d11e62ace78308de75cf21c56136da`，963项输入、4项产物，exit_code=0、inputs_unchanged=true，JUnit54.91秒。结果明确data_kind=SIMULATION、customer_success_verified=false、professional_source_adoption_verified=false；本次新建项目数量恢复原值、临时应用关闭。开发及生产浏览器组合另行核验。

实际SourceGovernance核准决定在原企业事务登记APPROVAL_DECISION及完整决定摘要，项目适用决定登记SOURCE_BINDING及完整清单摘要。解析、索引、核准和适用的模拟版本依据实际本轮记录完成登记，EXACT完整上游关系及ReferenceHold保留全部固定身份；查询保护写入使用reference_hold已有完整字段。模拟结果不会替代解析及索引Worker验收。

当前完整前端EXACT-RECOVERY-10通过strict、十八项计划/Schema、正文回归、九项上传结果、七项索引、三十三项EXACT参数及完整medical构建。回执`.implementation-work/IC-FRONT-CONTINUE-RELEASE-20261014-EXACT-RECOVERY-10/receipt.json`，SHA-256为`1ead72ad39ff5acf2315b0ce8a8ae3906306f0ef0f547444cf0d54e07a244509`，8738项输入和358项产物，exit_code=0、inputs_unchanged=true，生产构建1分34秒。范围编辑在选定实际原文片段及具备核准许可以后显示；原文定位通过Vue toRaw核查表格当前命中对象，再验证位置摘要、原会话、页面和generation，并展开核验成功的单一片段。实际组合浏览器由SourceRightsSimulationHttpTest及source-exact-actions维护，完整业务仍需核验。

BROWSER-31完整真实组合通过。开发及生产每种环境具有八项权利、三项实际上传、三项索引读取、十一项EXACT断言，共五十项；字段核准与完整重读、独立别名、五类等值、大小写和空白空结果、原文定位及展开、完整修订CAS、固定历史、原请求恢复及撤权后HTTP403均取得实际结果。原生部分另外执行真实项目适用决定、资料RETURN/APPROVE及完整版本登记摘要，再采用这些版本核准字段并建立固定查询。

组合回执`.implementation-work/IC-10-SOURCE-RIGHTS-20261014-BROWSER-31/receipt.json`，SHA-256为`a6f0950b7a224165f02eac39b55042d9a12a58be855f4d6925377e72b1063688`，1899项输入和8项产物，exit_code=0、inputs_unchanged=true；JUnit一项，532.945秒，失败、错误及跳过均为零。实际结果保留SIMULATION、视觉NOT_RUN、customer_success_verified=false及professional_source_adoption_verified=false，本次新建项目计数恢复原值，临时应用关闭。此检查核验TXT固定原文和实际空索引列表，实际多页命中、非空索引消费者、DENSE/HYBRID、网络采集及正式业务保持原要求。

`.implementation-work/verify-frontend-exact-current.py`已重新读取上述组合和EXACT-RECOVERY-10回执中的每项输入、产物及共同输入字节，并核查完整原生标志、JUnit和两种浏览器环境。输出`.implementation-work/frontend-exact-current-verification.json`为PASS，SHA-256为`76fa01ffdcfba01ff63c87c7dd95142c44451f73e3e9f775f2ba2dae8efe452d`，证明上述实际执行范围和当前文件字节一致；完整父工单继续IN_PROGRESS。

## 结构化 EXACT 字段与固定查询

当前完整应用已在PID27909运行，固定Jar7773fe25f20d062a988e37662d3c390fab05838c46787b08d8beedad0a14a2f9。完整构建回执`.implementation-work/source-exact-build-checks/2221fbca-959e-4fad-8ddf-45c59fb9658a/receipt.json`的SHA-256为0c7530c67af622a6cb3fa1f0d1d28ef243af8cd2dec480c2340e2227394d3fc7，exit_code=0、inputs_unchanged=true；当前运行回执`.implementation-work/source-processing-runtime-checks/0b1fe244-7fdf-4059-8d1c-8699a1972501/receipt.json`的SHA-256为4eeace6a535a399029d04639a3e7350bfa3cc307c42f3819ee9eb8e611ad361d，195项class及26份资源字节通过，EXACT查询和字段读取在真实安全规则下未认证均为401。当前固定结果完整身份及回执验证由二十二项组件回执维护，客户业务继续PENDING。

2026-10-14继续执行SOURCE-EXACT-01，状态引用实施任务计划§4.2和后端实施计划BE-09的IN_PROGRESS。本次检索IMP-08/09/10/36、LLD-数据§5.1、LLD-后端§3.6/5.1、当前RetrievalService/Mapper、SourceReadAuthority、ScopeSelectionAuthority和实际迁移。EXACT要求型号、产品技术要求编号、文号、标准编号及年代号按结构化字段等值查询；原始字段和经人工核准的别名需要分别保存，并关联真实原文位置。

Given已获准来源、固定解析/索引/核准/权利/项目适用版本和具有source.approve权限的当前审核人员；When核准完整字段清单并执行指定字段等值查询；Then字段、原值、别名审查理由、原文位置和全部固定身份保存后重读，查询结果建立不可变集合。原请求重放、修订冲突、未知字段、重复字段身份、原文摘要变化、容量不足和当前撤权分别明确拒绝。技术验证使用现有官方DOCX及实际本地组件，不产生客户批准或替代模型结果。

SourceExactController/Service/Schema/Policy/Mapper已经连接人工完整字段核准、当前字段查询、固定字段版本读取、结构化等值查询和固定结果分页。原值保持原字节和Unicode表示；别名有独立审查理由，同一原值的多个别名分别保存。完整字段逐项核查chunk_id、quote_hash、Locator摘要及Unicode范围；新字段版本、投影、CAS、来源关系、治理水位、会话上下文、原操作、审计和事件在当前企业事务保存后重读。读取复用SourceReadAuthority的完整当前身份、READ/ENTERPRISE_USE、项目适用、解析、索引及人工核准规则，核准额外使用source.approve。

查询按明确field_kind、UTF8摘要及CAST AS BINARY比较原值/人工别名。全部匹配字段按原片段合并，原请求、命中、每项字段版本/来源/解析/索引/核准/权利/适用/位置以及完整数量和摘要固定保存。每次分页重读完整集合和全部字段投影，验证原值、引用位置、完整匹配字段及当前来源许可，随后返回指定页；超限直接拒绝。新领域SOURCE_EXACT_REVISION与EXACT_RETRIEVAL具有真实资源归属映射，采用的来源版本分别登记来源关系和ReferenceHold。保存期限保持批准治理策略，当前没有创建客户字段或查询。

原FULLTEXT入口、retrieval-view.schema.json及已应用040—051保持原定义。新source-exact.schema.json、052和五项公开方法分别维护新增行为；formal ScopeSnapshot、retrieval_profile_revision、内部步骤身份、DENSE/HYBRID和网络采集继续完整原范围。LOCAL最大字段1000、匹配字段5000，结果上限复用retrievals.maximum-results=500；未配置容量时拒绝相关操作。这些本地工程值不代表生产发布批准。

当前代码为未提交修改，文档有效性CURRENT，last_verified=2026-10-14，适用范围为上述实现及真实组件检查。技术回执`.implementation-work/source-exact-build-checks/a0463c51-501e-4bf5-a251-f73a44b464b6/receipt.json`的SHA-256为b9aa31b4c49d7fa1368780cce0f4249246c4371ad2d68e59feeb554379fc05cf，exit_code=0、inputs_unchanged=true，二十二项通过，失败/错误/跳过均为零。MigrationBundleTest一项、SourceExactBootstrapTest一项、SourceExactRangeIT两项、SourceExactMapperIT三项、RetrievalSchemaTest两项、TenantAccessTest六项、VersionResourceAuthorityTest六项和SourceProcessingObjectIT一项；十九项真实Mapper SQL预备、全部无主体/企业拒绝、原文/位置变化拒绝、实际标识字节等值及真实S3完整重读分别核验。登记归属、每项字段/解析/索引/核准/权利身份和完整Envelope在当前读取中共同核查。原始JUnit和日志保存在同目录。Red记录`.implementation-work/source-exact-red-20261014.log`确认完整核准与等值定义缺少，当前实现通过对应检查。

当前PID23087已经运行固定Jar44b0159767615de6dae784e480e199bf27a83d10c534316b92988fdc1418f236；184项class和24份资源逐字节核查通过，EXACT字段读取及采用真实Origin/CSRF会话的EXACT查询未认证均为401。运行回执`.implementation-work/source-processing-runtime-checks/306f7b21-c764-460a-b7a8-72eb1f791a5f/receipt.json`的SHA-256为7c39d2eb6c1c52c917561c9e2c8f2f360529c5b52d31753bcaa5da94e9f410c5。该核验覆盖实际制品、配置及认证拒绝，完整获授权客户操作继续PENDING。

052已经实际应用，五张新表均为零条记录，292项迁移、原记录和287项checksum保持由IMP-03维护。完整应用构建回执`.implementation-work/source-exact-build-checks/6250e680-d6dd-41d0-97b3-fb7656c7ba21/receipt.json`的SHA-256为81b83bd7b9f3d1be9418a8952071ba251e288f401f2170282b7a107b8bbbe745，十模块通过，inputs_unchanged=true。实际运行核验由IMP-36维护。客户合法字段核准、非空查询、别名审核语义、原请求恢复、并发CAS、撤权和历史读取完整业务仍为PENDING；父任务保持IN_PROGRESS。

| 文件或identifier | 当前职责 | 关联任务 |
| --- | --- | --- |
| SourceExactController/Service | KN-EXACT-PUBLISH、当前/固定字段读取、KN-14-EXACT和完整结果分页 | IMP-08/09/10 |
| SourceExactSchema/Protocol/Policy、source-exact.schema.json | 封闭输入、完整原值及字段清单、位置核验、容量和公开响应 | IMP-03/09 |
| SourceExactMapper.xml、052-source-exact-fields.sql | 不可变版本和命中、CAS、字节等值及同企业完整关联 | IMP-03/04/09 |
| VersionResourceProtocol/Mapper | 两类实际领域归属，来源关系及引用保护的既有读取 | IMP-05/10 |
| check_source_exact_build/migration.py | 固定源码、实际SQL/S3、完整迁移和制品证据 | IMP-36 |

## FULLTEXT 索引消费者

2026-10-14继续执行SOURCE-INDEX-FULLTEXT-01，状态引用实施任务计划§4.2的IN_PROGRESS。当前读取IMP-08/09/36、后端实施计划BE-09、PRD§8.3.2、LLD-后端§3.6/5.1/6.1、LLD-数据§5.2及SourceProcessingWorker、Access、Mapper、SourceCompletionLeaseStore和实际迁移。来源处理已经产生固定source.index.requested事件，公开检索只接受READY清单；本项连接完整解析对象核验、固定索引身份、逐片段清单和持久消费者回执。

Given已登记的LOCAL_PARSER_CHECK官方DOCX及固定完整解析包；When生成FULLTEXT片段清单并检查容量、原文摘要和重复身份；Then完整220项按原顺序保存，超限或内容变化立即拒绝。技术验证不创建客户来源、发布配置、人工批准或模型结果。完整获授权客户索引、重建及旧引用、DENSE/HYBRID、EXACT、内部检索和网络候选继续原任务要求。

SourceIndexWorker/Access/Manifest/Policy/Configuration/Protocol/Mapper已连接固定source.index.requested、原成功解析作业及当前人员/来源/项目/配置许可。独立BUILDING清单、INDEX_VERSION及来源关系固定原事件、解析、权利、配置、完整片段、独立UUIDv5身份和截止时间；outbox_delivery控制owner、generation与revision。S3完整固定解析包、原命令与回执、原物化节点/片段、Locator、原文/搜索摘要及范围逐项核查。全部IndexItem在同事务保存并重新读取，随后READY、构建结果、投递终态、consumer_receipt、治理、审计和事件共同提交。租约到期保持原索引与期限，最多三次领取；到期或次数超限保存FAILED及INDEX_LIMIT，旧代际提交立即拒绝。原解析、原文、片段及人工核准保持原状态。

KN-INDEX-STATUS提供GET `/api/source-versions/{id}/index-builds?project_id={projectId}`，SourceIndexViewController/Service/Schema/Policy在EnterpriseAccess事务核查当前上下文、来源元数据及项目许可。完整读取同项目实际构建、索引、投递和消费者回执，重新核查固定身份、登记摘要、预期/核验数量和终态回执一致性；超限拒绝完整响应，尚未完成的回执保持null。公开内容仅包含真实状态、数量、开始/截止/更新时间及失败代码，不返回内部清单、ObjectRef或Worker身份。source-index-view.schema.json独立维护公开响应，源索引Schema字节保持；LOCAL最大构建数1000，来源处理集合复用原明确配置，未配置时拒绝相关读取。

当前source-index.enabled=false，实际source_index_build数量为零。051真实迁移、原记录及286项checksum保持由IMP-03维护；实际备份、运行和制品由IMP-36维护。合法客户来源、发布绑定、非空作业的领取/提交/中断恢复/旧代际、状态读取及完整业务索引仍为PENDING；完整任务保持IN_PROGRESS。

当前组件验证为`.implementation-work/source-index-build-checks/0bfb2c3a-65e9-4c47-ac25-08579fcea166/receipt.json`，SHA-256为37c7efd9c4c9c650412242bb88e955b898152f46571d5a2b828ee5a9612bfa81，exit_code=0、inputs_unchanged=true。完整十三项包括MigrationBundleTest一项、TenantAccessTest六项、SourceProcessingObjectIT一项、SourceIndexManifestIT三项、SourceIndexBootstrapTest一项及SourceIndexViewBootstrapTest一项；失败、错误、跳过均为零。真实S3固定解析包完整重读、原201141字节及220项原文清单、超限、内容变更、重复身份、二十四项无主体Mapper动作拒绝、当前完整运行定义及公开状态定义均通过。相应原始JUnit及Maven日志保存在同一目录，技术范围为LOCAL_PARSER_CHECK，客户索引及正式发布仍待验收。

## 当前实现

当前完整应用已部署到PID17978，169项class及21份资源逐字节核验通过；KN-INDEX-STATUS、上传状态及处理队列未认证均为401，使用实际现存来源和项目身份，没有创建替代业务记录。运行核验回执为`.implementation-work/source-processing-runtime-checks/3ad63ce4-105a-481e-97e2-dade19eae627/receipt.json`，SHA-256为d5255b1191e2c729a21ba9e462a84708f8375752a64a2e7e81a2e38eb5bfd950。制品、配置和完整备份由IMP-36维护；消费者保持关闭，客户索引、正式读取及业务终态继续PENDING。

- FE-09保留原检索请求的幂等键、上下文及摘要；刷新后提供FOUND、NOT_FOUND、EXPIRED和PROCESSING状态的原请求查询。参数变化拒绝重发，过期后的新检索需要明确操作。
- 固定结果历史通过retrieval_id和页码恢复，查询文字没有进入URL。分页核查完整集合身份、总数量、建立时间、状态、连续顺序及后续页码；Locator使用正式封闭定义。命中位置跳转及结果显示检查当前页面、会话和generation。
- 本地原请求元数据实际浏览器检查由[IMP-05](IMP-05.md)维护。公开检索集合、刷新历史及原请求恢复仍需完成SIMULATION业务集成检查，完整任务范围保持IN_PROGRESS。

- `POST /api/retrievals` 接收项目、查询文字和审核用途，在当前企业事务中创建固定检索集合，并以 `Operation` 返回集合身份。
- 检索只读取当前项目适用、解析完成、索引就绪、人工核准、生命周期有效且权利仍有效的来源；项目读取、来源读取、身份模式、用途、环境、区域和资料分类均需满足当前许可。
- MySQL FULLTEXT ngram 查询保存来源版本、解析版本、索引版本、内容片段、核准版本、权利版本、原文位置、引用摘要、顺序及相关度。
- `GET /api/retrievals/{id}` 只允许原创建人员读取固定集合；每次分页重新核查项目许可、来源许可、权利、项目适用关系、处理状态、核准状态和索引状态。
- S04 增加资料全文检索页面，支持获授权项目选择、查询、空结果、固定身份展开、原文位置展示和分页。
- 数据迁移新增 `retrieval_query` 与 `retrieval_hit`，服务端与前端共用封闭的检索视图 Schema。

## 固定规则

- 查询创建期间若候选超过配置上限，服务立即返回范围限制错误，不会截断结果集合。
- 查询没有命中时保存 `EMPTY` 集合，后续分页保持相同集合摘要。
- 固定集合中的任一来源在读取时失去许可或就绪条件，服务停止返回该页并给出来源不可用错误。
- 客户端校验检索编号、集合摘要、页码、结果顺序、位置类型和固定来源身份。
- 查询正文只在检索记录中保存；审计记录使用查询摘要和集合摘要。

## 当前验证

| 检查 | 结果 |
| --- | --- |
| BDD Red：检索控制器、页面和数据表在实现前均不存在 | PASS |
| `npm run typecheck:medical` | PASS |
| `npm run build:prod` | PASS，生成 `source-search` 发布资源 |
| `RetrievalSchemaTest` | PASS，2 项检查通过 |
| `MigrationBundleTest` | PASS，识别 256 个 changeset |
| `git -c core.whitespace=cr-at-eol diff --check` | PASS |
| Java 17 Maven 编译：`mvn -pl medical-persistence -am -DskipTests compile` | PASS，七个模块编译成功 |

## 仍需开发

- 按正式任务快照和固定 `retrieval_profile_revision` 提供内部检索入口，并与 Agent 工具共同使用同一业务方法。
- 完成EXACT正式快照及内部工具消费、DENSE、HYBRID与Qdrant候选，保存通道顺序、重排回执、覆盖范围和缺失要求；EXACT审核入口继续完整客户业务验证。
- 完成DENSE/HYBRID索引 Worker、独立重建入口、历史引用读取、获授权非空作业恢复及完整业务验证；当前FULLTEXT消费者和状态读取继续使用明确发布绑定。
- 完成网络查询许可、查询词发送检查、候选原件保存、时间证明及人工核准流程。
- 使用获授权客户资料执行创建、空结果、撤权、并发、重建后定位、网络候选和原文打开验收。

