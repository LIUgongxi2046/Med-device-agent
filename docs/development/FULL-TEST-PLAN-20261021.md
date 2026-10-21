# 完整测试执行计划（2026-10-21）

工作模式：补充测试、执行测试与基于真实日志报告；源码修复属于用户本轮继续开发的范围。合成 API 工程检查与真实 AI 语义评测分别统计。

## 范围和当前状态

用户要求：“继续开发完，需要的api你自己先模拟，然后开始完整测试”。本计划检查了当前恢复目录的实际脚本、POM、package.json 和 Python 项目；编写本计划未执行测试。开发交付并冻结源码后执行下列分片，不能把计划、静态计数、历史成绩或模拟结果写成当前完整验收通过。

当前登记的源码表数为 **526 业务表 / 565 全表**。这只是源码清单预期，最终须由修复后的迁移检查和独立数据库验证重新核对，不是数据库实际状态。发现源码增长时同时记录新计数和摘要，不能硬改断言让测试通过。

检查时发现 724 个 Java 测试源码文件、100 个 tests 顶层 test_*.py、18 个 professional-runtime/tests、8 个 model-gateway/tests、1 个 document/tests、1 个 tests/deferred、10 个 TS contract 测试和 6 个 executor 测试文件。开发仍在进行，冻结时重新枚举；文件数不是执行用例数。

## 启动门禁与证据

1. 等 mock API、前端 mock、运行入口/迁移解析兼容修复全部交付；记录源码摘要、依赖锁摘要和每项修复内容。先执行新增 mock 自检，确认所有未知路由拒绝、未注入 provider 凭据、请求仅到 loopback，之后才启动完整分片。
2. 使用全新输出目录；不得覆盖历史回执，也不能复用恢复前 PASS。记录每条命令 cwd、开始/结束时间、退出码、stdout/stderr、用例统计、失败用例和输入摘要。收集时隐藏凭据。
3. 不调用真实模型/客户 API，不迁移真实数据库，不删除真实对象。隔离 DB、对象存储、队列与账户必须明确为本轮生成的 synthetic fixtures；不能把历史服务身份替换成伪造生产回执。
4. 未安装依赖、缺本地服务、缺真实历史原件属于 BLOCKED 或 NOT_RUN，不改为 skip 后宣布通过。若执行失败，保留首次失败；修复后重跑受影响分片，最终再确认源码摘要一致。
5. 不同时运行两个 Maven reactor，也不要一边 clean 一边读取 target。前端生成 document-frontend-structure.json 后才能启动消费它的 Java 测试。相同固定端口的浏览器脚本顺序执行。

准备命令（全部从仓库根目录执行；PY/JDK/Maven 采用环境准备人员确认的路径）：

```bash
cd /workspace/scratch/5e00f913e304/recovery-20261021/work
export ROOT="$PWD"
source "$ROOT/../test-environment-20261021/env.sh"
export PY="$ROOT/../test-environment-20261021/venv/bin/python"
export RUN="full-20261021-$(date -u +%H%M%S)"
export OUT="$ROOT/.implementation-work/$RUN"
mkdir -p "$OUT" "$ROOT/.implementation-work/java-temp" "$ROOT/.implementation-work/migration-check-tmp" "$ROOT/.build-work"
export TMPDIR="$ROOT/.implementation-work/migration-check-tmp"
export PYTHONPATH="$ROOT/contracts/python/src:$ROOT/model-gateway/src:$ROOT/professional-runtime/src:$ROOT/document/src:$ROOT/tests:$ROOT/scripts"
# Set MEDICAL_JAVA_HOME and MEDICAL_MAVEN to verified installed tools, not guessed paths.
export JAVA_HOME="$MEDICAL_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$(dirname "$MEDICAL_MAVEN"):$PATH"
export CHROMIUM_EXECUTABLE=/usr/bin/chromium
"$PY" --version; node --version; npm --version; "$MEDICAL_MAVEN" --version
find business -path '*/src/test/*' -name '*.java' | sort > "$OUT/java-test-sources.txt"
find tests professional-runtime/tests model-gateway/tests document/tests -name 'test_*.py' | sort > "$OUT/python-test-sources.txt"
find contracts/typescript/tests professional-executor/tests -type f | sort > "$OUT/node-test-sources.txt"
find scripts -name 'check_*.mjs' | sort > "$OUT/frontend-check-sources.txt"
```

工具准备更新：已提供 `../test-environment-20261021/env.sh`，Maven 3.9.11 已运行，目前使用 Java 21 compiler-module shim，而不是已验证 Java 17。必须单列实际 JDK 偏差，Java 17 兼容性只能在真实 Java 17 验证后声明。contracts 精确 Python lock 使用独立 contracts-venv，因为 tzdata 2026.4 与 gateway lock 的 2026.5 冲突；共享主 venv 测试不等于两个锁环境分别通过。三个 npm ci 已完成，仍需显式构建。

锁版本：Python >=3.12；Java 17；POM Surefire 3.5.5，默认测试 JVM 512 MiB；TS 5.9.3；web Vite 6.4.3 / Vue 3.5.26。以锁文件安装，npm ci 使用 ignore-scripts 后需显式构建 contracts。2026-10-21 环境准备已确认官方 npm/PyPI/Maven 可达且 /usr/bin/chromium 存在；依赖安装和完整 JDK/Maven 准备尚在进行。初始环境没有 Redis/Docker/数据库可执行程序，这不是后来准备成功与否的最终结论。

## 并发与顺序

- 第一波：P（Python 包）、T（TS contracts→executor）、F（前端域测试与构建）并行；M 的纯语法测试可在独立进程做，但原生 SQL/JAR 检查等待 Java 产物。
- 第二波：J（唯一 Java reactor）在 F 的 document fixture 完成后执行；P 的顶层 service tests 仅在隔离依赖确认后执行。
- 第三波：M（迁移/跨语言）等待 J 产物；B（mock 浏览器）等待 F 与 mock server 自检。它们可并行但不得共用会被重建的产物。
- 第四波：I（隔离完整服务联调与业务链）在上述通过和服务真实启动后进行。缺依赖时继续可运行分片，单独报告此层 NOT_RUN，不使用模拟成功替代真实联调。

## P：Python 原生包、根目录与 deferred 测试

包测试使用标准 unittest，不能只跑 tests/check_python_contracts.py 中列出的四个旧文件。

```bash
"$PY" -m unittest discover -s professional-runtime/tests -p 'test_*.py' -v > "$OUT/python-runtime.log" 2>&1
"$PY" -m unittest discover -s model-gateway/tests -p 'test_*.py' -v > "$OUT/python-gateway.log" 2>&1
"$PY" -m unittest discover -s document/tests -p 'test_*.py' -v > "$OUT/python-document.log" 2>&1
"$PY" -m unittest discover -s tests/deferred -p 'test_*.py' -v > "$OUT/python-deferred.log" 2>&1
```

单独运行每条并保存 exit code，不让前一条失败吞掉后续报告。顶层 tests 含真实服务 runtime 测试和 native parser 测试，不能无条件在继承生产配置的 shell 中 discover。隔离 fixture 门禁通过后完整运行：

```bash
"$PY" -m unittest discover -s tests -p 'test_*.py' -v > "$OUT/python-root.log" 2>&1
```

若门禁缺失，明确列出未运行文件而非选择性删除：先保留根目录完整清单，再逐项分配静态/模拟/服务依赖标签，已确认无外部调用的文件用 `-m unittest discover -s tests -p '具体文件.py' -v` 单独执行，最后对照清单每个文件均有结果或具体 NOT_RUN 理由。文档解析/OCR/转换器依赖不全时 document 包不是完整通过。Python deps 来自各 pyproject/requirements.lock、data-design/requirements.txt、tests/web-requirements.lock 和 tests/object-storage-requirements.lock，不能以最小 essentials 已安装推断完整依赖齐全。

补充发布包检查（前面的单元测试通过仍不替代）：

```bash
"$PY" scripts/check_python.py --check package --execution "$RUN"
"$PY" scripts/check_model_gateway.py --check package --execution "$RUN"
```

这两项依赖项目 wheel 缓存和可用 capture helper。缺缓存应记录明确的包装构建 blocker，不安装未锁定替代版后沿用通过声明。

## T：TypeScript 契约与 professional executor

```bash
(cd contracts/typescript && npm run build && npm run typecheck && npm test) > "$OUT/typescript-contracts.log" 2>&1
(cd professional-executor && npm run build && npm test) > "$OUT/typescript-executor.log" 2>&1
```

contract 的 build 必须先于 executor；file:../contracts/typescript 链接需验证实际解析到当前源码构建。executor peer dependencies 中 DeepSeek harness/cordis/dsh 包必须真实安装；不得通过同名假 SDK 伪装成功。单元测试中的 API fake 可以模拟返回，但必须记录为模拟。

完整 profile 初始化另外执行：

```bash
"$PY" scripts/check_professional_executor.py --suite > "$OUT/executor-profile.log" 2>&1
```

此命令还执行 check_professional_initialize.py 并要求 professional-bridge-result.json。只有该初始化路径确认使用 synthetic input 和本地 mock 后才运行；否则 profile 集成单列 NOT_RUN。profile/package.json 没有额外 npm test 脚本，不虚构该命令。

## F：前端 schemas、类型、生产构建与域/传输层

```bash
(cd web && npm run check:medical-schemas && npm run typecheck:medical && npm run build:medical && npm run build:prod -- --outDir "$OUT/web-production") > "$OUT/web-build.log" 2>&1
for name in native_image_simulation artifact_content document evaluation evaluation_transport medical_framework_guard model_configuration run simulation_configuration simulation_configuration_transport source_exact source_index source_reader source_upload task_plan task_workspace; do
  case "$name" in
    evaluation_transport|medical_framework_guard|simulation_configuration_transport) script="scripts/check_${name}.mjs" ;;
    *) script="scripts/check_${name}_frontend.mjs" ;;
  esac
  node "$script" > "$OUT/frontend-$name.log" 2>&1
  printf '%s\t%s\n' "$?" "$script" >> "$OUT/frontend-exit-codes.tsv"
done
```

每个 frontend 脚本均对应当前实际文件。document 脚本生成 `.implementation-work/document-frontend-structure.json`，保留该产物供 J。source_reader 等也生成本地检查产物。这里不包含 browser 两个脚本；它们归 B。此域测试没有替代真实页面、窄屏、键盘和视觉验收。

## J：完整 Java reactor

优先使用实际注册入口（含 frontend 前置构建和 JUnit/产物摘要）：

```bash
export MAVEN_OPTS='-Xms128m -Xmx1024m -Dfile.encoding=UTF-8'
"$PY" scripts/build_business.py > "$OUT/java-full-build.log" 2>&1
```

build_business.py 会追加 -Xmx512m；若当前总内存允许且再次触发堆 OOM，应由负责人员修复可配置内存再重跑，不把 OOM 算测试通过。其原生核心命令为（诊断用，不并行执行）：

```bash
(cd business && "$MEDICAL_MAVEN" --batch-mode --no-transfer-progress \
  --settings "$MEDICAL_MAVEN_SETTINGS" \
  -Dmaven.repo.local="$ROOT/.implementation-work/maven-repository" \
  -Dmedical.testJvmMemory='-Xms128m -Xmx512m' clean verify) > "$OUT/java-native-diagnostic.log" 2>&1
```

原生诊断命令不产生 build_business.py 的全部自定义 evidence，因此不能替代注册入口的完整验收。11 模块都须进入 reactor：medical-contracts、medical-data、medical-workflow、medical-persistence、medical-identity、ruoyi-admin、ruoyi-framework、ruoyi-system、ruoyi-quartz、ruoyi-generator、ruoyi-common。记录未到达模块；不能 Maven 前段失败后宣称全量完成。

复制全部 `business/*/target/surefire-reports/TEST-*.xml` 和 reactor summary 到本轮 OUT，再汇总 tests/failures/errors/skipped。零用例不算 PASS；skipped 独立披露。禁止使用 -DskipTests 或 maven.test.failure.ignore 作为成功门禁。若为诊断收集使用 failure.ignore，报告必须明确诊断而非通过。

重点：此前 2026-10-18 全量诊断有 1456 tests、1 failure、78 errors 且后续 heap failure/admin 未运行，绝不可当作当前 baseline PASS。旧 service secrets、对象清单、document-venv、.lld-data-work/venv、Temporal/Qdrant 等依赖必须确实具备或单列缺失；不伪造历史对象/批准事实。

## M：迁移完整解析、打包与跨语言

```bash
"$PY" tests/test_migration_bundle.py > "$OUT/migration-bundle.log" 2>&1
"$PY" tests/test_migration_cli.py > "$OUT/migration-cli.log" 2>&1
"$PY" tests/check_cross_language.py > "$OUT/cross-language.log" 2>&1
"$PY" scripts/check_published_schema_references.py --execution "$RUN" > "$OUT/published-schema.log" 2>&1
```

最后一条真实入口已核对；当前工具路径仍硬编码原 macOS JDK/Maven，等待 portability 修复，否则单列 BLOCKED（其 PublishedSchemaReferencesTest 也应在 J 的全量中执行）。跨语言依赖本次 Java snapshot `IC-03-JAVA-SNAPSHOT/current.json`，必须 hash/current-input 核验，不能读取历史 snapshot 凑结果。

**已知必须修复的门禁**：现有 migration bundle 只有 068/076/099 专项兼容；新增 CREATE TRIGGER 和 105 等 compound DROP CHECK/ADD CHECK 不能跳过。portability worker 正在补严格有界解析器。最终必须加入合法 trigger/compound ALTER 正例，以及损坏 SIGNAL/触发器正文、漏语句、未知 statement、变更 CHECK/列定义等拒绝反例。检查原字节不变、语句数完整、每个 master include 都解析、coverage 表集/tenant 列/不可变更新删除保护完整。不能把 sqlglot Command fallback 当 AST 验证成功。

数据库实际验证与静态解析分开。仅对本轮明确创建的 disposable MySQL 库，在确认真实 server UUID、库名、JAR 和 library SHA256、批准引用、备份/空库创建证据后：

```bash
# LIQUIBASE_COMMAND_URL/USERNAME/PASSWORD and MEDICAL_MIGRATION_* come from protected local synthetic setup.
"$PY" scripts/run_migration.py validate
"$PY" scripts/run_migration.py status
"$PY" scripts/run_migration.py update-sql
"$PY" scripts/run_migration.py update
"$PY" scripts/run_migration.py status
```

绝不填写猜测的生产 URL。不能用 MariaDB/H2 SQLite 自动代替 MySQL 并宣称 MySQL 迁移成功。验证空库全量迁移、再次执行幂等、触发器实际拒绝 UPDATE/DELETE、受控 CAS 仍可合法更新，以及升级路径；历史升级 fixture 不存在时该路径 NOT_RUN。

## B：mock 浏览器与页面验收

已有独立模拟页面入口：

```bash
node scripts/check_model_configuration_browser.mjs > "$OUT/browser-model-configuration.log" 2>&1
node scripts/check_request_metadata_frontend.mjs > "$OUT/browser-request-metadata.log" 2>&1
```

model_configuration 支持 PLAYWRIGHT_MODULE 和 CHROMIUM_EXECUTABLE，使用本地 18131；request_metadata 使用 18088/18089 且当前硬编码 Playwright import/channel:chrome，需 portability 确认修复或真实 Chrome 安装后才能运行。tests/browser package 声明 @playwright/cli，不保证顶层 playwright/index.mjs 路径存在。不可把 Chromium 存在写成浏览器已通过。

前端 mock worker 新增入口必须在交付时追加准确命令、端口、fixture 和页面覆盖列表，不猜测尚不存在的 runner。每个 mock 流程覆盖成功、空、加载、拒绝/跨租户、timeout/unknown、重复点击/幂等、重试/恢复；确认未知路由 fail closed，记录请求次数，无外部供应商调用。应覆盖 N01 作者→计划→确认，N02 等待/回答→新运行，N03 检查/人工批准，N04 正文候选→人工采用，取消/恢复、导出/保留、凭证和来源治理路径；未实现自动页面测试的流程明确列出。

保存 desktop/narrow viewport 截图、console errors、network evidence；检查键盘焦点/表单标签/错误提示。截图存在不等于像素验收，通过 mock 页面不等于真实服务端到端通过。

## I：隔离真实服务与完整业务链

`python scripts/check_full_regression.py --execution "$RUN"` 只登记 30 个既有运行检查，依赖应用部署、owner credentials、Redis 7.4.8、对象服务、固定浏览器与 capture helper，且自身将 business_acceptance/visual 记为 NOT_RUN，external/ai_evals 记为 BLOCKED。它不是完整项目测试入口，不能替代 P/T/F/J/M/B。

该入口仅在隔离服务准备和安全审查后运行；它会执行注册、邀请等写流程，不能指向客户环境。legacy `tests/browser/frontend-entry-checks.js OUTPUT http://127.0.0.1:18085` 还依赖实际 owner credentials 和 OUTPUT/production，不能直接以 mock token 伪装部署通过。其它浏览器脚本由各 `scripts/check_*_browser.py --execution "$RUN"` 包装器提供真实参数和 fixture，不裸跑缺参数脚本。

整链需实际 Spring HTTP + disposable MySQL/Redis + workflow/worker + synthetic object/version store + mock model/tools：验证一次请求固定输入、异步处理、原结果恢复、UNKNOWN 不重发、取消/重试边界、权限撤销、审计/来源链、独立人工批准与最后落库。无真实 provider 调用是本轮约束，不是缺陷；记录 MOCK_PROVIDER_E2E，不能写 PROVIDER_VERIFIED。并发/CAS、事务回滚、outbox 重复投递、Temporal history replay 分别留真实证据。

## 独立结论与停止条件

每一层结果只能是 PASS / FAIL / BLOCKED / NOT_RUN，包含实际命令和日志。完整结束需全部可运行分片完成、失败修复并重跑、任何未运行项逐条解释，用户可据此决定是否需要补环境。至少独立报告：

- 单元/契约、构建、静态迁移、mock API、mock 浏览器、隔离服务 E2E；
- MySQL 真实迁移、历史升级、Temporal replay、真实对象存储、OCR/Office/PDF 转换器；
- 真实供应商/客户系统联调、真实付费调用（本轮不执行）；
- AI 质量 evals、人工业务验收、视觉/窄屏/键盘可访问性、安全/负载测试。

禁止以某一层 PASS 推断其它层通过。源树测试未全执行时结论应为“已完成可执行范围，剩余明确受阻”，不能称“全部测试通过”。
