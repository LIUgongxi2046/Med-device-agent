import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { ApiError, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import { canonicalDocumentValue as canonical } from './document-body'
import type { ReviewDecision, ReviewAssignmentView } from './document-review-return'
export interface RevisionQueueItem {project_id:string;decision:ReviewDecision;view:ReviewAssignmentView}
export interface RevisionQueuePage {schema_version:'1.0.0';tenant_id:string;actor_id:string;context_revision:string;auth_epoch:string;governance_epoch:string;revision:string;limit:number;items:RevisionQueueItem[];next_cursor:string|null;complete:boolean;listing_sha256:string}
export type RevisionQueueRead={kind:'PAGE';page:RevisionQueuePage}|{kind:'EXPIRED'}
const ref='urn:medical:document-revision-assignment-queue:1.0.0#/$defs/'
async function checkedDigest(value:object,key:string){const copy={...value} as Record<string,unknown>,hash=copy[key];delete copy[key];if(await sourceDigest(copy)!==hash)fail('CONTENT_MISMATCH','返修队列固定内容摘要不一致')}
export async function readRevisionQueue(session:MedicalSession,signal:AbortSignal,cursor?:string):Promise<RevisionQueueRead>{
 const context=session.current_context;if(!context)fail('ACCESS_DENIED','返修任务需要当前企业身份');validContext(context);const expected={...context},actor=session.user.user_id
 if(cursor!==undefined)validate('urn:medical:templates:1.0.0#/$defs/Id',cursor)
 const raw=await request.get('/api/my/document-revision-assignments',{medical:'envelope',signal,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':expected.context_revision},params:{limit:10,...(cursor?{cursor}:{})}})
 signal.throwIfAborted()
 if(session.user.user_id!==actor||canonical(session.current_context)!==canonical(expected))fail('CONTEXT_CHANGED','返修读取期间身份已改变')
 // A verified, expected cursor expiry is a local pagination outcome. Other errors retain strict fail-closed handling.
 if(raw.status===409&&(raw.data as {code?:unknown}|null)?.code==='CURSOR_EXPIRED'){
  if(cursor===undefined||raw.headers['cache-control']!=='no-store'||raw.headers['x-schema-version']!=='1.0.0')fail('SCHEMA_INVALID','返修分页过期响应不完整')
  const error=validate<ApiError>('urn:medical:shared#/$defs/Error',raw.data)
  if(raw.headers['x-request-id']!==error.request_id||error.operation_id!==null||error.resource_revision!==null||error.allowed_actions.length!==0||error.details.length!==0||error.retryable!==false)fail('CONTENT_MISMATCH','返修分页过期响应身份无效')
  if(error.resource_revision!==null)revision(error.resource_revision)
  return {kind:'EXPIRED'}
 }
 const e=envelope(raw),v=validate<RevisionQueuePage>(ref+'QueuePage',e.data);await checkedDigest(v,'listing_sha256')
 if(raw.status!==200||e.resource_revision!==expected.context_revision||v.revision!==expected.context_revision||v.tenant_id!==expected.tenant_id||v.actor_id!==actor||v.context_revision!==expected.context_revision||v.auth_epoch!==expected.auth_epoch||v.governance_epoch!==expected.governance_epoch||v.limit!==10||v.items.length>v.limit||v.complete!==(v.next_cursor===null)||cursor!==undefined&&v.next_cursor===cursor)fail('CONTENT_MISMATCH','返修队列与当前本人、企业或分页范围不一致')
 let last=''
 for(const item of v.items){const d=item.decision,b=d.baseline_manifest,a=d.assignment,w=item.view;await checkedDigest(b,'basis_sha256')
  if(a.assignment_id<=last||a.assignee_id!==actor||d.assignee_id!==actor||item.project_id!==b.project_id||b.tenant_id!==expected.tenant_id||d.document_id!==b.document_id||d.document_revision!==b.document_revision||a.document_id!==d.document_id||a.document_revision!==d.document_revision||a.task_id!==b.task_id||a.task_revision!==b.task_revision||a.return_id!==d.return_id||a.reason!==d.reason||canonical(a)!==canonical(w.assignment)||w.effective_state!=='INACTIVE'&&(w.current_task_revision!==a.task_revision||w.task_status!=='RETURNED')||w.effective_state==='OPEN'&&w.current_document_revision!==a.document_revision||w.effective_state==='REVISION_AVAILABLE'&&w.current_document_revision===a.document_revision)fail('CONTENT_MISMATCH','返修队列记录归属、原意见或派生状态不一致')
  last=a.assignment_id
 }
 return {kind:'PAGE',page:v}
}
export function revisionWorkspacePath(item:RevisionQueueItem):string{return '/projects/'+item.project_id+'/tasks/'+item.decision.assignment.task_id+'/documents/'+item.decision.document_id+'/write'}
