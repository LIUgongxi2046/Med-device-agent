# LOCAL-RESTART-20261021 本地同步与启动

## 任务身份与依据

本记录管理用户在2026-10-21提出的代码同步、重新接管及启动要求，以及2026-10-22提出的再次启动要求，关联IMP-03/36。本地同步与启动范围状态为ACCEPTED，文档CURRENT，last_verified=2026-10-22T10:40:25.580471+00:00。源码基线为main的b0a3591，保留当前工作区全部未提交修改。任务状态以本记录为准，原产品任务状态继续由实施任务计划维护。代码及记录尚未提交Git。

读取README、前后端交接、CONTINUATION-20261019、接管开发记录、IMP-36、RUN-AUTHORITY-LOCAL-CONFIG-01及现有启动脚本，核查实际进程、配置和数据。用户已明确授权完整备份并执行本地数据库迁移。

## 当前运行方式

采用已有本地进程、受保护配置、原有MySQL/Redis/SeaweedFS/Qdrant/Temporal持久数据。凭据保持在.implementation-work/runtime，本文不记录秘密值。前端监听127.0.0.1:18083，后端使用127.0.0.1:18080。

scripts/local_object_storage.py的verify_process_identity在macOS使用en_US.UTF-8读取ps的uid、comm和args，完整比较实际进程所有者、可执行路径及参数。scripts/local_runtime.py的verify_application_deployment在macOS采用相同编码读取完整command，并核验固定Jar与附加配置摘要。Linux身份检查保持原有行为。验收LOCAL-RESTART-IDENTITY使用实际SeaweedFS及Java进程与中文项目目录，两项身份检查均已通过。

FrameworkFormController支持Spring生成方法权限代理，五项PreAuthorize注解及原有权限核查完整保留。FrameworkFormProxyTest通过实际Spring ProxyFactory与AuthorizationManagerBeforeMethodInterceptor验证代理创建。

TaskFieldTargetConfiguration的输入能力服务及输入权限组件采用medical.formal-agent-publication.enabled条件；TaskFieldTargetController接收可选输入能力服务，相关操作在服务缺少时抛出DEPENDENCY_UNAVAILABLE，由现有错误响应组件返回503。普通任务字段映射、准备与原请求恢复继续采用原服务。本地正式发布开关保持原值，来源及专业Worker沿用已有配置。

## 同步与初次启动记录

| 验收 | 实际验证 | 当前结果 |
| --- | --- | --- |
| LOCAL-RESTART-SYNC | git fetch及fast-forward同步 | main已更新至b0a3591 |
| LOCAL-RESTART-FRONT | npm run typecheck:medical、npm run build:medical | 613项固定引用、类型及生产构建通过；前端HTTP入口已响应 |
| LOCAL-RESTART-DATA | 实际MySQL查询及Liquibase validate/status/update | validate通过，734项迁移全部成功，累计1028项 |
| LOCAL-RESTART-PROXY | 实际Spring权限代理测试 | 1项通过，失败、错误及跳过均为零；日志restart-20261021-final-build.log |
| LOCAL-RESTART-BUILD | Maven正式依赖安装与当前应用package | 11项模块通过，7分36秒；该命令未执行测试，日志restart-20261021-availability-build.log |
| LOCAL-RESTART-IDENTITY | local_object_storage.py identity及verify_application_deployment | SeaweedFS与Java的完整路径、参数及摘要核查通过 |
| LOCAL-RESTART-VECTOR | local_vector_storage.py verify | 实际Qdrant1.19.2连接通过，匿名401 |
| LOCAL-RESTART-TEMPORAL | local_temporal.py start及status | SERVING，原medical-local Namespace保持 |
| LOCAL-RESTART-HTTP | 当前应用启动日志、原账号认证、前端与后端HTTP | Started MedicalApplication，启动159.036秒；前端首页及后端登录均200 |
| LOCAL-RESTART-AUTH | 实际Cookie、CSRF、登录、注销和权限检查 | 登录302、会话200、匿名401、无表单权限403、注销204及原会话401 |
| LOCAL-RESTART-OPTIONAL | 实际已有任务的输入能力读取 | 正式发布依赖未启用时503及DEPENDENCY_UNAVAILABLE |
| LOCAL-RESTART-ASSETS | 当前Jar与web/dist完整文件比较、实际HTTP与前端代理 | 543份静态文件集合及字节一致，后端登录HTML一致，前端API代理正确返回401 |

最终检查命令为`.implementation-work/object-storage-venv/bin/python .implementation-work/restart-20261021-verify.py`，退出状态0。完整证据为`.implementation-work/restart-20261021-verification.json`，日志为同前缀verification.log。检查使用原有本地账号及真实服务，原账号可读取12项企业成员关系；没有生成业务测试数据。

数据库为medical_local，server_uuid为5ecfb746-be18-11f1-95e4-d822203bc633；升级后24个企业、36个成员、36个项目、60项任务、42条操作记录与升级前一致。原有294项迁移身份及checksum逐项保持，当前1028项迁移及568张物理表。证据为.implementation-work/restart-20261021-database-verification.json；原始核查与构建日志使用.implementation-work/restart-20261021前缀保存。

完整迁移前备份位于.implementation-work/runtime/migration-backups/3c2a1586-af15-47bc-9891-e186bba94133.sql.gz，SHA-256为6e556d516e2dd30902d82e248747135b9d0e088bef37e4724d9a8550609ada60。摘要与完整gzip读取均已核验，解压后3865678字节。

当前附加配置位于.implementation-work/runtime/application-profiles/c48e8f85-7410-443c-9b28-8416b86caf24/application-workspace.yaml，包含工作站验收服务及FONT、FORMAT、DEPENDENCY模板制品读取定义，保留来源消费者配置及配置目录的独立分页游标密钥；本地证书及签发配置位于.implementation-work/runtime/run-authority/restart-20261022。证书有效期24小时，CRL有效期12小时，与既有本地策略相同；当前证书有效至2026-10-23 08:51:49，CRL有效至2026-10-22 20:51:49，时间均为Asia/Shanghai。服务身份与令牌发行者保持原登记值。配置目录运行记录见[全局组织选择](GLOBAL-ORGANIZATION-20261021.md)。当前附加配置回执为.implementation-work/runtime/acceptance-public-template-profile.json。

当前前端npm进程PID为90383，独立会话由系统继续管理，监听进程PID为90400；后端PID、部署身份、Jar及附加配置摘要由受保护的.implementation-work/runtime/application-deployment.json登记，本机启动入口核查实际进程及文件摘要。当前14项后端回归及完整构建通过，最新生产前端已经打包，证据为.implementation-work/acceptance-final-backend-verification.log及acceptance-final-front-build.log；当前超管权限、业务测试及页面核查见[本地工作站功能验收](WORKSPACE-ACCEPTANCE-20261022.md)。

当前运行核查为READY，后端PID40953，552份前端文件与运行Jar逐项一致；18080、18083、MySQL及Redis检查通过，证据为.implementation-work/acceptance-final-runtime-health.json。当前业务数据库具有1029项迁移、568张基础表，实际验收资料及适用范围见ACCEPTANCE-DATA-20261022.md。

2026-10-22启动复验：前端/index返回200；当前后端实际超管liuhaonan登录跳转后200、会话200及PLATFORM_ADMIN、注销204；原medical_local数据库server_uuid保持一致，24个组织、36个项目、60项任务及1028项迁移记录保持完整。命令为`.implementation-work/object-storage-venv/bin/python .implementation-work/restart-20261022-check.py`，退出状态0，证据为.implementation-work/restart-20261022-verification.json。真实无界面Chrome156.0.8078.47完成两次表单登录、首页、品牌及导航检查，页面错误与请求失败均为空，证据为.implementation-work/login-brand-browser.json，日志为.implementation-work/restart-20261022-login.log；浏览器检查未使用截图。前端启动日志为.implementation-work/restart-20261022-frontend.log，后端启动日志为.implementation-work/runtime/application.log。

## 运行入口

| 服务 | 本地端口 | 持久数据 |
| --- | --- | --- |
| 前端 | 18083 | web源码及本地.env.medical |
| 后端 | 18080 | .implementation-work/runtime/applications |
| MySQL | 13306 | .implementation-work/runtime/mysql-data |
| Redis | 16379 | .implementation-work/runtime/redis-data |
| SeaweedFS S3 | 18333 | .implementation-work/runtime/object-storage/data |
| Qdrant | 16333/16334 | .implementation-work/runtime/vector-storage |
| Temporal | 17233/17234/17235 | .implementation-work/runtime/temporal-dev/server.db |

后端维护使用当前项目目录及已有私有配置：

```bash
.implementation-work/object-storage-venv/bin/python scripts/local_runtime.py stop-application
.implementation-work/object-storage-venv/bin/python scripts/local_runtime.py application --additional-config .implementation-work/runtime/application-profiles/c48e8f85-7410-443c-9b28-8416b86caf24/application-workspace.yaml
```

前端通过`.implementation-work/object-storage-venv/bin/python .implementation-work/start-local-frontend.py`启动独立会话，运行web目录的npm run dev:medical；进程记录位于.implementation-work/runtime/frontend-deployment.json，当前日志为.implementation-work/restart-20261022-0938-frontend.log。2026-10-22 09:38再次验证/index返回200、真实超管登录与会话200、注销204及原有数据库身份和数量；证据为.implementation-work/restart-20261022-0938-verification.log和.implementation-work/restart-20261022-verification.json。本机登录凭据继续保存在.implementation-work/runtime/owner-credentials.json。

## 后续业务范围

本次本地同步、数据库升级、服务启动及所列检查已经完成。专业任务、供应商调用及客户完整验收采用原任务要求，启动检查不改变其状态。仓库全量测试及视觉检查本次未执行。
