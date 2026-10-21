import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import request from '@/utils/request';
import candidateSchema from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication.schema.json';
import journalSchema from '../../../../business/medical-persistence/src/main/resources/agent-publication.schema.json';
import policySchema from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-policy.schema.json';
import scoreSchema from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-score-binding.schema.json';
import availabilitySchema from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication-feature-availability.schema.json';
import policyReadSchema from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-policy-read.schema.json';
import disableRecoverySchema from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication-disable-recovery.schema.json';
import workflowSchema from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication-workflow.schema.json';
import { envelope, fail, validContext, MedicalFailure } from './protocol';
import type { Basis, Context, MedicalSession } from './protocol';
import { capabilityCanonical as canonical, detached, capabilityGuard } from './formal-capability-domain';
import { sourceDigest } from './content-digest';
import { projectJsonIssue } from './project-json';
import { projectTextIssue } from './project-text';
import { lookupOperation, readOperation } from './operations';
import type { OperationLookup } from './operations';
import { readDraft } from './formal-agent-drafts';
export { canonical, detached };
export type PublicationReader = () => MedicalSession;
export type PublicationKind = 'SCORE_BIND' | 'ACCEPTANCE' | 'PUBLISH' | 'DISABLE';
export interface PublicationAvailability {
    schema_version: '1.0.0';
    current_context: Context;
    features: {
        acceptance_routes: boolean;
        publication_routes: boolean;
        professional_completion_registered: boolean;
    };
}
export interface CandidateSelection {
    record_id: string;
    basis: Basis;
    project_id: string | null;
    name: string;
    status: string;
    revision: string;
    created_by: string;
    execution_policy: Basis;
}
export interface ScoreBindingRequest {
    schema_version: '1.0.0';
    evaluation: Basis;
    statistics_policy: Basis;
    binding: {
        schema_version: '1.0.0';
        evaluation_id: string;
        score_id: string;
        definition_sha256: string;
        score_sha256: string;
        qualification: Basis;
        domain: string;
        reason: string;
    };
}
export interface AcceptanceRequest {
    schema_version: '1.0.0';
    candidate: Basis;
    evaluation: Basis;
    statistics_policy: Basis;
    decision: 'ACCEPT' | 'REJECT';
    reason: string;
    score_bindings: Basis[];
    expected_revision: string;
}
export interface PublishRequest {
    schema_version: '1.0.0';
    candidate: Basis;
    expected_revision: string;
    acceptance: Basis;
    reason: string;
}
export interface DisableRequest {
    schema_version: '1.0.0';
    release_id: string;
    expected_revision: string;
    reason: string;
}
export type PublicationRequest = ScoreBindingRequest | AcceptanceRequest | PublishRequest | DisableRequest;
export interface JournalReceipt {
    schema_version: '1.0.0';
    operation_id: string;
    resource_id: string;
    kind: 'ACCEPTANCE' | 'PUBLICATION';
    content_sha256: string;
    request_body_sha256: string;
}
export interface ScoreReceipt {
    schema_version: '1.0.0';
    original_key: string;
    request_body_sha256: string;
    receipt: {
        schema_version: '1.0.0';
        kind: 'SCORE_BIND';
        record: Basis;
        qualification: Basis;
        operation_id: string;
        request_body_sha256: string;
        qualification_revision: string;
        created_by: string;
        created_at: string;
    };
}
export interface PublishReceipt {
    schema_version: '1.0.0';
    state: 'PROCESSING' | 'COMPLETED';
    operation_id: string;
    release_id: string;
    request_body_sha256: string;
    artifact_id?: string;
    manifest_sha256?: string;
}
export interface DisabledRecovery {
    schema_version: '1.0.0';
    state: 'COMPLETED';
    original_key: string;
    actor_id: string;
    release_id: string;
    expected_revision: string;
    resulting_revision: string;
    request_body_sha256: string;
    receipt: JournalReceipt;
}
export type PublicationReceipt = JournalReceipt | ScoreReceipt | PublishReceipt | DisabledRecovery;
export interface AcceptanceRecord {
    schema_version: '1.0.0';
    acceptance_id: string;
    tenant_id: string;
    project_id: string | null;
    candidate: Basis;
    evaluation: Basis;
    execution_policy: Basis;
    statistics_policy: Basis;
    decision: 'ACCEPT' | 'REJECT';
    accepted_by: string;
    accepted_at: string;
    classification: string;
    manifest_sha256?: string;
    result_sha256?: string;
    qualified_scores?: unknown[];
    manifest?: {
        release_id: string;
    };
}
export interface QualificationView {
    qualification: Basis;
    definition: {
        tenant_id: string;
        qualification_id: string;
        request: {
            expert_id: string;
            project_id: string | null;
            domain: string;
            dimensions: string[];
            purpose: string;
            valid_from: string;
            valid_to: string;
        };
    };
    status: 'DRAFT' | 'APPROVED' | 'REJECTED' | 'REVOKED';
    revision: string;
}
export interface StatisticsPolicyView {
    schema_version: '1.0.0';
    record_id: string;
    content_sha256: string;
    production_eligible: false;
    content: {
        schema_version: '1.0.0';
        status: 'APPROVE' | 'REJECT';
        draft_id: string;
        draft_sha256: string;
        policy_schema_sha256: string;
        typed_input_sha256: string;
        sampling_assessment: string;
        policy: {
            policy_id: string;
            tenant_id: string;
            project_id: string | null;
            candidate_refs: Basis[];
            rule_revision: Basis;
            dataset: Basis;
            sample_manifest_artifact: Basis;
            familywise_confidence: string;
            dimensions: {
                dimension: string;
            }[];
        };
    };
}
export interface PendingPublication {
    schema_version: '1.0.0';
    actor: string;
    tenant_id: string;
    context: Context;
    project_id: string | null;
    kind: PublicationKind;
    key: string;
    body: PublicationRequest;
    body_sha256: string;
    receipt: PublicationReceipt | null;
}
export interface PublicationRecovery {
    complete: boolean;
    receipt: PublicationReceipt | null;
    acceptance: AcceptanceRecord | null;
    lookup: OperationLookup | null;
}
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true });
addFormats(ajv);
for (const schema of [candidateSchema, journalSchema, policySchema, scoreSchema, availabilitySchema, workflowSchema, policyReadSchema, disableRecoverySchema])
    ajv.addSchema(schema);
const ref = (schema: {
    $id: string;
}, name: string) => schema.$id + '#/$defs/' + name;
function checked<T>(schema: string, value: unknown): T { const check = ajv.getSchema(schema); if (!check || !check(value))
    fail('CONTENT_MISMATCH', '固定响应合同不匹配：' + (check?.errors?.[0]?.instancePath || schema)); return value as T; }
const pendingCheck = ajv.compile<PendingPublication>({ type: 'object', additionalProperties: false, required: ['schema_version', 'actor', 'tenant_id', 'context', 'project_id', 'kind', 'key', 'body', 'body_sha256', 'receipt'], properties: { schema_version: { const: '1.0.0' }, actor: { $ref: ref(candidateSchema, 'Id') }, tenant_id: { $ref: ref(candidateSchema, 'Id') }, context: { $ref: ref(journalSchema, 'Context') }, project_id: { anyOf: [{ $ref: ref(candidateSchema, 'Id') }, { type: 'null' }] }, kind: { enum: ['SCORE_BIND', 'ACCEPTANCE', 'PUBLISH', 'DISABLE'] }, key: { $ref: ref(candidateSchema, 'Id') }, body: { oneOf: [{ $ref: ref(scoreSchema, 'FormalScoreBinding') }, { $ref: ref(candidateSchema, 'Accept') }, { $ref: ref(candidateSchema, 'Publish') }, { $ref: ref(candidateSchema, 'Disable') }] }, body_sha256: { $ref: ref(candidateSchema, 'Hash') }, receipt: { anyOf: [{ type: 'null' }, { $ref: ref(scoreSchema, 'FormalReceipt') }, { $ref: ref(journalSchema, 'Receipt') }, { $ref: workflowSchema.$id }, { $ref: disableRecoverySchema.$id }] } } });
const path: Record<PublicationKind, string> = { SCORE_BIND: '/api/formal-evaluation-score-bindings', ACCEPTANCE: '/api/formal-agent-acceptance-decisions', PUBLISH: '/api/formal-agent-publications', DISABLE: '/api/formal-agent-publications/disable' };
const requestRef = (kind: PublicationKind) => kind === 'SCORE_BIND' ? ref(scoreSchema, 'FormalScoreBinding') : ref(candidateSchema, kind === 'ACCEPTANCE' ? 'Accept' : kind === 'DISABLE' ? 'Disable' : 'Publish');
export function publicationIssue(kind: PublicationKind, body: PublicationRequest): string { const check = ajv.getSchema(requestRef(kind))!; if (!check(body))
    return '请补齐真实固定记录：' + (check.errors?.[0]?.instancePath || '请求字段'); const reason = 'binding' in body ? body.binding.reason : body.reason; if (projectTextIssue(reason, kind === 'SCORE_BIND' ? 4096 : 2000))
    return '请填写规范、非空的理由'; if ('binding' in body && (body.evaluation.version_id !== body.binding.evaluation_id || body.evaluation.content_sha256 !== body.binding.definition_sha256))
    return '评分与评测固定定义不一致'; if (new TextEncoder().encode(canonical(body)).byteLength > 1048576)
    return '完整请求超过 1 MiB，无法提交'; return ''; }
export function publicationAllowed(s: MedicalSession | null, project: string | null, kind: PublicationKind, evaluationId?: string): boolean { const tenant = s?.current_context?.tenant_id; if (!tenant)
    return false; const action = kind === 'SCORE_BIND' ? 'evaluation.score' : 'agent.publish'; return s!.action_summary.some(x => x.action === action && (x.resource_kind === 'ENTERPRISE' && x.resource_id === tenant && x.project_id === null || project !== null && x.resource_kind === 'PROJECT' && x.resource_id === project && x.project_id === project || kind === 'SCORE_BIND' && x.resource_kind === 'EVALUATION' && x.resource_id === evaluationId && x.project_id === project)); }
export function publicationBlocker(a: PublicationAvailability | null, kind: PublicationKind, decision?: string): string { if (!a)
    return '请先核查当前部署服务'; if (!((kind === 'PUBLISH' || kind === 'DISABLE') ? a.features.publication_routes : a.features.acceptance_routes))
    return '当前部署未启用此操作接口'; if ((kind !== 'ACCEPTANCE' || decision !== 'REJECT') && !a.features.professional_completion_registered)
    return '尚未接入真实专业执行完成证明，不能绑定正式评分、通过验收或发布'; return ''; }
function headers(context: Context) { return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }; }
async function get(reader: PublicationReader, url: string, signal: AbortSignal, revision = '1') { const g = capabilityGuard(reader, signal), raw = await request.get(url, { medical: 'envelope', signal, headers: headers(g.context) }); g.assert(); const e = envelope(raw); if (raw.status !== 200 || e.resource_revision !== revision)
    fail('CONTENT_MISMATCH', '响应状态或固定修订不一致'); return e.data; }
export async function publicationAvailability(reader: PublicationReader, signal: AbortSignal): Promise<PublicationAvailability> { const g = capabilityGuard(reader, signal), value = checked<PublicationAvailability>(availabilitySchema.$id, await get(reader, '/api/formal-agent-publication-feature-availability', signal, g.context.context_revision)); g.assert(); if (canonical(value.current_context) !== canonical(g.context))
    fail('CONTENT_MISMATCH', '服务状态不属于当前授权上下文'); return detached(value); }
function inputId(id: string): void { if (!ajv.getSchema(ref(candidateSchema, 'Id'))!(id)) throw new MedicalFailure('SCHEMA_INVALID', '请填写已有记录的完整 UUID 编号'); }
export async function readPublicationCandidate(reader: PublicationReader, id: string, signal: AbortSignal): Promise<CandidateSelection> { inputId(id); const g = capabilityGuard(reader, signal), view = await readDraft(reader, 'AGENT', id, signal); g.assert(); const fixed = view.fixed, hash = await sourceDigest(fixed); g.assert(); return { record_id: id, basis: { version_id: fixed.version_id, content_sha256: hash }, project_id: fixed.project_id, name: fixed.name, status: view.status, revision: view.revision, created_by: fixed.created_by, execution_policy: detached(fixed.execution_policy) }; }
export async function readStatisticsPolicy(reader: PublicationReader, id: string, signal: AbortSignal): Promise<StatisticsPolicyView> { inputId(id); const g = capabilityGuard(reader, signal), view = checked<StatisticsPolicyView>(ref(policyReadSchema, 'Read'), await get(reader, '/api/formal-evaluation-policies/' + id, signal)); if (!('policy' in view.content))
    throw new MedicalFailure('ACTION_STALE', '所读记录仍是统计政策草稿，请读取批准决策记录'); if (view.record_id !== id || view.content.policy.policy_id !== id || view.content.policy.tenant_id !== g.context.tenant_id || await sourceDigest(view.content) !== view.content_sha256)
    fail('CONTENT_MISMATCH', '统计政策身份或固定摘要不一致'); g.assert(); return detached(view); }
export async function readPublicationQualification(reader: PublicationReader, id: string, signal: AbortSignal): Promise<QualificationView> { inputId(id); const g = capabilityGuard(reader, signal), raw = await request.get('/api/expert-qualifications/' + id, { medical: 'envelope', signal, headers: headers(g.context) }); g.assert(); const e = envelope(raw), view = checked<QualificationView>(ref(scoreSchema, 'Read'), e.data); if (raw.status !== 200 || e.resource_revision !== view.revision || view.qualification.version_id !== id || view.definition.qualification_id !== id || view.definition.tenant_id !== g.context.tenant_id || await sourceDigest(view.definition) !== view.qualification.content_sha256)
    fail('CONTENT_MISMATCH', '资格记录身份、修订或摘要不一致'); g.assert(); return detached(view); }
export async function readAcceptance(reader: PublicationReader, id: string, signal: AbortSignal): Promise<AcceptanceRecord> { inputId(id); const g = capabilityGuard(reader, signal), raw = await get(reader, '/api/formal-agent-acceptance-records/' + id, signal), record = checked<AcceptanceRecord>(ref(candidateSchema, (raw as AcceptanceRecord)?.decision === 'ACCEPT' ? 'Accepted' : 'Rejected'), raw); if (record.acceptance_id !== id || record.tenant_id !== g.context.tenant_id)
    fail('CONTENT_MISMATCH', '验收记录不属于当前企业或所选身份'); g.assert(); return detached(record); }
export const publicationStorageKey = (s: MedicalSession) => 'medical.formal-publication.pending:' + s.user.user_id + ':' + s.current_context?.tenant_id;
export function readPendingPublication(s: MedicalSession): PendingPublication | null { const raw = sessionStorage.getItem(publicationStorageKey(s)); if (raw === null)
    return null; if (new TextEncoder().encode(raw).byteLength > 2300000 || projectJsonIssue(raw))
    fail('CONTENT_MISMATCH', '原请求记录损坏，请保留记录并人工核查'); const p: unknown = JSON.parse(raw); if (!pendingCheck(p) || p.actor !== s.user.user_id || p.tenant_id !== s.current_context?.tenant_id || p.context.tenant_id !== p.tenant_id || publicationIssue(p.kind, p.body))
    fail('CONTENT_MISMATCH', '原请求身份或完整正文不一致'); validContext(p.context); return detached(p); }
async function verifyPending(s: MedicalSession, p: PendingPublication) { if (canonical(readPendingPublication(s)) !== canonical(p) || await sourceDigest(p.body) !== p.body_sha256)
    fail('CONTENT_MISMATCH', '原请求正文、摘要或原键已变化'); if (p.receipt)
    await verifyReceipt(p, p.receipt); }
function persist(s: MedicalSession, p: PendingPublication) { if (!pendingCheck(p))
    fail('CONTENT_MISMATCH', '不能保存不完整原请求'); sessionStorage.setItem(publicationStorageKey(s), canonical(p)); if (canonical(readPendingPublication(s)) !== canonical(p))
    fail('CONTENT_MISMATCH', '原请求未可靠保存，本次不会发送'); }
async function verifyReceipt(p: PendingPublication, raw: unknown): Promise<PublicationReceipt> {
    if (p.kind === 'SCORE_BIND') {
        const r = checked<ScoreReceipt>(ref(scoreSchema, 'FormalReceipt'), raw), b = (p.body as ScoreBindingRequest).binding;
        if (r.original_key !== p.key || r.request_body_sha256 !== p.body_sha256 || r.receipt.kind !== 'SCORE_BIND' || r.receipt.created_by !== p.actor || r.receipt.request_body_sha256 !== await sourceDigest(b) || canonical(r.receipt.qualification) !== canonical(b.qualification))
            fail('CONTENT_MISMATCH', '正式评分回执与完整原请求不一致');
        return r;
    }
    if (p.kind === 'PUBLISH') {
        const r = checked<PublishReceipt>(workflowSchema.$id, raw);
        if (r.request_body_sha256 !== p.body_sha256)
            fail('CONTENT_MISMATCH', '发布回执不属于完整原请求');
        return r;
    }
    if (p.kind === 'DISABLE' && raw && typeof raw === 'object' && 'state' in raw) {
        const r = checked<DisabledRecovery>(disableRecoverySchema.$id, raw), b = p.body as DisableRequest;
        if (r.original_key !== p.key || r.actor_id !== p.actor || r.release_id !== b.release_id || r.expected_revision !== b.expected_revision || BigInt(r.resulting_revision) !== BigInt(b.expected_revision) + 1n || r.request_body_sha256 !== p.body_sha256 || r.receipt.kind !== 'PUBLICATION' || r.receipt.request_body_sha256 !== p.body_sha256)
            fail('CONTENT_MISMATCH', '停用原键、原声明或固定回执不一致');
        return r;
    }
    const r = checked<JournalReceipt>(ref(journalSchema, 'Receipt'), raw);
    if (r.kind !== (p.kind === 'DISABLE' ? 'PUBLICATION' : 'ACCEPTANCE') || r.request_body_sha256 !== p.body_sha256)
        fail('CONTENT_MISMATCH', '验收回执不属于完整原请求');
    return r;
}
export async function submitPublication(reader: PublicationReader, kind: PublicationKind, body: PublicationRequest, project: string | null, signal: AbortSignal, onPending: (p: PendingPublication) => void): Promise<PendingPublication> {
    const g = capabilityGuard(reader, signal), frozen = detached(body), issue = publicationIssue(kind, frozen);
    if (issue)
        throw new MedicalFailure('SCHEMA_INVALID', issue);
    if (!publicationAllowed(g.session, project, kind, 'binding' in frozen ? frozen.binding.evaluation_id : undefined))
        throw new MedicalFailure('ACCESS_DENIED', '当前范围没有此操作权限');
    if (readPendingPublication(g.session))
        throw new MedicalFailure('IDEMPOTENCY_CONFLICT', '已有原请求待核查，不能换键重发');
    const available = await publicationAvailability(reader, signal);
    g.assert();
    const blocker = publicationBlocker(available, kind, 'decision' in frozen ? frozen.decision : undefined);
    if (blocker)
        throw new MedicalFailure('DEPENDENCY_UNAVAILABLE', blocker);
    const digest = await sourceDigest(frozen);
    g.assert();
    const pending: PendingPublication = { schema_version: '1.0.0', actor: g.session.user.user_id, tenant_id: g.context.tenant_id, context: detached(g.context), project_id: project, kind, key: crypto.randomUUID(), body: frozen, body_sha256: digest, receipt: null };
    // Recheck after asynchronous preflight so concurrent callers cannot replace the original.
    if (readPendingPublication(g.session)) throw new MedicalFailure('IDEMPOTENCY_CONFLICT', '已有原请求待核查，不能换键重发');
    // Persist before any mutation. Never replace the body, key, or original context during recovery.
    persist(g.session, pending);
    onPending(detached(pending));
    g.assert();
    const raw = await request.post(path[kind], detached(pending.body), { medical: 'envelope', signal, headers: { ...headers(g.context), 'Idempotency-Key': pending.key, [g.session.csrf.header_name]: g.session.csrf.token } });
    g.assert();
    const e = envelope(raw);
    if (raw.status !== 200 || e.resource_revision !== '1')
        fail('CONTENT_MISMATCH', '操作回执状态或修订不一致');
    const receipt = await verifyReceipt(pending, e.data);
    g.assert();
    await verifyPending(g.session, pending);
    g.assert();
    const saved = { ...pending, receipt: detached(receipt) };
    persist(g.session, saved);
    onPending(detached(saved));
    return detached(saved);
}
export async function recoverPublication(reader: PublicationReader, p: PendingPublication, signal: AbortSignal): Promise<PublicationRecovery> {
    const g = capabilityGuard(reader, signal);
    await verifyPending(g.session, p);
    g.assert();
    let receipt: PublicationReceipt | null = null, acceptance: AcceptanceRecord | null = null, lookup: OperationLookup | null = null;
    if (p.kind === 'DISABLE') {
        const body = { schema_version: '1.0.0', original_key: p.key, request: detached(p.body) };
        checked(ref(disableRecoverySchema, 'Recovery'), body);
        const raw = await request.post('/api/formal-agent-publications/disable/recovery', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } });
        g.assert();
        const e = envelope(raw);
        if (raw.status !== 200 || e.resource_revision !== '1')
            fail('CONTENT_MISMATCH', '停用原操作核查响应不一致');
        receipt = await verifyReceipt(p, e.data);
    }
    else if (p.kind === 'SCORE_BIND') {
        const body = { schema_version: '1.0.0', original_key: p.key, request: detached(p.body) };
        checked(ref(scoreSchema, 'FormalRecovery'), body);
        const raw = await request.post('/api/formal-evaluation-score-bindings/recovery', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } });
        g.assert();
        const e = envelope(raw);
        if (raw.status !== 200 || e.resource_revision !== '1')
            fail('CONTENT_MISMATCH', '原评分核查响应不一致');
        receipt = await verifyReceipt(p, e.data);
    }
    else if (p.kind === 'PUBLISH') {
        receipt = await verifyReceipt(p, await get(reader, '/api/formal-agent-publication-operations/' + encodeURIComponent(p.key), signal));
        if ((receipt as PublishReceipt).state !== 'COMPLETED') {
            if (p.receipt && ((p.receipt as PublishReceipt).state === 'COMPLETED' || (p.receipt as PublishReceipt).operation_id !== (receipt as PublishReceipt).operation_id || (p.receipt as PublishReceipt).release_id !== (receipt as PublishReceipt).release_id))
                fail('CONTENT_MISMATCH', '发布原操作发生变化或完成状态倒退');
            g.assert();
            await verifyPending(g.session, p);
            g.assert();
            if (p.receipt === null) persist(g.session, { ...p, receipt: detached(receipt) });
            return { complete: false, receipt, acceptance: null, lookup: null };
        }
    }
    else {
        lookup = await lookupOperation(g.session, 'AGENT-PUBLICATION-ACCEPTANCE', p.key, signal);
        g.assert();
        if (lookup.lookup_status !== 'FOUND' || lookup.operation?.status !== 'COMPLETED')
            return { complete: false, receipt: null, acceptance: null, lookup };
        const operation = lookup.operation;
        if (!operation.resource_id)
            fail('CONTENT_MISMATCH', '原验收操作缺少记录身份');
        const current = await readOperation(g.session, operation.operation_id, signal);
        g.assert();
        if (canonical(current) !== canonical(operation))
            fail('ACTION_STALE', '原操作已变化，请重新核查');
        const r = await verifyReceipt(p, await get(reader, '/api/formal-agent-acceptance-records/' + operation.resource_id + '/operation-receipts/' + operation.operation_id, signal)) as JournalReceipt;
        if (r.resource_id !== operation.resource_id || r.operation_id !== operation.operation_id)
            fail('CONTENT_MISMATCH', '类型回执与原键操作不一致');
        acceptance = await readAcceptance(reader, r.resource_id, signal);
        g.assert();
        const original = p.body as AcceptanceRequest;
        if (await sourceDigest(acceptance) !== r.content_sha256 || acceptance.accepted_by !== p.actor || acceptance.project_id !== p.project_id || ['candidate', 'evaluation', 'statistics_policy', 'decision'].some(k => canonical(acceptance![k as keyof AcceptanceRecord]) !== canonical(original[k as keyof AcceptanceRequest])))
            fail('CONTENT_MISMATCH', '固定验收内容与完整原请求不一致');
        receipt = r;
    }
    g.assert();
    if (p.receipt) {
        if (p.kind === 'PUBLISH') {
            const previous = p.receipt as PublishReceipt, next = receipt as PublishReceipt;
            if (previous.operation_id !== next.operation_id || previous.release_id !== next.release_id || previous.state === 'COMPLETED' && canonical(previous) !== canonical(next))
                fail('CONTENT_MISMATCH', '发布原操作或结果发生变化');
        }
        else if (canonical(p.receipt) !== canonical(p.kind === 'DISABLE' ? (receipt as DisabledRecovery).receipt : receipt))
            fail('CONTENT_MISMATCH', '核查回执与首次原回执不一致');
    }
    await verifyPending(g.session, p);
    g.assert();
    sessionStorage.removeItem(publicationStorageKey(g.session));
    if (sessionStorage.getItem(publicationStorageKey(g.session)) !== null)
        fail('CONTENT_MISMATCH', '结果已核验，但本地原请求尚未清除');
    return { complete: true, receipt: detached(receipt), acceptance: detached(acceptance), lookup };
}
