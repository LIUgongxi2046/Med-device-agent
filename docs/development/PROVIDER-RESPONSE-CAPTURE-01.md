# 模型供应商原始响应捕获

本模块位于 `model-gateway/src/medical_model_gateway/raw_response.py`，由既有 DeepSeek SDK 适配器显式传入一次性的 `ProviderResponseCapture` 使用。默认关闭，必须明确请求字节、响应字节、时限上界及已批准规范请求摘要。捕获本身不签发调用许可、来源许可或预算。

## 保存内容与边界

- 固定 HTTPS chat completions 端点，单一 POST，关闭代理环境、重试与重定向。实际 SDK 请求 JSON 的 RFC8785 摘要必须匹配已批准请求；另外记录 SDK 实际 HTTP 请求实体字节摘要。
- 原始响应指 HTTP entity 字节，不是 TLS 帧，也不是 SDK 归一化文本。只允许 identity 编码，严格限制长度和时间。
- HTTP `x-request-id` 与 SDK chunk 的 response ID 分开记录；前者缺失或重复时不生成替代值。不保存授权头、Cookie 或其他响应头。
- SDK 遇到 `[DONE]` 会提前关闭流。只有适配器已验证真实终态时，才对同一个响应执行有界尾部读取以确认实际 EOF；取消不继续读取，也不再次调用供应商。
- 内部推理内容禁止保留。首版只支持明确 `reasoning_effort=none`。完整响应仍逐字段检查；含内部推理、无法解释、被截断或读取未完成的内容不保存原文，保留状态与已观察长度。不能据此声称原件完整。
- 完整收齐 HTTP 错误响应不代表模型成功。`capture_complete`、`response_eof_observed` 与供应商终态、实际用量、计费结算是独立事实。

## 证据格式

封闭 Schema：`urn:medical:provider-response-capture:1.0.0#/$defs/CaptureArtifact`。

`provider-response-capture.schema.json` 在 Python 包与 Java 资源中保持相同字节。固定 SHA-256：`99e4a445fac2018857fd989ba2e0d5df1605d31fce28add1865c1498990c935d`。

制品为 RFC8785 JSON 容器，含元数据与原始字节的 Base64。对象存储登记摘要必须是完整容器摘要；`metadata.sha256` 是解码后的原始响应摘要，两者不可混淆。调用方必须再次核对 invocation、dispatch attempt、receipt、批准请求摘要、原字节长度、原终态和实际用量。Python 通用容器最大64MiB；实际消费者可以采用更低的明确上界，不可截断后报成功。

## 已执行验证

- `tests/test_provider_capture.py`：24项通过，包含真实 OpenAI SDK 3.24.0 与 httpx2 MockTransport 流解析、CRLF分块字节、终态后EOF、用量、失败、取消、隐含推理拒绝、封闭Schema与容器上界。
- 原 `test_model_gateway.py` / `test_model_simulation.py`：31项兼容回归通过。
- 独立 Java 原始证据消费者已用同一实际 SDK 测试产物验证 HTTP/SDK ID 区别、原始用量和批准请求绑定，并拒绝变造用量与请求摘要。

以上未使用真实供应商密钥、未发送外部模型请求、未产生付费调用。正式 C07 对象登记、原件重读、预算结算与调用恢复必须使用完整业务实现；本捕获模块不能替代这些流程。
