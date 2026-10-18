# IMP-23：已有标注的持久化回复扩展

日期：2026-10-18。范围：RV-05 保存回复及 BX-12 回复读取的独立扩展。此记录不表示 IMP-23/IC-23 已完成。

## 原设计依据与兼容边界

- `实施任务计划.md` IMP-23：回复保留人员/时间，回复不自动解决标注；历史版本与原文位置可追溯。
- `LLD-后端.md` §4 RV-05、§5.3：意见和当前版本写入回复；保留作者/时间；回复不自动解决问题。
- `LLD-前端.md` S11 定位与回复：固定 annotation_id，重读显示人员与时间；历史位置不自动重新定位。
- `backend-design/接口补充设计.md` §2.2：回复固定 annotation_id 与 expected_revision，拒绝空内容。

冻结的 BX-12 `/api/annotations/{id}/replies` 仍要求 `RecordPage.detail: ArtifactRef`；旧 `AnnotationDetail.status` 还未覆盖 `REANCHOR_REQUIRED`。本次不修改这些已发布 Schema、类型、目录或历史摘要，也不声称旧接口已经可用。新增独立接口及封闭 Schema：

- `GET /api/annotation-threads/{id}/replies`
- `POST /api/annotation-threads/{id}/replies`
- `backend-design/annotation-replies.openapi.yaml`
- `business/medical-persistence/src/main/resources/annotation-replies.schema.json`，身份 `urn:medical:annotation-replies:1.0.0`

此扩展直接读取/追加现有 `annotation_reply`，由现有 `annotation` 提供固定文稿和并发修订身份；没有替代标注业务状态或虚构制品。没有新增迁移或种子数据。

## 写入、原回执与状态

输入仅有 `document_revision`、`expected_revision`、`content`。企业、作者、时间、状态和回复身份均由服务端产生或读取。`expected_revision` 指父标注 revision，不是 document_head revision。新回复要求原正文版本匹配父标注，CAS 增加父修订，并保存实际 `annotation_reply`；父 `status`、选区和固定正文均不改变。

标注状态可以是 `OPEN`、`RESOLVED`、`RETURNED` 或 `REANCHOR_REQUIRED`。回复不会解决、重开、重新定位、确认、签发，也不会制造检查通过或批准记录。对原版本的回复保留原版本；读取明确返回 `ORIGINAL_VERSION`。如果标注以后由其他获准流程重新定位，同文稿的旧幂等请求仍按原正文重新检查读取许可并返回原回执。

原操作使用 `RV-05-REPLY-1`，请求摘要固定方法、路径、请求体、会话、上下文和本扩展 Schema 摘要。相同原键重放保存的 request_id、operation_id、reply_id、时间、内容及修订；已新增后续回复不会产生替代回执。改变内容或固定正文后复用键返回 `IDEMPOTENCY_CONFLICT`。重放同时核对真实回复、原操作、审计及 Outbox。

父 CAS、实际回复、operation_record、audit_event 与 outbox_event 位于同一个 `EnterpriseAccess` 事务。回复原文只保存在带父标注分类的回复与原操作回执中。审计和 Outbox 只保存身份、原/新修订、状态、时间和摘要，绝不保存回复自由文本。所有成功与错误响应都禁止缓存；错误不回显原文、SQL 或存储地址。

## 读取、权限和时间有效性

回复并不扩大任何 grant：

1. 以当前登录身份进入现有企业事务，验证上下文，真实父标注及其 document_revision/document_id/project_id。
2. 在真实 DOCUMENT 范围检查 `annotation.read` 或 `annotation.write`，用途 `DOCUMENT_REVIEW`。
3. 调用原 `DocumentReadService.snapshotFixed`，继续使用其 `DOCUMENT_BODY` 用途许可及完整文稿、Artifact、上游依赖、来源和权利核验。两类用途许可都需要已存在；本扩展不自动授予。
4. 外部内容读取完成后再进入短事务，核对主体、会话、完整上下文、实际 auth/governance epochs、文稿头、固定正文与摘要，并重新检查标注动作许可。
5. 在内容读取前、最后事务内及响应前比较当前主体授权和企业来源权利的最后一次生效/到期边界。即使没有水位变化，读取途中跨过有效期也拒绝；写入终态前发现变化会触发事务失败。

所有 Mapper 查询使用现有受保护企业 SQL、禁用读缓存、验证 userId 参数；父标注与文稿映射通过完整企业/项目关联。客户端不能注入企业、作者、位置、状态或权限。

读取输出最多 1000 条完整回复，按 `created_at, reply_id` 排序。数据库 COUNT 与完整集合数量必须相同，返回 `complete:true`、总量和 RFC8785 摘要；没有 page/cursor/query 参数。超过容量明确失败，不截断、不伪造 complete。新增也受 1000 条上限控制。单条内容最多 16000 Unicode code points；输入流最多 65536 字节，已声明或 chunked 超限都在调用服务前拒绝。原 Unicode、空格与换行保持不变，拒绝空白内容、非法代理对与非文本控制符。

## 验证状态与未完成范围

- 在 `business/` 执行 `../.implementation-work/toolchains/run-maven --batch-mode --no-transfer-progress -o -pl medical-persistence -am -Dtest='AnnotationReply*Test' -DforkCount=0 -Dsurefire.failIfNoSpecifiedTests=false test`。`MAVEN_OPTS` 使用 `-Xmx512m` 和项目已安装 `mockito-core-5.17.0.jar` 的 `-javaagent`；未使用动态 attach 或扩大环境权限。
- 验证结果：2026-10-18 04:17:40 UTC，31 项通过，0 失败、0 错误、0 跳过；Mapper 3、Service 17、Schema 6、Controller 5。生产类编译通过。日志 `.implementation-work/logs/annotation-replies-tests.log`，逐项 JUnit 原始报告保存在 `.implementation-work/annotation-replies-verification/junit/`。
- 新测试覆盖封闭 Schema、Unicode/大小边界、身份/上下文/版本与当前权限拒绝、时间有效期跨越、幂等重放、原版本定位、真实 SQL 企业过滤和参数化、完整集合、错误脱敏及父状态不改变。
- Service 测试使用真实 Schema 和 ResourceReadAuthority，模拟来源阅读、持久化 Mapper 和事务回滚边界；不是 MySQL 原子性证据。
- Controller 测试直接调用 servlet 契约；不是实时 HTTP、登录、CSRF 或端到端证据。
- SQL 测试加载实际 MyBatis Mapper、企业拦截器和 SQL parser；不连接数据库。
- 数据库/存储运行验证、真实登录后保存重读及浏览器工作流尚未执行。当前环境的数据库连接限制未绕过。
- 未完成：标注创建/列表、Selection 的服务端校验与制品登记、RV-06 决定/重新定位、原 BX-12 ArtifactRef 读取通路、前端回复接入、独立检查/证据核验/整批审查、再次认证/签发，以及 IC-23 的真实客户验收。
