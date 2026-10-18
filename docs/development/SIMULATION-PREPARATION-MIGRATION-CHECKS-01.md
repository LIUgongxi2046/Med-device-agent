# 063 / 065 离线迁移契约验证

## 覆盖

- 新增 `tests/test_simulation_preparation_migration.py`：使用既有 Java `InspectAlterMigrations`（JSqlParser）解析真实 065，再由 SQLGlot 读取完整 CREATE AST。验证独立一表、064 后唯一登记、tenant 主键/operation 唯一键、artifact/source/rights/creator 精确复合外键、完整非空正文与双层请求摘要、固定种类/版本/分类和 JSON_VALID 约束。
- Mapper 以实际 XML 为输入，仅把 MyBatis `#{...}` 转成 SQL parser placeholder；拒绝 `${...}` 拼接。检查 insert/select 完整字段、构造结果顺序、正文与摘要绑定、无 update/delete、企业级 artifact 的 NULL project、SOURCE 绑定、本人 operation 查询与原 key、源版本锁及缓存关闭。
- 静态核对 mapper 已进入现有 TenantScope 路径，准备表列入既有 immutable-table guard。Mapper 省略 tenant 值由服务端 interceptor 提供，测试不会把省略误判为跨租户授权，也不把源代码检查冒充运行时租户隔离验证。
- 扩展 `test_simulation_input_artifact_migration.py`：063 原 `request_sha256` 与新增 `request_body_sha256` 同时保留，canonical_request_json 非空且闭合到 case_id/request；实际 mapper 完整读写这三列与原回执。artifact 继续固定 CONFIGURATION/configId 与 projectId，fixture/source/rights/artifact 复合外键未削弱。
- 精确 inventory 更新为 205 business / 242 total，并列明新增 `simulation_evaluation_preparation`；不使用宽泛大于等于断言。063→064→065 顺序由各自前置相邻断言共同验证。

## 历史冻结

001–062 共 61 个 SQL/XML 文件再次与 part11 ZIP 逐字节比较一致。

组合 SHA-256（文件名、NUL、原字节、NUL）：
`2d397c29d7cd21da85187ffd3473d2f4eef452b404a04f50a3716daa4eed60bf`

证据：`.implementation-work/logs/part13-migration-freeze.json`。本轮不修改 SQL、Java、master、coverage 或冻结 Schema。

## 执行边界

使用 `.implementation-work/python-contracts/bin/python`，加载 `toolchain-env.sh`，设置 `JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:-UsePerfData'`。运行全部 `test_*migration*.py`，包括既有 CLI jar 资源字节一致性测试。没有 Maven、数据库迁移或 socket 绕过；静态 parser 通过不等于实际 MySQL DDL 执行通过。CLI 若使用旧 jar，其资源不一致应归为待重新打包，不能修改约束绕过。

## 本轮结果

- `part13-simulation-migrations-focused.log`：44 tests，0 failure/error/skip，PASS。
- `part13-all-migrations-final.log`：101 tests，源码 AST/mapper/冻结与 bundle 通过；CLI 资源一致性测试出现 3 failures / 1 error，全部由旧 jar 缺 065、仍含旧 063/master/coverage 引起。此时尚未重打包，不宣称整套通过；保留原检查供 root 新制品完成后复跑。
