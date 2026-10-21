# 090：N01确定性计划准备（已集成，未部署验收）

## Delivered boundary

A normal session can explicitly prepare the original 083 execution and 088 context binding through the existing private BoundExecutions admission. The new service first rejects every node set except exactly N01. Read-only preview validates a selected original cell against the complete server-derived candidate sample × repeat matrix. Choices come only from the fixed candidate template manifest. Exactly one compatible template is auto-selected; ambiguity requires an explicit ordinary selector. Permission failures remain failures, never synthetic NOT_APPLICABLE choices.

The deterministic expansion retains every fixed template node, including NOT_APPLICABLE and UNKNOWN nodes. Section IDs derive from immutable original identities. Owners and completion rules are empty and remain blockers. HUMAN_PROVIDED is an explicit provisional method requiring review. The original fixed profile must still be current; profile drift blocks rather than silently substituting a new profile.

Preparation persists a separate immutable intent/attempt/registered receipt and uses the deployed object transport with complete byte/version readback. The payload is the existing published PlanCandidate. Ownership is EVALUATION. Nothing saves a task plan, creates a task/run/session/step/invocation, invokes a model, replaces the approved 088 compiler, or records professional success. An original unknown PUT can only recover the original object; no new PUT or new attempt is created. Replacement grants cannot reauthorize the original operation: current exact registration permission and original ROLE narrowing must still equal the original immutable witness.

## Transport

All POST routes require current ordinary session, schema/context headers and CSRF, reject query parameters and duplicate headers, accept at most 1 MiB, and return no-store envelopes with resource_revision `1`.

- GET `/api/formal-evaluation-plan-feature-availability`: initialized callable capabilities, not authorization or completion.
- POST `/api/formal-evaluation-plan-preparations/executions`: original frozen 088 BoundStart; original Idempotency-Key; 201 Registration. Registration.request_sha256 hashes canonical original083 Start, not BoundStart.
- POST `/api/formal-evaluation-plan-preparations/executions/recovery`: BoundRecovery; no key header; read-only original Registration.
- POST `/api/formal-evaluation-plan-preparations/preview`: Request; no key header; no persistence or object transfer.
- POST `/api/formal-evaluation-plan-preparations`: Request and original Idempotency-Key; 201 Prepared.
- POST `/api/formal-evaluation-plan-preparations/status`: Recovery; no key header; read-only NOT_FOUND/PENDING/PREPARED metadata.
- POST `/api/formal-evaluation-plan-preparations/recovery`: Recovery; no key header; write-capable finalization using current original registration authority and GET-only recovery.

The exact closed DTOs are in resources/formal-evaluation-plan-preparation.schema.json. No caller-supplied PlanCandidate, authorization, output, object reference or digest is accepted as proof. Request hash is canonical semantic request + original actor/tenant/context/schema identity. Prepared.artifact_sha256 is the canonical PlanCandidate hash. receipt_sha256 is the hash of the private immutable full receipt, not a digest reconstructed from the public DTO.

Coverage describes the selected candidate's complete sample × repeat matrix, not all candidates' scoring matrix. selected_cells is 1; professional_completed_cells is 0; complete_evaluation, runnable and professional_completion are false.

## Historical read limitation

Status returns identity-only metadata, content_available:false, without preview or plan content. Original execution recovery and status use independent current evaluation/candidate/historical-policy/context/source/artifact read authority. They require no active execution policy, budget, evaluation.run, artifact.register, model eligibility or object PUT transport. Status validates original journals, full current reachable dependency membership and actual source rights against the immutable recorded closure. Revoked current read rights, changed ancestry/rights or corrupted records block disclosure. Cached preview must not be shown as currently authorized content.

Full historical object-content reading and full professional N01 execution remain separate unfinished work. Existing DOCUMENT_ONLY089 compiler/object remains unchanged.

## Wiring and rollout

Opt in with medical.formal-evaluation-plan.enabled=true. Object writes additionally require an existing private 0700 medical.formal-evaluation-plan.object-directory and the genuine deployed SourceUploadTransport bean. There are no fallback credentials, grants, approvals or fake records. Read capabilities are independently available without execution/write dependencies; each optional service group is gated on all dependencies it consumes. Missing optional prerequisites leave unrelated application modules usable. Invalid present transport configuration is not silently converted into a fake ready service.

Apply migration090 before deploying the shared generic ArtifactRegistry read change. There is deliberately no missing-table fallback. Generic artifact reads use the indexed tenant/artifact immutable ownership lookup, so renamed preparation artifacts remain blocked. Legacy unowned artifacts retain their previous behavior. All three090 tables are added to ProtectedTenantInterceptor's immutable mutation guard. Original075–089 schemas/DDL and stored objects are unchanged.

已集成正常评测页面、后端接口和090迁移。当前工作区包含287张业务表、324张总表；实际部署必须先迁移再上线共享读取保护。当前尚未追加Git提交或推送。

## Verification boundaries

Focused tests run actual new schema, controller, dependency traversal, expansion, authority, service composition, ledger, private transport proof, registration, historical reader, bean configuration and generic ownership guard. Upstream permission/083/088/template/profile readers and JDBC/S3 boundaries are explicitly named test doubles. Read closure drift and original-grant replacement are negative regressions. Existing actual rule expression and TaskPlanPolicy logic run. Servlet tests do not claim live Spring Security-filter evidence. SQLGlot/MySQL and native JSQLParser checks verify all090 FKs, keys/types and actual emitted SQL against existing schema columns. They do not execute a MySQL migration or prove live locking/transaction isolation. 未调用真实供应商。集成后实际多模块Maven定向23套258项、前端67项及090迁移结构13项通过；前端全量类型检查通过。完整离线汇总、打包和真实环境验证分别记录，不能由聚焦测试推定完整专业执行成功。

## 集成发现与已知限制

- 两个额外真实数据库测试因缺少部署连接配置而阻塞，未伪造配置或计为通过。
- 历史查询只返回经验证的原始身份和登记状态，不返回PlanCandidate内容；完整历史原件读取仍待开发。
- 当前页面保留一份选定单元的原操作，不提供多单元准备队列；完整候选样本×重复轮次范围始终显式展示。
- 未核准条件、缺负责人、缺完成规则，以及临时HUMAN_PROVIDED方法均保留为待处理项，不构成已完成专业N01。
- 已有DOCUMENT_ONLY绑定和对象不可升级替换为新规划编译器；未来完整N01分支需新的真实包/候选/上下文批准及新的原始执行。

### 历史完整性修补与最终离线核验

历史读取独立保留原083候选清单与原政策businessPurpose，在读取依赖前校验所选模板完整basis在原候选中恰好出现一次，并与非空原请求模板完全一致；资料用途必须等于经验证原政策的用途，实际资料读取直接使用该已验证值。未增加当前执行/预算/模型门禁来限制合法历史读取。原090 Schema与DDL字节未修改。

包含修补的最终离线结果为319套2872项Java、800项前端、369项迁移/CLI检查、12模块打包及499静态文件字节一致；真实数据库、对象存储和浏览器验收仍未执行。完整结果见STAGE24-CHECKPOINT-20261019.md。
