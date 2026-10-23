# 业务菜单实施检查

模式：IMPLEMENT；唯一任务状态来源：[BUSINESS-MENU-20261021](../../docs/development/BUSINESS-MENU-20261021.md)。

采用当前代码与用户要求作为依据。工作台保留直接入口，八个折叠分类依次为项目与业务、资料与文稿、Agent 与模型、评测与发布、企业与协作、系统管理、运行监控、开发工具。入口仅从当前已授权路由读取，保持原有名称、URL、query及权限。首页功能目录与既有移动导航组件共用分类。

继续使用若依SidebarItem、Element Plus ElMenu/ElSubMenu及现有SVG图标库。保留200px侧栏、54px收起宽度、50px顶部导航和34px标签页；继承现有字体、主题颜色及滚动容器。长名称使用若依菜单文本处理，收起后通过原生浮出菜单访问；移动端选择后关闭抽屉。

本任务没有新增图片、字体或业务页面。品牌资产继续引用login-brand-20261021中的清单。页面映射列出共享组件和受影响首页模板，不表示47个业务页面自身发生变更。

开发环境真实浏览器通过全部八项分类及47项入口保留检查，验证用户管理真实读取、跨分类页面与刷新高亮、收起浮出菜单、移动端选择后关闭抽屉。首页与权限回归通过真实项目读取、标签页返回及375/820/1440px宽度检查，当前admin与*:*:*保持有效。证据为.implementation-work/business-menu-browser.json、business-menu-green-final.log及business-menu-regression.log。

13项组件编译、类型检查、613项固定Schema引用、最终生产构建与UI清单结构检查通过。最终生产预览的八项分类、47项入口、实际用户管理读取、跨分类刷新高亮、收起浮出菜单及移动导航全部通过，页面错误为空；证据为.implementation-work/business-menu-production.json。遵守用户限制，未执行截图或视觉识别，DOM及行为验证不代表视觉检查。
