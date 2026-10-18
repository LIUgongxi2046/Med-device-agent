import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import request from '@/utils/request';
import contract from '../../../../business/medical-persistence/src/main/resources/configuration-authoring.schema.json';
import catalogue from '../../../../contracts/catalogue.json';
import { envelope, fail, validContext } from './protocol';
import type { Basis, Context, MedicalSession } from './protocol';
import { sourceDigest } from './content-digest';
import { lookupOperation, readOperation } from './operations';
import type { OperationLookup } from './operations';
export type AuthorKind = 'Capability' | 'Agent';
export interface CapabilityRequest {
    schema_version: '1.0.0';
    execution_mode: 'SIMULATION';
    project_id: string | null;
    kind: 'ROLE' | 'PROMPT';
    source_artifact: Basis;
    input_schema_ref: {
        schema_ref: string;
        schema_sha256: string;
    };
    output_schema_ref: {
        schema_ref: string;
        schema_sha256: string;
    };
    tool_scope: never[];
}
export interface AgentRequest {
    schema_version: '1.0.0';
    execution_mode: 'SIMULATION';
    project_id: string | null;
    name: string;
    role_version: Basis;
    prompt_revision: Basis;
    model_config_revision: Basis;
    limits: Record<string, string | number>;
}
export type AuthorRequest = CapabilityRequest | AgentRequest;
export interface AuthorView {
    definition: {
        schema_version: '1.0.0';
        tenant_id: string;
        config_id: string;
        version_id: string;
        request: AuthorRequest;
        created_by: string;
        created_at: string;
        dependency_manifest?: {
            dependencies: Basis[];
        };
    };
    content_sha256: string;
    status: 'DRAFT';
    revision: '1';
    production_eligible: false;
}
export interface AuthorReceipt {
    operation_id: string;
    tenant_id: string;
    project_id: string | null;
    config_id: string;
    version_id: string;
    content_sha256: string;
    current_context: Context;
}
export interface PendingAuthor {
    actor: string;
    tenant_id: string;
    kind: AuthorKind;
    key: string;
    context: Context;
    body_sha256: string;
    project_id: string | null;
}
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true });
addFormats(ajv);
ajv.addSchema(contract);
const prefix = contract.$id + '#/$defs/';
export const publishedSchemas = catalogue.schemas.map(item => ({ label: item.name, schema_ref: catalogue.base_uri + item.path, schema_sha256: item.sha256 }));
export const limitFields = Object.entries(contract.$defs.Limits.properties).filter(([, value]) => !('const' in value)).map(([key, value]) => ({ key, seconds: '$ref' in value && value.$ref.endsWith('/Seconds') }));
export function authorValidate<T>(name: string, value: unknown): T { const check = ajv.getSchema(prefix + name)!; if (!check(value))
    fail('SCHEMA_INVALID', '配置字段缺失或格式不正确：' + (check.errors?.[0]?.instancePath || name)); return value as T; }
export function authorAllowed(session: MedicalSession | null, action: string, project?: string | null, config?: string): boolean { return !!session?.current_context && session.action_summary.some(item => item.action === action && (item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context!.tenant_id && item.project_id === null || item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id && (project === undefined || project === item.project_id) || action === 'configuration.read' && item.resource_kind === 'CONFIGURATION' && (config === undefined || item.resource_id === null || item.resource_id === config) && (project === undefined || item.project_id === null || item.project_id === project))); }
function context(session: MedicalSession): Context { if (!session.current_context)
    fail('ACCESS_DENIED', '请先选择企业'); validContext(session.current_context); return session.current_context; }
function path(kind: AuthorKind): string { return kind === 'Agent' ? '/api/simulation-agent-versions' : '/api/capability-versions'; }
function operation(kind: AuthorKind): string { return kind === 'Agent' ? 'SIMULATION-AGENT-CREATE' : 'CP-05'; }
function storage(session: MedicalSession): string { return 'medical.configuration.authoring:' + session.user.user_id + ':' + context(session).tenant_id; }
/** Pure user-input preflight. Never dispatches a medical failure or grants authority. */
export function authorFieldIssue(name: string, value: unknown): string {
    const check = ajv.getSchema(prefix + name)!;
    return check(value) ? '' : '配置字段缺失或格式不正确：' + (check.errors?.[0]?.instancePath || name);
}
export function authorInputIssue(kind: AuthorKind, input: unknown): string {
    const shape = authorFieldIssue(kind, input);
    if (shape)
        return shape;
    if (kind === 'Agent') {
        const body = input as AgentRequest;
        const limits = body.limits;
        if (!body.name.trim() || body.name.trim() !== body.name || body.name.normalize('NFC') !== body.name || /[\u0000-\u001f\u007f]/u.test(body.name))
            return '请填写规范的 Agent 名称';
        if (new Set([body.role_version.version_id, body.prompt_revision.version_id, body.model_config_revision.version_id]).size !== 3)
            return '角色、提示词和模型须为不同版本';
        for (const [key, value] of Object.entries(limits))
            if (typeof value === 'string' && /^\d+$/.test(value) && BigInt(value) > 18446744073709551615n)
                return '上限超出允许范围：' + key;
        if (BigInt(limits.max_input_tokens!) + BigInt(limits.max_output_tokens!) > BigInt(limits.max_total_tokens!))
            return '输入与输出 Token 上限之和不能超过总 Token 上限';
        if (Number(limits.heartbeat_interval_seconds) >= Number(limits.heartbeat_timeout_seconds))
            return '心跳间隔必须小于心跳超时';
    }
    else {
        const body = input as CapabilityRequest;
        for (const binding of [body.input_schema_ref, body.output_schema_ref])
            if (!publishedSchemas.some(item => item.schema_ref === binding.schema_ref && item.schema_sha256 === binding.schema_sha256))
                return '请选择已发布的本地契约';
    }
    return '';
}
export function validateAuthorRequest(kind: AuthorKind, value: AuthorRequest): void {
    authorValidate(kind, value);
    if (kind === 'Agent') {
        const body = value as AgentRequest;
        const l = body.limits;
        if (body.name.trim() !== body.name || !body.name.trim() || body.name.normalize('NFC') !== body.name || /[\u0000-\u001f\u007f]/u.test(body.name))
            fail('SCHEMA_INVALID', '请填写规范的 Agent 名称');
        if (new Set([body.role_version.version_id, body.prompt_revision.version_id, body.model_config_revision.version_id]).size !== 3)
            fail('SCHEMA_INVALID', '角色、提示词和模型须为不同版本');
        for (const [key, v] of Object.entries(l))
            if (typeof v === 'string' && /^\d+$/.test(v) && BigInt(v) > 18446744073709551615n)
                fail('SCHEMA_INVALID', '上限超出允许范围：' + key);
        if (BigInt(l.max_input_tokens!) + BigInt(l.max_output_tokens!) > BigInt(l.max_total_tokens!))
            fail('SCHEMA_INVALID', '输入与输出 Token 上限之和不能超过总 Token 上限');
        if (Number(l.heartbeat_interval_seconds) >= Number(l.heartbeat_timeout_seconds))
            fail('SCHEMA_INVALID', '心跳间隔必须小于心跳超时');
    }
    else {
        for (const binding of [(value as CapabilityRequest).input_schema_ref, (value as CapabilityRequest).output_schema_ref])
            if (!publishedSchemas.some(item => item.schema_ref === binding.schema_ref && item.schema_sha256 === binding.schema_sha256))
                fail('SCHEMA_INVALID', '请选择已发布的本地契约');
    }
}
export function readPendingAuthor(session: MedicalSession): PendingAuthor | null {
    const raw = sessionStorage.getItem(storage(session));
    if (!raw)
        return null;
    let p: PendingAuthor;
    try {
        p = JSON.parse(raw);
    }
    catch {
        fail('CONTENT_MISMATCH', '原请求元数据无法读取，请核查原操作');
    }
    if (!p || Object.keys(p).sort().join() !== 'actor,body_sha256,context,key,kind,project_id,tenant_id' || p.actor !== session.user.user_id || p.tenant_id !== context(session).tenant_id || !['Agent', 'Capability'].includes(p.kind))
        fail('CONTENT_MISMATCH', '原请求身份不一致');
    authorValidate('Id', p.key);
    authorValidate('Hash', p.body_sha256);
    validContext(p.context);
    if (p.context.tenant_id !== p.tenant_id)
        fail('CONTENT_MISMATCH', '原企业不一致');
    if (p.project_id !== null)
        authorValidate('Id', p.project_id);
    return p;
}
export async function prepareAuthor(session: MedicalSession, kind: AuthorKind, body: AuthorRequest, signal: AbortSignal): Promise<PendingAuthor> {
    validateAuthorRequest(kind, body);
    if (!authorAllowed(session, kind === 'Agent' ? 'agent.configure' : 'capability.configure', body.project_id))
        fail('ACCESS_DENIED', '当前范围没有新建配置权限');
    const digest = await sourceDigest(body);
    signal.throwIfAborted();
    const old = readPendingAuthor(session);
    if (old) {
        if (old.kind !== kind || old.body_sha256 !== digest || (['tenant_id', 'context_revision', 'auth_epoch', 'governance_epoch'] as const).some(key => old.context[key] !== context(session)[key]))
            fail('IDEMPOTENCY_CONFLICT', '请先核查原请求，重试必须保持原内容和上下文');
        return old;
    }
    const pending = { actor: session.user.user_id, tenant_id: context(session).tenant_id, kind, key: crypto.randomUUID(), context: { ...context(session) }, body_sha256: digest, project_id: body.project_id };
    sessionStorage.setItem(storage(session), JSON.stringify(pending));
    return pending;
}
export async function readAuthored(session: MedicalSession, kind: AuthorKind, id: string, signal: AbortSignal): Promise<AuthorView> {
    authorValidate('Id', id);
    if (!authorAllowed(session, 'configuration.read'))
        fail('ACCESS_DENIED', '没有配置读取许可');
    const result = envelope(await request.get(path(kind) + '/' + id, { medical: 'envelope', signal, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision } }));
    const view = authorValidate<AuthorView>(kind + 'View', result.data);
    const d = view.definition;
    if (result.resource_revision !== '1' || d.version_id !== id || d.tenant_id !== context(session).tenant_id || !authorAllowed(session, 'configuration.read', d.request.project_id, d.config_id) || await sourceDigest(d) !== view.content_sha256)
        fail('CONTENT_MISMATCH', '固定配置的身份、归属或摘要不一致');
    signal.throwIfAborted();
    return view;
}
export async function submitAuthor(session: MedicalSession, p: PendingAuthor, body: AuthorRequest, signal: AbortSignal): Promise<AuthorReceipt> {
    const stored = await prepareAuthor(session, p.kind, body, signal);
    if (stored.key !== p.key)
        fail('IDEMPOTENCY_CONFLICT', '原请求标识不一致');
    signal.throwIfAborted();
    const raw = await request.post(path(p.kind), body, { medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': p.context.context_revision, 'Idempotency-Key': p.key, [session.csrf.header_name]: session.csrf.token } });
    const result = envelope(raw);
    const r = authorValidate<AuthorReceipt>('Receipt', result.data);
    if (raw.status !== 201 || result.resource_revision !== '1' || r.tenant_id !== p.tenant_id || r.project_id !== p.project_id || r.current_context.tenant_id !== p.tenant_id || BigInt(r.current_context.context_revision) !== BigInt(p.context.context_revision) + 1n || BigInt(r.current_context.governance_epoch) !== BigInt(p.context.governance_epoch) + 1n || r.current_context.auth_epoch !== p.context.auth_epoch)
        fail('CONTENT_MISMATCH', '保存回执与原范围或上下文不一致');
    return r;
}
export function lookupAuthor(session: MedicalSession, p: PendingAuthor, signal: AbortSignal): Promise<OperationLookup> { return lookupOperation(session, operation(p.kind), p.key, signal); }
export async function recoverAuthor(session: MedicalSession, p: PendingAuthor, operationId: string, signal: AbortSignal, receipt?: AuthorReceipt): Promise<AuthorView> { const op = await readOperation(session, operationId, signal); if (op.status !== 'COMPLETED' || !op.resource_id)
    fail('CONTENT_MISMATCH', '原操作未完成'); const view = await readAuthored(session, p.kind, op.resource_id, signal); if (view.definition.created_by !== p.actor || view.definition.request.project_id !== p.project_id || await sourceDigest(view.definition.request) !== p.body_sha256 || receipt && (receipt.operation_id !== op.operation_id || receipt.version_id !== view.definition.version_id || receipt.content_sha256 !== view.content_sha256 || receipt.config_id !== view.definition.config_id))
    fail('CONTENT_MISMATCH', '回读固定配置与原请求不一致'); signal.throwIfAborted(); if (readPendingAuthor(session)?.key !== p.key)
    fail('CONTENT_MISMATCH', '原请求已变化'); sessionStorage.removeItem(storage(session)); return view; }
