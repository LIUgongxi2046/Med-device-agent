import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { canonicalizeEx } from 'json-canonicalize';
import request from '@/utils/request';
import runtime from '../../../../business/medical-persistence/src/main/resources/formal-runtime-capability.schema.json';
import agent from '../../../../business/medical-persistence/src/main/resources/formal-agent-publication.schema.json';
import discovery from './formal-agent-draft-directory.schema.json';
import { envelope, fail, MedicalFailure, validate as validateShared } from './protocol';
import type { MedicalSession, Context, ApiError } from './protocol';
import { sourceDigest } from './content-digest';
import { projectJsonIssue } from './project-json';
import { lookupOperation, readOperation } from './operations';
import { capabilityGuard, capabilityScopeCandidates, capabilitySchemas } from './formal-capability-domain';
export { capabilityScopeCandidates, capabilitySchemas };
import type { CapabilityResourceCandidate } from './formal-capability-domain';
export type DraftKind = 'AGENT' | 'TOOL' | 'SKILL';
export type DraftReader = () => MedicalSession;
export type DraftBody = Record<string, any>;
export interface DraftSummary {
    record_id: string;
    version_id: string;
    config_id: string;
    project_id: string | null;
    kind: DraftKind;
    name: string | null;
    created_by: string;
    created_at: string;
    content_sha256: string;
    status: string;
    production_eligible: false;
}
export interface DraftPage {
    schema_version: '1.0.0';
    items: DraftSummary[];
    next_cursor: string | null;
}
export interface DraftAvailability {
    schema_version: '1.0.0';
    current_context: Context;
    features: {
        formal_agent_authoring: boolean;
    };
    maximum_page_size: 20;
}
export interface DraftReceipt {
    schema_version: '1.0.0';
    operation_id: string;
    resource_id: string;
    kind: 'AUTHOR';
    content_sha256: string;
    request_body_sha256: string;
}
export interface PendingDraft {
    schema_version: '1.0.0';
    actor: string;
    tenant_id: string;
    context: Context;
    kind: DraftKind;
    key: string;
    body: DraftBody;
    body_sha256: string;
    phase: 'UNKNOWN' | 'PENDING';
    receipt: DraftReceipt | null;
}
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true });
addFormats(ajv);
for (const s of [runtime, agent, discovery])
    ajv.addSchema(s);
const ref = (s: {
    $id: string;
}, name: string) => s.$id + '#/$defs/' + name;
export const draftCanonical = (v: unknown) => canonicalizeEx(v, { strictUndefined: true });
export const draftCopy = <T>(v: T): T => JSON.parse(draftCanonical(v)) as T;
function validate<T>(s: {
    $id: string;
}, name: string, value: unknown): T { const check = ajv.getSchema(ref(s, name)); if (!check || !check(value))
    fail('CONTENT_MISMATCH', '固定草稿合同不匹配：' + (check?.errors?.[0]?.instancePath || name)); return value as T; }
function headers(context: Context) { return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }; }
export function draftIssue(kind: DraftKind, body: DraftBody): string { const check = ajv.getSchema(ref(kind === 'AGENT' ? agent : runtime, kind === 'AGENT' ? 'Author' : 'FormalCapability'))!; if (!check(body))
    return '请补齐固定声明：' + (check.errors?.[0]?.instancePath || '字段格式'); if (kind !== 'AGENT' && body.kind !== kind)
    return '能力类型与当前表单不一致'; if (typeof body.reason !== 'string' || body.reason.trim() !== body.reason || !body.reason.trim() || body.reason.normalize('NFC') !== body.reason)
    return '登记理由须为规范化的非空文本'; return ''; }
export function draftAllowed(s: MedicalSession, kind: DraftKind, project: string | null): boolean { const t = s.current_context?.tenant_id; return !!t && s.action_summary.some(x => x.action === (kind === 'AGENT' ? 'agent.configure' : 'capability.configure') && (x.resource_kind === 'ENTERPRISE' && x.resource_id === t && x.project_id === null || project !== null && x.resource_kind === 'PROJECT' && x.resource_id === project && x.project_id === project)); }
const key = (s: MedicalSession) => 'medical.formal-agent-draft.pending:' + s.user.user_id + ':' + s.current_context?.tenant_id;
const pendingCheck = ajv.compile<PendingDraft>({ type: 'object', additionalProperties: false, required: ['schema_version', 'actor', 'tenant_id', 'context', 'kind', 'key', 'body', 'body_sha256', 'phase', 'receipt'], properties: { schema_version: { const: '1.0.0' }, actor: { $ref: ref(discovery, 'Id') }, tenant_id: { $ref: ref(discovery, 'Id') }, context: { $ref: ref(discovery, 'Context') }, kind: { enum: ['AGENT', 'TOOL', 'SKILL'] }, key: { $ref: ref(discovery, 'Id') }, body: { anyOf: [{ $ref: ref(agent, 'Author') }, { $ref: ref(runtime, 'FormalCapability') }] }, body_sha256: { $ref: ref(discovery, 'Hash') }, phase: { enum: ['PENDING', 'UNKNOWN'] }, receipt: { anyOf: [{ $ref: ref(runtime, 'Receipt') }, { type: 'null' }] } } });
export function pendingDraft(s: MedicalSession): PendingDraft | null { const raw = sessionStorage.getItem(key(s)); if (raw === null)
    return null; if (new TextEncoder().encode(raw).length > 2200000 || projectJsonIssue(raw))
    fail('CONTENT_MISMATCH', '原请求记录损坏，请保留并人工核查'); const p: unknown = JSON.parse(raw); if (!pendingCheck(p) || p.actor !== s.user.user_id || p.tenant_id !== s.current_context?.tenant_id || p.context.tenant_id !== p.tenant_id || p.receipt !== null && p.receipt.kind !== 'AUTHOR' || draftIssue(p.kind, p.body))
    fail('CONTENT_MISMATCH', '原请求身份或声明不一致'); return draftCopy(p); }
function persist(s: MedicalSession, p: PendingDraft) { if (!pendingCheck(p))
    fail('CONTENT_MISMATCH', '不能保存不完整原请求'); sessionStorage.setItem(key(s), draftCanonical(p)); if (draftCanonical(pendingDraft(s)) !== draftCanonical(p))
    fail('CONTENT_MISMATCH', '原请求未可靠保存'); }
async function verifyPending(s: MedicalSession, p: PendingDraft) { if (draftCanonical(pendingDraft(s)) !== draftCanonical(p) || await sourceDigest(p.body) !== p.body_sha256)
    fail('CONTENT_MISMATCH', '原请求正文、摘要或请求键已变化'); }
export async function draftAvailability(reader: DraftReader, signal: AbortSignal): Promise<DraftAvailability> { const g = capabilityGuard(reader, signal), raw = await request.get('/api/formal-agent-authoring-feature-availability', { medical: 'envelope', headers: headers(g.context), signal }); g.assert(); const e = envelope(raw), d = validate<DraftAvailability>(discovery, 'Availability', e.data); if (raw.status !== 200 || e.resource_revision !== g.context.context_revision || draftCanonical(d.current_context) !== draftCanonical(g.context))
    fail('CONTENT_MISMATCH', '草稿可用性上下文不一致'); return d; }
export async function queryDrafts(reader: DraftReader, kind: DraftKind, project: string | null, cursor: string | null, signal: AbortSignal): Promise<DraftPage> { const g = capabilityGuard(reader, signal), body = { schema_version: '1.0.0', project_id: project, kind, limit: 20, cursor }; validate(discovery, 'Query', body); const raw = await request.post('/api/formal-agent-drafts/query', body, { medical: 'envelope', signal, headers: { ...headers(g.context), [g.session.csrf.header_name]: g.session.csrf.token } }); g.assert(); if (raw.status === 409 && ['CURSOR_EXPIRED', 'CURSOR_INVALID'].includes(raw.data?.code)) {
    const error = validateShared<ApiError>('urn:medical:shared#/$defs/Error', raw.data);
    if (cursor === null || raw.headers['cache-control'] !== 'no-store' || raw.headers['x-schema-version'] !== '1.0.0' || raw.headers['x-request-id'] !== error.request_id || error.operation_id !== null || error.resource_revision !== null || error.allowed_actions.length || error.details.length || error.retryable)
        fail('CONTENT_MISMATCH', '分页失效响应不完整');
    throw new MedicalFailure(error.code, '目录游标已失效，请明确重新读取首页', error.request_id);
} const e = envelope(raw), page = validate<DraftPage>(discovery, 'Page', e.data); if (raw.status !== 200 || e.resource_revision !== '1' || cursor !== null && page.next_cursor === cursor || new Set(page.items.map(x => x.record_id)).size !== page.items.length || page.items.some(x => x.kind !== kind || x.project_id !== project))
    fail('CONTENT_MISMATCH', '草稿目录范围或分页不一致'); return page; }
function route(kind: DraftKind) { return kind === 'AGENT' ? '/api/formal-agent-records/' : '/api/formal-runtime-capability-records/'; }
export async function readDraft(reader: DraftReader, kind: DraftKind, id: string, signal: AbortSignal): Promise<DraftBody> { validate(discovery, 'Id', id); const g = capabilityGuard(reader, signal), raw = await request.get(route(kind) + id, { medical: 'envelope', signal, headers: headers(g.context) }); g.assert(); const e = envelope(raw), v = validate<DraftBody>(kind === 'AGENT' ? agent : discovery, kind === 'AGENT' ? 'CandidateView' : 'RuntimeView', e.data), fixed = kind === 'AGENT' ? v.fixed : v.content; if (raw.status !== 200 || e.resource_revision !== '1' || fixed.tenant_id !== g.context.tenant_id || (kind === 'AGENT' ? fixed.author_record_id : v.record_id) !== id || kind !== 'AGENT' && (fixed.request.kind !== kind || v.resource_id !== fixed.version_id || await sourceDigest(fixed) !== v.content_sha256))
    fail('CONTENT_MISMATCH', '固定草稿身份或摘要不一致'); g.assert(); return draftCopy(v); }
function verifyReceipt(p: PendingDraft, value: unknown, revision: string | null): DraftReceipt { const r = validate<DraftReceipt>(runtime, 'Receipt', value); if (revision !== '1' || r.kind !== 'AUTHOR' || r.request_body_sha256 !== p.body_sha256)
    fail('CONTENT_MISMATCH', '回执不属于完整原请求'); return r; }
export async function createDraft(reader: DraftReader, kind: DraftKind, body: DraftBody, signal: AbortSignal, onPending: (p: PendingDraft) => void, resources: CapabilityResourceCandidate[] = []) {
    const g = capabilityGuard(reader, signal), frozen = draftCopy(body), issue = draftIssue(kind, frozen);
    if (issue)
        throw new Error(issue);
    if (new TextEncoder().encode(draftCanonical(frozen)).length > 1048576)
        throw new MedicalFailure('CONTEXT_LIMIT', '完整声明超过1MiB，请减少声明规模');
    if (!draftAllowed(g.session, kind, frozen.project_id))
        fail('ACCESS_DENIED', '当前范围没有草稿登记权限');
    if (pendingDraft(g.session))
        throw new Error('已有原请求待核查，不能创建或重发');
    if (kind !== 'AGENT') {
        const candidates = capabilityScopeCandidates(g.session, frozen.project_id, resources);
        if (frozen.tool_scope.permissions.some((p: any) => !candidates.some(c => draftCanonical(c) === draftCanonical({ scope: p.scope, action: p.action }))))
            fail('ACCESS_DENIED', '权限须来自当前精确范围，不能通过声明扩大授权');
    }
    const availability = await draftAvailability(reader, signal);
    g.assert();
    if (!availability.features.formal_agent_authoring)
        throw new MedicalFailure('DEPENDENCY_UNAVAILABLE', '当前部署未启用正式草稿服务');
    const p: PendingDraft = { schema_version: '1.0.0', actor: g.session.user.user_id, tenant_id: g.context.tenant_id, context: draftCopy(g.context), kind, key: crypto.randomUUID(), body: frozen, body_sha256: await sourceDigest(frozen), phase: 'UNKNOWN', receipt: null };
    g.assert();
    persist(g.session, p);
    onPending(draftCopy(p));
    const raw = await request.post(kind === 'AGENT' ? '/api/formal-agents' : '/api/formal-runtime-capabilities', draftCopy(p.body), { medical: 'envelope', signal, headers: { ...headers(g.context), 'Idempotency-Key': p.key, [g.session.csrf.header_name]: g.session.csrf.token } });
    g.assert();
    const e = envelope(raw), r = verifyReceipt(p, e.data, e.resource_revision);
    if (raw.status !== 201)
        fail('CONTENT_MISMATCH', '登记未返回创建回执');
    await verifyPending(g.session, p);
    g.assert();
    const saved = { ...p, receipt: r };
    persist(g.session, saved);
    onPending(draftCopy(saved));
    return saved;
}
export async function recoverDraft(reader: DraftReader, p: PendingDraft, signal: AbortSignal) { const g = capabilityGuard(reader, signal); await verifyPending(g.session, p); g.assert(); const lookup = await lookupOperation(g.session, 'AGENT-PUBLICATION-AUTHOR', p.key, signal); g.assert(); const op = lookup.operation; if (lookup.lookup_status !== 'FOUND' || op?.status !== 'COMPLETED')
    return { lookup, view: null }; if (!op.resource_id)
    fail('CONTENT_MISMATCH', '原操作缺少记录身份'); const current = await readOperation(g.session, op.operation_id, signal); g.assert(); if (draftCanonical(current) !== draftCanonical(op))
    fail('ACTION_STALE', '原操作状态已变化，请再次核查'); const raw = await request.get(route(p.kind) + op.resource_id + '/operation-receipts/' + op.operation_id, { medical: 'envelope', signal, headers: headers(g.context) }); g.assert(); const e = envelope(raw), r = verifyReceipt(p, e.data, e.resource_revision); if (raw.status !== 200 || r.resource_id !== op.resource_id || r.operation_id !== op.operation_id || p.receipt && draftCanonical(p.receipt) !== draftCanonical(r))
    fail('CONTENT_MISMATCH', '原键与类型回执不一致'); const view = await readDraft(reader, p.kind, op.resource_id, signal); g.assert(); const fixed = p.kind === 'AGENT' ? view.fixed : view.content; if (await sourceDigest(fixed) !== r.content_sha256 || fixed.created_by !== p.actor)
    fail('CONTENT_MISMATCH', '固定草稿与原作者回执不一致'); if (p.kind === 'AGENT') {
    for (const field of ['project_id', 'name', 'execution_policy', 'manifest'])
        if (draftCanonical(fixed[field]) !== draftCanonical(p.body[field]))
            fail('CONTENT_MISMATCH', '候选固定字段与原声明不一致');
}
else if (draftCanonical(fixed.request) !== draftCanonical(p.body))
    fail('CONTENT_MISMATCH', '能力固定正文与原声明不一致'); g.assert(); await verifyPending(g.session, p); g.assert(); sessionStorage.removeItem(key(g.session)); return { lookup, view }; }
export const draftManifestSchema = agent.$defs.CandidateManifest;
export const draftSchemaDefinitions = agent.$defs as Record<string, any>;
