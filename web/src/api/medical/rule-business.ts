import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import request from '@/utils/request'
import schema from '../../../../business/medical-persistence/src/main/resources/rule-business-definition.schema.json'
import { envelope, fail, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import type { StoredRule } from './rule-versions'
import { canonicalTemplateValue, templateValueDigest } from './template-approval'
import { projectTextIssue } from './project-write'

export const ruleDimensionLabels={CONTENT_COMPLETENESS:'内容完整性',REFERENCE_ACCURACY:'引用准确性',STRICT_FIELD_CONSISTENCY:'严格字段一致性',RULE_COMPLIANCE:'规则符合性',HUMAN_MODIFICATION:'人工修改量'} as const
export const ruleHandlingLabels={BLOCK_SIGNING:'阻止签发',MANUAL_CONFIRMATION:'人工确认',NOTICE:'提示'} as const
export const ruleClassificationLabels={PUBLIC:'公开资料',INTERNAL:'内部资料',RESTRICTED:'受限资料',PERSONAL_HEALTH:'个人健康资料'} as const
export interface RuleBusinessDefinition {name:string;dimension:keyof typeof ruleDimensionLabels;condition:string;handling:keyof typeof ruleHandlingLabels;classification:keyof typeof ruleClassificationLabels}
export interface StoredRuleBusiness {schema_version:'1.0.0';tenant_id:string;rule_version:string;definition:RuleBusinessDefinition;definition_sha256:string;rule_content_sha256:string;schema_manifest_sha256:string;operation_id:string;created_by:string;created_at:string}
const ajv=new Ajv2020({strictSchema:true,strictTypes:false,allErrors:true});addFormats(ajv);ajv.addSchema(schema)
export function validateRuleBusiness(value:unknown):RuleBusinessDefinition{
 const validator=ajv.getSchema(schema.$id+'#/$defs/Definition')
 if(!validator||!validator(value))fail('SCHEMA_INVALID','请完整填写规则名称、检查维度、判断条件、处置方式及资料分类')
 const definition=value as RuleBusinessDefinition
 if(projectTextIssue(definition.name,200)||projectTextIssue(definition.condition,4000))fail('SCHEMA_INVALID','规则名称和判断条件需要完整文字，不能包含首尾空白或控制字符')
 return definition
}
export async function readRuleBusiness(session:MedicalSession,rule:StoredRule,signal:AbortSignal):Promise<StoredRuleBusiness|null>{
 const context=session.current_context;if(!context)fail('ACCESS_DENIED','业务规则读取需要工作组织');validContext(context)
 const captured=canonicalTemplateValue(context),actor=session.user.user_id
 const raw=await request.get('/api/rule-versions/'+rule.rule_version+'/business-definition',{medical:'envelope',signal,headers:{'X-Schema-Version':'1.0.0','X-Context-Revision':context.context_revision}})
 signal.throwIfAborted();const wrapped=envelope(raw)
 if(raw.status!==200||wrapped.resource_revision!==rule.revision||captured!==canonicalTemplateValue(session.current_context)||actor!==session.user.user_id)fail('CONTEXT_CHANGED','业务规则读取期间工作组织或规则修订变化')
 if(wrapped.data===null)return null
 const validator=ajv.getSchema(schema.$id+'#/$defs/Stored')
 if(!validator||!validator(wrapped.data))fail('SCHEMA_INVALID','业务规则记录不符合固定定义')
 const value=wrapped.data as StoredRuleBusiness
 validateRuleBusiness(value.definition)
 if(value.rule_version!==rule.rule_version||value.tenant_id!==context.tenant_id||value.rule_content_sha256!==rule.content_sha256||value.created_by!==rule.created_by||value.created_at!==rule.created_at||value.definition_sha256!==await templateValueDigest(value.definition))fail('CONTENT_MISMATCH','业务规则定义与当前固定版本或内容摘要不一致')
 return value
}
