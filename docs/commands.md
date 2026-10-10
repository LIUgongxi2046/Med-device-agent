# 实施检查命令

当前Agent发布范围使用同一`check_agent_release_manifest.py --store-only`定向入口，原62157已经取得成功终态。20261008-RELEASE-SCOPE-GREEN-02四项通过，Maven40.335秒、JUnit8.801秒，回执af44330b4da968ecd642259f8a5e9da7355313d130bcc879be4d0b1735dd7c51、inputs_unchanged=true；完整五组SQL字段、企业保护和实际参考资料的范围拒绝进入当前检查，十三份class及原JUnit独立保存。当前执行没有运行句柄，不重复该执行目录；合法发布范围和完整任务采用保持未验收。当前源码的固定读取与Scope采用此回执，后面的固定读取原证据保持原输入范围。

Agent固定发布元数据的定向入口为`.lld-data-work/venv/bin/python scripts/check_agent_release_manifest.py --execution <新的执行身份> --store-only`，连接已有本地MySQL，执行AgentReleaseStoreTest并保存当前JUnit；检查上限600秒，结果仅覆盖固定元数据查询及访问拒绝。当前原93717终态0，20261008-RELEASE-STORE-GREEN-02两项通过，回执摘要28d8eae02b1c0e347c04f99bca920036f6c010d9f634f0f1d9b57d301cc5d2b6，inputs_unchanged=true，七份class已独立保存。该执行目录不得复用；源码没有变化时不重复同一检查。完整发布、真实评测判定和任务准入继续由IMP-12维护，未取得完整业务通过。

当前计划页面原3600已经取得成功终态：`check_task_plan_frontend.py --execution 20261008-PLAN-FRONT-GREEN-03`完成medical严格类型和五项实际Vite runnerImport/Ajv检查，并正常关闭环境。回执摘要ac3cac6a873ea28e815cc987d88c8a05246a17511683f7d1d68c1bf3eafa95b0、inputs_unchanged=true；此目录已经存在，不重复使用。完整合法业务保存、人工计划决定、任务创建及新制品部署继续原范围。

任务计划保存与读取使用`.lld-data-work/venv/bin/python scripts/check_task_plan_write.py --execution <new-execution>`；当前PLAN-CURRENT-GREEN-01原18949终态0，42项通过，包含公开入口、完整PlanView、实际ownerGrants列与主体拒绝、原计划图及完整输入。共享语言使用`check_shared_contracts.py --execution <new-execution>`，当前PLAN-BASIS-SHARED-01原3671终态0，Python十二项和TypeScript严格类型/三十项通过，305项定义及318项引用保持原完整集合。页面代码使用`check_task_plan_frontend.py --execution <new-execution>`执行strict类型与实际Vite Schema加载/转换；服务业务验收及部署分别按IMP-12/06完整范围执行。本批不启动全量构建或重复部署。

按用户最新要求优先完成全部开发，针对性检查随对应功能执行，完整构建/部署/验收集中执行。当前完整Agent清单使用`check_agent_release_manifest.py --execution 20261008-AGENT-MANIFEST-GREEN-02`，原78105成功终止，28项清单和19项Java原共享检查通过；`check_shared_contracts.py --execution 20261008-AGENT-MANIFEST-LANGUAGES-01`原90855的Python12项、TypeScript严格类型和30项通过。原回执及18份class由唯一IMP-12维护，独立证明为AGENT-MANIFEST-20261008-evidence.json；这些目录已经存在，后续只在新代码或实际输入变化需要复验时使用新execution。CURRENT-RUN-TOKEN-POLICY-BUILD-FINAL-01原5922已经失败终止，466项中一项来源上传恢复失败，没有新应用Jar或完整capture回执，不重复该全量检查。

当前受保护Policy使用`check_run_token_policy.py --execution 20261008-CURRENT-RUN-TOKEN-POLICY-GREEN-01`，原27288的18项配置与42项签名检查通过。`check_service_certificate.py --execution 20261008-CURRENT-RUN-TOKEN-POLICY-IDENTITY-01 --scope identity`原9114的141项完整身份关联检查通过，实际TLS/Servlet与原信任配置保持。`verify_service_certificate_evidence.py`核验实际22份公开证书/CRL，`.implementation-work/verify-current-run-token-policy.py`核验当前源码、原Red/Green与完整关联字节；证据为CURRENT-RUN-TOKEN-POLICY-20261008-final-evidence.json，摘要c7179361d93c0703af2b6c5f9916beffdf039e65c9774740663fe0ac688345a3。目录已经存在，后续执行必须使用全新execution；当前完整构建与新部署继续验证，原448项结果保持其自身源码范围。

任务详情原检查使用`check_framework_navigation.py --login-entry --browser --execution 20261008-TASK-DETAIL-SPRING-ENTRY-GREEN-02`与`check_task_list.py --execution 20261008-TASK-DETAIL-SIMULATION-GREEN-02 --browser --frontend-directory .implementation-work/IC-12-TASK-DETAIL-BROWSER-20261008-PARALLEL-GREEN-02/production`，两项均成功终止且inputs_unchanged=true。独立复核输出为TASK-DETAIL-ENTRY-20261008-final-evidence.json，原65项JUnit、35项浏览器与231份静态字节由IMP-06/12维护。`check_migration.py --check build --execution 20261008-TASK-DETAIL-BUILD-FINAL-01`原14282已取得失败终态，完整构建没有成功回执，新制品尚未部署。原目录与独立证明已经存在，不重复使用。

共享许可身份的实际Red使用`check_upload_io.py --execution 20261008-UPLOAD-PERMIT-DEADLINE-RED-01 --test TransferConcurrencyTest`，原53781取得有效失败终态，17项中两项断言失败。当前Green命令为`check_upload_io.py --execution 20261008-UPLOAD-PERMIT-DEADLINE-GREEN-01 --test TransferConcurrencyTest --test ServletUploadInputTest --test IncomingUploadPartTest`，正常审批后的原76636已取得exit0终态；73项检查全部通过，原32次停止发送期限保留，inputs_unchanged=true，回执c84ec566edc1d3e9942ae38bc483d712eeb3adba22ad5c0fd5e98adf432e7e4e。三份JUnit独立保存，字节证明为UPLOAD-PERMIT-DEADLINE-20261008-green-evidence.json，原目录不可复用。

签名组件使用`check_run_token_signature.py --execution 20261008-RUN-TOKEN-SIGNATURE-GREEN-02`，原84973成功终止，42项实际RSA/JUnit通过，回执f4a4d11a6a9b62a52883ea1785ad6feae57b41d1a54840c380f37a498e37c949，独立证明为RUN-TOKEN-SIGNATURE-20261008-final-evidence.json。完整命令`check_migration.py --check build --execution 20261008-RUN-TOKEN-UPLOAD-TASK-PLAN-BUILD-FINAL-02`原8916成功终止，448项Java和前端检查通过，回执4b97faee4cd5c021e00db150b5e57a1bf1f6bee2460bb82a91b3164da6f6781d；均inputs_unchanged=true。

实际036迁移使用`check_task_plan_migration.py --execution 20261008-TASK-PLAN-RELATIONS-MIGRATE-01`，原71412的完整备份及249项登记通过；独立`check_database_structure.py --execution 20261008-ACTUAL-03`原88064的十二张表结构核验通过。当前部署命令`check_application_deployment.py --execution 20261008-RUN-TOKEN-TASK-PLAN-FINAL-01 --build-receipt .implementation-work/IC-03-JAVA-20261008-RUN-TOKEN-UPLOAD-TASK-PLAN-BUILD-FINAL-02/receipt.json`原97044成功终止，回执4ed943874ae25ef7271fd8a25b93a940ff1eaa3afc8ebba483698eb94c4822ec。`check_operations.py --execution 20261008-RUN-TOKEN-TASK-PLAN-RUNTIME-01`原77631的六项实际原操作通过，回执fae125e12cbd9e58599fdf8f97a431e68c4f12fbd1960bff6b20664445f6b868；均inputs_unchanged=true。当前记录入口为`check_inputs.py --execution 20261008-RUN-TOKEN-TASK-PLAN-RECORDS-FINAL-01`，组合入口为`.implementation-work/verify-run-token-task-plan-release.py`，结果读取实际文件。已经存在的执行目录不重复使用。

Temporal实际本地服务由`local_temporal.py`维护，当前使用明确17233/17234/17235端口与medical-local Namespace。`check_temporal_development.py --execution 20261008-PERSISTENT-SERVICE-GREEN-01`实际核验原服务身份、正常关闭、原SQLite完整性及同一Namespace持久重读，回执26ae2191420acfbb6d4e148cc6e3dda43437064f9774b19f959d9e99bb8d7cab通过且inputs_unchanged=true。当前状态使用`.implementation-work/document-venv/bin/python scripts/local_temporal.py status`读取，关闭使用同一脚本的`stop`；关闭后启动使用`start --port 17233 --http-port 17234 --metrics-port 17235 --namespace medical-local`，已有活动记录时启动拒绝。唯一IMP-13维护本地范围，完整Workflow、Worker和生产兼容继续原验收。

当前记录检查覆盖docs/development中的全部实际IMP主记录，每次execution使用尚不存在的目录；原始证据使用项目`.implementation-work`保存。以下命令和回执只证明各自原执行输入及部署范围。

## 原执行命令与验证范围

当前完整发布使用`check_migration.py --check build --execution 20261008-CURRENT-TRUST-SOURCE-UPLOAD-BUILD-FINAL-01`，403项Java与前端strict/生产构建通过；`check_application_deployment.py --execution 20261008-CURRENT-TRUST-SOURCE-UPLOAD-FINAL-01 --build-receipt .implementation-work/IC-03-JAVA-20261008-CURRENT-TRUST-SOURCE-UPLOAD-BUILD-FINAL-01/receipt.json`核验PID86534、229份静态字节、七项入口及上传刷新。`check_operations.py --execution 20261008-CURRENT-TRUST-SOURCE-UPLOAD-RUNTIME-01`六项实际检查通过。记录入口为`check_inputs.py --execution 20261008-CURRENT-TRUST-SOURCE-UPLOAD-RECORDS-FINAL-01`；组合入口为`.implementation-work/verify-current-trust-upload-release.py`，两项结果读取实际文件。全部入口使用项目document-venv/bin/python和项目中间目录，已存在的执行目录不能复用。下方命令只证明各自原输入和部署范围。

当前源码的信任配置检查使用`check_service_certificate.py --scope identity --execution 20261008-CURRENT-TRUST-GREEN-02`，81项关联检查通过；独立复核使用`verify_service_certificate_evidence.py --receipt .implementation-work/IC-04-SERVICE-CERTIFICATE-20261008-CURRENT-TRUST-GREEN-02/receipt.json --output .implementation-work/CURRENT-SERVICE-TRUST-20261008-final-evidence.json`，实际结果PASS。上传前端使用`check_source_upload_browser.py --execution 20261008-PARALLEL-GREEN-03`，十三项实际浏览器检查与构建通过；独立字节检查入口`.implementation-work/verify-source-upload-browser.py`已生成SOURCE-UPLOAD-BROWSER-20261008-final-evidence.json。Spring入口使用`check_framework_navigation.py --login-entry --browser --execution 20261008-SOURCE-UPLOAD-SPRING-ENTRY-GREEN-01`，三项Java/五项DOM与229份字节通过。以上命令使用项目document-venv/bin/python，所有执行目录均已存在，不重复使用。完整发布固定于CURRENT-TRUST-SOURCE-UPLOAD-BUILD-FINAL-01，结果取得后由IMP-06维护；下方原402项制品仍是实际PID74481当前部署，新源码尚未部署。

当前完整构建使用`.implementation-work/document-venv/bin/python scripts/check_migration.py --check build --execution 20261008-SERVICE-CERTIFICATE-TEMPLATE-BUILD-FINAL-01`，402项Java、前端strict及生产构建通过。`check_application_deployment.py --execution 20261008-SERVICE-CERTIFICATE-TEMPLATE-FINAL-01 --build-receipt .implementation-work/IC-03-JAVA-20261008-SERVICE-CERTIFICATE-TEMPLATE-BUILD-FINAL-01/receipt.json`核验PID74481、225份HTTP静态字节和六项入口；`check_operations.py --execution 20261008-SERVICE-CERTIFICATE-TEMPLATE-RUNTIME-01`六项原操作通过，inputs_unchanged=true。记录使用`check_inputs.py --execution 20261008-SERVICE-CERTIFICATE-TEMPLATE-RECORDS-FINAL-01`，包含IMP-11当前记录；组合复核使用`.implementation-work/document-venv/bin/python .implementation-work/verify-service-template-release.py`，输出SERVICE-CERTIFICATE-TEMPLATE-20261008-release-evidence.json，结果读取实际文件。各入口使用同一项目Python环境，TMPDIR保持项目目录；实际已执行目录不能重复使用。

独立服务证书检查为`check_service_certificate.py --execution 20261008-IDENTITY-FINAL-01 --scope identity`，80项身份/Schema及实际TLS/Servlet检查通过，回执dfae94374e051b6b42d135cebc10ca17066273830e8cc2839efc968cede3467e。独立SERVICE-CERTIFICATE-20261008-final-evidence.json证明其原503项输入与22份公开证书/CRL，关联Spring路径变化后保留原范围；当前两项证书检查由上述402项完整构建重新执行。依赖固定于scripts/requirements-native-security.txt，唯一IMP-04维护组件，运行令牌与完整Worker继续原要求；下方集合保留原输入及部署。

当前租约独立检查使用`.implementation-work/document-venv/bin/python scripts/check_source_upload_http.py --scope completion --execution 20261008-SOURCE-COMPLETION-LEASE-GREEN-03`，实际HTTP检查通过。完整受影响回归使用`.implementation-work/document-venv/bin/python scripts/check_migration.py --check build --execution 20261008-SOURCE-COMPLETION-LEASE-BUILD-FINAL-01`，400项Java、前端strict及生产构建通过，原61088会话成功终止、inputs_unchanged=true。原local_runtime正常关闭原应用并启动当前PID61813不可变制品；`check_application_deployment.py --execution 20261008-SOURCE-COMPLETION-LEASE-FINAL-01 --build-receipt .implementation-work/IC-03-JAVA-20261008-SOURCE-COMPLETION-LEASE-BUILD-FINAL-01/receipt.json`核验222份HTTP静态字节和五项入口，`check_operations.py --execution 20261008-SOURCE-COMPLETION-LEASE-RUNTIME-01`六项原操作通过。记录入口为`check_inputs.py --execution 20261008-SOURCE-COMPLETION-LEASE-RECORDS-FINAL-01`；九份字节组合使用`.implementation-work/verify-source-part.py`，输出SOURCE-COMPLETION-LEASE-20261008-final-evidence.json，实际结果读取各项文件。唯一IMP-08维护租约及完整剩余业务，唯一IMP-06维护运行身份；Worker身份与最终来源结果继续原要求，下方集合保持原输入范围。

来源持久投递检查使用`.implementation-work/document-venv/bin/python scripts/check_source_upload_http.py --scope completion --execution 20261008-SOURCE-COMPLETION-DELIVERY-GREEN-01`，实际HTTP检查通过；当前`check_migration.py --check build --execution 20261008-SOURCE-COMPLETION-DELIVERY-BUILD-FINAL-01`完整400项Java、前端strict及生产构建通过。`check_application_deployment.py --execution 20261008-SOURCE-COMPLETION-DELIVERY-FINAL-01 --build-receipt .implementation-work/IC-03-JAVA-20261008-SOURCE-COMPLETION-DELIVERY-BUILD-FINAL-01/receipt.json`与`check_operations.py --execution 20261008-SOURCE-COMPLETION-DELIVERY-RUNTIME-01`分别通过当前本地制品及六项原操作。当前记录检查使用`check_inputs.py --execution 20261008-SOURCE-COMPLETION-DELIVERY-RECORDS-FINAL-01`；九份字节组合使用`.implementation-work/verify-source-part.py`，输出SOURCE-COMPLETION-DELIVERY-20261008-final-evidence.json，各项结果以实际文件为准。下方较早构建、部署及组合只保持原输入范围；唯一IMP-08维护原事件/消费者投递和剩余Worker业务，完整状态保持未验收。

## 当前公开来源上传与分段验证

当前C09正式扫描与解析调用使用`.implementation-work/document-venv/bin/python scripts/check_source_scan_native.py --execution <unique_execution_id> --installed`。五项实际扫描/解析检查和十二项完整七格式回归分别由SOURCE-SCAN-PARSER-GREEN-01及SOURCE-SCAN-PARSER-REGRESSION-01通过，当前安装制品包含NativeParser.scan_and_parse和ClamAVScanner，固定发布文件在处理前后核验。原始扫描及三个官方数字签名输出随scan-parser-result.json保存摘要；配置与原件必须来自登记输入。当前完整记录入口为IC-02-LOCAL-20261008-SOURCE-COMPLETION-SCANNER-RECORDS-FINAL-01，九份字节组合入口为SOURCE-COMPLETION-SCANNER-20261008-final-evidence.json，实际结果读取唯一IMP-08。Worker及完整公开接收、持久化、来源版本、权利版本、页面和全部后续业务继续必需验收。

当前SOURCE-COMPLETION-COMMAND-BUILD-FINAL-01完整400项Java、前端strict及生产构建通过，十九项来源输入、六项来源HTTP、70项共享接收和10项缓存/当前身份检查进入同一构建；十七项运行JSON及完整JUnit独立保存。当前制品48c61f2e71e7d959683451548ad7c7d6b41bcd856e85809ef0ac73d997125d2e已在PID47930运行，222份静态HTTP字节、五项入口和六项原操作核验通过。独立完成命令检查使用`check_source_upload_http.py --scope completion`，完整来源检查使用默认`--scope all`；公开202只证明原处理命令已保存，Worker、扫描、格式、去重和最终来源状态继续完整验收。完整构建、部署和原操作分别使用check_migration.py、check_application_deployment.py及check_operations.py，各自execution由唯一IMP-08/06维护。记录检查入口为IC-02-LOCAL-20261008-SOURCE-COMPLETION-COMMAND-RECORDS-FINAL-01，当前字节核验入口为SOURCE-COMPLETION-COMMAND-20261008-final-evidence.json，以实际结果为准；下方命令结果只保持各自原输入范围。

当前SQL-CACHE-BUILD-FINAL-01完整390项Java与前端构建通过并已部署；SourceUploadHttpTest.rejectsExhaustedConfiguredCapacityWithoutWaitingForExistingInput参数化验证企业和用户容量，使用独立Redis命名空间。一项企业/两项用户/20秒验证企业耗尽；原LOCAL四项企业/两项用户/60秒验证同一用户两份输入占用名额且企业有余量。另一上传三秒内429，释放后相同原键/原操作ID继续，固定对象、全部字节、各一项审计/Outbox及私有清理共同验证。停止输入期限由expiresStoppedActualInputAndResumesTheOriginalOperation保持原30秒配置验证。十六项运行JSON与完整JUnit进入当前构建归档，包含70项共享接收和10项解析缓存/当前身份检查。唯一IMP-04/08/06维护共享缓存、业务和本地部署；多人企业容量、持续输入期限、排队取消及完整KN-02继续必需验收。

当前WRITING恢复检查使用已核验的Toxiproxy2.12.0对本次测试拥有的实际S3响应连接控制中断，正式接口自然产生原WRITING/PROCESSING记录，恢复后同键重新核验原对象。原操作、write_id、地址、版本、全量字节、进度重读、异参拒绝、原响应和私有清理共同验证；测试不修改原常驻服务或原秘密。完整SOURCE-PART-WRITING-BUILD-FINAL-01通过352项Java与前端构建，新制品已由原本地部署入口运行，部署与六项原操作验证由唯一IMP-06维护。下方完整恢复待办指全部WRITING条件、名额/期限、DOCUMENT_BODY共享消费及后续KN-03/04/页面；独立HTTP、完整构建和实际部署均使用各自唯一execution并核验原始回执。

唯一[IMP-08](development/IMP-08.md)维护BACK-SOURCE-UPLOAD-01。输入检查使用已登记官方原件元数据及获准SIMULATION业务标识，独立保存SourceUploadSchemaTest原始JUnit。HTTP检查连接实际本地账户、MySQL、Redis、完整应用及原S3服务，核查创建/原声明读取、分段Idempotency-Key、完整原响应字节、原操作查询、跨上传键冲突、缺失及非法键、各一项审计/Outbox、原件固定对象版本、进度和权限/水位/CSRF/期限；接收中的并发重放返回202，已完成操作的并发重放返回200及固定原响应；实际连接终止后清理文件、保留原操作并允许同键继续，在途撤权拒绝且不产生对象。本次生成数据库及对象版本清理，JUnit必须由当前执行产生。WRITING未知结果与完整恢复、全部名额/期限条件、DOCUMENT_BODY共享消费、KN-03处理、KN-04四项状态及页面继续实施。

当前公开HTTP已经覆盖接收中重复202及原操作查询、客户端取消后清理与相同原键继续、在途撤权403及零S3对象。共享取消检查使用下列入口，覆盖真实客户端保持连接时取消服务端处理、私有文件和原名额清理；源码变化后继续执行完整build及实际部署检查。每次execution使用尚不存在的目录名称，所有结果按当前回执和输入校验和核验。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution <unique_execution_id> --test ServletUploadInputTest --test IncomingUploadPartTest --test TransferConcurrencyTest
```

```bash
.lld-data-work/venv/bin/python scripts/check_source_upload_schema.py --execution <unique_execution_id>
.lld-data-work/venv/bin/python scripts/check_source_upload_http.py --execution <unique_execution_id>
```

## 当前七格式解析制品验证

唯一[IMP-08](development/IMP-08.md)维护DOCUMENT-NATIVE-PARSE-01。正式安装检查比较每个Python文件与当前源码，再处理登记原件和获准SIMULATION任务文件；五个DOCX表格逐项保持原正文顺序，固定Locator与原文摘要保持。当前DOCX-ORDER-PACKAGE-FINAL-01十二项全部通过，失败、错误及跳过为零；完整公开来源上传、Worker提交、持久化与阅读页面继续实施。

```bash
.implementation-work/document-venv/bin/python scripts/check_document_native.py --execution <unique_execution_id> --installed
```

当前八份回执的完整字节证明为`.implementation-work/DOCX-ORDER-20261007-associated-evidence.json`，关联共享与十二项解析制品证明为`.implementation-work/DOCX-ORDER-20261007-shared-evidence.json`；原始回执和摘要读取IMP-05/06/08。该证明没有执行模型、专业核准或完整客户业务验收。

## 当前完整构建与本地部署验证

唯一IMP-06维护完整构建、原框架生命周期、静态发布及当前部署；IMP-05维护原对象服务恢复和固定原件读取。构建、运行、业务与目标部署验收分别登记；已有常驻应用必须先确认运行身份，不能覆盖运行进程或使用旧Jar代表当前源码。所有中间文件使用项目`.implementation-work`，秘密通过原受保护文件读取。

```bash
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution <unique_execution_id>
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_application_deployment.py --execution <unique_execution_id> --build-receipt .implementation-work/IC-03-JAVA-<build_execution_id>/receipt.json
.lld-data-work/venv/bin/python scripts/check_operations.py --execution <unique_execution_id>
```

当前完整构建CURRENT-APPLICATION-RESTORE-NATIVE-03通过339项Java检查；部署RESTORE-FINAL-01核验同一源码、构建回执、不可变Jar、真实PID及222份静态HTTP字节，APPLICATION-RESTORE-RUNTIME-01通过六项原操作检查。当前关联集合CURRENT-APPLICATION-RESTORE-SHARED-02通过165项Java、131项Chrome及29个同源入口，原JUnit副本保存在独立目录。八份回执的当前字节证明引用IMP-06，customer_success_verified=false。完整资料上传、任务创建、模型生成、专业批准、导出及生产交接继续各自原要求。

## 虚拟企业的岗位、项目与任务业务验证

用户授权的企业、成员岗位、项目和任务使用 SIMULATION 数据；检查连接实际本地 MySQL、Redis 与完整 MedicalApplication，使用本人账户、Cookie、CSRF 和正式接口。检查结束清理本次生成的企业数据，保留既有记录。Chrome 检查操作实际 DOM，使用端口18083、18084；开发与生产模式分别运行。岗位和项目主记录读取 IMP-04，任务主记录读取 IMP-12。

```bash
.lld-data-work/venv/bin/python scripts/check_project_simulation.py --execution <unique_execution_id> --browser
.lld-data-work/venv/bin/python scripts/check_task_list.py --execution <unique_execution_id> --browser
.lld-data-work/venv/bin/python scripts/check_role_simulation.py --execution <unique_execution_id> --browser
.lld-data-work/venv/bin/python scripts/check_member_simulation.py --execution <unique_execution_id>
.lld-data-work/venv/bin/python scripts/check_member_simulation.py --execution <unique_execution_id> --browser
```

浏览器检查执行 medical 严格类型检查及 Vite 生产构建。`--frontend-directory` 可以引用项目 `.implementation-work` 内与当前源码对应的完整构建，制品字节进入检查输入。岗位检查验证发布、历史读取、修订、撤销、版本冲突、原请求重放及保存后的连接中断恢复；项目检查验证创建、属性修订、四种状态决定、原请求重放、版本冲突、撤权及审计；任务检查验证授权数量、过滤、分页、固定水位、详情、缺项、刷新与会话失效。全部回执保存于 `.implementation-work`，customer_success_verified 保持 false。

成员 HTTP 检查使用原框架中明确登记的 SIMULATION 方法权限，通过 LOCAL-02 正式创建三个账户，并由实际人员账户登录、接受邀请和读取授权资源。检查覆盖原批准版本、空动作岗位、原请求重放、独立授权授予及撤销、岗位撤销对授权与待接受邀请的传播、成员停用、当前上下文失效及凭据加密。检查结束清理本次生成的账户、企业、框架角色和菜单关联，保留原记录；邀请凭据和密码只保存在检查内存，公共证据没有秘密。

成员命令的`--browser`为开发与生产模式分别创建两个正式账户：一个接受预先通过正式接口建立的邀请，另一个接受管理页面建立的邀请。各十一项本人接受检查覆盖必需输入、未保存离开、错误凭据、提交后中断、刷新及原键重放、成员重读、企业选择恢复、实际消费者、普通成员页面拒绝和断网清理。管理人员明确选择企业后，每个模式另执行八项管理读取和十二项完整写入检查；邀请、独立授予、撤销和停用都连接正式接口，保存后核查当前会话、原操作及目标；NOT_FOUND保留原键，异参不提交，实际写入后中断通过原键恢复，邀请凭据不进入持久存储，新账户接受后实际项目/任务读取200。四项创建邀请、四项接受、六项权限决定与同步审计/Outbox数量一致。秘密只通过进程输入传递，检查文件不保存秘密；正式HTTP另核查完整委派、分页、容量、旧版本和越权拒绝。唯一IMP-04/06维护当前CURRENT-APPLICATION-RESTORE-SHARED-02的165项Java、131项Chrome及全部字节证明，SIMULATION保持customer_success_verified=false。

## 七格式原生解析组件验证

唯一 IMP-08 维护 C09 原生解析组件。正式 Python 制品安装于 `.implementation-work/document-venv`，Docling 与原生格式库按照 `document/pyproject.toml` 安装。检查使用登记原件及标注 SIMULATION 的 CSV、XLSX、PPTX 文件，不执行模型下载。

```bash
.implementation-work/document-venv/bin/python scripts/check_document_native.py --execution <unique_execution_id> --installed
```

检查核查安装文件与当前源码的字节、原文及七格式 Locator、固定 parse_version、参数清单与实际依赖版本；上传、Worker 提交、持久化及完整原件阅读分别按 IMP-08 验证。

## 原登录页面的同源发布检查

唯一docs/development/IMP-06.md维护原RuoYi登录组件、Spring MVC入口、正式构建及当前原权限/服务回归。检查使用真实MySQL、Redis、本机账户和Chrome DOM，固定临时端口18085需要空闲；不建立客户企业或项目。完整构建由scripts/build_business.py执行medical类型检查、生产构建及原clean verify，完整成功与常驻部署分别验证。

```bash
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --login-entry --browser --execution 20261006-NATIVE-LOGIN-ENTRY-GREEN-02
.lld-data-work/venv/bin/python scripts/check_inputs.py --execution 20261006-NATIVE-LOGIN-ENTRY-INPUTS-FINAL-01
```

重新执行使用新的唯一execution。第一条检查覆盖三项实际Servlet、四组实际Chrome及原管理方法权限和十三项Service，结果读取对应receipt.json。DeepSeek密钥配置读取docs/deepseek-configuration.md；本地check已返回configured=true。

## 原管理请求与完整关联检查

当前原请求、会话与管理API由唯一docs/development/IMP-06.md维护。检查使用真实本人账户、完整MedicalApplication、MySQL/Redis和Chrome DOM；开发及生产预览调用Vite原关闭方法，检查不表示常驻发布或客户验收。

```bash
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --complete --browser --password-browser --execution 20261006-RUOYI-REQUEST-GREEN-03
.lld-data-work/venv/bin/python scripts/check_inputs.py --execution 20261006-RUOYI-REQUEST-INPUTS-FINAL-01
```

复验使用新的唯一execution，当前原始输出、输入与产物读取对应回执。

## 原个人信息页面及关联验证

唯一docs/development/IMP-06.md维护原system/user/profile/index、userInfo、resetPwd及LOCAL-07的源码和实际证据。该检查使用真实本人资料，保存昵称、手机、邮箱、性别，重新读取原用户；检查原请求跨刷新恢复、原响应重放、并发旧版本、主体变化及完整密码与框架回归。头像上传与全部管理模块继续按原计划验收，临时应用及前端关闭以后不能登记常驻发布。

```bash
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --complete --browser --password-browser --execution 20261006-RUOYI-PROFILE-GREEN-03
.lld-data-work/venv/bin/python scripts/check_inputs.py --execution 20261006-RUOYI-PROFILE-INPUTS-FINAL-01
```

每次复验使用新的唯一execution，命令结果读取对应实际回执；来源及开发记录检查绑定当前主记录、索引、计划、环境与报告。

## 原密码组件及完整关联检查

当前源码、实际环境、唯一回执及未验收范围由docs/development/IMP-06.md和IMP-12.md维护。检查连接当前源码的完整MedicalApplication、真实MySQL/Redis和本人账户，实际提交密码变化并同步保存0600的凭据文件。原操作与同步审计逐字段重读，全部实际登录绑定与当前账户版本核对；原框架、身份、模板、项目、任务及资源/来源授权同时执行。前端检查只使用Chrome DOM，开发及生产预览结束后关闭，不表示常驻发布或客户验收。

```bash
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --complete --browser --password-browser --execution 20261006-RUOYI-PASSWORD-GREEN-03
```

该执行身份已取得实际回执；复验必须使用新的唯一execution。完整clean verify、新Jar发布及全部业务仍需原计划要求的实际证据。

## 原登录页面与完整关联检查

当前源码和实际结果由唯一docs/development/IMP-06.md维护。入口运行真实MySQL、Redis、本人账户及当前源码的临时应用，检查开发与生产构建的原RuoYi登录表单、当前原权限、刷新、注销及受影响Java；临时应用在检查结束时关闭。该入口没有发布常驻制品，客户业务分别验收。

```bash
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --complete --browser --execution 20261006-RUOYI-LOGIN-GREEN-01
```

上述执行身份已取得实际回执，复验使用新的唯一execution，证据目录不能复用。

## 原账户动作与医疗身份扩展

当前源码与实际验证由唯一docs/development/IMP-04.md维护。检查使用实际本机账户、原MySQL/Redis和原Controller；真实密码写入以后同步保存0600的本人凭据文件，恢复密码后验证可登录。原平台角色与原方法权限分别核验，不创建客户账户或许可。

```bash
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --account-actions --execution 20261006-RUOYI-ACCOUNT-ACTIONS-INDEPENDENT
.lld-data-work/venv/bin/python scripts/check_framework_navigation.py --complete --execution 20261006-RUOYI-ACCOUNT-ACTIONS-FINAL-02
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

当前FINAL-02已经执行，122项通过；独立命令为后续可重复入口，没有登记该独立执行身份已经运行。完整clean verify及新部署保持原完整要求和现有容量条件。

## 原 RuoYi 管理数据库与服务

原角色Controller依赖原TokenService。现有本地启动入口通过MEDICAL_RUOYI_TOKEN_SECRET读取受保护的ruoyi-token-secret.json；首次配置使用以下命令，实际密钥内容不输出，已有密钥保持原字节。当前Cookie会话继续承担登录与即时权限核查，完整业务制品仍须原clean verify通过才能部署。

```bash
.implementation-work/object-storage-venv/bin/python scripts/local_runtime.py framework-token
.lld-data-work/venv/bin/python -m unittest discover -s tests -p test_framework_token_configuration.py -v
```

原管理结构的完整字段、默认值及索引来自`business/sql/ry_20260417.sql`，034迁移已在本地真实MySQL应用。以下检查分别绑定当前源码和实际结果；`migrate`仅用于尚未应用的本次十项新增迁移，已应用环境继续运行只读`schema`检查。原迁移执行回执、完整备份和校验读取唯一[IMP-03](development/IMP-03.md)，服务注册与关联验证读取[IMP-06](development/IMP-06.md)。

```bash
.lld-data-work/venv/bin/python scripts/check_ruoyi_management_data.py --check schema --execution <unique_execution_id>
.lld-data-work/venv/bin/python scripts/check_ruoyi_management_data.py --check build --execution <unique_execution_id>
.lld-data-work/venv/bin/python scripts/check_ruoyi_management_data.py --check mappers --execution <unique_execution_id>
python3 scripts/check_framework_navigation.py --native-services --execution <unique_execution_id>
python3 scripts/check_framework_navigation.py --method-security --execution <unique_execution_id>
python3 scripts/check_framework_navigation.py --complete --execution <unique_execution_id>
```

原Mapper检查使用Druid只读连接、原system/quartz/generator XML及实际MySQL；原Service检查启动实际MedicalApplication、执行原初始化与查询、读取实际HTTP页面并关闭临时应用。complete检查覆盖关联共享/身份/模板/项目/原方法权限/导航、原服务和Mapper；完整Java仍执行原clean verify及全部实际S3检查，关联检查不能代替完整构建或客户业务。

四种本地来源选择、固定资料集成员、TK-02、专业快照及文稿依据由唯一[IMP-12](development/IMP-12.md)维护。SourceReadAuthorityTest十二项和TaskReadTest六项采用真实MySQL；实际账户与任务HTTP使用原Spring Security/Session。完整公开创建和客户成功保持独立验收。

```bash
.lld-data-work/venv/bin/python scripts/check_task_read.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_task_read.py --check runtime --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_task_storage.py --execution <new-execution>
```

2026-10-06当前SCOPE-SELECTION-NATIVE-01完整300项、任务HTTP五项、账户三十一项、原服务五项和635项输入/8项产物字节核查通过，证据读取唯一[IMP-12](development/IMP-12.md)，原身份服务读取[IMP-04](development/IMP-04.md)。原RuoYi、MapperScan、MyBatis-Plus、企业事务及成熟JSON/Schema组件直接复用。完整保存、人工确认与客户成功尚未验收，较早集合保留原输入和产物范围。

```bash
PYTHONPATH=scripts .lld-data-work/venv/bin/python .implementation-work/task-plan-artifacts.py --native .implementation-work/IC-03-JAVA-20261006-SCOPE-SELECTION-NATIVE-01/receipt.json
```

```bash
.lld-data-work/venv/bin/python scripts/check_ruoyi_accounts.py --check password-authority --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_ruoyi_accounts.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_local_identity.py --execution <new-execution>
```

```bash
.lld-data-work/venv/bin/python scripts/check_task_plan.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_task_storage.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_source_read_authority.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-<new-execution>/evidence.json --receipt .implementation-work/IC-03-JAVA-20261006-SNAPSHOT-MANIFEST-NATIVE-02/receipt.json --output .implementation-work/FULL-REGRESSION-<new-execution>/verified-evidence.json
```

重复执行使用尚不存在的execution及输出目录；源码或产物变化后使用对应的新构建回执。当前专业快照、原框架类、共享清单和独立迁移资源核验命令为`PYTHONPATH=scripts .lld-data-work/venv/bin/python .implementation-work/task-plan-artifacts.py --native .implementation-work/IC-03-JAVA-20261006-SNAPSHOT-MANIFEST-NATIVE-02/receipt.json --execution <new-execution>`。共享语言核验使用`scripts/check_shared_contracts.py --execution <new-execution>`。账户检查更换本人密码，后续freshread受保护凭据文件。[DeepSeek配置](deepseek-configuration.md)保持已有秘密引用和configured状态。

## 已有命令与各执行集合的原始范围

本节中固定回执仅适用于各自执行集合，不能用于新源码或新产物。

2026-10-06BACK-RUOYI-DATA-SCOPE-01由唯一[IMP-04](development/IMP-04.md)维护。当前采用RUOYI-DATA-SCOPE-NATIVE-02完整275项，以及RUOYI-DATA-SCOPE-FINAL-01完整30组和31份CURRENT_EVIDENCE_BYTES证明，SHA-256为a401f022f97f8dd611b9c271f74b3981a7132b84ef693e12c927ea015bad3a93。实际本机独立登录、原DataScopeAspect、用户/部门/角色查询、内部代理与标准注销已核查；全部客户业务继续验收，以下集合只保留各自原始范围。

```bash
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_ruoyi_accounts.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-<new-execution>/evidence.json --receipt .implementation-work/IC-03-JAVA-20261006-RUOYI-DATA-SCOPE-NATIVE-02/receipt.json --output .implementation-work/FULL-REGRESSION-<new-execution>/verified-evidence.json
```

2026-10-06正式企业审查采用FRONT-ENTERPRISE-REVIEW-01，唯一主记录[IMP-06](development/IMP-06.md)。ENTERPRISE-REVIEW-FINAL-01完整30组及275项Java合计31份当前输入、产物和双服务身份核查通过，证明为`.implementation-work/FULL-REGRESSION-20261006-ENTERPRISE-REVIEW-FINAL-01/verified-evidence.json`。开发16项/生产13项会话、登记与审查、本人账户各五项及模板各七项通过；合法客户业务独立验收。较早集合按其原始输入范围读取。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-<new-execution>/evidence.json --receipt .implementation-work/IC-03-JAVA-20261006-RUOYI-NATIVE-IDENTITY-NATIVE-01/receipt.json --output .implementation-work/FULL-REGRESSION-<new-execution>/verified-evidence.json
```

账户回归会更换本人密码，后续必须重新读取受保护owner-credentials.json；不输出秘密。DeepSeek本地检查读取[配置说明](deepseek-configuration.md)，当前configured=true。

2026-10-06原框架身份接入采用BACK-RUOYI-NATIVE-IDENTITY-01，唯一主记录为[IMP-04](development/IMP-04.md)。当前275项Java、30组回归及31份当前字节和运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261006-RUOYI-NATIVE-IDENTITY-FINAL-01/verified-evidence.json`。四项框架实际检查共11.789秒，包含原账户、密码计数、原表字段及真实Redis认证对象、原SecurityUtils和业务UUID索引；不授予客户企业或原管理权限。

```bash
.lld-data-work/venv/bin/python scripts/check_ruoyi_accounts.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-<new-execution>/evidence.json --receipt .implementation-work/IC-03-JAVA-20261006-RUOYI-NATIVE-IDENTITY-NATIVE-01/receipt.json --output .implementation-work/FULL-REGRESSION-<new-execution>/verified-evidence.json
```

命令读取当前本机受保护凭据，不输出密码或会话Cookie。账户回归会更换本人密码，后续读取当前凭据文件。完整管理模块、全部客户业务、非功能、安全及批准部署与接收继续按原计划执行；旧证据仅适用于对应原始输入和制品。

2026-10-06当前正式企业登记、会话、账户、模板及共享依赖使用ENTERPRISE-REGISTRATION-FINAL-01完整30组回归；273项Java回执及30组共31份当前输入、产物与运行身份核查通过，证明保存于`.implementation-work/FULL-REGRESSION-20261005-ENTERPRISE-REGISTRATION-FINAL-01/verified-evidence.json`。正式登记与全部当前检查范围读取唯一[IMP-06](development/IMP-06.md)，较早集合按各自原始范围读取。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution <new-execution>
```

该入口复用既有执行组件，运行strict、生产构建及真实开发13项/生产10项DOM；企业登记新增完整必需输入、有效账户明确选择、授权声明、未保存离开、刷新与连接中断清理检查。实际MySQL申请、申请审计、审查决定、企业、成员、项目和操作前后计数绑定回执。完整客户保存、跨入口原请求重放、清除、并发、撤权及后续业务仍使用原业务验收。

当前本地应用保持TEMPLATE-APPROVAL-NATIVE-02，273项Java检查及30组完整本地回归TEMPLATE-CREATE-UI-FINAL-01通过，31份当前回执的输入、产物和运行身份核查通过。证明读取`.implementation-work/FULL-REGRESSION-20261005-TEMPLATE-CREATE-UI-FINAL-01/verified-evidence.json`；PID88755及222项实际迁移的身份由IMP-04/11和环境登记维护。原UserDetailsServiceImpl、账户/密码/权限服务、完整模板Schema、正式创建和审查页面及全部已登记回归使用以下入口；每次execution使用全新名称，运行证据按对应回执的源码与制品范围读取。

```bash
.lld-data-work/venv/bin/python scripts/check_ruoyi_accounts.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_local_identity.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_template_storage.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_template_runtime.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_template_approval.py --check schema --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_template_approval_browser.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_shared_contracts.py --execution <new-execution>
```

check_ruoyi_accounts使用当前真实所有者核查唯一框架账户存储、原UUID、真实登录、原Redis失败计数及成功清除，并将真实数据库的sys_menu/sys_role_menu完整字段及类型与原RuoYi SQL逐项比较。SQL语句选择采用固定sqlparse0.6.0，结构校验使用原SQLGlot。check_template_runtime核查TP-01、TP-02和审查Preview的真实企业拒绝、必需Header、查询参数、CSRF/Origin与拒绝后没有业务写入；完整客户成功采用原业务验收。check_template_approval --check schema执行四项实际Schema检查；--check migration-build使用原Maven构建完整迁移制品；--check migrate保存独立UUID备份并调用原Liquibase，只在需要应用追加迁移时执行。当前031已经应用，不能重复迁移来刷新证据。check_shared_contracts执行Python11项、TypeScript类型检查及29项完整检查，绑定全部七份原Schema、源码、测试与锁文件。check_full_regression顺序执行30组现有本地检查；客户业务、性能、安全及正式接收采用原完整验收，本地回归只证明已执行的对应行为。其余集合按各自原始范围读取。

check_template_approval_browser执行严格vue-tsc及生产构建，使用当前本机所有者在开发和生产入口各执行七项检查，包含模板审查入口、深链拒绝、审查刷新、模板创建入口及刷新、创建动作权限和注销。真实MySQL重读确认没有企业、成员、项目、模板、决定、操作及事件写入；客户合法保存、核准、重放、撤权、中断恢复和专业管理交互仍需原完整业务验收，没有视觉验证。当前完整已登记回归包含该套，共30组。

inspect_current_evidence使用对应完整回归evidence.json和完整Native receipt.json，逐项核查当前输入、产物、应用及对象服务身份，输出目录限定为项目的.implementation-work，输出文件必须尚未存在。

```bash
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-<new-execution>/evidence.json --receipt .implementation-work/IC-03-JAVA-<native-execution>/receipt.json --output .implementation-work/FULL-REGRESSION-<new-execution>/verified-evidence.json
```

当前完整制品及运行采用TEMPLATE-DEFINITION-NATIVE-01/RUNTIME-01，256项原生及六项实际HTTP通过；PID58397与不可变制品、357/282项输入及8/1项产物SHA相符，唯一当前证据读取IMP-11。check_template_directory默认执行目录四项和模板定义九项，--test可选择对应测试类；execution必须使用全新名称。

```bash
.lld-data-work/venv/bin/python scripts/check_template_directory.py --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution <new-execution>
.lld-data-work/venv/bin/python scripts/check_operations.py --execution <new-execution>
```

目录检查的真实来源用途为LOCAL_PARSER_CHECK，当前资源REFERENCE_ONLY；模板核准、正式业务、性能及接收检查继续使用原完整验收。以下集合保留各自原始执行范围。

## IMP-05 Servlet接收与当前应用

UPLOAD-SERVLET-INPUT-NATIVE-01完整240项原生及RUNTIME-01六项实际HTTP通过，当前PID47704与不可变制品已经核查；341/275项输入和8/1项产物SHA相等，原始断言及运行身份读取唯一IMP-05。独立HTTP-05十二项通过，GREEN-03保存原78项及十一项Servlet的执行范围；当前完整源码和产物采用NATIVE-01。实际技术检查只使用原服务、D0和本次私有资源，不能登记客户业务PASS或批准阈值。以下execution已经使用，后续采用新名称；观察超时继续读取原handle。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-SERVLET-INPUT-HTTP-05 --test ServletUploadInputTest
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-SERVLET-INPUT-GREEN-03
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-SERVLET-INPUT-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-SERVLET-INPUT-RUNTIME-01
.lld-data-work/venv/bin/python .implementation-work/version-authority-evidence/check_bytes.py .implementation-work/IC-03-JAVA-20261005-UPLOAD-SERVLET-INPUT-NATIVE-01/receipt.json .implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-SERVLET-INPUT-RUNTIME-01/receipt.json
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

检查runner的--test只选择明确的实际JUnit集合；完整工程与业务验收仍采用各自全部必需检查，不能以部分集合代替完整通过。以下各集合保留原始输入和运行范围。

## IMP-05 对象请求截止与当前应用

UPLOAD-OBJECT-DEADLINE-GREEN-02独立67项上传工程、NATIVE-02完整228项原生及RUNTIME-01六项实际HTTP通过，当前PID43011与不可变制品已经核查；334/274项输入及8/1项产物SHA全部相等，唯一当前证据读取IMP-05。实际故障检查核查没有客户上传记录后暂停原对象服务，必须采用原进程身份和恢复保护；这些工程参数不能登记生产批准。以下execution已经使用，后续采用全新名称，不能仅因观察超时重启或重复检查。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-OBJECT-DEADLINE-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-OBJECT-DEADLINE-NATIVE-02
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-OBJECT-DEADLINE-RUNTIME-01
.lld-data-work/venv/bin/python .implementation-work/version-authority-evidence/check_bytes.py .implementation-work/IC-03-JAVA-20261005-UPLOAD-OBJECT-DEADLINE-NATIVE-02/receipt.json .implementation-work/IC-05-OPERATIONS-20261005-UPLOAD-OBJECT-DEADLINE-RUNTIME-01/receipt.json
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

stop-application取得终态后才启动application，保留本地独立账户。浏览器输入主动终止、完整配置发布、公开/内部上传、专业正文与Artifact登记及全部客户业务继续按原要求实施；以下集合仅保留各自原始范围。

## IMP-05 上传恢复与完成的传输控制

UPLOAD-COMPLETION-CONCURRENCY-GREEN-01实际63项上传工程、NATIVE-01完整224项原生及RUNTIME-01六项实际HTTP回归通过。当前PID39752、不可变制品及全部输入/产物SHA已经核查，唯一当前证据读取IMP-05。以下execution已经使用，后续使用全新名称；全部公开入口、网络主动终止、正式配置/批准和客户业务继续实施。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-COMPLETION-CONCURRENCY-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-COMPLETION-CONCURRENCY-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-COMPLETION-CONCURRENCY-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

## IMP-05 共享传输名额与当前制品

UPLOAD-CONCURRENCY-GREEN-03实际59项上传工程、NATIVE-01完整220项原生和RUNTIME-01六项实际账户/原操作/权限通过；当前PID37756及不可变制品已核查，SDK/输入/产物SHA逐项相等。唯一当前证据读取IMP-05，完整公开入口、底层网络主动中断、真实配置/批准及客户业务继续实施。以下execution已经使用，后续必须使用全新名称；较早命令只保留原执行范围。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-CONCURRENCY-GREEN-03
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-CONCURRENCY-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-CONCURRENCY-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

## IMP-05 上传输入与当前本地制品

UPLOAD-INCOMING-GREEN-02实际52项上传工程、NATIVE-01完整213项原生和RUNTIME-01六项真实账户/原操作/权限通过；当前PID35424及不可变制品身份已经核查。完整输入与产物SHA逐项相等，唯一主证据读取IMP-05。以下execution已经使用，后续必须使用全新名称；较早命令保留原执行范围，全部公开入口和客户业务继续实施。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-INCOMING-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-INCOMING-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-INCOMING-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

## IMP-05 当前上传许可事务

UPLOAD-ISSUANCE-GREEN-02实际44项上传工程、NATIVE-01完整205项原生和RUNTIME-01六项实际账户/原操作/权限回归通过；当前PID32289与不可变制品SHA已经核查。唯一当前证据读取IMP-05，完整公开上传、专业登记和客户业务继续实施。下面命令的execution已经使用，继续检查必须使用全新名称。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-ISSUANCE-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-ISSUANCE-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-ISSUANCE-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

原许可/会话/绑定、完整原操作、审计及Outbox已经连接同事务保存与重读；同参原结果核查当前读取权限，不创建第二个许可或延长期限。公开入口、服务器配置版本与批准依据、领域选择、Artifact登记及全部后续流程保持原任务要求。较早命令和回执保留各自原始范围。

## IMP-05 当前上传传输

UPLOAD-IO-NATIVE-01完整191项原生和UPLOAD-IO-RUNTIME-01六项实际账户/原操作回归通过；当前制品已经在本地运行，回执及原始范围由唯一IMP-05维护。下列execution已经使用，继续检查必须使用全新名称。完整客户上传、产物登记及后续业务继续实施。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_io.py --execution 20261005-UPLOAD-IO-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-IO-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-IO-RUNTIME-01
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

## IMP-05 当前上传授权与IMP-03共享Schema

当前UPLOAD-AUTHORIZATION-NATIVE-03完整184项原生、RUNTIME-02的31项实际身份/六项原操作/两组登录DOM通过；当前制品已在本地运行。完整许可创建、实际传输、登记及全部客户业务验收继续实施，唯一主记录为IMP-05，共享Schema记录为IMP-03。下列execution已使用，继续检查必须使用全新名称。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_authorization.py --execution 20261005-UPLOAD-AUTHORIZATION-GREEN-03
.lld-data-work/venv/bin/python scripts/check_published_schema_references.py --execution 20261005-CLASSPATH-SCHEMA-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-AUTHORIZATION-NATIVE-03
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_local_identity.py --execution 20261005-UPLOAD-AUTHORIZATION-RUNTIME-02
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-AUTHORIZATION-RUNTIME-02
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-UPLOAD-AUTHORIZATION-RUNTIME-02
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

## IMP-05 当前上传保存、版本更新和运行

UPLOAD-TRANSFER-NATIVE-01完整177项原生、当前六项原操作与两组登录DOM通过；当前制品已经在本地运行。独立GREEN-05九项检查保留执行时XML范围，当前完整源码与制品采用NATIVE-01回执。下列execution已经使用，继续验证需要全新名称；完整客户业务、全部验收、正式部署和交接保持原范围。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_transfer.py --execution 20261005-UPLOAD-TRANSFER-RED-01
.lld-data-work/venv/bin/python scripts/check_upload_transfer.py --execution 20261005-UPLOAD-TRANSFER-GREEN-05
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-TRANSFER-NATIVE-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-TRANSFER-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-UPLOAD-TRANSFER-RUNTIME-01
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

## IMP-05 当前上传身份与分片持久化

026已执行六项追加迁移，十项真实MySQL核查通过；完整168项原生及当前31项账户、六项原操作和两组DOM通过，本地新制品已经运行。唯一记录为IMP-05，下列已经使用的execution不得重复。全部完整业务、安全、非功能和交接保持原要求。

```bash
.lld-data-work/venv/bin/python scripts/check_upload_storage.py --execution 20261005-UPLOAD-STORAGE-RED-01
.lld-data-work/venv/bin/python scripts/check_upload_storage.py --check build --execution 20261005-UPLOAD-STORAGE-BUILD-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check coverage --execution 20261005-UPLOAD-STORAGE-COVERAGE-01
.lld-data-work/venv/bin/python scripts/check_upload_storage.py --check migrate --execution 20261005-UPLOAD-STORAGE-MIGRATION-01
.lld-data-work/venv/bin/python scripts/check_upload_storage.py --execution 20261005-UPLOAD-STORAGE-GREEN-03
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-UPLOAD-STORAGE-NATIVE-02
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_local_identity.py --execution 20261005-UPLOAD-STORAGE-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-UPLOAD-STORAGE-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-UPLOAD-STORAGE-RUNTIME-01
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

## IMP-05 当前私有对象写入及恢复

OBJECT-WRITE-BUILD-01完整168项原生通过，当前JavaPID8948、制品558d4e5da6c23a979b57a42af4e602d51c66537fb2be0716cdbedbf23eb87fe9，启动57.872秒，实际/auth/login HTTP 200；六项原操作及两组实际登录DOM通过。三份回执输入及产物SHA核查相等。唯一记录为IMP-05，下列execution已经使用，后续执行使用全新名称；以下其他集合保留原始输入范围。

```bash
.lld-data-work/venv/bin/python scripts/check_object_writer.py --execution 20261005-OBJECT-WRITE-RED-01
.lld-data-work/venv/bin/python scripts/check_object_writer.py --execution 20261005-OBJECT-WRITE-GREEN-01
.lld-data-work/venv/bin/python scripts/check_object_writer.py --execution 20261005-OBJECT-WRITE-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-OBJECT-WRITE-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-OBJECT-WRITE-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-OBJECT-WRITE-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

## IMP-05 当前独立依赖核验

DEPENDENCY-ACCESS-BUILD-01完整159项原生通过，当前JavaPID6766、制品a733d2884f08a32dcc0a62f11f8970cafa52ad29bddd82dffea699993410bcd0，启动56.062秒，实际/auth/login HTTP 200；六项原操作及两组实际登录DOM检查通过。唯一当前记录为IMP-05，以下execution已经使用，后续检查使用全新名称。

```bash
.lld-data-work/venv/bin/python scripts/check_dependency_access_authority.py --execution 20261005-DEPENDENCY-ACCESS-RED-01
.lld-data-work/venv/bin/python scripts/check_dependency_access_authority.py --execution 20261005-DEPENDENCY-ACCESS-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-DEPENDENCY-ACCESS-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-DEPENDENCY-ACCESS-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-DEPENDENCY-ACCESS-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

以下集合保留各自原始输入及制品范围。

## IMP-05 当前完整内容消费与回执

CONTENT-READ-BUILD-01完整155项原生通过，含五项读取回执、五项派生版本及七项来源检查。当前JavaPID3730、制品807e50678e712792a65113e4f3272b437f47cd7519a2ca70f912c4e599b21e58，启动52.87秒、登录HTTP 200；六项原操作及两组实际登录DOM检查通过。全部结果与范围读取唯一IMP-05记录，下列execution已使用，后续执行需要全新名称。

```bash
.lld-data-work/venv/bin/python scripts/check_content_read_receipts.py --execution 20261005-CONTENT-READ-RED-01
.lld-data-work/venv/bin/python scripts/check_content_read_receipts.py --execution 20261005-CONTENT-READ-GREEN-03
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-CONTENT-READ-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-CONTENT-READ-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-CONTENT-READ-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

读取回执检查使用实际只读MySQL，核对全部字段、保存SQL、主体保护及精确UTC/U64，没有创建客户回执。真实客户成功保存/重读、完整公开发送和内部任务读取继续验收。DeepSeek已经配置，当前没有新增模型请求。以下较早集合只表示其原始输入和制品范围。

## IMP-05 当前派生版本资源核验

VERSION-AUTHORITY-BUILD-01完整150项原生通过，包含五项派生版本及七项来源检查。当前JavaPID341、制品728e37d633a0446d9561ddefe7e1c76140f0b4e8faa4c95b35ac86a9c3accdf1、启动27.11秒、登录HTTP 200；六项原操作及两组实际登录DOM检查通过。原始回执及适用范围由IMP-05维护，下列execution均已使用，后续执行必须使用新名称。

```bash
.lld-data-work/venv/bin/python scripts/check_version_resource_authority.py --execution 20261005-VERSION-AUTHORITY-RED-01
.lld-data-work/venv/bin/python scripts/check_version_resource_authority.py --execution 20261005-VERSION-AUTHORITY-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-VERSION-AUTHORITY-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-VERSION-AUTHORITY-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-VERSION-AUTHORITY-RUNTIME-01
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

独立Green仅涵盖当次34类查询和两项检查；当前36类及五项检查由完整构建覆盖。清单检查只读取全部状态，不能证明业务通过；DeepSeek本地check不请求模型。以下较早集合各自保持原始输入范围。

## IMP-05 当前来源读取与完整关系

SOURCE-AUTHORITY-BUILD-02完整145项原生检查通过，包含七项来源检查；当前JavaPID97158、制品498ba86f368cb8f809c3b45db43eb299c20dfdec39030dd931ec63e5b6b01f3d，启动28.166秒、登录HTTP 200。当前六项原操作及两组真实登录DOM检查通过，原始回执和摘要由IMP-05维护。所有下列execution已经使用，后续执行需要新名称；较早25/30集合保留原始输入范围，完整业务未验收。

```bash
.lld-data-work/venv/bin/python scripts/check_source_read_authority.py --execution SOURCE-AUTHORITY-RED-01
.lld-data-work/venv/bin/python scripts/check_source_read_authority.py --execution SOURCE-AUTHORITY-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-SOURCE-AUTHORITY-BUILD-02
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_operations.py --execution 20261005-SOURCE-AUTHORITY-RUNTIME-01
.lld-data-work/venv/bin/python scripts/check_local_auth_errors.py --execution 20261005-SOURCE-AUTHORITY-RUNTIME-01
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
```

check_source_read_authority的独立Green保留原始输入范围；当前七项最终测试包含在上述完整原生构建中。最后一项只核查全部清单、映射与实际状态，不能证明业务验收通过。DeepSeek本地check不调用模型，未新增/models或生成请求。

## IMP-05 具体资源授权与固定登记读取

RESOURCE-REGISTRY-FINAL-01完整25组工程回归及30份当前字节核查通过，回归摘要8c50a915be8b586c35ad14be953df9d5be16c515805930287fb4e8dc4463210e，核查摘要37e63b7ce6dcb6da9d7c0fc92d4ac813ae882e2b1ffa1db3f04721da603427ef。当前新Java/对象部署和138项原生及全部输入/产物相符；业务验收继续未完成。实际核查命令如下，output已经存在，后续执行必须使用新的路径。

```bash
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261005-RESOURCE-REGISTRY-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261005-RESOURCE-REGISTRY-BUILD-01/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261005-RESOURCE-REGISTRY-FINAL-01/verified-evidence.json
```

RESOURCE-REGISTRY-BUILD-01完整138项原生检查已通过，新JavaPID92494、制品e97700afaa765311df917297f01ee3e0c2e1654621fc1466612fc92886a16882，启动25.176秒、登录HTTP 200。新增四项资源授权和三项登记读取使用真实只读MySQL、当前平台账户及成熟Schema。完整来源、客户成功与业务回执继续实施；RESOURCE-REGISTRY-FINAL-01完整25组结果读取原始终态。下方execution均为本次已执行身份，后续执行必须使用新的名称。

```bash
.lld-data-work/venv/bin/python scripts/check_resource_read_authority.py --execution 20261005-RESOURCE-AUTHORITY-RED-02
.lld-data-work/venv/bin/python scripts/check_resource_read_authority.py --execution 20261005-RESOURCE-REGISTRY-RED-01
.lld-data-work/venv/bin/python scripts/check_resource_read_authority.py --execution 20261005-RESOURCE-REGISTRY-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-RESOURCE-REGISTRY-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-RESOURCE-REGISTRY-FINAL-01
```

详细行为、BDD、代码位置和原始证据由[IMP-05](development/IMP-05.md)维护；DeepSeek仍使用[配置说明](deepseek-configuration.md)中的原秘密文件，本次没有调用模型清单或生成接口。以下集合各自保留原始输入范围。

## IMP-05 固定发布Schema及完整登记结构

PUBLISHED-SCHEMA-BUILD-01完整131项原生检查、PUBLISHED-SCHEMA-FINAL-01的25组回归和30份当前字节核查已经通过，JavaPID88142、制品96a21070ce705fbef32065100e32d751ed98119bacc14b714dcb9554b7da4a21，启动25.969秒，登录HTTP 200。固定发布身份及完整UTF8 JSON六项直接调用检查包含在完整构建中，当前五项真实MySQL登记结构重新通过。025实际应用六项迁移，累计192项。ARTIFACT-REGISTRY-FINAL-01及其他集合保持原始输入范围；下方执行身份均已使用，每次后续执行必须使用新的execution。

```bash
.lld-data-work/venv/bin/python scripts/check_artifact_registry.py --execution 20261005-ARTIFACT-REGISTRY-RED-03
.lld-data-work/venv/bin/python scripts/check_artifact_registry.py --check migrate --execution 20261005-ARTIFACT-REGISTRY-MIGRATE-01
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-ARTIFACT-REGISTRY-FINAL-01
.lld-data-work/venv/bin/python scripts/check_published_schema_references.py --execution 20261005-PUBLISHED-SCHEMA-RED-01
.lld-data-work/venv/bin/python scripts/check_published_schema_references.py --execution 20261005-PUBLISHED-SCHEMA-GREEN-02
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-PUBLISHED-SCHEMA-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-PUBLISHED-SCHEMA-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261005-PUBLISHED-SCHEMA-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261005-PUBLISHED-SCHEMA-BUILD-01/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261005-PUBLISHED-SCHEMA-FINAL-01/verified-evidence.json
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

原生迁移的备份和回执由local_runtime.py保存并重读核验；已有迁移001—025不得修改。发布目录检查使用真实D0数据及原221个引用，不创建客户企业、制品、依赖或读取回执。完整企业授权、来源关系、上传及公开/内部读取仍需业务验证。

当前25组/30份集合的原始结果和摘要由IMP-05维护。检查结束后环境、索引、计划、接口和源码保持原字节；维护记录、报告、命令和续接文件同回执输入的绝对路径交集为空。DeepSeek本地check通过，没有再次调用模型清单或收费生成。

## IMP-05 存储引用与当前制品

以下执行身份已经使用，后续执行必须使用新execution。PERSISTENCE-BUILD-01完整125项原生检查、PERSISTENCE-FINAL-01最终七项真实MySQL/S3、24组回归及30份当前字节核查通过。当前JavaPID81465、部署UUIDccc4d22e-50c6-48e1-845b-9e02cb8467b0、制品SHA-256为e2a47a3d7f939543902f053805e6d84fab59158ef10ef22dfb6abfd4b1315af3；实际启动26.932秒，登录HTTP 200。原始结果及全部关联由唯一IMP-05维护。

```bash
.lld-data-work/venv/bin/python scripts/check_stored_object_references.py --execution 20261005-PERSISTENCE-RED-01
.lld-data-work/venv/bin/python scripts/check_stored_object_references.py --execution 20261005-PERSISTENCE-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-PERSISTENCE-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_stored_object_references.py --execution 20261005-PERSISTENCE-FINAL-01
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-PERSISTENCE-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261005-PERSISTENCE-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261005-PERSISTENCE-BUILD-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-PERSISTENCE-20261005-PERSISTENCE-FINAL-01/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261005-PERSISTENCE-FINAL-01/verified-evidence.json
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
```

存储检查使用只读参数化MySQL表达式及原两份D0原件，没有登记客户业务记录。原生125项包含14项实际分页/完整消费；当前补充使用最终存储引用回执。秘密文件保持0600，缓存与临时目录位于项目.implementation-work/；企业Artifact许可、完整依赖、Schema及业务读取回执继续实施。

## IMP-05 完整内容消费与当前制品

以下执行身份已经使用，后续执行必须使用新execution。ASSEMBLY-BUILD-01完整118项原生检查通过，构建2分22秒；ASSEMBLY-FINAL-01实际14项分页与完整消费复验通过。新JavaPID77952、部署UUID8fb67320-b21e-498f-a8ea-709231ad1fd1、不可变制品摘要2527f8541ea100e61a494876c0158128972374a1f72c972da7efdf00faf67158，启动30.162秒；实际进程核查和登录入口HTTP 200已经通过。完整工程回归结果与原始证据由IMP-05维护。下方其他执行身份保持各自原始范围。

```bash
.lld-data-work/venv/bin/python scripts/check_object_paging.py --execution 20261005-ASSEMBLY-RED-01
.lld-data-work/venv/bin/python scripts/check_object_paging.py --execution 20261005-ASSEMBLY-GREEN-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-ASSEMBLY-BUILD-01
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
.lld-data-work/venv/bin/python scripts/check_object_paging.py --execution 20261005-ASSEMBLY-FINAL-01
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-ASSEMBLY-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261005-ASSEMBLY-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261005-ASSEMBLY-BUILD-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-PAGING-20261005-ASSEMBLY-FINAL-01/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261005-ASSEMBLY-FINAL-01/verified-evidence.json
```

检查读取当前127.0.0.1对象服务及两份D0公开原件。秘密文件通过0600权限保存，全部缓存和中间结果由执行入口设置在项目目录。字节消费容量及技术准入不能代替客户许可、模型tokenizer核算、正式Schema解释或业务读取回执。

## IMP-05 固定版本内容分页与游标

以下执行身份已经使用，后续执行必须使用新execution。PAGING-BUILD-01完整110项原生检查通过；PAGING-FINAL-01六项实际原件分页检查通过。新制品169b7016e499919e150e5b0cf5fb47653aa6319720e753856b2d52ea98eb17a8运行于PID72778，启动25.448秒。当前完整共享回归使用PAGING-FINAL-02，全部实际结果和输入/产物核查由IMP-05维护，不能根据命令文本登记通过。

```bash
env TMPDIR="$PWD/.implementation-work/object-paging-tmp" .lld-data-work/venv/bin/python scripts/check_object_paging.py --execution 20261005-PAGING-FINAL-01
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-PAGING-BUILD-01
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/local_runtime.py application
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-PAGING-FINAL-02
```

运行原生测试需要连接当前127.0.0.1对象服务；对象服务秘密使用0600文件，不能进入命令参数或普通回执。测试实际AES密钥、有效期和字节容量只证明技术范围，生产参数与客户读取许可仍需真实批准。

## IMP-05 Java固定版本对象读取

以下执行身份已经使用，后续检查需要新execution。OBJECT-REFERENCE-GREEN-01验证ObjectRef构造保护，OBJECT-READ-GREEN-02验证完整104项原生检查和真实原件的多个S3范围读取；源码、原件、原ObjectRef、当前对象部署及实际Java制品由原始回执绑定。当前Java制品21c6650a14db4c85689d443af50fd1a5cdc45a98645ceec0220fc4bdd2618774已经启动，PID68313，启动25.196秒。完整业务与当前共享回归结果由IMP-05维护。

```bash
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-OBJECT-REFERENCE-GREEN-01
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261005-OBJECT-READ-GREEN-02
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/local_runtime.py application
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-OBJECT-READ-FINAL-01
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261005-OBJECT-READ-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261005-OBJECT-READ-GREEN-02/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261005-OBJECT-READ-FINAL-01/verified-evidence.json
```

OBJECT-READ-FINAL-01的24组及29份当前字节核查已通过，Java与对象服务身份及全部输入/产物匹配。对象读取的客户权限、依赖核验、读取回执、coverage和业务入口继续实施；原生读取检查不代表这些业务已经验收。下方较早集合只表示各自原始日期及输入范围。

## IMP-05 本地私有对象及双部署回归

以下执行身份已经使用，重新检查需要新execution。实际存储四项、重启后的原版本重读及全部本机监听已经通过，最终24组工程回归与29份当前证据核查通过。Java与对象服务的部署身份分别为0105712b-4e55-4f45-b719-9acdaac5abf8和45368b63-317e-4edc-906d-8e347b2cd774；原始依据读取IMP-05。

```bash
env TMPDIR="$PWD/.implementation-work/object-storage-tmp" .implementation-work/object-storage-venv/bin/python scripts/check_object_storage.py --execution 20261004-OBJECT-STORAGE-RED-03
env TMPDIR="$PWD/.implementation-work/object-storage-tmp" .implementation-work/object-storage-venv/bin/python scripts/local_object_storage.py protect
env TMPDIR="$PWD/.implementation-work/object-storage-tmp" .implementation-work/object-storage-venv/bin/python scripts/check_object_storage.py --execution 20261004-OBJECT-STORAGE-GREEN-01
env TMPDIR="$PWD/.implementation-work/object-storage-tmp" .implementation-work/object-storage-venv/bin/python scripts/local_object_storage.py stop
env TMPDIR="$PWD/.implementation-work/object-storage-tmp" .implementation-work/object-storage-venv/bin/python scripts/local_object_storage.py start
env TMPDIR="$PWD/.implementation-work/object-storage-tmp" .implementation-work/object-storage-venv/bin/python scripts/check_object_storage.py --execution 20261004-OBJECT-STORAGE-RECOVERY-01 --recovery-from .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-GREEN-01/objects.json
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261005-OBJECT-STORAGE-FINAL-01
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261005-OBJECT-STORAGE-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-BUDGET-HISTORY-02/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-STORAGE-20261004-OBJECT-STORAGE-RECOVERY-01/receipt.json --receipt .implementation-work/IC-05-OBJECT-LISTENERS-20261004-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261005-OBJECT-STORAGE-FINAL-01/verified-evidence.json
```

原生监听检查使用capture_check.py保存/usr/sbin/lsof -nP -a -p 62380 -iTCP -sTCP:LISTEN的完整实际输出；回执为IC-05-OBJECT-LISTENERS-20261004-01。生命周期入口只操作经过制品、实际命令及OS身份核验的当前PID；已有服务数据和加密秘密继续保留，prepare仅用于尚未初始化的目录。秘密不作为命令参数或普通证据输入。对象业务授权、上传会话、审计、生产KMS和完整验收继续实施。

## IMP-07 预算批准历史与最终制品

下列执行身份已经实际使用，不得重用。三个Red来自真实缺少历史接口、正式页面或错误编码的有效断言；最终原生96项及23组回归通过，26份当前源码、产物和部署核查通过。当前程序PID56990；完整实现及原始证据读取IMP-07。没有访问收费接口、创建替代客户资料或执行视觉检查。

```bash
.lld-data-work/venv/bin/python scripts/check_budgets.py --execution 20261004-BUDGET-HISTORY-RED-01
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution 20261004-BUDGET-HISTORY-FRONT-RED-01
.lld-data-work/venv/bin/python scripts/check_budget_errors.py --execution 20261004-BUDGET-HISTORY-ERROR-RED-01
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261004-BUDGET-HISTORY-02
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-BUDGET-HISTORY-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-BUDGET-HISTORY-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-BUDGET-HISTORY-02/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-BUDGET-HISTORY-FINAL-01/verified-evidence.json
```

以下其他执行身份只表示各自原始输入和日期范围；客户成功、并发、撤权及全部AC/NFR继续读取实施任务计划和IMP-07。

## IMP-07 正式预算页面与关联回归

执行身份不得重用。下列命令已经执行，原始Red及Green分别保存；检查使用真实本机所有者、Cookie、MySQL/Redis和Chrome DOM，不创建客户替代资料。当前Java和模型输入未修改，26份核查包含原Java95项及模型28项/实际安装后28项。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution 20261004-BUDGET-FRONT-RED-01
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution 20261004-BUDGET-FRONT-GREEN-01
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-BUDGET-FRONT-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-BUDGET-FRONT-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-BUDGET-02/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-BUDGET-FRONT-FINAL-01/verified-evidence.json
```

当前集合23组通过，26份当前输入及产物核查通过；开发十组/生产七组及本人账户两种入口各五组通过。正式业务成功及全部验收继续读取实施任务计划和IMP-07。

## IMP-07 预算账户与批准事务

执行身份不得重用。以下分别执行原生构建、实际预算HTTP检查和包含新增预算的23组共享回归；原始结果由对应回执记录，完整客户业务另行验收。

```bash
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261004-BUDGET-02
.lld-data-work/venv/bin/python scripts/check_budgets.py --execution 20261004-BUDGET-GREEN-01
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-BUDGET-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-BUDGET-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-BUDGET-02/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-SETTLEMENT-GREEN-02/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-SETTLEMENT-PACKAGE-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-BUDGET-FINAL-01/verified-evidence.json
```

BUDGET-GREEN-01核查其原始部署；最终代码及部署的预算检查由BUDGET-FINAL-01重新执行。Java程序启动及停止沿用scripts/local_runtime.py application/stop-application，当前不可变制品、进程身份及本地数据库读取实施环境登记，不使用旧输入代表新增预算代码。

## IMP-07 四类金额及实际制品检查

执行身份不得重用。金额检查仅使用数学输入，实际供应商用量、价格批准及预算事务分别验收。

```bash
.implementation-work/model-gateway-venv/bin/python scripts/check_model_gateway.py --execution 20261004-SETTLEMENT-GREEN-02
.implementation-work/model-gateway-venv/bin/python scripts/check_model_gateway.py --check package --execution 20261004-SETTLEMENT-PACKAGE-01
.lld-data-work/venv/bin/python scripts/check_inputs.py --execution 20261004-SETTLEMENT-INPUTS-01
```

## IMP-06 项目写入及原请求恢复

正式Vue创建、属性修订、状态决定及BX-31/OP-01消费使用同一工程。检查执行真实本机账户、实际Cookie与数据库，不创建客户替代资料；合法客户成功流程仍需获授权输入。下列命令对应全新执行身份，结果读取实际回执，既有执行目录不得重用。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution 20261004-PROJECT-WRITE-GREEN-02
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-PROJECT-WRITE-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-PROJECT-WRITE-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-OPERATIONS-04/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-PROJECT-01/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-PROJECT-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-PROJECT-WRITE-FINAL-01/verified-evidence.json
```

## IMP-05 原操作查询

BX-31查询原主体及当前企业的原请求；OP-01返回原操作安全状态元数据。以下执行身份已经使用，复验需要替换为全新execution。Java90项、原操作六项HTTP/数据库检查、15项迁移检查及22组现有回归通过；25份回执的当前输入、产物和真实运行身份核查通过。

```bash
.lld-data-work/venv/bin/python scripts/check_migration.py --check coverage --execution 20261004-OPERATIONS-COVERAGE-03
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261004-OPERATIONS-04
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-OPERATIONS-FINAL-03
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-OPERATIONS-FINAL-03/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-OPERATIONS-04/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-PROJECT-01/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-PROJECT-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-OPERATIONS-FINAL-03/verified-evidence.json
```

024已实际执行186项迁移，原001—023继续保持原字节。期限及批准依据共同为空时继续保存原请求；期限到达不自动重发。检查使用真实操作系统所有者、当前企业拒绝和实际数据库元数据；合法企业FOUND/NOT_FOUND/EXPIRED与管理授权、撤权及并发继续需要获授权原操作，完整IC-05保持IN_PROGRESS。

## IMP-06 正式Vue会话与项目读取

FRONT-PROJECT-JSON-01使用原PJ请求转换器验证原始数值以及全部重复字段。JSON集合的21组登记回归及24份回执核查绑定对应原始程序及日期，实际执行身份如下；再次运行使用全新execution及全新输出文件。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution 20261004-JSON-GREEN-02
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-JSON-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-JSON-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-PROJECT-01/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-PROJECT-01/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-PROJECT-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-JSON-FINAL-01/verified-evidence.json
```

原生strict和正式构建、开发五组以及生产四组检查通过。开发组的十项转换和八项无效JSON断言只提供工程算法证据；真实PJ读取拒绝以及MySQL客户数量前后为零独立验证。本人账户两种入口各五组、实际原响应和审计重新通过。完整客户项目写入及后续业务仍需运行验收。

开发入口在web目录执行npm run dev:medical，地址为http://127.0.0.1:18083/access；实际后端继续使用127.0.0.1:18080。严格检查使用npm run typecheck:medical。以下命令由项目根目录运行，实际构建生产文件，并在开发和生产入口分别执行真实账户检查；代理和浏览器在检查结束时关闭。合法企业和项目成功分支仍需真实客户输入。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_session_browser.py --execution 20261004-VUE-PRODUCTION-01
```

每次复验使用全新execution；原始目录与回执不能覆盖。完整任务、关联组件及尚未验收内容读取development/IMP-06.md。

本人账户/account通过原Navbar进入，LOCAL-06核查当前本人及独立登录身份。以下入口使用当前真实本机所有者，原生构建以后在开发及生产入口分别验证密码确认、UTF-8限制、错误当前密码、原请求刷新恢复、密码更新、旧会话撤销、新密码登录、原响应重放及MySQL审计。检查会更新当前本机管理员密码，结束以后读取当前受控owner-credentials.json；密码值不进入命令或报告。

```bash
.lld-data-work/venv/bin/python scripts/check_medical_account_browser.py --execution 20261004-ACCOUNT-GREEN-02
```

sessionStorage按真实主体只登记actor、key和revision。密码输入在页面隐藏、离开、失效及完成时清除；刷新以后需要本人重新输入原密码请求的完整内容。原生flock拒绝并发恢复，候选凭据先以0600保存并同步目录，实际新密码认证以后原子替换受控凭据文件。平台账户身份与企业资料授权分别核验，当前账户没有客户企业，不创建业务样本。完整回归脚本新增IC-06-ACCOUNT-BROWSER，当前共21组；新集合实际通过以后由以下入口核查24份回执。

```bash
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-ACCOUNT-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-ACCOUNT-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-PROJECT-01/receipt.json --receipt .implementation-work/IC-07-ADAPTER-20261004-PROJECT-01/receipt.json --receipt .implementation-work/IC-07-PACKAGE-20261004-PROJECT-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-ACCOUNT-FINAL-01/verified-evidence.json
```

## IMP-07 DeepSeek适配器及本地配置

工作目录为项目根目录，Python解释器为.implementation-work/model-gateway-venv/bin/python。固定依赖读取model-gateway/requirements.lock和pyproject.toml；秘密只读取.implementation-work/runtime/deepseek-api-key，秘密值不进入命令参数、回执或源码。

```bash
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py verify
.implementation-work/model-gateway-venv/bin/python scripts/check_model_gateway.py --execution 20261004-PROJECT-01
.implementation-work/model-gateway-venv/bin/python scripts/check_model_gateway.py --check package --execution 20261004-PROJECT-01
```

既有18项原生检查及实际wheel的18项安装后检查绑定原日期与输入，设计输入变化以后需要复验。再次执行必须使用全新execution。check检查本地秘密文件；当前密钥已经配置，check及verify实际通过，真实/models返回deepseek-flash和deepseek-v4-pro，范围PROVIDER_CONNECTION_ONLY。连接回执为.implementation-work/deepseek-verification-4cd398d4-eb70-4e72-b060-fbfd1b76a614.json。[配置说明](deepseek-configuration.md)维护完整操作与调用条件，[IMP-07](development/IMP-07.md)维护源码、回执及完整尚未验收内容。

## IMP-04 项目接口与证据核查

PJ-01—06由ProjectController/Service/Schema/Mapper及023迁移维护，项目创建、属性修订与状态决定使用当前完整企业许可及原版本，不产生客户样本。正式S03/S18和真实企业成功路径继续验收。

```bash
.lld-data-work/venv/bin/python scripts/check_projects.py --execution 20261004-PROJECT-01
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261004-PROJECT-FINAL-01
.lld-data-work/venv/bin/python scripts/inspect_current_evidence.py --summary .implementation-work/FULL-REGRESSION-20261004-PROJECT-FINAL-01/evidence.json --receipt .implementation-work/IC-03-JAVA-20261004-PROJECT-01/receipt.json --output .implementation-work/FULL-REGRESSION-20261004-PROJECT-FINAL-01/verified-evidence.json
```

命令中的execution用于指定运行身份，已经执行的身份不能重复。只有相应回执实际保存且输入与产物摘要一致时才能登记通过；工程及拒绝检查不能替代业务验收。

2026-10-03 全部现有运行检查使用以下入口，逐项顺序执行并核对回执输入及产物摘要；不把运行检查通过登记为完整业务验收。执行身份必须全新。当前完整需求矩阵、数据规范、分层结果与未通过范围读取 [full-functional-report.md](testing/full-functional-report.md)。

```bash
.lld-data-work/venv/bin/python scripts/check_full_function_inventory.py
.lld-data-work/venv/bin/python scripts/check_full_regression.py --execution 20261003-FINAL-01
```

运行检查要求当前正式构建已经通过并启动、真实MySQL/Redis和本机所有者凭据可用。范围包括2项连接与校验、31项HTTP、5项账户浏览器、4项身份比较、4项成员数据库、7项邀请数据库、5项权限拒绝、5项邀请拒绝、6项组件、跨语言、4项迁移入口、14项迁移结构和5项输入登记；源文件、实际制品、配置、工具、数据库结果及浏览器行为分别绑定。该 execution 已经用于本次运行，重新运行须改用新身份。

在项目根目录执行，注明 web/ 的命令在该目录执行。迁移、类型检查和业务验收命令按对应工单的实际实现继续登记。

## IMP-04 企业登记申请

企业选择入口由账户页面进入，候选使用真实本人接口，旧企业上下文错误时保留错误并显示独立当前候选。检查使用实际Chrome DOM和原生MySQL，关闭全部视觉功能；当前没有客户企业，空成员与拒绝行为不能代表合法选择通过。

```bash
.implementation-work/web-check-venv/bin/python scripts/check_enterprise_context_browser.py --execution 20261004-CONTEXT-03
```

每次执行使用全新execution。捕获内容包括候选读取、Header/查询拒绝、空成员页面、刷新、实际断网恢复、注销及MySQL业务记录不增加；有真实企业以后按照完整IC-04检查合法选择、重放、撤权及并发。

```bash
.lld-data-work/venv/bin/python scripts/check_enterprise_registration.py --execution 20261004-REVIEW-01
.implementation-work/web-check-venv/bin/python scripts/check_enterprise_registration_browser.py --execution 20261004-REVIEW-01
```

账户页面进入企业登记申请，并从申请页面进入审查。当前平台管理员根据真实批准依据选择APPROVE/REJECT；批准需要明确治理许可和人工声明。HTTP核验11项真实账户/数据库/拒绝行为，浏览器核验三项实际DOM流程，均不代替真实企业提交、批准、重放、并发或者完整AC-01/02。再次执行使用全新execution。账户HTTP检查使用.lld-data-work/venv/bin/python及其完整共享依赖。

## IMP-01 正式工程构建

```bash
.lld-data-work/venv/bin/python scripts/build_business.py --versions
.lld-data-work/venv/bin/python scripts/build_business.py
.lld-data-work/venv/bin/python scripts/check_python.py --check package --execution 20261002-02
```

使用固定项目工具运行 Java 原生 Maven verify；依赖缓存和 Java临时目录均位于 .implementation-work/。项目工具的发行版本及摘要见 IMP-01。未配置业务测试时不得以 verify 的成功结果登记业务通过。

Python 命令构建共享库的实际 wheel，安装后以 `-I` 隔离模式核对十二份源码摘要、依赖和31项检查。构建组件为 setuptools 80.9.0；pyproject.toml 维护直接依赖和测试依赖，requirements.lock 维护完整固定版本。构建、缓存和临时目录均位于项目 .implementation-work/。当前 wheel 的 SHA-256 为687ca4f1d0ec27f59f9a577416ff6089be952404ede0d3909b344554fe9912a0；回执见 IMP-01。共享库制品不代表 Worker 或 SDK/runtime 已完成。

web/ 中执行：

```bash
npm ci --cache ../.implementation-work/npm-cache --registry https://registry.npmjs.org
npm run build:prod
```

工程使用原 Vite 构建。实际执行环境须允许 native 编译子进程；锁文件在版本管理范围。

## IMP-02 本地输入登记核查

```bash
.lld-data-work/venv/bin/python -m unittest discover -s tests -p test_implementation_inputs.py -v
.lld-data-work/venv/bin/python scripts/check_inputs.py --execution 20261002-18
```

断言：当前唯一数据 Schema、真实来源摘要和长度、来源关系与引用版本、本地用途许可、四项已启动工单的记录链接。该检查不代表业务或生产验收。

实际执行使用技能的 `capture_check.py` 保存原始输出、退出码、时间与输入 SHA-256。证据目录必须全新建立；主记录维护实际回执路径。已经取得的产品工程使用其原生命令，不从工具命令推断业务成功。

正式前端组件检查使用专用Python环境和当前tests/browser/的固定Playwright。以下命令在项目根目录执行；中间文件、pip缓存和浏览器临时文件归属项目目录。

```bash
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .lld-data-work/venv/bin/python -m venv .implementation-work/web-check-venv
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" .implementation-work/web-check-venv/bin/python -m pip install --requirement tests/web-requirements.lock --cache-dir .implementation-work/pip-cache --index-url https://pypi.org/simple
.implementation-work/web-check-venv/bin/python scripts/check_web.py --execution 20261002-10
```

当前IC-01-WEB-COMPAT-20261002-10通过六项真实Chrome检查，385项输入及460项产物，SHA-256为e87e837700a65d361ebad423a32b30a5b08687e9707d04fc99f6c7854ea595db。开发及正式路径读取90份实际SVG和IconSelect，redis-py 6.4.0读取Redis 7.4.8及23项命令统计，图表读取、resize及dispose通过；Chrome/Node/依赖、源码、当前程序及全部制品进入回执，没有秘密及视觉流程。重新执行使用全新execution。

正式制品在该执行目录的production/，组件检查制品在compatibility/；检查页面没有加入产品入口。固定锁文件的npm ci回执IC-01-WEB-INSTALL-20261002-01通过，SHA-256为9898f253c807346337eaf2806cf701f58c591603e669cf535418ba74e5dc0f5c。当前完整官方npm audit回执IC-01-WEB-SEC-20261002-03报告359项依赖和零项已知安全问题，七项源码及锁文件绑定；SHA-256为86a26aeb313043ec76c46205d3f8ab948b57ac03c6cc197702a9147ad7c441a8。完整业务及其他安全要求继续独立验收。

## IMP-03 共享 Schema 检查

项目根目录执行：

```bash
PYTHONPATH=contracts/python/src .lld-data-work/venv/bin/python -m unittest discover -s tests -p test_shared_contracts.py -v
.lld-data-work/venv/bin/python scripts/check_python.py --check test --execution GREEN-20261002-02
.lld-data-work/venv/bin/python scripts/build_business.py
PYTHONPATH=contracts/python/src .lld-data-work/venv/bin/python tests/check_cross_language.py
```

跨语言命令读取 current.json 所指的独立 Java 执行报告，核查路径、身份、报告摘要及全部绑定输入。Java 测试必须先完成；输入变化后直接拒绝使用过期报告。TypeScript 检查命令在 contracts/typescript/执行：

```bash
npm ci --cache ../../.implementation-work/npm-cache --registry https://registry.npmjs.org
npm run typecheck
npm test
```

Python 共享库的依赖版本由 contracts/python/requirements.lock 固定。当前检查限唯一 Schema、真实本地来源、表示与规范摘要；目标 MySQL、领域权限、模型、审查、签发、导出及生产迁移不在已通过范围。

Python 当前31项检查通过，覆盖登记包13项、模型25项、资源详情27项、接口102项、专业产物49项类型绑定及后端设计的完整68项错误代码，公共17项复用；原始字段、必填属性、枚举、继承、引用及联合分支保持对应。DatasetBindings 完整读取真实登记包及成员；ArtifactBindings 绑定八类输出和完整外层，原始 EvidenceRef 使用文本身份，AttachmentBinding.source_version 使用 UUID，金额24/8和测量值38/12分别核查。InterfaceBindings 保留93个接口对象的字段及继承、九种事件映射、开放属性和原始 from 字段。技术 Envelope 使用实际检查 UUID 标识登记读取，data 保持自由 JSON 和独立内容。TypedPayload.read 每次创建独立对象，UUID、UTC、revision 和 Decimal 保留原始字符串表示。scripts/check_python.py 绑定正式源码、测试、依赖文件及实际依赖 wheel、五份 Schema、后端错误设计、登记数据和真实原件。接口与产物服务消费仍待实施。

在项目根目录保存 TypeScript 检查回执：

```bash
.lld-data-work/venv/bin/python scripts/check_typescript.py --check typecheck --execution 20261002-02
.lld-data-work/venv/bin/python scripts/check_typescript.py --check test --execution 20261002-02
```

本次身份已用于实际执行；重新执行使用新目录。当前严格类型检查及26项运行检查通过；包含完整68项错误枚举、标准 Schema 拒绝、模型协议25项、资源详情27项、完整接口102项类型与绑定、专业产物49项及外层对象、登记包13项类型。实际 TypeScript 编译器核查93个接口对象及37个产物对象的字段、必填属性、枚举与条件关系，并核对102项绑定映射；九个事件分支及八种产物输出保持原始对应。接口表示按原始 Schema 引用和有效分支执行，实际登记包与技术 Envelope 保持独立内容；产物与资源表示字段集合均与原始 Schema 的引用完整相等。该脚本绑定 TypeScript 源码、测试、锁文件、全部唯一 Schema、后端错误设计和实际原件；中间目录在项目 .implementation-work/typescript-temp/。专业业务产物、接口服务及 AS-01—AS-15 的真实对象关系仍待实际服务验证。

完整迁移结构检查在项目根目录执行：

```bash
.lld-data-work/venv/bin/python -m unittest discover -s tests -p test_migration_bundle.py -v
```

当前完整覆盖135张企业表及13张平台表；十四项检查核查覆盖、唯一来源、完整关联、字段、审计、Schema、身份、上下文、邀请及批准角色。SQLGlot 30.0.3、XML及JSQLParser 5.2读取成熟格式。原生clean verify通过65项并使用Liquibase 4.31.1检查176项changeset和校验和；真实MySQL已执行全部迁移，生产、历史及恢复继续验收。

保存检查回执的命令在项目根目录执行，execution 必须使用全新目录身份：

```bash
.lld-data-work/venv/bin/python scripts/check_migration.py --check build --execution 20261002-37
.lld-data-work/venv/bin/python scripts/check_migration.py --check coverage --execution 20261002-20
.lld-data-work/venv/bin/python scripts/check_migration.py --check cross --execution 20261002-18
.lld-data-work/venv/bin/python scripts/check_migration.py --check entrypoint --execution 20261002-18
```

本次身份已用于实际执行。脚本按对应检查绑定源码、Schema、核心 DDL、迁移资源、独立 Java 报告及制品；原始日志、退出码、输入和结果摘要保存于 .implementation-work/ 对应目录。coverage 的非零退出码继续表示完整范围未通过。

上述检查的 TMPDIR 固定为项目 .implementation-work/migration-check-tmp/；Java 构建工具继续采用其项目内临时目录。

跨语言入口同时绑定 Python 的 pyproject.toml、requirements.lock、tests/error_design.py 及 LLD-后端.md。markdown-it-py 4.0.0 / mdurl 0.1.2 从实际 §4.10 表格读取全部68项错误代码，与三个运行输出逐项比较。真实登记包、12条定位与56个标量使用 Pydantic 类型绑定生成 Python 规范内容，再与实际 Java 报告和 TypeScript 输出比较；全部216项原始定义可解析，三种语言均包含216项类型绑定，实际业务消费分别验证。

Java 当前执行26项共享及 DTO 检查、1项迁移制品检查；五份 Schema 的216项定义及登记与产物外层均具有类型与绑定，68项错误代码及标准 Schema 拒绝已实施。Jackson 核查全部93个接口对象、17个登记及内联对象、37个产物对象的字段、枚举、继承与泛型引用，九种事件及八种产物输出保持原始对应；完整实际登记及每个来源、记录和定位进行类型往返，技术 Envelope 返回独立内容，实际11份文件的完整读取检查 Coverage。独立报告绑定当前36项输入。跨语言检查比较68项错误代码、56个实际登记标量的规范摘要，并继续核查216项定义、11份来源、12项定位和5处 TXT 原文范围。真实专业产物、接口与资源、模型回执及调用方业务消费仍需独立验证。

## IMP-03 独立迁移入口

原生 verify 同时构建普通迁移资源 jar 和 medical-data-3.9.2-cli.jar。本地包装命令使用已登记项目 JDK 17，原生 CLI、JDBC 及迁移资源均来自该 jar。

```bash
.lld-data-work/venv/bin/python scripts/run_migration.py version
.lld-data-work/venv/bin/python scripts/run_migration.py help
.lld-data-work/venv/bin/python scripts/run_migration.py help --for-command update
```

上述信息命令已经实际执行。数据库动作使用环境注入以下参数，不在命令行或仓库保存密码：

| 环境变量 | 内容与核查范围 |
| --- | --- |
| MEDICAL_MIGRATION_JAR_SHA256 | 已批准实际 CLI 制品摘要；与当前 jar 字节比较 |
| MEDICAL_MIGRATION_LIBRARY_SHA256 | 同版本普通迁移资源 jar 摘要；核验后使用原生 search-path 读取其全部资源 |
| LIQUIBASE_COMMAND_URL | 已批准 MySQL JDBC 连接；完整地址由 Connector/J 校验 |
| LIQUIBASE_COMMAND_USERNAME | 独立迁移账号；实际权限待核验 |
| LIQUIBASE_COMMAND_PASSWORD | 由秘密服务注入的秘密；不作为命令参数 |
| MEDICAL_MIGRATION_DATABASE | 目标数据库名称；update 前置条件与 DATABASE() 比较 |
| MEDICAL_MIGRATION_SERVER_UUID | 目标 MySQL server_uuid；小写 UUID；update 前置条件核对实际值 |
| MEDICAL_MIGRATION_APPROVAL_REF | 实际批准记录引用；真实性由发布验收核查 |
| MEDICAL_MIGRATION_BACKUP_RECEIPT_REF | update 必需的实际备份回执引用；恢复能力另行核验 |

取得获批准输入后执行对应动作：

```bash
.lld-data-work/venv/bin/python scripts/run_migration.py validate
.lld-data-work/venv/bin/python scripts/run_migration.py status
.lld-data-work/venv/bin/python scripts/run_migration.py update-sql
.lld-data-work/venv/bin/python scripts/run_migration.py update
```

真实本地update执行176项changeset，包括本人路由/上下文、账户操作/审计、明确身份绑定、邀请/接受路由及批准角色版本。包装命令使用固定changelog/MySQL driver、严格模式和STRICT解析，使用分析发送关闭；秘密经原生环境注入。master.xml核查MySQL 8.4、数据库及server_uuid，不符合目标时HALT；生产、历史及恢复继续验收。

四项迁移入口检查通过双制品全部资源、原生版本/帮助、四项数据库动作及必需参数/摘要拒绝；制品摘要由当前Java回执绑定。本地176项迁移已执行，生产权限、恢复、历史及领域业务继续验收。

## IMP-04 企业 SQL 控制

原生Java构建通过65项，共享26、迁移1、企业12及身份26。TenantAccessTest核查135张企业表、缺少上下文拒绝、显式企业列、固定处理器、查询缓存和未授权Mapper；PermissionStructureTest核查封闭Schema、U64、十四类资源及水位SQL；InvitationSecurityTest核查实际随机凭据的认证加密、HMAC、封闭输入及十四条邀请SQL。全部聚合工程及十个子模块SUCCESS。

业务模块和 Mapper XML 进入 medical-persistence-3.9.2.jar 及实际 ruoyi-admin 应用依赖。新增源码、Mapper、POM、框架配置和制品均由原生构建回执绑定。SSO、合法 SQL、两企业授权、撤权、事务锁定及完整 IC-04 仍需真实环境运行；不通过本地测试创建业务身份。

## IMP-04 独立 OIDC 与会话配置

以下配置只用于 medical.identity.mode=oidc；本地系统账户使用下一节的 local 配置。外部 OIDC 的实际值及批准引用继续待提供。

原生 Java 构建执行 OidcSecurityTest 九项检查，覆盖真实随机 nonce、S256/verifier、证明重复处理与变更拒绝、必需配置拒绝及身份错误的完整共享 Schema 往返；IdentityDirectoryTest 两项检查覆盖实际 SecurityContext 缺少认证和必需分类配置的拒绝；OidcSessionBindingTest 一项检查覆盖未认证主体在会话绑定入口的拒绝。medical-identity 已进入正式应用，认证回调接入平台身份及应用会话登记，API 准入接入主数据库当前身份和会话查询。SSO、身份及会话 SQL、Redis、Cookie、CSRF、Origin 和账户的 HTTP 验收尚未运行。

BackchannelLogoutTokenTest 三项检查核验缺少配置、缺少 Token 及真实资料登记包作为 Token 的拒绝。BackchannelLogoutVerifier 使用 Nimbus JWT parser、固定 JWK URI 和批准算法检查签名，LogoutClaimsValidator 检查注销声明。BackchannelLogoutPersistence 连接持久回执、固定会话集合、主数据库撤销、Redis 清除及恢复调度；ID-06 标准认证与应答继续关闭。真实签名、密钥轮换、时间、声明、并发及恢复仍待实际 SSO 验证。

以下参数通过批准环境和秘密服务提供；当前实际值及批准记录保持 UNKNOWN，不在仓库保存客户配置或秘密。

| 配置字段 | 必需内容与验证 |
| --- | --- |
| medical.identity.registration-id | 唯一服务端登记身份；浏览器不能选择其他 registration |
| medical.identity.issuer | 固定 HTTPS issuer；与 Spring provider 发行者完全相等 |
| medical.identity.callback | 固定 HTTPS /auth/callback；与客户端 redirect-uri 完全相等；认证过滤器仅接受 GET |
| medical.identity.browser-origin | 已登记 HTTPS origin，无路径、查询、fragment 和 userinfo；变更请求精确核对 Origin |
| medical.identity.return-target | 已登记本地绝对路径；拒绝 URL authority、编码路径、查询、fragment 和目录跳转 |
| medical.identity.directory.classification | 平台身份元数据经过批准的分类值；非空、最多24个 Unicode code point，必须能够使用 UTF-8 编码；没有预设客户值 |
| medical.identity.backchannel.clock-skew | 经批准的非负 Duration；用于注销 Token 时间核验，没有预设阈值 |
| medical.identity.backchannel.connect-timeout、medical.identity.backchannel.read-timeout | 经批准的正值 Duration；JWK 请求连接与读取超时；禁止自动重定向 |
| medical.identity.backchannel.signature-algorithms | 与实际 ID Token 客户端登记及发行者能力相符的非空 SignatureAlgorithm 集合；没有自动选择算法 |
| medical.identity.logout.actor-id | 获批准服务执行身份 UUID；持久注销及恢复审计使用 |
| medical.identity.logout.recovery-batch-size、medical.identity.logout.recovery-interval | 获批准正值恢复批量及 Duration；调度器只处理既有 PENDING 回执的固定会话集合 |
| spring.security.oauth2.client.registration.* | 实际 client-id、由秘密服务注入的 client-secret、authorization_code、openid、固定 redirect-uri 和 provider；客户端认证方法使用 client_secret_basic/client_secret_post |
| spring.security.oauth2.client.provider.*.issuer-uri | 已批准实际发行者；authorization、token、JWK 和可选 UserInfo 端点要求 HTTPS |
| server.servlet.session.timeout | 经过确认的正值 Duration，缺少时立即失败；没有预设批准阈值 |
| spring.session.redis.repository-type | indexed；关联会话使用正式 Redis repository |
| spring.data.redis.host、spring.data.redis.port、spring.data.redis.ssl.enabled | 显式批准 host、port 及启用 TLS；Redis 认证、连接权利和存储保护由目标配置及运行验收确认 |

Cookie 名称为 MEDICAL_SESSION，Secure、HttpOnly、SameSite=Lax，path=/。OAuth2 授权请求、state、nonce、verifier 及访问/刷新令牌保存于服务端会话。平台目录绑定 masterDataSource，运行前需由独立迁移岗位完成010身份和011会话表并核验运行账号权限。应用 API 接受对应 OIDC 身份并核查当前账号和会话均为 ACTIVE，完整比较实际绑定；企业成员、项目、动作和当前权限版本由完整 C02 核查。ID-03/04 会话与企业切换、ID-05/06 注销及去重、内部 mTLS 身份与前端会话使用继续实施，当前检查不登记其业务通过。

## IMP-04 本地独立账户运行

用户确认的 BACK-LOCAL-01 使用系统自己的用户名和密码。当前应用位于 http://127.0.0.1:18080/auth/login；用户名 liuhaoxian 来源于实际本机操作系统账户。登录密码读取项目 .implementation-work/runtime/owner-credentials.json 的 password 字段；文件权限0600，父目录权限0700，整个目录被 Git 忽略。不得将文件内容放入聊天、日志、命令参数或源码。

MySQL 8.4.3及Redis 7.4.8真实运行，端口13306/16379，数据库medical_local；176项迁移、135企业表及13平台表已经建立。medical_runtime只具有当前数据库DML权限，迁移采用独立管理员凭据；server_uuid及版本读取私密service-identity.json。

application-medical-local.yml 显式设置 medical.identity.mode=local、本地 browser-origin、indexed Redis、30分钟会话超时及 LOCAL_AUTH_METADATA 分类；这些值用于本地认证元数据，不表示生产参数或客户资料分类获得批准。两个秘密环境变量 MEDICAL_LOCAL_DATABASE_PASSWORD 和 MEDICAL_LOCAL_REDIS_PASSWORD 由运行脚本从私密 service-secrets.json 读取并注入。

应用正常停止及重新启动，在项目根目录分别执行：

```bash
.lld-data-work/venv/bin/python scripts/local_runtime.py stop-application
.lld-data-work/venv/bin/python scripts/local_runtime.py application
```

启动命令将实际构建 jar 保存于 .implementation-work/runtime/applications/<sha256>/ruoyi-admin.jar，文件权限0400；计算并比较源文件与保存文件摘要以后启动独立程序文件。application-deployment.json 记录实际 PID、操作系统身份、制品路径和摘要，每次启动另行保存 UUID 历史记录。停止命令核对记录、实际程序摘要、Java 路径和进程命令，再发送 SIGTERM 并等待正常退出。启动命令发现已有 application.pid 时立即拒绝重复启动。MySQL/Redis 在本机重新启动后，使用 scripts/local_runtime.py start 启动项目自己的原生持久服务；已经运行的服务不重复执行初始化。

本地迁移前备份和更新命令为 scripts/local_runtime.py migrate；实际服务秘密、双制品摘要、目标身份、BACK-LOCAL-01 引用及备份回执由脚本注入。备份使用原生 mysqldump 及 gzip，恢复能力必须另行验证。首次管理员通过真实设置表单建立；系统没有预置密码、演示账户或公开注册。密码恢复工具要求本机数据库管理员权限并核对首次管理员创建人、既有凭据所有者及权限；原生 flock 拒绝同时恢复。新凭据保存于0600候选文件并同步文件及目录，数据库提交以后原子替换 owner-credentials.json。中断时保留候选文件，必须依据实际数据库状态确认接收，不自动重发更新。

本机所有者需要恢复管理员密码时执行；成功以后读取当前凭据文件，旧登录会话立即被拒绝：

```bash
.lld-data-work/venv/bin/python scripts/local_runtime.py recover-owner
```

当前本地HTTP/数据库/运行版本检查通过三十一项，覆盖真实所有者登录、账户响应、Cookie、匿名拒绝、错误密码、CSRF、Origin、再次首次设置拒绝、注销、数据库重读、五次失败锁定、既有凭据恢复、同时恢复拒绝及程序身份。LOCAL-01—06的读取和拒绝、本人密码成功变更、两个旧会话403、新凭据登录302、原请求重放、异参冲突、最后管理员保护、独立HMAC计算和完整审计字段重读已验证。ID-03返回共享Envelope及七项data字段；当前管理员没有企业成员，成员/动作列表及上下文为空，实际上下文及稳定UUID已重读；CSRF可以完成注销。GET /api/session/identity-binding返回本人四字段认证绑定，local模式使用实际账户UUID和空issuer/sub；匿名请求、未知查询及缺少版本被拒绝。账户页面显示系统独立账户和真实UUID，浏览器以当前账户响应核查。ID-04验证未加入企业的拒绝及重复请求未保存业务记录，匿名、CSRF、Origin、字段、Header和版本冲突有明确结果。五项本地Chrome DOM及数据库检查见后续浏览器命令；新增真实人员和其他账户管理成功路径、全部正式页面流程及视觉验收继续保留未验证状态。

运行检查会更新实际本机管理员密码，结束后使用当前 owner-credentials.json；检查实际证明锁定期间正确密码被拒绝，恢复后失败次数清零、凭据版本增加且旧会话403。自然到期和进程中断恢复另行验证。Redis indexed repository 的注销使用原生活动期限删除和短期事件元数据保留；依据已完整读取的 [Spring Session 3.5.7 源码](https://raw.githubusercontent.com/spring-projects/spring-session/3.5.7/spring-session-data-redis/src/main/java/org/springframework/session/data/redis/RedisIndexedSessionRepository.java)，实际检查活动标记删除、事件元数据期限及旧会话401。

```bash
.lld-data-work/venv/bin/python scripts/check_local_identity.py --execution 20261002-21
```

IC-04-LOCAL-HTTP-20261002-21通过31项真实检查，144项输入及10项输出，SHA-256为7147f507ca4d26db1a18047035cf239916725b4c0db82f8d2205c16775ed77aa。秘密文件不进入证据，CSRF仅保存摘要。当前20261002-37程序SHA-256为472427f375ce406615bd9e3d7f5a7c4ae56cda895d7212d15f9187241a7dfdcc，PID87949，部署UUID921e64c5-76d5-48da-8ce6-f14b166e93a2；全部42项工单、57业务及17非功能继续执行，重新检查使用新execution。

本地管理员从/local-auth/account.html进入/local-auth/management.html；本人页面提供密码变更，管理员页面提供账户创建、显示名称/角色修改、状态决定及其他人员密码重设。LOCAL-01—06固定字段由接口补充设计§4.6维护。变更要求Schema版本、Idempotency-Key、CSRF和当前Origin；提交结果未确认时保留原请求身份及公开版本，重新登录后使用原输入重放。浏览器不持久保存密码。私密local-management-secret.json只保存固定HMAC秘密及key_id；当前有效操作存在时文件缺少会阻止重新生成秘密。owner-credentials.json在真实本机检查中通过私密候选文件及原子替换保持更新；接收人员自行保存产品操作使用的新密码。

## IMP-04 本地真实浏览器检查

tests/browser/package-lock.json固定@playwright/cli 0.1.22及官方Playwright依赖。使用当前本机Chrome和独立headless会话；所有业务请求访问当前真实应用，截图、视频及视觉检查未执行。首次安装或者依赖目录需要重建时执行：

```bash
env TMPDIR="$PWD/.implementation-work/migration-check-tmp" npm_config_cache="$PWD/.implementation-work/npm-cache" npm ci --prefix tests/browser --no-audit --no-fund
.lld-data-work/venv/bin/python scripts/check_local_browser.py --execution 20261002-10
```

该execution已经执行，重新检查使用全新目录身份。命令读取当前私密管理员凭据，以原生flock拒绝并发恢复；通过真实表单提交本人密码变更，先保存0600候选凭据并同步文件和目录，页面确认成功且新密码认证通过以后原子替换当前凭据文件。检查结束后使用当前owner-credentials.json。

五项真实Chrome DOM及审计重读通过实际登录/身份、最后管理员/重复用户名保护、请求及密码清除、本人密码更新、旧会话403、新密码302及注销204。原响应request_id、版本及审计相等，存储没有秘密，Cookie为HttpOnly/Lax。IC-04-LOCAL-BROWSER-20261002-10绑定150项输入及2项输出，SHA-256为a982bdf0496d084ff84e630bdc77ff7ff69b473d70bcc9fd1e4015d9ce29396c；完整企业/项目、其他人员及S17/S18继续验收。

## IMP-04 企业成员认证绑定

```bash
.lld-data-work/venv/bin/python scripts/check_member_identity.py --execution 20261002-07
.lld-data-work/venv/bin/python scripts/check_member_binding.py --execution 20261002-06
.lld-data-work/venv/bin/python scripts/check_member_invitation.py --execution 20261002-06
```

三项execution已经使用，分别通过四项成员数据库、四项Java真实身份比较及七项邀请/批准角色数据库检查，输入/输出为28/2、16/2及28/2。MemberInvitationBindingIT使用当前程序及原生PropertiesLauncher，独立于单元检查。客户企业、成员、身份、邀请、路由及岗位数量均为零；合法邀请/权限、OIDC历史成员及多企业继续验收。

## IMP-04 企业权限决定

```bash
.lld-data-work/venv/bin/python scripts/check_permission_decision.py --execution 20261002-05
```

IC-04-PERMISSION-20261002-05通过五项真实无企业管理员拒绝检查，135项输入及1项输出，SHA-256为a5bddc6f1fb857ff141bc623c4c7f0d96d937e72b26ab09f9f372b9087479bcd。合法授予、撤销、停用、重放、并发及界面仍需获授权企业及人员；重新执行使用新execution。

## IMP-04 邀请创建和接受

```bash
.lld-data-work/venv/bin/python scripts/check_member_invitation_flow.py --execution 20261002-02
```

IC-04-INVITATION-FLOW-20261002-02通过五项真实入口拒绝，135项输入及1项输出，SHA-256为b179b208405fcbd97ad1e995e941ae6ead60a1af4197e8634fad3c5ed2729131。ID-07/08包含批准角色、原管理授权版本、独立认证加密、本人路由、期限、成员/授权、水位、审计及Outbox；批准角色配置写入、合法业务和界面继续实施。local运行脚本通过私密invitation-response-secret.json注入MEDICAL_INVITATION_RESPONSE_KEY及MEDICAL_INVITATION_RESPONSE_KEY_ID；既有邀请操作存在时不重新生成秘密。独立目的INVITATION_RESPONSE，UUID身份2c397664-7c5d-499c-b2cf-b5498dd6307b，父目录0700、文件0600；其他环境须提供批准的同名配置，秘密不进入浏览器长期存储、URL、路由、审计或日志。
