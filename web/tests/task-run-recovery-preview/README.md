# 运行恢复核查：隔离前端预览

本目录挂载生产组件 `task-run-recovery.vue`，使用已有 `RunView` 类型的本地模拟记录。
所有界面均明确标识“模拟数据 / 未连接后端”，不会创建、继续、重试或停止运行。

## 场景

- 失败、停止/中断、等待输入、未知状态、执行中、未读取。
- 读取许可撤销、只读模式、活动/未结算调用。
- 原运行、任务和步骤修订完整展示；失败/缺项在页内导航。
- 用户选择、理由与核查声明仅保存在组件内存，始终不能提交执行。
- 原启动/停止请求的核查不被重新标注为新运行重试。

## 验证

从仓库根目录运行：

    node --test web/tests/task-run-recovery/recovery.test.mjs
    web/node_modules/.bin/vue-tsc --noEmit -p web/tests/task-run-recovery-preview/tsconfig.json

18 项测试包含纯模型、实际 Vue 模板编译、实际 Vue SSR、权限隐藏、只读表单、HTML 转义、完整大整数版本及执行边界。

已有 Vite 开发环境可访问 `/tests/task-run-recovery-preview/index.html`。本轮没有启动浏览器、调用后端或执行真实业务。

## 集成

组件 props：`snapshot: RunView | null`、`active: boolean`、`canRead: boolean`、`canWrite: boolean`、`loading?: boolean`。
唯一事件为 `refresh`，交由父组件重新读取实际运行。权限由父组件依据现有 `run.read` 与 `task.run` 许可分别计算；此组件不产生新许可、不派发写操作或取消操作。

当前接口没有提供可恢复检查点、完整快照和恢复资格，也没有继续/失败重试动作契约。因此最终操作始终禁用；手工勾选不代表服务端核验通过。

尚未验证：浏览器视觉、键盘实际操作、真实业务恢复。后端与运行行为不在本轮变更范围。

## 单文件实际 Vue 预览

在 `web/` 执行 `node tests/task-run-recovery-preview/build-standalone.mjs`，重建并内联 JS/CSS 到 `/workspace/shared/task-run-recovery-preview.html`。可用第一个参数覆盖输出位置。构建仅使用本地已有依赖，不安装软件、不连接后端。生成文件不依赖外部入口资源，保留明确模拟数据横幅。
