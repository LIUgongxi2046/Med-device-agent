import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import request from '@/utils/request'
import workspaceSchema from '../../../../business/medical-persistence/src/main/resources/document-workspace.schema.json'
import adoptionSchema from '../../../../business/medical-persistence/src/main/resources/document-candidate-adoption.schema.json'
import { envelope, fail, validate, validContext } from './protocol'
import type { MedicalSession, Context } from './protocol'
import type { DocumentBody } from '../../../../contracts/typescript/src/interfaces'
import { documentReference, canonicalDocumentValue } from './document-body'
const ajv=new Ajv2020({strictSchema:true,strictTypes:false,allErrors:true});addFormats(ajv);ajv.addSchema(workspaceSchema);ajv.addSchema(adoptionSchema)
function checked<T>(schema:string,name:string,value:unknown):T{const check=ajv.getSchema(schema+'#/$defs/'+name);if(!check||!check(value))fail('SCHEMA_INVALID','文稿操作数据不符合定义：'+name);return value as T}
export interface WorkspaceAvailability {schema_version:'1.0.0';current_context:Context;candidate_adoption_enabled:boolean;professional_review_enabled:boolean;review_confirmation_enabled:boolean;document_signing_enabled:boolean}
export interface CandidateItem {candidate_id:string;base_document_revision:string;status:'READY'|'ADOPTED'|'REJECTED'|'STALE';created_at:string;target_node_count:number}
export interface CandidateDirectory {schema_version:'1.0.0';document_id:string;project_id:string;items:CandidateItem[];complete:boolean;next_after:string|null;pagination_mode:'LIVE_DIRECTORY'}
export interface CandidateComparison {schema_version:'1.0.0';candidate_id:string;candidate_sha256:string;document_id:string;base_document_revision:string;expected_document_revision:string;target_node_ids:string[];check_ids:string[];body:DocumentBody;base_body:DocumentBody;professional_approval:false;new_evidence_path:'/evidence_links'}
export interface AdoptionPreview {schema_version:'1.0.0';candidate_id:string;pending_action_id:string;document_id:string;base_document_revision:string;action_revision:string;expected_document_revision:string;arguments_sha256:string;candidate_sha256:string;result_body_sha256:string;expires_at:string;mode:'INITIAL'|'SELECTED_NODES';selected_node_ids:string[];replaced_node_ids:string[];inserted_node_ids:string[];check_ids:string[];professional_approval:false}
export interface AdoptionResult {schema_version:'1.0.0';adoption_id:string;candidate_id:string;document_id:string;document_revision:string;pending_action_id:string;operation_id:string;check_document_intent_id:string;body_sha256:string;professional_approval:false}
export interface AdoptionPending {actor:string;tenant_id:string;document_id:string;candidate_id:string;key:string;operation:'PREVIEW'|'CONFIRM';input:Record<string,unknown>;preview?:AdoptionPreview}
function headers(session:MedicalSession){if(!session.current_context)fail('ACCESS_DENIED','文稿操作需要工作组织');validContext(session.current_context);return {'X-Schema-Version':'1.0.0','X-Context-Revision':session.current_context.context_revision}}
function contextual(session:MedicalSession,raw:Parameters<typeof envelope>[0]){const value=envelope(raw);if(value.resource_revision!==session.current_context?.context_revision)fail('CONTEXT_CHANGED','文稿工作组织已经变化');return value.data}
export async function readWorkspaceAvailability(session:MedicalSession,signal:AbortSignal){const result=checked<WorkspaceAvailability>(workspaceSchema.$id,'Availability',contextual(session,await request.get('/api/document-workspace-availability',{medical:'envelope',signal,headers:headers(session)})));validContext(result.current_context);if(canonicalDocumentValue(result.current_context)!==canonicalDocumentValue(session.current_context))fail('CONTEXT_CHANGED','文稿服务状态不属于当前工作组织');return result}
export async function readDocumentCandidates(session:MedicalSession,project:string,document:string,after:string|null,signal:AbortSignal){validate(documentReference+'Id',document);const result=checked<CandidateDirectory>(workspaceSchema.$id,'CandidateDirectory',contextual(session,await request.get('/api/documents/'+document+'/candidates',{medical:'envelope',signal,headers:headers(session),params:after?{after}:{}})));if(result.document_id!==document||result.project_id!==project||after!==null&&result.items.some(item=>item.candidate_id<=after)||result.complete!==(result.next_after===null))fail('CONTENT_MISMATCH','候选目录的文稿或分页不一致');return result}
export async function readCandidateComparison(session:MedicalSession,id:string,document:string,signal:AbortSignal){validate(documentReference+'Id',id);const raw=await request.get('/api/candidates/'+id+'/document-comparison',{medical:'envelope',signal,headers:headers(session)});if(raw.status!==200)envelope(raw);const value=checked<CandidateComparison>(workspaceSchema.$id,'Comparison',raw.data);validate(documentReference+'DocumentBody',value.body);validate(documentReference+'DocumentBody',value.base_body);if(value.candidate_id!==id||value.document_id!==document)fail('CONTENT_MISMATCH','候选比较不属于当前文稿');return value}
const storageKey=(session:MedicalSession,document:string)=>'medical.document.adoption:'+session.user.user_id+':'+session.current_context!.tenant_id+':'+document
export function readAdoptionPending(session:MedicalSession,document:string):AdoptionPending|null{
 headers(session);const text=sessionStorage.getItem(storageKey(session,document));if(text===null)return null
 const value=JSON.parse(text) as AdoptionPending
 if(value.actor!==session.user.user_id||value.tenant_id!==session.current_context!.tenant_id||value.document_id!==document||!['PREVIEW','CONFIRM'].includes(value.operation))fail('CONTENT_MISMATCH','原候选请求不属于当前身份及文稿')
 validate(documentReference+'Id',value.candidate_id);validate(documentReference+'Id',value.key)
 checked(adoptionSchema.$id,value.operation==='PREVIEW'?'PreviewInput':'ConfirmInput',value.input)
 if(value.operation==='PREVIEW'&&value.input.document_id!==document)fail('CONTENT_MISMATCH','原预览请求的文稿不一致')
 if(value.operation==='CONFIRM'){
  const preview=checked<AdoptionPreview>(adoptionSchema.$id,'Preview',value.preview)
  if(preview.candidate_id!==value.candidate_id||preview.document_id!==document||preview.pending_action_id!==value.input.pending_action_id||preview.arguments_sha256!==value.input.arguments_sha256||preview.action_revision!==value.input.expected_action_revision)fail('CONTENT_MISMATCH','原确认与固定采用范围不一致')
 }
 return value
}
export function prepareAdoption(session:MedicalSession,document:string,candidate:string,operation:AdoptionPending['operation'],input:Record<string,unknown>,preview?:AdoptionPreview):AdoptionPending{
 const original=readAdoptionPending(session,document)
 if(original){if(original.candidate_id!==candidate||original.operation!==operation||canonicalDocumentValue(original.input)!==canonicalDocumentValue(input))fail('IDEMPOTENCY_CONFLICT','请继续核查原候选请求');return original}
 checked(adoptionSchema.$id,operation==='PREVIEW'?'PreviewInput':'ConfirmInput',input)
 if(operation==='PREVIEW'&&input.document_id!==document)fail('CONTENT_MISMATCH','采用预览的文稿不一致')
 if(operation==='CONFIRM'){
  checked(adoptionSchema.$id,'Preview',preview)
  if(!preview||preview.candidate_id!==candidate||preview.document_id!==document||preview.pending_action_id!==input.pending_action_id||preview.arguments_sha256!==input.arguments_sha256||preview.action_revision!==input.expected_action_revision)fail('CONTENT_MISMATCH','确认必须使用原服务端预览')
 }
 const value:AdoptionPending={actor:session.user.user_id,tenant_id:session.current_context!.tenant_id,document_id:document,candidate_id:candidate,key:crypto.randomUUID(),operation,input:JSON.parse(canonicalDocumentValue(input)),...(preview?{preview}: {})}
 sessionStorage.setItem(storageKey(session,document),JSON.stringify(value));return value
}
export async function submitAdoption(session:MedicalSession,pending:AdoptionPending,signal:AbortSignal):Promise<AdoptionPreview|AdoptionResult>{
 const original=readAdoptionPending(session,pending.document_id),context=canonicalDocumentValue(session.current_context),actor=session.user.user_id
 if(canonicalDocumentValue(original)!==canonicalDocumentValue(pending))fail('CONTENT_MISMATCH','原候选操作身份不一致')
 const raw=await request.post('/api/candidates/'+pending.candidate_id+(pending.operation==='PREVIEW'?'/adoption-previews':'/adoptions'),pending.input,{medical:'envelope',signal,timeout:120000,headers:{...headers(session),'Idempotency-Key':pending.key,[session.csrf.header_name]:session.csrf.token}})
 signal.throwIfAborted()
 if(context!==canonicalDocumentValue(session.current_context)||actor!==session.user.user_id)fail('CONTEXT_CHANGED','采用请求期间工作组织或身份变化')
 if(raw.status!==201){envelope(raw);fail('CONTENT_MISMATCH','候选采用响应状态不符合原操作')}
 const value=pending.operation==='PREVIEW'?checked<AdoptionPreview>(adoptionSchema.$id,'Preview',raw.data):checked<AdoptionResult>(adoptionSchema.$id,'Result',contextual(session,raw))
 if(value.candidate_id!==pending.candidate_id||value.document_id!==pending.document_id)fail('CONTENT_MISMATCH','采用回执不属于原候选与文稿')
 if(pending.operation==='PREVIEW'){
  const preview=value as AdoptionPreview
  if(preview.candidate_sha256!==pending.input.candidate_sha256||preview.base_document_revision!==pending.input.base_document_revision||preview.expected_document_revision!==pending.input.expected_document_revision||preview.mode!==pending.input.mode||preview.mode==='SELECTED_NODES'&&canonicalDocumentValue(preview.selected_node_ids)!==canonicalDocumentValue(pending.input.selected_node_ids))fail('CONTENT_MISMATCH','采用预览与原节点及版本不一致')
 }else{
  const result=value as AdoptionResult
  if(result.pending_action_id!==pending.input.pending_action_id||!pending.preview||result.body_sha256!==pending.preview.result_body_sha256||result.document_revision===pending.preview.base_document_revision)fail('CONTENT_MISMATCH','采用结果不匹配原确认范围及正文摘要')
 }
 if(canonicalDocumentValue(readAdoptionPending(session,pending.document_id))!==canonicalDocumentValue(pending))fail('CONTENT_MISMATCH','采用请求身份在返回期间变化')
 sessionStorage.removeItem(storageKey(session,pending.document_id));return value
}
