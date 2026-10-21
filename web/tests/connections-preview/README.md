# 企业连接前端交付

本预览直接渲染正式 `src/views/medical/connections.vue`，不是静态替代页面。页面仅实现前端准备与状态展示，不能作为企业连接已完成的验收证据。

## 范围与依据

- PRD FR-08、AC-16、AC-17；LLD-前端 FRTE-16、FRONT-I08；后端文档中的 CT-01—CT-03 仅用于解释未来接入边界，未实现或调用这些服务。
- 企业空间概览、接入类别搜索与选择、接入详情、八项配置资料本地编辑与格式校验。
- BLOCKED、空态、加载、读取失败、离线、待验证、同步错误、权限未知及权限不足状态。
- 接入类别来自项目既有设计：账号与单点登录、资料系统连接、专家服务协作。它们不是已配置连接、服务商目录或真实连接记录。
- 最近验证、同步时间、错误来源及影响、版本、企业身份等没有权威数据的字段明确显示未知。
- 最终保存、验证、同步始终禁用并展示原因。权限未知仍可准备非敏感描述，但不授予任何实际管理权限。
- 本地草稿不持久化；权限变化、页面隐藏或卸载时清除。没有密码、令牌、密钥输入，没有网络请求或 OAuth 流程。

## 集成

路由 `/connections` 直接加载 `views/medical/connections.vue`。无需必填 props，默认权限未知且 BLOCKED。路由和侧栏由主任务负责。

可选展示 props：

- `access`: `unknown`（默认）、`allowed`、`denied`。正式接入须由权威权限决定，不能凭前端角色或预览值授予权限。
- `viewState`: `blocked`（默认）、`empty`、`loading`、`error`、`disconnected`、`verifying`、`sync-error`。当前正式默认无接口；其他状态仅用于隔离预览，不代表真实服务事件。
- `preview`: 默认 false；隔离预览开启，展示“模拟数据 / 未连接后端”。

## 验证与构建

在 `web/` 下运行：

    node --test tests/connections/*.test.mjs
    ./node_modules/.bin/vue-tsc --noEmit -p tsconfig.connections.json
    node tests/connections-preview/build-standalone.mjs

构建产物默认 `/workspace/shared/connections-preview.html`，包含实际 Vue 运行时与正式页面组件，可离线打开。状态控制只影响本地展示，不伪造成功回执。

12 项测试覆盖 SFC 编译、本地必填/长度/地址限制、无网络与持久化、三个关键动作禁用、真实组件 SSR、权限不足不输出目录和配置、加载清除目录，以及各错误状态。独立类型检查与 Vite 构建通过。

## 未验收

浏览器执行和截图因既有环境限制未进行。SSR 与构建不等于视觉/键盘/浏览器交互验收。尚需在允许的浏览器中验证响应式布局、搜索、配置折叠、错误焦点、权限切换清空，以及隐藏页面清空行为。没有后端集成、真实连接验证、实际同步、授权/规格批准或客户 B/S 系统验收。
