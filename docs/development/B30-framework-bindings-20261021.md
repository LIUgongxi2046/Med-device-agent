# B30 框架复用实现增量（2026-10-21）

本增量只编写源代码与延期测试，未启动服务、执行管理动作、构建、测试、迁移、调用外部服务或推送。不能作为十八模块正式验收完成证据。

## 原实现与实际接入

原用户、角色、菜单、部门、岗位、字典、参数、通知、操作日志、登录日志 Controller 和 Vue 页面保持，PLATFORM_ADMIN、当前会话及原 PermissionService/PreAuthorize 继续生效。菜单不授予动作权限。补齐十八模块和五个原明细页的服务器深链页面入口，仍由当前服务端菜单与路由权限注册决定可访问组件。

新增正常路径消费者：

- 在线用户：`/api/monitor/online/list` 使用实际 Spring Session 及当前账户凭据/角色/会话账本；注销使用短期、当前操作者会话绑定的不透明引用，先提交权威撤销再删除 Redis，未知清理结果不冒充成功。
- 缓存：原 `/api/monitor/cache` 查询、名称、键、值、清理路径只允许字典及两项界面参数；不允许会话、密码、限流、预算、授权等缓存，也不允许清空全部。页面隐藏全局清理，服务端仍拒绝。
- 服务监视：`/api/monitor/server` 提供实际 JVM 计数，未采样 CPU 百分比为 null；主机地址、启动参数、安装和项目路径不返回。
- 连接池：`/api/monitor/druid` 提供池计数，不提供 Druid SQL 控制台或连接串。医疗模式页面替换为只读计数，非医疗模式保留原页面。
- 定时任务：`/api/monitor/job/list`、`/{jobId}` 及 `/api/monitor/jobLog/list` 读取原表，参数白名单/有界分页，调用表达式、执行内容和异常详情脱敏。原任意反射任务及清理日志不对医疗模式开放；另有固定已注册处理器的创建、修订、退休全生命周期与有界脱敏导出。
- 接口文档：`/api/tool/swagger` 从实际注册的正常方法和原权限投影只读列表，不提供携带会话凭据的任意调试。非医疗模式原 Swagger 页面不变。
- 表单构建：原组件仅在 DEVELOPMENT/DESIGN、developer 角色及原权限成立后显示；原生成器仅用于本地设计，不等同受控发布。另有正常页面受控 Schema 草稿、独立批准与发布消费者，固定五类声明式控件，不执行 JS/HTML；真实 artifact/version_registry/BX25 绑定缺失时不开放发布。
- 代码生成：显式启用时接入原 Controller、Mapper、Service，要求 DEVELOPMENT、developer 和原权限；独立 `framework_dev_*` 数据库、独立数据库用户及事务模板。元数据导入/修改/删除/同步沿用原逻辑。预览和 ZIP 下载走当前医疗 Cookie/CSRF/身份响应核查。任意 SQL 创建和服务端路径生成拒绝，非医疗原实现未改。

## 已发布任务命令

`medical.framework.job-dispatch.enabled=true` 才实例化命令入口，还必须提供专用 `frameworkApprovedScheduler`、真实 `FrameworkJobRegistration` 及实现 `FrameworkRegisteredJob` 的已发布编译类。不存在默认任务或反射表达式回退；既有 `sys_job.invoke_target` 必须等于固定 `registered:<handlerKey>`，Scheduler 中 JobDetail 类型、键及参数也要匹配。源代码不会自动导入原有启动时清空/重建调度器的服务。

PUT `/api/monitor/job/run` 和 `/changeStatus` 需要原权限、当前身份、真实已批准 BX25 binding 及 Idempotency-Key。175 是平台管理账本，不是企业业务表。保存操作者、原请求摘要、绑定摘要、Schema 字节摘要、分类和不可变原响应。相同键返回原状态，不重复派发；同一任务已有 RESERVED/UNKNOWN 时阻止新命令。明确区分调度器接收与任务执行完成，未知结果记录 UNKNOWN。

GET `/api/monitor/job/commands/{id}` 读取本人的命令；POST `/{id}/observations` 只核对原 Scheduler 状态，不重新执行，只有正向证据才把未知接收结果改为 DISPATCHED，缺失 trigger 不等于失败或未执行。暂停/恢复不宣称能中断正在执行的任务。

## 配置与剩余边界

- generator 默认关闭。启用还需 `medical.framework.generator.datasource.url/username/password` 指向独立开发库；用户尚未提供实际环境或授权，代码不创建数据库、凭据、任务和发布批准。
- job dispatch 默认关闭。提供经真实发布流程批准的具体业务 handler 和 Scheduler 是部署输入，不可凭空造假；执行完成证明由实际 handler 运行记录提供，本接收账本不把接收当完成。
- BX25 清单生产、独立批准和运行消费由 FrameworkBinding* 增量维护，原 v1 合同不改。缺失原 source_commit 时不能伪造提交；恢复源文件摘要与真实声明身份分别记录。
- 十八模块逐动作合同、原生参数/JSON 严格门禁、受控表单发布、注册任务全生命周期及正常页清单编制入口已编写。医疗模式明确排除任意 SQL/服务器路径生成、反射调用、全缓存清空及日志永久清理；不能因此声称所有原生动作可用。非医疗原功能保留。
- 任务前端冻结请求和幂等键，UNKNOWN 时禁止替换并支持同键重送/查询/只读核对。暂停/恢复仅在全部实际 trigger 状态有正向证据时事务核对状态；无 trigger 继续 UNKNOWN。运行触发器消失也不推断未执行。
- 医疗原生审计保留操作、操作者、目标、状态和安全投影摘要，参数值/通知正文/历史请求响应/异常消息均不直接记录；用户编辑密码字段禁止绑定，岗位选项原权限补齐，登录解锁 GET 强制 CSRF，生成列路径与归属校验补齐。
- 延期测试：FrameworkManagementBoundaryTest、FrameworkMonitorBoundaryTest 及 FrameworkBinding* 测试均 NOT_RUN；javac/Maven 在当前环境不可用，不宣称通过。

## 延期验证场景（NOT_RUN）

- 丢失 RUN 接收响应后刷新页面，恢复同一 actor 的冻结命令，重送保持原 UUID，Scheduler 仅接收一次；未知命令不能被新动作覆盖。
- 创建/修订响应未知时全部表单字段冻结，查询或观察没有正向证据不能解除；换用户不得读取原命令回执。
- PAUSE/RESUME 所有实际 trigger 状态一致才观察恢复；无 trigger、异常、状态分歧仍保持 UNKNOWN；观察不触发任务。
- 原生 DTO 拒绝额外字段、用户 edit.password、generator 跨表列 ID、伪造 dataScope；登录解锁 GET 缺失 CSRF 被拒绝。
- configValue、noticeContent、历史 operParam/jsonResult/errorMsg 和密码改变不进入日志正文或秘密值摘要；审计目标/动作/操作者仍可核查。
- 十八模块原路径、相对导出路径、五个明细深链、医疗与非医疗模式分别核查；没有发布批准/真实配置不得假装运行可用。
