# SIMULATION-CONFIGURATION-FRONTEND-01：独立模拟配置草稿页面

## 范围

2026-10-18 新增 `/simulation-model-configurations`，用于当前企业身份下的模拟配置目录、固定版本回读、新建与追加修订。该页面仅使用独立的 `/api/simulation-model-config-revisions` 接口，保持既有 `/model-configurations` 外部供应商契约及 Schema，不改动既有评测页面。独立审查发现既有外部页同样存在摘要核对期间失效后仍首次派发写入的竞态，按限定范围同步补上可选 AbortSignal 的派发前检查；其他外部行为不变。

页面显式展示「模拟」「DRAFT」「不具备生产资格」。案例 UUID 与 SHA-256 仅为 `DECLARED_ONLY` 声明；保存配置不会核验案例文件实际字节、执行模拟、调用供应商、登记运行成功或授予生产资格。没有提供运行、连接验证、批准或发布动作。

## 实现

- `web/src/api/medical/simulation-model-configuration-domain.ts`：独立模拟类型、范围授权入口、结构及语义校验、请求摘要、CAS 与幂等恢复结果核对。
- `web/src/api/medical/simulation-model-configurations.ts`：独立列表、固定版本 GET 和草稿 POST；验证封闭响应 Schema、no-store/version/request-id 响应头、企业、当前上下文及维护修订。
- `web/src/api/medical/simulation-model-configuration-write-view.schema.json`：封闭的恢复元数据结构。
- `web/src/views/medical/simulation-model-configurations.vue` 和 `simulation-model-configuration-fields.vue`：中文结构化表单及目录/详情/恢复界面。
- `protocol.ts` 登记三个新 Schema；`router/index.js` 添加单一页面入口；`request.js` 将模拟接口纳入已有严格、有限大小的 JSON 编解码分支。

模拟器 ID/版本、执行方式、来源记录 Schema 身份及摘要、生产资格和无供应商请求计费标记全部从服务端模拟配置 Schema 的固定常量导入，只读显示。表单仅开放模拟模型 ID/版本、声明案例 UUID/摘要、能力限额、配置范围和保存理由；没有凭据、服务地址、供应商价格或数据外发设置。

限额保留十进制字符串，不转成 JavaScript Number；校验 uint64 上限、正整数、输出不超过上下文、temperature 与 none 推理档位的一致性，以及截止时间与停止项范围。案例摘要只做结构检查，未把摘要格式正确等同于案例有效。

## 保存与恢复

- 新建为 `config_id=null`、`expected_revision=0`、`based_on_revision=null`；追加必须从当前维护头复制配置 ID、维护修订号及固定版本/摘要。历史固定版本可回读但不能直接追加。
- 企业/项目/配置权限摘要仅作为页面入口判断；服务端仍核验当前授权、所属范围和条件。具体配置权限不能用于新建。
- 恢复元数据使用独立 `medical.simulation.configuration.write:` 命名空间，只保存主体、企业、原上下文、目标/固定依据、请求 UUID 与摘要。案例内容、自由文本和表单正文不持久化到浏览器存储。
- 原操作查询沿用现有 `CP-08` 操作记录；操作完成后必须通过模拟专用 GET 回读目标修订，验证模拟 Schema、原主体/范围/依据、定义摘要和原回执，再清除待核查标记。外部配置响应不能作为模拟配置恢复结果。
- 已查到处理中、失败或已过期操作时不能生成新请求绕过。仅当原操作查询为 NOT_FOUND、原上下文一致且完整正文摘要相同时，才沿用原幂等标识重试。
- 保存后的当前企业上下文重新读取。会话变化、隐藏页面或离开页面会清空敏感表单内存并中止读取；未保存编辑有离开确认，写入进行中阻止页面跳转。异步摘要核对期间会话或页面失效时阻止首次写入派发；已派发后的传输不确定则保留原请求待核查。
- `provider_charge_amount=0.00000000` 仅表示本地模拟不发供应商请求，不表示基础设施免费或预算结算。

## 验证

纯本地验证，不启动浏览器、HTTP 服务、Socket、数据库或供应商调用：

- `node scripts/check_simulation_configuration_frontend.mjs`：187 项通过。覆盖固定常量、禁止外部字段、封闭结构、参数/CAS、权限入口、摘要恢复、模式拒绝、组件编译及原契约摘要。
- `node scripts/check_simulation_configuration_transport.mjs`：30 项通过，12 次内存替身调用。覆盖独立路由/头、原请求重试、操作回读、模拟回执标记、权限/参数预检查、异步摘要期间失效阻止首次派发及错误响应。
- `npm run typecheck:medical --prefix web`：通过。
- 在 `web/` 中运行 `node node_modules/vite/bin/vite.js build --mode medical`：通过，30.36 秒；存在现有大块体积警告。
- 相邻外部配置纯测试 97 项、评测纯测试 99 项和评测替身传输测试 15 项通过，仅外部 API/页面的派发前失效检查及对应回归有必要改动，既有评测源码未更改。

日志：`.implementation-work/logs/simulation-configuration-frontend-tests.log`、`simulation-configuration-typecheck.log`、`simulation-configuration-web-build.log`。

这些检查不证明实际 HTTP、数据库、浏览器渲染/交互、模拟案例有效性或执行成功。需在允许对应环境验证后另行验收。

## 独立审查

独立审查发现摘要核对期间页面/会话失效后仍可能首次派发 POST 的竞态。模拟 API 与既有外部 API 均增加派发前 AbortSignal 检查，页面传入原控制器信号；已派发请求不因本次修复被自动重做。延迟 WebCrypto 的纯测试确认取消后零次派发、原待核查请求保留。

审查者独立复跑模拟前端 187 项、模拟传输 30 项、外部配置前端 97 项及单独的摘要竞态复现，全部通过；限定范围未发现剩余阻断项。此结论仍不扩展到浏览器、真实 HTTP、数据库或执行验证。
