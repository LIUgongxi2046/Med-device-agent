# B24 说明书与独立标签

代码已接入，测试、编译、集成验收均 NOT_RUN。未启动服务、迁移、网络、provider 或推送。不新增表，不占用 migration173。

## 正常受控链路

POST `/api/instructions-label/specifications` 接收 `{schema_version: "1.0.0", scenario, selection}`。沿用 B14 八项规范的真实作者、独立审核、发布、任务固定版本；较大 INPUTS 采用原 B14 Author 1.1.0。API 不产生批准。固定选择类型 INSTRUCTIONS_LABEL，定义前缀 instructions-label-v1。

ScenarioSpecificationService 同时验证作者和历史固定版本，全部源 version/hash 必须被任务选择。DocumentScenarioInputs 重查实际 tenant/project/source/parse/approval/rights。N04 instructions_label_plan 传递受控正文与缺项。N05 从真实保存正文和独立物理读取来源重新检查，结果写入 normalization.instructions_label。生产者附加结果，正常提交双消费者分别重算 saved 与 coverage 回执，不一致拒绝；缺项保持 UNKNOWN/blocked，不得 coverage_complete。

GET `/api/instructions-label/tasks/{taskId}/runs/{runId}/results/{actionId}` 验证注册 payload、receipt 摘要，实际 audit/outbox 一致性，并重新授权实际工件及规格全依赖闭包。只返回历史机器检查，不代表专业签发或当前法规批准。

## 独立文档与真实依据

selection.documents 必须恰有 IFU 与 LABEL；每个独立记录 version、stage、product_version、layout、rule_source_key、version_record、fields。两者实际 body 节点及章节分离，保留独立版本记录。独立版本记录来源 nature=VERSION_RECORD，literal 为真实 JSON `{document_kind, version, product_version, stage, packaging_version}`，逐项比较，不靠文件名或时间推断。selection.packaging_version 为实际 PACKAGING 来源 JSON `{version, product_version, minimum_sales_unit}` 的精确原文；文档版本记录中的 packaging_version 对应其 version，产品版本一致且必须为最小销售单元。两份文档产品版本和阶段必须一致。修订修改固定 selection/hash，不能复用旧审核回执。

每种文档的实际 APPROVED_RULES 来源 literal 必须具有 `{document_kind, stage, product_version, layout, rules, see_instructions, layout_basis}`。rules 为 `{group, field_keys, mandatory, minimum_content, deferrable_in_application}` 列表；本实现只要求检查主题完整，不编写法规事实、字段文字或最低内容。核准来源缺失、不匹配或无法解析时 UNKNOWN。规则来源及源权限仍受 B14 审核发布和独立物理读源约束，nature 标签本身不授予批准。

IFU 检查目录覆盖 IFU-01 至 IFU-14；另逐项覆盖十组警示及清洗、消毒、包装、灭菌、重复使用限制。LABEL 独立检查十一组：产品识别、注册主体与进口代理、注册编号、制造主体许可、日期期限、电源、符号、警示、储存操作、环境、辐射。字段具体要求、适用条件和最小内容必须来自已核准来源，不能以通用清单替代真实适用规则。

每字段保存 key、group、applicability、decision、value。decision 必须是来自 APPROVED_DECISION 的原文 JSON `{subject, status, product_version, outcome: "APPROVED"}`；subject 为 `IFU:字段key` 或 `LABEL:字段key`，逐项核对状态与产品版本。NOT_APPLICABLE 不能替代 mandatory 字段。UNKNOWN 保留待确认。首次申请的注册编号/技术要求编号只在规则明确 deferrable_in_application 时允许 PENDING_REGISTRATION，value 必须为空；上市或修订版本不能借此留空。真正编号仅接受 REGISTRATION_PROOF / APPROVED_TECHNICAL_REQUIREMENTS。实际批次日期来自 BATCH_RECORD；样稿格式和期限依据来自实际批准规则或验证报告；编修日期来自 VERSION_RECORD。警示不默认“无”，要求精确真实风险/核准事实来源。

SPACE_LIMITED 只适用于 LABEL。最低字段必须在真实规则中明确标记，正文必须包含该规则的 see_instructions 原文；不存在最低内容规则时阻断，不能任意删减。非缩减文档所有适用字段都必须进入实际正文。

product_facts 逐项固定真实产品事实及 product_version；comparisons 保存每侧原值来源、单位、上下文、明确判定依据。每个 REQUIRED 字段必须有实际产品事实比较，只有 EQUAL、原值/单位/上下文相等且版本一致才通过。条件不同不误判为普通数值冲突，而保留未解决状态并阻止签发。进口状态须有批准决定；进口还要求原文说明书与真实译文核验来源；译文核验 JSON `{original_source_version, original_sha256, chinese_version, product_version, outcome: "VERIFIED"}` 必须绑定实际原文来源 version/hash、中文说明书版本和产品版本。不得生成证明文件、批准、日期、编号或保证性宣称。

## 正文和证据

body 固定 node_id、section_id、document_kind、entity_keys、完整 content JSON 和来源。真实正文节点须精确匹配已核准内容，不能只检查一段平行文本；未声明节点阻断。字段值须在该实体的实际已匹配正文中出现。每个节点必须具有真实来源、规则、版本和适用决定 SUPPORT 引用，所有源 literal_sha256 必须与独立读取完全一致。模板及图示格式继续受原 N03/A5/导出规则约束，本实现不创造格式豁免。

## 延后验证

InstructionsLabelTest 为合成契约测试源码，未执行。覆盖规则缺失、缩减标签无最低内容、未知适用性、正式版留空编号、样稿伪造编号、普通来源假充警示/产品事实、独立版本往返、注入批准及版本变更。

集中验证还需完整真实 B14 作者/独立审核/发布链、两类独立章节与正式导出、完整 14 组说明书/10 组警示/5 组复用/11 组标签规则、合法全部字段路径、进口原文与译文、最小销售单元标签、空间缩减规则与最低文字、风险警示与符号对应、实际批次日期、申请到上市阶段转换、型号/版本/条件/单位冲突、源权利撤销、跨 tenant/project、独立读源失败、正文删项或换位、双回执篡改与历史重放、专业人工审查。实际标准授权、产品事实、法规批准和运行验收均不得由源码宣称已完成。
