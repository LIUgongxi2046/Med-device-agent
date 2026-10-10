# 设计系统

规范主体：[界面规范.md](../界面规范.md)。交互与屏幕编号：[交互原型说明.md](../交互原型说明.md)。设计稿：[设计稿.html](../设计稿.html)。变量：[tokens.json](./tokens.json)。

当前输入模式为 PRD 与现有原型。用户指定参照若依 RuoYi Vue3 管理界面。最左侧导航的三个分组统一使用分组标题与同级按钮：核心功能为工作台、项目中心、资料中心；平台管理为 Agent 管理、评测与运营；系统管理直接显示全部管理功能。六个场景 Agent 入口位于工作台。主区域采用深蓝灰侧栏、白色顶栏、浅灰画布、蓝色主操作和 Element Plus 风格控件。页面仅显示单行标题及必要操作。项目详情集中呈现办理进度，文档任务使用创建任务、撰写与核验、审查与签发三个步骤。项目中心、资料中心及系统管理使用列表与分页。

工作台强调待处理事项和明确的责任信息；文档使用连续正文与逐章引用；资料中心使用范围、文件夹、列表与多文件阅读标签；Agent 管理按版本组织默认配置、模型预算、评测发布和记录；评测统计与导出共用当前筛选范围。顶部 Gru 对话助手在全部页面提供上下文、来源查询、会话历史和修订预览，入口、标题、欢迎区域与回复共享蓝白配色的拟人男性形象，尺寸定义见 `tokens.json` 的 `coreWorkspace.assistant`。

核心行为位于 `product-workspace.js`，模型 API 配置由 `model-api-manager.js` 管理，并在核心行为文件之前加载；页面结构样式位于 `product-workspace.css`；对话与资料工作区结构位于 `conversation-library.css`。`ui-theme.css` 集中维护全局语义颜色、字体、间距、控件状态、容器适配与 Gru 布局，主题在结构样式之后加载，数值与 `tokens.json` 同步。系统 Logo 文件、切图规格和来源见 [asset-manifest.csv](./asset-manifest.csv)、[page-asset-map.csv](./page-asset-map.csv) 与 [image-generation-manifest.csv](./image-generation-manifest.csv)。完整页面分母见 [screen-design-map.csv](./screen-design-map.csv)。共享对话状态见 [component-state-map.csv](./component-state-map.csv)。

`ui-components.js` 提供统一 HTML 控件，`agent-capabilities.js` 管理角色与能力，`knowledge-workspace.js` 管理资料集、处理、检索与成果，`research-workspace.js` 管理来源范围、补证与停止请求，全部在 `product-workspace.js` 前加载。当前没有 Vue 工程；正式框架复用映射见 [component-reuse-audit.md](./component-reuse-audit.md)。

Agent 配置使用七个标签，角色卡片双列，窄屏单列；资料中心使用六个分区。长表格与标签在各自容器滚动，弹窗采用同一字段和按钮规范。资料集、消息来源和导出均固定版本；核准、解析、检索状态分别显示。能力审核、模型验证与真实运行服务的空态和不可用状态常驻。

正文旁的研究工作区使用问题、协作、记录、成果四个持续查看分区，包含原文展开、前置依赖、章节定位和 Gru 介入。阅读不打开模态窗口，编辑和决定沿用统一对话框。资料中心增加阶段概况和下一步处理区域。`complex-demo.js` 提供用户授权的复杂演示数据，字段、来源、任务、研究问题与资料集相互关联，详见 `complex-scenario-data.md`。

## 申请清单与图文证据

`registration-dossier.js` 使用统一按钮、状态标签、表格与对话框。申请清单采用 180px 分类栏与弹性表格，800px 以下分类排列为两列。文稿证据使用 `#f8fafc` 背景、`#dce3ec` 边线、14px 标题及至少 12px 图注，PNG 保持原始比例。完整原页在右侧预览，资料中心可以打开 PDF 对应页码。

`document-fields.js` 保存 11 类文档、57 个初始字段组及审查记录。`document-reader.js` 提供连续图文阅读：标题右侧显示版本记录，正文紧接相连流程导航；目录与申请清单位于右侧。审查页只读全文，拖选文字或选择图片后显示标注按钮，支持定位、回复与解决。`public-reference.js` 维护独立真实公开案例，包含官方原件及转换表格图片，企业名称使用 Epitel, Inc.；来源初始待核准，公开任务禁止申报签发。详见 `document-reading-design.md`。

文字、表格、截图统一保存来源文件与固定版本。历史版本默认保留，采用最新版本需明确选择。引用未核查时阻止审查确认，图文导出保留图片与出处。研究依据见 `registration-research.md`。

Agent 管理的两个顶部分区使用相同按钮尺寸和选中背景。模型 API 列表包含四项统计、查询、状态筛选与分页；详情使用主要配置区域和 300px 辅助区域；表单按基本信息、身份凭据、调用限制、授权与许可组织。继承全局颜色、字体、焦点、空态和确认弹窗。`check-model-api.mjs` 检查保存与编辑、版本、操作确认、Agent 选择条件、Gru 入口和移动布局；真实模型验证状态单独记录。

页面标题 24px、分区标题 17px、界面正文 14px、表格正文 13px、说明至少 12px；文档正文 16px、2 倍行高，字号支持调整。品牌蓝 `#409EFF` 用于装饰，主操作 `#1768C4` 搭配白色文字，链接为 `#175FAE`。状态文字使用对应的深色并保留名称。卡片圆角 8px、控件 6px、对话框 12px；桌面按钮至少 38px，移动端主要按钮至少 44px。

内容区域根据容器宽度 1060px、860px、620px 切换布局，支持 Gru 以 440px 或 740px 展开后继续阅读正文。浏览器检查位于 `check-ui-quality.mjs`；页面视觉审查状态及交互证据见 `ui-audit.md`。
