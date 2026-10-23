import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import request from '@/utils/request'
import schema from '../../../../business/medical-persistence/src/main/resources/enterprise-member-labels.schema.json'
import { envelope, fail, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { canonicalDocumentValue } from './document-body'

export interface MemberLabel {user_id:string;display_name:string|null;username:string|null;member_status:'ACTIVE'|'INVITED'|'DISABLED';valid_from:string|null;valid_to:string|null}
export interface MemberLabelDirectory {schema_version:'1.0.0';tenant_id:string;items:MemberLabel[];complete:boolean;next_after:string|null}
const ajv=new Ajv2020({strictSchema:true,strictTypes:false,allErrors:true});addFormats(ajv);const check=ajv.compile(schema)
export function memberLabelsAllowed(session:MedicalSession|null){return !!session?.current_context&&session.action_summary.some(item=>['member.read','member.manage'].includes(item.action)&&item.project_id===null&&item.resource_kind==='ENTERPRISE'&&item.resource_id===session.current_context!.tenant_id)}
export function memberLabelName(value:MemberLabel){return value.display_name??value.username??value.user_id}
export function activeMember(value:MemberLabel){return value.member_status==='ACTIVE'&&value.valid_from!==null&&Date.parse(value.valid_from)<=Date.now()&&(value.valid_to===null||Date.parse(value.valid_to)>Date.now())}
export async function readMemberLabels(session:MedicalSession,after:string|null,signal:AbortSignal):Promise<MemberLabelDirectory>{
 if(!memberLabelsAllowed(session)||!session.current_context)fail('ACCESS_DENIED','成员名称目录需要当前企业的成员读取许可');validContext(session.current_context)
 const captured=canonicalDocumentValue(session.current_context),actor=session.user.user_id,raw=await request.get('/api/enterprise-member-labels',{medical:'envelope',signal,timeout:120000,params:after?{after}:{},headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}})
 signal.throwIfAborted();const wrapped=envelope(raw);if(!check(wrapped.data))fail('SCHEMA_INVALID','成员名称目录不符合固定定义');const result=wrapped.data as unknown as MemberLabelDirectory
 if(raw.status!==200||captured!==canonicalDocumentValue(session.current_context)||actor!==session.user.user_id||wrapped.resource_revision!==session.current_context.context_revision||result.tenant_id!==session.current_context.tenant_id||result.complete!==(result.next_after===null))fail('CONTENT_MISMATCH','成员名称目录的企业或分页不一致')
 let previous=after;for(const item of result.items){if(previous!==null&&item.user_id<=previous)fail('CONTENT_MISMATCH','成员名称目录包含重复或不连续身份');previous=item.user_id}return result
}
