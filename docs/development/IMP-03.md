# IMP-03 共享 Schema 与完整迁移

2026-10-09当前正文迁移为037—039，关联完整正文流程的唯一[IMP-16](IMP-16.md)。实际MySQL登记254项ChangeSet，coverage.json登记163张企业表及200张物理表；既有迁移身份和checksum保持。039提供独立field_definition_id、既有值填充及document_evidence_projection完整身份和外键。实际迁移及完整备份回执为`.implementation-work/IC-03-TASK-PLAN-MIGRATION-20261009-DOCUMENT-REVISION-PROJECTIONS-01/receipt.json`，SHA256为b88f8766db3d4d69c78d447e9aa4143bc5cebbe1f3553a7916db617c32a0fd5c，exit_code=0、inputs_unchanged=true。正式恢复验收继续原要求。

迁移结构由Liquibase、JSQLParser与SQLGlot核验；Liquibase生成的外键删除语句直接由SQLGlot核验。`tests/test_migration_bundle.py`的十八项检查于2026-10-09通过，覆盖200张物理表、主键、完整企业外键、唯一键及字段定义身份填充。medical-data独立package和MigrationBundleTest已核验254项资源及原core.sql摘要。正文状态及后续问题、引用水位由业务消费者维护；代码状态引用实施任务计划，结构检查与客户验收分别登记。

## 当前计划关系迁移执行

2026-10-08，完整计划关系的唯一主记录为[IMP-12](IMP-12.md)，本记录维护IMP-03的正式迁移制品及部署过程，工单状态继续引用实施任务计划IN_PROGRESS。已经核查036-task-plan-relations.xml、master.xml、coverage.json、原实际数据库前置条件与local_runtime.migrate；当前新增scripts/check_task_plan_migration.py使用既有Liquibase、MySQL、ZipFile和gzip，核验制品完整资源、原数据库身份及三张原计划表为空条件，调用原完整备份和迁移入口，然后逐项重新读取原登记及备份。

TASK-PLAN-RELATIONS-MIGRATE-01原71412已取得exit0终态，回执`.implementation-work/IC-03-TASK-PLAN-MIGRATION-20261008-TASK-PLAN-RELATIONS-MIGRATE-01/receipt.json`，摘要f7d5f458897f58d96f24cd777aaa6e6f3d3fde00b530f88f269a438f026f1a3e、inputs_unchanged=true。实际MySQL保持原server_uuid=5ecfb746-be18-11f1-95e4-d822203bc633与medical_local；原237项ID/author/filename/checksum全部保持，恰好新增238—249十二项，当前登记249项。036源码摘要651feda48a95889aa632f81b1919c055b7e0859a8df1da5edd37e54edf372db4；原正式library与CLI摘要分别2855f440dfa559978cdd713d7c89fed4f1d8f838e4b1546b1ce7683ef471b97b和0d69c96c44d3f8f7bb7660e28f5d63a2ac24d3318b451e2e49c596f3e9f29bb4，均与迁移日志独立保存于原执行目录。

实际迁移前完整备份为`.implementation-work/runtime/migration-backups/5e6b25b0-b8f5-47ef-b770-d1353aaafd1d.sql.gz`，摘要05514cc1f9c7c5a08d65bbc7e9baa6eb2f1d775f6b80e6471de73c912a1f5246，同名JSON保存原database/server_uuid/path及摘要；两份文件均0600，完整GZIP读取3207663字节通过。此结果证明实际备份及迁移，完整恢复和历史业务核验继续原要求。

当前迁移定义包含161张企业表，TenantAccessTest保持逐表隔离拒绝并核对完整数量。RUN-TOKEN-UPLOAD-TASK-PLAN-BUILD-FINAL-02原8916已成功终止，448项Java、前端strict及生产构建通过，回执4b97faee4cd5c021e00db150b5e57a1bf1f6bee2460bb82a91b3164da6f6781d、inputs_unchanged=true。完整结构核验ACTUAL-03原88064成功终止，十二张相关表155个字段、34项主键及唯一键、35项外键和13项启用Check通过，唯一IMP-12维护回执4d4dc8f4ae9eb572f52ab5758bdfd853cb78a33d928211116395a01c27f434e2。当前新制品部署和六项原操作通过，由唯一IMP-06维护。migration.json保留原迁移执行范围；实际结构由独立ACTUAL-03结果证明。公开任务创建、计划保存、模型采用、完整共享发布与业务验收继续未完成。

2026-10-08当前SourceUploadTransport/UploadPartReceiver准入及原Lease由SOURCE-PART-USER-CAPACITY-BUILD-FINAL-01完整354项验证，回执ff649d3cf3a4e6328d317281d04204648dbd447d43176ccb640c99eed842e1a0，inputs_unchanged=true。原操作、分段、进度、审计和Outbox保持原字段及企业事务，237项迁移和153张企业表保持。当前PID15882部署由唯一IMP-06验证，企业与用户名额及恢复范围读取唯一IMP-08。下方回执仅证明各自原输入范围。完整共享发布和全部KN-02继续验收，IMP-03保持IN_PROGRESS。

2026-10-08当前SOURCE的WRITING原对象恢复由唯一IMP-08维护。SOURCE-PART-WRITING-BUILD-FINAL-01完整352项Java、39项共享输入及前端构建通过，回执9e8a25ce0cef7cd817262b221e9c97868fc33accc45a76ce6e02b6483081c4e1，inputs_unchanged=true。原分段、进度、operation_record、同步审计与Outbox共享原事务，237项迁移及153张企业表保持原结构；外部Schema与ErrorCode没有新增字段。当前PID7855及部署由唯一IMP-06验证。下方351项及以前完整组合为STALE，仅保持原输入范围；完整共享发布及KN-02全部恢复继续验收，IMP-03保持IN_PROGRESS。

2026-10-08当前Source分段在途操作、取消信号和已完成并发重放读取唯一IMP-08。没有新增迁移，原237项迁移及operation_record、分段、进度、同步审计与Outbox结构保持。当前SOURCE-PART-PROCESSING-BUILD-FINAL-04完整351项Java、39项共享输入、实际公开HTTP及前端构建通过，回执22601167e092857bceab9a97f71bc3586c6429d07ce336afd9c4b49c57dd7888，inputs_unchanged=true。PROCESSING返回202，COMPLETED返回200及固定原响应；新制品已在原本地PID1490运行，部署与六项原操作由唯一IMP-06验证。原03及下方构建仅保持各自原输入范围，当前整体为STALE。全部共享Schema发布及完整KN-02恢复继续验收，IMP-03保持IN_PROGRESS。

2026-10-08关联KN-02原请求完成事务：复用原operation_record的企业/主体/操作/键唯一范围及PROCESSING/COMPLETED状态，没有新增迁移。SourceUploadMapper对原操作执行带当前主体、请求摘要、目标、Schema及revision的CAS，并重新读取原响应和审计/Outbox；完整350项SOURCE-PART-OPERATION-BUILD-FINAL-01已经通过，回执b54c6eac69085f14a3292b00a1327c5861a92756e089453e645be9021d9a077f。完整共享发布与KN-02恢复仍待验收，IMP-03保持IN_PROGRESS。

2026-10-08关联SOURCE分段接收：SourceUploadMapper.receive使用当前主体、原revision与received_size执行受限更新；分段及来源进度共享企业事务并重读核验。SOURCE-PART-BUILD-FINAL-01完整350项通过，237项迁移与153张企业业务表由当前构建核验；本次没有新增迁移，来源Schema摘要保持原版本。实际部署与源码一致，由唯一IMP-06维护；KN-02请求身份及完整共享发布继续实施。

## 当前公开上传声明迁移

2026-10-08，关联BACK-SOURCE-UPLOAD-01，由唯一[IMP-08](IMP-08.md)维护实际创建、保存、重读与原响应检查。035-source-upload-request.sql追加完整逐文件请求、声明、原操作、当前许可与水位，原001—034保留原版本；实际MySQL应用237项changeset。当前153张企业业务表继续使用原TenantScope与ProtectedTenantInterceptor，SourceUploadMapper执行当前主体参数核查。当前十六项迁移覆盖检查通过，SOURCE-UPLOAD-DECLARATION-FINAL-01回执SHA-256为62c9576e0bad9e672ebc063e876095b4b4a6d89d385c5b4dabe9d159de192faf，inputs_unchanged=true。完整350项构建、当前Jar部署与实际HTTP均通过，完整共享发布及前端类型消费继续原范围。

完整迁移前备份使用原BACK-LOCAL-01授权工具，a6e10d80-a7ad-4532-a78b-4a49048e1b77.sql.gz摘要3e70f36812ba9bc1277f7eba8786fb36fea4d0301aa1c5e5dad0d2d2eecceb8a，同名JSON绑定medical_local及server_uuid=5ecfb746-be18-11f1-95e4-d822203bc633。原声明不产生来源版本、rights_revision或专业核准；完整恢复和全部需求继续验收，IMP-03保持IN_PROGRESS。以下数据库结果按各自原执行范围读取。

2026-10-06原管理Controller关联：当前正式源码直接注册十个原管理Controller，原完整框架表及Mapper继续由原组件使用；唯一[IMP-06](IMP-06.md)维护实际HTTP、当前权限及原表内容检查。共享Schema、原001—034迁移和已应用236项changeset保持原字节，没有新增数据结构或客户记录。当前源码尚未部署，完整十八模块、客户业务及恢复仍须验收。

## 原管理模块使用的完整数据库结构

2026-10-06；BACK-RUOYI-MANAGEMENT-DATA-01沿用本主记录，状态来源为实施任务计划IMP-03，保持IN_PROGRESS，关联IMP-01/04/06/37。依据用户框架复用要求、HLD的原基础管理组件及FRONT-R03/BX-25，实际读取原business/sql/ry_20260417.sql、原system/quartz/generator的Mapper与当前迁移。034-ruoyi-management-tables.sql保存原操作日志、登录日志、字典类型/项、任务/执行日志、公告/已读、生成表/字段十张表的完整结构。真实MySQL已应用全部236项迁移，具有189张业务/平台/框架基表及一项兼容View，其中框架表二十张。

Given当前固定RuoYi源码、原DDL及真实MySQL；When执行新增迁移并使用原Mapper读取；Then原字段顺序、类型、默认值、主键、索引和公告已读唯一约束完整保留，原用户、角色、菜单及全部企业数据保持原内容。迁移只提供原组件使用的数据库结构；不导入示例用户、角色、公告或定时任务。SQL解析采用现有SQLParse/SQLGlot和Liquibase，不开发格式解析器。迁移前使用已有完整备份入口，原001—033保持原字节。

当前代码本地未提交，数据和制品身份由实际执行回执维护。实际Red包含十张表缺少的二十项物理断言失败，错误为零；原DDL及全部迁移十六项检查、数据模块clean verify一项、迁移执行及实际MySQL两项检查均通过。迁移前后179张既有基表内容校验和及全部226项原Liquibase身份/MD5一致；没有导入种子记录。原Controller、环境绑定、企业授权、同步审计、正式页面及全部客户业务仍须完成，数据库结构不能代表十八模块验收。文档CURRENT、last_verified=2026-10-06仅适用于已验证的结构与实际迁移。

| 文件与identifier | 当前职责及关联 |
| --- | --- |
| business/sql/ry_20260417.sql | 固定原生管理DDL；实际原组件字段依据 |
| medical-data/db/changelog/master.xml、migration/coverage.json | 唯一迁移入口和框架表登记 |
| tests/test_migration_bundle.py | 原DDL及迁移结构核查，真实成熟SQL组件 |
| 原system/quartz/generator Mapper | 框架组件直接读取，不增加通用CRUD实现 |

| 实际检查 | 结果与范围 | 原始回执及SHA-256 |
| --- | --- | --- |
| MANAGEMENT-MYSQL-RED-03 | 两项检查中二十个字段/索引子断言失败，错误为零；实际物理表缺少 | `.implementation-work/IC-03-RUOYI-MANAGEMENT-20261006-RUOYI-MANAGEMENT-MYSQL-RED-03/receipt.json`；abb405663665a70760908bba63315b572478fe3049325b7f935c000cdf7f5440 |
| MANAGEMENT-DATA-GREEN-05 | 原DDL、完整字段/默认值/索引及全部迁移十六项通过，12.535秒 | `.implementation-work/IC-03-MIGRATION-COVERAGE-20261006-RUOYI-MANAGEMENT-DATA-GREEN-05/receipt.json`；45042d0458c809453061eb2b87504f1a66596335808a300a350c26d67e7d24d4 |
| MANAGEMENT-DATA-FINAL-01 | 当前检查入口及完整原结构十六项通过，9.994秒；当前静态结果使用本集合 | `.implementation-work/IC-03-MIGRATION-COVERAGE-20261006-RUOYI-MANAGEMENT-DATA-FINAL-01/receipt.json`；f51f81e403767e2c7430523bd77f77d3cd7ac231237df020d15859f525091e7b |
| MANAGEMENT-DATA-BUILD-03 | 数据模块原clean verify通过，MigrationBundleTest一项、236项changeset；6.724秒 | `.implementation-work/IC-03-RUOYI-MANAGEMENT-20261006-RUOYI-MANAGEMENT-DATA-BUILD-03/receipt.json`；be66d1a2414803415e0f15064a558397bad6c3d051c7d68737d5543661761e73 |
| MANAGEMENT-DATA-MIGRATE-01 | 十项新增迁移通过，179张既有表及226项迁移保持一致，原应用身份不变 | `.implementation-work/IC-03-RUOYI-MANAGEMENT-20261006-RUOYI-MANAGEMENT-DATA-MIGRATE-01/receipt.json`；426b51e3d041371d689602d821ff4f5ee9e70c46a10bee7cdf8fb3ff499cac53 |
| MANAGEMENT-MYSQL-GREEN-01 | 实际字段顺序、类型、默认值、非空、auto increment、collation和完整索引两项通过，12.909秒 | `.implementation-work/IC-03-RUOYI-MANAGEMENT-20261006-RUOYI-MANAGEMENT-MYSQL-GREEN-01/receipt.json`；f230107786007a7024a7f7743a912b876db2a13c7ab24ffb290cfc6295c90b33 |

迁移备份为`.implementation-work/runtime/migration-backups/a8826def-4177-4212-b5e5-663e5c78221a.sql.gz`，摘要a2d70ca5da9132a6b0816533bb8baf8a2852db93c34b08502c81937b182dbd15，同名JSON绑定medical_local和server_uuid=5ecfb746-be18-11f1-95e4-d822203bc633。备份摘要已经重读验证；完整恢复仍须按原要求取得证据。原应用PID31485及部署339ea611-75dd-4718-a50d-b4a11e69a20c保持，新的业务源码尚未部署。

原Mapper检查MANAGEMENT-MAPPERS-GREEN-02使用Druid只读连接及实际MySQL，二十份原XML加载成功；原system/quartz/generator查询、生成器二十二个实际字段和原真实用户的公告未读数通过，一项2.141秒，Maven12.683秒。回执为`.implementation-work/IC-03-RUOYI-MANAGEMENT-20261006-RUOYI-MANAGEMENT-MAPPERS-GREEN-02/receipt.json`，摘要e57328cd88db81df23018b018b8bdee80ff5ecb9e998424796634f1e819f3c2b。后续Service注册和测试新增使该整体源码集合为STALE；原运行结果保持原执行范围，当前关联验证由[IMP-06](IMP-06.md)维护。

当前MANAGEMENT-SERVICES-FINAL-01的121项关联Java全部通过，705项输入及42项产物回执223270f9c5a1455b86f1b7b8e97c29b9c9f212382d6e3111844dfddcaf5ae587。其中原Mapper0.420秒、实际十三项原Service2.174秒，全部原查询/生成器字段及公告计数通过。原完整构建入口已经绑定原quartz/generator源码和POM，静态原GREEN-05整体输入因此为STALE；最终结构核验使用FINAL-01。数据构建、实际迁移及物理MySQL回执保持各自当前输入，完整业务与恢复未验收。

六份最终回执全部输入/产物按实际摘要重读一致，证明为`.implementation-work/RUOYI-MANAGEMENT-20261006-final-evidence.jsonl`，摘要2d729dc78adcd9ef3643baa833f676ddb4447b7569c7ea461294a9eb36f25b07；当前唯一开发记录检查同时通过。该证明适用原管理结构和当前关联工程范围，完整业务、恢复和部署继续按原要求验证。

验证必须绑定实际源码、原DDL、迁移制品、数据库身份、备份和原始结果。权限及非空客户写入使用获授权真实输入，不通过技术空集合登记业务通过。

## 原方法权限与各执行集合的验证范围

2026-10-06当前BACK-RUOYI-METHOD-SECURITY-01由唯一[IMP-06](IMP-06.md)维护，使用原Spring Method Security和PermissionService；共享Catalogue、十一份Schema、278项定义及289项固定引用保持原字节。当前42项共享及全部119项关联Java通过，实际跨语言278项定义一致；SHARED-05的50项输入及一项产物再次核验一致。原完整Java构建NATIVE-01受S3容量保护未通过，完整原始JUnit归档入口已有实现，完整成功证据及新部署尚未产生。226项已应用迁移没有修改，没有客户记录或新增迁移。

2026-10-06BACK-RUOYI-NAVIGATION-01的当前原菜单Schema、接口与Vue消费由唯一[IMP-06](IMP-06.md)维护；独立导航响应使用原RouterVo/MetaVo，共享Catalogue保持十一份Schema、278项定义和289项固定引用。当前42项共享及关联全部118项Java、原Vue开发/生产DOM和当前跨语言核查通过；没有新增数据库迁移或客户数据，新源码尚未部署。较早整体输入证明只保持原始范围；完整业务和全部管理模块继续验收。

2026-10-06BACK-TEMPLATE-APPLICABILITY-01发布rule-expressions及template-applicability，Catalogue为十一份Schema、278项定义及289项固定引用，摘要e4c32464276006a7e64c854c4292afe11391374c4c198b6b1a23cdaf3e76878e。原模板、任务、资料集及全部既有定义保持原字节；两份身份manifest绑定当前Catalogue。当前114项受影响Java及实际HTTP检查通过，Python十二项、TypeScript严格类型和三十项通过；主记录为[IMP-11](IMP-11.md)。没有新增迁移或客户内容，原MySQL226项及旧运行制品保持原范围，较早整体输入证据为STALE。

## 原资料集发布集合的执行范围

2026-10-06当前源码Catalogue发布九份Schema、270项定义及279个固定引用，新增十三项资料集专业定义；collections摘要c36a4d9478afdc94e4862e8bad63c4d5685ece9cb0a2bcc7eaea3e08790a975f，Catalogue摘要322654a8fda66a17c2daffef4ec6c0c97eb822ff6117b8a25c34fc6b96b1da12。共享语言检查当前48项输入一致，Python十二项及TypeScript三十项通过；COLLECTION-CROSS-02当前跨语言表示通过。完整资料集与原范围308项构建由唯一[IMP-10](IMP-10.md)维护，账户分页与当前35项身份由[IMP-04](IMP-04.md)维护；COLLECTION-NATIVE-05完整构建因存储容量保护未通过，新代码尚未部署。MySQL保持226项迁移，无新增结构或客户内容；原运行制品仍采用其原Schema。完整业务及恢复尚未验收。

## 原部署与共享表示的原始范围

2026-10-06当前来源选择、固定资料集成员及制品证据读取唯一[IMP-12](IMP-12.md)。SCOPE-SELECTION-NATIVE-01完整300项通过，635项输入及8项产物、实际Jar及独立迁移Library/CLI字节一致。共享八份Schema、257项定义及265个固定引用保持原发布，MySQL保持226项迁移与152张企业表，没有新增迁移或内容填充。原整体集合对当前Java为STALE，其他集合保持各自原输入范围。客户快照/任务保存、完整成功及恢复尚未验收。

## 已有实现与各执行集合的原始范围

本节中的执行身份、制品、数量和验证结果分别对应其注明的集合；当前任务组件和部署证据使用IMP-12。

2026-10-06BACK-RUOYI-DATA-SCOPE-01、原DataScopeAspect和当前制品由唯一[IMP-04](IMP-04.md)维护。RUOYI-DATA-SCOPE-NATIVE-02完整275项、FINAL-01完整30组以及31份当前源码、产物与双服务身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261006-RUOYI-DATA-SCOPE-FINAL-01/verified-evidence.json`。当前JavaPID10793、0400制品ed114748e3771ad7e4434d0e64debde70a588b7b017d7894c1bddc6d3bfc03c5，222项迁移及公开Schema保持；当前范围组件直接使用原框架，完整客户业务与管理消费者继续验收。以下集合只保留各自原始范围。

2026-10-06FRONT-ENTERPRISE-REVIEW-01由唯一[IMP-06](IMP-06.md)维护。正式Vue通过原Ajv使用已有审查及Permission Condition Schema；原后端Schema、275项Java源码/产物和222项迁移保持原字节。ENTERPRISE-REVIEW-FINAL-01完整30组及31份当前输入、产物和运行身份证明通过，路径为`.implementation-work/FULL-REGRESSION-20261006-ENTERPRISE-REVIEW-FINAL-01/verified-evidence.json`。合法客户申请、完整业务和全部迁移业务消费者继续按原要求验收；较早集合按原始范围读取。

2026-10-06原框架身份接入由唯一[IMP-04](IMP-04.md)维护：当前275项Java、30组回归及31份当前源码/产物/运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261006-RUOYI-NATIVE-IDENTITY-FINAL-01/verified-evidence.json`。公开接口和Schema、数据库结构保持完整，本次没有迁移；原业务UUID、框架编号和当前会话关联已核验。完整客户业务、十八管理模块及批准部署继续按原计划验收，较早集合保持各自原始范围。

2026-10-06正式企业登记客户端直接注册原enterprise-registration.schema.json及原LocalAccountPage/Item，局部journal封闭为key/digest，复用原Ajv和已有完整JSON转换；原七份发布Schema和222项迁移保持原版本。当前30组ENTERPRISE-REGISTRATION-FINAL-01及273项Native合计31份当前字节和运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261005-ENTERPRISE-REGISTRATION-FINAL-01/verified-evidence.json`。唯一正式页面记录读取[IMP-06](IMP-06.md)，身份和框架服务读取[IMP-04](IMP-04.md)，较早集合按各自原始范围读取；客户业务及全部后续消费继续验收。

完整模板定义及发布Catalogue由[IMP-11](IMP-11.md)维护。共享发布集合包含七份Schema、253项定义及260个固定引用，模板采用原$id URN和文件摘要，两个模板消费者直接使用共享SchemaCatalogue。Java/Python/TypeScript继续采用原networknt、jsonschema/referencing和Ajv；原五份文档身份保持完整，全部新定义进入发布、Classpath资源及原件核查。独立Python11项、TypeScript类型检查和29项检查通过，完整当前运行结果由IMP-11维护。

028/029建立原框架账户表和原UUID关联，030追加原sys_menu/sys_role_menu，031追加完整人工模板决定表；真实MySQL保持222项迁移、150张企业表、17张平台基表、10张框架表及一项兼容View。TEMPLATE-APPROVAL-NATIVE-02通过273项Java检查，无失败/错误/跳过，617项输入和8项产物字节符合当前文件，回执SHA-256为bda0372628676c67270d243bf6b8ef3d8cdd91d19041776a2d6367b6de8c4fef。九项公开模板检查及五项实际结构检查通过；人工核准Schema直接使用原networknt和Jackson，创建和预览正文使用原完整模板Content。完整30组本地回归TEMPLATE-CREATE-UI-FINAL-01及Native合计31份回执通过当前字节和运行身份核验，证明保存于`.implementation-work/FULL-REGRESSION-20261005-TEMPLATE-CREATE-UI-FINAL-01/verified-evidence.json`。创建及审查浏览器局部身份Schema复用原Ajv，正文和理由不保存到浏览器；原七份共享Schema发布目录保持完整，没有数据库或Java修改。当前正式页面和运行证据由[IMP-11](IMP-11.md)维护，框架复用读取[IMP-04](IMP-04.md)。已应用迁移保持原字节，客户业务和完整后续消费继续未验收。

2026-10-05 Servlet接收数据关联：captureRequest继续采用原固定分片范围、完整SHA及原Lease，Content-Length未知或与原大小不同拒绝；接收缓冲显式有界。十二项真实HTTP检查通过，没有新增公开Schema字段或数据库迁移；唯一[IMP-05](IMP-05.md)维护当前制品及运行证据，198项原迁移和全部业务要求保持完整。

2026-10-05对象截止数据关联：TimedAdmission.remainingTime来自原租期，Lease.guard保留动态计时接口；对象请求阶段继续使用原完整上传身份和两份permit_id。67项上传工程通过，没有新增公开Schema字段或数据库迁移；唯一[IMP-05](IMP-05.md)维护当前完整制品和运行证据，原198项迁移及全部业务要求保持完整。

2026-10-05完成传输并发数据关联：recoverPart/complete/recoverCompletion使用原固定上传身份、原WRITING/COMPLETING版本与同一Lease；complete取得原名额以后才更新完成状态。63项上传工程通过，没有增加MySQL迁移或公开Schema字段；完整制品及运行证据由唯一[IMP-05](IMP-05.md)维护，198项迁移及全部业务要求保持完整。

2026-10-05传输并发数据关联：实际Redis使用原企业/用户稳定容量键，原配置版本/容量/时限及permit_id固定，配置变化不创建第二组名额；公开Schema和MySQL迁移保持原定义。59项上传工程通过，完整源码/制品/运行范围由唯一[IMP-05](IMP-05.md)维护，198项迁移及全部业务要求保持完整。

2026-10-05上传输入数据关联：IncomingUploadPart及receiveStream使用原固定UploadTransferProtocol.PART_BYTES和SessionRow全部固定身份，Content-Length等于原分片范围，SHA-256和当前上下文逐阶段核查；没有新增迁移或公开Schema字段。52项实际上传工程检查通过，完整制品与运行证据由唯一[IMP-05](IMP-05.md)维护；198项原迁移及全部业务要求保持完整。

2026-10-05当前许可事务数据关联：UploadOperationRow完整读取operation_record二十列，UploadIssuanceMapper保存十九非企业字段，原拦截器提供当前企业；许可/会话/绑定、原操作、审计和Outbox共享实际企业事务并重读。原生命周期期限及批准版本共同为空，MySQL JSON采用RFC8785比较；公开Schema和八字段请求保持原定义，没有新增迁移。44项实际上传工程检查通过，当前完整制品和运行范围读取唯一[IMP-05](IMP-05.md)，198项原迁移保持完整。

2026-10-05当前上传依据数据关联：五类Basis查询逐项读取实际任务/计划/快照/成员的全部物理字段，保持当前任务指针与完整企业/项目关联；没有新增迁移。内部upload-authorization.schema.json包含十七字段及封闭DocumentBasis/BasisSettings，DOCUMENT_BODY固定实际完整依据摘要，AGENT_ARTIFACT该字段为空；当前内部Schema摘要随实际文件更新，公开Schema保持原字段。35项实际工程检查及后续完整制品/运行证据读取唯一[IMP-05](IMP-05.md)，原198项迁移和全部业务范围保持完整。

2026-10-05上传传输数据关联：partIfPresent使用原Mapper完整字段与当前主体核查，内部文稿完成及恢复通过同一完整发布Catalogue校验DocumentBody；没有新增迁移或更改公开Schema。UPLOAD-IO-NATIVE-01当前191项原生及实际制品范围读取唯一[IMP-05](IMP-05.md)，原198项迁移保持完整，合法客户业务仍未验收。

2026-10-05当前共享Schema已进入UPLOAD-AUTHORIZATION-NATIVE-03制品和RUNTIME-02实际运行：全部184项原生通过，完整发布八项2.269秒；当前JavaPID21090、制品b073b82928e8fe6a8d50aa1d6faf9d0e4b00f77c4a2840bc1279ea9077f51a37，启动12.835秒、登录HTTP200。31项实际身份、六项原操作和两组DOM通过；全部当前输入/产物SHA已核查，唯一证据及完整范围读取[IMP-05](IMP-05.md)。同一不可变制品共享完整221项发布引用及Validators，fromProject仍核查实际项目文件，公开Schema与原迁移保持完整。IC-03及全部客户业务验收仍按原实施计划执行，以下集合保留原始执行范围。

CLASSPATH-SCHEMA-GREEN-01八项检查通过，4.843秒、failures/errors/skipped均为零、inputs_unchanged=true；[完整发布身份、项目读取及并发实际原件回执](../../.implementation-work/IC-05-PUBLISHED-SCHEMA-20261005-CLASSPATH-SCHEMA-GREEN-01/receipt.json)摘要64c04abecd7fc08b68b1c7796aee76b75db51bb86758a031439b30b3f868cf6d。全部221项引用/摘要、真实完整资料与原定位、未知引用/版本冲突、封闭字段、重复/多根JSON、UTF8、精确类型以及四线程八次独立校验均核查；线程数和次数仅为工程行为输入，不是批准性能阈值。当前完整制品和实际启动继续核查，全部客户业务保持原验收状态。

CLASSPATH-SCHEMA-RED-01实际八项检查中共享身份断言失败，errors/skipped为零、4.275秒，[原始执行回执](../../.implementation-work/IC-05-PUBLISHED-SCHEMA-20261005-CLASSPATH-SCHEMA-RED-01/receipt.json)摘要9bae3d03458823f28c7cef7f1c85f495b7ae706034d873e0308e164e1752cf57。当前fromClasspath通过同步初始化共享同一不可变制品的完整Catalogue，成功初始化以后发布；fromProject继续读取并核查当前项目文件。全部221项引用和原Schema/摘要、UTF8/JSON/精确数值规则保持完整；实际公开资料并发检查及新完整制品/启动继续验证。依据[NetworkNT1.5.9完整说明](https://raw.githubusercontent.com/networknt/json-schema-validator/1.5.9/README.md)的Performance Considerations，固定配置的JsonSchemaFactory/JsonSchema可共享，配置在发布后保持不变。

2026-10-05 BACK-CLASSPATH-SCHEMA-01继续当前制品的完整发布Schema共享。检索SchemaCatalogue及全部fromClasspath消费者、221项固定引用、实际D0原件和NetworkNT1.5.9完整README的Performance Considerations。Given同一不可变应用制品及固定发布清单；When多个组件加载、并发校验实际公开原件或从项目读取Schema；Then同一制品共享完整已核查Catalogue，项目读取保持独立版本核查，所有引用/摘要、严格JSON及并发结果保持原行为。唯一主记录为IMP-03，关联IMP-01/04/05/07/37；IC-03和全部业务状态仍按原任务计划核查。

2026-10-05上传授权Schema关联：内部清单封闭保存十六项原身份/水位/Schema/依赖与全部来源、领域、上游要求，使用NetworkNT、Jackson及RFC8785。原绑定保存和读取核对当前内部Schema摘要及原许可全部标量；DocumentRow读取原document_head十一字段和当前项目状态，没有新增迁移或修改公开Schema。当前检查与完整制品范围读取唯一[IMP-05](IMP-05.md)，客户写入和全部业务仍未验收。

2026-10-05上传保存关联：UploadTransferProtocol与UploadTransferMapper读取全部非生成字段，U64为BigInteger、part_no为Long、时间为UTC微秒；四类保存语句保留全部物理字段，企业字段由原受保护入口加入。UploadTransferObjects按026原key/version_id五字段转换精确JSON，其他原物理定义与公开Schema保持完整。九项实际MySQL、原件及准入检查通过，完整制品和当前运行读取唯一[IMP-05](IMP-05.md)。封闭授权清单、实际业务保存及客户验收继续实施。

2026-10-05上传结构关联：026已执行193—198六项追加迁移，保存object_upload_session、object_upload_part和artifact_upload_binding；累计198项、146企业业务表和17平台表。原001—025及core.sql保持完整，十五项迁移覆盖和十项实际MySQL字段/外键/约束/原件元数据检查通过；完整字段传播依据backend-design/接口补充设计.md §2.1，唯一主记录为[IMP-05](IMP-05.md)。公开Schema保持原字节，当前应用制品168项原生及适用运行检查通过，完整业务服务和客户成功仍未验收。

2026-10-05对象写入数据关联：S3ObjectWriter在实际固定版本和完整SHA核查后返回原ObjectRef五字段；metadata只描述实际写入身份、摘要和大小。原DDL、001—025和公开Schema保持原字节，本次没有迁移或客户记录写入。受限许可metadata、完整复合关联及业务Artifact登记继续原要求，当前证据读取[IMP-05](IMP-05.md)，以下集合保留原始范围。

2026-10-05独立依赖结构关联：DependencyAccessMapper读取既有dependency_access_set的八项非生成主体字段，构造映射通过真实只读MySQL核查。原core、001—025和公开Schema保持原字节，本次没有迁移。完整依赖核验及当前证据读取[IMP-05](IMP-05.md)，以下集合保留各自原始输入范围。

2026-10-05内容回执关联：IMP-05使用原009/025完整结构保存header、range及审计，ContentReadMapper的三个查询及全部record字段由真实只读MySQL核查，保存SQL与全部实际非生成列一致。原core、001—025、公开五份Schema及catalogue保持原字节，本次没有迁移。CONTENT-READ-BUILD-01完整155项原生及当前JavaPID3730的适用运行检查通过，证据读取[IMP-05](IMP-05.md)。完整客户成功保存/重读与业务领域写入继续验收，下方集合保持原始范围。

2026-10-05共享领域关联核查：IMP-05新增VersionResourceMapper的36类实际实体查询，字段和Java构造类型/顺序在当前只读MySQL核查通过。原core及001—025、公开Schema和catalogue本次保持原字节。VERSION-AUTHORITY-BUILD-01完整150项原生通过，当前JavaPID341；六项原操作及两组登录DOM检查通过。当前证据读取[IMP-05](IMP-05.md)，完整业务写入和客户成功仍未验收，下方较早集合保持原始输入范围。

2026-10-05 BACK-SOURCE-READ-AUTHORITY-01当前关联：内部source-rights.schema.json与SourceRightsSchema限定完整不可变权利载荷、原附件引用及RFC8785摘要；公开五份Schema、catalogue、core及001—025保持原字节。SourceReadMapper六类实际查询核对全部record字段及原生列顺序；SOURCE-AUTHORITY-BUILD-02完整145项通过，包含七项来源检查。当前证据由IMP-05维护，合法权利、来源业务、领域版本映射与完整读取回执继续未验收；较早集合保留原始输入范围。

2026-10-05 BACK-RESOURCE-READ-AUTHORITY-01当前关联：新增内部dependency-access-manifest.schema.json固定025完整清单，公开catalogue、共享五份Schema、core及001—025保持原字节。ArtifactRegistryMapper的五类当前查询由真实MySQL执行并核对record构造字段/类型/顺序；成熟Schema、严格JSON及完整成员摘要由DependencyManifestSchema实施。RESOURCE-REGISTRY-BUILD-01完整138项原生通过；当前25组及字节核查由IMP-05维护，完整传递来源、权利和回执继续实施。下方集合分别保留原始范围。

2026-10-05 BACK-ARTIFACT-SCHEMA-01当前关联：SchemaCatalogue维护当前221个固定发布引用及原文件SHA，requirePublished/bindPublished拒绝未知引用、别名和摘要冲突，完整UTF8 JSON采用原Schema及RFC8785规则。PUBLISHED-SCHEMA-BUILD-01完整131项通过，新增六项直接调用检查1.199秒；共享Schema、core.sql及001—025迁移保持原字节。当前25组工程回归与字节核查由唯一IMP-05维护；完整资源类型、当前企业授权、来源关系及实际业务回执继续实施，其他集合保留原始范围。

2026-10-05 BACK-ARTIFACT-REGISTRY-01当前关联：追加025保存主资源与发布Schema、完整依赖原清单及实际读取范围，001—024及core.sql保持原字节。MySQL实际192项迁移、143企业表及17平台表；15项迁移覆盖、五项真实登记结构、125项原生、25组回归和30份当前字节核查通过。五组完整外键含26个关联字段，原公开Schema保持完整。完整业务登记、来源核验、读取授权及回执仍由IMP-05实施；下方集合保持各自原始范围。

2026-10-05 BACK-OBJECT-PERSISTENCE-01当前关联：StoredObjectReferences按原005定义保存精确数值型JSON size_bytes，并在读取时与全部五项标量一致核查；接口ObjectRef继续使用原U64字符串。公开Schema、core.sql及001—024保持原字节，未创建客户制品。PERSISTENCE-BUILD-01完整125项、PERSISTENCE-FINAL-01最终七项、完整24组及30份当前核查通过，跨语言、迁移CLI及15项迁移覆盖重新通过。当前JavaPID81465；Artifact主资源、Schema、完整依赖与业务读取范围继续由IMP-05实施。下方集合保持原始范围。

2026-10-05 BACK-OBJECT-ASSEMBLY-01当前关联：内部完整内容消费沿用原ObjectRef、Encoding及Coverage，公开Schema与001—024迁移保持原字节；接口补充设计§2.4维护完整字节消费规则。ASSEMBLY-BUILD-01完整118项、实际14项及ASSEMBLY-FINAL-01全部24组通过，跨语言、迁移CLI和15项迁移覆盖重新通过。当前JavaPID77952；完整Artifact资源、Schema、依赖及业务读取范围仍由IMP-05实施。当前回执字节核查读取IMP-05，下方集合保持原始范围。

2026-10-05 BACK-OBJECT-PAGING-01当前关联：ObjectContentPaging复用原ObjectRef、Encoding及Coverage，公开ContentChunk字段与001—024迁移保持原字节；RFC8785字节范围及JWE绑定要求由接口补充设计§2.4维护。PAGING-BUILD-01完整110项原生检查及PAGING-FINAL-02的24组/30份当前核查通过，完整跨语言表示、迁移CLI及15项迁移覆盖重新通过。当前JavaPID72778，唯一对象记录为IMP-05，企业Artifact资源/Schema及真实读取回执继续实施。

2026-10-05 BACK-OBJECT-READ-01关联：WireValues.hash/boundedString和ObjectRef直接验证原五字段Schema，真实原件的原ObjectRef往返、缺字段/长度/摘要/U64拒绝四项通过。共享Schema及001—024迁移字节保持原值；OBJECT-READ-GREEN-02原生104项、OBJECT-READ-FINAL-01的24组/29份当前核查通过，迁移15项和跨语言完整映射重新通过。当前Java为PID68313及21c6650a14db4c85689d443af50fd1a5cdc45a98645ceec0220fc4bdd2618774制品；唯一对象主记录为IMP-05，完整业务消费和验收继续实施。

2026-10-05 BACK-OBJECT-STORAGE-01关联：ObjectRef的版本、SHA、size_bytes字符串和media_type由实际公开原件的S3固定版本重读验证，Artifact登记、coverage及企业/项目依赖授权仍需完整实施。001—024迁移与共享Schema本轮保持原字节；OBJECT-STORAGE-FINAL-01的24组及29份当前证据核查通过，Java96项与迁移覆盖15项对应当前输入。唯一对象主记录为IMP-05，真实数据业务及完整恢复仍未验收。

2026-10-04 BACK-BUDGET-HISTORY-01当前关联：独立budget-history.schema.json由BudgetHistorySchema和正式前端protocol共同引用原budgets.schema.json，原批准manifest与001—024迁移字节保持原值。BUDGET-HISTORY-02原生96项及预算18条SQL字段核查通过，最终BUDGET-HISTORY-FINAL-01的23组/26份当前字节核查通过；迁移覆盖15项、186项真实迁移和140企业/17平台表保持完整。主记录为IMP-07；下方集合保持原始输入范围，客户历史与全部数据业务仍未验收。

2026-10-04 FRONT-BUDGET-MANAGEMENT-01关联：预算前端注册现有budgets.schema.json及独立原请求技术元数据Schema，当前封闭字段与字符串精度由IMP-07维护。Java及001—024迁移本轮没有修改，BUDGET-FRONT-FINAL-01的23组与26份当前字节核查通过，Java95项和迁移覆盖15项对应当前输入。其他集合保持原始范围；历史批准、预算准入及领域消费者继续实施。

2026-10-04 BACK-BUDGET-MANAGEMENT-01关联：预算采用既有budget_account/budget_revision/version_registry，原001—024迁移字节未修改，MySQL仍为186项changeset、140企业表及17平台表。BudgetSchema定义完整批准、限额和账户输出，16条预算MyBatis语句经原生JSQLParser及SQLGlot核查实际DDL字段；IC-03-JAVA-20261004-BUDGET-02实际95项通过。新增接口与同事务读取由IMP-07维护，当前共享类型和迁移复验使用BUDGET-FINAL-01；其他回执保持各自原始输入范围。领域不可变保护、实际累计预算、历史迁移和恢复保持原验收条件。

2026-10-04当前复验：024迁移保存原操作期限与批准版本，完整企业外键及成对约束已在MySQL核查；原001—024字节保持完整。真实MySQL已执行186项changeset，140企业表及17平台表。IC-03-JAVA-20261004-OPERATIONS-04的90项原生检查通过，新增三条只读查询及MyBatis构造映射原生核查。IC-03-MIGRATION-COVERAGE-20261004-OPERATIONS-COVERAGE-03的15项检查通过：原始ALTER由JSQLParser类型节点交给SQLGlot，字段通过CREATE表示、约束通过独立ALTER表示，完整核查成员认证八项变更、原操作四项变更、外键、唯一约束及CHECK表达式。当前全部受影响回归使用FULL-REGRESSION-20261004-OPERATIONS-FINAL-03，结果读取实际回执；三种语言其他检查、领域业务、历史迁移与恢复继续保持全部原验收要求。

## 任务身份

| 字段 | 当前内容 |
| --- | --- |
| task_id | IMP-03 |
| 需求与验收 | FR-01—09；NFR-07；IC-03；实施任务计划 §5 IMP-03 |
| task_source | [实施任务计划](../../实施任务计划.md) §4.2 |
| 状态快照 | IN_PROGRESS；2026-10-04；140企业表和17平台表，186项changeset及95项原生检查通过；领域集成、历史迁移和恢复尚未验收 |
| 代码与环境 | 根仓库 main，尚无提交；源码与实际输入由 SHA-256 标识；Java 17、Node 24、Python 3.12 |
| 文档有效性 | CURRENT；last_verified=2026-10-04；本地唯一Schema、当前迁移、原生制品及完整未验收范围 |
| 检索字段 | SchemaCatalogue、Pydantic、DatasetBindings、ModelStreamBindings、ResourceBindings、ArtifactBindings、InterfaceBindings、OpenDomainModel、TypedPayload、DomainModel、ErrorCode、SchemaRejection、SchemaValidationError、EvidenceRef、Decimal、UTC、UUID、code point、复合外键、run_migration、IC-03 |

## 检索与输入依据

读取 IMP-01/02、开发索引、实施工单、LLD-数据 §4/6、LLD-后端 §3、接口补充设计 §4及五份共享 Schema。既有工程没有专业 Schema 绑定。数据、后端及 Agent 的原始 Schema 保持唯一来源，类型绑定不得复制字段定义作为新的依据。

Python 类型继续依据 LLD-Agent §2.2 和 LLD-后端 §3.1 的 Pydantic 要求实施；实际核查 pyproject.toml、requirements.lock、catalogue.py、wire.py 和原始登记、模型、资源、公共及专业产物定义。Pydantic 2.13.5 的实际依赖和 API 已核对版本源码、官方严格模式、TypeAdapter 及联合类型说明。

错误枚举依据 LLD-后端 §4.10 的25行错误表，共68个唯一代码。tests/error_design.py 使用 markdown-it-py 4.0.0 解析实际文档表格，Python 检查及跨语言执行逐项比较发布集合；mdurl 0.1.2 与测试依赖均已固定。HTTP 状态、公开错误体、重试准入及领域事务由后续服务消费验证。

## 当前功能与实现

本项提供五份 Schema 的受控读取、摘要检查、离线引用解析与载荷校验，并实施规定的跨语言表示。共享字段拒绝检查使用现有真实登记包的字段变更；迁移入口拒绝检查使用缺少的配置项及另一真实制品的摘要。检查不创建客户业务资料或服务回执。

| 文件路径 | identifier | 当前职责 | 使用方 |
| --- | --- | --- | --- |
| contracts/catalogue.json | schema_version=1.0.0 | 固定唯一 Schema 路径、身份与摘要 | Java/Python/TypeScript 绑定 |
| contracts/python/ | medical_contracts | Python 共享校验与表示、216项 Pydantic 类型绑定和公共17项声明 | Activity、文档处理与导入；正式消费待实施 |
| contracts/python/src/medical_contracts/domain.py、typed.py | DomainModel、TypedSchemaBindings、TypedPayload | 严格类型、字段拒绝、表示检查、唯一 Schema 及规范内容往返；每次读取创建独立对象 | Python 类型绑定 |
| contracts/python/src/medical_contracts/dataset.py | SourceDataset、Locator、EvidenceRef、DatasetBindings | 登记13项及完整外层、七类定位与内联成员；来源和证据引用使用原始文本身份 | Python 接口及专业产物类型复用 |
| contracts/python/src/medical_contracts/model_stream.py | ModelStreamBindings、EnterpriseModelProtocol、StreamPayload | 模型25项定义、八种事件与两种内容块；完成原因及执行状态条件 | C07 / Worker 待接入 |
| contracts/python/src/medical_contracts/common.py、resource_details.py | COMMON_DEFINITIONS、ResourceBindings、SourceScope | 公共17项声明、资源详情27项绑定；固定版本、选区、覆盖状态及来源条件 | Python 完整接口类型复用 |
| contracts/python/src/medical_contracts/artifacts.py | ArtifactBindings、ProfessionalArtifact、ArtifactOutput、Anchor | 专业产物49项及完整外层，八类输出与两类锚点；原始证据引用、测量精度、预算金额及条件约束 | Python 专业产物服务待接入 |
| contracts/python/src/medical_contracts/interfaces.py | InterfaceBindings、Envelope、OpenDomainModel、PublicEvent、ManagementModule | 完整102项接口类型，复用公共17项；继承字段、自由 JSON、原始证据引用、事件及管理条件 | Python 服务及 Worker 待接入 |
| contracts/python/src/medical_contracts/errors.py | ErrorCode、parse_error_code、SchemaValidationError、UnsupportedSchemaError | 完整68项枚举、严格读取及 Schema 拒绝代码；兼容原校验异常，代码只读 | Python SchemaCatalogue；业务服务待接入 |
| tests/error_design.py、tests/test_error_codes.py | published_error_codes、ErrorCodeChecks | 从实际后端设计读取完整代码；核查未知值、类型、实际资料损坏及安全诊断 | IC-03-PYTHON / IC-03-CROSS |
| tests/test_python_domain.py、tests/check_python_contracts.py | PythonDomainChecks | 原始字段、必填属性、枚举、引用和联合分支；真实资料及成员往返、许可原值、独立读取及拒绝检查 | IC-03-PYTHON |
| scripts/check_python.py、scripts/build_python.py、tests/inspect_python_package.py | main、LOCAL_PACKAGE_CHECK | 依赖及来源摘要、实际 wheel 构建、安装源码一致性及隔离执行 | IMP-01 共享制品与 IMP-03 类型验证 |
| business/medical-contracts/ | SchemaCatalogue、WireValues | Java 离线校验、规范编码、不可变载荷及表示 | ruoyi-admin 已包含模块；领域服务待接入 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/ModelProtocol.java、ModelStreamBindings.java | ModelProtocol、Definition、Binding | 模型协议25项定义的 Java 类型、sealed 内容块及流载荷、Schema 与类型往返校验 | Java 模型适配与 Worker 待接入 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/CommonProtocol.java、CommonBindings.java | CommonProtocol、CommonBindings | 公共17项定义、三种 sealed 选区、完整覆盖与来源范围条件；资源详情引用其中16项 | 资源与完整接口类型复用 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/ResourceProtocol.java、ResourceBindings.java、TypedSchemaBindings.java | ResourceProtocol、ResourceBindings、TypedSchemaBindings | 资源详情27项定义、保留泛型的 Jackson 类型、唯一 Schema 校验及规范内容往返 | Java 资源服务待接入 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/DatasetProtocol.java、DatasetBindings.java | SourceDataset、Locator、EvidenceRef、DatasetBindings | 完整登记包外层和13项定义，七类定位、PDF 区域及 DOCX 地址联合类型；文本来源身份保持原值 | Java 接口与专业产物的原始证据引用 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/InterfaceProtocol.java、InterfaceBindings.java | InterfaceProtocol、InterfaceBindings、OpenObject、eventMapping | 全部102项接口定义及绑定，复用公共17项；完整正文、管理、审查、公开事件及内部动作读取类型 | Java 服务及 Worker 待接入 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/ArtifactProtocol.java、ArtifactBindings.java | ArtifactProtocol、ArtifactBindings、ProfessionalArtifact、ArtifactOutput、Anchor | 专业产物49项定义及完整外层；八类输出、两类锚点、原始证据引用与条件约束 | Java 产物接收服务待接入 |
| business/medical-contracts/src/main/java/com/ruoyi/medical/contracts/ErrorCode.java、SchemaRejection.java | ErrorCode、parse、SchemaRejection、code | 完整68项枚举、严格读取及 final 错误代码；固定诊断信息 | Java SchemaCatalogue；领域服务待接入 |
| contracts/typescript/ | SchemaCatalogue、decodeDataset | TypeScript 共享校验、全部登记数据类型及表示 | Worker 与正式前端待接入 |
| contracts/typescript/src/model-stream.ts | ModelStreamDefinitions、bindModelStream、decodeModelStream | 全部25项模型协议定义、流事件与终态联合类型；读取逐次校验且返回独立内容 | 模型适配与 Worker 待接入 |
| contracts/typescript/src/interfaces-common.ts、resource-details.ts | CommonDefinitions、ResourceDetailsDefinitions、bindResourceDetails | 公共17项类型与资源详情27项定义；固定版本、空值、选区、范围和完整覆盖状态 | 前端与内部资源读取待接入 |
| contracts/typescript/src/interfaces.ts | PublicEvent、ManagementModule、DocumentBody 等102项定义 | 完整页面与内部接口静态类型；复用公共定义及原始 EvidenceRef；保留事件载荷和管理启用条件 | interface-bindings.ts；服务调用方待接入 |
| contracts/typescript/src/interface-bindings.ts | InterfaceDefinitions、INTERFACE_DEFINITIONS、bindInterface | 完整102项类型映射、原始 Schema 引用及有效分支的表示校验、独立规范内容读取 | 前端与内部接口消费待接入 |
| contracts/typescript/src/catalogue.ts | RepresentationVisitor、reference、matches、visit | 由 Ajv 解析已登记原始引用与有效分支，执行定义身份对应的表示规则 | bindInterface；原有不带 visitor 的绑定保持相同结构检查 |
| contracts/typescript/src/artifacts.ts | ArtifactDefinitions、ProfessionalArtifact、bindArtifact | 专业产物49项定义及外层条件类型；Schema、UUID、Decimal、UTC 和规范 JSON 绑定 | C12 产物与内部登记服务待接入 |
| contracts/typescript/src/errors.ts | ERROR_CODES、ErrorCode、parseErrorCode、SchemaRejection | 完整68项只读枚举及联合类型、严格读取；Schema 异常保存固定代码且 frozen | TypeScript SchemaCatalogue；前端和 Worker 待接入 |
| tests/test_shared_contracts.py | SharedContractChecks | 真实登记包、来源定位及拒绝验证 | IC-03 本地检查 |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/SharedContractTest.java | SharedContractTest | 真实来源、范围及 Java 制品校验；保存实际执行报告 | IC-03-JAVA |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/ModelStreamBindingsTest.java | ModelStreamBindingsTest | 全部模型定义与对象字段映射、实际登记标量读取、错误领域及无效 Unicode 拒绝 | IC-03-JAVA |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/ResourceBindingsTest.java | ResourceBindingsTest | 完整定义、原始引用范围、27个公共与资源对象的字段和直接枚举、真实标量与原件读取、独立内容及类型范围 | IC-03-JAVA |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/DatasetBindingsTest.java | DatasetBindingsTest | 原始13项定义、17个对象与内联成员、七种联合类型、完整真实登记及成员往返、许可状态和错误拒绝 | IC-03-JAVA |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/InterfaceBindingsTest.java、SchemaTypeAssertions.java | InterfaceBindingsTest、SchemaTypeAssertions | 原始102项定义、93个对象及继承字段、全部字段引用与泛型、九种事件条件、文本身份和技术 Envelope 独立读取 | IC-03-JAVA |
| business/medical-contracts/src/test/java/com/ruoyi/medical/contracts/ArtifactBindingsTest.java | ArtifactBindingsTest | 49项定义、37个对象的字段与枚举、完整类型引用与泛型、输出及锚点分支、实际标量和216项 Java 映射全集 | IC-03-JAVA |
| contracts/typescript/tests/contracts.test.ts | node:test | 真实登记包、原文范围及错误表示拒绝 | IC-03-TS |
| contracts/typescript/tests/resource-details.test.ts | TypeScript TypeChecker、node:test | 唯一资源定义与实际编译器字段映射、表示字段引用、实际登记标量及独立读取检查 | IC-03-TS |
| contracts/typescript/tests/interfaces.test.ts | TypeScript TypeChecker、node:test | 原始102项定义、93个对象与继承字段、九种事件分支及管理条件检查 | IC-03-TS |
| contracts/typescript/tests/artifacts.test.ts | TypeScript TypeChecker、node:test | 全部49项产物及37个对象、八类输出、条件类型、完整表示引用及真实标量绑定检查 | IC-03-TS |
| contracts/typescript/tests/interface-bindings.test.ts | node:test | 完整类型绑定集合、实际登记标量、原始引用及联合分支、技术 Envelope 与嵌套 revision 检查 | IC-03-TS |
| tests/check_cross_language.py | main | 核查 Java 报告输入，比较三种语言的实际结果 | IC-03-CROSS |
| business/medical-data/ | medical-data:3.9.2 | 独立 Liquibase 迁移制品，已加入聚合构建 | 迁移岗位；业务运行账号不执行迁移 |
| business/medical-data/pom.xml | migration-cli、LiquibaseCommandLine | 保留普通资源 jar，并构建包含原生 CLI、picocli 和 JDBC 的独立 cli jar | 数据库迁移岗位 |
| business/medical-data/src/main/resources/db/changelog/master.xml | 1.0.0-001—178 | 原始核心DDL及逐项补充changeset；错误立即终止 | Liquibase 4.31.1 |
| business/medical-data/src/main/resources/db/migration/017-member-identity-mode.sql；tests/InspectAlterMigrations.java | mode、local_user_id、BACK-MEMBER-IDENTITY-01 | 八项正式DDL维护明确认证分支、本地账户外键和企业内唯一约束；JSQLParser 5.2类型化节点交给完整约束检查 | IMP-04；接口补充设计§4.8 |
| business/medical-data/src/main/resources/db/migration/016-platform-local-management.sql | platform_local_operation、platform_local_audit | 本地账户操作、独立HMAC身份、不可变原响应及同步审计；十四项和十一项字段、完整关联及唯一约束 | IMP-04；接口补充设计§4.6 |
| business/medical-data/src/main/resources/db/migration/002-identity-sources.sql | identity_binding、rights_revision、collection_version 等24表 | 身份、幂等、上传、来源、目录及权利 | IMP-04/05/08—10 |
| business/medical-data/src/main/resources/db/migration/003-project-template.sql | project_profile_revision、template_version 等7表 | 项目属性、适用决定、模板、字段及规则 | IMP-11/12 |
| business/medical-data/src/main/resources/db/migration/004-task-execution.sql | task_revision、plan_revision、execution_registry 等12表 | 固定任务依赖、计划、运行、动作及头指针关联 | IMP-12—15 |
| business/medical-data/src/main/resources/db/migration/005-document-evidence.sql | document_revision_meta、field_value、artifact 等16表 | 固定正文、章节、字段、事实、指标方法与依赖集合 | IMP-16—24 |
| business/medical-data/src/main/resources/db/migration/006-review-signature-export.sql | candidate、check_run、signature_record 等18表 | 候选、证据及人工决定、再次身份核验、签发、导出与任务运行控制 | IMP-13/16/22—24 |
| business/medical-data/src/main/resources/db/migration/007-capability-model-budget.sql | configuration_head、invocation、budget_reservation 等12表 | 配置身份及历史版本、发布、真实连接核验、预算和调用结果关联 | IMP-07/14/28 |
| business/medical-data/src/main/resources/db/migration/008-governance-evaluation-connector.sql | reference_hold、evaluation_batch、connector_config 等10表 | 来源转存、引用保留、删除、评测、反馈与客户连接 | IMP-10/28/30 |
| business/medical-data/src/main/resources/db/migration/009-conversation-events-receipts.sql | conversation、stream_registry、content_read_receipt 等16表 | 固定会话、动作确认、事件投递、审计、上传及读取回执、管理发布和报告导出 | IMP-05/08/25/27/28 |
| business/medical-data/src/main/resources/db/migration/010-platform-identity.sql | platform_identity | 后端 §3.2 平台认证目录；issuer/sub 唯一键、状态、revision 及审计字段 | IMP-04 |
| business/medical-data/src/main/resources/db/migration/011-platform-oidc-session.sql | platform_oidc_session | 接口补充设计 §4.2 的应用会话绑定、身份外键、状态及撤销时间约束 | IMP-04 |
| business/medical-data/src/main/resources/db/migration/014-platform-business-session.sql | platform_member_route、platform_session_context | 本人成员路由、实际会话上下文、成员复合外键和上下文状态 CHECK；路由不构成授权 | IMP-04 |
| business/medical-data/src/main/resources/db/migration/coverage.json | required_tables、platform_tables | 135企业表及15平台表；包含完整登记申请、原响应及同步审计平台元数据 | 完整覆盖检查 |
| tests/test_migration_bundle.py | MigrationBundleChecks | 成熟 SQL AST 与实际迁移资源的结构、复合关联和覆盖检查 | IC-03-MIGRATION-COVERAGE |
| scripts/run_migration.py | required_value、main | 本地迁移包装命令；固定制品摘要、目标身份及必须提供的连接与记录引用 | 数据库迁移岗位 |
| tests/test_migration_cli.py | MigrationCliChecks | 实际 jar 资源、原生命令启动、参数及连接前拒绝检查 | IC-03-MIGRATION-CLI |
| scripts/check_migration.py | main | 使用实际输入与全新执行目录保存构建、覆盖、跨语言及迁移入口检查证据 | 本地实施检查 |
| scripts/check_typescript.py | main | 保存 TypeScript 类型与运行检查、完整 Schema、源码和实际来源的绑定证据 | IC-03-TS-TYPES / IC-03-TS |

## 决策与约束

五份 Schema 通过固定目录读取，引用只能解析登记集合；调用者不能传入任意远端 Schema URL。UUID、大整数、Decimal 和时间的语义校验按后端 §3.1执行，Schema 结构检查与业务授权分别负责。引用定位校验不证明人工支持决定或生产使用授权。JSON 规范编码使用成熟 RFC 8785 库；原件及原文摘要直接核对保存字节。216 项定义的引用均在本地解析；未知字段、版本、格式及摘要错误立即拒绝。错误不包含原始资料载荷。

Python 依赖由 requirements.lock 固定，使用 jsonschema 4.26.0、referencing 0.37.0、PyYAML 6.0.3、rfc8785 0.1.4、Pydantic 2.13.5、pydantic-core 2.46.5、annotated-types 0.8.0、typing-inspection 0.4.4；全部格式组件真实安装。Java 使用 networknt 1.5.9、Jackson 与 java-json-canonicalization 1.1；TypeScript 使用 Ajv 8.20.0、ajv-formats 3.0.1、yaml 2.9.1、json-canonicalize 3.0.1、decimal.js 10.6.0 及 Temporal polyfill 0.5.1。锁文件及构建定义纳入版本管理。

Python 使用 ConfigDict 的 strict、frozen 和 revalidate_instances=always。固定字段对象使用 extra=forbid；原始接口 Schema 的三个开放继承基准使用 extra=allow 和 JsonValue 保存扩展属性，封闭的继承请求继续使用 extra=forbid。nullable 字段保持必需输入；Pydantic 不自动替补缺少字段。UUID、UTC、revision 和 Decimal 的 AfterValidator 校验原值并返回原始字符串，来源登记及 EvidenceRef 的文本身份保持原定义。模型内容块、流载荷、七种定位、PDF 区域、DOCX 地址与三种选区使用已声明联合类型；Coverage、SourceScope、FinishReason 和 FinishEvent 保留原始条件规则。

TypedSchemaBindings 先验证发布集合的唯一 Schema，再由 TypeAdapter 严格读取，并将按原始字段名称序列化的内容重新验证 Schema 和 RFC 8785 字节一致性。保存内容来自独立规范字节；TypedPayload.read 每次生成独立模型，成员数组修改不会改变保存内容。模型属性采用 frozen，成员数组仍可修改，独立读取检查覆盖实际登记来源和记录。完整实际登记保持11份来源、12条记录、原许可和 formal_evidence_eligible=false。31项 Python 检查通过；对象核查覆盖登记与内联17个对象、模型19个对象、公共/资源27个对象、专业产物37个对象及完整接口93个对象的字段、必填属性、枚举和引用。五份 Schema 的216项定义逐项具有类型映射；真实业务模型、接口、资源详情、专业产物和服务消费保持 NOT_RUN。

Python InterfaceBindings 包含全部102项定义，复用 common.py 的公共17项及 dataset.py 的 EvidenceRef。ReviewBaseline、StepIdentity、ActionIdentity 保存原始开放属性；其封闭派生类型保留全部继承字段和必填属性。NodeContent 包含五种正文内容，PublicEvent 的九项映射同时检查资源种类、载荷类型及读取操作；ManagementModule 保留 BOUND 和未启用状态的条件。OperationLookup/ManagementOperation.operation_id、tokenizer_revision 保持文本类型。Envelope.data 使用 Pydantic JsonValue，技术 Envelope 以实际检查 UUID 标识登记读取，允许真实登记包、实际字节长度和 null；独立读取与 U64 超范围拒绝通过。CredentialWrite.secret_value 不出现在模型字符串表示中，保留 writeOnly 元数据；实际秘密服务及请求消费尚未运行。ReportFilters.from_ 使用 alias 保持原始 from 字段，TypeAdapter 序列化明确采用 by_alias=True；字段名称、UTC 与测量精度均按原始定义核查。

Python ArtifactBindings 将49项定义逐项映射到已声明类型，另行绑定 ProfessionalArtifact 外层。ArtifactOutput 和 Anchor 使用 discriminator，八种输出分别保存对应内容类型；EvidenceRef 复用 dataset.py 的原始文本身份，AttachmentBinding.source_version 使用 UUID。TASK 必须包含项目、计划版本和资料快照；DECIMAL 必须包含原文、数值和精度；四种事实或要求的 Claim 必须包含证据；DOCUMENT 汇总必须引用文档及文档章节版本。唯一 Schema 与 model_validator 同时执行这些条件。测量值38/12、非负金额24/8、安全整数及 UTC 保持原始表示。三项新增检查核查完整定义、37个对象、引用及联合分支，以及真实登记标量和规定数值边界；没有生成业务专业产物或收费记录。

Pydantic API 依据 [严格模式](https://pydantic.dev/docs/validation/latest/concepts/strict_mode/)、[TypeAdapter](https://pydantic.dev/docs/validation/latest/concepts/type_adapter/)、[联合类型](https://pydantic.dev/docs/validation/latest/concepts/unions/)、[字段别名](https://pydantic.dev/docs/validation/latest/concepts/alias/)及 [2.13.5 构建定义](https://raw.githubusercontent.com/pydantic/pydantic/v2.13.5/pyproject.toml)。JsonValue 和扩展属性同时核查实际2.13.5源码与官方 API。实际取得的 Pydantic wheel 摘要为 `346a034f080da3755d8e9cb5e00e8b07de1d39e4f6e2c87d8ab7cafa0b269a73`，与官方版本元数据相等。共享 wheel 的安装及31项隔离检查见 IMP-01，不能代表 Python Worker 或 SDK/runtime 已完成。

已定义 TypeScript 登记包的13项静态类型、模型协议的全部25项定义、资源详情的全部27项定义、页面与内部接口的全部102项定义，以及专业产物的全部49项定义与外层对象。公共17项类型在 interfaces-common.ts 中维护，接口与资源类型复用同一声明。完整接口类型映射及表示绑定已实施，实际编译器逐项核查102项映射与原类型双向可赋值。模型类型保持请求参数、内容块、用量、失败、完成原因、八类流载荷、调用状态及终态关系，数值类型与空值沿用原始 Schema；运行校验负责 UUID、范围及其他格式限制。bindModelStream 保存经验证的规范 JSON，每次读取重新解析，调用者修改无法改变保存内容。原始登记包使用文本来源身份，模型 Id 要求 UUID；检查保持两份 Schema 各自的身份规则。完整错误枚举已实施，三种语言的服务调用方继续实施。三种语言均可将216项原始定义绑定为经验证、可重读且不共享可变状态的载荷。技术表示检查的执行 UUID 只标识真实检查进程；数值边界取规定类型范围，检查不创建业务身份、客户资料或收费记录。

Python ErrorCode、Java ErrorCode 和 TypeScript ERROR_CODES 均包含后端 §4.10 的全部68项代码。三个严格读取入口拒绝未知值并返回 SCHEMA_UNSUPPORTED；Python/TypeScript 同时拒绝非字符串。SchemaCatalogue 校验失败的异常 code 为 SCHEMA_INVALID，诊断文字保持 SCHEMA_VALIDATION_FAILED；未登记名称、定义及版本返回 SCHEMA_UNSUPPORTED。Python 继承 jsonschema.ValidationError 并仅保留校验规则及位置，未知 Schema 异常继承 ValueError；Java SchemaRejection 继承 IllegalArgumentException，code 为 final；TypeScript SchemaRejection 和枚举数组 frozen。实际资料的版本损坏、真实来源身份作为未登记名称以及未知代码均通过拒绝检查，异常不包含被拒绝资料值或异常原因。Python 四项新增检查、Java 两项新增检查及 TypeScript 两项新增检查通过；跨语言报告分别输出代码集合，再与实际设计表格逐项比较。当前没有将枚举绑定到 HTTP 错误体、重试策略或领域服务，不登记这些行为通过。

interfaces.ts 保留九种 PublicEvent 的 event_type、resource_kind、payload 与 read_operation_id 对应关系。ManagementModule 的 BOUND 成员要求文本路径、至少一项操作及权限、空 unavailable_reason；UNBOUND/DISABLED 成员保持空操作集合及原因文本。StepIdentity、ActionIdentity 与审查基准按原始 allOf 继承，EvidenceLink 和内部读取沿用 dataset 的 EvidenceRef。OperationLookup.operation_id 与 ManagementOperation.operation_id 保持文本类型，tokenizer_revision 保持文本，不能按字段名称统一转换为 UUID。实际编译器核查102个定义及93个对象的全部已声明字段、必填属性和直接枚举，并独立核查九个事件分支；静态检查不证明接口生成、权限执行或业务验收通过。JSON 属性名称与属性内容均使用标准 UTF-8 编码检查，无效 Unicode 立即拒绝。

bindInterface 根据原始 Schema 的 format、Revision 定义身份及 FieldValue.decimal_value 原始位置检查小写 UUID、UTC、BIGINT UNSIGNED 和测量值精度，保存规范 JSON 后每次读取重新解析。SchemaCatalogue 使用 Ajv.getSchema 返回的原始 Schema 身份定位引用；allOf 全部执行，oneOf/anyOf 与 if/then/else 由实际 Ajv 验证选择有效分支，数组和已声明属性沿用原始节点。来源定位及 EvidenceRef 的文本字段保持原定义，Envelope.data 的 true Schema 维持自由 JSON；规则不按字段名称转换类型。依据已完整读取的 [Ajv API 说明](https://ajv.js.org/api.html)及本地8.20.0源码，JSON Pointer 转义使用 Ajv 的 escapeFragment，引用仅在原发布集合中解析。五项绑定检查使用实际登记包及检查 UUID；技术 Envelope 标识本次实际登记读取，resource_revision 为空，不创建企业、项目或业务回执。实际字段、接口资源、权限和数据库事务的服务验证仍为 NOT_RUN。

artifacts.ts 保留八种输出与内容类型的对应关系，TASK 的项目/计划/快照非空、DECIMAL 的原文/值/精度非空、可核查 Claim 的证据集合非空、DOCUMENT 汇总的章节类型为 DOCUMENT。ObjectRef.size_bytes 按产物原始 Schema 保持安全整数，内部接口中的字符串大小转换继续由宿主完成。bindArtifact 校验原始 Schema、小写 UUID、测量值 DECIMAL(38,12)、金额 DECIMAL(24,8) 和 UTC，再保存独立规范 JSON；每次读取重新解析。EvidenceRef 与 EvidenceRefs 使用登记包原始身份，AttachmentBinding.source_version 使用产物 Schema 的 UUID。表示字段集合与唯一 Schema 引用逐项相等。五项产物检查使用实际编译器、真实登记标量和实际检查进程 UUID，未创建专业业务产物。AS-01—AS-15 的当前身份、模板全集、依赖、原文、问题、授权、预算和实际对象比较仍需 C04/C05/C07/C12 的业务实施与运行验证，不能由类型检查或绑定代替。

资源类型保持任务、核查、证据决定、批注、签发、消息、配置、评测、资料目录、模板、变更与比较的原字段；ConfigurationDetail.version 为 UUID，resource_revision 和各类水位为十进制字符串。Coverage 的 complete 与 next_cursor、SourceScope 的模式与集合、网络开关与批准版本保持联合类型的条件关系。bindResourceDetails 校验唯一 Schema、小写 UUID、UTC 与 BIGINT UNSIGNED 范围，再保存独立规范 JSON；读取返回独立内容。实际 TypeScript 编译器检查27个公共与资源对象的字段、必填属性及直接枚举；表示字段集合与两份原始 Schema 的类型引用完整相等。真实资源详情对象的服务生成、读取与业务使用仍为 NOT_RUN。

Java 模型类型覆盖同一25项定义，内容块及流载荷使用 sealed interface 与 Jackson 已声明子类型，集合成员使用独立不可变集合。Binding 保存已验证的规范 JSON，读取后重新校验序列化内容，字段与空值发生变化时立即报错。Java 与 TypeScript 的模型读取入口执行小写 UUID 与 UTC 时间表示规则。Java 容器使用 JsonCanonicalizer，标量数字使用其公开 NumberToJSON API，文本、布尔和空值使用 Jackson 的 JSON 编码；Unicode 编码检查使用标准 UTF-8 Encoder 的 REPORT 模式，转义采用小写十六进制。API 依据已完整读取的 [JCS 说明](https://github.com/erdtman/java-json-canonicalization)及 [NumberToJSON 源码](https://raw.githubusercontent.com/erdtman/java-json-canonicalization/master/src/main/java/org/erdtman/jcs/NumberToJSON.java)，实际 Jackson 2.21.4 API 已核查。

Java 公共17项和资源详情27项定义已建立明确类型；原始资源 Schema 引用16项公共定义，公共登记保留 NullableHash 供完整接口使用。TypedSchemaBindings 使用 JavaType 保留数组成员类型，读取执行严格 Jackson 字段检查及 DTO 构造规则，再核对原始 Schema 和完整规范内容。CommonProtocol 与 ResourceProtocol 按原定义区分 UUID、文本身份、十进制 revision 和 UTC，集合成员复制为独立内容；Coverage、SourceScope 和三种选区保持原始条件关系。四项检查核查完整定义集合、原始引用、27个对象的全部字段和直接枚举，并使用实际登记标量及11份原件进行读取。Coverage 正向检查记录真实文件的完整读取摘要，范围为 LOCAL_PARSER_CHECK；没有创建企业、项目、签发或人工决定。实际资源详情服务及业务对象往返仍为 NOT_RUN。

DatasetProtocol 与 DatasetBindings 包含登记包外层及全部13项定义。来源、记录、EvidenceRef 及定位身份保持原始文本；七种定位、两种 PDF 区域、三种 DOCX 地址使用 sealed 类型与 Jackson 已声明子类型。三个检查核查17个对象的全部字段、直接枚举与集合枚举，读取当前11份来源及12条记录，保存许可和正式证据资格原值。完整登记及每个实际成员通过类型往返，集合保持独立内容；没有扩大生产用途或提供授权决定。EvidenceRef 的真实业务读取仍需获授权运行资料。

InterfaceProtocol 与 InterfaceBindings 包含全部102项定义，复用 CommonBindings 的17项声明。五个检查核查93个对象的全部字段、枚举、继承必填属性和原始引用对应的 Java 类型及泛型成员；原文证据字段直接引用 DatasetProtocol.EvidenceRef。九种公开事件保留 event_type、resource_kind、具体 payload 和 read_operation_id 的对应，正文保留五种内容类型，管理绑定保留启用条件。ReviewBaseline、StepIdentity、ActionIdentity 的原始 Schema 允许额外属性，OpenObject 保存独立内容并参与完整规范 JSON 往返；实际基准及扩展载荷仍待业务输入验证。OperationLookup.operation_id、ManagementOperation.operation_id 和 tokenizer_revision 保持文本。CredentialWrite 的字符串表示隐藏秘密值，接口请求序列化和后续秘密服务分别验证。

Java 技术 Envelope 使用本次检查 UUID 标识真实登记读取，resource_revision 为空，data 保存原始登记内容并逐次返回独立内容；自由 JSON 的 null 值可读取。U64 超范围断言核查 ValueInstantiationException 的具体原因，防止其他异常成为通过证据。该检查范围为 LOCAL_SCHEMA_AND_WIRE_CHECK，没有创建企业、项目、动作、业务事件、审查或签发。接口服务、权限、持久化及真实对象往返继续保持 NOT_RUN。

ArtifactProtocol 与 ArtifactBindings 包含专业产物49项及完整外层对象。ArtifactOutput 与 Anchor 使用 sealed 类型和 Jackson 已声明子类型，八种输出的 content 保持各自类型。TASK 的项目/计划/快照非空、DECIMAL 的原文/值/精度非空、四种可核查 Claim 的证据集合非空、DOCUMENT 汇总的章节版本为 DOCUMENT，均由唯一 Schema 及 DTO 构造执行。集合成员复制为独立内容；EvidenceRef 和 EvidenceRefs 直接引用 DatasetProtocol 的文本身份，AttachmentBinding.source_version 保持产物定义的 UUID。ObjectRef.size_bytes 保持安全整数，测量值按38/12、金额按24/8、HandoffRecord.created_at 按 UTC 执行表示规则。

四个产物检查核查49项定义和37个对象的全部字段、必填属性、枚举、原始引用与泛型，八类输出及两类锚点的子类型声明与原始联合分支相等。真实登记摘要、文本、字节长度的字符串编码和用途文本用于标量读取，检查 UUID 只标识本次执行，数值超范围使用规定类型边界；检查没有创建专业产物、计费或预算记录。五份 Schema 的216项定义已逐项具有 Java 类型映射，完整外层另行登记。实际专业产物正向往返、生成服务、AS-01—AS-15 的身份、模板、证据、权限、依赖与预算关系仍为 NOT_RUN。

跨语言报告追加56个实际登记标量的规范摘要：11份来源的文本身份、摘要、字节长度和父来源空值/文本，以及12条原文。字节长度仅用于检查整数编码；本项没有创建模型用量或收费回执。Java 对象 DTO 当前通过定义集合与全部对象字段的映射检查；真实 ModelResult、StreamEvent 的类型往返、调用方使用及业务接收仍为 NOT_RUN，需要获授权的实际运行回执。

迁移使用当前 Spring Boot BOM 管理的 Liquibase 4.31.1，SQL/XML 使用成熟解析器。data-design/core.sql 保持18张核心表的唯一来源，由 sqlFile 引用并按原字节加入迁移 jar。补充表采用独立 changeset，MySQL DDL 标记 runInTransaction=false；失败立即终止，已执行 changeset 的校验和由 Liquibase 管理。制品尚未进入批准环境，不配置业务服务自动建表。

medical-data 同时构建普通资源 jar 和 `medical-data-3.9.2-cli.jar`；独立 CLI 以 LiquibaseCommandLine 为入口，包含 Liquibase 4.31.1、官方所需 picocli 4.7.6 和当前 BOM 的 Connector/J 9.7.0。picocli 的实际 POM 记录 Apache 2.0 许可。打包依据已完整读取的 [Spring Boot 3.5 打包说明](https://docs.spring.io/spring-boot/3.5/maven-plugin/packaging.html)；CLI 选项及环境变量以实际4.31.1命令帮助核查。普通 jar 与 CLI 的11项迁移资源及原始 core.sql 逐项字节一致。

本地包装命令支持 version/help、validate、status、update-sql、update。数据库动作要求实际制品 SHA-256、MySQL JDBC 连接、账号、秘密、数据库名称、server_uuid 和批准记录引用；update 还要求备份回执引用。密码由环境注入，命令参数保留固定 changelog、JDBC driver 及目标属性。CLI 使用严格模式和 STRICT 解析，并关闭使用分析发送。未提供摘要、摘要属于其他实际文件或未提供连接时，包装命令在启动 Liquibase 前立即拒绝。

master.xml的HALT前置条件规定MySQL 8.4、实际DATABASE()和@@server_uuid与目标输入相等。本地update已执行178项数据库记录，当前身份已经重读；其他目标、生产批准及备份恢复分别验收。当前双制品摘要由IC-03-JAVA-20261004-REGISTRATION-05绑定；其他原生CLI检查依据各自输入判断适用性。

JSON 列所在补充表记录 schema_version 与 schema_manifest_sha256，供写入方绑定已批准发布集合。成员、项目、来源与版本引用保留完整企业身份；任务、计划与节点的关联绑定同项目和固定模板。允许企业作业的运行通过生成列 project_key 将空 project_id 表示为 ENTERPRISE，再建立包含企业、归属和运行的复合外键。TASK 与 SYSTEM_JOB 使用类型外键，防止记录关联不同执行种类。头指针按后端 §3.5 的构造顺序允许初始化空值，并通过复合外键关联对应版本。运行中的结果引用允许空值，完成检查类作业需要真实覆盖产物；终态约束、领域事务与全部跨实体关联继续实施。

document_revision_meta 同时关联固定正文身份、快照与正文摘要、模板及计划基准、正文制品的对象版本和依赖集合。based_on_project_id、based_on_document_id 与 based_on_document_revision 共同引用原版本；新的任务和文档头可以保留原签发版本关系。document_node 的父节点属于同一文稿，field_value 和 fact_usage 的节点、字段及重复行保持同版本关联。来源读取许可、完整正文投影与状态转换仍由后续业务事务验证。

candidate 保留 PLAN 与 DOCUMENT 类型及固定任务修订、模板和快照。计划候选没有正文基准及批准计划，正文候选需要正文基准、计划及两类证据产物；candidate_adoption 将采纳结果关联对应计划或正文版本。内容摘要通过复合外键绑定实际 artifact。check_run 通过 target_kind 将 DOCUMENT 关联固定正文，将 CANDIDATE 关联候选的 content_artifact_id，分别保留目标身份与内容版本；数据库结构禁止候选检查成为正文问题的检查来源。候选内容产物作为不可变版本的映射需要在写入 SDK 与 API 中完成。证据决定按同文稿、批次和 EvidenceLink 关联，人工章节决定关联文稿中的 section_revision。reauthentication_receipt 保持挑战、回调回执、主体、正文、用途及策略的完整关联，signature_record 绑定同一再次身份核验回执并保留用途唯一键。export_item 按格式与尝试区分结果，SUCCEEDED 需要成果及验证产物。task_active_run 按同任务的唯一记录限制可派发运行；派发及取消事务尚未实施。

configuration_head保存配置身份、类型、名称及当前版本；agent_version、capability_version、model_config_revision和connector_config分别保存版本。Agent名称按企业和类型唯一，历史版本保留同一名称；ConfigurationDetail的config_id/version及名称约束保持完整，135张企业表保留全部设计实体。调用固定动作、模型、秘密引用版本、连接、价格和币种；结算与预留核销回执关联原invocation，秘密值不进入相关表，未知金额保持空值。

治理表保存引用保护、删除对象与处理回执。evaluation_score 允许待判定分值为空，保存原始文本和分子/分母表示，专家修订关联同一评测、样本、Agent 和维度；评测规则计算仍由后续服务负责。sync_item 保存完整外部身份及版本，规范摘要作为唯一索引；写入方必须重新比较完整身份字节，核查摘要碰撞。当前没有创建连接、模型调用或客户同步记录。

conversation_message 保存原范围快照、正文版本、选区及依赖集合。pending_action 保存参数摘要、预期 revision、期限及人工决定；确认记录必须具有决定身份及时间。stream_registry 分别关联 RUN 或 CONVERSATION；stream_event 的序号、资源 revision 与具体资源身份独立保存，九种事件类型关联对应种类，项目及运行关系使用完整外键。outbox_delivery/consumer_receipt 按事件和消费者唯一，租约 owner、期限及 generation 共同保存。principal_token 同时关联原 principal 的项目和运行，步骤引用同一运行；source_session_binding 关联同项目会话。

artifact_upload_grant 在实际对象版本产生前允许 object_version/object_identity_sha256 为空，取得版本后共同填写；COMPLETED 需要实际 artifact，外键绑定对象版本和摘要。完整对象身份摘要需要写入方比较原始 key/version，字节、大小与 Schema 检查仍由上传服务执行。content_read_receipt 保留选择、交付和内容摘要以及当前权限水位；步骤读取需要运行、步骤和租约 generation。报告导出成功必须具有真实 manifest_artifact_id 与 record_count，当前没有生成报告成品。管理发布当前启用指针、领域 CAS、状态变更、JSON 发布校验和不可变记录保护继续实施。

生成列均使用 STORED，关联采用完整唯一键并保持字段类型一致；MySQL 实际执行仍需核验字符集、排序规则及约束行为。相关语法依据已读取的 [MySQL 8.4 生成列说明](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html)、[外键说明](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html) 与 [CHECK 说明](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)。

Java 实际检查报告保存在 .implementation-work/IC-03-JAVA-SNAPSHOT/executions/<execution_id>/report.json，使用 CREATE_NEW 保存；current.json 通过原子替换指向最新报告。报告绑定全部 Java 源文件、测试、唯一 Schema、登记包、后端错误设计和实际构建定义，当前36项输入。跨语言检查重查路径、身份、报告摘要及绑定输入。每个执行报告独立保存，后续运行保留以前报告。

## 验证与证据

当前迁移及构建证据为IC-03-MIGRATION-COVERAGE-20261004-REGISTRATION-01和IC-03-JAVA-20261004-REGISTRATION-05，原始SHA及187/40项输入由IMP-04的当前证据表维护。以下既有回执仅适用于各自记录的输入和时间；完整IC-03继续IN_PROGRESS。真实本地MySQL总计178项迁移及150张业务/平台表，由当前账户数据库回执重新读取。

Given：实际来源登记包、原文及唯一 Schema；When：读取完整登记集合并验证载荷及原文字节；Then：所有引用可解析，真实结构接受，未知字段、损坏版本或摘要以及错误定位拒绝。测试必须实际执行；各次运行回执保存于项目 .implementation-work/。

| check_id | 实际检查 | 当前结果 | 回执及 SHA-256 |
| --- | --- | --- | --- |
| IC-03-PYTHON-RED | 原始登记、模型、资源及专业产物类型行为断言 | Red；类型入口缺少时分别出现5、3、2、3项断言失败；依赖和既有检查通过 | [登记回执](../../.implementation-work/IC-03-PYTHON-RED-20261001-02/receipt.json) `4d55193619617e45dd22a9cf57a6309d4ad60651b1534541aace538efc0a4d3e`；[模型回执](../../.implementation-work/IC-03-PYTHON-RED-MODEL-20261001-01/receipt.json) `af9f50f3d823abc5dd93fd087a114a614325f720afceec6b893727498085cfe7`；[资源回执](../../.implementation-work/IC-03-PYTHON-RED-RESOURCE-20261001-01/receipt.json) `4634389bb0385f478de2741bf8b882c93c8672fd3eb2b0430debcb2684631c3a`；[专业产物回执](../../.implementation-work/IC-03-PYTHON-RED-ARTIFACT-20261001-01/receipt.json) `98271f29f7bbb9347aa0e2e69918b1ca76faf372e9c4238360adcb988833bb51` |
| IC-03-PYTHON-RED-INTERFACE | 完整接口类型行为断言 | Red；类型入口缺少时四项断言失败，既有23项检查通过 | [完整接口回执](../../.implementation-work/IC-03-PYTHON-RED-INTERFACE-20261001-01/receipt.json)；`1a2797ed7f3441c4c9f4fdcd51857827afb151e89deb770b8e5b0b3152950e9e` |
| IC-03-PYTHON-RED-ERRORS | 标准错误代码及 Schema 拒绝行为断言 | Red；四项新行为断言失败，既有27项检查通过 | [错误代码回执](../../.implementation-work/IC-03-PYTHON-RED-ERRORS-20261001-01/receipt.json)；`77eb5eafbe7ef1cd01230bea44a24c0fcb2733a5a3ddf22f316e2b4a3b333fea` |
| IC-03-PYTHON | scripts/check_python.py --check test；共享与 Pydantic 类型、实际登记包、原文及错误设计 | PASS；31/31；216项类型绑定、公共17项声明；完整68项错误枚举与类型化拒绝；字段、枚举、继承、引用、联合分支及九种事件；真实往返、独立读取及拒绝检查 | [当前回执](../../.implementation-work/IC-03-PYTHON-GREEN-20261002-02/receipt.json)；0250c916ad718a8edd534df278e0b439afb7e3fbbb024faf752db87bb671aa07 |
| IC-03-JAVA | Maven clean verify；共享、迁移、企业及身份 | PASS；共享26、迁移1、企业12、身份26，共65项；聚合工程及十个子模块SUCCESS；216定义、68错误、176项changeset；178项输入及8项输出 | [当前回执](../../.implementation-work/IC-03-JAVA-20261002-37/receipt.json)；06567e0bd0f25b055692d9dea4b59aabb2c1ee00415cc9e416f2c68b0a478b67 |
| IC-03-TS-TYPES | tsc --noEmit，strict=true | PASS；完整68项 ErrorCode；模型25项、资源27项、接口102项、专业产物49项、登记13项类型与绑定；外层对象及公共17项声明 | [当前回执](../../.implementation-work/IC-03-TS-TYPES-20261002-02/receipt.json)；724cb464abbe29ac2c2e8255d7412fa70af39d8a872261aa0c2608c3ff194a70 |
| IC-03-TS | Node 实际执行26项测试 | PASS；26/26；标准错误、未知值、全部定义、真实编译器字段与映射、继承、条件类型、Unicode、引用、表示、真实登记及独立读取 | [当前回执](../../.implementation-work/IC-03-TS-20261002-02/receipt.json)；86314868e0ac99329b25ae7fe99c610531513affb8be082dcc531de7ea832c0d |
| IC-03-CROSS | Python/Java/TypeScript实际结果比较 | PASS；216定义、68错误、11来源、12定位、5处原文、56标量；68项输入及2项输出；独立当前报告重读 | [当前回执](../../.implementation-work/IC-03-CROSS-20261002-18/receipt.json)；c466e1088c19ddfd4ce1bf250328ba209219505f428c22873487ec250f2a7064 |
| IC-03-MIGRATION-COVERAGE | SQLGlot 30.0.3、JSQLParser 5.2及XML | PASS；14/14；135企业表、13平台表；完整覆盖、复合键、字段、审计、Schema及邀请/角色；39项输入 | [当前回执](../../.implementation-work/IC-03-MIGRATION-COVERAGE-20261002-20/receipt.json)；ba00a139b98451fbcb7a25407ec2be7379c7b113a60af5d9eaea21648171feef |
| IC-03-MIGRATION-CLI | 实际双制品及原生Java进程 | PASS；4/4；资源一致、版本/帮助、数据库动作参数及连接前必需配置/摘要拒绝；37项输入 | [当前回执](../../.implementation-work/IC-03-MIGRATION-CLI-20261002-18/receipt.json)；79583b6114fba473efd7f1e9a25ada6e4aee7162e7fa5e562cf5f17cabaf9c61 |

实际登记包规范摘要为 `67560cf1a91e7464bab061fb8b13bf2432439f6984a623a9968e2c9bcc90e359`。原始输出与输入摘要保存于回执所在目录；命令见 [实施检查命令](../commands.md)。跨语言检查拒绝过期 Java 报告。正式 Java jar 的 BOOT-INF/lib 已包含 medical-contracts-3.9.2.jar、json-schema-validator-1.5.9.jar 和 java-json-canonicalization-1.1.jar。

共享表示通过范围为LOCAL_SCHEMA_AND_WIRE_CHECK；真实MySQL 8.4.3按BACK-LOCAL-01执行178项迁移，包括本人路由/上下文、账户操作/审计、明确成员身份、邀请/接受路由、批准角色版本及企业登记申请/审计。135企业表和15平台表已经建立；历史数据库、领域事务、全部约束及恢复继续验证。七种定位结构已校验，原文件读取限登记五处TXT范围，其他格式由IMP-08实施；没有新增AC、NFR或生产通过记录。

共享构建及迁移检查由 scripts/check_migration.py 固定 TMPDIR 到项目 .implementation-work/migration-check-tmp/。Python 单元及制品检查使用 .implementation-work/python-tmp/，全部执行日志和回执保存到新的项目目录。

结构及资源检查限MIGRATION_STATIC_CHECK / LOCAL_PARSER_CHECK，CLI限LOCAL_ARTIFACT_CHECK / LOCAL_ENTRYPOINT_CHECK；真实本地结果单独绑定当前HTTP、成员及登记数据库回执。135企业表及15平台表来自后端§3/5.2及接口补充设计§4；178项changeset实际运行。当前72项原生检查包含共享26、迁移1、企业19及身份26。外部OIDC、字符比较、并发、领域事务及历史完整性继续验收，不登记完整IC-03或IC-04通过。

## 关联影响与后续使用

绑定供后续全部领域 API、Activity 与前端读取；接口消费前重新检查 Schema 摘要。禁止通过改动原始 Schema 降低断言。所有修改通过文件编辑工具完成。

## 未完成内容与继续条件

继续实施全部服务消费；核查全部跨实体关联、不可变版本、终态约束、索引、Outbox、预算与引用保护；完成领域错误体、重试准入、事务及调用方绑定；在真实目标 MySQL 执行并核查约束、事务及历史版本。完整68项错误枚举与 Schema 拒绝已通过本地检查，独立 CLI 已通过本地启动检查；实际数据库、批准连接及历史输入缺失只影响对应运行。全部 IC-03 通过之前保留 IN_PROGRESS。
