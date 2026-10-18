<template>
  <details class="gru-records" aria-label="固定文稿会话记录">
    <summary>Gru · 固定文稿会话记录</summary>
    <p>保存你的消息，供后续处理。当前仅记录，尚未执行。</p>
    <details><summary>当前固定版本</summary><p>{{ snapshot.document_revision }}；未保存正文不会进入会话。</p></details>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="!checked">正在核查服务状态。</p>
    <template v-else-if="!enabled"><p v-if="!error">当前服务器尚未启用会话记录。</p><el-button :disabled="busy" @click="reset">重新核查</el-button></template>
    <template v-else>
      <p v-if="!canRead">当前没有此文稿的 conversation.read 权限，无法读取会话。</p>
      <el-button :disabled="busy||!canRead" @click="list">读取我的会话</el-button>
      <el-select v-model="selected" :disabled="busy||!!pending||!canRead" placeholder="选择此文稿的真实会话" @change="open()">
        <el-option v-for="item in items" :key="item.conversation_id" :value="item.conversation_id" :label="item.created_at+' · '+item.document_revision+' · '+item.conversation_id"/>
      </el-select>
      <el-button :disabled="busy||!!pending||!canRead||!selected" @click="open()">读取所选会话</el-button>
      <section v-if="pending" class="gru-pending">
        <h4>原消息操作待核查</h4><p>原 key：{{ pending.key }}；{{ pending.kind }}。结果未知不能当作失败，也不会自动换 key 重发。</p>
        <details><summary>完整原请求</summary><pre>{{ JSON.stringify(pending.body,null,2) }}</pre></details>
        <el-button :disabled="busy||!canRead" @click="recover">查询原操作与固定回执</el-button>
        <p v-if="lookupStatus">查询结果：{{ lookupStatus }}。NOT_FOUND 不证明原写入未发生。</p>
        <section v-if="recoveryProof?.status==='NOT_APPLIED_STALE'">
          <p>服务器已核验：这份原请求未应用，且会话修订已变化。先保存原证明与草稿，再读取新修订。</p>
          <el-button :disabled="busy" @click="downloadProof">下载原证明与消息草稿</el-button>
          <el-checkbox v-model="savedProof" :disabled="busy">我已保存原证明和草稿，确认结束这份未应用的旧请求</el-checkbox>
          <el-button :disabled="busy||!savedProof||proofExportFailed" @click="finishStale">保留草稿并读取新修订</el-button>
        </section>
        <el-checkbox v-model="confirmed" :disabled="busy">我已核查原请求，明确按原 key 重放</el-checkbox>
        <el-button :disabled="busy||!canUse||!sameContext||!confirmed||lookupStatus!=='NOT_FOUND'" @click="send">显式重放原请求</el-button>
      </section>
      <section v-else>
        <el-button :disabled="busy||!canRead||!canUse||dirty" @click="loadBasis">读取当前固定文稿的真实会话依据</el-button>
        <p v-if="basis">固定文稿 {{ basis.document_revision }} · 来源快照 {{ basis.scope_snapshot_id }} · {{ basis.scope.mode }}</p>
        <details v-if="basis"><summary>完整来源范围（只读）</summary><pre>{{ JSON.stringify(basis.scope,null,2) }}</pre></details>
        <el-form-item label="创建会话理由"><el-input v-model="createReason" type="textarea" :disabled="busy" maxlength="2000"/></el-form-item>
        <el-button :disabled="busy||!canRead||!canUse||dirty||!basis" @click="create">创建固定版本会话</el-button>
      </section>
      <gru-record-export v-if="view" :session="session" :selected="view" :active="active" :disabled="busy || !!pending" />
      <section v-if="view">
        <h4>已保存的用户消息 · 未执行</h4><p>会话 {{ view.conversation_id }} · 固定版本 {{ view.document_revision }} · 修订 {{ view.revision }}</p>
        <p v-if="view.document_revision!==snapshot.document_revision">这是另一固定版本的会话；可阅读，但不能将当前选区或草稿追加到旧版本。</p>
        <p v-if="view.messages.length===0">此页没有已保存消息。</p>
        <article v-for="(message,index) in view.messages" :key="message.message_id"><strong>用户 · {{ view.message_records[index]?.intent }} · 未执行</strong><p>{{ message.created_at }}</p><pre>{{ message.content }}</pre><details><summary>记录标识</summary><p>消息 {{ message.message_id }} · 操作 {{ message.operation_id }}</p></details></article>
        <p>第 {{ view.page_info.page }} 页 · 共 {{ view.page_info.total }} 条记录</p>
        <el-button :disabled="busy||!!pending||view.page_info.page===1" @click="open(view.page_info.page-1,view.page_info.dataset_watermark)">上一页</el-button>
        <el-button :disabled="busy||!!pending||view.page_info.next_page===null" @click="open(view.page_info.next_page??1,view.page_info.dataset_watermark)">下一页</el-button>
        <el-form v-if="!pending" label-position="top" :disabled="busy||!appendAllowed">
          <el-form-item label="消息用途（仅记录）"><el-select v-model="intent"><el-option v-for="value in intents" :key="value" :value="value" :label="value"/></el-select></el-form-item>
          <el-checkbox v-model="includeSelection" :disabled="!selection">绑定当前已保存正文选区</el-checkbox>
          <p v-if="includeSelection">选区绑定固定正文版本；消息不会改动正文或创建批注。</p>
          <el-form-item label="你的消息"><el-input v-model="text" type="textarea" :rows="5" maxlength="16384"/></el-form-item>
          <el-form-item label="保存理由"><el-input v-model="messageReason" type="textarea" maxlength="2000"/></el-form-item>
          <el-button :disabled="!appendAllowed||busy" @click="append">保存用户消息（不执行）</el-button>
        </el-form>
      </section>
      <section v-if="staleArchive"><p>旧请求的未应用证明已保留。原消息与理由已放回输入框；选区请重新确认。不会自动生成新请求。</p><el-button :disabled="busy" @click="downloadProof">下载已保留证明与原草稿</el-button></section>
      <p v-if="receipt" role="status">已保存记录，未执行。会话 {{ receipt.conversation_id }}；消息 {{ receipt.message_id??'尚无消息' }}；操作 {{ receipt.operation_id }}。</p>
    </template>
  </details>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import GruRecordExport from './gru-record-export.vue'
import useUserStore from '@/store/modules/user'
import { fail } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import type { DocumentSnapshot, Selection } from '../../../../contracts/typescript/src/interfaces'
import { sourceDigest } from '@/api/medical/content-digest'
import { canonicalDocumentValue } from '@/api/medical/document-body'
import { gruAllowed, gruAvailability, gruInputIssues, readGruContext, listGru, readGru, writeGru, recoverGru, recoverGruAppend, archiveGruStale, restoreGruStale, verifyGruPending, saveGruPending, restoreGruPending, clearGruPending } from '@/api/medical/gru-records'
import type { GruContext, GruView, GruRead, GruReceipt, GruPending, GruCreate, GruAppend, GruAppendRecovery, GruStaleArchive } from '@/api/medical/gru-records'
const props=defineProps<{session:MedicalSession;snapshot:DocumentSnapshot;active:boolean;dirty:boolean;selection?:Selection|null|undefined}>()
const user=useUserStore(),enabled=ref(false),checked=ref(false),loading=ref(false),error=ref('')
const basis=ref<GruContext|null>(null),items=ref<GruView[]>([]),selected=ref(''),view=ref<GruRead|null>(null),pending=ref<GruPending|null>(null),receipt=ref<GruReceipt|null>(null)
const createReason=ref(''),messageReason=ref(''),text=ref(''),intent=ref<GruAppend['intent']>('QUERY'),includeSelection=ref(false),confirmed=ref(false),lookupStatus=ref('')
const recoveryProof=ref<GruAppendRecovery|null>(null),staleArchive=ref<GruStaleArchive|null>(null),savedProof=ref(false),proofExportFailed=ref(false)
const intents:GruAppend['intent'][]=['QUERY','RETRIEVE','CHECK','PROPOSE_EDIT']
const busy=computed(()=>loading.value||user.medicalWriting)
const canRead=computed(()=>enabled.value&&props.active&&gruAllowed(props.session,props.snapshot.project_id,props.snapshot.document_id,'conversation.read'))
const canUse=computed(()=>enabled.value&&props.active&&gruAllowed(props.session,props.snapshot.project_id,props.snapshot.document_id,'conversation.use'))
const appendAllowed=computed(()=>canUse.value&&!props.dirty&&!!view.value&&view.value.document_revision===props.snapshot.document_revision)
const sameContext=computed(()=>!!pending.value&&canonicalDocumentValue(pending.value.context)===canonicalDocumentValue(props.session.current_context))
let generation=0,alive=true,controller=new AbortController(),ownsWrite=false
function live(g:number,s:MedicalSession){return alive&&props.active&&!document.hidden&&generation===g&&props.session===s}
async function read<T>(work:(s:MedicalSession,signal:AbortSignal)=>Promise<T>,apply:(v:T)=>void){if(!enabled.value||!props.active||busy.value||document.hidden)return;const g=generation,s=props.session;loading.value=true;error.value='';try{const value=await work(s,controller.signal);if(live(g,s))apply(value)}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'读取失败'}finally{if(live(g,s))loading.value=false}}
function list(){if(!canRead.value)return;return read((s,signal)=>listGru(s,props.snapshot.project_id,signal),v=>{items.value=v.items.filter(i=>i.document_id===props.snapshot.document_id)})}
function loadBasis(){if(!canRead.value||!canUse.value||props.dirty||pending.value)return;basis.value=null;return read((s,signal)=>readGruContext(s,props.snapshot.project_id,props.snapshot.document_id,props.snapshot.document_revision,signal),v=>{basis.value=v})}
function open(page=1,watermark?:string){if(!canRead.value||!selected.value||pending.value||busy.value)return;const id=selected.value;view.value=null;includeSelection.value=false;return read((s,signal)=>readGru(s,id,signal,page,watermark),v=>{if(v.document_id!==props.snapshot.document_id||v.project_id!==props.snapshot.project_id)fail('CONTENT_MISMATCH','会话不属于当前文稿');view.value=v})}
async function prepare(kind:'CREATE'|'APPEND',body:GruCreate|GruAppend){if(busy.value||pending.value||!canUse.value||kind==='CREATE'&&!canRead.value||props.dirty)return;const issues=gruInputIssues(kind,body);if(issues.length){error.value=issues.join('\n');return}const g=generation,s=props.session;if(!s.current_context)return;body=JSON.parse(JSON.stringify(body)) as GruCreate|GruAppend;loading.value=true;try{const original=kind==='CREATE'?{kind:'CREATE' as const,conversation_id:null,body:body as GruCreate,actor:s.user.user_id,context:{...s.current_context},key:crypto.randomUUID()}:{kind:'APPEND' as const,conversation_id:view.value!.conversation_id,body:body as GruAppend,actor:s.user.user_id,context:{...s.current_context},key:crypto.randomUUID()};const digest=await sourceDigest(original);if(!live(g,s))return;const p={...original,digest};saveGruPending(s,props.snapshot.document_id,p);pending.value=p;confirmed.value=true;lookupStatus.value=''}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'无法保留原请求'}finally{if(live(g,s))loading.value=false}if(pending.value&&live(g,s))await send()}
function create(){if(!basis.value)return;return prepare('CREATE',{...basis.value,reason:createReason.value})}
function append(){const v=view.value;if(!v||!appendAllowed.value)return;return prepare('APPEND',{schema_version:'1.0.0',expected_revision:v.revision,document_revision:v.document_revision,scope_snapshot_id:v.scope_snapshot_id,scope:v.scope,selection:includeSelection.value?props.selection??null:null,intent:intent.value,execution_mode:'RECORD_ONLY',text:text.value,reason:messageReason.value})}
function completed(s:MedicalSession,r:GruReceipt){receipt.value=r;clearGruPending(s,props.snapshot.document_id);pending.value=null;confirmed.value=false;lookupStatus.value='';text.value='';messageReason.value='';createReason.value='';basis.value=null;selected.value=r.conversation_id;view.value=null}
async function send(){const p=pending.value,s=props.session;if(!p||!canUse.value||p.kind==='CREATE'&&!canRead.value||busy.value||!sameContext.value||!confirmed.value||document.hidden)return;const g=generation;ownsWrite=true;user.medicalWriting=true;error.value='';try{const r=await writeGru(s,p,controller.signal);if(live(g,s))completed(s,r)}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'保存结果未知，原请求已保留'}finally{if(ownsWrite){ownsWrite=false;user.medicalWriting=false}}}
function recover(){const p=pending.value;if(!p||!canRead.value)return;recoveryProof.value=null;savedProof.value=false;proofExportFailed.value=false;if(p.kind==='APPEND')return read((s,signal)=>recoverGruAppend(s,p,signal),v=>{lookupStatus.value=v.status;recoveryProof.value=v;if(v.receipt)completed(props.session,v.receipt)});return read((s,signal)=>recoverGru(s,p,signal),v=>{lookupStatus.value=v.status;if(v.receipt)completed(props.session,v.receipt)})}
function downloadProof(){const value=pending.value&&recoveryProof.value?{pending:pending.value,proof:recoveryProof.value}:staleArchive.value;if(!value||busy.value)return;let url:string|null=null;try{url=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='gru-original-message-recovery.json';a.click();proofExportFailed.value=false}catch(e){proofExportFailed.value=true;savedProof.value=false;error.value='证明与草稿导出失败，原请求仍保留；请重试保存后再结束。'}finally{if(url)URL.revokeObjectURL(url)}}
async function finishStale(){const p=pending.value,proof=recoveryProof.value,s=props.session;if(!p||p.kind!=='APPEND'||!proof||proof.status!=='NOT_APPLIED_STALE'||!savedProof.value||proofExportFailed.value||!canRead.value||busy.value)return;const g=generation;loading.value=true;error.value='';try{const archive=await archiveGruStale(s,props.snapshot.document_id,p,proof);if(!live(g,s))return;clearGruPending(s,props.snapshot.document_id);staleArchive.value=archive;pending.value=null;recoveryProof.value=null;savedProof.value=false;confirmed.value=false;lookupStatus.value='';selected.value=p.conversation_id;view.value=null;text.value=p.body.text;messageReason.value=p.body.reason;intent.value=p.body.intent;includeSelection.value=false}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'无法结束原请求'}finally{if(live(g,s))loading.value=false}if(live(g,s)&&!pending.value)await open()}

function clear(){generation++;controller.abort();controller=new AbortController();recoveryProof.value=null;staleArchive.value=null;savedProof.value=false;proofExportFailed.value=false;enabled.value=false;checked.value=false;loading.value=false;basis.value=null;items.value=[];selected.value='';view.value=null;pending.value=null;receipt.value=null;includeSelection.value=false;confirmed.value=false;lookupStatus.value='';error.value='';text.value='';messageReason.value='';createReason.value=''}
async function reset(){clear();if(!alive||!props.active||document.hidden||!props.session.current_context)return;const g=generation,s=props.session;loading.value=true;try{const available=await gruAvailability(s,controller.signal);if(!live(g,s))return;enabled.value=available;checked.value=true;if(available){const p=restoreGruPending(s,props.snapshot.document_id);if(p){await verifyGruPending(s,p,false);if(live(g,s))pending.value=p}else{const archive=await restoreGruStale(s,props.snapshot.document_id);if(live(g,s)&&archive&&archive.pending.kind==='APPEND'){staleArchive.value=archive;selected.value=archive.pending.conversation_id;text.value=archive.pending.body.text;messageReason.value=archive.pending.body.reason;intent.value=archive.pending.body.intent}}}}catch(e){if(live(g,s)){checked.value=true;error.value=e instanceof Error?e.message:'服务状态读取失败'}}finally{if(live(g,s))loading.value=false}}
watch(()=>[props.session,canonicalDocumentValue(props.session.current_context),props.snapshot.document_id,props.snapshot.document_revision,props.active],reset,{immediate:true,flush:'sync'})
watch(()=>props.selection,()=>{includeSelection.value=false},{deep:true})
function visibility(){void reset()}function unload(e:BeforeUnloadEvent){if(pending.value||ownsWrite){e.preventDefault();e.returnValue=''}}
document.addEventListener('visibilitychange',visibility);window.addEventListener('beforeunload',unload)
onBeforeUnmount(()=>{alive=false;clear();document.removeEventListener('visibilitychange',visibility);window.removeEventListener('beforeunload',unload)})
</script>
<style scoped>.gru-records{padding:16px;border:1px solid #DCE3EC;border-radius:8px;margin-top:24px;color:#243247}.gru-records pre{white-space:pre-wrap;overflow-wrap:anywhere}.gru-records article{border-top:1px solid #E9EDF3;padding:12px 0}.gru-records .el-button{margin:8px}.gru-records .el-select{width:100%;max-width:560px}.gru-records summary{cursor:pointer;font-weight:600}.gru-records p{overflow-wrap:anywhere}@media(max-width:650px){.gru-records{padding:12px}.gru-records .el-button{max-width:100%;white-space:normal;height:auto;min-height:32px}}.gru-pending{padding:12px;background:#FFF6E8}</style>
