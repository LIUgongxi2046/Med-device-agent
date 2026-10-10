# IMP-29 场景目录与未定义场景控制

任务来源为实施任务计划IMP-29、PRD§8.7、AC-14/15和CP-07，完整工单状态IN_PROGRESS。BACK-SCENARIO-CATALOGUE-01及FRONT-SCENARIO-CATALOGUE-01的配置元数据查询和规划要求页面登记IMPLEMENTED。完整场景规格、发布准入和正式任务采用继续原工单范围。

文档有效性CURRENT，last_verified为2026-10-08；当前代码为未提交源码，代码身份引用下方回执捕获的文件摘要。检索依据包括实施任务计划、PRD§8.7、LLD-后端CP-07、开发索引、IMP-12、企业事务及配置/项目许可实现。当前验证使用原本地实际服务及临时Spring应用；常驻应用保持自身已有制品范围。

Given当前企业成员及当前读取范围；When读取场景目录；Then目录仅展示实际数据库中的获授权SCENARIO版本，规划要求单独显示，不作为正式版本或演示数量。五类规划要求来源为PRD：法规比较、报告生成、翻译、市场分析和多模态问答。缺少独立规格及发布准入时不提供正式任务入口；旧上下文、未知参数、跨项目与撤权拒绝。全部读取使用原企业事务，响应保持封闭字段和no-store。

当前实现：GET /api/scenarios使用EnterpriseAccess原企业事务和受保护ScenarioCatalogueMapper，读取capability_version、configuration_head及version_registry；配置许可使用configuration.read/CONFIGURATION_READING，项目许可使用project.read/PROJECT_READING，项目必须ACTIVE。实际版本归属、登记种类CAPABILITY_VERSION、摘要和配置修订逐项核验；返回前重新核查全部配置和项目许可。查询容量沿用当前maximumVersions，超过容量明确拒绝。total来自当前获授权版本，规划要求独立返回。分页水位绑定当前主体、企业上下文、授权水位、项目、查询容量、全部版本元数据和规划内容；失效水位及集合以外的after分别返回CURSOR_EXPIRED和CURSOR_INVALID。

场景规格尚未核验时返回definition_status=UNVERIFIED及具体缺项，task_entry=null。文稿适用性依赖完整规格，document_type合法但尚不能核验时返回DEPENDENCY_UNAVAILABLE，不推定适用范围。五类规划要求保留使用角色、输入、适用知识、任务规则、输出、人工审核、失败状态和验收样本八项内容。规划说明不生成配置、评测或业务成果。

当前页面/scenarios提供明确查询、实际配置版本、状态、缺项、数量及下一页，规划要求单独展示；复用Layout、Router、Pinia、Axios、Ajv、Element Plus和session-panel。页面离开及企业身份变化清理读取状态，旧响应不进入当前页面；隐藏页面取消读取，恢复显示后按保留的筛选条件重新查询。未知标签明确拒绝。FrameworkFrontendConfiguration提供Spring页面入口，当前新页面尚未部署到常驻应用。

代码位置：business/medical-persistence中的ScenarioCatalogueController、ScenarioCatalogueService、ScenarioCatalogueSchema、ScenarioCatalogueRow、ScenarioCatalogueMapper、medical-mappers/ScenarioCatalogueMapper.xml及scenario-catalogue.schema.json；web/src/api/medical/scenarios.ts、protocol.ts、web/src/views/medical/scenarios.vue与router/index.js。接口表示引用backend-design/interfaces.openapi.yaml。

当前验证：实际Spring、MySQL及Redis集成通过；验证当前企业空配置目录、五类独立规划要求、原水位重读、当前项目读取、匿名访问拒绝、旧上下文、跨项目、未知/重复参数、查询容量、无效/失效游标及缺少文稿适用规格。原任务读取、草稿和人工计划拒绝检查保持通过。验证没有新增专业核准、模型回复或场景配置记录。前端严格类型和实际Schema运行检查通过。当前证据不覆盖非空正式场景、页面完整交互、新部署、Gru调用或已核准注册场景采用。

验证命令：`.lld-data-work/venv/bin/python scripts/check_task_plan_write.py --execution 20261008-SCENARIO-CATALOGUE-HTTP-02 --decision-http`及`.lld-data-work/venv/bin/python scripts/check_task_plan_frontend.py --execution 20261008-SCENARIO-CATALOGUE-FRONT-03`，分别使用300秒及120秒超时。

必要原始证据：Red引用.implementation-work/IC-12-PLAN-WRITE-20261008-SCENARIO-CATALOGUE-RED-01/output.log；当前接口回执.implementation-work/IC-12-PLAN-WRITE-20261008-SCENARIO-CATALOGUE-HTTP-02/receipt.json，SHA256为a56e1c7ac3f0f851726bdf5f0165858f356f045275b55b760cf9dbb85ccf59b9；当前前端回执.implementation-work/IC-12-PLAN-FRONT-20261008-SCENARIO-CATALOGUE-FRONT-03/receipt.json，SHA256为fe1bac09c8f6aa53abd5c908dace108799dbdd17e9df7bd45f0e6e6097c36bc9。两个当前检查均exit_code=0及inputs_unchanged=true，临时应用正常关闭，全部检查已结束。

剩余开发：完整场景规格保存/读取、当前发布决定与范围核验、正式入口和固定场景任务版本，以及Gru正式成果控制的完整集成；正式注册场景成功入口需要C06完整发布准入。五类专属流程分别引用IMP-31—35，当前特有处理规则按PRD保留UNKNOWN。专业批准、正式样本和生产环境分别登记，其他已定义功能继续开发。
