# GLOBAL-ORGANIZATION-20261021 顶部全局组织选择

任务状态来源为本文，当前ACCEPTED，文档CURRENT，last_verified=2026-10-21 23:43（Asia/Shanghai）。依据用户要求，将组织选择和切换统一放到顶部导航，并应用于全部业务菜单。基线b0a3591及当前已有未提交修改。

检索BUSINESS-MENU-20261021、HOME-ADMIN-RUOYI-20261021、Navbar、session-panel、user store、BusinessSessionProtocol及实际会话API。当前成员数据提供tenant_id，展示使用真实组织标识，不生成组织名称。

验收：GO-01顶部始终显示组织选择；GO-02选择两项实际有效组织并跨项目、资料、模型页面验证相同服务端上下文；GO-03页面内没有重复组织选择；GO-04切换状态、失败与原请求继续可核查，未保存输入在切换确认中说明。

沿用服务端tenant-selections、上下文修订、成员授权与幂等请求。会话更新后清除旧组织页面状态并读取当前组织数据。权限与组织范围继续分别核验。关联NAVIGATION-STABILITY-20261021、FORM-DESIGN-20261021。

## 当前实现

| 文件 | identifier / 接口 | 当前职责 |
|---|---|---|
| web/src/components/Medical/OrganizationSwitcher.vue | 工作组织 / select | 顶部统一候选、当前组织、全局切换确认与原幂等请求。 |
| web/src/layout/components/Navbar.vue | OrganizationSwitcher | 所有菜单共用入口，移动端使用独立一行，组织邀请入口保留在账户菜单。 |
| web/src/views/medical/session-panel.vue、web/src/views/index.vue | 会话状态 | 页面内仅显示未选择、读取及错误状态；选定组织后使用当前业务数据。 |
| web/src/store/modules/user.js | selectMedical | 使用实际上下文修订、有效成员和原请求身份，切换后重新读取会话与全部页面使用的数据。 |
| web/src/views/medical/source-catalogue.vue | mayManageFolders | 文件夹接口依照后端相同范围的source.write摘要授权决定入口，资料目录继续使用实际读取接口。 |
| scripts/configure_local_discovery.py | 本地目录配置 | 以受保护部署配置提供独立AES-256游标密钥，供模型、模拟模型与规则目录使用。 |

## 环境与约束

开发前端127.0.0.1:18083，后端127.0.0.1:18080。当前后端PID62762，部署ID bdf10e8b-b7a4-4616-854c-b10c6ffa11b0，制品SHA-256为1af1e323704b3f5c40527856780a34b75523d9f602eaabcbf62c37559c363fd8。

运行配置为.implementation-work/runtime/application-profiles/e016f3fc-708c-46ae-a6e8-7b9060e0018b/application-workspace.yaml，SHA-256为8bc6ec57169ff8a93eeb332a4ee79f5274d6ba510d4f6e9651bd6005f3af1a9c。独立密钥保存在configuration-discovery-key.json，目录0700、文件0600；配置生成与启动均验证当前进程、制品及来源配置。游标有效期15分钟，密钥不进入日志或仓库。既有来源消费者、证书和签发配置继续保留。

组织上下文应用于业务和原生管理请求的X-Context-Revision。平台系统页面的数据范围仍由其原权限与接口确定。当前12项有效成员的业务动作存在差异，组织切换后按钮使用服务端实际action_summary；没有增加业务授权或创建业务样本。

组织写入和会话核查完成后，在发布新会话之前解除写入状态，页面能够立即开始读取当前组织。身份会话、组织候选和框架菜单采用60秒请求时限，适用于完整成员及权限核查；超时仍明确报告错误。

## 验证与证据

- GO-01—04：tests/browser/workspace-experience-checks.mjs在真实账号及两个有效组织中验证顶部选择、三类菜单、组织修订、模型读取与无授权按钮、移动端和原生管理请求；开发环境通过。.implementation-work/workspace-experience-browser.json、workspace-experience-green-final.log。
- tests/browser/organization-model-contexts.mjs对全部12个有效组织执行真实选择与目录读取，均返回200且tenant_id正确；证据organization-model-contexts.json。
- 首页、47项入口、真实项目total=1、超管admin及*:*:*、标签页和主题回归通过；证据workspace-experience-regression.log。
- 最终类型检查、613项Schema校验、4159模块生产构建与生产预览全部通过。生产环境重复验证组织切换、当前页面自动读取、三类菜单、原生管理上下文、表单和注销；错误为空，业务申请写入0。证据workspace-experience-typecheck-final.log、workspace-experience-build-final.log、workspace-experience-production.json。源码身份见workspace-experience-source-sha256.txt，生产入口摘要见workspace-experience-production-sha256.txt。

测试使用真实本地账号、有效企业与现有数据，没有执行截图。关联文档已更新LOCAL-RESTART-20261021的当前部署及维护命令。

GO-01—04及本任务必需验证通过，没有剩余必需实施。后续新增组织展示名称需要由正式接口提供实际名称，当前继续使用既有tenant_id。
