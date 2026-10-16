# IMP-11 模板目录、字段及适用条件

## FE-11 结构化模板定义编辑

2026-10-14 EXACT-CURRENT-01重新验证当前组件、共享Schema及实际依赖，开发五项和生产五项全部通过，exit_code=0、inputs_unchanged=true；8900项输入及359项产物。回执`.implementation-work/IC-FRONT-CONTINUE-TEMPLATE-FIELDS-20261014-EXACT-CURRENT-01/receipt.json`的SHA-256为`a2a8ad2e4c385088a09560900cc6d9bcfbe7cea2aa597558ec97109a69a39848`。全部十一种ValueType、完整目录/字段/重复表格/附件/跨文档定义、精确Decimal及U64、取消/非法JSON/明确删除和身份变化清理均通过真实Chrome DOM断言。输入SIMULATION、视觉NOT_RUN；专业保存、核准及项目采用继续原完整条件。

2026-10-13；状态保持IN_PROGRESS，文档有效性CURRENT。依据前端实施计划FE-11、PRD§8.3.5/8.5、完整templates.schema.json和当前TP-01页面。当前未提交内容按完整原要求继续开发，用户明确授权SIMULATION输入。合法模板保存、核准、规则执行及项目采用分别需要实际服务结果。

`template-definition-editor.vue`在原`template-create.vue`提供目录、字段、重复表格、附件及跨文档规则编辑。各类定义使用原Schema的全部字段及稳定ID，ValueType直接采用全部十一种枚举。条件、证据规则、输出位置、限制、许可依据及关联集合使用成熟JSON组件完整读取；Decimal和U64使用字符串保留原值。确认当前定义和采用全部修改分别执行原Schema及重复ID检查。取消不会改变原模板，移除当前对象需要明确确认，原输入、禁用状态、页面隐藏或身份变化清理全部编辑内容。原完整JSON输入、导入、整体校验、幂等保存及未保存离开继续复用。

Given正式Vue组件与已授权SIMULATION定义；When编辑全部字段类型及各类完整定义；Then全部原字段、依据和数值精度保持，非法JSON留在错误位置，取消保留原内容，删除只作用于明确确认对象，上下文变化清理旧编辑。独立入口为`tests/browser/template-fields-checks.js`和`web/tests/template-fields/`，当前严格类型检查通过；开发与生产浏览器完整验证及绑定回执由`scripts/check_frontend_continuation.py --suite template-fields --execution`维护。输入为直接组件参数，没有替代HTTP业务成功或正式客户批准。有效Red保存于`.implementation-work/template-fields-red.log`：原模板页面已加载，结构化字段入口断言失败。

关联IMP-03维护唯一Schema，IMP-04维护实际身份与权限，IMP-12消费固定模板及项目适用结果，IMP-24消费依据影响。规则草稿/核准、版式的专业交互、真实保存及重读、人工目录决定与后续任务采用继续保持原完整验收范围。

当前GREEN-05的开发五项及生产五项实际Chrome检查全部通过，exit_code=0、inputs_unchanged=true；8841项输入和343项产物固定当前源码、唯一Schema、实际依赖与制品。回执为`.implementation-work/IC-FRONT-CONTINUE-TEMPLATE-FIELDS-20261013-GREEN-05/receipt.json`，SHA-256为`0e34ceca8dbe9f463b36e228c653a12ebd7455dc69642e9b0ed198b408809a3b`，原输出位于同目录`command/output.log`。十一种字段、精确Decimal、完整目录/重复表格/附件/跨文档规则、U64范围、取消/非法JSON/明确删除及上下文清理均取得真实DOM断言；输入标记SIMULATION，没有HTTP业务写入，视觉保持NOT_RUN。完整模板保存、核准及项目采用仍须对应组合业务验收。

## FRONT-TEMPLATE-APPLICABILITY-01

2026-10-08，当前聊天负责项目适用模板查询页面；「按实施清单完成功能开发」负责服务身份、来源处理、Temporal、共享任务状态和整体部署。唯一主记录继续使用本文件，任务状态引用[实施任务计划](../../实施任务计划.md)中的 IMP-11，当前 IN_PROGRESS。检索本记录、索引、PRD §8.3.5/8.4、LLD-前端 FRTE-07/18 与 TP-05、TemplateApplicabilityService/Schema、projects.ts、tasks.ts 和原页面测试，确认现有后端完整返回固定属性、模板定义、三态条件和分页身份。

Given 实际本机账户没有企业成员；When 打开项目适用模板入口、刷新、返回项目及退出账户；Then 页面明确显示身份与权限要求，不读取项目或模板正文，不产生业务记录。真实客户的全部文档类型查询、固定属性与分页、完整条件展示和后续任务采用继续属于验收范围；当前资料不包含获授权的客户模板，不能登记客户业务通过。视觉检查保持 NOT_RUN。

当前文件范围为 `web/src/api/medical/template-applicability.ts`、`web/src/views/medical/template-applicability.vue`、`web/src/api/medical/protocol.ts`、`web/src/router/index.js`、`web/src/views/medical/project.vue`，验证入口为 `scripts/check_template_applicability_browser.py` 和 `tests/browser/template-applicability-checks.js`。使用现有 Vue、Element Plus、Pinia、Axios、Ajv、Web Crypto 和 RFC8785，完整内容保存在页面内存。工程版本通过独立执行回执中的实际输入摘要绑定，当前文件尚未提交；文档有效性 CURRENT，last_verified=2026-10-08，仅指本次代码和需求核查。

当前页面位于 `/projects/:projectId/templates`，项目详情依据当前 `project.read` 提供入口；十三种目标文档采用现有 `taskDocumentLabels`。查询重新读取项目与固定属性，TP-05 响应核查企业、项目、属性版本及摘要、完整模板摘要和排序；后续分页绑定原水位、过滤摘要、文档类型与总数。模板、目录、字段、附件逐项对应条件，三态结果完整显示，待确认属性和缺项保留。目录、字段约束、重复表格、附件、跨文档规则、版式及固定依据均可完整阅读。退出、上下文变化、路由变化、页面隐藏和网络中断清除内存内容与未结束请求。

2026-10-08，`.implementation-work/document-venv/bin/python scripts/check_template_applicability_browser.py --execution 20261008-PARALLEL-GREEN-01` 已通过严格 TypeScript、生产构建及开发五项/生产五项实际 Chrome DOM 检查。十项覆盖当前无企业账户的查询权限、刷新、项目返回导航、禁用表单、无正文保存和退出清理；MySQL 的企业、成员、项目、操作、企业登记、登记审计、登记决定、事件、模板决定及模板十类数量前后全部为零。回执 `.implementation-work/IC-11-APPLICABILITY-BROWSER-20261008-PARALLEL-GREEN-01/receipt.json` 的 SHA-256 为 `696a3ee9bbfd7cc34498a76dcd3849b34561965816829d1f175c7fce3e3fb9fa`，8927 项输入、229 项产物，exit_code=0、inputs_unchanged=true。真实客户成功、分页/属性变化/撤权、人工适用决定和任务采用尚未验收，IMP-11 保持 IN_PROGRESS。完整应用构建与部署由并行聊天维护，当前本节仅证明独立前端制品及其真实权限行为。

相同源码与生产制品的模板创建、人工核准关联回归通过开发七项/生产七项实际 Chrome 检查，执行 `.implementation-work/document-venv/bin/python .implementation-work/check-template-page-regression.py`。回执 `.implementation-work/IC-11-APPLICABILITY-REGRESSION-20261008-01/receipt.json` 的 SHA-256 为 `b3f92a23dfc9cd28c7ce5da89b352def058de5bfc3577cc55ef0ad2d17564665`，exit_code=0、inputs_unchanged=true；实际业务数量前后全部为零，常驻应用身份保持一致。共二十四项 DOM 检查与当前独立生产制品关联，浏览器上下文和临时监听均由检查入口关闭。正式模板合法保存、核准与项目采用仍按原完整业务验收，未使用模拟资料或视觉工具。

2026-10-06当前原Spring Method Security配置及原管理方法调用由唯一[IMP-06](IMP-06.md)维护；119项关联Java继续核验当前模板定义、审批、操作、适用条件、固定项目属性及真实TemplateApplicabilityHttpTest，后者3.744秒。严格Vue类型、28.50秒生产构建及开发七项/生产六项DOM继续核验原模板入口的企业权限限制，没有新增客户模板或规则审批。完整Java构建因实际S3空间保护未通过，新源码尚未部署；TP-03/04、完整规则发布与人工目录确认及全部客户成功仍需原验收。

## BACK-TEMPLATE-APPLICABILITY-01

2026-10-06关联BACK-RUOYI-NAVIGATION-01的原菜单/权限读取与Vue消费由唯一[IMP-06](IMP-06.md)维护。新的身份及admin源码使较早整体输入证明为STALE；模板适用的当前关联工程结果使用导航的完整检查集合，客户成功、人工适用和完整TK-01继续独立验收。TEMPLATE-APPLICABILITY-GREEN-07、SHARED-05、CROSS-02和INPUTS-01的四份输入及产物曾逐项核验，其固定证明c1a22dc47fef44957803a6e819f774455ee2dc6df85ded9414091ad5a19e6e21只保持该集合的原始范围。

2026-10-06；唯一状态来源为[实施任务计划](../../实施任务计划.md)中的IMP-11，保持IN_PROGRESS。依据PRD§8.3.5/8.4、TP-05、TK-01、AC-07/18/37与现有Condition的ALWAYS/RULE及UNKNOWN语义，实施固定项目属性和核准模板的完整适用检查。任务创建继续依赖其结果，全部业务目标保持原范围。

已检索本记录、索引、IMP-04/12、TemplateProtocol/DefinitionSchema/VersionStore/DependencyAuthority、ProjectService/Schema、ResourceReadAuthority、原rule_version及lineage_edge、SchemaCatalogue和现有Spring Expression Language 6.2.19。当前采用原Spring受限执行上下文及成熟AST，只核查许可的属性/常量引用、比较和布尔运算；表达式不具有方法、类型、对象创建、网络或赋值能力。完整JSON、版本、来源、当前权限及规范摘要继续复用Jackson、networknt、RFC8785和原企业事务。

Given当前真实数据库及平台账户；When读取固定规则、候选模板和匹配版本查询；Then映射、原企业保护和固定发布定义完整可用。未知项目属性产生UNKNOWN，已核准模板和规则的原内容、全部依据及成员许可必须完整核查；没有兼容模板明确返回缺项。合法客户模板适用与目录确认仍需真实获授权企业、项目、原件和批准版本，不制造业务记录。

主记录CURRENT，last_verified=2026-10-06，适用于当前设计、代码及下列工程检查；关联IMP-03/04/05/06/12/37，完整TP-03/04发布与批准、TK-01保存、目录人工确认及后续签发保持原要求。

当前GET /api/projects/{id}/applicable-templates要求当前project.read、明确文档类型和固定profile_revision。ProjectService.fixedProfile复用原项目、属性、注册摘要及依据核查；TemplateVersionStore.applicationTarget复用完整模板和物理目录、字段、重复行、附件、依赖投影，要求APPROVED。TemplateDependencyAuthority为专业用途TEMPLATE_APPLICATION核查项目属性及全部模板依赖，调用原VersionResourceAuthority、SourceReadAuthority及完整关系组件；来源范围、核准、权利、有效期及当前许可每次重新读取。

application_path/ivd/directory_profile分别比较当前获核定属性和模板定义；缺少或未知保持UNKNOWN。固定规则重读原rule_version、version_registry及完整RULE_DEFINITION依据关系，比较原Content、Schema、manifest和来源摘要。Spring SpEL承担解析和布尔执行；业务层限制声明式能力及固定依据，禁止方法、类型、构造、Bean、赋值、正则和未经声明的引用。原Jackson保留精确数值，常量必须具有完整依据，整个规则依据不能为空。结果保留模板、节点、字段和附件各自条件，祖先及附件条件共同计算待确认缺项；条件结果不构成人工适用批准。

原PageHelper在完整筛选以后提供固定UUID排序、页码、数量和原PageInfo。分页指纹绑定当前用户、上下文、授权及治理水位、固定属性和全部可用模板结果；后续页面同时提交dataset_watermark/filter_sha256，状态变化返回SCOPE_STALE。属性版本变化返回RESULT_VERSION_MISMATCH，原Error Schema提供完整响应。没有可读取兼容模板时，返回明确compatible模板Requirement及原SOURCE_MISSING。全部目录内容始终保留，没有自动授予后续处理动作。

| 文件 | identifier及职责 | 后续使用 |
| --- | --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TemplateApplicabilityService.java | list、compatible、missing、condition；完整TP-05、来源许可及条件结果 | S07/S18、TK-01 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TemplateApplicabilityController.java | 原Spring HTTP入口、当前身份和Schema响应 | 当前认证会话 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TemplateApplicabilitySchema.java | 完整共享规则及查询校验、固定manifest及Envelope | 规则读取、TP-05 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RuleExpression.java | 原SpEL、SimpleEvaluationContext及成熟AST限制 | 固定规则消费者 |
| business/medical-persistence/src/main/resources/medical-mappers/TemplateMapper.xml | 原MyBatis受保护候选、分页及规则完整查询 | 原企业协调事务 |
| business/medical-persistence/src/main/resources/rule-expressions.schema.json、template-applicability.schema.json | 完整固定发布定义 | Java/Python/TypeScript |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/TemplateApplicabilityTest.java | 实际MySQL映射、框架能力、未知与封闭输入 | scripts/check_template_applicability.py |
| business/ruoyi-admin/src/test/java/com/ruoyi/medical/runtime/TemplateApplicabilityHttpTest.java | 原Maven应用依赖、实际Spring HTTP和本机账户 | 受影响检查集合 |

有效Red为TEMPLATE-APPLICABILITY-RED-01，两项业务组件断言失败，错误和跳过为零，4.679秒；回执`.implementation-work/IC-11-APPLICABILITY-20261006-TEMPLATE-APPLICABILITY-RED-01/receipt.json`，摘要798882514160c37d08a9bea5456d47b3fc21ed337b8455ebd2dff5f3f608bd67。表达式组件检查使用真实平台账户及实际MySQL迁移数量，没有登记为项目属性或批准业务依据；空项目属性继续保持UNKNOWN。

当前TEMPLATE-APPLICABILITY-GREEN-07通过114项，contracts42/identity36/persistence35/runtime1，失败、错误和跳过为零，构建1分28秒。六项适用组件0.467秒，真实Spring HTTP18.68秒；匿名401、真实无企业账户403、错误版本及未绑定分页422，原登录302及注销204，全部客户业务计数前后为零，临时应用关闭。回执`.implementation-work/IC-11-APPLICABILITY-20261006-TEMPLATE-APPLICABILITY-GREEN-07/receipt.json`，摘要970b078f41af23b560ee8c3245fdcc3b1574bfb44a5aed7f3b3f968218061bfb。当前共享TEMPLATE-APPLICABILITY-SHARED-05通过Python十二项、TypeScript严格类型及三十项，回执b0a32026c63c4e9b175a8bc08a0fac408b55a28334797d72526a7d6d48cfe092。运行入口使用原Maven依赖和当前编译类，没有生成完整应用Jar或改变现有部署。

当前客户获授权模板适用、人工目录决定、完整任务采用和后续签发保持未验收。正式运行仍使用原PID31485及原9ed4566制品；新TP-05、账户/项目分页和资料集后端尚未部署。全部业务目标继续保持原范围，当前工程结果不登记ACCEPTED。

2026-10-06当前来源选择、固定资料集成员和制品由唯一[IMP-12](IMP-12.md)维护。SCOPE-SELECTION-NATIVE-01完整300项通过；完整目录、固定模板、项目属性、PlanCandidate和Requirement保持原要求，模板两份Schema及公开发布保持原字节。新选择组件仍需接入完整任务消费者，客户模板保存、核准、任务创建与项目采用尚未验收。模板HTTP及DOM集合保持各自原输入和制品范围。

## 已有实现与各执行集合的原始范围

本节中的执行身份、制品、数量和验证结果分别对应其注明的集合；当前任务组件和部署证据使用IMP-12。

2026-10-06BACK-RUOYI-DATA-SCOPE-01和当前Java制品由唯一[IMP-04](IMP-04.md)维护。当前275项Java、30组回归及31份源码、产物和双服务身份证明通过，路径为`.implementation-work/FULL-REGRESSION-20261006-RUOYI-DATA-SCOPE-FINAL-01/verified-evidence.json`。模板创建与审查开发/生产各七项，九项模板HTTP6.645秒通过；原企业准入、完整依赖、当前权限、Schema和222项迁移保持。真实客户创建、核准、固定制品读取、项目采用、并发与撤权继续验收；以下集合只保留各自原始范围。

2026-10-06FRONT-ENTERPRISE-REVIEW-01使用原申请及RFC8785摘要组件，当前共享修改及回归由唯一[IMP-06](IMP-06.md)维护。ENTERPRISE-REVIEW-FINAL-01模板创建与审查开发/生产各七项、九项模板HTTP6.112秒及完整30组通过；31份当前源码、产物和运行身份证明保存于`.implementation-work/FULL-REGRESSION-20261006-ENTERPRISE-REVIEW-FINAL-01/verified-evidence.json`。原模板Schema、Java制品、数据库和客户数量保持；真实模板建立、核准、采用、并发和撤权继续按完整要求验收，较早集合按原始范围读取。

2026-10-06原框架身份及当前制品由唯一[IMP-04](IMP-04.md)维护；模板企业准入、当前权限及全部依赖核验保持原要求。当前275项Java、30组回归及31份当前源码/产物/运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261006-RUOYI-NATIVE-IDENTITY-FINAL-01/verified-evidence.json`。九项模板HTTP6.337秒、存储结构及正式创建/审查开发与生产各七项DOM通过。客户合法创建、人工决定、固定Artifact读取及项目采用仍须真实正向验收，较早集合保持各自原始范围。

2026-10-06当前完整工程回归采用ENTERPRISE-REGISTRATION-FINAL-01，30组及273项Native合计31份当前输入、产物和运行身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261005-ENTERPRISE-REGISTRATION-FINAL-01/verified-evidence.json`。共享Router/协议的企业登记接入由[IMP-06](IMP-06.md)维护；模板创建/审查严格类型、构建27.43秒及开发/生产各七项DOM通过，回执421e6dfa5555c404c36bf73cba47167ddb4af1a238e7c28742042b383c1270a4绑定9141项输入和211项产物。模板HTTP九项6.358秒、存储五项通过，原模板定义、决定、Java和数据结构保持原版本；客户合法模板创建、核准、项目采用、完整Artifact及专业交互仍需原完整验收。较早集合保持各自原始范围。

FRONT-TEMPLATE-CREATE-01继续本主记录，依据TP-01、templates.schema.json、template-operations.schema.json、PRD§8.3.5及FRONT-R04/R06/R12。当前检索原TemplateService、TemplateOperationSchema、模板审查页面、原请求恢复、project-json和正式Router；创建入口使用原Layout、Element Plus、Pinia、request及Ajv。完整目录、字段、重复行、附件、条件、依据和版式均须进入原VersionInput，原固定版本及摘要不能改变；创建草稿不产生核准。客户许可与合法保存仍需真实业务输入，不能使用本机平台身份代替。

正式位置为`web/src/views/medical/template-create.vue`及`web/src/api/medical/template-create.ts`，入口`/templates/new`按当前企业template.write控制。十三文档、三路径、IVD类别、目录规范和原版本采用原表单控件，八组完整定义采用独立JSON编辑，完整企业定义经原ElUpload导入内存；全部内容必须通过原VersionInput和成熟JSON组件，不生成示例产品、来源或批准。后台继续核查完整目录关系、原依赖、核准及来源权利，草稿不能替代模板核准或BX-35固定制品。完整专业字段交互仍需按原管理要求完成，当前JSON编辑不代表全部管理交互已经验收。

`template-create-view.schema.json`封闭七项原请求身份和摘要，正文及理由仅保存在当前页面内存。TP-01原参数与完整上下文相等才能使用原key；过期、执行中、失败和上下文变化停止再次提交，原查询继续使用lookupOperation/readOperation。201回执逐项比较完整正文、原版本、理由、主体、企业、全部依赖及RFC8785摘要；当前会话重读后使用同一原key取得原固定回执，同时由服务器重查完整依赖许可，两个回执一致并重读原完成操作以后才清除原请求。当前维护权限失效不展示受限正文，原创建回执不推定模板当前治理状态。未保存编辑离开与关闭确认使用原ElDialog及Router入口。

Given真实本机所有者没有企业成员；When进入创建、审查及刷新入口；Then不读取客户模板、不生成创建请求身份、不提交草稿或决定，注销清除受限页面。当前严格类型、生产构建27.17秒及开发/生产各七项DOM检查通过，回执`.implementation-work/IC-11-APPROVAL-BROWSER-20261005-TEMPLATE-CREATE-UI-FINAL-01/receipt.json`，SHA-256为ac2855b907942debed3bd158a52a8fb9b04830800cddb68ed91d76075ee7a051，9138项输入及208项产物；客户计数保持零。完整30组回归及其与Native合计31份当前字节和运行身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261005-TEMPLATE-CREATE-UI-FINAL-01/verified-evidence.json`。独立GREEN-01保持其原始代码范围。客户保存成功、重放、响应中断恢复、并发、撤权、来源变化和后续项目使用仍未执行，状态保持IN_PROGRESS。

FRONT-TEMPLATE-APPROVAL-01继续本主记录，采用TP-02、approval-baseline、PRD§8.3.5、前端FRONT-R04/R06/R08/R12及接口补充设计§4.20。检索当前TemplateController/ApprovalSchema/Protocol、正式Vue路由、medical/protocol、operations、project-write及既有会话/本人账户DOM检查；采用现有Element Plus、Vue Router、Pinia、Axios request和Ajv。页面固定模板版本与原审查清单，完整展示目录、字段、重复行、附件、条件、跨文件规则、版式、依据及依赖；人工决定需当前template.approve和理由。浏览器保存主体、企业、原请求标识、上下文、版本及请求摘要，正文和理由仅在内存；中断后查询原TP-02操作，禁止创建新请求冒充原决定。Given真实所有者没有企业身份；When打开正式模板审查入口及当前本人UUID深链；Then显示权限前置条件，不读取客户模板、不提交人工决定。严格vue-tsc、生产构建及开发/生产各四项实际DOM检查通过；独立回执为`.implementation-work/IC-11-APPROVAL-BROWSER-20261005-TEMPLATE-APPROVAL-UI-GREEN-01/receipt.json`，SHA-256为bb4193438b95e8cc95adb31f3315cd97d783946031b1ee859908ee1d330fcaa1，9135项输入及205项产物。实际MySQL企业、成员、项目、操作、事件、决定及模板计数在检查前后全部为零；没有模型或视觉请求。合法核准及其后续项目使用仍按原客户业务验收，任务保持IN_PROGRESS。

当前正式页面位置为`web/src/views/medical/template-approval.vue`，路由`/templates`及`/templates/:templateId/approval`；`web/src/api/medical/template-approval.ts`核查实际API Header、封闭Preview/正文、当前企业、完整RFC8785内容摘要、固定Baseline、治理修订及原操作。模板响应的语法及重复字段由原jsonc-parser检查，JSON由标准组件读取，原序列化组件拒绝不精确整数；请求继续使用原Axios适配器。`template-approval-view.schema.json`封闭浏览器仅存身份字段，`protocol.ts`直接复用原Ajv登记真实后端Schema。json-canonicalize3.0.1通过正常npm依赖安装并固定锁文件；SHA-256使用Web Crypto。原模板没有经过项目属性判断的条件明确显示待判断，完整内容不截断且稳定ID作为表格key。

人工决定表单和确认窗口使用ElForm/ElInput/ElSelect/ElCheckbox/ElButton/ElDialog；完整目录、字段、附件采用ElTable，其他全部定义采用ElCollapse。原请求提交前保存身份及摘要，重复提交使用原key；模板/审查上下文变化停止再次提交，未知结果经原lookupOperation/readOperation核查。服务器响应以后重新读取当前会话、原操作及完整模板，当前版本可包含后续治理决定，原回执与当前状态独立展示。当前身份失效或页面不可见采用原全局清理，未保存理由的路由离开、窗口关闭及页面关闭提供确认。客户完整展示、核准成功、响应丢失恢复、并发、撤权、来源变化和后续项目采用尚未执行；模板完整专业管理交互、BX-35字段Artifact及项目条件决定继续实施。

BACK-TEMPLATE-APPROVAL-01继续本主记录，采用PRD BR-01/19、§8.3.5及LLD-后端TP-02。已检索TemplateService、TemplateVersionStore、TemplateMapper、TemplateDependencyAuthority、原Schema、企业事务和当前公开检查。当前任务连接人工APPROVE/RETURN/DISABLE决定，固定目标版本、治理修订、完整内容及依赖授权检查清单，同事务保存决定、状态、原操作、审计和Outbox；重复请求读取原决定，异参冲突，旧修订拒绝。客户授权及资料许可没有登记，正向验收保持未运行。Given真实本机账户没有企业成员；When请求TP-02；Then拒绝访问，不创建模板核准记录。当前运行检查先验证这一原业务前置条件；完整核准、受控读取、项目采用与正式页面继续保持原验收范围。

当前Java制品采用TEMPLATE-APPROVAL-NATIVE-02，273项检查通过，contracts42/data1/identity30/persistence200，失败/错误/跳过为零，构建2分25秒。Native回执SHA-256为bda0372628676c67270d243bf6b8ef3d8cdd91d19041776a2d6367b6de8c4fef，617项输入和8项产物字节核查通过。当前JavaPID88755、UID501、部署UUID367c28a5-9f68-4a45-9780-59ea640ae34c，0400不可变Jar摘要d2561d1d6f13d0e82fc972ee21ce0f44caafbce6da439eff0bdae87cee2ddb4e，启动16.481秒，完整登录页面HTTP200。完整30组本地回归TEMPLATE-CREATE-UI-FINAL-01通过；账户HTTP31项29.379秒、原框架三项4.520秒、模板HTTP九项6.194秒、实际存储五项0.404秒、Python11项及TypeScript类型检查和29项检查通过。模板创建和审查页面严格类型、生产构建27.17秒及开发/生产各七项DOM检查通过，9138项输入和208项产物。30组及Native合计31份回执的当前输入、产物、应用及对象服务身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261005-TEMPLATE-CREATE-UI-FINAL-01/verified-evidence.json`。真实MySQL保持222项迁移、150张企业基表、17张平台基表、10张原框架表及一项兼容View，本次没有Java或数据库修改。客户成功、原决定重放、并发及撤权、来源变化、影响事件消费、BX-35完整Artifact读取、项目采用及正式页面完整业务仍未验收；下列集合仅适用于各自原始代码和制品。

TemplateApprovalProtocol、template-approval.schema.json及TemplateApprovalSchema定义封闭请求、完整审查Preview和决定回执，直接采用原Jackson/networknt，Preview正文继续使用共享模板Content校验。TemplateVersionStore.approvalTarget重读固定模板内容和治理状态。TemplateApprovalService.baseline/decide在原EnterpriseAccess事务内核查template.approve、当前主体、权限水位、原治理修订和全部真实依赖；APPROVE/RETURN/DISABLE分别产生APPROVED/RETURNED/DISABLED。新决定按原模板和定义revision执行CAS，内容摘要及版本身份保持固定；同事务推进治理水位及当前会话上下文，保存原TP-02幂等操作、完整决定、audit_event及outbox_event，并逐项重读。原决定重放重新核查当前权限和依赖，并比较原请求、Baseline、完整决定、回执、审计及事件；同键异参、旧修订或依赖变化明确拒绝。TemplateController提供POST `/api/template-versions/{id}/approval-decisions`及GET `/api/template-versions/{id}/approval-baseline`，使用原Spring MVC/Security、CSRF、Origin及统一错误响应。审查Preview不能替代BX-35所需的field_definitions ArtifactRef及适用决定。

031-template-approval.sql为追加的第222项迁移，保存template_approval_decision的26个完整字段、四组复合外键、固定操作及模板治理修订唯一约束、状态转换和正修订约束；没有客户决定写入。迁移回执为`.implementation-work/IC-11-APPROVAL-20261005-TEMPLATE-APPROVAL-MIGRATION-01/receipt.json`，SHA-256为e663d0269f9687a71ebb7e39d83c27c0b85c7acfd305db5cd955babe59d6b5ed，实际Liquibase执行一项、原221项、累计222项。独立备份`.implementation-work/runtime/migration-backups/2980dbe1-bac0-4004-82ca-d6cf5bf378ac.sql.gz`摘要26759f939687f5567484b823329c8a80451af884caa6e806f13c3a6d6fcf8019，同名JSON绑定实际数据库身份，文件0600及目录0700。该迁移回执只证明其原始执行范围，当前结构由完整回归重新核验。

当前九项HTTP检查使用真实本机账户，验证TP-01/TP-02企业拒绝、必需Header、查询参数、CSRF/Origin及审查Preview访问，拒绝后没有新增模板、决定、操作或事件；回执SHA-256为62ad94c44251b19745bf0e2e7ba79f2158a67aa546df6d38f2ec6f65a5a0f2db。五项实际MySQL结构检查包含新表全部外键、唯一和状态/revision约束，回执SHA-256为66abec788d565120c81844007c2f08b72b48cd1ce7bce56a3c2f0977c64a0ca8。TemplateApprovalSchemaTest四项及TemplateOperationQueriesTest一项进入273项完整构建，后者在真实MySQL执行九条原操作、决定、审计及事件只读查询，并检查缺少企业事务时拒绝；全部150张企业表继续参加原隔离检查。工程检查没有登记客户企业、岗位、来源许可或人工批准，完整客户业务保持原验收状态。

BACK-TEMPLATE-SCHEMA-PUBLICATION-01继续本主记录，关联IMP-03/04/05/10/12/23/26/37。已经核查TP-02、BX-35、ArtifactRegistry.requirePublished、原三语言SchemaCatalogue及完整模板消费者，采用接口补充设计§4.19。原共享发布Catalogue登记七份Schema、253项完整定义和260个固定文档/定义引用；模板保留原$id URN和文件SHA-256，两个模板消费者直接复用共享Catalogue。Given当前原模板Schema及实际D0原件；When核查固定发布引用和原件摘要；Then全部定义可解析，别名及不同摘要拒绝，真实参考资料不能成为客户模板。有效Red四项SCHEMA_UNSUPPORTED错误，回执`.implementation-work/IC-11-PUBLICATION-20261005-TEMPLATE-PUBLICATION-RED-01/receipt.json`，SHA-256为3eefe9213142c4e51b5b5d9a604913aef10b8c26b8f84f81aa1054f13dcd482c；独立Green四项通过，回执`.implementation-work/IC-11-PUBLICATION-20261005-TEMPLATE-PUBLICATION-GREEN-01/receipt.json`，SHA-256为2a766b627b353966653cb9270b7173fe0e364883cbbd132e40d918001c15fc42。当前Python/TypeScript完整检查、Native构建和实际运行已取得本记录首段证据；TP-02/BX-35及完整客户业务保持未验收，下列集合保持各自原始输入和制品范围。

BACK-TEMPLATE-CREATE-01继承TP-01、PRD§8.3.5/§8.5、LLD-数据§4.7和接口补充设计§4.18，继续维护本主记录。检索原TemplateVersionStore、DependencySourceAuthority、VersionResourceAuthority、ProjectService、原操作/审计/Outbox以及当前真实登录检查；公开模板保存采用既有Spring MVC/Security、企业事务、MyBatis、Jackson/networknt和JGraphT。Given真实本机账户没有企业成员；When提交模板草稿请求；Then拒绝访问且不创建模板、操作或事件。有效Red保存于`.implementation-work/IC-11-HTTP-20261005-TEMPLATE-CREATE-RED-01/receipt.json`，摘要ecf4892be76b7ca72e88c9a00ffbd2c260fdeafd4b043374b5ff33733e7018f2。当前五项公开检查3.473秒通过，回执`.implementation-work/IC-11-HTTP-20261005-TEMPLATE-CREATE-FINAL-02/receipt.json`，摘要a781ddabc599260c34bafe6ca6ae4a5db2f7ca4261627e881f795379fc509802。完整客户成功、重放、并发和撤权验收需要真实企业、依据与许可，保持未验收。

当前完整制品采用TEMPLATE-CREATE-NATIVE-04，264项Java检查通过，contracts38/data1/identity29/persistence196，失败、错误和跳过为零，构建2分25秒。回执`.implementation-work/IC-03-JAVA-20261005-TEMPLATE-CREATE-NATIVE-04/receipt.json`，摘要893b64a3b83d8c92eac592b380c6b69e951af868d99fc625a5c18054efc9e737；605项输入及8项产物字节核查通过。PID77324、UID501、部署UUID84ed126e-45a9-4cc6-893c-5e451044027d、0400不可变Jar摘要c9cb4be88e58380debfc952daeb01de3cebfd19336932ba55d9eec714a9be3f2，启动16.94秒，完整登录页面HTTP200。原框架账户及密码调用保持，实际复用记录读取[IMP-04](IMP-04.md)。

TemplateVersionStore.save/read保存完整定义、目录、字段、重复行、附件、依赖与原关系，逐项重读核对内容、摘要、版本、主体和当前授权；027已应用到真实MySQL，模板定义、附件及依赖共三张企业表继续使用TenantScope。TP-01公开事务已经连接原操作/审计/Outbox，当前成功路径代码包含完整重读和同参数原结果返回，客户正向验收尚未运行。TP-02后端人工决定及审查Preview已经连接当前事务，客户成功、重放、并发及撤权仍未验收；影响事件消费、BX-35固定制品受控读取、项目任务及正式页面继续实施，IC-11保持未验收。目录仍为REFERENCE_ONLY，没有创建客户模板、许可或批准。

TemplateDependencyAuthority.inspect沿真实主库关系检查完整循环及总容量，每个版本经VersionResourceAuthority核查实际领域、资源及当前grant，原来源经SourceReadAuthority核验当前权利版本、环境、区域、ENTERPRISE_USE和核准门槛。编号、条件、验证、计算及显示规则需要RULE_VERSION/APPROVED，原修订依据需要TEMPLATE_VERSION，字体需要ARTIFACT。制品消费使用服务器明确的FONT/FORMAT/DEPENDENCY、主资源/动作、固定Schema及摘要/manifest；本地artifact-profiles为空，不能自动取得字体许可。snapshot保存全部依赖权限与来源权利版本、水位及执行参数，不保存私有对象地址。

TemplateService在EnterpriseAccess同一治理事务核查当前template.write、完整输入和请求摘要，TemplateVersionStore保存以后共同登记operation_record、audit_event、outbox_event并重读。相同操作名TP-01、主体和幂等键由原数据库唯一身份及企业锁协调；同参数读取原响应，其他参数明确IDEMPOTENCY_CONFLICT，当前权限及依赖重新核验。状态推进以后原创建内容和响应保持固定身份，失败整体终止事务。TemplateController注册POST /api/template-versions，强制Schema、上下文、幂等、CSRF和Origin，拒绝查询参数。独立TemplateOperationSchema引用原模板定义，返回完整草稿及原操作UUID，allowed_actions为空；后续动作依其真实实现提供。

TemplateOperationSchemaTest四项初始化、封闭响应和HTTP状态检查与TemplateOperationQueriesTest实际MySQL只读检查进入上述264项集合；后者实际执行原操作、审核和事件SQL并检查无企业事务拒绝。TemplateErrorResponseTest三项使用实际序列化组件及共享Error Schema核查SOURCE_NOT_READY、RIGHTS_PENDING和CONTEXT_LIMIT，响应retryable=false且没有自行授予后续动作；模板的资料、权利、容量、Schema和内容核验拒绝使用422。有效Red回执`.implementation-work/IC-11-ERRORS-20261005-TEMPLATE-ERRORS-RED-01/receipt.json`，摘要3ed0b1bdf99fe256ce8e17c02a292234ae975f6f1c04ba66c8bddbeeb80b1510；相同实际组件检查Green回执`.implementation-work/IC-11-ERRORS-20261005-TEMPLATE-ERRORS-GREEN-01/receipt.json`，摘要dc4d930f7350183d9b63bc38919721007e7e9442ca27e17d684dba3c8bbdcde7，11项输入及一项产物字节相等。28组本地回归FULL-REGRESSION-20261005-TEMPLATE-CREATE-FINAL-02已经取得成功终态；它与Native合计29份回执的当前输入、产物、应用及对象服务身份逐项核查通过，证明为同目录verified-evidence.json。较早集合按原始输入、制品及执行范围读取。

实际模板存储结构四项检查0.282秒通过，当前回执`.implementation-work/IC-11-STORAGE-20261005-TEMPLATE-CREATE-FINAL-02/receipt.json`，SHA`bcab304f49b63b41d09459cfb93c0c5f7de53b95752691a1d817a2eaf4766908`；当前输入及产物字节与完整28组回归共同核查通过。这些结构检查不能证明客户模板成功保存、核准或者后续采用。

任务状态引用[实施任务计划](../../实施任务计划.md)，当前IN_PROGRESS，文档有效性CURRENT，last_verified=2026-10-05仅指本次规范和当前代码核查。

检索[索引](index.md)、PRD§8.3.5/8.5.2、LLD-数据§4.7、LLD-后端TP-01—06、template_version/requirement_node/field_definition/repeat_definition现有迁移及TemplateDetail。模板主记录此前尚未建立；完整业务验收AC-18/45/46/50/57保持原状态。

当前开发采用PRD规定的非IVD首次注册六章38个二级节点，完整保留官方编号、标题、父节点及顺序。唯一数据资源为business/medical-persistence/src/main/resources/registration-directory-2021-121.json；RegistrationDirectory作为Spring组件在启动时通过Jackson严格读取，JGraphT DirectedAcyclicGraph核查目录关系，重复编号、缺少父节点及顺序冲突就地失败。requireComplete比较完整原始目录，条件性节点保持存在。该目录明确为REFERENCE_ONLY，固定实际来源ID及SHA，不代表企业已核准模板、项目适用性或现行法规已完成复核。模板不得通过目录缺项、节点移动或隐藏条件性节点取得发布资格。

Given既有PRD完整目录及实际来源；When模板提供节点集合；Then完整编号、标题、父子关系和顺序必须一致，未知适用条件继续保留，各项目的人工适用决定独立保存。RegistrationDirectoryTest重读真实来源全部字节及原摘要，并直接比较原文44行目录，验证缺少条件性节点、6.1/6.2标题变化及节点归属变化的拒绝结果。四项检查通过，无失败、错误或跳过，0.861秒；GREEN-01原始回执.implementation-work/IC-11-DIRECTORY-20261005-REGISTRATION-DIRECTORY-GREEN-01/receipt.json，SHA d898047f9c120efe1eff60b90427ac3a72fd6afa722c1ef6c091254e0683f76c。原RED-01四项缺少完整规范目录的断言失败，错误及跳过为零，SHA b821bb20e43d8c98689ba93a26178840325fabf87816320aab0e8e608f43a3d0。独立命令为scripts/check_template_directory.py --execution，完整业务AC保持未通过，目录组件、模板核准服务、项目消费及正式页面分别验证。

全部十三文档入口、十一类字段、重复行、真实附件、条件三态、声明式规则引擎、人工适用决定、依据影响、模板草稿/核准/发布、正式S07/S18/S09和项目任务消费继续保持完整要求。真实客户模板、项目及资料许可仍需满足对应正向验收前置条件；本次不创建客户记录、批准或模型响应。

关联IMP-03维护共享Schema及已有表，IMP-10维护来源核准与生命周期，IMP-12消费固定模板及完整计划，IMP-17—22使用各专业文档要求，IMP-23核查签发门槛，IMP-26使用固定版式与成品清单。权限、日志、事务、JSON和图处理继续复用现有框架及依赖。

BACK-TEMPLATE-DEFINITION-01采用补充设计§4.17，templates.schema.json、TemplateProtocol及TemplateDefinitionSchema表达十三文档类型、十一类字段、目录、重复行、附件、条件、跨文档规则、版式和全部固定依据。成熟Jackson/networknt/JGraphT及原PermissionSchema直接复用。草稿输入不接受企业、创建人、批准、状态或者产品默认值；真实附件仅声明AUTHORIZED_ORIGINAL_ONLY，未知条件始终保留UNKNOWN。目录、字段、重复列、附件和计算依赖执行完整身份关系校验，规范内容及全部依赖摘要形成Validated。内部主库保存已有完整实现；公开调用、核准发布、真实依赖权限/核准核验、项目消费及正式页面继续按原完整验收实施。

Given实际PRD、阅读设计和既有官方目录；When读取完整模板定义；Then十三种入口与十一类字段分别表达，禁止输入产品默认值和自行批准，真实附件禁止叙述或计算生成，条件固定规则版本，重复行与输出保持稳定身份，REFERENCE_ONLY目录不能直接成为客户模板。TEMPLATE-DEFINITION-RED-01八项实际断言失败，错误/跳过均为零，原因是完整Schema缺失；回执SHA945f9d81415a306d6a7f5f856268cda7e07c72e6c5c15c33528334d8cf58a863。GREEN-01十三项组件检查通过，包括原目录四项及模板九项，回执SHA955208e58dddd63cccaf4407aa6bbbf37dfcd7784bb6148db091d08b1cbb7bc9。回执目录分别为.implementation-work/IC-11-DIRECTORY-20261005-TEMPLATE-DEFINITION-RED-01及GREEN-01；未构造客户、产品、来源批准或人工签发，完整模板正向业务保持未运行。后续制品验证使用当前源码重新构建，原247项及PID54071仅证明此前目录制品执行范围。
