# FRONT-WORKBENCH-01 医疗业务工作台

## 任务身份

| 字段 | 内容 |
| --- | --- |
| task_id / 标题 | FRONT-WORKBENCH-01 / 医疗业务工作台 |
| 需求与验收 | 交互原型S01；PRD FR-01、FR-04、FR-07；AC-01、AC-07、AC-14、AC-15 |
| task_source | [实施任务计划](../../实施任务计划.md) IMP-06，整体状态IN_PROGRESS |
| 状态快照 | 本项代码IMPLEMENTED；业务验收仍随IMP-06、IMP-29与IMP-37执行 |
| 代码与环境 | `main`提交`2fb17df`及当前未提交工作台修改；Vue 3、Element Plus、现有Axios与Pinia |
| 文档有效性 | CURRENT；last_verified=2026-10-11；适用于当前源码和本次工程检查 |
| 检索字段 | S01、工作台、PJ-01、PJ-06、场景目录、index.vue、listWorkItems |

## 检索与输入依据

读取PRD§8.1、§8.4、§8.7，《交互原型说明》S01，LLD-前端S01/FRONT-Q17，LLD-后端PJ-01/PJ-06，IMP-04/06/12/29开发记录及当前项目、待办、任务和场景接口。现有默认首页仅显示若依介绍；项目、待办、项目明细和场景目录已有实际公开接口，可以独立完成工作台读取。

## 当前功能与实现

用户进入默认首页后，页面读取当前企业上下文内的项目、当前人员待办和场景目录。顶部展示获授权项目、当前人员待办、重点项目任务与活动运行数量；今日更新待办提供项目入口；重点项目展示任务、运行和待办数量；六个场景入口由注册申报资料入口与服务端返回的五项规划要求组成。

页面只在具备相应动作许可时读取项目或待办，企业上下文、页面可见性或身份变化会中止原请求并清理原内容。项目数量及项目明细来自PJ-01，当前人员待办来自PJ-06，场景规划来自CP-07；页面不创建业务记录。

| 文件路径 | identifier或接口 | 当前职责及本任务相关内容 | 调用方与关联流程 |
| --- | --- | --- | --- |
| web/src/views/index.vue | Index | S01页面、并发读取、今日待办、项目进度及六场景入口 | 登录后的默认首页 |
| web/src/router/index.js、web/src/views/login.vue、Sidebar/Logo.vue | /index | 医疗模式路由、登录目标及主页入口 | 登录、侧栏标识 |
| web/src/api/medical/projects.ts | listWorkItems | PJ-06响应Schema、权限、范围与游标核查 | Index |
| web/src/api/medical/protocol.ts | Waiting / WaitingListing | 登记项目Schema中的待办响应 | listWorkItems |
| business/medical-persistence/.../ProjectController.java | GET /api/work-items | 当前人员待办公开入口 | listWorkItems |

## 决策与约束

工作台复用既有项目、待办和场景接口，不新增汇总接口。重点项目采用当前项目列表中的最近六项，并读取各项目明细取得任务、运行和待办数量。今日待办按浏览器当前日期筛选服务端返回的WAITING_INPUT事项；顶部仍显示全部当前人员待办数量。

注册申报资料为当前可进入的主要业务入口，另外五项名称和用途读取服务端规划要求。规划中场景统一进入场景目录查看缺项，不提供正式任务入口。

## 验证与证据

| 验收ID / check_id | 场景与前置条件 | 实际命令或操作 | 预期断言 | 真实结果与状态 | 版本及证据路径 |
| --- | --- | --- | --- | --- | --- |
| FRONT-WORKBENCH-RED-01 | 修改前默认首页 | `rg`查询工作台标题、今日待办、重点项目和场景入口 | 查询无匹配，证明S01页面缺失 | PASS；查询无匹配 | 修改前`2fb17df` |
| FRONT-WORKBENCH-TYPE-01 | 当前源码与本地依赖 | `cd web && npm run typecheck:medical` | TypeScript通过 | PASS；exit_code=0 | 当前工作区，Node v24.13.0 |
| FRONT-WORKBENCH-BUILD-01 | 当前源码与本地依赖 | `cd web && npm run build:prod` | 正式前端构建通过 | PASS；3048个模块完成转换，exit_code=0 | 当前工作区，Node v24.13.0 |
| FRONT-WORKBENCH-ROUTE-01 | 医疗模式默认入口 | 核查`/index`路由、登录目标及侧栏主页 | 三处入口统一进入工作台，路由触发医疗会话读取 | PASS；源码匹配并通过正式构建 | 当前工作区 |
| FRONT-Q17 | 获授权企业、项目和场景数据 | 待正式数据执行 | 项目、待办及场景状态可核查 | NOT_RUN | IMP-37 |

## 关联影响与后续使用

工作台使用现有会话、项目、待办和场景读取能力。共享`protocol.ts`新增两个已经发布的Schema引用，所有医疗页面继续使用同一Ajv实例。医疗登录与侧栏主页均进入`/index`，路由在医疗环境触发现有会话读取。后续任务可以在不改变首页数据源的情况下补充评测和客户连接摘要。

## 未完成内容与继续条件

本项代码与工程检查已经完成，状态登记IMPLEMENTED。合法客户企业中的完整页面操作、键盘、窄屏和数据更新验收继续IMP-37；场景正式任务入口继续IMP-29及IMP-31—35。
