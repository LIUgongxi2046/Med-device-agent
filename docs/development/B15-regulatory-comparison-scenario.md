# B15 固定法规比较场景

## 正常入口与真实发布

POST `/api/regulatory-comparisons/specifications` 接受独立 `selection` 和完整 B14 `scenario`。场景固定 baseline/target 两个不同的真实 source version/hash、parse、approval、rights revision，法律标识、版本、辖区、实施日期、比较日期，以及逐条精确 EvidenceRef 和可空的 applicability rule version/hash。`scenario_kind=REGULATION_COMPARISON` 与目录一致；它不是新模板文稿类型。使用实际已获准的模板及其原有 document type，不修改基础模板/task/release Schema digest。

typed selection 以版本化前缀和规范 JSON 编码进 B14 不可变 INPUTS.definition，因此选择本身也经过独立专业规格审核、真实 release dependency closure、CAS 发布与当前任务准入。独立 controller 和普通 B14 author 路径均校验该类型；没有手工 seed、默认权限或自动批准。输入大小沿用 B14 16,000 字符 definition 上限，最多 64 条。

八字段全部由正常作者输入并绑定实际证据：ROLES 真实 ROLE；INPUTS 独立固定选择；APPLICABLE_KNOWLEDGE 两侧原文及许可；TASK_RULES 固定核准规则；OUTPUTS 变化、影响与证据格式；HUMAN_REVIEW 注册专业人员对适用性及结果的人工复核；FAILURE_STATES 撤权、缺证据及未知；ACCEPTANCE_SAMPLES 真正获准的版本化样本。接口不会把合成测试样本登记为客户验收，也不会凭说明文字认定这些业务事实。

## 实际 N05 producer/consumer

正式 N05 input authority 调用原 B14 TaskCreate gate 重新验证当前规格、独立 reviewer/publisher、发布、模板、完整证据与真实评测，并要求相同 specification/publication/version pins 已存在于本 run replay。替换成另一个场景不能使原法规比较绕过旧约束。两侧来源须精确匹配本 run 的 source/parse/approval/rights 选择。缺失服务依赖拒绝运行。

法规条款引用独立加入 N05 evidenceRefs，不依赖 N04 模型是否主动引用这些条款。既有 ContextReads 对全部引用执行真实 raw object GET/hash、parsed object GET/hash、locator literal 校验、独立 session receipt 与当前正式使用/模型发送许可。比较生产器只消费该真实 terminal 的 read pins，完全不使用模型自报的读取状态或适用结论。

RegulatoryComparisonPipeline.Result 是实际生成及消费的 typed 输出，保存到真实 REVIEW_FINDINGS/REVIEW_COVERAGE 不可变注册 receipt 的 normalization provenance；保留原有 schema digest。submitSaved 在写入 check/submission 前重新运行该纯确定性计算并要求两份 provenance 精确相等。任一条款 UNKNOWN 将真实 findings.coverage_complete 置 false，既有 check、stage commit 与 N06 consumer 进入 WAITING_INPUT，不产生通过或签发资格。模型 PASS 不能覆盖它。

GET `/api/regulatory-comparisons/tasks/{taskId}/runs/{runId}/results/{actionId}` 返回真实已提交结果。校验固定 task 权限、注册 payload/receipt hash、对应审计/outbox，再重新核验实际 artifact 与 specification 的完整 source/dependency closure。返回始终明确 historical_machine_result、legal_approval=false、human_review_required=true；不是额外的批准入口。所需 SOURCE_VERIFICATION artifact profile 与权限必须正常配置，不自动授予。

## 语义边界与人工闭环

- 范围只覆盖显式 SELECTED_CLAUSES，不声称整部法规比较完整。两侧可定位 literal hash 相同/不同产生 EXACT_LITERAL_UNCHANGED/CHANGED，可沿 EvidenceRef 复核原文。不存在文稿结构 diff→法规结论的转换。
- 单侧空缺仅产生 UNKNOWN：选中清单里缺一侧不证明法规新增或删除。条款重编号/迁移由独立审核过的固定选择表达，不做模型猜测映射。
- 适用性必须由 N05 原有真实 APPROVED RuleVersion 与 exact hash 提供，且该规则 basis_manifest 包含两侧法规 source/version/hash；项目事实必须来自 replay 的获准 profile，不能来自请求或模型。
- 比较日期和辖区还必须与项目中 KNOWN regulatory_as_of_date / regulatory_jurisdiction 完全一致。缺映射、缺核准规则、缺业务事实、locator 不完整均给出逐条 UNKNOWN 原因，不解释成不适用。
- MACHINE_APPLICABLE / MACHINE_NOT_APPLICABLE 仅是固定规则的机器命题，不能批准项目法律适用性。真实法规状态/实施日期元数据也由 B14 规格审核和源治理核准，不由代码声称合法有效。
- 完整机器结果仍须原 B08 来源/语义人工复核与 B09 文稿 review confirmation，B10 签发权限保持独立。UNKNOWN 应补齐获准映射、事实或来源后发布新规格/规则及新任务版本重跑，不允许前端或本接口把 UNKNOWN 改成批准。

## 持久化与验证状态

复用 148 scenario_record 的 immutable/version owner/audit/outbox/FK 与 scenario_task_entry CAS，复用 114 N05 immutable result registration/submission；不新增表，因此无需 151 migration 或改 master/coverage。所有新 SQL 显式限定 tenant/project/task/run/action。

完成 8 个变更生产类的 Java 17 窄 diagnostic compile。未运行测试、测试编译、完整 build、DB migration、provider/runtime、网络或 push。RegulatoryComparisonTest 仅为延期测试源码，合成 fixture 不是实际法规或获准验收样本。

统一测试待覆盖：通用 author 绕过、字段/版本/大小限制、错 tenant/project、同法规异版本和日期、B14 替换/禁用及 reviewer grant 撤销、真实 release pin 漂移、选定源/parse/approval/rights 不匹配、原始及解析对象篡改、locator 缺失、额外独立条款实读、模型 PASS 与 UNKNOWN 冲突、规则未批准/撤销/无双侧依据、事实未知、单侧空缺、精确 literal 对比、重复提交、两份 provenance 不同、结果跨项目读取、许可撤销、结果 read hash/audit/outbox 篡改、N06 不可消费 UNKNOWN，以及 B08/B09/B10 人工链路。真实客户样本与专业审批未提供，实际业务验收保持 NOT_RUN。
