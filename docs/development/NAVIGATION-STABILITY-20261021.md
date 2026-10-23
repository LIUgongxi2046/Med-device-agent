# NAVIGATION-STABILITY-20261021 菜单切换稳定性

任务状态来源为本文，当前ACCEPTED，文档CURRENT，last_verified=2026-10-22 09:51（Asia/Shanghai）。用户要求菜单切换时左侧菜单保持稳定。基线b0a3591及当前已有未提交修改。

检索BUSINESS-MENU-20261021、user.readMedical、invalidateMedical、permission.setMedicalRoutes、动态路由登记和AppMain。会话核查期间保留侧栏组件，访问资格依据当前服务端结果更新。

验收：NS-01在实际菜单导航和会话刷新期间，侧栏分类DOM保持原有身份且没有空导航；NS-02每次页面导航继续从服务端核查身份与授权；NS-03授权变化、注销和明确失效时撤销路由与权限；NS-04当前分类、高亮和收起状态持续正常。

展示期间不授予旧权限，业务内容继续以当前有效会话和框架资格控制。身份与权限变更成功读取后更新路由登记；明确失效仍执行完整清除。关联GLOBAL-ORGANIZATION-20261021、BUSINESS-MENU-20261021。

## 当前实现

- user.invalidateMedical的preserveNavigation只保留导航和账户展示，清除当前业务会话、角色及权限；框架资格暂停，原登记路由继续保留组件身份。
- readMedical仍逐次读取实际session、selection-options和framework/navigation。主体、完整菜单、roles与permissions一致时，permission.setMedicalRoutes恢复资格并保留导航数组；授权变化时重新登记。
- framework-routes的installed与authorized分别表示已登记路由和当前访问资格。suspend暂停资格，resume仅在当前服务端完整身份比较成功后调用，clear清除两者。
- main.js对DEPENDENCY_UNAVAILABLE保留仍有效的会话并显示错误；身份、上下文、权限及其他完整性错误继续清除资格。注销执行完整清除。
- 管理响应的generation已经更新时，旧响应按取消终止；router.onError对已取消的读取保留新导航会话。相关实现、连续导航与真实组织回归见[NAVIGATION-SESSION-20261022](NAVIGATION-SESSION-20261022.md)。2026-10-22复验侧栏原有节点保持连接、最少8项分类，组织切换、管理读取及注销均通过，证据navigation-session-20261022-regression.log。

## 验证与证据

NS-01—04开发环境通过。真实菜单访问项目、资料和模型时，MutationObserver确认分类标题最少8项、原侧栏及首项标题始终连接、没有替换DOM。组织选择仍读取实际会话；原生用户管理携带当前组织修订并返回200。

通过账户菜单执行实际注销后，导航数0、权限数0、User登记资格false，服务端/api/session返回401。.implementation-work/workspace-experience-browser.json及workspace-experience-green-final.log保存证据。原账号角色、47项入口和标签页回归通过，证据workspace-experience-regression.log。

相关位置：web/src/store/modules/user.js、permission.js、components/Medical/framework-routes.js、main.js。当前源码身份见workspace-experience-source-sha256.txt。与全局组织任务使用相同后端部署。最终类型检查、613项Schema、4159模块生产构建通过；生产预览的原侧栏与首项标题保持连接，最少8项分类，错误为空。证据workspace-experience-production.json、workspace-experience-build-final.log及workspace-experience-typecheck-final.log。NS-01—04通过，没有剩余必需实施。
