# B18 固定市场分析场景

## 正常作者、发布和读取

POST `/api/market-analyses/specifications` 接受 `schema_version`、完整 B14 `scenario`、独立 `selection`。GET `/api/market-analyses/tasks/{taskId}/runs/{runId}/results/{actionId}` 返回真实已提交 N05 的 `market_analysis`，不是预览或另一个批准接口。完整八字段、真实 ROLE、获准样本、独立规格审查、真实发布/评测、模板、CAS、当前来源许可继续由 B14 处理。普通 B14 作者入口及历史规格读取也执行该 typed definition 的校验，不能通过通用接口绕过。

`market-analysis-scenario-v1` 固定 geography、population、product_scope、statistical_definition、counting_method、currency、price_basis、period_start/end、exclusions、limitations。非货币统计须明确填写不适用，不默认猜测币种。每个 input 有且只有一个显式观察起止日期，必须在总时间窗口内；所有输入继承这个固定统计口径和地域/总体。独立核准规则必须检查实际来源是否符合该口径，以及跨期运算、去重、范围、币值基础和结论限制。字符串声明不是代码替业务批准统计事实。

inputs 固定真实 source version/hash、rights revision、parse/approval/locator EvidenceRef、精确 decimal_literal、unit。context 另外固定真实方法/范围来源及完整 locator literal，不能仅凭数字推断来源的地域或总体。conclusions 固定 key、section_id、唯一且互不重叠的 prefix、instruction、input_keys、calculation_keys。每个目标章节都必须有固定结论；结论必须包含其所选计算的全部输入依赖。独立 conclusion_review_rule 固定 version/hash，必须是 TASK_RULES 的真实证据；审查要求随 typed selection 进入实际 N05 上下文。

`evidence_policy=APPROVED_PINNED_SOURCES_ONLY` 是唯一支持策略。新采集网络候选、搜索摘要或模型自报数字不会进入正式结论。已正常批准且具有当前来源/模型发送许可的资料可以使用，不要求新联网任务，也不声称 A11 已完成。

## N04 生产及 N05 独立复核

ScenarioSpecificationService 重新验证真实当前 B14 publication/specification、reviewer/publisher、release、模板、知识闭包及 run replay 中的相同版本/hash。额外 context 与数值证据都必须精确匹配 run 的 source/parse/approval/rights；N04、N05 都走原有 raw/parsed 实际 GET、对象 hash、locator 与权限检查。N05 仍使用独立 session，不接受 N04 自报读取状态。

N04 真实 typed context 获得固定规格与 `market_calculations`；正常生成并保存候选正文。确定性计算复用 B16 BigDecimal 的 SUM、DIFFERENCE、PRODUCT、RATIO、固定 scale/rounding/unit/prefix/suffix。复用的是纯计算和保存候选核对函数，不修改实际市场规格或 Verified input，不让市场任务伪装成报告任务。乘除结果单位由专业规格明确，不做隐式换算；不支持任意公式、回归、预测或自动推断市场规模。除零/不可舍入结果为 UNKNOWN。

N05 对每个数值原文和方法/范围原文再做完整 literal hash 核对，重新计算，核对实际保存的当前目标章节、精确数值文本及逐输入 EvidenceLink。对每个固定结论，要求唯一实际文本节点、完整结论 ClaimRecord、包含所选全部数值和方法来源的正文引用和 claim 证据、独立 SUPPORTED，以及指定核准规则对该节点的 PASS。该规则须包含所有实际来源 basis，独立审查结果须明确引用全部数值与方法证据；它也必须覆盖每个计算文本节点，以复核范围/口径而非仅数值相等。

所有实际 TEXT 节点必须映射至固定计算或结论，避免只审查模型挑出的部分结论。非文本图表/复杂布局明确 UNKNOWN，不把未经核对的图形或额外文本当作已完成分析。每次结果明确 `coverage_mode=CURRENT_SECTION_CANDIDATE` 与 verified_section_ids；完整多章节汇编与覆盖继续交给 N06。

## 真实登记、消费、失败和人工链

MarketAnalysisScenarioPipeline 的真实结果写入 REVIEW_FINDINGS 和 REVIEW_COVERAGE 不可变 registration receipt 的 normalization provenance。submitSaved 使用保存的真实 findings 重新计算读取/数值/结论结果，并分别对两份 provenance 做精确一致性检查。任一 UNKNOWN/缺引用/独立结论未通过会使真实 coverage_complete=false，经原 check/submission/N06 路径进入 WAITING_INPUT；模型 PASS 无法覆盖这些失败。

结果读取重验 tenant/project/task/run/action、payload/receipt hash、audit/outbox，以及当前 artifact 和 specification 的完整 dependency closure。返回 historical_machine_result=true、human_review_required=true、legal_approval=false。机器结论不是业务签发或投资建议批准。B08 来源/语义人工复核、B09 文稿确认、B10 签发权限保持独立。缺失事实/资料许可/核准规则/客户样本要正常补齐并发布新版本重跑，不允许人工直接把 UNKNOWN 改成通过。

复用已有 148 场景治理和 114 N05 注册表，不需要 161 migration，不改基础 schema digest、master、coverage 或固定任务台账。真实客户市场定义、专业批准和验收样本未提供，未生成任何市场事实、批准或 provider receipt。

## 延期验证和验收协议

MarketAnalysisScenarioTest 提供延期合成测试源码：严格 roundtrip/未知字段、网络候选策略拒绝、空口径、非法日期、时间倒置/越界、观察一对一、缺方法来源、结论依赖缺失、缺核准规则、精确十进制、除零 UNKNOWN 和原始 market selection 不被计算适配器篡改。测试 fixture 不是客户验收证据。

统一集成验证还应覆盖：

1. 真实正常作者→独立 review→publish→TaskCreate→N04→独立 N05→双工件登记→submitSaved→N06→B08/B09/B10 的成功链。需要业务方真实获准样本及可用 provider，不以 mock 代替业务验收。
2. source/parse/approval/rights 漂移或撤销、原始/解析对象篡改、数字/method literal 不符、未批准网络候选、跨项目/跨租户/跨任务结果读取。
3. source 统计口径/时期不一致、范围或币值混用、重复汇总、遗漏限制、未经支持外推。相同数学值不能证明市场结论成立，应由固定核准独立规则拒绝。
4. N04/N05 同 session、方法来源未实读、缺 source basis、未核准/撤销规则、仅 SECTION PASS 未覆盖 TEXT、结论无完整 claim 或缺任一依据、正文多余结论/图片、重复 prefix/匹配歧义、章节重排。
5. 模型 PASS 与确定性 UNKNOWN 冲突、provenance 任一副本篡改、重复提交、规格替换/禁用、reviewer/publisher grant 撤销、模板/release pin 漂移、审计/outbox/receipt hash 篡改。

按照用户要求未执行测试、测试编译、完整构建、服务、provider、网络、迁移或 push。环境 javac 不可用，未进行编译；仅源码人工检查。运行验证和真实业务验收保持 NOT_RUN。

源码复核同时修复 B16/B17 attach 的既有 defensive-copy 问题：Normalized.pins() 每次返回副本，原实现对多个临时副本写入会丢失场景 provenance。现在一次获取本地 pins，写入结果和 coverage 后构造新的 Normalized；B15 本来已使用正确模式，未改。新增对应延期回归断言，未执行。
