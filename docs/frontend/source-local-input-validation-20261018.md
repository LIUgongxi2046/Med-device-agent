# 来源权利与 EXACT 局部输入预检

日期：2026-10-18。改动范围仅 `source-rights.vue`、`source-exact-fields.vue`、独立 `source-input-issues.ts` helper、测试和本说明。未修改 protocol、main、后端、strict API 或冻结 Schema。

## 风险与处理

协议 fail 会发出 medical-failure，主应用使医疗会话失效。以下普通手输错误现仅在 strict API 调用之前产生局部 issues，不进入该链路：

- 权利恢复 JSON：语法解析、完整 DecisionInput Schema（不能用 PreviewInput 冒充恢复决定）、既有 rightsInputIssues 语义规则。
- EXACT 固定字段版本：非空手输版本用已登记 UUID Schema 预检，空值继续读取当前版本。
- EXACT 发布与恢复：完整 PublicationInput Schema、修订整数上限、规范化且非空理由；字段原值及别名有效 Unicode/长度/文字、范围安全整数、别名/审核理由配对、别名独立值、规范化别名理由、重复原值/位置/别名身份。
- 从原文选字段：本地预检选择长度和字段规则，合法输入仍调用 verifyExactField。

页面保留输入文本、字段和原 pending 元数据，明确显示尚未提交及待修正项。未对原值/别名自动 trim 或 Unicode 归一化，避免篡改固定原文。理由规范化要求只提示，不代改。

## 安全边界

helper 只返回 issues，不调用 fail，不发请求。唯一 catch 仅包围本地 JSON.parse 且只处理 SyntaxError；其它异常继续抛出。未 catch strict 校验、digest、权限或服务器响应。

恢复 JSON 通过语法/Schema/语义预检后，仍走原 strict 验证与 requestBodyDigest 比对。恢复 body 与 pending.body_sha256 不同继续触发原 IDEMPOTENCY_CONFLICT；身份、企业、权限、原件/固定定位、服务端回执及固定清单一致性仍使用原 fail-closed 路径。合法发布及恢复继续使用原请求键与元数据协议。

## 检查证据

命令：`node --test tests/frontend/source-local-input-checks.mjs`

实际结果：11/11 通过。测试使用 compiler-sfc 编译后的真实组件 setup、Vue 响应式、Ajv2020 及仓库实际 source-exact/source-rights-decisions Schema；strict domain 和 rightsInputIssues 使用实际实现，网络及会话服务以桩隔离。

覆盖：两页编译；非法/合法固定 UUID；修订语法和边界、理由、别名和重复字段不发请求；合法发布进入原 strict send；两种恢复语法/Schema/语义错误保留输入与原 key；两种合法结构但摘要不符仍抛 IDEMPOTENCY_CONFLICT；合法恢复进入原路径；超长原文选择局部拒绝；非法 Unicode、空白、范围、别名理由的无副作用检查。

diff 检查通过。未运行浏览器、服务端或完整构建；全局类型与构建由根任务聚合。测试不代替真实权限/签发/业务验收。
