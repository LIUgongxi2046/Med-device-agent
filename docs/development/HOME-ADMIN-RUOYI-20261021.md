# HOME-ADMIN-RUOYI-20261021 首页与超管功能

任务状态来源为本文，当前ACCEPTED，文档CURRENT，last_verified=2026-10-21。依据用户要求修复首页内容、超管功能权限、完整功能导航、字体颜色与若依框架布局。代码基于main的b0a3591及用户当前保留的未提交修改。

读取LOGIN-BRAND-ACCEPTANCE-20261021、workbench-access-acceptance、当前首页、路由与权限状态、Sidebar、Navbar、AppMain、若依权限服务及业务会话。验证使用真实本机账号和当前服务，不生成模拟业务数据，不使用截图或视觉识别。

验收：AC-01登录后首页显示有效内容；AC-02超管权限由后端确认且功能导航完整；AC-03使用若依布局与Element Plus组件并正确显示字体颜色；AC-04实际数据读取、导航、响应式、类型与构建检查通过。业务数据继续使用当前企业上下文与既有审计流程。

## 当前功能

首页显示当前账户、超级管理员身份、47项唯一功能入口、12项有效企业空间、企业选择以及项目与任务区域。功能目录取自当前路由和服务器确认的若依菜单，不包含重复入口或无法直接打开的参数路径。选择现有企业后，首页读取当前企业的真实项目数据；企业范围和操作继续由服务端会话确认。

侧栏与首页目录的当前业务分类由[BUSINESS-MENU-20261021](BUSINESS-MENU-20261021.md)维护；47项入口、当前超管、企业项目、主题与标签页已在该任务中重新验证。

liuhaonan关联有效的若依admin角色，框架权限为*:*:*。SysUser按照有效admin角色识别管理身份，SecurityUtils在当前认证主体范围内识别该身份；框架菜单、数据范围与方法权限沿用原有服务。业务权限继续通过当前企业的action_summary、授权条件和审计流程核查，系统管理角色不登记专业核准或签发结论。

侧栏、顶部导航、标签页、固定顶部、内容滚动和移动端抽屉使用若依原有布局。首页组件使用Element Plus。颜色采用现有主题变量；明亮主题文字rgb(48,49,51)、背景rgb(242,243,245)，深色主题文字rgb(255,255,255)、背景rgb(10,10,10)。窄视口功能文字可以换行。

## 文件与调用位置

| 文件 | 当前职责 |
| --- | --- |
| business/ruoyi-common/src/main/java/com/ruoyi/common/core/domain/entity/SysUser.java、utils/SecurityUtils.java | 有效admin角色与当前认证主体的管理身份判定 |
| scripts/InitializeLocalFramework.java、scripts/initialize_local_framework.py | 使用JSqlParser读取仓库原始若依SQL；完整备份后，通过事务初始化菜单、字典及admin角色并关联本机首次管理员 |
| web/src/router/index.js、store/modules/permission.js | 完整业务功能入口、父级标题及服务器批准的若依路由 |
| web/src/layout/index.vue、layout/components/Sidebar/index.vue、Navbar.vue、AppMain.vue | 若依原有共享布局与控件 |
| web/src/components/Medical/navigation.js、views/index.vue | 唯一功能目录、首页概览与当前企业数据 |
| web/src/assets/styles/medical.scss、views/medical/session-panel.vue | 使用主题变量显示字体、边界和表面颜色 |
| tests/browser/admin-workspace-checks.mjs | 实际浏览器、当前账号与现有企业数据的开发和生产验收 |

## 数据与运行身份

初始化数据来自business/sql/ry_20260417.sql中的sys_menu、sys_dict_type、sys_dict_data及admin角色定义，共125条参考数据，其中85项菜单；85项菜单均关联admin角色。当前账号保持原有用户身份和密码。完整备份为.implementation-work/runtime/framework-backups/080c9dd8-4a30-4a52-a625-66650e96c784.sql.gz，SHA-256为80004319671e19e165116be08ab1f9b01c0d618a69a7fa5e8b197e47deef576c。

当前后端部署ID为bdf10e8b-b7a4-4616-854c-b10c6ffa11b0，PID62762，Jar SHA-256为1af1e323704b3f5c40527856780a34b75523d9f602eaabcbf62c37559c363fd8。后端使用18080，开发前端使用18083。顶部组织选择和当前运行配置见[全局组织选择](GLOBAL-ORGANIZATION-20261021.md)，侧栏稳定性见[NAVIGATION-STABILITY-20261021](NAVIGATION-STABILITY-20261021.md)。角色与菜单属于正式配置数据，初始化工具要求目标参考表为空，并校验本机首次管理员身份；全部修改在事务中完成。

## 验证与证据

| 验收 | 操作与断言 | 当前结果 | 证据 |
| --- | --- | --- | --- |
| AC-01/02 | 当前账号登录，读取真实角色、框架权限、菜单和企业会话 | admin、*:*:*、85项菜单、12项有效企业空间 | .implementation-work/home-admin-permissions.log、home-admin-diagnose.json |
| AC-01/04 | 实际点击企业选择，读取首页项目数量并比较响应；打开若依用户管理，标签页返回首页 | 通过，当前所选企业1项项目，用户列表显示liuhaonan | .implementation-work/home-admin-browser.json |
| AC-02 | 从真实可用路由生成全部入口，比较链接唯一性及可直接打开的路径 | 通过，47项入口 | 同一浏览器证据 |
| AC-03 | 若依侧栏、顶部、标签页与页面组件；375/820/1440px无横向滚动，明暗主题计算样式 | 通过，页面错误为空 | 同一浏览器证据 |
| AC-04组件 | node --test web/tests/business-navigation/components.test.mjs | 13项组件编译检查通过 | .implementation-work/home-admin-component-compile.log |
| AC-04类型 | npm run typecheck:medical | 通过，613项固定Schema引用编译通过 | .implementation-work/home-admin-typecheck.log |
| AC-04前端构建 | npm run build:medical | 通过，1分41秒 | .implementation-work/home-admin-build.log |
| AC-01/02/03/04生产 | 最终生产预览中的真实登录、47项唯一入口、企业项目读取、若依用户管理、标签页返回、三项视口及明暗主题 | 全部通过，页面错误为空 | .implementation-work/home-admin-production.json |
| AC-04后端构建 | Maven离线package，ruoyi-admin及依赖模块 | 通过，3分11秒，新后端启动成功 | .implementation-work/home-admin-backend-build.log、home-admin-backend-restart.log |
| AC-03交付结构 | audit_ui_delivery.py，implementation模式 | 6项页面及共享组件映射通过 | .implementation-work/home-admin-ui-audit.json |

浏览器采用真实Chrome与当前服务，没有模拟HTTP或新增测试业务数据。视觉检查未执行。当前任务不替代其他业务流程的完整验收；相关记录继续引用原有任务范围。代码尚未提交或推送Git。

生产预览18084已经关闭；开发前端18083与后端18080继续运行，HTTP检查均返回200。
