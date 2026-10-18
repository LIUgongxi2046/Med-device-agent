# FE-08 上传工作区前端增量

日期：2026-10-18。依据：PRD §8.3.2、docs/前端实施计划.md FE-08、LLD-前端 S04/S05、ui-delivery/component-state-map.csv 的 knowledge-collections/library-next-step。

## 已实现范围

- `source-uploads.vue`：替换横向宽表为可键盘选择的文件队列和当前文件详情；文件声明、恢复原文件、继续接收、查询固定结果和打开固定来源在当前文件附近提供。队列数量仅是本页 `items.length`，不作为服务端批次统计。身份、路由和原页面清理同时清理当前选择。
- 服务器已接收字节和当前分段本地已发送字节分别显示。进度仅取已核查 registration 的 received_size / request.size_bytes，用 BigInt 计算；未知、零容量或越界数据不生成进度，100% 接收不被显示为解析或核准成功。
- `source-upload-result.vue`：突出真实 REGISTERED/DUPLICATE/FAILED 回执；每个解析版本分别显示解析、索引与人工核准。保留所有原完成操作、权利、来源、处理尝试、审查摘要、完整清单和固定版本字段，详细依据可折叠。
- `source-declaration.vue`：原字段分为归属与版本、来源记录、使用授权三个组，不增删字段、不代填授权或自动确认。

## 复用契约和边界

仍使用 `source-uploads.ts` 的原上传登记、分段发送、完成、原请求查询和固定结果核查。接口为 POST /api/uploads、GET /api/uploads/{uploadId}、PUT /api/uploads/{uploadId}/parts/{partNo}、POST /api/uploads/{uploadId}/completions、GET /api/uploads/{uploadId}/status，以及原操作查询。真实控制器包括 SourceUploadController、SourceUploadStatusController。

未更改接口、原请求键、固定声明摘要、恢复协议、后端、路由或权限检查。不新增自动重试或自动轮询，不扩大上传范围。既有 sendConfirmed 的逐文件执行和异常行为保持原实现；不宣称新增部分失败自动续传。未读取状态、没有版本和空清单分别显示。

## 针对性检查

命令：`node --test tests/frontend/source-upload-workspace-checks.mjs`

实际结果：23/23 通过。覆盖三份 Vue script/template 编译；八个原上传、恢复、停止和校验函数逐字摘要不变；已确认进度的未知、零、越界、负值、超安全整数和本地发送排除；独立状态、固定来源、重复结果及选择清理边界。

`git diff --check` 对本增量文件通过。测试包含静态编译、原逻辑保留、纯计算及编译后组件 setup 的真实 Vue 响应式运行检查（外部 I/O 使用桩），不等同真实服务或浏览器验收。本轮未启动服务器、浏览器或执行截图；完整类型检查及生产构建由聚合检查负责。

## 后续实际验收

1. 当前获授权项目选择多个真实文件，分别填写声明；切换队列项时状态、理由和确认不串项。
2. 选择原文件恢复时核验大小和摘要；未知完成结果先查原请求，重复点击不创建新身份。
3. 实际传输中观察服务器接收与本地发送分离；停止仅终止原有本地发送流程。
4. 对真实 REGISTERED、DUPLICATE、失败、解析部分失败、索引未建立及待核准结果分别核查展示；固定来源跳转不更换版本。
5. 会话切换、隐藏、离线及权限错误仍清理敏感内容；窄屏与键盘可选择当前文件。
6. 七格式真实上传、处理、定位和阅读，继续按 FE-08 完整业务验收执行，本增量不将其标记完成。

## 交互复核修正

旧确认撤销 watcher 仅跟踪当前打开的声明对话框。现逐文件观察完整声明快照，任何字段编辑或服务端读回替换都会同步撤销该文件的本地确认，关闭对话框后同样有效；其它文件确认不受影响，传输进度和消息变化也不会误撤销确认。

移除当前文件会清理当前选择、声明对话框和重新选文件目标；异步文件摘要返回时若替换目标已移除，不再回填或重开其对话框。原登记未知时明确提示只能恢复原声明；既有 API 的声明摘要冲突检查继续阻止覆盖原请求。

新增六个编译后组件 setup 运行测试，使用真实 Vue ref/reactive/watch/computed，外部 API 桩隔离网络：关闭对话框后的深层字段变化和整体替换、未确认阻止 send、删除和会话切换清理、迟到原文件回填拒绝、原声明冲突传播且无新写入、服务端恢复撤销旧确认、未知登记保留原声明和原请求。这些运行测试不以函数摘要代替行为判断，不声称浏览器或实际上传服务验收。

## 选文件的本地输入预检

核对调用链：FileUpload 的 selectionOnly 分支在 handleFileSelection 直接发出原文件；sourceMediaType 对不支持扩展名/空文件调用 protocol.fail；后者 dispatch medical-failure，main.js 会使当前医疗会话失效。因此仅在页面 selectFile 开始增加无副作用检查，复用 sourceMediaTypes 的真实扩展名集合并检查正的安全整数大小。不合格文件只更新本页 notice，不读取字节、不建立记录、不发上传、不抛协议错误，原队列、确认及恢复目标保持。大小合法不代表文件内容已获准。

保留后续 sourceMediaType、摘要、原文件身份、权限、schemaIssues 和所有服务器核验。新增三项 setup 运行回归涵盖错误扩展名、空/负/非有限/非安全大小、合法大小写扩展名的未提交选择，以及合法扩展名但原摘要错误仍 CONTENT_MISMATCH 拒绝。实际 23/23 通过，未更改全局 protocol/main 或后端。
