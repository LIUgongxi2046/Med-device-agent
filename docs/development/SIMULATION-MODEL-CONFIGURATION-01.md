# C07 显式 SIMULATION 配置持久化切片

## 范围与结果

本切片实现不需要任何供应商凭据的 SIMULATION 模型配置草稿：创建、固定修订追加、历史读取、幂等重放、权限过滤的独立列表，以及 migration 057。

它不执行模型调用，不提交评测，不创建 invocation、connection_verification、价格审批、发布记录或预算回执。此前的 Python 本地执行组件见 `SIMULATION-EXECUTION-01.md`，二者尚未通过后端 worker/bridge 接通。CP02/RN01、生产准入与完整 Agent 流程没有因此完成。

## API 与兼容性

- POST `/api/simulation-model-config-revisions`
- GET `/api/simulation-model-config-revisions/{revisionId}`
- GET `/api/simulation-model-config-revisions?limit=50&after=...&q=...`
- `X-Schema-Version: 1.0.0`、当前 `X-Context-Revision`；写入另需 `Idempotency-Key`
- 两个 POST 都在验证唯一 schema/context/idempotency 请求头并取得当前认证身份后，直接读取 servlet 流，最多读取 65,537 字节；声明长度或实际内容超过 65,536 字节时返回 HTTP 413 / FILE_TOO_LARGE，不经过 MVC 的无界 `@RequestBody byte[]` 缓冲。有效请求原始字节与后续 canonical/hash 语义不变。
- 权限仍为 `model.configure` 与 `configuration.read`，purpose `MODEL_CONFIGURATION`；沿用当前企业、项目、会话、授权 epoch 与数据库事务边界
- 新建要求 `config_id=null`、`expected_revision="0"`、`based_on_revision=null`；追加要求原配置、当前 revision 和完整 version/hash basis
- 写入回执仍为 HTTP 201，状态固定 DRAFT，另明确 `execution_mode=SIMULATION` 与 `production_eligible=false`
- 操作查询沿用 CP-08。相同 actor/key、原 session/context/body/path 的重放返回原回执；请求变化返回 IDEMPOTENCY_CONFLICT。恢复读取必须使用 simulation 专属固定修订接口，不能把外部配置的 CP-08 结果当作模拟配置
- 列表沿用 bounded keyset、每页 1–100、最大候选扫描 500、逐项权限及固定内容验证，无 total；SIMULATION 游标与外部模型/规则游标不互通

新增应用契约为 `simulation-model-configuration.schema.json` 和 `simulation-configuration-discovery.schema.json`，各有独立 URN，未加入共享 catalogue。原外部模型及列表 API/JSON shape、原固定记录的哈希算法与 manifest 均保留。原外部固定 ID 读取碰到模拟记录时返回 OBJECT_UNAVAILABLE，不先尝试旧 schema 产生内部错误。外部列表 SQL 只选 EXTERNAL，模拟列表只选 SIMULATION。

固定兼容性基线：

- 外部配置 manifest：`8f76403cbe5922ee1e2a5eb8658915962a79de2001345a0a56ea73f01efe5630`
- 原配置 schema：`0ff65a1d7c939fed8042372f93486da2f5790122736a5aed69ceb075bfffef1b`
- 原列表 schema：`84bcf78bc35cad4f9b0e5e61e2a010719f8f0ec4f5bc2d6b24bc616d9f4fadfb`
- 原 `contracts/catalogue.json`：`dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915`
- permission schema：`fca270edf7d9697f340333c15992ecaa77fff4a8f1ac7bc51d2ffa8d56094843`

## SIMULATION 定义

定义是严格关闭的对象，不接受 auth_ref、credential、endpoint、price_basis、currency、data_policy 或任何连接/审批字段。

- `execution_mode`: SIMULATION
- `simulator_id`: `medical_model_gateway.simulation.SimulationProvider`
- `simulator_version`: `1.0.0`
- `model_id`、`model_version`: 明确以 `SIMULATION:` 开头的固定身份
- `case_binding`: case UUID、SHA-256，`validation` 只能为 DECLARED_ONLY
- `limits`: 与外部配置相同的数值边界和能力限制形状，仍校验无符号修订溢出、输出不超过上下文、temperature/reasoning 兼容性
- `provenance_schema_ref`: `urn:medical:local-simulation-provenance:1.0.0`
- `provenance_schema_sha256`: `ba6ee6a53f72ed4f62bec2243e35853706add8bf4e24e272b441fa50ef6dbc4c`
- `production_eligible`: false
- `provider_charge_amount`: `0.00000000`
- `charge_basis`: LOCAL_SIMULATION_NO_PROVIDER_REQUEST

provenance 文件从现有 Python 组件的真实 schema 字节复制为后端打包资源 `model-simulation-provenance.schema.json`，构造 schema 时核对其 hash，测试再比较原文件字节。这是应用控制的本地模拟数学声明：没有供应商请求便没有该请求的供应商费用。它不是报价审批、免费额度承诺、发票或预算结算，未来不得用它绕过外部计费门禁。

case hash 当前是用户声明的固定绑定，不证明 fixture 存在、内容可解析、输入相符或执行成功。界面/读取明确显示 DECLARED_SIMULATION_BINDING_ONLY；本切片不为 case 伪造 version_registry 或批准证据。未来运行消费者必须取得真实 case 字节，验证 hash、case/receipt 身份、实际 settings/input/parameters hash 和组件输出 provenance 后才能使用。

## 存储和不可转换模式

migration `057-simulation-model-configuration.sql` 包含 changeset 311/312：

1. configuration_head 增加 execution_mode，旧行默认 EXTERNAL。SIMULATION 只允许 MODEL 且状态 DRAFT。
2. model_config_revision 增加 execution_mode 与 simulation_definition。一次 ALTER 同时放宽仅外部所需的七个列为空，并加入完整补偿 CHECK。EXTERNAL 必须保留所有原非空字段；SIMULATION 要求这些字段全为 NULL、provider SIMULATION、region LOCAL、DRAFT，以及显式模拟元数据。
3. 每一条历史模型修订都通过含 mode 的复合外键引用 head。历史存在时不能把配置从 EXTERNAL 转为 SIMULATION 或反向转换；既有 current-version 外键继续约束当前修订。
4. 未改变原 model_configuration_definition、version_registry、lineage 的身份/哈希绑定，也未修改 invocation 或 connection_verification 表。

应用同时检查 head/stored/fixed 的 mode 与项目、状态、固定 JSON/hash/manifest、registry hash、全部重复投影字段和依赖谱系。新模拟修订没有价格/策略伪依赖；追加仅以真实前一配置修订为 lineage。事务内写入后重新读取固定内容、head、审计、outbox 和操作回执，模式与生产资格标签不能在重放时丢失。CAS 或 outbox 失败应回滚，单元测试使用明确隔离的事务替身验证调用与失败传播。

现有 invocation 非空凭据/价格复合 FK 无法指向这些 NULL 的模拟配置；但 connection_verification 的旧外键并未提供相同排除保证。未来 CP02 与调用 admission 必须显式检查 mode。本切片没有创建或执行该路径。

## 验证与剩余边界

测试包含旧外部创建/追加/读取/重放、固定原 manifest，模拟创建/追加/历史、权限撤销、CAS/outbox 回滚、跨模式请求/配置/操作/游标拒绝、投影与 manifest 篡改、回执去标签、伪 PUBLISHED head、SQL 的租户/mode 过滤、原 provenance 打包字节、migration 本地成熟解析器与约束断言。

最终定向测试：59 个 Java 测试（含两个模型端点的 8 个直接 servlet 边界测试）及 5 个 migration 静态解析测试通过，无失败、错误或跳过。更广的 migration bundle 起初被环境依赖阻塞，后来经独立工作修复测试工具链可移植性与旧表数量断言后，20 个离线检查已通过；证据为 `.implementation-work/migration-bundle-offline-20261018/report.json` 和 `full-bundle.log`。这仍不等于执行了 MySQL 迁移。命令和最终结果记录于 `.implementation-work/logs/simulation-configuration-evidence.json`。这些是无网络服务依赖的 schema、service 隔离测试、真实 mapper SQL 生成/tenant 拦截、静态 migration 检查；没有实际 MySQL DDL 升级、数据库事务、HTTP、供应商调用或客户验收结果。迁移执行前仍须在允许的隔离 MySQL 8.4 环境验证既有外键列的 nullable ALTER，以及升级失败恢复。

当前打包入口在 `ruoyi-admin/pom.xml` 固定为 `com.ruoyi.medical.runtime.MedicalApplication`，仅扫描 `com.ruoyi.medical`；两个显式若依集成配置未导入旧 FilterConfig，因此旧 RepeatableFilter 的全量 body 缓冲不在该入口的过滤链内。旧 `RuoYiApplication` 的宽包扫描若被重新启用，会重新引入该缓冲和换行改写风险，必须另外处理；本切片没有修改框架过滤器，也没有把直接 servlet 测试宣称为运行时过滤链/HTTP 集成测试。

## 后续依赖顺序

1. 在授权的隔离运行环境验证 migration057、真实持久化、HTTP 权限与回滚；不得把静态测试替代为已执行的迁移。
2. 增加独立 SIMULATION invocation/ledger 变体，保留 EXTERNAL 凭据、连接验证、网络授权、计费、限流和 release 门禁；不要伪造 PASSED/APPROVED。
3. 实现 fixture 字节读取/校验与 Java→Python 配置/settings 映射，绑定真实上下文/输入/action/receipt，并使用已有组件的强制 provenance 和保存后复读。
4. 完成 Activity/bridge、执行状态与中断恢复之后，才验证系统作业评测准入；后续真实批准/发布/运行各自需要独立证据。
