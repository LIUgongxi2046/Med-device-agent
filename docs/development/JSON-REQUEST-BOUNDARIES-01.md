# JSON 请求边界检查（2026-10-18）

## 当前入口与已修复范围

打包入口为 `com.ruoyi.medical.runtime.MedicalApplication`，扫描范围 `com.ruoyi.medical`。当前显式框架导入不包含 `FilterConfig` / `RepeatableFilter`，不能把旧 RuoYiApplication 的全包扫描行为误当成本轮医疗应用行为。

批注回复及真实/模拟模型配置写入采用已有 65,536 字节限额：先检查头部和当前身份，声明长度超限直接拒绝；未知/chunked 长度最多读取 65,537 字节，超限返回既有错误封装的 413/FILE_TOO_LARGE。控制器离线测试已覆盖，真实 HTTP 尚未执行。

若将来改用旧 RuoYiApplication 入口，必须重查原 RepeatableFilter 的无界 JSON 缓冲与文本规范化行为；本轮未修改原框架过滤器。

## 尚未解决

`RuleVersionController` 的草稿/审批、`EvaluationController` 的登记/评分/终结、`RunCreateController` 的创建，以及抽查的 `TemplateController` 仍以 `@RequestBody byte[]` 接收数据，在业务校验前由 MVC 分配完整正文。认证和 CSRF 不等于内存限额。

规则常量和依赖数组没有总字节约束，评测定义也可能非常大。评测前端 `parseEvaluationJson` 有 2,000,000 JavaScript 字符单元上限，但它不是 UTF-8 字节上限，更不是服务端防护。不能宣称目前所有 JSON 接口都已防止大请求耗尽内存。

## 后续实施建议

按操作定义、记录可配置的传输资源上限，保留领域 Schema 与历史摘要，不施加未知的全局 `/api` 上限。较小请求可评审 16 KiB 运行创建/评测终结、128 KiB 评分等初始值；这些是建议，不是已经批准或已实现的限制。规则和评测定义需明确部署容量，并为大批次设计分批或固定制品引用。

公共有界读取器应只应用在明确 JSON 写入路径，保存身份/头部检查顺序与 413 错误协议。不得影响 `/api/uploads/{uploadId}/parts/{partNo}` 的专门二进制流式上传。后续需要控制器、真实过滤链、chunked/虚假Content-Length、跨限中止与大合法请求回归。
