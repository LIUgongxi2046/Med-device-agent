# 企业级模拟评测准备产物

## 状态与边界

本切片新增实现及隔离回归测试，真实数据库、对象存储和 HTTP 验收仍须在真实环境完成。测试替身不会生成有效用户授权或证明数据库通过。默认关闭：`medical.simulation-evaluation-preparation.enabled=true` 才启用生产者公开入口。

仅企业级 `project_id=null`；完整依赖祖先也必须为企业级。项目材料不会被删去作用域或复制提升为企业材料。现有企业级 SIM rule 因此不能消费项目样本。本服务不修改来源、许可、授权 grant、审批或对象字节，也不提交或推送代码。

## 固定类型与对象语义

| 逻辑 kind | 实际 artifact_kind | 文档 Schema |
|---|---|---|
| EXPECTED_ITEMS | EXPECTED_ITEMS | 冻结 evaluation-results / ExpectedItems |
| SAMPLE_MANIFEST | SAMPLE_MANIFEST | 冻结 evaluation-results / SampleManifest |
| AUTHORIZED_USE_EVIDENCE | SIM_AUTHORIZED_USE_EVIDENCE | 独立 simulation-authorized-use-evidence / Document |

全部 artifact_content_binding 以真实企业 SOURCE 的 source_id 为主资源，SOURCE/source.read。固定 descriptor 为 `SimulationEvaluationPreparationProfile.profile(kind)` / `forSchema(schemaRef)`；不得从用户参数选择 schema、owner 或 resourceKind。

ExpectedItems 的 input_ref 必须为真实已登记 ExactInput ARTIFACT。SampleManifest 逐样本核对 input 和 expected ARTIFACT，重建完整 ExpectedItems 并核验 expected_ref SHA；要求非空、全 SIMULATION、sample_id 唯一、同来源族 split 一致、同输入来源族一致。不生成占位 UUID 或引用。

授权清单只调用 `SimulationAuthorizedUseEvidenceAuthority.preview` 与 `verifySourceDocument`。现有批准/权利及真实授权证据由该独立组件重建并复核；调用者声明不是许可。独立 Authority 采用 opaque 审批 witness：公开文档仅含承诺摘要/数量和业务来源身份，私有许可文件不作为普通内容依赖返回。生产者只登记 Authority 返回的真实 SIM 业务闭包，不删除已有内容图边，不要求许可文件取得第二种 current purpose。

## 接口

所有入口要求真实应用会话、X-Schema-Version=1.0.0、X-Context-Revision，无 query；POST 另由现有安全过滤器验证 CSRF，控制器拒绝重复头。返回 no-store 和接口 Envelope。

- POST `/api/simulation-evaluation-preparations/preview`
  - `{schema_version, project_id:null, kind, specification}`
  - ExpectedItems specification 为原 ExpectedItems 对象；SampleManifest 为原样本数组；授权清单为独立 Authority 定义的 specification。
  - 返回 canonical_json、document_sha256、registered=false、approved=false、production_eligible=false。
  - 该预览不会登记对象或创建许可。
- POST `/api/simulation-evaluation-preparations`
  - Idempotency-Key 必填。
  - `{schema_version, project_id:null, kind, source_version, rights_revision, document_sha256}`。
  - SOURCE 必须已经上传接收、审核批准、ACTIVE，且具有当前 SIMULATION_EVALUATION 权利与真实 artifact.register 权限。
  - 通过 SourceUploadTransport 在事务外读取真实 ObjectRef；逐块重查当前会话/来源/权限。原 bytes 必须严格等于 canonical bytes，原对象 hash、实际 raw hash、请求 hash 完全一致。没有对象改写或 PUT。
  - 事务内再次核验当前来源、权利、完整祖先、依赖权限及分类；登记 artifact、完整 dependency access set、lineage、不可变 sidecar、共同 operation_record、audit/outbox 并写回读。
- GET `/api/simulation-evaluation-preparations/{artifactId}`
  - 返回 `{receipt, document}`，使用当前读权限；不要求 artifact.register。
  - 重新读取和核验原对象、登记、完整依赖与历史账本。权利换版/撤回/到期不会靠历史回执继续放行。

实际上传路径：从 preview 保存 canonical_json 的精确 UTF-8 字节为 `.txt`（不加 BOM、缩进或行末换行），经既有来源上传入口以 text/plain 上传，再完成真实来源审核和 SIM rights 审批后登记。既有 SourceUploadSchema 不开放 JSON 扩展名；本切片不扩上传白名单。已存在 application/json 原件也可登记。两种 MIME 都必须通过相同固定 JSON Schema、raw hash 与严格 canonical 字节核验；登记保留原 media_type/ObjectRef。

原件和请求限定 1 MiB，严格拒绝 duplicate keys、trailing JSON、非 canonical 字节。JSON 语义相同但空白或键序不同，仍需先由真实上传/审核流程提供正确原件，服务不会静默改写。

## 恢复与账本

使用 `operation_record.operation_name=SIM-EVALUATION-PREPARE`，target_id 为 artifactId。可从通用 operation lookup 找到真实 target，再调用 typed GET 恢复，避免重复登记猜测。

065 `simulation_evaluation_preparation` 存储 strict canonical_document、document SHA、原请求 canonical JSON/body SHA、operation request SHA、receipt、operation ID 和标准 schema_version/created_by/created_at/classification；没有 update/delete mapper。

`SimulationEvaluationPreparationEvidence.verify(admission,row,mapper)` 是共享只读核验器，验证 canonical 文档及原请求、完整 typed receipt、operation 状态/目标/请求摘要/response、完整 audit/outbox 内容。当前身份始终使用 admission.actorId；creatorId 仅过滤历史创建者，不构造用户会话。授权消费组件可直接复用，避免循环注入 producer service。

## Root 集成清单

1. master 引入 `065-simulation-evaluation-preparation.sql`，更新 coverage。
2. 保护表新增 `simulation_evaluation_preparation`。
3. `SimulationEvaluationPreparationMapper` 10条全部加入 subject/transaction allowlist：
   - 读：registration、lockSource、operation、operationById、audit、event。
   - 写：insertRegistration、insertArtifact、insertBinding、saveOperation。
4. EvaluationReferences / SimulationRuleDependencies 对 SIM 用途选择固定 preparation profile；原用途不放宽。
5. ArtifactRegistry 仅为独立授权文档开放精确本地 Schema digest 分支：实际 kind=SIM_AUTHORIZED_USE_EVIDENCE、SOURCE/source.read、企业scope、SIM purpose、schemaDoc/manifest 正确；冻结两种定义仍走原 catalogue。
6. EvaluationService 的全 SIM 消费调用独立授权 Authority，并由现有完整 load 复用于执行阶段。
7. 通用 operation lookup 保持真实 subject 归属；typed GET 路由如上。

## 验证

`SimulationEvaluationPreparationSchemaTest`：重复键/尾随/非canonical、闭合类型和企业作用域、固定profile。

`SimulationEvaluationPreparationServiceTest`：规范预览、真实引用调用、原对象登记/replay/typed read、读写授权分离、非canonical拒绝、项目祖先和登记授权失败、审计回执损坏、混合/空样本/expected hash、授权专用Authority委派。

`SimulationEvaluationPreparationMapperTest`：真实 mapper XML 解析、缓存禁用、tenant/subject/transaction 限制、共同 operation 表、SOURCE binding、insert-only。

Maven 由 root 串行运行。该文档不将未执行的测试或真实外部系统检查记为通过。
