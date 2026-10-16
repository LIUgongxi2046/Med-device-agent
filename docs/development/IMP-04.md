# IMP-04 独立身份、企业和项目授权

2026-10-14 C02持久运行令牌、步骤领取与续租、签发和续期、当前资源动作授权由独立唯一[RUN-TOKEN-AUTHORITY-01](RUN-TOKEN-AUTHORITY-01.md)维护。053补充步骤服务所有者/期限及原令牌摘要、Policy和代际绑定。当前实现、工程证据和未验收业务继续读取该记录；父工单保持IN_PROGRESS，生产调度关联[IMP-13](IMP-13.md)。

## FRONT-SESSION-RECOVERY-01

2026-10-14；对应FE-04、当前上下文及原请求恢复，任务状态来源保持IMP-04的IN_PROGRESS。已核查readMedical、医疗路由准入、router.onError、当前错误事件及真实权利提交取消后的导航结果。会话、企业候选和框架权限读取继续完整版本校验，冲突即时抛出错误；路由错误入口显示实际错误及请求身份，业务内容清理，原目标页面仅保留当前内存。

显式恢复入口重新读取完整会话后继续原医疗页面，没有自动重试或替代响应。原KN-RIGHTS-01及其他请求元数据保持当前安全定义，读取原操作继续使用原请求身份。Given真实权利决定改变上下文；When重新进入原页面；Then读取冲突具有可见错误入口，主动核查完整会话后可以查询原操作。真实Red证据为BROWSER-14的production导航及tenant-selection-options HTTP409记录，后续完整开发/生产检查继续执行。

IdentityErrorResponseWriter完整消费当前ErrorCode全部公开代码，使用穷举switch生成原代码、明确中文说明、请求身份及完整Error。Schema、非重试状态、空动作及原空值语义保持，解析内容、原文位置、引用保护和保存政策分别保留对应代码。TemplateErrorResponseTest通过当前Schema逐项核验全部公开错误；真实Red为source-error-red-01，当前Java及完整HTTP回归继续执行。

当前TemplateErrorResponseTest六项通过，20.27秒，失败、错误及跳过均为零；包含全部ErrorCode循环、原领域错误、完整Schema、请求身份、九项字段、非重试状态及空值。日志`.implementation-work/source-error-green-01.log`的SHA-256为`2d37c8e16fbe99a5c245a19f0f283693178fd4b537a23e08854dc06628daa803`。完整真实HTTP消费及当前应用制品继续对应组合检查。

组合应用编译使用RunTokenAuthoritySchema的原SHA-256文件及RFC8785清单摘要，调用当前已有Commons Codec DigestUtils，原输入字节和小写十六进制表示保持。完整令牌授权由原后端任务维护，本次仅核验当前应用依赖编译。

## FE-04 当前页面与会话清理

2026-10-13资料目录、处理队列、成果目录和全文检索使用useMedicalRouteRead及当前会话监听。页面离开、会话重新读取或企业身份变化后，停止当前读取并清理列表、选择、筛选、文件夹输入、提交表单及查询文字；后续响应需要通过当前generation、会话、页面和AbortSignal检查。成果转存及资料维护写入后，只有原页面和原会话仍然有效时才更新界面。对应实现为source-catalogue.vue、source-processing.vue、artifact-catalogue.vue和source-search.vue。

资料目录、处理队列和成果目录的watch及查询动作返回完整Promise，继续使用Vue已有的异步生命周期管理。权限拒绝和Schema错误立即传播到原会话错误入口，原内容按当前身份清理；取消请求保留原AbortSignal和generation检查。文件夹与资料条目读取通过完整Promise.all执行，没有自行生成成功状态。

项目模拟浏览器使用实际公开接口创建、修订并执行四项状态决定。FRONT-CURRENT-DEV-02与FRONT-CURRENT-PROD-02顺序读取同一CURRENT-04医疗制品；开发八项、生产八项实际Chrome检查全部通过，每次六项原生项目动作、创建重放与请求冲突、修订冲突、状态重放与重读、审计及Outbox均取得真实结果。两次项目数量恢复原值，临时应用关闭，输入标记SIMULATION、customer_success_verified=false。独立结果及摘要由[IMP-37](IMP-37.md)维护。两次执行期间后端输入发生变化，回执均为exit_code=0、inputs_unchanged=false；完整当前源码组合仍需后端输入稳定后复验。本节不登记完整身份及项目业务验收通过。

Given资料筛选及文件夹输入属于当前页面；When重新读取实际会话；Then页面清理原输入。对应Red断言为`.implementation-work/IC-04-PROJECT-SIMULATION-20261013-FRONT-PRODUCTION-RED/project-browser-output.log`。当前实现独立检查以及完整企业集成分别登记，其他身份验收条件继续保留。

Green命令为`node tests/browser/source-context-checks.js "$PWD/.implementation-work/FRONT-20261013-CURRENT/production"`。实际本地账户通过正式/access入口登录；开发四项及生产四项输入清理检查全部通过，包含资料目录、处理队列、成果目录和全文检索。生产入口HTML及直接引用的Script/CSS与构建文件字节一致。检查没有业务POST，查询文字没有进入URL；原始输出为`.implementation-work/FRONT-20261013-CURRENT/source-context-verified.log`，结果为`.implementation-work/FRONT-SOURCE-CONTEXT-20261013/checks.json`。本节验证实际会话重新读取及页面输入清理，完整跨企业撤权及专业数据流程继续按原要求执行。

## BACK-CURRENT-RUN-TOKEN-POLICY-01

### RUN-TOKEN-SIGNING-KEY-01

2026-10-14继续补充C02的受保护签发私钥，任务状态引用实施任务计划IMP-04的IN_PROGRESS，关联BE-04/07/13/14。已经读取本记录、RunTokenSignature、RunTokenPolicyStore、ProtectedConfigurationFile、当前封闭Policy和LLD-后端§2.1；复用既有Jackson/networknt/JNR/JDK/Nimbus，全部PKCS8及JWS由成熟组件处理。

Given当前0700目录、0600单一链接配置、实际生成的RSA密钥及固定服务器公钥Policy；When读取签发私钥、验证配置绑定或进行密码算法操作；Then完整Base64和PKCS8字节往返一致，实际密钥与公钥及Policy修订/字节摘要相同，读取前后变化或权限/链接变化立即拒绝。技术检查使用实际RSA和已有原件，不创建客户运行、审批、费用或替代模型结果。持久principal_token/run_principal/execution_registry、mTLS、当前动作授权、续期及完整令牌签发继续原业务验收。

RunTokenSigningKeyStore已经连接独立run-token-signing-key.schema.json、原ProtectedConfigurationFile的openat/fstat、完整配置容量、当前所有者、0700目录及0600单一链接文件。私钥使用成熟PKCS8/JDK RSA CRT，至少2048位，标准Base64及完整DER往返一致；由实际modulus/publicExponent派生公钥并与当前Policy完整SPKI核对。配置同时固定policy_revision和policy_sha256，公钥及私钥文件读取前后分别核查。Current公开内容只有配置修订、摘要和公钥身份，toString不提供秘密。JSON解析明确关闭正文位置引用，格式错误不包含私钥正文。

sign固定原Claims的独立JSON快照，复用原封闭声明、发行者/受众、动作、U64及时间检查，再使用Nimbus RS256、medical-run+jwt和当前kid。签名核查、全部配置重读及原到期检查通过后返回原字节。该组件只处理密码及配置，客户令牌的jti保存、持久运行归属、mTLS、当前授权、续期和合法终态回执继续完整C02业务实现；本项没有启用生产签发配置，没有生成客户运行、审批或收费。

有效Red为`.implementation-work/run-token-signing-key-red-20261014.log`，独立私钥定义缺少造成一项failure、零项error/skip。当前四项实际私钥检查及六十项原Policy/签名检查全部通过，共六十四项、失败/错误/跳过均为零。实际RSA2048、登记官方原件37870字节及其a70e1b2c0407416a2ea5a076c96e4fd85ea6edf2fcbf5235d0a6581e5a43d7ee摘要、实际签名、公钥核验、不同实际密钥、权限及额外链接、同修订Policy字节变化、未知/重复/尾随JSON、非法Base64/DER、容量及秘密诊断分别核查。签名在密码操作前核查完整UTF8及JWS输出容量，使用成熟Base64URL计算实际签名编码长度，不截取声明。回执`.implementation-work/run-token-signing-key-build-checks/f20b4925-fba4-4331-b402-56709d84f3bc/receipt.json`的SHA-256为8b138bdb0da9cb7e466fd81449c8047273f6026d767ea66c66de6324bf229ee5，exit_code=0、inputs_unchanged=true；全部JUnit及原始日志保存在同目录。原公钥Policy及Schema保持原定义，私钥只在实际检查的0700/0600配置中使用，秘密值不进入记录。

本项文档CURRENT、last_verified=2026-10-14，范围为上述密码组件及受影响原验证；完整令牌签发、当前业务许可、生产密钥配置及内部HTTP继续PENDING，父任务保持IN_PROGRESS。当前改动没有提交；完整应用制品和部署由IMP-36维护。

当前密码组件已进入PID27909的固定完整Jar7773fe25f20d062a988e37662d3c390fab05838c46787b08d8beedad0a14a2f9。运行核验包括四份身份源码、十一项身份class和两份Policy/私钥Schema，与当前编译字节逐项相同；完整运行回执`.implementation-work/source-processing-runtime-checks/0b1fe244-7fdf-4059-8d1c-8699a1972501/receipt.json`的SHA-256为4eeace6a535a399029d04639a3e7350bfa3cc307c42f3819ee9eb8e611ad361d，构建及环境归属由IMP-36维护。实际生产签发配置及合法业务令牌尚未启用。

| 文件或identifier | 当前职责 |
| --- | --- |
| RunTokenSigningKeyStore、run-token-signing-key.schema.json | 当前PKCS8私钥、Policy绑定、签名前后配置核查及秘密诊断保护 |
| RunTokenSignature.requireClaims | 原完整声明验证共享，保持原签名验证行为 |
| RunTokenSigningKeyStoreTest | 实际RSA、登记原件签名、当前配置和格式/权限拒绝 |
| check_run_token_signing_key.py | 固定源码与原件、六十四项原始组件检查和独立JUnit |

2026-10-08，依据LLD-后端§2.1/§7及BACK-RUN-TOKEN-SIGNATURE-01，继续内部运行身份的当前受保护配置，唯一状态来源为实施任务计划IMP-04的IN_PROGRESS，关联IMP-08/13/38。已经读取当前RunTokenSignature、ServiceCertificateTrustStore及其实际原生文件检查，核查JNR POSIX/FFI固定依赖。原Agent发布清单草稿与LLD-Agent§6.3的完整依赖保持IMP-12范围，没有发布或任务采用成功。

Given明确操作系统所有者、0700配置目录、固定0600配置及实际RSA公钥；When启动、重复验证令牌或原子更新配置；Then每次读取当前发行者、受众、公钥及明确期限/字节容量，配置缺失、权限失效、链接、未知/重复字段、无效DER、无效容量及当前密钥变化立即报告。配置读取期间及签名验证前后检查当前字节身份，不采用旧配置。使用成熟Jackson/networknt/JDK/JNR依赖；不保存私钥，不产生企业、运行、模型结果或专业批准。

当前实施决定为INFERRED/CONFIRMED：run-token-policy.json使用封闭schema_version/revision/issuer/audience/public_key_der_base64/maximum_lifetime_seconds/maximum_token_bytes；明确配置容量由部署提供，没有默认值。ProtectedConfigurationFile复用现有实际openat/fstat机制，ServiceCertificateTrustStore与RunTokenPolicyStore共同消费当前受保护文件；原信任配置全部TLS/Servlet及权限检查进入关联回归。运行令牌消费者继续检查持久归属、mTLS、当前发起人动作、续期和原终态回执。验证入口为scripts/check_run_token_policy.py；Red/Green及当前制品状态按实际结果登记。

有效Red为CURRENT-RUN-TOKEN-POLICY-RED-01原57804，实际配置及RSA密钥生成后，正式读取器存在断言失败，1项failure、0项error/skip，回执9cc6a6150180a0247be56ff4569d851c34083f22603e4d15bbfaaf6c29748763、inputs_unchanged=true，JUnit独立保存。Green-01原27288成功终止，18项当前配置及42项原签名检查全部通过，回执fb4a953d639900b23792db428eba057ee8ab541ab73cc490c08a7ebff51bc4b7、inputs_unchanged=true；原配置JUnit5.820秒，签名JUnit6.613秒。实际文件原子更新、删除与恢复、0700/0600、符号链接、单一链接、当前所有者、配置容量、七项必需字段、未知/重复/尾随JSON、实际RSA2048/1024及EC/DER拒绝、受众、原期限及字节容量逐项验证。

完整身份关联CURRENT-RUN-TOKEN-POLICY-IDENTITY-01原9114成功终止，141项原生JUnit全部通过，Maven47.837秒，回执24b4cc5f8da71d334104423acb314251e8a31b7a579214edc82288f88cae64e4、inputs_unchanged=true；原TLS、Servlet、当前信任配置及新Policy共同验证，独立JUnit和22份公开证书/CRL已经保存。独立证明CURRENT-RUN-TOKEN-POLICY-20261008-final-evidence.json摘要c7179361d93c0703af2b6c5f9916beffdf039e65c9774740663fe0ac688345a3，当前源码、成熟依赖、原Red/Green字节、141项JUnit、证书签名/有效期与实际配置清理全部通过。文档本节CURRENT、last_verified=2026-10-08，范围仅为受保护Policy及签名消费；记录更新后原回执保持自身文档版本。

| 文件与identifier | 当前职责与关联 |
| --- | --- |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/ProtectedConfigurationFile.java | 固定目录和文件名、原生openat/fstat、当前私有文件身份及完整读取；服务信任和运行令牌共同消费 |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/RunTokenPolicyStore.java | read/verify/Current；完整当前Policy、签名验证前后重新读取及原到期拒绝 |
| business/medical-identity/src/main/resources/run-token-policy.schema.json | 当前服务器配置的封闭字段与明确正整数容量/期限 |
| RunTokenPolicyStoreTest、scripts/check_run_token_policy.py | 实际RSA、当前配置更新和拒绝、独立JUnit及固定输入回执 |
| ServiceCertificateTrustStore、check_service_certificate、verify_service_certificate_evidence | 原CA/CRL/TLS/Servlet消费者与完整共享身份回归，原行为及私有文件清理保持 |

448项完整构建和PID16562部署代表此前已核验源码，组合证据RUN-TOKEN-TASK-PLAN-20261008-release-evidence.json摘要f4218711027f9827f1aecfc2ffe3f5221ce801187d700d51da5dac7e7e2834a9，含231份静态字节、八项入口、六项原操作与五项记录检查。当前新Policy组件的完整应用构建和新部署继续验证；实际生产配置发布、C02签发私钥保护、持久运行归属、mTLS与逐动作当前许可、续期、原终态回执和Worker业务仍未完成，父工单保持IN_PROGRESS。

## BACK-RUN-TOKEN-SIGNATURE-01

2026-10-08继续LLD-后端§2.1的签名运行令牌，关联IMP-08/13/14/15/38；工单仍引用实施任务计划IMP-04的IN_PROGRESS。已经检索ServiceCertificateIdentity/TrustStore、原OIDC令牌组件、WireValues、002的run_principal/principal_token及004的execution_registry。当前服务证书Verified不提供运行令牌或业务授权；持久运行归属、当前发起人权限及原终态回执保持全部原要求。

Given实际本地RSA密钥、明确发行者/受众及生命周期/容量配置和完整签名Claims；When读取原JWT或者改变原消息、密钥、用途、受众、时间与字段；Then只接收当前固定公钥的RS256签名及完整封闭字段，未知、重复、缺少和不合法字段立即失败，原U64授权水位保持精度，原期限不延长。使用既有Nimbus9.37.4、Jackson和JDK密码学；没有新增依赖、网络、数据库或外部服务。企业、成员、项目和任务字段的SIMULATION范围继承用户授权；实际签名与验证不使用替代密码学或模拟服务。

常规实施选择为INFERRED/CONFIRMED：用途为medical-run+jwt，JOSE头仅包含alg/typ/kid；kid为实际RSA公钥SPKI的SHA-256，最小密钥位数为2048，实际签名库用法和本地制品已经核查。Claims包含iss/iat/exp/aud以及tenant_id/project_id/initiator_id/run_id/step_id/action_scope/jti/auth_epoch，project_id和step_id分别允许物理表规定的显式null，不能省略。期限和字节上限由明确服务器Policy提供，没有采用五分钟提议作为生产默认值。原生检查只证明密码学及字段读取，不能证明mTLS传输、principal_token保存、运行归属、撤销、当前动作许可、续期或Worker业务；完整生产Policy读取与C02内部入口继续实施。

有效Red为RUN-TOKEN-SIGNATURE-RED-01原88660，唯一完整签名行为检查中的组件存在断言失败，1项failure、0项error/skip，回执b908e5500861f005efb2f054ad1824af737aa948b8ceaa4546d2d664d2f9cd26、inputs_unchanged=true。当前Green为RUN-TOKEN-SIGNATURE-GREEN-02原84973，已取得exit0终态；42项实际RSA/Jackson/JUnit检查全部通过，6.453秒，失败、错误和跳过零项。回执位于`.implementation-work/IC-04-RUN-TOKEN-20261008-RUN-TOKEN-SIGNATURE-GREEN-02/receipt.json`，摘要f4a4d11a6a9b62a52883ea1785ad6feae57b41d1a54840c380f37a498e37c949、inputs_unchanged=true，原JUnit独立保存于同目录RunTokenSignatureTest.xml。检查采用JDK17与两个实际生成的RSA2048密钥，真实时间到期、不同密钥签名、原消息修改、每项必需字段、未知/重复字段、完整U64、显式null和封闭用途均验证；没有连接数据库或服务，也没有生成业务运行、专业批准或模型结果。

| 文件与identifier | 当前职责与关联 |
| --- | --- |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/RunTokenSignature.java | verify/Policy/Verified；只接收当前服务器公钥与明确期限/字节容量，私有构造保存不可修改的原Claims及令牌摘要 |
| business/medical-identity/src/test/java/com/ruoyi/medical/identity/RunTokenSignatureTest.java | 42项实际签名及字段检查；完整声明、原期限和当前密钥共同验证 |
| scripts/check_run_token_signature.py | 离线原生检查，捕获相关源码、LLD、迁移、Nimbus制品、JDK与JUnit字节 |

本节文档CURRENT、last_verified=2026-10-08，适用范围为Green-02原输入下的密码学及字段读取；本次更新只登记原结果。服务配置的当前安全读取、持久principal_token/run_principal/execution_registry、当前授权、续期、内部服务HTTP及Temporal消费者继续实施，父工单保持IN_PROGRESS，完整业务验收尚未完成。

独立字节证明为`.implementation-work/RUN-TOKEN-SIGNATURE-20261008-final-evidence.json`，摘要b503715cf3536badc4c75dd738361252835c0e5c0a5734b4963831e859273181；原Red/Green回执、独立JUnit和日志逐项核验，Green的源码与运行依赖全部符合原输入。主记录在检查后更新，原回执保留其原文档版本；后端LLD随后补充当前签名规则，后续完整构建采用更新后的设计输入。证明中的持久归属、当前动作授权、Worker业务、新部署和客户成功均为false。

当前ServiceCertificateTrustStore已经进入完整403项Java、前端strict与生产构建，CURRENT-TRUST-SOURCE-UPLOAD-BUILD-FINAL-01回执dfd20f8a0c63d6cacc6f0f8fa9e51f732523596f6142c3641d002479eb420e39、inputs_unchanged=true。三项实际证书/配置检查和十六项Servlet行为重新验证；十九份运行JSON与完整JUnit独立保存于38c825d0-122f-49a7-878a-923ef669ef66。当前PID86534制品已部署，229份HTTP静态字节、七项入口和六项原操作通过，由唯一[IMP-06](IMP-06.md)维护。下方独立证明和部署只证明各自原输入范围；服务信任配置管理、短期运行令牌、持久运行归属、当前发起人授权与完整Worker继续原要求。

## 当前受保护的服务信任配置

BACK-CURRENT-SERVICE-TRUST-01由本记录唯一维护，关联BACK-SERVICE-CERTIFICATE-01、IMP-08/13/38及LLD-后端§2.1/§7。2026-10-08已读取当前证书组件、真实Servlet消费者、原私有目录读取和已验证制品；上一状态问答为NO_PROGRESS，当前已完成原操作与制品复核并继续实际配置消费。Given获准操作系统所有者的0700目录、0600配置、实际CA与签名CRL；When启动读取配置或在同一TLS会话重新请求；Then读取当前固定修订、完整CA/CRL、URI/证书摘要及TLS政策，拒绝配置缺失、非私有权限、符号链接、超出容量、重复字段、未知字段、证书/CRL损坏、无效签名及过期CRL。错误就地报告，不采用上次配置。

常规实施决定为INFERRED/CONFIRMED：单个受保护JSON包含完整Base64 DER和封闭字段，Jackson/networknt及JDK CertificateFactory负责成熟格式读取。目录、当前文件名trust.json、操作系统所有者和明确容量共同固定；正式依赖JNR POSIX 3.2.4/JNR FFI 2.3.3提供原生文件描述符及成熟属性读取，openat相对固定目录打开文件，O_NOFOLLOW/O_CLOEXEC/O_NONBLOCK和fstat核查设备/inode、UID、权限、单一链接、容量及纳秒mtime/ctime，读取结束再次打开当前文件名核查身份。缺少原生功能立即拒绝，文件属性格式由依赖处理。发布使用同目录0600临时文件和ATOMIC_MOVE，同时更新完整CA、CRL及登记。运行令牌、服务端配置管理、完整内部HTTP/Worker和客户业务继续原要求，父工单保持IN_PROGRESS。

有效Red为IC-04-SERVICE-CERTIFICATE-20261008-CURRENT-TRUST-RED-02，回执35e7f40c10f7f1d96a790bbbc7de0b6d9e3924e347860965531eeca8319a92aa、inputs_unchanged=true；实际CA及签名CRL已经生成，三个JUnit中的配置读取器存在断言失败，1项failure、0项error/skip，另外两项真实TLS/Servlet通过。当前Green为CURRENT-TRUST-GREEN-02，原6894会话成功终止，回执1eea81d16f546a43ad6850b8dd8c19aebb853271fd5192e678f331b10496e55e、inputs_unchanged=true；81项关联JUnit由42项Schema和39项身份组成，失败、错误及跳过零项。包含三项实际TLS、Servlet和当前文件配置检查；私有凭据清理和临时监听关闭均通过。

当前Servlet十六项行为包含同一TLS会话当前登记拒绝、过期签名CRL拒绝、有效登记重新读取、当前文件权限拒绝、配置删除503/no-store及完整配置恢复200。TrustStore实际检查0700/0600、符号链接、单一链接、实际所有者、容量、重复字段、未知字段、尾随JSON、签名损坏及协议拒绝，并在原子更新后读取新修订。独立证明CURRENT-SERVICE-TRUST-20261008-final-evidence.json，SHA-256为5fac5366dcd62901164618b45272a1bc0dec88ea5815949a3ff06f061ea15ff3，复核544项当前源码/测试/依赖输入、81项原始JUnit及22份公开证书/CRL的签名、有效期和撤销序列。该证明在生成时适用于原始输入；关联Spring路由更新以后按各自实际输入核验，完整构建将重新执行。当前源码与已部署PID74481的原402项制品分别维护，新的读取器未部署；完整运行令牌及业务Worker仍未完成，父工单保持IN_PROGRESS。

## 服务证书身份

BACK-SERVICE-CERTIFICATE-01由本记录唯一维护，关联IMP-08/13/37/38、FR-01、NFR-03/13及LLD-后端§2.1/§7。当前工单状态继续引用实施任务计划IMP-04的IN_PROGRESS。2026-10-08已检索索引、ApplicationSessionIdentity、LocalIdentitySecurityConfiguration和来源租约组件，核实当前ServiceCertificateIdentity及实际TLS/Servlet调用。浏览器身份继续保持原完整要求。

Given实际签名的CA、服务证书、当前CRL和实际TLS连接；When服务提交其证书身份；Then检查证书路径、有效期限、clientAuth、digitalSignature、唯一URI、当前登记摘要和撤销状态，返回只能由验证器创建的服务身份。无证书、未知CA、未登记身份、未登记摘要、歧义URI、过期证书、撤销证书、过期CRL及当前取消登记均拒绝。浏览器Cookie不能提供内部服务身份。

决策BACK-SERVICE-CERTIFICATE-01为INFERRED/CONFIRMED，属于已授权C02实施：使用JDK PKIX和cryptography 50.0.2，逐次取得当前受保护信任配置，CRL不足或验证失败立即报告；服务URI和证书摘要共同登记。Trust复制全部集合，明确TLSv1.3和所允许的TLSv1.3密码套件；Verified构造器私有，包含serviceUri/certificateSha256/trustRevision。每次请求独立验证完整证书路径和签名CRL。Servlet仅使用容器实际TLS属性，Cookie与Origin拒绝；SSLSession入口核查实际协议与密码套件。证书认证不产生企业授权、任务令牌或浏览器Verified对象。配置发布及生产消费者、短期运行令牌、持久运行归属、Temporal和终态回执继续完整范围。

| 文件与identifier | 当前职责与关联 |
| --- | --- |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/ServiceCertificateIdentity.java | require/Trust/Verified；当前服务证书身份组件，后续内部业务消费者仍须运行令牌与当前授权 |
| ServiceCertificateNativeTest、ServiceCertificateServletTest | 实际JDK HTTPS、Tomcat10.1.55、Spring MVC；成功、拒绝、同一TLS会话当前登记变化、私有文件清理和监听关闭 |
| scripts/prepare_service_certificates.py、requirements-native-security.txt | cryptography50.0.2/cffi2.1.1/pycparser3.0；实际CA、服务证书、加密PKCS12、签名CRL；秘密不进入参数或证据 |
| scripts/check_service_certificate.py、verify_service_certificate_evidence.py | 捕获当前输入、JUnit、原生结果及22份公开证书/CRL；独立验证原始摘要、签名、有效期限及实际撤销登记 |
| scripts/build_business.py、check_migration.py | 当前完整构建加入两份证书运行结果与原生证书生成器输入；402项检查及本地制品部署通过 |

Red为IC-04-SERVICE-CERTIFICATE-20261008-NATIVE-RED-01，回执e65cec6c262973409b4166ee8bb0989fefc2b9a2d30c75c6c2a48e6d1eeeae4d、inputs_unchanged=true；实际TLS握手完成后，验证器存在断言失败，1项failure、0项error/skip。当前Green为IC-04-SERVICE-CERTIFICATE-20261008-IDENTITY-FINAL-01，原8095会话成功终止，回执dfae94374e051b6b42d135cebc10ca17066273830e8cc2839efc968cede3467e、inputs_unchanged=true。80项受影响检查由42项Schema和38项身份组成，失败、错误、跳过均为零，包含两项实际TLS/Servlet检查。Native原始结果摘要10da237152d4740bee541268ae6e319d91fcc61c7534aaed3db4d56b76b6b1ee，Servlet原始结果摘要b1aa3c5ac96570388d4924420a152470d6b6b9fdb9ef78bb7c51181df2983fd3。

Native十项和Servlet十三项行为分别验证合法证书身份、未登记摘要/URI、歧义URI、实际撤销、无证书/未知CA/过期证书的握手拒绝、当前取消登记及过期签名CRL拒绝；Servlet另外验证Cookie/Origin拒绝和当前有效登记重新读取。同一TLS会话的ssl_session_id在登记取消前后相等。被撤销证书的原生原因明确为PKIX REVOKED。实际证书资产私有目录0700、文件0600；每次检查结束清理全部私有文件，并核验监听关闭。测试Controller和Configuration仅在service-certificate-servlet-native生效，均在src/test；原浏览器安全配置保持原实现。

独立80项复核入口SERVICE-CERTIFICATE-20261008-final-evidence.json、摘要5131e7cfcb73703b7dca74876f7c66ff495131fbf9b52df33916b1ab7eabaf36，证明其原503项输入、原始JUnit及22份公开证书/CRL；当前关联页面入口变化后，该集合保留原输入范围。当前完整SERVICE-CERTIFICATE-TEMPLATE-BUILD-FINAL-01回执45d43d5ed9d3018baa5c8bc7c5476805ba4b3a65270c3cc72b0f88582f56e758通过402项Java、前端strict和生产构建，十九份运行JSON及完整JUnit独立保存，其中两项实际TLS/Servlet检查重新执行。当前组件已经进入PID74481的制品，部署和六项原操作回执由唯一IMP-06维护。当前组合复核入口为SERVICE-CERTIFICATE-TEMPLATE-20261008-release-evidence.json，结果读取实际文件；其中检查当前制品包含正式身份组件且排除原生测试Controller。源码自查核验当前登记逐次读取、集合不可变、Opaque身份构造、失败传播、成熟证书解析及Profile范围。当前信任配置生产发布、运行令牌及完整Worker继续原要求。本文档CURRENT、last_verified=2026-10-08，完整企业治理与客户验收继续原要求。

2026-10-08当前SOURCE-COMPLETION-LEASE-BUILD-FINAL-01完整400项Java、前端strict及生产构建通过，回执e67ade702483141b82417d2c4388debabef8e21f6fa16f9f76afe785fa157eae、inputs_unchanged=true。TenantScope.subjectId读取原已验证上下文，SourceCompletionLeaseStore及SourceUploadMapper继续当前企业事务和人员参数核验；TenantAccessTest六项包含新增Mapper无上下文拒绝，原共享缓存/当前身份十项、304项持久化与完整来源HTTP共同通过。当前PID61813的222份HTTP静态字节和六项原操作分别通过，运行证据由唯一IMP-06维护；租约业务由唯一IMP-08维护，Worker服务身份仍待实施。当前九份组合入口为SOURCE-COMPLETION-LEASE-20261008-final-evidence.json，实际结果读取原始文件；下方DELIVERY及其他集合保持原输入范围。本段CURRENT、last_verified=2026-10-08，完整企业治理与客户验收继续原要求。

2026-10-08当前SOURCE-COMPLETION-DELIVERY-BUILD-FINAL-01完整400项Java与前端构建通过，回执98f3abf21a85ff41bde9130441ae428740150c4ab4276b5a8250c762c293c671，inputs_unchanged=true；原缓存/当前身份、304项持久化和完整实际HTTP回归进入同一构建。来源完成命令与消费者投递继承TenantScope、当前主体参数与EnterpriseAccess事务，唯一IMP-08维护业务。当前PID53689部署、222份HTTP静态字节与六项原操作分别通过，由唯一IMP-06维护运行证据；本段仅证明对应当前实现，完整企业治理与客户验收继续原要求。下方旧构建和组合只保持原输入范围；当前九份字节组合入口为SOURCE-COMPLETION-DELIVERY-20261008-final-evidence.json，结果以实际文件为准。

## SQL解析缓存与当前权限检查

当前共享缓存和企业保护由SOURCE-COMPLETION-COMMAND-BUILD-FINAL-01完整400项Java、前端strict及生产构建重新验证，回执3fa07e3cbecec98599944262cafed46c8f9c7e16a7712d6154208ca0fe867fa1、inputs_unchanged=true，失败、错误和跳过零项。304项持久化、70项共享接收与10项缓存/当前身份检查共同通过，来源六项实际HTTP包含202命令保存和重读、当前上下文、跨企业、撤权及原对象恢复；企业20秒容量first_stored=8495ms、用户60秒容量first_stored=10868ms，停止输入期限30114ms。当前PID47930运行Jar48c61f2e71e7d959683451548ad7c7d6b41bcd856e85809ef0ac73d997125d2e；部署全部静态字节和六项原操作通过，完整原始证据由唯一IMP-06/08维护。本段CURRENT、last_verified=2026-10-08，范围为共享缓存、当前企业保护和已运行的来源命令；完整企业业务与父工单仍待验收。下方结果只证明各自保存的输入范围。

当前SQL-CACHE-BUILD-FINAL-01完整390项Java、前端strict及生产构建通过，回执a65f1b6a8ebb1354c20c20d629e3bc4580471c79fe58a6eeaaef43fecf11f264、inputs_unchanged=true，失败、错误及跳过零项；包含295项持久化、70项共享接收及10项缓存/当前身份检查。完整构建企业20秒容量first_stored=10478ms、用户60秒容量first_stored=13103ms；独立五项HTTP回执3206a03af1edb67b0e12a4f4a3fa660416c90a31ca18b85e760a628bb75d0100同时通过。成功、容量拒绝、取消、撤权、期限、未知响应恢复及唯一事件保持。当前源码已经在PID40465使用0400不可变Jar运行，摘要667dc87510bc0b2d3eaaf91f8212536ff79028c2c6d5fb579ca2cd993a4791ca；部署和六项原始操作检查通过，实际原件与业务回执读取唯一IMP-08，完整企业隔离与父工单验收保持原要求。本节CURRENT、last_verified=2026-10-08，范围为当前共享缓存、既有企业保护、完整构建和本地部署。

2026-10-08，SQL-PARSE-CACHE-01关联IMP-08原20秒企业容量检查。有效Red由唯一IMP-08的DEADLINE-BUILD-FINAL-02与CAPACITY-DIAGNOSIS-01原始结果保存。已经检索本记录、索引、MedicalPersistenceConfiguration、ProtectedTenantInterceptor、TenantAccessTest、固定MyBatis-Plus3.5.17二进制及完整官方多企业插件文档，采用LLD-后端§6.4的SQL-PARSE-CACHE-01。来源类型INFERRED，常规实施选择CONFIRMED；当前完整390项检查与独立五项HTTP检查保持原20/60秒容量、成功、对象及权限要求。

Given真实MySQL/Redis/S3、原账户、SIMULATION企业项目与登记原DOCX；When原企业20秒和用户60秒容量场景接收文件并继续原请求；Then保持原429时限、200成功、全部对象字节、唯一事件和清理要求。共享解析结构命中后仍逐次核验当前主体、事务、企业和权限。SqlParseCacheTest使用当前正式Mapper与文档SQL，检查单语句和完整四语句的独立AST、两个原生解析入口的类型及实际序列化结果损坏立即报错；TenantAccessTest在实际缓存命中以后继续检查未授权真实Mapper调用。十项检查已经进入当前完整390项构建；原始JUnit逐文件摘要进入归档。类型行为Red由IC-04-SQL-CACHE-20261008-TYPE-RED-01原始回执6bbe90b12c283e3ada535d90a04f1286146df531e1950993ca0e1fd073778451保存。FailFastSqlParseCache直接扩展成熟JdkSerialCaffeineJsqlParseCache，原生序列化及Caffeine资源管理负责实际读写，同一进程共享实例，同步保存独立单语句/多语句键；错误在原生反序列化位置传播。当前完整构建、独立上传HTTP和本地部署共同验证相关消费者，IMP-04保持IN_PROGRESS，完整企业业务验收继续原要求。

当前关联检查继续采用IMP-06的CURRENT-APPLICATION-RESTORE-SHARED-02，165项Java与131项Chrome的原始输入、产物字节保持一致；与当前十二项解析制品共同核验的证明为`.implementation-work/DOCX-ORDER-20261007-shared-evidence.json`，SHA-256为27f6448dda4f7ecd86d5a71ebf14bf51315d7d9e97611be8d5f9a5f6d6790cc1。包含本地恢复、完整构建及部署的八份关联回执证明为`.implementation-work/DOCX-ORDER-20261007-associated-evidence.json`，SHA-256为7f5b08d42d5a539a9cb813f45417de89b24117e916bd4d31a239713b82f2c1c0；原件顺序和安装检查由唯一IMP-08维护。本次Python变化不改变企业授权代码，完整客户业务继续原验收要求。以下各执行集合分别保持原输入和范围。

## 成员管理的完整写入操作

FRONT-MEMBER-MANAGEMENT-01 继续使用本主记录，父工单 IMP-04 保持 IN_PROGRESS；当前依据为 PRD §8.1、接口补充设计 §4.10/4.11、LLD-前端 §1.3、唯一邀请/权限 Schema、原 operation-lookups 与当前读取页面。已检索实际 MemberInvitationService、PermissionDecisionService、治理读取、本人接受页面、project-json 和原请求元数据组件。正式页面已经连接邀请、独立授权、指定授权撤销和成员停用；后续来源变化使 MEMBER-MANAGEMENT-SHARED-FINAL-01 只保留原执行输入，完整写入与关联共享检查重新验证。

Given 明确 SIMULATION 企业、实际账户、固定岗位版本和完整管理委派；When 管理人员通过页面邀请成员，由本人接受并读取项目任务，再授予和撤销独立权限并停用成员；Then 当前记录、原请求、上下文水位、实际操作、同步审计和 Outbox 一致，重新读取后的结果才显示完成。邀请秘密仅进入当前页面内存；刷新、重复提交、连接中断、非法字段、未知响应与权限拒绝保留明确状态。完整输入与原键在原请求恢复时保持一致，过期请求不重新提交，未保存离开需要明确处理。

| 文件与 identifier | 当前职责与关联 |
| --- | --- |
| `web/src/api/medical/member-governance-write.ts` | ID-07/09 完整输入及回执；主体、范围、原版本、微秒时间和原请求摘要；当前会话、原操作及逐页目标重读 |
| `member-governance-write-view.schema.json`、protocol.ts | 封闭原请求技术元数据与原业务 Schema 登记；不保存业务输入或邀请凭据 |
| `member-governance-write.vue`、enterprise-members.vue | 原 Element Plus 表单、checkbox、Dialog 与结果；撤销/停用继承已经读取的目标及版本；离开处理、秘密清除与原操作恢复 |
| `web/package.json`、`web/package-lock.json` | 固定 @js-temporal/polyfill 0.5.1 与 jsbi 4.3.2，成熟 Instant 比较保留微秒精度 |
| MemberGovernanceSimulationHttpTest、`member-acceptance-checks.js` | 实际账户、MySQL/Redis/HTTP、开发/生产 Chrome、完整写入及后续消费者，唯一操作/审计/Outbox 与生成数据清理 |
| `scripts/check_framework_navigation.py` | 当前共享检查捕获实际 Temporal 与 jsbi 依赖字节 |

有效 Red 为 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-WRITE-RED-02/command/receipt.json`，SHA-256 `4849d9abe98bb2bb4956b284b77d3b846ba3ece2cd4dc929930dab24a0799003`，inputs_unchanged=true；实际登录、接受与管理读取以后，邀请按钮的必需断言未通过。

MEMBER-WRITE-CHECK-02 实际通过，回执 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-WRITE-CHECK-02/command/receipt.json`，SHA-256 `db4073f414f20721a9ca06108612c16c303e5b6773bda1f191b180748c62ec4c`，inputs_unchanged=true。strict 类型检查通过，当前生产制品由 MEMBER-WRITE-GREEN-01 实际生成；此回执捕获 JUnit 和命令输出，浏览器及业务结果由同目录生成文件保存，完整产物捕获继续共享检查。开发/生产分别完成十一项本人接受、八项管理读取、十项管理写入检查；邀请和授权在实际提交后中断，刷新后同一原键恢复，撤销/停用状态和原版本重新读取，新邀请由实际新账户接受以后项目和任务读取200。四项 ID-07、四项 ID-08、六项 ID-09 与对应同步审计/Outbox 数量通过；实际生成数据清理、临时应用及前端关闭。客户成功保持false，没有视觉检查或模型请求。

MEMBER-WRITE-CHECK-03 已通过原操作 NOT_FOUND 恢复、异参摘要拒绝及普通成员没有写入入口的检查，开发/生产各十二项管理写入检查。回执 SHA-256 `022fa9de817d23564a1854fb7a32aad82318362f1f5aa71847414c1add3cf8f8`，inputs_unchanged=true；独立回执捕获 JUnit 与命令输出，完整业务和浏览器产物采用下方共享回执。API 保持服务器完整管理委派、当前认证绑定、固定岗位版本与批准依据，前端摘要只决定入口。邀请秘密不进入 URL、Storage 或检查文件；原请求恢复保留主体、企业、原上下文、动作、键、目标、原版本和完整参数摘要。

当前完整关联Green为`.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261007-CURRENT-APPLICATION-RESTORE-SHARED-02/receipt.json`，SHA-256为3c0ea17151f557e930efd9d17ce20d1640eb424e6706e42959c4f525dbe907c4，9700项输入、301项产物、inputs_unchanged=true。165项Java、131项Chrome、strict类型检查及medical构建通过，失败、错误及跳过均为零；29个同源入口核查通过。开发/生产分别完成十一项本人接受、八项管理读取和十二项管理写入检查；项目、岗位、任务、原账户、模板与框架权限共同复验。临时应用与前端关闭，生成数据清理完成，customer_success_verified=false；没有视觉或模型请求。

当前共享与十一项正式解析制品的全部输入、产物字节证明为`.implementation-work/APPLICATION-RESTORE-20261007-shared-final-evidence.json`，SHA-256为4c77dc43b2340f07e96d4c8e88f4c3a1e06d3f3e90343b238b052131e39cf96e；完整构建、常驻应用、对象恢复及关联八份回执证明为`.implementation-work/APPLICATION-RESTORE-20261007-final-evidence.json`，SHA-256为ca9534218cb1b6abb88a9665140cc65190da68c6405e8818ce24f56316f48ceb。339项完整Java构建与当前新Jar运行通过，222份静态HTTP字节与五项页面入口通过，运行身份由IMP-06维护。本文本节CURRENT、last_verified=2026-10-07，范围为完整成员页面的SIMULATION检查与上述关联功能。正式客户验收、完整项目隔离、任务创建、生产部署及全部父工单要求继续保留；IMP-04保持IN_PROGRESS，不新增ACCEPTED。以下回执仅保留各自原执行输入和范围。

## 成员管理的读取实现与原执行范围

本节读取行为保持当前实现；MEMBER-MANAGEMENT-SHARED-FINAL-01 回执只证明原读取阶段输入，当前完整关联证据读取前节。

BACK-MEMBER-GOVERNANCE-READ-01、FRONT-MEMBER-MANAGEMENT-01 继续使用本主记录，关联 FR-01、AC-01/02、ID-07—09 与 IMP-06/37；任务状态继续引用实施任务计划 IMP-04，IN_PROGRESS。已读取当前 PRD §8.1、接口补充设计 §4.10/4.11、LLD-前端 §1.3、EnterpriseAccess、PermissionAuthority、InvitationMapper、PermissionMapper、正式成员 HTTP 和本人接受页面。当前成员、授权及邀请读取接口和正式管理读取页面已实现；完整管理写入页面继续实施。

Given 当前企业身份与完整管理委派；When 分页读取成员、授权及邀请，并在邀请接受、岗位撤销和成员停用以后重新读取；Then 响应仅包含当前可管理对象及当前版本，不返回邀请秘密，重复读取没有业务写入。项目授权人员只读取自身完整委派覆盖的许可；本人普通项目读取许可不产生成员管理权限。旧上下文、旧分页水位、跨企业目标和非法查询明确拒绝。

读取采用当前企业事务及完整授权条件，在已配置容量内核查完整候选集合，按授权后的 UUID 顺序分页，分页水位绑定主体、上下文、筛选及全部可见内容。超过容量明确返回 CONTEXT_LIMIT。当前依据为补充设计中的 MEMBER-READ-01—03；成员读取复用完整 DISABLE_MEMBER 委派，授权读取复用完整 REVOKE 委派，邀请读取要求全部原动作及角色共同处于当前完整邀请管理委派范围。多个管理授权不组合局部条件。已撤销记录保持历史状态；分页数量和游标仅描述可管理内容。local 配置容量为10000，实际 HTTP 容量拒绝检查使用显式100；这些检查不证明生产性能阈值。

正式读取 HTTP 已通过 MEMBER-READ-FINAL-01，回执 SHA-256 `aa580e0c73aceb15aa7bcbe5710a5025a15d8673722dadacbaf692d7bf950d9f`，inputs_unchanged=true；完整成员流程同时通过。项目授权人员只取得委派范围内的 project.read，指定成员管理许可只返回唯一指定成员，其他成员不进入数量或游标；容量拒绝及 subject_id 受限查询通过。此回执保留原执行输入，当前整体读取后续关联回归。

正式页面 `/enterprises/members` 复用原 Layout、Router、Pinia、Axios、Element Plus、完整 Schema 和项目 JSON 显示。项目中心根据当前 member.manage 或 project.grant 提供入口，服务器仍执行完整委派核查。页面提供三类读取、账户 UUID 筛选、每页数量、原水位分页、完整记录和刷新；身份变更、页面隐藏、离开与断网清除受限内容并停止旧请求。查询不保存企业资料或邀请秘密。当前没有邀请创建、授权决定或成员停用的页面按钮。

| 文件与 identifier | 当前职责与关联 |
| --- | --- |
| `member-governance-read.schema.json`、MemberGovernanceReadSchema | 封闭查询、三类完整结果、原 Scope/Condition/Basis/角色引用和 Envelope |
| MemberGovernanceReadMapper、`medical-mappers/MemberGovernanceReadMapper.xml` | 企业内显式安全列、当前版本、主体筛选、容量和 UUID 顺序；邀请不查询凭据列 |
| MemberGovernanceReadService、MemberGovernanceReadController | 当前企业准入、完整管理范围、三项 GET、受限分页和旧上下文拒绝 |
| PermissionAuthority.findGovernance、TenantScope | 与原管理写入共同使用完整委派判断，维持企业查询保护 |
| `web/src/api/medical/member-governance.ts`、`enterprise-members.vue` | 完整读取响应校验、实际管理记录、分页与受限数据清除 |
| `protocol.ts`、Router、projects.vue、FrameworkFrontendConfiguration | 唯一 Schema 登记、正式入口和同源刷新 |
| MemberGovernanceSimulationHttpTest、SimulationTaskData、`member-acceptance-checks.js` | 实际 HTTP、开发/生产管理 DOM、生成数据清理及关联接受流程 |

读取接口有效 Red 为 MEMBER-READ-RED-01，SHA-256 `aff87cdb2a9932548dea79a74fbce7690b4944fec21c51c68c598a5936d46eae`；实际登录与企业选择通过以后，缺少读取接口的必需断言失败。页面有效 Red 为 MEMBER-MANAGEMENT-RED-01/command/receipt.json，SHA-256 `919dcc99cd60bb792322fba30923097a2ac5d1dab36db951a3257070580d90b1`；实际接受和企业选择通过以后，管理入口的必需 DOM 断言失败。两项回执均为 inputs_unchanged=true。

页面独立 Green 为 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-MANAGEMENT-GREEN-01/command/receipt.json`，SHA-256 `0524d9dfea01e50b58cda98744b98b8c5922c86b2185ef8dd1ee75adb7067961`，inputs_unchanged=true。strict 类型检查、medical 构建、完整成员 HTTP、开发/生产各十项接受与八项管理读取检查通过，Chrome 155.0.8059.39；该回执保留原执行范围。

当前关联 Green 为 `.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261007-MEMBER-MANAGEMENT-SHARED-FINAL-01/receipt.json`，SHA-256 `17f0f23df1bc5ba24ea4d9681944483492888d71d3bca3bc12a23e69e01cb4c8`，9649项输入、298项产物、inputs_unchanged=true。165项 Java、107项 Chrome、strict 类型检查和 medical 构建通过，失败、错误及跳过均为零；29个同源入口核查通过，Maven9分34秒。开发/生产分别完成十一项本人接受和八项管理读取检查，普通成员直接访问明确拒绝且不发送管理查询。项目、岗位、任务、原账户、模板及框架权限共同复验。

当前共享回执及十一项正式解析制品检查的全部输入与产物摘要一致，证明 `.implementation-work/MEMBER-MANAGEMENT-20261007-final-evidence.json`，SHA-256 `e12041da981ef14a8e24d24ec56126fb0556cb8392c47abd35c25839fec7b1dd`。临时应用及前端关闭，生成数据清理完成，customer_success_verified=false；没有视觉或模型请求，当前源码未常驻部署。本节 CURRENT，last_verified=2026-10-07，仅覆盖读取接口、读取页面及相应关联检查。后续章节中的较早回执保留原输入与执行范围，当前共享证明使用本节回执。完整邀请、授予、撤销、停用页面、客户验收与部署继续原范围，父工单保持 IN_PROGRESS。

## 正式邀请接受页面

FRONT-MEMBER-ACCEPT-01 使用本主记录，关联 ID-08、FR-01、AC-01/02 与 IMP-06/12/37；状态来源为实施任务计划 IMP-04，IN_PROGRESS。输入依据为 PRD §8.1、LLD-前端 §2/3、接口补充设计 §4.11、AcceptanceReceipt、SelectionOptions 及正式 Cookie/CSRF 组件。已检索当前成员 HTTP、SessionPanel、Router、原请求恢复、会话读取和企业选择实现。

Given 通过原 LOCAL-02 创建并登录的被邀请账户，实际邀请及批准岗位；When 使用正式页面接受、刷新、重放、处理写入中断并选择企业；Then 同一原请求提交，实际成员重读一致，当前项目与任务授权生效。错误凭据不显示成功，凭据不进入 URL、Storage、日志或证据。取消未保存输入、断网及身份失效清理受限内容。

页面通过已认证 tenant-selection-options 读取本人和当前成员，通过原 auth/csrf 读取安全凭据；两项读取不依赖当前企业上下文有效性。公开页面入口仅提供界面，接口仍验证实际登录。接受回执核查后重新读取本人候选和 ACTIVE 成员。明确企业选择复用原 ID-04；保存时仅保留本人、企业、邀请、原版本及原请求键，不保存邀请凭据或凭据摘要。刷新及连接中断后由用户再次填写原凭据，正式 ID-08 核查原请求并重新核验当前授权。本节 CURRENT，last_verified=2026-10-07，范围为 local 模式下的本人接受页面及关联回归；完整管理人员邀请和授权界面继续原范围。

有效 Red 为 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-ACCEPT-RED-01/command/receipt.json`，SHA-256 `7b9086e2410ac5af749b0675c5c70157f57d6b683c21e1b782855d236d761c53`，inputs_unchanged=true。完整应用、实际账户登录和项目页面读取成功，必需的接受邀请入口断言未通过。此前 MEMBER-SHARED-FINAL-01 证明对应页面开发前的输入；新的页面、共享会话服务、Schema 和正式路径关联检查由本节当前回执维护。

企业选择中断检查的有效 Red 为 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-SELECTION-RED-01/command/receipt.json`，SHA-256 `fb74f826b20f0a1768f07cb47a401a71a2ccaa37e3b89ac11097118db1b79ce7`，inputs_unchanged=true。实际接受、原响应重放、成员重读与 ID-04 保存成功；刷新后的原选择恢复断言未通过。当前选择流程保存独立原键和原上下文版本，重新读取本人候选后重放原选择，读取完整实际会话及水位一致后进入项目；两个模式的正式检查已通过。

独立 Green 为 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-ACCEPT-FINAL-01/command/receipt.json`，SHA-256 `dba5068f885a0f96fb5bf278e7c1f4dd8980f86b6811caaf7af78e1ceef51615`，inputs_unchanged=true。strict 类型检查、medical 生产构建、完整成员 HTTP 和开发/生产各十项 Chrome 155.0.8059.39 检查通过。浏览器使用两个通过 LOCAL-02 创建的账户、两项正式邀请和实际企业事务；两项接受操作、审计及 Outbox 各只保存一次。邀请及企业选择分别在实际提交后中断响应，刷新以后使用同一原键恢复；实际成员、项目及任务读取通过。生成记录清理完成，临时应用及前端关闭，customer_success_verified=false。

当前关联 Green 为 `.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261007-MEMBER-ACCEPT-SHARED-FINAL-01/receipt.json`，SHA-256 `e048984654636faba4ceba76b0da2eb006a210313b21a30657e7a476b029506e`，9637 项输入、292 项产物、inputs_unchanged=true。165 项 Java、89 项 Chrome、strict 类型检查与 medical 构建通过，失败、错误和跳过均为零；28 个正式同源入口已核查，Maven7分41秒。当前共享与十一项正式解析制品检查的全部输入和产物字节核查一致，证明 `.implementation-work/MEMBER-ACCEPT-20261007-final-evidence.json`，SHA-256 `b06f4b932008132b327d35ebdecc7b4182b923b68901d4b48c7972808fb67b41`。当前 JUnit 与共享证据使用此回执，以下集合保留原执行范围；常驻应用尚未更新。

| 文件及 identifier | 当前处理与关联 |
| --- | --- |
| `web/src/views/medical/invitation-accept.vue` | 本人邀请表单、原请求恢复、成员重读、明确企业选择、离开处理及秘密清理 |
| `web/src/api/medical/invitation-accept.ts`、`invitation-accept-view.schema.json` | ID-08、本人/邀请/版本及成员比较、原请求技术元数据、ID-04 原请求恢复 |
| `web/src/api/medical/session.ts`、`protocol.ts` | 原 CSRF 读取供登录与邀请复用；原企业选择接受经过正式接口核查的本人及 CSRF；唯一 Schema 登记 |
| `SessionPanel`、Router、`FrameworkFrontendConfiguration` | 当前账户区域入口、原 Layout、正式页面与同源刷新路径 |
| `MemberGovernanceSimulationHttpTest`、`member-acceptance-checks.js` | 原账户创建、实际 HTTP、两个模式的 DOM、当前授权消费者与唯一业务决定 |
| `scripts/check_member_simulation.py`、`scripts/check_framework_navigation.py` | 当前来源与产物、strict/构建、独立成员检查及受影响共享检查 |

字段传播核查：invitation_id/expected_revision/credential 使用 ID-08 唯一 Schema；operation_id/user_id/member_id/member_revision/grants/auth_epoch 完整保留 AcceptanceReceipt，实际成员从本人候选重读。tenant_id 由邀请输入与实际成员共同比较。ID-04 提交原 expected_context_revision 与原 Idempotency-Key，当前会话的 tenant_id/context_revision/auth_epoch/governance_epoch 必须与回执一致。邀请凭据和密码通过进程标准输入传递，证据只保存实际行为和结果。视觉检查未执行，成员操作没有模型调用。

## 2026-10-07 成员邀请与授权传播

BIZ02-MEMBER-SIMULATION-01 继续使用本主记录，状态来源为实施任务计划 IMP-04，保持 IN_PROGRESS。已检索本记录的 ID-07—09、MemberInvitationService、PermissionDecisionService、InvitationAccess、PermissionAuthority、岗位撤销消费者及原本地账户创建。依据 PRD §8.1、接口补充设计 §4.10/4.11/4.14；企业和成员测试沿用用户授权的 SIMULATION 分类，专业批准保持原要求。

Given 实际平台账户、完整企业绑定和明确成员治理范围；When 通过正式接口邀请成员、由被邀请人员登录接受、读取已授权项目任务、授予及撤销独立许可并撤销原岗位；Then 成员、发现路由、岗位来源、当前许可、原响应、同步审计及 Outbox 一致，撤销岗位终止相关待接受邀请并撤销相关授权，独立许可保持有效，旧上下文拒绝。停用成员之后企业操作拒绝，重放不恢复已撤销授权。

检查采用完整应用、真实 MySQL/Redis、Cookie/CSRF 与原账户 Controller。用于检查的人员账户通过 LOCAL-02 创建；检查结束清理本次生成的企业、账户和明确生成的框架权限关联。凭据仅保存在检查内存，不进入公共证据。成员治理界面、正式客户验收及完整 BIZ-02 继续保留；本节 CURRENT，last_verified=2026-10-07，范围为正式成员 HTTP 及关联检查。

独立正式 HTTP 检查已经通过，回执 `.implementation-work/IC-04-MEMBER-SIMULATION-20261007-MEMBER-HTTP-06/receipt.json`，SHA-256 `572a4154e62ff2e03e4b86f736515c37e91ddc33d910e2b4af4b0073f2eab504`，inputs_unchanged=true。实际创建三个账户、四项邀请、两项接受及五项权限决定；两项岗位授权撤销、一项相关待接受邀请终止、一项成员停用后的邀请终止均通过实际消费者核查。原批准版本能够继续接受，空动作岗位不产生权限；实际项目和任务读取、旧上下文拒绝、独立授权保留及撤销、停用后拒绝、原请求重放、凭据加密和生成记录清理同时通过。

关联 Green 为 `.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261007-MEMBER-SHARED-FINAL-01/receipt.json`，SHA-256 `73e2030683a3374afc46ac6ba6902dfd5c589444ededf44796144b161ebbb972`，9633 项输入、289 项产物、inputs_unchanged=true。165 项 Java、69 项 Chrome、strict 类型检查与生产构建通过，失败、错误及跳过均为零，Maven6分56秒。成员治理、岗位、项目、任务、原账户、框架服务与权限共同复验。全部共享及十一项解析制品检查的输入与产物字节核查一致，证明 `.implementation-work/MEMBER-20261007-final-evidence.json`，SHA-256 `c9a80ac4620e60762e279f4091f052a16adc79f3252782d90b1fbcf4ee5f7e6e`。临时应用及前端关闭，生成记录清理，customer_success_verified=false；以下集合保留其原执行输入范围。

| 验证项目 | 实际断言与证据范围 |
| --- | --- |
| LOCAL-02 → 原账户 → 实际登录 | 三个 USER 账户、未指定部门、实际 BCrypt 登录与原账户身份；没有预置企业权限 |
| ID-07 → ID-08 → 当前业务读取 | 原邀请固定版本及完整绑定、本人发现路由、两项授权来源、当前企业选择、实际项目/任务读取 |
| 空动作岗位 | 接受与成员启用成功，grants 为空，项目读取仍然拒绝 |
| ROLE-04 → 当前许可及邀请 | 两项原岗位授权 REVOKED、一个 PENDING 邀请终止、独立许可 ACTIVE、旧上下文拒绝且重新选择后权限结果一致 |
| ID-09 → REVOKE/DISABLE_MEMBER | 原独立授权撤销、成员停用共同撤销其授权并终止待接受邀请；当前会话或资源拒绝 |
| 原请求、事务与秘密 | 完整原响应重放不增加决定，异参与旧版本拒绝；11项成员操作、审计与Outbox数量一致；原邀请响应采用AES-256-GCM |

LOCAL-02 创建采用原 SysUserController、SysUserServiceImpl、BCrypt 及方法权限；接口没有部门、框架角色或者岗位输入，SysUser.deptId 保持 null，roleIds/postIds 明确为空集合。sys_user 保存未指定部门，sys_user_role/sys_user_post 不产生默认关联；平台 USER 元数据与原账户创建共同提交。既有账户的角色、部门及岗位保持各自当前记录，企业岗位和项目权限继续通过正式治理接口决定。

| 文件及 identifier | 当前职责与关联 |
| --- | --- |
| LocalAccountManagement、RuoYiAccountOperations、SysUserController | LOCAL-02 输入、原部门/角色/方法权限、原账户及平台身份共同创建 |
| SimulationAccountData、SimulationTaskData | 明确 SIMULATION 的实际框架权限与企业治理范围；仅清理本检查生成记录 |
| MemberGovernanceSimulationHttpTest、scripts/check_member_simulation.py | 实际登录、完整邀请接受、授权消费者、岗位撤销、成员停用、原响应与数据核查 |
| MemberInvitationService、PermissionDecisionService、EnterpriseRoleService | 原批准版本、成员路由、授权来源、当前水位、操作/审计/Outbox 同事务保存 |

## 2026-10-07 企业岗位完整操作

BIZ02-ROLE-SIMULATION-01 沿用本主记录，任务状态来源为实施任务计划 IMP-04，保持 IN_PROGRESS。已重新读取 PRD §8.1、接口补充设计 §4.14、EnterpriseRoleController/Service/Schema、PermissionAuthority、唯一接口 Schema、正式路由、项目写入及原操作查询能力。合法岗位发布、历史读取、修订、撤销及正式 Vue 页面已通过实际接口与浏览器检查。本节 CURRENT，last_verified=2026-10-07；完整成员治理继续实施。

Given 用户授权的 SIMULATION 企业、实际本机账户、完整成员绑定和明确岗位管理范围；When 通过正式接口与页面发布岗位、重新读取固定版本、修订当前发布、撤销指定版本并查询原操作；Then 保存完整条件、审批依据、同一事务中的版本、水位、操作、审计及 Outbox，重复提交不增加决定，越权与旧版本拒绝，刷新及中断恢复保留原请求身份。专业岗位批准和完整客户验收继续按各自来源执行。

当前入口为项目中心中具有 role.manage 许可的“管理企业岗位”，正式路径 `/enterprises/roles`。使用原 Layout、Router、Pinia、Axios、Element Plus 表单、表格、弹窗和描述列表。Spring MVC 提供同一构建的页面入口；直接访问后仍读取当前身份，缺少企业岗位管理许可时不请求业务列表。岗位标识、名称、原发布序号、动作、完整条件、明确期限、批准依据与声明均由操作人员填写，不默认建立岗位或授予动作。

JSON 输入由现有 jsonc-parser、JSON 和序列化组件处理，重复字段拒绝，完整 RoleDefinition 和 Condition 分别通过唯一 Schema。角色定义使用 json-canonicalize 核查 RFC8785 摘要；版本字符串通过 BigInt 检查。历史版本与当前发布分别展示，撤销目标为已读取的具体版本；新发布保持原 head 的 CAS 语义。

请求内容保存在页面内存。sessionStorage 只保存本人、企业、目标岗位、操作定义、原请求键、原上下文、版本和摘要。保存后依次重读当前会话、原操作、固定岗位及当前列表，通过以后清除原请求记录。保存后连接中断时不显示成功；恢复及刷新以后查询原操作，并由当前许可重新读取目标版本，不再次创建决定。身份失效、企业更新和页面隐藏清除受限内容；未保存离开通过原弹窗明确处理。

| 当前文件与 identifier | 职责与调用方 |
| --- | --- |
| `web/src/views/medical/enterprise-roles.vue` | 发布、修订、历史读取、撤销、原操作恢复及未保存输入处理 |
| `web/src/api/medical/enterprise-roles.ts`、`role-write-view.schema.json` | 完整接口字段、响应摘要、当前身份、原请求及完成核查 |
| `web/src/api/medical/protocol.ts` | 登记企业岗位、角色定义及原请求 Schema；既有调用方进入共享回归 |
| `web/src/router/index.js`、`projects.vue`、`FrameworkFrontendConfiguration` | 正式路径、当前许可入口及同源页面刷新 |
| `EnterpriseRoleSimulationHttpTest`、`SimulationTaskData` | 实际应用、正式 Cookie/CSRF、明确管理范围、真实事务和生成数据清理 |
| `tests/browser/role-simulation-checks.js`、`scripts/check_role_simulation.py` | 开发与生产 DOM、实际保存、中断、刷新及原操作恢复 |
| `scripts/check_framework_navigation.py` | 岗位 Schema/查询列、身份、项目、任务、原账户及框架的当前关联检查 |

有效 Red 为 `.implementation-work/IC-04-ROLE-SIMULATION-20261007-ROLE-BROWSER-RED-01/command/receipt.json`，SHA-256 `2e5fe3ffc8ad31ee5cf6765b06edcd55f44427209593f4f9ce41303645cc8f4c`，inputs_unchanged=true；实际本机登录和企业选择之后，正式岗位页面的必需断言未通过。

当前 Green 为 `.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261007-MEMBER-ACCEPT-SHARED-FINAL-01/receipt.json`，SHA-256 `e048984654636faba4ceba76b0da2eb006a210313b21a30657e7a476b029506e`。165 项 Java、89 项 Chrome、strict 类型检查及 medical 生产构建通过，失败、错误及跳过均为零；9637项输入、292项产物，inputs_unchanged=true，Maven7分41秒。岗位十四项浏览器检查包含必需输入、保存与重读、刷新、不可变历史、指定版本撤销、已经提交的写入中断及原操作恢复、断网内容清除与恢复。正式 HTTP 同时核查完整原响应重放、异参及版本冲突、越权、撤权、三项唯一操作/审计/Outbox。

共享回执及十一项正式解析制品检查的全部输入、产物字节重新核查一致，证明 `.implementation-work/MEMBER-ACCEPT-20261007-final-evidence.json`，SHA-256 `b06f4b932008132b327d35ebdecc7b4182b923b68901d4b48c7972808fb67b41`。临时应用与前端已关闭，生成数据已清理；SIMULATION 检查保持 customer_success_verified=false，没有视觉检查或模型请求，新源码尚未常驻部署。当前共享验证采用本节回执。

成员治理 HTTP、本人接受页面及岗位撤销传播由本记录相应内容维护；完整管理人员邀请、权限授予/撤销和停用页面继续按 ID-07—09 实施。真实客户岗位、专业审批、完整企业隔离、签发岗位分离与部署未通过本项关闭。父工单仍为 IN_PROGRESS，全部原验收要求保留。

## 2026-10-07 虚拟项目写入验证

当前执行 BIZ02-SIMULATION-01，状态继续引用实施任务计划 IMP-04。已检索本记录的 BACK-PROJECT-SERVICE-01、PJ-01—06、ProjectController/Service/Schema/Mapper、当前权限用途与实际项目记录结构；用户已授权企业、成员、项目和任务使用虚拟数据。采用实际本地账户、MySQL、Redis、Cookie、CSRF 和正式 HTTP，资料标注 SIMULATION，分类保持 INTERNAL。

Given 当前虚拟企业、有效成员及明确项目管理许可；When 创建项目、重新读取、修订属性、人工决定状态并使用原请求键重放；Then 固定版本、当前上下文、原响应、实际审计与 Outbox 一致，旧版本与异参拒绝，撤权以后读取拒绝。虚拟企业范围外的既有记录保持原内容；测试结束只清理本次生成的企业数据。完整企业岗位发布、成员治理及前端业务继续按原要求验证。本节 CURRENT，last_verified=2026-10-07。

正式 HTTP 的创建、不可变属性修订、ACTIVATE/PAUSE/RESTORE/ARCHIVE、原请求重放、异参及旧版本409、撤权403、六项操作/审计/Outbox 记录均通过。证据为 `.implementation-work/IC-04-PROJECT-SIMULATION-20261007-BIZ02-INITIAL-02/receipt.json`，SHA-256 `9fccc9f70ae54c06f059ec2e6365617c79a648df1d0061ba61e787f6157c0f9d`，inputs_unchanged=true；当前整体采用后续关联验证。

前端 Given 已授权虚拟企业和完整项目请求；When 正式接口返回201并完成会话、原操作和项目重读；Then 页面显示完成回执，清除已经核查的技术请求记录。原会话返回值和 Pinia 中当前会话使用同一引用，调用方仍检查当前账户、企业、路由、页面可见性和操作身份。项目、预算、资料集与模板写入共同消费 `readMedical()`，这些调用方进入关联检查范围；客户资料及专业批准继续按实际输入单独验收。

当前项目浏览器检查为开发五项、生产五项，Chrome 155.0.8059.39。检查覆盖未完整填写时禁止提交、创建与原操作/资源重读、未知属性及新属性版本、四种状态决定、刷新后的归档状态与当前动作；两个模式各执行六次实际项目写入，原请求记录在完成核查后清除。正式 HTTP 继续核查原响应完整 JSON、重放不增加操作、版本冲突、撤权以及六项同步审计和 Outbox。

| 当前代码与验证位置 | 职责与使用方 |
| --- | --- |
| `web/src/store/modules/user.js` 的 `readMedical()` | 返回当前 Pinia 会话引用，项目及其他写入页面执行当前身份检查 |
| `ProjectSimulationHttpTest`、`SimulationTaskData` | 完整应用、实际数据库、当前 Schema、明确项目许可、正式 Cookie/CSRF 与数据清理 |
| `tests/browser/project-simulation-checks.js` | 原 Element Plus 表单、实际项目请求、原操作及资源重读、开发与生产 DOM |
| `scripts/check_project_simulation.py` | 独立业务验证、当前源码与前端制品登记 |
| `scripts/check_framework_navigation.py` | 当前项目、任务及原账户/权限/页面共同回归 |

当前 Green 采用 `.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261007-MEMBER-ACCEPT-SHARED-FINAL-01/receipt.json`，SHA-256 `e048984654636faba4ceba76b0da2eb006a210313b21a30657e7a476b029506e`。165项 Java、89项 Chrome 检查通过，失败、错误与跳过均为零，medical 严格类型校验和生产构建通过；9637项输入、292项产物、inputs_unchanged=true，Maven7分41秒。项目成功回执的有效 Red 由 `BIZ02-BROWSER-RED-DIAGNOSIS-01/receipt.json` 保存，SHA-256 `85a393c75a9eec6154d3935c979094b6bbaa2604309a35c14e95b8ab5beb7abc`；实际 HTTP 与 DOM 的必需断言由完整输出维护。

当前回执及 IMP-08 正式解析制品的全部输入/产物字节重新核查一致，证明 `.implementation-work/MEMBER-ACCEPT-20261007-final-evidence.json`，SHA-256 `b06f4b932008132b327d35ebdecc7b4182b923b68901d4b48c7972808fb67b41`。临时应用及前端均已关闭，生成数据已清理，原账户与既有业务记录保留；customer_success_verified=false，没有视觉或模型请求。当前验收包含 BIZ-02 的项目操作、岗位管理、成员治理 HTTP 和本人接受页面，完整成员管理页面及客户隔离仍按原工单执行，IMP-04 保持 IN_PROGRESS；源码未常驻部署。

2026-10-06原登录同源发布由唯一[IMP-06](IMP-06.md)的FRONT-RUOYI-LOGIN-01维护。后端/auth/login使用原views/login.vue，Spring MVC及Maven处理页面与完整构建；当前三项真实Servlet与四组Chrome检查通过，原方法权限及十三项原服务同时复验。HTML入口不扩大/api权限，客户和管理表内容保持；原版本、幂等、审计、企业与项目授权要求保持完整。常驻JavaPID31485仍使用原制品，新源码未部署，全部账户管理入口及客户成功继续验收；较早整体输入或被重写JUnit证明为STALE。

2026-10-06原管理请求关联由唯一[IMP-06](IMP-06.md)的FRONT-RUOYI-REQUEST-01维护。当前原API经已有Axios进入/api，Cookie、CSRF、原权限403及原响应核查通过；完整关联156项Java、开发二十二组和生产十六组Chrome，以及两个资料/两个密码操作的真实数据库重读通过。原账户ACTIVE、revision5509、credential_revision350，临时应用关闭。完整管理员元数据、副作用、幂等、同步审计、企业/项目授权及客户成功继续原验收；新源码未常驻发布，较早整体证明或被重写产物为STALE。

2026-10-06原个人信息关联：唯一[IMP-06](IMP-06.md)维护原个人信息页面、userInfo、getUserProfile/updateUserProfile与LOCAL-07。资料写入由原SysProfileController执行，既有账户事务维护主体绑定、版本、幂等和同步审计；当前156项Java及开发十六项/生产十五项Chrome通过，实际原字段保存、恢复、重放、旧版本及主体变化拒绝通过。原LOCAL-02—06、资源/来源授权与当前任务读取同时复验，新源码尚未常驻发布，完整权限与客户业务继续验收。较早整体输入与被重写JUnit产物为STALE。

2026-10-06原密码组件关联：本人密码使用原system/user/profile/resetPwd.vue、usePasswordRule和updateUserPwd，LOCAL-06仍执行原SysProfileController及医疗版本/幂等/审计/会话事务。唯一[IMP-06](IMP-06.md)维护当前156项关联Java、开发十五项和生产十四项Chrome检查及真实MySQL原响应、唯一审计和实际登录版本变化。资源及来源授权、原框架与当前任务列表同时验证；原账户动作回归包括在当前集合。较早整体输入及被重写JUnit证明为STALE，原始执行范围保留。原个人信息、全部管理模块、客户授权及业务继续完整验收，新源码尚未常驻部署。

2026-10-06原登录页面关联：/access与medical构建的/login直接使用原views/login.vue和user.login，Cookie、CSRF、原账户认证及当前身份保持既有实现；唯一[IMP-06](IMP-06.md)维护当前122项Java与开发十项/生产九项实际Chrome检查。当前原账户动作与身份检查包含在该回执，账户动作FINAL-02的整体输入和JUnit产物为STALE，原始执行范围保留；完整常驻制品发布、原个人信息和全部管理页面、客户权限及全部业务继续验收。本机DeepSeek秘密文件仍为0600，父目录0700。

## 原账户动作与医疗身份扩展

BACK-RUOYI-ACCOUNT-ACTIONS-01沿用本主记录，任务状态读取实施任务计划IMP-04，保持IN_PROGRESS。依据用户明确的框架复用要求、接口补充设计§4.4/4.6和原SysUserController、SysProfileController，账户创建、资料、状态、管理员密码重置及本人密码更新直接执行原Controller。医疗适配负责业务UUID、平台角色、版本、当前会话、幂等和同步审计，不承担框架账户写入或密码算法。

已检索本记录、IMP-06、项目索引、原Controller/Service/Mapper、当前LocalAccountManagement及实际HTTP检查。Given真实本人账户、MySQL、Redis和完整MedicalApplication；When调用账户动作；Then原方法权限和数据范围执行，原密码规则执行，医疗版本、会话撤销、审计和重复请求与原操作保持同一事务。当前源码本地未提交，文档CURRENT、last_verified=2026-10-06，适用于本次原动作连接及实际关联检查。管理页面及其余框架模块、全部42工单与客户业务继续完整验收。

FrameworkAccountOperations由ruoyi-admin的RuoYiAccountOperations实现，连接原SysUserController和经过Spring注入/AOP的原SysProfileController；LocalAccountManagement只保存医疗身份扩展及原操作结果。原Validator、原方法权限和原DataScope继续执行；资料编辑保留当前真实角色与岗位关联。当前原PermissionService在业务状态判断和原响应重放以前核验原动作权限；原Controller执行时继续核验原PreAuthorize。SecurityUtils.encryptPassword读取应用BCryptPasswordEncoder，保持local工作因子12。公开LOCAL Schema和已有真实操作响应保持当前固定版本。

| 检查 | 实际结果与证据 |
| --- | --- |
| 原账户动作接入Red | 实际应用运行；一项必要组件断言失败，错误和跳过为零，15.67秒；`.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261006-RUOYI-ACCOUNT-ACTIONS-RED-01/command/receipt.json`；1b5c814882c99f418a7e5c7cb6a0eb94744fc71a34eae0947fef6536e2518e50 |
| 当前原权限准入Red | 使用真实本人资料和当前无原编辑权限身份，一项拒绝断言失败，错误和跳过为零；`.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261006-RUOYI-ACCOUNT-ACTIONS-PERMISSION-RED-01/command/receipt.json`；ff30913b4c54e03ad0e5f81c3572ddc6dae7bf64d1a0e6440e320db10d5f806f |
| 当前完整关联Green | 122项Java通过，42共享、36身份、38模板/项目和六项实际框架/Mapper/HTTP，失败、错误和跳过为零，Maven1分40秒；711项输入和44项产物；`.implementation-work/IC-06-FRAMEWORK-NAVIGATION-20261006-RUOYI-ACCOUNT-ACTIONS-FINAL-02/receipt.json`；855a19913312bb6f029afef8972d16c66b43c4595326833b0a73dbc3c3ee8165 |

FrameworkAccountMutationHttpTest使用真实本人账户，两次真实密码写入分别增加医疗版本、凭据版本、同步操作及审计；原请求重放返回原结果，异参409，两个旧会话403，错误当前密码及相同新旧密码401，缺少CSRF403，缺少原编辑权限403。原四条操作日志覆盖两次密码拒绝和两次写入，未包含秘密；受保护的本人凭据文件0600保持可用，恢复原密码后重新登录及读取成功。客户企业、项目、成员、许可、任务数量保持零。原用户/个人信息代理与当前BCrypt配置已经核查；合法管理员创建、编辑、状态及密码重置没有取得成功证据。

完整构建入口build_business同步登记account-actions-result.json；check_framework_navigation提供account-actions及complete模式。122项属于受影响工程及实际本机账户检查，完整clean verify、新制品部署、全部十八框架模块、原管理页面和客户业务继续未验收。当前运行仍为JavaPID31485、部署339ea611-75dd-4718-a50d-b4a11e69a20c，新增源代码尚未部署；对象容量、既有归档批准问题及实际环境由implementation-environment维护。较早全部源码证明、独立Green和原角色121项整体范围为STALE；034实际迁移保持原执行来源，本次没有再次迁移。本次没有Vue、浏览器、视觉或模型请求。

当前来源、许可和唯一开发记录五项检查通过，19项输入和一项产物，回执`.implementation-work/IC-02-LOCAL-20261006-RUOYI-ACCOUNT-ACTIONS-INPUTS-01/receipt.json`，摘要39f7ba6a0fb6477818f2f05e6d5f4c3528662d95a9e56d754407ec0b05bfd020。最终重读当前122项、两项原密钥配置及本次五项输入检查的全部输入/产物字节，三份回执均相等；证明为`.implementation-work/RUOYI-ACCOUNT-ACTIONS-20261006-final-evidence.jsonl`，734项输入记录和46项产物记录，包含重复绑定。LocalAccountManagement不再执行原账户Service写入或密码计算，原动作通过当前组件执行；完整原接口/页面及全部业务仍按原要求验收。

2026-10-06原角色Controller关联：当前源码直接注册原十五项动作和TokenService；实际独立框架密钥、原依赖和当前原方法权限由唯一[IMP-06](IMP-06.md)维护。当前Cookie会话继续逐次读取原角色及菜单权限；医疗UUID、业务成员与企业许可保持原要求。合法角色授权、撤权、并发和后续医疗权限尚未取得完整业务证据，完整账户原页面、版本/幂等/审计、原个人资料和扩展关联继续实施；新源码尚未部署，旧整体证明保持各自原范围。

2026-10-06原管理Controller关联：正式MedicalApplication直接注册十个原管理Controller，原方法权限、数据范围和当前Cookie会话检查继续使用既有组件；全局管理入口额外要求当前PLATFORM_ADMIN身份。唯一[IMP-06](IMP-06.md)维护原正式路由与原表内容检查。完整账户管理页面、原角色及个人资料入口、医疗UUID扩展关联、同步审计、幂等、企业授权和客户业务仍须完整验收；源代码尚未部署，当前关联整体证据读取本次新回执。

2026-10-06原框架关联：真实MySQL已应用034原管理表迁移，236项changeset和全部二十张框架表由唯一[IMP-03](IMP-03.md)维护，原用户/角色/菜单及全部既有数据校验一致。RuoYiAccountConfiguration的十三项原system服务注册和实际Spring检查由唯一[IMP-06](IMP-06.md)维护；原独立会话、业务UUID、企业授权及全部业务要求保持完整。已有整体源码证明对当前新增为STALE，当前关联回归须重新执行；当前运行仍使用原部署制品，完整管理Controller及页面尚未完成。

2026-10-06BACK-RUOYI-METHOD-SECURITY-01在原RuoYiAccountConfiguration启用原EnableMethodSecurity，原PreAuthorize、PermissionService、LoginUser和数据范围服务保持原实现。当前真实Cookie调用原用户列表及详情均为403，匿名和注销后401，原导航200；119项关联Java包含36项身份与38项模板/项目回归，实际原权限Advisor已经核验。原框架权限不能提供企业许可，完整管理Controller、BX-25、企业范围和审计仍需完成；唯一[IMP-06](IMP-06.md)维护证据。新源码尚未部署，完整Java构建及原31项账户/五项已部署框架回归未取得新部署结果。

2026-10-06BACK-RUOYI-NAVIGATION-01直接使用原角色、菜单权限及菜单树服务；独立账户准入在原当前事务中重新核查，医疗UUID与原框架编号分别保持。Vue中的原全局roles/permissions来自原框架，医疗动作继续使用业务Session；原动态路由转换和失效清理由唯一[IMP-06](IMP-06.md)维护。当前角色/菜单管理的完整动作、环境绑定、企业范围、审计及客户验收保持原要求，新源码尚未部署。

2026-10-06BACK-TEMPLATE-APPLICABILITY-01的固定属性读取复用ProjectService.fixedProfile及原ProjectSchema、ProjectMapper和PermissionAuthority；主记录[IMP-11](IMP-11.md)。当前Catalogue及两份身份manifest共同绑定十一份Schema，原IdentityErrorResponseWriter支持RESULT_VERSION_MISMATCH。当前36项身份、42项共享及其余模板/项目/实际Spring HTTP合计114项通过，真实本机登录、注销和无企业拒绝有效。新代码尚未部署，较早包含上述源码的整体输入证据为STALE；原项目、账户及全部客户权限业务保持原验收要求。

## BACK-PROJECT-PAGINATION-01

状态来源为[实施任务计划](../../实施任务计划.md)中的IMP-04，保持IN_PROGRESS；2026-10-06。依据用户要求复用框架能力、PJ-01/PJ-04/PJ-05/PJ-06及接口补充设计§4的项目治理要求，项目列表、待办列表和项目变更影响中的任务与运行查询使用原PageHelper。原limit/after、授权过滤、同范围total、影响完整性及固定排序保持完整；没有数据库结构变更。

已检索本记录、索引、ProjectService、ProjectMapper、MedicalPersistenceConfiguration、CollectionTest、ProjectSchemaTest及真实数据库检查入口。当前已有PageInterceptor、企业拦截器及独立数量查询，直接复用；业务UUID、当前企业事务、主体参数、权限、操作、审计和Outbox继续采用原实现。主记录适用于本次项目分页，整体发布及客户业务仍需独立验收。

Given真实本机MySQL及当前平台账户，未建立企业或客户项目；When原MySQL分页实现处理四类项目查询、全部原筛选和两组分页参数；Then查询可执行、返回完整列，数量来自相同查询范围，原SQL没有重复分页。When未经企业事务调用分页查询；Then当前企业保护拒绝查询，PageHelper清除线程分页状态。工程检查不证明客户项目成功创建或权限隔离的全部业务验收。

| 文件或identifier | 职责与使用方 |
| --- | --- |
| ProjectService.list / waiting / impact | 原项目及待办入口、属性和状态决定使用PageHelper生成分页，原计数查询保持同一范围 |
| medical-mappers/ProjectMapper.xml | 原筛选、游标和固定排序使用框架分页SQL；原企业协调行锁保护同一事务中的数量与列表，项目详情和变更继续锁定原项目记录 |
| ProjectPaginationTest / check_project_pagination | 真实MySQL查询及列映射、原分页数量、企业拒绝和线程状态核查 |

ProjectService.list和waiting各使用PageHelper.startPage(1,limit+1,false).doSelectPageInfo；impact分别查询最多1000项任务和运行。分页参数不进入Mapper的业务过滤，原数量查询继续使用相同范围，complete根据实际数量和返回集合判定。所有入口保持EnterpriseAccess/TenantTransaction的原企业协调行锁，ProjectMapper.project继续FOR UPDATE。原PageHelper 6.1.1的MySQL分页行为已读取[完整官方实现](https://raw.githubusercontent.com/pagehelper/Mybatis-PageHelper/v6.1.1/src/main/java/com/github/pagehelper/dialect/helper/MySqlDialect.java)，没有新增分页引擎或SQL解析器。

| 检查与实际范围 | 结果及原始证据 |
| --- | --- |
| 实际分页Red | 三项中一项业务断言失败，错误/跳过为零，2.921秒；PROJECT-PAGINATION-RED-01，回执3aaebf6f1f18340f04a003d1970b3ba4157ceedcfd3cf91d9350b46b4be4c0fa |
| 当前项目分页与原项目检查 | `scripts/check_project_pagination.py --suite project --execution 20261006-PROJECT-PAGINATION-GREEN-02`；七项通过，失败/错误/跳过为零，构建19.041秒；分页三项2.366秒、原Schema四项11.30秒，包括全部26项项目SQL的原解析与列核查；[回执](../../.implementation-work/IC-04-PROJECT-PAGINATION-20261006-PROJECT-PAGINATION-GREEN-02/receipt.json)，SHA-256 aa15ca48fa4eb3a2d30cfbdc681ef8d5bc002372bdda56aef004f3396df580ba |
| 当前输入、索引与产物 | 项目检查361项输入/四项产物及输入检查19项输入/一项产物的实际SHA-256匹配；输入、来源许可声明及唯一开发记录链接五项0.235秒通过，回执d3348f5124605241c06b618e0e9b2994d2087c14bf9a89fe6e1c81fdc81b1f06；[证明](../../.implementation-work/PROJECT-PAGINATION-20261006-final-evidence.json)，SHA-256 6f6ee2e680ef6b18974482ccbe29a33a3b9e65a35f37b2ec691300efbe718ced；范围CURRENT_PROJECT_FRAMEWORK_PAGINATION_AND_SCHEMA_EVIDENCE_BYTES |

代码保持本地main未提交，文档CURRENT、last_verified=2026-10-06，适用于上述源码与工程范围。检查包含四类查询、两组原筛选及两页的十六次真实分页查询、八次同范围数量核查，以及启用/关闭原数量机制的八次企业拒绝和线程状态清理；没有建立客户数据。原JavaPID31485及对象PID62380保持运行，最新只读环境证据PROJECT-PAGINATION-20261006-environment.json显示226项迁移、业务计数零、登录200、可用空间5186576384字节及ReadOnly=false。该元数据检查没有重新证明对象写入、完整Java构建和发布容量；账户、项目分页及资料集新代码尚未部署。

项目源码与索引变化使受影响的较早整体输入证明为STALE，各回执只保持其注明的原范围。合法企业与客户项目、完整公开TK-01、发布及全部工单验收继续原目标；当前工程检查不登记ACCEPTED。关联[IMP-06](IMP-06.md)、[IMP-12](IMP-12.md)及原BACK-PROJECT-SERVICE-01。DeepSeek本地check再次返回configured=true，秘密引用保持原受保护文件，没有新增模型请求。

## BACK-RUOYI-ACCOUNT-PAGINATION-01

状态来源为[实施任务计划](../../实施任务计划.md)中的IMP-04，保持IN_PROGRESS；2026-10-06。依据用户明确要求直接复用框架已有能力及接口补充设计LOCAL-01，账户列表使用已有PageHelper和MyBatis。原RuoYi账户服务继续提供用户资料及凭据，平台身份扩展View只补充业务UUID、平台角色和版本。limit/cursor、当前会话核验、PLATFORM_ADMIN权限、原递增顺序及封闭响应保持完整。

已检索本记录、索引、LocalAccountManagement/Controller/Protocol、RuoYiAccountConfiguration、原SysUserServiceImpl/DataScopeAspect、PageHelper及真实账户HTTP检查。原框架Mapper与平台身份扩展查询使用同一masterDataSource；企业查询继续由ProtectedTenantInterceptor保护。Given真实本人账户和原MySQL；When执行账户查询与原PageHelper；Then返回实际账户、游标之后的真实结果，并核验线程分页状态清理。当前真实账户分页及全部身份检查通过，完整构建与新部署需要恢复存储容量；没有创建客户账户或模拟数据。

LocalAccountManagement.list直接调用PageHelper和LocalAccountMapper.selectAccounts；框架负责分页SQL及线程状态清理。RuoYiAccountConfiguration加载原system Mapper及唯一local-identity-mappers/LocalAccountMapper.xml，并注册原PageInterceptor。只在当前会话、账户版本和PLATFORM_ADMIN核查以后读取实际View；原七项AccountItem、limit/cursor、递增顺序和最多一项游标判断保持完整。新增、停用、资料和凭据写入继续调用原ISysUserService/SysPasswordService，企业资料授权保持原独立准入。

| 当前检查与范围 | 实际结果及原始证据 |
| --- | --- |
| 真实账户分页Red | 三项行为断言失败，错误/跳过为零，2.165秒；[回执](../../.implementation-work/IC-04-ACCOUNT-PAGINATION-20261006-ACCOUNT-PAGINATION-RED-01/receipt.json)，SHA-256 5a63341e77e96649c7fdfc35297a2b6b73d4cb48f638bd4b0bd79cb3fa0fdbb8 |
| 独立分页Green原始范围 | 三项实际MySQL/PageHelper检查通过，2.295秒；[回执](../../.implementation-work/IC-04-ACCOUNT-PAGINATION-20261006-ACCOUNT-PAGINATION-GREEN-01/receipt.json)，SHA-256 d08e339d7c992676131e6086bfd52f7ea09641398520b527f2dc7fd7e5082fcd；脚本范围随后扩展，当前结果读取IDENTITY-02 |
| 当前全部身份检查 | `scripts/check_account_pagination.py --suite identity --execution 20261006-ACCOUNT-PAGINATION-IDENTITY-02`；全部35项身份及42项共享检查通过，失败/错误/跳过为零，23.891秒；分页三项2.012秒。原账号、OIDC配置、身份清单、Schema及原管理依赖继续检查；[回执](../../.implementation-work/IC-04-ACCOUNT-PAGINATION-20261006-ACCOUNT-PAGINATION-IDENTITY-02/receipt.json)，SHA-256 b9b2bc30cc4cae4c7beda57271c090c040e73ab1986aa9ee456d3c90696e8555，318项输入和两项产物 |

当前应用仍为PID31485及SCOPE-SELECTION原制品；账户分页代码尚未发布，实际HTTP与原框架Servlet回归保持未执行。COLLECTION-NATIVE-05整体构建未通过，原对象写入受到磁盘容量保护；其身份35项与资料集八项通过，失败输出读取`.implementation-work/IC-03-JAVA-20261006-COLLECTION-NATIVE-05/output.log`。待存储容量满足原保护规则并保留构建/发布空间后重新构建全部Java、发布并运行31项账户、五项原框架、七项资料集与五项任务HTTP及制品核查。当前资料集实现与原始308项构建读取唯一[IMP-10](IMP-10.md)；原完整证据对修改后的整体源码为STALE。全部42工单及客户业务保持原验收范围。

2026-10-06当前来源选择及整体制品由唯一[IMP-12](IMP-12.md)维护。SCOPE-SELECTION-NATIVE-01完整300项、账户三十一项33.771秒及原框架五项51.813秒通过；635项Native输入和8项产物、JavaPID31485及原身份/框架类字节一致。原业务UUID及框架编号100保持身份。RuoYi密码、账户、角色、菜单、数据范围与Spring Security/Session直接使用原实现，原PermissionAuthority与ResourceReadAuthority保持同一判断路径。MySQL保持226项迁移；客户业务许可及全部管理模块保持未验收，下列集合保留各自原输入与制品范围。

BACK-RUOYI-RETRY-AUTHORITY-01继续本记录，状态来源为实施任务计划中的IMP-04，保持IN_PROGRESS。依据用户明确的框架复用要求及接口补充设计§4.4，密码失败计数、锁定期限与解除均由原SysPasswordService负责；MySQL元数据保存审核信息，登录不另行根据审核信息判断锁定。检索当前LocalAccountDirectory、LocalPrincipal、原UserDetailsServiceImpl/SysPasswordService、真实账户检查及本记录；公开接口、业务UUID、凭据版本、账户停用及会话撤销要求保持完整。验证使用实际本机账户、原Redis与MySQL、部署Jar和完整Servlet应用，不建立客户企业或人员记录。

Given实际本机账户及原五次失败配置；When真实HTTP达到原重试上限，并通过原SysPasswordService清除该账户的失败记录；Then原服务解除锁定后正确密码能够登录，审核快照不承担第二次锁定判断。五次失败及正确密码在上限时返回401；原服务清除以后正确密码返回302，账户读取200，审核计数归零，注销204。检查从当前部署Jar启动完整Servlet应用，使用原Redis、MySQL和实际账户，结束后关闭检查应用。文档CURRENT，last_verified=2026-10-06，适用于本增量源码、构建和实际运行；完整42工单及客户业务验收保持原范围。

当前RETRY-AUTHORITY-NATIVE-02完整292项Java通过，contracts42/data1/identity32/persistence217，失败、错误和跳过为零，构建2分28秒，627项输入及8项产物绑定实际字节。JavaPID23630、UID501、部署202b55a7-c6f9-442e-9e64-60ca68c8810d、0400不可变Jar摘要d250ec389442b3fa0408c2e3398787e23a79c0c2377cfca9af56ff067626f530，启动15.505秒；独立登录入口http://127.0.0.1:18080/auth/login。实际本人业务UUID、框架编号100及原角色和菜单权限保持原身份。MySQL225项迁移、152张企业表、17张平台基表、10张框架表及一项兼容View保持完整。实际存储容量状态保存于`.implementation-work/storage-capacity-20261006-RETRY-AUTHORITY.json`，原SeaweedFS进程和服务身份保持，两个Volume可写。

| 检查与实际范围 | 证据及SHA-256 |
| --- | --- |
| 有效Red；一项真实Servlet/Redis/MySQL检查，原服务解除锁定后的登录行为断言；退出1，输入未变 | `.implementation-work/IC-04-RUOYI-PASSWORD-AUTHORITY-20261006-RETRY-AUTHORITY-RED-02/receipt.json`；a6c20ef3dbd17b799ac0522ebcf7aad4999d912ae8b3b0d70417bcb12104e45c |
| 完整原生构建；292项，无失败、错误或跳过；627项输入及8项产物 | `.implementation-work/IC-03-JAVA-20261006-RETRY-AUTHORITY-NATIVE-02/receipt.json`；747337663d966c1f219cbbc440433c525c9bf8fb0bdc35f3db66278983aba9fa |
| Green；五项实际原账户、密码计数、原表字段、身份及数据范围、锁定与解除；54.490秒 | `.implementation-work/IC-04-RUOYI-20261006-RETRY-AUTHORITY-GREEN-01/receipt.json`；001a1651bf03da1d51f3731c7bf1ff1104ebb7931eee9b9ef2be24a3671acd9c |
| 实际账户回归；31项，32.382秒；密码管理及重放、即时撤权、恢复、CSRF、Origin、会话和企业拒绝 | `.implementation-work/IC-04-LOCAL-HTTP-20261006-RETRY-AUTHORITY-RUNTIME-01/receipt.json`；4ba126ece32f855515c19c003ff391dafa4e89b37a3997aa496bb1a64d2dbc0b |
| 当前部署；实际PID/Jar、627项Native输入、8项产物、三项身份类、两项原框架类、七项任务/来源类和独立迁移资源字节 | `.implementation-work/IC-12-DEPLOYMENT-20261006-RETRY-AUTHORITY-DEPLOYMENT-01/receipt.json`；774a90d9e0ba11ead71a7af7d9489db6a1ef3530fa4e40c5cc4eb5720fc25b8a |
| 当前输入与记录；五项，0.218秒；环境、实施计划、唯一索引和资料引用 | `.implementation-work/IC-02-LOCAL-20261006-RETRY-AUTHORITY-INPUTS-01/receipt.json`；bdb5b6c76ae3c9b7ae7a7189871429873de7bb6ea4d0d7657a8db33e35c15f3c |

生产位置为LocalAccountDirectory.loadUserByUsername、LocalPrincipal及原SysPasswordService/UserDetailsServiceImpl；RuoYiPasswordRetryIT与test_original_password_service_controls_lock_and_clear执行真实检查，check_ruoyi_accounts提供password-authority和完整runtime入口。业务凭据版本、账户状态及会话关联继续每次核查；医疗企业授权保持原EnterpriseAccess。身份源码与制品变化以后，TASK-SOURCE-CANDIDATE的构建、运行和部署集合对当前身份版本为STALE，任务/来源组件及存储结构按原范围读取[IMP-12](IMP-12.md)。完整管理入口、客户企业成功、来源、任务、生成、编辑、签发、导出以及部署与交接继续实施。账户回归更新本人密码，后续读取受保护的owner-credentials.json，不输出秘密。

最终独立登录入口实际返回HTTP200，完整文本响应保存于`.implementation-work/RETRY-AUTHORITY-20261006-login.html`；没有执行视觉检查。DeepSeek秘密引用保持`.implementation-work/runtime/deepseek-api-key`，实际文件0600且非空，没有新增模型请求。下一可执行业务继续TK-01/02、固定来源快照、计划保存及人工决定；直接采用现有ResourceBindings.TASK_DETAIL、ResourceProtocol.TaskDetail和Requirement，权限、会话、JSON及企业控制继续使用已有成熟组件。完整工单保持9 IN_PROGRESS/1 IMPLEMENTED/32 TODO，业务和非功能保持36 NOT_RUN/38 BLOCKED；工程检查不登记客户业务PASS或工单ACCEPTED。

## 已有实现与各执行集合的原始范围

本节中的执行身份、制品、数量和验证结果分别对应其注明的集合；当前任务组件和部署证据使用IMP-12。

BACK-RUOYI-DATA-SCOPE-01继续本记录，状态来源为[实施任务计划](../../实施任务计划.md)的IMP-04，保持IN_PROGRESS。依据用户明确的框架复用要求、PRD§8.1、接口补充设计§4.4及原SysUserServiceImpl、SysDeptServiceImpl、SysRoleServiceImpl和DataScopeAspect，直接注册原数据范围组件，并采用Spring AOP的exposeProxy配置支持原服务内部调用。检索了本记录、索引、IMP-06/37、当前认证配置和真实运行检查；用户、角色、部门及范围算法继续使用原框架，医疗企业授权保持既有独立核查。文档CURRENT，last_verified=2026-10-06，适用于当前源码、制品和已执行工程范围；当前代码全部未提交，版本依据采用实际输入SHA-256、运行制品及完整回执。

Given：本机实际所有者通过独立登录建立Redis会话，原框架编号100、没有原角色或菜单权限，原MySQL保留实际本人账户；When：原ISysUserService查询用户及执行checkUserDataScope，原部门和角色服务查询数据范围；Then：原DataScopeAspect根据当前身份限制结果，客户端params.dataScope不能改变结果，服务内部调用继续经过原代理。检查只使用实际账户、原数据库和当前会话，不创建企业、人员、角色或批准依据。Red、Green、完整构建、运行版本及受影响检查证据已经登记；完整管理入口及客户企业成功验收保持全部要求。

| 当前位置 | identifier | 处理与使用方 |
| --- | --- | --- |
| `business/medical-identity/src/main/java/com/ruoyi/medical/identity/RuoYiAccountConfiguration.java` | `DataScopeAspect`、`EnableAspectJAutoProxy(exposeProxy=true)` | 直接加载原范围组件；原用户、部门和角色服务通过Spring代理执行，原内部核查继续使用AopContext |
| `business/ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/DataScopeAspect.java` | `doBefore`、`dataScopeFilter` | 保持原框架实现，读取原LoginUser及实际Servlet权限上下文，清除客户端范围参数并执行原角色范围规则 |
| `business/medical-identity/src/test/java/com/ruoyi/medical/identity/RuoYiNativeIdentityIT.java` | `ActualDataScopeController`、`verifyOriginalDataScope` | 从部署Jar启动完整原应用和本机临时Tomcat，使用实际Cookie、Spring Session、原Druid及MyBatis执行查询；检查Controller仅在test-classes，应用在结束时关闭 |
| `tests/test_ruoyi_accounts_runtime.py` | `test_current_identity_uses_original_framework_security_and_uuid_session_index` | 核查实际部署不提供工程入口，验证原身份、HTTP查询结果、范围参数清除、内部代理拒绝和检查应用关闭 |

有效Red为`IC-04-RUOYI-20261006-RUOYI-DATA-SCOPE-RED-03`，四项中三项通过、一项数据范围断言失败，错误为零；回执SHA-256为0417a37dd4ec32bf395ffcb16812795c8074509d7228a678bd248416bd42f6ee，实际输入在执行期间保持。独立Servlet Green为`IC-04-RUOYI-20261006-RUOYI-DATA-SCOPE-GREEN-02`，四项24.857秒通过，回执92be9f41fbdcde9c65ad73ab155cf9e1eb42e67d5a5cd8cc0dc3f19fcbed1241；实际HTTP端口61385，原用户、部门和角色结果均为零，当前业务UUID及原编号100匹配，检查应用已关闭且原标准注销完成。独立集合保留各自执行时的输入范围，当前全部源码、部署及共享回归以最终集合核查。

最终`RUOYI-DATA-SCOPE-NATIVE-02`完整275项Java通过，contracts42/data1/identity32/persistence200，失败、错误和跳过均为零，构建2分20秒，618项输入及8项产物已经核查，回执951fc1703f80746f9d0e453de4d49901933811dcfffb9aa1e221f8c4cffdaa83。实际JavaPID10793、UID501、部署3656cb2a-4ace-4d04-a62d-96e4cbdb32cd、0400制品ed114748e3771ad7e4434d0e64debde70a588b7b017d7894c1bddc6d3bfc03c5，启动16.376秒。MySQL222项迁移、原Redis、对象服务及D0保持现有身份；没有迁移或客户实体写入。十八管理模块、全部客户业务和批准环境/接收仍需完整验收。

`scripts/check_full_regression.py --execution 20261006-RUOYI-DATA-SCOPE-FINAL-01`完整30组通过，起止2026-10-05T18:38:07.483863+00:00至2026-10-05T18:51:13.731742+00:00，完整摘要f19380c5c68f43fda3dc3e96b121caac43f4a0440c101f130189260ba66887ae。正式会话、登记及审查开发16项/生产13项，本人账户各五项，模板各七项，31项账户HTTP30.596秒、四项框架28.502秒、九项模板HTTP6.645秒及其他既有检查通过。当前框架检查回执f29e32594837c436523b5fd88ee9bfaee92632d08b6b52cc747360a8b3a490a7，364项输入及5项产物；实际部署工程路径返回404，检查应用端口64330使用实际原会话，原数据范围、内部代理、关闭及标准注销均通过。30组与当前275项原生共31份CURRENT_EVIDENCE_BYTES证明为[verified-evidence.json](../../.implementation-work/FULL-REGRESSION-20261006-RUOYI-DATA-SCOPE-FINAL-01/verified-evidence.json)，SHA-256为a401f022f97f8dd611b9c271f74b3981a7132b84ef693e12c927ea015bad3a93；当前源码、产物及Java/对象双服务身份均匹配。

全部42工单仍为8 IN_PROGRESS/1 IMPLEMENTED/33 TODO，57业务及17非功能仍为36 NOT_RUN/38 BLOCKED，客户业务PASS和工单ACCEPTED均为零。全部18边界、12EVAL、30SEC、条件需求及批准部署、监控、完整备份恢复和接收保持原范围。工程回归不关闭客户业务验收；框架当前无角色拒绝已经验证，合法部门/角色范围及十八模块的完整企业消费者继续按获授权真实资料验收。十份本轮维护的主记录、关联记录、命令、报告及续接与31份输入路径交集为空；索引、设计、计划及环境已在回归以前更新。较早集合按各自原始输入与运行范围读取。

2026-10-06FRONT-ENTERPRISE-REVIEW-01正式人工审查入口由唯一[IMP-06](IMP-06.md)维护，直接调用原LOCAL-REG-04—06，当前平台身份与企业正文许可分别核查。原Java制品、275项原生回执、数据库及Schema保持原字节；ENTERPRISE-REVIEW-FINAL-01完整30组及31份当前输入、产物和双服务身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261006-ENTERPRISE-REVIEW-FINAL-01/verified-evidence.json`。31项账户HTTP29.330秒、四项原框架11.596秒通过；原LoginUser/SecurityUtils/ss继续直接复用。真实企业批准、治理授权、原请求恢复及完整系统管理Controller、菜单和审计接入继续实施与验收，较早集合按原始范围读取。

BACK-RUOYI-NATIVE-IDENTITY-01继续本记录，来源为用户明确的框架复用要求，依据接口补充设计§4.4及IMP-06的原管理组件依赖；任务状态读取实施任务计划，保持IN_PROGRESS，文档CURRENT、last_verified=2026-10-06，范围为当前源码与工程运行。已读取原LoginUser、SecurityUtils、PermissionService、UserDetailsServiceImpl、SysUserController，以及当前账户、认证、Spring Session、业务身份消费者和真实运行检查。认证主体继承原LoginUser，医疗扩展仅关联业务UUID、凭据版本、摘要、平台角色和锁定快照；原账户、部门、角色及菜单权限保持原框架语义。本人账户读取直接使用原SecurityUtils，原ss组件共同加载，每次受保护请求核验当前账户及会话以后通过原服务刷新角色和菜单权限。业务UUID作为明确的Spring Session索引保存，原框架用户名保持原定义；企业资料授权仍由原EnterpriseAccess执行。既有会话需要完整原身份及医疗绑定才能使用，缺少绑定必须拒绝并重新登录。公开接口和Schema、数据结构保持完整。当前275项Java、四项真实框架检查、全部30组本地回归及31份当前证据核查通过；十八管理模块、全部客户业务和批准部署/接收保持未验收。

当前RUOYI-NATIVE-IDENTITY-NATIVE-01完整275项Java检查通过，contracts42/data1/identity32/persistence200，失败/错误/跳过为零，构建2分29秒；618项输入及8项产物当前字节匹配。回执`.implementation-work/IC-03-JAVA-20261006-RUOYI-NATIVE-IDENTITY-NATIVE-01/receipt.json`，SHA-256为053b73f99f207e51b8e3e1c38983c1a7dfe872f2d626d7a479bbb9ba14b70978。JavaPID2646、UID501、部署UUID822f6955-a037-400c-870b-6689c26ab557，0400不可变Jar摘要dccbafd34ab430835bbb27bb06ec75e5010e16d9379b592a5c76757415b71bfc，实际启动14.238秒。原本机用户名liuhaoxian、框架编号100及业务UUIDcdb10f6b-d781-47f8-9666-a3489f62f165在实际HTTP、原SecurityUtils及Redis认证对象中相等；明确UUID索引、原角色和权限、标准注销均已核验。当前没有原管理角色或权限，没有使用管理员编号1或添加授权。

当前RUOYI-NATIVE-IDENTITY-FINAL-01完整30组回归通过，起止2026-10-05T16:46:11.069256+00:00至2026-10-05T16:58:14.120989+00:00。31项账户HTTP29.935秒、四项框架检查11.789秒、九项模板HTTP6.337秒；正式会话与企业登记开发13项/生产10项、本人账户各五项、模板创建与审查各七项，以及原接口/绑定/授权/迁移/共享定义检查通过。框架回执30a0d84825e9d38bbeb0470ba86ab2dc35d7d4188b7f0188669ba3067a2072ed绑定363项输入和5项产物；完整回归摘要7230ed9c2d8d098ce252e66a20c34bd12964bd286b5fae8ed0e8db103d9cac16。30组与Java合计31份当前输入、产物、Java与对象运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261006-RUOYI-NATIVE-IDENTITY-FINAL-01/verified-evidence.json`。本记录及关联IMP-03/05/06/07/11/37、命令、报告和续接共十份文档与31份回执输入交集为空。完整42工单仍为8 IN_PROGRESS/1 IMPLEMENTED/33 TODO；57业务与17非功能仍为36 NOT_RUN/38 BLOCKED，客户业务PASS和工单ACCEPTED为零。以下执行集合仅适用于各自原始输入与制品。

Given当前原框架与实际本机账户；When使用已有认证主体和原管理权限组件；ThenLoginUser类型、原ss组件、原用户编号及用户名、业务UUID和会话索引须一致。结构断言只证明组件接入，真实账户读取、密码修改、即时撤权、会话恢复和浏览器回归分别取得运行证据，不能用结构断言代替业务验收。

| 当前方法或字段 | 框架及业务来源 | 使用方与核验 |
| --- | --- | --- |
| LocalPrincipal | 继承原LoginUser；保留原userId/deptId/SysUser/permissions/username，附加businessUserId、credentialRevision、credentialSha256及platformRole | Spring Security标准认证、原SecurityUtils、原ss组件；原用户名不承担业务UUID语义 |
| LocalAccountDirectory.loadUserByUsername | 原UserDetailsServiceImpl完成账户、密码、原权限核验；一对一元数据补充医疗身份 | 标准DaoAuthenticationProvider和实际登录；平台角色不授予客户企业权限 |
| requireAccount/requireBinding | 原View核验ACTIVE、框架编号、凭据摘要、原版本和平台角色；原会话记录核验同一UUID、版本和ACTIVE | 原医疗API、lockCurrent及独立事务；核验以后使用原服务刷新角色、部门及菜单权限 |
| registerSession | 原绑定事务提交后设置Spring Session的principalName为业务UUID | 原RedisIndexedSessionRepository；账户密码和停用仍撤销旧绑定 |
| view | 原SecurityUtils读取当前LoginUser、框架编号及用户名；原View按原UUID和框架身份共同读取 | /auth/account、ApplicationSessionIdentity及全部既有业务接口；返回原五字段 |
| RuoYiAccountConfiguration | 原PermissionService、SysPermissionService、账户/角色/菜单/部门服务和原Mapper | 原ss bean直接使用原SecurityUtils；十八管理模块的具体业务接入仍需完成 |
| RuoYiNativeIdentityIT、test_ruoyi_accounts_runtime.py | 实际本机登录、原Spring Session仓库及JDK序列化组件读取真实认证；原SecurityUtils/PermissionService执行原规则 | 原check_ruoyi_accounts增加实际身份检查；本机账户没有原管理角色，不能伪造管理员编号或权限 |

原接口、Schema、数据库列、用户名及业务UUID保持原定义，未增加公开接口和迁移。认证版本更换采用RuoYiNativeIdentityIT的prepare：只读取该实际账户的UUID索引，逐项核查原认证身份并通过原HTTP注销处理保存撤销及删除Redis会话；存在其他账户时执行入口拒绝。实际旧Cookie请求返回401，当前数据库撤销数量与HTTP注销数量相等；迁移范围回执`.implementation-work/IC-04-NATIVE-IDENTITY-20261006-SESSION-TRANSITION-02/receipt.json`摘要ba2b706a6102c4df13f34ab4b5af80bb1f502937b789647b6d585825d3140fad，只适用于原部署的认证更换范围。新部署重新登录、原身份工具、业务UUID索引、原角色与权限以及标准注销均已验证。结构Red回执`.implementation-work/IC-04-NATIVE-IDENTITY-20261006-RED-02/receipt.json`，SHA-256为b9e429c193fbe13ee8c8bf03823fc278be844f97212a69bd015f33bd070cbc57，三项结构检查包含两项失败，错误及跳过为零，inputs_unchanged=true。当前工程运行不能登记客户业务PASS。完整管理组件仍需逐项接入原Controller、菜单、方法权限检查、原审计及企业授权与版本副作用；当前医疗应用没有直接开放原全局管理路径。

2026-10-06当前完整回归采用ENTERPRISE-REGISTRATION-FINAL-01，30组通过，连同原273项Java回执共31份当前输入、产物及运行身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261005-ENTERPRISE-REGISTRATION-FINAL-01/verified-evidence.json`。31项账户HTTP31.047秒、原框架三项4.620秒、原企业登记/审查接口和静态入口通过；正式登记表单及开发13项/生产10项DOM由唯一[IMP-06](IMP-06.md)维护。真实MySQL申请、审计、决定及客户实体前后均为零，当前PID88755与273项Java制品保持原身份。当前客户成功、审查、岗位、项目及全部业务继续验收，较早集合保持各自原始输入和制品范围。

当前TEMPLATE-APPROVAL-NATIVE-02完整273项Java检查通过，contracts42/data1/identity30/persistence200，无失败/错误/跳过，2分25秒；617项输入及8项产物字节核查通过，回执SHA-256为bda0372628676c67270d243bf6b8ef3d8cdd91d19041776a2d6367b6de8c4fef。当前JavaPID88755、UID501、部署UUID367c28a5-9f68-4a45-9780-59ea640ae34c，0400不可变Jar摘要d2561d1d6f13d0e82fc972ee21ce0f44caafbce6da439eff0bdae87cee2ddb4e，启动16.481秒，完整登录页面HTTP200。完整30组本地回归TEMPLATE-CREATE-UI-FINAL-01通过；31项账户HTTP检查29.379秒、三项真实框架账户/密码/原表字段检查4.520秒通过，框架回执SHA-256为c8f5b326adffbbe08ec3ec4ea531144278e99305799be55437637a0d9a0fa84a。真实账户继续使用原UUID，原SQL的完整菜单和关联字段、类型逐项核验，原权限表没有演示记录。30组及Native合计31份回执的当前输入、产物、应用和对象服务身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261005-TEMPLATE-CREATE-UI-FINAL-01/verified-evidence.json`。真实MySQL保持222项迁移；当前模板创建、人工决定及正式页面由[IMP-11](IMP-11.md)维护。账户验证、密码与框架权限继续调用原RuoYi服务，本次没有Java或身份存储修改；完整客户业务及其他管理能力继续按原计划实施，下列集合仅适用于各自原始代码和制品。

BACK-RUOYI-AUTHENTICATION-01按用户明确的框架复用要求继续本任务。已读取原UserDetailsServiceImpl、SysPermissionService、SysRoleServiceImpl、SysMenuServiceImpl、原SQL及当前LocalAccountDirectory/认证配置，采用接口补充设计§4.4。账户存在性、删除、停用、密码与框架权限验证交给原UserDetailsServiceImpl；医疗适配只关联业务UUID、版本、平台角色和当前会话。030追加原sys_menu、sys_role_menu完整结构，框架权限不授予企业访问。迁移备份保存独立UUID路径、0600文件及0700目录，原备份保持可核查。Given当前认证配置；When检查原账户验证及其原权限依赖；Then必须全部登记。有效Red回执`.implementation-work/IC-04-AUTHENTICATION-20261005-ORIGINAL-SERVICE-RED-01/receipt.json`，SHA-256为4fc4f8a3834a3be95a7bdf4ae35c00cb49a80e9482c08c51a8571258e4f3cd5c，一项断言失败、错误/跳过为零。当前269项完整构建、实际迁移及登录/锁定/恢复/旧会话拒绝已取得上述当前证据；完整客户业务保持未验收，下列集合保持各自原始输入和制品范围。

TEMPLATE-CREATE-NATIVE-04及TEMPLATE-CREATE-FINAL-02的264项Java检查、28组本地回归和对应29份回执保留原始输入及制品范围，读取[IMP-11](IMP-11.md)。当前制品、222项迁移及31份回执采用本记录首段。模板公开事务沿用EnterpriseAccess、当前会话、权限水位、template.write和template.approve；完整客户成功、重放、并发和撤权继续按原业务范围验收。

2026-10-05继续BACK-RUOYI-PASSWORD-01，沿用用户明确的框架复用要求和接口补充设计§4.4。实际检索原SysPasswordService、RedisConfig、AuthenticationContextHolder、Spring Security认证、现有账户目录及恢复调用方；密码核验、失败计数、锁定期限与成功清除直接使用原服务。元数据保存原服务结果的审核快照，公开表单、Cookie、CSRF、原UUID及凭据版本保持原规定。Given当前真实本机所有者及原账户；When输入错误密码、重新正确登录；Then原框架Redis计数为一、期限不超过600秒，数据库审核快照与原计数相等，成功后清除计数并读取原UUID。两项实际HTTP/Redis/数据库检查3.226秒通过，当前回执`.implementation-work/IC-04-RUOYI-20261005-RUOYI-PASSWORD-FINAL-02/receipt.json`，摘要`1bbe66a8243a2d6ea6591d1900e9adb10fbf9446364e69a8266c0ee64382c05b`。原Red回执`.implementation-work/IC-04-RUOYI-PASSWORD-20261005-PASSWORD-RED-01/receipt.json`，摘要`b9e3fa5a65dfd1ae5ffaf6ecd0fe67741c01873bc4021154ce685c7dc8dbc589`。完整业务、其他管理模块和部署交接继续按唯一计划实施。

当前完整制品RUOYI-PASSWORD-NATIVE-03通过256项Java检查，contracts38/data1/identity26/persistence191，失败/错误/跳过为零，构建2分20秒，回执摘要`897998fa895efcf292a26dd0f90742361836dac03183679ff2908ed0334e5762`。当前PID70295、UID501、部署UUID5465b1a6-063b-4fdc-8b86-44f44a0f547f，0400不可变Jar摘要`99ac748b8f5f52d7a6710c5fee1e3448159787c6841b7e2fafec99cee1ee07b0`，启动14.28秒。Native596项输入及8项产物字节核查通过；Spring Security原setAdditionalExceptionMappings维护完整认证失败事件，审核快照逐次采用原服务结果。27组完整本地回归RUOYI-PASSWORD-FINAL-02全部通过，账户HTTP31项29.241秒包含五次失败、数据库快照、锁定、恢复、新登录及旧会话拒绝。当前账户HTTP回执摘要`70e468845d6e631b072ebf1c1991fc290257c1512f4dcdd62c01a92ef497023a`。完整结果保存于`.implementation-work/FULL-REGRESSION-20261005-RUOYI-PASSWORD-FINAL-02/evidence.json`；该27组与Native合计28份回执的当前输入、产物及运行身份核查通过，证明保存于同目录`verified-evidence.json`。这些工程结果没有登记客户业务验收或工单ACCEPTED，其余回执按原始输入及执行范围读取。

2026-10-05继续 BACK-RUOYI-ACCOUNT-01。检索当前 MedicalApplication、RuoYi SecurityConfig、SysUserServiceImpl、SysUserMapper 及原框架 SQL，依据用户明确复用要求和接口补充设计 §4.4。当前账户读写调用原 ISysUserService，平台账户与业务 UUID 通过一对一元数据关联；既有接口、会话、权限和审计要求完整保留。框架表不载入演示账户、企业、岗位或配置。

Given当前真实本机所有者及原UUID；When读取框架账户存储及本人账户；Then sys_user是唯一资料和密码来源，元数据没有重复账户字段，登录后返回原UUID。当前Green由上述框架账户及密码两项真实检查证明；028/029实际迁移已经完成，完整账户及客户业务分别按原范围验收。原Red证据`.implementation-work/IC-04-RUOYI-20261005-RUOYI-ACCOUNT-RED-01/receipt.json`，摘要`1fa0393dddf2fab75c93be7744aae3f8dfbfe3bffc6889037bca3598c6369ebf`。

当前关联文件：RuoYiAccountConfiguration、LocalAccountDirectory、LocalAccountManagement、LocalPrincipal、028/029 迁移及 RecoverLocalOwner。平台 Mapper 使用原 RuoYi XML，企业业务继续使用当前 ProtectedTenantInterceptor；原用户服务事务参加同一 masterDataSource 事务。账户新增、资料、状态及密码五类写入由原服务完成，版本、幂等、审计和会话撤销由医疗权限适配层共同保存。框架直接密码修改会使旧认证摘要失效。成员、项目、来源许可及全部工单继续按原计划验收，IMP-04保持 IN_PROGRESS。

当前账户制品、运行身份及框架复用证据由本记录维护。模板内部保存与完整定义读取[IMP-11](IMP-11.md)，027—029已在真实MySQL执行，219项迁移及原UUID保持；完整模板公开接口、核准发布、项目使用及全部客户业务仍需验收。

2026-10-05 Servlet接收授权关联：receiveRequest在服务器消费线程中核查原主体/context/upload/完整授权，持有原企业和用户的同一Lease；停止/缓慢发送、实际网络取消及请求期间技术权限撤销分别检查私有文件与名额清理。十二项真实HTTP工程检查不表示客户授权验收，当前证据读取唯一[IMP-05](IMP-05.md)；等待期间撤权通知与完整客户成功继续实施。

2026-10-05对象截止授权关联：原剩余租期进入Get/Head/Put请求级截止，每次发送与内容处理仍核查当前权限，未知写入只明确恢复原对象。67项上传工程包括实际对象保存以后撤销技术文件权限、固定原版本恢复与条件重复拒绝；本地技术检查不能代表客户授权验收，唯一当前证据读取[IMP-05](IMP-05.md)。

2026-10-05完成传输并发授权关联：Lease.guard在当前领域准入前后核查原企业和用户名额、租期及配置，分片恢复与完整重组/发送/恢复使用同一原Lease。STORED/COMPLETED原回执继续核查当前读取许可，容量拒绝不开始完成。63项工程检查包括阶段期间实际技术权限变化、容量变化与关闭后拒绝；没有客户许可或批准写入，唯一证据读取[IMP-05](IMP-05.md)。

2026-10-05传输并发授权关联：receiveStream在原企业/主体/上下文/上传准入后取得实际双名额，输入和对象发送继续核查同一Lease及完整当前权限；租期或配置/容量变化停止后续处理，部分取得和权限失败释放原身份。59项工程检查不登记客户批准或许可成功，完整运行证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传输入授权关联：receiveStream在消费网络输入以前核查当前应用身份及Header上下文，每次读取前后通过原EnterpriseAccess独立短事务重新核查固定上传身份和完整权限；原保存片段使用当前读取许可，新发送保留完整写入许可。52项工程检查使用实际公开原件和技术文件权限撤销，没有客户许可写入；完整制品/运行证据读取唯一[IMP-05](IMP-05.md)，客户成功及全部业务继续实施。

2026-10-05当前许可事务权限关联：新增UploadIssuanceMapper全部查询及写入继承TenantScope当前主体和实际企业事务，DocumentUploadIssuance只处理实际EnterpriseAccess准入。新许可保存前后完整写入授权重新核查；原响应重放要求当前document.read和全部原依赖，不延长期限，不授予新发送权限。实际MySQL加锁读取、完整字段和缺少准入拒绝检查通过；合法客户创建、重复、撤权和并发仍未验收，当前44项工程及完整运行证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05当前上传依据权限关联：DocumentUploadBasisAuthority要求实际EnterpriseAccess、当前document.write/task.read和全部FORMAL来源许可；当前任务/确认计划/快照及原完整Binding在每次写入准入重新核查。完整当前依据进入阶段授权摘要，历史对象回执仍核查当前读取许可及原完整依赖。缺少实际主体、事务或核验依据拒绝已经验证，合法客户更新和撤权仍需真实业务结果；35项当前工程与完整运行范围读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传传输身份关联：内部DOCUMENT_BODY消费者对实际应用身份、独立EnterpriseAccess短事务与当前完整文稿授权逐阶段核查，实际对象I/O不继承企业事务。requireVisibleBoundDocument为原回执使用当前document.read及全部原依赖，不授予写入；三个授权入口缺少实际Admission及传输缺少实际应用身份拒绝已经检查。技术文件权限撤销和真实账户回归不代替客户企业隔离及撤权验收，当前191项原生和六项实际运行证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传授权身份关联：UploadAuthorizationAuthority仅在实际EnterpriseAccess事务核对企业/主体、水位、当前文稿原版本与SIGNED、项目ACTIVE、document.write和全部DependencyAccessSet授权；原绑定额外核对contextRevision、上传/许可状态与期限。两个入口缺少Admission拒绝已核查，实际客户签发状态、撤权和合法上传仍需正向业务证据；AGENT_ARTIFACT继续要求TaskToken/mTLS/lease。当前制品与运行范围读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传保存身份关联：UploadTransferMapper全部方法要求TenantScope原主体和已核查企业事务；UploadTransferStore只接受EnterpriseAccess内部Admission，原来源、许可和绑定使用实际企业/项目/主体关联。九项工程检查包含缺少准入拒绝，未建立客户成员或业务许可；全部当前领域权限、TaskToken/mTLS和lease继续由完整上传服务核查，客户撤权及企业隔离仍需原业务验收。当前源码、完整制品和运行证据读取唯一[IMP-05](IMP-05.md)。

2026-10-05上传身份关联：026以完整企业/project_key/主体/用途关联原许可，来源上传和Artifact许可保留各自身份；绑定固定文稿预期revision、应用上下文或实际run/step/principal/lease。十一组实际外键及缺少身份拒绝检查通过，没有创建客户成员、授权或许可。完整EnterpriseAccess、TaskToken/mTLS、当前权限重查和业务上传服务继续实施，当前证据读取[IMP-05](IMP-05.md)，结构检查不能代替客户撤权或两企业隔离验收。

2026-10-05对象写入权限关联：内部S3ObjectWriter要求服务器提供当前准入回调，每次实际Put/Head/Get及结果返回前执行。九项对象检查使用实际技术文件权限变化，不能据此证明客户撤权、两企业隔离或真实上传许可。完整EnterpriseAccess、许可持久化和TaskToken/lease仍需领域上传服务连接，当前证据读取[IMP-05](IMP-05.md)，以下集合保留原始范围。

2026-10-05独立依赖权限关联：DependencyAccessAuthority及DependencySourceAuthority要求当前企业事务，实际Mapper企业拦截和两个服务入口拒绝无准入访问的检查通过。完整清单、来源当前权利及全部36类实际领域权限共同核验；合法授权、两企业隔离及实际撤权继续真实业务验收。当前证据读取[IMP-05](IMP-05.md)，以下集合保留各自原始输入范围。

2026-10-05内容权限关联：IMP-05的完整内容方法逐阶段重新执行EnterpriseAccess及全部来源/版本权限，TenantScope对全部ContentReadMapper语句要求当前真实主体。授权摘要固定当前认证会话、企业/项目、上下文与水位、资源/用途/Schema、完整依赖和实际grant；无当前企业准入不能产生摘要或查询回执。CONTENT-READ-BUILD-01完整155项原生与JavaPID3730的六项原操作、两组实际登录DOM通过，证据读取[IMP-05](IMP-05.md)。合法客户授权、两企业隔离、并发撤权及回执成功仍未验收，下方集合保持原始范围。

2026-10-05共享读取权限核查：IMP-05的VersionResourceAuthority已接入来源准入，按36类真实领域版本核查资源UUID和当前grant，版本项目与资源项目不同额外检查project.read；会话消息要求实际所有者及固定会话范围。没有创建客户身份、企业或授权。VERSION-AUTHORITY-BUILD-01完整150项原生及当前JavaPID341的六项原操作、两组实际登录DOM检查通过。证据读取[IMP-05](IMP-05.md)，合法客户成功、撤权及两企业隔离继续未验收；下方较早集合保持原始输入范围。

2026-10-05 BACK-SOURCE-READ-AUTHORITY-01当前关联：SourceReadAuthority在原EnterpriseAccess/TenantScope中使用当前source.read、目标project.read和独立source.external.process；来源归属、实际会话所有者及固定使用范围共同核查。ArtifactSourceAuthority每次重新核查主资源、制品和全部关系。当前145项原生、六项原操作及两组实际登录DOM检查通过，JavaPID97158；合法客户授权、撤权、共享及两企业隔离继续未验收。较早集合保留原始输入范围，当前证据由IMP-05维护。

2026-10-05 BACK-RESOURCE-READ-AUTHORITY-01当前关联：PermissionAuthority.requireResource匹配grant本身的主体、动作、企业/项目/类型/资源、身份方式、用途及有效期；ResourceReadAuthority核查实际归属，resourceGrants使用当前主库时间并关闭缓存，TenantScope核查请求主体及已验证企业事务。四项当前只读MySQL/无授权检查进入RESOURCE-REGISTRY-BUILD-01完整138项；新JavaPID92494、登录HTTP 200。受影响身份与权限回归采用RESOURCE-REGISTRY-FINAL-01，由IMP-05维护。真实合法授权成功、撤权及客户隔离继续未验收；下方集合分别保留原始范围。

2026-10-04 BACK-BUDGET-HISTORY-01当前关联：BD-05/06沿用当前企业/认证会话、budget.read用途和ENTERPRISE/PROJECT/BUDGET范围。共享IdentityErrorResponseWriter提供明确内容不匹配、对象不可用、预算超限及版本上限响应，原生共享Schema检查通过。BUDGET-HISTORY-02原生96项、最终23组回归及26份当前字节核查通过；预算HTTP五项、账户HTTP31项、正式Vue开发十组/生产七组通过。实际客户数量保持零，合法客户隔离、撤权和并发仍未验收；主记录为IMP-07，下方集合仅表示原始范围。

2026-10-04 FRONT-BUDGET-MANAGEMENT-01关联：正式预算页面按当前ID-03动作摘要处理ENTERPRISE/PROJECT/BUDGET，服务端预算授权及身份继续由当前Java核查。BUDGET-FRONT-FINAL-01实际23组及26份当前字节核查通过；Vue开发十组/生产七组、本人账户两种入口各五组、账户HTTP31项和预算HTTP四项通过。当前真实客户企业、项目及预算仍为零，合法批准、隔离、撤权与并发仍未验收。完整预算记录及实际回执由IMP-07维护。

2026-10-04 BACK-BUDGET-MANAGEMENT-01关联：IMP-07增加预算管理，TenantScope对全部BudgetMapper语句要求实际主体参数；ENTERPRISE/PROJECT/BUDGET当前许可、用途、身份及数据库有效期均重新核验。当前原生构建为BUDGET-02的95项，程序PID46382及部署身份读取IMP-01/07。共享身份、账户、企业、岗位和项目回归使用BUDGET-FINAL-01，结果读取实际回执；本记录其他集合保持其原始输入和日期范围。合法客户成功、撤权和并发仍未验收。

BUDGET-FINAL-01当前23组共享回归实际通过，26份回执当前输入和产物核查通过；账户HTTP31项、项目六项、预算四项、企业及岗位等已有身份流程按新程序重新验证。客户企业、成员、项目、预算及专业岗位成功路径尚未运行，不能据此关闭IC-04。

当前PROJECT-WRITE-FINAL-01的22组回归及25份当前回执核查已经实际通过。Vue会话开发八组和生产六组、本人口令两种入口各五组、31项账户HTTP、项目和原操作各六项HTTP/数据库、成员及权限等既有检查通过；Java90项及当前部署保持一致。原始结果读取FULL-REGRESSION-20261004-PROJECT-WRITE-FINAL-01/evidence.json及verified-evidence.json。合法客户企业与项目成功、专业岗位授权、撤权和并发仍未验收。

2026-10-04 FRONT-PROJECT-WRITE-01关联：正式Vue已经提供PJ-02/04/05及BX-31/OP-01的项目写入和原请求恢复消费，具体实现与BDD读取IMP-06。PROJECT-WRITE-GREEN-01开发八组、生产六组实际无企业账户检查通过；当前完善后的前端复验使用PROJECT-WRITE-GREEN-02，共享回归使用PROJECT-WRITE-FINAL-01，结果读取实际回执。Java、数据库和原HTTP接口本次未修改；OPERATIONS-FINAL-03保持原始输入范围，其前端回执不表示当前新增输入已经验证。合法客户成功、隔离、撤权及并发仍未验收。

2026-10-04当前关联：IMP-05提供BX-31/OP-01原操作查询，IMP-06维护同一个Vue Router、user模块和Axios的正式Cookie会话、项目读取、本人账户及项目原始JSON转换。当前IC-03-JAVA-20261004-OPERATIONS-04的90项检查通过，实际MySQL186项迁移；完整成员与原操作约束由15项成熟解析器检查验证。FULL-REGRESSION-20261004-OPERATIONS-FINAL-03的22组及25份当前回执核查通过，部署PID39062、UUIDba2ec276-4cf9-4413-af6d-e43a7c226e18、程序摘要d3f878c25133e17d4e21e61f2e5e3d477d5a81ee9b6a8babc73b3bbb8f2e3dbc。正式本人密码入口/account调用LOCAL-06，两种真实入口各五组通过；原请求、旧会话撤销、新登录及审计实际核查。项目与原操作各六项HTTP/数据库检查、31项账户HTTP及原有身份流程重新通过。数学边界、真实企业拒绝与合法客户业务分别登记；完整合法企业/项目与全部管理页面仍未验收。其余回执保留对应日期及实际输入范围。

2026-10-04继续BACK-PROJECT-SERVICE-01及PJ-01—06：已读取PRD§8.1、后端§2/3.3/4.3、数据ProjectProfileRevision、前端S03/S18、当前PermissionAuthority/EnterpriseAccess/TenantScope和实际项目/任务/运行/待办DDL。采用接口补充设计§4.15，ProjectController/Service/Schema/Mapper提供项目创建、范围内列表与详情、不可变属性修订、状态决定及待办读取。023已经真实执行184/185项，project_record保存名称、负责人、当前属性及原批准依据，project_state_decision保存状态CAS和实际影响。完整项目授权、真实成功、重放、并发、暂停与恢复保持同一任务及原验收范围。

BDD Red实际使用当前操作系统所有者、有效登录与会话，项目入口返回404而预期企业拒绝403/输入422；IC-04-PROJECTS-20261004-PROJECT-RED-01回执摘要243ac7294a6bf1e7cbbdbed76c1df9584972f53d0321b0db119db2096c2cd8e0。Green为IC-04-PROJECTS-20261004-PROJECT-01，六项实际HTTP/数据库检查通过，九项企业及项目外键关联完整，项目/profile/业务operation/Outbox均保持零；回执摘要d0e66f21a7800e67d241fa0a9bcf6aa680c271630f436768a6f8ee374a299ea6。IC-03-JAVA-20261004-PROJECT-01实际clean verify通过86项，26条项目SQL经MyBatis/JSQLParser及SQLGlot核对全部原始DDL，回执摘要c5927d98921831cae82e9f633ca5d9622c5a9345f69560c73fac1b0e919e5d42。

每项读取根据当前project.read或者work_item.read及完整身份/用途/范围判定，数量采用相同授权与过滤条件。创建不自动建立业务授权；UNKNOWN属性保留null原值与明确原因，KNOWN声明保存现存依据及摘要。属性修订保留原profile及CAS，状态决定使用完整转换表并保存原许可版本、原响应、审计和Outbox；重复原请求重查当前许可及对象现状。状态影响的任务/运行数量和身份仅在各自动作允许时返回，其他结果为null/complete=false；原数据库审计保存实际影响。状态变更不生成外部取消或恢复回执，后续Temporal及运行准入仍需消费验证。正式S03/S18、合法客户创建/属性修订/状态变更、原响应、并发、隔离及实际运行恢复未验收。

2026-10-04继续实施BACK-ROLE-GOVERNANCE-01，依据接口补充设计§4.14及FR-01/AC-01/02。检索当前PermissionAuthority、PermissionDecisionService、InvitationSchema/Mapper、MemberInvitationService、018/019迁移和索引；EnterpriseRoleController/Service/Schema/Mapper已经提供ROLE-01—04的当前列表、历史读取、发布及撤销，使用同一主记录和企业权限事务。022迁移已经真实执行181—183项，保存岗位head、批准元数据和邀请正式授权来源，全部已应用迁移字节保持完整。当前真实账户的六项HTTP/数据库检查通过，十项企业外键关联完整；合法企业岗位运行仍需要客户真实输入，完整IC-04保持IN_PROGRESS。

企业管理要求role.manage、ROLE_ADMINISTRATION、当前完整企业身份、明确role_id及同一完整授权的全部动作/条件/期限。发布使用head CAS并保存不可变批准版本；新邀请要求当前head，原邀请持续核查原版本。ID-08共同保存member_role_grant，并在新接受及重放时重新读取授权、来源、条件与期限。指定岗位撤销共同终止原版本待接受邀请、撤销关联ACTIVE授权、增加auth_epoch/governance_epoch及本人上下文版本，原审计、Outbox和幂等响应保持完整，不恢复已经撤销的对象。

账户页面提供enterprise-roles.html/js入口，明确填写名称、完整条件、期限、批准依据和声明；历史版本可按明确UUID读取并作为后续发布的表单内容。页面只保存技术请求UUID、摘要及原上下文版本，未知结果需要相同内容重放；离开、身份/水位变化和错误时清除可见内容，注销失效后返回登录。企业审查页面允许明确填写其他业务用途或明确全部用途，全部选项不预选。正式Vue S17/S18及真实合法岗位成功、撤销、重放、并发和隔离仍未验收。

2026-10-04继续实施BACK-REGISTRATION-REVIEW-01。读取当前申请服务、不可变020迁移、TenantScope和ProtectedTenantInterceptor、现有许可条件及FR-01/AC-01/02；采用接口补充设计§4.13。审查决定与原申请分别保存，批准事务建立明确的首位管理员治理许可，平台身份不能读取企业业务内容。真实接口11项、Chrome DOM三项及原生79项检查通过；合法企业申请及批准声明继续由客户填写，当前没有客户企业数据；完整验收保持未完成。

BACK-REGISTRATION-01使用补充设计§4.12维护LOCAL-REG-01—03和真实输入界面，当前企业记录仍为空，本任务持续保持IN_PROGRESS。真实所有者检查本人列表、缺少企业正文、Header/分页/其他标识、CSRF/Origin及产品入口；授权企业提交和批准启用需要真实客户填写，完整AC-01/02尚未通过。

2026-10-04当前本地环境：应用PID22458，部署UUIDbf6889c6-f51a-4aed-abad-3ba2fab7f7a3，程序摘要e49fecef5405cd3eb2e293f6c983410ffd128ecbf1fa83f3887d933d8b92eca3，启动21.251秒。真实MySQL已执行185项迁移，140企业表及17平台表；Redis继续使用保留数据目录。当前86项Java原生及六项项目HTTP/数据库检查通过，全部已登记回归由FULL-REGRESSION-20261004-PROJECT-FINAL-01继续验证；取得该集合证据以前不登记全部回归通过。FULL-REGRESSION-20261004-ROLE-FINAL-01绑定其原始程序及输入，不能代表新项目程序的全部验证。真实客户岗位及项目业务成功流程、生产与完整IC-04仍未验收。

2026-10-03 当前复验：S009 已读取本记录、接口补充设计 §4.4/4.6、实际认证页面、账户服务与现有检查；S008 继续维护同一任务。登录及首次设置表单在连接未完成时显示明确提示并恢复提交控件，网络异常继续抛出；成功提交及离开页面清除密码输入。密码确认表单使用当前控件的原生校验，确认内容不一致时停止提交并显示明确提示，修改或清除输入后解除校验状态。新增 `tests/browser/local-auth-errors.js` 与 `scripts/check_local_auth_errors.py` 使用当前真实所有者、实际连接中断和恢复检查页面行为，全部关闭视觉流程。两项补充检查、31项账户HTTP和5项账户浏览器检查已在 `20261003-FINAL-01` 执行通过。验收证据由 `docs/testing/full-functional-report.md` 维护；完整 IC-04 状态继续读取实施任务计划。

## 任务身份

| 字段 | 当前内容 |
| --- | --- |
| task_id | IMP-04 |
| 需求与验收 | FR-01、FR-08；AC-01/02/16；IC-04；后端 §2 |
| task_source | [实施任务计划](../../实施任务计划.md) §4.2 / §5 |
| 状态快照 | IN_PROGRESS；2026-10-04；本地独立身份、企业申请/审查、本人候选、企业选择页面及空成员/输入/连接/注销检查通过；真实企业提交、审批、启用、成员和项目完整授权尚未验收 |
| 代码与环境 | 根仓库 main；尚无提交；Java 17；实际源码、制品、配置和服务身份绑定回执 |
| 文档有效性 | CURRENT；last_verified=2026-10-04；当前本地登记与账户运行证据、完整未验收范围 |
| 检索字段 | BACK-LOCAL-01、ID-03、ApplicationSessionIdentity、BusinessSessionProtocol、BusinessSessionSchema、BusinessSessionController、BusinessSessionDirectory、MemberDiscovery、TenantMemberMapper、platform_member_route、platform_session_context、verify_application_deployment、medical-identity、IdentityMode、LocalAccountDirectory、LocalPrincipal、LocalAuthenticationProvider、LocalIdentitySecurityConfiguration、LocalIdentityController、MedicalSessionConfiguration、MedicalApplication、local_runtime、check_local_identity、platform_local_account、platform_local_session、platform_local_bootstrap、OidcPolicy、RequiredOidcProofs、PlatformIdentityDirectory、PlatformOidcSessions、BackchannelLogoutVerifier、VerifiedLogoutToken、CanonicalOidcKey、OidcClientLedger、BackchannelLogoutPersistence、LogoutPersistencePolicy、platform_logout_receipt、platform_logout_session、PKCE、CSRF、Origin、TenantScope、ProtectedTenantInterceptor、TenantTransaction、medicalSqlSessionFactory、C02 |

## 检索与实施依据

读取开发索引及 IMP-01/02/03，核查 HLD 的企业隔离与 ADR-006、LLD-后端 §2/3.2/3.5/4.3/6、接口补充设计 §4.2—4.4、实施工单及当前认证、DataSource、MyBatis、Redis、Mapper 和迁移资源。重新核实实际 JDK、数据库二进制、Redis 二进制、原生制品及本机操作系统身份。

BACK-LOCAL-01 为2026-10-02用户明确要求“先本地运行，系统独立用户和登录”。medical.identity.mode 必须显式指定 local/oidc；当前实际使用 local。local 启动不创建外部 OIDC 客户端、注销验证器或恢复调度；企业、项目、动作、版本和来源权限保持完整要求。可选 oidc 的真实发行者、客户端、签名 Token 和批准恢复配置仍需取得。

已读取 MyBatis-Plus 官方安装、多租户文档及3.5.17处理器源码；当前 MyBatis-Spring 为3.0.5。已读取固定 Spring Security 的授权请求和 PKCE 源码、原生 Session API、Back-Channel Logout 标准及 Security 注销说明；Nimbus JOSE JWT 固定9.37.4。已核查 Liquibase SQLFile 使用和实际资源访问器，迁移工具通过同版本普通资源 jar 的原生 search-path 取得全部资源。

## 当前功能与代码位置

### 企业空间选择页面

2026-10-04继续检索BACK-CONTEXT-01、ID-03/04、BusinessSessionSchema/Protocol/Controller、TenantSelectionService、当前账户页面及LLD-前端§2.1—2.3。企业选择API已具备实际成员、上下文、水位和幂等处理，账户页面需要提供明确选择入口。页面沿用当前本地身份界面，仅从真实ID-03取得当前用户、成员、上下文、CSRF及可用动作。没有成员时禁止提交，不建立企业样本或者预设许可；正式Vue业务页面和完整S02保持独立未验收范围。

Given：真实操作系统所有者没有企业成员；When：经当前账户进入企业空间、刷新及重新加载；Then：显示服务端空成员和未选择状态，提交控件禁用，没有选择操作或企业记录。Given：浏览器实际断开连接；When：重新读取失败及恢复连接；Then：遮盖原内容、恢复读取控件、恢复以后重新验证会话。Given：真实用户注销；When：再次访问企业空间；Then：返回登录入口且业务会话401。对应真实DOM检查为tests/browser/enterprise-context-checks.js，由scripts/check_enterprise_context_browser.py绑定当前部署、浏览器及原始输入，并从真实MySQL核查业务记录没有增加。合法成员选择、重放、并发及其他标签页切换需要真实企业批准后取得证据，不能由空成员检查代替。

Red已经实际执行：IC-04-CONTEXT-BROWSER-20261004-CONTEXT-RED-01，真实账户登录及ID-03读取正常，缺少“选择企业空间”链接的断言为0 != 1；回执SHA-256为1a9498671fbf727afe469c311c480e420b14adf4d61e274e5307bd9b36b56029。当前实现由账户链接进入独立企业选择页面，成员、上下文、水位和动作响应完整校验；提交使用原键、原版本、正文摘要和实际CSRF，成功重读服务端上下文。visibilitychange、generation及AbortController清除旧内容和旧读取，BroadcastChannel仅传失效常量。连接失败的技术请求记录保留，明确核查后可清除；没有客户资料时不建立企业业务数据。Node语法和Python编译检查已通过，实际运行结果见当前证据表。

BACK-SELECTION-OPTIONS-01补充GET /api/session/tenant-selection-options，读取本人真实有效成员和当前选择版本，保持旧企业业务水位准入规则。当前BusinessSessionDirectory的正式ID-03在所选成员或水位失效时拒绝，恢复入口需要独立读取本人元数据。Red回执IC-04-CONTEXT-BROWSER-20261004-OPTIONS-RED-01的实际404 != 200，SHA-256为fbecc90c86f1e359e945973b070468482f30f2bc55b8c73f74eaac0f17efc3d5。新TenantSelectionOptions/Schema复用唯一成员定义及Envelope，原身份Schema和清单字节保持不变；Controller返回前重查当前应用会话、选择版本及已观察成员。页面旧上下文失败时保留具体错误，通过重新读取的本人候选供用户明确选择，不展示旧企业动作或者内容。

IC-04-CONTEXT-BROWSER-20261004-CONTEXT-03已经通过六组实际检查：候选HTTP一组、真实Chrome DOM四组及原生数据库一组；回执SHA-256为5c30888e6069b535be7d2e896b51f91fcc1d55a0428ad8c6af7bc58c516bff5f。候选返回当前真实主体、版本0及空成员，Envelope/Header/禁止缓存一致；缺少Schema和未知查询分别422，重复读取data相等。实际登录以后从账户链接进入页面，空成员禁止提交且没有默认企业；刷新/重载重读，真实离线后遮盖内容并禁用选择，恢复连接后重新读取；真实注销后候选和会话均401并返回登录入口。MySQL在浏览器前后重读企业、成员、ID-04操作数量均为零；当前部署和原始输入绑定同一回执。服务没有创建客户资料或者发起模型调用，合法企业成功、旧水位后的重新选择、跨标签页及并发继续需要真实输入。

| 文件路径 | identifier | 调用及职责 |
| --- | --- | --- |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/TenantSelectionOptions.java、TenantSelectionOptionsSchema.java | 本人候选五字段、唯一成员定义、U64及Envelope | 当前企业选择页面；原身份Schema及原操作清单保持不变 |
| business/medical-identity/src/main/resources/tenant-selection-options.schema.json | 候选封闭字段 | 正式Java资源及实际HTTP序列化 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/BusinessSessionDirectory.java、BusinessSessionController.java | selectionOptions、requireOptionsUnchanged | 当前真实应用主体、本人成员及选择版本；不授予业务许可 |
| business/medical-identity/src/main/resources/static/local-auth/enterprise-context.html、enterprise-context.js、enterprise-context.css、account.html | 企业选择入口、受限状态清理、原请求身份和结果重读 | ID-03/04及本人候选；正式Vue页面继续独立实施 |
| tests/browser/enterprise-context-checks.js、scripts/check_enterprise_context_browser.py | IC-04-CONTEXT-BROWSER | 实际所有者、真实Chrome、MySQL及当前部署 |
| scripts/check_full_regression.py | 企业申请/审查及企业选择检查编入整体入口 | 整体脚本本次未执行；对应检查已经分别实际运行 |

### 企业登记申请与真实输入入口

LOCAL-REG-01—03使用接口补充设计§4.12。当前平台管理员可以通过账户页面进入企业登记申请，填写真实企业法定名称、地区、登记标识、授权依据、明确声明及已有有效账户。原始登记状态为PENDING_REVIEW；当前审查状态从独立LOCAL-REG-04—06读取。批准和首位管理员治理实现见下一节，项目创建及完整企业岗位配置继续实施；申请不产生企业资料许可。

EnterpriseRegistrationService在独立READ_COMMITTED事务中锁定LOCAL认证协调行，重查实际账户和会话，核验当前PLATFORM_ADMIN及指定管理员ACTIVE账户版本。请求内容经封闭Schema、原生Jackson重复字段/尾随正文拒绝、Unicode规范及JCS校验。当前主体/键唯一，同一主体的地区/登记标识摘要唯一；同参重读原201响应，异参拒绝。申请、原响应和同步审计共同保存；返回前重读并校验数据库申请、原正文、时间、内容摘要和完整审计绑定。当前管理员身份或指定管理员版本失效时拒绝重放。

本人列表及详情只能查询当前实际主体。列表使用UUID游标及有界分页；不存在及其他主体的编号均拒绝。响应采用唯一共享Envelope字段名称、固定Schema和request_id Header，禁止缓存。PermissionSchema及邀请响应使用相同公开字段表示。

页面只读取实际账户候选，首位管理员需要用户明确选择，授权声明需要明确勾选。sessionStorage仅保存当前主体的请求UUID和正文摘要，企业信息及授权依据不进入浏览器存储。连接失败保留原键；内容变化要求恢复原正文或者明确清除原请求。成功保存后重新读取本人列表和详情，页面离开清除表单和可见内容；401/403或身份变化清除受限内容并停止操作。文本长度按code point核验，标签和原生必填校验保持可访问。

| 文件路径 | identifier | 使用方 |
| --- | --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/EnterpriseRegistrationController.java | LOCAL-REG-01—03 | 当前账户及企业登记页面 |
| 同目录EnterpriseRegistrationService.java、EnterpriseRegistrationSchema.java | create、list、detail、requestHash、companyHash、readEnvelope | 当前主体申请、幂等、事务、持久重读及审计 |
| business/medical-persistence/src/main/resources/enterprise-registration.schema.json | Request、Detail、Page、Audit | Java输入、响应及持久校验 |
| business/medical-data/src/main/resources/db/migration/020-platform-enterprise-registration.sql | platform_enterprise_registration、platform_enterprise_registration_audit | 第177/178项迁移；平台申请元数据及完整同步审计关联 |
| business/medical-identity/src/main/resources/static/local-auth/enterprise-registration.html、enterprise-registration.js、enterprise-registration.css | data-registration、requireCurrent、readPage、securityToken | 真实输入、实际账户候选、本人申请列表及详情 |
| tests/test_enterprise_registration_runtime.py、scripts/check_enterprise_registration.py | EnterpriseRegistrationRuntimeChecks | 当前真实所有者HTTP、数据库约束及拒绝检查 |
| tests/browser/enterprise-registration-checks.js、scripts/check_enterprise_registration_browser.py | IC-04-REGISTRATION-BROWSER | 实际headless Chrome DOM；视觉检查未运行 |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/EnterpriseRegistrationSchemaTest.java、PermissionStructureTest.java | emptyOwnedQueueProducesValidEnvelope、sharedEnvelopeUsesPublishedFieldNamesForPermissionsAndInvitations | 空列表、原生输入拒绝及公开Envelope字段 |

真实企业提交、原响应重放、重复企业、指定管理员失效、多人隔离、并发及完整审批使用需要真实企业和人员输入分别验收。当前空列表、拒绝、数据库元数据及DOM检查不代表这些成功业务通过。

### 本地账户和真实运行

MedicalApplication 只扫描 com.ruoyi.medical，使用独立 masterDataSource 和 application-medical-local.yml。应用监听127.0.0.1:18080；真实 MySQL Community 8.4.3 使用127.0.0.1:13306/medical_local，真实 Redis 7.4.8 使用127.0.0.1:16379。运行账号只有该数据库的 SELECT/INSERT/UPDATE/DELETE 权限，独立管理员账号执行迁移。Redis 使用密码认证、protected-mode 及 AOF/everysec。服务、日志、备份和秘密均保存在项目 .implementation-work/。

实际本机所有者 liuhaoxian 已经通过 POST /auth/setup 登记为首次 PLATFORM_ADMIN。凭据保存于 .implementation-work/runtime/owner-credentials.json，文件权限0600，运行目录权限0700；密码不进入源码、命令参数、日志或检查输出。账户包含真实 user_id，未创建企业、项目或客户资料。PLATFORM_ADMIN 不获得客户业务资料权限。

| 文件路径 | identifier | 当前职责 |
| --- | --- | --- |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/IdentityMode.java、IdentityModeConfiguration.java | IdentityMode | 明确模式；缺少或未知模式立即拒绝启动 |
| 同目录 LocalAccountInputs.java、LocalIdentityPolicy.java | requireName、requirePassword、browserOrigin | 用户名及显示名称最多128个 code point；密码至少15个 code point、UTF-8最多72字节；BCrypt 输入不截取；本地 Origin 明确登记 |
| 同目录 LocalAccountDirectory.java | bootstrap、registerSession、requireSession、revoke、failedPassword、view | 原生 JDBC 短事务、首次管理员协调锁、当前账户及会话重读、持久撤销、五次失败锁定十分钟 |
| 同目录 LocalPrincipal.java、LocalAuthenticationProvider.java | credentialRevision、additionalAuthenticationChecks | 标准 DaoAuthenticationProvider/PasswordEncoder；认证主体保存实际 UUID 和凭据版本；敏感字段不出现在字符串表示 |
| 同目录 LocalIdentitySecurityConfiguration.java、BrowserOriginFilter.java | localIdentityFilterChain | 原生 formLogin、会话固定防护、CSRF、精确 Origin、统一身份错误及 LogoutHandler |
| 同目录 LocalIdentityController.java | login、setup、csrf、account | 实际登录和首次设置页面、CSRF、经过会话核验的五字段账户响应 |
| 同目录 MedicalSessionConfiguration.java | medicalSessionCookieSerializer | indexed Redis；当前 loopback HTTP 使用 HttpOnly、SameSite=Lax、path=/；生产要求 Secure/TLS |
| business/medical-identity/src/main/resources/static/local-auth/ | login.html、setup.html、account.html、auth.js、account.js、auth.css | 实际表单、CSRF 获取、账户读取、错误提示及注销；使用现有 UI 的颜色规范 |
| business/medical-data/src/main/resources/db/migration/013-platform-local-account.sql | platform_local_account、platform_local_session、platform_local_bootstrap | 本地账户14字段、会话11字段、协调6字段；唯一键、外键、状态及版本 CHECK |
| business/ruoyi-admin/src/main/java/com/ruoyi/medical/runtime/MedicalApplication.java | MedicalApplication、masterDataSource | 当前正式应用入口及原生 Druid DataSource |
| scripts/local_runtime.py、scripts/RecoverLocalOwner.java | initialize、start、migrate、application、recover_owner | 独立真实服务、迁移前备份、秘密环境注入；本机数据库管理员恢复真实首次管理员凭据，增加 credential_revision 并撤销既有会话 |
| tests/browser/local-account-checks.js、scripts/check_local_browser.py、tests/browser/package-lock.json | login、storageHasNoSecrets、run_checks、browser_identity | 真实Chrome DOM表单操作、私密凭据同步、原请求及审计重读；固定工具依赖及原生浏览器/Node/应用字节进入回执 |
| scripts/check_local_identity.py、tests/test_local_identity_runtime.py | LocalIdentityRuntimeChecks | 直接访问当前应用及数据库，保存实际输入、制品、服务身份和运行回执 |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/LocalAccountManagement.java、LocalAccountManagementController.java | list、mutate；LOCAL-01—06 | 账户列表、受控创建、状态、资料及凭据变更；当前身份重读、版本比较、最后管理员保护、原请求重放及同步审计 |
| 同目录LocalAccountSchema.java、LocalAccountProtocol.java、LocalManagementPolicy.java、LocalAccountConflict.java | request、digest、envelope、AccountItem、Mutation、hmacKeyId | 封闭Schema、Jackson/JCS、HMAC-SHA256、共享Envelope及标准错误 |
| business/medical-data/src/main/resources/db/migration/016-platform-local-management.sql | platform_local_operation、platform_local_audit | 操作原响应与同步审计；独立平台认证元数据，完整外键及请求唯一约束 |
| business/medical-identity/src/main/resources/static/local-auth/ | management.html、management.js、management-request.js、account.html、account.js | 本人密码表单及管理员账户管理；目标确认、原幂等键保留、页面成功及离开清除秘密输入 |

application 使用 .implementation-work/runtime/applications/<sha256>/ruoyi-admin.jar 的独立程序文件，权限0400；启动前重新比较构建文件及保存文件摘要。application-deployment.json 和每次启动的 UUID 历史记录绑定实际 PID、操作系统身份、程序路径和摘要。verify_application_deployment 同时核对保存记录、程序字节和实际 Java 进程命令，停止操作复用该核验。后续构建使用自己的 target 目录，运行检查另行绑定实际运行文件。

首次设置在 BCrypt 计算以后锁定 bootstrap 行并重新核查账户集合，在同一事务建立账户及 created_by。成功认证使用真实 HttpSession，锁定当前 ACTIVE 账户并比较 credential_revision、角色，再登记并重读实际 session_id 绑定。每次账户/API 准入查询主数据库，账户停用、凭据版本变化或会话 REVOKED 时拒绝新请求。

GET /auth/account 只返回 user_id、username、display_name、platform_role、revision，响应禁止缓存。POST /auth/logout 提交数据库 REVOKED 后，由标准 LogoutHandler 清除实际 Redis 会话和 Cookie；旧 Cookie 不能继续访问。账户恢复要求本机数据库管理员秘密，核对首次管理员及 bootstrap 创建人，在短事务更新密码、增加凭据版本并撤销会话。恢复工具核查运行目录及既有凭据的所有者、权限和用户名，以原生 flock 拒绝同时执行的恢复；候选凭据采用独占文件、0600权限和文件/目录 fsync，在数据库提交后原子替换当前凭据并同步目录。中断时保留候选文件，接收人员依据实际数据库状态处理，不自动重发凭据更新。

### 企业和项目授权基础

| 文件路径 | identifier 或接口 | 当前职责及使用方 |
| --- | --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/TenantSelectionService.java | select | 目标成员重读、事务内账户和会话核验、上下文版本比较、幂等结果校验和共同提交；ID-04 |
| 同目录 mapper/TenantSelectionMapper.java、SelectionOperationRow.java；medical-mappers/TenantSelectionMapper.xml | currentOperation、saveOperation、saveAudit、saveEvent | 完整企业准入和本人参数核验后的操作、审计、事件读写 |
| business/medical-identity/src/main/resources/identity-session.schema.json、identity-schema-manifest.json | SelectionRequest、SelectionReceipt、SelectionAudit、SelectionEvent | 封闭字段、UUID、版本及模块/共享Schema摘要；BusinessSessionSchema |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/AuthenticatedIdentityBinding.java、IdentityBindingSchema.java；identity-binding.schema.json、identity-binding-manifest.json | binding、view、matches、envelope | 当前真实local/OIDC完整绑定、独立封闭Schema及清单；成员邀请使用同一明确表示 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/BusinessSessionController.java；static/local-auth/account.js、account.html | GET /api/session/identity-binding、data-identity、data-mode | 本人身份子资源；响应前重查实际会话及完整绑定；账户页面读取并核验同一用户身份 |
| business/medical-data/src/main/resources/db/migration/015-platform-context-identity.xml | context_id | 现有会话UUID、非空和唯一约束；原生Liquibase及MySQL |
| business/medical-data/src/main/resources/db/migration/018-member-invitation.sql | member_invitation、platform_invitation_route | 完整邀请身份、授权、期限、结果、版本及目标本人路由；174/175项正式迁移 |
| business/medical-identity/src/main/java/com/ruoyi/medical/identity/InvitationCredential.java；InvitationCredentialTest.java | issue、validate、matches、secret、sha256 | SecureRandom产生256位秘密、规范Base64 URL、SHA-256及恒定时间比较；ID-07/08实际消费 |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/MemberInvitationService.java、MemberInvitationController.java、InvitationAccess.java、InvitationMapper.java、InvitationSchema.java、InvitationProtocol.java | create、accept、authorize、originalAuthorities | 实际企业/本人邀请准入、批准角色、原委派版本、期限、成员/授权/水位、幂等结果、同步审计及Outbox |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/InvitationResponseCipher.java；InvitationSecurityTest.java；member-invitations.schema.json；019-enterprise-role-version.sql | encrypt、decrypt、requestHmac、enterprise_role_version | 独立认证加密、HMAC、封闭Schema、批准角色版本及十四条SQL原生核查 |
| scripts/check_member_invitation.py；tests/test_member_invitation_runtime.py | MemberInvitationRuntimeChecks | 六项真实数据库元数据和客户记录数量检查；回执绑定实际迁移及服务身份 |
| 同目录EnterpriseAccess.java、PermissionDecisionService.java、PermissionAuthority.java、PermissionProtocol.java | execute、decide、require；ID-09 | 当前企业/身份事务准入、完整条件委派、授予、撤销、停用、版本与原响应、共同审计及Outbox |
| 同目录PermissionDecisionController.java、PermissionSchema.java；permission-decisions.schema.json | POST /api/permission-decisions、headers、request、readEnvelope | 封闭请求和结果、Schema/Header拒绝、严格Jackson及JCS摘要；企业准入以后解释正文 |
| 同目录mapper/PermissionMapper.java；medical-mappers/PermissionMapper.xml | currentDelegations、targetBinding、insertGrant、revokeGrant、disableMember、advanceAuthEpoch | 固定企业处理器、真实目标及依据查询、CAS和同步保存；没有动态表名或查询缓存 |
| PermissionStructureTest.java；tests/check_permission_sql_columns.py；scripts/check_permission_decision.py | IC-04-PERMISSION | 原生Schema/U64拒绝、十四类实际Mapper查询及水位列核查、五项真实无企业管理员HTTP检查 |

ID-03 的本人会话代码位于 ApplicationSessionIdentity、BusinessSessionProtocol、BusinessSessionSchema、BusinessSessionController、BusinessSessionDirectory 和 MemberDiscovery。本人身份只能从已经核查的真实应用会话建立，Verified 的构造器为 private。成功响应复用共享 Envelope和唯一 Schema，data 由 identity-session.schema.json校验；版本 Header、请求 UUID、禁止缓存和实际 CSRF保持对应。

014-platform-business-session.sql 定义 platform_member_route 和 platform_session_context。本人路由逐企业通过受保护 TenantMemberMapper 重读成员。TenantScope 的成员发现状态只允许固定水位及本人查询，必须在企业行锁以后读取成员；ProtectedTenantInterceptor 另行比较查询主体与已验证主体。完整动作摘要在 TenantTransaction 的当前授权及治理水位准入以后读取；返回前重新核查成员、水位、上下文和应用会话。

十四项迁移结构检查绑定39项输入：[当前结构回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-20261002-20/receipt.json)，SHA-256为ba00a139b98451fbcb7a25407ec2be7379c7b113a60af5d9eaea21648171feef。135张企业表及13张平台表、上下文、账户操作/审计、完整成员身份、邀请路由及批准角色版本均在覆盖集合；真实MySQL已经执行176项迁移。原生20261002-37构建通过65项，178项输入及8项输出绑定；真实成员比较另有四项Java集成检查，邀请/角色通过七项数据库检查。合法企业选择及完整成员/项目/动作继续验收。

ID-09使用接口补充设计§4.10及独立permission-decisions.schema.json。EnterpriseAccess锁定当前企业、实际认证协调行、账户/会话和上下文；操作人员企业identity_binding必须与已验证完整local/OIDC绑定一致，目标企业成员也使用完整身份核查。PermissionAuthority以当前PERMISSION_ADMINISTRATION用途、身份方式及同一完整授权的delegation核查授予动作、角色、类型、项目和资源，明确null及空数组保持原语义；进一步委派和期限受到当前授权限制。十四类资源通过固定Mapper查询实际归属，主体与授权资源独立保存，成员停用要求对应目标成员；basis使用实际不可变版本及摘要。首位管理员及业务角色继续等待获授权输入，不从平台岗位产生企业许可。

PermissionDecisionService实施GRANT、REVOKE及DISABLE_MEMBER；成员停用共同撤销该主体ACTIVE授权并终止其PENDING邀请。授权原依据保持保存，当前请求、有效管理授权版本、目标原状态、当前决定依据及新水位进入同步审计。权限水位和本人上下文各增加一次，operation_record及Outbox共同提交；任何CAS或版本上限失败立即终止事务。原请求摘要包含实际session_id及原Header；重放重新核验当前企业及完整管理范围，返回原响应，异参拒绝。全部成功路径、当前业务条件、并发和其他会话失效需要真实企业运行证据，当前五项拒绝检查不代表完整ID-09通过。

medical-persistence已进入正式应用。业务Mapper固定使用medicalSqlSessionFactory及MyBatis-Plus TenantLineInnerInterceptor/JSQLParser AST；原有Mapper使用sqlSessionFactory，共享DataSource。业务会话没有二级缓存，一级缓存限STATEMENT，SQL日志关闭；135张企业表均要求tenant_id，没有跳过规则。

TenantScope.Identity/open 为同包服务端入口，上下文不可继承到异步线程；UUID、U64 和当前事务水位必须有效。ProtectedTenantInterceptor 在查询、写入及准备语句时核查当前权限，拒绝显式企业插入列和归属更新，处理器不能替换。TenantTransaction 使用 READ_COMMITTED 和 TenantWatermarkMapper.lockCurrentTenant 的 FOR UPDATE 读取 auth_epoch/governance_epoch，BigInteger 保留 BIGINT UNSIGNED；核对后仅授权当前事务，结束清除准入，拒绝嵌套入口。

账号、企业成员、项目、动作、context_revision、来源权利和内部任务身份仍需 C02 完整核验。当前尚未建立合法企业业务上下文；成员及项目服务、合法 SQL、两企业运行和撤权并发仍待实施验证。

### 可选 OIDC 和持久注销

oidc 模式使用标准 OAuth2 Login、服务器 HttpSession 授权请求及令牌保存。OidcPolicy 核查固定 registration、issuer、HTTPS 端点、confidential client、openid、GET callback、browserOrigin 和登记返回路径；RequiredOidcProofs 要求成熟生成器的 S256、verifier、nonce 及对应摘要。PlatformIdentityDirectory 从经过认证的 SecurityContext 读取 issuer/sub，在主数据库登记并重读固定身份；组合唯一键命中后比较完整原值，重复认证保留账号停用状态。

VerifiedOidcSession 固定实际 HttpSession、client、registration、sid 和 ID Token 的 UTC iat；PlatformOidcSessions 在短事务核查当前身份与会话 ACTIVE 及完整字段匹配。BackchannelLogoutVerifier 使用成熟 JWTParser、固定 JWK URI、明确算法及时限，签名通过后核验完整注销声明；原始 Token 不保存。

VerifiedLogoutToken 绑定签名通过的实际 Token 摘要及原始声明。CanonicalOidcKey 使用 Jackson/JCS 编码完整 [issuer,client_id] 或 [issuer,client_id,jti] 后计算 SHA-256，命中时重新比较原值。OidcClientLedger 为登录和注销锁定同一客户端协调行；匹配注销 iat 覆盖待完成登录时拒绝登记。BackchannelLogoutPersistence 在同一短事务保存 PENDING 回执、首次匹配的固定会话集合及 REVOKED 状态；事务外清除 Redis，全部成功后登记 COMPLETED。调度器只重试既有固定集合，不扩展到后来建立的会话。

012-platform-logout.sql 保存 platform_oidc_client、platform_logout_receipt、platform_logout_session 的字段、完整关联和状态约束。恢复 actorId、batchSize、interval 必须通过明确配置取得。ID-06 标准协议认证与应答保持关闭；真实发行者签名、密钥轮换、SQL、Redis、并发及恢复尚未验证。

## 验证与证据

### 当前审查、启用及关联回归

BACK-REGISTRATION-REVIEW-01沿用本记录和补充设计§4.13。EnterpriseRegistrationReviewController/Service/Schema提供平台申请审查列表、详情与APPROVE/REJECT决定。列表三种状态、分页与参数均封闭；原申请和原LOCAL-REG-01响应保持不可变。完整批准依据、人工声明和明确治理条件由实际用户输入，动作限定member.manage、role.manage、project.create；没有默认岗位、企业资料许可或专业签发权限。

审查READ_COMMITTED事务重查真实账户、当前会话、申请内容与同步审计；批准核查首位管理员ACTIVE及原账户版本。APPROVE使用企业标识摘要唯一约束，通过专用EnterpriseProvisioningMapper及受限TenantScope保存服务端企业UUID、完整local绑定、ACTIVE成员、不可变ENTERPRISE_GOVERNANCE依据、明确授权、本人发现路由及Outbox。平台决策、原响应和完整同步审计共同提交并重读；REJECT只保存决定。已有批准重放先锁企业水位，再锁认证协调行，保持撤权操作顺序；重查完整成员/绑定及原治理许可，不恢复已撤销记录。

enterprise-registration-review.html/js从登记页面进入，读取实际申请及原始内容，逐项选择治理动作、用途、可委派动作/岗位/资源类别和期限，并明确人工声明。所有批准条件没有默认值，业务表单内容不进入sessionStorage；保存请求UUID及摘要，连接异常保留原请求，异参要求明确清除。保存成功重新读取服务端结果；身份失效和离开页面清除受限显示，没有申请时无法提交决定。仅运行真实DOM，视觉检查未执行。

021新增179/180项迁移，平台决定和审计以完整申请/操作/人员复合外键关联；批准企业标识唯一，APPROVE/REJECT与企业/成员空值一致，原请求和审计JSON均受CHECK约束。当前迁移已经实际执行，021及前序迁移字节保持不可变。全部九条专用Mapper语句经原生MyBatis/JSQLParser及SQLGlot读取，核查实际DDL字段和服务端企业注入。

| check_id | 实际断言与范围 | 回执及SHA-256 |
| --- | --- | --- |
| IC-04-REGISTRATION Red | 原生真实所有者登录，三项审查入口断言因404失败，原有六项通过 | IC-04-REGISTRATION-20261004-REVIEW-RED-01；72d211083cf3f9b13ea3771103ae7bbcdbf01f4e54db57ae05ecf4e597c7ea04 |
| IC-04-REGISTRATION-BROWSER Red | 两项登记DOM通过，缺少审查入口导致明确链接断言失败 | IC-04-REGISTRATION-BROWSER-20261004-REVIEW-RED-01；dd6086805056e498095ce180aaa609f5422551a60188943e9a70f1fce93a5a1b |
| IC-03-JAVA | clean verify，79项：共享26、迁移1、身份26、企业26；全部模块SUCCESS，包含九条实际Mapper字段检查 | IC-03-JAVA-20261004-CONTEXT-04；2d1fa47962ef16841d961409a9cb21690e84a2fc52a4ab84f3297e22d5ff295e |
| IC-03-MIGRATION-COVERAGE | 14项；135企业表、17平台表及完整审批复合关联 | IC-03-MIGRATION-COVERAGE-20261004-CONTEXT-01；96fb2876c9b0ed3c66de2e86f698499cb199c3a06e4256e4a33905207df9c8d1 |
| IC-04-CONTEXT-BROWSER | 候选HTTP一组、真实DOM四组及数据库一组，完整断言见企业选择页面章节 | IC-04-CONTEXT-BROWSER-20261004-CONTEXT-03；5c30888e6069b535be7d2e896b51f91fcc1d55a0428ad8c6af7bc58c516bff5f |
| IC-04-REGISTRATION | 11项，10.759秒；真实本人申请、三种审查状态、未知申请/正文处理顺序、参数/Header、CSRF/Origin、真实约束及零业务记录 | IC-04-REGISTRATION-20261004-CONTEXT-01；b26831f1ee0694ab90bc40b4447de22d45709d342d3b8e3dd5413d0999edfe37 |
| IC-04-REGISTRATION-BROWSER | 三项Chrome 154.0.8037.93真实DOM；登记及审查入口、状态筛选、空申请无决定、没有默认许可或页面异常 | IC-04-REGISTRATION-BROWSER-20261004-CONTEXT-01；c4604d94d5605ee4ce7dc41680e78587b80b2033dd11b5d3c7bdbbe9313f610e |
| IC-04-PERMISSION | 五项真实权限拒绝 | IC-04-PERMISSION-20261004-CONTEXT-01；3ce886709f2ccb318640b5a37b18c6d5bbf3212ad82be7484859e56544a79e3e |
| IC-04-INVITATION-FLOW | 五项真实邀请拒绝 | IC-04-INVITATION-FLOW-20261004-CONTEXT-01；cc178fc49c6d2eab22e5887700b7afac221a36c5e5980044184352e2637fcce4 |
| IC-04-AUTH-ERRORS | 两项真实连接中断、恢复及密码确认 | IC-04-AUTH-ERRORS-20261004-CONTEXT-01；753a8698724c300bc4bc6507ee2acd26e74836372f70923966f715a2a57680f0 |
| IC-04-LOCAL-HTTP | 31项，24.197秒；真实账户、密码、会话、审计、恢复及180项迁移重读 | IC-04-LOCAL-HTTP-20261004-CONTEXT-01；1ad9f7ce367dba521a880734a7084c5585c82765bd78efcb5f28fb83099259bc |
| IC-02-LOCAL | 五项；11份实际来源、12条登记及六项唯一开发记录入口 | IC-02-LOCAL-20261004-CONTEXT-01；d7e049acd57e1f32de6754d0a9c77cf43a222056bb2cfa055ff7deb4715d635b |

真实申请提交、批准/拒绝成功、当前企业发现与选择、授权重放、跨企业隔离及并发尚未取得合法客户输入，保持NOT_RUN。角色版本管理、企业名称/属性读取、项目完整接口与页面、拒绝以后修订重提继续实施；此处不登记完整AC-01/02通过。

### 2026-10-04登记申请检查的原始范围

以下原始回执绑定登记申请实施时的代码和部署身份。当前源码及部署已经更新，适用范围按各自输入核对；当前有效审查检查读取前表。登记成功、幂等成功、审批及启用保持NOT_RUN；没有创建公司、其他人员或者替代业务资料。

| check_id | 命令、断言及当前范围 | 回执 |
| --- | --- | --- |
| IC-04-REGISTRATION Red | check_enterprise_registration.py --execution RED-20261004-01；真实登录以后四项入口断言失败，CSRF/Origin通过 | 原始回执IC-04-REGISTRATION-RED-20261004-01；fc0166b37a4d17536ff42fed601aa5c0b40d425b12f375e71deb64f846c04d76 |
| IC-03-JAVA | check_migration.py --check build --execution 20261004-REGISTRATION-05；clean verify，72项通过；187输入及8产物；所有模块SUCCESS | [原生构建](../../.implementation-work/IC-03-JAVA-20261004-REGISTRATION-05/receipt.json)；daabf30e4cc6e4de20512e7a1dab3a90375abe2a2b9f32d9fd91790f1592c380 |
| IC-03-MIGRATION-COVERAGE | check_migration.py --check coverage --execution 20261004-REGISTRATION-01；14项、135企业表及15平台表；40输入 | [结构](../../.implementation-work/IC-03-MIGRATION-COVERAGE-20261004-REGISTRATION-01/receipt.json)；535b8ec74513d22ecf32152d34265c2a314b6037dcaa347bd2ac901464d48273 |
| IC-04-REGISTRATION | check_enterprise_registration.py --execution 20261004-03；六项；本人空列表、Header/分页/不存在ID拒绝、缺少正文无新增数据、真实复合审计外键/去重、CSRF/Origin及产品入口 | [HTTP与数据库](../../.implementation-work/IC-04-REGISTRATION-20261004-03/receipt.json)；9692075a0bf0ec84d90e0d6dd4cafeb270f585ec6e724df774d127ac4ff1c88e |
| IC-04-REGISTRATION-BROWSER | check_enterprise_registration_browser.py --execution 20261004-03；两项；实际登录、管理员标签和明确选择、当前账户候选、实际CSRF、空列表、必填声明及刷新；无视觉流程 | [真实DOM](../../.implementation-work/IC-04-REGISTRATION-BROWSER-20261004-03/receipt.json)；f75212747873e2d6300858e35e9ea6b59e1a70ed11c8cd4b31bf1fe0c1033089 |
| IC-04-PERMISSION | check_permission_decision.py --execution 20261004-REGISTRATION-02；五项当前真实权限拒绝 | [权限](../../.implementation-work/IC-04-PERMISSION-20261004-REGISTRATION-02/receipt.json)；a9cb9f821d7d6000845713d532a54111e6f9edbf50f29971c8b14f8b2d41648e |
| IC-04-INVITATION-FLOW | check_member_invitation_flow.py --execution 20261004-REGISTRATION-02；五项当前真实邀请拒绝 | [邀请](../../.implementation-work/IC-04-INVITATION-FLOW-20261004-REGISTRATION-02/receipt.json)；6d125fa36ce2cf2bee1fb9c6cb0870eeac3749861970a9c3c4abe658ab519471 |
| IC-04-LOCAL-HTTP | check_local_identity.py --execution 20261004-REGISTRATION-01；31项；21.444秒；当前账户、密码、会话、审计、恢复、应用身份和178项迁移重读 | [账户](../../.implementation-work/IC-04-LOCAL-HTTP-20261004-REGISTRATION-01/receipt.json)；9a201d1d7ae5bcbd8ab9b3c605c2e95e29fa4ed8aa98247e80c93d9a53ed1a9e |
| IC-02-LOCAL | check_inputs.py --execution 20261004-REGISTRATION-02；五项实际原件及六项唯一开发记录入口 | [来源与索引](../../.implementation-work/IC-02-LOCAL-20261004-REGISTRATION-02/receipt.json)；24d4dfa3cb437ad0b30a8db0e80ceebe2808ec118ba5b8750e8f6b47071642e6 |

### 既有实施检查及适用条件

本节保留既有原始检查的实际时间与范围；输入或部署发生变化时需要复验，当前有效集合读取前表。记录过往原生检查通过不代表当前完整业务通过。

BACK-PERMISSION-01继续本记录，实施ID-09的GRANT、REVOKE和DISABLE_MEMBER。读取PRD§8.1、后端§2.2/4.3、当前企业范围、上下文、身份入口、permission_grant、version_registry及审计/Outbox。Given：真实本机PLATFORM_ADMIN尚未取得任何企业身份；When：实际请求权限决定入口；Then：企业准入在业务正文解释以前拒绝，重复请求不建立企业、成员、授权、操作或事件，Schema/上下文/幂等Header及CSRF/Origin具有明确结果。合法授权、撤销、停用、当前条件及委派、并发、原结果重读与恢复继续使用获授权企业和人员验证，不生成授权业务样本。

BACK-INVITATION-SERVICE-01继续本记录，完整设计读取接口补充设计§4.9/4.11及ID-07/08。Given：真实本机所有者已认证、没有当前企业或本人邀请；When：提交邀请创建和本人接受请求；Then：403拒绝且不创建企业、成员、身份、邀请、操作或授权，必需Header缺少时422，CSRF及Origin继续有效。tests/test_member_invitation_flow_runtime.py实际执行五项检查，四项因缺少入口返回404而失败、一项通过，无执行异常；Red回执为IC-04-INVITATION-FLOW-RED-20261002-01，SHA-256为37003da12c03252ea23ee6e387773ecc993653bf9be4bff053a377b90d315d43。批准角色、成功创建/接受、重放、期限及撤权继续使用真实企业和人员验证；不将本次拒绝检查登记为完整业务通过。

BACK-MEMBER-INVITATION-01继续本记录。读取PRD§8.1、后端ID-07/08/09、当前成员表、身份绑定、受保护事务、本人路由和迁移检查。Given：真实MySQL、真实本机用户且没有客户企业记录；When：核查邀请字段、完整外键、并发重复限制、期限及状态CHECK和接受路由；Then：数据库保存全部邀请身份、角色、范围、授权依据、凭据摘要和版本，路由完整关联目标主体，迁移不创建客户记录。tests/test_member_invitation_runtime.py执行六项真实元数据检查；完整邀请创建、接受及撤销继续使用获授权企业和人员验证。

接口补充设计§4.9/4.11维护完整字段、四项状态、无动作语义、批准角色/依据、本人路由、恒定时间凭据、认证加密、共同提交及撤权水位。018/019项DDL通过真实Liquibase执行；七项当前数据库检查确认邀请23字段、五项企业外键、唯一键、八项结果CHECK、完整路由及角色审批/期限/关联，客户记录为零。InvitationCredential由创建和接受服务消费；三个原生检查确认真实随机秘密、比较及无效输入拒绝。单独摘要比较不授予权限，当前身份、范围、期限和依据继续共同核查。

MemberInvitationService创建时读取同企业ROLE_DEFINITION、JCS摘要、ACTIVE批准版本、审批人员完整当前身份及期限，展开完整动作并要求当前INVITATION_ADMINISTRATION用途及member.manage。原管理授权ID和版本随完整条件保存；无动作角色仅保存治理核查依据，action=null不创建业务授权。重复成员409；INVITED且邀请期限已经结束时共同登记EXPIRED及新邀请。接受使用本人路由和独立受限范围，企业锁以后重查实际账户/会话、绑定、凭据、期限、原批准角色/委派及依据；新增其他授权不能替代已撤销原依据。

接受共同提交ACCEPTED邀请、ACTIVE成员、正式成员路由、实际permission_grant、auth_epoch、原响应、审计及Outbox，不自动选择企业。过期本人请求登记EXPIRED及同步事件以后返回拒绝；已接受重放只读取原结果，核查当前成员及原授权状态，不创建或恢复授权。创建增加权限水位并更新当前操作会话版本；其他旧上下文继续按照当前水位拒绝。合法业务、期限、重放、并发及界面仍需真实客户验收。

InvitationResponseCipher使用JDK AES-256-GCM、随机96位nonce、128位认证标签及完整AAD保存ID-07原响应，ID-08秘密正文摘要采用HMAC及明确用途。local的独立key_id为2c397664-7c5d-499c-b2cf-b5498dd6307b，目的INVITATION_RESPONSE；私密文件0700/0600及环境注入实际使用，值不进入回执、审计、路由或Outbox。已有邀请操作存在时不重新生成秘密。四项新增原生检查核验真实产生凭据的加密/重读、nonce独立、认证标签/关联对象改变拒绝、HMAC及封闭输入，并通过成熟解析器比较十四条MyBatis SQL与原始DDL及原生ALTER字段。

BACK-MEMBER-IDENTITY-01沿用本记录；已读取PRD§8.1/11、后端§2/3.2/4.3、接口补充设计§4.7/4.8、实际企业事务及身份Mapper。Given：真实MySQL及本机所有者账户；When：迁移并读取企业身份表元数据；Then：mode必填且没有默认值，本地UUID具有账户外键和企业内唯一约束，CHECK启用并保持完整认证分支，issuer/subject允许本地空值且subject采用精确字符比较，企业及成员数量保持零。Given：实际密码登录取得四字段绑定；When：Java通过唯一Schema读取邀请描述并比较；Then：完整值保持一致；未知字段、缺少字段、分支替换、重复JSON字段均明确拒绝。输入来源为当前实际登录响应，传输View不能创建认证主体。

017-member-identity-mode.sql维护166—173项独立DDL。IdentityBindingSchema.readBinding与AuthenticatedIdentityBinding.matches(View)供ID-07/08消费，成员、期限、邀请凭据及授权继续核验。MemberInvitationBindingIT通过独立原生Java入口使用当前程序和实际HTTP登录，四项检查与Maven单元检查分别登记。数据库检查限真实元数据、约束启用状态及当前无企业记录；已有OIDC业务记录迁移、合法成员写入及完整邀请接受继续验证。

BACK-IDENTITY-BINDING-01继续使用本记录。已经检索ID-03及ID-07/08、ApplicationSessionIdentity、PlatformIdentityDirectory、当前User Schema和本地真实运行证据；身份子资源的字段及边界由接口补充设计§4.7维护。Given：当前真实本机用户和有效应用会话；When：读取本人身份绑定子资源；Then：返回local、实际user_id及空issuer/subject，共享Envelope及请求Header一致，重复读取不产生企业成员或授权。Given：未知查询、缺少Schema版本或匿名请求；When：读取同一子资源；Then：明确422或401拒绝，没有身份内容。OIDC真实成功及完整邀请流程继续保留未验证状态。

本人绑定查询已经在当前制品运行。AuthenticatedIdentityBinding的构造器为private；实际认证入口建立绑定，Verified在构造及事务内核验实际user_id和完整原值。OIDC绑定来自已经与主数据库原值比较的VerifiedOidcIdentity，本地绑定的issuer/subject为空。当前本人接口返回前再次核验实际会话及绑定；账户页面显示经过同一用户核验的登录方式和账户身份。真实HTTP确认四字段、共享Envelope、请求Header、重复读取、新请求UUID及422/401拒绝，数据库企业/成员数量均为零。真实Chrome确认本人页面显示当前实际身份，并完成全部既有五项账户操作及审计回归。

本地浏览器检查继续使用本记录。当前已经核查Playwright CLI官方完整README、项目Playwright技能及浏览器E2E执行规范、本机Chrome/npx和实际部署身份。固定@playwright/cli 0.1.22及其官方Playwright依赖，通过headless Chrome读取DOM并操作真实表单；全部服务请求来自当前真实应用，不启用模拟、截图、视频或视觉工具。凭据从私密文件读取，检查日志仅保存公开身份及状态；浏览器输出使用项目output/playwright/目录。

Given：实际本机管理员、当前私密凭据和运行制品；When：通过登录表单进入本人账户并打开管理入口；Then：本人身份、当前真实账户列表及操作控件与数据库一致。Given：只有一个真实ACTIVE管理员；When：确认停用或者移除管理员角色；Then：页面显示实际REVISION_CONFLICT且账户保持不变；原请求身份保留，显式清除以后才产生新请求。Given：本人页面、当前密码及当前版本；When：真实表单提交新密码；Then：数据库、原响应和审计一致，页面要求重新登录，旧会话拒绝，新密码能够通过真实表单登录并注销。密码输入、CSRF和Cookie不进入检查输出，localStorage/sessionStorage中没有秘密。

BACK-LOCAL-MANAGEMENT-01继续使用本记录。已检索开发索引、IMP-01—04、后端§2.1/2.4及接口补充设计§4.4，核查当前本地SecurityFilterChain、账户目录、恢复工具、账户页面和真实运行回执。本地受控创建、账号禁用与凭据变化进入接口补充设计§4.6；新增平台操作和审计保持独立认证范围，密码通过独立HMAC秘密保护请求摘要，企业成员及项目权限要求保持完整。

Given：当前真实管理员及有效Cookie；When：请求LOCAL-01账户列表；Then：返回当前实际账户元数据、共享Envelope及分页字段，没有密码字段。Given：真实本人会话、当前密码及账户版本；When：LOCAL-06提交新凭据；Then：当前版本增加一次、保存一个操作和审计、全部旧会话拒绝、新凭据可以登录；重新登录后使用同一键重放返回原结果且版本不再增加。Given：错误当前密码、旧版本、未知字段、缺少Schema/幂等键/CSRF或其他Origin；When：提交相应请求；Then：明确拒绝且账户和操作不变。Given：只有当前真实ACTIVE管理员；When：请求停用或移除管理员角色；Then：REVISION_CONFLICT且保留真实管理员。新增真实人员创建、状态变更及管理者撤权的成功路径使用相应获授权真实身份验证，不创建人员样本。

LOCAL-01—06已进入当前制品。变更按LOCAL协调行、当前账户/会话及目标账户加锁；BCrypt在事务外计算，密码版本在事务内重读。账户变更、全部目标会话撤销、原响应及审计共同提交。原请求由Jackson/JCS规范字节及独立HMAC-SHA256秘密标识，保存固定key_id；未知秘密/Schema版本立即报告错误。本地秘密文件local-management-secret.json权限0600、父目录0700，通过环境变量注入；已有操作时缺少秘密文件拒绝生成新秘密。浏览器仅保存原请求身份及公开版本，密码不进入持久浏览器存储或审计。

实际LOCAL-06验证使用本机已有管理员和两份真实登录会话。新凭据在发送前保存私密候选文件并同步文件及目录，成功结果确认以后原子替换owner-credentials.json。数据库重读确认credential_revision增加一次、原响应和六字段审计一致；Python的rfc8785及hmac独立计算与Java保存的请求摘要相等。两个旧会话返回403；新凭据登录302；新会话重放原请求返回原request_id/operation_id及完整原响应，账户版本保持不变；同键异参409且只有一个操作和审计。检查秘密值未保存到响应，回执仅登记key_id、公开结果及断言。

账户列表、分页输入拒绝、Schema/CSRF/Origin和封闭字段、错误当前密码、旧版本、已有用户名、本人管理员重设拒绝、最后管理员停用/降权保护及本地页面控件均已通过实际HTTP检查。真实Chrome的五项DOM检查通过登录及管理入口、最后管理员停用/降权保护和请求清除、已有真实用户名拒绝及密码清除、本人密码变更/旧会话拒绝/新密码登录/注销。数据库通过实际幂等键及主体UUID查找唯一操作，原响应的request_id与浏览器HTTP Header相等，原响应版本增加一次，完整六字段审计及当前凭据版本一致。浏览器localStorage/sessionStorage没有实际密码、CSRF或会话Cookie，真实Cookie为HttpOnly/Lax，页面模块没有未处理异常。真实新增人员、其他账户启停/重设、显示名称/角色成功变更、管理者撤权及正式S17/S18页面集成继续验证；视觉验收未执行。Node对相关模块和浏览器检查执行语法检查通过。

BACK-CONTEXT-01继续使用同一IMP-04记录。已经检索当前索引、LLD-后端§2.3/2.4/3.2和ID-04、共享Schema、原生operation_record/audit_event/outbox_event、现有会话及企业事务代码。本人路由、目标成员和账户核验保持完整，内部context_id用于固定Outbox资源身份；具体字段与事务顺序由接口补充设计§4.5维护。

Given：真实管理员没有企业成员，目标参数使用实际用户ID且数据库不存在企业；When：携带实际CSRF、Origin、Schema、上下文版本及新幂等键请求企业选择；Then：403 ACCESS_DENIED、重复请求保持拒绝，当前会话上下文不变，不创建operation_record/audit_event/outbox_event。匿名、缺少CSRF、其他Origin、未知字段、缺少Header及正文/Header版本不相等分别验证。已保存真实Red回执IC-04-LOCAL-HTTP-RED-TENANT-SELECTION-20261002-01，实际404 != 403，SHA-256为3dae233dc790273f14cccf3711f06f85ac78e1f6a923a4867c1871d167ea9df7。

企业选择代码使用TenantSelectionService和受保护TenantSelectionMapper。ApplicationSessionIdentity.lockCurrent进入同一企业事务，local及oidc分别锁定当前账户和会话。服务重新读取本人有效成员和路由，锁定本人上下文，检查当前版本和幂等摘要；已有结果重新校验Schema、成员、水位及上下文。新结果通过版本比较更新上下文，并在共同事务保存operation_record、audit_event及outbox_event。控制器采用Jackson的重复字段拒绝和封闭Schema，未知字段及缺少必需Header返回422。

015-platform-context-identity.xml通过Liquibase原生操作保存四项迁移；真实MySQL重读确认现有context_id有效、全局唯一、非空且类型为ascii CHAR(36)。原生20261002-37构建通过，应用使用独立保存的同一制品；合法选择、重放、异参、并发、撤权及事件消费继续取得真实企业证据。

Given：真实本机所有者及首次设置账户；When：提交当前密码登录；Then：302进入账户页面，五字段和登记相等，Cookie为HttpOnly/Lax且响应没有密码。Given：真实Cookie/CSRF/Origin及账户；When：缺少CSRF、不同Origin、错误密码或再次设置；Then：401/403明确拒绝。Given：真实登录会话；When：注销并使用旧Cookie；Then：204以后旧会话401。Given：真实MySQL；When：原生重读；Then：唯一ACTIVE首次管理员、BCrypt、150张企业及平台表、178项changeset、实际REVOKED会话和有效唯一非空context_id符合设计。

Given：真实管理员凭据文件及登录会话；When：执行本机管理员凭据恢复；Then：credential_revision 增加一次，旧会话立即返回403，新凭据登录成功，文件权限0600。Given：另一个恢复操作持有原生文件锁；When：再次执行恢复；Then：立即拒绝且数据库凭据版本不变。Given：真实管理员已成功登录；When：连续五次提交变更密码；Then：数据库 failed_attempts 逐次增加至五次，锁定期限为十分钟，正确密码也被拒绝；管理员恢复后解除锁定、清除失败次数、撤销旧会话并能够重新登录。

Given：真实管理员尚未加入企业且没有企业数据；When：携带当前 Cookie 和 X-Schema-Version=1.0.0 读取 /api/session；Then：200响应的共享 Envelope、请求 UUID、七项 data 字段、禁止缓存及 CSRF 均有效，成员和动作列表为空、上下文为空；原生 MySQL 重读确认本人 session_id/user_id、context_revision=0及企业和水位为空。重复读取保留相同当前会话字段，返回的 CSRF 可以完成实际注销，注销后的会话读取返回401。Given：真实管理员或匿名浏览器；When：缺少 Schema Header 或没有认证读取；Then：分别422 SCHEMA_UNSUPPORTED 和401 AUTHENTICATION_REQUIRED。运行记录保存 CSRF 摘要，秘密值不进入验收回执。

Given：实际启动记录和当前 Java 进程；When：核对记录、程序字节、进程命令及历史记录；Then：运行文件与原生构建摘要相等，具有独立路径及0400权限，实际 PID 和操作系统身份一致。

三十一项HTTP/数据库/程序检查限IC-04-LOCAL-HTTP；不登记AC或完整IC-04通过。65项Java原生检查包含共享26、迁移1、企业12及身份26；企业包含六项既有拒绝、两项权限Schema/SQL及四项邀请认证加密、HMAC、封闭输入和十四条SQL。十四项结构检查覆盖135企业表及13平台表。自然经过十分钟解除锁定、进程中断恢复及真实企业完整流程继续验证。

| check_id | 实际结果 | 回执及 SHA-256 |
| --- | --- | --- |
| IC-04 本地账户 Red | 四项生产入口/输入断言失败，既有检查正常 | [原始回执](../../.implementation-work/IC-03-JAVA-RED-LOCAL-ACCOUNT-20261002-01/receipt.json)；a73102f397d58c64a146d3455e239bb6ccee39f421694dc563f2dd05c23f6a47 |
| IC-04 持久注销 Red | 三项入口及配置断言失败，既有48项正常 | [原始回执](../../.implementation-work/IC-03-JAVA-RED-LOGOUT-PERSISTENCE-20261002-01/receipt.json)；274aa71f354a3602cb2f0b317d17e50d96a3df973905ec2a0f43beadbfc4713f |
| IC-04 本地账户迁移 Red | 新增结构断言确认缺少字段及表，既有八项正常 | [原始回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-RED-LOCAL-ACCOUNT-20261002-01/receipt.json)；674b20eac46074fb16cbea121bc4bc9f63fb372d6d803b42b708475bedbab921 |
| IC-04 持久注销迁移 Red | 新增结构断言确认缺少表，既有八项正常 | [原始回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-RED-LOGOUT-PERSISTENCE-20261002-01/receipt.json)；dedc8eee35768629cd31bb773095781979191bf45071648f4582746e64c9aa5b |
| IC-04 既有凭据恢复 Red | 真实账户恢复检查确认既有凭据文件无法完成更新；其余八项正常 | [原始回执](../../.implementation-work/IC-04-LOCAL-HTTP-RED-CREDENTIAL-20261002-01/receipt.json)；f173705269dadcb3cbcb17c876622cd60d5ff24addb04cf67a3fe0dd6d642958 |
| IC-04 本人会话 Red | 真实管理员登录302后，实际 /api/session 返回404，当前会话功能断言失败 | [原始回执](../../.implementation-work/IC-04-LOCAL-HTTP-RED-SESSION-20261002-02/receipt.json)；5c91f382490de2799391b4da97e367e4fe6127acf2755d10ca9c60c3fa627d6d |
| IC-04 业务上下文迁移 Red | 新增成员路由及上下文结构断言失败，既有十项检查正常 | [原始回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-RED-SESSION-20261002-01/receipt.json)；31ff1dd47cad510a0e9f6962d007c3cb215ccf608a601394ce684c5356869814 |
| IC-03-JAVA | PASS；65项原生检查；178项输入及8项输出；聚合工程及十个子模块SUCCESS | [当前回执](../../.implementation-work/IC-03-JAVA-20261002-37/receipt.json)；06567e0bd0f25b055692d9dea4b59aabb2c1ee00415cc9e416f2c68b0a478b67 |
| IC-03-MIGRATION-COVERAGE | PASS；十四项、39项输入、135企业表及13平台表 | [当前回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-20261002-20/receipt.json)；ba00a139b98451fbcb7a25407ec2be7379c7b113a60af5d9eaea21648171feef |
| IC-04-MEMBER-IDENTITY Red | 真实数据库字段及本地账户关联断言失败 | [原始回执](../../.implementation-work/IC-04-MEMBER-IDENTITY-RED-20261002-01/receipt.json)；da8260fc807a6ef757ea46417cd387d99ed67787285a47942aa382b467978972 |
| IC-04-MEMBER-IDENTITY | PASS；四项真实数据库检查；28项输入、2项输出；没有客户记录 | [当前回执](../../.implementation-work/IC-04-MEMBER-IDENTITY-20261002-07/receipt.json)；5402a859269fc4c15735895a7c317488fd66f3eff027aff8ba873b2d04a6c142 |
| IC-04-MEMBER-INVITATION Red | 真实数据库的邀请字段、外键、结果CHECK和接受路由四项断言失败，另外两项正常，无执行异常 | [原始输出](../../.implementation-work/IC-04-MEMBER-INVITATION-RED-20261002-03/output.log)；回执4378d220c9b04f452aa1fbd2a30f83af1761d86730ece5887789ec1dc701f30f |
| IC-04-MEMBER-INVITATION | PASS；七项真实数据库检查；28项输入、2项输出；完整邀请、本人路由及批准角色字段/关联/CHECK；客户记录均为零 | [当前回执](../../.implementation-work/IC-04-MEMBER-INVITATION-20261002-06/receipt.json)；fff50a74aa2a213abc7500d7effc7635713583ab9a47f535d914274eea455daf |
| IC-04-PERMISSION Red | 真实管理员五项检查中四项接口行为断言失败、一项CSRF/Origin检查通过，无执行异常 | [原始回执](../../.implementation-work/IC-04-PERMISSION-RED-20261002-01/receipt.json)；ca37dec6588a43677779ee92e6197d239e54caa190749548d2a007c7a9d4b39e |
| IC-04-PERMISSION | PASS；五项真实HTTP检查；135项输入、1项输出；无企业身份拒绝、重复不创建记录、Header、CSRF及Origin | [当前回执](../../.implementation-work/IC-04-PERMISSION-20261002-05/receipt.json)；a5bddc6f1fb857ff141bc623c4c7f0d96d937e72b26ab09f9f372b9087479bcd |
| IC-04-MEMBER-BINDING | PASS；四项Java真实登录响应检查；16项输入、2项输出；完整绑定及封闭/分支/重复拒绝 | [当前回执](../../.implementation-work/IC-04-MEMBER-BINDING-20261002-06/receipt.json)；8ee8cfff5f05276494141ac0d74a0e93069eb6300a9e8730949dacb337a1cd02 |
| IC-04-INVITATION-FLOW Red | 真实邀请入口四项404行为断言失败，CSRF/Origin通过，没有执行异常 | [原始回执](../../.implementation-work/IC-04-INVITATION-FLOW-RED-20261002-01/receipt.json)；37003da12c03252ea23ee6e387773ecc993653bf9be4bff053a377b90d315d43 |
| IC-04-INVITATION-FLOW | PASS；五项真实HTTP拒绝检查；135项输入、1项输出；当前企业及本人邀请路由、Header、重复零客户记录、CSRF/Origin | [当前回执](../../.implementation-work/IC-04-INVITATION-FLOW-20261002-02/receipt.json)；b179b208405fcbd97ad1e995e941ae6ead60a1af4197e8634fad3c5ed2729131 |
| IC-04 企业选择 Red | 真实会话携带必需字段请求，接口行为断言失败；既有真实登录及会话读取正常 | [原始回执](../../.implementation-work/IC-04-LOCAL-HTTP-RED-TENANT-SELECTION-20261002-01/receipt.json)；3dae233dc790273f14cccf3711f06f85ac78e1f6a923a4867c1871d167ea9df7 |
| IC-04 上下文身份迁移 Red | 稳定上下文UUID资源断言失败，既有十一项检查正常 | [原始回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-RED-CONTEXT-IDENTITY-20261002-01/receipt.json)；4d153c71b026302224be6e25d9f0a12ca0e6a11e67ff90ffce6eb516ab8fe60f |
| IC-04 本地管理Red | 真实管理员列表请求返回404，新增业务断言确认尚无入口 | [原始回执](../../.implementation-work/IC-04-LOCAL-HTTP-RED-LOCAL-MANAGEMENT-20261002-01/receipt.json)；88b464ad4cf0c6b7fe401b482d6d848df988c6c8fe906eac642d7b93d1317704 |
| IC-04 本地管理迁移Red | 新增操作及审计结构断言失败，既有十二项检查正常 | [原始回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-RED-LOCAL-MANAGEMENT-20261002-01/receipt.json)；6eb3c6c9688dc92d14abee509ac3406bd02d59e2dc4f6c0647d158ec6b2fbb80 |
| IC-04 本人绑定Red | 真实管理员登录后请求本人绑定，404 != 200，业务断言失败 | [原始回执](../../.implementation-work/IC-04-LOCAL-HTTP-RED-IDENTITY-BINDING-20261002-01/receipt.json)；c47a71b5a3ffa074872531cd75a387ede4cb08dd0548c4f4f2404a3ad1f9746f |
| IC-04-LOCAL-HTTP | PASS；31项真实检查，原始输出28.698秒；144项输入、10项输出；本人绑定、账户管理、密码变更/重放、HMAC、审计、最后管理员、登录/会话/选择拒绝/恢复 | [当前回执](../../.implementation-work/IC-04-LOCAL-HTTP-20261002-21/receipt.json)；7147f507ca4d26db1a18047035cf239916725b4c0db82f8d2205c16775ed77aa |
| IC-04-LOCAL-BROWSER | PASS；五项真实Chrome DOM及审计重读；150项输入、2项输出；实际身份、当前原响应及审计、密码/请求清除、Cookie与浏览器存储；Chrome/Node/工具/应用字节绑定；没有视觉流程 | [当前回执](../../.implementation-work/IC-04-LOCAL-BROWSER-20261002-10/receipt.json)；a982bdf0496d084ff84e630bdc77ff7ff69b473d70bcc9fd1e4015d9ce29396c |

命令见[实施检查命令](../commands.md)。当前180项迁移及独立0400运行文件、启动和数据库身份由当前回执绑定；恢复演练继续执行。其他部署及原始回执以各自保存的时间、输入和制品为适用条件。

## 关联影响及未完成内容

当前回执的全部输入和产物SHA-256按本次企业选择集合重新核对，应用进程及独立制品由当前运行检查绑定。合法企业业务、批准角色治理、并发及正式界面继续验收；完整集合由实施续接记录和测试报告维护。

当前模块供 C02—C08/C10 的企业服务和主数据库事务使用；共享类型来自 medical-contracts，迁移来自 medical-data。关联 IMP-01 的工程和依赖、IMP-02 的真实输入、IMP-03 的结构和类型、IMP-06 的正式页面。界面没有执行视觉检查。

继续完整 C02 企业切换、成员/项目/动作权限、内部受限身份、权限水位、账号治理与前端集成；ID-03 的真实成员、所选企业、动作摘要及撤权分支继续验证。真实验证锁定自然到期恢复、账号停用、重复/并发设置、凭据恢复过程的中断、数据库/Redis 故障和两企业授权。可选 OIDC 的真实发行者、批准配置和标准协议另行验证。客户资料、企业成员、模型预算、生产环境及业务指标仍需相应批准输入，全部42项工单范围保持完整。
