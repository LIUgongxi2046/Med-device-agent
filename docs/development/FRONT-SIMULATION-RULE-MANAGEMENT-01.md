# 独立 SIM 评估规则前端

## 交付

- 管理入口：`/agents/simulation-rules/:ruleVersion?`，路由名 `MedicalSimulationRules`（由 root 集成）。`new` 展示独立创建表单，其余为固定版本读取与人工审查。
- `simulation-rule-create.vue` 复用已有结构化表达式、逐项输入属性、有据常量与 `rule-basis-editor.vue`。只需编辑常量实际值的 JSON，不要求手编完整请求。修订原版本必须实际读取已核准 SIM 规则，摘要只读，并在保存前重新核对。
- `simulation-rule-management.vue` 展示真实内容、状态、治理修订与固定依据。显式读取基线、选择人工决定并确认后，提交前再次读取和比较 CAS/完整基线。没有默认核准、自动执行或虚构发现列表。
- `simulation-rule-versions.ts` 直接使用现有后端 `simulation-rule-version-workflow.schema.json`，本地 Ajv 严格校验。独立 `simulation-rule-write-view.schema.json` 仅保存请求身份、上下文和摘要，不保存正文、来源内容或理由。

## 真实接口

- `POST /api/simulation-rule-versions`：201 草稿回执。
- `GET /api/simulation-rule-versions/{id}`：当前固定版本。
- `GET /api/simulation-rule-versions/{id}/approval-baseline`：当前人工审查基线。
- `POST /api/simulation-rule-versions/{id}/approval-decisions`：200 人工决定回执。
- 通用 `/api/operation-lookups` 与 `/api/operations/{id}`：使用原 `SIM-RULE-CREATE` / `SIM-RULE-APPROVE` 与原 key 核查完成操作；再读取固定规则后才能清除技术元数据。

列表接口不存在。入口只提供新建和已知固定 ID，创建成功提供真实回执链接。当前规则仅支持企业级依赖。用途固定 `SIMULATION_EVALUATION`、`production_eligible=false`，核准不能赋予生产使用资格。普通规则组件、发现目录与发布 Schema 均未修改。

## 边界与恢复

- 普通文本、UUID、常量 JSON、缺字段、重复依据/常量及依据归属等编辑问题在本地预检，不派发 medical-failure，不写请求元数据，不发送请求。
- 服务端响应 Schema、内容哈希、SIM 绑定摘要、企业/当前主体、权限、固定引用和 CAS 继续失败关闭。绑定摘要依据后端六字段 canonical binding 重新计算；审查 definition_manifest_sha256 按后端相同的三个发布 Schema 摘要重算。
- 结果未知时保留原 key。仅显式 lookup 得到 NOT_FOUND 后允许重填完整原正文并按原 hash 重放；FOUND/PROCESSING/FAILED/EXPIRED 不自动建立替代请求。
- 读取使用代次隔离。提交在 dispatch 前检查生命周期取消；已发 POST 故意不附取消 signal，因为页面卸载不代表服务端回滚。卸载/身份切换后的迟到结果不更新 UI、不刷新其他身份、不清原 key。
- 独立写锁持有期持续到自己的异步操作结束，即使卸载也观察锁归属；旧 finally 不得释放新操作的 medicalWriting。

## 验证

`node tests/unit/simulation-rule-management-checks.mjs`

测试编译真实 Vue script/setup 与 template，以 Vue renderer 执行实际组件 handlers，保留真实严格适配器/Schema，只替换请求与应用基础设施。覆盖局部错误零写入/session 保留、绑定摘要篡改、真实创建回执、精确原 key 恢复、不同 operation 拒绝、审批前 CAS 变动、显式批准后的上下文刷新和复读、未知结果原 key、卸载迟到写响应、重叠读取旧 finally、dispatch 前 abort。

测试使用隔离合成数据，不代表浏览器视觉验收或真实后端业务验收。全局 typecheck/build 由 root 聚合运行。本切片不增加后端接口，不触碰本轮 065 准备生产者接口。
