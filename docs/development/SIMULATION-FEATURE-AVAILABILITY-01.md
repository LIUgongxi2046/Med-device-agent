# 已认证的 SIM 功能可用性

## 最小只读接口

`GET /api/simulation-feature-availability` 接受单值 `X-Schema-Version: 1.0.0`、`X-Context-Revision`，不接受 query/body。先经 `ApplicationSessionIdentity.requireCurrent` 和 `EnterpriseAccess` 验证当前用户、企业成员与上下文，再返回 no-store Envelope。

独立本地 `simulation-feature-availability.schema.json` 闭合响应，只包含 schema_version、current_context 和三个明确布尔值：

- input_artifact_registration
- rule_workflow
- evaluation_preparation

服务仅检查这三类控制器是否已实际注册，不实例化它们、不读取任意环境变量、不暴露配置、秘密或 grants。控制器原有 ConditionalOnProperty 默认关闭规则保持原样。新查询本身始终注册，允许已认证的当前企业用户查看关闭状态。它不授予操作权限，也不证明某次业务输入可执行。

## 前端接入

共享 `simulation-feature-availability.vue` 根据严格 API 返回决定是否挂载默认 slot。初始、未知、错误、隐藏、身份变化均不可用；不使用默认 true 或 fallback 成功。

接口：`feature="rule_workflow"`（或另两项），`@change="enabled = $event"`。父组件已有自动读取或提交 handler 时，还必须把该 enabled 状态纳入 guard；仅隐藏模板不能阻止父 setup 中的请求。

SIM 规则管理页已包装，并在父 read/busy guard 加入服务状态；new 子页面只有启用后才挂载。063/065 的 simulation-preparation.vue 由其当前工人按同一契约接入，避免并行改写。权限仍由页面及严格业务适配器独立核查，后端仍是最终权威。

读取不共享跨身份缓存，检查完整 context，旧请求响应/finally 按代次丢弃；页面重新可见时重新核查。无效服务响应继续 fail-closed；合法 false 仅显示“服务器尚未启用”，不会撤销会话或冒充权限不足。

## 验证

- Java：SimulationFeatureAvailabilityTest（5项）、SimulationFeatureAvailabilityControllerTest（3项），交 root 的统一 Maven 运行。使用隔离 bean registration / mock 身份边界，不代表真实数据库或部署验收。
- `node tests/unit/simulation-feature-availability-checks.mjs`：真实编译 template + setup，以 Vue renderer 验证 disabled 不挂载业务 slot、enabled 挂载但不添加权限、身份/隐藏/迟到响应隔离、严格闭合 Schema 与未知状态。
- `node tests/unit/simulation-rule-management-checks.mjs`：原 SIM 规则工作流回归保留；测试显式授予 feature gate 状态，不把它当成业务授权。

没有开启功能、添加权限、修改冻结发布 Schema、修改全局 protocol 或受保护导航权限。全局构建、Java测试与部署状态由 root 聚合。
