# 需求、设计与开发完整性核对

核对日期：2026-10-11。代码基准：本地 `main`，提交 `2fb17df`。核对开始时工作区没有未提交修改。

## 统计口径

| 范围 | 数量 | 当前状态 |
| --- | ---: | --- |
| 全部实施任务 | 42 | 17 IN_PROGRESS、24 TODO、1 IMPLEMENTED、0 ACCEPTED |
| 产品功能任务 IMP-04—35 | 32 | 每项仍有开发或业务集成缺口；其中15项已有明确局部实现，17项尚未形成完整业务流程 |
| 其他工程、输入、验收与交付任务 | 10 | IMP-01—03、36—42；IMP-02登记IMPLEMENTED，其余9项仍在进行或待处理 |
| 业务验收 AC-01—57 | 57 | 19 NOT_RUN、38 BLOCKED、0 PASS |
| 非功能验收 NFR-01—17 | 17 | 17 NOT_RUN、0 PASS |

按照任务表状态，41项尚未登记完整实现。32项产品功能任务是本次审查采用的任务数量，每项包含多个功能，工作量并不相同。不能据此计算代码完成百分比，也不能将全部任务理解为尚未开始。

15项局部实现包括IMP-04/05/06/07/08/09/10/11/12/13/14/16/26/27/29。IMP-14虽仍登记TODO，当前源码已经包含Harness专业插件、工具范围和模型适配，因此计入局部实现。

17项尚未形成完整业务流程包括IMP-15/17—25/28/30—35。部分已有Schema、迁移、通用依赖或设计定义；这些内容没有单独证明相应业务能够完成。

统计来源为[实施任务计划](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/实施任务计划.md:198)，业务和非功能验收来源分别为同文件第92行及第156行。验收状态仅说明已有登记；本次没有重新执行历史测试，也没有将历史测试数量转换为当前业务通过数量。

## 产品功能逐项核对

“已有部分”表示当前存在相应业务代码；“流程待完成”表示当前没有发现足以完成该任务目标的业务调用与页面组合。每行只计算一个IMP任务。

| 任务 | 功能 | 当前已有内容 | 尚需完成的内容 | 依据 |
| --- | --- | --- | --- | --- |
| IMP-04 | 身份、企业与项目授权 | 登录、企业申请、岗位、成员、授权与项目接口；运行令牌签名和服务身份组件 | 持久运行身份、令牌续期、内部动作授权与Worker完整连接，以及完整客户授权流程验收 | E04、E13 |
| IMP-05 | 幂等、对象、事件与审计 | 原操作查询、对象传输、版本与依赖授权、审计和Outbox基础 | 全部领域生产者与消费者接入；跨处理阶段的业务完成回执和完整恢复 | E05、E08、E13、E26 |
| IMP-06 | 页面框架与管理模块 | 正式Vue、Router、S01业务工作台、账户、企业、项目及多个业务页面 | 全部管理入口和其余设计页面的完整功能与交互验收 | E06、EUI |
| IMP-07 | 模型、预算与结算 | 预算创建/批准/历史、供应商适配、精确金额组件 | 模型配置及发布管理、企业与父运行原子预留、真实调用保存、未知费用核对和结算 | E07 |
| IMP-08 | 上传、解析与阅读 | 批量上传页面、分段传输、完成命令；七格式解析和原生扫描组件；重新处理请求与S04处理队列读取 | 处理Worker、结果持久化、OCR及复杂内容 | E08 |
| IMP-09 | 索引、检索与网络资料 | S04获授权项目FULLTEXT检索、固定结果集合、每页许可复验、空结果、原文位置和分页 | 正式任务快照与固定检索配置、Qdrant、索引Worker、重建后历史定位、网络采集和候选资料核准 | E09、EAPI |
| IMP-10 | 资料核准与生命周期 | 资料列表与范围筛选、资料集创建、成员修订、固定历史、KN-06固定版本详情、KN-07原件与解析内容读取、KN-08重新处理请求、KN-09人工核准、KN-10项目适用决定、KN-16资料引用与影响、KN-17停用/回收/恢复/永久清理申请、删除作业查询、逐项删除回执、全部确认后的PURGED更新、KN-20至KN-22文件夹/标签/收藏维护、任务成果目录及S04/S05页面 | 六类存储删除执行器、成果来源关系；处理Worker由IMP-08继续实施 | E10 |
| IMP-11 | 模板与适用条件 | 模板定义保存、核准入口、适用查询及对应页面 | TP-03/04、完整规则发布、目录人工适用决定、后续任务采用与完整管理交互 | E11 |
| IMP-12 | 创建任务与计划 | 创建草稿和任务、来源选择及快照、计划修订、确认与退回 | 发布配置与实际执行消费者连接；从核准资料和模板创建任务并进入完整运行 | E12、E13、E29 |
| IMP-13 | Temporal运行控制 | Workflow、DAG、固定输入、投递与Signal、运行快照、SSE、取消请求 | 持久RunActivities、专业Activity、运行启动/继续、当前授权、完整取消终态和恢复 | E13、EWORK |
| IMP-14 | Harness与专业工具 | 专业插件、七类角色工具范围、模型适配和私有通信 | Python宿主、专业Activity、IA/IN业务接口、真实调用/费用、全部角色、取消和进程退出证明 | E14、EPRO |
| IMP-15 | 规划、研究、生成与检查 | 专业角色和工具定义、执行依赖 | 规划、研究、章节生成、独立来源/字段检查、整稿合成及完整实际执行 | EPL、EWORK、EPRO |
| IMP-16 | 文档编辑与候选采用 | 正文读取/修订、稳定节点、表格、字段、草稿和版本冲突比较 | 图片/公式完整呈现、全文选区、服务器候选、历史目录、部分候选采用和页面完整交互 | E16 |
| IMP-17 | 产品技术要求 | 通用模板、字段、正文与导出基础 | 八组专属字段、多型号与软件要求、指标方法对应、标准年代号、专属检查与格式 | EPL、EPRD |
| IMP-18 | 注册目录、监管与质量体系 | 通用目录/字段定义 | 六章38节点专业目录、监管综述、质量体系内容与附件、完整性检查及生成流程 | EPL、EPRD |
| IMP-19 | 非临床、基本原则与风险 | 通用文档与证据结构 | 研究方案和真实结果分别管理、五列基本原则、风险控制证据、条件判断及专业检查 | EPL、EPRD |
| IMP-20 | 临床与免评价 | 通用模板与证据结构 | 临床评价和免评价独立路径、目录核查、比较证据、专业生成与审查 | EPL、EPRD |
| IMP-21 | 说明书与标签 | 通用文档编辑基础 | IFU十四组、独立标签、警示内容、编号规则及跨文件一致性检查 | EPL、EPRD |
| IMP-22 | IVD、变更与延续 | 通用路径、模板与项目属性定义 | 各路径的兼容核准模板、资料差异、专属检查和完整任务采用 | EPL、EPRD |
| IMP-23 | 正文检查、人工审查与签发 | 正文修订已有审查失效处理，存在审查/签发数据结构 | 当前正文检查、选区批注、问题处理、退回、审查确认、再次认证、同版签发与重读 | EPL、E16、EAPI、EUI |
| IMP-24 | 共享事实与变更影响 | 版本、依赖授权及FactUsage基础 | 共享事实变更覆盖所有使用位置、依据更新影响、撤权对索引/模型/历史/导出的完整传播 | EPL、E16、EAPI |
| IMP-25 | Gru | 设计、消息与动作定义 | 对话和选区处理、明确采纳、局部修订、摘要追溯、习惯管理及删除生效 | EPL、EUI、EAPI |
| IMP-26 | 导出与成果转存 | DOCX/PDF/Markdown渲染组件、EX-01—04、固定输入与核验制品、授权下载、持久状态、当前版本导出记录、导出页面、S04成果目录与成果转存关系 | 目录页码、附件顺序、渠道核准、视觉版面检查、签发创建入口、服务重启自动续办 | E26、EAPI |
| IMP-27 | 运行工作区与人工继续 | 运行详情、SSE、步骤/预算展示和取消请求 | 人工继续、完整会话订阅协调、候选结果显示、完整页面操作及统一部署验证 | E27 |
| IMP-28 | 评测、运营与发布 | 版本和审计等基础结构 | 固定样本评分、同样本版本比较、统计和审计查询、反馈处理、能力发布准入与页面 | EPL、EUI、EAPI |
| IMP-29 | 场景目录 | 场景元数据查询、五类规划说明、未定义入口限制 | 完整规格保存/读取、发布决定、正式任务入口及固定版本采用 | E29、ESC |
| IMP-30 | 客户B/S连接 | 需求和接口设计 | 连接配置与授权、真实同步、映射冲突处理、失败恢复和远端重读；需要客户接口输入 | EPL、EAPI、EUI |
| IMP-31 | 法规比较 | 场景目录中的规划要求 | 独立规格、比较流程、变化证据、人工审核与正式成果 | E29、ESC |
| IMP-32 | 报告生成 | 场景目录及通用导出依赖 | 独立报告规格、完整生成审核流程、正式成果 | E29、E26、ESC |
| IMP-33 | 翻译 | 场景目录中的规划要求 | 独立翻译规则、术语及格式处理、审核和正式成果 | E29、ESC |
| IMP-34 | 市场分析 | 场景目录中的规划要求 | 独立资料范围、研究规则、证据、分析审核与正式成果 | E29、ESC |
| IMP-35 | 多模态问答 | 场景目录和文件解析依赖 | 独立输入规则、多模态处理、证据回答、人工责任和正式成果 | E29、ESC |

## 设计页面核对

设计范围采用《交互原型说明》的14个页面。设计稿HTML中的演示按钮和内容不计入正式Vue功能；嵌入任务详情的组件可以承载设计功能，判定不要求路由名称完全相同。

| 设计页面 | 当前对应实现 | 仍有缺口 |
| --- | --- | --- |
| S01 工作台 | 正式业务工作台已经接入项目、当前人员待办、重点项目进度和六项场景入口 | 合法客户企业数据、键盘、窄屏及持续更新验收 |
| S02 系统管理 | 若依管理组件、导航、企业与岗位页面 | 全部18项入口在当前企业权限下的完整接入与验收 |
| S03 项目中心 | projects.vue、project-write.vue | 设计要求的资料状态、任务进度等完整业务汇总及成功流程验收 |
| S04 资料中心 | source-catalogue.vue、source-processing.vue、artifact-catalogue.vue、collections.vue、source-uploads.vue、source-search.vue；资料列表筛选、授权分页、全部资料/收藏/已停用/回收区常用视图、处理队列、任务成果目录、成果转存、文件夹/标签/收藏维护、获授权项目全文检索、固定集合和原文位置已经接入 | 处理Worker结果更新、网络候选和客户资料验收 |
| S05 资料详情与核准 | 固定资料详情、历史版本、原件分段、解析内容分页与定位复验、重新处理、审核清单、人工核准、项目适用决定、使用关系、生命周期及删除作业状态已经接入 | 网络候选处理及成果转存客户验收 |
| S06 任务列表 | tasks.vue、TaskController | 随完整运行变化的待办、步骤和失败状态，以及后续动作 |
| S07 创建任务与计划 | task-create.vue、task-plan-editor.vue、task-plan-return.vue | 核准资料/发布配置到实际生成的完整流程 |
| S09 撰写与来源核验 | document-editor.vue、run-detail.vue等 | 完整来源面板、选区、候选采用、补证和局部重新处理 |
| S11 审查与签发 | 文档读取及相关数据结构 | 正式总稿审查页面、批注问题、退回确认、再次认证和签发 |
| S13 Agent管理 | 预算管理与历史页面 | Agent配置、模型连接管理、角色工具配置、评测发布与任务记录 |
| S15 评测与运营 | 审计及运行基础数据 | 专业评分、版本比较、业务统计、筛选与审计导出页面 |
| S16 企业空间与连接 | 企业、岗位和成员管理 | 服务状态、外部连接授权、验证与同步错误处理 |
| S17 拒绝与恢复 | 当前身份、权限拒绝及清理处理 | 全部业务异常的可执行恢复，尤其人工继续、来源与导出恢复 |
| S18 项目详情 | project.vue及项目/任务/模板入口 | 资料证据数量、待审决定、运行进度等完整项目汇总 |

页面表依据为EUI与[交互原型说明](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/交互原型说明.md:51)。本次没有使用截图或执行视觉比较，不给出版式相似度和像素一致性结论。

## 当前主要业务阻碍

1. **资料尚不能完整成为生成依据。** 上传、原件及解析内容读取、人工核准、S04获授权全文检索、资料引用查询和删除作业回执已有实现，处理Worker结果保存、索引Worker、六类存储删除执行、正式任务检索及网络候选仍需形成完整流程。
2. **任务尚不能完成全部专业执行。** 任务与计划已有持久实现，Temporal和Harness组件仍需连接持久业务Activity、模型结算与专业节点。
3. **专业申报文档规则仍需实现。** 六项专业任务IMP-17—22覆盖不同文档和注册路径，通用正文编辑与模板结构没有完成这些专业要求。
4. **文稿尚不能完成审查签发与正式成品交付。** 公开导出执行、授权下载、失败重试和当前版本记录已经接入；人工审查、再次认证、同版签发、渠道核准和视觉版面检查仍未完成。
5. **扩展产品功能仍有独立缺口。** Gru、评测运营、能力发布、客户连接和五类额外场景继续各自完整范围。

当前缺少的外部输入包括获授权客户资料与专业核准、岗位及签发责任、模型正式发布配置与费用规则、客户B/S接口、五类场景规格、目标部署及服务条件。它们影响相应成功流程的执行与验收；明确存在的编码缺口仍然需要继续开发。

## 证据索引

| 编号 | 当前来源 |
| --- | --- |
| EPL | [实施任务计划](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/实施任务计划.md:198) |
| EPRD | [PRD专业文档要求](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/PRD.md:429) |
| E04 | [IMP-04开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-04.md) |
| E05 | [IMP-05开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-05.md) |
| E06 | [IMP-06开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-06.md) |
| E07 | [模型及结算剩余内容](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-07.md:168) |
| E08 | [扫描解析与Worker范围](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-08.md:66) |
| E09 | [检索开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-09.md) |
| E10 | [资料集实现与生命周期](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-10.md:5) |
| E11 | [模板实现](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-11.md:3) |
| E12 | [任务和计划开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-12.md) |
| E13 | [调度剩余开发](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-13.md:43) |
| E14 | [Harness当前实现与范围](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-14.md:15) |
| E16 | [编辑剩余开发](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-16.md:35) |
| E26 | [导出剩余开发](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-26.md:17) |
| E27 | [运行页面开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-27.md) |
| E29 | [场景目录开发记录](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/docs/development/IMP-29.md) |
| EUI | [当前正式路由](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/web/src/router/index.js:30)、[当前首页](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/web/src/views/index.vue:5)及web/src/views/medical组件 |
| EAPI | business/medical-persistence/src/main中全部Controller/HTTP入口，与web/src/api/medical消费接口的对应检查；审查、签发、公开导出、检索、Gru、评测和连接仍缺少完整业务入口组合 |
| EWORK | [WorkflowWorker依赖外部Activity实现](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/business/medical-workflow/src/main/java/com/ruoyi/medical/workflow/WorkflowWorker.java:12)、[ProfessionalActivity接口](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/business/medical-workflow/src/main/java/com/ruoyi/medical/workflow/ProfessionalActivity.java:8)；生产源码未找到持久RunActivities完整实现 |
| EPRO | [专业插件源码](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/professional-executor/src/index.ts:21)、[当前开发配置](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/professional-executor/medical.patch.yml:10) |
| ESC | [场景读取逻辑](/Users/liuhaoxian/Downloads/我的/AI项目/医疗设备厂商agent/business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ScenarioCatalogueService.java:39)；文稿适用规格尚不可用 |

## 核对边界

初始统计来自需求、设计HTML源码、页面说明、实施任务、开发记录和当前生产源码的静态核对。核对后已继续实现S01工作台、S04授权全文检索与资料列表、S05固定版本详情、人工核准和项目适用决定，并执行Java编译、四项定向测试、迁移资源检查、前端类型检查和生产构建。本次没有执行模型请求、客户流程或视觉比较。当前完整业务、部署和客户验收仍需相应运行证据。
