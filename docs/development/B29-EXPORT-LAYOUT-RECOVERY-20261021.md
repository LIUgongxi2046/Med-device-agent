# B29 成果导出余项实施记录

## 本次实际实现

- `ExportLayoutService` 提供固定文稿版式的正常作者入口、重读入口、执行消费与核验记录；`ExportController` 和同源 `export-api.schema.json`、前端 API 实现对应协议。
- POST `/api/documents/{id}/export-layout-profiles`：同版正文、格式模板及编号规则修订，明确目录标题/深度、十进制起始页码，以及明确附件章节节点顺序。渠道名称、规范制品 ID 与摘要可以明确为空，不会生成“已核准”的默认渠道。返回 profile_id 供现有导出创建的 channel_profile_revision 使用。该 ID 是固定版式/渠道声明，不是监管机构电子签名。
- GET `/api/documents/{id}/export-layout-profiles/{profileId}?document_revision=...`：重读固定规则和摘要。
- 渲染读取固定规则，附件只能是本版正文的唯一顶层 SECTION；将其按明确声明的顺序放置在普通正文之后。原节点、事实和证据关联不变，完整节点覆盖检查仍有效。附件指的是正文中的附件章节，不虚构不存在的独立附件文件。
- DOCX 目录生成真实内部链接和 PAGEREF 域；页脚生成 PAGE 域；保留真实章节书签。字段标记为需更新，不把未知页码填成看似真实的常数。DOCX 的 pagination_check 为 FIELDS_AUTHORED，不是已经完成排版。
- PDF 继续运行实际配置且摘要/版本固定的转换器；用实际 PDF outline 的目标页与实际渲染目录逐项交叉核对。目标项不全、页码未解析或不一致时失败；仅真实完成该检查才产生 CONVERTER_RESOLVED。没有配置规则则 UNKNOWN；Markdown 不适用分页，继续生成内部交换文件。
- 转换程序只允许返回 channel_check=UNKNOWN、visual_check=NOT_RUN；执行服务拒绝把转换返回值当渠道/视觉人工核准。真实版面记录不覆盖原始转换验证制品。
- POST `/api/export-items/{id}/layout-verifications`：需要现有 review.decide 权限，指定成品摘要、固定规则、实际核验附件 ID/摘要和说明，并以 previous_verification_id 串联并发修订。APPROVED/PASSED 必须有实际登记且可读的证据；未知渠道或没有规范制品不得 APPROVED。FAILED/REJECTED 和 NOT_RUN/UNKNOWN 均可真实保存。
- GET `/api/export-items/{id}/layout-verifications` 返回真实核验历史；导出 Item 同时返回最新独立核验记录，UI 显示真实状态，不能将 NOT_RUN 显示成通过。
- `/review-content` 仅对有 review.decide 权限的用户提供固定成品用于人工检查；正式发布下载还须最新渠道 APPROVED 且视觉 PASSED。下载前后再次验证权限和证据。核验附件通过现有制品上传/登记流程准备，此改动不模拟上传回执。
- 迁移157新增两张不可变表，包含 creator、版本摘要、规范/成品/证据制品外键、分类与状态 CHECK、幂等键、并发序号和 UPDATE/DELETE 拒绝触发器。主迁移清单及 coverage 由主线程集中登记；本次没有执行数据库迁移。

## B10 正式签发消费

既有 B10 公共签发、回调与签发数据不变。新增包内 requireExportCurrent 消费入口避免 EnterpriseAccess 嵌套事务：在现有 Admission 下调用 DocumentReadService.authorizeMetadata，复用原签发记录、固定正文完整字节及摘要、回调证据、签发操作回执、审计和 outbox 的完整核验代码；正文完整字节与当前元数据摘要一致，实际导出另外读取并核验完整对象字节。正式执行/发布消费同版签发与当前权限、治理水位和影响失效检查，不重新签发、不延长原签名，也不伪造外部电子签名。新消费入口用于当前正式发布；B10 原历史读取仍保留原行为。

## 原操作恢复

- 继续使用原 export_id、operation_id、export_item_id 和 attempt_no。
- 重新进入导出页面自动读取本版历史；选中的未结束原操作由当前已认证用户每30秒续办。已完成格式直接读取，不再次运行；失败格式仍需明确重试。
- 服务器重启遗留的 RUNNING/VERIFYING 只有超过配置执行期限三倍加60秒的保守窗口后才可恢复。行锁和 revision 递增隔离旧工作进程；重入时仍须当前权限和同版签发检查。
- 工作目录含 item revision，避免重启后旧工作目录阻塞新执行。成功提交格式不会重复；未登记的失败/崩溃上传可能留下不可发布的孤立对象，需要存储生命周期清理。这不是声称外部对象存储“恰好一次写入”。
- 不持久化用户凭据，不以无用户权限的后台进程绕过身份；无人登录/页面未激活期间不代替用户执行。重新取得真实用户会话后自动续办原操作。
- 修复重试旧失败 attempt 可重新创建已成功格式的缺口；拒绝存在成功或后续 attempt 的旧格式重试。
- 导出 item 使用实际配置 converterRevision，移除硬编码虚构版本。

## 当前验证事实与待执行项

仅对两份生产 Python 源进行 AST 解析、对 JSON Schema 和 Mapper XML 进行语法解析。没有运行任何测试、全量构建、服务、转换器、真实提供商、网络调用、迁移或 push。此环境无 javac/mvn、无前端依赖，Java/TypeScript 编译未运行。

新增 `tests/test_document_export_layout.py` 和 `ExportLayoutSchemaTest.java`，均未执行。后续须在获准测试阶段执行：

1. 真实注册字体/模板/转换器的中文、多页、嵌套目录、表格、公式、图片 DOCX/PDF，以及附件重新排序和重复/外版附件拒绝；在 Word/LibreOffice 核对域、目录链接、页脚和 PDF outline。
2. 原始 NOT_RUN/UNKNOWN 始终不形成发布通过；真实核验附带错版成品/错摘要/撤权证据拒绝；前一核验 ID 不匹配并发冲突；失败核验撤销先前放行。
3. SQL 迁移实际执行、唯一键/FK/不可变触发器、租户隔离和 reader 权限；新 API 请求与返回 schema、CSRF、幂等重放、审计记录。
4. 崩溃点覆盖：转换中、上传前/后、完成提交前/后；活跃 lease 不抢占，过期 lease 原 item 恢复；旧进程 fencing；已成功格式无再次转换；失败旧 attempt 无重复重试。
5. B10 同版签发、签发记录/固定字节/回调/审计损坏、治理水位变化、来源失权和影响失效，均不得以格式生成成功绕过。
6. 浏览器断线/重启/重新登录、上下文改变、页面隐藏与卸载应停止或恢复正确原操作，不能用其他企业或用户会话延续旧请求。

真实受理渠道、规范制品、真实核验附件和转换器部署仍由实际环境提供；没有这些输入时明确 UNKNOWN/NOT_RUN 或阻断，不是已通过现实受理验收。
