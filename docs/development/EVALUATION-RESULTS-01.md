# EVALUATION-RESULTS-01：固定评测定义、追加评分与确定性结果

## 实施范围与限制

此切片处理**已产生输出**的评测登记及专家观察，不调用模型，不创建假发布 Agent、任务或运行，不实现 SYSTEM_JOB 候选执行。

新增：

- POST `/api/evaluation-batches`：EV-01，固定定义，201。
- GET `/api/evaluation-batches/{id}`：定义、完整评分历史、已终结结果。
- POST `/api/evaluation-batches/{id}/scores`：EV-02，追加观察或追加修订，201。
- GET `/api/evaluation-batches/{id}/scores`：相同受权评分历史和结果。
- POST `/api/evaluation-batches/{id}/finalize`：EV-03，计算并固定结果，200。

SampleManifest（样本数组）和 ExpectedItems（含输入身份的预期对象）现以 `urn:medical:evaluation-results:1.0.0#/$defs/SampleManifest`、`#/$defs/ExpectedItems` 正式登记到共享 SchemaCatalogue；当前资源绑定必须声明各自精确 Schema，不能借用另一种已发布 Schema。

新增共享定义通过固定的 `contracts/extensions/evaluation-results.catalogue.json` 扩展登记。Java/Python/TypeScript 均固定扩展目录原始摘要，并核对其中的原基础目录摘要；拒绝名称/URI 覆盖、路径越界及未发布引用。原 `contracts/catalogue.json` 字节和全部原发布身份保持不变，既有 PermissionSchema 摘要仍为 `6345592f8753d1f0a392d773790c2f63c975145d84228226c10720a69ac707c6`，不会因新增评测 Schema 使旧权限及其后继制品失效。

请求/固定记录的封闭 Schema：`business/medical-persistence/src/main/resources/evaluation-results.schema.json`。所有读取要求 `X-Schema-Version: 1.0.0`、`X-Context-Revision`，写入另要求 `Idempotency-Key`。拒绝查询参数、重复边界头、未知字段、重复 JSON 键及尾随 JSON。

这是登记与计算实现，**不是上线验收证据**。真实 MySQL 迁移及事务、真实样本、真实专家评分、当前来源权利、真实外部执行尚需环境验证。质量统计阈值、置信区间、独立审批及发布门槛没有被补造。`production_eligible` 在代码、返回和数据库约束中固定为 false。

## 固定定义

定义冻结：数据集版本/摘要、样本清单制品/摘要、完整样本 ID、来源家族、分组、文档类型、REAL/SIMULATION 性质、输入和预期制品引用、候选版本/摘要、评分规则版本/摘要、必需维度、重复次数、方差策略、授权用途证据、环境和创建理由。样本清单制品摘要必须等于整份样本数组 RFC 8785 摘要，不能从原清单选择成功子集后继续声称相同清单。

REAL/PRODUCTION 是固定的来源性质声明，不是服务器签发的真实客户资格或生产核准。当前权利和血缘检查不能代替客户样本认证、专家资质确认或统计协议批准。

服务从实际 `agent_version` 及 `version_registry` 固定候选的版本、依赖清单、限制和 Schema 身份。候选不要求 PUBLISHED；DRAFT/VALIDATING/EVALUATING 等真实现有版本可被评分。DISABLED 候选仅允许获准历史读取，禁止新登记、追加、终结。此切片没有候选 Agent 创建接口。

每个样本另冻结逐维度的 `expectations`，含完整 `applicable_item_ids`，EVAL-11 另含 `control_item_ids`。ExpectedItems 还同时固定 schema_version、sample_id、source_family、document_type 及 exact input_ref（ID/摘要）；此完整对象的 RFC 8785 摘要必须等于该样本既有 `expected_ref` 制品摘要，分母由完整项目集合的数量确定，不来自首个评分者。已批准样本 A 的较小项目集不能在重算外层样本清单后移用于输入 B；输入或样本身份变化必须有新的预期制品及其批准绑定。创建时只有当前真实 APPROVED 规则的固定 basis_manifest 精确包含该 expected_ref（ID 和摘要）的样本，才得到服务端 `approved_expectation_samples` 记录；没有该绑定仍可登记观察，但结果为 INSUFFICIENT_EVIDENCE。现有规则维护是企业级范围，因此用于此审批绑定的预期项目集制品必须是企业所有、具有获准 DEPENDENCY 制品档案和当前来源权限；项目私有制品不能绕过规则服务范围。

样本×候选×必需维度×repeat_index 形成精确矩阵；repeat_index 从 0 起。每一来源家族只可属于 DEVELOPMENT/TUNING/ACCEPTANCE 中一个分组，相同输入制品不能伪装为不同家族。定义可保存空样本集，终结结果必为 INSUFFICIENT_EVIDENCE。

有界限制：100 个样本、10 个候选、12 个维度、100 次指定重复，笛卡尔矩阵总量最多 10,000 单元；完整历史读取最多 50,000 观察。上述是工程容量限制，不是业务质量阈值。

## 权限、来源与持久性

- 当前 `EnterpriseAccess` 身份/事务；MyBatis 租户拦截和 `userId` 检查。
- 动作：`evaluation.create`、`evaluation.read`、`evaluation.score`、`evaluation.finalize`；用途 `EVALUATION_RESULTS`。创建在真实项目/企业资源授权，其余在真实 EVALUATION 资源授权；写入同时需要当前读取许可以验证回读。
- 评分规则必须有真实 RULE_VERSION/规则定义、固定 Schema 身份及相同摘要。创建、追加、终结要求当前 APPROVED 状态和对应审批操作/审计/Outbox 链；DRAFT/RETURNED/DISABLED 或仅可读的其他领域版本不能充当规则。历史读取保留后续退回/禁用后的固定旧内容，继续核对当前读取权限。
- 真实候选另要求 `configuration.read`。所有引用按当前完整血缘核对真实领域所有者、资源许可、来源权利和服务端登记 Schema，不能仅凭 UUID 或登记摘要获得授权。
- `medical.evaluations` 配置提供服务端 environment、region、maximumVersions、maximumRelations 和 artifactProfiles。每个制品档案包含 resourceKind、action、schemaRef、schemaDocumentSha256、artifactSchemaManifest。缺少配置返回 DEPENDENCY_UNAVAILABLE，不使用请求体环境作为授权策略，不猜测或自动允许 Schema。
- 本流程只记录引用和评分，既不发送制品字节，也不签发读取回执或模型外发许可。
- 同事务写入、CAS、操作记录、审计与 Outbox，随后核对原记录。幂等重放保留原响应、原版本、原评分 ID，重查当前权限/引用；不能用重放绕过已撤销许可。

迁移 056：`evaluation_definition`、`evaluation_score_observation`、`evaluation_result`。复用已有 `evaluation_batch` 和 `evaluation_score`；不改写已有定义或评分。新增血缘关系 EVALUATION_DEFINITION。原定义摘要永久不变，批次只推进状态和修订。

## 观察和修订

客户端只能提交 SCORED/UNKNOWN/BLOCKED 观察、原始分子分母、未知/违反数量、EVAL-11 合法配对控制分子分母、输出和证据制品、理由以及 supersedes。scorer、租户、裁定和 production_eligible 不能由客户端指定。

scorer 取当前身份和有效 `evaluation.score` 许可；候选创建者不能给自己的候选评分。这里的权限配置仍必须真实映射到有资质的独立专家，代码不会把持有权限自动宣称为某项专业资质。

分子分母使用无负数的整数字符串，保存原值。首个观察、不同候选、每次重复及修订都必须保持固定 applicable_item_ids 的同一分母；EVAL-11 的控制分母同样绑定 control_item_ids。9/10 不能通过改为 9/9 获得通过；真实分子纠正为 10/10 仍可追加。分母零时值为 null，结果为 INSUFFICIENT_EVIDENCE。不同维度必须引用同一样本/候选/重复的同一个输出；不能换输出刷分。

修订只能追加并 supersede 当前同一单元的最新评分；单根、无环、无分叉、同候选/样本/维度/重复祖先链同时由服务和数据库约束。最终结果保留全部历史，选定有效叶子评分集合及其摘要。终结后不能追加；新的评测需新定义。

## 结果语义

输出包含定义摘要、有效评分和评分集合摘要、逐单元判定、逐维度覆盖计数、缺证据原因、总 verdict 及结果摘要。逐样本保留文档/来源家族分组信息，不用总平均掩盖失败。

- EVAL-02、06、07、11、12 仅对现有 PRD/EVAL 中明确的完成率 100%、未知/违反数量为零等不变量计算；EVAL-11 同时必须有非零合法控制分母且全部通过。
- EVAL-01、03、04、05、08、09、10 没有获准统计阈值及完整统计决策契约，始终显示 UNAPPROVED_STATISTICAL_THRESHOLD 与 INSUFFICIENT_EVIDENCE。提交一个规则版本引用并不自动批准阈值。
- 有缺失单元、UNKNOWN/BLOCKED、零分母、缺观察证据、未批准阈值或模拟样本，整体 INSUFFICIENT_EVIDENCE；仍保留已发现的逐单元 FAIL。
- 没有缺证据且任一已声明不变量失败，整体 FAIL；全部声明范围逐项通过时 PASS。此 PASS 只描述明确列出的观察断言范围，绝不是全产品 GO、真实业务改善或发布许可。
- `evaluation_batch.COMPLETED` 仍不意味着发布许可。完整发布门槛及独立评审/批准不在此切片。

## 本地验证清单

- CatalogueExtensionTest / EvaluationCatalogueCompatibilityTest：固定扩展及基础目录摘要、扩展篡改/覆盖拒绝、既有权限身份不变。
- EvaluationSchemaTest：封闭契约、完整样本摘要、分组泄漏、客户端 PASS/身份注入、分母零、容量和 JSON 严格性。
- EvaluationVerdictTest：完整矩阵、缺单元、逐组失败、未知阈值、模拟数据、修订链和篡改、确定性摘要、合法控制分母。
- EvaluationMapperTest：租户/当前主体/事务、禁缓存读取、CAS、不可覆盖历史、真实 DRAFT 候选读取。
- EvaluationRuleAuthorityTest：真实规则领域/Schema、缺审批链、草稿/退回/禁用写入拒绝与历史读取。
- EvaluationDenominatorTest：冻结预期项目集、输入替换攻击拒绝、真实发布 Schema 及 canonical 摘要、跨候选/重复相同分母、9/10→9/9 拒绝、合法分子修订、控制分母及缺审批绑定。
- EvaluationServiceBoundaryTest：端点、头/查询拒绝、幂等摘要、策略缺失关闭及返回值不能变为发布权限。

具体执行结果由本轮构建日志记录；没有运行数据库集成或真实评测的情况下，不得把上述单元/结构测试描述为生产流程验证。

## 前端登记与结果切片（2026-10-18）

### 已实现范围

- `/evaluation`：已有批次标识入口，明确当前没有批次列表、数据集/候选发现及执行接口（不提供相应操作；限制列于本开发说明）。
- `/evaluation/new`：严格解析完整 `Create` JSON，在页面内结构化核查样本、来源分组、候选、固定引用和评分矩阵，人工确认后登记。拒绝重复字段、尾随JSON、原始数值精度损失、样本/预期摘要不匹配及超容量定义；不会自动生成候选、制品或引用摘要。
- `/evaluation/:evaluationId`：定义、完整评分历史及终结结果分区。直接连接上文五个现有端点；刷新历史使用 `/scores`，全定义使用批次读取端点，没有虚构列表或执行端点。
- 结构化评分表单从固定样本、候选、维度、重复索引选择单元。固定分母和既有实际输出只读，候选创建人不能选择本人候选评分。缺少预期集合时仅提供 UNKNOWN/BLOCKED 观察，缺数保留为未知。修订追加到当前有效评分，并保留完整历史。
- 终结前说明评分将转为只读并要求确认；请求前再次读取修订，变化时不静默重放或换基线。只显示独立回读的服务端结果，始终显示不具备生产准入资格。

### 边界与恢复

`evaluation-domain.ts`复用已登记后端封闭Schema；前端`evaluation-view.schema.json`仅约束实际读取外壳与本地待核查请求。响应同时验证企业、项目范围、定义摘要、候选快照、评分单根修订链、连续原修订、评分人员独立性、固定分母、同次输出、结果/评分集摘要与逐维度覆盖。

结果完整性校验按本切片的固定 `EvaluationVerdict` 语义核对每个单元的结论、原因、缺项和四位比率，使用BigInt实现HALF_UP，不能仅凭一致的摘要把缺评分、未知、零分母、未批准统计阈值或仿真数据提升为PASS。此校验只用于拒绝错误响应，不把客户端计算作为实际结果展示。

创建要求企业/项目级创建与读取入口许可；仅持其他既有评测资源的读取许可不能用于新建回读。服务端继续作为当前真实权限、用途及来源权利的最终裁决者。缺少制品授权档案/环境策略时保持DEPENDENCY_UNAVAILABLE。

写入使用当前上下文头、CSRF和固定Idempotency-Key；`sessionStorage`只保留主体、企业、目标、原修订、上下文及摘要，不保存原定义、样本内容、评分理由、输出内容或凭据。未知结果须先查询原操作：FOUND按实际状态核查，NOT_FOUND只能在原上下文以完全相同原参数重试，EXPIRED不提供重试。只有原操作COMPLETED并独立回读匹配原请求后才清除待核查身份。

会话、路由变化及页面隐藏会取消旧读取并清空页面数据。导航后的完成回写再次检查原会话、可见性与目标，防止旧企业数据回流。未保存表单和正在写入的导航有离开保护。

为保持纯验证模块可独立加载，原`projectTextIssue`实现无行为改变地移至`project-text.ts`，`project-write.ts`保留同名重导出。

### 本轮验证证据

- VERIFIED：`node scripts/check_evaluation_frontend.mjs`，退出码0，99项纯Schema/领域/幂等恢复断言。包含缺评分伪PASS、未知/阻断伪PASS、伪数值、缺项伪造、自评分、断裂修订、其他批次读取许可误用、空样本等反例。
- VERIFIED：`node scripts/check_evaluation_transport.mjs`，退出码0，15项断言、15次内存模拟transport调用，覆盖5个业务端点、请求头、状态码、原key重试、操作确认、追加修订及独立回读。测试不建立HTTP连接。
- VERIFIED：`cd web && npm run typecheck:medical`，退出码0。共享文本函数原出口保持兼容。
- VERIFIED：受影响已有文件`git diff --check`通过。
- NOT_RUN：本切片浏览器交互、截图/视觉验收、真实HTTP、真实MySQL、真实专家样本与模型执行。前述全部测试数据为SIMULATION，不是业务验收或生产证据；未调用供应商、未发布/推送/部署。
- 完整生产构建由主任务在合并本地变更后单独复核；本工作单元未并发运行完整构建。
