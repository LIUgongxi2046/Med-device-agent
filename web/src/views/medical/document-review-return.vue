<template>
  <details class="review-return" ref="panel">
    <summary>文稿退回修改</summary>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在核查固定版本的审查信息…</p>
    <template v-if="checked&&!enabled"><p v-if="!error">当前服务器尚未启用审查退回。</p><el-button :disabled="busy" @click="reset">重新核查</el-button></template>
    <template v-if="enabled">
      <p>这是一项独立的退回修改操作，不代表正式审查确认或批准。只针对已保存正文；现有选区、批注和草稿不会被改动。</p>
      <el-button :disabled="busy" @click="loadHistory">读取退回历史</el-button><el-button :disabled="busy||!canReadAssignments" @click="loadAssignments">读取退回分派</el-button>
      <p v-if="!knownTaskId">读取退回依据或历史后，可核查真实关联任务的分派读取权限。</p>
      <section v-if="pending">
        <h4>原退回请求待核查</h4><p>结果尚不确定。保留完整原意见与责任人，不会自动重复提交。</p>
        <details><summary>原请求与恢复标识</summary><p>{{ pending.key }}</p><pre>{{ JSON.stringify(pending.body,null,2) }}</pre></details>
        <el-button :disabled="busy" @click="recover">查询原退回回执</el-button><p v-if="lookupStatus">{{ lookupStatus==='NOT_FOUND'?'当前未找到原记录，不能据此断定未发生。':lookupStatus }}</p>
        <el-checkbox v-model="confirmed" :disabled="busy">我已复核完整原请求，明确按原标识重放</el-checkbox>
        <el-button :disabled="busy||!canDecide||!sameContext||!confirmed||lookupStatus!=='NOT_FOUND'" @click="send">按原请求重放退回</el-button>
      </section>
      <template v-else>
        <p v-if="!canDecide">当前没有此正文的退回修改权限，可以继续阅读有权访问的历史。</p>
        <el-button :disabled="busy||!canDecide" @click="loadBasis">核查退回条件</el-button>
        <section v-if="basis&&canDecide">
          <h4>{{ ready?'退回条件已就绪':'暂不能退回' }}</h4>
          <ul v-if="basis.blockers.length"><li v-for="blocker in basis.blockers" :key="blocker">{{ blockers[blocker] }}</li></ul>
          <p>正式检查的当前有效性尚未核验。可以要求补充资料，但不能据此确认审查、签发或重新提交审查。</p>
          <p v-if="dirty">正文存在未保存编辑，请先处理草稿，再针对已保存版本提交退回修改。</p>
          <details><summary>检查依据（{{ basis.checks.length }} 条真实记录）</summary><ul><li v-for="check in basis.checks" :key="check.check_id">{{ check.record_complete?'检查记录完整（未核验当前性）':'检查记录不完整' }} · {{ check.status }}<span v-if="check.invalidation_reason"> · {{ check.invalidation_reason }}</span></li></ul><p>本页不提供审查确认或签发。</p></details>
          <el-form label-position="top" :disabled="busy||!ready">
            <el-form-item label="需要补充或修改的意见"><el-input v-model="reason" type="textarea" :rows="5" maxlength="16000" placeholder="明确指出需要补充的内容与原因"/></el-form-item>
            <el-form-item label="负责补充的成员"><el-select v-model="assignee" placeholder="从真实授权成员中选择"><el-option v-for="member in assignees?.items??[]" :key="member.user_id" :value="member.user_id" :label="'成员 '+member.user_id"/></el-select></el-form-item>
            <p v-if="assignees">目录尚未提供姓名；以上为真实成员账号标识，不会猜测显示名称。</p>
            <p v-if="assignees?.items.length===0">没有可分派成员，暂不能退回。</p>
            <el-checkbox v-model="confirmed">确认退回这份已保存正文，按以上意见分派补充</el-checkbox>
            <el-button type="primary" :disabled="busy||!ready||!confirmed||!assignee" @click="prepare">退回修改</el-button>
            <el-button :disabled="busy" @click="discardInputs">放弃未提交意见</el-button>
          </el-form>
        </section>
      </template>
      <section v-if="receipt"><h4>退回决定已保存</h4><pre>{{ receipt.decision.reason }}</pre><p>回执中的任务状态：RETURNED。当前任务与分派状态请以重新读取结果为准。</p><details><summary>回执标识</summary><p>操作 {{ receipt.operation_id }} · 分派 {{ receipt.decision.assignment.assignment_id }}</p></details></section>
      <section v-if="history"><h4>退回修改历史</h4><p v-if="history.items.length===0">此固定版本暂无退回修改记录。</p><article v-for="item in history.items" :key="item.decision.return_id"><strong>退回修改</strong><p>{{ item.decision.decided_at }}</p><pre>{{ item.decision.reason }}</pre><details><summary>相关状态变更</summary><p>原审查确认失效记录：{{ item.invalidated_review_ids.length }} 条。</p><p>退回标识 {{ item.decision.return_id }}</p></details></article></section>
      <document-review-assignments v-if="assignments" :rows="assignments.items"/>
    </template>
  </details>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import useUserStore from '@/store/modules/user'
import type {MedicalSession} from '@/api/medical/protocol'
import type {DocumentSnapshot} from '../../../../contracts/typescript/src/interfaces'
import {canonicalDocumentValue} from '@/api/medical/document-body'
import {sourceDigest} from '@/api/medical/content-digest'
import {reviewAllowed,reviewAssignmentsAllowed,reviewAvailability,readReviewBasis,readReviewHistory,readReviewAssignments,readReviewAssignees,reviewInputIssues,verifyReviewPending,writeReviewReturn,recoverReviewReturn,retainReview,restoreReview,forgetReview} from '@/api/medical/document-review-return'
import type {ReviewBasis,ReviewHistory,ReviewAssignments,ReviewAssignees,ReviewReceipt,ReviewRequest,PendingReviewReturn} from '@/api/medical/document-review-return'
import {matchesReturnHandoff} from '@/api/medical/document-review-workspace'
import type {LocalReturnHandoff} from '@/api/medical/document-review-workspace'
import DocumentReviewAssignments from './document-review-assignments.vue'
const props=defineProps<{session:MedicalSession;snapshot:DocumentSnapshot;active:boolean;dirty:boolean;taskId?:string|undefined}>()
const emit=defineEmits<{returned:[receipt:ReviewReceipt]}>(),user=useUserStore()
const panel=ref<HTMLDetailsElement|null>(null)
function openFromReview(handoff?:LocalReturnHandoff,contextKey='') {
  if(panel.value){panel.value.open=true;panel.value.scrollIntoView?.({block:'nearest',behavior:'smooth'})}
  if(!handoff)return true
  let retainedPending:PendingReviewReturn|null=null
  try{retainedPending=restoreReview(props.session,props.snapshot.document_id)}catch{error.value='未复制草稿：原退回恢复记录无法核验，请先处理恢复状态。';return false}
  if(!canDecide.value||busy.value||props.dirty||document.hidden||pending.value||retainedPending){
    error.value='未复制草稿：请先核查权限、版本及原请求恢复状态。原请求和已有意见保持不变。';return false
  }
  const expectedContextKey=canonicalDocumentValue([props.session.user.user_id,props.session.current_context])
  if(contextKey!==expectedContextKey||!matchesReturnHandoff(handoff,props.snapshot,expectedContextKey)){
    error.value='未复制草稿：固定版本或当前上下文已经变化，请重新核对。';return false
  }
  if(reason.value||assignee.value){error.value='未复制草稿：已有未提交意见或责任人，请先处理或明确放弃已有输入。';return false}
  reason.value=handoff.reason;confirmed.value=false;error.value='';return true
}
defineExpose({openFromReview})
const enabled=ref(false),checked=ref(false),loading=ref(false),error=ref(''),reason=ref(''),assignee=ref(''),confirmed=ref(false),lookupStatus=ref('')
const basis=ref<ReviewBasis|null>(null),history=ref<ReviewHistory|null>(null),assignments=ref<ReviewAssignments|null>(null),assignees=ref<ReviewAssignees|null>(null),pending=ref<PendingReviewReturn|null>(null),receipt=ref<ReviewReceipt|null>(null)
const busy=computed(()=>loading.value||user.medicalWriting),canDecide=computed(()=>enabled.value&&props.active&&reviewAllowed(props.session,props.snapshot))
const knownTaskId=computed(()=>props.taskId??basis.value?.task_id??history.value?.items[0]?.decision.assignment.task_id??null)
const canReadAssignments=computed(()=>enabled.value&&props.active&&reviewAssignmentsAllowed(props.session,props.snapshot.project_id,knownTaskId.value))
const ready=computed(()=>canDecide.value&&!props.dirty&&basis.value?.return_available===true&&basis.value.blockers.length===0)
const sameContext=computed(()=>!!pending.value&&canonicalDocumentValue(pending.value.context)===canonicalDocumentValue(props.session.current_context))
const blockers:Record<ReviewBasis['blockers'][number],string>={DOCUMENT_NOT_CURRENT:'这不是当前正文版本',PROJECT_NOT_ACTIVE:'项目当前未启用',DOCUMENT_SIGNED:'正文已经签发',TASK_NOT_REVIEW_PENDING:'任务尚未处于待审查阶段',TASK_REVISION_CHANGED:'关联任务版本已经变化'}
let generation=0,alive=true,controller=new AbortController(),ownsWrite=false
function live(g:number,s:MedicalSession){return alive&&props.active&&!document.hidden&&g===generation&&props.session===s}
async function read<T>(work:(s:MedicalSession,signal:AbortSignal)=>Promise<T>,apply:(v:T)=>void){if(!enabled.value||!props.active||busy.value||document.hidden)return;const g=generation,s=props.session;loading.value=true;error.value='';try{const value=await work(s,controller.signal);if(live(g,s))apply(value)}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'审查信息读取失败'}finally{if(live(g,s))loading.value=false}}
function loadHistory(){return read((s,signal)=>readReviewHistory(s,props.snapshot,signal),v=>{history.value=v})}
function loadAssignments(){if(!canReadAssignments.value)return;return read((s,signal)=>readReviewAssignments(s,props.snapshot,signal),v=>{assignments.value=v})}
function loadBasis(){if(!canDecide.value||pending.value||busy.value)return;basis.value=null;assignees.value=null;confirmed.value=false;return read(async(s,signal)=>{const b=await readReviewBasis(s,props.snapshot,signal);const members=b.return_available?await readReviewAssignees(s,props.snapshot,signal):null;return {b,members}},v=>{basis.value=v.b;assignees.value=v.members})}
watch([reason,assignee],()=>{confirmed.value=false},{flush:'sync'})
async function prepare(){const b=basis.value,s=props.session;if(!ready.value||!b||!s.current_context||busy.value||pending.value||!confirmed.value)return;if(!assignees.value?.items.some(item=>item.user_id===assignee.value)){error.value='请选择当前真实目录中的责任成员';return}const body:ReviewRequest=JSON.parse(JSON.stringify({document_revision:b.document_revision,expected_revision:b.revision,expected_task_revision:b.task_head_revision,reason:reason.value,assignee_id:assignee.value,basis:b}));const issues=reviewInputIssues(body);if(issues.length){error.value=issues.join('\n');return}const g=generation;loading.value=true;try{const original={actor:s.user.user_id,context:{...s.current_context},key:crypto.randomUUID(),body};const digest=await sourceDigest(original);if(!live(g,s))return;const p={...original,digest};retainReview(s,props.snapshot.document_id,p);pending.value=p;lookupStatus.value=''}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'无法保留原退回请求'}finally{if(live(g,s))loading.value=false}if(pending.value&&live(g,s))await send()}
function completed(s:MedicalSession,r:ReviewReceipt){receipt.value=r;forgetReview(s,props.snapshot.document_id);pending.value=null;basis.value=null;assignees.value=null;history.value=null;assignments.value=null;reason.value='';assignee.value='';confirmed.value=false}
async function send(){const p=pending.value,s=props.session;if(!p||!canDecide.value||busy.value||!sameContext.value||!confirmed.value||document.hidden)return;const g=generation;let completedReceipt:ReviewReceipt|null=null;ownsWrite=true;user.medicalWriting=true;error.value='';try{const value=await writeReviewReturn(s,p,controller.signal);if(live(g,s)){completed(s,value);completedReceipt=value}}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'退回结果未知，原请求已保留'}finally{if(ownsWrite){ownsWrite=false;user.medicalWriting=false}}if(completedReceipt&&live(g,s))emit('returned',completedReceipt)}
async function recover(){const p=pending.value;if(!p||!enabled.value||!props.active)return;const g=generation,s=props.session;let completedReceipt:ReviewReceipt|null=null;await read((s,signal)=>recoverReviewReturn(s,p,signal),v=>{lookupStatus.value=v?'FOUND':'NOT_FOUND';if(v){completed(s,v);completedReceipt=v}});if(completedReceipt&&live(g,s))emit('returned',completedReceipt)}
function discardInputs(){reason.value='';assignee.value='';confirmed.value=false}
// Only unsent inputs survive a temporary UI suspension, never its server basis or consent.
const inputScope=computed(()=>canonicalDocumentValue([props.session.user.user_id,props.session.current_context,props.snapshot.project_id,props.snapshot.document_id,props.snapshot.document_revision,props.snapshot.body.sha256]))
let retainedScope:string|null=null
function clear(preserveInputs=false){generation++;controller.abort();controller=new AbortController();enabled.value=false;checked.value=false;loading.value=false;basis.value=null;history.value=null;assignments.value=null;assignees.value=null;pending.value=null;receipt.value=null;if(!preserveInputs)discardInputs();confirmed.value=false;lookupStatus.value='';error.value=''}
async function reset(){const scope=inputScope.value;clear(retainedScope===scope&&reviewAllowed(props.session,props.snapshot));retainedScope=scope;if(!alive||!props.active||document.hidden||!props.session.current_context)return;const g=generation,s=props.session;loading.value=true;try{const available=await reviewAvailability(s,controller.signal);if(!live(g,s))return;enabled.value=available;checked.value=true;if(available){const p=restoreReview(s,props.snapshot.document_id);if(p){await verifyReviewPending(s,p,false);if(live(g,s))pending.value=p}}}catch(e){if(live(g,s)){checked.value=true;error.value=e instanceof Error?e.message:'服务状态读取失败'}}finally{if(live(g,s))loading.value=false}}
watch(()=>[props.session,inputScope.value,props.active],reset,{immediate:true,flush:'sync'})
watch(()=>reviewAllowed(props.session,props.snapshot),allowed=>{if(!allowed){generation++;controller.abort();controller=new AbortController();loading.value=false;discardInputs();basis.value=null;assignees.value=null}},{flush:'sync'})
function invalidate(){retainedScope=null;clear()}
function visibility(){void reset()}function unload(e:BeforeUnloadEvent){if(reason.value||assignee.value||pending.value||ownsWrite){e.preventDefault();e.returnValue=''}}
document.addEventListener('visibilitychange',visibility);window.addEventListener('beforeunload',unload);window.addEventListener('medical-context-invalidated',invalidate)
onBeforeUnmount(()=>{alive=false;clear();document.removeEventListener('visibilitychange',visibility);window.removeEventListener('beforeunload',unload);window.removeEventListener('medical-context-invalidated',invalidate)})
</script>
<style scoped>.review-return{padding:16px;margin-top:24px;border:1px solid #DCE3EC;border-radius:8px;color:#243247}.review-return summary{font-weight:600;cursor:pointer}.review-return pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.6}.review-return p{overflow-wrap:anywhere}.review-return .el-button{margin:8px}.review-return .el-select{width:100%}.review-return article{padding:12px 0;border-top:1px solid #E9EDF3}@media(max-width:650px){.review-return{padding:12px}.review-return .el-button{max-width:100%;height:auto;min-height:32px;white-space:normal}}</style>
