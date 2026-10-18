# 规则管理前端（TP-03 / TP-04）

## 可达入口

- `/rules`：实际授权规则列表，按固定版本或引擎检索，受限游标分页；也保留固定版本入口。
- `/rules/new`：结构化编辑表达式、输入属性、有据常量、固定依据、可选已核准原版本及理由。
- `/rules/:ruleId`：读取不可变规则内容及当前治理状态，核准/退回/停用的明确人工确认。
- 模板人工审查页中的规则引用链接携带原引用 SHA-256；规则读取会核对引用摘要。

## 实际接口

- GET `/api/rule-versions` 使用 `configuration-discovery.schema.json` 的 `Query` 和 `RuleList`。每页 25 项；空 `items` 且非空 `next_after` 仍为可继续扫描，不展示为终止空结果。
- POST `/api/rule-versions` 使用 `rule-version-workflow.schema.json` 的 `VersionInput` 和 `Created`。
- GET `/api/rule-versions/{id}` 和 `/approval-baseline` 使用 `Read` / `Preview`。
- POST `/api/rule-versions/{id}/approval-decisions` 使用 `Decide` / `Receipt`。
- 原操作恢复使用既有 `/api/operation-lookups` 和 `/api/operations/{id}`。

## 约束

真实会话中的企业/资源动作范围和服务端结果共同决定可读/可写状态。没有企业或权限时不请求受限内容。Schema、正文摘要、固定依据、身份、治理修订及回执上下文均核验。

人工决定前重新读取基线。修订、授权基线或允许动作变化时，本次不发送决定并撤销页面确认，需重新审查。重复提交保留原幂等键、完整请求摘要、原上下文和 CAS；未知、执行中、失败或过期结果不伪装为成功。

常量JSON先检查原始数值token，再进行原生JSON解析。嵌套对象/数组中的高精度小数、非安全整数、溢出或下溢均被拒绝，不自动舍入或改成字符串；`3`、`3.0`、`3e0`等值表达保持相同数值语义。

浏览器会话存储仅保存请求身份和摘要，不保存表达式、常量值、来源正文、决定理由或 CSRF 凭据。刷新后可查询原操作；确实完成且与读取的规则匹配才清除待核查身份。原创建回执和后续当前治理状态分别展示。页面不运行适用判断，未知输入保持 `UNKNOWN`。

## 验证

- `cd web && npm run typecheck:medical`
- `node tests/unit/rule-management-checks.mjs`

后者使用隔离传输和明确标为 SIMULATION 的合成测试数据，覆盖 schema、权限、幂等、未知结果、回执一致性、分页以及 Vue SFC 编译。不是浏览器、视觉或真实后端端到端测试；测试报告写入 `.implementation-work/rule-ui-unit/result.json`。实际浏览器与全量构建由集成检查单独执行。
