# B23 临床评价与免评价后端

## 正常生产链

POST `/api/clinical-evaluation/specifications` 接受 `schema_version=1.0.0`、B14 `scenario` 及 `selection`。selection 固定 `scenario_kind=CLINICAL_EVALUATION`，使用 `clinical-evaluation-v1` INPUTS 前缀；大 INPUTS 使用已有 Author 1.1.0，原 1.0.0 上限不变。八字段、来源授权、独立审查、真实发布、任务 admission 继续经过 B14，不新增旁路批准。所有源 version/hash 必须存在于选定来源；N04/N05 再核 tenant/project、parse、approval、rights revision。

N04 `clinical_evaluation_plan` 提供固定正文与缺项；N05 在真实保存的候选正文上逐字匹配批准节点，独立物理读源并核 literal hash，核 SUPPORT 引用及每个实体的所有依赖。`clinical_evaluation` 结果写入原 normalization receipt；消费时重算 saved 和 coverage 两份 receipt。任何 blocked 均强制 coverage_complete=false。GET `/api/clinical-evaluation/tasks/{taskId}/runs/{runId}/results/{actionId}` 重查 payload/receipt hash、audit/outbox、当前源/规格依赖权限。它返回历史机器结果，不是临床或法规批准。

复用既有不可变 B14 与检查工件表，无新增业务表，不使用预留 migration169。路径、目录、证据、人员、正文改变均改变 selection/规格 hash，必须新版本、独立重审及新检查，旧结果不能证明新版本。已有任务固定依赖/变化影响与人工审查签发链继续有效。

## 类型及真实来源

路径支持 EQUIVALENT_DEVICE、COMPARABLE_DEVICE、CLINICAL_TRIAL、COMBINATION、EXEMPTION、UNKNOWN。组合至少两个不同分支；比较逐项绑定 evaluation_path，不能用一份等同器械对比覆盖另一可比器械分支。机器不代替注册人员选路径。

path_decision 是精确来源 Fact，其真实 APPROVED_DECISION 来源 literal 必须为严格 JSON，包含 path、combination_paths、outcome=APPROVED，内容须与选择完全一致。这个输入是专业人员已有决定的来源，不是本服务生成的批准。缺失或拒绝/不匹配仍允许保存待补证作者版本，但计划和验证结果为 UNKNOWN/BLOCKED。

Record 包含 key、group、applicability、applicability_basis、fields、attachment_source_keys。适用性 REQUIRED/NOT_APPLICABLE/UNKNOWN；判定必须精确匹配真实 APPROVED_DECISION 来源。每个 Field 为 name 与 Fact；Fact 为 text、source_keys，必需字段必须逐字来自对应源。基本分组 COVER、PRODUCT、SCOPE、PATH、CONCLUSION、EVALUATOR、OTHER，分别覆盖 PRD 8.5.6 封面、产品及研发、评价范围、路径、结论、人员与附件。完整必需字段名在 ClinicalEvaluationPipeline.FIELDS。

- 签名仅来自 SIGNATURE；人员资质仅来自 QUALIFICATION；评价范围排除须已有专业决定。
- 等同/可比器械路径须 LITERATURE：实际检索、纳入排除、去重、全文、合法获取依据、相关性、贡献、方法及结果局限。无检索报告不生成无结果声明。
- 临床试验须 TRIAL：设计、机构时间、指标、入排标准、样本量、随访、结果及分析。真实附件必须有伦理批准、方案、知情同意样本、报告、原始数据库、分析代码。结果必须逐字来自 TRIAL_REPORT，方案不代替结果。
- 结论来源限定已有专业决定、试验报告、文献全文、比较分析证据；不允许方案或普通材料充作完成结论。机器结果始终 clinical_approval=false、human_review_required=true。
- 免评价须 EXEMPTION：目录版本/条目、当前目录核验、境内注册、等同性决定、等同性结果、差异影响。目录来源 CURRENT_CATALOGUE 仍须真实核准决定和当前授权，名称本身不证明现行有效。EQUIVALENCE_OUTCOME 必须是 APPROVED_DECISION 中的精确 `BASICALLY_EQUIVALENT` 值；缺项或其他结果阻断免评价，并提示需要临床评价，机器不自动切换路径。

## 比较与实际正文

Comparison 为 key、group、evaluation_path、item、applicability、applicability_basis、applicant、comparator、difference、supporting_materials、analysis、table_body_key、row_id、column_node_ids。免评价同时要求 CATALOGUE_ENTRY 与 DOMESTIC_DEVICE 两组，逐项覆盖 PRINCIPLE、STRUCTURE、MATERIAL、PERFORMANCE、STERILIZATION_DISINFECTION、INTENDED_USE、METHOD_OF_USE。非免评价 DEVICE 比较每个相应分支覆盖 TECHNICAL_CHARACTERISTICS、BIOLOGICAL_CHARACTERISTICS、INTENDED_USE。真实不适用决定可保留，不可静默省略。

每行绑定实际 TABLE 六列及真实 TEXT 单元格，列标题依次：对比项目、目录中医疗器械（DEVICE 组为“对比产品”）、申报产品、差异性、支持性资料概述、分析研究资料概述。逐列验证 item、comparator、applicant、difference、supporting_materials、analysis；拒绝多余行、错行错列、重复 cell 及缺节点。正文所有 TEXT/TABLE/IMAGE/FORMULA 都必须是固定批准节点。每个事实都要出现在对应实体的实际可见正文中，不能只存在于 provenance；表格单元格和判定说明应分别声明 Body 并绑定相应实体与完整来源。

## 未执行验证与边界

已写 ClinicalEvaluationTest 合成负例，按用户要求没有运行测试、编译、构建、服务、迁移、provider 或真实临床流程。真实专业批准、目录适用性、临床证据、签名与客户项目验收均未宣称已取得。

后续统一验收仍需：合法完整路径实际作者→独立审查→发布→N04→N05→人工审查→重读；缺源/撤权/跨租户；伪造专业决定；目录过期与未核验；不等同免评价；组合分支漏项；报告/原始数据库/伦理缺失；检索方案冒充结果；六列错位/重用/跨 section；节点来源不匹配；路径或目录改变后旧 receipt 重放；人工审查与正式签发门禁。
