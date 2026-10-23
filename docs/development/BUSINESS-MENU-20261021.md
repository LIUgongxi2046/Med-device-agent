# BUSINESS-MENU-20261021 左侧业务菜单

## 任务身份与依据

- task_id：BUSINESS-MENU-20261021；task_source：本文件；状态：ACCEPTED；同步时间：2026-10-21 21:47（Asia/Shanghai）。
- 用户要求：左侧菜单按照业务重新分类；沿用若依组件与当前授权。
- 文档有效性：CURRENT；last_verified：2026-10-21。
- 代码基线：b0a3591，工作目录包含已有未提交修改，本任务继续维护navigation.js、Sidebar/index.vue与相关验证；对应SHA-256记录为.implementation-work/business-menu-source-sha256.txt。
- 检索：读取开发索引、HOME-ADMIN-RUOYI-20261021、当前导航函数、权限存储、SidebarItem、首页目录与真实浏览器脚本。
- 关联：[HOME-ADMIN-RUOYI-20261021](HOME-ADMIN-RUOYI-20261021.md)、LOGIN-BRAND-ACCEPTANCE-20261021。

## 验收条件

| 编号 | Given / When / Then |
|---|---|
| BM-01 | 当前真实超管登录后，侧栏按照业务归类，全部47项功能保持唯一且可访问。 |
| BM-02 | 展开分类并访问页面，实际URL正确，当前菜单高亮，所属分类展开。 |
| BM-03 | 收起侧栏后能够使用浮出菜单；移动端抽屉可展开并导航。 |
| BM-04 | 首页功能目录与侧栏使用一致分类；若依权限、主题和标签页继续正常。 |

## 当前设计与约束

工作台保留直接入口，其余采用项目与业务、资料与文稿、Agent 与模型、评测与发布、企业与协作、系统管理、运行监控、开发工具。企业与协作包括本人账户。共享分类只消费已授权可见路由，不修改路由注册或数据库。项目详情等需要业务参数的隐藏路由保持原入口。

## 当前功能与实现

| 文件 | identifier | 职责与调用方 |
|---|---|---|
| web/src/components/Medical/navigation.js | medicalNavigationGroups / medicalNavigationEntries | 从可见路由提取实际入口，按照业务归类、去重和排序；供侧栏、首页目录与既有移动导航使用。 |
| web/src/layout/components/Sidebar/index.vue | businessNavigation / activeMenu / handleSelection | 使用若依SidebarItem和ElSubMenu，正确显示当前页面及所属分类，移动端选择后关闭抽屉。 |
| tests/browser/business-menu-checks.mjs | BM-01—04 | 使用真实账户、当前数据库和HTTP服务验证分类、全部入口、刷新、浮出菜单及移动导航。 |
| web/tests/business-navigation/navigation.test.mjs | 业务分类检查入口 | 业务分类由真实浏览器脚本完整验收，当前文件保持其余既有检查。 |

未选企业与选定企业继续使用现有会话规则；本任务没有修改业务权限、后端、数据或品牌资产。隐藏详情根据当前实际路径匹配最近的可见业务入口，授权不足时继续由当前服务与路由控制。分类缺少入口映射时立即报告错误。

## 验证与证据

| 验收 | 实际命令或操作 | 当前结果 | 证据 |
|---|---|---|---|
| BM-01—04开发环境 | env TMPDIR="$PWD/.implementation-work/migration-check-tmp" node tests/browser/business-menu-checks.mjs | 真实八项分类、47项唯一入口、用户管理读取、跨分类高亮刷新、浮出菜单与移动导航通过，页面错误为空。 | .implementation-work/business-menu-browser.json；business-menu-green-final.log |
| 首页与权限回归 | node tests/browser/admin-workspace-checks.mjs | 当前admin与*:*:*、12项有效企业、真实项目total=1、用户管理、标签页、三项视口和明暗主题通过。 | .implementation-work/business-menu-regression.log；home-admin-browser.json |
| 组件 | node --test web/tests/business-navigation/components.test.mjs | 13项通过。 | .implementation-work/business-menu-components.log |
| 类型与Schema | npm run typecheck:medical | 通过，613项固定Schema引用编译通过。 | .implementation-work/business-menu-typecheck.log |
| 生产构建 | npm exec -- vite build --mode medical | 通过，4158项模块转换，最终制品生成成功。 | .implementation-work/business-menu-build-final.log；business-menu-production-sha256.txt |
| BM-01—04生产环境 | node tests/browser/business-menu-checks.mjs http://127.0.0.1:18084 | 通过，八项分类、47项入口、用户管理200、跨分类高亮刷新、收起浮出菜单、移动导航、admin及*:*:*均正常，页面错误为空。 | .implementation-work/business-menu-production.log；business-menu-production.json |
| UI清单 | audit_ui_delivery.py ui-delivery/business-menu-20261021 --mode implementation --source-root "$PWD" | 通过，3项共享模板及组件引用有效。 | .implementation-work/business-menu-ui-audit.json |

开发环境为127.0.0.1:18083，当前后端127.0.0.1:18080。测试使用本机受保护凭据和实际服务，秘密值不进入记录。行为验证不包含截图或视觉识别。初始行为断言证据保存于.implementation-work/business-menu-red.log。

## 关联影响与继续条件

2026-10-22补充要求：SidebarItem的图标仅在一级分类显示，isNest为true的二级菜单不渲染svg-icon。真实超管浏览器读取二级图标数量为0，一级分类图标及原导航继续保留。当前专项证据为.implementation-work/account-workspace-browser.json。

共享导航影响Sidebar、首页功能目录及既有MobileNavigation。后续新增可见入口需要同时维护业务分类。BM-01—04及相关工程验证通过，本任务没有剩余必需验收。既有MobileNavigation未在当前布局挂载，其验证范围为真实源码编译和共享分类；当前移动端使用若依侧栏抽屉，已完成实际交互验证。截图和视觉识别遵守用户限制，未执行。
