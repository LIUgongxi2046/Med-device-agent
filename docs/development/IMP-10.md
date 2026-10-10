# IMP-10 核准、资料集、来源关系与生命周期

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
