# B20 产品技术要求与指标方法

代码实现，测试/编译/集成验收未运行。未启动服务、调用提供方、执行迁移或推送。

## 正常路径

POST `/api/product-technical-requirements/specifications` 接受版本 1.0.0 的 `{schema_version, scenario, selection}`。scenario 为原 B14 Author，全部八项人工规范和证据保留；selection 严格解析为专属产品技术要求模型。API 不授予批准，仍通过 B14 独立 Review、Publish 和真实任务固定版本入口。所有来源、公式、方法及标准依据必须列在原 APPLICABLE_KNOWLEDGE，任务实际选择必须包含每个来源版本。原有模板、规则和 A5 严格字段核验照常执行，无旁路。

selection 包括全部明确列出的 scopes（型号、配置、条件、指标），sources（固定版本、权利修订、精确原文位置与 literal），standards（编号、年代号与来源），methods（真实适用组合、标准、方法学验证适用性及证据），indicators（原要求、上下限、比较符、单位和精度、每个数值的精确来源），conversions（明确批准的仿射公式、单位、精度/舍入及公式依据），body（按真实章节列出的专属正文 content 对象，支持精确文本及结构化表格/图形内容，实际节点仍须通过正常制品 schema），applicability（软件、IVD 方法、术语、附录的 REQUIRED / NOT_APPLICABLE / UNKNOWN 及依据）。不推断笛卡尔积，亦不将不同型号合并。

N04 在真实物理上下文读取路径交付 product_technical_requirement_plan，包括原指标、原值、适用组合、方法、标准、精确正文及转换输出。所有数值使用 BigDecimal；原值不可覆盖，仅输出额外转换字段，非正乘数或单位不符阻断。

N05 专属 producer 从实际保存并物理读取的正文、当前候选章节及独立来源回执检查每个组合。缺指标/方法/标准年代号/验证依据、未知条件、原文冲突、精度或单位冲突均产生定位原因。正文必须精确匹配批准 body、位于规定章节、含对应型号配置条件、指标原值、方法和标准年代号、转换值，并逐一具有原来源链接。每个原文 literal 的摘要必须与实际独立物理读取相等，不能由模型自报匹配。

结果作为 `normalization.product_technical_requirement` 存入真实 REVIEW_FINDINGS / REVIEW_COVERAGE 注册回执。正常 submit consumer 重新计算并比对两份回执中的专属结果；blocked 会使 coverage_complete=false，正常 check_run 为 WAITING_INPUT，不能确认。GET `/api/product-technical-requirements/tasks/{taskId}/runs/{runId}/results/{actionId}` 验证注册制品/回执哈希、真实审计和 outbox，重新授权制品及规范完整依赖闭包后返回历史机器结果。

未增加新表或迁移；沿用 B14 和 N04/N05 的 tenant/project owner、元数据、不可变内容摘要、原审批/出版、注册制品及审计链。保留 B15–B18 路由。

## 延后验证

新增 ProductTechnicalRequirementTest（仅合成算术，不是产品参数或实测证据）。集中验证需覆盖真实作者/独立审核/发布/固定任务链；全部型号组合及多方法；单个数值/单位/精度/标准年代号冲突；条件 UNKNOWN；必需方法验证缺失；源权利撤销、跨租户/项目与重放；实际正文删项、错章节、错转换、缺引文；两制品回执篡改；阻断确认；E07 模板导出及现有 N03/A5 联合验收。这里不宣称标准全文授权、产品试验、方法验证、签发批准或真实运行验收已经完成。

专属正文按完整 content JSON 等值验证，不将表格/图形扁平化为普通文本；用于字段一致性的文本投影只辅助核对，不替代结构、单元格内容及正式制品 schema 检查。未声明的正文节点阻断。
