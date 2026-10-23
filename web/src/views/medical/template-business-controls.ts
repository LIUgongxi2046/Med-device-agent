import type { TemplateAttachment, TemplateField, TemplateNode, TemplateRepeat } from '@/api/medical/template-approval'
import { schemaIssues } from '@/api/medical/protocol'
import { readBoundedJson } from '@/api/medical/project-json'
import { draftJsonIssue } from './template-collection-input'
import type { Basis } from '@/api/medical/protocol'
export interface DefinitionCatalogue {nodes:TemplateNode[];fields:TemplateField[];repeats:TemplateRepeat[];attachments:TemplateAttachment[]}
export function definitionCatalogue(sections:Record<string,string>):DefinitionCatalogue|null {
  const values:Record<string,unknown[]>={}
  for(const [key,kind] of [['nodes','Node'],['field_definitions','Field'],['repeat_definitions','Repeat'],['attachment_requirements','Attachment']]){
    const text=sections[key!]!;if(!text.trim()){values[key!]=[];continue}
    if(draftJsonIssue(text))return null
    const parsed=readBoundedJson(text);if(!Array.isArray(parsed)||parsed.some(item=>schemaIssues('urn:medical:templates:1.0.0#/$defs/'+kind,item).length))return null
    values[key!]=parsed
  }
  return {nodes:values.nodes as unknown as TemplateNode[],fields:values.field_definitions as unknown as TemplateField[],repeats:values.repeat_definitions as unknown as TemplateRepeat[],attachments:values.attachment_requirements as unknown as TemplateAttachment[]}
}
export function templateBasisNames(sections:Record<string,string>,catalogue:DefinitionCatalogue|null):Record<string,string>{
  const names:Record<string,string>={}
  const manifest=sections.basis_manifest
  if(manifest?.trim()&&!draftJsonIssue(manifest)){const value=readBoundedJson(manifest);if(!schemaIssues('urn:medical:templates:1.0.0#/$defs/Bases',value).length)for(const basis of value as unknown as Basis[])names[basis.version_id]='其他固定依据'}
  for(const node of catalogue?.nodes??[])for(const basis of node.basis_refs)names[basis.version_id]='章节依据 · '+node.title
  for(const field of catalogue?.fields??[])for(const basis of field.basis_refs)names[basis.version_id]='字段依据 · '+field.name
  const format=sections.format_profile
  if(format?.trim()&&!draftJsonIssue(format)){const value=readBoundedJson(format);if(!schemaIssues('urn:medical:templates:1.0.0#/$defs/FormatProfile',value).length){const profile=value as unknown as {format_revision:Basis;numbering_rule_revision:Basis;font_artifacts:Basis[]};names[profile.format_revision.version_id]='固定版式';names[profile.numbering_rule_revision.version_id]='固定编号规则';profile.font_artifacts.forEach((basis,index)=>names[basis.version_id]='获授权字体 '+(index+1))}}
  return names
}
