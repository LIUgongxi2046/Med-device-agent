# PR-06 企业与连接

## 任务身份与依据

任务来源PROTOTYPE-RESTORATION-20261022，状态IN_PROGRESS，文档CURRENT。已核查PRD 8.8 FR-08、AC-16/17，screen-design-map.csv S16，以及final-product.js中的部署方式、三个接入类别和连接管理表单。

## 当前实施范围

界面采用“企业与连接”标题、部署与企业服务分区、私有化部署/SaaS企业空间/配套服务协作导航，以及账号与单点登录、资料系统连接、专家服务协作三个管理入口。企业信息、角色、配置、审核、验证、同步与错误记录使用实际授权数据。企业和项目范围继续分别核验。

CustomerConnectorController已经提供配置创建、固定版本读取、人工决定、实际验证与原请求恢复、同步与恢复、运行读取和资料导入。当前页面需要接入这些能力，并补充获授权目录、实际状态、时间与完整处理记录。客户协议和凭据缺少时返回具体缺项；不填写不存在的客户身份、接口规格或连接成功状态。

## 验收要求

PR06-CT01：Given当前获授权身份，When进入企业与连接，Then原型三个部署导航及三个管理入口完整显示，并读取实际企业及连接状态。

PR06-CT02：Given当前项目与实际配置输入，When保存连接配置，Then形成固定草稿版本和同事务审计，刷新后完整读取；参数改变需要独立版本。

PR06-CT03：Given缺少客户规格、账号映射或受控凭据，When核查或验证，Then显示真实BLOCKED及具体缺项，不能显示连接成功。

PR06-CT04：Given已批准配置和有效客户服务，When执行验证、同步或原请求恢复，Then显示实际回执、影响范围和重试条件，不修改已确认资料。

PR06-CT05：When权限、企业或项目变化，Then清除旧配置及未发送输入，拒绝跨企业读取。

2026-10-23实际接口核验通过：连接业务资料与接口定义同事务保存、固定版本重读、原请求完整参数查询、相同请求重发、参数变化冲突、实际连接目录、企业成员名称目录，以及审核/验证/同步空记录读取。审核缺少业务资料摘要时返回422，缺少实际客户输入时返回503 DEPENDENCY_UNAVAILABLE，状态保持BLOCKED且connected=false。证据：.implementation-work/connector-business-profile-green.log、connector-business-profile-checks.json。

18项后端检查及前端严格类型检查通过，包含真实会话与正式任务目录数据库检查。证据：.implementation-work/prototype-connector-backend-release.log、prototype-connector-audit-types-verified.log。

实际Chrome核验通过三类部署导航、真实身份弹窗、资料连接业务表单、草稿目录、原请求确认、缺少输入时的操作阻止、三类处理记录及刷新。计算样式核验页面间距28px、标题24px、文字颜色#243247、卡片圆角8px。证据：.implementation-work/connector-workspace-business-verified.log、connector-workspace-business-checks.json、connector-workspace-navigation-green.log。

实际普通账户与匿名请求核验通过：匿名读取需要认证，普通账户的工作区操作集合为空，配置/目录/审核记录/原请求/成员名称读取及连接创建均返回拒绝。证据：.implementation-work/connector-permission-green.log、connector-permission-checks.json。没有执行视觉检查，真实客户验证与同步仍为NOT_RUN。账号与专家服务的独立配置保存、企业业务名称及完整客户接入仍继续实施，任务保持IN_PROGRESS。

当前新增连接工作区状态与获授权配置目录、连接业务资料固定Schema、与接口配置同事务保存的名称/负责人/说明、审核摘要绑定及原请求只读查询。接口规格继续使用原固定定义，业务资料单独保存并记录同步审计。当前企业连接四项管理许可仅适用于用户指定的本地超管及SIMULATION工作组织，保留授权审计，不创建客户连接或客户批准。界面和完整实际业务验证继续实施。

业务资料范围登记为当前企业或指定项目；真实客户资料始终指定一个同步目标项目，保留独立项目权限核验。负责人选择来自当前企业实际有效成员，固定业务资料摘要参与人工审核及运行前核验。验证与同步时间来自实际运行记录。处理记录按固定配置、业务类型与UUID分页读取，不能用空目录代替服务未启用或权限不足。

原请求恢复只保存身份、固定目标、摘要及必要的非敏感执行参数。客户接口正文、账号映射、配置说明和审核理由不写入浏览器持久存储。没有完整回执时保留原请求身份，重新提交需要核对完整原参数和原上下文。
