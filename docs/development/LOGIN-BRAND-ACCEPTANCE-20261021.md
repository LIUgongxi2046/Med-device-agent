# LOGIN-BRAND-ACCEPTANCE-20261021 账号与品牌验收

任务状态来源为本文，当前ACCEPTED，文档CURRENT，last_verified=2026-10-21；代码基于main的b0a3591及用户保留的未提交修改。用户要求超管账号为liuhaonan，中文名称为医疗设备智能工作站，英文名称为Med-device-Agent Workspace；重新设计Logo并替换登录页、导航、首页及浏览器页签，修复登录连接错误。

读取LOGIN-LOCAL-ADMIN-20261021、当前登录页、session.ts、user.js、Vite代理、local_runtime.py及RecoverLocalOwner.java。验证使用当前本机账号、数据库、服务及无界面Chrome，遵守用户禁止主动视觉功能的要求，不创建截图或执行视觉识别。

验收编号：AC-01账号更名并保留原有身份与权限；AC-02统一品牌名称与Logo；AC-03真实表单认证与会话恢复；AC-04响应式、类型检查与生产构建。

当前前后端监听18083和18080，认证CSRF接口直接及经过代理均返回200。详细诊断与验证证据保存在已忽略的.implementation-work目录。关联任务为LOGIN-LOCAL-ADMIN-20261021和LOCAL-RESTART-20261021。

## 当前功能与实现

| 文件 | 当前职责 |
| --- | --- |
| scripts/local_runtime.py、scripts/RenameLocalOwner.java | rename-owner核查首次管理员身份，通过事务更新用户名与显示名称，保留密码及用户编号，撤销更名前的活动会话，保存0600凭据文件。recover-owner使用当前保存的用户名。 |
| web/branding.mjs | 统一中文名称、英文名称及Logo路径；medical模式标题采用此处定义。 |
| web/src/views/login.vue、web/src/layout/components/Sidebar/Logo.vue、web/src/views/index.vue | 登录、导航与首页使用相同品牌资产和完整名称。 |
| web/index.html | 浏览器页签与Apple Touch图标，application-name。 |
| web/src/api/medical/session.ts、web/src/utils/request.js | BCrypt认证请求等待60秒；响应超时与连接中断分别报告；不自动重复提交密码。 |
| tests/browser/login-workspace-checks.mjs | 真实账号与浏览器验收，包含两次完整登录、注销、品牌、响应式与存储检查。 |

用户名和显示名称为liuhaonan，原有user_id、framework用户编号、PLATFORM_ADMIN、密码摘要和24项企业成员记录已经通过真实数据库比对。新账号登录返回302，读取会话返回200；旧用户名认证返回401。凭据保持原有密码，文档不复制秘密值。

## 品牌规范与资产

中文名称为医疗设备智能工作站；英文名称为Med-device-Agent Workspace。品牌标识采用医疗设备监视器轮廓、字母M结构和连接节点，蓝色#1768c4与青绿色#24b8b0。品牌名称保持页面文本。桌面登录标识48px，窄视口42px；导航32px；首页56px。沿用登录页面48px输入及按钮、8px圆角、蓝灰背景与系统中文字体。

资产brandmark.png使用内置imagegen生成，项目文件为web/public/assets/brandmark.png，1254×1254，PNG，具有Alpha通道。图像来源为当前任务生成文件exec-76404747-6008-40f1-ad40-171544be54c2.png。生成提示要求：医疗设备智能工作站的单个几何品牌标识，融合M结构、设备轮廓及连接节点；蓝色与青绿色，透明背景，轮廓清晰，适用于24px图标；不包含文字、阴影、水印或背景装饰。页面均使用同一资产并保持等比显示。资源路径使用已有后端允许访问的/assets目录。

| 使用位置 | 路径或组件 | 状态 |
| --- | --- | --- |
| 登录页及登录别名 | login.vue | 已接入 |
| 导航展开与收起 | Sidebar/Logo.vue | 已接入 |
| 首页 | index.vue | 已接入 |
| 浏览器页签与Apple Touch | index.html | 已接入 |

视觉检查未执行；图像信息与资源引用通过文件信息及浏览器DOM检查核查。没有新增插画、字体、弹窗或业务页面，全量产品设计稿与PDF不属于本次局部更新范围。

## 验证与证据

| 验收 | 操作与断言 | 当前结果 | 证据 |
| --- | --- | --- | --- |
| AC-01 | 数据库更名前后比较身份、密码摘要、角色、企业成员记录；新账号登录及会话读取；非当前用户名拒绝认证 | 通过，24项成员记录保持一致 | .implementation-work/login-brand-account.json |
| AC-01维护 | provision_owner读取当前用户名；重复提交相同更名目标时拒绝，凭据文件内容保持一致 | 通过 | .implementation-work/login-brand-maintenance.json |
| AC-02 | 四项使用位置共享Logo；源资产格式、尺寸、页面清单及生成来源校验 | 通过，4项位置、1份资产 | .implementation-work/login-brand-asset-audit.json、ui-delivery/login-brand-20261021 |
| AC-03/04浏览器 | 真实Chrome156、当前本机账号；两次表单登录和注销；375/820/1440px及200%缩放；当前会话和品牌资源；导航展开及收起；密码存储检查 | 通过，页面错误与失败请求为空 | tests/browser/login-workspace-checks.mjs、.implementation-work/login-brand-browser.json |
| AC-04账号接口 | 比较当前真实凭据；检查无缓存以及Origin、Host、跨站、请求方法、查询参数边界 | 通过 | .implementation-work/login-brand-account-http.log |
| AC-04类型 | npm run typecheck:medical | 通过，613项固定Schema引用编译通过 | .implementation-work/login-brand-typecheck.log |
| AC-04构建 | npm run build:medical，最终资源路径 | 通过，3分34秒 | .implementation-work/login-brand-build.log |
| AC-03/04生产 | 生产预览实际登录、读取PLATFORM_ADMIN并注销；375/1440px；构建文件不含当前密码；生产没有本机凭据接口和账号卡片 | 通过，544份构建文件核查通过 | .implementation-work/login-brand-production.json |
| 工程检查 | Python编译、浏览器脚本语法及git -c core.whitespace=cr-at-eol diff --check | 通过 | 当前工作区检查 |

前端开发服务127.0.0.1:18083与后端127.0.0.1:18080均继续运行，HTTP读取返回200；生产预览18084已经关闭。开发记录、页面映射与资产检查记录均已保存；未提交或推送Git。完整产品业务验收不属于本任务范围，其他任务状态继续由原有任务记录管理。
