# PR-06 项目中心与项目详情

当前状态IN_PROGRESS，文档CURRENT，任务来源PROTOTYPE-RESTORATION-20261022。依据PRD项目全流程、设计稿S03及product-workspace.js项目详情，当前采用用户已确认的若依导航与品牌。

项目中心采用项目名称、产品名称、器械类别、注册路径、项目阶段、资料状态、任务进度、最近更新及操作九列。阶段对应现有PENDING、ACTIVE、PAUSED、ARCHIVED，界面采用待配置、使用中、暂停和归档。查询、重置、每页条数、上一页与下一页均通过实际授权接口读取，条件变化不能继续使用原列表游标。产品属性与资料状态需要实际来源和授权，缺少信息时保持待确认，不生成完整度比例。

PJ-01：Given真实获授权项目，When进入项目中心并查询或重置，Then按原型显示九列业务信息，负责人使用获授权成员姓名，版本标识可独立展开。

PJ-02：Given实际授权目录及固定筛选，When切换条数或前后分页，Then列表连续、没有重复，完整范围总数与当前页统计明确区分，改变筛选清除旧结果。

PJ-03：Given实际项目及其已核准属性和资料，When进入详情，Then展示实际申请资料清单及逐节点状态；没有属性、证据或授权时明确显示需要确认的对象，不推算完成比例。

已读取ProjectController、ProjectService、ProjectProtocol、ProjectMapper、projects.schema.json及project-attributes.vue。项目接口完整保留属性值、KNOWN/UNKNOWN、声明原因和来源版本。产品名称、器械类别、注册路径和产品类型采用业务控件；其他属性继续保留完整JSON及数值精度。未知属性保留原因，已有声明需要实际来源版本。资料逐节点完整度继续实施，没有执行视觉检查。

项目中心已提供原型九列、查询、重置、每页条数、前后分页及组织管理入口。目录使用独立project-workspace-directory Schema，同一次原有EnterpriseAccess事务读取完整项目属性、实际人员名称、可见资料核准数量与获准读取的任务数量。资料统计包含当前账户有权读取的企业公共及本项目资料版本；SESSION资料与已回收资料不进入统计，不计算完成比例。完整项目及属性仍由原ProjectService验证版本、摘要、声明依据与当前授权。

产品名称采用product_name，器械类别采用device_class，申请路径继续采用已有application_path。只有KNOWN属性进入产品名称查询，UNKNOWN及其原因保持原值。项目状态沿用既有四项值，目录总数与当前游标分别处理。分页使用受保护目录游标，绑定完整筛选、账户、会话、身份模式、当前组织上下文及授权时间边界；前端只保留服务器返回的原游标，筛选或组织变化清除旧记录及分页。原项目定义Schema和写入接口保持原协议。

ProjectDirectoryCommittedIT在真实数据库及实际权限校验器取得的读取范围内，通过完整筛选总数、前后边界、项目/产品查询、独立Schema及无效参数检查。严格类型检查、646项固定Schema和492项调用检查通过，证据.implementation-work/project-profile-release-types.log。

通过PJ-02真实创建接口登记SIMULATION本地工作区草稿3f77eb99-511e-4609-bead-3f2f4759c8c3，阶段PENDING，产品属性没有登记确认值。原操作已核查为COMPLETED，原始参数和请求身份保存在受保护凭据.implementation-work/runtime/project-directory-workspace-input.json，证据.implementation-work/project-directory-input.log。

实际超管及普通账户均读取两个获授权项目，并通过逐项目完整内容、实际可见来源数量、全部分页、条件变更、账户范围及游标校验。无成员读取许可时不返回其他成员名称。原项目列表接口的完整筛选总数通过独立分页检查。证据.implementation-work/project-workspace-api-checks.json及project-directory-pagination-checks.json。

真实Chrome通过九列业务表格、搜索空状态、重置、PENDING阶段筛选、实际资料核准数量及项目资料入口、每页条数和分页检查。证据.implementation-work/project-directory-ui-checks.json。

项目属性通过实际资料目录及原件摘要选择依据；本项目资料与企业公共资料在原授权范围内读取。项目建档通过实际成员目录选择负责人，并采用实际企业公共资料作为建档依据。原请求身份、原版本核查、输入恢复及未保存输入处理继续使用已有流程。真实Chrome通过业务控件、未知原因与确认声明校验、来源版本选择、继续编辑和放弃输入；重新读取的完整项目及依据与编辑前一致。证据.implementation-work/project-profile-controls-checks.json。

本地来源的初始生命周期登记依据既有source_version元数据完成，保留原件摘要、权利版本与审查内容；来源完整读取返回200。登记凭据.implementation-work/runtime/source-lifecycle-registration.json。

真实Chrome完成PJ-04属性保存，原操作1e46979a-766d-4f41-94c4-4df5af8c8b23为COMPLETED，项目版本2，属性版本50ece572-c0eb-47f4-94ab-ef5121e9bf0e。四项产品属性保持UNKNOWN、null值和空依据，并保存实际未知原因。原项目名称与建档依据保持完整，重新读取的全部属性与实际提交参数一致。证据.implementation-work/project-profile-save-checks.json；完整原参数与请求身份保存在受保护的project-profile-save-operation.json。当前生产构建继续验证，PJ-01/PJ-02/PJ-03保持IN_PROGRESS。

公共样式遵循ui-delivery/tokens.json中的24px标题、600字重、17px分区标题、8px卡片及6px控件圆角；桌面控件至少38px，移动端至少44px，继续使用现有深色主题。实际项目、规则、模板和保存审计页面的计算样式检查通过，证据.implementation-work/medical-design-token-checks.json，没有执行截图或视觉识别。
