import { envelope, fail, validate, validContext } from './protocol'
import type { ApiError, Context, MedicalSession } from './protocol'
import { readBoundedJson } from './project-json'
import { canonicalDocumentValue as canonical } from './document-body'

export interface AuditAvailability { schema_version:'1.0.0'; current_context:Context; features:{business_audit:boolean}; coverage_scope:'DOCUMENT_SAVE_AUDIT'; supported_actions:['document.revision.saved']; scope_mode:'PROJECT_REQUIRED'; maximum_page_size:number|null; maximum_window_seconds:number|null; maximum_response_bytes:number|null }
export interface AuditQuery { project_id:string; action:'document.revision.saved'; from:string; to:string; actor_id:string|null; document_id:string|null; limit:number }
export interface AuditEntry { audit_id:string; action:'document.revision.saved'; occurred_at:string; actor_id:string; outcome:'SUCCESS'; target:{resource_kind:'DOCUMENT';project_id:string;document_id:string;document_revision:string;read_url:string} }
export interface AuditPage { schema_version:'1.0.0';current_context:Context;coverage_scope:'DOCUMENT_SAVE_AUDIT';pagination_mode:'LIVE_AUTHORIZED_SCAN';query:AuditQuery;items:AuditEntry[];complete:boolean;next_cursor:string|null }
export type AuditRead={kind:'PAGE';page:AuditPage}|{kind:'CURSOR_STALE';code:'CURSOR_EXPIRED'|'CURSOR_INVALID'}
export class AuditInputError extends Error {}
export class AuditCapacityError extends Error {}
export const AUDIT_CLIENT_MAX_BYTES=4*1024*1024
export const AUDIT_TIMEOUT_MS=60000
const ref='urn:medical:business-audit:1.0.0#/$defs/'
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
/** Same UTC microsecond domain as the server. Fractions use Java Instant's 0/3/6 digit grouping. */
export function auditTimestamp(value:string):string {
 const m=/^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/.exec(value)
 if(!m||!Number.isFinite(Date.parse(m[1]+'Z'))||new Date(m[1]+'Z').toISOString().slice(0,19)!==m[1])throw new AuditInputError('时间必须是有效 UTC 时间（Z 结尾，最多六位小数）。')
 const fraction=(m[2]??'').replace(/0+$/,'');return m[1]+(fraction?'.'+fraction.padEnd(fraction.length<=3?3:6,'0'):'')+'Z'
}
function instant(value:string):bigint {const s=auditTimestamp(value),m=/^(.*?)(?:\.(\d+))?Z$/.exec(s)!;return BigInt(Date.parse(m[1]+'Z'))*1000n+BigInt((m[2]??'').padEnd(6,'0'))}
export function prepareAuditQuery(value:AuditQuery,availability:AuditAvailability):AuditQuery {
 if(!availability.features.business_audit||availability.maximum_page_size===null||availability.maximum_window_seconds===null)throw new AuditInputError('当前服务未启用，请先核查服务状态。')
 if(!uuid.test(value.project_id)||[value.actor_id,value.document_id].some(id=>id!==null&&!uuid.test(id)))throw new AuditInputError('请选择真实项目；可选人员和文稿标识须为已知的完整 UUID。')
 if(value.action!=='document.revision.saved'||!Number.isSafeInteger(value.limit)||value.limit<1||value.limit>availability.maximum_page_size)throw new AuditInputError('分页数量超出当前服务容量。')
 const from=auditTimestamp(value.from),to=auditTimestamp(value.to),window=instant(to)-instant(from)
 if(window<=0n||window>BigInt(availability.maximum_window_seconds)*1000000n)throw new AuditInputError('结束时间须晚于开始时间，且时间范围不能超过服务上限。')
 return {...value,from,to}
}
function capture(session:MedicalSession){if(!session.current_context)fail('ACCESS_DENIED','审计读取需要当前企业身份');validContext(session.current_context);return {actor:session.user.user_id,context:{...session.current_context}}}
function current(session:MedicalSession,expected:ReturnType<typeof capture>,signal:AbortSignal){signal.throwIfAborted();if(session.user.user_id!==expected.actor||canonical(session.current_context)!==canonical(expected.context))fail('CONTEXT_CHANGED','审计读取期间身份已变化')}
/** Narrow same-origin GET transport. Limits decoded UTF-8 bytes before strict JSON parsing. */
async function get(path:string,context:Context,limit:number,signal:AbortSignal){
 if(!Number.isSafeInteger(limit)||limit<1)fail('CONTENT_MISMATCH','审计响应容量无效')
 if(!['/api/audit-events','/api/business-audit-feature-availability'].includes(path.split('?')[0]!))fail('ACCESS_DENIED','审计读取地址未经允许')
 limit=Math.min(limit,AUDIT_CLIENT_MAX_BYTES)
 const requestController=new AbortController();const abort=()=>requestController.abort(signal.reason);signal.addEventListener('abort',abort,{once:true});if(signal.aborted)abort();const timeout=setTimeout(()=>requestController.abort(new DOMException('审计读取超时','TimeoutError')),AUDIT_TIMEOUT_MS)
 try{
 const response=await fetch(path,{method:'GET',credentials:'same-origin',cache:'no-store',redirect:'error',signal:requestController.signal,headers:{Accept:'application/json','X-Schema-Version':'1.0.0','X-Context-Revision':context.context_revision}})
 signal.throwIfAborted()
 if(!response.headers.get('content-type')?.match(/^application\/json(?:\s*;|$)/i)||!response.body){await response.body?.cancel().catch(()=>{});fail('SCHEMA_INVALID','审计响应类型无效')}
 const length=response.headers.get('content-length');if(length!==null&&(!/^\d+$/.test(length)||BigInt(length)>BigInt(limit))){await response.body.cancel();throw new AuditCapacityError('本页响应超过浏览器或服务器容量，请缩小时间范围或分页数量。')}
 const reader=response.body.getReader(),decoder=new TextDecoder('utf-8',{fatal:true});let total=0,text=''
 const decode=(bytes?:Uint8Array,stream=false)=>{try{return decoder.decode(bytes,{stream})}catch{fail('SCHEMA_INVALID','审计响应不是有效 UTF-8')}}
 try{while(true){signal.throwIfAborted();requestController.signal.throwIfAborted();const chunk=await reader.read();if(chunk.done)break;total+=chunk.value.byteLength;if(total>limit)throw new AuditCapacityError('本页响应超过浏览器或服务器容量，请缩小时间范围或分页数量。');text+=decode(chunk.value,true)}text+=decode()}

 finally{await reader.cancel().catch(()=>{});reader.releaseLock()}
 requestController.signal.throwIfAborted();signal.throwIfAborted()
 const headers:Record<string,string>={};response.headers.forEach((v,k)=>{headers[k]=v})
 return {status:response.status,statusText:response.statusText,headers,data:readBoundedJson(text),config:{headers:{}}} as Parameters<typeof envelope>[0]
 }finally{clearTimeout(timeout);signal.removeEventListener('abort',abort)}
}
export async function readAuditAvailability(session:MedicalSession,signal:AbortSignal):Promise<AuditAvailability>{
 const expected=capture(session),raw=await get('/api/business-audit-feature-availability',expected.context,65536,signal);current(session,expected,signal)
 const e=envelope(raw),a=validate<AuditAvailability>(ref+'Availability',e.data)
 const capacities=[a.maximum_page_size,a.maximum_window_seconds,a.maximum_response_bytes]
 if(raw.status!==200||e.resource_revision!==expected.context.context_revision||raw.headers['x-resource-revision']!==expected.context.context_revision||canonical(a.current_context)!==canonical(expected.context)||
   (a.features.business_audit?!capacities.every(v=>v!==null&&Number.isSafeInteger(v)&&v>0):!capacities.every(v=>v===null)))fail('CONTENT_MISMATCH','审计服务状态与当前身份或容量不一致')
 return a
}
export async function readAuditPage(session:MedicalSession,query:AuditQuery,availability:AuditAvailability,signal:AbortSignal,cursor?:string,last?:AuditEntry):Promise<AuditRead>{
 const q=prepareAuditQuery(query,availability);if(cursor!==undefined&&!uuid.test(cursor))throw new AuditInputError('分页标识无效，请从第一页重新读取。')
 const expected=capture(session);if(canonical(availability.current_context)!==canonical(expected.context))fail('CONTEXT_CHANGED','审计服务状态需要重新读取')
 validate(ref+'Query',q)
 const params=new URLSearchParams({project_id:q.project_id,action:q.action,from:q.from,to:q.to,limit:String(q.limit)})
 if(q.actor_id!==null)params.set('actor_id',q.actor_id);if(q.document_id!==null)params.set('document_id',q.document_id);if(cursor!==undefined)params.set('cursor',cursor)
 const raw=await get('/api/audit-events?'+params.toString(),expected.context,availability.maximum_response_bytes!,signal);current(session,expected,signal)
 const code=(raw.data as {code?:unknown}|null)?.code
 if(raw.status===409&&(code==='CURSOR_EXPIRED'||code==='CURSOR_INVALID')){
  if(cursor===undefined||raw.headers['cache-control']!=='no-store'||raw.headers['x-schema-version']!=='1.0.0')fail('SCHEMA_INVALID','审计分页失效响应不完整')
  const error=validate<ApiError>('urn:medical:shared#/$defs/Error',raw.data)
  if(raw.headers['x-request-id']!==error.request_id||error.operation_id!==null||error.resource_revision!==null||error.details.length||error.allowed_actions.length||error.retryable!==false)fail('CONTENT_MISMATCH','审计分页失效响应不一致')
  return {kind:'CURSOR_STALE',code}
 }
 const e=envelope(raw),page=validate<AuditPage>(ref+'Page',e.data)
 let returned:AuditQuery;try{returned={...page.query,from:auditTimestamp(page.query.from),to:auditTimestamp(page.query.to)}}catch{fail('CONTENT_MISMATCH','审计查询回显时间无效')}
 if(raw.status!==200||e.resource_revision!==expected.context.context_revision||raw.headers['x-resource-revision']!==expected.context.context_revision||canonical(page.current_context)!==canonical(expected.context)||canonical(returned)!==canonical(q)||page.items.length>q.limit||page.complete!==(page.next_cursor===null)||cursor!==undefined&&page.next_cursor===cursor)fail('CONTENT_MISMATCH','审计响应与当前身份、查询或分页范围不一致')
 let previous=last;const ids=new Set<string>()
 for(const item of page.items){let time:bigint;try{time=instant(item.occurred_at)}catch{fail('CONTENT_MISMATCH','审计条目时间无效')}
  if(ids.has(item.audit_id)||time<instant(q.from)||time>=instant(q.to)||item.target.project_id!==q.project_id||q.actor_id!==null&&item.actor_id!==q.actor_id||q.document_id!==null&&item.target.document_id!==q.document_id||item.target.read_url!=='/api/documents/'+item.target.document_id+'/revisions/'+item.target.document_revision||previous!==undefined&&(time>instant(previous.occurred_at)||time===instant(previous.occurred_at)&&item.audit_id>=previous.audit_id))fail('CONTENT_MISMATCH','审计条目的范围、顺序或固定文稿地址不一致')
  ids.add(item.audit_id);previous=item
 }
 return {kind:'PAGE',page}
}
