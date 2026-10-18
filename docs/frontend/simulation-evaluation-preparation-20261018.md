# 065 企业模拟评测材料前端

新增 `simulation-evaluation-preparation.vue`、`simulation-sample-editor.vue`、`simulation-evaluation-preparation.ts`，嵌入模拟准备页。未改后端、router/protocol全局文件。

## 用户流程

1. 结构化填写真实ExpectedItems：样本/来源家族标识、文档类型、已登记输入引用、逐维度预期项目。
2. 可从typed GET读取真实ExpectedItems回执及完整文档，带入SampleManifest样本，明确选择split；无占位UUID。
3. 授权用途见证选择真实dataset，并typed GET读取完整SampleManifest及样本。witness仅由服务器生成，前端不构造/编辑私有许可材料。
4. Preview通过当前权限及引用校验，前端再核对canonical字符串和原字节SHA，下载字节不改写的TXT。预览registered=false、approved=false。
5. 选择真实处理项目后走显式ENTERPRISE上传模式（由上传页面切片提供），不得把project_id=null当处理项目。完成真实来源审核/权利核准后，选择企业来源固定版本及rights，原件SHA须与预览完全相同，再显式登记。
6. 展示真实ARTIFACT引用。registered=true仍approved=false、production_eligible=false。

## 恢复和安全

- 注册使用原key和固定Register正文，结果未知存同actor/tenant的恢复记录；不自动重发。
- 通用 `SIM-EVALUATION-PREPARE` operation lookup + operation固定回读 + typed GET恢复；核对operation_id、artifact、source/rights/hash/creator。
- 当前上下文变更只可查询恢复；原上下文完全一致且NOT_FOUND时才允许用户明确原key重放。NOT_FOUND不是未发生证明。
- 页面隐藏/身份变更清理展示，卸载迟到不安装结果；共享写锁只由拥有者结束释放。
- 授权Document使用最终opaque approval witness，仅rights/decision/witness_sha256/evidence_count，不打开私有证据。
- availability父组件将未启用功能整段不挂载，063父setup另有enabled guard。

## 验证

`node --test tests/frontend/simulation-evaluation-preparation-checks.mjs tests/frontend/simulation-preparation-checks.mjs`

测试运行真实SFC setup、Vue reactivity和真实API适配器/冻结schema，mock HTTP边界。覆盖局部错误、canonical下载字节、项目来源拒绝、未知请求保留、typed GET恢复、操作串线拒绝、opaque witness、卸载迟到。没有浏览器视觉或真实对象存储验收结论。
