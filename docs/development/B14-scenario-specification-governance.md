# B14 场景规格与固定任务入口

## 实现边界

独立的 `scenario-specification-v1.schema.json` 契约不修改已有发布、任务输入或基础 Schema digest。正常 API 提供 Author、Review、Publish、Disable 和固定记录读取/当前入口发现；不依靠手工种子。规格绑定项目内真实正式 SKILL 能力，复用 FormalCapabilityIdentityAuthority 和正式发布链，不创建虚构能力、客户事实、法规、专业规则或获准样本。

八字段全部必填且拒绝空白：ROLES、INPUTS、APPLICABLE_KNOWLEDGE、TASK_RULES、OUTPUTS、HUMAN_REVIEW、FAILURE_STATES、ACCEPTANCE_SAMPLES。每项要求实际说明和至少一个固定 version/hash evidence。ROLES 证据必须为真实正式 ROLE；其它证据必须可按当前正式治理用途读取并通过真实 lineage。契约完整只表示可配置，并不表示内容已被专业批准。

## 正常作者与发布流程

1. 正式能力/工件既有作者流程产出真实项目级 SKILL 与 ROLE。POST `/api/scenario-specifications` 写入完整规格，要求 `capability.configure` 与当前全部依赖读权限；模板版本与文稿类型显式固定。
2. 不同于作者的当前有效成员 POST `/reviews`，要求独立 `scenario.review`，固定规格 hash 和理由。此接口明确表示批准该固定规格；不提供自动批准或演示样本。
3. POST `/publications`，要求 `scenario.publish`，同时指定规格、审查、真实 release、document type 与 expected_revision。正式发布 authority 在事务外准备物理证据，事务内消费不可伪造的 VerifiedPublication；真实评测/验收/发布依赖缺失时拒绝，不接受 SIM 代替。全部规格证据必须已被真实发布 dependency closure 覆盖；模板必须出现在真实发布 manifest。
4. 首次发布 expected_revision=0；替换发布使用当前入口 revision。新 record 永不覆盖旧 record。Disable 必须固定 publication/version hash、CAS revision 与理由；保留历史，不删除。
5. 普通记录读取和当前入口读取返回 execution_eligible=false。ACTIVE 只表示入口配置状态。实际可执行性必须通过当前 TaskCreate admission。

## 实际任务入口

PublishedTaskCreateAdmission 在获得真实当前 VerifiedPublication 后调用场景 gate。由 `(tenant, project, agent_release_id, document_type)` 唯一解析已发布规格，不修改原任务请求 hash。TK-01 和 TK-06 的既有 prepared admission 都经过此路径。当前规格、独立审查、发布者/审查者成员及原治理 grant、正式能力、完整八字段证据、模板适用性、实际发布/评测资格、全部依赖读权限均需重新验证。当前选择的资料必须包含在 APPLICABLE_KNOWLEDGE 的真实依赖闭包内；现有来源策略、权限、release scope 和当前项目检查仍保留。

返回 private-constructor Proof；其版本依赖进入实际任务 lineage，且既有 requireUnchanged 在写入两侧重查。缺规格、禁用、替换、过期/撤销 reviewer/publisher、缺证据、无正式发布 proof 等均不可执行。目录既有 PLANNED 元数据从不产生资格。

## 持久化与权限

148 新增 `scenario_record` 和 `scenario_task_entry`。前者包含 mandatory metadata、版本注册 hash FK、真实 configuration owner、项目/成员/父记录/audit/outbox FKs、幂等键、内容 hash 与完整 witness，UPDATE/DELETE 被禁止；读取重新验证 hash、类型、原审计/事件与精确 lineage。后者引用真实 release 和同项目 publication，使用 CAS revision、不可变 owner metadata、禁删触发器。新增 VersionResource typed owner 分支为 SCENARIO_SPECIFICATION、SCENARIO_REVIEW、SCENARIO_PUBLICATION、SCENARIO_DISABLE，沿用真实 configuration.read，不把目录映射成资格。

所有治理/读取操作在 EnterpriseAccess 事务中执行，SQL 显式限定 tenant。新 action 通过现有权限授予流程配置；没有自动权限或默认放行。

## 验证状态

仅执行变更生产类的 Java 17 diagnostic compile，未跑测试、完整构建、DB migration、provider/runtime 或网络联调。新增 ScenarioSpecificationSchemaTest 和更新 PublishedTaskCreateAdmissionTest 是留待统一测试的源码，不是已通过报告。

后续统一验证应覆盖：八字段逐项缺失/空白/多余字段；无真实正式能力；自审；审查/发布 grant 撤销；同版本异 hash；历史/replaced/disabled publication；跨项目/跨文稿/跨模板；选定知识越界；真实评测资格撤销与 SIM 拒绝；幂等同体/异体、CAS 竞争、审计/事件/lineage 篡改；TK-01/TK-06 都不得绕过 gate；DB immutable/CAS/FK 边界。
