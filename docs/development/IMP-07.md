# IMP-07 模型配置、预算、调用与结算

2026-10-09新增SimulationCase、SimulationProvider及SimulationCompletion，沿用已授权的模拟输入。配置、完整模型输入、参数及原回执全部固定，完整文本、工具参数和事件保持原内容；模拟身份明确标记，缺少用量保持空值。三项当前检查已经通过，原始回执为`.implementation-work/IC-07-SIMULATION-20261009-SIMULATION-GREEN-01/receipt.json`，SHA-256为601f6c2cb459256389c1f0f915685ca04593db3eb834df632105b4e4b9a26fbb，exit_code=0、inputs_unchanged=true。实际wheel全部六份源码字节及隔离安装后的31项检查已经通过，回执为`.implementation-work/IC-07-PACKAGE-20261009-SIMULATION-PACKAGE-01/receipt.json`，SHA-256为b607a870e1901e2358cb501f00c426964f6eb4f0424172a1c00c9a6c98949ef1。完整C07的配置发布、预留、一次发送、结果保存、结算及页面继续开发，工单保持IN_PROGRESS。

2026-10-06当前来源选择及完整制品由唯一[IMP-12](IMP-12.md)维护，SCOPE-SELECTION-NATIVE-01完整300项和部署字节核查通过。新选择复用原正式来源及企业使用许可；外发继续要求独立批准，PLAN_DRAFT继续使用SYSTEM_JOB。当前预算集合仅适用于自身原输入范围，DeepSeek本地check为configured=true，没有模型请求。完整模型配置、批准预算、实际发送、采用和结算继续按本工单实施与验收。

## 已有实现与各执行集合的原始范围

本节中的执行身份、制品、数量和验证结果分别对应其注明的集合；当前任务组件和部署证据使用IMP-12。

2026-10-06BACK-RUOYI-DATA-SCOPE-01及当前制品由唯一[IMP-04](IMP-04.md)维护。275项Java、30组本地回归及31份当前源码、产物和双服务身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261006-RUOYI-DATA-SCOPE-FINAL-01/verified-evidence.json`。原框架身份和数据范围不授予医疗企业预算或资料外发许可；原预算及页面检查通过当前制品回归。既有DeepSeek配置继续保留，没有新增模型请求；完整模型配置、许可、预算、真实结算和业务结果采用继续按原要求实施与验收。以下集合只保留各自原始范围。

2026-10-06FRONT-ENTERPRISE-REVIEW-01共享前端回归由唯一[IMP-06](IMP-06.md)维护，ENTERPRISE-REVIEW-FINAL-01完整30组及31份当前输入、产物和运行身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261006-ENTERPRISE-REVIEW-FINAL-01/verified-evidence.json`。模型预算、历史和其他原页面保持已有权限要求。DeepSeek本地check再次返回configured=true，密钥继续位于原0600文件；没有模型请求、费用或者客户授权。业务模型配置、发送准入、预留、真实结算和结果采用保持完整未验收，较早集合按原始范围读取。

2026-10-06原管理身份及当前运行制品由唯一[IMP-04](IMP-04.md)维护；原框架身份不授予客户企业、预算、资料外发或者模型调用权限。当前275项Java、30组回归及31份当前源码/产物/运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261006-RUOYI-NATIVE-IDENTITY-FINAL-01/verified-evidence.json`。原预算和页面检查通过当前制品回归，完整配置/价格/调用/结算与客户业务继续验收。DeepSeek本地check返回configured=true，秘密继续读取原0600文件，没有模型请求或收费；较早集合保持各自原始范围。

2026-10-06正式企业登记入口由[IMP-06](IMP-06.md)维护，真实客户输入与人工审查需要原完整验收。ENTERPRISE-REGISTRATION-FINAL-01当前30组及原273项Java回执共31份字节和运行身份核查通过，包含原预算及开发/生产拒绝检查；完整证明为`.implementation-work/FULL-REGRESSION-20261005-ENTERPRISE-REGISTRATION-FINAL-01/verified-evidence.json`。DeepSeek本地check返回configured=true，秘密仍读取既有受保护文件，没有新增模型请求；企业登记不产生模型发布、外发许可、预算或收费，全部专业生成与结算继续按原要求实施。

当前完整制品、账户及原密码服务运行证据读取[IMP-04](IMP-04.md)。本地DeepSeek检查configured=true，秘密引用为.implementation-work/runtime/deepseek-api-key，没有新增模型请求。原RuoYi平台身份不授予客户模型调用或预算权限；完整模型发布、价格、预算、调用、结算和专业生成继续实施。

2026-10-05 Servlet接收模型关联：新HTTP接收只消费当前传输准入与原租期，不授予MODEL_SEND、TaskToken或预算权限；十二项真实HTTP工程检查没有模型请求及客户费用写入。本地DeepSeek检查返回configured=true，配置位置读取[DeepSeek说明](../deepseek-configuration.md)；完整当前制品和运行证据由唯一[IMP-05](IMP-05.md)维护。

2026-10-05对象截止模型关联：ObjectRequestDeadline只限制实际对象请求，不授予MODEL_SEND、TaskToken/lease或预算权限；67项上传工程没有模型发送或客户费用写入。DeepSeek保持原秘密配置，完整源码/制品/运行及实际对象故障证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05完成传输并发模型关联：原分片及完成恢复、重组和完整发送连接同一传输Lease，不能据此获得内部运行、MODEL_SEND或预算使用权限。63项工程检查没有模型请求或客户费用写入；DeepSeek保留既有秘密配置，完整制品及运行证据由唯一[IMP-05](IMP-05.md)维护。

2026-10-05传输并发模型关联：TransferConcurrency只管理传输名额，不能替代模型运行准入、TaskToken/lease、预算或费用核对；当前工程检查没有模型发送。59项上传工程及当前完整制品/运行证据由唯一[IMP-05](IMP-05.md)维护，DeepSeek保持既有秘密配置。

2026-10-05上传输入模型关联：IncomingUploadPart和receiveStream只接收固定应用会话的DOCUMENT_BODY输入，不授予MODEL_SEND或AGENT_ARTIFACT运行权限；实际内部认证、TaskToken/lease、许可/预算及模型业务继续由原工单实施。52项上传工程检查没有模型请求，完整制品及运行证据由唯一[IMP-05](IMP-05.md)维护，DeepSeek使用既有秘密配置。

2026-10-05当前许可事务模型关联：DocumentUploadIssuance限定DOCUMENT_BODY，通过原FORMAL依据准备许可并保存幂等/审计/事件；没有模型发送、收费或AGENT_ARTIFACT许可。内部TaskToken/mTLS/lease、C07专属ModelResult、固定ContextInput、预算与结算继续保留完整原要求。本轮没有新增模型请求，DeepSeek秘密位置读取[配置说明](../deepseek-configuration.md)；当前44项工程及完整制品/实际运行范围读取唯一[IMP-05](IMP-05.md)。

2026-10-05当前上传依据模型关联：DOCUMENT_BODY明确使用FORMAL来源和当前任务Basis；AGENT_ARTIFACT的document_basis为空，运行依据继续要求真实TaskToken/mTLS/lease，不能调用当前公开文稿权限消费者。ModelResult保存、ContextInput发布引用、模型许可/预算/结算继续保持完整原设计。DeepSeek本地check返回configured=true，密钥位置读取[配置说明](../deepseek-configuration.md)，本轮没有新增模型请求；当前工程和运行范围读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传传输模型关联：当前消费者限定实际应用DOCUMENT_BODY身份和固定发布DocumentBody，AGENT_ARTIFACT、TaskToken/mTLS/lease、ContextInput与C07专属ModelResult仍使用原内部要求。实际分片写入及恢复不提供模型发送或费用结算；本轮没有模型请求，DeepSeek本地check返回configured=true，配置位置读取[配置说明](../deepseek-configuration.md)。当前制品及工程范围读取唯一[IMP-05](IMP-05.md)，完整模型业务继续实施。

2026-10-05上传授权模型关联：完整清单保留原来源/领域及上游要求，公开DOCUMENT_BODY授权拒绝MODEL_SEND和AGENT_ARTIFACT。当前组件不授予模型发送、C07 ModelResult保存或内部运行认证；TaskToken/mTLS/lease、外发许可、预算和费用继续按原设计实施。DeepSeek本地check再次返回configured=true，没有新增模型请求，配置位置读取[配置说明](../deepseek-configuration.md)。当前制品和证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传保存模型关联：AGENT_ARTIFACT绑定保留原run/step/principal/lease、固定Schema摘要和完整依赖身份。内部Store没有提供TaskToken认证或模型发送入口，ModelResult仍由C07保存；当前DeepSeek本地check返回configured=true，没有新增模型请求或客户费用。完整授权、预算、未知费用核对与业务采用继续实施，工程证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传模型关联：026的AGENT_ARTIFACT绑定固定运行/步骤/主体/lease、原Schema摘要及完整依赖清单；ModelResult仍由C07保存，ContextInput仍需固定发布Schema。当前十项数据库检查只证明物理关联与约束，没有模型清单或生成请求、客户外发批准和实际费用记录。DeepSeek原配置保持完整，完整模型发送、预算、未知费用核对及业务采用继续实施，当前证据读取[IMP-05](IMP-05.md)。

2026-10-05对象写入模型关联：S3ObjectWriter的原对象恢复只核对已写入私有存储的完整字节，不能代替ModelResult的C07保存、模型发送许可、预算和费用核对。原MODEL_SEND/TaskToken/lease要求保持完整，Python模型源码保持原字节，本次没有模型清单或生成请求，DeepSeek密钥已经配置。当前证据读取[IMP-05](IMP-05.md)，以下集合保留原始范围。

2026-10-05独立依赖模型关联：DependencySourceAuthority共同核验原清单及全部来源和领域授权。读取结果不能代替MODEL_SEND所需PHI处理、外发许可、预算、运行主体、TaskToken及lease。Python模型源码保持原字节，本次没有模型清单或生成请求；DeepSeek密钥已经配置，配置位置读取[配置说明](../deepseek-configuration.md)。当前证据读取[IMP-05](IMP-05.md)，以下集合保留原始范围。

2026-10-05完整输入关联：IMP-05的ArtifactContentService连接实际完整字节及逐页读取回执，完整结果在consume/consumeJson前重查全部当前授权。应用会话路径拒绝MODEL_SEND；C07发送仍须PHI、外发许可、预算、TaskToken、运行主体及lease核验，不能凭服务端读取回执发送模型。CONTENT-READ-BUILD-01完整155项原生及JavaPID3730的适用运行检查通过，证据读取[IMP-05](IMP-05.md)。Python模型源码保持原字节，DeepSeek密钥已经配置，本次未调用模型清单或生成；完整模型业务继续验收，下方集合保持原始范围。

2026-10-05共享输入前置核查：IMP-05的VersionResourceAuthority已逐项核查36类派生版本当前读取许可、会话范围及每个上游制品的固定领域读取要求，来源Use与服务端ReadContext一致。内部结果仍需实际内容读取/回执、数据处理、预算及运行身份准入才能发送模型。VERSION-AUTHORITY-BUILD-01完整150项原生及JavaPID341的六项原操作、两组登录DOM检查通过；证据读取[IMP-05](IMP-05.md)。Python模型源码本次保持原字节，DeepSeek本地check有效，没有新增/models或生成请求；完整模型业务仍未验收，下方集合保留原始输入范围。

2026-10-05 BACK-SOURCE-READ-AUTHORITY-01当前关联：SourceRightsSchema与SourceReadAuthority核查原权利、提供方/区域/环境/分类、期限、允许操作及当前source.external.process，ArtifactSourceAuthority要求全部原始来源和成员权利归属一致；预算、运行身份及完整发送记录继续由业务准入实施。当前145项原生通过，JavaPID97158；DeepSeek本地check返回configured=true，秘密文件保持原位置，没有新增/models或生成请求。合法客户模型输入、预算及发布继续未验收，当前证据由IMP-05维护。

2026-10-05 BACK-RESOURCE-READ-AUTHORITY-01当前关联：配置正文消费者可使用内部ResourceReadAuthority及ArtifactRegistry固定主资源/Schema/依赖，完整来源权利、外部处理许可及回执继续核查。RESOURCE-REGISTRY-BUILD-01完整138项原生通过，新JavaPID92494；受影响预算/身份回归由IMP-05的RESOURCE-REGISTRY-FINAL-01维护。DeepSeek密钥已经配置，当前没有额外模型清单或生成请求。实际客户模型输入、配置及预算成功仍未验收；下方集合保持原始范围。

2026-10-05 BACK-ARTIFACT-SCHEMA-01当前关联：配置正文需要的固定发布Schema和完整JSON核验由IMP-05提供，PUBLISHED-SCHEMA-BUILD-01完整131项通过。客户端引用不能选择未发布类型或不同摘要，领域配置仍需核查原规定类型及完整依赖；JSON核验不授予模型发送权限。当前25组工程回归及补充字节核查由唯一IMP-05维护，其他集合保留原始范围。DeepSeek密钥已配置，最新本地check通过；完整配置发布、客户外发许可、调用及结算继续未验收。

2026-10-05 BACK-ARTIFACT-REGISTRY-01当前关联：配置正文需要的主资源/Schema/依赖/读取范围结构已经追加025，实际五项检查、完整125项原生、25组回归和30份当前字节核查通过；预算及历史页面、原模型28项及实际wheel安装后28项保持当前输入。DeepSeek本地check确认configured=true，原秘密位置保持0600，没有重新调用模型清单或收费生成。完整Artifact服务、配置发布、客户外发许可、调用及结算仍未验收；下方集合保持原始范围。

2026-10-05 BACK-OBJECT-PERSISTENCE-01当前关联：配置正文需要的存储引用转换及固定版本读取由IMP-05提供，StoredObjectReferences保持精确大小并核查原JSON/标量。PERSISTENCE-BUILD-01完整125项、最终七项实际存储引用、24组回归与30份当前核查通过；预算和历史页面、原模型28项与实际wheel安装后28项均符合当前输入。DeepSeek本地check通过，密钥仍保存在原0600文件；没有再次读取模型清单或调用生成接口。完整Artifact、实际模型配置、客户外发许可、预算准入、调用及结算继续未验收。下方集合保持原始范围。

2026-10-05 BACK-OBJECT-ASSEMBLY-01当前关联：配置正文需要的内部完整内容消费已由IMP-05提供，ObjectContentPaging.readComplete核查全部字节、原完整摘要及当前准入；实际14项与完整118项原生检查通过。ASSEMBLY-FINAL-01全部24组回归通过，预算及历史页面重新通过；模型和实际wheel的补充字节核查读取IMP-05。DeepSeek本地check通过，没有再次读取模型清单或调用生成接口。完整Artifact、实际模型配置、客户许可、预算准入、调用、结算和业务采用继续未验收；下方集合保持原始范围。

2026-10-05 BACK-OBJECT-PAGING-01当前关联：配置正文需要的固定对象分页、BASE64实际字节、完整摘要及受保护游标已经由IMP-05实施；完整Artifact授权、资源与Schema登记、依赖及读取回执继续实施。PAGING-FINAL-02完整24组/30份当前核查通过，预算、历史页面、原模型28项及实际wheel安装后28项匹配当前输入；新Java110项及实际分页六项共同核查。DeepSeek本地配置check通过，没有再次调用模型清单或收费生成；完整配置、客户许可、调用和结算仍未验收。

2026-10-05 BACK-OBJECT-READ-01关联：模型配置正文所需的Java内部固定版本读取及完整SHA核验已由IMP-05实施，真实公开原件检查通过；Artifact授权、依赖集合和读取回执继续实施。OBJECT-READ-GREEN-02原生104项及OBJECT-READ-FINAL-01的24组/29份当前核查通过，预算五项、历史页面、原模型28项和实际安装后28项均符合当前输入。DeepSeek秘密本地check继续通过，位置读取docs/deepseek-configuration.md；没有重新调用模型清单或收费生成。完整配置发布、客户许可、实际调用与结算仍未验收。

2026-10-05 BACK-OBJECT-STORAGE-01关联：模型配置正文和大型调用产物所需的本地私有对象服务前置已实际核查，唯一主记录为IMP-05；C03受控读取、Artifact登记及当前企业授权仍需实施。OBJECT-STORAGE-FINAL-01全部24组与29份当前证据核查通过，包含预算HTTP五项、历史页面及原Java96项、模型28项和实际安装后28项；原模型与Java输入未变更。客户预算、收费调用、配置发布和完整结算仍未验收，DeepSeek秘密已经配置，无需重复索取。

2026-10-04当前核查范围为BACK-BUDGET-HISTORY-01。最终BUDGET-HISTORY-02原生96项通过，完整BUDGET-HISTORY-FINAL-01的23组通过，26份当前源码、产物及运行身份核查通过。Java进程PID56990、部署UUID0105712b-4e55-4f45-b719-9acdaac5abf8、不可变制品摘要fe2e5cf920a1c30c0787ca31b404e7571e8f34602059b89117e9365d08d8c4a9，启动24.751秒。预算HTTP五项、正式Vue开发十组/生产七组、本人账户两种入口各五组、原组件六项、账户HTTP31项及迁移覆盖15项实际通过。历史客户成功、并发及撤权仍未验收；完整IC-07保持IN_PROGRESS。下方其他集合只表示各自原始输入、日期及运行范围。

2026-10-04关联核查：IMP-04提供本地企业登记、人工审查、岗位治理及项目接口，批准事务按明确条件建立首位管理员治理许可；当前没有实际客户申请、企业或费用账户。MODEL-PRICING-02的适配器28项已经通过，回执为IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02，SHA-256为f252a9411e570b42fe20bd84649dce0e354662036d1772878748eaeb2a7a95dc。SETTLEMENT-PACKAGE-01已经实际构建并安装wheel，五份源码字节核查、包含pricing的实际安装位置核查及28项隔离重复检查通过，回执SHA-256为9e5e2c19191f635280f9595805fe194c460921a89bc3350b234e9fb8098c9976；此前PROJECT-01的18项与旧制品仅表示对应原始输入。真实供应商连接已通过，收费调用及完整结算继续未验收。真实企业准入及模型外发许可仍不能由平台岗位替代。

当前DeepSeek密钥文件已经配置，check实际通过；verify使用固定HTTPS端点的真实/models接口取得deepseek-flash和deepseek-v4-pro。scope为PROVIDER_CONNECTION_ONLY，回执为.implementation-work/deepseek-verification-4cd398d4-eb70-4e72-b060-fbfd1b76a614.json。秘密文件权限0600、目录0700，回执及输出没有保存秘密。本次没有生成或Token收费记录；模型许可、明确版本、价格、预算事务、业务采用及真实质量验证仍需完成。

| 字段 | 当前内容 |
| --- | --- |
| task_id | IMP-07 |
| 需求与验收 | PRD FR-02/FR-09、AC-03/04/24/28、NFR-10；IC-07 |
| task_source | [实施任务计划](../../实施任务计划.md) |
| 状态快照 | IN_PROGRESS；2026-10-04；完整 IC-07 尚未验收 |
| 代码与环境 | 根仓库 main 尚无提交；检查回执绑定当前源文件及实际安装版本 |
| 文档有效性 | CURRENT；2026-10-04；当前实施范围及未完成内容 |
| 检索字段 | DeepSeek、C07、medical_model_gateway、供应商 SDK、凭据、流式事件、预算、调用结算 |

## 检索与输入依据

读取开发索引、IMP-01/03/04/37、实施续接记录、PRD §8.2/8.9、HLD 的 C07/C12 分工、LLD-Agent §4.3 和唯一模型流 Schema、LLD-后端 §6.2/6.3、接口补充设计 IA-06/07/09/10。当前 Java 身份模块已经提供独立本地登录，Python 共享模型类型已经实现，实际模型适配器尚需建立。当前用户明确选择 DeepSeek API 并要求提供本地配置位置。

供应商协议依据当前官方 Chat Completions、Lists Models、Thinking Mode 和价格文档；安装版本由真实 PyPI 查询固定。密钥仅由 C07 Python 适配器读取。Java 负责企业权限、调用身份、原子预算和发送准入，专业 Worker 不取得供应商密钥。

## 当前功能与实现

BACK-BUDGET-HISTORY-01继续本主记录，关联IMP-06/05。读取预算Schema、Service/Controller/Protocol/Mapper、007不可变DDL与正式预算页面，核实公开入口仅提供当前批准。接口补充设计§4.16增加BD-05/06及独立历史响应Schema，保持原批准manifest。Given真实本地所有者没有企业身份，When访问历史列表及版本，Then当前权限拒绝；无效路径、查询及Header仍按输入规则拒绝，数据库不产生账户、批准、费用或事件。合法客户历史、摘要损坏、撤权及并发批准需要真实企业和许可，继续保留未验收状态。

当前实现为BudgetService.history/historyRevision、BudgetHistorySchema及BudgetMapper.history/historyCount，读取当前授权以后核验原批准manifest、RFC8785、version_registry、币种、主体和原依据。正式budget-history.vue及budget-history-entry.vue通过当前Router、Pinia、Axios和Element Plus展示完整批准，当前预算详情提供历史入口，原批准版本可继续导航。protocol注册独立历史Schema；budgets.ts检查完整定义、当前账户/批准身份、指定目标、许可、U64及游标排序。身份或目标变化清除内容并终止旧读取，异步结果只进入同一主体和目标。IdentityErrorResponseWriter明确编码CONTENT_MISMATCH、OBJECT_UNAVAILABLE、BUDGET_EXCEEDED及REVISION_LIMIT，错误使用共享封闭Schema；原生序列化校验不创建业务回执。

历史HTTP Red为IC-07-BUDGETS-20261004-BUDGET-HISTORY-RED-01，登录正常、现有分支通过，缺少历史入口导致404与403/422断言不匹配；SHA-256为8e17bcc6f654f637b9d3cddb2bad87944f04841cc81aba6b0ef5878075e324c2。正式页面Red为BUDGET-HISTORY-FRONT-RED-01，现有预算页面正常，历史标题缺少导致等待失败；SHA-256为61b1e5aa19ba331ab9a76f9fddba086b9ef70b79a4a02cd4b412f9f5a403139b。错误编码Red为IC-07-BUDGET-ERRORS-20261004-BUDGET-HISTORY-ERROR-RED-01，原生错误编码检查未通过；SHA-256为1d513f0280f13a4d49f4f7c9fbabbc87ff83605e149aefcc41c5b6479850d387。这些原始回执均inputs_unchanged=true，后续Green使用最终新代码和新制品。以下其他集合保持各自原始输入及运行身份范围。

FRONT-BUDGET-MANAGEMENT-01继续本主记录，关联IMP-06及IMP-05。本次读取BD-01—04、budgets.schema.json、LLD-前端§2.1—2.3、S13界面规范，以及正式project-write、operations、protocol、user/permission和真实会话浏览器检查。预算页面使用现有Layout、Router、Pinia、Axios和Element Plus，不建立替代企业、批准依据或供应商回执。用户入口为/agents/budgets、new、预算详情及decisions；字段、币种和六项限制全部继承当前预算Schema。操作元数据只保存主体、企业、原目标、原版本、原上下文、请求键和正文摘要；金额、批准原因与依据内容保持页面内存。预算作用域精确处理ENTERPRISE/PROJECT/BUDGET，创建需要可创建范围，追加批准同时要求当前读取及批准；授权摘要不能扩大服务端许可。列表按实际游标和已应用筛选读取；账户及批准的企业、项目、币种、主体与版本分别核查。提交保持原请求与原正文摘要，逐项核查回执的上下文/治理增量、固定批准、原操作及获授权账户定义；原查询NOT_FOUND/EXPIRED/PROCESSING/FAILED保留原请求且不自动发送。离开及同页面切换预算均处理未保存输入，身份失效同步清除表单和原生控件，旧异步响应不能覆盖当前对象。合法客户成功、并发批准、权限撤销及模型消费者仍按原验收要求执行。

有效BDD Red为BUDGET-FRONT-RED-01，真实登录及既有项目分支正常，缺少预算页面标题导致等待断言失败；原回执SHA-256为bc003720d0035af55478252925d4343a94aff3df4c57b591da9da45e4daa1c8c，inputs_unchanged=true。当前Green为BUDGET-FRONT-FINAL-01：实际strict、正式构建、开发十组及生产七组通过，9016项输入与200项产物绑定，回执SHA-256为e2944a7efb392c10e9446cbace3c34fa3fbe924aa2bbd2750104746044d62866；原BUDGET-FRONT-GREEN-01只表示对应原始输入。真实MySQL客户企业、成员、项目及原操作前后均为零，范围ACTUAL_OWNER_WITHOUT_ENTERPRISE；完整客户业务未取得通过结果。

FULL-REGRESSION-20261004-BUDGET-FRONT-FINAL-01实际23组全部通过，结束时间2026-10-04T14:13:02.765445+00:00。inspect_current_evidence.py核查这23组、未修改的Java95项、模型28项及实际安装后28项，共26份当前输入与产物相符；evidence.json摘要为72b536679c5a6ddcc38aad754d73771fee479ccc1c0177caae22b9596d117773，verified-evidence.json摘要为c127c73d3693428faad8cdc467638f88910b0ccb6b070aa34c4a58432c4a623c。当前本人账户两种入口各五组、预算四项HTTP、原操作六项、账户HTTP31项、原组件六项及迁移覆盖15项通过。Java部署保持原PID46382、UUID及不可变制品，原生95项和模型制品无需重复构建；其当前输入及产物已经实际核查。

BACK-BUDGET-MANAGEMENT-01继续本主记录。当前检索实际007预算DDL、PermissionAuthority、EnterpriseAccess、ProjectController/Service/Schema/Mapper、OperationService及真实账户HTTP检查。BudgetController/Service/Schema/Protocol/Mapper已经编码BD-01—04，接口补充设计§4.16定义完整限制、批准依据、原请求恢复、账户CAS及同事务审计/Outbox。实现采用既有budget_account/budget_revision/version_registry，不修改已运行迁移。实际Red为IC-07-BUDGETS-20261004-BUDGET-RED-01：实际所有者登录正常，预算入口缺少导致三项404断言失败，CSRF及Origin检查通过，回执SHA-256为a06f26b714a8cd8a97c61f1de3ea3033ec65be2e3a9048c005845b833f38e424。BUDGET-GREEN-01的四项真实HTTP及零客户费用检查通过，回执SHA-256为c8e0aa8b05836d2487f37e9c5eea63571cc30e9ee3b2d40663add4667b34341c；其输入范围由原始回执定义。最终BUDGET-02原生clean verify通过95项，回执SHA-256为95820c0a00422c8361164c40d324ea141cf67e3481bec18a4a2a2eefcc6710cd；五项预算检查及16条查询核验通过。最终程序PID46382、部署UUIDd9a21341-3348-4286-b69d-db5eb5473273、摘要5889d881f77b794c140df65f07fa55229cd81dd6cb03e124c8a5c99ca4a92178，启动23.272秒；原生进程和不可变制品由检查入口重新核验。BUDGET-FINAL-01的23组共享复验实际全部通过，预算四项回执SHA-256为7c37261747d77fc1f7d733974cc909c03fb9f5d715326ce01122752fe88c4972；inspect_current_evidence.py核验这23组及Java95项、模型28项和实际安装后28项，共26份当前输入与产物相符，结果为对应目录verified-evidence.json。客户预算创建、合法批准、并发额度竞争及收费结算保持待验收，完整IC-07仍为IN_PROGRESS。

BudgetService在当前企业及认证会话锁内重新读取budget.read/budget.approve的有效许可，明确处理ENTERPRISE、PROJECT及BUDGET范围和用途。项目锁先于预算锁；列表按项目UUID及预算UUID排序取得对应锁。批准人取当前主体，金额和U64保留精度，完整定义及RFC8785摘要固定到budget_revision和version_registry。创建保留初始零累计；追加使用账户及原批准版本CAS，不修改累计金额/计数，不释放原预留，不重新绑定或启动运行。新金额、Token及调用数限制不得低于累计占用；所有限制缺少或期限无效均拒绝。原请求保存实际路径、会话及原上下文摘要，原响应重放重新核查当前许可和版本；账户、定义、原操作、审计和Outbox均重读核验。正式模型配置、调用及结算消费者继续实施。

批准事务共同更新governance_epoch及当前会话context_revision以后，从原生企业Mapper重读两项水位，并按实际context_id、session_id、user_id和tenant_id重读平台会话；任意不匹配立即终止事务。原响应校验要求回执企业与current_context企业相同。Account及批准定义的所有计数维持U64范围，金额数学检查包含精确边界和累计占用下限；这些检查没有创建企业、预算批准、供应商用量或业务样本。

MODEL-PRICING-02依据LLD-后端§3.1/6.2、LLD-Agent§4.3的互不重复用量口径及DECIMAL(24,8)要求，继续本主记录。费用计算覆盖未缓存输入、缓存读取、缓存写入和输出；reasoning_tokens属于输出子项，不重复计费。预算上界需要覆盖所有已发布输入类别的最高单价。实际金额计算使用价格版本明确提供的舍入规则，不设置默认规则；非零单价类别的未知用量返回null，其他无效价格或计数仍立即拒绝。零单价类别不会将未知用量转换为零计数。数学检查不创建价格批准、预算、调用或供应商用量记录；真实结算仍需要实际回执、账单及同一预算事务。

在 `model-gateway/` 建立独立 Python 包，复用 `medical-contracts` 的模型事件、用量和结果类型，使用OpenAI Python SDK 3.24.0、httpx2/httpcore2 2.13.1处理HTTPS、JSON和SSE。秘密文件位于已忽略的项目runtime目录，父目录0700、文件0600；当前配置和真实连接已经通过。没有密钥或必要发布配置时立即报告错误，不访问模型生成接口。

| 文件 | identifier | 当前行为及消费者 |
| --- | --- | --- |
| web/src/api/medical/budgets.ts、budget-write-view.schema.json | budgetActionAllowed/budgetCreationAllowed、BD-01—04、PendingBudgetWrite | 精确预算范围、封闭Schema、完整字符串精度、原参数摘要、固定版本与原操作核查；供S13预算页面使用 |
| web/src/views/medical/budgets.vue、budget.vue、budget-account.vue、budget-write.vue | FRONT-BUDGET-MANAGEMENT-01 | 授权列表/筛选/游标、完整账户与固定批准、六项限制及依据表单、原请求恢复、未保存输入与当前身份清理 |
| web/src/router/index.js、api/medical/protocol.ts、utils/request.js、tests/browser/medical-session-checks.js | MedicalBudgets/Create/Approve、Ajv、现有JSON转换器 | 正式页面登记、预算传输及Schema、真实账户开发/生产路由和SDK拒绝回归；关联IMP-06 |
| model-gateway/src/medical_model_gateway/credentials.py | prepare_key_file/read_key | 使用目录描述符、O_NOFOLLOW和实际文件身份核查读取秘密；拒绝覆盖、公开权限、链接及无内容 |
| model-gateway/src/medical_model_gateway/protocol.py | ExactInput/ProviderSettings | 封闭文本投影、完整工具历史、明确限额及支持参数；供C07供应商入口使用 |
| model-gateway/src/medical_model_gateway/provider.py | DeepSeekProvider/ProviderCompletion/normalize_usage | SDK读取真实模型清单和流；业务文本/工具/实际用量映射，内部推理不进入产物；C07持久保存消费者尚需接入 |
| model-gateway/src/medical_model_gateway/pricing.py | upper_amount/actual_amount | 四类单价及互不重复用量；预算按三类输入最高单价向上保留八位；实际金额按明确舍入规则处理，未知非零单价用量返回null，其他非法值及溢出立即拒绝；预算事务尚需接入 |
| scripts/configure_deepseek.py | prepare/check/verify | 配置文件准备、本地检查和真实模型清单验证；[配置说明](../deepseek-configuration.md)提供用户操作 |
| scripts/check_model_gateway.py、scripts/build_model_gateway.py、tests/inspect_model_package.py | IC-07-ADAPTER/IC-07-PACKAGE | 当前源码和实际依赖字节、wheel构建、源码一致、实际安装位置及安装后检查 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/BudgetController.java、BudgetService.java、BudgetSchema.java、BudgetProtocol.java、mapper/BudgetMapper.java | BD-01—04 | 范围列表与读取、预算创建、追加批准、完整当前授权、双版本CAS、固定依据、原请求重放及同事务治理记录 |
| business/medical-persistence/src/main/resources/budgets.schema.json、medical-mappers/BudgetMapper.xml | Approval/Limits/Account/Receipt；18条查询 | 封闭输入输出、DECIMAL(24,8)、U64和明确截止时间；当前MyBatis及受保护企业事务 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/BudgetHistorySchema.java、business/medical-persistence/src/main/resources/budget-history.schema.json | BD-05/06；Entry/Listing | 独立历史响应，原批准Schema引用及完整身份、当前版本和原依据核查 |
| web/src/views/medical/budget-history.vue、budget-history-entry.vue、web/src/api/medical/budgets.ts | listBudgetHistory/readBudgetHistory | 完整历史列表、指定批准阅读、原批准导航、当前权限与异步身份核验 |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/IdentityErrorResponseWriter.java、business/medical-identity/src/test/java/com/ruoyi/medical/identity/OidcSecurityTest.java、scripts/check_budget_errors.py | 业务错误编码 | 明确内容不匹配、对象不可用、预算超限及版本上限，全部响应满足共享Schema |
| tests/test_budget_runtime.py、scripts/check_budgets.py、tests/check_budget_sql_columns.py、BudgetSchemaTest.java | IC-07-BUDGETS | 真实本地账户HTTP拒绝、非法输入、CSRF/Origin、数据库零客户费用；成熟解析器SQL字段、金额精度及批准下限检查 |

供应商流校验同一响应身份、单一候选、已发布工具名称和完整参数、有效终态及用量顺序。工具参数只有标准JSON解析及对应Schema检查通过后才形成完整块；当前组件不执行工具。缺少最终回执立即报告协议错误。ProviderCompletion需要交由C07保存并重读ModelResult，当前组件不提前生成finish事件。

最终Green为BUDGET-HISTORY-FINAL-01：预算HTTP五项通过，封闭历史查询和版本路径、Header、真实所有者拒绝、NoStore和响应身份均核查；实际MySQL账户/批准/预留/调用/原操作/Outbox仍为零。正式Vue实际strict、生产构建、开发十组及生产七组通过，9020项输入与203项产物绑定，回执摘要1d52a8a957398b5696cc9bc648fb1d52391364c32009dfce443a3c3ffec3b461。完整23组回归结束于2026-10-04T14:58:28.324757+00:00，evidence.json摘要e4fc236997d10bb2af30e343d038ef918c0933a3f18f21a1f8c480d4a99d4ac2；26份CURRENT_EVIDENCE_BYTES核查通过，verified-evidence.json摘要3369ea7d25738d32acc90a028cb20adef3e55739a34bb440d1637a01888fa032。模型28项及wheel安装后28项输入未修改，原回执当前字节已实际核查，没有再次访问供应商。工程结果没有增加业务AC通过项。

## 决策与约束

SDK 收费请求额外重试为零。端点固定官方 HTTPS，不读取代理环境配置，不跟随 HTTP 重定向。内部推理内容不进入业务产物、事件或普通日志。无法确认外部终态时保留 UNKNOWN，实际未知用量保持 null。连接验证只能证明当前凭据与端点可用，不能代替企业许可、模型发布、预算和真实文档验收。

## 验证与证据

MODEL-PRICING-02有效Red为SETTLEMENT-RED-01及RED-02：依赖与原十八项检查正常，缓存写入参数及实际金额函数缺少，RED-02还核查不完整价格未被拒绝，原始回执SHA-256分别为65c780c28ad596ca187dd21f97c3a650d18242870143021c5e0106327ee81d32和bc84084b21aa6e296af337cf6ab01bf760669528b77308e5ed3914f673fb3cc7。Green为SETTLEMENT-GREEN-02，28项检查通过，包含四类数学金额、未知收费类别、零单价未知计数、两种明确舍入边界、规则缺少、完整类型拒绝、负数及存储溢出。没有构造供应商响应、用量回执、价格批准或企业费用数据。upper_amount的cache_write_rate和actual_amount的rounding均必须明确传入；当前仅有数学调用方及安装检查，Java预算和真实回执消费者尚需接入。

| 检查 | 实际命令及结果 | 证据 |
| --- | --- | --- |
| 预算历史最终原生构建 | check_migration.py --check build --execution 20261004-BUDGET-HISTORY-02；96项通过，18条预算SQL字段及原生Schema引用检查通过，业务错误编码通过 | .implementation-work/IC-03-JAVA-20261004-BUDGET-HISTORY-02/receipt.json；27d20b87657d0bf0b75f4350a38ef108237880248bbfdf184cefb1c84febc377 |
| 预算历史实际HTTP Green | BUDGET-HISTORY-FINAL-01预算五项通过，真实所有者没有企业身份，数据库业务数量保持零 | .implementation-work/IC-07-BUDGETS-20261004-BUDGET-HISTORY-FINAL-01/receipt.json；a285b5fddcdc11b5a7524ab7506148b4a0c056ee5bdb74b5aae1c9b7365f9954 |
| 预算历史正式页面Green | 同一集合strict、构建、开发十组及生产七组通过，9020项输入和203项产物绑定；未执行视觉检查 | .implementation-work/IC-06-SESSION-BROWSER-20261004-BUDGET-HISTORY-FINAL-01/receipt.json；1d52a8a957398b5696cc9bc648fb1d52391364c32009dfce443a3c3ffec3b461 |
| 预算历史共享回归与当前核查 | 23组实际通过，Java96、模型28及实际安装后28共同纳入26份当前字节核查，通过 | .implementation-work/FULL-REGRESSION-20261004-BUDGET-HISTORY-FINAL-01/evidence.json及verified-evidence.json |
| 预算前端BDD Red | check_medical_session_browser.py --execution 20261004-BUDGET-FRONT-RED-01；真实登录及既有项目检查通过，预算标题缺少导致有效断言失败 | .implementation-work/IC-06-SESSION-BROWSER-20261004-BUDGET-FRONT-RED-01/command/receipt.json；bc003720d0035af55478252925d4343a94aff3df4c57b591da9da45e4daa1c8c |
| 预算前端当前Green | BUDGET-FRONT-FINAL-01实际strict、生产构建、开发十组及生产七组通过；合法客户写入仍未运行 | .implementation-work/IC-06-SESSION-BROWSER-20261004-BUDGET-FRONT-FINAL-01/receipt.json；e2944a7efb392c10e9446cbace3c34fa3fbe924aa2bbd2750104746044d62866 |
| 当前完整工程回归 | BUDGET-FRONT-FINAL-01实际23组通过；26份源码、产物及部署身份核查通过；范围REGISTERED_LOCAL_REGRESSION/CURRENT_EVIDENCE_BYTES | .implementation-work/FULL-REGRESSION-20261004-BUDGET-FRONT-FINAL-01/evidence.json及verified-evidence.json |
| 预算BDD Red | check_budgets.py --execution 20261004-BUDGET-RED-01；实际登录正常，缺少预算入口导致三项HTTP断言失败，CSRF/Origin通过 | .implementation-work/IC-07-BUDGETS-20261004-BUDGET-RED-01/receipt.json；a06f26b714a8cd8a97c61f1de3ea3033ec65be2e3a9048c005845b833f38e424 |
| 预算最终原生构建 | check_migration.py --check build --execution 20261004-BUDGET-02；95项通过，预算五项和16条语句原生解析/字段核查通过 | .implementation-work/IC-03-JAVA-20261004-BUDGET-02/receipt.json；95820c0a00422c8361164c40d324ea141cf67e3481bec18a4a2a2eefcc6710cd |
| 预算最终HTTP Green | check_full_regression.py --execution 20261004-BUDGET-FINAL-01中的check_budgets；四项实际账户/输入/CSRF/数据库检查通过，客户预算和调用仍为零 | .implementation-work/IC-07-BUDGETS-20261004-BUDGET-FINAL-01/receipt.json；7c37261747d77fc1f7d733974cc909c03fb9f5d715326ce01122752fe88c4972 |
| 当前共享回归与证据核查 | BUDGET-FINAL-01的23组通过；inspect_current_evidence.py核查23组、Java95、模型28和安装后28共26份输入及产物，通过 | .implementation-work/FULL-REGRESSION-20261004-BUDGET-FINAL-01/evidence.json及verified-evidence.json；范围REGISTERED_LOCAL_REGRESSION/CURRENT_EVIDENCE_BYTES |
| 密钥BDD Red | 九项检查因缺少prepare_key_file/read_key失败；依赖及导入正常 | .implementation-work/IC-07-CREDENTIAL-RED-20261004-01/receipt.json；0145d8f3168e808197f0451852a7018240c431e52b2972525acb95fd865e3278 |
| 日志BDD Red | 实际SDK启用DEBUG后未满足日志保护断言，十四项中一项失败 | .implementation-work/IC-07-ADAPTER-20261004-LOGGING-RED-01/receipt.json；89f0ed5be927b44b8c5566e80d15fed25c8e56d90c707d3a2c8e16a21ccb3526 |
| 金额BDD Red | 四项价格计算检查因缺少upper_amount失败；其余十四项通过 | .implementation-work/IC-07-ADAPTER-20261004-PRICING-RED-01/receipt.json；a8f916b1abe7a72bd0161933ff233a21734fe1755d292eb4b4bd5a18f3fc080c |
| 当前Green | model-gateway-venv/bin/python scripts/check_model_gateway.py --execution 20261004-SETTLEMENT-GREEN-02；28项通过，输入未变更 | .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json；f252a9411e570b42fe20bd84649dce0e354662036d1772878748eaeb2a7a95dc |
| 实际制品 | 相同解释器执行check_model_gateway.py --check package --execution 20261004-SETTLEMENT-PACKAGE-01；实际wheel五份源码字节相等、包含pricing的安装位置核验、28项重复检查、pip check通过 | .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json；9e5e2c19191f635280f9595805fe194c460921a89bc3350b234e9fb8098c9976 |
| 受影响输入登记 | .lld-data-work/venv/bin/python scripts/check_inputs.py --execution 20261004-SETTLEMENT-INPUTS-01；五项检查通过，实际数据字节、授权范围、关系和唯一开发记录重新核查 | .implementation-work/IC-02-LOCAL-20261004-SETTLEMENT-INPUTS-01/receipt.json；2395b797261a195c42d306ef85fe31cc0e27c1be456d7f4bf99466d5d06d702c |
| 配置操作 | configure_deepseek.py check及verify实际通过，真实清单为deepseek-flash/deepseek-v4-pro，配置文件权限0600/父目录0700 | PROVIDER_CONNECTION_ONLY；真实生成、业务采用和实际费用尚未运行；回执为deepseek-verification-4cd398d4-eb70-4e72-b060-fbfd1b76a614.json |

原生检查包含当前SDK实例的零重试、无环境代理、无重定向、连接及读取时限和DEBUG保护。用于存储检查的秘密由本地密码组件实际产生，只用于文件及本地SDK实例检查，没有发送供应商请求。金额检查只核查十进制数学、计数类别及存储精度，没有创建调用用量或企业价格批准记录；测试数字不能作为发布价格使用。真实模型流、费用、终态及完整业务路径尚未验收。

## 关联影响与继续条件

关联 IMP-01 的版本及构建、IMP-03 的共享模型类型、IMP-04 的当前企业授权、IMP-05 的对象与幂等、IMP-13/14/15 的执行和专业节点、IMP-28 的发布、IMP-37 的完整验收。完整 CP/IN/IA 接口、预算事务、S13 管理页面及真实调用和结算继续实施；当前不能登记 ACCEPTED。

当前S13预算管理和批准历史页面分别由FRONT-BUDGET-MANAGEMENT-01及BACK-BUDGET-HISTORY-01提供。受影响全部现有账户、企业、岗位、项目、原操作、预算及Vue流程已由BUDGET-HISTORY-FINAL-01按最终新代码与新程序复验，23组及26份当前字节核查通过。Python适配器及安装制品保持SETTLEMENT-GREEN-02/PACKAGE-01的原输入，没有重复供应商请求。完整模型配置与价格/秘密版本管理、企业及父运行的原子预留、累计研究/修订轮次、真实调用与回执核对、未知占用、结算和RN-06运行批准关联继续实施；BD-01—06及预算页面不表示这些要求已经完成。

MODEL-PRICING-02修改Python金额组件、模型测试及制品安装检查，Java和Vue源码未修改。新增索引、环境登记及实施计划由SETTLEMENT-INPUTS-01重新绑定；此前PROJECT-WRITE-FINAL-01的IC-02及两份模型回执保持原始输入范围。新检查没有生成当前全部25份回执集合的声明。后续Java预算须同时验证企业与父运行金额、Token、调用数及截止时间，未知调用保留预留，超过预留的实际费用完整登记并阻止新准入；收费发送、原回执去重、批准币种及价格版本、账单核对和真实业务采用全部保留。
