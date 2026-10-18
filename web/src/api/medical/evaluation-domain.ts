import { canonicalizeEx } from 'json-canonicalize'
import { fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import { readRuleConstantJson } from './rule-values'
import { projectTextIssue } from './project-text'
import type { Operation } from './operations'

export const evaluationSchema = 'urn:medical:evaluation-results:1.0.0#/$defs/'
export const evaluationViewSchema = 'urn:medical:evaluation-view:1.0.0#/$defs/'
export type EvaluationDimension = `EVAL-${'01' | '02' | '03' | '04' | '05' | '06' | '07' | '08' | '09' | '10' | '11' | '12'}`
export type EvaluationAction = 'evaluation.create' | 'evaluation.read' | 'evaluation.score' | 'evaluation.finalize'
export type EvaluationVerdict = 'PASS' | 'FAIL' | 'INSUFFICIENT_EVIDENCE'
export type EvaluationStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INSUFFICIENT_EVIDENCE'
export interface EvaluationExpectation { dimension: EvaluationDimension; applicable_item_ids: string[]; control_item_ids: string[] }
export interface EvaluationSample {
  sample_id: string; source_family: string; split: 'DEVELOPMENT' | 'TUNING' | 'ACCEPTANCE'; document_type: string
  evidence_class: 'REAL' | 'SIMULATION'; input_ref: Basis; expected_ref: Basis; expectations: EvaluationExpectation[]
}
export interface EvaluationCreate {
  schema_version: '1.0.0'; project_id: string | null; dataset: Basis; sample_manifest_artifact: Basis; samples: EvaluationSample[]
  candidates: Basis[]; rule_revision: Basis; required_dimensions: EvaluationDimension[]
  variance_policy: { repeat_count: number; policy: 'ALL_REQUIRED_RUNS'; confidence_interval_method: string | null }
  authorized_use_evidence: Basis; environment: 'SIMULATION' | 'PRODUCTION'; reason: string
}
export interface EvaluationDefinition {
  schema_version: '1.0.0'; tenant_id: string; evaluation_id: string; request: EvaluationCreate
  candidate_snapshots: (Basis & { agent_id: string; created_by: string; dependency_manifest: Record<string, unknown>; limits: Record<string, unknown>; schema_version: '1.0.0'; schema_manifest_sha256: string })[]
  approved_expectation_samples: string[]; authority_grant_id: string; authority_revision: string; created_by: string; created_at: string
}
export interface EvaluationCell { sample_id: string; agent_version_id: string; dimension: EvaluationDimension; repeat_index: number }
export interface EvaluationFinalize { schema_version: '1.0.0'; expected_revision: string; definition_sha256: string }
export interface EvaluationAppend extends EvaluationFinalize {
  cell: EvaluationCell; input_ref: Basis; output_ref: Basis | null; evidence_refs: Basis[]
  numerator: string | null; denominator: string | null; unknown_count: string | null; violation_count: string | null
  control_numerator: string | null; control_denominator: string | null; status: 'SCORED' | 'UNKNOWN' | 'BLOCKED'; supersedes: string | null; reason: string
}
export interface EvaluationScore {
  schema_version: '1.0.0'; score_id: string; evaluation_id: string; definition_sha256: string; rule_revision: Basis; observation: EvaluationAppend
  scorer_id: string; authority_grant_id: string; authority_revision: string; created_at: string
}
export interface EvaluationResult {
  schema_version: '1.0.0'; evaluation_id: string; definition_sha256: string; score_set_sha256: string; effective_scores: EvaluationScore[]
  cells: { cell: EvaluationCell; score_id: string | null; status: EvaluationVerdict; reason: string; value: string | null }[]
  dimensions: { dimension: EvaluationDimension; expected_cells: number; observed_cells: number; passed_cells: number; failed_cells: number; insufficient_cells: number }[]
  missing_requirements: string[]; verdict: EvaluationVerdict; production_eligible: false
}
export interface EvaluationRead {
  schema_version: '1.0.0'; evaluation_id: string; content_sha256: string; status: EvaluationStatus; revision: string; definition: EvaluationDefinition
  score_history: EvaluationScore[]; result: EvaluationResult | null; result_sha256: string | null; production_eligible: false; allowed_actions: []
}
export type EvaluationScores = Omit<EvaluationRead, 'definition'>
export interface EvaluationReceipt {
  schema_version: '1.0.0'; operation_id: string; evaluation_id: string; definition_sha256: string; revision: string; status: EvaluationStatus
  score_id: string | null; result_sha256: string | null; verdict: EvaluationVerdict | null; production_eligible: false
}
export interface PendingEvaluation {
  actor: string; tenant_id: string; context: Context; key: string; operation: 'EV-01' | 'EV-02' | 'EV-03'; target_id: string | null
  project_id: string | null; expected_revision: string | null; definition_sha256: string | null; body_sha256: string
}
export const evaluationStatusLabels: Record<EvaluationStatus, string> = { QUEUED: '已登记，待评分', RUNNING: '评分记录中', COMPLETED: '结果已固定：通过', FAILED: '结果已固定：未通过', INSUFFICIENT_EVIDENCE: '结果已固定：证据不足' }
export const evaluationVerdictLabels: Record<EvaluationVerdict, string> = { PASS: '通过', FAIL: '未通过', INSUFFICIENT_EVIDENCE: '证据不足' }
export const canonicalEvaluation = (value: unknown): string => canonicalizeEx(value, { strictUndefined: true })
export const evaluationDigest = sourceDigest
const same = (left: unknown, right: unknown): boolean => left === undefined || right === undefined ? left === right : canonicalEvaluation(left) === canonicalEvaluation(right)
const mismatch = (message: string): never => fail('CONTENT_MISMATCH', message)
function positive(value: string): void { if (revision(value) === '0') fail('SCHEMA_INVALID', '评测修订必须为正整数') }
export function evaluationContext(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '评测操作需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
/** Entry gate only. The server resolves current grants, source rights and independent scorer authority. */
export function evaluationAllowed(session: MedicalSession | null, action: EvaluationAction, project?: string | null, id?: string | null): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === action && (project === undefined || item.project_id === null || item.project_id === project) && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context?.tenant_id
    || item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id && (project === undefined || project === item.project_id)
    || action !== 'evaluation.create' && id !== null && item.resource_kind === 'EVALUATION' && (item.resource_id === null || id === undefined || item.resource_id === id)
  ))
}
export function parseEvaluationJson(text: string): unknown {
  if (text.length > 2_000_000) fail('SCHEMA_INVALID', '定义文本过长，请缩小评测批次')
  return readRuleConstantJson(text)
}
export function evaluationMatrix(definition: EvaluationCreate): EvaluationCell[] {
  return definition.samples.flatMap(sample => definition.candidates.flatMap(candidate => definition.required_dimensions.flatMap(dimension =>
    Array.from({ length: definition.variance_policy.repeat_count }, (_, repeat_index) => ({ sample_id: sample.sample_id, agent_version_id: candidate.version_id, dimension, repeat_index })))))
    .sort((a, b) => a.sample_id.localeCompare(b.sample_id) || a.agent_version_id.localeCompare(b.agent_version_id) || a.dimension.localeCompare(b.dimension) || a.repeat_index - b.repeat_index)
}
export function evaluationCellKey(cell: EvaluationCell): string { return `${cell.sample_id}:${cell.agent_version_id}:${cell.dimension}:${cell.repeat_index}` }
export async function validateEvaluationCreate(input: unknown): Promise<EvaluationCreate> {
  const body = validate<EvaluationCreate>(evaluationSchema + 'Create', input)
  if (projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '请填写规范的登记理由')
  if (new Set(body.samples.map(item => item.sample_id)).size !== body.samples.length || new Set(body.candidates.map(item => item.version_id)).size !== body.candidates.length) fail('SCHEMA_INVALID', '样本或候选固定版本不能重复')
  if (body.samples.length * body.candidates.length * body.required_dimensions.length * body.variance_policy.repeat_count > 10_000) fail('SCHEMA_INVALID', '固定评分矩阵最多10000个单元')
  const families = new Map<string, string>(), inputs = new Map<string, string>()
  for (const sample of body.samples) {
    if (families.has(sample.source_family) && families.get(sample.source_family) !== sample.split) fail('SCHEMA_INVALID', '同一来源家族不能跨开发、调优与验收分组')
    if (inputs.has(sample.input_ref.version_id) && inputs.get(sample.input_ref.version_id) !== sample.source_family) fail('SCHEMA_INVALID', '同一输入不能标记为不同来源家族')
    families.set(sample.source_family, sample.split); inputs.set(sample.input_ref.version_id, sample.source_family)
    if (new Set(sample.expectations.map(item => item.dimension)).size !== sample.expectations.length || sample.expectations.some(item => item.dimension !== 'EVAL-11' && item.control_item_ids.length)) fail('SCHEMA_INVALID', '预期维度不能重复，对照项目仅适用于EVAL-11')
    const expected = { schema_version: '1.0.0', sample_id: sample.sample_id, source_family: sample.source_family, document_type: sample.document_type, input_ref: sample.input_ref, expectations: sample.expectations }
    if (await evaluationDigest(expected) !== sample.expected_ref.content_sha256) mismatch('预期项目集摘要与完整输入、样本身份及项目集合不一致')
  }
  if (await evaluationDigest(body.samples) !== body.sample_manifest_artifact.content_sha256) mismatch('样本清单摘要与完整样本数组不一致；不能选择子集后沿用原摘要')
  return body
}
export function validateEvaluationAppend(input: unknown): EvaluationAppend {
  const body = validate<EvaluationAppend>(evaluationSchema + 'Append', input); positive(body.expected_revision)
  if (projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '请填写规范的评分或修订理由')
  if ((body.numerator === null) !== (body.denominator === null) || (body.control_numerator === null) !== (body.control_denominator === null)
      || body.status === 'SCORED' && (body.numerator === null || body.output_ref === null || body.evidence_refs.length === 0)
      || body.status !== 'SCORED' && (body.numerator !== null || body.control_numerator !== null)
      || body.numerator !== null && BigInt(body.numerator) > BigInt(body.denominator!)
      || body.control_numerator !== null && BigInt(body.control_numerator) > BigInt(body.control_denominator!)) fail('SCHEMA_INVALID', '评分须包含实际输出和依据，成对计数且分子不能大于分母；未知或阻断不填分数')
  return body
}
export function evaluationExpectation(definition: EvaluationDefinition, cell: EvaluationCell): EvaluationExpectation | undefined {
  return definition.request.samples.find(sample => sample.sample_id === cell.sample_id)?.expectations.find(item => item.dimension === cell.dimension)
}
function checkObservation(definition: EvaluationDefinition, hash: string, body: EvaluationAppend): void {
  validateEvaluationAppend(body)
  const request = definition.request, sample = request.samples.find(item => item.sample_id === body.cell.sample_id)
  if (body.definition_sha256 !== hash || !sample || !same(sample.input_ref, body.input_ref)
      || !request.candidates.some(item => item.version_id === body.cell.agent_version_id) || !request.required_dimensions.includes(body.cell.dimension)
      || body.cell.repeat_index >= request.variance_policy.repeat_count) mismatch('评分单元或输入与固定定义不一致')
  const expected = evaluationExpectation(definition, body.cell)
  if (body.status === 'SCORED' && expected && (body.denominator !== String(expected.applicable_item_ids.length)
      || body.cell.dimension === 'EVAL-11' && body.control_denominator !== null && body.control_denominator !== String(expected.control_item_ids.length))) mismatch('评分分母必须等于固定预期项目集合数量')
}
export function effectiveEvaluationScores(definition: EvaluationDefinition, hash: string, history: EvaluationScore[]): Map<string, EvaluationScore> {
  const byId = new Map<string, EvaluationScore>(), children = new Map<string, string>(), outputs = new Map<string, Basis>(), roots = new Map<string, EvaluationScore>()
  for (const score of history) {
    validate(evaluationSchema + 'Score', score); positive(score.authority_revision); checkObservation(definition, hash, score.observation)
    if (score.evaluation_id !== definition.evaluation_id || score.definition_sha256 !== hash || !same(score.rule_revision, definition.request.rule_revision) || byId.has(score.score_id)) mismatch('评分记录身份、规则或固定定义不一致')
    byId.set(score.score_id, score)
    const cell = score.observation.cell, key = evaluationCellKey(cell), parent = score.observation.supersedes
    if (parent === null) { if (roots.has(key)) mismatch('同一评分单元存在多个初始记录'); roots.set(key, score) }
    else { if (children.has(parent)) mismatch('同一评分记录出现分叉修订'); children.set(parent, score.score_id) }
    if (score.observation.output_ref) {
      const outputKey = `${cell.sample_id}:${cell.agent_version_id}:${cell.repeat_index}`, previous = outputs.get(outputKey)
      if (previous && !same(previous, score.observation.output_ref)) mismatch('相同样本、候选与重复次数的实际输出不一致')
      outputs.set(outputKey, score.observation.output_ref)
    }
  }
  for (const score of history) if (score.observation.supersedes !== null) {
    const parent = byId.get(score.observation.supersedes)
    if (!parent || !same(parent.observation.cell, score.observation.cell) || BigInt(parent.observation.expected_revision) >= BigInt(score.observation.expected_revision)) mismatch('评分修订链目标或顺序不一致')
  }
  const effective = new Map<string, EvaluationScore>(), visited = new Set<string>()
  for (const [key, root] of roots) {
    let latest = root
    while (true) {
      if (visited.has(latest.score_id)) mismatch('评分修订链存在循环')
      visited.add(latest.score_id)
      const child = children.get(latest.score_id); if (!child) break
      latest = byId.get(child)!
    }
    effective.set(key, latest)
  }
  if (visited.size !== history.length) mismatch('评分修订链不完整')
  return effective
}
export function validateEvaluationScoreTarget(view: EvaluationRead, body: EvaluationAppend, actor: string): void {
  checkObservation(view.definition, view.content_sha256, body)
  if (view.result || !['QUEUED', 'RUNNING'].includes(view.status) || body.expected_revision !== view.revision) fail('REVISION_CONFLICT', '当前批次已终结或修订变化，请重新读取')
  if (view.definition.candidate_snapshots.find(item => item.version_id === body.cell.agent_version_id)?.created_by === actor) fail('ACCESS_DENIED', '候选创建人员不能给自己的候选评分')
  const effective = effectiveEvaluationScores(view.definition, view.content_sha256, view.score_history)
  if ((effective.get(evaluationCellKey(body.cell))?.score_id ?? null) !== body.supersedes) fail('REVISION_CONFLICT', '修订必须追加到当前有效评分')
  // All dimensions of a fixed repetition must reference the same actual output.
  for (const score of view.score_history) {
    const cell = score.observation.cell
    if (cell.sample_id === body.cell.sample_id && cell.agent_version_id === body.cell.agent_version_id && cell.repeat_index === body.cell.repeat_index
        && body.output_ref && score.observation.output_ref && !same(body.output_ref, score.observation.output_ref)) mismatch('同一固定重复的各维度必须引用相同实际输出')
  }
}
/** Integrity check mirrors the server's fixed deterministic policy; no local result is presented as a server result. */
function expectedCellResult(definition: EvaluationDefinition, cell: EvaluationCell, score: EvaluationScore | undefined): EvaluationResult['cells'][number] {
  if (!score) return { cell, score_id: null, status: 'INSUFFICIENT_EVIDENCE', reason: 'MISSING_SCORE', value: null }
  const r = score.observation; let reason: string | null = null, value: string | null = null
  if (!evaluationExpectation(definition, cell)) reason = 'MISSING_EXPECTED_ITEM_SET'
  else if (!definition.approved_expectation_samples.includes(cell.sample_id)) reason = 'UNAPPROVED_EXPECTED_ITEM_SET'
  else if (r.status !== 'SCORED') reason = r.status + '_SCORE'
  else if (r.denominator === null || r.denominator === '0') reason = 'ZERO_OR_MISSING_DENOMINATOR'
  else if (r.output_ref === null || !r.evidence_refs.length) reason = 'MISSING_ACTUAL_OUTPUT_OR_EVIDENCE'
  else {
    // Exact integer arithmetic matches BigDecimal scale 4 / HALF_UP, without binary float rounding.
    const numerator = BigInt(r.numerator!) * 10_000n, denominator = BigInt(r.denominator)
    const rounded = numerator / denominator + (numerator % denominator * 2n >= denominator ? 1n : 0n)
    value = (rounded / 10_000n).toString() + '.' + (rounded % 10_000n).toString().padStart(4, '0')
    if (!['EVAL-02', 'EVAL-06', 'EVAL-07', 'EVAL-11', 'EVAL-12'].includes(cell.dimension)) reason = 'UNAPPROVED_STATISTICAL_THRESHOLD:' + cell.dimension
    else if (r.unknown_count === null || r.violation_count === null) reason = 'MISSING_INVARIANT_COUNTS'
    else if (cell.dimension === 'EVAL-11' && (r.control_denominator === null || r.control_denominator === '0')) reason = 'MISSING_LEGAL_CONTROL_DENOMINATOR'
  }
  if (reason !== null) return { cell, score_id: score.score_id, status: 'INSUFFICIENT_EVIDENCE', reason, value }
  const passed = r.numerator === r.denominator && r.unknown_count === '0' && r.violation_count === '0'
    && (cell.dimension !== 'EVAL-11' || r.control_numerator === r.control_denominator)
  return { cell, score_id: score.score_id, status: passed ? 'PASS' : 'FAIL', reason: passed ? 'DOCUMENTED_INVARIANTS_MET' : 'DOCUMENTED_INVARIANT_FAILED', value }
}
export async function verifyEvaluationRead(session: MedicalSession, id: string, input: unknown, resourceRevision: string | null): Promise<EvaluationRead> {
  const view = validate<EvaluationRead>(evaluationViewSchema + 'Read', input); positive(view.revision); positive(view.definition.authority_revision)
  const definition = view.definition; await validateEvaluationCreate(definition.request)
  if (view.evaluation_id !== id || definition.evaluation_id !== id || definition.tenant_id !== evaluationContext(session).tenant_id || view.revision !== resourceRevision
      || !evaluationAllowed(session, 'evaluation.read', definition.request.project_id, id) || await evaluationDigest(definition) !== view.content_sha256) mismatch('评测响应与当前企业、范围、固定定义摘要或修订不一致')
  if (definition.candidate_snapshots.length !== definition.request.candidates.length || new Set(definition.candidate_snapshots.map(item => item.version_id)).size !== definition.candidate_snapshots.length
      || definition.candidate_snapshots.some(candidate => !definition.request.candidates.some(item => item.version_id === candidate.version_id && item.content_sha256 === candidate.content_sha256))
      || definition.approved_expectation_samples.some(sample => !definition.request.samples.some(item => item.sample_id === sample))) mismatch('候选快照或获核准预期集合与定义不一致')
  const effective = effectiveEvaluationScores(definition, view.content_sha256, view.score_history)
  if (view.score_history.some((score, index) => score.observation.expected_revision !== String(index + 1)
      || definition.candidate_snapshots.some(candidate => candidate.version_id === score.observation.cell.agent_version_id && candidate.created_by === score.scorer_id))) mismatch('评分历史修订不连续或评分人员与候选创建人员不独立')
  const expectedRevision = BigInt(view.score_history.length) + (view.result ? 2n : 1n)
  if (BigInt(view.revision) !== expectedRevision || (view.result === null) !== (view.result_sha256 === null)) mismatch('评测修订、完整历史与终结记录不一致')
  if (!view.result) {
    if (view.status !== (view.score_history.length ? 'RUNNING' : 'QUEUED')) mismatch('未终结评测的状态与评分历史不一致')
    return view
  }
  const result = view.result, matrix = evaluationMatrix(definition.request)
  if (result.evaluation_id !== id || result.definition_sha256 !== view.content_sha256 || await evaluationDigest(result) !== view.result_sha256
      || await evaluationDigest(result.effective_scores) !== result.score_set_sha256 || result.effective_scores.length !== effective.size
      || result.effective_scores.some(score => !same(effective.get(evaluationCellKey(score.observation.cell)), score))
      || new Set(result.effective_scores.map(score => score.score_id)).size !== result.effective_scores.length
      || result.cells.length !== matrix.length || new Set(result.cells.map(cell => evaluationCellKey(cell.cell))).size !== result.cells.length) mismatch('固定结果摘要、有效评分或矩阵范围不一致')
  const cells = new Map(result.cells.map(item => [evaluationCellKey(item.cell), item]))
  const expectedMissing = new Set<string>()
  if (!definition.request.samples.length) expectedMissing.add('NO_SAMPLES')
  if (definition.request.environment === 'SIMULATION' || definition.request.samples.some(sample => sample.evidence_class === 'SIMULATION')) expectedMissing.add('SIMULATION_IS_NOT_BUSINESS_ACCEPTANCE')
  for (const cell of matrix) {
    const expected = expectedCellResult(definition, cell, effective.get(evaluationCellKey(cell)))
    if (!same(cells.get(evaluationCellKey(cell)), expected)) mismatch('结果单元的结论、数值或原因与固定观察不一致')
    if (expected.status === 'INSUFFICIENT_EVIDENCE') expectedMissing.add(expected.reason)
  }
  if (!same(result.missing_requirements, [...expectedMissing].sort())) mismatch('结果缺失要求与完整观察范围不一致')
  if (result.dimensions.length !== definition.request.required_dimensions.length || new Set(result.dimensions.map(item => item.dimension)).size !== result.dimensions.length) mismatch('结果维度统计不完整')
  for (const dimension of result.dimensions) {
    const items = result.cells.filter(item => item.cell.dimension === dimension.dimension)
    if (!definition.request.required_dimensions.includes(dimension.dimension) || dimension.expected_cells !== items.length || dimension.observed_cells !== items.filter(item => item.score_id !== null).length
        || dimension.passed_cells !== items.filter(item => item.status === 'PASS').length || dimension.failed_cells !== items.filter(item => item.status === 'FAIL').length
        || dimension.insufficient_cells !== items.filter(item => item.status === 'INSUFFICIENT_EVIDENCE').length) mismatch('结果统计与原始单元不一致')
  }
  const statuses = { PASS: 'COMPLETED', FAIL: 'FAILED', INSUFFICIENT_EVIDENCE: 'INSUFFICIENT_EVIDENCE' }
  if (view.status !== statuses[result.verdict] || result.verdict !== (result.missing_requirements.length ? 'INSUFFICIENT_EVIDENCE' : result.cells.some(cell => cell.status === 'FAIL') ? 'FAIL' : 'PASS')
      || result.cells.some(cell => cell.status === 'INSUFFICIENT_EVIDENCE') && !result.missing_requirements.length
      || (!definition.request.samples.length || definition.request.environment === 'SIMULATION' || definition.request.samples.some(sample => sample.evidence_class === 'SIMULATION')) && result.verdict !== 'INSUFFICIENT_EVIDENCE') mismatch('结果状态、证据缺项或仿真标记不一致')
  return view
}
function storageKey(session: MedicalSession): string { return 'medical.evaluation.write:' + session.user.user_id + ':' + evaluationContext(session).tenant_id }
export function readPendingEvaluation(session: MedicalSession): PendingEvaluation | null {
  const raw = sessionStorage.getItem(storageKey(session)); if (raw === null) return null
  const saved = validate<PendingEvaluation>(evaluationViewSchema + 'Pending', JSON.parse(raw)); validContext(saved.context)
  if (saved.expected_revision !== null) positive(saved.expected_revision)
  if (saved.actor !== session.user.user_id || saved.tenant_id !== evaluationContext(session).tenant_id || saved.context.tenant_id !== saved.tenant_id
      || (saved.operation === 'EV-01' ? saved.target_id !== null || saved.expected_revision !== null || saved.definition_sha256 !== null : saved.target_id === null || saved.expected_revision === null || saved.definition_sha256 === null)) mismatch('原评测请求身份或目标不一致')
  return saved
}
export async function prepareEvaluation(session: MedicalSession, operation: PendingEvaluation['operation'], body: EvaluationCreate | EvaluationAppend | EvaluationFinalize, view: EvaluationRead | null, signal?: AbortSignal): Promise<PendingEvaluation> {
  signal?.throwIfAborted(); const context = { ...evaluationContext(session) }
  const create = operation === 'EV-01', project = create ? (body as EvaluationCreate).project_id : view?.definition.request.project_id
  const target = create ? null : view?.evaluation_id
  if (project === undefined || target === undefined || !evaluationAllowed(session, create ? 'evaluation.create' : operation === 'EV-02' ? 'evaluation.score' : 'evaluation.finalize', project, target)
      || !evaluationAllowed(session, 'evaluation.read', project, target)) fail('ACCESS_DENIED', '评测写入与独立回读都需要当前明确权限')
  if (create) await validateEvaluationCreate(body)
  else if (operation === 'EV-02') validateEvaluationScoreTarget(view!, body as EvaluationAppend, session.user.user_id)
  else {
    validate(evaluationSchema + 'Finalize', body); positive((body as EvaluationFinalize).expected_revision)
    if (view!.result || !same(body, { schema_version: '1.0.0', expected_revision: view!.revision, definition_sha256: view!.content_sha256 })) fail('REVISION_CONFLICT', '终结需使用当前未终结的固定定义与修订')
  }
  const hash = await evaluationDigest(body); signal?.throwIfAborted()
  const saved = readPendingEvaluation(session)
  if (saved) {
    if (saved.operation !== operation || saved.target_id !== target || saved.project_id !== project || saved.body_sha256 !== hash || !same(saved.context, context)) fail('IDEMPOTENCY_CONFLICT', '原评测请求待核查；必须保留原参数、上下文和请求标识')
    return saved
  }
  if (!create && view!.revision === '18446744073709551615') fail('REVISION_LIMIT', '评测修订已达上限')
  const pending: PendingEvaluation = { actor: session.user.user_id, tenant_id: context.tenant_id, context, key: crypto.randomUUID(), operation, target_id: target,
    project_id: project, expected_revision: create ? null : view!.revision, definition_sha256: create ? null : view!.content_sha256, body_sha256: hash }
  validate(evaluationViewSchema + 'Pending', pending)
  // Persist identity and hashes only. Samples, observations, references and reasons stay in page memory.
  sessionStorage.setItem(storageKey(session), JSON.stringify(pending)); return pending
}
export async function verifyEvaluationRequest(session: MedicalSession, pending: PendingEvaluation, body: EvaluationCreate | EvaluationAppend | EvaluationFinalize): Promise<void> {
  const saved = readPendingEvaluation(session)
  if (!saved || !same(saved, pending) || !same(pending.context, evaluationContext(session)) || await evaluationDigest(body) !== pending.body_sha256
      || !evaluationAllowed(session, pending.operation === 'EV-01' ? 'evaluation.create' : pending.operation === 'EV-02' ? 'evaluation.score' : 'evaluation.finalize', pending.project_id, pending.target_id)
      || !evaluationAllowed(session, 'evaluation.read', pending.project_id, pending.target_id)) mismatch('当前身份、授权或完整参数与原评测请求不一致')
}
export function verifyEvaluationReceipt(pending: PendingEvaluation, input: unknown, resourceRevision: string | null): EvaluationReceipt {
  const receipt = validate<EvaluationReceipt>(evaluationSchema + 'Receipt', input); positive(receipt.revision)
  if (receipt.revision !== resourceRevision || pending.target_id !== null && receipt.evaluation_id !== pending.target_id || pending.definition_sha256 !== null && receipt.definition_sha256 !== pending.definition_sha256
      || receipt.revision !== (pending.operation === 'EV-01' ? '1' : (BigInt(pending.expected_revision!) + 1n).toString())) mismatch('评测回执与原目标、摘要或修订不一致')
  if (pending.operation === 'EV-01' ? receipt.status !== 'QUEUED' || receipt.score_id !== null || receipt.result_sha256 !== null || receipt.verdict !== null
    : pending.operation === 'EV-02' ? receipt.status !== 'RUNNING' || receipt.score_id === null || receipt.result_sha256 !== null || receipt.verdict !== null
      : receipt.score_id !== null || receipt.result_sha256 === null || receipt.verdict === null || receipt.status !== ({ PASS: 'COMPLETED', FAIL: 'FAILED', INSUFFICIENT_EVIDENCE: 'INSUFFICIENT_EVIDENCE' }[receipt.verdict])) mismatch('评测回执状态与原操作类型不一致')
  return receipt
}
export async function confirmEvaluationResult(session: MedicalSession, pending: PendingEvaluation, operation: Operation, view: EvaluationRead, receipt: EvaluationReceipt | null): Promise<void> {
  const saved = readPendingEvaluation(session)
  await verifyEvaluationRead(session, view.evaluation_id, view, view.revision)
  if (!saved || !same(saved, pending) || operation.status !== 'COMPLETED' || operation.resource_id !== view.evaluation_id || pending.target_id !== null && pending.target_id !== view.evaluation_id
      || view.definition.request.project_id !== pending.project_id || pending.definition_sha256 !== null && pending.definition_sha256 !== view.content_sha256) mismatch('原操作尚未取得一致的独立回读结果')
  if (receipt && (receipt.operation_id !== operation.operation_id || receipt.evaluation_id !== view.evaluation_id || receipt.definition_sha256 !== view.content_sha256 || BigInt(receipt.revision) > BigInt(view.revision))) mismatch('原回执与操作或固定定义不一致')
  if (pending.operation === 'EV-01') {
    if (view.definition.created_by !== pending.actor || await evaluationDigest(view.definition.request) !== pending.body_sha256) mismatch('固定定义与原创建请求不一致')
  } else if (pending.operation === 'EV-02') {
    const matches: EvaluationScore[] = []
    for (const score of view.score_history) if (score.scorer_id === pending.actor && await evaluationDigest(score.observation) === pending.body_sha256) matches.push(score)
    if (matches.length !== 1 || receipt && matches[0]!.score_id !== receipt.score_id) mismatch('完整评分历史未找到唯一的原评分或修订')
  } else {
    if (!view.result || view.revision !== (BigInt(pending.expected_revision!) + 1n).toString()
        || await evaluationDigest({ schema_version: '1.0.0', expected_revision: pending.expected_revision, definition_sha256: pending.definition_sha256 }) !== pending.body_sha256
        || receipt && (view.result_sha256 !== receipt.result_sha256 || view.result.verdict !== receipt.verdict)) mismatch('原终结操作与固定结果不一致')
  }
  // Recheck storage after async hashing so a replacement request can never be cleared.
  if (!same(readPendingEvaluation(session), pending)) mismatch('原评测请求身份已变化')
  sessionStorage.removeItem(storageKey(session))
}
export function evaluationFailureMessage(error: unknown): string {
  const code = error && typeof error === 'object' && 'code' in error ? String(error.code) : ''
  const messages: Record<string, string> = {
    ERR_NETWORK: '网络请求未取得确认结果，请查询原操作；不会自动重发或显示成功。',
    ECONNABORTED: '请求等待超时，请查询原操作，不能据此判定服务端未保存。',
    DEPENDENCY_UNAVAILABLE: '服务器缺少评测环境或制品授权档案，当前操作受阻。请完成服务端配置后查询原操作。',
    ACCESS_DENIED: '当前身份缺少所需授权，或评分人员与候选创建人员不独立。请核查企业、项目及评测权限。',
    CONTEXT_CHANGED: '企业上下文已变化，请重新读取会话并查询原操作。', REVISION_CONFLICT: '批次修订或有效评分已变化，请重新读取；原请求不会被覆盖。',
    RIGHTS_PENDING: '引用资料的当前来源权利尚未核准。请先完成权利审查。', SOURCE_NOT_READY: '引用资料尚未就绪，不能登记为有效证据。',
    OBJECT_UNAVAILABLE: '当前授权范围无法读取该批次或其依赖，请核查固定标识及权限。',
    IDEMPOTENCY_CONFLICT: '原请求身份与本次参数不一致，请先查询原操作；不会自动新建或替换请求。'
  }
  return messages[code] ?? (error instanceof Error ? error.message : '请求未取得可确认结果，请查询原操作；当前没有成功证据。')
}

export function evaluationRequirementLabel(reason: string): string {
  const labels: Record<string, string> = {
    NO_SAMPLES: '缺少固定样本', SIMULATION_IS_NOT_BUSINESS_ACCEPTANCE: '仿真来源不能用于真实业务验收', MISSING_SCORE: '尚未评分',
    MISSING_EXPECTED_ITEM_SET: '缺少固定预期项目集', UNAPPROVED_EXPECTED_ITEM_SET: '预期项目集尚未与核准规则绑定',
    UNKNOWN_SCORE: '观察结果未知', BLOCKED_SCORE: '观察受阻', ZERO_OR_MISSING_DENOMINATOR: '适用项目分母为零或缺失',
    MISSING_ACTUAL_OUTPUT_OR_EVIDENCE: '缺少实际输出或评分依据', MISSING_INVARIANT_COUNTS: '缺少未知或违反项目数量',
    MISSING_LEGAL_CONTROL_DENOMINATOR: '缺少有效的合法对照项目分母', DOCUMENTED_INVARIANTS_MET: '已声明要求全部满足',
    DOCUMENTED_INVARIANT_FAILED: '部分已声明要求未满足'
  }
  return labels[reason] ?? (reason.startsWith('UNAPPROVED_STATISTICAL_THRESHOLD:') ? reason.split(':')[1] + '统计阈值尚未核准' : reason)
}
