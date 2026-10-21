import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { canonicalizeEx } from 'json-canonicalize';
import request from '@/utils/request';
import contract from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-plan-preparation.schema.json';
import contentContract from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-plan-preparation-content.schema.json';
import { envelope, fail, validContext } from './protocol';
import type { Basis, Context, MedicalSession } from './protocol';
import type { EvaluationRead } from './evaluation-domain';
import type { PlanCandidate } from './task-plans';
import type { TemplateConditionResult } from './template-applicability';
import { sourceDigest } from './content-digest';
import { projectJsonIssue } from './project-json';
import { readN01Json } from './n01-json';
import { sameN01Basis, n01OriginalCoverage } from './n01-plan-selection';
export type N01Reader = () => MedicalSession;
export interface N01Start {
    schema_version: '1.0.0';
    evaluation_id: string;
    definition_sha256: string;
    expected_evaluation_revision: string;
    candidate: Basis;
    execution_policy: Basis;
    enterprise_budget: Basis;
    parent_budget: Basis;
}
export interface N01BoundStart {
    schema_version: '1.0.0';
    start: N01Start;
    context_binding: Basis;
}
export interface N01Request {
    schema_version: '1.0.0';
    execution_id: string;
    start: N01Start;
    sample_id: string;
    repeat_index: number;
    node_kind: 'N01';
    template: Basis | null;
}
export interface N01Registration {
    schema_version: '1.0.0';
    execution_id: string;
    request_sha256: string;
    expected_cells: number;
    expected_nodes: number;
}
export interface N01Coverage {
    scope: 'SELECTED_CELL';
    expected_cells: number;
    expected_nodes: number;
    selected_cells: 1;
    professional_completed_cells: 0;
    complete_evaluation: false;
}
export interface N01Choice {
    template: Basis;
    document_type: string;
    applicability: 'APPLICABLE' | 'NOT_APPLICABLE' | 'UNKNOWN';
    eligible: boolean;
}
export type N01BlockerCode = 'TEMPLATE_CHOICE_REQUIRED' | 'NO_COMPATIBLE_TEMPLATE' | 'APPLICABILITY_UNKNOWN' | 'OWNER_UNASSIGNED' | 'COMPLETION_RULE_MISSING' | 'METHOD_REVIEW_REQUIRED';
export interface N01Preview {
    schema_version: '1.0.0';
    preparation_kind: 'DETERMINISTIC_PLAN_DRAFT';
    execution_id: string;
    evaluation_id: string;
    candidate: Basis;
    execution_policy: Basis;
    context_binding: Basis;
    source_snapshot: Basis;
    profile_revision: string;
    profile_sha256: string;
    sample_id: string;
    repeat_index: number;
    document_type: string;
    coverage: N01Coverage;
    templates: N01Choice[];
    selected_template: Basis | null;
    selection_state: 'NO_COMPATIBLE_TEMPLATE' | 'CHOICE_REQUIRED' | 'SELECTED';
    plan_candidate: PlanCandidate | null;
    conditions: TemplateConditionResult[];
    blockers: {
        code: N01BlockerCode;
        target_ids: string[];
    }[];
    action_summary: {
        action: 'artifact.register';
        resource_kind: 'EVALUATION';
        resource_id: string;
        project_id: string;
        purpose: 'EVALUATION_RESULTS';
        allowed: boolean;
    }[];
    runnable: false;
    professional_completion: false;
}
export interface N01Prepared {
    schema_version: '1.0.0';
    operation_id: string;
    write_id: string;
    attempt_id: string;
    execution_id: string;
    artifact_id: string;
    artifact_sha256: string;
    object_version: string;
    receipt_sha256: string;
    preview: N01Preview;
    state: 'PREPARED';
    runnable: false;
    professional_completion: false;
}
export type N01Identity = Omit<N01Prepared, 'preview'>;
export interface N01Content {
    schema_version: '1.0.0';
    receipt: N01Identity;
    plan_candidate: PlanCandidate;
    original_preview: N01Preview;
    runnable: false;
    professional_completion: false;
}
export interface N01Status {
    schema_version: '1.0.0';
    state: 'NOT_FOUND' | 'PENDING' | 'PREPARED';
    content_available: false;
    receipt: N01Identity | null;
}
export interface N01Availability {
    schema_version: '1.0.0';
    features: {
        bound_preparation: boolean;
        execution_recovery: boolean;
        plan_preview: boolean;
        plan_preparation: boolean;
        status: boolean;
        object_recovery: boolean;
    };
    unavailable_reason: null | 'FEATURE_DISABLED' | 'DEPENDENCY_UNAVAILABLE';
}
export interface N01Pending {
    schema_version: '1.0.0';
    actor: string;
    tenant_id: string;
    context: Context;
    project_id: string;
    evaluation_id: string;
    kind: 'EXECUTION' | 'PLAN';
    key: string;
    body: N01BoundStart | N01Request;
    body_sha256: string;
    identity_sha256: string;
    phase: 'UNKNOWN';
    receipt: N01Registration | N01Prepared | null;
}
export const n01Canonical = (value: unknown): string => canonicalizeEx(value, { strictUndefined: true });
export const n01Copy = <T>(value: T): T => JSON.parse(n01Canonical(value)) as T;
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true });
addFormats(ajv);
ajv.addSchema(contract);
ajv.addSchema(contentContract);
const prefix = contract.$id + '#/$defs/', base = '/api/formal-evaluation-plan-preparations';
const pendingCheck = ajv.compile<N01Pending>({ type: 'object', additionalProperties: false, required: ['schema_version', 'actor', 'tenant_id', 'context', 'project_id', 'evaluation_id', 'kind', 'key', 'body', 'body_sha256', 'identity_sha256', 'phase', 'receipt'], properties: { schema_version: { const: '1.0.0' }, actor: { $ref: prefix + 'Id' }, tenant_id: { $ref: prefix + 'Id' }, context: { type: 'object', additionalProperties: false, required: ['tenant_id', 'context_revision', 'auth_epoch', 'governance_epoch'], properties: { tenant_id: { $ref: prefix + 'Id' }, context_revision: { type: 'string', pattern: '^(0|[1-9][0-9]{0,19})$' }, auth_epoch: { type: 'string', pattern: '^(0|[1-9][0-9]{0,19})$' }, governance_epoch: { type: 'string', pattern: '^(0|[1-9][0-9]{0,19})$' } } }, project_id: { $ref: prefix + 'Id' }, evaluation_id: { $ref: prefix + 'Id' }, kind: { enum: ['EXECUTION', 'PLAN'] }, key: { $ref: prefix + 'KeyOriginal' }, body: { oneOf: [{ $ref: prefix + 'BoundStart' }, { $ref: prefix + 'Request' }] }, body_sha256: { $ref: prefix + 'Hash' }, identity_sha256: { $ref: prefix + 'Hash' }, phase: { const: 'UNKNOWN' }, receipt: { oneOf: [{ type: 'null' }, { $ref: prefix + 'Registration' }, { $ref: prefix + 'Prepared' }] } } });
function checked<T>(name: string, value: unknown): T { const check = ajv.getSchema(prefix + name); if (!check || !check(value))
    fail('CONTENT_MISMATCH', 'N01 计划响应不符合固定合同：' + (check?.errors?.[0]?.instancePath || name)); return value as T; }
function headers(context: Context) { return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }; }
function fingerprint(session: MedicalSession): string { return n01Canonical({ actor: session.user.user_id, context: session.current_context, csrf: session.csrf, actions: session.action_summary }); }
function guard(reader: N01Reader, signal: AbortSignal) { const session = reader(); if (!session.current_context)
    fail('ACCESS_DENIED', '请先恢复身份并选择企业'); validContext(session.current_context); const context = n01Copy(session.current_context), identity = fingerprint(session); const assert = () => { signal.throwIfAborted(); if (reader() !== session || fingerprint(reader()) !== identity)
    fail('CONTEXT_CHANGED', '身份、授权或 CSRF 已变化，请重新读取'); }; assert(); return { session, context, assert }; }
/** Exact current evaluation scope, with existing enterprise/project inheritance only. */
export function n01Allowed(session: MedicalSession | null, project: string | null, evaluation: string, action: 'evaluation.read' | 'evaluation.run' | 'artifact.register'): boolean { const tenant = session?.current_context?.tenant_id; return !!tenant && !!project && session!.action_summary.some(item => item.action === action && (item.resource_kind === 'ENTERPRISE' && item.resource_id === tenant && item.project_id === null || item.resource_kind === 'PROJECT' && item.resource_id === project && item.project_id === project || item.resource_kind === 'EVALUATION' && (item.project_id === null || item.project_id === project) && (item.resource_id === null || item.resource_id === evaluation))); }
export function n01MayPrepare(session: MedicalSession | null, project: string, preview: N01Preview): boolean { return preview.selection_state === 'SELECTED' && preview.plan_candidate !== null && n01Allowed(session, project, preview.evaluation_id, 'artifact.register') && preview.action_summary.some(item => item.action === 'artifact.register' && item.resource_kind === 'EVALUATION' && item.resource_id === preview.evaluation_id && item.project_id === project && item.purpose === 'EVALUATION_RESULTS' && item.allowed); }
export function n01RequestIssue(value: unknown, kind: 'EXECUTION' | 'PLAN'): string { const check = ajv.getSchema(prefix + (kind === 'EXECUTION' ? 'BoundStart' : 'Request'))!; if (!check(value))
    return '请先选择完整的固定候选、已批准上下文、预算和样本'; if (new TextEncoder().encode(n01Canonical(value)).byteLength > 1048576)
    return '完整请求超过 1 MiB，不能提交'; return ''; }
function startOf(body: N01BoundStart | N01Request): N01Start { return body.start; }
export function n01VerifyDefinition(start: N01Start, view: EvaluationRead, historical = false): void { if (start.evaluation_id !== view.evaluation_id || start.definition_sha256 !== view.content_sha256 || (historical ? BigInt(start.expected_evaluation_revision) > BigInt(view.revision) : start.expected_evaluation_revision !== view.revision) || !view.definition.request.candidates.some(item => sameN01Basis(item, start.candidate)))
    fail('CONTENT_MISMATCH', '准备请求不属于当前完整评测定义及固定候选'); }
export async function n01VerifyRegistration(input: unknown, body: N01BoundStart, view: EvaluationRead, historical = false): Promise<N01Registration> { const value = checked<N01Registration>('Registration', input); n01VerifyDefinition(body.start, view, historical); const coverage = n01OriginalCoverage(view); if (value.request_sha256 !== await sourceDigest(body.start) || value.expected_cells !== coverage.samples * coverage.repeats || value.expected_nodes !== value.expected_cells)
    fail('CONTENT_MISMATCH', '原执行登记摘要或完整 N01 矩阵覆盖不一致'); return n01Copy(value); }
export function n01VerifyPreview(input: unknown, body: N01Request, bound: N01BoundStart, registration: N01Registration, view: EvaluationRead, historical = false): N01Preview {
    const value = checked<N01Preview>('Preview', input);
    n01VerifyDefinition(body.start, view, historical);
    const project = view.definition.request.project_id, sample = view.definition.request.samples.find(item => item.sample_id === body.sample_id);
    if (!sample || body.repeat_index >= view.definition.request.variance_policy.repeat_count || body.execution_id !== registration.execution_id || n01Canonical(body.start) !== n01Canonical(bound.start) || value.execution_id !== body.execution_id || value.evaluation_id !== body.start.evaluation_id || !sameN01Basis(value.candidate, body.start.candidate) || !sameN01Basis(value.execution_policy, body.start.execution_policy) || !sameN01Basis(value.context_binding, bound.context_binding) || value.sample_id !== body.sample_id || value.repeat_index !== body.repeat_index || value.document_type !== sample.document_type || value.coverage.expected_cells !== registration.expected_cells || value.coverage.expected_nodes !== registration.expected_nodes || value.action_summary.some(item => item.resource_id !== view.evaluation_id || item.project_id !== project) || new Set(value.templates.map(item => item.template.version_id)).size !== value.templates.length)
        fail('CONTENT_MISMATCH', '计划预览身份、原矩阵覆盖或操作范围不一致');
    const eligible = value.templates.filter(item => item.eligible), selected = value.selected_template, plan = value.plan_candidate;
    if (value.templates.some(item => item.eligible && item.applicability === 'NOT_APPLICABLE') || (value.selection_state === 'SELECTED') !== !!selected || !!selected !== !!plan || selected && !eligible.some(item => sameN01Basis(item.template, selected)) || body.template && (!selected || !sameN01Basis(selected, body.template)) || body.template === null && eligible.length !== 1 && selected !== null || value.selection_state === 'CHOICE_REQUIRED' && eligible.length < 2 || value.selection_state === 'NO_COMPATIBLE_TEMPLATE' && eligible.length !== 0)
        fail('CONTENT_MISMATCH', '模板选择必须来自完整候选清单；多模板不能自动选择');
    if (plan && selected) {
        const covered = plan.sections.flatMap(section => section.requirement_node_ids), nodes = plan.requirement_node_ids, unknown = [...new Set(value.conditions.filter(item => item.status === 'UNKNOWN').map(item => item.target_id))].sort();
        if (plan.template_version !== selected.version_id || plan.profile_revision !== value.profile_revision || !nodes.length || covered.length !== nodes.length || new Set(covered).size !== nodes.length || covered.some(id => !nodes.includes(id)) || new Set(plan.sections.map(item => item.section_id)).size !== plan.sections.length || n01Canonical([...plan.pending_condition_ids].sort()) !== n01Canonical(unknown) || n01Canonical([...nodes].sort()) !== n01Canonical(value.conditions.filter(item => item.scope === 'NODE').map(item => item.target_id).sort()) || new Set(value.conditions.map(item => item.scope + ':' + item.target_id)).size !== value.conditions.length || value.conditions.filter(item => item.scope === 'PROFILE' && item.target_id === project).length !== 1 || value.conditions.filter(item => item.scope === 'TEMPLATE' && item.target_id === selected.version_id).length !== 1 || value.conditions.some(item => item.unknown_input_keys.some(key => !item.input_keys.includes(key))))
            fail('CONTENT_MISMATCH', '计划目录覆盖或待确认条件不完整');
    }
    return n01Copy(value);
}
export const n01StorageKey = (s: MedicalSession, evaluation: string, kind: N01Pending['kind']) => 'medical.n01-plan:' + s.user.user_id + ':' + s.current_context?.tenant_id + ':' + evaluation + ':' + kind;
function identity(p: N01Pending) { return { actor: p.actor, tenant_id: p.tenant_id, context: p.context, project_id: p.project_id, evaluation_id: p.evaluation_id, kind: p.kind, key: p.key, body_sha256: p.body_sha256 }; }
export function readN01Pending(s: MedicalSession, evaluation: string, kind: N01Pending['kind']): N01Pending | null { const raw = sessionStorage.getItem(n01StorageKey(s, evaluation, kind)); if (raw === null)
    return null; if (new TextEncoder().encode(raw).byteLength > 4000000 || projectJsonIssue(raw))
    fail('CONTENT_MISMATCH', '原 N01 请求记录损坏，请保留并人工核查'); const p: unknown = readN01Json(raw); if (!pendingCheck(p) || p.actor !== s.user.user_id || p.tenant_id !== s.current_context?.tenant_id || p.context.tenant_id !== p.tenant_id || p.evaluation_id !== evaluation || p.kind !== kind || startOf(p.body).evaluation_id !== evaluation || n01RequestIssue(p.body, kind) || p.receipt && (kind === 'EXECUTION' ? !('expected_cells' in p.receipt) : !('artifact_id' in p.receipt)))
    fail('CONTENT_MISMATCH', '原 N01 请求身份、类型或完整正文不一致'); return n01Copy(p); }
/** Synchronous fence: callers use this after their final await, immediately before transport or projection. */
export function assertN01Pending(s: MedicalSession, p: N01Pending): void {
    if (n01Canonical(readN01Pending(s, p.evaluation_id, p.kind)) !== n01Canonical(p))
        fail('CONTENT_MISMATCH', '原 N01 请求正文、键或身份摘要已变化');
}
export async function verifyN01Pending(s: MedicalSession, p: N01Pending): Promise<void> {
    const frozen = n01Copy(p), exact = n01Canonical(frozen);
    assertN01Pending(s, frozen);
    const bodyHash = await sourceDigest(frozen.body), identityHash = await sourceDigest(identity(frozen));
    if (bodyHash !== frozen.body_sha256 || identityHash !== frozen.identity_sha256 || n01Canonical(p) !== exact)
        fail('CONTENT_MISMATCH', '原 N01 请求正文、键或身份摘要已变化');
    assertN01Pending(s, frozen);
}
function persist(s: MedicalSession, p: N01Pending, previous?: N01Pending) { if (!pendingCheck(p))
    fail('CONTENT_MISMATCH', '不能保存不完整原请求'); if (previous) assertN01Pending(s, previous); sessionStorage.setItem(n01StorageKey(s, p.evaluation_id, p.kind), n01Canonical(p)); if (n01Canonical(readN01Pending(s, p.evaluation_id, p.kind)) !== n01Canonical(p))
    fail('CONTENT_MISMATCH', '原请求未可靠保存，本次不会发送'); }
async function preparePending(reader: N01Reader, kind: N01Pending['kind'], body: N01Pending['body'], project: string, signal: AbortSignal): Promise<N01Pending> { const g = guard(reader, signal), frozen = n01Copy(body), issue = n01RequestIssue(frozen, kind); if (issue)
    throw new Error(issue); const evaluation = startOf(body).evaluation_id; if (readN01Pending(g.session, evaluation, kind))
    fail('IDEMPOTENCY_CONFLICT', '已有原请求，仅可只读核查，不会生成新键或重发'); const hash = await sourceDigest(frozen); g.assert(); const basePending = { schema_version: '1.0.0' as const, actor: g.session.user.user_id, tenant_id: g.context.tenant_id, context: g.context, project_id: project, evaluation_id: evaluation, kind, key: crypto.randomUUID(), body: frozen, body_sha256: hash, identity_sha256: '', phase: 'UNKNOWN' as const, receipt: null }; basePending.identity_sha256 = await sourceDigest(identity(basePending)); g.assert(); if (readN01Pending(g.session, evaluation, kind))
    fail('IDEMPOTENCY_CONFLICT', '另一原请求已保存，请先核查'); persist(g.session, basePending); return n01Copy(basePending); }
export function n01FailureMessage(failure: unknown): string { const code = failure && typeof failure === 'object' && 'code' in failure ? String(failure.code) : ''; if (code === 'ACCESS_DENIED')
    return '当前身份没有此次操作的实际授权，请核查当前项目和资源许可。'; if (code === 'CONTEXT_CHANGED' || code === 'CONTEXT_STALE')
    return '身份或授权上下文已变化，请重新读取；原请求不会自动重发。'; if (code === 'DEPENDENCY_UNAVAILABLE')
    return '当前部署缺少此次操作所需依赖，仍可单独查询原准备状态。'; if (code === 'OBJECT_UNAVAILABLE')
    return '原对象当前无法读取，仍可单独查询原准备状态。'; if (code === 'ACTION_STALE' || code === 'SCOPE_STALE')
    return '固定依据或当前资格已变化，请重新读取并核查原请求。'; return failure && typeof failure === 'object' && 'message' in failure ? String(failure.message) : '请求结果尚不明确，请只读核查原操作；不会自动重发。'; }
function responseData<T>(raw: any, name: string, status: number): T { const e = envelope(raw); if (raw.status !== status || e.resource_revision !== '1')
    fail('CONTENT_MISMATCH', 'N01 响应状态或固定修订不一致'); return checked<T>(name, e.data); }
export async function readN01Availability(reader: N01Reader, signal: AbortSignal): Promise<N01Availability> { const g = guard(reader, signal), raw = await request.get('/api/formal-evaluation-plan-feature-availability', { medical: 'envelope', signal, headers: headers(g.context) }); g.assert(); return n01Copy(responseData<N01Availability>(raw, 'Availability', 200)); }
async function requireFeature(reader: N01Reader, feature: keyof N01Availability['features'], signal: AbortSignal) { const value = await readN01Availability(reader, signal); if (!value.features[feature])
    throw new Error(value.unavailable_reason === 'DEPENDENCY_UNAVAILABLE' ? '当前部署缺少 N01 准备所需依赖。' : '当前部署尚未启用 N01 计划准备。'); }
function checkPendingView(p: N01Pending, view: EvaluationRead, kind: N01Pending['kind']) { if (p.kind !== kind || p.project_id !== view.definition.request.project_id || p.evaluation_id !== view.evaluation_id)
    fail('CONTENT_MISMATCH', '原请求与当前批次或项目不一致'); }
async function remember(reader: N01Reader, p: N01Pending, receipt: N01Registration | N01Prepared, signal: AbortSignal): Promise<N01Pending> { const g = guard(reader, signal); p = n01Copy(p); await verifyN01Pending(g.session, p); g.assert(); if (p.receipt && n01Canonical(p.receipt) !== n01Canonical(receipt))
    fail('CONTENT_MISMATCH', '回读与已保存的原回执不一致'); const saved = { ...n01Copy(p), receipt: n01Copy(receipt) }; persist(g.session, saved, p); return saved; }
export async function startN01Execution(reader: N01Reader, body: N01BoundStart, view: EvaluationRead, signal: AbortSignal, onPending: (p: N01Pending) => void): Promise<N01Pending> { const g = guard(reader, signal); n01VerifyDefinition(body.start, view); const project = view.definition.request.project_id; if (!project || !n01Allowed(g.session, project, view.evaluation_id, 'evaluation.run') || !n01Allowed(g.session, project, view.evaluation_id, 'evaluation.read'))
    fail('ACCESS_DENIED', '当前批次没有原执行准备权限'); await requireFeature(reader, 'bound_preparation', signal); g.assert(); const pending = await preparePending(reader, 'EXECUTION', body, project, signal); onPending(n01Copy(pending)); g.assert(); assertN01Pending(g.session, pending); const raw = await request.post(base + '/executions', n01Copy(pending.body), { medical: 'envelope', signal, headers: { ...headers(g.context), 'Idempotency-Key': pending.key, [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); const registration = await n01VerifyRegistration(responseData(raw, 'Registration', 201), body, view); g.assert(); return remember(reader, pending, registration, signal); }
export async function recoverN01Execution(reader: N01Reader, pending: N01Pending, view: EvaluationRead, signal: AbortSignal): Promise<N01Pending> { const g = guard(reader, signal); pending = n01Copy(pending); checkPendingView(pending, view, 'EXECUTION'); await verifyN01Pending(g.session, pending); g.assert(); const body = { schema_version: '1.0.0', original_key: pending.key, original_request: n01Copy(pending.body) }; checked('BoundRecovery', body); assertN01Pending(g.session, pending); const raw = await request.post(base + '/executions/recovery', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); const registration = await n01VerifyRegistration(responseData(raw, 'Registration', 200), pending.body as N01BoundStart, view, true); g.assert(); return remember(reader, pending, registration, signal); }
export async function previewN01Plan(reader: N01Reader, body: N01Request, bound: N01BoundStart, registration: N01Registration, view: EvaluationRead, signal: AbortSignal): Promise<N01Preview> { const g = guard(reader, signal), issue = n01RequestIssue(body, 'PLAN'); if (issue)
    throw new Error(issue); if (!n01Allowed(g.session, view.definition.request.project_id, view.evaluation_id, 'evaluation.read'))
    fail('ACCESS_DENIED', '当前批次没有读取权限'); const raw = await request.post(base + '/preview', n01Copy(body), { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); return n01VerifyPreview(responseData(raw, 'Preview', 200), body, bound, registration, view); }
export async function n01VerifyPrepared(input: unknown, body: N01Request, bound: N01BoundStart, registration: N01Registration, view: EvaluationRead): Promise<N01Prepared> { const value = checked<N01Prepared>('Prepared', input), preview = n01VerifyPreview(value.preview, body, bound, registration, view); if (value.execution_id !== body.execution_id || preview.selection_state !== 'SELECTED' || !preview.plan_candidate || value.artifact_sha256 !== await sourceDigest(preview.plan_candidate))
    fail('CONTENT_MISMATCH', '准备制品与完整计划内容摘要不一致'); return n01Copy(value); }
export async function prepareN01Plan(reader: N01Reader, body: N01Request, preview: N01Preview, bound: N01BoundStart, registration: N01Registration, view: EvaluationRead, signal: AbortSignal, onPending: (p: N01Pending) => void): Promise<N01Pending> { const g = guard(reader, signal), project = view.definition.request.project_id; if (!project || !n01MayPrepare(g.session, project, preview))
    fail('ACCESS_DENIED', '当前固定单元没有计划制品登记权限'); await requireFeature(reader, 'plan_preparation', signal); g.assert(); const fresh = await previewN01Plan(reader, body, bound, registration, view, signal); g.assert(); if (n01Canonical(fresh) !== n01Canonical(preview))
    fail('SCOPE_STALE', '模板、项目属性或授权已变化，请重新预览并确认'); const pending = await preparePending(reader, 'PLAN', body, project, signal); onPending(n01Copy(pending)); g.assert(); assertN01Pending(g.session, pending); const raw = await request.post(base, n01Copy(pending.body), { medical: 'envelope', signal, headers: { ...headers(g.context), 'Idempotency-Key': pending.key, [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); const prepared = await n01VerifyPrepared(responseData(raw, 'Prepared', 201), body, bound, registration, view); g.assert(); return remember(reader, pending, prepared, signal); }
export async function readN01PlanStatus(reader: N01Reader, pending: N01Pending, view: EvaluationRead, signal: AbortSignal): Promise<{
    pending: N01Pending;
    status: N01Status;
}> {
    const g = guard(reader, signal);
    pending = n01Copy(pending);
    checkPendingView(pending, view, 'PLAN');
    if (!n01Allowed(g.session, pending.project_id, pending.evaluation_id, 'evaluation.read'))
        fail('ACCESS_DENIED', '当前批次没有原准备状态读取权限');
    await verifyN01Pending(g.session, pending);
    g.assert();
    const body = { schema_version: '1.0.0', original_key: pending.key, original_request: n01Copy(pending.body) };
    checked('Recovery', body);
    assertN01Pending(g.session, pending);
    const raw = await request.post(base + '/status', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } });
    g.assert();
    const status = responseData<N01Status>(raw, 'Status', 200);
    if ((status.state === 'PREPARED') !== (status.receipt !== null))
        fail('CONTENT_MISMATCH', '原准备状态与身份回执不一致');
    if (status.receipt) {
        if (status.receipt.execution_id !== (pending.body as N01Request).execution_id)
            fail('CONTENT_MISMATCH', '原准备身份不属于原执行');
        if (pending.receipt && 'preview' in pending.receipt) {
            const { preview: _, ...identity } = pending.receipt;
            if (n01Canonical(identity) !== n01Canonical(status.receipt))
                fail('CONTENT_MISMATCH', '状态身份与原准备回执不一致');
        }
    }
    await verifyN01Pending(g.session, pending);
    g.assert();
    assertN01Pending(g.session, pending);
    return { pending: n01Copy(pending), status: n01Copy(status) };
}
/** Explicit read-only reopen. Fresh original metadata is a selector; only the server's typed GET can return content. */
export async function readN01PlanContent(reader: N01Reader, pending: N01Pending, bound: N01BoundStart, registration: N01Registration, view: EvaluationRead, signal: AbortSignal): Promise<N01Content> {
    const g = guard(reader, signal);
    pending = n01Copy(pending);
    checkPendingView(pending, view, 'PLAN');
    n01VerifyDefinition((pending.body as N01Request).start, view, true);
    const fresh = await readN01PlanStatus(reader, pending, view, signal);
    g.assert();
    if (!fresh.status.receipt || fresh.status.state !== 'PREPARED')
        fail('OBJECT_UNAVAILABLE', '原准备尚未登记完成，不能重新打开内容');
    const body = { schema_version: '1.0.0', recovery: { schema_version: '1.0.0', original_key: pending.key, original_request: n01Copy(pending.body) }, expected_receipt: n01Copy(fresh.status.receipt) };
    const checkRequest = ajv.getSchema(contentContract.$id + '#/$defs/Request')!;
    if (!checkRequest(body)) fail('CONTENT_MISMATCH', '历史内容请求不符合固定合同');
    if (new TextEncoder().encode(n01Canonical(body)).byteLength > 1048576) fail('FILE_TOO_LARGE', '完整历史内容请求超过 1 MiB');
    assertN01Pending(g.session, pending);
    const raw = await request.post(base + '/content', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } });
    g.assert();
    const e = envelope(raw), check = ajv.getSchema(contentContract.$id + '#/$defs/Content')!;
    if (raw.status !== 200 || e.resource_revision !== '1' || !check(e.data))
        fail('CONTENT_MISMATCH', '历史计划内容响应不符合固定合同');
    const value = e.data as N01Content;
    const preview = n01VerifyPreview(value.original_preview, pending.body as N01Request, bound, registration, view, true);
    if (n01Canonical(value.receipt) !== n01Canonical(fresh.status.receipt) || !preview.plan_candidate || preview.selection_state !== 'SELECTED' || preview.action_summary.length !== 0 || n01Canonical(value.plan_candidate) !== n01Canonical(preview.plan_candidate) || value.receipt.artifact_sha256 !== await sourceDigest(value.plan_candidate))
        fail('CONTENT_MISMATCH', '重新读取的计划、原对象版本或原回执不一致');
    await verifyN01Pending(g.session, pending);
    g.assert();
    assertN01Pending(g.session, pending);
    return n01Copy(value);
}
/** Explicit write-capable recovery may finalize the original registration, but never creates a key or uploads again. */
export async function recoverN01Plan(reader: N01Reader, pending: N01Pending, bound: N01BoundStart, registration: N01Registration, view: EvaluationRead, signal: AbortSignal): Promise<N01Pending> { const g = guard(reader, signal); pending = n01Copy(pending); checkPendingView(pending, view, 'PLAN'); if (!n01Allowed(g.session, pending.project_id, pending.evaluation_id, 'artifact.register') || !n01Allowed(g.session, pending.project_id, pending.evaluation_id, 'evaluation.run'))
    fail('ACCESS_DENIED', '完成原登记需要当前评测执行与制品登记授权'); await requireFeature(reader, 'object_recovery', signal); g.assert(); await verifyN01Pending(g.session, pending); g.assert(); const body = { schema_version: '1.0.0', original_key: pending.key, original_request: n01Copy(pending.body) }; checked('Recovery', body); assertN01Pending(g.session, pending); const raw = await request.post(base + '/recovery', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); const value = await n01VerifyPrepared(responseData(raw, 'Prepared', 200), pending.body as N01Request, bound, registration, view); g.assert(); return remember(reader, pending, value, signal); }

/** Explicit continuation of the immutable original request, never a replacement attempt.
 * The server serializes the original key and makes any existing physical attempt recovery-only. */
export async function previewN01PlanContinuation(reader: N01Reader, pending: N01Pending, bound: N01BoundStart,
    registration: N01Registration, view: EvaluationRead, signal: AbortSignal): Promise<N01Preview> {
    const g = guard(reader, signal);
    pending = n01Copy(pending);
    checkPendingView(pending, view, 'PLAN');
    if (pending.receipt || n01Canonical(pending.context) !== n01Canonical(g.context))
        fail('ACTION_STALE', '原请求已有回执或授权上下文已变化，只能核查原记录');
    n01VerifyDefinition(pending.body.start, view);
    if (!n01Allowed(g.session, pending.project_id, pending.evaluation_id, 'evaluation.run') ||
        !n01Allowed(g.session, pending.project_id, pending.evaluation_id, 'artifact.register'))
        fail('ACCESS_DENIED', '继续原准备需要当前评测执行与制品登记授权');
    await verifyN01Pending(g.session, pending); g.assert();
    const result = await readN01PlanStatus(reader, pending, view, signal); g.assert();
    if (result.status.state !== 'NOT_FOUND')
        fail('ACTION_STALE', '原准备已经登记，只能查询状态或核查原对象');
    await requireFeature(reader, 'plan_preparation', signal); g.assert();
    const preview = await previewN01Plan(reader, pending.body as N01Request, bound, registration, view, signal); g.assert();
    if (!n01MayPrepare(g.session, pending.project_id, preview))
        fail('ACCESS_DENIED', '原固定单元当前不可登记');
    await verifyN01Pending(g.session, pending); g.assert(); assertN01Pending(g.session, pending);
    return preview;
}
export async function continueN01Plan(reader: N01Reader, pending: N01Pending, confirmedPreview: N01Preview,
    bound: N01BoundStart, registration: N01Registration, view: EvaluationRead, signal: AbortSignal): Promise<N01Pending> {
    const g = guard(reader, signal);
    pending = n01Copy(pending);
    confirmedPreview = n01Copy(confirmedPreview);
    const fresh = await previewN01PlanContinuation(reader, pending, bound, registration, view, signal); g.assert();
    if (n01Canonical(fresh) !== n01Canonical(confirmedPreview))
        fail('ACTION_STALE', '原准备预览已变化，请重新核查并确认');
    assertN01Pending(g.session, pending);
    const raw = await request.post(base, n01Copy(pending.body), { medical: 'envelope', signal,
        headers: { ...headers(g.context), 'Idempotency-Key': pending.key, [g.session.csrf.header_name]: g.session.csrf.token } });
    g.assert();
    const value = await n01VerifyPrepared(responseData(raw, 'Prepared', 201), pending.body as N01Request, bound, registration, view);
    g.assert();
    return remember(reader, pending, value, signal);
}
