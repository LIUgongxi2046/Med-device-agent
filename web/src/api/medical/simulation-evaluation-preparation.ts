import request from '@/utils/request'
import { envelope, fail, schemaIssues, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { canonicalizeEx } from 'json-canonicalize'
import { byteDigest, sourceDigest } from './content-digest'
import { lookupOperation, readOperation } from './operations'
export type PreparationKind = 'EXPECTED_ITEMS' | 'SAMPLE_MANIFEST' | 'AUTHORIZED_USE_EVIDENCE'
export const preparationSchema='urn:medical:simulation-evaluation-preparation:1.0.0#/$defs/'
export interface PreparationPreview {schema_version:'1.0.0';project_id:null;kind:PreparationKind;registered:false;approved:false;production_eligible:false;canonical_json:string;document_sha256:string}
export interface PreparationRegister {schema_version:'1.0.0';project_id:null;kind:PreparationKind;source_version:string;rights_revision:string;document_sha256:string}
export interface PreparationReceipt extends PreparationRegister {operation_id:string;registered:true;approved:false;production_eligible:false;artifact:Basis;created_by:string;created_at:string}
// Receipt has artifact.content_sha256 rather than a top-level document_sha256.
export type RegisteredPreparation = Omit<PreparationReceipt,'document_sha256'>
export interface PreparationRead {receipt:RegisteredPreparation;document:unknown}
export interface PendingMaterial {actor:string;context:Context;key:string;body:PreparationRegister;digest:string}
export const canonicalPreparation=(v:unknown)=>canonicalizeEx(v,{strictUndefined:true})
const definitions={EXPECTED_ITEMS:'urn:medical:evaluation-results:1.0.0#/$defs/ExpectedItems',SAMPLE_MANIFEST:'urn:medical:evaluation-results:1.0.0#/$defs/SampleManifest',AUTHORIZED_USE_EVIDENCE:'urn:medical:simulation-authorized-use-evidence:1.0.0#/$defs/Specification'}
export function materialIssues(kind:PreparationKind,spec:unknown):string[]{
 const issues=schemaIssues(definitions[kind],spec).map(i=>i.path+': '+i.code)
 if(issues.length)return issues
 if(kind==='SAMPLE_MANIFEST'&&Array.isArray(spec)){
  if(!spec.length)issues.push('样本清单不能为空')
  const ids=new Set(),families=new Map(),inputs=new Map()
  for(const s of spec){if(s.evidence_class!=='SIMULATION'||ids.has(s.sample_id))issues.push('样本须为SIMULATION，标识不能重复');ids.add(s.sample_id);if(families.has(s.source_family)&&families.get(s.source_family)!==s.split)issues.push('同来源家族必须保持相同split');families.set(s.source_family,s.split);if(inputs.has(s.input_ref?.version_id)&&inputs.get(s.input_ref?.version_id)!==s.source_family)issues.push('同输入来源家族必须一致');inputs.set(s.input_ref?.version_id,s.source_family)}
 }
 const samples=kind==='EXPECTED_ITEMS'?[spec]:kind==='SAMPLE_MANIFEST'&&Array.isArray(spec)?spec:[]
 for(const sample of samples){const es=(sample as {expectations?:{dimension:string;control_item_ids:string[]}[]})?.expectations;if(Array.isArray(es)){if(new Set(es.map(e=>e.dimension)).size!==es.length)issues.push('评分维度不能重复');if(es.some(e=>e.dimension!=='EVAL-11'&&e.control_item_ids?.length))issues.push('只有EVAL-11可填写对照项目')}}
 return issues
}
function context(s:MedicalSession){if(!s.current_context)fail('ACCESS_DENIED','需要当前企业会话');validContext(s.current_context);return s.current_context}
function headers(s:MedicalSession){return {'X-Schema-Version':'1.0.0','X-Context-Revision':context(s).context_revision,[s.csrf.header_name]:s.csrf.token}}
function exactKeys(v:unknown,keys:string[]){if(!v||typeof v!=='object'||Array.isArray(v)||Object.keys(v).sort().join('|')!==keys.sort().join('|'))fail('CONTENT_MISMATCH','准备产物响应结构不一致')}
async function documentCheck(kind:PreparationKind,doc:unknown,hash:string){validate(kind==='AUTHORIZED_USE_EVIDENCE'?'urn:medical:simulation-authorized-use-evidence:1.0.0#/$defs/Document':definitions[kind],doc);if(await sourceDigest(doc)!==hash)fail('CONTENT_MISMATCH','准备文档内容摘要不一致')}
export async function previewMaterial(s:MedicalSession,kind:PreparationKind,spec:unknown,signal:AbortSignal):Promise<PreparationPreview>{
 const e=envelope(await request.post('/api/simulation-evaluation-preparations/preview',{schema_version:'1.0.0',project_id:null,kind,specification:spec},{medical:'envelope',signal,headers:headers(s)}));const v=e.data as PreparationPreview
 exactKeys(v,['schema_version','project_id','kind','registered','approved','production_eligible','canonical_json','document_sha256'])
 if(e.resource_revision!=='1'||v.schema_version!=='1.0.0'||v.project_id!==null||v.kind!==kind||v.registered!==false||v.approved!==false||v.production_eligible!==false||typeof v.canonical_json!=='string'||! /^[a-f0-9]{64}$/.test(v.document_sha256))fail('CONTENT_MISMATCH','预览不是固定未登记文档')
 let doc:unknown;try{doc=JSON.parse(v.canonical_json)}catch{return fail('CONTENT_MISMATCH','预览文档JSON无效')}
 if(canonicalPreparation(doc)!==v.canonical_json||await byteDigest(new TextEncoder().encode(v.canonical_json))!==v.document_sha256||kind!=='AUTHORIZED_USE_EVIDENCE'&&canonicalPreparation(spec)!==v.canonical_json)fail('CONTENT_MISMATCH','预览文档与请求或原字节摘要不一致')
 await documentCheck(kind,doc,v.document_sha256);if(kind==='AUTHORIZED_USE_EVIDENCE'){const d=doc as {tenant_id:string;specification:unknown};if(d.tenant_id!==context(s).tenant_id||canonicalPreparation(d.specification)!==canonicalPreparation(spec))fail('CONTENT_MISMATCH','授权见证不属于当前企业和原清单')}return v
}
export async function verifyMaterialPending(s:MedicalSession,p:PendingMaterial,sameContext:boolean){validate(preparationSchema+'Register',p.body);validContext(p.context);if(p.actor!==s.user.user_id||p.context.tenant_id!==context(s).tenant_id||! /^[0-9a-f-]{36}$/.test(p.key)||await sourceDigest({actor:p.actor,context:p.context,key:p.key,body:p.body})!==p.digest||sameContext&&canonicalPreparation(context(s))!==canonicalPreparation(p.context))fail('CONTENT_MISMATCH','原准备登记身份或参数已变化')}
export function matchMaterial(p:PendingMaterial,r:RegisteredPreparation){if(r.kind!==p.body.kind||r.source_version!==p.body.source_version||r.rights_revision!==p.body.rights_revision||r.artifact.content_sha256!==p.body.document_sha256||r.created_by!==p.actor)fail('CONTENT_MISMATCH','准备登记回执与原请求不一致')}
export async function registerMaterial(s:MedicalSession,p:PendingMaterial,signal:AbortSignal):Promise<RegisteredPreparation>{await verifyMaterialPending(s,p,true);signal.throwIfAborted();const raw=await request.post('/api/simulation-evaluation-preparations',p.body,{medical:'envelope',headers:{...headers(s),'Idempotency-Key':p.key}});const e=envelope(raw),r=validate<RegisteredPreparation>(preparationSchema+'Receipt',e.data);if(raw.status!==201||e.resource_revision!=='1')fail('CONTENT_MISMATCH','准备登记状态无效');matchMaterial(p,r);return r}
export async function readMaterial(s:MedicalSession,id:string,signal:AbortSignal):Promise<PreparationRead>{validate('urn:medical:templates:1.0.0#/$defs/Id',id);const e=envelope(await request.get('/api/simulation-evaluation-preparations/'+id,{medical:'envelope',signal,headers:headers(s)}));exactKeys(e.data,['receipt','document']);const v=e.data as PreparationRead;validate(preparationSchema+'Receipt',v.receipt);if(e.resource_revision!=='1'||v.receipt.artifact.version_id!==id)fail('CONTENT_MISMATCH','准备登记固定身份不一致');await documentCheck(v.receipt.kind,v.document,v.receipt.artifact.content_sha256);if(v.receipt.kind==='AUTHORIZED_USE_EVIDENCE'&&(v.document as {tenant_id:string}).tenant_id!==context(s).tenant_id)fail('CONTENT_MISMATCH','授权见证企业不一致');return v}
export async function recoverMaterial(s:MedicalSession,p:PendingMaterial,signal:AbortSignal):Promise<{status:string;value:PreparationRead|null}>{await verifyMaterialPending(s,p,false);const lookup=await lookupOperation(s,'SIM-EVALUATION-PREPARE',p.key,signal);if(lookup.lookup_status!=='FOUND')return {status:lookup.lookup_status,value:null};const op=await readOperation(s,lookup.operation!.operation_id,signal);if(op.operation_id!==lookup.operation!.operation_id||op.resource_id!==lookup.operation!.resource_id)fail('CONTENT_MISMATCH','恢复操作身份不一致');if(op.status!=='COMPLETED')return {status:op.status,value:null};if(!op.resource_id)fail('CONTENT_MISMATCH','完成操作缺少固定制品');const value=await readMaterial(s,op.resource_id,signal);if(value.receipt.operation_id!==op.operation_id)fail('CONTENT_MISMATCH','恢复回执不属于原操作');matchMaterial(p,value.receipt);return {status:'COMPLETED',value}}
const key=(s:MedicalSession)=>'medical.simulation.material:'+s.user.user_id+':'+context(s).tenant_id
export function retainMaterial(s:MedicalSession,p:PendingMaterial){sessionStorage.setItem(key(s),JSON.stringify(p))}
export function restoreMaterial(s:MedicalSession):PendingMaterial|null{const v=sessionStorage.getItem(key(s));if(!v)return null;try{return JSON.parse(v)}catch{return fail('CONTENT_MISMATCH','准备登记恢复记录损坏')}}
export function forgetMaterial(s:MedicalSession){sessionStorage.removeItem(key(s))}
