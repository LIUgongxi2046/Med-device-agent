import request from '@/utils/request'
import { envelope, fail, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { checkStored, ruleAllowed } from './rule-versions'
import type { RuleRead, RuleStatus } from './rule-versions'
import { validateRuleBusiness } from './rule-business'
import type { RuleBusinessDefinition, StoredRuleBusiness } from './rule-business'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'
import { projectTextIssue } from './project-write'

export const ruleDirectorySchema='urn:medical:rule-business-directory:1.0.0#/$defs/'
export interface RuleDirectoryQuery {limit?:number;after?:string;q?:string;dimension?:RuleBusinessDefinition['dimension'];handling?:RuleBusinessDefinition['handling'];status?:RuleStatus}
export interface RuleDirectoryFilters {limit:number;q:string|null;dimension:RuleBusinessDefinition['dimension']|null;handling:RuleBusinessDefinition['handling']|null;status:RuleStatus|null}
export interface RuleDirectoryEntry {resource:RuleRead;business:StoredRuleBusiness|null}
export interface RuleBusinessDirectory {schema_version:'1.0.0';tenant_id:string;current_context:Context;query:RuleDirectoryFilters;items:RuleDirectoryEntry[];next_after:string|null}

export async function readRuleBusinessDirectory(session:MedicalSession,query:RuleDirectoryQuery,signal:AbortSignal):Promise<RuleBusinessDirectory>{
 const context=session.current_context
 if(!context||!ruleAllowed(session,'rule.read'))fail('ACCESS_DENIED','检查规则目录需要当前组织的规则读取权限')
 validContext(context);validate(ruleDirectorySchema+'Query',query)
 if(query.q&&projectTextIssue(query.q,256))fail('SCHEMA_INVALID','规则筛选文字需要有效内容')
 const captured=canonicalTemplateValue(context),actor=session.user.user_id
 const raw=await request.get('/api/business-rule-directory',{medical:'envelope',signal,params:query,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':context.context_revision}})
 signal.throwIfAborted();const response=envelope(raw),result=validate<RuleBusinessDirectory>(ruleDirectorySchema+'Directory',response.data)
 validContext(result.current_context)
 const expected:RuleDirectoryFilters={limit:query.limit??25,q:query.q??null,dimension:query.dimension??null,handling:query.handling??null,status:query.status??null}
 if(raw.status!==200||response.resource_revision!==context.context_revision||result.tenant_id!==context.tenant_id
   ||canonicalTemplateValue(result.current_context)!==captured||canonicalTemplateValue(session.current_context)!==captured||session.user.user_id!==actor
   ||canonicalTemplateValue(expected)!==canonicalTemplateValue(result.query)||result.items.length>expected.limit
   ||result.next_after!==null&&result.next_after===query.after)fail('CONTENT_MISMATCH','检查规则目录与当前身份、组织或完整筛选条件不一致')
 let previous=''
 for(const item of result.items){
  const rule=item.resource.rule,business=item.business
  if(previous&&rule.rule_version<=previous||!ruleAllowed(session,'rule.read',rule.rule_version)||expected.status&&rule.status!==expected.status)fail('CONTENT_MISMATCH','检查规则的权限、顺序或状态不一致')
  await checkStored(session,rule.rule_version,rule,rule.revision)
  if(business){
   validateRuleBusiness(business.definition)
   if(business.rule_version!==rule.rule_version||business.tenant_id!==context.tenant_id||business.rule_content_sha256!==rule.content_sha256
      ||business.created_by!==rule.created_by||business.created_at!==rule.created_at||business.definition_sha256!==await templateValueDigest(business.definition))fail('CONTENT_MISMATCH','目录业务定义与固定规则内容不一致')
  }
  if(expected.dimension&&business?.definition.dimension!==expected.dimension||expected.handling&&business?.definition.handling!==expected.handling)fail('CONTENT_MISMATCH','检查规则目录包含不符合业务筛选的记录')
  previous=rule.rule_version
 }
 signal.throwIfAborted();return result
}
