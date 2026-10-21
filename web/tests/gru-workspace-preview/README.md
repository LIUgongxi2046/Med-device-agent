# Gru 实际 Vue 组件独立预览

从 web 目录运行 `node tests/gru-workspace-preview/build-standalone.mjs`，生成 `/workspace/shared/gru-workspace-preview.html`。HTML 内联全部入口脚本和样式。

预览使用生产 `document-gru-records.vue`，只在此 Vite 配置中替换用户 Store 和 HTTP transport。模拟会话只授予 conversation.read，不授予保存、执行、导出或采纳权限。transport 仅返回四类明确读取夹具；所有其他读取和所有写入抛出异常，无网络回退。所有记录标为模拟用户记录，没有助手回复。生产源文件无需修改。

操作：刷新我的会话，选择唯一模拟会话；切换未保存正文和选区状态。演示不证明后端功能或截图视觉验收。
