# 首页与若依布局实施检查

模式为IMPLEMENT。任务依据及唯一状态来源为docs/development/HOME-ADMIN-RUOYI-20261021.md。修改范围包括首页、功能目录与共享布局组件；用户管理用于验证真实管理页面与标签页导航。

侧栏与首页目录的当前业务分类及其交互证据由[业务菜单检查](../business-menu-20261021/ui-audit.md)维护。

使用若依现有Sidebar、Navbar、TagsView、AppMain、HeaderSearch、Screenfull、SizeSelect及设置组件。首页采用Element Plus的ElCard、ElRow、ElCol、ElStatistic、ElTag、ElButton；字体与表面颜色使用现有Element Plus主题变量。导航宽度、顶部高度、标签页高度、滚动及移动端抽屉沿用若依定义。

真实浏览器读取当前超管权限、47项唯一功能入口、现有企业项目与用户列表，验证标签页返回首页、375/820/1440px宽度以及明暗主题计算样式。明亮主题文字rgb(48,49,51)，背景rgb(242,243,245)；深色主题文字rgb(255,255,255)，背景rgb(10,10,10)。运行结果引用.implementation-work/home-admin-browser.json。

品牌名称与Logo继续使用web/branding.mjs及login-brand-20261021中的资产清单，本次没有新增图片、字体或其他图像资产。未执行截图或视觉识别；DOM、计算样式、编译和交互证据不代表视觉检查。
