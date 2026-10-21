# B16 报告场景

正常 POST /api/reports/specifications 接受 schema_version、完整 B14 scenario 和独立 selection；GET /api/reports/tasks/{taskId}/runs/{runId}/results/{actionId} 读取真实 N05 已提交 receipt 中的报告结果。不是导出接口，也不替代 B29 导出。所有八字段、真实样本证据、角色、规格独立审核、发布、模板和任务准入复用 B14；没有默认批准、合成客户资料或自动授权。

selection 固定 section_ids、inputs、calculations。每个 input 固定 key、source(version_id/content_sha256)、rights_revision、完整 evidence、decimal_literal、unit。decimal_literal 是选定 locator 的完整精确十进制原文，不是模型抽取或浮点数。计算固定 key/operation/inputs/scale/rounding/unit/section_id/prefix/suffix。支持 SUM、DIFFERENCE、PRODUCT、RATIO；同维加减校验单位，乘除输出单位由规格专业审核明确，不擅自做单位换算。除零/不允许舍入得到 UNKNOWN。范围明确为固定选定输入和章节，不声称覆盖全部原始资料。

N04 InputAuthority 当前重验 B14 frozen replay proof 和实际来源版本/parse/approval/rights，额外证据走真实 raw/parsed GET、hash 与 locator，typed specification 和确定性 BigDecimal 结果实际进入上下文及其 content hash。N04 正常产生并持久化候选正文。N05 独立 session 再读全部选定证据，完整原文 hash 必须等于批准 decimal literal hash，重新计算而不信任模型数值。检查实际保存的候选报告章节顺序、每个结果的 exact prefix/value/suffix、所属章节、逐输入正文 EvidenceLink。没有匹配值、遗漏引用、未完成原文读取或计算不成立均令真实 coverage_complete=false，不能被模型 PASS 覆盖。

结果存入真实 REVIEW_FINDINGS/REVIEW_COVERAGE immutable registration provenance，submitSaved 两份重新计算核对；原 N06、B08/B09/B10 人工审核和签发保持独立。正常结果读检查 tenant/project/task/run/action、payload/receipt hash、audit/outbox 和当前 artifact/spec dependency closure。结果始终 historical_machine_result=true、human_review_required=true、legal_approval=false。

复用 148/114 表结构，不需要 154 migration。未改基础 schema digest；无 master/coverage/ledger 编辑。真实报告定义、专业审批和客户验收样本必须正常提供，缺失不伪造。延期测试源码 ReportScenarioTest 已写，未执行任何测试、构建、provider、服务、网络、迁移或 push；当前环境无 javac，窄编译未运行。后续需集成验证精确 locator 差异、源撤权、规格更换、重复提交、跨项目、章节/引用漂移、原件篡改、舍入/除零、模型 PASS 冲突以及完整人工审核链。

N04 本身按章节执行：N05 每次核对当前 targetSections 对应的已保存候选，结果明确 coverage_mode=CURRENT_SECTION_CANDIDATE / verified_section_ids；多章节报告由原有 N06 正常汇编及覆盖门禁完成，单章结果不会宣称整份报告完成。
