# SIMULATION-EXECUTION-01：本地模拟执行与不可变来源记录

## 范围及状态

2026-10-18 新增 C07 前置的 Python 执行组件。该组件实际调用现有 `SimulationProvider`，保存并重新读取实际文件，验证完整结果和必要的模拟来源记录。它不是 C07 HTTP 服务、业务数据库调用台账、费用预留、正式 Artifact 登记、Python Harness 宿主或 Temporal Activity。

没有创建 Agent 发布、批准、真实连接、客户验收或生产可用状态；没有实现 CP-02 或 RN-01 成功执行。传入的企业、运行及动作 UUID 只用于组件绑定和隔离，不证明这些业务记录存在或调用者有权使用。接入业务前必须通过现有服务身份、运行授权、版本和预算准入。

## 交付与调用契约

- `model-gateway/src/medical_model_gateway/simulation_execution.py`：`SimulationIdentity`、`SimulationFileStore`、`SimulationExecutor` 和 `SimulationExecutionFinished`。
- 同包 `simulation-execution.schema.json`：封闭的本地来源记录，身份为 `urn:medical:local-simulation-provenance:1.0.0`。该 Schema 仅随本组件打包，没有进入公共 SchemaCatalogue；它不授予业务制品登记资格。
- `tests/test_simulation_execution.py`：组件测试。测试使用真实 SimulationProvider、共享 ModelResult 绑定器和临时目录，故障注入只作用于写入/读取/时序和禁止调用断言，不替换模型输出。

构造 Store 时必须给出已经存在的专用私有目录和明确的 `maximum_record_bytes`。构造 Executor 时必须提供现有 `ModelStreamBindings`，从而用原发布 Schema 验证 ModelResult 以及逐个输出事件。`stream(identity, case, settings, parameters, exact_input)` 只接受明确的 SIMULATION 模型及固定 SimulationCase；没有供应商、端点、秘密或替代执行器参数。

流中的文本/工具片段只是未完成候选。只有收到 `SimulationExecutionFinished` 才表示本地持久化和重新读取已验证。该类型明确携带 `input_data_kind=SIMULATION`，返回原始 result/provenance 字节以及是否复用了原记录；它不是后端 `FinishEvent`，也没有伪造服务端 artifact_id。读取方法每次重建独立对象。

## 保存、重复和中断

1. 在任何写入或执行前重新验证并复制完整模型输入、配置、参数、模拟案例、工具名称和完整 JSON 参数。非 SIMULATION 模型、摘要不符、未知工具、重复 JSON 键及无效参数立即拒绝。
2. 同一企业、运行、步骤和动作计算固定目录身份；同一动作的新 invocation、输入、配置、案例、Schema 或参数不能替换原绑定。文件目录和文件必须同用户私有，拒绝最终路径符号链接、非普通文件及不安全权限。
3. 使用实际跨进程文件排他锁阻止同一动作同时执行。请求及输出使用临时文件、fsync 和拒绝覆盖的原子链接保存；单条记录有明确大小上限。读取与锁文件使用非阻塞打开，再验证普通文件身份，FIFO 不能阻塞事件循环。
4. `request.json` 保存完整固定输入和 Schema 摘要；`started.json` 在实际 SimulationProvider 开始前固定请求摘要。
5. 执行中验证全部事件顺序和内容，以及实际 provider completion 与固定案例相同。执行使用单调时钟及原配置截止时间；超时上下文只包住 provider 的下一项读取，不跨越向调用方 yield，不能取消调用方正在执行的其他逻辑。保存提交和完成返回前再次核查截止时间。
6. 完整结果按原 ModelResult Schema 保存到 `result.json`；必要来源记录保存到 `provenance.json`；最后保存绑定三者摘要的 `commit.json`。成功返回前重新读取全部文件，验证精确字节、Schema、原请求、完整案例及相互绑定。公开完成项在内部生成器关闭、文件锁释放后才返回；调用方在完成项处停止读取不阻止后续重读。
7. 新实例遇到相同已提交动作时只核对并返回原字节，不调用 SimulationProvider。更改参数返回 `SIMULATION_IDEMPOTENCY_CONFLICT`。已有开始标记但没有提交记录时返回 `SIMULATION_RECONCILIATION_REQUIRED`；不会自动重做、删除或推定成功。只有请求已保存而尚无开始标记时可以继续首次执行。
8. 磁盘写入失败、消费者提前关闭、截止时间或不完整结果都不能产生成功完成项。提交已完成但最后重新读取失败时，本次不报成功；之后可以重新验证同一提交记录并恢复。没有运行取消或外部终止证明。

文件权限和摘要检查用于受控本地存储的一致性与误用防护；不是对拥有该操作系统账户或目录管理员权限的恶意修改者提供密码学认证。业务接入仍需服务端身份、审计及受限对象存储。

## 模拟性质与费用

来源记录必含 `input_data_kind=SIMULATION`、`execution_scope=LOCAL_COMPONENT`、案例/回执身份、全部输入/配置/参数/案例/结果摘要、原 ModelResult Schema 身份和摘要。`external_request_sent` 与 `production_eligible` 固定为 false。

`provider_charge_amount=0.00000000` 的唯一依据是 `LOCAL_SIMULATION_NO_PROVIDER_REQUEST`，表示这个本地组件没有发出供应商请求。它不是供应商价格批准、真实账单、预算结算或基础设施零成本声明。未提供用量时保持 null 和 `usage_origin=UNKNOWN`；显式测试计数标记 `SIMULATION_FIXTURE`，不能冒充真实模型 Token 计量。本组件不释放预算预留、不修改调用/Token 余额。

## 兼容性

原 `contracts/catalogue.json`、评测扩展、权限 Schema 和 `agent-design/model-stream.schema.json` 保持原字节；既有 ModelResult 不增加任何模拟字段。必要模拟性质通过完整绑定的独立来源记录保留，不能丢弃后当成真实结果。

当前公共 Catalogue 未登记此本地 Schema，后端不能据此接受一个任意 Artifact。后续发布应新增独立固定扩展，并同步 Java/Python/TypeScript 的编译期路径/摘要白名单、打包和一致性测试；不能修改原基础 Catalogue 或覆盖既有名称/URI。

## 后续 C07 与 Agent 依赖

- 持久模型配置和 invocation 需要明确的 SIMULATION 分支。现有数据库要求 credential 和 connection_verification 外键，不能用虚构秘密或 PASSED 连接填满这些字段。外部供应商分支继续保持原凭据、当前验证、价格和外发要求。
- 实现 IA-06、IN-08/09/10 的当前授权、精确 ContextManifest、真实事务预留、一次派发 CAS、结果/来源记录保存与核对；IA-09/10 和 IN-11 继续处理未知、取消和结算。使用同一 invocation/action 台账，不建立绕过预算的影子运行。
- Python 私有桥接宿主与专业 Activity 仍缺少，当前 TypeScript Unix Socket 测试服务器不能作为生产宿主。随后连接持久 RunActivities、完整步骤和受控 SYSTEM_JOB 评测准入。
- RunTokenAuthority 当前对 SYSTEM_JOB 同样要求 PUBLISHED 角色。预发布候选执行必须明确实现受限的评测资格，不能先伪造发布来运行测试。
- RN-01 的 C06 准入、完整 ReplayManifest、正式运行保存策略和工作流消费仍需完成。Evaluation.COMPLETED 不能替代通过门槛与发布批准；评分阈值、合格评审人员、生产批准和保存期限保持各自待确认范围。

## 验证记录

独立审查发现并修复三项问题：非普通文件阻塞打开、超时上下文影响消费者以及完成项返回时仍持有锁。对应回归已加入；独立审查复跑全部 23 项通过，5.934 秒，无剩余具体阻断项。

最终从本地源码通过 `pip wheel --no-index --no-deps --no-build-isolation` 构建 wheel，使用 `pip install --no-index --no-deps --force-reinstall` 安装到现有 Python 3.12.14 环境。没有下载或新增依赖。wheel SHA-256 为 `4979b8fcd41214a4650af462ce4cf09f03199848ac6e0bd457974d0271c68cbd`。

最终检查使用 `PYTHONPATH=contracts/python/src`，确保模型模块和 JSON 资源来自实际安装的 wheel，而非源码目录：

- `test_simulation_execution.py`：23 项通过，6.093 秒。
- `test_model_simulation.py`：3 项通过，0.010 秒。
- `test_model_gateway.py`：28 项通过，0.036 秒。原凭据组件测试只创建并清理其自己的临时测试值，不读取既有真实密钥或发送网络请求。

合计 54 项通过，失败/错误/跳过为零。原始日志位于 `.implementation-work/logs/simulation-execution-installed-final.log`、`simulation-execution-adjacent-installed-final.log`、`simulation-execution-gateway-installed-final.log`；构建与安装分别保存 `simulation-execution-wheel-final.log`、`simulation-execution-install-final.log`。精确源码、已安装文件、wheel 与日志摘要保存在同目录的 `simulation-execution-evidence.json`。

这些检查仅证明本地组件、独立进程文件锁及包安装范围。没有执行真实数据库、业务 HTTP、Unix Socket、付费供应商、Harness 或 Temporal Worker 验证。
