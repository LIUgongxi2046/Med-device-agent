# LOGIN-LOCAL-ADMIN-20261021 登录页与本机超管账号

## 任务身份与依据

用户要求提供当前超管账号、在本机登录页展示账号并重新设计登录页面。本记录为本任务唯一状态来源，关联IMP-04/06/37，本次范围ACCEPTED。代码基于main的b0a3591及当前保留修改，文档CURRENT，last_verified=2026-10-21。修改尚未提交Git。

读取现有login.vue、医疗路由、权限状态、用户登录方法、医疗页面样式、Vite配置、前端操作说明与LOCAL-RESTART-20261021。实际认证确认原有本机账号具有PLATFORM_ADMIN角色；账号与密码仅保存在本机受保护文件中，本文不复制凭据。

## 页面与交互

范围为login.vue对应的/access、/login及现有登录别名。保留既有Cookie、CSRF、当前会话、权限与跳转校验。

沿用医疗页面蓝灰色系，桌面采用深蓝品牌介绍与白色登录表单的左右布局；700px及以下使用上下布局。标题、表单、账号卡片及页脚使用统一间距；输入与主按钮高度48px。页面支持键盘焦点、密码显示控制、加载、表单校验、认证错误、账号读取错误与重新读取。窄视口密码换行，不产生横向滚动。

| 设计变量 | 数值与用途 |
| --- | --- |
| --login-brand | #1768c4，主要按钮、焦点及链接 |
| --login-text | #243247，标题与表单文字 |
| --login-muted | #65758b，辅助说明 |
| --login-border | #dce3ec，输入与内容边界 |
| --login-surface | #f5f8fc，本机账号卡片 |
| 字体与尺寸 | 系统中文字体；桌面标题29px，移动端26px；输入及主按钮48px |

页面共用一个组件，主要验收入口为/access；/auth/login、/local-auth/login.html为其既有别名，/login继续共用login.vue。别名关系通过当前路由核查，浏览器完整交互在/access执行。

本机账号卡片提供账号、密码显示控制及填入登录表单；填入操作不直接提交登录。账号和密码保持在页面内存，页面离开时清除。

## 当前实现

| 文件 | 职责 |
| --- | --- |
| web/src/views/login.vue | 登录布局、表单状态、本机账号展示与填入 |
| web/vite/plugins/local-owner-credentials.mjs | 读取原有0600本机账号文件，校验文件归属与请求来源，禁止缓存 |
| web/vite.config.js | 仅在medical开发服务注册账号读取入口 |

账号接口仅服务127.0.0.1:18083，检查连接地址、Host、Origin、Sec-Fetch-Site、GET方法与固定无查询路径。生产构建与预览不注册账号接口，浏览器构建文件不包含实际凭据。功能图标复用Element Plus，品牌名称、Logo和当前账号使用[品牌与登录验收记录](LOGIN-BRAND-ACCEPTANCE-20261021.md)中的当前定义。

## 验收与证据

验收使用当前真实账号、后端与本机浏览器，不生成模拟业务数据。验证命令及结果使用.implementation-work/login前缀保存。视觉检查未执行；浏览器验证限定DOM、计算样式与实际行为。

| 验收 | 实际结果 | 证据 |
| --- | --- | --- |
| 当前账号 | 真实登录读取PLATFORM_ADMIN，检查会话正常注销 | .implementation-work/login-account-20261021.py |
| 账号HTTP边界 | 原有真实凭据一致；禁止缓存；Origin、Host、跨站、方法和查询限制通过 | .implementation-work/login-account-http-20261021.test.mjs |
| 类型与Schema | 类型检查及613项固定引用通过 | .implementation-work/login-typecheck-20261021.log |
| 生产构建 | medical构建通过，3分9秒 | .implementation-work/login-build-20261021.log |
| 浏览器交互 | 12项检查通过，page_errors为空；实际表单登录后重读PLATFORM_ADMIN并注销 | .implementation-work/login-ui-browser-20261021.json |
| 响应式与缩放 | 375、820、1440px布局及完整密码换行；200%缩放无横向溢出 | 同一浏览器回执 |
| 生产文件与预览 | 543份构建文件不含实际密码；预览没有凭据接口及账号卡片，375与1440px检查通过 | .implementation-work/login-production-20261021.json |

浏览器使用项目已有Playwright SDK与实际无界面Chrome156.0.8078.47，未使用模拟HTTP、截图或视觉识别。检查包含空表单阻止请求、键盘输入顺序、账号填入不自动提交、密码显示与隐藏、密码未进入浏览器持久存储。生产预览检查完成后已关闭，开发服务18083继续运行。

代码格式检查使用git -c core.whitespace=cr-at-eol diff --check，保留原Vue文件的CRLF格式。原有认证失败及账号文件不可用的界面保留明确错误与重新操作入口；未注入服务故障进行人工错误测试。
