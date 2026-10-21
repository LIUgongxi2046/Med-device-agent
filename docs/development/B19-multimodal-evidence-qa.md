# B19 多输入证据问答：已实现边界与剩余能力

2026-10-21；实现代码已写入，未运行测试、编译、服务、迁移、模型或网络请求。

## 已实现

- POST `/api/multimodal-qa/specifications`：封闭 `schema_version:1.0.0`、`scenario`、`selection`；scenario 使用真实 B14 Author（较大 INPUTS 明确选择 Author 1.1.0）。输入定义不是自动审批。角色、知识、规则、输出、人工责任、失败策略及验收证据仍必须完整，评审和发布仍走 B14。
- Selection 固定 `scenario_kind:MULTIMODAL_QA`、`capability:PARSED_EVIDENCE_QA`、`failure_policy:ABSTAIN_ON_MISSING_OR_CONFLICTING_EVIDENCE`、review_rule、inputs、questions。Input 固定 key/source/rights_revision/evidence/literal/representation；Question 固定 key/section_id/question/input_keys。来源哈希、解析、引用、许可修订和题目关联不可由模型扩权。
- IMAGE 输入是 B26 原件区域定位的真实 OCR 文本，明确 `IMAGE_OCR_TEXT`；HTML/文档为 `PARSED_TEXT`。IMAGE/HTML 使用显式 EvidenceRef 2.0.0；发布角色及读写者仍必须显式选择既有 v2 evidence contract，并验证其真实 schema_ref/digest。保存旧 v1 不改写。
- 正常任务创建及 N04 preparation 固定场景并校验快照来源。物理原件/解析对象、原文段真实读取，模型发送经过原有当前许可、提供方、区域、预算、实际调用及结算回执链，不新增虚构回执或旁路。
- N04 为每个问题生成独立 TEXT 块：`key|ANSWER|解释` / `key|NO_ANSWER|原因` / `key|CONFLICT|冲突说明`。关联全部证据，不能选取有利来源掩盖矛盾。
- N05 独立读取所有原始定位证据、核对真实 literal hash/read receipt、逐题引用、全答案 claim 支持和固定独立审核规则；拒绝遗漏/重复答案、未映射正文、缺引文、未读证据、只靠模型自报正确、没有来源的冲突。
- 无答案、冲突、模型/读取异常不包装为可采纳答案；问题结果和阻断原因可见，人工责任不被替代。正常候选采纳仍走 B12/DC03 独立确认，不自动写项目事实。
- 双份 REVIEW_FINDINGS / COVERAGE 保存回执在提交和恢复消费时重算专属结果，不能替换场景、答案或原始证据。GET `/api/multimodal-qa/tasks/{taskId}/runs/{runId}/results/{actionId}` 从真实 REGISTERED findings 和 submission 读取，核对 payload/receipt/audit/outbox 并重新授权场景与成果依赖。

## 未完成与禁止夸大

现有 `TaskSectionWriterInvocationSchema` 是文本适配器，`FormalInvocationSendPreparation.providerBody` 仅接受现有 DeepSeek profile。没有实际获批准的原生图像输入、图像计费、图像上下文或视觉提供方回执，因此不把 OCR 问答称为图像理解，拒绝 VISUAL_INTERPRETATION。B19 整体状态由主账本保持开放，待原生视觉要求及真实提供方契约闭合。

原生视觉追加需要版本化 exact-input/context schema、限定真实原始图像字节/区域与 MIME、能力和 model.send 授权、provider profile/请求、预算和实际发送结算、N05 独立图像审查及完整恢复重验证。不能仅放开字符串或为现有文本模型贴视觉标签。

## 待执行验收

MultimodalQaSchemaTest：文本和 IMAGE OCR 往返、未知字段、literal hash 篡改、未知题目来源、伪视觉能力、OCR 伪装拒绝。进一步集成：跨租户/项目、许可撤回、v1角色拒绝v2、发送前撤权、无答案/冲突、不完整实际读取、未结算模型、两个独立审查会话、恢复重复提交、证据与回执篡改、成果当前权限撤回。全部 NOT_RUN。

## 原生图像 API 模拟实现（2026-10-21）

新增 NativeImageQaProtocol 1.0.0：Context 持有实际 PNG/JPEG 原始字节（base64）、内容哈希、MIME、尺寸、裁剪区域、来源与许可修订、租户/项目、独立模型会话和幂等请求标识。严格拒绝未知/重复字段、FORMAL、其他提供方、OCR 冒充图片、哈希/MIME/尺寸/区域错误及超限。提供方协议明确命名 local-synthetic-image-v1，不声称兼容任何未知真实厂商。

应用入口（需显式启用 medical.multimodal-qa-simulation.enabled=true，默认关闭）：
- GET /api/multimodal-qa/simulations/projects/{projectId}/profile 返回唯一服务器生成的 2×2 PNG fixture、显式 SIM 配置及边界。
- POST /api/multimodal-qa/simulations/prepare、dispatch、recover 接受上述 Context 的 snake_case JSON。请求需现有应用会话、CSRF 及 X-Schema-Version:1.0.0 / X-Context-Revision；request_id 是此隔离 API 的幂等键。
- POST /api/multimodal-qa/simulations/review 接受 {answer:Context,reviewer:Context}，两者须已结算、不同请求/模型会话、同一题目与全部原始图像。

模拟器每次准备、发送、恢复、审核均重新执行授权回调；HTTP 适配器重新核对当前企业/项目访问权限，仅允许完整匹配服务器 fixture。此处是 fixture 模拟许可，不是生产 model.send、原件来源许可或预算回执。真实来源授权/原件绑定应由未来生产 Authority/Provider 适配器接入，不可直接启用为正式提供方。当前 DeepSeek 文本 profile、B19 OCR 正式链及采纳规则完全不变。

模拟预算以 1 + ceil(原始图片字节/1024) 个模拟单位计费，不是厂商价格。请求预算与租户/项目/用户隔离预算同时约束。发送前保留预算；发送不确定则保留预算并禁止重发；recover 只查询原请求结果，验证完整上下文哈希及回执后一次性结算。上下文修改与幂等重放冲突被拒绝。独立审核重新查询两份提供方回执，只验证传输和隔离，不判断图像语义/答案真实性。

所有输出固定 SIM、synthetic:true、formal_evidence_eligible:false，结果文本以 SYNTHETIC 开头；不产生 FORMAL artifact/审批/采纳。本模拟 journal 为进程内状态，可覆盖同 journal 的 harness 重建和不确定结果恢复；不声称数据库持久恢复，服务重启清空。10000 模拟单位/作用域、最多 128 作用域且每作用域最多 128 个请求，测试环境容量耗尽应重启，不应拿来承载正式业务。

NativeImageQaSimulationTest 覆盖真实图片序列化、拒绝 FORMAL/OCR/篡改、授权撤回、预算、幂等、发送不确定、恢复不重复计费、独立会话审核及伪造回执拒绝。执行状态以本轮最终测试报告为准。原生视觉真实提供方接入和语义验收仍未完成，不能用这些模拟结果关闭生产视觉验收。

模拟 UI：设置 VITE_MEDICAL_IMAGE_SIMULATION=true 后，场景目录显示 NativeImageSimulationWorkspace。选择当前获授权项目，读取服务端固定图像与字节摘要，固定两个独立调用标识，分别准备/发送或只读恢复，最后核对回执。界面显著标记 SIM，未知结果不能换键重发；不会把合成回执作为生产语义支持。服务端 profile 明确返回 X-Schema-Version。
