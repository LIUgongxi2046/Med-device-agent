# 增量功能部署启用与排障指引

核查日期：2026-10-18。本文依据当前正式源码，只读核查并记录配置；没有修改 application 配置、创建凭据、授予权限或启动服务。单元测试、前端构建通过不代表真实数据库、对象存储或生产业务链已经验收。

## 1. 先区分三层状态

1. **部署可用**：对应条件 Controller 实际注册。authenticated availability 接口返回当前企业上下文和真实开关结果。
2. **业务授权**：当前用户、企业、项目、资源、用途、身份水位和来源权利共同满足后端校验。开关与菜单可见性不授予权限；写权限也不自动包含读权限。
3. **真实资源就绪**：固定正文/Source/rights/配置/制品确实存在，数据库记录、原对象、内容摘要、依赖链和访问证明相符。

不要将“未部署”“没有当前权限”“真实资料尚未登记”“结果读取许可缺失”都展示成同一种权限问题。availability=false 应先检查部署；availability=true 后的 ACCESS_DENIED 才沿当前业务授权链调查。不要靠业务端点 404 推断授权，也不要临时伪造 availability=true。

## 2. 真实功能开关与入口

以下 7 个键均由代码中的 ConditionalOnProperty(havingValue="true") 控制，未设置时不启用；067 显式 matchIfMissing=false，其余沿 Spring 默认 false。没有“打开后自动赋权”或演示成功回退。

| 功能 | 部署键 | 当前前端入口 | 真实 availability |
|---|---|---|---|
| 063 模拟输入制品登记 | medical.simulation-input-artifact.enabled | /agents/simulation-preparation 的 fixture/input 区域 | GET /api/simulation-feature-availability → features.input_artifact_registration |
| 064 独立 SIM 评分规则 | medical.simulation-rule.enabled | /agents/simulation-rules/:ruleVersion? | 同上 → features.rule_workflow |
| 065 模拟评测准备制品 | medical.simulation-evaluation-preparation.enabled | /agents/simulation-preparation 的评测准备区域 | 同上 → features.evaluation_preparation |
| 066 Gru 固定文稿消息 | medical.gru-records.enabled | 文稿工作区折叠消息面板 | GET /api/gru-feature-availability → features.gru_records |
| 067 文稿退回修改/我的返修任务 | medical.review-return.enabled | review 工作区退回面板；首页返修队列 | GET /api/document-review-availability → review_return_enabled |
| 文稿历史目录 | medical.document-history.enabled | 已保存文稿工作区历史目录 | GET /api/document-history-feature-availability → features.document_history |
| 068 技术结构检查 | medical.document-technical-check.enabled | 已读取正文内的折叠技术检查面板 | GET /api/document-technical-check-feature-availability → features.document_technical_check |

所有 availability 请求仍需当前 authenticated application session、X-Schema-Version=1.0.0、X-Context-Revision。响应绑定当前企业/context/auth/governance 水位，不能缓存到另一身份。它们检查实际 Controller 注册，不验证 S3 健康、不预先执行业务，也不证明用户有权读写某个对象。

代码依据：business/medical-persistence/src/main/java/com/ruoyi/medical/persistence/ 下的 SimulationInputArtifactController、SimulationRuleVersionController、SimulationEvaluationPreparationController、GruController、DocumentReviewReturnController、DocumentTechnicalCheckController、DocumentRevisionDirectoryController，以及对应 *FeatureAvailability / DocumentReviewAvailabilityController。前端路由依据 web/src/router/index.js。

**Fixture 特例**：POST /api/simulation-fixtures 的 SimulationFixtureController 本身没有上述独立 ConditionalOnProperty；没有 GET/list fixture 接口。准备页的 input-artifact gate 不应被解释为 fixture 登记、模拟执行或模型授权全部就绪。

## 3. 066 Gru：只保存真实用户消息

- 数据库：按完整迁移链安装 066-gru-records.sql，已有文稿、任务、scope snapshot、operation/audit/outbox 等基础表必须齐备；租户拦截器和 mapper 必须实际接线。
- 真实输入：已保存的 document_revision，以及 GET /api/gru-conversations/context 返回的实际 scope_snapshot_id/SourceScope。不能创建假任务或猜测 snapshot。
- 授权：GruContextAuthority 要求 PROJECT project.read、DOCUMENT document.read，并按操作区分 conversation.read / conversation.use，用途 DOCUMENT_BODY。前端创建流程先读 context，因此需要 read 与 use 两项。完整当前来源/集合授权仍会重验。
- 所有者：当前实现 owner-only，不是项目成员共享聊天。
- 存储：消息/回执在实际 DB 中；固定文稿和来源读取仍依赖既有对象存储与完整来源读取链。该开关不会配置模型提供商。
- 能力边界：RECORD_ONLY、NOT_EXECUTED，保存成功仅表示用户消息已记录；没有模型回答、运行成功或“AI 已完成”。
- 恢复：原 key/body 的通用 operation lookup + typed receipt，并比对服务端 request_body_sha256。APPEND 的 NOT_APPLIED_STALE 必须来自专用只读 append-recovery 的完整绑定证明，且用户成功保存证明/草稿后才能结束旧 pending；普通 NOT_FOUND/ACTION_STALE 不允许清除不确定请求。
- 此功能代码没有新增 Gru 专属可配置容量键；不要发明 medical.gru-records.maximum-*。

主要依据：GruService、GruContextAuthority、GruController、gru-records.schema.json；docs/frontend/gru-records-20261018.md。

## 4. 067：独立退回修改，不是正式审查完成

- 数据库：067-document-review-return.sql，以及真实文稿/任务、权限、审计、operation/outbox 基础表和实际 mapper。安装整个迁移链，不孤立执行一个补丁来绕过约束。
- 发起：当前固定正文完整读取权限 + DOCUMENT review.decide，审查用途 DOCUMENT_REVIEW。真实任务必须 REVIEW_PENDING；正文当前且未签发，不能存在该版本真实签名。
- 责任人：从真实成员目录选择，后端核该人的 task.read、document.read/DOCUMENT_BODY 与 work_item.read/TASK 许可。缺姓名时显示真实成员标识，不能编造姓名或用户 UUID。
- 读取与恢复：恢复原操作只要求当前固定文稿/来源读取授权和原 actor，不把 review.decide 当作只读恢复前提；写入和写重放仍要求 review.decide。
- 分派：固定版本分派读取额外核 TASK task.read/TASK_READING 和 work_item.read/WORK_ITEM_READING。我的返修队列只返回真实当前 actor 可读的分派，并逐项校验旧固定正文权限。
- 状态：任务被真实退回为 RETURNED；没有正式 ReviewBatch、确认、签发或复审提交。REVISION_AVAILABLE / INACTIVE 不能当作完成。缺专业检查当前性核验时仍无重新提交正式审查能力。
- 分页：GET /api/my/document-revision-assignments 的 cursor 是服务器不透明 UUID，绑定 session/actor/context/watermarks/limit。正常严格校验的 CURSOR_EXPIRED 可以局部清分页、要求手动回首页；不得把认证/权限错误伪装成过期。空页可能有下一页，不自动全量扫描。
- 本切片没有新增可配置容量键；不要将硬编码的队列上限当作外部配置属性。

主要依据：DocumentReviewReturnService、DocumentRevisionAssignmentQueueService、DocumentRevisionAssignmentCursor、DocumentReviewReturnController；docs/frontend/document-return-for-revision-20261018.md。

## 5. 068：必须显式设置五个有限容量

DocumentTechnicalCheckPolicy（ConfigurationProperties 前缀 medical.document-technical-check）在 enabled=true 时强制以下值全部存在且为正整数，没有隐式容量：

| 配置键 | 类型/约束 | 含义 |
|---|---|---|
| medical.document-technical-check.maximum-bytes | Integer > 0 | 有界请求/内容处理容量，具体使用以 Service 为准 |
| medical.document-technical-check.maximum-nodes | Integer > 0 | 文稿节点容量 |
| medical.document-technical-check.maximum-table-cells | Integer > 0 | 表格单元容量 |
| medical.document-technical-check.maximum-relationships | Integer > 0 | 关系/依赖处理容量 |
| medical.document-technical-check.maximum-seconds | Integer > 0 | 本次同步执行期限 |

- 数据库：068-document-technical-check.sql；TECHNICAL_CHECK_MANIFEST 与真实 check、artifact、workflow job、operation、audit/outbox 记录必须具备一致的 schema/mapper/租户保护。不可只复制 UI 或 Controller。
- 对象存储：实际 DocumentReadService、SourceUploadTransport 及其真实 ObjectReader/ObjectWriter 路径；需要原文可读、结果对象可写、原对象版本与 SHA 完整验证。创建 intent 后的未知写入不能覆盖或伪装完成。
- 发起权限：document.check 加完整 DOCUMENT_BODY 固定文稿/来源读取权限。浏览器传真实已保存 revision 和 expected revision，不生成 check/job/manifest/artifact 身份。
- 结果权限：两个实际产物分别需要 artifact.read，目的 DOCUMENT_BODY。创建者可能有发起权限但没有结果读取权；这是已有检查的读取受限，不是创建失败，也不应创建替代检查。
- 输出：仅 STRUCTURE_FIXED_REFERENCES。COMPLETE/PASS 是有限技术结构及内部固定引用结论。professional_coverage=UNKNOWN、overall_review_ready=false；不能据此推进正式审查、签发或授予导出能力。
- typed Read 核当前性、真实覆盖/结果制品字节与摘要；STALE 必须保持失效标识。findings 当前完整有界数组至多 1 项，没有服务端分页接口。
- PROCESSING 是未闭合同步操作记录，不能描述成后台 worker 继续执行。原 key 的只读恢复不启动对象写入，NOT_FOUND 不证明未发生。超时或权利变化后未决操作仍需要受控协调；当前没有通用管理协调入口。

主要依据：DocumentTechnicalCheckPolicy、DocumentTechnicalCheckService、DocumentTechnicalCheckEngine、DocumentTechnicalCheckController；docs/backend/document-technical-check-api-20261018.md 与 docs/frontend/document-technical-check-20261018.md。

## 6. SIM 准备、输入与规则的真实前置条件

### 063 fixture / input ARTIFACT

实际流程：读取获授权模型配置与 case/input Source 固定版本 → 输入封闭参数 → 明确 POST fixture 登记 → 按真实回执登记 input artifact。SimulationInvocationSources 在登记时校验模型配置及 CONFIGURATION model.configure（SIMULATION_EVALUATION），Source 当前 rights/profile/use 授权和原对象。可见、可选或文本 caseId 不代表取得模拟许可。

fixture 的 canonical 内容摘要与 input artifact 的实际 raw SHA 是不同绑定，不能互换。ARTIFACT 不能冒充 SOURCE。input 登记需要 SOURCE artifact.register，读取还核 artifact.read/source.read、配置以及完整 lineage。lookup 必须携带原 key 和完整原 Request；NOT_FOUND 不允许自动重试或换 key。

fixture POST 原接口没有 Idempotency-Key，保留完整业务绑定后由用户显式核对，不能伪造 fixture lookup。

### 065 评测准备

支持 EXPECTED_ITEMS / SAMPLE_MANIFEST / AUTHORIZED_USE_EVIDENCE。当前为企业资源，project_id=null；预览 canonical JSON 不是批准、来源登记或权限授予。

用户必须走真实企业 Source 上传、完成与核准，之后用真实 source/rights/docSHA 登记。当前 TXT 上传可承载符合冻结 schema 的 JSON 原件；正文必须是真实 canonical bytes，不能只复制界面展示文字后猜摘要。/sources/uploads 选择真实获授权处理项目，业务归属保持 ENTERPRISE/owner_project_id=null；处理项目 UUID 不是企业来源归属。

登记校验 SourceReadAuthority（REVIEW、SIMULATION_EVALUATION）、SOURCE artifact.register 和所有真实依赖。返回制品读取仍校验 artifact.read 等完整引用。授权证据公开文档只含不透明 witness/业务引用；不暴露私有许可材料，不提供伪造许可文件链接。

### 064 SIM 规则

走独立 /api/simulation-rule-versions 路径：真实 definition/read、approval-baseline 与 approval-decisions，不把 SIM 版本导向普通规则详情接口。

创建要求企业级 rule.write；读取 rule.read；审批 rule.approve 及实际批准依据。规则输入/引用与最终 APPROVED 记录仍须服务端证明。开关不创建一个可用规则，更不自动批准。当前三个准备/规则开关没有附加的专属 @Value 容量属性；不要凭 UI 限额发明配置键。

数据库分别需要 063、064、065 及此前全部相关迁移；来源、模型配置、权限、版本、artifact registry、access-set/lineage 与审计链必须真实。依据：SimulationInvocationSources、SimulationInputArtifactService、SimulationEvaluationPreparationService、SimulationRuleVersionService/Store/ApprovalService。

## 6.1 文稿历史目录：元数据分页与固定正文读取

`medical.document-history.enabled` 默认关闭。DocumentRevisionDirectoryPolicy 在启用时要求以下五个容量显式配置；没有隐式默认容量：

| 配置键 | 类型/约束 | 含义 |
|---|---|---|
| medical.document-history.maximum-page-size | Integer > 0 | 单页最多披露的已授权目录条目数 |
| medical.document-history.maximum-scan-rows | Integer ≥ maximum-page-size，且 < Integer.MAX_VALUE | 每页最多处理的原始候选行；查询额外取一行判断后续候选 |
| medical.document-history.maximum-references | Integer > 0，且 < Integer.MAX_VALUE | 每个版本各类 check/review/signature 引用列表的上限；超限明确拒绝 |
| medical.document-history.maximum-cursor-entries | Integer > 0 | 进程内不透明游标缓存条目上限；容量淘汰后旧游标失效 |
| medical.document-history.cursor-seconds | Integer > 0 | 游标有效期，单位秒 |

- authenticated `GET /api/document-history-feature-availability` 返回真实 `features.document_history`；关闭时 `maximum_page_size=null`，启用时为实际已校验策略中的最大页大小。前端取 `min(20, maximum_page_size)`，目录请求始终显式发送 `limit`。此值不代表授予文稿读取权限。
- `GET /api/documents/{documentId}/revisions?limit=…&cursor=…` 读取真实版本元数据，并逐项走现有元数据/来源授权与当前性复核。目录不下载正文，不要求额外 S3 目录列举权限，也不产生正文已读取或字节已验证的证明。既有授权及数据库依赖仍须就绪。
- 点击“读取此版本”后由编辑器原 `readSavedDocumentRevision` 路径读取固定正文；仍需该版本实际文稿/来源读取权限、对象读取及原有字节/摘要验证。目录可见不保证之后正文读取获准；不能为此授予存储列举权限或绕过原读取链。正文读入独立历史视图，保留当前草稿。
- 无虚构总数或全量冻结列表；目录是实时 keyset 分页，空页仍可能有下一页。头版本或身份水位变化为 `CURSOR_INVALID`，过期/缓存淘汰为 `CURSOR_EXPIRED`；前端仅对完整校验的预期游标错误提供局部首页刷新。全局权限与损坏记录错误保持原严格处理；候选条目明确缺权/来源未就绪按后端规则隐藏，不暴露其标识。引用容量预检在来源授权前执行，超限导致整页 CONTEXT_LIMIT，不伪装为空页。
- 复用既有版本/检查/审查/签名表，无新增历史目录迁移或对象存储制品。目录中的检查/审查/签名数量仅是记录数，不表示通过、批准或可签发。

主要依据：DocumentRevisionDirectoryPolicy、DocumentRevisionDirectoryService、DocumentRevisionDirectoryCursor、DocumentRevisionDirectoryFeatureAvailabilityController，以及 docs/backend/document-revision-directory-20261018.md。真实数据库、对象存储与浏览器联调尚未执行。

## 7. 无凭据的禁用态模板

以下仅是文档示例，未写入 application 配置。保持 false 可避免把缺失资源伪装成启用；真正启用应在部署变更中审批并补齐依赖。容量不提供猜测默认值。

~~~properties
medical.document-history.enabled=false
medical.gru-records.enabled=false
medical.review-return.enabled=false
medical.document-technical-check.enabled=false
medical.simulation-input-artifact.enabled=false
medical.simulation-rule.enabled=false
medical.simulation-evaluation-preparation.enabled=false

# 历史目录启用前必须显式确定五个容量，约束见上文；以下仅为键名占位。
# medical.document-history.maximum-page-size=<positive integer>
# medical.document-history.maximum-scan-rows=<integer at least maximum-page-size, below Integer.MAX_VALUE>
# medical.document-history.maximum-references=<positive integer below Integer.MAX_VALUE>
# medical.document-history.maximum-cursor-entries=<positive integer>
# medical.document-history.cursor-seconds=<positive integer>

# 技术检查启用前，部署人员必须显式确定五个正整数；下面是键名占位，不是可直接运行的值。
# medical.document-technical-check.maximum-bytes=<positive integer>
# medical.document-technical-check.maximum-nodes=<positive integer>
# medical.document-technical-check.maximum-table-cells=<positive integer>
# medical.document-technical-check.maximum-relationships=<positive integer>
# medical.document-technical-check.maximum-seconds=<positive integer>
~~~

既有共享配置还需与真实环境对应。SourceUploadTransportPolicy 的非秘密键包括 medical.source-upload-transport.storage-directory、region、tenant-permits、user-permits、segment-bytes、demand、maximum-duration、admission-interval、namespace、revision。它们不是新功能开关；必须依据实际存储与部署身份设置，其中 revision 为真实 UUID，不能编造固定资源版本来让校验通过。代码限制 user-permits ≤ 2、demand ≤ 16、maximum-duration ≤ 60 秒，admission-interval 至少 1ms 且小于 maximum-duration；segment-bytes 上限以 S3ObjectReader.MAX_SEGMENT_BYTES 为准。

DocumentUploadPolicy 还有真实 environment/region、最大来源/版本/关系/字节/节点/表格容量、期限及实际 schema manifest/artifact profiles；DocumentReadPolicy 需要正确的受保护游标配置。此处不提供凭据、签名密钥、游标密钥、S3 endpoint/bucket 身份或权限模板；应走既有受控秘密与基础设施配置渠道，不能复制测试密钥。

**模拟执行另属一层**：medical.simulation-execution.enabled、medical.simulation-execution.local-bridge.enabled 和 medical.simulation-execution.result-persistence.enabled 不是上述准备开关的别名。真正运行还需 SimulationInvocationConfiguration 的 configuration-directory、configuration-owner、maximum-configuration-bytes；local-bridge 的 python-executable、project-root、store-directory、maximum-record-bytes、deadline-seconds；结果持久化 maximum-object-bytes。所有键均位于相应前缀下、来自实际 @Value。准备成功不等于这些执行、worker 安全和结果存储依赖已部署。本文不生成配置密钥目录或提供外部模型访问凭据。

## 8. 建议的验收/排障顺序

1. 先确认所部署 commit、迁移版本、mapper 注册、租户与 immutable-table 防护一致。对真实 MySQL 实际执行迁移并核表/约束；离线 migration parser 通过不能替代数据库执行。
2. 在变更审批范围内填写有限容量及现有存储配置，使用受控凭据注入。观察应用真实启动是否失败；缺必需 bean/参数时不要替换空实现。
3. 用真实应用会话选择企业后请求对应 availability。记录 request_id、当前水位、功能标志；不要记录 cookie、CSRF 值、令牌或私有源地址。
4. 若 false，先核实际 Controller 注册和配置作用域。若 true 但业务拒绝，核当前资源与用途权限、成员状态、Source rights/profile/grant、固定版本、actor/context 和真实对象；不要先添加全局宽权限。
5. 走实际 Source 与文稿读取路径，核原对象版本/字节/SHA、schema、lineage 与真实配置引用。TXT JSON 的预览与登记回执必须分别验收。
6. 人工发起一个范围受控的真实操作，保存真实 operation/check/conversation/return/artifact ID 与请求摘要；检查 DB 事务及 audit/outbox 与对象一致，不凭 HTTP 200/201 单独判断业务完成。
7. 分别验收无读权、有写无读、撤权、跨上下文、固定版本变化、未知网络结果、只读恢复和迟到响应。结果读取被拒绝不能自动重新创建。
8. 确认最终 UI 语义：Gru 尚未执行；067 是退回修改；068 专业覆盖 UNKNOWN；SIM 准备/批准/执行是不同步骤。仍无真实专业检查闭环时，审查推进继续保持不可用。

## 9. 当前验收结论的边界

本指引只完成源码级配置/契约核查和文档交付。现有聚合测试与构建结果应查看对应交付验证记录；本文没有启动真实服务、运行真实 MySQL/S3 E2E、检查部署凭据或验证某个用户已获生产权限。后续真实部署验收应单独记录环境、版本、实际请求回执和残留问题。

## 10. 069—072 新增能力

本节列出配置契约，未启用当前环境的开关或创建任何权限。

- 固定保存版本比较：`medical.document-comparison.enabled` 默认关闭。启用必须显式给出 `maximum-nodes`、`maximum-cells`、`maximum-relationships`、`maximum-input-bytes`、`maximum-result-bytes`、`maximum-records`、`maximum-text-code-points`、`maximum-work`、`maximum-milliseconds`、`storage-seconds`、`maximum-versions`、`maximum-lineage-relations`，均为正数，完整边界见 `DocumentComparisonPolicy`。前端独立解析上限为 8 MiB；超过前端容量是局部失败，不应登出或重新写入。能力只比较固定两版，不执行候选采用或医学判断。
- Gru 记录导出：同时需要 `medical.gru-records.enabled` 与 `medical.gru-record-export.enabled`。导出前缀必须显式设置 `maximum-bytes`（不超过 33554432）、`maximum-seconds`、`maximum-nodes`、`maximum-relationships`。导出用户记录不等于模型执行；下载需独立制品权限与实际对象核验。
- 071 是技术检查恢复账本加固，无独立启用开关。升级前梳理旧068未决/完成记录；缺少新增不可变意图/尝试账本的旧记录会被明确拒绝，不能自动补造成功证明或重新执行未知写。需要独立受控迁移/对账方案。
- 文稿保存审计：`medical.business-audit.enabled` 默认关闭。必须显式设置 `maximum-page-size`、`maximum-scan-rows`、`maximum-payload-bytes`、`maximum-response-bytes`、`maximum-window-seconds`、`cursor-seconds`、`maximum-cursor-entries`。扫描上限至少为页大小且小于 Integer.MAX_VALUE；均为正数。SQL配置5秒查询超时，其真实驱动/数据库效果仍需联调。浏览器响应流上限取服务端与4 MiB较小者。游标是有界进程内缓存，进程重启/多实例切换可返回过期并重新查询，不声称分布式冻结全量清单。仅展示 `document.revision.saved` 的授权摘要；实际 audit.read/AUDIT_READ 与文稿/来源权限分别核验。
