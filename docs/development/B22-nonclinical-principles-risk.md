# B22 非临床、基本原则与风险

## 实际链路与作者入口

POST `/api/nonclinical-principles-risk/specifications` 接受外层 `schema_version=1.0.0`、`scenario` 和 `selection`。scenario 使用 B14 Author 1.0.0 或新增 1.1.0；较大 INPUTS 使用后者（200000 字符、整个请求仍受 262144 字节限制）。八字段、正式能力、角色、实际证据、独立审查和真实发布均沿用 B14。没有自动补造数据、批准、权限或签名。固定选择类型 `NONCLINICAL_PRINCIPLES_RISK`。

真实任务 admission 校验全部选定 source version/hash；N04/N05 输入重查 tenant/project、source、parse、approval 和 rights revision。N04 `nonclinical_principles_risk_plan` 提供已固定正文，不调用假研究工具；N05 对已保存候选正文及独立物理读源重新计算。`nonclinical_principles_risk` 放入真实 normalization receipt，生产端 attach，消费端对 saved 和 coverage 两份 receipt 均重算比对。blocked 不可变成 coverage_complete。结果 GET `/api/nonclinical-principles-risk/tasks/{taskId}/runs/{runId}/results/{actionId}` 验证原登记 payload、receipt、audit、outbox 以及当前源/规格依赖权限；返回历史机器结果，不是专业批准。

## 专业字段

- Study：研究标识和版本、目的范围、依据/接受准则、样本型号批次代表性、方案、设备条件、执行方法时间、原始数据、结果及统计、差异处理、结论局限、责任签署日期、真实报告位置。
- 固定十组条件：材料；电气机械环境 EMC 辐射；软件网络安全；生物学及生物源；清洁消毒灭菌；动物及其他；文献；稳定性；技术要求/检验报告；其他。每组须明确 REQUIRED/NOT_APPLICABLE/UNKNOWN 和真实判定来源。专用字段分别覆盖软件版本/需求风险追溯/漏洞及 AI 云互操作适用性，生物接触时间/残留/豁免及免疫风险，灭菌参数/验证/耐受/重复次数/包装与说明书，稳定性时间点及储运包装，动物必要性及文献实际检索等。
- PLAN_ONLY、IN_PROGRESS、COMPLETED、UNKNOWN 区分保存。只有实际数据 ORIGINAL_DATA 与实际报告 ACTUAL_REPORT 能支持完成结论；结果和结论文本必须精确来自相应类型来源，不能仅附一个不相关报告。方案不得夹带结果、完成结论或报告引用。待补证仍可保存作者版本，但不能生成已完成检查资格。
- 原则清单：固定核准 catalogue 来源 nature=APPROVED_TEMPLATE；literal 是 `{ "clauses": [{ "clause": "真实条款号", "requirement": "真实要求" }] }` 的实际来源内容，不由本实现生成。完整条款集合与要求逐项匹配，不允许漏项。每项有适用性、方法、客观文件名称/编号/位置；不适用附真实决定及理由。不能只写“符合”。
- 风险：生命周期范围、依据、准则、危害/情形/可预见事件/损害/分析评价、控制 ID、实施和有效性独立来源、精确有效性结论、剩余风险、说明书警示或不适用依据、总体剩余风险与受益评价、责任人员。
- 标准只持有元数据、未授权正文或 UNKNOWN 均产生 STANDARD_TEXT_UNVERIFIED；即使作者标记 TEXT_AUTHORIZED，也必须有对应授权全文来源、当前 rights/approval 和独立读取。机器不宣称已自行核验 GB/T 42062 正文或替代专业审查。

## 正文和五列约束

Body 固定 key、node_id、section_id、group、entity_keys、content、source_keys。支持真实 TEXT/TABLE/IMAGE/FORMULA 内容，最终通用正文验证仍有效；每个可见正文节点必须精确等于批准选择中的对应节点。重复单元格文字通过不同 node_id 区分，不会因多个“适用”单元格误拒绝。

principle_rows 将 principle_key 绑定实际 table_body_key、row_id 与五个依次排列的 column_node_ids。实际 TABLE 必须恰好五列，首行依次为“条款号、要求、适用、证明符合性采用的方法、为符合性提供客观证据的文件”，之后行集合必须等于全部声明绑定。单元格要求 1×1 且唯一文本节点；实际正文逐列核对，不把 provenance 中的平行表当作正文。适用显示“适用”，不适用显示“不适用：理由”，未知显示“待确认”；客观文件逐项显示 `文件名称 [编号] 位置`，多文件用中文分号连接。无适用方法/客观文件的不适用项显示“不适用”；适用但缺资料时显示“待补证”，并阻断完成。

每个实体事实必须在对应正文中出现，实体完整来源依赖必须以 SUPPORT evidence link 关联实际节点，物理读源 literal_sha256 必须吻合。缺源、未授权、缺实际数据、未完成研究、适用性未知、无有效性或正文/位置冲突都保留可见缺项且阻断覆盖完成。IMAGE 仍由既有文稿工件物理读取和正文授权校验约束。

## 版本变化与验证边界

复用现有 B14 不可变规格/独立审查/发布和 N04/N05 工件审计持久化，不新增表，不使用 migration168。任何研究数据、报告、风险控制、适用性、标准或正文变化均改变固定 selection 与规格内容 hash，必须形成新作者版本、重审/发布和新文稿检查；旧 receipt 不可消费为新内容证据。人工签发继续由既有专业审查流程完成。

新增 NonclinicalPrinciplesRiskTest 为未执行的合成契约测试源码。未运行测试、编译、完整构建、服务、迁移、provider、网络或真实业务验收。真实数据、专业判断、标准许可、人工签署、运行时端到端结果：NOT_RUN，不能据源码声明完成。

统一测试还需覆盖：实际五列表格多行重复文字、缺/多/交换行列、重复 cell/node ID、跨 section；PLAN 冒充结果；假标 COMPLETED 缺原始数据；结果文本来自 PLAN 但夹带无关报告；未检索的无文献声明；动物必要性缺判定；每种专用字段缺项；只有实施没有有效性；残余风险及警示断链；标准仅元数据；真实源权限/批准/rights 撤销；来源与图示字节变化；规范版本变化后旧检查重放；作者自审、发布替换和缺真实发布；合法完整路径的实际 N04/N05 正文产出、独立源读、消费与人工审查。
