# protocol 首次导入性能核查（只读生产代码）

## 结论

`web/src/api/medical/protocol.ts` 注册所有固定 schema 后，在模块顶层遍历固定引用列表，对每项执行 `ajv.getSchema` 并写入 validators Map。因此首次导入同步编译全部 **432 个固定引用**，并非只有 addSchema 的登记成本。

测量版本 SHA-256：`800fc58ff805d2cdc3068613f1598ca6ef6e214db97cc5dc202ddaa66690d56d`。

## 实测方法及数据

隔离 Node v24.19.0、现有 esbuild、真实 protocol 及 JSON/YAML/AJV 依赖打包为 CJS。构建不计入导入计时。每个方案启动5个全新Node进程，测量 require 冷导入、addSchema/解析阶段、getSchema循环阶段及首个 identity schemaIssues({}, invalid payload)；之后同一校验重复1000次。后者是缓存调用成本，不代表典型有效业务payload。

仅测量副本通过 esbuild onLoad 内存变换：将顶层编译循环改为固定引用 Set，validate/schemaIssues 仅对 Set 内引用按需 getSchema 并缓存。生产 protocol 未改。再次逐项编译全部引用，证明候选能解析完整引用集。

| 指标（毫秒，中位数；冷导入附范围） | 当前全编译 | 临时懒编译候选 |
|---|---:|---:|
| 冷导入 | 1521（1330–1721） | 281（242–302） |
| 注册/解析阶段 | 214 | 243 |
| 顶层引用处理 | 1284 | 0.53 |
| 首次 identity 校验 | 0.56 | 14.90 |
| 缓存重复校验每次 | 0.001 | 0.003 |
| 后续遍历全部引用编译 | 0.97 | 1297 |

可复现测量脚本与原始结果：`.implementation-work/protocol-perf/measure.cjs`、`results.json`。两个候选均完成432引用的全编译。未连接HTTP或浏览器；未运行全局构建。

这些数字说明本机环境存在可延后的同步CPU成本，不证明用户手机的卡顿原因。Node依赖初始化、CPU负载、浏览器JIT/CSP、真实页面路由和网络均可能不同。尚未单独测每次新增schema的增量成本，不将全部耗时归因于Gru或某个新schema。懒编译仍保留schema导入、YAML解析和addSchema成本，不能声称消除全部启动成本或缩小网络包。

## 建议最小实施方案（待root分配）

1. 固定引用列表抽成只读 allowlist Set；保留所有静态 schema 注册、版本、AJV严格配置与格式校验，不开放动态URL/远端schema或任意引用。
2. 内部唯一 resolver：先检查 allowlist，再查 Map，再调用同步 getSchema，成功才入缓存。未知引用不能直接传给AJV；编译失败/缺失必须抛出，禁止返回成功、宽松validator、旧版validator或忽略错误。
3. validate 继续在无校验器或payload不符时执行现有 fail；schemaIssues 保留现有边界：已知schema普通输入错误返回issues，未知schema/编译失败属于程序完整性错误并抛出，不能伪装输入合法。不改变医疗失败事件或会话处理。
4. 构建/CI增加显式compileAll检查入口，只由测试调用，穷尽 allowlist 432项及后续增量。使用同一注册器和严格AJV，缺失引用、坏schema、重复/冲突ID必须让构建失败；不能仅依赖访问过的页面覆盖。
5. 回归覆盖：未知但AJV已登记的未允许片段仍拒绝；每个allowlist引用全编译；同引用第二次不再编译；无效/有效payload结果及errors一致；首次编译异常不会留下成功缓存；前端全套请求契约与本地输入预检仍通过。
6. 若需浏览器性能结论，下一步应单独测目标设备首屏Performance trace，区分网络、解析执行、schema编译和渲染，再决定是否还需拆分schema注册模块。当前无需扩大为异步校验或放宽验证。

## 实施记录（stage14归档之后）

已实施固定 allowlist + 同步懒编译缓存，保留原 imports、addSchema 调用及允许引用表达式。加入显式 `compileAllMedicalSchemas()`；`web/scripts/check-medical-schemas.mjs` 隔离加载真实模块并穷尽编译，失败退出非零。`build:medical` 与 `typecheck:medical` 都先运行 `npm run check:medical-schemas`。无需浏览器全局即可运行构建门。没有使用编译失败fallback。

`web/tests/protocol-schemas/lazy-compilation.test.mjs` 4项测试覆盖真实432基线全编译、缓存同一函数、未知/恶意/已登记却未允许引用、悬空ref故障注入、fail事件及构建脚本顺序。全编译数量以实际allowlist为准，基线至少432，允许067等显式新增注册自然增长。

实际实施后、067接入之前重新以同样隔离Node24/esbuild方式启动5个进程：冷导入中位 **315ms**（230–525ms）；首次identity校验中位 **15.23ms**；缓存重复校验中位 **0.0034ms/次**；剩余完整编译中位 **1385ms**。真实原始结果与可复现脚本为 `.implementation-work/protocol-perf/implemented-results.json`、`measure-implemented.mjs`。该次未用候选代码变换，只打包实际新protocol源码。仍是本机Node结果，不是手机或浏览器验收；没有以单次数字承诺固定性能改善比例。

全局typecheck/build由root统一执行；作者仅运行独立全量schema门与定向测试，没有运行Maven或全局构建。root接067期间作者不再修改共享protocol文件。
