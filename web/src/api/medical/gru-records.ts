import request from '@/utils/request'
import { envelope, fail, schemaIssues, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import type { SourceScope, Selection } from '../../../../contracts/typescript/src/interfaces'
import type { Message } from '../../../../contracts/typescript/src/resource-details'
import { canonicalDocumentValue } from './document-body'
import { projectTextIssue } from './project-text'
import { sourceDigest } from './content-digest'
import { lookupOperation, readOperation } from './operations'

export const gruRef = 'urn:medical:gru-records:1.0.0#/$defs/'
const sharedRef='https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/'
const messageRef='https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml#/$defs/Message'
const idRef = 'urn:medical:templates:1.0.0#/$defs/Id'
export interface GruContext { schema_version:'1.0.0'; project_id:string; document_id:string; document_revision:string; scope_snapshot_id:string; scope:SourceScope }
export interface GruCreate extends GruContext { reason:string }
export interface GruAppend { schema_version:'1.0.0'; expected_revision:string; document_revision:string; scope_snapshot_id:string; scope:SourceScope; selection:Selection|null; intent:'QUERY'|'RETRIEVE'|'CHECK'|'PROPOSE_EDIT'; execution_mode:'RECORD_ONLY'; text:string; reason:string }
export interface GruReceipt { schema_version:'1.0.0'; conversation_id:string; message_id:string|null; operation_id:string; request_body_sha256:string; revision:string; execution_state:'NOT_EXECUTED' }
export interface GruView extends GruContext { conversation_id:string; revision:string; status:'ACTIVE'; owner_id:string; mode:'RECORD_ONLY'; execution_state:'NOT_EXECUTED'; created_at:string }
export interface GruRead extends GruView { messages:Message[]; message_records:{message_id:string;intent:GruAppend['intent'];execution_state:'NOT_EXECUTED';revision:string}[]; page_info:{page:number;page_size:number;total:string;dataset_watermark:string;next_page:number|null} }
export interface GruList {schema_version:'1.0.0';project_id:string;mode:'RECORD_ONLY';items:GruView[]}
export type GruPending = {actor:string; context:Context; key:string; digest:string} & ({kind:'CREATE';conversation_id:null;body:GruCreate}|{kind:'APPEND';conversation_id:string;body:GruAppend})
const canonical=canonicalDocumentValue
function current(s:MedicalSession):Context {if(!s.current_context)fail('ACCESS_DENIED','会话记录需要当前企业身份');validContext(s.current_context);return s.current_context}
function headers(s:MedicalSession,key?:string){return {'X-Schema-Version':'1.0.0','X-Context-Revision':current(s).context_revision,...(key?{'Idempotency-Key':key,[s.csrf.header_name]:s.csrf.token}:{})}}
export function gruAllowed(s:MedicalSession|null,project:string,document:string,action:'conversation.read'|'conversation.use'):boolean {
 return !!s?.current_context&&s.action_summary.some(item=>item.action===action&&(item.project_id===null||item.project_id===project)&&(item.resource_kind==='ENTERPRISE'&&item.resource_id===s.current_context?.tenant_id||item.resource_kind==='PROJECT'&&item.resource_id===project||item.resource_kind==='DOCUMENT'&&(item.resource_id===null||item.resource_id===document)))
}
function matchContext(v:GruContext,b:GruContext){if(v.project_id!==b.project_id||v.document_id!==b.document_id||v.document_revision!==b.document_revision||v.scope_snapshot_id!==b.scope_snapshot_id||canonical(v.scope)!==canonical(b.scope))fail('CONTENT_MISMATCH','会话固定文稿或来源范围不一致')}
function owner(s:MedicalSession,v:GruView){validate(sharedRef+'SourceScope',v.scope);if(v.scope.network_enabled||v.owner_id!==s.user.user_id||v.mode!=='RECORD_ONLY'||v.execution_state!=='NOT_EXECUTED')fail('CONTENT_MISMATCH','记录会话所有者或执行状态不一致')}
export function gruInputIssues(kind:'CREATE'|'APPEND',body:unknown):string[]{
 const issues=schemaIssues(gruRef+(kind==='CREATE'?'Create':'Append'),body).map(i=>i.path+': '+i.code)
 if(issues.length)return issues
 const b=body as GruCreate|GruAppend
 issues.push(...schemaIssues(sharedRef+'SourceScope',b.scope).map(i=>i.path+': '+i.code))
 if('selection'in b&&b.selection!==null)issues.push(...schemaIssues(sharedRef+'Selection',b.selection).map(i=>i.path+': '+i.code))
 if(projectTextIssue(b.reason,2000))issues.push('保存理由须为规范文本，请保留原值并检查空白、控制字符或长度')
 if('expected_revision'in b&&BigInt(b.expected_revision)>18446744073709551615n)issues.push('修订超过允许范围')
 if(b.scope.network_enabled)issues.push('此记录入口不支持联网范围')
 for(const text of [b.reason,...('text'in b?[b.text]:[])])if(!text.trim()||/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/u.test(text)||Array.from(text).some(c=>c.length===1&&c.charCodeAt(0)>=0xd800&&c.charCodeAt(0)<=0xdfff))issues.push('文字不能为空或包含无效控制字符')
 if(new TextEncoder().encode(JSON.stringify(body)).length>65536)issues.push('请求超过64KiB容量')
 return issues
}
export async function readGruContext(s:MedicalSession,project:string,document:string,revision:string,signal:AbortSignal):Promise<GruContext>{
 if(!gruAllowed(s,project,document,'conversation.read'))fail('ACCESS_DENIED','读取会话依据需要独立读取许可')
 validate(idRef,document);validate(idRef,revision)
 const raw=await request.get('/api/gru-conversations/context',{medical:'envelope',signal,headers:headers(s),params:{document_id:document,document_revision:revision}}),e=envelope(raw),v=validate<GruContext>(gruRef+'Context',e.data)
 validate(sharedRef+'SourceScope',v.scope)
 if(raw.status!==200||e.resource_revision!=='1'||v.project_id!==project||v.document_id!==document||v.document_revision!==revision||v.scope.network_enabled)fail('CONTENT_MISMATCH','会话依据不是当前固定文稿的真实范围')
 return v
}
export async function listGru(s:MedicalSession,project:string,signal:AbortSignal):Promise<GruList>{
 validate(idRef,project);const raw=await request.get('/api/gru-conversations',{medical:'envelope',signal,headers:headers(s),params:{project_id:project}}),e=envelope(raw),v=validate<GruList>(gruRef+'List',e.data)
 if(raw.status!==200||e.resource_revision!=='1'||v.project_id!==project||new Set(v.items.map(i=>i.conversation_id)).size!==v.items.length)fail('CONTENT_MISMATCH','会话目录归属或身份不一致')
 for(const i of v.items){owner(s,i);if(i.project_id!==project)fail('CONTENT_MISMATCH','会话目录跨项目')}return v
}
export async function readGru(s:MedicalSession,id:string,signal:AbortSignal,page=1,watermark?:string):Promise<GruRead>{
 validate(idRef,id);const raw=await request.get('/api/gru-conversations/'+id,{medical:'envelope',signal,headers:headers(s),params:{page,page_size:25,...(watermark?{dataset_watermark:watermark}:{})}}),e=envelope(raw),v=validate<GruRead>(gruRef+'Read',e.data);owner(s,v)
 if(raw.status!==200||e.resource_revision!==v.revision||v.conversation_id!==id||v.page_info.page!==page||v.page_info.page_size!==25||watermark!==undefined&&v.page_info.dataset_watermark!==watermark||v.messages.length!==v.message_records.length||new Set(v.messages.map(m=>m.message_id)).size!==v.messages.length)fail('CONTENT_MISMATCH','会话分页、修订或消息身份不一致')
 for(let i=0;i<v.messages.length;i++){const m=validate<Message>(messageRef,v.messages[i]),r=v.message_records[i]!;if(m.message_id!==r.message_id||m.conversation_id!==id||m.project_id!==v.project_id||m.document_revision!==v.document_revision||canonical(m.scope)!==canonical(v.scope)||m.sender_kind!=='USER'||m.run_id!==null||m.candidate_ids.length!==0||r.execution_state!=='NOT_EXECUTED')fail('CONTENT_MISMATCH','消息记录的固定范围或未执行边界不一致')}
 return v
}
export async function verifyGruPending(s:MedicalSession,p:GruPending,replay:boolean){
 validate(idRef,p.actor);validate(idRef,p.key);validContext(p.context);validate(gruRef+(p.kind==='CREATE'?'Create':'Append'),p.body)
 if(!['CREATE','APPEND'].includes(p.kind)||p.kind==='CREATE'&&p.conversation_id!==null||p.kind==='APPEND'&&schemaIssues(idRef,p.conversation_id).length)fail('CONTENT_MISMATCH','原记录请求类型无效')
 const {digest,...original}=p
 if(p.actor!==s.user.user_id||p.context.tenant_id!==current(s).tenant_id||await sourceDigest(original)!==digest)fail('CONTENT_MISMATCH','原记录请求或身份已改变')
 if(replay&&canonical(p.context)!==canonical(current(s)))fail('CONTEXT_CHANGED','上下文改变，只能查询原记录操作')
}
async function receiptMatch(p:GruPending,r:GruReceipt){if(await sourceDigest(p.body)!==r.request_body_sha256)fail('CONTENT_MISMATCH','记录回执与完整原请求摘要不一致');if(p.kind==='CREATE'&&(r.message_id!==null||r.revision!=='1')||p.kind==='APPEND'&&(r.message_id===null||r.conversation_id!==p.conversation_id||BigInt(r.revision)!==BigInt(p.body.expected_revision)+1n))fail('CONTENT_MISMATCH','记录回执不属于原请求')}
export async function writeGru(s:MedicalSession,p:GruPending,signal:AbortSignal):Promise<GruReceipt>{
 await verifyGruPending(s,p,true);if(p.kind==='CREATE'&&(!gruAllowed(s,p.body.project_id,p.body.document_id,'conversation.read')||!gruAllowed(s,p.body.project_id,p.body.document_id,'conversation.use')))fail('ACCESS_DENIED','创建会话需要读取与使用许可');signal.throwIfAborted()
 const raw=await request.post('/api/gru-conversations'+(p.kind==='APPEND'?'/'+p.conversation_id+'/messages':''),p.body,{medical:'envelope',headers:headers(s,p.key)}),e=envelope(raw),v=validate<GruReceipt>(gruRef+'Receipt',e.data)
 if(raw.status!==201||e.resource_revision!==v.revision)fail('CONTENT_MISMATCH','消息保存回执状态无效');await receiptMatch(p,v);return v
}
export async function recoverGru(s:MedicalSession,p:GruPending,signal:AbortSignal):Promise<{status:string;receipt:GruReceipt|null}>{
 await verifyGruPending(s,p,false);const found=await lookupOperation(s,p.kind==='CREATE'?'GRU-RECORD-CREATE':'GRU-RECORD-APPEND',p.key,signal)
 if(found.lookup_status!=='FOUND')return {status:found.lookup_status,receipt:null}
 const op=await readOperation(s,found.operation!.operation_id,signal)
 if(op.resource_id!==found.operation!.resource_id||op.operation_id!==found.operation!.operation_id)fail('CONTENT_MISMATCH','原记录操作身份不一致')
 if(op.status!=='COMPLETED')return {status:op.status,receipt:null}
 if(!op.resource_id||p.kind==='APPEND'&&op.resource_id!==p.conversation_id)fail('CONTENT_MISMATCH','原记录操作目标不一致')
 const raw=await request.get('/api/gru-conversations/'+op.resource_id+'/receipts/'+op.operation_id,{medical:'envelope',signal,headers:headers(s)}),e=envelope(raw),r=validate<GruReceipt>(gruRef+'Receipt',e.data)
 if(raw.status!==200||e.resource_revision!==r.revision||r.operation_id!==op.operation_id||r.conversation_id!==op.resource_id)fail('CONTENT_MISMATCH','固定原操作回执不一致');await receiptMatch(p,r)
 const view=await readGru(s,r.conversation_id,signal)
 if(BigInt(view.revision)<BigInt(r.revision))fail('CONTENT_MISMATCH','恢复后的会话修订早于原回执')
 if(p.kind==='CREATE')matchContext(view,p.body)
 else if(view.document_revision!==p.body.document_revision||view.scope_snapshot_id!==p.body.scope_snapshot_id||canonical(view.scope)!==canonical(p.body.scope))fail('CONTENT_MISMATCH','恢复消息固定范围不一致')
 return {status:'COMPLETED',receipt:r}
}
const storageKey=(s:MedicalSession,document:string)=>'medical.gru.records:'+s.user.user_id+':'+current(s).tenant_id+':'+document
export function saveGruPending(s:MedicalSession,document:string,p:GruPending){sessionStorage.setItem(storageKey(s,document),JSON.stringify(p))}
export function restoreGruPending(s:MedicalSession,document:string):GruPending|null{const raw=sessionStorage.getItem(storageKey(s,document));if(!raw)return null;try{return JSON.parse(raw)}catch{return fail('CONTENT_MISMATCH','原消息恢复记录已损坏')}}
export function clearGruPending(s:MedicalSession,document:string){sessionStorage.removeItem(storageKey(s,document))}

export async function gruAvailability(s:MedicalSession,signal:AbortSignal):Promise<boolean>{
 const c={...current(s)},raw=await request.get('/api/gru-feature-availability',{medical:'envelope',signal,headers:headers(s)}),e=envelope(raw)
 const v=validate<{schema_version:'1.0.0';current_context:Context;features:{gru_records:boolean}}>('urn:medical:gru-feature-availability:1.0.0',e.data)
 if(raw.status!==200||e.resource_revision!==c.context_revision||canonical(v.current_context)!==canonical(c))fail('CONTENT_MISMATCH','会话服务状态与当前身份不一致')
 return v.features.gru_records
}

export interface GruAppendRecovery {
 schema_version:'1.0.0';status:'FOUND'|'NOT_APPLIED_STALE'|'UNKNOWN';current_context:Context;actor_id:string;conversation_id:string;idempotency_key:string;request_body_sha256:string;expected_revision:string;current_revision:string;checked_at:string;receipt:GruReceipt|null
}
export interface GruStaleArchive {pending:GruPending;proof:GruAppendRecovery}
export async function verifyGruAppendRecovery(s:MedicalSession,p:GruPending,v:GruAppendRecovery,requireCurrent=true){
 await verifyGruPending(s,p,false);validate(gruRef+'Recovery',v)
 if(p.kind!=='APPEND'||v.actor_id!==s.user.user_id||v.conversation_id!==p.conversation_id||v.idempotency_key!==p.key||v.expected_revision!==p.body.expected_revision||v.request_body_sha256!==await sourceDigest(p.body)||v.current_context.tenant_id!==current(s).tenant_id||requireCurrent&&canonical(v.current_context)!==canonical(current(s)))fail('CONTENT_MISMATCH','原消息恢复证明与完整请求或当前身份不一致')
 if(v.status==='FOUND'){if(!v.receipt)fail('CONTENT_MISMATCH','已找到记录但缺少原回执');await receiptMatch(p,v.receipt);if(BigInt(v.current_revision)<BigInt(v.receipt.revision))fail('CONTENT_MISMATCH','原消息恢复修订无效')}
 else if(v.receipt!==null||v.status==='NOT_APPLIED_STALE'&&BigInt(v.current_revision)<=BigInt(v.expected_revision))fail('CONTENT_MISMATCH','原消息未应用证明缺少有效的过期修订')
}
export async function recoverGruAppend(s:MedicalSession,p:GruPending,signal:AbortSignal):Promise<GruAppendRecovery>{
 await verifyGruPending(s,p,false);if(p.kind!=='APPEND')fail('CONTENT_MISMATCH','此恢复只适用于原追加消息');signal.throwIfAborted()
 const raw=await request.post('/api/gru-conversations/'+p.conversation_id+'/append-recovery',{schema_version:'1.0.0',idempotency_key:p.key,request:p.body},{medical:'envelope',signal,headers:{...headers(s),[s.csrf.header_name]:s.csrf.token}}),e=envelope(raw),v=validate<GruAppendRecovery>(gruRef+'Recovery',e.data)
 if(raw.status!==200||e.resource_revision!==v.current_revision)fail('CONTENT_MISMATCH','原消息恢复响应修订不一致');await verifyGruAppendRecovery(s,p,v);return v
}
export async function archiveGruStale(s:MedicalSession,document:string,p:GruPending,proof:GruAppendRecovery):Promise<GruStaleArchive>{
 await verifyGruAppendRecovery(s,p,proof);if(proof.status!=='NOT_APPLIED_STALE')fail('CONTENT_MISMATCH','只有明确未应用且已过期的原请求可结束')
 const archive=JSON.parse(JSON.stringify({pending:p,proof})) as GruStaleArchive
 sessionStorage.setItem(storageKey(s,document)+':stale-proof',JSON.stringify(archive))
 return archive
}

export async function restoreGruStale(s:MedicalSession,document:string):Promise<GruStaleArchive|null>{
 const raw=sessionStorage.getItem(storageKey(s,document)+':stale-proof');if(!raw)return null
 let value:GruStaleArchive;try{value=JSON.parse(raw)}catch{return fail('CONTENT_MISMATCH','旧消息证明记录已损坏')}
 await verifyGruAppendRecovery(s,value.pending,value.proof,false)
 if(value.proof.status!=='NOT_APPLIED_STALE')fail('CONTENT_MISMATCH','旧消息证明状态无效')
 return value
}
