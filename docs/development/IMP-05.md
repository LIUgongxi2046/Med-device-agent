# IMP-05 幂等、对象读取、事件与审计基础

2026-10-09当前VersionResourceAuthority使用一次完整36类领域查询核查全部传递版本及实际实体归属；完整来源、会话、依赖和资源许可判断保留。实际MySQL十九项上传授权、版本归属、依赖授权及制品读取检查通过；回执为`.implementation-work/IC-05-UPLOAD-AUTHORIZATION-20261009-DOCUMENT-BATCH-VERSION-02/receipt.json`，SHA256为ec06f078a6b17f50c856b24c222018debb01715c33bc154bef5ef74179805f8a，exit_code=0、inputs_unchanged=true。正文公开保存的实际服务证据读取唯一IMP-16；全部开发输入采用SIMULATION，正式客户验收独立登记。

## BACK-UPLOAD-PERMIT-DEADLINE-01

2026-10-08继续当前上传期限及许可身份检查，状态来源为实施任务计划，保持IN_PROGRESS。检索本记录、索引、TransferConcurrency、TransferConcurrencyTest、ServletUploadInputTest、接口补充设计§2.1和实际Redisson3.52.0制品。Given原企业、用户及同分段许可与实际Redis；When许可到期或原身份已释放且其他请求取得容量；Then原请求立即返回LEASE_STALE，不能借用其他请求的许可，不延长原截止，不增加容量。原任务详情完整构建中的上传断言和原JUnit文本/XML独立保存于IC-03-JAVA-20261008-TASK-DETAIL-BUILD-FINAL-01；完整构建未通过，新制品未部署。

当前许可核查使用成熟Redisson的RScoredSortedSet/ByteArrayCodec读取固定3.52.0原期限集合；原二进制permit_id由JDK HexFormat处理，键名使用RedissonObject.suffixName及NameMapper并核对实际rawName。取得、到期清理和释放继续由原RPermitExpirableSemaphore负责，不更新租期或容量；原单调时钟截止、当前配置和权限继续核查，Redis错误直接传播。带副本的Redis模式必须配置ReadMode.MASTER，当前单实例模式继续直接读取当前服务。依赖升级需要复验实际存储格式与原身份读取。企业、用户及同分段原许可被释放以后，其他请求持有相同容量仍须拒绝旧身份。

有效Red为UPLOAD-PERMIT-DEADLINE-RED-01原53781，17项检查中两项企业/用户原身份断言失败、错误/跳过零项，inputs_unchanged=true，回执a42288dd83497418ecc1f8c27fbf31908dbe17bfd97fb3be622aec23483ebbe5；原JUnit独立保存于同目录TransferConcurrencyTest.xml，摘要90f7b3e192006f5fa9cf6a6cec873fce22af1d15465557b542722fbf5f0d3119。当前实际服务Green已经沿正常审批执行，UPLOAD-PERMIT-DEADLINE-GREEN-01原76636已取得exit0终态。73项检查全部通过：IncomingUploadPartTest八项、ServletUploadInputTest四十七项、TransferConcurrencyTest十八项；原32次停止发送期限检查完整保留，失败、错误和跳过零项。回执为`.implementation-work/IC-05-UPLOAD-IO-20261008-UPLOAD-PERMIT-DEADLINE-GREEN-01/receipt.json`，摘要c84ec566edc1d3e9942ae38bc483d712eeb3adba22ad5c0fd5e98adf432e7e4e、inputs_unchanged=true；三份原JUnit均独立保存于同目录。

离线UPLOAD-PERMIT-DEADLINE-COMPILE-01原4255已取得exit0终态，52.528秒，回执6bd9fb3baf867e80b5648b0daa6a9316d4b8c592f94e9e7b21a61876a76bfaf3、inputs_unchanged=true。实际Maven使用--offline和test-compile，当前154份持久化源码及41份测试源码编译通过。TransferConcurrency.class与TransferConcurrencyTest.class独立保存于原执行目录，摘要分别为0788b45226aaf37579f846fb2367a423c645e5d9cb6dca82259a77abb648c1fc与fb301d14046aee2081081481559c5b38967a5d08799e3fd45d3cd4c01c5f2b27，并与原回执逐项核对。该结果只证明编译，实际许可、Servlet期限、取消及完整业务行为待原服务验证。

原SOURCE与DOCUMENT_BODY消费者共同进入受影响检查，关联IMP-08/06；完整业务、导出、恢复与客户验收仍保持原要求。当前真实服务验证使用check_upload_io的TransferConcurrencyTest、ServletUploadInputTest和IncomingUploadPartTest，原期限、容量、撤权、配置变更和清理断言保持并已通过。独立Green证明为`.implementation-work/UPLOAD-PERMIT-DEADLINE-20261008-green-evidence.json`，摘要460e063296f700ef45e70d8bf75d868c81512f1ec716a243e00a174728e3ba73，原输入和独立JUnit全部字节一致，原32次重复数目经过XML核查。随后更新的后端LLD由下一完整构建采用；原Green保留自身执行输入范围。当前继续完整构建、实际部署与原操作复验，完整工单保持IN_PROGRESS。

独立文件复核为UPLOAD-PERMIT-DEADLINE-20261008-progress-evidence.json，摘要c1248eee293ee70bd62651794f43fa84aa7d75599c9c12cb15c506e1102c192d；原Red回执、原JUnit断言、两份原class及三份命令日志摘要分别一致。计划关系公共接入后，原编译输入中的TenantScope、master.xml和coverage.json三份文件已经变化，新增八份源码/迁移；原编译仅证明对应原输入，当前完整工程由IMP-12接入检查和后续完整构建继续验证。五项来源及开发记录检查原66326通过，回执8bd337ebb732e27488890232d34039516af7970adeb0a5380c011641029a81f1、inputs_unchanged=true；该回执只证明其原记录范围。实际服务行为、当前新部署及完整目标在独立证明中均为false。

2026-10-08当前SourceUploadTransport在准入线程登记原操作及同分段名额，UploadPartReceiver.receiveRequest使用Mono.using取得并释放原企业/用户Lease；接收线程消费实际文件和对象，WRITING继续核查原write_id。SOURCE-PART-USER-CAPACITY-BUILD-FINAL-01完整354项及实际HTTP、前端构建通过，回执ff649d3cf3a4e6328d317281d04204648dbd447d43176ccb640c99eed842e1a0，inputs_unchanged=true。当前PID15882部署和六项原操作由唯一IMP-06维护，原对象服务、秘密、数据与备份保持。唯一IMP-08维护企业与用户容量拒绝和原请求恢复；下方回执仅证明各自原输入范围。公开期限、多人企业容量、排队取消、全部WRITING条件、DOCUMENT_BODY共享消费、完整导出和恢复继续验收，IMP-05保持IN_PROGRESS。

2026-10-08当前UploadPartReceiver.receive/recoverHeld在WRITING原请求完整输入验证后使用原write_id/object_key核查原S3全部字节、版本、元数据与AES256，沿用当前名额和期限；Source回调共同保存原响应、进度、审计及Outbox。SOURCE-PART-WRITING-BUILD-FINAL-01完整352项与39项共享接收及实际HTTP通过，回执9e8a25ce0cef7cd817262b221e9c97868fc33accc45a76ce6e02b6483081c4e1，inputs_unchanged=true。当前原本地PID7855部署和六项原操作由唯一IMP-06维护，实际对象卷ReadOnly=false。原服务/数据/秘密及完整备份保持；下方351项及以前完整组合STALE，仅保持原输入范围。唯一IMP-08维护当前原对象恢复范围，全部名额/期限、WRITING条件、DOCUMENT_BODY共享消费、完整导出及恢复继续验收，IMP-05保持IN_PROGRESS。

2026-10-08当前Source分段通过Redis单项限时名额控制同企业/上传/序号；名额占用时SourceUploadService.partReplay重新核查授权及原请求，PROCESSING返回202，COMPLETED返回200及固定原响应。共享输入取消信号终止实际等待并执行私有文件及原企业/用户名额清理，原operation_record保持请求身份。当前SOURCE-PART-PROCESSING-BUILD-FINAL-04完整351项Java、39项共享检查及公开HTTP通过，回执22601167e092857bceab9a97f71bc3586c6429d07ce336afd9c4b49c57dd7888，inputs_unchanged=true；新制品已在原本地PID1490运行，部署和六项原操作由唯一IMP-06维护。原03及下方构建只保持各自原输入，当前整体为STALE。WRITING未知结果恢复、全部名额/期限条件及DOCUMENT_BODY共享消费继续验收，唯一主记录IMP-08，IMP-05保持IN_PROGRESS。

2026-10-08关联KN-02原请求事务，唯一IMP-08维护SourceUploadService.startPart/partAuthority/completePart。原operation_record保存分段PROCESSING请求身份，完成响应、实际进度、source.upload.part.stored同步审计与Outbox共同提交；重复原键重新核查当前权限并返回固定原响应。当前SOURCE-PART-OPERATION-BUILD-FINAL-01完整350项通过，PID87720部署、222份静态HTTP字节、五项入口及六项原操作检查由唯一IMP-06维护。完整公开恢复与并发仍须验收；以下原分段构建、部署与整体组合保留原输入范围，IMP-05保持IN_PROGRESS。

2026-10-08关联BACK-SOURCE-UPLOAD-01，唯一IMP-08维护公开SOURCE分段实际写入、固定对象版本重读、完整原文摘要、AES256、重复回执、实际进度和权限拒绝。当前使用原IncomingUploadPart、TransferConcurrency、UploadTransferStore和S3ObjectWriter；SOURCE进度与分段保存共享企业事务。当前SOURCE-PART-BUILD-FINAL-01完整350项全部通过，新制品已在PID83402运行；部署222份静态HTTP字节、五项页面入口及六项原操作检查由唯一IMP-06维护。KN-02完整请求身份、分段原操作/审计/Outbox和WRITING公开恢复继续实施，原SOURCE声明不产生专业核准。以下原创建构建、部署与组合证明仅保留原范围；完整导出和恢复仍未验收。

## 当前来源上传的原操作与部署关联

2026-10-08，BACK-SOURCE-UPLOAD-01由唯一[IMP-08](IMP-08.md)维护。公开创建在同一企业事务保存原声明、SOURCE上传、原操作、同步审计和Outbox，保存后重读完整请求与响应；重复提交继续核查当前许可、水位及授权期限，返回相同原响应字节，异参拒绝且不增加业务记录。实际HTTP使用本地账户、MySQL、Redis及获准SIMULATION企业和项目，专业批准没有生成。当前350项完整构建包含现有对象写入、传输并发、原操作与新的SourceUploadHttpTest；失败、错误和跳过均为零。当前源码与不可变Jar部署、222份HTTP静态字节、五项页面入口及六项常驻原操作检查由[IMP-06](IMP-06.md)维护。

对象服务使用原数据、秘密与service_id，两卷ReadOnly=false；实际部署检查取得可用26039480320字节、5.2082796%，原1%保护保持。公开KN-02实际SOURCE分段消费、KN-03处理、固定导出、完整备份恢复及IC-05全部要求继续实施，IMP-05保持IN_PROGRESS。原339项构建、部署和八份组合证明按原执行范围读取，整体输入为STALE。

## 本地对象服务恢复的原执行

2026-10-07继续BACK-OBJECT-STORAGE-01，唯一状态来源为实施任务计划，IMP-05保持IN_PROGRESS。检索索引、原对象部署、历史身份、local_object_storage.py、check_object_storage.py及NativeStorageRecoveryChecks。当前SeaweedFS4.48对象PID58150、部署5d3a6958-e967-4329-9e2a-5c041487718d，原service_id为11dab8e2-1a0d-4548-914b-79f96c297e8a，数据、加密秘密与备份继续使用原位置和身份。实际部署检查取得可用27371999232字节、5.474803%，两个卷ReadOnly=false，原1%空间保护保持。

Given原本地服务的数据、技术身份、加密秘密、固定SeaweedFS4.48制品和原件版本保留；When使用原start入口恢复服务；Then新部署继续使用原service_id、bucket和全部原数据，原两份D0固定版本的完整字节、SHA-256、metadata与AES256一致，版本保护与匿名拒绝继续有效。恢复检查采用原objects.json执行读取；当前版本Enabled，私有ACL、匿名403、完整与范围读取均通过实际S3检查。技术秘密不进入输出，全部十项TCP监听均为127.0.0.1。

原件恢复回执`.implementation-work/IC-05-OBJECT-STORAGE-20261007-OBJECT-SERVICE-RESTORE-01/receipt.json`，SHA-256为2b1617aa7787e8140c0cd4b9df2721dc64c64fee5d21878c0a7e94980beffb11；四项实际S3检查回执`IC-05-OBJECT-STORAGE-20261007-OBJECT-SERVICE-WRITE-01/receipt.json`，SHA-256为9e511931c25bd6071456aa5e62ea125da263a70d1c5802540f924c9e04e2c066；监听回执`IC-05-OBJECT-LISTENERS-20261007-RESTORE-01/receipt.json`，SHA-256为fe1fe5a2f55fc4f2128ae77ee25609038adc53eb7d936baef5af6a093e83a7cf。回执均exit_code=0、inputs_unchanged=true，公开D0保持技术检查用途和原权利条件。

当前完整339项Java构建及常驻应用部署由[IMP-06](IMP-06.md)维护。八份关联回执的全部当前输入和产物字节证明为`.implementation-work/DOCX-ORDER-20261007-associated-evidence.json`，SHA-256为7f5b08d42d5a539a9cb813f45417de89b24117e916bd4d31a239713b82f2c1c0，其中十二项当前解析制品检查由[IMP-08](IMP-08.md)维护；六项常驻原操作检查通过。本文本节CURRENT、last_verified=2026-10-07，适用于本地对象恢复、原件重读、当前实际S3与监听检查；customer_success_verified=false。客户上传、固定导出、完整备份恢复及IC-05全部要求继续保持。以下集合仅证明各自日期和原执行输入范围。

2026-10-06当前原管理方法权限及119项关联Java/当前DOM由唯一[IMP-06](IMP-06.md)维护，没有客户写入或模型请求。完整Java构建RUOYI-METHOD-SECURITY-NATIVE-01未通过，实际S3ObjectWriter及TransferConcurrency写入返回500，原对象日志显示卷只读；完整成功回执和新部署没有产生。最新实际环境为RUOYI-METHOD-SECURITY-20261006-CURRENT：JavaPID31485、对象PID62380、226项迁移及业务零计数保持，完整登录200；可用3871498240字节、0.77435666%，两个卷ReadOnly=true，原1%保护保持。77份历史制品8250328906字节和既有备份完整，原归档/清理问题尚待明确批准；完整对象写入、导出和恢复继续原验收要求。

2026-10-06当前原RuoYi菜单、角色权限及原Vue会话消费由唯一[IMP-06](IMP-06.md)维护；118项所选Java检查及开发七项/生产六项DOM通过，没有客户写入。新源码尚未部署，完整客户操作、全部S3写入、Jar构建及固定导出仍未验收。最新RUOYI-NAVIGATION-20261006-environment.json记录原JavaPID31485、对象PID62380、226项迁移和业务计数零，可用4018061312字节、0.8036715%，两个原卷ReadOnly=true。77份历史制品8250328906字节保持完整，归档/清理等待原明确批准；不得通过改变原空间保护规则获得写入通过。DeepSeek保持原受保护配置，没有模型请求。下方集合只保持各自原始范围。

2026-10-06当前资料集创建、版本修订及专业清单由唯一[IMP-10](IMP-10.md)维护，原企业事务、许可、版本、操作/审计/Outbox共同使用既有组件；原PageHelper账户分页由[IMP-04](IMP-04.md)维护。COLLECTION-NATIVE-04原范围308项通过，实际对象写入十四项16.745秒和传输十五项6.210秒，整体证据为STALE。COLLECTION-NATIVE-05是当前整体失败检查，对象写入因磁盘容量保护返回HTTP500，新代码尚未部署。原对象服务PID62380、D0及备份保持身份；原默认1%保留空间规则生效，当前0.83420855%，两个原卷只读，对象写入不可用。77份历史应用制品的归档与清理等待明确批准，尚未执行；真实容量和业务零计数证据为`.implementation-work/COLLECTION-20261006-environment.json`。全部客户业务、固定导出及完整恢复保持原验收范围。

## 原部署与对象检查的原始范围

2026-10-06当前来源选择、固定成员及完整部署证据读取唯一[IMP-12](IMP-12.md)。ScopeSelectionAuthority复用原来源/版本授权与完整关系核查，原MyBatis-Plus保护当前主体、企业及锁定查询。SCOPE-SELECTION-NATIVE-01完整300项通过，十四项实际对象写入22.08秒、十五项传输6.282秒、任务HTTP五项、账户三十一项及原框架五项通过；635项输入及8项产物与实际应用一致。对象服务PID62380和D0保持身份，客户快照/任务保存、文稿成功、证据和固定成品导出尚未验收。其他集合按原范围读取，原整体集合对当前Java为STALE。

## 已有实现与各执行集合的原始范围

本节中的执行身份、制品、数量和验证结果分别对应其注明的集合；当前任务组件和部署证据使用IMP-12。

2026-10-06BACK-RUOYI-DATA-SCOPE-01由唯一[IMP-04](IMP-04.md)维护。原DataScopeAspect、Spring AOP及当前275项制品通过真实HTTP/Redis/MySQL与完整30组回归，31份当前输入、产物及双服务身份证明为`.implementation-work/FULL-REGRESSION-20261006-RUOYI-DATA-SCOPE-FINAL-01/verified-evidence.json`。当前JavaPID10793；原操作、审计、对象服务、D0及222项迁移保持原要求和实际身份。完整客户事务、资料、证据、签发和固定导出继续实施与验收；以下集合只保留各自原始范围。

2026-10-06FRONT-ENTERPRISE-REVIEW-01正式审查、原请求键恢复与完整回执核查由唯一[IMP-06](IMP-06.md)维护，服务器保存、事务、审计及原操作保持既有实现。ENTERPRISE-REVIEW-FINAL-01完整30组及31份当前源码、产物和双服务运行身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261006-ENTERPRISE-REVIEW-FINAL-01/verified-evidence.json`。原对象服务、D0、275项Java制品和222项迁移保持原身份；真实客户决定、恢复、正文、来源、签发和固定导出继续按完整要求实施与验收，较早集合按原始范围读取。

2026-10-06原框架身份接入及当前制品由唯一[IMP-04](IMP-04.md)维护；原LoginUser、SecurityUtils及ss组件直接使用原框架实现，业务UUID、凭据版本及企业授权保持完整。当前275项Java、30组回归及31份当前源码/产物/运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261006-RUOYI-NATIVE-IDENTITY-FINAL-01/verified-evidence.json`。原操作、审计、对象和上传服务通过受影响工程检查，客户成功及完整公开消费者继续未验收；较早集合保持各自原始范围。

2026-10-06正式企业登记原请求处理由[IMP-06](IMP-06.md)维护，直接复用原LOCAL-REG服务、当前平台身份、完整Schema和固定回执；平台原请求不使用企业OP-01，也不产生企业正文许可。当前ENTERPRISE-REGISTRATION-FINAL-01完整30组及273项Java回执共31份当前字节和运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261005-ENTERPRISE-REGISTRATION-FINAL-01/verified-evidence.json`。原操作、对象、事件、授权、上传和审计服务保持原版本，客户成功、恢复及全部业务继续验收，较早集合保持各自原始范围。

当前完整源码、制品及本地运行身份由[IMP-04](IMP-04.md)维护，账户写入及密码限制使用原RuoYi服务；150张企业表继续由ProtectedTenantInterceptor保护。模板创建和人工审查页面复用原lookupOperation/readOperation，当前30组回归和31份字节及运行身份核查由[IMP-11](IMP-11.md)维护；完整客户成功与恢复尚未验收。原操作、事件、审计、对象服务、完整上传授权及私有原件要求保持。公开上传、专业文稿及全部客户业务继续实施，工单保持IN_PROGRESS；其余证据按对应原始源码、制品与运行范围读取。

2026-10-05 BACK-UPLOAD-WAIT-ADMISSION-01当前工程已验证：检查采用成熟Reactor Mono.fromRunnable、subscribeOn、repeatWhen及delayElements，上次完整准入成功后经过服务器明确间隔才执行下一次，原expiry持续独立计时。接收、期限及检查Scheduler分别管理工作线程，数据库查询使用各自短事务；Lease.closed使用volatile，每次外部查询以后继续检查原剩余量。实际停止发送64字节后的技术撤权、Redis原配置版本和容量变化分别终止接收并清理原私有文件及双名额，没有等待下一段内容。独立GREEN-02十五项通过，回执bcd36b7b309beb239709509b1a71e2a4541abb02ba132dfc13e0b4bab763e444；有效RED-01记录实际停止发送三项等待断言失败，回执fd8196548cc4027b67b1bf8d963e8ae386fbe7f1eb38dba445ec991992dd4442。两份原件、总期限、连续缓慢发送和实际客户端取消均保留验证，测试只使用实际服务、公开D0及技术权限，没有客户业务记录。

UPLOAD-WAIT-ADMISSION-NATIVE-01完整243项通过，contracts38/data1/identity26/persistence178，失败/错误/跳过为零，构建2分34秒；Servlet十五项11.85秒、并发十五项6.418秒。回执4470734f5596aa8111931684b525528f826a058408eb8a7c43f83b686713ce67；当前应用PID51939、UID501、部署UUIDf39f6cd6-5db6-49c3-a25e-48f7a955c468，不可变Jar摘要495da16e629ee0092bef5ebd98536e17f9a1958792b62416a094d246b685bddf，启动14.954秒，独立登录完整HTML为HTTP200。RUNTIME-01六项实际账户/操作/权限通过，回执5ff09a0424eaf6c44c1e93249f649016031bcd78b0265cb303bd1a719b23bc97；343/275项输入及8/1项产物SHA逐项相等。原始证据目录分别为.implementation-work/IC-03-JAVA-20261005-UPLOAD-WAIT-ADMISSION-NATIVE-01及.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-WAIT-ADMISSION-RUNTIME-01。当前工单保持IN_PROGRESS，正式检查间隔、公开入口、专业文稿、全部客户业务和交接仍需完成；其后源码变化时，这些回执仅表示各自原始执行范围。

BACK-UPLOAD-WAIT-ADMISSION-01继续本工单，状态来源为实施任务计划，保持IN_PROGRESS。当前检索IncomingUploadPart.captureRequest、DocumentUploadTransferService.receiveRequest、TransferConcurrency.Lease、ServletUploadInputTest、索引和接口说明，并核查PRD§8.3资料撤权要求。Given实际客户端发送原件首段后停止、原十秒租期仍有效；When原技术许可撤销或真实Redis配置变化；Then等待必须终止，失败不得生成成功回执，本次私有文件及原双名额完成清理。服务器显式提供准入检查间隔与独立检查Scheduler，原总截止保持独立；生产批准间隔和公开入口仍由完整配置及业务任务实施。Red/Green和当前制品证据在实际执行后登记；新增Awaitility采用当前Spring Boot BOM管理的4.2.2，仅用于观察真实网络消费线程和原文件状态，没有模拟源或客户数据。

2026-10-05当前有效工程采用UPLOAD-SERVLET-INPUT-NATIVE-01/RUNTIME-01，BACK-UPLOAD-SERVLET-INPUT-01已经连接内部Servlet接收及原总期限并更新本地应用。完整240项原生通过，contracts38/data1/identity26/persistence175，失败/错误/跳过为零，构建1分55秒；Servlet十二项6.561秒、并发十五项6.208秒、对象十四项16.720秒、分片十项2.812秒、输入八项1.121秒。当前JavaPID47704、UID501、部署UUIDe4d7589d-7033-4276-a263-f52cb3152a4b，0400不可变Jar摘要e9ea16a75c18366da5dd3d8c1f66ac964415008f05f622c8c793e3711b73a3bf，实际进程/制品核查通过；启动13.561秒，完整独立登录HTTP200，六项实际账户/原操作/权限3.873秒通过。341/275项输入和8/1项产物SHA逐项相等，文档有效性CURRENT、last_verified=2026-10-05，适用范围为本轮接收、完整工程制品及本地运行。唯一工单状态继续IN_PROGRESS；客户业务PASS和工单ACCEPTED保持零，全部42工单与57业务/17非功能及全部条件场景保持原范围。

| 当前证据 | 原始回执及SHA-256 |
| --- | --- |
| 240项完整原生与全部当前制品 | [NATIVE-01](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-SERVLET-INPUT-NATIVE-01/receipt.json)；a877a8fe225502f45accd4d9b0615791c44ff2ed7d99258c095ae6f61934cefc |
| 六项实际HTTP与数据库 | [RUNTIME-01](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-SERVLET-INPUT-RUNTIME-01/receipt.json)；e401583e21bf2e5bcb9defbc8ad2f66269ccf6a1f2d398ab543a9437a40c2aa9 |

上一目标turn仅回答完成状态，归类NO_PROGRESS；本轮检索原IncomingUploadPart、DocumentUploadTransferService、TransferConcurrency、相关记录、完整PRD§8.3、接口说明和实际服务身份。Given原双名额、原传输截止及实际HTTP原件；When客户端停止发送、持续缓慢发送、取消或当前准入拒绝；Then接收停止等待、取消输入订阅、清理本次私有文件及原名额。完整内容按原大小与摘要逐字节核查，失败接收不进入对象发送或成功回执。采用Spring ServletHttpHandlerAdapter、DataBufferUtils.subscriberInputStream和Reactor单次总截止，服务器明确提供segmentBytes、demand、receiver和deadlines Scheduler，不重置原期限；相同Scheduler和NonBlocking消费拒绝。真实Tomcat/JDK HTTP、Redis、当前平台身份及两份D0公开原件用于工程验证，没有客户业务批准或数据，技术权限按请求独立管理。原PID43011已经终止，对象服务PID62380保持运行；MySQL198项迁移/146企业表/17平台表、Redis、原D0和备份保持身份。本地DeepSeek检查configured=true，没有新增模型发送。

| 当前文件及identifier | 使用方及完整边界 |
| --- | --- |
| IncomingUploadPart.captureRequest | ServerHttpRequest非阻塞Publisher桥接有界InputStream；Content-Length必须等于原片段大小，demand和segmentBytes显式为正，segmentBytes*(demand+2)不超过8 MiB；每个实际DataBuffer不得超配置，完整摘要与私有文件身份继续由原capture/path核查 |
| DocumentUploadTransferService.receiveRequest | 返回Mono，在服务器receiver上重新核查原主体/context/upload/授权，取得原企业和用户双名额；同一Lease覆盖接收、一次对象发送及保存，全部文件/名额在finally释放 |
| receiver / deadlines | 服务器分别管理消费与单次期限Scheduler；Mono.delay采用原remainingTime，takeUntilOther传递LEASE_STALE，收到新内容不延长期限；关闭桥接流取消订阅并释放剩余buffer |
| ServletUploadInputTest | 实际Tomcat10.1.55/JDK HTTP、Redis7.4.8、ACTIVE平台管理身份、当前对象部署及D0原字节；实际取消、技术撤权、停止/缓慢发送、大小/摘要/配置拒绝及完成后的私有文件/双名额清理 |
| medical-persistence/pom.xml及两个检查runner | 直接声明原Spring6.2.19、Reactor3.7.19及Servlet6.0.0 API，真实Tomcat为测试依赖；检查输入绑定六份实际库JAR，公开Schema及MySQL迁移保持原定义 |

有效Red为[UPLOAD-SERVLET-INPUT-RED-02](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-SERVLET-INPUT-RED-02/receipt.json)，实际74项中七项缺少Servlet总期限行为的断言失败，错误/跳过为零，inputs_unchanged=true；回执摘要d5e1959c64390c2bbbada7d63032c5ed7c25d785029e0900b964782306e9eb84。独立[HTTP-05](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-SERVLET-INPUT-HTTP-05/receipt.json)十二项通过，错误/失败/跳过为零，7.493秒；回执摘要9ac299a3aca85680ddc81a8ab204cf6a1c5925d6d60ae1fe447e8f04e5f7732e。原[GREEN-03](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-SERVLET-INPUT-GREEN-03/receipt.json)保存78项上传工程的原源码/十一项Servlet检查范围，回执0af8507b53e8e08e3fa535e8552c6feb8dcd95b7c3016342996a7e7e82b2a6f6；当前完整源码、接口说明、全部原生检查及制品采用上述NATIVE-01。该次Native停止发送/持续缓慢发送/实际取消分别1.093/1.074/0.058秒，包含真实网络与清理；技术撤权0.176秒，两份原件完整接收1.437秒，NonBlocking消费拒绝0.048秒。每项期限值及四秒判定上界只用于工程检查，不能登记批准生产阈值或客户业务验收。

依据为[Spring ServletHttpHandlerAdapter](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/server/reactive/ServletHttpHandlerAdapter.html)、[Spring DataBufferUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html)、实际Spring JAR及[Reactor原实现](https://raw.githubusercontent.com/reactor/reactor-core/v3.7.19/reactor-core/src/main/java/reactor/core/publisher/FluxTakeUntilOther.java)。设计维护位置为接口补充设计§2.1，来源INFERRED，实施选择在原完整开发授权内；SDK、数据库和企业权限继续使用原版本。真实客户端取消断言在技术发送资源清理以前核查服务端终态，没有以清理动作代替取消验证。组件只证明非阻塞接收、总期限和本次资源清理；公开Servlet注册、统一错误投影、配置发布及生命周期、等待期间撤权通知、完整Artifact登记、专业语义、SOURCE/AGENT和全部客户业务保持未完成要求。原receiveStream保留原内部输入流语义，不将其登记为具备主动截止的公开HTTP接收路径。以下集合保持各自原始源码与运行范围。

2026-10-05当前有效工程证据为UPLOAD-OBJECT-DEADLINE-NATIVE-02/RUNTIME-01：228项原生通过，contracts38/data1/identity26/persistence163，失败/错误/跳过为零，构建1分52秒；并发十五项7.611秒、对象十四项18.200秒、分片十项2.834秒、输入八项0.946秒、依据五项0.315秒、准备四项0.355秒、授权六项0.334秒、许可事务五项0.528秒。该次真实暂停服务的HeadObject/PutObject/GetObject超时分别为973/967/978毫秒，原PID62380已经恢复。当前JavaPID43011、UID501、部署UUID7cbd1894-d4eb-466d-9c21-02c84606d02e，0400不可变Jar摘要53250360e518557761e7809d61389b22d0b8026a2580c2462c87e7efdc2149d3；实际进程/制品核查通过，启动14.420秒，完整独立登录页面HTTP200。六项实际账户/原操作/权限4.130秒通过；334/274项输入和8/1项产物SHA全部相等。文档有效性CURRENT，last_verified=2026-10-05，适用范围为对象请求截止、完整工程构建及本地运行；客户业务状态继续读取唯一实施任务计划。

| 当前证据 | 原始回执及SHA-256 |
| --- | --- |
| 228项完整原生及全部制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-OBJECT-DEADLINE-NATIVE-02/receipt.json)；c8ddf6eed91e1e4d6f04a7dc7df5b76ac95d4075ee2444026234e7f22c1eda15 |
| 六项实际HTTP与数据库 | [当前运行](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-OBJECT-DEADLINE-RUNTIME-01/receipt.json)；7f7654ae3bb57793254d0e270b2543c4c938b39fc39336f7a92a5635b347295c |

MySQL198项迁移/146企业表/17平台表、Redis、对象服务、原D0与备份保持实际身份，没有新增迁移或客户业务记录。DeepSeek本地check返回configured=true，既有密钥文件0600及父目录0700，配置说明为[DeepSeek配置](../deepseek-configuration.md)，没有新增模型发送。原PID39752已核查终止；全部42工单、57业务（SaaS核心55）、17非功能、18边界、12AI评测、30安全及全部条件场景、批准部署/监控/备份/完整恢复/接收保持完整范围。公开上传/查询/恢复/完成、浏览器输入主动终止、持久发送协调、正式配置发布/排空后更换、专业语义/Artifact登记、SOURCE/AGENT与全部客户业务继续实施，目标保持active。以下各集合仅保留各自原始范围。

2026-10-05 BACK-UPLOAD-OBJECT-DEADLINE-01提供对象请求的原剩余租期截止。检索当前S3ObjectReader/Writer、Lease.guard、全部上传消费者、内容B时限及完整官方AWS SDK超时说明；本地2.55.11的MakeHttpRequestStage和SyncTimeoutTask字节码已经核查实际HTTP请求注册及abort调用。Given原传输租期与实际对象服务；When读取、Head核验或Put请求等待服务响应；Then请求及全部读取重试使用原剩余租期和既有SDK上限共同限制，阶段包装保留原截止，不延长租期；SDK取消原HTTP请求，错误直接传播，未知写入仍只明确恢复原对象。技术故障检查只在没有客户上传记录时暂停当前本地对象服务，采用原进程身份和必需恢复保护，不建立模拟服务、客户批准或业务数据。实际浏览器输入的主动终止、公开入口与配置发布仍保持完整要求，唯一工单状态读取实施任务计划。

| 当前文件及identifier | 字段传播、使用方与约束 |
| --- | --- |
| S3ObjectReader.TimedAdmission / TransferConcurrency.Lease.remainingTime / guard | 内部Duration来自原取得时间与原Limits，当前权限、配置、两份permit_id及阶段准入继续核查；阶段包装保留动态计时接口，不延长原租期 |
| ObjectRequestDeadline.apply / MAXIMUM_CALL / MAXIMUM_ATTEMPT | GetObject、HeadObject和PutObject采用SDK请求级apiCallTimeout与apiCallAttemptTimeout，两者取原剩余量和既有60/45秒上限的较小值；剩余不足一毫秒立即LEASE_STALE |
| S3ObjectReader.readSegment / S3ObjectWriter.writeVerified / verifyRemote | 同一原Admission进入请求、每次发送、读取核验与保存前阶段；读取重试共享同次调用截止，Put保持maxAttempts=1，恢复只读取原对象 |
| TransferConcurrencyTest / S3ObjectWriterTest | 实际Redis原时间递减、实际服务暂停后的三种SDK超时及恢复保护；写入后撤权依据实际原对象版本触发，完整固定版本恢复、原字节及重复条件拒绝分别核查 |
| check_upload_io.py / check_migration.py | 回执绑定实际sdk-core与apache-client 2.55.11 JAR和全部实际源码、测试、设计及依赖身份，没有安装或更换SDK |

UPLOAD-OBJECT-DEADLINE-GREEN-02实际67项上传工程通过，失败/错误/跳过为零，inputs_unchanged=true，构建44.250秒；并发十五项7.843秒、对象十四项19.040秒、分片十项3.281秒、输入八项1.128秒、依据五项2.235秒、准备四项0.522秒、授权六项0.306秒、许可事务五项0.482秒。[原始Green回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-OBJECT-DEADLINE-GREEN-02/receipt.json)摘要0dd44ab4d1a1778da17d27e7cc499828aaa088c072ee13cdc23fcb14ac4acf5e。原有效Red为四项缺少原剩余时间的行为断言失败，错误/跳过为零，[原始Red回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-OBJECT-DEADLINE-RED-01/receipt.json)摘要20b7d83bf7239a6b556afe3b812f0c4ae0c1d6c217f222ae6b1ed9b658868948；断言在服务暂停以前执行，实际服务身份保持完整。

该次实际HeadObject/PutObject/GetObject超时分别为942/964/927毫秒；原服务PID62380在finally与独立保护任务中恢复，Get超时后同一reader完整重读原D0固定版本并逐字节相等。Put使用原独立技术prefix，恢复以后实际版本数量至多一项，按原版本清理；未据此判定未知写入已经保存。测试的一秒租期与四秒判定/五秒保护是工程参数，不能登记正式SLO、生产阈值或客户恢复验收。SDK计时在原剩余量快照之后开始，不承诺纳秒精确截止；浏览器InputStream主动终止、原发送阶段持久协调、公开/内部入口、正式配置发布和全部客户业务继续实施。

依据：[AWS SDK timeouts](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/timeouts.html)、[AWS SDK best practices](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices.html)及实际SDK字节码；本地sdk-core JAR摘要518b50737bb48ad364da31a0a90e45ec8e4519093a678b6a1d36eea6a93e650c，apache-client摘要027afc1098f35c5dd0f7acdfb1c4bc0b2eb99d53690d6b48dd415590e8b93e19。唯一主记录保持IMP-05，关联IMP-01/03/04/06/07/08/12/16/26/37，完整制品与新运行证据继续核查；以下各集合只保留各自原执行范围。

UPLOAD-COMPLETION-CONCURRENCY-NATIVE-01/RUNTIME-01保留原始224项工程与原PID39752的执行范围：contracts38/data1/identity26/persistence159，失败/错误/跳过为零，构建1分41秒；并发十一项3.211秒、输入八项0.847秒、分片十项3.311秒、对象十四项13.940秒、依据五项0.310秒、准备四项0.380秒、授权六项0.276秒、许可事务五项0.417秒。原部署UUIDe05ea0be-9d3d-4f28-a32c-9fb87b8f05a0、制品摘要5b1a90ebad97f4c9665257557f8849d5c235aa647bf071b3fff093b516a40ada，启动14.497秒、登录HTTP200及六项实际账户/原操作/权限3.972秒属于原次运行；331/273项输入与8/1项产物在原次核查相等。当前源码与运行采用本文开头UPLOAD-OBJECT-DEADLINE-NATIVE-02/RUNTIME-01。

| 原始完成传输证据 | 原始回执及SHA-256 |
| --- | --- |
| 224项原生与完整制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-COMPLETION-CONCURRENCY-NATIVE-01/receipt.json)；3f372fcbf3ca9f07360f6d36a4d0076f15a86cb36d5ef79212fbf4932c89a3ab |
| 六项实际HTTP及数据库 | [当前运行](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-COMPLETION-CONCURRENCY-RUNTIME-01/receipt.json)；b88032a19f1ea236d3ac69f2e115c04ca5c696b66549c42efd82f644ded56a60 |
| 四项有效Red、错误/跳过为零 | [原始Red](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-COMPLETION-CONCURRENCY-RED-01/receipt.json)；21efd2a3fe3a0e1d846cef89b7864d8b98cf6a5c775a415e821bfc13809dfe91 |
| 独立63项上传工程通过 | [原始Green](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-COMPLETION-CONCURRENCY-GREEN-01/receipt.json)；26e90397887e3f19e171d9e4a94d6002de604ad31e9a81e1f9c24f3b68c60372 |

BACK-UPLOAD-COMPLETION-CONCURRENCY-01当前检索原TransferConcurrency、DocumentUploadTransferService全部接收/恢复/完成方法、UploadObjectAssembly、UploadAuthorizationAuthority、PRD§8.3/AC-29及KN-02/BX-04定义。Given实际Redis企业及用户容量、原上传许可与固定对象；When恢复原分片、重组全部分片、发送完整对象或恢复完成；Then全部外部传输持有同一企业和用户的原传输名额，每个阶段重新核查租期及当前业务授权，取得名额以前不改变完成状态，未知响应只读取原对象。Lease.guard在领域准入前后核查原租期和容量；接收方法必需原Lease，恢复和完成方法必需实际TransferConcurrency和完整Limits。完成前核查固定上传身份及当前完整授权，实际WRITING/COMPLETING仍使用原版本；STORED/COMPLETED只在当前读取许可通过后返回原回执，不产生新网络写入。当前源码全部未提交，没有Git提交或推送。

独立GREEN-01构建45.260秒，十一项并发3.517秒，实际D0条件写入、原版本恢复及完整字节重组保持同一原名额；原阶段在close以后、新请求取得容量以后、实际容量修改及阶段准入期间技术权限变化时均拒绝。其他上传检查继续全部通过；独立Green的XML保持原执行范围，当前完整产物使用NATIVE-01。技术scope采用实际对象服务/MySQL/平台账户身份，不创建客户企业、许可、批准或业务Artifact；技术对象只按本次独立prefix及原版本清理，原D0版本保持完整。

| 文件与identifier | 当前职责与使用方 |
| --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TransferConcurrency.java；Lease.guard | 原租期、容量、配置及当前准入包围每次领域检查；receive/recoverPart/completeHeld/recoverCompletion使用 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentUploadTransferService.java；inspectCompletion/requireCompletionTransportCurrent/completeHeld | 当前读取/写入分支、完整固定上传身份、取得容量后才进入COMPLETING、同一Lease连接全部重组/发送/恢复与保存前检查 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/TransferConcurrencyTest.java | 十一项真实Redis检查，其中四项阶段生命周期/容量/权限及实际对象完整恢复与重组；没有客户成功验收 |

原PID37756已核查终止；MySQL198项迁移/146企业表/17平台表、原Redis/对象服务/D0和备份保持实际身份，没有新增迁移。DeepSeek本地check返回configured=true，秘密路径仍为`.implementation-work/runtime/deepseek-api-key`，本次没有新增模型或视觉请求。关联IMP-01/03/04/06/07/37、索引、接口说明、计划、环境、报告与命令同步；唯一完整工单保持IN_PROGRESS。正式配置发布/批准及排空后更换、阻塞底层网络主动终止、发送以前中断的持久协调、公开上传/查询/恢复/完成入口、DocumentBody专业语义/Artifact登记、SOURCE/AGENT及内部认证、全部生成/编辑/核验/问题处理/审查/签发/导出、费用、安全/非功能、批准部署/监控/备份/完整恢复与接收仍需实施和验收。没有使用本次工程结果登记客户业务PASS或工单ACCEPTED。

2026-10-05原UPLOAD-CONCURRENCY-NATIVE-01/RUNTIME-01执行范围：完整220项原生通过，contracts38/data1/identity26/persistence155，失败/错误/跳过为零，构建1分41秒；并发七项1.922秒、输入八项1.093秒、分片十项2.979秒、实际对象十四项15.740秒、依据五项0.273秒、准备四项0.354秒、授权六项0.252秒、许可事务五项0.402秒。原JavaPID37756、UID501、部署UUIDbbfec586-0fd1-4f0d-9024-589c45e17963，0400不可变Jar摘要dc913fb10394ab807fb43b6b3873f76cc808d979eb48ab9bd75f23d66b2bb0b4，原进程/制品核查通过；启动15.194秒，完整独立登录页面HTTP200，六项真实账户/原操作/权限4.026秒通过。331/273项输入与8/1项产物SHA在原范围逐项相等，原进程已终止；当前完整制品与实际运行使用本记录顶部UPLOAD-COMPLETION-CONCURRENCY-NATIVE-01/RUNTIME-01，客户业务状态仍读取实施任务计划。

| 当前完整证据 | 原始回执及SHA-256 |
| --- | --- |
| 220项原生及当前完整制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-CONCURRENCY-NATIVE-01/receipt.json)；669e61da55f78dec168f808f6482d2bbe7ed5c57ea69f1413ac8b74d258c2d73 |
| 六项实际HTTP与数据库 | [当前运行](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-CONCURRENCY-RUNTIME-01/receipt.json)；4ecaa52df16eed739a34d5afc8c1f425502c45bd6cf79cd9255b5b46001df8d0 |

实际Redisson 3.52.0 SDK摘要89482bcbdbd7c8ab7ec8796e76f67ef5589d62d5fb57ce37bf3372646daae8de已绑定本次上传和原生回执；没有新增迁移或客户业务写入。原PID35424已核查终止，MySQL198项迁移/146企业表/17平台表、原Redis/对象服务/D0及备份保持实际身份，完整恢复/RTO/RPO未验收。DeepSeek本地check返回configured=true，没有新增模型或视觉请求。当前名额控制已经连接输入和发送准入，正式配置发布/批准、底层阻塞I/O主动终止、公开KN-02/BX-03/BX-04、专业正文语义/Artifact登记、SOURCE/AGENT及全部生成、编辑、核验、问题处理、人工审查、签发、导出、费用、安全/非功能、批准部署/监控/恢复/接收继续实施和验收。完整IC-05保持IN_PROGRESS，本次工程结果不登记客户业务PASS或工单ACCEPTED。

2026-10-05 BACK-UPLOAD-TRANSFER-CONCURRENCY-01继续原LLD-后端§2.4及内容B传输并发要求。Given实际Redis、两个独立客户端、明确配置版本/企业与用户名额/传输时限及当前准入；When取得、重查或释放传输名额；Then两个应用客户端共享企业及用户名额，取得完整双名额以后才能开始输入和对象发送，容量不足返回RATE_LIMITED，配置/准入变化及租期截止停止后续处理，异常释放已取得名额，未知Redis结果不自动再次取得。成熟Redisson 3.52.0的RPermitExpirableSemaphore和RBucket管理名额/租期及固定配置，Redis发送额外重试为零；技术检查不登记客户企业许可或批准数值。GREEN-03实际59项上传工程全部通过，并发七项2.271秒、输入八项1.049秒、分片十项2.979秒、对象十四项16.230秒、依据五项3.350秒、准备四项0.576秒、授权六项0.491秒、许可事务五项0.604秒，构建31.796秒。原Red五项缺少并发组件的行为断言失败、errors/skipped为零；当前完整源码/产物使用UPLOAD-CONCURRENCY-NATIVE-01，独立Green的XML仅保留原执行范围，较早213项仅保留原制品范围。公开入口、真实配置/批准、网络中断协调及全部业务仍按原工单实施，完整IC-05继续IN_PROGRESS。

| 并发原始证据 | 实际执行范围及摘要 |
| --- | --- |
| UPLOAD-CONCURRENCY-RED-01 | 57项中五项行为失败、错误/跳过为零；receipt.json：6487ea4b18e9fd84c26bc13902d9d7b35d010b5b591bf40451146a5dfb7cda3f |
| UPLOAD-CONCURRENCY-GREEN-03 | 59项通过，inputs_unchanged=true，绑定实际Redisson SDK文件；receipt.json：6c24c660cb44f83d264a0dc14187a95eb48ee91796b6001bd770a3698f633272 |

原始目录分别为`.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-CONCURRENCY-RED-01`与`.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-CONCURRENCY-GREEN-03`，客户业务PASS仍为零。

| 文件及identifier | 当前职责与调用方 |
| --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TransferConcurrency.java；acquire/Limits/Lease/RetryConfiguration | 原固定配置、企业及用户双名额、明确总租期、零额外Redis发送重试、当前准入与容量核查、原身份释放；receiveStream使用 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentUploadTransferService.java；receiveStream/receive | 取得双名额后接收输入；每次读取、文件核查、对象发送及保存以前核查原Lease与完整当前授权，外部I/O不持企业事务 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/TransferConcurrencyTest.java | 实际Redis双客户端容量、部分取得释放、配置冲突、租期、技术权限撤销、实际容量变化及两份名额下D0原件完整接收；技术scope使用实际对象服务/MySQL/账户身份，没有客户记录 |
| business/medical-persistence/pom.xml；scripts/check_upload_io.py/check_migration.py | 明确Redisson 3.52.0依赖；当前执行回执绑定实际SDK文件摘要及完整代码/环境 |

SDK使用依据已经核查[原PermitExpirableSemaphore API](https://raw.githubusercontent.com/redisson/redisson/redisson-3.52.0/redisson/src/main/java/org/redisson/api/RPermitExpirableSemaphore.java)、[原RBucket API](https://raw.githubusercontent.com/redisson/redisson/redisson-3.52.0/redisson/src/main/java/org/redisson/api/RBucket.java)及[原Config](https://raw.githubusercontent.com/redisson/redisson/redisson-3.52.0/redisson/src/main/java/org/redisson/config/Config.java)，采用原许可身份与到期、固定配置原值比较和Config完整服务器模式读取。生产配置发布/排空后的更换、批准容量、底层阻塞I/O主动终止、正式Redis故障恢复及全部客户业务仍需实现和验收；本组件不能据租期核查声明完整网络中断已经实现。

2026-10-05当前有效工程证据为UPLOAD-INCOMING-NATIVE-01/RUNTIME-01：完整213项原生通过，contracts38/data1/identity26/persistence148，失败/错误/跳过为零，构建1分48秒；输入八项1.554秒、分片十项2.918秒、实际对象十四项15.500秒、依据五项0.551秒、准备四项0.530秒、授权六项0.212秒、许可事务五项0.389秒。实际JavaPID35424、UID501、部署UUID318f629b-a071-4a1c-85ef-242fc9a1b333，0400不可变Jar摘要f8c26c4e3c03315cbbf7b98cac1d8005b0e9987e66118e9d535d0dedd64454ca，进程及实际制品核查通过；启动14.010秒，完整独立登录页面HTTP200，六项真实账户/原操作/权限3.967秒通过。两份回执328/271项输入和8/1项产物的当前SHA逐项相等，check_bytes.py输出PASS。文档有效性CURRENT，last_verified=2026-10-05，适用范围为本次输入接收及受影响工程、本地制品与运行；以下集合仅保留各自原始执行范围，全部客户业务状态继续读取实施任务计划。

| 当前完整证据 | 原始回执及SHA-256 |
| --- | --- |
| 213项原生及当前完整制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-INCOMING-NATIVE-01/receipt.json)；ca2417e486864769e884c7ae4721213782177018e2beaa4519b4c8e76be3a2bc |
| 六项实际HTTP及数据库 | [当前运行](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-INCOMING-RUNTIME-01/receipt.json)；6bf0e515175beb7e9e260824e27c650eb41b66bfb9de0e7ce01583e9910e94f9 |

原PID32289已核查终止。MySQL198项迁移、146企业表/17平台表、原Redis/对象服务/D0和备份保持实际身份，没有新增迁移或客户业务写入。DeepSeek本地check返回configured=true，秘密文件路径与使用要求读取[配置说明](../deepseek-configuration.md)，本次没有模型或视觉请求。公开上传入口、读取时限、企业/用户并发、服务器配置版本/批准依据、专业领域选择、DocumentBody专业语义/Artifact登记、SOURCE/AGENT与内部认证，以及全部生成、编辑、核验、问题处理、人工审核、签发、导出、费用、安全/非功能、批准部署、完整恢复和接收仍需实施及验收。完整IC-05保持IN_PROGRESS，不能使用本次工程检查登记客户业务PASS或工单ACCEPTED。

BACK-UPLOAD-INCOMING-PART-01已取得有效Red：六项缺少输入组件的行为断言失败，errors/skipped为零，原四十四项检查通过；原始证据为`.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-INCOMING-RED-01/receipt.json`，SHA-256为99c03773b8da78efd508a7119bcd052074dfc31fe7c2544f0753edf7978059d5。IncomingUploadPart及receiveStream已经连接完整输入、原固定范围和每次读取前后当前准入。UPLOAD-INCOMING-GREEN-02实际52项全部通过，失败/错误/跳过为零，inputs_unchanged=true；八项输入检查1.195秒，分片十项3.381秒、对象十四项18.020秒、依据五项3.568秒、准备四项1.121秒、授权六项0.548秒、许可事务五项0.705秒，构建42.593秒。回执为`.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-INCOMING-GREEN-02/receipt.json`，SHA-256为05b9e17840dd3eeeb640585f1cd74d6d904b955561aac7a8c6e01f43ddda84f5。当前完整源码/产物采用UPLOAD-INCOMING-NATIVE-01，独立Green的XML保留原始执行范围；公开入口、读取时限、企业/用户并发、服务器配置/批准依据、专业语义/Artifact登记、SOURCE/AGENT和全部后续业务保持完整工单要求。

| 文件及identifier | 当前职责与调用方 |
| --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/IncomingUploadPart.java；capture/path/close | 原分片大小、SHA-256、私有目录/文件身份及每次读取前后实际准入；receiveStream消费完整文件，关闭时清理 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentUploadTransferService.java；receiveStream/inspectIncoming/UploadIdentity | 当前Header上下文、固定上传身份及真实原范围；短事务重查，原receive负责固定写入/保存及明确恢复 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/IncomingUploadPartTest.java | 八项实际检查：两份D0原件完整字节及0600身份、长短内容/摘要/额外字节冲突、技术权限撤销、目录权限变化、部署二进制超容量、保存后当前权限及关闭后路径拒绝；没有客户许可写入 |

2026-10-05 BACK-UPLOAD-INCOMING-PART-01继续完整网络上传输入。实际读取KN-02/IN-15、原8 MiB分片、DocumentUploadTransferService全部接收/恢复路径、S3ObjectWriter私有目录规则、原服务器配置及公开回执。Given实际当前上传许可、原分片范围、Header长度与摘要及完整输入流；When接收片段；Then完整原字节必须在同一所有者的0700目录/0600文件中核查，短内容、额外内容、摘要冲突、目录权限或当前准入变化立即拒绝，未完成输入不进入分片写入。阶段重查保持当前上下文及完整授权，重复已保存片段使用当前读取许可，新发送使用原完整写入许可；并发变化和未知写入继续保留明确结果。对应Red、Green和当前完整构建证据已经取得；合法客户成功及完整公开/内部入口仍按原工单继续实施。

2026-10-05当前有效工程证据为UPLOAD-ISSUANCE-NATIVE-01/RUNTIME-01：205项原生通过，contracts38/data1/identity26/persistence140，无失败/错误/跳过，构建1分38秒；许可事务五项0.351秒、准备四项0.311秒、依据五项0.272秒、授权六项0.226秒、分片十项3.266秒、真实对象十四项15.770秒。当前JavaPID32289、UID501、部署UUIDa888348f-fe60-47f2-bc33-961fee4ed7a0，0400不可变Jar摘要ea349c8abbd36de6b0a03d1c352632d15f05aa271b4e5dcef72aaa03c8fb169b，实际进程和制品核查通过；启动14.272秒，完整登录页面HTTP200，六项真实登录/原操作/权限4.019秒通过。两份当前回执326/269项输入及8/1项产物SHA逐项相等。文档有效性CURRENT，last_verified=2026-10-05，适用范围为当前工程代码、构建与本地运行；合法客户许可事务及全部业务仍保留原验收状态。

| 当前完整证据 | 原始回执及SHA-256 |
| --- | --- |
| 205项原生与全部当前制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-ISSUANCE-NATIVE-01/receipt.json)；fede8d6d2a9b84208497b4caa3c3b876d3d0c6ce7a966deff6206a4651a8759b |
| 六项实际HTTP及数据库 | [当前运行](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-ISSUANCE-RUNTIME-01/receipt.json)；e9934f6c85ff669af4480e766c900e4a862bcc84447a6bed8c0fe12e6dbf0688 |

MySQL198项迁移、146企业表/17平台表与原Redis/对象服务/D0保持实际身份，本轮没有迁移、客户业务写入、模型或视觉请求。DeepSeek本地check返回configured=true，秘密位置读取[配置说明](../deepseek-configuration.md)。原PID29906已核查终止。内部上传许可保存、原操作重放与当前权限核查已进入当前制品；公开入口、配置版本及批准依据、专业领域选择、DocumentBody专业语义/Artifact登记、SOURCE/AGENT、内部认证和全部后续业务继续实施，不能据工程结果登记客户许可成功、业务PASS或ACCEPTED。以下较早集合仅保留各自原始源码、制品和运行范围。

UPLOAD-ISSUANCE-GREEN-02实际44项上传工程检查通过，无失败/错误/跳过，inputs_unchanged=true；新增事务五项0.982秒、准备四项0.506秒、依据五项2.451秒、授权六项0.298秒、分片十项3.384秒、真实对象十四项17.010秒，整体33.282秒。[实际执行回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-ISSUANCE-GREEN-02/receipt.json)摘要b50317b1999444120ac0afe7ffdfd6621433ac27730676825c79b8f10dd685a4。RED-01实际43项中四项行为断言失败，错误/跳过为零，[原始回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-ISSUANCE-RED-01/receipt.json)摘要bf3ca61e77c0cd078af2302c447c70dff73757abacabfff127419f998007e409。当前完整源码、产物和实际运行使用UPLOAD-ISSUANCE-NATIVE-01/RUNTIME-01；独立Green及较早制品证据保留各自原始范围。

DocumentUploadIssuance.issue/issueCurrent已连接原许可/会话/绑定、完整二十字段幂等记录、审计和Outbox的同事务保存及重读；TenantScope为UploadIssuanceMapper全部语句要求当前主体和实际企业事务。原操作摘要固定BX-03路径、Schema实际身份、目标及全部正文；同键异参冲突，同参重放重新核查当前document.read与全部原依赖，不重新分配身份、写入审计/事件或延长期限。公开DocumentUploadController/Service/Policy及正文KN-02分片处理由唯一[IMP-16](IMP-16.md)维护；服务器依据实际文稿及依赖选择固定读取要求，原请求重放使用原绑定，明确配置缺少时拒绝新许可。UploadGrant和Envelope保持原字段，Result在企业事务提交后返回。正文上传完成、Artifact业务登记、实际正文采用、客户成功及批准环境配置继续完整开发和验证，当前入口检查不能登记这些业务已完成。

| 当前新增文件/identifier | 字段传播及使用方 |
| --- | --- |
| UploadOperationRow、UploadIssuanceMapper/Mapper XML | operation_record全部二十物理字段；原企业拦截、主体/BX-03/请求键唯一范围及FOR UPDATE，保存全部十九非企业字段，原生命周期批准与期限共同为空 |
| DocumentUploadIssuance.issue/replay/normalized | 当前准入→完整Preparation→原Store保存重读→当前写入复核→幂等/审计/事件保存重读；MySQL JSON通过RFC8785比较，原响应按成熟Schema/DTO读取 |
| TenantScope.requireSubjectParameter | 新Mapper全部读取及写入继承实际主体核查，不影响原平台身份或资源读取语义 |
| DocumentUploadIssuanceTest、check_upload_io.py | 实际MySQL完整列/构造映射、原加锁查询/审计/事件、主体/事务拒绝及无客户写入检查；完整上传工程集合和原生构建使用同一当前源码 |

当前文档有效性CURRENT，last_verified=2026-10-05，适用范围为实际上传工程检查及当前完整构建和运行；当前证据使用UPLOAD-ISSUANCE-NATIVE-01/RUNTIME-01。没有新增迁移、公开Schema或前端字段、客户实体/批准/成功许可、模型请求或视觉操作。唯一主记录保持IMP-05，关联IMP-01/03/04/06/07/12/16/26/37；原42工单及全部业务、安全、非功能、部署和交接范围保持完整。

2026-10-05 BACK-DOCUMENT-UPLOAD-ISSUANCE-01继续完整上传许可事务。实际读取原UploadRequest/UploadGrant、DocumentUploadPreparation、UploadTransferStore、UploadAuthorizationAuthority、operation_record全部二十列、原审计/Outbox及LLD-后端§2.4；唯一工单和验收状态继续引用实施任务计划。Given实际EnterpriseAccess准入、当前上传请求及明确服务器Settings；When创建许可或按原请求键读取结果；Then许可/会话/绑定、完整幂等记录、审计和Outbox在同一企业事务保存并重新读取，重复请求返回当前获授权原结果且不更新许可期限，异参立即冲突。当前没有合法客户输入；实际数据库查询、全部字段与缺少准入的拒绝检查进入Red/Green，客户成功仍保留原状态。公开入口、服务器配置版本及领域选择、正文专业语义/Artifact登记和全部后续业务继续实施。

2026-10-05当前有效工程范围为UPLOAD-PREPARATION-NATIVE-01/RUNTIME-01。200项原生通过，contracts38/data1/identity26/persistence135，无失败/错误/跳过，构建1分44秒；准备四项0.700秒、依据五项0.494秒、授权六项0.213秒、分片十项3.092秒、真实对象十四项17.340秒。当前JavaPID29906、UID501、部署UUID712c00a5-b4a6-42b7-960c-cfe8a80e39cc，0400不可变Jar摘要415fd3b50aa58d3aa11ec0c6d8cc9ad8e64e9542b0e52486ceffa22880037d2d，实际进程及制品核查通过；启动13.711秒，完整登录页面HTTP200，六项真实登录/原操作/权限3.982秒通过。两份当前回执321/264项输入及8/1项产物SHA逐项相等。文档有效性CURRENT，last_verified=2026-10-05，适用范围为当前工程实现、制品及本地运行；客户业务仍保持原验收状态。

| 当前完整证据 | 原始回执及SHA-256 |
| --- | --- |
| 200项原生及全部当前制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-PREPARATION-NATIVE-01/receipt.json)；f3e8093e9b17b217830b886452ca692b9fb6776ea7bdbfdc5330ffee7fd1ed7e |
| 六项实际HTTP及数据库 | [当前运行](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-PREPARATION-RUNTIME-01/receipt.json)；ec8cd4cf54ba0745b988f86d2e7d3bf2426c38f6f887766484de0319291ff828 |

| 当前新增文件/字段 | 传播、使用与约束 |
| --- | --- |
| DocumentUploadPreparation.request/prepare | 公开八字段经实际共享Schema/DTO读取；规范请求保留完整内容供外层operation摘要使用，真实DocumentBody引用固定同一实际Schema文件摘要 |
| DocumentUploadPreparation.Settings | 完整服务器Basis/关系容量/Artifact发布摘要/字节容量/期限/上游读取要求；没有公开配置输入或默认业务阈值 |
| Prepared.grant/session/binding | 使用原UploadTransferProtocol全部字段，真实主体/文稿/项目/UTC及当前完整授权；供外层UploadTransferStore与原operation、审计/Outbox同事务保存及重读 |
| DocumentUploadPreparationTest与check_upload_io.py | 缺少实际准入、实际公开资料的错误请求类型、UTF8/多根JSON及不完整服务器配置拒绝；没有客户批准或正向许可写入 |

原MySQL198项、146企业表/17平台表及Redis/对象服务/D0保持实际身份，本轮没有迁移、客户业务写入或模型请求。DeepSeek本地check返回configured=true，配置位置读取[配置说明](../deepseek-configuration.md)。原PID27548已核查终止，原31项身份/DOM及各较早上传集合保留其原制品范围，未使用视觉功能。服务器配置版本/批准依据、上游固定领域选择、同事务许可保存/原operation/审计/Outbox、专业语义/Artifact登记、全部公开与内部入口、SOURCE/AGENT及完整客户成功继续实施；准备结果不能登记许可已签发或业务ACCEPTED。完整42工单、57业务、17非功能、18边界、12AI评测、30安全、批准部署/监控/备份/恢复/交接及全部条件场景保持原范围。

UPLOAD-PREPARATION-GREEN-01该次39项上传工程检查通过，无失败/错误/跳过，inputs_unchanged=true；新许可准备四项0.720秒、依据五项2.250秒、上传授权六项0.663秒、分片十项3.015秒、真实对象十四项16.020秒。[原始实际执行回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-PREPARATION-GREEN-01/receipt.json)摘要471e115e08c7c5951bb83495d7d99511388d45b703a8678558006816a380200e。RED-01实际39项中四项缺少完整服务器准备的行为断言失败，错误/跳过为零，[原始回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-PREPARATION-RED-01/receipt.json)摘要b5be9a5104277dc63932439576b2fc4e837f04f3ecec3f86410111e96ce29984。检查使用实际Mapper/Schema组件、现有公开技术数据的格式拒绝及真实对象服务；没有构造客户批准或合法许可，正向准备、全部字段采用、实际期限和客户业务仍需真实输入核查。当前完整源码、接口说明和新运行制品继续取得独立证据。

DocumentUploadPreparation.request/prepare已提供完整服务器准备：固定实际发布UploadRequest和DocumentBody、严格UTF8/完整JSON、真实Basis/依赖Header和完整Authority、原Manifest/Binding、当前数据库UTC与全部实际权限/权利截止、原grant/session/binding全部字段及私有对象键。Settings完整要求由服务器领域消费者提供，容量、期限、发布摘要和上游资源/Schema不能通过默认值或登记值推断。Prepared保留原规范请求及全部核验结果；外层同事务幂等operation、审计/Outbox和保存/重读仍需连接，当前没有登记许可已签发或客户上传完成。唯一主记录保持IMP-05，关联IMP-03/04/06/07/12/16/26/37；全部原任务范围和状态保持完整。

2026-10-05 BACK-DOCUMENT-UPLOAD-PREPARATION-01继续服务器许可准备。检索原UploadRequest/UploadGrant、TypedSchemaBindings、完整Basis/UploadAuthorizationAuthority、原grant/session/binding全部字段和数据库UTC；上游ReadRequirement继续由领域服务固定，不能采用登记值推断预期Schema或资源。Given实际当前主体、公开上传请求与明确服务器配置；When准备许可；Then使用固定发布Schema读取完整UTF8/JSON，核查当前任务/计划/快照、全部来源和依赖，构建完整服务器Manifest和原绑定，并准备全部许可/会话字段。大小、期限、发布摘要及上游读取要求不能由默认值补充。许可、幂等operation、审计/Outbox同事务写入及公开入口继续同一工单；当前准备结果不能代表已经签发许可或完成客户业务验收。

2026-10-05当前有效源码与运行范围为UPLOAD-BASIS-BINDING-NATIVE-01/RUNTIME-01。196项原生通过，contracts38/data1/identity26/persistence131，失败/错误/跳过均为零，构建1分38秒；分片十项2.989秒、实际对象十四项15.620秒、上传依据五项0.442秒、上传授权六项0.201秒。当前JavaPID27548、UID501、部署UUID734ba7ee-b1e8-4770-b3b2-6e69a75224bb，0400不可变Jar摘要6688d4054190a47c4d1c82008073c02489812ae7d8ad06f4cf8cff56ad76d07a，实际进程和制品核查通过；启动13.423秒，完整/auth/login页面HTTP200，六项实际登录/原操作/权限检查3.831秒通过。两份当前回执319/262项输入和8/1项产物逐项SHA全部相等。文档有效性CURRENT，last_verified=2026-10-05，适用范围为当前工程实现与实际本地制品；全部客户业务仍按原验收状态核查。

| 当前完整证据 | 原始回执及SHA-256 |
| --- | --- |
| 196项原生、当前源码及全部制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-BASIS-BINDING-NATIVE-01/receipt.json)；b54aa0eab8b3afbee45ba499bb6cbe5402941815ad4f7db6c9c1e9b1fb2516f0 |
| 六项实际运行检查 | [当前HTTP及数据库](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-BASIS-BINDING-RUNTIME-01/receipt.json)；8f0f84208cb3c8edb14364b88539d6f75f9ffaf865814601177d1c3fc6b2f280 |

当前MySQL198项迁移、146企业表/17平台表及原Redis/对象服务保持原实际身份；本轮没有新增迁移、客户业务写入或模型请求。DeepSeek本地check返回configured=true，位置读取[配置说明](../deepseek-configuration.md)，秘密没有进入检查输出。完整清单核查仍为42工单=7 IN_PROGRESS/1 IMPLEMENTED/34 TODO，57业务+17非功能=36 NOT_RUN/38 BLOCKED，客户业务通过零项。18边界、12AI评测、30安全、正式部署/监控/备份/恢复/交接及全部条件场景保留；当前没有登记业务ACCEPTED。原31项身份、DOM和各次上传集合分别保留原制品范围，未执行视觉检查。

UPLOAD-BASIS-BINDING-GREEN-03当前35项上传工程检查通过，失败/错误/跳过均为零，inputs_unchanged=true；分片十项2.889秒、真实对象十四项16.240秒、上传依据五项2.151秒、上传授权六项0.410秒。[实际执行回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-BASIS-BINDING-GREEN-03/receipt.json)摘要472a631a7fc653bfb814af562a8b7a94fe26d3f94acb52fc2010920e79a8fddb，保存执行时的完整源码、接口说明和四份JUnit范围。RED-01实际35项中两项行为断言失败，错误/跳过为零：[原始断言回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-BASIS-BINDING-RED-01/receipt.json)，摘要00c5e0467dc2ea633db14705f771361dc3aa5a4a187c7dde08ef96e4764ae779。实际检查读取原MySQL物理字段和完整构造映射，缺少真实主体/事务/核验依据及不完整配置均拒绝；两份D0原件与实际部署二进制仍使用真实固定版本和连续范围核查，没有创建客户批准、许可或成功回执。

| 本轮文件及字段传播 | 当前职责及使用方 |
| --- | --- |
| DocumentUploadBasisProtocol及DocumentUploadBasisMapper/Mapper XML | 完整task_head十一字段、task_revision十五字段、plan_revision十三字段、scope_snapshot八字段、scope_member九字段；U64使用BigInteger，UTC时间使用LocalDateTime；读入完整Basis |
| DocumentUploadBasisAuthority.require/bind/requireBinding | 当前任务指针、确认计划/决定、原注册摘要、全部成员与FORMAL来源许可；固定完整依据SHA及明确服务器Settings；对原依赖清单核查六类固定依据 |
| UploadAuthorizationProtocol、upload-authorization.schema.json、UploadAuthorizationSchema | 内部清单十七字段；document_basis封闭保存task_id/task_revision/plan_revision/snapshot_id/basis_sha256/settings；DOCUMENT_BODY必填，AGENT_ARTIFACT为空；原绑定核验使用新实际Schema摘要 |
| UploadAuthorizationAuthority及DocumentUploadTransferService | 每阶段写入读取当前完整Basis并比较原绑定，完整Basis进入授权摘要；原已保存回执继续核查当前读取许可与原依赖 |
| DocumentUploadBasisTest、UploadAuthorizationTest及check_upload_io.py | 实际只读MySQL、全部字段/Schema/主体/事务、缺少实际Basis及完整配置拒绝；完整实际对象与分片回归 |

公开接口与Vue没有新增document_basis输入，未执行视觉检查；本轮没有新增迁移、客户业务写入或模型请求。内部Binding及全字段查询已经连接实际写入准入；合法客户创建许可、task依据更新后的实际拒绝和全部业务成功仍需真实项目条件核查。服务器许可创建、专业正文语义/Artifact登记、原幂等操作/审计/Outbox、SOURCE/AGENT、TaskToken/mTLS/lease、完整公开入口、取消/期限和发送前中断协调继续完整实施。当前完整构建和运行使用UPLOAD-BASIS-BINDING-NATIVE-01/RUNTIME-01；各较早集合保持原始输入和制品范围。

2026-10-05 BACK-UPLOAD-BASIS-BINDING-01继续当前写入依据绑定。实际VersionResourceMapper保留历史任务/计划/快照版本读取语义；文稿上传写入需要使用DocumentUploadBasisAuthority读取当前task_head.current_revision和确认计划。Given固定原上传许可；When任务、计划、快照或当前来源核准改变；Then每次发送及保存重新读取完整依据并比较原服务器绑定，拒绝已变依据。原已保存对象回执仍使用当前读取权限和原完整依赖。内部授权Schema追加document_basis，公开请求不接收该字段；全部来源、领域及上游核查继续保持完整。缺少真实企业成功条件时仅记录工程检查，完整IC-05保持IN_PROGRESS。

2026-10-05 BACK-UPLOAD-BASIS-01继续服务器上传许可依据。检索实际document_head、task_head/task_revision、plan_revision、scope_snapshot/scope_member完整物理字段、原SourceRead与VersionRegistry和BX-03要求。Given实际文稿所属任务及固定计划/快照；When服务器构建上传依据；Then读取全部原字段，核查当前主体、写入权限、原文稿revision及签发状态、任务/计划/快照复合关联与原版本摘要，来源要求由实际快照和明确服务器环境/区域产生。不能让公开请求选择来源权限、计划/快照或Schema摘要；完整许可、依赖清单和产物登记继续同一IMP-05，客户验收保持IN_PROGRESS。

UPLOAD-IO-NATIVE-01及UPLOAD-IO-RUNTIME-01保持其原始源码、制品和运行范围。该次191项原生通过，contracts38/data1/identity26/persistence126，构建1分37秒；原应用PID24231、部署UUID06075b9a-4a85-45f1-9766-2537096be130、制品fc27f82f2dff27e7f0400593b708cb30c1ca3dd28707451becf18cf029976983，启动13.681秒、HTTP200，原六项回归4.383秒。当前完整源码、制品和运行使用UPLOAD-BASIS-BINDING-NATIVE-01/RUNTIME-01；原进程已经核查终止。

| 当前完整证据 | 原始回执及SHA-256 |
| --- | --- |
| 191项原生及全部当前制品 | [完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-IO-NATIVE-01/receipt.json)；57f184a1fdc524daad6e98e00b4684e6e9939e4ce29d5c55f43a161c07bb8c79 |
| 六项实际运行回归 | [当前HTTP及数据库](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-IO-RUNTIME-01/receipt.json)；850130925e154ad639f837a622dc4759aad61610391b6153230aaac0a8eb3afc |

本轮没有新增迁移、客户业务写入、模型请求、Git提交/推送/恢复、子代理、自动化或视觉操作。MySQL198项、146企业表/17平台表，原Redis/对象部署与D0版本保持原身份。DeepSeek本地check返回configured=true，配置位置读取[配置说明](../deepseek-configuration.md)。新组件没有改变公开身份及页面协议，当前回归使用真实账户和原操作，原31项账户及两组DOM回执保持原制品范围；需要这些入口再次改变时执行对应回归。启动时间和二进制输入只证明工程观测，不代表批准性能阈值或客户业务成功。

UPLOAD-IO-GREEN-02三十项原执行检查通过，无失败、错误或跳过，分片持久化十项3.225秒、实际对象写入及重组十四项16.760秒、上传授权六项2.475秒；[实际MySQL和对象服务回执](../../.implementation-work/IC-05-UPLOAD-IO-20261005-UPLOAD-IO-GREEN-02/receipt.json)摘要8de64d56058910a9e3ba3bd6746ca0d3de2092c8eef4efbaa3e5ef9b242fb4fe，inputs_unchanged=true。两份D0原件及实际部署的153798704字节SeaweedFS二进制分别使用真实对象写入/固定版本恢复/连续重组；二进制为十九个固定分片，十八个8 MiB片段与一个2803760字节末片段，重组结果与原文件逐字节相等，完整摘要保持实际部署身份。检查使用技术对象独立prefix，按实际versionId清理；没有客户企业、项目、许可、正文或批准，也没有模型请求。该集合保持原始接口说明及JUnit XML，当前完整源码和说明使用UPLOAD-IO-NATIVE-01核查。

DocumentUploadTransferService连接原会话/绑定、分片预留及WRITING→STORED、独立当前授权、实际条件Put、原写入恢复、连续重组与原会话COMPLETED保存/重读。每次实际I/O和保存比较完整当前授权，完成与恢复共同调用DocumentBodySemantics核验固定发布Schema、完整结构及规范字节；服务器消费者显式提供正文节点、单元格和关系容量。当前结构校验、受影响检查和剩余正文流程引用唯一[IMP-16](IMP-16.md)。同内容读取原回执、异参拒绝；已保存回执使用当前document.read和完整原依赖，不重新采用旧写入期限或旧文稿revision。当前提供内部DOCUMENT_BODY消费者；专业资格、Artifact登记、许可创建、幂等操作/审计/Outbox、公开BX-03/04/KN-02、SOURCE/AGENT消费者、内部TaskToken/mTLS/lease、发送前中断的显式协调、取消/过期业务入口及完整客户成功继续实施，不能登记业务完成。

| 当前新增或关联文件 | identifier及实际职责 | 使用方 |
| --- | --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DocumentUploadTransferService.java | receive/recoverPart/complete/recoverCompletion，真实短事务和对象I/O | 待接入公开文稿领域完成服务 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadObjectAssembly.java | 固定范围、私有文件、完整摘要与当前准入 | 文稿完成；SOURCE/AGENT后续共用 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadTransferStore.java | partIfPresent读取原分片全部字段及实际身份 | 重复片段和原写入恢复 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadAuthorizationAuthority.java | requireVisibleBoundDocument，当前读取许可和全部原依赖 | 原分片及完整对象回执 |
| scripts/check_upload_io.py；S3ObjectWriterTest/UploadTransferTest/UploadAuthorizationTest | 实际D0原件、部署二进制、MySQL字段、完整范围和权限撤销 | 独立检查及完整原生构建 |

Given固定原许可、真实私有对象及当前身份；When重复分片、恢复未知响应或完成重组；Then同内容使用原回执、异参拒绝，当前完整权限在各阶段重新核查。Given实际部署二进制与原公开文件；When读取全部固定8 MiB分片及末片段；Then完整大小、摘要和原字节相等；缺少片段、顺序错误、媒体类型不符、完整摘要不符、私有权限变化和超容量分别拒绝。原RED-01十二项中三项行为失败，0错误/跳过、4.114秒，回执9bedfb1cf2667afe7147d9368932df3d7c87935730036e6c7033f06df640aaf6；RED-02十项中一项行为失败，0错误/跳过、3.012秒，回执da6f72d8f9fd7a07f21e6bc0ee64a276080d111fd2799aef6bde3e6b8581b3ac；RED-03二十八项中一项行为失败，0错误/跳过，回执c392a5c4fb16ecc5d587a47a52060faebdd65f1bdb357488357d79428c6eb0f6。原始证据保存在各独立执行目录，不代表客户合法成功已经验收。

2026-10-05 BACK-UPLOAD-IO-01继续原分片业务服务。检索当前UploadTransferStore/Mapper/Protocol、UploadAuthorizationAuthority、EnterpriseAccess、S3ObjectReader/Writer、实际D0原件、BX-03/04及KN-02；采用原8 MiB范围、原write_id和短事务准入。Given当前服务器固定许可和已保存分片；When接收重复片段、恢复原写入或完成连续重组；Then相同内容读取原回执、异参拒绝，实际I/O与保存分别核查当前完整授权，未知响应明确重读原对象，完整文件核查大小与SHA-256。实际企业正向上传、专业语义、Artifact登记、幂等操作/审计/Outbox及全部公开/内部入口继续实施。当前源码修改后使用新执行身份核查，以下证据保持各自原始范围；工单继续IN_PROGRESS。

2026-10-05 UPLOAD-AUTHORIZATION-NATIVE-03及RUNTIME-02为当前源码、制品和本地运行证据。全部184项原生通过，contracts38/data1/identity26/persistence119，无失败、错误或跳过，构建1分25秒；上传授权五项0.195秒，完整发布Schema八项2.269秒。当前应用PID21090、UID501、部署UUID60ff7cb3-e0e1-4306-929e-3cfc45667c6a、制品b073b82928e8fe6a8d50aa1d6faf9d0e4b00f77c4a2840bc1279ea9077f51a37，0400不可变Jar和实际进程核查通过，启动12.835秒、登录HTTP200。31项实际账户与会话22.985秒、六项原操作2.835秒及两组实际登录DOM均通过；Chrome154.0.8037.93，视觉检查未执行。四份回执的312/264/255/92项输入及8/10/1/1项产物逐项重读，SHA-256全部相等。启动时间和线程输入仅为当前工程观测，不表示性能阈值或正式业务已经批准。

| 当前工程证据 | 原始回执及SHA-256 |
| --- | --- |
| 全部184项原生 | [完整当前构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-AUTHORIZATION-NATIVE-03/receipt.json)；a69180725cc439f39449f7e8397bf14153b4e7af2ce4df1f9989cd7b3a33d165 |
| 31项实际账户与会话 | [当前HTTP及数据库](../../.implementation-work/IC-04-LOCAL-HTTP-20261005-UPLOAD-AUTHORIZATION-RUNTIME-02/receipt.json)；920d0bdd7c4cb91370c584c859f6509a2628b9e48ccf66a0bf7e236ec97f1c27 |
| 六项原操作 | [当前HTTP与权限](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-AUTHORIZATION-RUNTIME-02/receipt.json)；928ba69800adadf5c547815e7f78324333a922e61375bb1e915195e9642827f6 |
| 两组实际DOM | [登录连接及输入](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-UPLOAD-AUTHORIZATION-RUNTIME-02/receipt.json)；0619aed7a7cd4eb40733cf32e66b39318c4b942fafc23c2ceda2a97c163242d9 |

当前授权核查与成熟Schema已进入运行制品，内部Store保存/读取原绑定使用完整校验；当前文稿消费者核查实际企业/主体、水位、固定发布Schema、document.write、原头记录/项目状态、资源revision/SIGNED、完整原依赖及所有来源/领域/上游许可。原绑定额外核对contextRevision、上传/许可状态和UTC期限；实际I/O授权摘要已保留完整当前信息。完整许可创建、实际传输与恢复、SOURCE/Artifact登记、幂等操作/审计/Outbox、公开BX-03/04/KN-02、内部IN-13—15/IA-08/TaskToken/mTLS/lease及全部客户成功继续实施。当前工程检查没有创建客户企业、项目、文稿、许可、核准或Artifact，不能登记客户上传完成。全部42工单仍为7 IN_PROGRESS/1 IMPLEMENTED/34 TODO，57业务+17非功能仍为36 NOT_RUN/38 BLOCKED；18边界、12AI评测、30安全、正式部署和交接保持完整。

本轮没有迁移；MySQL仍为198项、146企业表/17平台表，原Redis和对象服务、D0固定版本及备份保持原范围。完整恢复/RTO/RPO未验收。DeepSeek本地check再次返回configured=true，密钥位置读取[配置说明](../deepseek-configuration.md)，本轮没有新增模型请求。当前共享Schema工作由唯一[IMP-03](IMP-03.md)维护。以下集合保留原始输入、制品和运行范围。

当前文稿路径在读取状态及判断原revision/SIGNED以前核查当前document.write许可。当前最终授权摘要仍保留同一许可和全部固定依赖，公开字段和业务状态保持原要求；当前制品和运行证据按最终源码重新验证。

| 当前相关文件 | identifier与实际用途 | 后续消费者 |
| --- | --- | --- |
| business/medical-persistence/src/main/resources/upload-authorization.schema.json | 根十六字段、六类完整要求及封闭Schema | 上传原绑定保存和读取 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadAuthorizationProtocol.java | Manifest，原身份/水位、Schema/依赖、完整来源/领域/上游要求 | 服务器固定授权选择 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadAuthorizationSchema.java | encode/decode/requireBinding、成熟Schema/RFC8785和完整许可标量核对 | UploadTransferStore及领域上传 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadAuthorizationAuthority.java | requireDocument/requireBoundDocument、当前文稿/许可/依赖和完整授权摘要 | 每次实际I/O和保存的当前准入 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/UploadTransferProtocol.java；mapper/UploadTransferMapper.java；resources/medical-mappers/UploadTransferMapper.xml | DocumentRow/document、原文稿十一字段及实际项目状态 | 当前文稿上传核查 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/UploadAuthorizationTest.java；scripts/check_upload_authorization.py | 实际MySQL完整字段、D0错误类型及缺少当前准入检查 | 独立检查与完整原生构建 |

UPLOAD-AUTHORIZATION-NATIVE-01完整182项原生检查通过，contracts36/data1/identity26/persistence119，构建2分43秒、失败/错误/跳过为零；[原生执行回执](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-AUTHORIZATION-NATIVE-01/receipt.json)摘要7b5429d397accab67c3600c8eb61a197f719e851f51e446d6339c3fbbd5fce07，312项输入及八项产物逐项SHA核查通过。该集合保留其原始制品范围；当前SchemaCatalogue共享由唯一[IMP-03](IMP-03.md)维护，新增完整原生构建及实际运行继续取得当前证据，完整上传许可创建、实际传输、领域登记和全部业务仍未验收。

UPLOAD-AUTHORIZATION-GREEN-03五项检查通过，8.993秒，failures/errors/skipped均为零、inputs_unchanged=true；[授权、当前文稿与实际MySQL回执](../../.implementation-work/IC-05-UPLOAD-AUTHORIZATION-20261005-UPLOAD-AUTHORIZATION-GREEN-03/receipt.json)摘要313223ce02d99633f081cd3c8c450da50af1d13e1ed535c847a1bdc2af02c4f5。核查六类已有完整读取要求、十六项根字段、封闭Schema、实际D0资料类型拒绝、当前文稿全部字段/项目状态查询及两个授权入口缺少实际Admission拒绝。RED-02五项中两项业务行为失败，errors/skipped为零、1.823秒，[原始执行回执](../../.implementation-work/IC-05-UPLOAD-AUTHORIZATION-20261005-UPLOAD-AUTHORIZATION-RED-02/receipt.json)摘要e0f8448339804b48c9eb63aa2fbee104cfe689f30336328bde0966657096e7df。RED-01三项行为失败回执摘要41b181d57724019f23a4da0ecb8052dfa9ce61787ffa17e50ba63ac28544be75，保留原始执行范围。完整制品构建继续核查新增物理字段比较；技术拒绝不证明合法客户上传成功。

当前UploadAuthorizationProtocol/Schema保留服务器固定的全部来源、领域、上游要求与原身份/水位/Schema/依赖；UploadTransferStore已连接成熟Schema及JCS验证，保存前和读取原绑定时逐项核对许可及绑定标量。UploadAuthorizationAuthority.requireDocument在当前企业事务核查真实文稿头十一字段、项目ACTIVE、资源revision、SIGNED、document.write、固定发布DocumentBody及全部DependencyAccessSet/来源/36类领域许可。requireBoundDocument继续核查原contextRevision、上传/许可当前状态及数据库UTC截止时间，AGENT_ARTIFACT拒绝该公开路径。授权摘要包含当前身份、上下文、水位、文稿、许可及完整依赖，供领域传输服务在每次实际I/O及保存时比较。完整许可创建、实际传输、登记、幂等操作/审计/Outbox、公开/内部认证与客户正向验收仍继续实施。

当前源码已改变，UPLOAD-TRANSFER-NATIVE-01及其运行回执保留各自原始输入与制品范围；当前授权代码需要新完整原生构建及新应用运行证据。以下集合仅证明其记录的版本，客户业务验收状态继续读取实施任务计划。

2026-10-05上传授权Schema三项检查已取得真实终态，failures/errors/skipped均为零，回执为IC-05-UPLOAD-AUTHORIZATION-20261005-UPLOAD-AUTHORIZATION-GREEN-01，SHA-256为c8ece9e2b945768b4223a25b2d5d99a8397c9c54a43516026e336c0cbb0cba13。当前继续核查实际MySQL文稿头全部十一字段、当前项目状态及缺少EnterpriseAdmission拒绝；公开上传动作继承接口补充设计的document.write，正文采用仍继承document.edit。Given当前文稿及固定服务器授权；When读取上传原绑定或创建许可；Then当前企业、主体、水位、原文稿版本、SIGNED状态、固定Schema及全部依赖分别核查。合法客户上传和完整业务仍未验收。

2026-10-05 BACK-UPLOAD-AUTHORIZATION-01继续完整上传授权与实际传输。检索UploadTransferStore、SourceReadProtocol、ArtifactRegistryProtocol、DependencyAccessAuthority、VersionResourceProtocol、ContentReadAuthorization、document_head全部字段及BX-03/04/IN-13—15。Given：上传绑定保存服务器固定的全部来源、领域及上游要求；When：保存清单、恢复读取或执行文稿上传；Then：成熟封闭Schema、RFC8785和精确U64保持全部原字段，当前主体/企业/权限水位、原依赖、固定发布Schema及文稿版本/签发状态重新核查。实际内部认证、传输、登记和客户成功继续保持完整原要求，唯一记录仍为IMP-05。

UPLOAD-TRANSFER-RUNTIME-01当前运行：JavaPID16369、UID501、部署UUIDe97165c0-9792-4157-ae02-29d3a44cd66a，制品SHA-256为d898739da959d2076996d8f1a7a52c7cf8116c998e4444ba7a8c1d6473fb945e，0400不可变Jar与实际进程已经核查，启动46.585秒、实际/auth/login HTTP 200；原PID12398已经核验终止。六项实际原操作HTTP/数据库检查7.263秒通过，两组真实登录/连接恢复/输入DOM检查通过，未执行视觉检查。完整177项原生、当前六项原操作及两组DOM分别使用以下回执；较早集合保留原始输入和制品范围。MySQL198项迁移、146企业表/17平台表、Redis、对象服务及原D0固定版本保持完整，本次没有客户业务写入或模型请求；DeepSeek本地check返回configured=true。

| 当前上传保存与运行证据 | 原始回执及SHA-256 |
| --- | --- |
| 完整177项原生 | [当前完整制品](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-TRANSFER-NATIVE-01/receipt.json)；991bda17d806e5280ba5c2d9244f71478f8ad34d562c948c30ef8cc343c637d9 |
| 六项原操作 | [实际HTTP与数据库](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-TRANSFER-RUNTIME-01/receipt.json)；68129ce2a8e7d10bc7d06393b0e04c2f21e0145e7b50cff216b99ac9d188d630 |
| 两组登录DOM | [实际连接与输入](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-UPLOAD-TRANSFER-RUNTIME-01/receipt.json)；fad12a618aad16f1bdfa7e70a72ed04287828d4dca14df13605f39bbb84d1373 |

三份当前回执的307/250/92项输入及8/1/1项产物逐项读取，check_bytes.py输出全部PASS，SHA-256相等。当前浏览器版本154.0.8037.93；完整源码、当前运行及业务范围分别核查。全部当前记录没有登记ACCEPTED，42项工单仍为7 IN_PROGRESS/1 IMPLEMENTED/34 TODO，74项业务及非功能为36 NOT_RUN/38 BLOCKED，完整客户业务通过零项。

当前内部保存消费者仍需完整上传服务连接：authorization_manifest封闭Schema/RFC8785、原published Schema和完整当前DependencyAccessSet/来源/领域/上游授权、SOURCE实际来源登记及Artifact登记；服务器固定原幂等操作、审计及Outbox；分片实际条件Put、WRITING原write_id恢复、全部连续字节重组与完整SHA；公开BX-03/04/KN-02和内部IN-13—15/IA-08、TaskToken/mTLS/lease、C07专属ModelResult/ContextInput；全部客户上传及后续生成、编辑、证据、问题、审核、签发和导出。完整业务、安全、非功能、正式部署及交接继续IN_PROGRESS。

UPLOAD-TRANSFER-NATIVE-01完整177项原生检查通过，无失败、错误或跳过，构建2分31秒；contracts36/data1/identity26/persistence114，新增上传九项2.953秒。[完整当前制品回执](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-TRANSFER-NATIVE-01/receipt.json)摘要991bda17d806e5280ba5c2d9244f71478f8ad34d562c948c30ef8cc343c637d9，inputs_unchanged=true；307项输入及八项产物逐项重读SHA相等。新应用制品d898739da959d2076996d8f1a7a52c7cf8116c998e4444ba7a8c1d6473fb945e、PID16369、部署UUIDe97165c0-9792-4157-ae02-29d3a44cd66a，实际启动46.585秒；原PID12398已核查并终止。适用登录与原操作运行检查继续执行，完整上传成功及全部客户验收未完成。独立GREEN-05保留其执行时XML范围，当前完整源码以本次原生回执为准；以下各集合保持原始输入、制品和运行范围。

UPLOAD-TRANSFER-GREEN-05九项检查通过，3.130秒，failures/errors/skipped全部为零，inputs_unchanged=true；[原件与实际MySQL回执](../../.implementation-work/IC-05-UPLOAD-TRANSFER-20261005-UPLOAD-TRANSFER-GREEN-05/receipt.json)摘要46513242dbe7e796e342880a00f58bf36a3e1fe1a6bdb0aa14527ca1e9df9ef4。核查六类完整读取的全部constructor/列映射、四类INSERT全部非生成字段、全部Mapper主体/事务门槛、八项CAS原版本及单目标语句、实际MySQL执行计划、当前精确接收总量/UTC、缺少EnterpriseAdmission拒绝、实际两份D0原件转换与冲突以及完整U64/part_no容量边界。EXPLAIN只取得计划，未执行客户INSERT/UPDATE；依据[MySQL EXPLAIN完整说明](https://dev.mysql.com/doc/refman/8.4/en/explain.html)。九类客户记录仍为空，customer_records_created=false、formal_evidence_eligible=false。完整原生构建及受影响当前运行继续核查，合法客户写入和上传业务尚未验收。

UPLOAD-TRANSFER-RED-01已取得实际终态：五项检查中四项因缺少上传读取、完整保存、主体核查或原版本更新失败，errors=0、skipped=0，2.826秒，inputs_unchanged=true；回执[实际上传检查](../../.implementation-work/IC-05-UPLOAD-TRANSFER-20261005-UPLOAD-TRANSFER-RED-01/receipt.json)，SHA-256为cf9ae73ae7eca06701a67b8778547c02eada8a78a754db5e4527cfe751654b22。当前Protocol/Mapper/Store提供原许可及来源完整字段、实际事务/主体、短事务CAS、稳定分片分页、完整范围/总量和对象重读；UploadTransferObjects保留上传物理表原五字段。独立检查和完整构建继续执行，完整客户上传、实际传输与领域登记仍未验收。

2026-10-05 BACK-UPLOAD-TRANSFER-01继续完整上传持久化与领域服务。检索原026、SOURCE/DOCUMENT_BODY/AGENT_ARTIFACT身份、EnterpriseAccess/TenantScope、已有短事务/CAS、S3ObjectWriter、BX-03/04/KN-02/IN-13—15和完整对象核验。Given：实际主体与企业事务已经核验，上传保留原许可及固定分片；When：保存、开始发送、核对原写入、重读全部字段或完成；Then：主体、原版本、完整对象及连续范围必须保持一致，未知响应保留原写入身份，取消/过期阻止新发送。唯一记录仍为IMP-05，完整授权Schema、实际传输、领域登记、公开/内部入口和客户验收继续IN_PROGRESS。

UPLOAD-STORAGE-RUNTIME-01当前运行：JavaPID12398、UID501、部署UUID7af0e237-6f72-441d-b5bb-7ee2a9b71a6d、制品e272d55b10ab13accbbf15fb0b7d73e31c25204d0861c148b0ff781879c50021，启动48.287秒，实际/auth/login HTTP 200；原PID8948已经核验终止。31项实际独立账户/登录HTTP与数据库检查30.259秒通过，原操作六项3.239秒通过，两组实际登录与输入DOM检查通过，Chrome154.0.8037.93，未使用视觉功能。六份当前回执的301/43/49/253/244/92项输入及8/2/1/10/1/1项产物逐项重读，SHA全部相等，check_bytes.py实际输出PASS。新迁移后各身份、水位和原操作检查继续使用实际账户，未创建客户企业、项目、上传许可、Artifact或批准。DeepSeek本地check再次返回configured=true，秘密保持原位置与权限，没有新增模型请求。

| 当前上传结构及制品运行证据 | 原始回执与SHA-256 |
| --- | --- |
| 全部168项原生 | [当前完整构建](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-STORAGE-NATIVE-02/receipt.json)；e951db014692289ff2bbc124c729e36c4c361db246cfd197a1f58beaa189c2ac |
| 十项实际上传结构 | [真实MySQL与原件元数据](../../.implementation-work/IC-05-UPLOAD-STORAGE-20261005-UPLOAD-STORAGE-GREEN-03/receipt.json)；af9fd27cba3f28fa2ea5cb5f6d4f456d6268b8e89c40d0a19e9b0966a791e4a2 |
| 十五项迁移覆盖 | [完整主键/外键/类型核查](../../.implementation-work/IC-03-MIGRATION-COVERAGE-20261005-UPLOAD-STORAGE-COVERAGE-01/receipt.json)；f69d931330ae558f74943efb74df90a40ad81f82e9a2aa90fd591de55c2fdf5f |
| 31项账户与实际登录 | [真实HTTP及数据库](../../.implementation-work/IC-04-LOCAL-HTTP-20261005-UPLOAD-STORAGE-RUNTIME-01/receipt.json)；bbc611dc71adaaba1e3061b3a1fcdc18c19c7fc23077efbd2841ccc856309bde |
| 六项原操作 | [HTTP/当前数据库](../../.implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-STORAGE-RUNTIME-01/receipt.json)；e787888c98174a6ddadf8a1442c811f4e3772bf3a38b040b8f63566d70199aeb |
| 两组实际DOM | [连接恢复及账户输入](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-UPLOAD-STORAGE-RUNTIME-01/receipt.json)；225e470266a517001fe4c3c3a34cf492a3fd1bb764258460efb669313c4129f4 |

当前BACK-UPLOAD-STORAGE-01物理结构已执行并核查；原数据包打包和迁移回执分别保留执行时的制品范围。完整许可服务、逐片CAS/实际写入/原响应核对、完整连续重组、领域Schema与完整当前依赖、Artifact登记及公开/内部入口继续实施。authorization_manifest必须由成熟封闭Schema和RFC8785保存服务器固定来源、领域及上游要求，不能从客户端选择字段推导授权。实际I/O不持长期企业事务，保存及重读重新核查当前权限；AGENT_ARTIFACT仍需TaskToken/mTLS、运行主体和lease。全部生成、编辑、证据核验、问题处理、审查、签发、导出、安全、非功能、正式部署和交接保持完整。

UPLOAD-STORAGE-NATIVE-02完整168项原生检查通过，无失败、错误或跳过，构建2分24秒；contracts36/data1/identity26/persistence105。[当前完整制品回执](../../.implementation-work/IC-03-JAVA-20261005-UPLOAD-STORAGE-NATIVE-02/receipt.json)摘要e951db014692289ff2bbc124c729e36c4c361db246cfd197a1f58beaa189c2ac，inputs_unchanged=true。企业控制遍历全部146张当前业务表，原数据包解析198项；实际对象写入九项2.191秒、来源七项0.326秒、完整分页14项3.881秒、依赖四项2.401秒。应用制品SHA-256为e272d55b10ab13accbbf15fb0b7d73e31c25204d0861c148b0ff781879c50021，当前进程PID12398、部署UUID7af0e237-6f72-441d-b5bb-7ee2a9b71a6d，实际启动48.287秒、登录HTTP 200；当前31项账户、六项原操作及两组DOM通过。完整上传业务服务及全部客户验收仍未完成。

UPLOAD-STORAGE-GREEN-03十项实际MySQL检查通过，4.511秒，无失败、错误或跳过，inputs_unchanged=true；[实际数据库回执](../../.implementation-work/IC-05-UPLOAD-STORAGE-20261005-UPLOAD-STORAGE-GREEN-03/receipt.json)摘要af9fd27cba3f28fa2ea5cb5f6d4f456d6268b8e89c40d0a19e9b0966a791e4a2。核查三个表完整字段/类型、十一组外键、全部启用CHECK、连续范围、互斥来源、缺少执行身份、完整五字段对象、实际原件版本/大小/摘要、缺少/额外字段及JSON类型拒绝、六项实际迁移checksum和九类客户计数全零。原生SHOW CREATE TABLE使用MySQL XML输出、ElementTree和SQLGlot处理，再以实际数据库表达式验证，不编写SQL解析器。metadata定义依据[MySQL CHECK_CONSTRAINTS](https://dev.mysql.com/doc/refman/8.4/en/information-schema-check-constraints-table.html)，本文只引用其当前约束条件字段；完整客户许可和上传成功保持未验收。

UPLOAD-STORAGE-MIGRATION-01已执行六项追加迁移，累计198项，146张企业业务表和17张平台表。目标MySQL8.4.3、medical_local及UUID5ecfb746-be18-11f1-95e4-d822203bc633保持当前身份；[实际迁移回执](../../.implementation-work/IC-03-UPLOAD-MIGRATION-20261005-UPLOAD-STORAGE-MIGRATION-01/receipt.json)摘要a7cadf3f98a4669f97b16c95fae30cd8f2187ca222e4633ff00bb6826387dc2e。新原生备份SHA-256为7ec82af221d18fcff33a2d6ba9972593b18540045986b2838aadeb58b8f5a26f，备份/日志/回执权限0600；原备份与日志保存于.implementation-work/runtime/backup-20261005-before-upload-storage/，目录0700。该备份未证明完整恢复或RTO/RPO。UPLOAD-STORAGE-BUILD-01原生解析198项并打包通过，6.179秒；UPLOAD-STORAGE-COVERAGE-01十五项完整字段、企业主键、复合外键、类型和Schema覆盖通过，3.682秒。最终当前制品已通过UPLOAD-STORAGE-NATIVE-02构建和当前运行核验；单独数据包的原始范围保持完整。

UPLOAD-STORAGE-RED-01六项实际MySQL检查均因缺少共用上传结构/关联或迁移失败，errors=0、skipped=0，0.742秒，inputs_unchanged=true；[原始回执](../../.implementation-work/IC-05-UPLOAD-STORAGE-20261005-UPLOAD-STORAGE-RED-01/receipt.json)的SHA-256为2864fb33e8307831653cc9895cb5dfa7be5764afb85d4750f0f9f9f540207c15。当前026提供六项追加迁移、三张新表、十一组完整外键、互斥来源身份、固定总量/8 MiB连续范围、完整对象JSON及不可变许可绑定；原001—025保持完整。原生打包、覆盖检查及真实备份/迁移/重读继续运行，尚未将该结构登记为业务通过。

2026-10-05 BACK-UPLOAD-STORAGE-01实施原上传许可的固定身份和共用分片持久化。检索原002上传批次/分片、009 Artifact许可、005完整依赖、025固定清单、UploadRequest/Grant/Completion、IN-13—15和当前原件写入恢复组件。Given：来源上传与DocumentBody/Agent许可使用各自真实身份；When：保留原许可、主体、项目、依赖、预期版本以及逐片范围；Then：完整复合关联和当前数据库约束必须存在，同一上传不能混用来源批次和Artifact许可，分片不能改变原总量或许可。现有主记录和索引保持唯一，业务服务、真实客户成功及完整IC-05继续IN_PROGRESS。

OBJECT-WRITE-RUNTIME-01当前运行：JavaPID8948、UID501、部署UUID9221ac53-e6bd-4bef-bfac-7fc5c49fd67f，制品SHA-256 558d4e5da6c23a979b57a42af4e602d51c66537fb2be0716cdbedbf23eb87fe9，启动57.872秒，实际/auth/login HTTP 200；原PID6766已核验终止。六项原操作HTTP/数据库10.804秒通过，回执645b11572f8dec86eefce62c272dc5c77b1aefdab2b0439e1a7ac0ec9b158252；两组真实登录DOM通过，回执e4836dfc9aac45f7b6278f92b915f2ec8f94f17a493b2a1b293822b666acd0eb。当前完整168项原生、原操作和登录三份回执的300/243/92项输入与8/1/1项产物已经逐项重读，SHA全部相等，实际输出PASS。检查使用当前本机账户和D0原件，没有新增客户企业、批准、上传许可或Artifact。MySQL、Redis及对象服务保持原配置，完整客户业务及IC-05尚未验收，以下集合保持原始输入范围。

| 当前对象写入制品的运行证据 | 原始回执 |
| --- | --- |
| 原操作六项 | [实际HTTP与数据库检查](../../.implementation-work/IC-05-OPERATIONS-20261005-OBJECT-WRITE-RUNTIME-01/receipt.json) |
| 登录两组 | [实际DOM及连接恢复](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-OBJECT-WRITE-RUNTIME-01/receipt.json) |

OBJECT-WRITE-BUILD-01完整168项原生通过，无失败、错误或跳过，构建2分18秒；contracts36/data1/identity26/persistence105，新对象写入九项2.348秒，独立依赖四项2.041秒、来源七项0.475秒、派生版本五项1.958秒、内容回执五项0.510秒、实际分页14项3.871秒。当前完整构建包括最终MIME、长度及私有目录权限核验，回执SHA-256 d7809705ef4bec178640153948cf62746e02dc6132374333e92f2b42f4fb4056，inputs_unchanged=true；[当前原生回执](../../.implementation-work/IC-03-JAVA-20261005-OBJECT-WRITE-BUILD-01/receipt.json)固定全部源码和制品。新制品运行检查继续取得证据，以下集合保留各自原始输入范围。

OBJECT-WRITE-RED-01取得一项缺少写入组件的行为断言失败，errors=0、skipped=0；回执SHA-256 9d1d393dbeaf6c0a672475dbae3f9735cf645e230f4b5b44a22e9495f7212f4f。最终独立GREEN-02九项真实S3检查通过，2.467秒，回执9699cc46c107b92e3b6122874662b58dfeed94d502beecc219bce5ca836aaddf；原GREEN-01七项保留原始输入范围。检查使用两份实际D0原件及当前私有对象部署，核查完整字节、固定版本、AES256、完整metadata、重复及异内容覆盖拒绝、两份不同原件并发仅产生一个版本、明确恢复及重新建立客户端后读取、快照后原文件变化、实际技术文件权限撤销后恢复、摘要/大小/身份/类型冲突和私有文件/链接/容量拒绝。原技术版本在记录实际引用之后按独立本次prefix及原versionId清理；客户资料、原D0固定对象和批准保持完整，没有新增客户业务。完整构建继续取得最终参数/目录检查及当前制品证据，完整IC-05尚未验收。

| 当前对象写入实现 | 文件与identifier |
| --- | --- |
| 完整私有快照、条件保存、原固定版本恢复及字节核验 | business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/S3ObjectWriter.java；WriteIntent、writeVerified、recoverVerified |
| 实际对象及并发检查 | business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/S3ObjectWriterTest.java；scripts/check_object_writer.py |
| 设计依据及完整业务接口 | backend-design/接口补充设计.md §2.1；BX-03/04、KN-02、IN-13—15、IA-08 |

| 对象写入原始证据 | 回执 |
| --- | --- |
| Red | [缺少对象写入能力的断言](../../.implementation-work/IC-05-OBJECT-WRITE-20261005-OBJECT-WRITE-RED-01/receipt.json) |
| 最终独立检查 | [九项实际私有对象检查](../../.implementation-work/IC-05-OBJECT-WRITE-20261005-OBJECT-WRITE-GREEN-02/receipt.json) |

2026-10-05 BACK-OBJECT-WRITE-01实施受限上传完成所需的私有对象写入及原对象恢复。读取原S3ObjectReader、当前SeaweedFS部署与两份D0公开原件、BX-03/04、IN-13—15、UploadRequest和009上传许可结构。Given：完整内容已由领域上传服务保存为私有文件，并固定写入身份、对象键、摘要、大小及类型；When：向当前私有对象服务保存内容或核对原写入；Then：重复同名写入拒绝，原版本、完整字节和摘要一致以后才能产生ObjectRef，每次实际I/O重新准入；未知响应通过明确恢复查询处理，不能自动再次写入。技术D0检查不创建客户企业或上传许可，完整分片、授权元数据、登记写入及IC-05继续IN_PROGRESS。

DEPENDENCY-ACCESS-RUNTIME-01当前运行核查：新JavaPID6766、UID501、部署UUID893de6eb-079e-4f4b-a367-521332e6df7c，制品SHA-256 a733d2884f08a32dcc0a62f11f8970cafa52ad29bddd82dffea699993410bcd0，启动56.062秒，实际/auth/login HTTP 200；原PID3730已核验终止。六项原操作HTTP/数据库检查10.218秒通过，回执ffb3fed8e71c30726baa1fd7ea3125937b2cf9f06e3e25dc3af79dff2f4983f7；两组真实登录DOM通过，回执823632831242405bf782c85e43a687d19b2d50fa7747caa720981fc805912382。当前制品包含159项原生检查所对应的独立依赖及共同来源实现，数据库、Redis与对象配置保持完整，没有迁移、客户写入或模型请求。完整客户业务及IC-05继续未验收，以下集合分别保留原始输入范围。

| 当前运行证据 | 原始回执 |
| --- | --- |
| 原操作六项 | [HTTP与数据库检查](../../.implementation-work/IC-05-OPERATIONS-20261005-DEPENDENCY-ACCESS-RUNTIME-01/receipt.json) |
| 实际登录两组 | [DOM及连接恢复检查](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-DEPENDENCY-ACCESS-RUNTIME-01/receipt.json) |

DEPENDENCY-ACCESS-BUILD-01完整159项原生检查通过，无失败、错误或跳过，构建2分52秒；contracts36、data1、identity26、persistence96。新独立依赖四项1.876秒，原来源七项0.442秒、派生版本五项2.823秒、内容回执五项0.700秒、实际分页14项5.648秒。当前原生回执SHA-256 5cac721dfb26b9e701f8673435f57ef054df371d67ca9d4c8b417a7bba9e708a，输入保持原字节；[原生构建回执](../../.implementation-work/IC-03-JAVA-20261005-DEPENDENCY-ACCESS-BUILD-01/receipt.json)固定本次完整源码和制品。新制品运行检查继续取得实际证据，完整业务仍未验收。

2026-10-05 BACK-DEPENDENCY-ACCESS-AUTHORITY-01继续受限上传及制品登记所需的独立依赖核验。当前读取原005/025结构、ArtifactRegistry、DependencyLineage、来源及36类版本授权、接口补充设计§2.4与本记录。Given：上传请求固定已有dependency_access_set_id；When：在创建Artifact之前核验该清单；Then：原主体、实际版本、完整清单及全部上游来源和领域授权必须同时通过，缺少当前企业事务立即拒绝。独立查询使用原八项主体字段，关闭缓存，保留当前企业拦截；DependencySourceAuthority供此路径和ArtifactSourceAuthority共同使用，来源集合、当前权利、可达关系和实际上游Artifact要求保持完整。没有新增客户实体、批准或上传许可，完整IC-05保持IN_PROGRESS。

DEPENDENCY-ACCESS-RED-01两项行为断言失败，errors=0、skipped=0，输入保持原字节；回执SHA-256 c6243fb5730174d9d4078955463a7609172d67e155c794fcb38d95f2a5cda674。GREEN-01四项真实只读MySQL、八字段构造映射、实际Mapper企业拦截、独立及共同服务准入与固定参数检查通过，5.978秒；回执SHA-256 14d132b4ba3baaca475c331bff4a891e9e6b13ef4de0cbc0d279a869fc7f9f9d。最终接口说明补充以后，当前完整原生构建及运行检查使用全新执行身份。合法清单成功、完整来源授权、共享、受限上传及登记写入继续按照真实客户验收要求实施；以下集合保留各自输入范围。

| 本次实现 | 位置 |
| --- | --- |
| 独立清单主体与固定读取要求 | business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DependencyAccessProtocol.java |
| 独立清单核验 | business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DependencyAccessAuthority.java |
| 完整来源与派生版本共同核验 | business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DependencySourceAuthority.java；ArtifactSourceAuthority.java |
| 当前企业主体查询 | business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/DependencyAccessMapper.java；src/main/resources/medical-mappers/DependencyAccessMapper.xml |
| 实际检查 | business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/DependencyAccessAuthorityTest.java；VersionResourceAuthorityTest.java；scripts/check_dependency_access_authority.py |

| 独立依赖检查 | 实际结果及原始证据 |
| --- | --- |
| Red | [两项缺少查询入口的断言](../../.implementation-work/IC-05-DEPENDENCY-ACCESS-20261005-DEPENDENCY-ACCESS-RED-01/receipt.json) |
| Green | [四项实际数据库及企业保护检查](../../.implementation-work/IC-05-DEPENDENCY-ACCESS-20261005-DEPENDENCY-ACCESS-GREEN-01/receipt.json) |

2026-10-05 BACK-ARTIFACT-CONTENT-READ-01实施ArtifactContentService、ContentReadAuthorization、ContentReadProtocol和ContentReadMapper。服务端完整消费固定原Artifact、ObjectRef、Schema和来源要求，每次实际I/O重新执行EnterpriseAccess与全部来源/版本授权，每页连续范围通过后重新准入，同事务保存header、range和审计并重读全部字段。完整结果仅能从内部读取方法产生，consume/consumeJson使用前重新准入；JSON消费还核查固定发布Schema。当前应用会话路径不接受MODEL_SEND，内部TaskToken/run/step/lease、公开HTTP实际发送、受限上传和客户成功验收继续实施，完整IC-05保持IN_PROGRESS。

Given：真实MySQL已经具有009/025完整结构，实际本机管理员没有企业成员或客户资源；When：查询并核对回执、范围和审计，尝试无企业上下文的主体操作，以及编码数据库实际UTC时间与U64最大值；Then：全部构造字段与真实列一致、保存SQL包含全部实际非生成字段、企业/主体保护拒绝无准入、UTC和U64保持精确。CONTENT-READ-RED-01两项行为失败、零错误/跳过，回执ab985a1dda3d5b202f9a4fcc84090bcea9caa5f9031a68cb819fcacdbe3b4dac。最终独立CONTENT-READ-GREEN-03五项通过，2.216秒，回执89b5238c8fe69b856bf8ad0a198903ef682cf4ba49916d1e2d1a8c363b5681ed。当前五项由完整CONTENT-READ-BUILD-01覆盖。上述检查没有保存客户业务回执，完整真实客户成功、并发撤权、公开发送与内部任务读取仍未验收。

CONTENT-READ-BUILD-01当前完整155项原生通过，无失败、错误或跳过，构建2分32秒；新增五项读取回执检查0.575秒，原派生五项2.132秒、来源七项0.579秒。回执ab3d994e9e60e6092a9c001e6875d337c508541f1a9672e0213d4d1b27ebe72e。当前JavaPID3730、部署UUID25823225-dd73-43e3-b8a4-5de7650ba107、制品807e50678e712792a65113e4f3272b437f47cd7519a2ca70f912c4e599b21e58，实际启动52.87秒、登录HTTP 200；实际进程、不可变0400 Jar及构建原件一致。六项原操作HTTP/数据库检查7.857秒通过，回执a3e62377ec08282a6c27d04a02a5acd41958708b45d2a022903ff34ecad86b3f；两组真实登录DOM检查通过，回执721f8438a436c599b15cee3d2cbbb07e72a8512c1995a2fbb207c3c91b2d24e6。以下较早集合均表示各自原始输入/制品范围，完整业务仍未验收。

文档更新以后，当前原生、原操作及登录DOM三份回执的292/235/92项输入与8/1/1项产物已使用.implementation-work/version-authority-evidence/check_bytes.py逐项重读，当前SHA全部相等，实际输出均为PASS。原始Red/Green只保留当次断言，不重复计为当前完整业务证据；字节检查只证明上述工程输入和制品，不代表客户业务验收。

2026-10-05 BACK-VERSION-RESOURCE-AUTHORITY-01继续派生版本资源核验。读取现有索引、SourceReadAuthority/ArtifactSourceAuthority/DependencyLineage/ResourceReadAuthority、完整核心版本登记及002—009/019实际实体关联、LLD-数据§4.2/4.5与接口补充设计§2.4。Given：当前平台账户没有客户企业和业务授权；When：通过正式Mapper查询各实际版本实体，并调用来源与派生版本准入；Then：36类查询使用原数据库字段及构造映射、关闭缓存和企业事务保护，版本UUID不能替代task_id/document_id/config_id等资源UUID，缺少准入直接拒绝。当前完整原生制品及适用运行检查继续核查，以下较早集合保留原始输入范围。

VersionResourceAuthority逐项查询全部可达版本的实际领域身份，要求唯一实体、同企业、相同版本项目及独立摘要一致。资源动作由服务端Domain确定，继续使用ResourceReadAuthority的真实归属及当前grant；版本使用项目与资源项目不同额外检查版本项目的project.read。私有会话消息须匹配服务端conversationId和当前所有者，REVIEW/HISTORY允许ACTIVE/ARCHIVED，FORMAL/MODEL_SEND要求ACTIVE。每个上游Artifact都需要领域消费者提供固定ReadRequirement，重新核查资源、Schema、对象及完整依赖；实际制品与服务端要求集合完全相等。ArtifactSourceAuthority已经接入全部派生准入，并要求来源Use/会话一致及原始来源关系完整。内部核验结果不能代替实际发送和读取回执，未定义的具体领域版本保持拒绝。

VERSION-AUTHORITY-RED-01两项实际行为断言失败，errors=0、skipped=0，真实数据库前置通过，回执8ad60e84ba6fb81ef693782d7e4eccf05d146eeb834c70e73bf5cd79fd7c708f。原GREEN-01两项通过、2.779秒，回执f7cddddf121ba5d7ae08bd5cf1312c04a843eb3f23ed2d525191a0421ab1bd39，范围为该次34类查询/字段映射和事务拒绝；当前扩展为36类及五项直接检查，最终结果由VERSION-AUTHORITY-BUILD-01登记。没有创建客户企业、版本、制品或业务许可；合法成功、当前撤权、跨项目共享和两企业隔离继续NOT_RUN。

SOURCE-AUTHORITY-BUILD-02当前完整145项原生检查通过，无失败、错误或跳过，构建2分35秒；新增七项来源检查0.592秒，回执6fd50d0ccf6f6d38ed60db804b72a896e508647c809a76d55c933e3d5cce047e。当前制品498ba86f368cb8f809c3b45db43eb299c20dfdec39030dd931ec63e5b6b01f3d已在本地启动，PID97158、部署UUID8fc6248d-94f9-444a-82b4-2c7ea5242308、启动28.166秒、登录HTTP 200；实际进程及不可变Jar字节已核查。六项原操作运行检查4.727秒通过，回执a5fbe891c69b4e7a643f1dbd8dc19dc58f92177cda6f7a988e77126b1311edd3；登录连接与账户校验两组实际DOM检查通过，回执89120e5e66ff902145e7856da9eaa125a9d19c36687c4e1ae3f0df776c3a2a64。全部原生输入和产物当前摘要一致；较早25/30集合仅表示原始代码范围。本次没有改变Vue、模型或数据库结构，也没有客户记录写入；完整业务继续未验收。

2026-10-05 BACK-SOURCE-READ-AUTHORITY-01继续来源读取和完整关系核验。核查PRD§8.1/8.3、LLD-数据§4.5、原SourceVersion/RightsRevision、scope_member、approval_decision、source_use_gate、project_source_binding，以及009已经建立的source_session_binding与conversation复合外键。Given：实际平台账户没有企业来源或授权；When：读取固定来源、权利、范围成员、会话和全部上游关系；Then：查询使用当前企业事务与关闭缓存，缺少记录拒绝，原Schema完整核查所有者、用途、操作、环境、区域、提供方和授权附件。历史读取不以资料停用替代当前读取授权，正式使用仍须固定核准和就绪门槛；会话附件必须保存为项目资料并核准才能正式使用。实现及适用原生检查继续执行，较早138/25/30集合表示原始源码范围。完整业务验收与IC-05保持未完成。

SourceReadAuthority通过当前source.read和目标project.read读取原来源实体，固定rights_revision并以主库时间检查APPROVED、用途、期限、允许操作、环境、区域、提供方及原分类。SourceRightsSchema使用封闭内部Schema、严格JSON和RFC8785核查完整不可变权利载荷及版本登记摘要；当前status另行控制立即停止。原始授权附件仅保存完整ObjectRef，人工授权和实际附件核验仍由核准服务完成。REVIEW可以检查待核准资料；HISTORY固定原范围和解析并保留停用状态；FORMAL/MODEL_SEND检查当前核准、处理、索引数量、适用及项目状态。SESSION核查实际绑定、ACTIVE会话和所有者，正式任务要求项目资料。全部原core及001—025保持原字节，没有客户记录写入。

DependencyLineage使用JGraphT 1.5.2处理原数据库的全部可达关系、完整图循环及成员一致性，容量由服务端消费者显式提供。ArtifactRegistry实际调用完整关系核验；ArtifactSourceAuthority每次重新调用当前登记核验，并要求固定来源集合、用途和项目一致，逐项核查原始来源及成员权利的实际可达归属。结果仅供后续领域版本准入和回执使用，不能直接发送内容。没有批准的生产容量、客户权利记录或合法来源运行证据；对应成功、循环、完整依赖及撤权业务检查保持NOT_RUN。

SOURCE-AUTHORITY-RED-01三项断言失败，errors=0、skipped=0，原始回执3ee24067daa56c5e9ebb2a86a688d390fbed968068ed4ac883b49a3f33a9501a。SOURCE-AUTHORITY-GREEN-01七项通过，2.432秒，回执ad820a04c64d3c73e680f73f7c3a72cdbcaa64c54bd25bcfd010b9234afc87ca：实际六类MySQL查询/全部构造字段顺序、关闭缓存、无企业事务拒绝、封闭权利Schema、错误JSON和无效来源/容量参数均核查。最终当前构建使用SOURCE-AUTHORITY-BUILD-02；独立Green保留原始输入范围，不能代表合法客户成功。

RESOURCE-REGISTRY-FINAL-01完整25组工程回归及30份CURRENT_EVIDENCE_BYTES核查全部通过。起止2026-10-04T20:59:00.931790+00:00至2026-10-04T21:07:33.584449+00:00，evidence.json为8c50a915be8b586c35ad14be953df9d5be16c515805930287fb4e8dc4463210e，verified-evidence.json为37e63b7ce6dcb6da9d7c0fc92d4ac813ae882e2b1ffa1db3f04721da603427ef。当前138项原生及全部输入/产物、JavaPID92494和对象PID62380身份相符；模型原28项/实际wheel28项、对象恢复/监听四份补充保持当前适用范围。开发记录、关联说明、报告、命令与续接文件同30份回执输入的绝对路径交集为空；环境、索引、计划、接口和源码在回归后保持原字节。完整业务通过零项，IC-05继续IN_PROGRESS。

RESOURCE-REGISTRY-BUILD-01完整138项原生检查通过，无失败、错误或跳过，构建2分20秒；回执6c3fd2769156594bf37fa9604b18778d3142f3850129f0a62a502d867f58eb91。四项资源授权4.399秒、三项登记读取0.171秒包含在完整构建中。新JavaPID92494、部署UUID82d4266f-73d5-44a8-9e62-b15a981cf0bb、制品e97700afaa765311df917297f01ee3e0c2e1654621fc1466612fc92886a16882，启动25.176秒、实际登录HTTP 200，部署身份已经核查。当前25组工程回归采用RESOURCE-REGISTRY-FINAL-01，实际结果读取原始终态；更早集合各自保持原始输入范围。完整客户业务继续未验收。

BACK-RESOURCE-READ-AUTHORITY-01已实施具体资源授权及Artifact登记读取前置。requireResource根据grant本身匹配主体、动作、企业/项目/资源、身份方式、用途、Schema及有效期；ResourceReadAuthority通过真实resourceOwner和已核验TenantScope核查实际归属，再读取当前有效授权。ArtifactRegistry.requireRegistration分别核验主资源及artifact.read、固定领域Schema、原制品发布清单、对象五字段、版本登记和完整依赖；DependencyManifestSchema使用封闭五字段成熟Schema、严格JSON及RFC8785核查完整成员、顺序、重复版本、数量和原摘要。登记结果不允许传输内容，完整来源权利及读取回执继续实施。

RESOURCE-AUTHORITY-RED-02取得三项中的两项缺少读取判定/当前查询的断言失败，errors=0、skipped=0；真实十四类资源查询通过。RESOURCE-REGISTRY-RED-01取得新增两项缺少登记查询/完整清单Schema的断言失败，原四项资源检查通过。最终RESOURCE-REGISTRY-GREEN-02七项通过，当前资源四项4.350秒、登记三项0.403秒，回执0e001632155a6f24a55a1a61a53635e4b0314d3b9cf9802b56c72a3931e2296d。实际只读MySQL查询、record构造参数/数据库字段顺序、关闭缓存、主库有效期、十四类无授权拒绝、企业事务/主体准入、固定Schema和严格JSON均核查。没有创建客户记录；合法授权成功、完整传递来源及撤权仍未验收。当前源码/接口输入已改变，PUBLISHED-SCHEMA及更早集合保持原始输入范围；完整原生构建和受影响运行检查继续取得当前证据。

PUBLISHED-SCHEMA-FINAL-01完整工程回归25组通过，起止2026-10-04T20:17:01.207615+00:00至2026-10-04T20:25:19.643321+00:00；evidence.json SHA-256为2798ceb372a1e1802579afee3a883997a3d9ae4334b40126fdde2e80a54c9745。30份CURRENT_EVIDENCE_BYTES核查通过，verified-evidence.json为a95b96f1d365c7bc0de09bd0e8ba5cfc4c4040512b5d18bc0cd28f6b0f20a905；包含当前131项原生、原模型及实际wheel各28项、对象恢复及监听补充证据。JavaPID88142、对象PID62380和全部输入/产物匹配。主记录、关联说明、报告、命令和续接文件与30份回执输入的绝对路径交集为空；环境、索引、计划、接口及代码保持检查时原字节。完整客户业务通过零项，IC-05及全范围验收继续实施，下方集合分别保留原始输入范围。

PUBLISHED-SCHEMA-BUILD-01完整131项原生检查通过，无失败、错误或跳过，构建2分19秒；回执65b4d3ebdf0e3920e958e12f0d2ecebcefc2ae2e633ccc004fa5fd9de29e09ba，新增六项直接调用检查1.199秒。新JavaPID88142、部署UUIDff5e71e4-509f-47f0-8c56-7dc3212da663、制品96a21070ce705fbef32065100e32d751ed98119bacc14b714dcb9554b7da4a21，启动25.969秒、登录HTTP 200。对象PID62380与MySQL192项迁移保持原身份。受影响25组回归使用PUBLISHED-SCHEMA-FINAL-01，实际结果读取该集合终态；下方ARTIFACT-REGISTRY和其他集合保留对应原始输入范围，完整业务继续未验收。

2026-10-05 BACK-ARTIFACT-SCHEMA-01继续主资源登记所需的发布Schema核验。读取SchemaCatalogue、原发布清单五份完整文档及216个定义、实际D0数据、原类型/规范编码检查和接口补充设计§2.4。Given：完整内容绑定固定schema_ref与schema_document_sha256；When：核查当前发布身份并解释完整UTF8 JSON；Then：引用与摘要必须同时匹配当前发布目录，未登记引用不得加载网络，正文必须通过原完整Schema及RFC8785数值/Unicode核验。业务授权、来源依赖、对象完整SHA和实际读取回执仍由Artifact服务核查，技术发布目录不授予客户许可。当前主记录保持唯一，完整IC-05继续IN_PROGRESS。

PUBLISHED-SCHEMA-RED-01取得五项缺少固定发布身份和完整内容入口的行为断言失败；GREEN-02六项通过，3.470秒，回执5567bd9603c673cc65a2ab61d7aac699a92618c825f887abc837c98e237aa690。全部221个当前引用及原文件摘要、实际完整D0数据/12条原定位、实际来源摘要标量、未登记引用/别名/摘要冲突、原Schema拒绝、重复字段、多根JSON、其他编码和空正文均核查。检查不创建客户实体或批准。最终测试采用直接调用，完整原生构建和新制品回归继续取得证据；下方ARTIFACT-REGISTRY集合保留原始输入范围。

ARTIFACT-REGISTRY-FINAL-01完整工程回归25组通过，起止2026-10-04T19:52:32.223819+00:00至2026-10-04T20:00:52.684135+00:00；evidence.json SHA-256为e66459b5e2ecd78527c946b60329fdf23e11ea984006304f45b8525d9c2e6480。30份CURRENT_EVIDENCE_BYTES核查通过，verified-evidence.json为1bfad549cb8963a8a2eb318e567321ece6c14f22d21f18b34f48b15c2974c1a0；包含当前Java125项、模型和实际wheel各28项及对象恢复/监听回执，全部输入、产物和Java/对象部署身份一致。新增登记五项实际MySQL检查1.258秒，48字段、五组完整外键及26关联字段、七项实际约束、六项已应用迁移和九类客户计数核查通过。当前JavaPID85377、部署UUIDf804a6f4-d83d-4d67-94d9-3b1633663101、制品2faddcfab5db549fadf98cba2abee2a695931f5d8f390ef47688092a306c48eb，启动27.038秒，登录HTTP 200。MySQL累计192项迁移、143企业表及17平台表；完整业务服务与客户成功继续实施，IC-05保持IN_PROGRESS。下方集合分别保留原始日期及输入范围。

2026-10-05 BACK-ARTIFACT-REGISTRY-01继续实施Artifact登记及读取追踪。核查005、009、原version_registry、来源权利、BX-37/IA-01/08、完整DependencyAccessSet及当前企业准入；现有artifact对象五字段与原文摘要保持完整。追加025用于保存主资源与固定Schema、完整依赖原清单，以及绑定原制品/资源/依赖的实际字节读取范围。原001—024保持原字节，没有替代客户资料或批准。Given：当前真实MySQL已有原对象、依赖及读取回执结构；When：检查登记所需固定字段、完整复合外键和范围约束；Then：缺少关联必须使检查失败，完整迁移在原生解析、备份及目标身份核查之后执行。实际业务服务、客户授权、上传、来源完整核验及公开/内部读取继续实施，IC-05保持IN_PROGRESS。

PERSISTENCE-FINAL-01完整工程回归24组通过，起止2026-10-04T19:11:29.715128+00:00至2026-10-04T19:19:49.203962+00:00；evidence.json SHA-256为03bfee5341aa307bb89124cbda8b1256573af3ed78b7d9e30be32e113af8d36b。30份CURRENT_EVIDENCE_BYTES核查通过，verified-evidence.json为7d2d809ae050b0dabf3154040c46af070c8a59462dc543d7337218eb450c1b37；包含新Java125项、最终存储引用七项、原模型及实际wheel各28项、原对象恢复/监听，全部输入、产物与双部署身份相符。原生125项包含14项实际分页/完整消费，无需将较早分页回执作为当前补充。秘密原值未出现在本轮构建、最终存储引用及程序日志中；DeepSeek本地check通过。主记录、关联说明、报告、命令和续接记录与全部回执输入的绝对路径交集为空；环境、索引、计划、接口和代码在本次回归后保持原字节。完整客户业务及IC-05继续未验收。

2026-10-05 BACK-OBJECT-PERSISTENCE-01已实施Artifact对象表示。读取现有005 artifact完整DDL、原ObjectRef、S3ObjectReader、实际MySQL及两份D0固定对象，按原数值JSON及同值标量定义核查精确转换、数据库全部五项比较和实际固定版本原件读取。StoredObjectReferences使用成熟Jackson严格处理重复字段、多根正文、封闭结构和精确BigInteger；readStored在S3读取前核查原JSON与全部标量。既有Connector/J 9.7.0作为原生检查的直接测试依赖；001—024与公开Schema保持原字节。PERSISTENCE-BUILD-01完整125项通过，构建2分09秒；PERSISTENCE-FINAL-01最终七项真实MySQL与S3检查通过，2.864秒。新JavaPID81465、部署UUIDccc4d22e-50c6-48e1-845b-9e02cb8467b0、制品SHA-256为e2a47a3d7f939543902f053805e6d84fab59158ef10ef22dfb6abfd4b1315af3，启动26.932秒，登录HTTP 200。没有创建客户企业、制品或业务回执；完整共享回归使用PERSISTENCE-FINAL-01，结果读取原始终态回执。企业授权、来源完整依赖、资源/Schema、读取回执及全范围业务继续实施，完整IC-05保持IN_PROGRESS。其他集合分别保留原始输入范围。

2026-10-05 ASSEMBLY-FINAL-01当前工程核查：24组回归及30份CURRENT_EVIDENCE_BYTES全部通过，新JavaPID77952和对象PID62380在执行前后保持真实身份，118项原生、14项实际分页及完整消费、原模型28项及实际wheel安装后28项、对象恢复和监听回执的输入与产物符合当前字节。回归起止2026-10-04T18:38:11.653582+00:00至2026-10-04T18:46:42.052912+00:00；evidence.json SHA-256为fb2f73f55a2eac185e8d075dafaf5c611674e48870c2ef194bc8f95390a95dbf，verified-evidence.json为1d3104bafa08e033e621d96d0de1df75f2267650fcd4495034e840079165c161。所有执行handle已取得终态；环境、索引、计划、接口和源码在最终检查以后保持原字节，结果说明及续接文件不属于这些检查输入。全业务、安全、非功能、部署和交接继续实施，业务通过零项；下方集合保持各自原始范围。

2026-10-05 BACK-OBJECT-ASSEMBLY-01实施完整内容消费。当前核查ObjectContentPaging、原件分页测试、接口补充设计§2.4/3、LLD-数据§4.5及PRD§8.9。ObjectContentAssembly和ObjectContentPaging.readComplete在读取前核查完整内容容量，消费原固定对象的连续实际范围，逐页核查编码、类型、范围、分片及完整摘要，完整内容核验通过后才返回字节供后续解释组件使用；返回前再次核查原准入。当前ASSEMBLY-BUILD-01完整118项通过，ASSEMBLY-FINAL-01独立14项通过；新JavaPID77952、部署UUID8fb67320-b21e-498f-a8ea-709231ad1fd1、制品SHA-256为2527f8541ea100e61a494876c0158128972374a1f72c972da7efdf00faf67158，实际启动30.162秒，实际登录入口HTTP 200。检查使用两份真实D0原件，没有增加客户记录。企业/项目/来源许可、Artifact资源和Schema登记、读取回执及公开/内部业务入口继续实施。完整共享工程回归使用ASSEMBLY-FINAL-01，实际结果读取原始回执；下方集合保持各自原始输入范围，完整IC-05保持IN_PROGRESS。

2026-10-05 PAGING-FINAL-02当前工程结果：24组回归及30份CURRENT_EVIDENCE_BYTES核查通过，实际JavaPID72778与对象PID62380在执行前后相同，原生110项、实际分页六项、模型与实际wheel各28项、对象恢复及监听回执的当前字节均匹配。集合起止2026-10-04T17:58:19.048410+00:00至2026-10-04T18:06:27.978625+00:00；evidence.json SHA-256为21961410c2fac104e4147c5583c2d290c4253ace773a14ff41f0855c4bc69bf3，verified-evidence.json为db38d74f03a25a77634c0afcb3a2e547c9c43b1f13cf66a168d0a79e56d7b828。全部检查已取得终态，没有待续读的运行handle。完整业务通过零项；全42项工单及业务、安全、非功能和交接继续实施。以下较早集合分别表示原始输入和运行范围。

2026-10-05 PAGING-BUILD-01完整110项原生检查通过，实际构建2分12秒；PAGING-FINAL-01六项分页独立复验通过，全部输入保持当前字节。新Java PID72778、部署UUIDe59bb1b2-1689-4794-be0e-1a840a687f8b、SHA-256为169b7016e499919e150e5b0cf5fb47653aa6319720e753856b2d52ea98eb17a8，实际启动25.448秒。对象服务仍为原PID62380及部署身份；当前完整共享回归采用新的PAGING-FINAL-02执行身份，结果以原始终态回执为准。完整企业Artifact业务及全范围验收仍未完成。

2026-10-05 BACK-OBJECT-PAGING-01继续实施BX-37/IA-01需要的实际字节分页和受保护游标。读取接口补充设计§1/2/6、Coverage/ContentChunk、当前S3ObjectReader及原生测试，采用原固定对象版本、BASE64原字节和成熟Nimbus JWE。ObjectContentPagingTest使用已保存D0原件、当前对象部署及实际AES密钥；技术目录权限撤销作用于实际文件，不创建客户企业或业务读取回执。PAGING-RED-01三项缺少分页组件的断言失败；PAGING-GREEN-03六项实际分页、完整重组、重复范围、不同实际对象/准入摘要/容量拒绝、目录权限撤销、认证标签、实际密钥更换、实际时间到期和封闭游标检查通过。当前源码和设计输入已经改变，较早集合保持原始执行范围，完整原生构建及受影响回归继续取得新证据。完整IC-05保持IN_PROGRESS。

2026-10-05 OBJECT-READ-FINAL-01当前核查：24组工程回归及29份当前输入、产物与双部署身份核查通过，Java为PID68313，对象服务为PID62380。回归起止为2026-10-04T16:58:23.115201+00:00至2026-10-04T17:06:57.764069+00:00；evidence.json摘要1e358213e9c3f00163e80b6484821c4c2ec854444e67b90cdaf5960deb6f823d，verified-evidence.json摘要489cec973febb696b12635040e9a5e97a9c09a3bb6f6c93912878fe54b976391。新Java104项及实际原件读取、原模型28项与wheel安装后28项、对象恢复与监听回执全部通过当前字节核查。完整业务、非功能、安全和交接继续未验收，业务通过零项。

2026-10-05 BACK-OBJECT-READ-01已完成原ObjectRef五字段构造校验及S3ObjectReader/VerifiedObject内部读取能力。OBJECT-READ-GREEN-02完整原生104项通过，其中ObjectRef四项用时1.879秒、真实S3读取四项用时2.087秒；两份原件的原版本、多个实际范围请求、完整SHA、元数据冲突拒绝、边界和清理通过。新制品SHA-256为21c6650a14db4c85689d443af50fd1a5cdc45a98645ceec0220fc4bdd2618774，新进程PID68313；共享回归继续取得新执行证据。企业许可、DependencyAccessSet、coverage、读取回执及公开入口继续实施，完整IC-05保持IN_PROGRESS。下方较早集合仅保留其原始执行范围。

2026-10-05 BACK-OBJECT-STORAGE-01原始核查：FULL-REGRESSION-20261005-OBJECT-STORAGE-FINAL-01全部24组通过，29份输入、实际产物及Java/对象服务身份核查通过。对象存储四项检查用时1.418秒，原版本恢复及十个本机监听的补充回执同样通过对应字节核查；该集合Java96项、模型28项及实际安装后28项均符合原始输入。完整IC-05、57项业务/17项非功能、安全和交接保持原验收要求，业务通过零项。下方较早集合保留原日期及适用输入。

2026-10-04 BACK-BUDGET-HISTORY-01当前关联：历史读取完整核验原批准定义、version_registry及原依据摘要，查询不会修改账户、费用、原操作或Outbox。BUDGET-HISTORY-FINAL-01预算五项和原操作六项实际HTTP/数据库检查通过，全部23组及26份当前字节核查通过；本地客户记录保持零，合法预算原请求与历史读取仍待客户输入验证。主记录为IMP-07；下方集合保持原始范围。

2026-10-04 FRONT-BUDGET-MANAGEMENT-01关联：正式预算创建及追加批准消费BX-31/OP-01，保留原主体、企业、目标、原版本、请求键与正文摘要；当前操作完成后重新查询真实状态及获授权预算。BUDGET-FRONT-FINAL-01的23组及26份当前字节核查通过，原操作六项HTTP/数据库和正式Vue开发十组/生产七组通过。客户原响应、未知恢复、并发及撤权继续实际验收；主记录为IMP-07，其他回执保持原始输入范围。

2026-10-04 BACK-BUDGET-MANAGEMENT-01关联：BD-02/04将预算变更、完整批准定义、原请求摘要、原响应、审计和Outbox共同保存并重读，BX-31/OP-01继续读取真实原操作身份；预算原响应重放由IMP-07重新核查当前许可、账户及批准版本。当前Java为BUDGET-02，受影响原操作及共享身份复验使用BUDGET-FINAL-01；本记录其他执行身份保持原始输入范围。实际客户原请求成功、响应丢失、并发及撤权仍未验收。

BUDGET-FINAL-01当前原操作六项真实HTTP/数据库检查及23组共享回归通过，26份回执的当前输入、实际产物和部署身份相符；原始结果为该集合evidence.json及verified-evidence.json。客户FOUND/NOT_FOUND/EXPIRED、批准事务原响应、并发及撤权仍需获授权实际业务验证。

当前PROJECT-WRITE-FINAL-01的22组回归及25份当前回执核查已经实际通过，原操作六项HTTP/数据库检查保持当前输入。Vue使用原请求键及完整参数摘要进行查询与核查，开发八组及生产六组无企业账户检查通过。实际结果读取FULL-REGRESSION-20261004-PROJECT-WRITE-FINAL-01/evidence.json及verified-evidence.json；合法FOUND/NOT_FOUND/EXPIRED、恢复重读、撤权和并发仍未验收。

2026-10-04 FRONT-PROJECT-WRITE-01关联：IMP-06的operations.ts、project-write.ts和project-write.vue消费BX-31/OP-01，保存原请求技术元数据，明确区分NOT_FOUND、EXPIRED和原操作终态。查询不发送业务动作，完成确认重新读取实际操作及当前获授权项目。PROJECT-WRITE-GREEN-01开发八组及生产六组实际无企业检查通过，完善后的前端复验与共享回归使用PROJECT-WRITE-GREEN-02及PROJECT-WRITE-FINAL-01，结果以实际回执为准。原Java及数据库输入本次未修改；OPERATIONS-FINAL-03保留对应原始前端范围，合法原操作成功及并发仍未验收。

2026-10-05 BACK-RESOURCE-READ-AUTHORITY-01继续具体资源读取授权。读取原PermissionAuthority/Schema/Mapper、EnterpriseAccess、十四类resourceOwner、005/009/025结构及LLD-数据§4.5。Given：实际平台账户没有企业成员和业务授权；When：请求十四类具体资源并查询当前主体/动作的有效授权；Then：全部请求拒绝，授权查询保持当前数据库时间和关闭缓存，委派范围不能产生读取许可。ResourceReadAuthorityTest使用真实只读MySQL和当前platform_local_account，实际用户UUID用于无对应资源的越权请求，没有创建客户企业或批准。合法授权成功、两企业隔离及实际撤权仍等待已请求的真实业务输入；完整IC-05保持IN_PROGRESS。

## 任务身份

| 字段 | 当前内容 |
| --- | --- |
| task_id | IMP-05 |
| 需求与验收 | FR-03—05、FR-09；NFR-02/06；IC-05；EDGE-01/03/17 |
| task_source | [实施任务计划](../../实施任务计划.md) §4.2/5 |
| 状态快照 | IN_PROGRESS；2026-10-05；完整 IC-05 尚未验收 |
| 代码与环境 | 根仓库 main，尚无提交；源码及运行身份通过原始检查回执绑定；本地独立账户、MySQL、Redis及SeaweedFS 4.48 |
| 文档有效性 | CURRENT；last_verified=2026-10-05；UPLOAD-STORAGE-NATIVE-02完整168项原生通过，当前JavaPID12398；十项实际结构、十五项覆盖、31项账户、六项原操作及两组DOM通过，六份回执输入/产物SHA核查相等。此前集合保留原始范围，完整业务流程继续未验收 |
| 对象写入检索 | BACK-OBJECT-WRITE-01、S3ObjectWriter、WriteIntent、writeVerified、recoverVerified、S3ObjectWriterTest、check_object_writer、IC-05-OBJECT-WRITE |
| 独立依赖检索 | BACK-DEPENDENCY-ACCESS-AUTHORITY-01、DependencyAccessAuthority、DependencyAccessProtocol、DependencyAccessMapper、DependencySourceAuthority、DependencyAccessAuthorityTest、check_dependency_access_authority、IC-05-DEPENDENCY-ACCESS |
| 派生资源检索 | BACK-VERSION-RESOURCE-AUTHORITY-01、VersionResourceAuthority、VersionResourceProtocol、VersionResourceMapper、ReadContext、OwnerRow、Domain、VersionResourceAuthorityTest、check_version_resource_authority |
| 内容回执检索 | BACK-ARTIFACT-CONTENT-READ-01、ArtifactContentService、ContentReadAuthorization、ContentReadProtocol、ContentReadMapper、ContentReadReceiptTest、check_content_read_receipts、CompleteRead、consume、consumeJson、IC-05-CONTENT-READ |
| 资源读取检索 | BACK-RESOURCE-READ-AUTHORITY-01、ResourceReadAuthority、PermissionAuthority.requireResource、PermissionMapper.resourceGrants、ArtifactRegistry.requireRegistration、ArtifactRegistryProtocol、ArtifactRegistryMapper、DependencyManifestSchema、ResourceReadAuthorityTest、ArtifactRegistryReadTest、check_resource_read_authority |
| 登记检索 | BACK-ARTIFACT-REGISTRY-01、025-artifact-content-registry、artifact_content_binding、dependency_access_manifest、content_read_range、IC-05-ARTIFACT-REGISTRY、check_artifact_registry、schema_document_sha256、dependency_manifest_sha256 |
| 发布核验检索 | BACK-ARTIFACT-SCHEMA-01、SchemaCatalogue.requirePublished/bindPublished、PublishedSchema、PublishedSchemaReferencesTest、IC-05-PUBLISHED-SCHEMA、check_published_schema_references |
| 存储表示检索 | BACK-OBJECT-PERSISTENCE-01、StoredObjectReferences、StoredObjectReferencesTest、readStored、IC-05-OBJECT-PERSISTENCE、check_stored_object_references、size_bytes、BigInteger、Connector/J |
| 检索字段 | BACK-OBJECT-ASSEMBLY-01、ObjectContentAssembly、readComplete、CONTEXT_LIMIT、CONTENT_MISMATCH、BACK-OBJECT-PAGING-01、ObjectContentPaging、ObjectContentPagingTest、IC-05-OBJECT-PAGING、check_object_paging、Nimbus JWE、BASE64、Coverage、BACK-OBJECT-READ-01、S3ObjectReader、VerifiedObject、ObjectRef、StoredObjectReferenceTest、S3ObjectReaderTest、DependencyAccessSet、content_read_receipt、BX-37、BACK-OPERATION-READ-01、BX-31、OP-01、operation_record、OperationController/Service/Schema/Mapper、dedup_expires_at、dedup_policy_revision、EnterpriseAccess、TenantScope、operation.read、OPERATION_READING、check_operations |

## 检索与输入依据

检索开发索引、IMP-03/04/06/37、LLD-后端 §2.4/4、接口补充设计 §1/2.3、OperationLookup/OperationLookupResult/Operation 和实际 Project/Permission/Role/Invitation 操作保存代码。当前唯一主记录维护企业原操作和对象基础。企业操作保存原主体、操作定义、请求键、状态与资源引用，查询入口已经实施。已有001—024迁移保持原字节。

BACK-OBJECT-STORAGE-01续接核查scripts/local_object_storage.py、scripts/check_object_storage.py、两份实际存储检查、依赖锁文件和原始回执；读取HLD §3.1/6、LLD-数据不可变对象要求、LLD-后端外部服务表及现有ObjectRef/ArtifactRef。官方固定制品、实际native命令和成熟客户端共同确定本地实现；原Java、Vue、DDL、模型源码保持原字节。涉及配置正文、原件、导出和部署的后续任务均引用当前唯一记录。

## 当前功能与实现

SchemaCatalogue维护当前发布清单中五份完整文档和216个定义的固定身份。requirePublished核对原schema_ref及原文件SHA-256；不同版本或未登记引用立即按SCHEMA_UNSUPPORTED拒绝，不加载客户端URL。bindPublished在身份核查后使用严格UTF8解码器和成熟Jackson解释完整JSON，拒绝重复字段、多根正文及空内容；原Schema、精确整数和RFC8785数值/Unicode要求继续核验。返回原发布身份及独立规范字节。调用方仍须核验ObjectRef原完整字节、当前企业/资源/依赖许可及实际读取回执，不能使用JSON规范摘要代替原对象字节摘要。

BACK-ARTIFACT-REGISTRY-01追加025的六项迁移。dependency_access_manifest保存固定五字段完整JSON、成员数量及原清单摘要，关联原dependency_access_set；artifact_content_binding固定主资源、原制品、发布Schema及同一完整依赖版本；content_read_range固定原读取回执、制品/资源/Schema/依赖及实际[start_byte,end_byte)。完整复合关联保留企业与project_key。主资源归属、发布Schema、完整依赖成员与传递来源、当前授权和实际对象均须业务服务核查，数据库结构不能授予权限或证明事实支持。检查只读实际数据库结构及约束表达式，没有建立客户企业、制品、依赖或读取回执。

BACK-OBJECT-PERSISTENCE-01消费现有005的五项JSON/标量约束。Given：两份D0真实原件的固定ObjectRef及当前MySQL；When：使用成熟Jackson编码数值型size_bytes、经真实MySQL读取JSON并比较全部标量、随后readStored读取原固定版本；Then：全部原字段、精确大小、实际字节与完整SHA一致。结构、重复字段、多根JSON、大小类型、字段冲突和非法Unicode必须立即拒绝；MySQL实际无符号最大值必须保持精确，不能匹配原件大小。检查只执行参数化SELECT，不创建客户业务记录。

ObjectContentAssembly固定原ObjectRef及RFC8785原对象和选择范围摘要。完整内容容量由调用方显式提供；构造时比较U64原字节大小，超过容量按CONTEXT_LIMIT拒绝，零容量或零内容立即拒绝。accept核查BASE64规范编码、连续实际范围、类型、分片和完整摘要及实际范围摘要。范围遗漏、重复、顺序变化、类型/摘要/范围冲突或字节损坏立即拒绝，校验失败后的实例不继续接受内容。只有最后范围覆盖原全部字节并通过完整SHA才能调用content，返回独立字节数组，后续修改返回数组不改变原核验内容。readComplete使用原分页、游标和同一准入摘要，全部页重组后再次核查准入；没有部分内容返回路径。

Given：真实S3保存两份D0原件的固定版本及实际元数据；When：正式完整内容方法按已配置容量逐页消费；Then：返回字节与原件完全一致。容量不足在对象准入之前拒绝，读取中撤销实际目录权限立即拒绝；实际分页被重复、遗漏、改变顺序或篡改元数据/字节时无法取得完整内容。检查没有建立客户企业、项目、读取回执或模型外发批准。

ObjectContentPaging使用成熟Nimbus 9.37.4 JWE dir/A256GCM及现有S3ObjectReader。每次分页从已核验固定版本获得实际字节，按BASE64传输，chunk_sha256使用实际当前页字节，full_sha256使用完整核验结果。范围摘要的原文编码由接口补充设计§2.4维护；complete只表示没有后续分页，消费者仍需重组并核验完整内容。准入回调每次提供当前摘要，读取器在实际发送、内容读取和返回前共同核验，准入变化按SCOPE_STALE拒绝。游标包含完整固定对象身份摘要、范围、容量、原时间和原有效期，使用AES-GCM认证加密，拒绝未知Header/字段、边界、不同对象/准入/容量及已到期游标，不重新选择对象或延长期限。解密/解析错误直接抛出，由业务入口按CURSOR_INVALID映射；原S3客户端异常继续保持真实原因。内部Page保存实际字节位置，完整Artifact消费者还需登记真实读取回执及其范围。

构造时要求独立AES-256密钥、版本UUID、完整有效期和分片容量。最大分片仍为8MiB技术保护上限，游标最大8192字符；测试有效期和容量仅用于本地工程检查。当前类没有作为公开Artifact入口启用，也没有创建业务秘密或业务批准。企业/项目/来源完整许可、资源与schema_ref绑定、回执及受限上传继续实施。原迁移001—024与共享Schema保持原字节。

BACK-OBJECT-READ-01继续实施C03的成熟Java对象读取。当前读取必须使用原ObjectRef五字段及实际不可变版本，核查大小、media_type、SHA和当前授权后才能登记读取回执。已核查ObjectRef原Schema、两份实际存储产物、artifact/content_read_receipt/dependency_access_set完整DDL及BX-37，不根据技术存储身份推断客户资源许可。字段准入的BDD使用真实保存原件及其实际ObjectRef，并在原字段中明确改变单项无效表示，要求构造时立即拒绝；不建立替代客户业务数据。Java原生及下游证据随着源码变化重新执行，OBJECT-STORAGE-FINAL-01保留对应原始输入范围。

WireValues.hash/boundedString与ObjectRef使用原小写64位摘要和Unicode code point长度要求；size_bytes仍按原U64十进制字符串核查，共享Schema允许的空字符串不改变。物理读取另核查有效对象键、固定版本、类型、正数大小及完整配置的容量上限。OBJECT-READ-RED-01的原生输出取得四项中的两项字段拒绝断言失败，OBJECT-REFERENCE-GREEN-01四项全部通过，完整Maven构建通过；原始输出分别保存在对应执行目录。

S3ObjectReader使用AWS SDK for Java 2.55.11、Apache HttpClient、Commons IO 2.22.0及Resilience4j 2.4.0。明确指定服务凭据、端点、区域和path-style，不从系统或环境取得Proxy配置；本地模式只接受127.0.0.1的明确HTTP端口，PRIVATE_TLS要求HTTPS并由环境配置核查批准地址及私有网络。完整读取按最多8MiB分段请求，SDK每段总时限60秒、attempt45秒、连接5秒和socket30秒，额外读取attempt最多两次。SDK每次真正发送前及各读取阶段调用原Admission，不能使用较早准入替代；业务Admission仍需接入EnterpriseAccess及完整依赖许可。

读取响应核验原version、media_type、Content-Length及完整Content-Range，使用成熟BoundedInputStream限制实际读取量；大小/类型/版本/完整摘要冲突明确拒绝。完整原字节存入0700目录中的0600私有文件，完整SHA通过以后才能取得VerifiedObject；实际FileChannel使用DELETE_ON_CLOSE，失败或关闭共同清理。VerifiedObject保留原Admission，调用方不能在取得内容时改用另一项准入。对外不提供对象地址、凭据或私有文件路径。传输及5xx失败采用五次连续失败打开30秒的成熟CircuitBreaker，内容/许可错误不计入连接故障，错误继续抛出，不提供替代文件。

S3ObjectReaderTest使用已经登记的两份实际D0原件及真实对象服务，技术检查的容量上限由现存原件字节数计算，分段大小采用首份原件实际大小的一半，验证两份原件的多个真实S3范围请求及最后一个不足完整分段的范围。这些技术参数不作为生产容量批准；读取器要求调用方完整配置对象和分段容量，分段不能超过8MiB保护上限。原件和服务身份核查只能证明私有存储前置，不能作为企业授权或正式读取回执。客户正向读取、两企业隔离、撤权、取消、并发、故障熔断及生产参数仍按完整业务要求验收。

BACK-OBJECT-STORAGE-01实施本地私有对象存储前置。依据HLD §3.1/6、LLD-数据的不可变对象要求和LLD-后端外部服务表，使用固定SeaweedFS 4.48官方macOS制品及成熟S3客户端。服务数据、配置、缓存和临时文件均保存在项目目录，全部网络监听限定127.0.0.1。技术服务身份和加密秘密使用CSPRNG生成，不建立客户企业、项目或业务批准。逐项运行原件版本、SHA-256、服务身份、匿名拒绝、服务端加密和持久重读检查；原件只采用D0已登记的本地公开资料。

决定LOCAL-OBJECT-STORAGE-01的来源类型为INFERRED，状态为CONFIRMED，权限依据为用户授权本地运行及完整编码。具体产品选择属于本地实施选择；固定版本官方制品摘要为425ef61d2ee773d5d5175003f38bd6cc891070ae6319370354aaf5378c45d5b3。本地使用受限配置中的SSE-S3 KEK，加密秘密不保存于Filer或普通回执，服务凭据不进入普通回执。生产KMS及秘密服务仍需独立实施与验证。生产区域、复制、保留策略和恢复阈值保持原批准条件。此项前置不能替代对象业务授权、审计、上传会话、IA-01/08或IN-13—15验收。

Given：固定官方服务和真实S3客户端能够运行，当前技术桶尚未建立版本及加密保护；When：检查技术桶配置及既有公开原件的受控保存；Then：缺少必需保护必须明确失败。Green需要实际桶版本、加密、固定对象版本重读、内容摘要和匿名拒绝证据。检查来源和产物分别绑定实际字节，不以SDK导入或启动退出码证明业务完成。

BACK-OPERATION-READ-01 实施 BX-31 `POST /api/operation-lookups` 和 OP-01 `GET /api/operations/{id}`。BX-31 使用原操作定义编号和原请求键，企业及主体从真实当前会话取得，查询不创建业务操作。OP-01 读取原操作 UUID，原主体可读取安全元数据；其他主体必须具有当前企业范围 `operation.read`、`OPERATION_READING` 明确授权。当前企业成员、认证绑定、上下文和授权水位继续由 EnterpriseAccess 同事务核查。完整业务响应、原文、Prompt 和秘密不在安全元数据中返回，资源内容仍通过各自业务权限读取。

| 文件路径 | identifier | 当前职责及关联流程 |
| --- | --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ArtifactContentService.java | readComplete、CompleteRead、consume、consumeJson | 连接完整实际字节消费、每阶段重新准入、逐页回执保存与重读；领域调用方和公开/内部入口继续接入 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ContentReadAuthorization.java及ContentReadProtocol.java | digest、ReadIntent、HeaderRow、RangeRow、AuditRow | 完整固定准入摘要、精确U64/UTC、闭合业务读取输入与原数据库完整字段 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/mapper/ContentReadMapper.java及src/main/resources/medical-mappers/ContentReadMapper.xml | databaseTime、header、range、audit、saveHeader、saveRange、saveAudit | 009/025原结构完整保存与当前主体重读，企业条件由原拦截器提供 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/ContentReadReceiptTest.java及scripts/check_content_read_receipts.py | IC-05-CONTENT-READ | 实际只读MySQL全部查询/字段/构造参数、原生SQL与实际列比较、主体/企业拒绝及数据库精确UTC/U64；客户成功未执行 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/VersionResourceAuthority.java、VersionResourceProtocol.java、mapper/VersionResourceMapper.java及VersionResourceMapper.xml | require、ReadContext、Domain、OwnerRow、owners | 36类具体领域版本与实际资源关联、当前动作/范围、会话及上游Artifact固定读取要求；由ArtifactSourceAuthority调用 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/VersionResourceAuthorityTest.java及scripts/check_version_resource_authority.py | IC-05-VERSION-AUTHORITY | 当前只读MySQL全部查询/构造字段、原生SQL解析确认资源UUID区别、实际版本/来源服务事务拒绝及会话上下文参数；合法客户成功未运行 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceReadAuthority.java、SourceReadProtocol.java、mapper/SourceReadMapper.java及SourceReadMapper.xml | require、ReadRequirement、source、rights、selection、gates、conversation、parents | 当前来源许可、固定原版本/解析/范围、当前权利/生命周期/会话及正式使用门槛 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/SourceRightsSchema.java及src/main/resources/source-rights.schema.json | verify、require、manifestSha256 | 封闭完整权利载荷、原始授权附件ObjectRef、RFC8785摘要、分类/用途/环境/区域/提供方及当前期限/状态 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/DependencyLineage.java、ArtifactSourceAuthority.java及pom.xml | VerifiedLineage、Limits、VerifiedSources | JGraphT 1.5.2检查完整可达关系及循环；每次登记与来源准入共同重查，完整成员/来源/权利归属一致 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/SourceReadAuthorityTest.java及scripts/check_source_read_authority.py | IC-05-SOURCE-AUTHORITY | 当前只读MySQL六类查询、全部字段/类型/顺序、事务拒绝、封闭Schema、错误JSON和无效使用/容量参数；合法客户成功保持NOT_RUN |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ResourceReadAuthority.java、PermissionAuthority.java、TenantScope.java、mapper/PermissionMapper.java及PermissionMapper.xml | require、requireResource、resourceGrants | 已核验企业事务中的实际资源归属、当前主体/动作/用途/有效期及明确范围授权 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ArtifactRegistry.java、ArtifactRegistryProtocol.java、DependencyManifestSchema.java、mapper/ArtifactRegistryMapper.java及ArtifactRegistryMapper.xml | requireRegistration、verify、Registration | 固定主资源/制品/Schema/版本和完整依赖集合；结果仅供后续完整内容准入 |
| business/medical-persistence/src/main/resources/dependency-access-manifest.schema.json | dependency-access-manifest:1.0.0 | 025内部五字段清单及封闭成员；固定成熟Schema及原文件发布摘要 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/ResourceReadAuthorityTest.java、ArtifactRegistryReadTest.java及scripts/check_resource_read_authority.py | IC-05-RESOURCE-AUTHORITY | 当前只读MySQL、实际平台身份、十四类拒绝、原生字段及构造映射、事务/主体准入和严格JSON检查 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/SchemaCatalogue.java | requirePublished、bindPublished、PublishedSchema | 按当前发布目录的固定引用及原文件摘要核验身份；完整UTF8 JSON使用原Schema及RFC8785规则，不加载客户端URL |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/PublishedSchemaReferencesTest.java、scripts/check_published_schema_references.py | IC-05-PUBLISHED-SCHEMA | 全部221个固定引用、实际完整D0数据及定位/摘要标量；未知引用、别名、摘要冲突、正文/编码拒绝 |
| business/medical-data/src/main/resources/db/migration/025-artifact-content-registry.sql | dependency_access_manifest、artifact_content_binding、content_read_range | 六项追加迁移，固定主资源/Schema、完整依赖原清单和实际范围；同企业/project_key及原制品/回执完整关联 |
| tests/test_artifact_registry_runtime.py、scripts/check_artifact_registry.py | IC-05-ARTIFACT-REGISTRY | 实际MySQL身份、字段/外键/约束/迁移checksum和客户计数只读核查；原生备份后应用追加迁移 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/StoredObjectReferences.java、S3ObjectReader.java | encode、decode、readStored | 精确数值型存储JSON与原U64接口之间的转换、封闭五字段与标量一致性；通过原Admission消费固定版本内容 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/StoredObjectReferencesTest.java、scripts/check_stored_object_references.py | IC-05-OBJECT-PERSISTENCE | 两份真实原件及只读MySQL五字段比较、固定版本字节读取、冲突拒绝、成熟JSON/Unicode处理和精确U64检查 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ObjectContentAssembly.java、ObjectContentPaging.java | accept、content、readComplete | 完整字节容量、连续实际范围、分片和完整摘要、同一准入及完整结果保护；供Artifact正文、专业检查和内部内容消费者使用 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ObjectContentPaging.java | read、Page、Position | 固定版本实际字节分页、范围摘要、当前准入及JWE游标；由Artifact业务消费者继续接入 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/ObjectContentPagingTest.java、scripts/check_object_paging.py | IC-05-OBJECT-PAGING | 真实原件完整重组、原范围重读、加密认证、实际期限/密钥/目录权限及封闭游标检查 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/InterfaceProtocol.java、WireValues.java | ObjectRef、hash、boundedString | 原五字段构造校验；共享Schema的U64、类型、字符数量及小写摘要规则 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/S3ObjectReader.java | S3ObjectReader、Admission、VerifiedObject | 固定版本、逐次准入、限定分段和时限、实际响应/完整SHA核验及私有文件清理；企业许可由业务消费者继续接入 |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/StoredObjectReferenceTest.java、business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/S3ObjectReaderTest.java | ObjectRef与实际S3原件检查 | 真实保存原件和ObjectRef、原Schema往返、无效单项表示拒绝、多个实际范围、完整字节、冲突拒绝及关闭清理 |
| scripts/local_object_storage.py | install、prepare、start、protect、verify_identity、client、stop | 官方制品与许可摘要、原生服务身份、私密配置、固定端口及Socket、实际桶配置重读；终止操作只允许已核验的当前服务PID |
| tests/object-storage-requirements.lock | Boto3 1.43.108、botocore 1.43.108及其完整七项依赖 | 固定安装版本；正常pip安装与pip check；实际安装文件进入原始证据 |
| tests/test_object_storage_runtime.py | PrivateObjectStorageChecks | 四项真实私有桶、不可变版本、公开原件完整/分段读取、匿名拒绝及native日志秘密检查；不创建客户记录 |
| tests/test_object_storage_recovery.py | NativeStorageRecoveryChecks | 同一技术服务及制品的新部署读取原两份对象版本；没有重新上传原件 |
| scripts/check_object_storage.py | IC-05-OBJECT-STORAGE、IC-05-OBJECT-STORAGE-RECOVERY | 使用成熟capture_check入口；绑定实际客户端、源码、D0原件、制品、部署及产物字节，秘密文件不进入普通回执 |
| scripts/check_full_regression.py、scripts/inspect_current_evidence.py | REGISTERED_LOCAL_REGRESSION、CURRENT_EVIDENCE_BYTES | 25组包含对象存储及完整登记结构；核查Java/对象服务实际部署身份和每项输入/产物 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/OperationController.java | BX-31、OP-01 | Schema/context/路径/正文校验及公共错误响应；真实会话入口 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/OperationService.java | lookup、read | 同一企业事务内的原请求查询、当前管理许可及安全元数据 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/OperationSchema.java | OperationLookup/Result/Operation | 使用原共享 Schema，严格重复字段及多根正文拒绝，结果空值语义核查 |
| business/medical-persistence/src/main/resources/medical-mappers/OperationMapper.xml | lookup、operation、readers | 只读原操作和当前有效许可；不读取 response_ref；实际企业及主体拦截 |
| business/medical-data/src/main/resources/db/migration/024-operation-retention.sql | 186-operation-retention | 保存可空期限/批准依据、完整企业外键及成对约束 |
| tests/check_operation_sql_columns.py | READ_ONLY_SAFE_METADATA | 原生 MyBatis/JSQLParser 查询及成熟 SQLGlot 原始字段核对；构造映射直接匹配真实 record 类型 |
| tests/InspectAlterMigrations.java、tests/test_migration_bundle.py | native_alters、MigrationBundleChecks | JSQLParser 读取完整 ALTER，原字段通过 CREATE、原约束通过独立 ALTER 原生编码交给 SQLGlot；核查成员外键、唯一约束、认证分支及原操作期限表达式 |

## 决策与约束

S3ObjectWriter使用完整已知大小的私有快照和原PutObject条件写入；[S3官方条件写入说明](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html)定义If-None-Match的同名对象判定与条件冲突。当前SeaweedFS4.48实际重复、异内容和并发检查确认同名请求只保存一个版本，工程证据不替代客户幂等操作、预算或上传完成事务。组件只有内部显式调用，没有公开HTTP入口，也没有授予客户业务权限；原对象恢复查询不执行第二次Put。完整领域服务必须从实际已保存许可生成固定WriteIntent，并在每次I/O及完成事务重新核验原主体、资源、依赖、用途、版本、期限和当前运行/租约。

ResourceReadAuthority及ArtifactRegistry属于内容读取的内部前置。ResourceReadAuthority在当前已核验企业事务中读取真实实体归属和grant；用途与动作由服务端领域消费者确定，不能从客户端声明推定授权。ReadRequirement同时固定领域Schema及原制品Schema发布清单摘要；DependencyManifestSchema固定025内部清单的独立Schema发布摘要，公开catalogue保持原字节。来源为INFERRED，依据为LLD-后端§2.2、LLD-数据§4.5和接口补充设计§2.4；当前授权允许实施这些内部处理，客户许可仍须使用实际业务登记。

Registration不表示完整内容准入。后续内容服务必须核查全部传递来源、当前来源read、权利版本/状态/用途/期限、分类及会话范围，结合每次实际传输重新准入，再保存读取回执。当前没有公开Artifact内容入口调用Registration，不能把登记查询或无授权拒绝作为合法客户成功验收。七项检查的空业务集合来自真实数据库，没有建立测试客户或批准。

固定发布身份来自当前运行制品中的原Schema清单，完整文档和定义使用同一文件SHA；客户端提供的引用只能作为登记目录的精确查找键。具体资源内容类型仍须依据OpenAPI的x-detail-schema和领域登记单独核验，requirePublished不授予任意资源采用任意已发布Schema的权限。完整JSON解释在取得原全部对象字节并核验其SHA后执行；规范JSON摘要不能覆盖原对象摘要。客户资料外发及业务准入仍受当前授权、用途、分类和完整依赖限制。

完整消费沿用内部BASE64字节分页及原RFC8785范围编码，公开ContentChunk、ObjectRef和既有迁移保持原字段。字节容量必须由实际消费者配置；tokenizer与模型输入核算仍由IMP-07/14执行，不能从技术测试容量推断生产批准。内容解释只能使用完整核验字节，Schema和媒体格式由后续成熟组件处理。

Artifact持久化边界按005实际DDL保存数值型JSON size_bytes，并保留同值BIGINT UNSIGNED标量；接口继续使用原U64十进制字符串。StoredObjectReferences采用Jackson精确整数节点，在读取边界核查封闭五字段、全部标量和原U64表示。企业资源、固定Schema、完整依赖及字节读取回执结构已通过追加025保存；实际业务登记、授权和来源核验继续实施。001—025保持原字节，后续结构采用追加迁移。

Operation 保持已发布的四字段：operation_id、resource_id、status、status_url。BX-31 的 FOUND 必须包含 Operation，NOT_FOUND/EXPIRED 必须为 null。NOT_FOUND 不证明请求从未发送；EXPIRED 要求人工核对。期限必须来自保存的批准版本，不采用推断的默认天数；期限与依据均为空时原记录继续保留。原请求唯一身份不因过期查询删除。期限配置及生命周期写入由完整 IMP-10 继续实施。

## 验证与证据

| check_id | 实际断言与结果 | 原始证据 |
| --- | --- | --- |
| 当前内容服务原生构建 | CONTENT-READ-BUILD-01完整155项通过；新增五项0.575秒，完整构建2分32秒，无失败/错误/跳过 | [当前原生回执](../../.implementation-work/IC-03-JAVA-20261005-CONTENT-READ-BUILD-01/receipt.json)，ab3d994e9e60e6092a9c001e6875d337c508541f1a9672e0213d4d1b27ebe72e |
| 当前内容制品运行 | PID3730，实际账户及原操作HTTP/数据库六项通过，7.857秒；启动52.87秒、登录HTTP 200 | [当前运行回执](../../.implementation-work/IC-05-OPERATIONS-20261005-CONTENT-READ-RUNTIME-01/receipt.json)，a3e62377ec08282a6c27d04a02a5acd41958708b45d2a022903ff34ecad86b3f |
| 当前内容制品登录DOM | 实际离线错误/连接恢复后登录成功，密码确认校验与秘密输入清理两组通过；视觉未执行 | [当前DOM回执](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-CONTENT-READ-RUNTIME-01/receipt.json)，721f8438a436c599b15cee3d2cbbb07e72a8512c1995a2fbb207c3c91b2d24e6 |
| 内容回执原始Red | 两项行为断言失败，零错误/跳过；实际数据库前置通过 | [原始Red](../../.implementation-work/IC-05-CONTENT-READ-20261005-CONTENT-READ-RED-01/receipt.json)，ab985a1dda3d5b202f9a4fcc84090bcea9caa5f9031a68cb819fcacdbe3b4dac |
| 内容回执原始Green | 五项实际MySQL字段/完整SQL列、当前主体/企业保护及精确UTC/U64通过，2.216秒；客户成功未执行 | [原始Green](../../.implementation-work/IC-05-CONTENT-READ-20261005-CONTENT-READ-GREEN-03/receipt.json)，89b5238c8fe69b856bf8ad0a198903ef682cf4ba49916d1e2d1a8c363b5681ed |
| 派生版本原始构建 | VERSION-AUTHORITY-BUILD-01当次150项通过；36类查询/全部构造字段、实际版本与来源服务拒绝、成熟SQL解析核查不同资源UUID及会话上下文参数均通过 | [原始原生回执](../../.implementation-work/IC-03-JAVA-20261005-VERSION-AUTHORITY-BUILD-01/receipt.json)，1f044ba3510db1c27913099d92d907417e143f9995c2e53cc5278a3e70701346 |
| 派生版本原始运行 | 当次PID341，实际独立账户、HTTP/企业拒绝、封闭输入及数据库结构；六项4.623秒通过 | [原始运行回执](../../.implementation-work/IC-05-OPERATIONS-20261005-VERSION-AUTHORITY-RUNTIME-01/receipt.json)，5044eef10de393baaa80055085f9fedce8dfbfc569cc68c15a670e59c5f1f6eb |
| 派生版本原始登录DOM | 当次实际离线错误/连接恢复后登录成功，密码确认校验与秘密输入清理通过；没有视觉检查 | [原始DOM回执](../../.implementation-work/IC-04-AUTH-ERRORS-20261005-VERSION-AUTHORITY-RUNTIME-01/receipt.json)，86f25cec6419e5b28b067e79a23a850525c3ff5a6b124653648a758c5cc7071f |
| 派生版本原始Red | 两项行为断言失败，errors=0、skipped=0；实际数据库前置通过 | [Red回执](../../.implementation-work/IC-05-VERSION-AUTHORITY-20261005-VERSION-AUTHORITY-RED-01/receipt.json)，8ad60e84ba6fb81ef693782d7e4eccf05d146eeb834c70e73bf5cd79fd7c708f |
| 派生版本原始Green | 该次34类查询/构造字段及企业事务拒绝两项通过，2.779秒；保持该次原始输入范围 | [Green回执](../../.implementation-work/IC-05-VERSION-AUTHORITY-20261005-VERSION-AUTHORITY-GREEN-01/receipt.json)，f7cddddf121ba5d7ae08bd5cf1312c04a843eb3f23ed2d525191a0421ab1bd39 |
| RESOURCE-REGISTRY原始REGISTERED_LOCAL_REGRESSION | 原始25组通过；后续来源源码和制品已更新，保留原始适用范围 | [原始工程集合](../../.implementation-work/FULL-REGRESSION-20261005-RESOURCE-REGISTRY-FINAL-01/evidence.json)，8c50a915be8b586c35ad14be953df9d5be16c515805930287fb4e8dc4463210e |
| RESOURCE-REGISTRY原始CURRENT_EVIDENCE_BYTES | 原始25组及138项/模型/对象补充共30份通过；保留原始适用范围 | [原始字节核查](../../.implementation-work/FULL-REGRESSION-20261005-RESOURCE-REGISTRY-FINAL-01/verified-evidence.json)，37e63b7ce6dcb6da9d7c0fc92d4ac813ae882e2b1ffa1db3f04721da603427ef |
| IC-05-PUBLISHED-SCHEMA Red | 五项缺少固定发布身份或完整正文入口的行为断言失败，实际发布目录和D0前置通过 | [Red回执](../../.implementation-work/IC-05-PUBLISHED-SCHEMA-20261005-PUBLISHED-SCHEMA-RED-01/receipt.json)，2182ecdc2649a5402d950c5cb9873016bbd56c03056f0ae6bd60d761e8e62f03 |
| IC-05-PUBLISHED-SCHEMA Green | 六项通过，3.470秒；完整221引用/原文件摘要、实际D0及定位/摘要标量、未知引用/别名/摘要冲突和正文/编码拒绝 | [Green回执](../../.implementation-work/IC-05-PUBLISHED-SCHEMA-20261005-PUBLISHED-SCHEMA-GREEN-02/receipt.json)，5567bd9603c673cc65a2ab61d7aac699a92618c825f887abc837c98e237aa690，保留原始输入范围 |
| IC-03-JAVA当前构建 | 138项完整原生通过，无失败、错误或跳过；四项资源授权4.399秒、三项登记读取0.171秒，构建2分20秒 | [当前构建](../../.implementation-work/IC-03-JAVA-20261005-RESOURCE-REGISTRY-BUILD-01/receipt.json)，6c3fd2769156594bf37fa9604b18778d3142f3850129f0a62a502d867f58eb91 |
| IC-05-RESOURCE-AUTHORITY Red | 当前数据库前置和十四类查询通过，两个缺少具体读取判定/当前查询的断言失败，errors=0、skipped=0 | [Red回执](../../.implementation-work/IC-05-RESOURCE-AUTHORITY-20261005-RESOURCE-AUTHORITY-RED-02/receipt.json)，077996a744c3fc4ffa98d9ed30eb61f642d1d74891f6ef0b06c84103be3cd154 |
| IC-05-RESOURCE-REGISTRY Red | 新增两个缺少固定登记查询/完整Schema的断言失败，原四项资源检查通过；errors=0、skipped=0 | [Red回执](../../.implementation-work/IC-05-RESOURCE-AUTHORITY-20261005-RESOURCE-REGISTRY-RED-01/receipt.json)，c1307913b20978404284cf325faf50ab7d9c7da9bed819a7b5ee69c0a765c31f |
| IC-05-RESOURCE-REGISTRY Green | 七项通过；真实只读MySQL、当前账户、全部查询原生列/record映射、无授权拒绝、事务/主体和严格JSON核查；保留原始输入范围 | [Green回执](../../.implementation-work/IC-05-RESOURCE-AUTHORITY-20261005-RESOURCE-REGISTRY-GREEN-02/receipt.json)，0e001632155a6f24a55a1a61a53635e4b0314d3b9cf9802b56c72a3931e2296d |
| IC-05-ARTIFACT-REGISTRY Red | 真实MySQL前置通过，四项缺少登记结构的行为断言失败，errors=0、inputs_unchanged=true | [Red回执](../../.implementation-work/IC-05-ARTIFACT-REGISTRY-20261005-ARTIFACT-REGISTRY-RED-03/receipt.json)，479ed7507a1092ea37c4b7f37a1d7acfcba55863c7b882dea440d1fd33601d52 |
| IC-03-ARTIFACT-MIGRATION | 目标UUID及双制品核查、原生备份通过；六项追加迁移执行成功，累计192项；保留执行时原始输入范围 | [迁移回执](../../.implementation-work/IC-03-ARTIFACT-MIGRATION-20261005-ARTIFACT-REGISTRY-MIGRATE-01/receipt.json)，3e4c1ff6a0dbfbb38c602f82a7b3a3ec8301c58dff4b874de9f757d3d7a069ee |
| IC-03-JAVA | 完整125项原生检查通过，无失败、错误或跳过，构建2分16秒；包括实际存储引用七项及分页/完整消费14项 | [构建回执](../../.implementation-work/IC-03-JAVA-20261005-ARTIFACT-REGISTRY-BUILD-01/receipt.json)，fde111dea529a38764ccbea74ff5d902da863e29bba60e3d7f5a673b1dccfe03 |
| IC-05-ARTIFACT-REGISTRY Green | 最终五项检查通过，48字段、五组外键、七约束、六迁移checksum及九类客户计数全零 | [当前回执](../../.implementation-work/IC-05-ARTIFACT-REGISTRY-20261005-ARTIFACT-REGISTRY-FINAL-01/receipt.json)，1983e58bc4fcee7b51e855539bd4c448863d05307e5ca0b256020d4f11a78892 |
| REGISTERED_LOCAL_REGRESSION | 当前25组全部通过，原始输入、产物及双部署身份保持一致；完整业务验收NOT_RUN | [集合](../../.implementation-work/FULL-REGRESSION-20261005-ARTIFACT-REGISTRY-FINAL-01/evidence.json) |
| CURRENT_EVIDENCE_BYTES | 25组及五份补充共30份核查通过；主记录、关联说明、报告、命令与续接文件同回执输入的绝对路径交集为空 | [字节核查](../../.implementation-work/FULL-REGRESSION-20261005-ARTIFACT-REGISTRY-FINAL-01/verified-evidence.json) |

| 存储引用检查 | 实际结果及范围 | 原始证据 |
| --- | --- | --- |
| PERSISTENCE-RED-01 | 两项行为断言失败，真实MySQL与S3前置通过；failures=2、errors=0、skipped=0，exit_code=1、inputs_unchanged=true | [Red回执](../../.implementation-work/IC-05-OBJECT-PERSISTENCE-20261005-PERSISTENCE-RED-01/receipt.json)；SHA-256 4a95ab4e5dd88c5d512d8a8493afaf128bbac620bbc943870a8447eaea5ee9c6 |
| PERSISTENCE-GREEN-01 | 七项通过，2.584秒；该次原始输入范围保存在回执中 | [Green回执](../../.implementation-work/IC-05-OBJECT-PERSISTENCE-20261005-PERSISTENCE-GREEN-01/receipt.json)；SHA-256 ec03b9826181cf7b94e2b493f31ce82f2bdff48e872cc6524923f759700f2d3e |
| PERSISTENCE-BUILD-01 | 完整125项通过，构建2分09秒；共享30项、数据一项、身份26项、持久化68项；14项实际分页与完整消费、七项实际存储引用共同通过，无失败、错误或跳过 | [原生回执](../../.implementation-work/IC-03-JAVA-20261005-PERSISTENCE-BUILD-01/receipt.json)；SHA-256 e7c5149db41637e52481cf89cffaec662379d6d8c385692f0b273832380267d6 |
| PERSISTENCE-FINAL-01 | 最终七项真实MySQL与固定版本S3检查通过，2.864秒，全部输入保持原字节；成熟JsonGenerator生成重复字段的故障输入，未创建客户记录 | [最终回执](../../.implementation-work/IC-05-OBJECT-PERSISTENCE-20261005-PERSISTENCE-FINAL-01/receipt.json)；SHA-256 a7b9137528899abca0af05189f2e7d9f02a300ab08ac00ee54179decd2c4ead3 |
| PERSISTENCE-FINAL-01完整工程核查 | 24组回归与30份当前字节核查通过，全部输入/产物及双部署一致；业务通过零项 | [完整回归](../../.implementation-work/FULL-REGRESSION-20261005-PERSISTENCE-FINAL-01/evidence.json)、[当前核查](../../.implementation-work/FULL-REGRESSION-20261005-PERSISTENCE-FINAL-01/verified-evidence.json) |

| 完整内容消费检查 | 实际结果及范围 | 原始证据 |
| --- | --- | --- |
| ASSEMBLY-RED-01 | 原六项真实分页通过，两项完整消费行为断言失败；八项检查中failures=2、errors=0、skipped=0，exit_code=1、inputs_unchanged=true | [Red回执](../../.implementation-work/IC-05-OBJECT-PAGING-20261005-ASSEMBLY-RED-01/receipt.json)；SHA-256 5678b527d282b4bd54436b22855d99673147a5cf165e6ed1ebdcebd603c2793b |
| ASSEMBLY-GREEN-01 | 14项真实分页与完整消费检查通过，6.552秒；两份原件完整读取、部分内容拒绝、容量拒绝、实际权限撤销、遗漏/重复/顺序、元数据/范围/分片冲突、字节损坏后的完整SHA拒绝及返回数组保护 | [Green回执](../../.implementation-work/IC-05-OBJECT-PAGING-20261005-ASSEMBLY-GREEN-01/receipt.json)；SHA-256 b52ed478db6e30e985d93f5be25c08a037d3642d7fea039aa621b56f7690b525；当前设计说明和检查入口更新后使用新执行身份复验 |
| ASSEMBLY-BUILD-01 | 完整118项原生检查通过，构建2分22秒；共享30项、数据一项、身份26项、持久化61项全部无失败及跳过；真实分页和完整消费14项4.355秒 | [原生回执](../../.implementation-work/IC-03-JAVA-20261005-ASSEMBLY-BUILD-01/receipt.json)；SHA-256 6172097c884e3b7fdec9124dd115481c5ae1796975e4c2f92aa99a3831bc23ab |
| ASSEMBLY-FINAL-01 | 14项最终分页与完整消费独立复验通过，7.649秒；最终源码、接口说明、D0原件、固定对象refs和实际对象部署保持输入字节 | [最终分页回执](../../.implementation-work/IC-05-OBJECT-PAGING-20261005-ASSEMBLY-FINAL-01/receipt.json)；SHA-256 3b52ed94d188a37c8bc4d5726a8d380258c74726c31a24416cd55b402c04b230 |
| ASSEMBLY-FINAL-01完整工程核查 | 24组回归及30份当前字节核查通过；原生118项、实际14项、模型及实际wheel各28项、对象恢复/监听与当前输入、产物及双部署相符；业务通过零项 | [完整回归](../../.implementation-work/FULL-REGRESSION-20261005-ASSEMBLY-FINAL-01/evidence.json)、[当前核查](../../.implementation-work/FULL-REGRESSION-20261005-ASSEMBLY-FINAL-01/verified-evidence.json) |

| 内容分页检查 | 实际结果及范围 | 原始证据 |
| --- | --- | --- |
| PAGING-RED-01 | 真实服务与原件前置通过，三项缺少分页能力的断言失败；没有编译或依赖错误；exit_code=1、inputs_unchanged=true | [Red回执](../../.implementation-work/IC-05-OBJECT-PAGING-20261005-PAGING-RED-01/receipt.json)；SHA-256 7f69a2df7ce62ad40ae2238dc4afcddbd38407e20ecaa7ede91bb786d74b5295 |
| PAGING-BUILD-01 | 完整110项原生检查通过，其中六项真实分页检查3.493秒；原104项共享、数据、身份及持久化检查继续通过；完整构建2分12秒 | [原生回执](../../.implementation-work/IC-03-JAVA-20261005-PAGING-BUILD-01/receipt.json)；SHA-256 13d71448e1cab5915914ff23245658ee41998018de02e8a221ebdf7c3705fb37 |
| PAGING-FINAL-01 | 六项实际原件分页、完整字节重组、原范围重读、对象/准入/容量绑定、AES-GCM认证及真实密钥/时间/目录权限检查通过；无客户记录和业务读取回执 | [分页回执](../../.implementation-work/IC-05-OBJECT-PAGING-20261005-PAGING-FINAL-01/receipt.json)；SHA-256 1a24a0e9b948a80a9b4ac42e3a2bc4c0cfc9a4522f6c95de52a94b4a64188fd8 |
| PAGING-FINAL-02当前完整工程集合 | 24组回归及30份当前字节核查通过；实际代码、输入、产物及Java/对象双部署身份匹配；业务通过仍为零项 | [当前回归](../../.implementation-work/FULL-REGRESSION-20261005-PAGING-FINAL-02/evidence.json)、[当前字节核查](../../.implementation-work/FULL-REGRESSION-20261005-PAGING-FINAL-02/verified-evidence.json) |

BX-37、IA-01的企业授权、资源与Schema登记、完整依赖及业务读取回执仍需完成；当前分页检查限内部字节传输能力，其他执行集合保留原始输入范围。

| Java读取检查 | 实际结果及适用范围 | 原始证据 |
| --- | --- | --- |
| ObjectRef有效Red | 两项字段拒绝断言失败；原Schema/真实ObjectRef往返及U64大小拒绝通过，四项中两项失败；只引用原始输出 | [原生输出](../../.implementation-work/IC-03-JAVA-20261005-OBJECT-READ-RED-01/output.log) |
| ObjectRef原生Green | 四项字段及原Schema检查通过；完整构建100项通过，适用于当时的ObjectRef实现及测试输入 | [原始回执](../../.implementation-work/IC-03-JAVA-20261005-OBJECT-REFERENCE-GREEN-01/receipt.json)；SHA-256 95f4c528de31364bd1a3fb2f57335f858a1c3e12dec249e3ecdc0909fba58d05 |
| 当前Java读取原生Green | 完整104项通过；ObjectRef四项1.879秒、真实S3读取四项2.087秒，全部检查无跳过；完整构建2分04秒 | [当前回执](../../.implementation-work/IC-03-JAVA-20261005-OBJECT-READ-GREEN-02/receipt.json)；SHA-256 9bbc9969b88fa1986e8388dbaaed5e82c0e99f9ae9e8c5762b7af9da2e57f256 |
| 当前工程回归与字节核查 | 24组及29份回执通过；实际Java和对象服务身份相同，全部当前输入和产物匹配 | [当前回归](../../.implementation-work/FULL-REGRESSION-20261005-OBJECT-READ-FINAL-01/evidence.json)、[当前字节核查](../../.implementation-work/FULL-REGRESSION-20261005-OBJECT-READ-FINAL-01/verified-evidence.json) |

当前测试容量来自实际原件，原对象refs及公开原件字节在Java回执中绑定，当前对象部署及native制品共同绑定。秘密文件不进入普通回执；新Java制品的全部依赖随实际Jar摘要保存。该范围不证明正式Artifact业务、企业读取回执、故障熔断、性能或完整安全验收。

| 对象前置检查 | 实际结果及适用范围 | 原始证据 |
| --- | --- | --- |
| IC-05-OBJECT-STORAGE有效Red | 四项实际检查中缺少版本保护的两项失败，匿名拒绝及日志检查通过；exit_code=1、inputs_unchanged=true，1.026秒 | [Red回执](../../.implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RED-03/receipt.json)；SHA-256 acb77aff8058da7759dcbc50b69925a685390aa44a40ee15b7c627fc6a47fc73 |
| IC-05-OBJECT-STORAGE原部署Green | 四项真实配置及原件检查通过，1.305秒；对象版本/完整字节/SHA/类型/metadata/分段及匿名拒绝；适用于原部署71d57242-b438-4f3d-898f-dcdf254e421d | [Green回执](../../.implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-GREEN-01/receipt.json)；SHA-256 e528a2f1266c85625f41cf070e08dcf4b873d85a142f41b87098a476ca03384f；其objects.json保存实际原版本，当前部署变化后保持原范围 |
| IC-05-OBJECT-STORAGE-RECOVERY | 终止已核验PID61596后重新运行相同制品、技术身份和加密秘密；新PID62380按原版本重读两份原件通过，1.006秒；全部原字节/SHA及保护一致 | [恢复回执](../../.implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json)；SHA-256 4a6dff0a2e414ada56d7069ea6514b85fa06f80a4f7ab98eb9b36a36d54db6bd |
| IC-05-OBJECT-LISTENERS | 原生lsof读取当前PID62380的十个TCP监听，全部为127.0.0.1；原始输出和制品/部署输入绑定 | [监听回执](../../.implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json)；SHA-256 5df797d6cf9aeb52b5254a71e9af2a382f094b67b29c7057d6ca1a2fe096e381 |
| IC-05-OBJECT-STORAGE当前Green | 当前部署四项实际存储检查通过，1.418秒；两份D0原件保存为同一key的不同实际版本，第二次保存后逐个固定版本重读、SHA/类型/长度/metadata/分段及匿名拒绝通过 | [当前回执](../../.implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/receipt.json)；SHA-256 f1c32c3a36a86ff7a1958c7b61562d57e17d36ea3eef5201c05f12ed25730d08 |
| 当前统一回归与证据核查 | 24组检查通过，29份回执当前输入及实际产物通过；JavaPID56990和对象PID62380身份在执行前后相同；检查程序自身字节共同绑定 | [回归汇总](../../.implementation-work/FULL-REGRESSION-20261005-OBJECT-STORAGE-FINAL-01/evidence.json)、[当前核查](../../.implementation-work/FULL-REGRESSION-20261005-OBJECT-STORAGE-FINAL-01/verified-evidence.json)；摘要分别808a5373d6378e67040b4649575718538f5878a7cdb1e95317dd2330630acf0f、2af8ede7332f5b6310b6af2bee5eae0b12e969abef9d80e69f1e271c3053a481 |

当前对象部署UUID为45368b63-317e-4edc-906d-8e347b2cd774，技术服务UUID为11dab8e2-1a0d-4548-914b-79f96c297e8a，binary SHA-256为5816e3fbc608f9b0d787579f4f7b573643cff3f3ff7aac506611a8b5ce438485。官方archive及Apache-2.0许可证字节均已绑定，native制品权限0500。实际客户记录及正式业务证据没有增加；恢复检查不能代表NFR-02、RTO/RPO或完整安全验收通过。24组共享回归起止为2026-10-04T16:06:20.486144+00:00至16:14:47.435798+00:00，29份证据核查随后取得实际终态。环境、索引及实施计划已由当前IC-02绑定；源码、配置或部署变化后需要使用新执行身份复验。

Given：真实本机所有者已经登录且没有企业成员；When：查询原请求或操作 UUID；Then：返回 ACCESS_DENIED，不返回企业信息，不创建 operation_record、Outbox 或客户记录。实际登录、Header、CSRF/Origin、封闭输入及 MySQL 元数据检查由 tests/test_operation_runtime.py 和 scripts/check_operations.py 保存证据。

合法企业 FOUND/NOT_FOUND/EXPIRED、明确管理授权、并发及撤权分支需要获授权的真实企业和原操作，通过独立业务证据验收。当前缺少这些记录，不创建替代企业或操作。

| check_id | 实际验证 | 当前证据 |
| --- | --- | --- |
| IC-03-MIGRATION-COVERAGE | 15项成熟解析器检查通过；157张表、全部外键引用、成员认证八项变更及原操作四项变更；约束名称、字段及完整 CHECK 保持原始迁移语义 | [实际回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-20261004-OPERATIONS-COVERAGE-03/receipt.json)；SHA-256 17085893d907de11ccca44e10241911ffefe4614d7aa3ace7625135d28faa5ce |
| IC-05-OPERATIONS Red | 实际所有者登录及 CSRF 正常，查询入口缺失，404 与预期企业拒绝 403 不同 | [.implementation-work 原始回执](../../.implementation-work/IC-05-OPERATIONS-20261004-OPERATIONS-RED-01/receipt.json)；SHA-256 57831247928358b9e51cf665f08019b9310dcaf55849d0f7258a8a40e24df02d |
| IC-03-JAVA | Maven clean verify，90 项 Java 检查通过；186 项 Liquibase 资源解析；三条查询的只读字段及实际构造类型核查；当前成熟ALTER桥接及原约束断言共同绑定 | [原生回执](../../.implementation-work/IC-03-JAVA-20261004-OPERATIONS-04/receipt.json)；SHA-256 1ac7de8e3b27d9250576718d21da0f27d70d23c0e99a12f9fce5f414b82df249 |
| IC-05-OPERATIONS Green | 新部署的六项实际HTTP/数据库检查；当前企业拒绝、封闭输入、Schema/context、CSRF/Origin、重复查询不创建记录、真实期限字段及完整外键 | [实际回执](../../.implementation-work/IC-05-OPERATIONS-20261004-OPERATIONS-GREEN-01/receipt.json)；SHA-256 8f2ef62207424bfe47756dd545af431fbc8f79f858aaa2abe118033bcba0dd25 |
| IC-05-OPERATIONS 当前复验 | 六项实际检查通过；当前制品、186项迁移、原始输入字节及实际数据库约束重新绑定 | [当前回执](../../.implementation-work/IC-05-OPERATIONS-20261004-OPERATIONS-FINAL-03/receipt.json)；SHA-256 626880a55fa287a2af4fcc73628c5e1586bfe53dea20283030beafcfc4bb11ad |
| 当前完整回归及证据核查 | 22组现有回归通过；当前Java、模型及wheel补充回执共25份；逐文件重读输入和实际产物，确认对应同一真实部署 | [回归汇总](../../.implementation-work/FULL-REGRESSION-20261004-OPERATIONS-FINAL-03/evidence.json)、[当前字节核查](../../.implementation-work/FULL-REGRESSION-20261004-OPERATIONS-FINAL-03/verified-evidence.json)；范围REGISTERED_LOCAL_REGRESSION/CURRENT_EVIDENCE_BYTES |

025已在当前MySQL执行，Liquibase共192项；迁移前取得原生备份，既有备份和回执保存于.implementation-work/runtime/backup-archive-20261004-OPERATIONS-01及backup-archive-20261005-ARTIFACT-REGISTRY-01。当前应用制品SHA-256为96a21070ce705fbef32065100e32d751ed98119bacc14b714dcb9554b7da4a21，部署UUIDff5e71e4-509f-47f0-8c56-7dc3212da663，PID88142，启动25.969秒；当前工程集合为FULL-REGRESSION-20261005-PUBLISHED-SCHEMA-FINAL-01，25组及30份当前字节核查通过。上表其他集合保持对应原始输入范围。

## 关联影响与后续使用

关联 IMP-03 的追加迁移、IMP-04 的身份与权限、IMP-06 的未知提交恢复以及各业务写入入口。源码变更后的 Java、实际部署和现有完整回归需要复验，旧回执保留原日期和范围。

对象前置关联IMP-07配置正文、IMP-08原件、IMP-16正文与证据、IMP-26成品、IMP-36部署和备份。新增存储组没有修改这些业务的原权限或完成条件；Java受控S3组件、Artifact登记及coverage、逐对象授权/审计、上传会话、实际未知结果恢复及生产KMS继续实施。

## 未完成内容与继续条件

具体资源、固定登记、完整传递关系、当前原始来源权利、36类派生领域映射及服务端完整内容消费/回执已具备内部实现；下一可执行处理为领域Artifact登记写入、受限上传及固定领域读取要求，连接BX-37实际HTTP发送和IA-01/IA-08的运行主体/TaskToken/lease。合法客户回执成功保存与重读仍需真实企业、资源和授权数据验证；当前五项只读查询及保护检查不代表此项成功。跨项目共享决定、会话共享/转存，以及尚无具体实体的策略/规则配置版本继续完整实施。MODEL_SEND继续要求真实提供方/区域/外部处理许可、数据处理、预算和运行准入；当前应用会话内容方法明确拒绝该用途。生产游标身份、期限、完整关系容量及性能/恢复阈值继续使用实际批准配置；真实客户正向验收等待此前已请求的企业、人员、岗位、项目、资料及许可输入。DeepSeek密钥已经配置，不重复索取。完整IC-05继续IN_PROGRESS。

完整 IC-05 包含统一错误、对象授权读取、分片上传与摘要、完整分页及 coverage、授权下载、持久事件、审计以及 IA-01/08、IN-13—15 传输登记。查询入口实施和工程检查不能关闭这些验收。合法客户原操作及期限依据尚未取得，完整任务保持 IN_PROGRESS。
