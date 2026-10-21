<template>
 <section aria-label="Gru 显式执行" class="gru-execution">
  <h3>显式执行（独立于历史记录）</h3>
  <p>查询和修改建议使用已有、已批准的固定任务与预算；检查是技术检查，不代表专业审查通过。所有执行先核对完整参数，再单独确认。</p>
  <label>意图 <select v-model="intent" :disabled="busy"><option>RETRIEVE</option><option>CHECK</option><option>QUERY</option><option>PROPOSE_EDIT</option><option>ADOPT_EDIT</option></select></label>
  <label>需求原文<textarea v-model="text" rows="3" maxlength="8000" :disabled="busy" /></label>
  <label v-if="intent!=='RETRIEVE'">固定执行参数 JSON<textarea v-model="parameters" rows="7" :disabled="busy" spellcheck="false" /></label>
  <p v-if="intent==='QUERY'||intent==='PROPOSE_EDIT'">参数需要 task_id 和 run（task_revision、plan_revision、snapshot_id、budget_revision、expected_revision、schema_version）。任务目标须与需求原文一致；不会自动创建任务或扩大来源。</p>
  <p v-if="intent==='ADOPT_EDIT'">参数填写 candidate_id 和 preview（原 DC03 预览输入）。服务器读取真实预览并展示完整采纳参数，再由你确认。</p>
  <button :disabled="busy||!text.trim()" @click="propose">准备待确认动作</button>
  <button :disabled="busy" @click="list">读取历史执行</button>
  <ul><li v-for="item in history" :key="item.action_id"><button :disabled="busy" @click="select(item)">{{ item.request.intent }} · {{ item.state }} · {{ item.action_id }}</button></li></ul>
  <p v-if="error" role="alert">{{ error }}</p>
  <section v-if="action" aria-label="动作确认与结果">
   <p>状态：{{ action.state }}；目标版本：{{ action.request.target_version }}</p>
   <pre>{{ JSON.stringify(action.request,null,2) }}</pre>
   <p>请求摘要：{{ action.request_sha256 }}</p>
   <label v-if="action.state==='PENDING'"><input v-model="confirmed" type="checkbox" />我确认以上全部参数、目标版本和实际操作</label>
   <button v-if="action.state==='PENDING'" :disabled="busy||!confirmed" @click="execute">确认执行一次</button>
   <button v-if="action.state==='STARTING'" :disabled="busy" @click="recover">恢复原操作（不新建请求）</button>
   <button :disabled="busy" @click="refresh">重新读取真实结果</button>
   <pre v-if="action.presentation" aria-label="按本人偏好呈现的执行结果">{{ action.presentation.text }}</pre>
   <p v-if="action.presentation">偏好只影响助手说明；已发布模板和项目约定优先，原始成果不变。</p>
   <details v-if="action.current_result" :open="action.presentation?.effective.detail==='FULL'"><summary>已核验工具／运行结果</summary><pre>{{ JSON.stringify(action.current_result,null,2) }}</pre></details>
   <p v-if="action.state==='STARTING'">原操作结果尚不确定。不要改参数或生成新动作来重试。</p>
   <label v-if="['PENDING','RUNNING'].includes(action.state)">取消理由<input v-model="reason" maxlength="2048" /></label>
   <button v-if="['PENDING','RUNNING'].includes(action.state)" :disabled="busy||!reason.trim()" @click="cancel">请求取消</button>
   <p v-if="cancelReceipt">取消请求已返回，需重新读取实际终态；不把接受请求当作已终止。</p>
  </section>
  <details><summary>本人习惯与摘要（仅呈现偏好，不授予业务权限）</summary>
   <label>本人习惯<textarea v-model="memoryText" rows="3" maxlength="8000" /></label>
   <label>说明布局<select v-model="memoryLayout"><option value="PARAGRAPH">段落</option><option value="BULLETS">列表</option></select></label>
   <label>说明详略<select v-model="memoryDetail"><option value="FULL">完整</option><option value="COMPACT">简洁</option></select></label>
   <p>仅主动保存的选项用于新执行的助手说明；备注不作为模型指令，模板与项目正文不受覆盖。</p>
   <button :disabled="busy||!memoryText.trim()" @click="saveMemory">{{ editingMemory?'保存修改':'保存本人习惯' }}</button>
   <button v-if="editingMemory" :disabled="busy" @click="editingMemory=null;memoryText=''">取消修改</button>
   <button :disabled="busy" @click="loadMemory">读取本人记录</button>
   <button :disabled="busy" @click="summary">摘录原会话的本人消息</button>
   <ul v-if="memories"><li v-for="memory in memories.items" :key="memory.memory_id">
    <p>{{ memory.kind }} · 版本 {{ memory.revision }}：{{ memory.content }}</p>
    <p v-if="memory.kind==='HABIT'">{{ memory.presentation.layout }} / {{ memory.presentation.detail }}</p>
    <details v-if="memory.kind==='SUMMARY'"><summary>原始消息标识（摘要不是证据）</summary><pre>{{ JSON.stringify(memory.source_refs,null,2) }}</pre></details>
    <button v-if="memory.kind==='HABIT'" :disabled="busy" @click="editMemory(memory)">修改习惯</button>
    <button :disabled="busy" @click="removeMemory(memory)">清除（停止使用，保留必要审计）</button>
   </li></ul>
  </details>
 </section>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import type { MedicalSession } from '@/api/medical/protocol'
import type { GruView } from '@/api/medical/gru-records'
import { proposeGruExecution, confirmGruExecution, readGruExecution, recoverGruExecution, listGruExecutions, cancelGruExecution, prepareGruAdoption, readGruOwnerMemory, saveGruOwnerMemory, summarizeGruOwnerMessages, editGruOwnerMemory, removeGruOwnerMemory } from '@/api/medical/gru-execution'
import type { GruExecutionAction, GruExecutionIntent, GruExecutionRequest, GruOwnerMemory, GruOwnerMemories, GruPresentationPreference } from '@/api/medical/gru-execution'
import { readBoundedJson } from '@/api/medical/project-json'
const props=defineProps<{session:MedicalSession;conversation:GruView}>()
const intent=ref<GruExecutionIntent>('RETRIEVE'),text=ref(''),parameters=ref('{}'),busy=ref(false),error=ref(''),confirmed=ref(false),reason=ref(''),cancelReceipt=ref(false)
const action=ref<GruExecutionAction|null>(null),history=ref<GruExecutionAction[]>([]),memoryText=ref(''),memories=ref<GruOwnerMemories|null>(null),editingMemory=ref<GruOwnerMemory|null>(null),memoryLayout=ref<GruPresentationPreference['layout']>('PARAGRAPH'),memoryDetail=ref<GruPresentationPreference['detail']>('FULL')
let controller=new AbortController(),generation=0,pending:{key:string;body:GruExecutionRequest}|null=null
async function work(fn:(signal:AbortSignal)=>Promise<void>){if(busy.value)return;const g=generation;busy.value=true;error.value='';try{await fn(controller.signal)}catch(e){if(g===generation)error.value=e instanceof Error?e.message:'请求失败，保留原参数以恢复'}finally{if(g===generation)busy.value=false}}
function propose(){return work(async signal=>{if(!pending)pending={key:crypto.randomUUID(),body:{schema_version:'1.0.0',intent:intent.value,text:text.value,target_version:props.conversation.document_revision,parameters:intent.value==='RETRIEVE'?{query:text.value}:readBoundedJson(parameters.value) as Record<string,unknown>}};const p=pending;const a=p.body.intent==='ADOPT_EDIT'?await prepareGruAdoption(props.session,props.conversation.conversation_id,p.body.text,p.body.parameters,p.key,signal):await proposeGruExecution(props.session,props.conversation.conversation_id,p.body,p.key,signal);if(signal.aborted)return;action.value=a;confirmed.value=false;pending=null})}
function execute(){if(!action.value||!confirmed.value)return;return work(async signal=>{const a=await confirmGruExecution(props.session,action.value!,signal);if(!signal.aborted){action.value=a;confirmed.value=false}})}
function refresh(){if(!action.value)return;return select(action.value)}
function select(item:GruExecutionAction){return work(async signal=>{const a=await readGruExecution(props.session,item.conversation_id,item.action_id,signal);if(!signal.aborted){action.value=a;confirmed.value=false}})}
function list(){return work(async signal=>{const items=await listGruExecutions(props.session,props.conversation.conversation_id,signal);if(!signal.aborted)history.value=items})}
function recover(){if(!action.value)return;return work(async signal=>{const a=await recoverGruExecution(props.session,action.value!,signal);if(!signal.aborted)action.value=a})}
function cancel(){if(!action.value)return;return work(async signal=>{const a=action.value!;const result=a.current_result as {data?:{revision?:string};run?:{data?:{revision?:string}}}|undefined;const revision=a.state==='PENDING'?a.revision:result?.data?.revision??result?.run?.data?.revision;if(!revision)throw new Error('先重新读取当前运行修订，再请求取消');await cancelGruExecution(props.session,a,revision,reason.value,signal);if(!signal.aborted)cancelReceipt.value=true})}
function saveMemory(){return work(async signal=>{const presentation={layout:memoryLayout.value,detail:memoryDetail.value};const value=editingMemory.value?await editGruOwnerMemory(props.session,props.conversation.conversation_id,editingMemory.value,memoryText.value,presentation,signal):await saveGruOwnerMemory(props.session,props.conversation.conversation_id,'HABIT',memoryText.value,signal,presentation);if(!signal.aborted){memories.value=value;editingMemory.value=null}})}
function editMemory(memory:GruOwnerMemory){editingMemory.value=memory;memoryText.value=memory.content;memoryLayout.value=memory.presentation.layout;memoryDetail.value=memory.presentation.detail}
function removeMemory(memory:GruOwnerMemory){return work(async signal=>{const value=await removeGruOwnerMemory(props.session,props.conversation.conversation_id,memory,signal);if(!signal.aborted){memories.value=value;if(editingMemory.value?.memory_id===memory.memory_id)editingMemory.value=null;if(action.value){const current=await readGruExecution(props.session,action.value.conversation_id,action.value.action_id,signal);if(!signal.aborted)action.value=current}}})}
function loadMemory(){return work(async signal=>{const value=await readGruOwnerMemory(props.session,props.conversation.conversation_id,signal);if(!signal.aborted)memories.value=value})}
function summary(){return work(async signal=>{const value=await summarizeGruOwnerMessages(props.session,props.conversation.conversation_id,signal);if(!signal.aborted)memories.value=value})}
watch(()=>[props.session.user.user_id,JSON.stringify(props.session.current_context),props.conversation.conversation_id],()=>{generation++;controller.abort();controller=new AbortController();action.value=null;history.value=[];pending=null;confirmed.value=false;busy.value=false;error.value='';text.value='';parameters.value='{}';memoryText.value='';memories.value=null;editingMemory.value=null},{flush:'sync'})
onBeforeUnmount(()=>{generation++;controller.abort()})
</script>
<style scoped>
.gru-execution{border:1px solid #ccd8e5;border-radius:8px;padding:12px;margin-block:12px}.gru-execution label{display:block;margin-block:8px}.gru-execution textarea{display:block;width:100%;box-sizing:border-box}.gru-execution pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:360px;overflow:auto}.gru-execution button{margin:4px;padding:6px}.gru-execution [role=alert]{color:#a03932}
</style>
