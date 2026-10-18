<template>
 <fieldset class="formal-input" :disabled="disabled || busy" aria-label="正式评测输入选择">
  <legend>正式评测输入（079）</legend>
  <p>选择真实企业来源，登记固定文稿，核验原文后用于本样本。登记不会形成分数、预期集、REAL声明或验收通过结论。</p>
  <p v-if="availability" role="status">{{ availability.enabled ? '正式输入登记服务已启用' : '当前部署未启用正式输入登记服务' }} · 企业范围 · 原件上限 2 MiB</p>
  <el-button :disabled="disabled || busy" @click="refresh">读取实际部署与原登记</el-button>
  <p v-if="error" role="alert">{{ error }}</p>
  <details><summary>需要什么来源文稿？</summary><p>先通过真实来源上传、完成、审批和权利流程准备企业 SOURCE。原件须为规范 UTF-8 JSON：文档类型、语言和1至256个唯一章节，每章具有标识、标题、非空正文。原件必须满足正式输入schema，不能使用085项目输入或仿真成果替代。</p><router-link to="/sources/uploads">进入真实来源上传</router-link><p>来源目录仅提供候选元数据；登记时服务端才读取原件并核验完整依赖和用途权限。</p></details>
  <details><summary>按字段准备规范文稿文件</summary><formal-evaluation-document-editor :disabled="disabled || busy"/></details>
  <h4>选择来源并登记</h4>
   <el-form-item label="明确登记用途"><el-input v-model="purpose" maxlength="64" placeholder="输入实际批准用途，不从权利自动推断" :disabled="disabled || busy"/></el-form-item>
  <el-button :disabled="disabled || busy || !purpose" @click="loadSources(false)">读取企业来源候选</el-button>
  <el-select v-model="sourceId" filterable placeholder="选择已批准、有效的企业来源" :disabled="disabled || busy"><el-option v-for="s in sources" :key="s.source.version_id" :value="s.source.version_id" :label="s.source.version_id+' · '+s.media_type+' · '+s.size_bytes+'字节'"/></el-select>
  <el-button v-if="sourceCursor" :disabled="disabled || busy" @click="loadSources(true)">更多来源</el-button>
  <el-button :disabled="disabled || busy || !sourceId" @click="loadSource">读取所选来源固定依据</el-button>
  <template v-if="source">
   <dl><dt>来源固定版本</dt><dd>{{ source.source_version }}</dd><dt>原件SHA-256</dt><dd>{{ source.original_sha256 }}</dd><dt>当前权利版本</dt><dd>{{ source.rights_revision }}</dd><dt>实际权利用途（供核查，不自动选择）</dt><dd>{{ source.rights_purpose }}</dd></dl>

   <el-checkbox v-model="attested" :disabled="disabled || busy">确认读取所选真实原件，校验并登记独立正式评测输入；不创建权限或运行评测。</el-checkbox>
   <el-button type="primary" :disabled="disabled || busy || !availability?.enabled || !attested || !purpose || purpose!==source.rights_purpose || !!existingPending" @click="register">校验原件并登记正式输入</el-button>
   <p v-if="existingPending">此来源用途已有原请求，请使用下方原键读取，不能重新登记。</p>
  </template>
  <h4>原登记与未知结果恢复</h4>
  <p v-if="!pending.length">当前身份没有本机保留的原登记。</p>
  <div v-for="p in pending" :key="p.key" class="pending"><p>原键：{{ p.key }} · 用途：{{ p.body.purpose }} · {{ p.receipt ? '已收到登记回执，仍需读取核验原文' : '结果未知，只能读取原操作' }}</p><el-button :disabled="disabled || busy" @click="recover(p)">读取原键结果</el-button><el-button v-if="p.receipt" :disabled="disabled || busy" @click="read(p.receipt.artifact,p)">读取原输入详情</el-button></div>
  <h4>选择已有正式输入</h4>
  <el-button :disabled="disabled || busy" @click="loadArtifacts(false)">读取企业正式输入成果</el-button><el-select v-model="artifactId" :disabled="disabled || busy" placeholder="选择真实 FORMAL_EVALUATION_INPUT"><el-option v-for="a in artifacts" :key="a.artifact_id" :value="a.artifact_id" :label="a.artifact_id"/></el-select><el-button v-if="artifactCursor" :disabled="disabled || busy" @click="loadArtifacts(true)">更多成果</el-button><el-button :disabled="disabled || busy || !artifactId" @click="readSelected">读取并核验所选文稿</el-button>
  <p v-if="status" role="status">{{ status }}</p>
  <section v-if="view" class="document" aria-label="正式输入原文详情"><h4>{{ view.document.document_type }} · {{ view.document.language }}</h4><p>固定输入：{{ view.receipt.artifact.version_id }} · 原用途：{{ view.receipt.purpose }}</p><details v-for="section in view.document.sections" :key="section.section_id"><summary>{{ section.section_id }} · {{ section.title }}</summary><p class="section-text">{{ section.text }}</p></details><el-button type="primary" :disabled="disabled || busy" @click="apply">将核验后的固定输入用于本样本</el-button></section>
  <p v-if="modelValue.version_id" class="selected">当前样本输入：{{ modelValue.version_id }} · {{ modelValue.content_sha256 }}</p>
 </fieldset>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import FormalEvaluationDocumentEditor from './formal-evaluation-document-editor.vue'
import useUserStore from '@/store/modules/user'
import type { Basis, MedicalSession } from '@/api/medical/protocol'
import type { SourceGovernanceView, ArtifactCatalogueItem } from '@/api/medical/source-governance'
import { formalInputCanonical, readFormalInputAvailability, listFormalInputSources, readFormalInputSource, registerFormalInput, readPendingFormalInputs, verifyPendingFormalInput, recoverFormalInput, readFormalInput, listFormalInputArtifacts } from '@/api/medical/formal-evaluation-input'
import type { FormalInputAvailability, FormalInputRead, PendingFormalInput, FormalInputSourceCandidate } from '@/api/medical/formal-evaluation-input'
const props=defineProps<{modelValue:Basis;disabled?:boolean}>(),emit=defineEmits<{'update:modelValue':[value:Basis];documentType:[value:string]}>()
const user=useUserStore(),loading=ref(false),error=ref(''),status=ref(''),availability=ref<FormalInputAvailability|null>(null),pending=ref<PendingFormalInput[]>([]),sources=ref<FormalInputSourceCandidate[]>([]),sourceCursor=ref<string|null>(null),sourceId=ref(''),source=ref<SourceGovernanceView|null>(null),purpose=ref(''),attested=ref(false),artifacts=ref<ArtifactCatalogueItem[]>([]),artifactCursor=ref<string|null>(null),artifactId=ref(''),view=ref<FormalInputRead|null>(null)
const busy=computed(()=>loading.value||user.medicalWriting),existingPending=computed(()=>pending.value.find(p=>p.body.source.version_id===sourceId.value&&p.body.purpose===purpose.value))
let controller=new AbortController(),generation=0,mounted=true,ownsWrite=false
const reader=():MedicalSession=>{if(!user.medicalSession?.current_context)throw Error('请选择企业');return user.medicalSession}
function invalidateRequest(){controller.abort();controller=new AbortController();generation++;loading.value=false;view.value=null;attested.value=false;status.value=''}
function clear(){invalidateRequest();availability.value=null;pending.value=[];sources.value=[];sourceId.value='';source.value=null;sourceCursor.value=null;purpose.value='';artifacts.value=[];artifactId.value='';artifactCursor.value=null;error.value=''}
async function run(work:(signal:AbortSignal)=>Promise<void>,write=false){if(props.disabled||busy.value||document.hidden)return;const s=reader(),g=generation;loading.value=true;error.value='';if(write){ownsWrite=true;user.medicalWriting=true}try{await work(controller.signal)}catch(e){if(mounted&&generation===g&&user.medicalSession===s&&!controller.signal.aborted)error.value=e instanceof Error?e.message:'读取失败'}finally{if(write&&ownsWrite){ownsWrite=false;user.medicalWriting=false}if(g===generation)loading.value=false}}
async function refresh(){await run(async signal=>{const a=await readFormalInputAvailability(reader,signal),rows=readPendingFormalInputs(reader());for(const p of rows)await verifyPendingFormalInput(reader(),p);signal.throwIfAborted();availability.value=a;pending.value=rows})}
async function loadSources(more=false){await run(async signal=>{const old=sourceCursor.value,page=await listFormalInputSources(reader,purpose.value,more?old!: '0',signal);signal.throwIfAborted();if(more&&(page.next_offset===old||page.items.some(i=>sources.value.some(s=>s.source.version_id===i.source.version_id))))throw Error('来源分页未前进，请重新读取');sources.value=more?[...sources.value,...page.items]:page.items;sourceCursor.value=page.next_offset;if(!sources.value.length)status.value='本页没有可登记候选；如有更多页请继续读取。候选不代表原文已验证。'})}
async function loadSource(){const id=sourceId.value;if(!id)return;source.value=null;attested.value=false;await run(async signal=>{const v=await readFormalInputSource(reader,id,signal);signal.throwIfAborted();if(id===sourceId.value)source.value=v})}
function replace(p:PendingFormalInput){pending.value=[...pending.value.filter(x=>x.key!==p.key),p]}
async function register(){const s=source.value;if(!s||!attested.value||!availability.value?.enabled||existingPending.value)return;const selectedPurpose=purpose.value;await run(async signal=>{const p=await registerFormalInput(reader,s,selectedPurpose,signal,p=>{replace(p);attested.value=false});signal.throwIfAborted();replace(p);status.value='登记回执已核验。请读取真实输入文稿，再明确用于本样本。'},true)}
async function recover(p:PendingFormalInput){await run(async signal=>{const result=await recoverFormalInput(reader,p,signal);signal.throwIfAborted();replace(result.pending);view.value=result.view;status.value=result.view?'原操作与实际文稿已核验。':result.lookup.lookup_status==='EXPIRED'?'原键期限已过，保留原记录供人工核查。':result.lookup.lookup_status==='FOUND'?'原操作尚未完成，只读核查。':'未找到原操作，保留原键，不能换键重发。'})}
async function read(basis:Basis,p?:PendingFormalInput){view.value=null;await run(async signal=>{const v=await readFormalInput(reader,basis,signal,p);signal.throwIfAborted();view.value=v;status.value='独立正式输入身份、契约及实际原文摘要已核验；不代表评测批准。'})}
async function loadArtifacts(more=false){await run(async signal=>{const old=artifactCursor.value,page=await listFormalInputArtifacts(reader,more?old:null,signal);signal.throwIfAborted();if(more&&(page.next_after===old||page.items.some(i=>artifacts.value.some(a=>a.artifact_id===i.artifact_id))))throw Error('成果分页未前进，请重新读取');artifacts.value=more?[...artifacts.value,...page.items]:page.items;artifactCursor.value=page.next_after;if(!artifacts.value.length)status.value='此页暂无企业正式输入成果，可继续翻页或登记真实来源。'})}
async function readSelected(){const a=artifacts.value.find(a=>a.artifact_id===artifactId.value);if(a)await read({version_id:a.artifact_id,content_sha256:a.sha256})}
function apply(){if(!view.value||props.disabled||busy.value||document.hidden)return;emit('update:modelValue',{...view.value.receipt.artifact});emit('documentType',view.value.document.document_type)}
watch(sourceId,()=>{invalidateRequest();source.value=null},{flush:'sync'})
watch(purpose,()=>{invalidateRequest();sources.value=[];sourceCursor.value=null;sourceId.value='';source.value=null},{flush:'sync'})
watch(artifactId,invalidateRequest,{flush:'sync'})
watch(()=>[user.medicalSession,formalInputCanonical(user.medicalSession?.current_context??null)],()=>{clear();if(user.medicalSession?.current_context&&!document.hidden)void refresh()},{immediate:true,flush:'sync'})
function visibility(){clear();if(!document.hidden&&user.medicalSession?.current_context)void refresh()}
document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{mounted=false;clear();document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>.formal-input{border:1px solid #DCE3EC;border-radius:6px;padding:16px;margin:12px 0;min-width:0}.formal-input p{line-height:1.6}.formal-input dd,.selected,.pending{overflow-wrap:anywhere}.formal-input dd{margin:0 0 8px;font-family:monospace}.el-select{width:100%;margin:12px 0}.pending{border-top:1px solid #DCE3EC;padding:12px 0}.section-text{white-space:pre-wrap;overflow-wrap:anywhere}.el-button{margin:8px 8px 8px 0}</style>
