# NAVIGATION-SESSION-20261022 页面切换与会话读取

任务状态来源为本文，当前ACCEPTED，文档CURRENT，last_verified=2026-10-22 09:51（Asia/Shanghai）。需求来源为2026-10-22用户报告切换页面进入/session-recovery并显示INTERNAL_ERROR。代码为main基线b0a3591及当前保留的未提交修改；本地前端18083、后端18080，沿用原有数据库与账号。当前源码摘要保存在.implementation-work/navigation-session-20261022-source-sha256.txt。

检索NAVIGATION-STABILITY-20261021、GLOBAL-ORGANIZATION-20261021、LOCAL-RESTART-20261021、main.js、user.readMedical、framework-request.ts和request.js。实际浏览器错误显示旧管理响应触发CONTEXT_CHANGED、当前会话请求产生ERR_CANCELED。原始证据为.implementation-work/navigation-session-20261022-red.log；普通菜单、组织切换和注销回归证据为.implementation-work/navigation-error-20261022-red.log。

## 验收要求

- NC-01：真实账号登录后依次进入项目、资料、模型和首页，页面路径与目标一致，当前会话有效。
- NC-02：旧页面管理请求晚于新导航返回时，旧响应停止交付，新会话继续有效。
- NC-03：连续导航期间，已取消的读取不清除新会话，不转入恢复页面，最后目标页面完成读取。
- NC-04：实际组织切换、原生管理读取及注销继续采用服务端身份和授权结果。

Given真实本地服务与liuhaonan账号；When进入不同业务页面并连续发起导航；Then最终页面与最新目标一致，medicalError为空。输入仅使用当前真实账号和已有组织，不生成业务数据，不使用接口替换或截图。

## 实现与验证位置

| 文件 | identifier | 职责 |
| --- | --- | --- |
| web/src/api/medical/framework-request.ts | verifyFrameworkResponse | 管理响应身份、有效会话与原请求编号检查 |
| web/src/main.js | router.onError | 页面导航错误与取消处理 |
| web/src/layout/components/HeaderNotice/index.vue | loadNoticeTop | 返回公告读取Promise，由Vue管理生命周期异步错误 |
| tests/browser/navigation-session-checks.mjs | sequential、superseded | 真实导航复现与回归 |
| tests/browser/workspace-experience-checks.mjs | visit、selectOrganization | 组织、菜单、管理读取与注销回归 |

## 当前行为

verifyFrameworkResponse在解释响应前检查原请求的generation；属于已被新会话读取替换的请求时抛出Axios CanceledError，停止旧结果交付。当前请求的身份、响应状态、缓存及内容检查保持有效。router.onError识别已取消的请求，不改变新导航的会话状态；真实认证和其他错误继续进入现有处理。loadNoticeTop返回实际Promise，使Vue能够处理组件请求结束时的取消状态。

## 实际验证

| 验收 | 操作与断言 | 结果与证据 |
| --- | --- | --- |
| NC-01 | 真实登录，依次进入/projects、/library、/model-configurations、/index | 四项通过，navigation-session-checks.json |
| NC-02 | 登录后立即导航，公告读取与新会话读取重叠，旧结果停止交付 | 页面错误为空，navigation-session-20261022-green-final.log |
| NC-03 | 三轮/projects尚在读取时进入/library，断言最后路径/library及medicalError为空 | 三轮通过，navigation-session-checks.json |
| NC-04 | 两个已有组织切换，项目/资料/模型菜单，当前组织管理请求及注销后401 | 通过，navigation-session-20261022-regression.log及workspace-experience-browser.json |
| NC-TYPE | npm run typecheck:medical | 退出0，613项固定引用通过，navigation-session-20261022-typecheck.log |

浏览器命令为`node tests/browser/navigation-session-checks.mjs`及`node tests/browser/workspace-experience-checks.mjs`，TMPDIR均采用项目.implementation-work/migration-check-tmp。证据位于.implementation-work。真实浏览器没有页面错误；回归没有创建业务申请，注销后权限、路由和登记资格均清除。当前Vite已加载三个修改文件，前端监听18083；本任务没有更新后端制品或数据库。

本任务继续保持原账号、组织授权、服务端错误及响应内容校验。NC-01—04已经通过，没有剩余必需实施。完整应用功能和视觉验收不属于本次缺陷范围。关联[NAVIGATION-STABILITY-20261021](NAVIGATION-STABILITY-20261021.md)，后续相关修改继续核查上述请求身份和取消行为。
