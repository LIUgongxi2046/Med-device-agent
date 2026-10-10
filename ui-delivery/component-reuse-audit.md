# 组件复用检查

检查日期：2026-09-27。对象：当前目录的设计稿与若依 Vue3 官方源码。当前目录没有 Vue 工程、package.json 或 .vue 文件；正式工程目录为 UNKNOWN。

## 当前实现

`ui-components.js` 统一按钮、标签、选择项、标题、空态、表格、分区导航、表单字段和分页的 HTML 渲染。`product-workspace.js` 管理的十个屏幕共用这些入口；另外四个屏幕沿用入口文件结构与共同主题。`ui-theme.css` 统一颜色、字号、间距、焦点和响应布局。`--el-*` 仅为主题变量映射，页面没有加载 Element Plus。

专业角色、研究限制、资料集和成果使用同一组控件。通用组件负责显示和事件，核准、引用、发布与版本判断由业务模块负责。

## 正式 Vue3 工程的复用范围

| 页面用途 | 复用目标 | 业务部分 |
| --- | --- | --- |
| 查询、编辑、数值限制 | ElForm、ElFormItem、ElInput、ElSelect、ElInputNumber | 业务必填、地址、预算和版本校验 |
| 列表、多选、分页 | ElTable、ElTableColumn、若依 Pagination | 查询条件、总数、当前页和授权数据 |
| 搜索显隐、刷新、列设置 | 若依 RightToolbar | 当前列表列定义与刷新请求 |
| 页面分区、状态、空态 | ElTabs、ElTag、ElEmpty | 来源模式、处理状态和业务文案 |
| 编辑、详情、确认 | ElDialog、ElDrawer、ElMessageBox | 版本冲突、引用影响和决定原因 |
| 文件夹与上传 | ElTree、ElUpload | 文件摘要、处理队列、资料版本和解析定位 |
| 进度与记录 | ElSteps、ElTimeline | 服务返回的运行、子任务、用量和取消状态 |
| 导航与权限 | 若依已有布局、路由和权限机制 | 核心功能、平台管理、系统管理及项目数据权限 |

表格和表单优先直接使用框架组件。业务组件包括资料范围选择、资料集版本、四项处理状态、原文引用、专业角色配置、研究问题、成果转存和 Gru 修改确认。它们组合通用组件，保留一致的按钮、分页与校验反馈。

正式工程还需要核查其当前版本、全局主题、已有组件 API 和权限实现。以上为工程接入映射，状态为 PLANNED；当前完成的是 HTML 原型的共享渲染与主题统一。

## 参考依据

[RuoYi Vue3 官方工程](https://github.com/yangzongzhuan/RuoYi-Vue3)的依赖清单包含 Vue3 与 Element Plus。[Pagination 源码](https://raw.githubusercontent.com/yangzongzhuan/RuoYi-Vue3/master/src/components/Pagination/index.vue)包装分页控件并维护 page、limit 和分页事件；本产品列表应复用该入口。RightToolbar 的搜索显示、刷新和列设置应沿用项目已有实现。

[GPT Researcher 来源配置](https://docs.gptr.dev/docs/gpt-researcher/context/tailored-research)提供 local、web、hybrid 及指定来源控制；本稿采用显式范围与联网选择。[深度研究说明](https://docs.gptr.dev/docs/gpt-researcher/gptr/deep_research)提供广度、深度和并发配置参考，本产品继续要求发布上限及人工核查。

[DeerFlow 官方说明](https://github.com/bytedance/deer-flow)中的 Skill、Tools、项目资料、成果与消息引用范围，为能力目录、资料转存和固定来源提供参考。当前没有接入或运行这些项目的后台服务。
