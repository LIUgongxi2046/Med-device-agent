<template>
  <el-button :disabled="!contentVisible" @click="panelOpen=true">打开 Gru 文稿助手</el-button>
  <gru-panel :open="panelOpen && contentVisible" label="Gru 固定文稿助手" @close="panelOpen=false">
  <section class="gru-records" aria-label="Gru 固定文稿助手">
    <header class="gru-heading"><span class="gru-avatar" aria-hidden="true">G</span><span>Gru 文稿助手<small>围绕固定正文版本，整理你的问题与修改需求</small></span><span class="gru-badge">仅记录 · 未执行</span></header>
    <p v-if="!contentVisible" role="status" class="gru-muted">会话内容暂时隐藏，请返回当前文稿并完成身份核查。</p>
    <div v-if="contentVisible" class="gru-content">
    <section class="gru-context" aria-label="固定会话上下文">
      <div class="gru-section-title"><h3>当前上下文</h3><span class="gru-badge">固定版本</span></div>
      <dl><div><dt>项目</dt><dd>{{ snapshot.project_id }}</dd></div><div><dt>文稿</dt><dd>{{ snapshot.document_id }}</dd></div><div><dt>正文版本</dt><dd>{{ snapshot.document_revision }}</dd></div></dl>
      <p v-if="dirty" class="gru-warning" role="status">正文有未保存修改。先保存正文，再创建会话或绑定选区；未保存内容不会进入会话。</p>
      <p v-else class="gru-muted">仅使用已保存正文。会话不会随页面或正文修改自动切换版本。</p>
      <details v-if="selection"><summary>当前选区 · {{ selection.segments.length }} 个片段</summary><pre v-for="(quote,index) in selectionPreview" :key="index" class="gru-quote">{{ quote }}</pre><p>选区版本：{{ selection.document_revision }}</p></details>
      <p v-else class="gru-muted">尚未选择正文。可在编辑器中选择文字、图片或表格后绑定。</p>
    </section>
    <aside class="gru-capability" aria-label="助手能力状态"><strong>历史消息仍为仅记录</strong><p>保存消息不会执行。需要执行时，使用独立动作入口核对参数并明确确认；原记录与导出保持未执行语义。</p></aside>
    <gru-execution v-if="view && canRead && canUse && !dirty" :session="session" :conversation="view" />
    <p v-if="error" class="gru-error" role="alert">{{ error }}</p>
    <p v-if="!checked" role="status">正在核查服务状态。</p>
    <template v-else-if="!enabled"><p v-if="!error">当前服务器尚未启用会话记录。</p><el-button :disabled="busy" @click="reset(true)">重新核查</el-button></template>
    <template v-else>
      <p v-if="!canRead">当前没有此文稿的 conversation.read 权限，无法读取会话。</p>
      <p v-if="hasDraft && !view && !pending" class="gru-warning" role="status">未提交草稿已保留在当前页面内存中。重新读取会话后可继续编辑；不会自动保存或执行。</p>
      <el-button v-if="hasDraft && !pending" :disabled="busy" @click="discardDraft">放弃未提交草稿</el-button>
      <div class="gru-section-title"><h3>会话历史</h3><el-button :disabled="busy||!canRead" @click="list">刷新我的会话</el-button></div>
      <p v-if="historyLoaded && !items.length" class="gru-empty">此文稿还没有你的会话。可在下方创建固定版本会话。</p>
      <el-select v-model="selected" :disabled="busy||!!pending||!canRead" aria-label="选择固定文稿会话" placeholder="选择此文稿的真实会话" @change="open()">
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
      <details v-else class="gru-create"><summary>围绕当前版本新建会话</summary>
        <p class="gru-muted">先读取已保存文稿与真实来源范围，再填写创建理由。此操作不会执行需求。</p>
        <el-button :disabled="busy||!canRead||!canUse||dirty" @click="loadBasis">读取当前固定文稿的真实会话依据</el-button>
        <p v-if="basis">固定文稿 {{ basis.document_revision }} · 来源快照 {{ basis.scope_snapshot_id }} · {{ basis.scope.mode }}</p>
        <details v-if="basis"><summary>完整来源范围（只读）</summary><pre>{{ JSON.stringify(basis.scope,null,2) }}</pre></details>
        <el-form-item label="创建会话理由"><el-input v-model="createReason" type="textarea" :disabled="busy" maxlength="2000"/></el-form-item>
        <el-button :disabled="busy||!canRead||!canUse||dirty||!basis" @click="create">创建固定版本会话</el-button>
      </details>
      <gru-record-export v-if="view" :session="session" :selected="view" :active="active" :disabled="busy || !!pending" />
      <section v-if="view" class="gru-conversation" aria-label="已保存会话">
        <h3>已保存的用户消息 · 未执行</h3><p>会话 {{ view.conversation_id }} · 固定版本 {{ view.document_revision }} · 修订 {{ view.revision }}</p>
        <p v-if="view.document_revision!==snapshot.document_revision">这是另一固定版本的会话；可阅读，但不能将当前选区或草稿追加到旧版本。</p>
        <div v-if="view.messages.length===0" class="gru-empty"><strong>从一条需求开始</strong><p>此页没有已保存消息。填写问题与保存理由后，只会生成用户消息记录。</p></div>
        <article v-for="(message,index) in view.messages" :key="message.message_id" class="gru-message"><header><strong>你 · {{ intentLabel(view.message_records[index]?.intent) }}</strong><span class="gru-badge">已保存 · 未执行</span></header><time>{{ message.created_at }}</time><pre>{{ message.content }}</pre><details v-if="message.selection"><summary>这条消息绑定的选区</summary><p>固定版本 {{ message.selection.document_revision }} · {{ message.selection.segments.length }} 个片段</p></details><details><summary>记录标识</summary><p>消息 {{ message.message_id }} · 操作 {{ message.operation_id }}</p></details></article>
        <p>第 {{ view.page_info.page }} 页 · 共 {{ view.page_info.total }} 条记录</p>
        <el-button :disabled="busy||!!pending||view.page_info.page===1" @click="open(view.page_info.page-1,view.page_info.dataset_watermark)">上一页</el-button>
        <el-button :disabled="busy||!!pending||view.page_info.next_page===null" @click="open(view.page_info.next_page??1,view.page_info.dataset_watermark)">下一页</el-button>
        <el-form class="gru-composer" label-position="top" :disabled="!appendAllowed">
          <div class="gru-section-title"><h3>编写需求</h3><span class="gru-muted">{{ text ? '未提交草稿' : '仅保存用户消息' }}</span></div>
          <p v-if="!canUse" class="gru-warning">当前没有 conversation.use 权限，不能保存消息。</p>
          <p v-else-if="dirty" class="gru-warning">请先保存正文修改，再保存此消息。</p>
          <p v-else-if="!appendAllowed" class="gru-warning">此会话不是当前正文版本。请围绕当前版本新建会话。</p>
          <el-form-item label="消息用途（仅记录）"><el-select v-model="intent"><el-option v-for="value in intents" :key="value" :value="value" :label="gruIntentLabels[value]"/></el-select></el-form-item>
          <el-checkbox v-model="includeSelection" :disabled="!selection || selection.document_revision !== snapshot.document_revision">绑定当前已保存正文选区</el-checkbox>
          <p v-if="includeSelection">选区绑定固定正文版本；消息不会改动正文或创建批注。</p>
          <el-form-item label="你的消息"><el-input v-model="text" type="textarea" @compositionstart="composerKeys.start" @compositionend="composerKeys.end" @keydown="composerKeydown" @keyup="composerKeys.keyup" :rows="5" maxlength="16384" show-word-limit placeholder="描述你希望解释、检索、检查或修改的内容。保存后不会自动执行。"/></el-form-item>
          <p class="gru-muted">Enter 保存记录，Shift+Enter 换行。输入法选字不会发送；保存中可继续编写下一条草稿。</p>
          <el-form-item label="保存理由"><el-input v-model="messageReason" type="textarea" maxlength="2000"/></el-form-item>
          <el-button :disabled="!canAppend" @click="append">保存用户消息（不执行）</el-button>
        </el-form>
      </section>
      <section v-if="staleArchive"><p>旧请求的未应用证明已保留。原消息与理由已放回输入框；选区请重新确认。不会自动生成新请求。</p><el-button :disabled="busy" @click="downloadProof">下载已保留证明与原草稿</el-button></section>
      <p v-if="receipt" role="status">已保存记录，未执行。会话 {{ receipt.conversation_id }}；消息 {{ receipt.message_id??'尚无消息' }}；操作 {{ receipt.operation_id }}。</p>
    </template>
    </div>
  </section>
  </gru-panel>
</template>
<script setup lang="ts">
import GruExecution from './gru-execution.vue'
import { computed, ref, watch, onBeforeUnmount, toRaw } from 'vue'
import GruRecordExport from './gru-record-export.vue'
import GruPanel from './gru-panel.vue'
import { createGruComposerKeys, registerGruEntry } from '@/api/medical/gru-keyboard'
import useUserStore from '@/store/modules/user'
import { fail } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import type { DocumentSnapshot, DocumentBody, Selection } from '../../../../contracts/typescript/src/interfaces'
import { gruIntentLabels, gruSelectionPreview } from '@/api/medical/gru-presentation'
import { sourceDigest } from '@/api/medical/content-digest'
import { canonicalDocumentValue } from '@/api/medical/document-body'
import { gruAllowed, gruAvailability, gruInputIssues, readGruContext, listGru, readGru, writeGru, recoverGru, recoverGruAppend, archiveGruStale, restoreGruStale, verifyGruPending, saveGruPending, restoreGruPending, clearGruPending } from '@/api/medical/gru-records'
import type { GruContext, GruView, GruRead, GruReceipt, GruPending, GruCreate, GruAppend, GruAppendRecovery, GruStaleArchive } from '@/api/medical/gru-records'
const props=defineProps<{session:MedicalSession;snapshot:DocumentSnapshot;active:boolean;dirty:boolean;selection?:Selection|null|undefined;savedBody?:DocumentBody|undefined}>()
const panelOpen=ref(false)
const composerKeys=createGruComposerKeys()
let unregisterEntry:(()=>void)|undefined
let draftRevision=0,submittedDraft:{key:string;revision:number}|null=null
function composerKeydown(event:KeyboardEvent){if(composerKeys.shouldSubmit(event)){event.preventDefault();void append()}}
const user=useUserStore(),enabled=ref(false),checked=ref(false),loading=ref(false),error=ref('')
const basis=ref<GruContext|null>(null),items=ref<GruView[]>([]),selected=ref(''),view=ref<GruRead|null>(null),pending=ref<GruPending|null>(null),receipt=ref<GruReceipt|null>(null)
const createReason=ref(''),messageReason=ref(''),text=ref(''),intent=ref<GruAppend['intent']>('QUERY'),includeSelection=ref(false),confirmed=ref(false),lookupStatus=ref('')
const recoveryProof=ref<GruAppendRecovery|null>(null),staleArchive=ref<GruStaleArchive|null>(null),savedProof=ref(false),proofExportFailed=ref(false)
const historyLoaded=ref(false), pageHidden=ref(document.hidden), invalidated=ref(false)
let invalidatedSession:MedicalSession|null=null, ownerInitialized=false, archiveDraftEligible=true
const contentVisible=computed(()=>props.active&&!pageHidden.value&&!invalidated.value&&toRaw(user.medicalSession)===toRaw(props.session)&&gruAllowed(props.session,props.snapshot.project_id,props.snapshot.document_id,'conversation.read'))
const hasDraft=computed(()=>!!(text.value||messageReason.value||createReason.value))
function discardDraft(){if(busy.value||pending.value)return;archiveDraftEligible=false;text.value='';messageReason.value='';createReason.value='';intent.value='QUERY';includeSelection.value=false}
const selectionPreview=computed(()=>props.dirty ? ['正文尚有未保存修改，选区内容暂不可用于会话。请保存后重新选择。'] : gruSelectionPreview(props.selection,props.snapshot.document_revision,props.savedBody))
function intentLabel(value:GruAppend['intent']|undefined){return value ? gruIntentLabels[value] : '用户需求'}
const intents:GruAppend['intent'][]=['QUERY','RETRIEVE','CHECK','PROPOSE_EDIT']
const busy=computed(()=>loading.value||user.medicalWriting)
const canRead=computed(()=>enabled.value&&props.active&&gruAllowed(props.session,props.snapshot.project_id,props.snapshot.document_id,'conversation.read'))
const canUse=computed(()=>enabled.value&&props.active&&gruAllowed(props.session,props.snapshot.project_id,props.snapshot.document_id,'conversation.use'))
const appendAllowed=computed(()=>canUse.value&&!props.dirty&&!!view.value&&view.value.document_revision===props.snapshot.document_revision)
const canAppend=computed(()=>appendAllowed.value&&!busy.value&&!pending.value&&!!text.value.trim()&&!!messageReason.value.trim())
watch([text,messageReason,intent,includeSelection],()=>{draftRevision++},{flush:'sync'})
watch(contentVisible,visible=>{unregisterEntry?.();unregisterEntry=undefined;if(visible)unregisterEntry=registerGruEntry({open:()=>{panelOpen.value=true},close:()=>{panelOpen.value=false;composerKeys.reset()}});else panelOpen.value=false},{immediate:true,flush:'sync'})
const sameContext=computed(()=>!!pending.value&&canonicalDocumentValue(pending.value.context)===canonicalDocumentValue(props.session.current_context))
let generation=0,alive=true,controller=new AbortController(),ownsWrite=false
function live(g:number,s:MedicalSession){return alive&&contentVisible.value&&!document.hidden&&generation===g&&props.session===s}
async function read<T>(work:(s:MedicalSession,signal:AbortSignal)=>Promise<T>,apply:(v:T)=>void){if(!enabled.value||!props.active||busy.value||document.hidden)return;const g=generation,s=props.session;loading.value=true;error.value='';try{const value=await work(s,controller.signal);if(live(g,s))apply(value)}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'读取失败'}finally{if(live(g,s))loading.value=false}}
function list(){if(!canRead.value)return;return read((s,signal)=>listGru(s,props.snapshot.project_id,signal),v=>{historyLoaded.value=true;items.value=v.items.filter(i=>i.document_id===props.snapshot.document_id)})}
function loadBasis(){if(!canRead.value||!canUse.value||props.dirty||pending.value)return;basis.value=null;return read((s,signal)=>readGruContext(s,props.snapshot.project_id,props.snapshot.document_id,props.snapshot.document_revision,signal),v=>{basis.value=v})}
function open(page=1,watermark?:string){if(!canRead.value||!selected.value||pending.value||busy.value)return;const id=selected.value;view.value=null;includeSelection.value=false;return read((s,signal)=>readGru(s,id,signal,page,watermark),v=>{if(v.document_id!==props.snapshot.document_id||v.project_id!==props.snapshot.project_id)fail('CONTENT_MISMATCH','会话不属于当前文稿');view.value=v})}
async function prepare(kind:'CREATE'|'APPEND',body:GruCreate|GruAppend){if(busy.value||pending.value||!canUse.value||kind==='CREATE'&&!canRead.value||props.dirty)return;const issues=gruInputIssues(kind,body);if(issues.length){error.value=issues.join('\n');return}const g=generation,s=props.session,revision=draftRevision;if(!s.current_context)return;body=JSON.parse(JSON.stringify(body)) as GruCreate|GruAppend;loading.value=true;try{const original=kind==='CREATE'?{kind:'CREATE' as const,conversation_id:null,body:body as GruCreate,actor:s.user.user_id,context:{...s.current_context},key:crypto.randomUUID()}:{kind:'APPEND' as const,conversation_id:view.value!.conversation_id,body:body as GruAppend,actor:s.user.user_id,context:{...s.current_context},key:crypto.randomUUID()};const digest=await sourceDigest(original);if(!live(g,s))return;const p={...original,digest};saveGruPending(s,props.snapshot.document_id,p);pending.value=p;submittedDraft={key:p.key,revision};confirmed.value=true;lookupStatus.value=''}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'无法保留原请求'}finally{if(live(g,s))loading.value=false}if(pending.value&&live(g,s))await send()}
function create(){if(!basis.value)return;return prepare('CREATE',{...basis.value,reason:createReason.value})}
function append(){const v=view.value;if(!v||!canAppend.value)return;return prepare('APPEND',{schema_version:'1.0.0',expected_revision:v.revision,document_revision:v.document_revision,scope_snapshot_id:v.scope_snapshot_id,scope:v.scope,selection:includeSelection.value?props.selection??null:null,intent:intent.value,execution_mode:'RECORD_ONLY',text:text.value,reason:messageReason.value})}
function completed(s:MedicalSession,r:GruReceipt){const original=pending.value;const unchanged=!!original&&submittedDraft?.key===original.key&&submittedDraft.revision===draftRevision;if(original?.kind==='APPEND'&&unchanged){text.value='';messageReason.value='';intent.value='QUERY';includeSelection.value=false}if(original?.kind==='CREATE'&&createReason.value===original.body.reason)createReason.value='';submittedDraft=null;receipt.value=r;clearGruPending(s,props.snapshot.document_id);pending.value=null;confirmed.value=false;lookupStatus.value='';basis.value=null;selected.value=r.conversation_id;view.value=null}
async function send(){const p=pending.value,s=props.session;if(!p||!canUse.value||p.kind==='CREATE'&&!canRead.value||busy.value||!sameContext.value||!confirmed.value||document.hidden)return;const g=generation;ownsWrite=true;user.medicalWriting=true;error.value='';try{const r=await writeGru(s,p,controller.signal);if(live(g,s))completed(s,r)}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'保存结果未知，原请求已保留'}finally{if(ownsWrite){ownsWrite=false;user.medicalWriting=false}}}
function recover(){const p=pending.value;if(!p||!canRead.value)return;recoveryProof.value=null;savedProof.value=false;proofExportFailed.value=false;if(p.kind==='APPEND')return read((s,signal)=>recoverGruAppend(s,p,signal),v=>{lookupStatus.value=v.status;recoveryProof.value=v;if(v.receipt)completed(props.session,v.receipt)});return read((s,signal)=>recoverGru(s,p,signal),v=>{lookupStatus.value=v.status;if(v.receipt)completed(props.session,v.receipt)})}
function downloadProof(){const value=pending.value&&recoveryProof.value?{pending:pending.value,proof:recoveryProof.value}:staleArchive.value;if(!value||busy.value)return;let url:string|null=null;try{url=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='gru-original-message-recovery.json';a.click();proofExportFailed.value=false}catch(e){proofExportFailed.value=true;savedProof.value=false;error.value='证明与草稿导出失败，原请求仍保留；请重试保存后再结束。'}finally{if(url)URL.revokeObjectURL(url)}}
async function finishStale(){const p=pending.value,proof=recoveryProof.value,s=props.session;if(!p||p.kind!=='APPEND'||!proof||proof.status!=='NOT_APPLIED_STALE'||!savedProof.value||proofExportFailed.value||!canRead.value||busy.value)return;const g=generation;loading.value=true;error.value='';try{const archive=await archiveGruStale(s,props.snapshot.document_id,p,proof);if(!live(g,s))return;clearGruPending(s,props.snapshot.document_id);staleArchive.value=archive;pending.value=null;recoveryProof.value=null;savedProof.value=false;confirmed.value=false;lookupStatus.value='';selected.value=p.conversation_id;view.value=null;text.value=p.body.text;messageReason.value=p.body.reason;intent.value=p.body.intent;includeSelection.value=false}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'无法结束原请求'}finally{if(live(g,s))loading.value=false}if(live(g,s)&&!pending.value)await open()}

function clear(preserveDraft=false){submittedDraft=null;composerKeys.reset();historyLoaded.value=false;generation++;controller.abort();controller=new AbortController();recoveryProof.value=null;staleArchive.value=null;savedProof.value=false;proofExportFailed.value=false;enabled.value=false;checked.value=false;loading.value=false;basis.value=null;items.value=[];if(!preserveDraft)selected.value='';view.value=null;pending.value=null;receipt.value=null;includeSelection.value=false;confirmed.value=false;lookupStatus.value='';error.value='';if(!preserveDraft){text.value='';messageReason.value='';createReason.value='';intent.value='QUERY'}}
async function reset(preserveDraft=false){clear(preserveDraft);if(!alive||!contentVisible.value||document.hidden||!props.session.current_context)return;const g=generation,s=props.session;loading.value=true;try{const available=await gruAvailability(s,controller.signal);if(!live(g,s))return;enabled.value=available;checked.value=true;if(available){const p=restoreGruPending(s,props.snapshot.document_id);if(p){await verifyGruPending(s,p,false);if(live(g,s))pending.value=p}else{const archive=await restoreGruStale(s,props.snapshot.document_id);if(live(g,s)&&archive&&archive.pending.kind==='APPEND'){staleArchive.value=archive;selected.value=archive.pending.conversation_id;if(archiveDraftEligible&&(!preserveDraft||!hasDraft.value)){text.value=archive.pending.body.text;messageReason.value=archive.pending.body.reason;intent.value=archive.pending.body.intent}}}}}catch(e){if(live(g,s)){checked.value=true;error.value=e instanceof Error?e.message:'服务状态读取失败'}}finally{if(live(g,s))loading.value=false}}
watch(()=>[props.session,user.medicalSession,props.session.user.user_id,canonicalDocumentValue(props.session.current_context),canonicalDocumentValue(props.session.action_summary),props.snapshot.project_id,props.snapshot.document_id,props.snapshot.document_revision],()=>{if(ownerInitialized)archiveDraftEligible=false;ownerInitialized=true;invalidated.value=toRaw(user.medicalSession)!==toRaw(props.session)||invalidatedSession===toRaw(props.session);void reset(false)},{immediate:true,flush:'sync'})
watch(()=>props.active,()=>{void reset(true)},{flush:'sync'})
watch(()=>props.selection,()=>{includeSelection.value=false},{deep:true})
function visibility(){pageHidden.value=document.hidden;void reset(true)}
function invalidate(){archiveDraftEligible=false;invalidatedSession=toRaw(props.session);invalidated.value=true;clear(false)}function unload(e:BeforeUnloadEvent){if(pending.value||ownsWrite){e.preventDefault();e.returnValue=''}}
document.addEventListener('visibilitychange',visibility);window.addEventListener('medical-context-invalidated',invalidate);window.addEventListener('beforeunload',unload)
onBeforeUnmount(()=>{unregisterEntry?.();alive=false;clear();document.removeEventListener('visibilitychange',visibility);window.removeEventListener('medical-context-invalidated',invalidate);window.removeEventListener('beforeunload',unload)})
</script>
<style scoped>
.gru-records{margin-top:24px;border:1px solid #dbe4ee;border-radius:14px;color:#25364a;background:#fff;overflow:hidden;font-size:14px;line-height:1.6}.gru-heading{display:flex;align-items:center;gap:12px;padding:18px;background:#f1f6fc;list-style:none}.gru-heading small{display:block;color:#63768c;font-size:12px;font-weight:400}.gru-avatar{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:#284d79;color:white;font-size:22px}.gru-heading>.gru-badge{margin-left:auto}.gru-content{padding:18px}.gru-records h3{font-size:15px;margin:0}.gru-records p{overflow-wrap:anywhere;margin:8px 0}.gru-section-title,.gru-message header,.gru-actions{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.gru-badge{font-size:12px;background:#eaf0f7;border:1px solid #d7e2ef;border-radius:20px;padding:2px 9px;color:#46617e;white-space:nowrap}.gru-context{border:1px solid #e1e8f0;border-radius:10px;padding:14px;margin-bottom:14px;background:#f8fafc}.gru-context dl{margin:12px 0}.gru-context dl>div{display:grid;grid-template-columns:76px 1fr;gap:8px}.gru-context dt,.gru-muted{color:#687a8f}.gru-context dd{margin:0;overflow-wrap:anywhere;font-size:12px}.gru-capability{padding:12px 14px;border-left:3px solid #819ab6;background:#f5f8fc;margin-bottom:18px}.gru-capability p{color:#64758a;font-size:13px}.gru-actions{justify-content:flex-start}.gru-records pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.gru-quote{border-left:3px solid #8da9c7;padding:8px 12px;background:#fff;max-height:180px;overflow:auto}.gru-records summary{cursor:pointer;font-weight:600}.gru-records summary:focus-visible{outline:2px solid #416c9e;outline-offset:-3px}.gru-records .el-select{width:100%}.gru-records .el-button{margin:6px 0;max-width:100%;white-space:normal;height:auto;min-height:32px}.gru-create{border-block:1px solid #e4eaf2;margin:16px 0;padding:14px 0}.gru-conversation{margin-top:18px}.gru-message{margin:14px 0 14px 20px;padding:14px 16px;border:1px solid #dce5ef;border-radius:12px 12px 3px 12px;background:#f3f7fc}.gru-message time{font-size:12px;color:#6b7c90}.gru-message details{font-size:12px;color:#66798f}.gru-empty{padding:20px;text-align:center;color:#687a8f;border:1px dashed #ccd8e5;border-radius:10px;margin:12px 0}.gru-composer{background:#fafbfd;border:1px solid #dce5ef;border-radius:10px;padding:16px;margin-top:18px}.gru-composer .gru-section-title{margin-bottom:14px}.gru-error,.gru-warning{background:#fff5e7;border-radius:6px;padding:10px;color:#85551f}.gru-error{background:#fff0ef;color:#a03932}.gru-pending{padding:14px;background:#fff6e8;border-radius:10px;margin:12px 0}@media(max-width:650px){.gru-content{padding:12px}.gru-heading{padding:12px;flex-wrap:wrap}.gru-heading>.gru-badge{margin-left:50px}.gru-message{margin-left:0}.gru-context dl>div{grid-template-columns:60px 1fr}.gru-composer{padding:12px}.gru-records .el-button{width:100%}}
</style>
