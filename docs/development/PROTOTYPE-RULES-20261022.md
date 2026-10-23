# PR-02 检查规则业务配置

## 任务身份与依据

任务来源 PROTOTYPE-RESTORATION-20261022，状态 IN_PROGRESS，文档 CURRENT。已核查原型 final-product.js 的检查规则表格与 ruleEdit，字段为规则名称、检查维度、判断条件、处置方式、版本和审核状态。检查维度包含内容完整性、引用准确性、严格字段一致性、规则符合性及人工修改量；处置方式包含阻止签发、人工确认及提示。

已核查 RuleVersionService、RuleVersionStore、RuleVersionApprovalService、RuleVersionSchema、RuleVersionMapper、OperationService 及对应前端。现有规则表达式与依据保持固定版本，业务名称、维度和处置方式需要持久化，并进入同一规则版本的人工审核。

## 设计决定与实施要求

业务定义与原表达式定义分别使用固定 Schema。新增 rule_business_definition，按企业及 rule_version 唯一保存完整业务定义、其摘要、原规则内容摘要、Schema 摘要及创建身份。名称、维度、判断条件、处置方式和业务资料分类在创建时保存，此后修改创建新的规则版本。原表达式及其摘要、来源关系与已有人工决定保持原协议。

新建业务规则使用独立请求入口，服务在同一次已有 EnterpriseAccess 事务中保存表达式、业务定义、原操作身份及完整审计。请求摘要包括完整业务定义及其 Schema 身份，重复请求重读原记录，参数变化明确拒绝。表达式继续由固定引擎执行，不把自然语言条件解释为已经执行的检查。

业务规则人工决定要求当前业务定义摘要，服务在原决定事务中核验不可变定义、规则内容摘要及创建审计；缺少或不一致时拒绝。界面在同一审查页显示业务定义与完整表达式。已有无业务定义的版本保留其实际状态，不生成名称或处置方式。

业务资料分类按实际业务定义保存，后续反馈读取同一版本及内容摘要下的分类。既有非标准分类只接受显式登记的版本与摘要；不以权限元数据标签推断资料敏感程度。

## 验收与验证

PR02-01：Given获授权维护人员，When填写名称、维度、条件、处置及实际表达式和依据，Then同一次保存生成完整业务规则草稿，刷新后完整读取。

PR02-02：Given当前业务规则，When通过完整审查登记人工决定，Then状态推进并读取同一业务定义；缺少业务定义摘要或版本变化时拒绝。

PR02-03：Given已有规则，When修改业务定义，Then生成独立新版本并保留原始定义和审核状态。

PR02-04：Given当前业务规则及项目，When登记反馈，Then业务分类来自本版本的实际定义；不同企业、摘要不匹配及未完成审查的依赖分别拒绝。

PR02-05：Given实际规则及其不可变业务定义，When组合查询名称/条件、检查维度、处置方式和审核状态，Then服务器在完整授权目录中执行筛选并返回核实后的同版本业务资料。分页绑定完整筛选与当前身份，改变条件不能继续使用原游标。界面采用业务筛选和列表，已知UUID入口放入高级区域；不把本页筛选解释为完整目录查询。

当前实现包括完整业务定义Schema、不可变存储、同事务创建及审计、固定定义读取、审核摘要绑定、真实业务分类读取，以及规则名称/维度/条件/处置表单、规则目录、详情和基于已核准版本的修订。原规则内容及workflow Schema保持原有固定字节。2026-10-23 已完整备份medical_local并执行新增表迁移，当前1030项迁移记录；Liquibase制品6项检查通过。证据 .implementation-work/prototype-rule-business-migration-build.log、prototype-rule-business-migration.log及受保护数据库备份凭据。

严格类型检查、636项固定Schema引用和480项调用检查通过。当前统一后端18项检查通过，正式前端构建完成，577项静态文件与运行制品逐字节一致。实际接口通过已有规则空登记状态、业务创建与读取、完整参数重发、参数冲突拒绝、审核摘要必填及核准后不可变业务定义重读。实际浏览器通过业务表单、基于已核准版本的修订、人工核准及刷新读取，新版本e194c0be-6450-4f59-9bf8-357fd5a1e5f5。证据 .implementation-work/rule-business-definition-current.log、rule-business-definition-checks.json、rule-business-ui-current.log、rule-business-ui-checks.json、prototype-rule-audit-runtime.json。没有执行视觉检查。

规则目录采用独立rule-business-directory Schema，组合查询名称/条件、检查维度、处置方式和审核状态。服务器在已有授权目录扫描事务中读取规则及同版本不可变业务定义，逐项核验内容摘要和原始审计；原始规则目录及固定定义Schema保持原协议。分页游标绑定全部筛选、当前身份、会话、组织及授权时间边界，不计算未授权记录总数。页面通过一次目录请求读取完整业务行，名称、条件、检查对象、处置与状态保留直接业务展示；固定版本标识入口位于高级区域。

PR02-05已通过真实数据库、HTTP与Chrome核验。中文名称、四项组合筛选及同版本业务摘要对应，全部五页、五个实际规则连续且没有重复；名称、维度、处置、状态与条数变化均使原游标失效，跨目录使用同样返回现有规范422/CURSOR_INVALID，非法筛选明确拒绝。页面通过原型五列、组合查询、空状态清除、筛选重置、实际详情及目录没有逐行业务HTTP请求的核查。标题24px、文字#243247、主操作#1768C4、卡片圆角8px、控件圆角6px、高度38px，浏览器page_errors为空。证据.implementation-work/rule-business-directory-green.log、rule-business-directory-checks.json、rule-business-directory-ui-green.log、rule-business-directory-ui-checks.json。

640项固定Schema及490项调用检查通过，后端19项检查通过。最终正式前端构建、统一打包及运行核查通过，588项前端资源与运行制品逐字节一致，制品985ca2c9e408fd4c6d04e86290f8268dedb942f37f005083832e3eecc414ce50。证据.implementation-work/rule-directory-types.log、rule-directory-backend.log、rule-directory-frontend-release.log、rule-directory-package-release.log、rule-directory-runtime-release.json。处置方式进入独立检查及签发门槛的消费仍需在PR-05核查，任务保持IN_PROGRESS，没有执行视觉检查。
