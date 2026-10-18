# MODEL-CONFIGURATION-01 模型配置与固定版本

> 2026-10-18 续接说明：下文原电脑的密钥、连接、运行服务和回执为历史事实，不代表当前工作区具备它们。当前仅恢复源码；未迁移供应商秘密，未调用付费模型。开发环境及实测限制见[最新接管记录](../接管开发记录-2026-10-18.md)。

## 任务身份与依据

本任务属于BE-07，关联C06/C07、CP-01—03、IC-07及BE-12/13/14/28。唯一状态来源为本记录，当前IN_PROGRESS。2026-10-14读取IMP-07、IMP-12、开发索引、LLD-后端模型配置/内部调用定义、007迁移、BudgetProtocol、实际供应商适配器与凭据读取、当前CP公开路径，以及已实施RUN-TOKEN-AUTHORITY-01。完整父工单状态保持IN_PROGRESS。

## 当前事实与验收

当前已有Python供应商适配、价格数学、受保护秘密读取，以及公开预算管理；model_config_revision的完整持久配置、秘密版本和价格依赖、实际连接回执及正式发布仍需实施。已有DeepSeek秘密文件保持受保护状态，不需要重新提供秘密值。

2026-10-14已经核查当前DeepSeek官方[模型列表定义](https://api-docs.deepseek.com/api/list-models/)及[价格定义](https://api-docs.deepseek.com/quick_start/pricing/)完整内容。模型列表包含实际名称、context_window、max_output_tokens、输入/输出媒介、effort及按协议的api_capabilities；当前公开价格区分PEAK/OFF-PEAK，包含工作日、UTC时间和中国公共假日规则。固定价格版本必须完整表达适用条件，预留继续采用完整最高价格上界；结算需要实际供应商回执和适用价格依据，不建立人工假日、费用或批准记录。

当前DeepSeekProvider.list_models已经实现真实模型读取，configure_deepseek.py的verify会保存完整实际返回；后续开发复用该入口。正确环境为.implementation-work/model-gateway-venv，当前已有受保护密钥及供应商连接记录；配置持久化、元数据资格、预算和收费调用继续分别实施。

Given当前模型配置管理许可、明确提供的供应商/端点/地区/模型身份、秘密引用及固定版本、全部限制/data_policy、价格引用、请求参数Schema与协议修订；When保存固定配置；Then同一企业事务保存完整不可变内容与摘要、版本登记、来源关系、原操作、审计和Outbox，CAS更新维护头并重读原版本。缺少输入、未知字段、旧修订、异参和越权均拒绝。

Given完整实际连接回执、独立评测及当前授权发布决定；When发布或新任务采用；Then完整当前依赖与原固定版本共同核验。没有真实许可、价格和用量时，不建立批准价格、模型调用、费用或专业成果。

## 未完成范围与继续条件

完整封闭定义及保存/读取、秘密引用与版本管理、价格依赖、连接/评测/发布、当前任务消费者与合法业务成功仍待实施。配置、预算预留、一次发送、结果保存、未知占用与结算保持完整BE-07责任；缺少业务输入只影响相应实际动作。

## 2026-10-16 本地工程增量：固定草稿保存与读取

本次继续完成 CP-08 的首个有界工程切片，父任务继续为 IN_PROGRESS。新增 ModelConfigurationController/Service/Schema/Protocol/Mapper 和 054-model-configuration-definition.sql；没有建立真实凭据、价格批准、验证回执、评测、发布或供应商调用。

- POST /api/model-config-revisions 保存固定 DRAFT；GET /api/model-config-revisions/{revisionId} 按原固定版本读取。现有会话、CSRF/Origin、X-Schema-Version、X-Context-Revision、Idempotency-Key 和 EnterpriseAccess 企业事务继续适用。
- Save 使用封闭字段 schema_version、project_id、config_id、expected_revision、based_on_revision、definition、reason。新配置明确传 config_id=null、expected_revision="0"、based_on_revision=null；追加必须传原配置身份、当前维护头修订和原模型版本及摘要，不能改变项目归属。
- Definition 明确固定 provider/base_url/region/model_id/model_version、auth_ref、credential_reference_id/credential_revision、完整 limits、price_basis/currency、完整 data_policy、request_parameter_schema_ref/request_parameter_schema_sha256 和 model_protocol_revision。HTTPS 端点不得包含 userinfo、query、fragment 或可归一化的路径跳转；这里只校验存储输入，不进行 DNS、SSRF 网络准入或 HTTP 请求。
- limits 包含 context_window、max_output_tokens、max_concurrency、requests_per_minute、tokens_per_minute、deadline_seconds、temperature_enabled、reasoning_efforts、stop_limit。计数为不溢出 U64 的正数字符串，输出不得超过上下文窗口；当前适配器 deadline_seconds 上限 300，stop_limit 上限 16。所有值由请求明确给出，无默认额度。
- data_policy 包含 allowed_classifications、allowed_regions、allow_training、retention_days、policy_basis；配置地区必须在显式地区集合中。price_basis 与 policy_basis 采用 version_id/content_sha256 固定引用，当前切片只核验同企业登记、摘要与项目归属，并保存 lineage。该核验不代表来源权利、价格资格、当前外发授权或正式批准，读取明确返回 dependency_validation=REGISTRY_REFERENCE_ONLY。
- 完整固定内容含服务器认证主体、权限见证、原版本及理由，按 RFC8785 计算摘要；配置投影、model_configuration_definition、version_registry、lineage、维护头 CAS、治理水位、会话上下文、原操作、审计和 Outbox 在同一事务保存并重读。旧的缺失定义行不自动补造内容。
- 同键异参拒绝；同键原请求重读原不可变版本，即使维护头已推进，也不会另建新版本。当前权限仍须重新核查。
- configuration.read 与 model.configure 分开检查。公共详情不返回 auth_ref、credential_revision；记录类型的 toString 隐去完整定义。审计和事件只包含摘要、固定身份与权限见证，不保存请求体、端点、凭据路径/版本或自由文本理由。

工程验证：新增 ModelConfigurationSchemaTest，覆盖封闭字段、重复/尾随 JSON、体积上限、CAS 成对约束、U64/计数范围、端点、地区政策、冲突依赖摘要、凭据引用脱敏、请求摘要及原记录、全部 Mapper SQL 解析和企业/主体注入。JSON/XML 解析、Draft 2020-12 元 Schema 与 git diff --check 已通过。Java 定向运行已通过 ModelConfigurationSchemaTest 11 项及 ModelConfigurationServiceTest 9 项（合计 20 项，失败/错误/跳过均为 0）。服务测试使用真实 Schema、ResourceReadAuthority 与 PermissionAuthority，存储和平台 JDBC 为隔离替身，覆盖合法保存/读取、原版本重放、异参、旧上下文/CAS、撤权、跨企业、依赖错误及失败传播；替身回滚不是实际数据库事务证明。数据库迁移与事务/并发/HTTP 集成尚未执行，不能以这些单测代替运行验收。

继续范围：批准秘密服务的引用可用性及轮换；真实价格固定版本、全量适用条件和最高预留上界；依赖资源的当前访问/用途/来源权利；参数 Schema 制品资格；真实连接和独立评测；当前发布决定；状态管理；运行消费者、预算预留、一次发送、实际用量与未知占用/结算。上述门槛未实现前，本切片永远只产生 DRAFT，不授予执行或发布能力。
