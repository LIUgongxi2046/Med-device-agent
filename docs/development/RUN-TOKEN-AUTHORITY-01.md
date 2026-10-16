# RUN-TOKEN-AUTHORITY-01 持久运行令牌与步骤授权

## 任务身份与依据

本任务属于BE-04，关联BE-07/09/13/14、C02、IC-04及SEC内部调用要求。任务状态来源为本记录，当前IMPLEMENTED；父工单状态保持实施任务计划IMP-04的IN_PROGRESS。2026-10-14读取IMP-04、IMP-13、开发索引、LLD-后端§2.1/2.2/3/6、LLD-Agent的StepIdentity与ActionIdentity、002/004/007/009迁移、当前签名及服务证书组件、企业事务和PermissionAuthority。

当前签名组件证明原签名与Claims，服务证书组件证明登记服务身份。内部调用还需要真实principal_token、run_principal、execution_registry、当前人员身份和授权、运行状态、步骤代际及期限。此任务提供签发、续期与短事务执行入口；运行创建和专业消费者由各自任务维护。

## 验收行为

- Given登记的mTLS服务、当前原运行与步骤、有效发起人员授权；When签发或续期；Then保存唯一jti、原签名摘要、服务身份、当前Policy摘要及步骤代际，重新读取原记录后返回令牌。
- Given原签名令牌及当前登记服务；When请求明确资源、动作与用途；Then当前企业事务核查原记录、运行归属、全部固定清单、有效人员、权限、步骤租约及已登记动作后调用实际业务操作。
- Given旧代际、已到期或撤销令牌、已取消运行、停止成员或已撤销动作；When请求新动作；Then在业务操作执行前拒绝。
- Given旧调用需要提交终态回执；When原业务消费者登记原调用结果；Then使用独立回执准入要求；本任务的新动作入口不扩大该权限。

## 当前实现与约束

当前代码及本地必要配置已经具备，状态IMPLEMENTED；业务验收保持PENDING，文档CURRENT，last_verified=2026-10-14仅适用于本节工程及实际运行证据。真实客户运行和岗位批准尚未提供，没有建立客户企业、专业许可、模型结果或收费调用。合法业务成功、并发撤权及正式Worker使用继续原要求。

RunTokenAuthority的claimStep/renewStep/issue/renew/execute供内部业务模块使用。领取及续租使用原企业事务、当前人员与权限、发布ROLE、原运行、步骤修订、服务URI、数据库期限和租约代际；领取新代际使旧令牌不能执行新动作。TASK还核查实际task_id上的task.run/TASK_EXECUTION许可，SYSTEM_JOB核查主体固定的完整许可集合。令牌续期保持原动作范围，当前Policy与服务绑定和原签名摘要完整保存，新jti与原jti撤销使用同一事务。execute核查当前资源/动作/用途以及已登记动作的完整输入、参数、kind、tool_version与代际；业务模块继续核查目标对象及固定依赖。返回前重新检查当前许可、服务证书、Policy和期限。

ScopeManifest、PrincipalManifest、Permission、Service和RoleScope使用run-token-authority.schema.json封闭定义及成熟Jackson/networknt/JCS。MySQL JSON动作数组重新读取时使用RFC8785形式，Claims保留原有序动作集合；摘要及持久身份逐项比较。Admission、Lease和Issued的构造器私有，Issued的toString不返回令牌正文。内部HTTP、正式运行创建、完整固定依赖、合法终态回执及生产RunActivities继续由对应消费者连接。

| 文件及identifier | 当前职责 |
| --- | --- |
| business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/RunTokenAuthority.java | 当前运行与动作准入、步骤领取与续租、签发与续期、当前权限及期限重验 |
| 同目录RunTokenAuthorityStore.java | 十一项明确企业条件SQL、行锁、原jti重读、租约CAS、审计和Outbox |
| 同目录RunTokenAuthoritySchema.java；src/main/resources/run-token-authority.schema.json | 封闭身份、完整资源及许可、服务受众、当前ROLE范围及完整摘要 |
| 同目录RunTokenAuthorityConfiguration.java | 明确启用及受保护配置目录、所有者和容量；实际三文件读取 |
| business/medical-data/src/main/resources/db/migration/053-run-token-authority.sql | 原步骤两项字段及索引/CHECK、原令牌五项字段及完整关联CHECK |
| business/medical-persistence/src/test/java/com/ruoyi/medical/persistence/RunTokenAuthoritySchemaIT.java | 实际数据库字段及SQL、封闭定义、真实资料不能声明运行许可、缺少企业事务拒绝 |
| scripts/check_run_token_authority_build.py、check_run_token_authority_migration.py | 当前完整输入、JUnit、制品及原数据库身份核验 |

## 验证与证据

有效Red为`.implementation-work/run-token-authority-red.log`，SHA-256为9771a801de37ee052c8d8977b5b8926bbd7cb529c4d74bc3da13a322b5396865；实际两项断言确认原数据库缺少令牌绑定及步骤租约字段、原代码缺少封闭运行定义，失败/错误/跳过为2/0/0。原JUnit保存在`.implementation-work/run-token-authority-red.xml`。

`scripts/check_run_token_authority_build.py --check technical`当前78项通过，失败/错误/跳过均为零；其中新运行身份五项、TenantAccess六项、PermissionStructure三项、原签发与Policy/Signature共64项。回执`.implementation-work/run-token-authority-build-checks/68c1a1ad-210b-4958-9d44-f4c4ac338593/receipt.json`，SHA-256为498d41f98296ee063cdf2e1c42e914d6738ed903524d45cf36a973e5c375f70d，exit_code=0、inputs_unchanged=true；原JUnit与完整日志同目录。全部十一项SQL由真实MySQL进行服务器prepare，检查没有创建运行、jti、人员、许可或供应商结果。

053实际迁移回执`.implementation-work/run-token-authority-migration-checks/39e06b31-1d39-46e1-9c29-e2bd1b4e6da3/receipt.json`，SHA-256为799b3623a1495f495c60153763a025b29967f48b2073064108428000e310af2d。当前294项迁移，原292项ID/author/file/checksum、来源/权利/解析/索引/查询及运行主体、令牌和步骤保持。全部20项迁移结构检查通过，日志`.implementation-work/run-token-authority-migration-schema.log`，SHA-256为e6b6b9082c138e1e9385d0429cafe46cc46235842d8fb1762c9ffd786e048e0c，178张企业表、215张物理表。

`--check application`的完整应用构建十模块通过，回执`.implementation-work/run-token-authority-build-checks/066e8070-d735-4bab-ab24-681d34097262/receipt.json`，SHA-256为8a50dbb6af78d94736d5f74ba01bc93e079bbeb640fe3c346618ecc19cc7f767，exit_code=0、inputs_unchanged=true，988项当前输入已经重新读取一致。当前Jar SHA-256为b49e1ea83e5890f0f537dba20cd38136dace68782768971c45521bd6720e313f。配置与实际部署由[RUN-AUTHORITY-LOCAL-CONFIG-01](RUN-AUTHORITY-LOCAL-CONFIG-01.md)及[IMP-36](IMP-36.md)维护。

运行回执`.implementation-work/source-processing-runtime-checks/e2f15653-3b04-4a61-83d6-70d778971e20/receipt.json`，SHA-256为5b768e97392d84536ec45662275dff35c41ac7d4966dd52abeca711815a4d5fa。当前PID45581、部署530d8708-5360-424a-b33b-c05422a143ed，218项class及76份Java源码与当前Jar逐字节核查通过；真实受保护配置启用并由应用启动读取，原公开五项未认证请求继续401。业务消费者、来源权利审批及客户运行均未启用，全部合法业务成功及恢复场景继续PENDING。

## 关联影响

IMP-04维护身份主任务，IMP-13维护持久调度，IMP-09消费内部检索身份，IMP-07维护模型调用与终态结算。索引登记本记录的稳定路径。签发配置没有默认期限、容量、发行者或受众。

## 未完成范围

真实运行创建、正式ROLE范围发布、固定依赖检查、内部HTTP与mTLS接入、RunActivities和实际消费者、原终态回执仍需实施；完整两企业成功/拒绝配对、续租竞争、撤权、自然到期及服务重启恢复需要获授权实际运行。配置及密码组件通过不能单独完成BE-04或BE-13。
