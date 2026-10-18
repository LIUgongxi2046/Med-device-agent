# 岗位历史与计划编辑：局部输入边界

## 本次范围

- `enterprise-roles.vue`：手输历史岗位版本 UUID 的格式问题在本地字段显示，保留已读岗位及原输入，不调用读取 API。
- `task-plan-editor.vue` 与纯函数 `plan-input-issues.ts`：在 `preparePlan` 之前检查未提交草稿的 Schema 和已有严格验证器中的编辑语义。覆盖理由/目标/问题文本规范、章节身份与节点完整唯一覆盖、重复研究问题 key、章节与问题引用、自引用、删除/改名后遗留依赖以及问题目录与章节的关系。错误显示在字段和计划问题摘要；不自动修复或覆盖用户草稿。
- `task-plan-return.vue`：提交 handler 补充与现有按钮一致的理由预检，防止直接调用绕过按钮约束。

## 安全边界

局部检查仅用于尚未发送的可编辑输入，不替换原 `validatePlanInput`、`preparePlan`、保存、读取和恢复校验。身份、权限、固定编辑依据、CAS、响应 Schema/哈希与原操作摘要继续走严格失败处理。提交结果未知时保留原技术元数据及幂等 key；不得用改写草稿替换原操作。

未修改全局 protocol/main、后端或冻结 Schema；没有增加 API 或模拟业务成功。纯预检不新增超出原严格验证器的依赖环判断，服务端仍为业务权威。

## 验证

运行：`node tests/unit/roles-plan-local-input-checks.mjs`

测试编译真实三个 Vue SFC 的 script/setup 和 template，通过 Vue renderer 执行 setup/handlers；仅替换请求、store/router 和浏览器基础设施，保留真实适配器/Schema/严格验证。测试数据是隔离的合成测试数据，不是线上业务验收。

覆盖普通错误的零请求、零 pending 写入、不派发 medical-failure、不清 session、保留原草稿与写锁；有效 UUID/计划仍抵达严格 API，畸形服务端响应仍派发失败；保存未知结果保留原 key，随后不同正文仍被原幂等冲突校验拒绝。
