<template>
  <section class="revision-queue" aria-label="我的返修任务">
    <div class="queue-heading"><h2>我的返修任务</h2><el-button :disabled="busy||!enabled" @click="load()">从首页刷新</el-button></div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取当前获授权的返修分派…</p>
    <template v-if="checked&&!enabled"><p v-if="!error">当前服务器尚未启用文稿退回修改。</p><el-button :disabled="busy" @click="reset">重新核查服务状态</el-button></template>
    <template v-if="enabled">
      <p class="queue-note">这里是分派给你的文稿修改事项，与自动运行的等待项分别记录。</p>
      <p v-if="!page&&!loading">点击“从首页刷新”读取真实返修任务。</p>
      <template v-if="page">
        <p>本页 {{ page.items.length }} 条获授权分派<span v-if="page.complete"> · 本轮分页结束，刷新首页可查看新增事项</span></p>
        <p v-if="page.items.length===0">本页没有可显示的返修分派<span v-if="page.next_cursor">，后续仍有候选页，可继续读取</span>。</p>
        <article v-for="item in page.items" :key="item.decision.assignment.assignment_id" class="revision-item">
          <header><strong>{{ states[item.view.effective_state] }}</strong><span>{{ item.decision.decided_at }}</span></header>
          <pre>{{ item.decision.reason }}</pre>
          <p v-if="item.view.effective_state!=='OPEN'">正文或任务状态已经变化，这不表示返修已完成。</p>
          <p>当前任务：{{ taskStates[item.view.task_status] }}</p>
          <router-link :to="revisionWorkspacePath(item)">打开当前撰写工作区</router-link>
          <p><router-link :to="{path:revisionWorkspacePath(item),query:{history_revision:item.decision.document_revision}}">查看退回时的原固定版本</router-link></p>
          <p class="queue-note">撰写入口打开当前正文；原版本入口需要在工作区明确点击读取并通过权限核验。退回意见对应的原固定版本见下方，不能将当前正文当作原文。</p>
          <details><summary>原版本与真实分派记录</summary><dl><dt>原正文版本</dt><dd>{{ item.decision.document_revision }}</dd><dt>当前正文版本</dt><dd>{{ item.view.current_document_revision }}</dd><dt>原任务版本</dt><dd>{{ item.decision.assignment.task_revision }}</dd><dt>分派标识</dt><dd>{{ item.decision.assignment.assignment_id }}</dd></dl></details>
          <p class="queue-note">当前检查有效性尚未具备核验能力，暂不能重新提交正式审查；没有完成或签发操作。</p>
        </article>
        <el-button v-if="page.next_cursor" :disabled="busy" @click="load(page.next_cursor)">读取下一页</el-button>
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import useUserStore from '@/store/modules/user'
import type {MedicalSession} from '@/api/medical/protocol'
import {canonicalDocumentValue} from '@/api/medical/document-body'
import {reviewAvailability} from '@/api/medical/document-review-return'
import {readRevisionQueue,revisionWorkspacePath} from '@/api/medical/document-revision-assignment-queue'
import type {RevisionQueuePage,RevisionQueueItem} from '@/api/medical/document-revision-assignment-queue'
const props=defineProps<{session:MedicalSession;active:boolean}>(),user=useUserStore()
const page=ref<RevisionQueuePage|null>(null),enabled=ref(false),checked=ref(false),loading=ref(false),error=ref('')
const busy=computed(()=>loading.value||user.medicalWriting)
const states:Record<RevisionQueueItem['view']['effective_state'],string>={OPEN:'待修改',REVISION_AVAILABLE:'已有新正文版本',INACTIVE:'已非当前处理分派'}
const taskStates:Record<RevisionQueueItem['view']['task_status'],string>={NEEDS_MATERIALS:'需要资料',PLAN_PENDING:'计划待确认',READY:'已就绪',RUNNING:'执行中',REVIEW_PENDING:'待审查',SIGN_PENDING:'待签发',SIGNED:'已签发',FAILED:'失败',RETURNED:'已退回修改',CANCEL_REQUESTED:'取消处理中',CANCELLED:'已取消'}
let generation=0,alive=true,controller=new AbortController()
function live(g:number,s:MedicalSession){return alive&&props.active&&!document.hidden&&g===generation&&props.session===s}
async function load(cursor?:string){if(!enabled.value||!props.active||busy.value||document.hidden)return;if(cursor!==undefined&&cursor!==page.value?.next_cursor)return;const g=generation,s=props.session;page.value=null;loading.value=true;error.value='';try{const value=await readRevisionQueue(s,controller.signal,cursor);if(!live(g,s))return;if(value.kind==='EXPIRED'){error.value='分页已过期，请从首页重新读取。';return}page.value=value.page}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'返修分派读取失败，请从首页重读'}finally{if(live(g,s))loading.value=false}}
function clear(){generation++;controller.abort();controller=new AbortController();page.value=null;enabled.value=false;checked.value=false;loading.value=false;error.value=''}
async function reset(){clear();if(!alive||!props.active||document.hidden||!props.session.current_context)return;const g=generation,s=props.session;loading.value=true;try{const available=await reviewAvailability(s,controller.signal);if(live(g,s)){enabled.value=available;checked.value=true}}catch(e){if(live(g,s)){checked.value=true;error.value=e instanceof Error?e.message:'服务状态读取失败'}}finally{if(live(g,s))loading.value=false}if(live(g,s)&&enabled.value)await load()}
watch(()=>[props.session,canonicalDocumentValue(props.session.current_context),props.active],reset,{immediate:true,flush:'sync'})
function visibility(){void reset()}document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{alive=false;clear();document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>.revision-queue{padding:24px;background:#fff;border:1px solid #DCE3EC;border-radius:8px;color:#243247}.queue-heading,.revision-item header{display:flex;gap:16px;align-items:center;justify-content:space-between}.queue-heading h2{font-size:17px;margin:0}.queue-note{color:#526279;font-size:13px;line-height:1.6}.revision-item{padding:16px 0;border-top:1px solid #E9EDF3}.revision-item header span{color:#526279;font-size:12px}.revision-item pre{font:inherit;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6}.revision-item dd{margin:4px 0 12px;overflow-wrap:anywhere}.revision-item dt{font-size:12px;color:#526279}.revision-item a{color:#175FAE}.revision-item summary{cursor:pointer}.revision-queue .el-button{margin:8px 0}@media(max-width:650px){.revision-queue{padding:16px}.queue-heading,.revision-item header{align-items:flex-start;flex-direction:column;gap:8px}}</style>
