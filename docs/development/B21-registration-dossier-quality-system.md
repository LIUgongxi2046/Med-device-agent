# B21 注册目录、监管综述与质量体系

状态：代码交付，编译、测试、服务运行、迁移、提供方调用与业务验收全部 NOT_RUN。没有创建实际注册批准、试验或质量记录，没有推送。

## 正常入口与版本治理

POST `/api/registration-dossiers/specifications` 使用外层 1.0.0 `{schema_version,scenario,selection}`。scenario 是真实 B14 Author，八项人工规格及证据仍全部必需。小型 Author 可使用原 1.0.0；完整目录使用明确的 1.1.0 Author：INPUTS.definition 上限 200000 字符，APPLICABLE_KNOWLEDGE.evidence 上限 512。外层请求及写入后的真实 UTF-8 请求仍受 262144 字节上限约束，超限明确拒绝，不截断内容。依赖闭包、运行上下文等既有限制不扩大。

原 scenario-specification-v1.schema.json 字节未修改，SHA-256 为 71cf38ff7b7f8b2b9b2cf36e0636d97c2a779c69f3bed654049d7e47916604c3。新增 scenario-specification-v1.1.schema.json 仅定义 Author。原 Review、Publish、Disable 保持 1.0.0，通过固定 basis 指向 1.1.0 Author。服务按实际记录 schema_version 选择验证器及 manifest，校验请求摘要、行元数据、版本注册、依赖边、审计、事件；历史 1.0.0 不改写。当前 publication/task_entry 仍用原 CAS 和实时权限。

166-scenario-large-author-contract.sql 仅提供源文件，未执行。它先核验 information_schema 中目标检查的表、名称、类型和精确规范化表达式，再仅放宽 scenario_record 的 Author 版本条件。若真实安装的约束名或表达式不同则 HALT，不能盲目删除。其他摘要、分类、租户/项目外键保留；无新表。只有部署该迁移后，1.1.0 Author 才能落库。

## 专属输入

selection 包括 section_ids、固定 as_of_date、path、sources、directory、documents、evidence_usage、fields、procedures、conditions、body。

- path 的申请类型、IVD 属性、分类及编码、机关分别绑定真实已核准决定原文；法规年代/版本绑定法规原文。模板绑定真实核准模板来源和实际 template_version basis，作者规格必须列入该模板，正常任务必须选择同一模板版本。延续和变更还必须有原证及历次变更或明确无变更的真实依据，不能套用首次非 IVD 模板。
- sources 固定 source version/hash、rights revision、包含租户/项目及批准修订的 EvidenceRef 和精确 literal。所有来源必须进入 B14 APPLICABLE_KNOWLEDGE，并实际被任务选择。nature 区分法规、核准模板/决定、注册文档、原始记录、原件附件和草稿。标记不授予权限，仍经过真实来源批准及物理读取。
- 模板 literal 是严格 Template JSON：application_type/device_type/regulation_edition/template_version、nodes、conditions。nodes 含 code/title/parent、required_field_keys、requires_document/record/attachment。完整保留实际模板所有节点。首次非 IVD 对照已有参考目录的 6 个一级、38 个二级节点，额外保存 6.7.1–6.7.4 设计开发、采购、生产服务、监视测量设备子节点。6.1 为综述，6.2 为章节目录。参考资源不是新的法规批准。
- 软件、灭菌、人体接触、净化、委托与既往核查条件都有明确模板节点和决定。Condition 的实际依据 literal 必须精确包含 condition、applicability、rationale、directory_codes。UNKNOWN 阻断；不适用保留节点且必须是模板条件节点，并具有一致的真实批准依据，不删除原目录。
- 每个目录项保存责任、适用性、理由、证据、固定子文稿、页码和签署要求。首次非 IVD 每个适用的非目录二级/子节点必须引用实际固定子文稿，不接受六段空正文替代全部资料。
- documents 绑定具体注册文稿来源及其版本/hash、编号、主体、有效期、批准状态、记录与附件。批准来源 literal 必须精确包含 version_id/content_sha256/project_id/subject/number/version/valid_through/status，status 为 APPROVED。它只证明上传的真实核准版本，不表示模型或本系统批准上市。
- evidence_usage 逐条绑定真实记录/附件到固定子文稿。其核准来源 literal 包含 source_version_id/content_sha256/document_version_id/project_id/subject/recorded_at/valid_through/status，status 为 APPLICABLE。过期、未来执行日期、项目/版本不符、草稿冒充原始记录均阻断。日期检查针对固定 as_of_date，历史结果不声称当前仍有效。
- fields 为按目录定位的具名字段，支持文本和结构化 JSON 表格/关系。监管表单、产品列表、关联文件/沟通/声明；综述的分类、组成软件接口、逐型号参数差异、包装、研发比较、适用范围禁忌、上市与召回期间、配合产品；质量的生产场所供应商控制、质量手册方针目标、管理职责资源、实现程序、测量分析改进及核查附件均有明确必需字段。字段必须与真实来源 literal 相符，不能从空白推断“无禁忌”等事实。
- procedures 逐份保留 scope/responsibility/workflow 与实际 record_source_keys，检查实际程序原文及记录关联。核查附件不能只有生成的制度草稿。
- body 显式固定 node_id、directory_code、完整 content JSON、source_keys、field_keys 和 document_keys。表格、图形及引用关系不扁平化；允许不同节点具有相同文本，依靠 node_id 精确匹配。全部内容仍须通过正常制品 schema。

## N04 → N05 → 提交与人工审查

N04 在真实上下文读取结果内返回 registration_dossier_plan：完整目录、固定子文稿、条件、字段、程序、记录附件和规定正文。缺项形成明确 unknown_reasons，不生成不存在的执行记录、监管沟通或批准。

N05 使用真实保存并物理读取的候选正文、manifest 和独立来源回执。每个来源 literal 的 SHA-256 必须等于独立读取回执，不能采用模型自报已读。正文以固定 node_id、章节和 content JSON 精确匹配；逐项核验字段、文档身份、程序内容和原始证据链接，额外未核准正文阻断。输出目录、文字、证据完整性及 human_review_complete=false，明确是当前候选章节的机器检查而非全包人工签发。

专属结果注册于正常 REVIEW_FINDINGS / REVIEW_COVERAGE 的 provenance.normalization.registration_dossier。正常 submit 对两份真实回执分别重算并比较；blocked 清除 coverage_complete，正常 check_run 进入 WAITING_INPUT，无法以自报合格绕过原审查链。人工确认及签发继续使用既有文稿审查/签发机制。

GET `/api/registration-dossiers/tasks/{taskId}/runs/{runId}/results/{actionId}` 校验注册结果的制品/回执摘要、真实审计和 outbox，重新授权制品及已核准规格整个依赖闭包后返回历史机器结果，不把 source nature 或机器检查视为法定批准。

保留 B15–B20 全部路由。还按 B22 所有者提供的接口加入其 B14、源选择、N04 和 N05 producer/consumer 钩子；B22 实现与验收由其所有者负责。

## 延后验证

新增 RegistrationDossierTest 和 ScenarioLargeAuthorContractTest，仅合成合同数据，未执行。覆盖完整目录、错误路径、未知条件、草稿/缺记录/过期记录、结构化字段绑定、额外批准声明、正常覆盖阻断、旧 v1 manifest、仅显式 v1.1 Author 扩容、UTF-8 字节上限及其他类型不扩容。

仍须集中运行真实独立作者/审核/发布/任务固定链，来源权利撤销、跨租户/项目、原件实读及哈希篡改、正文重复表格节点、审批或附件过期、两回执篡改/重放、目录版本变化、正常阻断确认、人工签发、导出版式及完整业务验收。1.1 迁移须在真实 MySQL 上先验证约束元数据，本文不声称已完成部署或运行验证。
