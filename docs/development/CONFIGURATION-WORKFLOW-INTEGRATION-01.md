# 配置维护真实接口集成检查

## 范围与当前结论

新增 ConfigurationWorkflowHttpTest、ConfigurationWorkflowSimulationData 和 scripts/check_configuration_workflow.py，覆盖模型配置、规则维护及授权目录。2026-10-18 已通过 10 模块 Java 测试编译、Python 语法/help 与空白检查。**真实 MySQL/HTTP/事务执行 NOT_RUN**：当前环境禁止 MySQL Unix socket，受控提权在执行前失败，不能用编译成功替代集成通过。

测试通过真实账号接口创建两个普通 USER，以独立随机 SIMULATION 租户、最小授权及确实生成的自有测试制品验证行为。不使用会插入虚拟发布记录的 SimulationTaskData，不创建生产发布、Agent、任务、来源审批或供应商调用。

覆盖模型保存/固定历史/原请求重放/敏感引用脱敏、并发 CAS 与台账增量、授权列表分页及字面搜索、规则核准/退回/停用、跨租户拒绝与限定租户清理。填充升级、注入故障后的事务回滚和备份恢复仍需独立验证。

## 受控恢复与执行

1. 在可运行服务的原授权隔离环境准备 MySQL 8.4 与仅回环监听且有密码的 Redis。当前半初始化目录保留原秘密，不能再次 initialize 或删除重来；仅在允许启动 socket 后恢复引导与 secure。
2. 迁移使用独立管理身份，固定两个 medical-data JAR 摘要、实际数据库名/server_uuid、当前批准依据和新备份回执。依次 validate、status、审阅 update-sql、备份、update、status 与无变更再执行检查。054–056 共7个变更项、6张新表；全量 master 当前301项。
3. 应用用户仅 DML 权限。为本轮创建独立管理签名/邀请/token/discovery 配置，明确 LOCAL template artifact profile。启动真实应用后通过 CSRF、/auth/setup、/auth/login 引导管理员。
4. 执行：python scripts/check_configuration_workflow.py --execution <新的执行标识> --additional-config .implementation-work/runtime/configuration-workflow.properties

运行器要求已有本轮服务身份、私密凭据、管理员和当前迁移；自身不初始化服务或秘密。保存 JUnit、源码摘要及工作流证据，不记录密码，不把历史 PID 或旧检查回执当成本轮成功。

## 评测集成后续

仍需独立评测运行器、固定 sample/expected-items/input/output/evidence、真实 TP-04 核准规则、不同于候选创建人的评分者和精确制品策略。SIMULATION 记录不具有生产资格，production_eligible 必须保持 false。当前配置测试不证明这些评测业务已通过。
