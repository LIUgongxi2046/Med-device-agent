import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import policySchema from '../../../../business/medical-persistence/src/main/resources/agent-execution-policy.schema.json'
import availabilitySchema from '../../../../business/medical-persistence/src/main/resources/agent-execution-policy-feature-availability.schema.json'
import publicationSchema from '../../../../business/medical-persistence/src/main/resources/agent-publication.schema.json'
import { fail, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import { capabilityCanonical as canonical, detached, capabilityGuard as policyGuard } from './formal-capability-domain'
import { projectJsonIssue } from './project-json'
export { canonical, detached, policyGuard }
export type PolicyReader = () => MedicalSession
export type PolicyKind = 'EXECUTION_POLICY_AUTHOR' | 'EXECUTION_POLICY_DECISION'
export type PolicyStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'DISABLED'
export type DecisionKind = 'APPROVE' | 'REJECT' | 'DISABLE'
export interface PolicyAction { resource_kind: string; target_selector: string; action: string; purpose: string; fixed_version: Basis | null; artifact_kinds: string[] }
export interface PolicyBinding { node_kind: string; role_version: Basis; model_version: Basis | null; tokenizer_version: Basis | null; tokenizer_artifact: {artifact: {artifact_id: string; sha256: string; size_bytes: string; media_type: string}; schema: {schema_ref: string; schema_sha256: string}} | null; generation_parameters: Record<string, unknown>; actions: PolicyAction[] }
export interface RetrievalInput { mode: 'FULLTEXT'; maximum_results: number; permitted_fields: string[]; index_configuration: Basis }
export interface PolicyDefinitionInput { schema_version: '1.0.0'; project_id: string | null; business_purpose: 'DOCUMENT_BODY'; profile_evidence_purpose: string; allowed_classifications: string[];
 source_selection: { mode: 'FIXED_SOURCE_VERSIONS'; sources: Basis[] }; retrieval_profiles: RetrievalInput[];
 network_policy: { https_origins: []; external_requests_allowed: false }; worker: { service_uri: string; audience: string }; bindings: PolicyBinding[];
 locale: string; timezone: string; environment: string; region: string; training_allowed: false; valid_from: string; valid_until: string | null }
export interface PolicyDefinition extends Omit<PolicyDefinitionInput,'source_selection'|'retrieval_profiles'|'network_policy'> {
 source_selection: PolicyDefinitionInput['source_selection'] & {version: Basis}; retrieval_profiles: (RetrievalInput & {version: Basis})[]; network_policy: PolicyDefinitionInput['network_policy'] & {version: Basis} }
export interface PolicyAuthor { schema_version: '1.0.0'; definition: PolicyDefinitionInput; reason: string }
export interface PolicyDecide { schema_version: '1.0.0'; expected_revision: string; policy_version: Basis; decision: DecisionKind; reason: string }
export interface PolicyFixed { schema_version: '1.0.0'; tenant_id: string; policy_id: string; version_id: string; author_record_id: string; definition: PolicyDefinition; definition_sha256: string; created_by: string; created_at: string; classification: string }
export interface PolicyDecision { schema_version: '1.0.0'; tenant_id: string; project_id: string | null; policy_id: string; version_id: string; definition_sha256: string; decision_record_id: string; previous_status: 'DRAFT'|'APPROVED'; previous_revision: string; decision: DecisionKind; status: Exclude<PolicyStatus,'DRAFT'>; revision: string; actor_id: string; decided_at: string; authority_grant_id: string; authority_revision: string; reason: string }
export interface PolicyView { schema_version: '1.0.0'; fixed: PolicyFixed; status: PolicyStatus; revision: string; latest_decision: PolicyDecision | null; missing_input_reasons: string[] }
export interface PolicyPage { schema_version: '1.0.0'; items: PolicyView[]; next_cursor: string | null }
export interface PolicyAvailability { schema_version: '1.0.0'; current_context: Context; features: {policy_authoring: boolean; model_policy_approval: boolean} }
export interface PolicyReceipt { schema_version: '1.0.0'; operation_id: string; resource_id: string; kind: PolicyKind; content_sha256: string; request_body_sha256: string }
export interface PendingPolicy { schema_version: '1.0.0'; actor: string; tenant_id: string; context: Context; key: string; kind: PolicyKind; operation: string; body: PolicyAuthor | PolicyDecide; body_sha256: string; phase: 'PENDING'|'UNKNOWN'; receipt: PolicyReceipt | null }
const ajv = new Ajv2020({strictSchema: true, strictTypes: false, allErrors: true}); addFormats(ajv); ajv.addSchema(policySchema); ajv.addSchema(availabilitySchema); ajv.addSchema(publicationSchema)
const prefix = policySchema.$id + '#/$defs/', publication = publicationSchema.$id + '#/$defs/'
export const operationName = (kind: PolicyKind) => 'AGENT-PUBLICATION-' + kind
export function policyValidate<T>(name: string, value: unknown): T { const check = ajv.getSchema(prefix + name); if (!check || !check(value)) fail('SCHEMA_INVALID', '执行策略合同不匹配：' + (check?.errors?.[0]?.instancePath || name)); return value as T }
export function policyAllowed(session: MedicalSession | null, project: string | null, action: 'agent.configure'|'agent.publish'): boolean { const tenant = session?.current_context?.tenant_id; return !!tenant && !!session?.action_summary.some(item => item.action === action && (item.resource_kind === 'ENTERPRISE' && item.resource_id === tenant && item.project_id === null || project !== null && item.resource_kind === 'PROJECT' && item.resource_id === project && item.project_id === project)) }
export function decisionIssue(session: MedicalSession, view: PolicyView, decision: DecisionKind, availability: PolicyAvailability): string {
 if (!availability.features.policy_authoring) return '当前部署未启用执行策略服务'
 if (!policyAllowed(session, view.fixed.definition.project_id, 'agent.publish')) return '当前范围没有 agent.publish 审批权限'
 if (decision === 'DISABLE' ? view.status !== 'APPROVED' : view.status !== 'DRAFT') return '此状态不接受所选决定，请重新读取当前版本'
 if (decision === 'APPROVE' && session.user.user_id === view.fixed.created_by) return '作者不能批准自己的执行策略，请由独立审批人审核'
 if (decision === 'APPROVE' && view.fixed.definition.bindings.some(item => item.model_version !== null) && !availability.features.model_policy_approval) return '真实模型适配器未就绪，当前部署不能批准含模型的策略'
 return ''
}
export function inputDefinition(value: PolicyDefinition): PolicyDefinitionInput { const copy = detached(value); const {version: _s, ...source_selection} = copy.source_selection, {version: _n, ...network_policy} = copy.network_policy; return {...copy, source_selection, network_policy, retrieval_profiles: copy.retrieval_profiles.map(({version: _r, ...item}) => item)} }
export function policyInputIssue(kind: PolicyKind, value: unknown): string {
 try { policyValidate(kind === 'EXECUTION_POLICY_AUTHOR' ? 'Author':'Decide', value) } catch (error) { return error instanceof Error ? error.message : '声明格式无效' }
 const body = value as PolicyAuthor | PolicyDecide
 if (!body.reason.trim() || body.reason.trim() !== body.reason || body.reason.normalize('NFC') !== body.reason || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(body.reason)) return '请填写规范、非空的原因'
 if ('definition' in body) { const d = body.definition
  if (d.valid_until !== null && Date.parse(d.valid_from) >= Date.parse(d.valid_until)) return '结束时间必须晚于开始时间'
  try { const uri = new URL(d.worker.service_uri); if (uri.username || uri.password || uri.hash || uri.search) return 'Worker 身份 URI 不得包含凭据、查询或片段'; Intl.getCanonicalLocales(d.locale); new Intl.DateTimeFormat('en',{timeZone:d.timezone}) } catch { return '请核查 Worker URI、语言和时区' }
  for (const field of [d.environment,d.region,d.worker.audience]) if (!field.trim() || field.trim() !== field || field.normalize('NFC') !== field) return '环境、区域和 audience 必须使用规范的真实部署配置'
  if (new Set(d.source_selection.sources.map(x=>x.version_id)).size !== d.source_selection.sources.length || new Set(d.bindings.map(x=>x.node_kind)).size !== d.bindings.length) return '来源版本或节点不得重复'
  for (const b of d.bindings) if (b.model_version === null && (b.node_kind !== 'N00' || b.tokenizer_version !== null || b.tokenizer_artifact !== null || Object.keys(b.generation_parameters).length)) return '非模型策略仅支持 N00，且不得包含 tokenizer 或生成参数'
 }
 if (new TextEncoder().encode(canonical(body)).byteLength > 1048576) return '执行策略声明超过 1 MiB 上限'
 return ''
}
export async function verifyPolicyView(session: MedicalSession, input: unknown, resourceRevision: string | null, version?: string): Promise<PolicyView> {
 const value = policyValidate<PolicyView>('View', input), f = value.fixed
 if (resourceRevision !== '1' || f.tenant_id !== session.current_context?.tenant_id || f.policy_id === f.version_id || f.author_record_id === f.version_id || version !== undefined && f.version_id !== version || await sourceDigest(f.definition) !== f.definition_sha256) fail('CONTENT_MISMATCH','执行策略归属、固定身份或内容摘要不一致')
 const componentIds = new Set<string>(), dependencies = new Set<string>([...f.definition.source_selection.sources.map(x=>x.version_id), ...f.definition.bindings.flatMap(x=>[x.role_version.version_id, ...(x.model_version?[x.model_version.version_id]:[]), ...(x.tokenizer_version?[x.tokenizer_version.version_id]:[]), ...x.actions.flatMap(a=>a.fixed_version?[a.fixed_version.version_id]:[])]), ...f.definition.retrieval_profiles.map(x=>x.index_configuration.version_id)])
 for (const component of [f.definition.source_selection, ...f.definition.retrieval_profiles, f.definition.network_policy]) { const {version: basis, ...body} = component; if (componentIds.has(basis.version_id) || dependencies.has(basis.version_id) || await sourceDigest(body) !== basis.content_sha256) fail('CONTENT_MISMATCH','策略组件固定身份或摘要不一致'); componentIds.add(basis.version_id) }
 const d = value.latest_decision
 if (!d) { if (value.status !== 'DRAFT' || value.revision !== '1') fail('CONTENT_MISMATCH','策略当前状态缺少决定证据') }
 else if (d.tenant_id !== f.tenant_id || d.project_id !== f.definition.project_id || d.policy_id !== f.policy_id || d.version_id !== f.version_id || d.definition_sha256 !== f.definition_sha256 || d.status !== value.status || d.revision !== value.revision || BigInt(d.previous_revision) + 1n !== BigInt(d.revision) || (d.decision === 'DISABLE' ? d.previous_status !== 'APPROVED' || d.status !== 'DISABLED' : d.previous_status !== 'DRAFT' || d.status !== (d.decision === 'APPROVE' ? 'APPROVED':'REJECTED')) || d.decision === 'APPROVE' && d.actor_id === f.created_by) fail('CONTENT_MISMATCH','策略决定、独立审批人或 CAS 修订证据不一致')
 return detached(value)
}
export function verifyPolicyAvailability(input: unknown, context: Context, resourceRevision: string | null): PolicyAvailability { const check = ajv.getSchema(availabilitySchema.$id)!; if (!check(input)) fail('SCHEMA_INVALID','执行策略可用性合同不匹配'); const value = input as PolicyAvailability; validContext(value.current_context); if (canonical(value.current_context) !== canonical(context) || resourceRevision !== context.context_revision) fail('CONTENT_MISMATCH','策略可用性不属于当前上下文'); return detached(value) }
const pendingCheck = ajv.compile({type:'object',additionalProperties:false,required:['schema_version','actor','tenant_id','context','key','kind','operation','body','body_sha256','phase','receipt'],properties:{schema_version:{const:'1.0.0'},actor:{$ref:prefix+'Id'},tenant_id:{$ref:prefix+'Id'},context:{$ref:publication+'Context'},key:{$ref:prefix+'Id'},kind:{enum:['EXECUTION_POLICY_AUTHOR','EXECUTION_POLICY_DECISION']},operation:{type:'string'},body:{anyOf:[{$ref:prefix+'Author'},{$ref:prefix+'Decide'}]},body_sha256:{$ref:prefix+'Hash'},phase:{enum:['PENDING','UNKNOWN']},receipt:{anyOf:[{$ref:publication+'Receipt'},{type:'null'}]}}})
function storageKey(session: MedicalSession) { if (!session.current_context) fail('ACCESS_DENIED','请先选择当前企业'); return 'medical.execution-policy.pending:' + session.user.user_id + ':' + session.current_context.tenant_id }
export function readPendingPolicy(session: MedicalSession): PendingPolicy | null { const raw = sessionStorage.getItem(storageKey(session)); if (raw === null) return null
 if (new TextEncoder().encode(raw).byteLength > 1064960 || projectJsonIssue(raw)) fail('CONTENT_MISMATCH','原策略请求损坏或超过容量，请保留记录人工核查')
 let value: unknown; try { value = JSON.parse(raw) } catch { fail('CONTENT_MISMATCH','原策略请求无法解析') }
 if (!pendingCheck(value)) fail('CONTENT_MISMATCH','原策略请求恢复合同无效')
 const p = value as PendingPolicy
 if (p.actor !== session.user.user_id || p.tenant_id !== session.current_context?.tenant_id || p.context.tenant_id !== p.tenant_id || p.operation !== operationName(p.kind) || policyInputIssue(p.kind,p.body) || p.receipt && p.receipt.kind !== p.kind) fail('CONTENT_MISMATCH','原策略身份、正文或操作不一致')
 return detached(p)
}
export function verifyPolicyReceipt(p: PendingPolicy, input: unknown, revision: string | null): PolicyReceipt { const check = ajv.getSchema(publication+'Receipt')!; if (!check(input)) fail('SCHEMA_INVALID','执行策略类型回执不符合固定合同'); const receipt = input as PolicyReceipt; if (revision !== '1' || receipt.kind !== p.kind || receipt.request_body_sha256 !== p.body_sha256) fail('CONTENT_MISMATCH','类型回执与原请求正文不一致'); return detached(receipt) }
export async function verifyPendingPolicy(session: MedicalSession, p: PendingPolicy) { const saved = readPendingPolicy(session); if (!saved || canonical(saved) !== canonical(p) || await sourceDigest(p.body) !== p.body_sha256) fail('CONTENT_MISMATCH','原策略正文、摘要或请求键已改变'); if (p.receipt) verifyPolicyReceipt(p,p.receipt,'1') }
function persist(session: MedicalSession,p: PendingPolicy) { if (!pendingCheck(p)) fail('CONTENT_MISMATCH','不能保存不完整的原策略请求'); sessionStorage.setItem(storageKey(session),canonical(p)); if (canonical(readPendingPolicy(session)) !== canonical(p)) fail('CONTENT_MISMATCH','原策略请求没有可靠保存，本次不会发送') }
export async function preparePolicy(reader: PolicyReader, kind: PolicyKind, body: PolicyAuthor | PolicyDecide, project: string | null, signal: AbortSignal): Promise<PendingPolicy> { const guard = policyGuard(reader,signal), frozen=detached(body), issue=policyInputIssue(kind,frozen); if(issue) fail('SCHEMA_INVALID',issue)
 if (!policyAllowed(guard.session,project,kind === 'EXECUTION_POLICY_AUTHOR'?'agent.configure':'agent.publish')) fail('ACCESS_DENIED','当前范围没有执行策略写入权限')
 if (readPendingPolicy(guard.session)) fail('IDEMPOTENCY_CONFLICT','已有待核查原请求，不能生成新键或重发')
 const hash = await sourceDigest(frozen); guard.assert(); if (readPendingPolicy(guard.session)) fail('IDEMPOTENCY_CONFLICT','另一策略请求已保存，请先核查原操作')
 const p: PendingPolicy={schema_version:'1.0.0',actor:guard.session.user.user_id,tenant_id:guard.context.tenant_id,context:guard.context,key:crypto.randomUUID(),kind,operation:operationName(kind),body:frozen,body_sha256:hash,phase:'PENDING',receipt:null}; persist(guard.session,p); return detached(p)
}
export async function dispatchPolicy(reader: PolicyReader,p: PendingPolicy,signal: AbortSignal): Promise<PendingPolicy> { const guard=policyGuard(reader,signal); await verifyPendingPolicy(guard.session,p); guard.assert(); if(p.phase!=='PENDING'||canonical(p.context)!==canonical(guard.context)) fail('ACTION_STALE','原策略请求已发送或上下文已变化，仅可读取恢复'); const next: PendingPolicy={...detached(p),phase:'UNKNOWN'}; persist(guard.session,next); return next }
export async function rememberPolicyReceipt(reader: PolicyReader,p: PendingPolicy,receipt: PolicyReceipt,signal: AbortSignal) { const guard=policyGuard(reader,signal); await verifyPendingPolicy(guard.session,p); guard.assert(); verifyPolicyReceipt(p,receipt,'1'); const next={...detached(p),receipt:detached(receipt)};persist(guard.session,next);return next }
export async function settlePolicy(reader: PolicyReader,p: PendingPolicy,receipt: PolicyReceipt,view: PolicyView,signal: AbortSignal): Promise<void> { const guard=policyGuard(reader,signal); await verifyPendingPolicy(guard.session,p);guard.assert(); verifyPolicyReceipt(p,receipt,'1');await verifyPolicyView(guard.session,view,'1');guard.assert()
 if(p.receipt && canonical(p.receipt)!==canonical(receipt)) fail('CONTENT_MISMATCH','原回执与类型回读不一致')
 if(p.kind==='EXECUTION_POLICY_AUTHOR') { const body=p.body as PolicyAuthor; if(view.fixed.created_by!==p.actor||view.fixed.author_record_id!==receipt.resource_id||canonical(inputDefinition(view.fixed.definition))!==canonical(body.definition)||await sourceDigest(view.fixed)!==receipt.content_sha256) fail('CONTENT_MISMATCH','固定策略与原作者声明不一致') }
 else { const body=p.body as PolicyDecide,d=view.latest_decision; if(view.fixed.version_id!==body.policy_version.version_id||view.fixed.definition_sha256!==body.policy_version.content_sha256||BigInt(view.revision)<=BigInt(body.expected_revision)) fail('CONTENT_MISMATCH','原决定目标或当前修订不一致'); if(d?.decision_record_id===receipt.resource_id && (d.actor_id!==p.actor||d.previous_revision!==body.expected_revision||d.decision!==body.decision||d.reason!==body.reason||await sourceDigest(d)!==receipt.content_sha256)) fail('CONTENT_MISMATCH','原决定内容与类型回执不一致'); if(d?.decision_record_id!==receipt.resource_id && BigInt(view.revision)===BigInt(body.expected_revision)+1n) fail('CONTENT_MISMATCH','原决定修订对应另一记录') }
 guard.assert();await verifyPendingPolicy(guard.session,p);guard.assert();sessionStorage.removeItem(storageKey(guard.session))
}
