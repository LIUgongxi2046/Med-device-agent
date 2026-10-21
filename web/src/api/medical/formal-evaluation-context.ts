import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import request from '@/utils/request'
import contract from '../../../../business/medical-persistence/src/main/resources/formal-evaluation-context-http.schema.json'
import { envelope, fail, MedicalFailure, validate, validContext } from './protocol'
import type { ApiError, Basis, Context, MedicalSession } from './protocol'
import { capabilityCanonical as canonical, detached, capabilityGuard } from './formal-capability-domain'
import { sourceDigest } from './content-digest'
import { projectJsonIssue } from './project-json'
export { canonical, detached }
export type ContextReader = () => MedicalSession
export type ContextKind = 'AUTHOR' | 'DECISION'
export type ContextStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'DISABLED'
export type ContextDecisionKind = 'APPROVE' | 'REJECT' | 'DISABLE'
export interface ContextSchema { schema_ref: string; schema_sha256: string }
export interface ContextNode { node_kind: string; role: Basis; prompt: Basis; context_assembler: Basis; input_schema: ContextSchema; output_schema: ContextSchema }
export interface ContextDefinition { schema_version: '1.0.0'; project_id: string; candidate: Basis; execution_policy: Basis; node_bindings: ContextNode[] }
export interface ContextAuthor { schema_version: '1.0.0'; definition: ContextDefinition; reason: string }
export interface ContextDecide { schema_version: '1.0.0'; binding: Basis; expected_revision: string; decision: ContextDecisionKind; reason: string }
export interface ContextAvailability { schema_version: '1.0.0'; current_context: Context; features: { context_bindings: boolean; bound_preparation: false }; maximum_page_size: 20; unavailable_reason: null | 'FEATURE_DISABLED' | 'DEPENDENCY_UNAVAILABLE' }
export interface ContextReceipt { binding: Basis; status: ContextStatus; revision: string; operation_id: string }
export interface ContextFixed { schema_version: '1.0.0'; tenant_id: string; version_id: string; created_by: string; created_at: string; classification: string; definition: ContextDefinition }
export interface ContextDecision { schema_version: '1.0.0'; tenant_id: string; version_id: string; binding_sha256: string; record_id: string; previous_status: ContextStatus; previous_revision: string; decision: ContextDecisionKind; status: ContextStatus; revision: string; actor_id: string; decided_at: string; reason: string }
export interface ContextView { schema_version: '1.0.0'; binding: Basis; fixed: ContextFixed; author_reason: string; status: ContextStatus; revision: string; decisions: ContextDecision[] }
export interface ContextSummary { binding: Basis; project_id: string; candidate: Basis; execution_policy: Basis; status: ContextStatus; revision: string; created_by: string; created_at: string; classification: string }
export interface ContextPage { schema_version: '1.0.0'; items: ContextSummary[]; next_cursor: string | null }
export interface PendingContext { schema_version: '1.0.0'; actor: string; tenant_id: string; context: Context; project_id: string; kind: ContextKind; key: string; body: ContextAuthor | ContextDecide; body_sha256: string; phase: 'UNKNOWN'; receipt: ContextReceipt | null }
const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true }); addFormats(ajv); ajv.addSchema(contract)
const prefix = contract.$id + '#/$defs/', base = '/api/formal-evaluation-context-bindings'
const pendingCheck = ajv.compile<PendingContext>({ type: 'object', additionalProperties: false, required: ['schema_version','actor','tenant_id','context','project_id','kind','key','body','body_sha256','phase','receipt'], properties: { schema_version: {const:'1.0.0'}, actor: {$ref:prefix+'Id'}, tenant_id: {$ref:prefix+'Id'}, context: {$ref:prefix+'Context'}, project_id: {$ref:prefix+'Id'}, kind: {enum:['AUTHOR','DECISION']}, key: {$ref:prefix+'Key'}, body: {oneOf:[{$ref:prefix+'Author'},{$ref:prefix+'Decide'}]}, body_sha256: {$ref:prefix+'Hash'}, phase: {const:'UNKNOWN'}, receipt: {anyOf:[{type:'null'},{$ref:prefix+'Receipt'}]} } })
function checked<T>(name: string, value: unknown): T { const check = ajv.getSchema(prefix + name); if (!check || !check(value)) fail('CONTENT_MISMATCH', '上下文绑定响应不符合固定合同：' + (check?.errors?.[0]?.instancePath || name)); return value as T }
function checkInput(name: string, value: unknown): void { const check=ajv.getSchema(prefix+name); if(!check || !check(value)) throw new Error('请先选择有效的项目、固定记录或分页位置') }
function headers(context: Context) { return { 'X-Schema-Version':'1.0.0', 'X-Context-Revision':context.context_revision } }
function reasonIssue(value: string): boolean { return !value.trim() || value.trim() !== value || value.includes('\0') }
function outcome(decision: ContextDecisionKind): ContextStatus { return decision === 'APPROVE' ? 'APPROVED' : decision === 'REJECT' ? 'REJECTED' : 'DISABLED' }
function validState(status: ContextStatus, revision: string): boolean { return revision === (status === 'DRAFT' ? '1' : status === 'DISABLED' ? '3' : '2') }
export function contextInputIssue(kind: ContextKind, value: unknown): string {
 const check = ajv.getSchema(prefix + (kind === 'AUTHOR' ? 'Author' : 'Decide'))!
 if (!check(value)) return '请补齐固定绑定字段：' + (check.errors?.[0]?.instancePath || '请求格式')
 const body = value as ContextAuthor | ContextDecide
 if (reasonIssue(body.reason)) return '请填写规范、非空的操作理由'
 if ('definition' in body && new Set(body.definition.node_bindings.map(n => n.node_kind)).size !== body.definition.node_bindings.length) return '同一个模型节点只能绑定一次'
 if (new TextEncoder().encode(canonical(body)).byteLength > 1048576) return '完整绑定请求超过 1 MiB，无法提交'
 return ''
}
/** A soft UI gate mirrors current-tenant enterprise inheritance and exact project grants. The server reauthorizes every action. */
export function contextAllowed(session: MedicalSession | null, project: string, kind: ContextKind): boolean { const tenant = session?.current_context?.tenant_id; return !!tenant && !!project && session!.action_summary.some(x => x.action === (kind === 'AUTHOR' ? 'agent.configure' : 'agent.publish') && (x.resource_kind === 'ENTERPRISE' && x.resource_id === tenant && x.project_id === null || x.resource_kind === 'PROJECT' && x.resource_id === project && x.project_id === project)) }
export function contextDecisionIssue(session: MedicalSession | null, view: ContextView | null, candidateAuthor: string | null, decision: ContextDecisionKind): string {
 if (!view || !session?.current_context) return '请先读取完整绑定与候选作者'
 if (!contextAllowed(session, view.fixed.definition.project_id, 'DECISION')) return '当前项目没有独立审核权限'
 if (!candidateAuthor) return '请先从真实候选目录核查候选作者'
 if ([view.fixed.created_by, candidateAuthor].includes(session.user.user_id)) return '绑定作者和 Agent 候选作者均不能审核此绑定'
 if (view.status === 'DRAFT' ? !['APPROVE','REJECT'].includes(decision) : view.status !== 'APPROVED' || decision !== 'DISABLE') return '此决定不适用于当前状态，请重新读取'
 return ''
}
export async function contextAvailability(reader: ContextReader, signal: AbortSignal): Promise<ContextAvailability> {
 const g = capabilityGuard(reader, signal), raw = await request.get('/api/formal-evaluation-context-feature-availability', { medical:'envelope', signal, headers:headers(g.context) }); g.assert()
 const e = envelope(raw), value = checked<ContextAvailability>('Availability', e.data)
 if (raw.status !== 200 || e.resource_revision !== '1' || canonical(value.current_context) !== canonical(g.context) || value.features.context_bindings !== (value.unavailable_reason === null)) fail('CONTENT_MISMATCH', '服务可用性不属于当前授权上下文')
 return detached(value)
}
function localReadError(raw: any, codes: string[], message: string): boolean {
 if (!codes.includes(raw.data?.code)) return false
 const error = validate<ApiError>('urn:medical:shared#/$defs/Error', raw.data)
 if (raw.headers['cache-control'] !== 'no-store' || raw.headers['x-schema-version'] !== '1.0.0' || raw.headers['x-request-id'] !== error.request_id || error.operation_id !== null || error.resource_revision !== null || error.retryable || error.allowed_actions.length || error.details.length) fail('CONTENT_MISMATCH', '只读查询错误响应不完整')
 throw new MedicalFailure(error.code, message, error.request_id)
}
export async function queryContexts(reader: ContextReader, project: string, cursor: string | null, signal: AbortSignal): Promise<ContextPage> {
 const body = {schema_version:'1.0.0', project_id:project, limit:20, cursor}; checkInput('Query', body); const g = capabilityGuard(reader, signal)
 const raw = await request.post(base + '/query', body, {medical:'envelope', signal, headers:{...headers(g.context),[g.session.csrf.header_name]:g.session.csrf.token}}); g.assert()
 if (raw.status === 409 && cursor !== null) localReadError(raw, ['CURSOR_EXPIRED','CURSOR_INVALID'], '目录游标已失效，请明确重新读取首页')
 const e = envelope(raw), page = checked<ContextPage>('Page', e.data)
 if (raw.status !== 200 || e.resource_revision !== '1' || cursor !== null && page.next_cursor === cursor || new Set(page.items.map(x => x.binding.version_id)).size !== page.items.length || page.items.some(x => x.project_id !== project || !validState(x.status, x.revision))) fail('CONTENT_MISMATCH', '上下文绑定目录范围、修订或游标不一致')
 return detached(page)
}
export async function verifyContextView(session: MedicalSession, input: unknown, resourceRevision: string | null, id?: string): Promise<ContextView> {
 const value = checked<ContextView>('View', input), f = value.fixed
 if (value.binding.version_id !== f.version_id || id !== undefined && id !== f.version_id || f.tenant_id !== session.current_context?.tenant_id || resourceRevision !== value.revision || !validState(value.status, value.revision) || await sourceDigest(f) !== value.binding.content_sha256) fail('CONTENT_MISMATCH', '绑定固定身份、内容摘要或当前修订不一致')
 if (contextInputIssue('AUTHOR', {schema_version:'1.0.0',definition:f.definition,reason:value.author_reason})) fail('CONTENT_MISMATCH', '原作者声明内容无效')
 let state: ContextStatus = 'DRAFT', revision = '1', time = Date.parse(f.created_at)
 const records = new Set<string>()
 for (const d of value.decisions) {
  if (d.tenant_id !== f.tenant_id || d.version_id !== f.version_id || d.binding_sha256 !== value.binding.content_sha256 || d.actor_id === f.created_by || d.previous_status !== state || d.previous_revision !== revision || d.revision !== (BigInt(revision)+1n).toString() || d.status !== outcome(d.decision) || (state === 'DRAFT' ? d.decision === 'DISABLE' : state !== 'APPROVED' || d.decision !== 'DISABLE') || Date.parse(d.decided_at) < time || records.has(d.record_id) || reasonIssue(d.reason)) fail('CONTENT_MISMATCH', '独立审核链与固定绑定不一致')
  records.add(d.record_id); state = d.status; revision = d.revision; time = Date.parse(d.decided_at)
 }
 if (state !== value.status || revision !== value.revision) fail('CONTENT_MISMATCH', '当前绑定状态与决定链不一致')
 return detached(value)
}
export async function readContext(reader: ContextReader, id: string, signal: AbortSignal): Promise<ContextView> {
 checkInput('Id', id); const g = capabilityGuard(reader, signal), raw = await request.get(base+'/'+id, {medical:'envelope',signal,headers:headers(g.context)}); g.assert(); const e = envelope(raw)
 if (raw.status !== 200) fail('CONTENT_MISMATCH','绑定详情读取状态无效')
 const value = await verifyContextView(g.session,e.data,e.resource_revision,id); g.assert(); return value
}
export const contextStorageKey = (s: MedicalSession) => 'medical.evaluation-context.pending:' + s.user.user_id + ':' + s.current_context?.tenant_id
export function readPendingContext(session: MedicalSession): PendingContext | null {
 const raw = sessionStorage.getItem(contextStorageKey(session)); if (raw === null) return null
 if (new TextEncoder().encode(raw).byteLength > 2200000 || projectJsonIssue(raw)) fail('CONTENT_MISMATCH','原绑定请求损坏，请保留记录并人工核查')
 const value: unknown = JSON.parse(raw)
 if (!pendingCheck(value) || value.actor !== session.user.user_id || value.tenant_id !== session.current_context?.tenant_id || value.context.tenant_id !== value.tenant_id || contextInputIssue(value.kind,value.body) || value.kind === 'AUTHOR' && (value.body as ContextAuthor).definition.project_id !== value.project_id) fail('CONTENT_MISMATCH','原请求身份、类型或项目不一致')
 validContext(value.context); if (value.receipt) verifyReceipt(value,value.receipt,value.receipt.revision)
 return detached(value)
}
async function verifyPending(session: MedicalSession, pending: PendingContext) { if (canonical(readPendingContext(session)) !== canonical(pending) || await sourceDigest(pending.body) !== pending.body_sha256) fail('CONTENT_MISMATCH','原请求正文、摘要或原键已变化') }
function persist(session: MedicalSession, pending: PendingContext) { if (!pendingCheck(pending)) fail('CONTENT_MISMATCH','不能保存不完整原请求'); sessionStorage.setItem(contextStorageKey(session),canonical(pending)); if (canonical(readPendingContext(session)) !== canonical(pending)) fail('CONTENT_MISMATCH','原请求未可靠保存，本次不会发送') }
function verifyReceipt(pending: PendingContext, input: unknown, resourceRevision: string | null): ContextReceipt {
 const receipt = checked<ContextReceipt>('Receipt',input)
 if (resourceRevision !== receipt.revision || !validState(receipt.status,receipt.revision)) fail('CONTENT_MISMATCH','原操作回执修订不一致')
 if (pending.kind === 'AUTHOR') { if (receipt.status !== 'DRAFT' || receipt.revision !== '1') fail('CONTENT_MISMATCH','创建回执冒充后续审核结果') }
 else { const body = pending.body as ContextDecide; if (canonical(receipt.binding) !== canonical(body.binding) || receipt.status !== outcome(body.decision) || BigInt(receipt.revision) !== BigInt(body.expected_revision)+1n) fail('CONTENT_MISMATCH','决定回执不属于原固定版本和修订') }
 if (pending.receipt && canonical(pending.receipt) !== canonical(receipt)) fail('CONTENT_MISMATCH','回读回执与已保存原回执不一致')
 return detached(receipt)
}
export async function submitContext(reader: ContextReader, kind: ContextKind, body: ContextAuthor | ContextDecide, project: string, signal: AbortSignal, onPending: (p: PendingContext) => void): Promise<PendingContext> {
 const issue = contextInputIssue(kind,body); if (issue) throw new Error(issue)
 const g = capabilityGuard(reader,signal), frozen = detached(body)
 if (!contextAllowed(g.session,project,kind)) throw new Error('当前项目没有此项写入权限')
 if (kind === 'AUTHOR' && (frozen as ContextAuthor).definition.project_id !== project) throw new Error('作者请求项目与选定范围不一致')
 if (readPendingContext(g.session)) throw new Error('已有原请求待核查，不能生成新键或重发')
 const availability = await contextAvailability(reader,signal); g.assert(); if (!availability.features.context_bindings) throw new Error('当前部署未启用上下文绑定服务')
 const hash = await sourceDigest(frozen); g.assert(); if (readPendingContext(g.session)) throw new Error('另一原请求已经保存，请先核查')
 const pending: PendingContext = {schema_version:'1.0.0',actor:g.session.user.user_id,tenant_id:g.context.tenant_id,context:detached(g.context),project_id:project,kind,key:crypto.randomUUID(),body:frozen,body_sha256:hash,phase:'UNKNOWN',receipt:null}
 // Persist the exact key and complete request before the first mutation. Unknown is never replayable.
 persist(g.session,pending); onPending(detached(pending)); g.assert()
 const raw = await request.post(kind === 'AUTHOR' ? base : '/api/formal-evaluation-context-decisions',detached(pending.body),{medical:'envelope',signal,headers:{...headers(g.context),'Idempotency-Key':pending.key,[g.session.csrf.header_name]:g.session.csrf.token}}); g.assert()
 const e = envelope(raw); if (raw.status !== 201) fail('CONTENT_MISMATCH','写入没有返回固定创建回执')
 const receipt = verifyReceipt(pending,e.data,e.resource_revision); await verifyPending(g.session,pending); g.assert()
 const saved = {...pending,receipt}; persist(g.session,saved); onPending(detached(saved)); return detached(saved)
}
/** Read-only original-key recovery; no new grant, replay, re-key, or implicit decision. */
export async function recoverContext(reader: ContextReader, pending: PendingContext, signal: AbortSignal): Promise<ContextView> {
 const g = capabilityGuard(reader,signal); await verifyPending(g.session,pending); g.assert()
 const body = {schema_version:'1.0.0',kind:pending.kind,original_key:pending.key,request:detached(pending.body)}; checked('Recovery',body)
 const raw = await request.post(base+'/recovery',body,{medical:'envelope',signal,headers:{...headers(g.context),[g.session.csrf.header_name]:g.session.csrf.token}}); g.assert()
 if (raw.status === 404) localReadError(raw,['OBJECT_UNAVAILABLE'],'尚未找到可核验的原操作；原请求继续保留，不自动重发')
 const e = envelope(raw); if (raw.status !== 200) fail('CONTENT_MISMATCH','原键只读恢复状态无效')
 const receipt = verifyReceipt(pending,e.data,e.resource_revision), view = await readContext(reader,receipt.binding.version_id,signal); g.assert()
 if (canonical(view.binding) !== canonical(receipt.binding) || view.fixed.definition.project_id !== pending.project_id || BigInt(view.revision) < BigInt(receipt.revision)) fail('CONTENT_MISMATCH','原回执与固定绑定身份不一致')
 if (pending.kind === 'AUTHOR') { const original = pending.body as ContextAuthor; if (view.fixed.created_by !== pending.actor || canonical(view.fixed.definition) !== canonical(original.definition) || view.author_reason !== original.reason) fail('CONTENT_MISMATCH','固定作者声明不属于完整原请求') }
 else { const original = pending.body as ContextDecide, d = view.decisions.find(x => x.revision === receipt.revision); if (!d || d.actor_id !== pending.actor || d.decision !== original.decision || d.previous_revision !== original.expected_revision || d.reason !== original.reason) fail('CONTENT_MISMATCH','固定决定链没有对应的原决定') }
 await verifyPending(g.session,pending); g.assert(); sessionStorage.removeItem(contextStorageKey(g.session)); if (sessionStorage.getItem(contextStorageKey(g.session)) !== null) fail('CONTENT_MISMATCH','原操作已核验，但本地记录未能清除')
 return detached(view)
}
