# 前端制品与环境核验说明

核查日期：2026-10-14。对应FE-01/36/40/41/42。当前生产制品准备使用medical模式；目标SaaS、客户私有化地址、TLS、容量及接收人员仍需按原批准输入提供。

## 当前环境

正式工程为`web/`，使用原Vue Router、Pinia、Layout、Axios和Element Plus。`web/.env.medical`规定医疗标题、`VITE_APP_ENV=medical`及同源`/api`；当前开发代理仅接入已登记的`http://127.0.0.1:18080`，开发及预览分别使用127.0.0.1的18083/18084。构建资源基础路径当前为`/`。

正式页面、Cookie及API按后端同源发布方式提供。开发代理的Origin转换仅适用于当前本机入口，不能据此推定生产环境授权。客户子路径或独立域名需要对应部署定义，按完整路由、资源、Cookie、CSRF和刷新入口验证以后发布。

## 可执行制品检查

在项目根目录使用唯一执行身份，检查目录位于已忽略的`.implementation-work/`。

```bash
python3 scripts/check_frontend_continuation.py --suite release --execution 20261013-RELEASE-01
python3 scripts/check_frontend_continuation.py --suite document-selection --execution 20261013-SELECTION-01
python3 scripts/check_frontend_continuation.py --suite template-fields --execution 20261013-TEMPLATE-01
python3 scripts/check_frontend_continuation.py --suite source-rights --execution 20261014-RIGHTS-01
```

release执行医疗strict类型、当前计划、正文、完整上传结果、索引记录及EXACT字段参数检查、完整Vite医疗生产构建。组件集合分别执行实际Chrome开发及生产交互，不进行截图或视觉比较。现有capture_check.run_check固定当前源码、全部身份及持久模块API Schema、共享Schema、依赖及实际工具字节；检查前后输入变化或任意断言失败都会拒绝登记通过。回执保存原始命令、时间、输出、输入摘要及完整静态文件摘要。

来源权利及逐文件传输的实际检查使用`scripts/check_source_rights_frontend.py --execution <执行身份> --frontend-directory <完整制品目录>`。原生检查使用`--native-only`，核查正式页面入口、三个完整决定、授权原字节、原键重放、冲突、当前来源及操作重读、审计和Outbox及实际空索引清单。完整浏览器检查分别执行开发和生产，各自包括八项权利操作、三项真实上传/刷新/注销、三项固定索引读取/项目清理/刷新和十一项EXACT字段/别名/历史/恢复/撤权检查；全部输入明确标记SIMULATION。来源登记后的七格式Worker、非空索引、实际多页命中及专业采用仍需原完整业务验收。

该入口显式使用`medical-isolated-check` Maven Profile，以`medical.check.output-root`保存本项独立模块输出，防止其他构建改变正在验证的class。`--reuse-build-from`只接受本项已经创建的独立检查目录，使用Maven原生增量检查和完整依赖；新执行的日志、JUnit及回执仍保存到新目录。此参数只复用生成物，全部断言及实际接口流程继续执行，当前结果以本次输入和回执为准。

来源权利完整检查的`--timeout`接受600至3600秒，默认3600秒，覆盖完整编译、临时应用及两种浏览器环境。服务端对象读取租约、单项请求限制、权限检查和全部业务断言保持当前配置；实际租约过期、超时或失败继续拒绝登记通过。

实际项目流程采用`scripts/check_project_simulation.py --browser --frontend-directory`读取同一完整制品；当前MySQL、Redis、Java公开接口及真实Chrome共同执行项目创建、属性修订、四项状态决定、刷新和资料页面会话清理。模拟企业和项目数据由原检查入口创建并清理，正式客户验收独立登记。

项目检查的总执行时限默认为1200秒，`--timeout`接受600至3600秒，用于覆盖当前后端编译、应用启动及完整业务检查。`MEDICAL_BROWSER_MODE=development`及`MEDICAL_BROWSER_MODE=production`分别执行同一套完整浏览器断言，两次执行顺序进行。Java中的浏览器240秒时限和全部业务断言保持完整；任意超时或失败都不会登记通过。每次成功检查将实际JUnit复制到独立执行目录，逐字节核对原回执，固定项目结果、浏览器结果和输出，保存独立`receipt.json`。

`scripts/verify_frontend_continuation.py`通过`--selection`、`--template`和`--release`接收三份前端回执，通过重复`--project`接收项目回执，通过`--output`指定项目检查目录中的新结果文件。工具逐项重读全部输入与产物，核对公共输入摘要、开发及生产检查数量、唯一项目请求身份、实际业务重读和临时资源清理；只有开发及生产各八项项目检查与全部组件结果有效时才输出`FRONTEND_CONTINUATION_CURRENT`。相应执行身份及结果由[IMP-37](development/IMP-37.md)维护。

单独构建采用以下明确命令；此命令只有工程构建结果，不包含全部业务验收。

```bash
npm --prefix web run typecheck:medical
npm --prefix web run build:prod -- --mode medical --outDir ../.implementation-work/FRONT-MEDICAL-RELEASE/production
```

## 发布与升级核验

| 核验对象 | 接收证据 |
| --- | --- |
| 当前源码及依赖 | 回执中的完整输入、当前Git提交及未提交内容范围、package及锁文件 |
| 静态资源 | 正式index.html、直接引用及按路由加载的JS/CSS、字体和原件阅读依赖的实际HTTP字节 |
| 正式入口 | 登录、项目、资料、任务、模板、预算和场景的实际深链及刷新结果 |
| 身份与业务 | 同源Cookie、CSRF、当前会话、实际动作及业务对象重新读取 |
| 升级与缓存 | 新入口和资源属于同一固定制品，当前页面重新核验版本，旧上下文内容清理 |
| 完整业务范围 | 原57项AC、17项NFR、18项EDGE、30项SEC及12项EVAL对应结果；独立场景按其完整规格核查 |
| 视觉与接收 | 视觉保持NOT_RUN；接收人员、岗位、目标环境及批准操作分别记录 |

固定制品通过以后由后端部署责任人员执行已批准发布，核对实际应用和HTTP资源身份。当前前端工程回执不能独立证明目标部署、专业资料生成、正式签发或客户接收完成。

私有化环境保留全部产品功能。维护人员需要获准访问地址、资源基础路径、浏览器矩阵、模型出口、维护权限及升级恢复要求；每项参数由客户环境提供。服务岗位按[前端操作说明](frontend-operation-guide.md)和实际授予职责完成接收，各自保存请求及成果记录。

## 增量打包时的静态资源边界

`ruoyi-admin` 的 `generate-resources` 阶段使用固定版本 `maven-clean-plugin:3.4.1`，只清理 `${project.build.outputDirectory}/static`。该执行设置 `excludeDefaultDirectories=true`，不清理其余 `target` 内容，不跟随符号链接，也不触碰 `src/main/resources`。随后正常 `process-resources` 从 `medical.frontendDirectory`（默认 `web/dist`）复制当前资源，避免前一次构建的旧哈希文件被继续打入 JAR。不得在此生成目录对应的 `src/main/resources/static` 中混入手工资源；需要变更所有权时先修改并验证构建约定。

配置约束检查：`python -m unittest tests.test_frontend_build_cleanup`。

离线增量回归：`python tests/check_frontend_package_refresh.py --maven <mvn路径> --settings <settings.xml路径> --repository <已填充Maven缓存> --evidence <新的证据目录>`。先准备真实 `web/dist`；脚本在临时资源副本上连续执行两次普通 `package`，验证删除和重命名的资源不会残留，验证生成目录以外的哨兵及所有手工资源保持不变，然后用真实 `web/dist` 再次打包并核对 dist、生成目录和 JAR 的完整文件集合及哈希。它不执行数据库迁移或启动应用，不替代实际环境验收。

插件参数参考：[Apache Maven Clean Plugin](https://maven.apache.org/plugins/maven-clean-plugin/clean-mojo.html)。
