import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Attribute, Context, MedicalSession, Project } from './protocol'
import { checkProject } from './projects'
import { projectActionAllowed, projectTextIssue } from './project-write'
import { canonicalTemplateValue } from './template-approval'
import { formatProjectJson } from './project-json'

export const projectStageLabels={PENDING:'待配置',ACTIVE:'使用中',PAUSED:'暂停',ARCHIVED:'归档'} as const
export const projectPathLabels={FIRST_REGISTRATION:'首次注册',CHANGE_REGISTRATION:'变更注册',RENEWAL_REGISTRATION:'延续注册'} as const
export const projectDeviceClassLabels={II:'第二类',III:'第三类'} as const
export const projectDirectorySchema='urn:medical:project-workspace-directory:1.0.0#/$defs/'
export interface ProjectDirectoryQuery {limit?:number;after?:string;q?:string;status?:keyof typeof projectStageLabels;responsible_user_id?:string}
export interface ProjectDirectoryFilters {limit:number;q:string|null;status:keyof typeof projectStageLabels|null;responsible_user_id:string|null}
export interface ProjectResponsible {user_id:string;identity_available:boolean;display_name:string|null;username:string|null}
export interface ProjectMaterials {scope:'AUTHORIZED_ENTERPRISE_AND_PROJECT';registered:string;approved:string;not_approved:string;checked_at:string}
export interface ProjectDirectoryEntry {project:Project;responsible:ProjectResponsible;materials:ProjectMaterials}
export interface ProjectWorkspaceDirectory {schema_version:'1.0.0';tenant_id:string;current_context:Context;query:ProjectDirectoryFilters;total:string;items:ProjectDirectoryEntry[];next_after:string|null}

export function projectAttribute(project:Project,key:string):Attribute|undefined{return project.profile.attributes.find(value=>value.key===key)}
export function projectAttributeText(project:Project,key:string):string{
 const attribute=projectAttribute(project,key)
 if(!attribute||attribute.status==='UNKNOWN')return '尚未确认'
 const value=attribute.value
 if(typeof value==='string'){
  if(key==='application_path'&&value in projectPathLabels)return projectPathLabels[value as keyof typeof projectPathLabels]
  if(key==='device_class'&&value in projectDeviceClassLabels)return projectDeviceClassLabels[value as keyof typeof projectDeviceClassLabels]
  return value
 }
 if(typeof value==='boolean')return key==='ivd'?(value?'体外诊断试剂':'医疗器械（非体外诊断试剂）'):formatProjectJson(value)
 return formatProjectJson(value)
}
export function projectResponsibleName(owner:ProjectResponsible):string{
 if(!owner.identity_available)return '无成员目录读取授权'
 if(owner.display_name)return owner.display_name
 if(owner.username)return owner.username
 return '尚未登记成员姓名'
}
export async function readProjectWorkspaceDirectory(session:MedicalSession,query:ProjectDirectoryQuery,signal:AbortSignal):Promise<ProjectWorkspaceDirectory>{
 const context=session.current_context
 if(!context||!projectActionAllowed(session,'project.read'))fail('ACCESS_DENIED','项目目录需要当前组织的项目读取权限')
 validContext(context);validate(projectDirectorySchema+'Query',query)
 if(query.q&&projectTextIssue(query.q,256))fail('SCHEMA_INVALID','项目查询文字需要有效内容')
 const captured=canonicalTemplateValue(context),actor=session.user.user_id
 const raw=await request.get('/api/project-workspace-directory',{medical:'envelope',signal,params:query,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':context.context_revision}})
 signal.throwIfAborted();const wrapped=envelope(raw),value=validate<ProjectWorkspaceDirectory>(projectDirectorySchema+'Directory',wrapped.data);validContext(value.current_context);revision(value.total)
 const expected:ProjectDirectoryFilters={limit:query.limit??10,q:query.q??null,status:query.status??null,responsible_user_id:query.responsible_user_id??null}
 if(raw.status!==200||wrapped.resource_revision!==context.context_revision||value.tenant_id!==context.tenant_id
    ||captured!==canonicalTemplateValue(value.current_context)||captured!==canonicalTemplateValue(session.current_context)||session.user.user_id!==actor
    ||canonicalTemplateValue(expected)!==canonicalTemplateValue(value.query)||value.items.length>expected.limit||BigInt(value.total)<BigInt(value.items.length))fail('CONTENT_MISMATCH','项目目录与当前账户、组织或完整筛选范围不一致')
 let previous=''
 for(const item of value.items){
  checkProject(item.project,context);const project=item.project,owner=item.responsible,materials=item.materials
  if(previous&&project.project_id<=previous||!projectActionAllowed(session,'project.read',project.project_id)||owner.user_id!==project.responsible_user_id
     ||!owner.identity_available&&(owner.display_name!==null||owner.username!==null)||query.status&&query.status!==project.status
     ||query.responsible_user_id&&query.responsible_user_id!==project.responsible_user_id)fail('CONTENT_MISMATCH','项目记录、负责人或分页顺序不一致')
  for(const amount of [materials.registered,materials.approved,materials.not_approved])revision(amount)
  if(BigInt(materials.registered)!==BigInt(materials.approved)+BigInt(materials.not_approved))fail('CONTENT_MISMATCH','可见资料数量与实际核准数量不一致')
  previous=project.project_id
 }
 if(value.next_after!==null&&(value.items.length!==expected.limit||value.next_after===query.after))fail('CONTENT_MISMATCH','项目下一页与当前完整结果不一致')
 return value
}
