# 正式工程来源与目录

日期：2026-10-02；任务：IMP-01。用户授权在当前文件夹新建本地仓库；产品建设继续依据既有完整实施授权和设计指定的若依组件。当前项目根目录为唯一 Git 仓库，分支 main；正式业务代码直接维护在以下目录。

| 逻辑目录 | 正式路径 | 来源与版本 | 当前职责 |
| --- | --- | --- | --- |
| business | business/ | 官方 RuoYi-Vue springboot3；commit a51a838b71b446ea27256900efe7ed2faa2a02fd；3.9.2；MIT | Spring Boot 3.5.16 / Java 17；六个原模块及项目 medical-contracts、medical-data、medical-persistence、medical-identity 模块 |
| web | web/ | 官方 RuoYi-Vue3 master；commit 838965c5a18d2c61b73ec30c6e288057aaa08b63；3.9.2；MIT | Vue3、Router、Pinia、Element Plus、Vite、既有组件 |
| runtime | runtime/ | 项目专业 Worker；待实施 | Java Temporal Worker、Python Activity 与 Harness profile |
| document | document/ | medical-document 1.0.0 原生解析组件；正式安装制品与十一项检查通过，完整 Worker 待实施 | 七格式只读原件、固定 Locator/parse_version、原文及参数摘要；完整上传处理与固定成品转换继续原工单 |
| contracts | contracts/；Java 在 business/medical-contracts/ | 项目 Schema 发布集合 1.0.0；Java/Python/TypeScript 类型及运行绑定已实施；Python 使用 Pydantic 2.13.5，共享 wheel 已构建及安装验证 | 五份唯一 Schema 的216项定义、离线校验与跨语言表示；三种语言均包含登记13项、模型25项、资源详情27项、完整接口102项及产物49项类型与绑定、后端设计的完整68项错误枚举及 Schema 拒绝，公共17项复用；目标服务消费待完整验证 |
| migration | business/medical-data/；scripts/run_migration.py | 项目迁移制品3.9.2；BOM 管理 Liquibase 4.31.1；独立 CLI 使用 picocli 4.7.6、Connector/J 9.7.0 | 原始核心DDL及逐项补充changeset；135张企业业务表和13张平台表，共176项changeset；十四项结构检查、四项成员身份及七项邀请/角色真实元数据检查通过；真实本地MySQL已执行；生产目标、历史迁移及恢复尚未验收 |
| persistence | business/medical-persistence/ | 项目模块3.9.2；MyBatis-Plus 3.5.17、JSQLParser 5.2、MyBatis 3.5.19、MyBatis-Spring 3.0.5 | 业务 Mapper 使用 medicalSqlSessionFactory，共享 DataSource 和 Spring 事务；企业条件、无缓存及主数据库水位入口；六项本地拒绝和配置检查通过，真实授权与 SQL 执行未验收 |
| identity | business/medical-identity/ | 项目模块3.9.2；Spring Security 6.5.11、Spring Session 3.5.7、commons-codec 1.18.0、Spring JDBC 6.2.19；Nimbus JOSE JWT 显式固定9.37.4，与实际 Security JOSE 依赖一致 | 独立 OIDC、PKCE/nonce、Redis 会话、CSRF 与 Origin、平台身份和应用会话目录、主数据库状态查询及注销 Token 验证组件；真实 SSO、签名 Token、SQL、会话撤销、成员和对象授权未验收 |
| deploy | deploy/ | 项目运行配置；待实施 | 受控环境、监控、备份与恢复 |
| tests、docs | tests/、docs/ | 当前项目 | 可执行检查及独立开发记录 |

源码通过固定提交的 Git archive 取得；上游 bare 仓库与归档位于已忽略的 .implementation-work/upstreams/。business 与 web 不包含独立 Git 元数据。两份 LICENSE 保留原版权与许可。仓库来源由项目记录维护，不将上游研究版本登记为客户既有系统版本。

前端直接复用 web/src/layout、router、store、utils/request.js、components/Editor、FileUpload、Pagination 与 RightToolbar。Editor 当前使用 @vueup/vue-quill 和 HTML 内容接口；结构化正文扩展由 IMP-16 按实际能力核查维护。现有 request 使用的身份、幂等、日志和浏览器缓存行为由 IMP-04/05 按已定义安全规范接入。

前端采用npm，固定依赖并维护package-lock.json；Axios 1.20.0、js-cookie 3.0.8、Vite 6.4.3、ECharts 6.1.0、@spiriit/vite-plugin-svg-spritemap 6.0.0及SVGO 4.1.0已经按官方资料核查。SVG图标使用独立构建资源及现有icon-身份，ECharts使用官方v5主题及现有pie/gauge参数。当前官方npm audit对完整锁文件报告零项已知安全问题，回执IC-01-WEB-SEC-20261002-03；该结果限定于npm依赖检查。固定锁文件已通过npm ci；当前原生构建和六项真实Chrome开发/生产兼容检查由IC-01-WEB-COMPAT-20261002-10绑定385项输入及460项产物。锁文件进入本地版本管理；后续安装使用npm ci。Java工程采用原Maven多模块结构和clean verify；工具及实际构建回执在IMP-01主记录与commands.md登记。

数据库、身份、模型、目标部署及 SDK/runtime 的生产发布资格继续分别验证。取得源码、依赖安装或编译成功不能关闭业务验收。

Python 共享库使用 contracts/python/pyproject.toml 和 requirements.lock。构建组件固定 setuptools 80.9.0；Pydantic 2.13.5、pydantic-core 2.46.5、annotated-types 0.8.0、typing-inspection 0.4.4 已安装。测试依赖固定 markdown-it-py 4.0.0 / mdurl 0.1.2。scripts/build_python.py 构建实际 wheel，安装后使用 Python 隔离模式核对十二份源码摘要并执行31项检查；该制品属于共享库，Python Worker 和 SDK/runtime 仍待实施。

document 使用 `document/pyproject.toml`、Python 3.12 与 setuptools 80.9.0，原生解析制品为 medical-document 1.0.0；依赖 Docling 2.73.1、docling-parse 4.7.2、python-docx 1.2.0、pypdf 6.9.2、openpyxl 3.1.5、pandas 2.3.3、python-pptx 1.0.2 与 markdown-it-py 4.0.0。当前运行目录为 `.implementation-work/document-venv`，成熟依赖按正常安装流程取得，安装源码与项目源码字节相等，十一项原件处理检查通过。实际依赖、配置摘要、许可使用范围和完整待办由唯一 IMP-08 维护；正式 Worker、完整七格式内容、上传持久化及原件阅读继续验收。
