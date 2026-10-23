import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import { canonicalizeEx } from 'json-canonicalize'
import request from '@/utils/request'
import metricsSchema from '../../../../business/medical-persistence/src/main/resources/business-operations-metrics.schema.json'
import agentFeedbackSchema from '../../../../business/medical-persistence/src/main/resources/business-operations-feedback.schema.json'
import knowledgeFeedbackSchema from '../../../../business/medical-persistence/src/main/resources/knowledge-rule-feedback.schema.json'
import { envelope, fail } from './protocol'
import type { MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'

const validator = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true })
addFormats(validator)
for (const schema of [metricsSchema, agentFeedbackSchema, knowledgeFeedbackSchema]) validator.addSchema(schema)
function checked<T>(reference: string, data: unknown): T {
  const check = validator.getSchema(reference)
  if (!check || !check(data)) fail('SCHEMA_INVALID', '运营数据不符合接口定义：' + (check?.errors?.[0]?.instancePath ?? reference))
  return data as T
}
export interface Ratio { numerator: number; denominator: number; rate: string | null; evidence_status: string; definition: string }
export interface CurrencyUsage { currency: string; invocations: number; settled_count: number; unknown_count: number; known_amount: string; total_amount: string | null; known_tokens: string; known_provider_calls: string }
export interface OperationsQuery {
  project_id?: string; from: string; to: string; actor_id?: string; device_category?: string; product_type?: string
  stage?: string; document_type?: string; role_version?: string; include_records?: boolean
}
export interface OperationsMetrics {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; actor_id: string | null; from: string; to: string
  observed_at: string; metric_revision: string; auth_epoch: string; governance_epoch: string; complete: true
  registered_task_invocations: number; independent_revision_return_count: number; adoption: Ratio; return: Ratio
  cost_by_currency: CurrencyUsage[]; records_included: boolean; records: Record<string, unknown>[]
  project_groups: { project_id: string; invocations: number; candidates: number; formal_review_decisions: number; independent_revision_returns: number }[]
  dimension_groups: { dimensions: { device_category: string | null; product_type: string | null; stage: string | null; document_type: string; role_version: string | null }; registered_task_invocations: number; adoption: Ratio; return: Ratio; cost_by_currency: CurrencyUsage[] }[]
  exclusions: string[]; filter_exclusion_counts: Record<string, number>; applied_filters: Record<string, unknown>
}
export type FeedbackKind = 'AGENT' | 'SOURCE' | 'RULE'
export interface FeedbackReceipt {
  schema_version: '1.0.0'; feedback_id: string; project_id: string; target_version: string; revision: string
  state: string; reason: string; actor_id: string; recorded_at: string; kind?: 'SOURCE' | 'RULE'
  revision_version: string | null; acceptance_id?: string | null; release_id?: string | null
  proposal_id?: string | null; decision_id?: string | null; approval_proof?: Record<string, unknown> | null
}
export interface FeedbackPage { schema_version: '1.0.0'; project_id: string; complete: boolean; next_after_id: string | null; items: FeedbackReceipt[] }
export interface FeedbackHistory { schema_version: '1.0.0'; feedback_id: string; revision: string; events: FeedbackReceipt[]; original?: Record<string, unknown> }
export const feedbackStateLabels: Record<string, string> = { OPEN: '待审核', PENDING_REVIEW: '待审核', REVIEWED: '已确认',
  REVISION_LINKED: '已纳入修订', PROPOSAL_LINKED: '已关联修订', EVALUATED: '已完成评测', PUBLISHED: '已发布', CLOSED: '已完成', REJECTED: '未采纳' }
export const feedbackKindLabels: Record<FeedbackKind, string> = { AGENT: 'Agent', SOURCE: '资料', RULE: '检查规则' }
export function operationsAllowed(session: MedicalSession | null, action: string, project?: string): boolean {
  return Boolean(session?.current_context && session.action_summary.some(grant => grant.action === action &&
    (grant.resource_kind === 'ENTERPRISE' && grant.resource_id === session.current_context!.tenant_id && grant.project_id === null
      || project !== undefined && grant.resource_kind === 'PROJECT' && grant.project_id === project && grant.resource_id === project)))
}
function headers(session: MedicalSession) {
  if (!session.current_context) fail('ACCESS_DENIED', '请选择工作组织')
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision }
}
function contextResult(session: MedicalSession, response: Parameters<typeof envelope>[0]) {
  const result = envelope(response)
  if (result.resource_revision !== session.current_context?.context_revision) fail('CONTEXT_CHANGED', '运营结果对应的工作组织已经变化')
  return result.data
}
export async function readOperationsMetrics(session: MedicalSession, query: OperationsQuery, signal: AbortSignal): Promise<OperationsMetrics> {
  const data = contextResult(session, await request.get('/api/business-operations/metrics', { medical: 'envelope', params: query, signal, headers: headers(session) }))
  const result = checked<OperationsMetrics>(metricsSchema.$id, data)
  if (result.tenant_id !== session.current_context?.tenant_id || result.project_id !== (query.project_id ?? null)
      || Date.parse(result.from) !== Date.parse(query.from) || Date.parse(result.to) !== Date.parse(query.to)) fail('CONTENT_MISMATCH', '统计范围与当前选择不一致')
  return result
}
export async function exportOperationsMetrics(session: MedicalSession, query: OperationsQuery, signal: AbortSignal): Promise<Blob> {
  const response = await request.get('/api/business-operations/metrics.csv', { medical: 'bytes', responseType: 'arraybuffer', params: query, signal, headers: headers(session) })
  if(String(response.headers['content-type']).startsWith('application/json'))envelope({...response,data:JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(response.data))})
  if (response.status !== 200 || !String(response.headers['content-type']).startsWith('text/csv')) fail('CONTENT_MISMATCH', '统计导出没有返回CSV文件')
  return new Blob([response.data], { type: 'text/csv;charset=utf-8' })
}
const feedbackPath = (kind: FeedbackKind) => '/api/business-operations/' + (kind === 'AGENT' ? 'feedback' : 'knowledge-feedback')
function receipt(kind: FeedbackKind, data: unknown) {
  const result = checked<FeedbackReceipt>(kind === 'AGENT' ? agentFeedbackSchema.$id : knowledgeFeedbackSchema.$id + '#/$defs/Receipt', data)
  if (kind !== 'AGENT' && result.kind !== kind) fail('CONTENT_MISMATCH', '反馈类型与当前选择不一致')
  return result
}
export async function readFeedbackPage(session: MedicalSession, kind: FeedbackKind, project: string, after: string | null, signal: AbortSignal): Promise<FeedbackPage> {
  const result = contextResult(session, await request.get(feedbackPath(kind), { medical: 'envelope', params: { project_id: project, ...(after ? { after_id: after } : {}) }, signal, headers: headers(session) })) as FeedbackPage
  if (!result || result.schema_version !== '1.0.0' || result.project_id !== project || !Array.isArray(result.items)
      || typeof result.complete !== 'boolean' || result.next_after_id !== null && typeof result.next_after_id !== 'string') fail('SCHEMA_INVALID', '反馈目录格式无效')
  const items = result.items.map(item => receipt(item.kind ?? 'AGENT', item)).filter(item => kind === 'AGENT' || item.kind === kind)
  if(items.some(item=>item.project_id!==project))fail('CONTENT_MISMATCH','反馈记录不属于当前项目')
  if (result.next_after_id === after && after !== null) fail('CONTENT_MISMATCH', '反馈目录分页没有前进')
  return { ...result, items }
}
export async function readFeedbackHistory(session: MedicalSession, kind: FeedbackKind, id: string, signal: AbortSignal): Promise<FeedbackHistory> {
  const result = contextResult(session, await request.get(feedbackPath(kind) + '/' + id, { medical: 'envelope', signal, headers: headers(session) })) as FeedbackHistory
  if (!result || result.schema_version !== '1.0.0' || result.feedback_id !== id || !Array.isArray(result.events) || !result.events.length) fail('SCHEMA_INVALID', '反馈处理记录格式无效')
  result.events.forEach((event, index) => { receipt(kind, event); if (event.feedback_id !== id || event.revision !== String(index + 1)) fail('CONTENT_MISMATCH', '反馈记录版本不连续') })
  if (result.events.at(-1)?.revision !== result.revision) fail('CONTENT_MISMATCH', '反馈当前版本与处理记录不一致')
  return result
}
export interface FeedbackMetadata { path:string;key:string;body_sha256:string;request_sha256:string;actor:string;tenant_id:string;kind:FeedbackKind;operation:string;project_id:string;feedback_id:string|null;parameters:Record<string,unknown> }
export interface FeedbackWrite extends FeedbackMetadata { body:Record<string,unknown>;context:string }
const pendingKey=(session:MedicalSession)=>'medical.feedback.write:'+session.user.user_id+':'+session.current_context!.tenant_id
export function readPendingFeedback(session:MedicalSession):FeedbackMetadata|null{
  if(!session.current_context)return null
  const text=sessionStorage.getItem(pendingKey(session));if(text===null)return null
  const value=JSON.parse(text) as FeedbackMetadata
  if(!value||value.actor!==session.user.user_id||value.tenant_id!==session.current_context.tenant_id||!['AGENT','SOURCE','RULE'].includes(value.kind)
    ||typeof value.key!=='string'||!(/^[a-f0-9-]{36}$/).test(value.key)||typeof value.body_sha256!=='string'||!(/^[a-f0-9]{64}$/).test(value.body_sha256)
    ||typeof value.request_sha256!=='string'||!(/^[a-f0-9]{64}$/).test(value.request_sha256)||!value.parameters||typeof value.parameters!=='object'
    ||Object.hasOwn(value.parameters,'reason')||value.path!==feedbackPath(value.kind)+(value.feedback_id?'/'+value.feedback_id+'/'+value.operation:''))fail('CONTENT_MISMATCH','原反馈操作元数据无效')
  return value
}
export function clearFeedbackWrite(session:MedicalSession,pending:FeedbackMetadata){const saved=readPendingFeedback(session);if(!saved||saved.key!==pending.key||saved.request_sha256!==pending.request_sha256)fail('CONTENT_MISMATCH','原反馈操作身份不一致');sessionStorage.removeItem(pendingKey(session))}
async function requestDigest(kind:FeedbackKind,operation:string,id:string|null,body:Record<string,unknown>){
  const definitions:Record<string,string>={proposals:'LinkProposal',reviews:'LinkReview',closures:'Close',rejections:'Reject'}
  const method=operation==='create'?'CREATE':kind==='AGENT'?'TRANSITION':definitions[operation]
  if(!method)fail('SCHEMA_INVALID','反馈操作类型无效')
  return sourceDigest({operation:method,...(kind==='AGENT'&&operation==='create'?{}:{feedback_id:id}),request:body})
}
export function feedbackInputIssue(kind:FeedbackKind,operation:string,body:Record<string,unknown>):string{
  if(kind==='AGENT')return ''
  const definitions:Record<string,string>={create:'Create',proposals:'LinkProposal',reviews:'LinkReview',closures:'Close',rejections:'Reject'}
  const check=validator.getSchema(knowledgeFeedbackSchema.$id+'#/$defs/'+definitions[operation])
  if(!check)fail('SCHEMA_INVALID','反馈操作没有对应定义')
  return check(body)?'':'请补充有效的处理依据及记录：'+(check.errors?.[0]?.instancePath??'输入内容')
}
export async function prepareFeedbackWrite(session: MedicalSession, kind: FeedbackKind, operation: string, body: Record<string, unknown>, id?: string,project?:string):Promise<FeedbackWrite> {
  headers(session)
  if (kind !== 'AGENT') {
    const definitions: Record<string, string> = { create: 'Create', proposals: 'LinkProposal', reviews: 'LinkReview', closures: 'Close', rejections: 'Reject' }
    checked(knowledgeFeedbackSchema.$id + '#/$defs/' + definitions[operation], body)
  }
  const path = feedbackPath(kind) + (id ? '/' + id + '/' + operation : '')
  const bodyHash=await sourceDigest(body),requestHash=await requestDigest(kind,operation,id??null,body),saved=readPendingFeedback(session)
  if(saved&&(saved.path!==path||saved.kind!==kind||saved.body_sha256!==bodyHash||saved.request_sha256!==requestHash))fail('IDEMPOTENCY_CONFLICT','请核查原反馈操作；重新填写时必须保持完整原参数')
  const parameters={...body};delete parameters.reason
  const metadata:FeedbackMetadata=saved??{path,key:crypto.randomUUID(),body_sha256:bodyHash,request_sha256:requestHash,actor:session.user.user_id,tenant_id:session.current_context!.tenant_id,kind,operation,project_id:project??String(body.project_id),feedback_id:id??null,parameters}
  sessionStorage.setItem(pendingKey(session),JSON.stringify(metadata))
  return {...metadata,body:JSON.parse(canonicalizeEx(body)),context:session.current_context!.context_revision}
}
export async function lookupFeedbackWrite(session:MedicalSession,pending:FeedbackMetadata,signal:AbortSignal):Promise<FeedbackReceipt|null>{
  if(session.current_context?.tenant_id!==pending.tenant_id||session.user.user_id!==pending.actor)fail('CONTEXT_CHANGED','原反馈操作不属于当前身份')
  const value=contextResult(session,await request.get(feedbackPath(pending.kind)+'/operations/'+pending.key,{medical:'envelope',signal,headers:headers(session)})) as {schema_version:string;lookup_status:string;receipt:unknown;request_sha256:string|null}
  if(value.schema_version!=='1.0.0'||!['FOUND','NOT_FOUND'].includes(value.lookup_status))fail('SCHEMA_INVALID','原反馈查询结果无效')
  if(value.lookup_status==='NOT_FOUND'){if(value.receipt!==null||value.request_sha256!==null)fail('CONTENT_MISMATCH','原反馈查询状态与回执不一致');return null}
  if(value.request_sha256!==pending.request_sha256)fail('CONTENT_MISMATCH','原反馈回执不匹配完整原请求')
  const result=receipt(pending.kind,value.receipt)
  if(result.project_id!==pending.project_id||pending.feedback_id!==null&&result.feedback_id!==pending.feedback_id)fail('CONTENT_MISMATCH','原反馈回执不匹配业务对象')
  return result
}
export async function writeFeedback(session: MedicalSession, pending: FeedbackWrite, signal: AbortSignal): Promise<FeedbackReceipt> {
  if (session.current_context?.context_revision !== pending.context || session.current_context?.tenant_id !== pending.tenant_id || session.user.user_id !== pending.actor) fail('CONTEXT_CHANGED', '反馈操作的身份已经变化')
  const result=receipt(pending.kind, contextResult(session, await request.post(pending.path, pending.body, { medical: 'envelope', signal,
    headers: { ...headers(session), 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })))
  if(result.actor_id!==pending.actor||result.project_id!==pending.project_id||result.reason!==pending.body.reason
    ||pending.feedback_id!==null&&result.feedback_id!==pending.feedback_id||pending.body.target_version!==undefined&&result.target_version!==pending.body.target_version
    ||result.revision!==(pending.body.expected_revision===undefined?'1':(BigInt(String(pending.body.expected_revision))+1n).toString()))fail('CONTENT_MISMATCH','反馈提交回执与原请求不一致')
  return result
}
export function percentage(ratio: Ratio): string {
  if (ratio.rate === null) return '暂无统计'
  // 百分比仅用于展示，费用和原始统计值保留服务器字符串。
  return new Intl.NumberFormat('zh-CN', { style: 'percent', maximumFractionDigits: 2 }).format(Number(ratio.rate))
}
