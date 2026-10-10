# IMP-01 正式工程与版本登记

2026-10-07文档组件登记：`document/pyproject.toml` 与 `document/src/medical_document` 提供 medical-document 1.0.0，Docling 与成熟格式库负责七格式原生内容和 Locator；正式安装制品及十一项检查由唯一[IMP-08](IMP-08.md)维护，工程路径与固定依赖已登记到 engineering-baseline。安装文件与当前源码逐字节核查一致；完整 Worker、上传持久化、服务组合和生产资格继续原验收，本任务保持 IN_PROGRESS。

2026-10-06原账户动作关联：FrameworkAccountOperations由原RuoYi Controller实现；原SecurityUtils使用实际配置的BCrypt组件，local工作因子12保持。完整构建入口同步保存新增实际账户动作结果；当前122项关联Java通过，唯一[IMP-04](IMP-04.md)维护当前输入/产物和真实本人操作。完整clean verify、S3全量写入、新制品和部署仍未通过；原运行部署保持，新源码尚未部署，较早源码整体证明为STALE。

2026-10-06原角色Controller关联：正式源码共注册十一项原管理Controller，SysRoleController全部十五项动作与原TokenService保持原实现；新独立框架密钥由本地启动入口以环境引用传入原token.secret。121项关联真实Java及两项实际配置检查由唯一[IMP-06](IMP-06.md)维护，当前源代码尚未部署，原完整clean verify与发布仍未通过。角色、原页面、十八模块和全部业务继续完整范围。

2026-10-06原管理Controller关联：RuoYiManagementConfiguration直接注册十个原类、原LogAspect及ThreadPoolConfig。正式MedicalApplication的原路由、当前框架方法权限和内容保持检查由唯一[IMP-06](IMP-06.md)维护。当前原完整clean verify仍未通过，新源码尚未部署；所选关联检查不代表完整构建、发布或业务验收。

## 完整构建的JVM资源与当前制品

2026-10-06；BUILD-JVM-RESOURCES-01沿用本主记录，状态来源为实施任务计划IMP-01，保持IN_PROGRESS。目标为完整原生构建、原始JUnit/HTTP证据及当前本地制品，完整业务验收保持原范围。已读取原build_business、check_migration、五个医疗模块Surefire配置、原失败S3输出、当前服务身份和实际机器资源；关联IMP-03/04/05/06/10/11/12/37。

实际机器内存8589934592字节，交换空间总量7168MiB、使用6362.25MiB；JDK17默认最大堆2147483648字节。当前Maven和全部五个医疗模块的Surefire使用-Xms128m/-Xmx512m，真实HTTP及原管理检查保存max_heap_bytes；这些事实不证明此前磁盘变化的全部原因。CURRENT-20261006-BUILD-RESOURCES记录构建前可用6118711296字节、1.2238324%，两个对象卷ReadOnly=false；该环境只对应其执行时间。原1%保护、77份历史制品、已有对象与备份保持完整。

Given完整当前源码、真实MySQL/Redis/S3及受限本机资源；When使用原Maven和原Surefire执行全部测试与verify；Then各构建及测试JVM采用明确堆上限，全部原检查继续执行，不跳过S3、不替换真实服务、不改变对象空间保护；生成完整原始JUnit、真实HTTP和制品证据。实际全部检查未通过时保留失败结果并禁止登记完整通过。当前构建参数属于工程运行配置，不代表批准的业务性能或容量阈值。

完整构建JVM-RESOURCES-NATIVE-01实际执行321项，组成42共享/1数据/36身份/242持久化；一项错误，失败和跳过为零，Maven2分34秒。实际S3ObjectWriter十四项通过，TransferConcurrency的一项对象恢复/组装写入返回500。原始输出为`.implementation-work/IC-03-JAVA-20261006-JVM-RESOURCES-NATIVE-01/output.log`；构建后CURRENT-20261006-FRAMEWORK-BINDINGS记录可用4895744000字节、0.9792209%，两个卷ReadOnly=true。没有完整成功回执、新业务Jar或发布，不使用所选关联回归代替完整构建。全部原测试继续保留，保护规则不变。

原管理结构034已在真实MySQL应用，当前236项迁移及完整备份由[IMP-03](IMP-03.md)维护；十三项原system服务注册和实际关联验证由[IMP-06](IMP-06.md)维护。原构建入口继续使用clean verify，并归档全部原JUnit及五项实际运行JSON，当前完整成功归档仍待全部检查通过。当前应用PID31485、部署339ea611-75dd-4718-a50d-b4a11e69a20c及原0400业务Jar保持；源码本地未提交。文档CURRENT、last_verified=2026-10-06仅指上述资源参数、实际构建结果及来源；项目整体尚未验收。

当前完整制品及运行身份由[IMP-04](IMP-04.md)维护。medical-identity直接声明并使用原ruoyi-framework、ruoyi-system及ruoyi-common，账户与密码服务使用原Mapper、RedisConfig、SpringUtils和国际化资源。当前219项迁移保持原UUID及全部企业授权；完整工程兼容、全部业务及正式部署继续按原要求验证。

2026-10-05 Servlet接收工程关联：medical-persistence直接声明原Spring Web、Reactor及Servlet API，测试使用实际Tomcat10.1.55；IncomingUploadPart.captureRequest与receiveRequest连接非阻塞输入、原总期限、明确缓冲和独立消费/截止Scheduler。十二项真实HTTP检查通过，完整制品与本地应用更新由唯一[IMP-05](IMP-05.md)维护；全部公开入口、业务验收与交接继续保持完整要求。

2026-10-05对象截止工程关联：ObjectRequestDeadline将原TimedAdmission剩余租期连接实际SDK Get/Head/Put，sdk-core及apache-client 2.55.11原JAR摘要进入检查输入。67项上传工程通过，实际服务暂停、SDK请求终止和恢复保护已经核查；完整制品及当前运行证据读取唯一[IMP-05](IMP-05.md)，全部业务和交接要求保持完整。

2026-10-05完成传输并发工程关联：DocumentUploadTransferService的分片恢复、完整重组、完整发送及完成恢复已必需取得原企业和用户的传输名额，Lease.guard在领域准入前后重新核查原租期与容量。63项上传工程通过，完整制品及本地运行证据由唯一[IMP-05](IMP-05.md)维护；全部公开入口、客户业务与交接继续保持原要求。

2026-10-05传输并发工程关联：TransferConcurrency和receiveStream连接实际Redis企业及用户双名额、固定配置、明确租期和零额外发送重试；Redisson 3.52.0实际SDK摘要进入当前检查回执。59项上传工程通过，当前完整制品/运行由唯一[IMP-05](IMP-05.md)维护；全部公开入口、客户业务及交接保持原要求。

2026-10-05上传输入工程关联：IncomingUploadPart及DocumentUploadTransferService.receiveStream连接完整原分片输入、当前上下文/权限、私有文件身份和异常清理；52项实际上传工程检查通过。完整新制品、运行和原始证据由唯一[IMP-05](IMP-05.md)维护；以下集合保留各自原始范围，全部公开入口、客户业务与交接继续实施。

2026-10-05当前许可事务工程关联：DocumentUploadIssuance、UploadOperationRow和UploadIssuanceMapper连接完整原许可/会话/绑定、幂等记录、审计和Outbox的同事务保存及重读；UPLOAD-ISSUANCE-GREEN-02实际44项检查通过。完整新制品和运行证据由唯一[IMP-05](IMP-05.md)维护，较早制品保留原始范围。公开上传入口、专业登记、全部客户业务和交接继续保持原要求。

2026-10-05当前上传依据工程关联：DocumentUploadBasisProtocol/Mapper/Authority、内部document_basis及当前写入准入已经连接，35项实际上传工程检查通过。完整构建、制品与运行证据由唯一[IMP-05](IMP-05.md)维护；公开许可创建、Artifact登记和全部客户业务仍按原工单实施。

2026-10-05上传传输工程关联：DocumentUploadTransferService、UploadObjectAssembly、原分片查询及完成后当前读取核查进入运行制品。UPLOAD-IO-NATIVE-01完整191项原生及UPLOAD-IO-RUNTIME-01六项实际运行回归通过，当前应用和不可变Jar身份读取唯一[IMP-05](IMP-05.md)。公开入口、产物登记和完整客户业务保持原验收要求；当前工程结果没有登记ACCEPTED。

2026-10-05上传授权工程关联：UploadAuthorizationProtocol/Schema/Authority、upload-authorization.schema.json及当前文稿完整查询进入源码，UploadTransferStore已连接原绑定校验。当前完整制品和实际运行证据由唯一[IMP-05](IMP-05.md)维护；公开入口、合法客户上传及全部业务验收继续实施。

2026-10-05上传保存关联：UploadTransferProtocol/Mapper/Store、UploadTransferObjects及TenantScope主体入口进入当前源码；九项实际上传查询、完整字段、数据库计划、原件及准入检查通过。完整原生制品、运行身份和工程证据继续由唯一[IMP-05](IMP-05.md)维护；正式业务验收保持原范围。

2026-10-05上传制品关联：当前026已经进入UPLOAD-STORAGE-NATIVE-02完整制品，168项原生检查通过，构建2分24秒；数据包原生解析198项，企业控制覆盖146张业务表。当前应用SHA-256为e272d55b10ab13accbbf15fb0b7d73e31c25204d0861c148b0ff781879c50021，运行核验与全部当前回执由[IMP-05](IMP-05.md)维护。原公开Schema与依赖保持完整，完整业务验收和正式发布仍未完成。

2026-10-05对象写入制品关联：S3ObjectWriter及九项实际S3并发、完整原件、固定版本与恢复检查进入medical-persistence，沿用既有AWS SDK、Commons IO、Commons Codec和Spring库。完整当前构建、运行制品和回执由[IMP-05](IMP-05.md)维护；没有新增供应商或部署模式，以下集合保留原始范围。

2026-10-05独立依赖关联：BACK-DEPENDENCY-ACCESS-AUTHORITY-01进入medical-persistence，原Artifact消费者复用DependencySourceAuthority。当前源码、独立四项实际检查、完整构建与运行制品证据读取[IMP-05](IMP-05.md)和环境登记。以下集合保留各自原始输入和制品范围，完整业务及交接继续未验收。

2026-10-05内容制品关联：IMP-05增加ArtifactContentService及完整准入/读取回执处理。CONTENT-READ-BUILD-01完整155项原生通过，构建2分32秒；实际JavaPID3730、制品807e50678e712792a65113e4f3272b437f47cd7519a2ca70f912c4e599b21e58、启动52.87秒、登录HTTP 200。六项原操作及两组登录DOM检查通过；当前源码、运行制品和回执由[IMP-05](IMP-05.md)及环境登记维护。完整客户业务与交接继续未验收，下方较早集合保持原始输入/制品范围。

2026-10-05共享制品核查：IMP-05新增36类派生版本的实际资源与当前权限核验。VERSION-AUTHORITY-BUILD-01完整150项原生通过，新JavaPID341、制品728e37d633a0446d9561ddefe7e1c76140f0b4e8faa4c95b35ac86a9c3accdf1、实际启动27.11秒、登录HTTP 200；六项原操作及两组登录DOM检查通过。当前回执和实际部署由[IMP-05](IMP-05.md)及环境登记维护。工程检查不能代替完整业务及交接验收，下方较早集合各自保持原始输入范围。

2026-10-05 BACK-SOURCE-READ-AUTHORITY-01当前关联：JGraphT 1.5.2进入medical-persistence，完整关系与当前来源权利由IMP-05维护。SOURCE-AUTHORITY-BUILD-02完整145项原生通过，构建2分35秒；当前JavaPID97158、制品498ba86f368cb8f809c3b45db43eb299c20dfdec39030dd931ec63e5b6b01f3d，实际启动28.166秒、登录HTTP 200。六项原操作及两组实际登录DOM检查通过；较早工程集合保留原始输入范围。完整业务、服务兼容、安全及部署交接继续未验收。

2026-10-05 BACK-RESOURCE-READ-AUTHORITY-01当前关联：RESOURCE-REGISTRY-BUILD-01完整138项原生检查通过，新JavaPID92494、制品e97700afaa765311df917297f01ee3e0c2e1654621fc1466612fc92886a16882，启动25.176秒、登录HTTP 200。资源当前授权与固定登记读取进入新制品；25组受影响工程回归采用RESOURCE-REGISTRY-FINAL-01，结果由唯一IMP-05维护。完整来源、业务、安全、部署及交接继续未验收；下方集合分别保留原始输入范围。

2026-10-05 BACK-ARTIFACT-SCHEMA-01当前关联：SchemaCatalogue.requirePublished/bindPublished已进入新Java制品96a21070ce705fbef32065100e32d751ed98119bacc14b714dcb9554b7da4a21，PID88142、部署UUIDff5e71e4-509f-47f0-8c56-7dc3212da663，实际启动25.969秒，登录HTTP 200。PUBLISHED-SCHEMA-BUILD-01完整131项原生检查通过；最终六项直接调用检查覆盖221个固定引用及原文件摘要、完整D0和原定位/摘要标量、正文和编码拒绝。当前25组工程回归及字节核查结果由唯一IMP-05维护；其他集合保留原始输入范围，全业务、Worker、安全、生产和交接继续未验收。

2026-10-05 BACK-ARTIFACT-REGISTRY-01当前关联：新Java制品2faddcfab5db549fadf98cba2abee2a695931f5d8f390ef47688092a306c48eb实际运行于PID85377、部署UUIDf804a6f4-d83d-4d67-94d9-3b1633663101，启动27.038秒。完整125项原生、25组工程回归和30份当前字节核查通过；新增三表及完整关联进入原医疗数据制品，六项迁移实际应用。唯一记录为IMP-05，全业务、Worker、安全、生产和交接继续未验收。下方集合保留原始范围。

2026-10-05 BACK-OBJECT-PERSISTENCE-01当前关联：StoredObjectReferences及S3ObjectReader.readStored已进入新Java制品e2a47a3d7f939543902f053805e6d84fab59158ef10ef22dfb6abfd4b1315af3，PID81465、部署UUIDccc4d22e-50c6-48e1-845b-9e02cb8467b0，启动26.932秒。PERSISTENCE-BUILD-01完整125项通过；PERSISTENCE-FINAL-01最终七项实际存储引用、24组回归及30份当前字节核查通过。Connector/J 9.7.0使用原固定制品并作为持久化测试的直接依赖，生产原依赖保持完整。全业务、Worker、安全、生产与交接仍未验收；唯一对象记录为IMP-05，下方集合保留各自原始范围。

2026-10-05 BACK-OBJECT-ASSEMBLY-01当前关联：ObjectContentAssembly及ObjectContentPaging.readComplete已进入新Java制品2527f8541ea100e61a494876c0158128972374a1f72c972da7efdf00faf67158，当前PID77952、部署UUID8fb67320-b21e-498f-a8ea-709231ad1fd1。ASSEMBLY-BUILD-01原生118项、ASSEMBLY-FINAL-01实际14项及完整24组回归通过；补充回执和当前字节核查结果由IMP-05维护。全业务、Worker、安全、生产和交接继续未验收；下方集合保持各自原始范围。

2026-10-05 BACK-OBJECT-PAGING-01当前关联：medical-persistence直接声明既有固定Nimbus 9.37.4，ObjectContentPaging及真实原件检查已进入新制品169b7016e499919e150e5b0cf5fb47653aa6319720e753856b2d52ea98eb17a8，当前PID72778。PAGING-BUILD-01原生110项、PAGING-FINAL-01实际分页六项及PAGING-FINAL-02完整24组/30份当前核查通过。全业务、Worker、安全、生产和交接继续未验收；唯一对象记录为IMP-05，以下集合保持各自原始范围。

2026-10-05 BACK-OBJECT-READ-01关联：AWS SDK for Java 2.55.11、Apache HttpClient、Commons IO 2.22.0及Resilience4j 2.4.0已进入真实Java制品；新制品21c6650a14db4c85689d443af50fd1a5cdc45a98645ceec0220fc4bdd2618774实际运行于PID68313。OBJECT-READ-GREEN-02原生104项和OBJECT-READ-FINAL-01全部24组/29份当前证据核查通过。完整Worker、业务、安全、生产及交接仍未验收；唯一主记录为IMP-05，下方较早集合保持原始输入范围。

2026-10-05 BACK-OBJECT-STORAGE-01关联：官方SeaweedFS 4.48制品、许可证、固定Boto3客户端和全部实际依赖字节由IMP-05维护。本地对象PID62380与JavaPID56990已经共同进入统一回归，OBJECT-STORAGE-FINAL-01全部24组及29份当前证据核查通过，原Java96项、模型28项和实际wheel安装后28项输入保持原字节。完整Worker、各外部组件、安全、生产与交接仍未验收；较早集合保持其原始输入范围。

2026-10-04 BACK-BUDGET-HISTORY-01当前关联：BUDGET-HISTORY-02原生96项通过，最终程序PID56990、部署UUID0105712b-4e55-4f45-b719-9acdaac5abf8、摘要fe2e5cf920a1c30c0787ca31b404e7571e8f34602059b89117e9365d08d8c4a9，启动24.751秒。BUDGET-HISTORY-FINAL-01实际23组及26份源码/产物/部署核查通过，Vue开发十组/生产七组；原模型28项及实际安装后28项当前字节已核查。实现及证据由IMP-07维护，下方集合保持各自原始运行范围；完整工程及业务未验收。

2026-10-04 FRONT-BUDGET-MANAGEMENT-01关联：预算前端复用当前固定Vue工程与依赖，主记录为IMP-07。当前BUDGET-FRONT-FINAL-01实际23组回归及26份源码/产物/部署身份核查通过，正式Vue开发十组/生产七组、本人账户各五组和原组件六项通过。Java95项、模型28项及安装后28项输入保持原字节并经当前核查；没有新增依赖、迁移或Java部署。其他集合保留其原始输入范围，完整工程、Worker、业务及生产仍未验收。

2026-10-04当前Java制品：IC-03-JAVA-20261004-BUDGET-02原生clean verify通过95项，所有模块SUCCESS，回执SHA-256为95820c0a00422c8361164c40d324ea141cf67e3481bec18a4a2a2eefcc6710cd。当前应用PID46382、程序摘要5889d881f77b794c140df65f07fa55229cd81dd6cb03e124c8a5c99ca4a92178，部署UUIDd9a21341-3348-4286-b69d-db5eb5473273。本地企业选择、岗位治理、PJ-01—06、BX-31/OP-01及BD-01—04已经进入程序；预算实现和证据读取IMP-07，正式Vue项目写入与恢复消费由IMP-06维护。受影响共享回归使用BUDGET-FINAL-01，结果读取实际回执。完整工程、Worker和其他服务继续未验收，其他执行身份保持各自原始输入范围。

2026-10-04模型工程：独立model-gateway包使用OpenAI Python SDK 3.24.0、httpx2/httpcore2 2.13.1及固定requirements.lock，复用当前medical-contracts。实际wheel包含五份当前Python源码，逐项核对归档字节并安装到独立目标，28项检查通过，包含pricing的安装位置核查和pip check通过；IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01回执摘要为9e5e2c19191f635280f9595805fe194c460921a89bc3350b234e9fb8098c9976。四类金额组件和数学检查由MODEL-PRICING-02维护；模型业务、真实费用与原子预算由IMP-07继续实施，完整Worker、Harness与SDK/runtime尚未验收。

BUDGET-FINAL-01当前23组受影响回归全部通过，inspect_current_evidence.py核查23组及当前Java95项、模型28项和实际安装后28项，共26份回执的输入、产物和部署身份相符；原始结果由该集合evidence.json及verified-evidence.json保存。当前Python源码与制品未变化，复用SETTLEMENT-GREEN-02/PACKAGE-01并核查实际字节，当前Vue源码未修改，其运行检查按新的Java输入重新执行。客户完整业务和生产资格仍未验收。

正式Vue的Cookie会话、共用request适配及S03/S17/S18由IMP-06维护。当前增加固定Ajv 8.20.0、ajv-formats 3.0.1、yaml 2.9.1、TypeScript 5.9.3及vue-tsc 3.1.8；原生安装没有运行依赖安装脚本或外部audit。此前依赖安全报告只适用于其原锁文件，当前依赖安全及受影响组件继续核查。

2026-10-03复验：Java原生clean verify、前端正式构建与6项组件、Python wheel和31项隔离检查已经通过，当前源码及制品摘要由[完整测试报告](../testing/full-functional-report.md)和18份已核对回执维护。本次npm audit的外部依赖信息披露等待用户授权。完整服务、Worker与SDK/runtime仍未验收。

## 任务身份

| 字段 | 当前内容 |
| --- | --- |
| task_id | IMP-01 |
| 需求与验收 | 全部需求；IC-01；实施任务计划 §5 IMP-01 |
| task_source | [实施任务计划](../../实施任务计划.md) §4.2 |
| 状态快照 | IN_PROGRESS；2026-10-02；本地仓库、Java/Vue 工程和 Python 共享制品构建已建立；完整服务兼容、Python Worker 和 SDK/runtime 待完成 |
| 代码与环境 | 项目根目录 Git 仓库；main；尚无提交；业务代码由执行回执的文件 SHA-256 与上游提交共同标识 |
| 文档有效性 | CURRENT；last_verified=2026-10-02；实际源码、许可、工具和原生构建 |
| 检索字段 | RuoYi-Vue3、Java 17、Python、Temporal、SDK/runtime、PLN-D01/05/10 |

## 检索与输入依据

读取技能及指定参考、索引、实施工单、HLD、前端详细设计与测试方案。当前用户要求在当前目录新建本地仓库；业务建设沿用既有完整实施授权。已读取官方完整 README、构建文件、MIT 许可和本地实际 Editor、request、Pinia 入口；取得的工程未包含 AGENTS.md。

当前源码及目录映射见 [engineering-baseline.md](../engineering-baseline.md)。Java 上游为 RuoYi-Vue springboot3，提交 `a51a838b71b446ea27256900efe7ed2faa2a02fd`；Vue 上游为 RuoYi-Vue3 master，提交 `838965c5a18d2c61b73ec30c6e288057aaa08b63`。两项代码版本为 3.9.2，许可 MIT。上游 bare 仓库及固定提交归档保存在项目中间目录。

本次依赖安全实施继续维护IMP-01。实际检索package.json、锁文件、SVG构建入口、SvgIcon、IconSelect及唯一ECharts调用页面，核查原始安全报告和官方资料。安全行为的Given为固定锁文件，When为官方服务核查，Then要求当前依赖没有已知安全问题；Red证据为IC-01-WEB-SEC-RED-20261002-02，摘要e6fd324ed2827529d5e5ac5d30dfef2eff9673bcbb7ea454ce970feccb949848，Green由当前官方完整报告核验。图标回归覆盖现有90份SVG及IconSelect的筛选、选择和重置；图表兼容验证使用实际运行Redis的测量值，不创建业务样本。正式业务页面和视觉验收继续按其对应工单验证。

## 当前功能与实现

Git 仓库直接位于当前项目，分支 main。business/ 保留官方六个 Java 模块和聚合 Maven 工程，并包含 IMP-03 的 medical-contracts 共享模块、medical-data 迁移模块及 IMP-04 的 medical-persistence 企业控制模块和 medical-identity 身份模块；web/ 保留 Vue3、Element Plus、Router、Pinia、Axios、Editor、上传、列表及管理页面。领域业务接口、客户资料、SSO、模型、签发及导出按对应工单实施。

| 文件路径 | identifier 或接口 | 当前职责 | 使用方 |
| --- | --- | --- | --- |
| business/pom.xml | com.ruoyi:ruoyi:3.9.2 | Spring Boot 3.5.16 / Java 17 原生模块构建 | Java 业务及后续领域模块 |
| web/package.json、package-lock.json | build:prod | 固定直接依赖及完整安装结果 | 正式前端与组件复用 |
| web/src/components/Editor/index.vue | QuillEditor | 既有 HTML 内容及 readOnly 入口 | IMP-16 核查结构化扩展 |
| web/src/utils/request.js、store/index.js | service、store | 唯一普通请求及 Pinia 实例 | IMP-04/05/06 |
| scripts/build_business.py | main | 使用项目内固定 JDK/Maven 执行 verify，缓存与临时目录归属项目 | IC-01-JAVA |
| contracts/python/pyproject.toml、requirements.lock、scripts/build_python.py | medical-contracts 1.0.0、main | 固定 setuptools、Pydantic 和测试依赖，构建 wheel，安装后核对源码及隔离执行31项检查 | Python 共享类型及错误代码；正式 Worker 待实施 |
| docs/engineering-baseline.md | IC-01 | 来源提交、许可、路径和实际组件 | 全部实施工单 |
| docs/commands.md | IC-01/02/03 | 可重复执行命令 | 验证及交接 |
| tests/browser/package.json、package-lock.json、scripts/check_local_browser.py | @playwright/cli 0.1.22、browser_identity | 独立本地DOM检查使用固定官方Playwright依赖及实际Chrome；原生工具与应用字节进入回执 | IMP-04真实浏览器检查 |
| web/vite/plugins/svg-icon.js、src/components/SvgIcon/index.vue、src/main.js | createSvgIcon、SvgIcon | 固定SVG组件生成资源，开发及生产URL引用保持全部现有图标身份 | 全部图标使用页面及IconSelect |
| web/src/utils/cache-chart-options.js、src/views/monitor/cache/index.vue | createCacheChartOptions | 现有pie/gauge参数及官方v5主题；页面与真实指标检查使用同一函数 | 缓存监控页面 |
| scripts/check_web.py、tests/browser/web-compatibility-checks.js、web/tests/engineering/ | run_checks、read_metrics | 真实Redis解析、两种资源路径、六项Chrome DOM及组件行为、全部构建文件摘要 | IC-01-WEB-COMPAT |
| tests/web-requirements.lock | redis-py 6.4.0 | 专用检查环境读取实际INFO，成熟库负责解析，秘密不进入产物 | 前端兼容检查 |

## 决策与约束

采用指定官方源码建设当前正式工程，保留原组件和模块结构。前端使用 npm；package-lock.json 纳入 Git；后续安装使用 npm ci。Axios 固定 1.20.0、js-cookie 固定 3.0.8、Vite 固定 6.4.3，版本已核对官方发布及 npm 元数据。Java 工具固定 Temurin 17.0.20.1+1 和 Maven 3.9.16，并核对官方摘要。工具均位于项目 .implementation-work/toolchains/，不变更系统 JDK。

已完整读取 [Axios 发布说明](https://github.com/axios/axios/releases/tag/v1.20.0)、[js-cookie 发布说明](https://github.com/js-cookie/js-cookie/releases/tag/v3.0.8)与 [Vite 发布说明](https://github.com/vitejs/vite/releases/tag/v6.4.3)。安全修复适用性通过当前官方完整锁文件报告核查，图表及图标的运行兼容单独验证。

WEB-DEPENDENCY-01：采用ECharts 6.1.0、@spiriit/vite-plugin-svg-spritemap 6.0.0和SVGO 4.1.0，保持Vite 6.4.3及现有Vue/Element Plus调用方式。官方固定元数据确认SVG组件支持Vite 6；图标身份保持icon-前缀和现有文件名称，构建生成独立资源，SvgIcon引用实际资源URL。生产及开发资源路径分别验证，完整90份图标进入回归，颜色及className继续由现有组件属性提供。ECharts采用官方v5主题保持已有默认设置，现有pie/gauge全部参数继续使用；实际数据和资源释放进入检查。已读取[ECharts发布说明](https://github.com/apache/echarts/releases/tag/6.1.0)、[升级指南](https://echarts.apache.org/handbook/en/basics/release-note/v6-upgrade-guide/)、[SVG组件选项](https://spiriitlabs.github.io/vite-plugin-svg-spritemap/options/)、[输出选项](https://spiriitlabs.github.io/vite-plugin-svg-spritemap/options/output.html)、[部署说明](https://spiriitlabs.github.io/vite-plugin-svg-spritemap/guide/deployment.html)及[SVGO发布说明](https://github.com/svg/svgo/releases/tag/v4.1.0)。源码、许可证、完整锁文件、原生构建和官方安全结果共同登记，尚未通过的检查保持当前状态。

SDK/runtime 与 Python Worker、独立 SSO、Temporal/MySQL 等服务的兼容记录分别继续验证。上游当前 request 的会话、日志、缓存及错误行为需由 IMP-04/05 适配已定义安全要求。所有生产资格以真实授权、实现和运行证据为依据。

业务 Mapper 使用 medicalSqlSessionFactory，原有 system/quartz/generator Mapper 使用 sqlSessionFactory；共享实际 DataSource 与事务管理器。依赖固定 MyBatis-Plus 3.5.17、JSQLParser 5.2、MyBatis 3.5.19、MyBatis-Spring 3.0.5、Spring 6.2.19，最终应用 jar 的各项版本已核查。生产会话配置及未授权 Mapper 拒绝已通过本地检查；PageHelper 完整运行、真实 SSO 及数据库执行由 IC-01/04 继续验证。

独立 OIDC 模块使用 Spring Security 6.5.11、Spring Session 3.5.7、Spring JDBC 6.2.19、commons-codec 1.18.0 和显式固定的 Nimbus JOSE JWT 9.37.4。业务安全配置覆盖固定医疗接口，服务端令牌使用 Redis 会话；平台目录从真实 OIDC 上下文登记用户及实际 HttpSession 绑定，每次 API 准入读取主数据库中的当前身份和应用会话；注销 Token 验证组件使用固定 JWK 及批准算法和时限。实际 SSO、TLS、会话、身份及会话 SQL、注销流程、浏览器行为及企业授权尚未验收。九项证明及配置检查、两项目录、三项注销输入及一项会话绑定拒绝检查见 IMP-04。

Python 共享库由 setuptools 80.9.0 构建，依赖包括 Pydantic 2.13.5 / pydantic-core 2.46.5、annotated-types 0.8.0 和 typing-inspection 0.4.4，完整固定版本在 requirements.lock 登记。测试依赖固定 markdown-it-py 4.0.0 / mdurl 0.1.2，用于读取实际后端错误设计。安装实际 wheel 后使用 Python `-I` 隔离模式确认十二份源码的集合和摘要，执行共享及领域31项检查；pip check 通过。当前 wheel 摘要为687ca4f1d0ec27f59f9a577416ff6089be952404ede0d3909b344554fe9912a0，包含五份 Schema 的216项类型绑定、完整68项错误枚举及 Schema 拒绝，公共17项声明复用。构建、pip 缓存和临时目录均位于项目 .implementation-work/；完整 Worker、SDK/runtime 与目标服务分别继续实施。

本地浏览器工具使用独立tests/browser/包，@playwright/cli固定0.1.22，其官方Playwright/playwright-core依赖固定1.64.0-alpha-1790635538000，许可Apache-2.0。安装结果由该目录package-lock.json维护；node_modules被忽略。已经读取官方CLI完整README及项目Playwright技能；CLI取得匿名登录页面的实际DOM，原生Node调用官方Playwright SDK执行当前真实账户表单。Chrome 154.0.8037.93使用独立headless会话，原生发行文件、框架、Node及工具代码绑定检查回执。五项身份DOM流程及数据库重读通过，范围及证据由IMP-04维护；正式前端组件的构建、图标及Redis图表兼容由本记录维护。

## 验证与证据

| check_id | 实际命令及预期断言 | 真实结果 | 证据 |
| --- | --- | --- | --- |
| IC-01 / 仓库 | git rev-parse --show-toplevel；git branch --show-current | 当前项目根目录；main | 本次实际命令输出 |
| IC-01 / 工具 | Python scripts/build_business.py --versions | Maven 3.9.16；Temurin Java 17.0.20.1；macOS 13.7.8 x86_64 | 固定工具及工程登记 |
| IC-01-WEB-INSTALL | 固定package-lock.json执行npm ci | PASS；输入未变更；ECharts、Vite、SVG组件及SVGO原生安装版本绑定 | [当前回执](../../.implementation-work/IC-01-WEB-INSTALL-20261002-01/receipt.json)；SHA-256 9898f253c807346337eaf2806cf701f58c591603e669cf535418ba74e5dc0f5c |
| IC-01-PYTHON-PACKAGE | Python scripts/check_python.py --check package --execution 20261002-02；实际 wheel、安装源码及隔离执行 | PASS；依赖一致；十二份源码摘要相等；31项检查通过；范围 LOCAL_PACKAGE_CHECK | [当前回执](../../.implementation-work/IC-01-PYTHON-PACKAGE-20261002-02/receipt.json)；SHA-256 08737f74363e944d6b4422421dd4c10039dab2e36ba10dcba009c34d1f5a6058 |
| IC-01-JAVA / IC-03-JAVA | Python scripts/build_business.py；Maven原生clean verify | PASS；聚合工程及十个子模块SUCCESS；共享26项、迁移1项、企业12项、身份26项，共65项；176项迁移、ID-07/08服务、角色版本、认证加密及ID-09进入制品；178项输入及8项输出，领域业务另行验收 | [当前回执](../../.implementation-work/IC-03-JAVA-20261002-37/receipt.json)；SHA-256 06567e0bd0f25b055692d9dea4b59aabb2c1ee00415cc9e416f2c68b0a478b67 |
| IC-01-WEB / IC-01-WEB-COMPAT | check_web.py；npm run build:prod及真实Chrome开发/生产路径 | PASS；6/6；90份实际SVG、IconSelect、当前Redis统计、图表尺寸调整和释放；385项输入及460项产物；时间读取原始执行输出 | [当前回执](../../.implementation-work/IC-01-WEB-COMPAT-20261002-10/receipt.json)及command/output.log；SHA-256 e87e837700a65d361ebad423a32b30a5b08687e9707d04fc99f6c7854ea595db |
| IC-01-WEB-SEC | npm audit --json；当前 package.json 与完整 package-lock.json | PASS；359项依赖，零项已知安全问题；七项源码及锁文件输入绑定；范围为npm官方依赖检查 | [当前回执](../../.implementation-work/IC-01-WEB-SEC-20261002-03/receipt.json)及原始output.log；SHA-256 86a26aeb313043ec76c46205d3f8ab948b57ac03c6cc197702a9147ad7c441a8 |

依赖安全回执只适用于对应日期的原始锁文件；当前锁文件的官方服务检查仍待完成。固定版本的许可及原生代码已经核查；正式产品业务、完整兼容矩阵、其他组件安全检查和发布资格仍按相应工单验证。

六项兼容检查分别覆盖development和production的图标资源、IconSelect操作及Redis图表。全部90个symbol身份、viewBox和实际图形元素有效；SvgIcon的颜色、className和aria-hidden保留，生产URL指向实际带摘要的构建资源。筛选读取真实图标名称，选择回调、active状态、重置及清空均通过。redis-py 6.4.0原生读取当前7.4.8服务INFO及commandstats，原始统计、run_id及采集时间保存在redis-metrics.json；同一实际数据进入页面使用的createCacheChartOptions，ECharts读取结果逐项比较并完成resize/dispose。没有模拟接口、业务样本、截图、视频或视觉判定。检查服务器和独立Chrome会话在结束时正常关闭，真实本地登录服务保持运行。

原始command/receipt.json保持独立；最终receipt.json绑定原始回执、全部源码/工具/运行身份及460项实际产物，所有文件重新计算摘要。正式制品保存在同一执行目录的production/，检查页面的制品位于compatibility/，不将检查页面加入正式产品入口。固定锁文件已经通过npm ci重现安装，当前构建及兼容检查使用重现安装后的依赖。

源码取得、构建及版本登记没有产品业务行为变化；不制造业务 Red。完整构建不替代登录、授权、持久化、模型、审查和成品验收。视觉检查未执行。

## 关联影响与后续使用

物理目录已经确定，后续工单在登记范围实现。IMP-03 可以使用实际 Java/Vue 工程开展共享结构和迁移准备；SDK/runtime 与目标服务兼容未验证的动作继续保持待执行。所有修改通过文件编辑工具实施，保留用户原有设计及资产。实际角色、模型预算、生产区域及阈值仍引用 IMP-02。

## 未完成内容与继续条件

登记 Python 正式 Worker 入口及锁定依赖、SDK/runtime/profile 实际制品；验证 MySQL、Temporal、Qdrant、Redis、独立 SSO 和存储组合；明确维护责任并处理依赖安全检查结果。IC-01 未全部通过，任务不登记 ACCEPTED。已有工程路径和构建为相应后续实现提供实际输入，继续处理就绪动作。
