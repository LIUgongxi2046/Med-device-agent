<template>
  <details class="technical-check">
    <summary>技术结构检查</summary>
    <p>检查已保存正文的结构及内部固定引用。专业覆盖仍为未知（UNKNOWN），技术通过不能推进正式审查或签发。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在核查技术检查信息…</p>
    <template v-if="checked&&!enabled"><p>当前服务器尚未启用技术结构检查。</p><el-button :disabled="busy" @click="reset">重新核查服务</el-button></template>
    <template v-if="enabled">
      <template v-if="pending">
        <p v-if="!receipt">原检查请求结果待核查。不会自动重试或以新请求覆盖。</p>
        <details><summary>原请求与恢复标识</summary><p>{{ pending.key }}</p><pre>{{ JSON.stringify(pending.body,null,2) }}</pre><p>原正文摘要 {{ pending.body_sha256 }}</p></details>
        <el-button :disabled="busy||!canRead" @click="recover">只读查询原操作</el-button>
        <p v-if="recoveryState==='NOT_FOUND'">当前未找到原记录，不能断定原请求未发生；原请求仍保留。</p>
        <p v-if="recoveryState==='PROCESSING'">原操作尚未得到完整结果。这是同步请求留下的未决记录，不代表后台仍在执行；目前没有自动协调入口。</p>
        <el-button v-if="receipt" :disabled="busy" @click="startNext">已保存回执，准备下一次检查</el-button>
      </template>
      <el-form v-else label-position="top">
        <p v-if="dirty">当前存在未保存内容，请先处理草稿，再检查已保存正文。</p>
        <p v-if="!canCreate">当前固定正文尚不具备检查发起条件。</p>
        <el-form-item label="检查理由"><el-input v-model="reason" type="textarea" :rows="3" maxlength="2000" :disabled="busy"/></el-form-item>
        <el-checkbox v-model="confirmed" :disabled="busy">仅检查这份已保存正文的结构与内部固定引用</el-checkbox>
        <el-button type="primary" :disabled="busy||!canCreate||!confirmed" @click="create">发起技术结构检查</el-button>
      </el-form>
      <section v-if="receipt">
        <h4>真实检查回执</h4>
        <p>技术执行已完成；有限技术结果：{{ receipt.technical_result==='PASS'?'通过':'未通过' }}。专业覆盖：未知，整体审查未就绪。</p>
        <p>结果读取另需两个真实产物的读取权限。读取失败不会撤销已存在的检查或自动创建替代检查。</p>
        <el-button :disabled="busy||!canRead" @click="readResult">读取结果与覆盖</el-button>
        <details><summary>固定版本与回执标识</summary><p>正文版本 {{ receipt.document_revision }}</p><p>检查 {{ receipt.check_id }}</p><p>操作 {{ receipt.operation_id }}</p><p>输入摘要 {{ receipt.input_sha256 }}</p></details>
      </section>
      <section v-if="result">
        <h4>{{ result.currentness==='CURRENT'?'当前有效的技术结果':'已失效的技术结果' }}</h4>
        <ul v-if="result.currentness==='STALE'"><li v-for="item in result.invalidation_reasons" :key="item">{{ item }}</li></ul>
        <p>技术输入交付覆盖完整；专业检查覆盖仍未知。</p>
        <p>发现项 {{ result.result.findings.length }} 条，已完整读取，无后续页。</p>
        <ul><li v-for="finding in result.result.findings" :key="finding.code">固定正文未通过结构或内部引用校验。该项不包含专业充分性结论。</li></ul>
        <details><summary>真实覆盖摘要</summary><p>选定输入 {{ result.coverage.selection_sha256 }}</p><p>已交付输入 {{ result.coverage.delivered_sha256 }}</p></details>
      </section>
    </template>
  </details>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import useUserStore from '@/store/modules/user'
import {fail} from '@/api/medical/protocol'
import type {MedicalSession} from '@/api/medical/protocol'
import type {DocumentSnapshot} from '../../../../contracts/typescript/src/interfaces'
import {canonicalDocumentValue as canonical} from '@/api/medical/document-body'
import {sourceDigest} from '@/api/medical/content-digest'
import {technicalAllowed,technicalAvailability,technicalInputIssues,verifyTechnicalPending,createTechnicalCheck,recoverTechnicalCheck,readTechnicalCheck,retainTechnicalPending,restoreTechnicalPending,forgetTechnicalPending} from '@/api/medical/document-technical-check'
import type {TechnicalPending,TechnicalReceipt,TechnicalRead,TechnicalRequest} from '@/api/medical/document-technical-check'
const props=defineProps<{session:MedicalSession;snapshot:DocumentSnapshot;active:boolean;dirty:boolean;isCurrent:boolean}>()
const emit=defineEmits<{completed:[receipt:TechnicalReceipt]}>(),user=useUserStore()
const enabled=ref(false),checked=ref(false),loading=ref(false),error=ref(''),reason=ref(''),confirmed=ref(false),recoveryState=ref('')
const pending=ref<TechnicalPending|null>(null),receipt=ref<TechnicalReceipt|null>(null),result=ref<TechnicalRead|null>(null)
const resultTarget=ref<{project_id:string;document_id:string;document_revision:string;body:{sha256:string}}|null>(null)
const busy=computed(()=>loading.value||user.medicalWriting)
const canRead=computed(()=>enabled.value&&props.active&&technicalAllowed(props.session,props.snapshot,'document.read'))
const canCreate=computed(()=>canRead.value&&props.isCurrent&&!props.dirty&&technicalAllowed(props.session,props.snapshot,'document.check'))
let alive=true,generation=0,controller=new AbortController(),ownsWrite=false
function live(g:number,s:MedicalSession){return alive&&props.active&&!document.hidden&&generation===g&&props.session===s}
function accept(p:TechnicalPending,r:TechnicalReceipt){receipt.value=r;result.value=null;resultTarget.value={project_id:p.project_id,document_id:p.document_id,document_revision:p.body.document_revision,body:{sha256:p.body_sha256}}}
watch(reason,()=>{confirmed.value=false},{flush:'sync'})
async function create(){
 const s=props.session,d=props.snapshot
 if(!canCreate.value||busy.value||pending.value||!confirmed.value||!s.current_context||document.hidden)return
 const body:TechnicalRequest={schema_version:'1.0.0',document_revision:d.document_revision,expected_revision:d.revision,reason:reason.value}
 const issues=technicalInputIssues(body);if(issues.length){error.value=issues.join('\n');return}
 const g=generation;loading.value=true;error.value='';let done:TechnicalReceipt|null=null
 try{
  const original={actor:s.user.user_id,context:{...s.current_context},document_id:d.document_id,project_id:d.project_id,body_sha256:d.body.sha256,key:crypto.randomUUID(),body}
  const p={...original,digest:await sourceDigest(original)}
  if(!live(g,s)||!canCreate.value||user.medicalWriting)return
  retainTechnicalPending(s,p);pending.value=p;receipt.value=null;result.value=null;recoveryState.value=''
  ownsWrite=true;user.medicalWriting=true
  const value=await createTechnicalCheck(s,p,controller.signal)
  if(live(g,s)){accept(p,value);done=value}
 }catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'检查结果不确定，原请求已保留'}
 finally{if(ownsWrite){ownsWrite=false;user.medicalWriting=false}if(live(g,s))loading.value=false}
 if(done&&live(g,s))emit('completed',done)
}
async function recover(){
 const p=pending.value,s=props.session;if(!p||!canRead.value||busy.value||document.hidden)return
 const g=generation;loading.value=true;error.value='';result.value=null;let done:TechnicalReceipt|null=null
 try{const value=await recoverTechnicalCheck(s,p,controller.signal);if(live(g,s)){recoveryState.value=value.state;if(value.receipt){accept(p,value.receipt);done=value.receipt}}}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'原操作读取失败'}
 finally{if(live(g,s))loading.value=false}
 if(done&&live(g,s))emit('completed',done)
}
async function readResult(){
 const s=props.session,r=receipt.value,target=resultTarget.value;if(!r||!target||!canRead.value||busy.value||document.hidden)return
 const g=generation;loading.value=true;error.value='';result.value=null
 try{const value=await readTechnicalCheck(s,target,r,controller.signal);if(live(g,s))result.value=value}catch(e){if(live(g,s))error.value=e instanceof Error?e.message:'结果读取失败'}
 finally{if(live(g,s))loading.value=false}
}
function startNext(){if(busy.value||!pending.value||!receipt.value||!canRead.value)return;try{forgetTechnicalPending(props.session,pending.value.document_id);pending.value=null;reason.value='';confirmed.value=false;recoveryState.value=''}catch(e){error.value=e instanceof Error?e.message:'原回执记录无法归档'}}
function clear(){generation++;controller.abort();controller=new AbortController();enabled.value=false;checked.value=false;loading.value=false;error.value='';reason.value='';confirmed.value=false;pending.value=null;receipt.value=null;result.value=null;resultTarget.value=null;recoveryState.value=''}
async function reset(){
 clear();if(!alive||!props.active||document.hidden||!props.session.current_context)return
 const g=generation,s=props.session;loading.value=true
 try{const value=await technicalAvailability(s,controller.signal);if(!live(g,s))return;enabled.value=value;checked.value=true
  if(value){const p=restoreTechnicalPending(s,props.snapshot.document_id);if(p){await verifyTechnicalPending(s,p);if(p.document_id!==props.snapshot.document_id||p.project_id!==props.snapshot.project_id)fail('CONTENT_MISMATCH','原检查不属于此固定文稿');if(live(g,s))pending.value=p}}
 }catch(e){if(live(g,s)){checked.value=true;error.value=e instanceof Error?e.message:'服务状态读取失败'}}
 finally{if(live(g,s))loading.value=false}
}
watch(()=>[props.session,props.session.user.user_id,canonical(props.session.current_context),canonical(props.session.action_summary),props.snapshot.document_id,props.snapshot.document_revision,props.snapshot.revision,props.snapshot.body.sha256,props.active],reset,{immediate:true,flush:'sync'})
function visibility(){void reset()}function unload(e:BeforeUnloadEvent){if(pending.value||ownsWrite){e.preventDefault();e.returnValue=''}}
document.addEventListener('visibilitychange',visibility);window.addEventListener('beforeunload',unload)
onBeforeUnmount(()=>{alive=false;clear();document.removeEventListener('visibilitychange',visibility);window.removeEventListener('beforeunload',unload)})
</script>
<style scoped>.technical-check{padding:16px;margin-top:24px;border:1px solid #DCE3EC;border-radius:8px;color:#243247}.technical-check summary{cursor:pointer;font-weight:600}.technical-check p{line-height:1.6;overflow-wrap:anywhere}.technical-check pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.technical-check .el-button{margin:8px}.technical-check section{margin-top:16px}@media(max-width:650px){.technical-check{padding:12px}.technical-check .el-button{white-space:normal;height:auto;min-height:32px}}</style>
