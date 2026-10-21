# Med-device-agent

医疗器械企业 Agent 平台，面向注册资料与项目知识管理。仓库包含产品需求、架构和详细设计文档，以及 Web 前端、Java 业务服务、Python 模型与文档服务、TypeScript Agent 执行器和共享数据协议。

平台支持企业与项目权限管理、资料和模板维护、任务计划、来源追溯、文档草稿生成、规则检查、人工审查与签发记录。法规适用性、业务结论和正式签发由获授权人员确认；模型输出作为待审内容，不能代替监管批准或专业审查。

## 项目状态

- [产品需求文档](PRD.md)当前标记为 `DRAFT`，真实客户业务验收状态为 `NOT_RUN`。
- [概要设计](HLD.md)与各详细设计文档说明目标架构和验证要求。标记为 `PROPOSED` 的技术决策仍需按文档完成验证。

## 主要模块

| 路径 | 内容 |
| --- | --- |
| `web/` | Vue 3、Element Plus 与 Vite 前端；医疗业务开发命令为 `npm run dev:medical` |
| `business/` | Java 17、Spring Boot 3 与 MyBatis-Plus 业务服务，基于 RuoYi 管理框架 |
| `model-gateway/` | Python 3.12 模型 API 网关 |
| `professional-executor/` | TypeScript 专业 Agent 执行器与 DeepSeek Harness 集成代码 |
| `document/` | Python 文档读取、扫描和导出组件 |
| `contracts/` | Python、TypeScript 共享协议与类型定义 |
| `agent-design/` | Agent 输入、结果与事件的 JSON Schema |
| `backend-design/` | 后端接口与资源 Schema |
| `data-design/` | 数据结构与资料处理设计 |
| `ui-delivery/` | 界面规范、设计依据和交付资料 |
| `scripts/`、`tests/` | 项目检查脚本与测试资料 |

## 本地运行前端

需要 Node.js 与 npm。首次安装依赖并启动医疗业务界面：

```bash
cd web
npm ci
npm run dev:medical
```

开发服务器监听 `127.0.0.1:18083`。医疗业务模式从本地 `web/.env.medical` 读取配置；该文件不纳入版本控制。联调后端时，设置以下配置并确保后端地址为项目 Vite 配置允许的本地地址：

```dotenv
VITE_APP_TITLE=医疗器械业务平台
VITE_APP_ENV=medical
VITE_APP_BASE_API=/api
VITE_MEDICAL_BACKEND=http://127.0.0.1:18080
```

前端类型检查命令：

```bash
cd web
npm run typecheck:medical
```

业务服务、模型服务与文档处理组件依赖相应数据库、缓存和本地服务配置。各模块的设计约束与验收要求见下方文档；凭据和环境配置应保存在本地，不要提交到仓库。

## 项目文档

- [产品需求规格说明书](PRD.md)
- [概要设计与架构决策](HLD.md)
- [Agent 编排与治理详细设计](LLD-Agent.md)
- [数据详细设计](LLD-数据.md)
- [后端详细设计](LLD-后端.md)
- [前端详细设计](LLD-前端.md)
- [项目研发规划](项目研发规划.md)
- [实施任务计划](实施任务计划.md)
- [测试与评测方案](测试与评测方案.md)
- [安全检查方案](安全检查方案.md)
- [架构审查记录](架构审查记录.md)

## 作者与职业介绍

作者：刘浩南（刘浩贤）。拥有12年医疗健康与产业互联网产品经验，曾任金域医学产品与解决方案部总监，目前从事独立开发，关注广州的 AI 产品总监、AI 产品负责人与 AI 产品经理机会。

- [职业介绍与代表案例](https://doctor-graph.com.cn/about/)
- [Med-device-agent 产品介绍与个人工作](https://doctor-graph.com.cn/products/med-device-agent/)
