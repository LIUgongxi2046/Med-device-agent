<template>
  <section class="binding-publisher">
    <h2>框架绑定清单发布</h2>
    <el-alert title="草稿不会启用功能。独立批准、真实制品登记及部署配置全部完成后才能消费；不会自动产生源码提交或审批。" type="info" :closable="false" />
    <p role="status">{{ message }}</p>
    <el-button :disabled="busy" @click="loadCatalogue">读取当前固定动作及配置摘要</el-button>
    <el-collapse v-if="catalogue"><el-collapse-item title="查看服务器动作清单和安全配置"><pre>{{ JSON.stringify(catalogue, null, 2) }}</pre></el-collapse-item></el-collapse>
    <label>真实环境 UUID<el-input v-model="environmentId" :disabled="locked" /></label>
    <label>实际部署源码 commit<el-input v-model="sourceCommit" :disabled="locked" /></label>
    <el-button :disabled="locked || !catalogue" @click="prepare">准备十八模块 UNBOUND 草稿，不启用任何操作</el-button>
    <label>Manifest JSON<el-input v-model="editor" type="textarea" :rows="16" :disabled="locked" /></label>
    <el-button :disabled="locked || !editor.trim()" @click="saveDraft">保存不可变草稿</el-button>
    <label>读取已保存的 release UUID<el-input v-model="releaseId" :disabled="busy" /></label>
    <el-button :disabled="busy || !releaseId" @click="readRelease">读取固定版本</el-button>
    <div v-if="record">
      <p>{{ record.release_id }} · {{ record.status }}</p><p>SHA-256：{{ record.manifest_sha256 }}</p>
      <el-button :disabled="busy" @click="download">下载原始规范化 JSON</el-button>
      <p>下载不是制品登记。请通过现有企业级制品流程登记这些原始字节，并提供真实 artifact ID。</p>
      <label>真实 manifest artifact UUID<el-input v-model="artifactId" :disabled="locked" /></label>
      <label>批准或撤销理由<el-input v-model="reason" maxlength="2000" :disabled="locked" /></label>
      <el-button :disabled="locked || record.status !== 'DRAFT' || isAuthor || !artifactId || !reason.trim()" @click="approve">由当前独立管理员批准</el-button>
      <el-button type="danger" :disabled="locked || record.status !== 'ACTIVE' || !reason.trim()" @click="revoke">撤销此绑定发布</el-button>
      <p v-if="isAuthor">当前账户是作者，不能批准自己的版本。</p>
    </div>
    <el-alert v-if="pending" title="上次提交结果尚未核对。保留原请求身份，不会自动换键或重发。" type="warning" :closable="false" />
    <el-button v-if="pending" :disabled="busy || pending.identity !== identity()" @click="retry">使用原幂等键核对提交</el-button>
  </section>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
import { envelope } from '@/api/medical/protocol'
import { readBoundedJson } from '@/api/medical/project-json'
import { byteDigest, sourceDigest } from '@/api/medical/content-digest'
import { canonicalizeEx } from 'json-canonicalize'
const user = useUserStore(), busy = ref(false), message = ref('需要当前企业上下文、平台管理身份和对应管理许可。')
const catalogue = ref(null), record = ref(null), editor = ref(''), releaseId = ref(''), environmentId = ref(''), sourceCommit = ref(''), artifactId = ref(''), reason = ref(''), pending = ref(null)
const locked = computed(() => busy.value || !!pending.value)
const isAuthor = computed(() => record.value?.created_by === user.medicalSession?.user.user_id)
const uuid = value => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value)
const identity = () => canonicalizeEx({actor:user.medicalSession?.user.user_id??null,context:user.medicalSession?.current_context??null,generation:user.medicalGeneration??null})
const storageKey = () => 'medical.framework.binding:'+user.medicalSession?.user.user_id+':'+user.medicalSession?.current_context?.tenant_id
function retain(){if(pending.value)sessionStorage.setItem(storageKey(),JSON.stringify(pending.value));else sessionStorage.removeItem(storageKey())}
function restore(){try{pending.value=JSON.parse(sessionStorage.getItem(storageKey())||'null')}catch{message.value='原请求记录损坏，请先人工核对已保存版本。'}}
function headers(){const s=user.medicalSession;if(!s?.current_context||!s.csrf)throw Error('缺少当前企业上下文');return {'X-Schema-Version':'1.0.0','X-Context-Revision':s.current_context.context_revision,[s.csrf.header_name]:s.csrf.token}}
async function call(url,method='get',body=null,key=null){const before=identity();const response=await request({url,method,data:body,medical:'envelope',headers:{...headers(),...(key?{'Idempotency-Key':key}:{})}});if(before!==identity()||document.hidden)throw Error('上下文或页面可见状态已变化，请重新读取');try{return envelope(response).data}catch(error){if(response.status>=400&&response.status<500&&error.requestId)error.definitive=true;throw error}}
async function run(fn){if(busy.value)return;busy.value=true;try{await fn()}catch(error){if(error.definitive){pending.value=null;retain()}message.value=error.message||'操作结果未确认。'}finally{busy.value=false}}
function loadCatalogue(){return run(async()=>{const value=await call('/api/management-bindings/catalogue');if(!Array.isArray(value.operations)||!value.configuration)throw Error('动作清单响应不完整');catalogue.value=value;message.value='已读取实际动作与配置摘要，尚未启用任何绑定。'})}
function prepare(){try{if(!uuid(environmentId.value)||!/^([0-9a-f]{40}|[0-9a-f]{64})$/.test(sourceCommit.value))throw Error('请先填写真实环境 UUID 与实际源码 commit');const modules=['USER','ROLE','MENU','DEPARTMENT','POST','DICTIONARY','PARAMETER','NOTICE','ONLINE_USER','SCHEDULED_JOB','OPERATION_LOG','LOGIN_LOG','SERVICE_MONITOR','CACHE_MONITOR','CONNECTION_POOL','CODE_GENERATOR','API_DOCUMENTATION','FORM_BUILDER'];editor.value=JSON.stringify({schema_version:'1.0.0',release_id:crypto.randomUUID(),environment_id:environmentId.value,source_commit:sourceCommit.value,configuration_sha256:catalogue.value.configuration_sha256,action_catalogue_sha256:catalogue.value.action_catalogue_sha256,runtime_environment:catalogue.value.configuration.runtime_environment,modules:modules.map(module_id=>({module_id,binding_status:'UNBOUND',route_name:null,component_ref:null,source_commit:null,operations:[],permission_codes:[],unavailable_reason:'尚未配置并批准实际操作'}))},null,2);message.value='已准备未绑定草稿。仅将确认的组件与固定操作填写为 BOUND。'}catch(error){message.value=error.message}}
async function checked(value){if(!uuid(value.release_id)||!value.manifest||!['DRAFT','ACTIVE','REVOKED'].includes(value.status)||typeof value.manifest_canonical!=='string')throw Error('版本响应不完整');if(await sourceDigest(value.manifest)!==value.manifest_sha256||await byteDigest(new TextEncoder().encode(value.manifest_canonical))!==value.manifest_sha256)throw Error('规范化内容摘要不匹配');return value}
async function load(id){if(!uuid(id))throw Error('release UUID 无效');const value=await checked(await call('/api/management-bindings/drafts/'+id));record.value=value;releaseId.value=id;editor.value=JSON.stringify(value.manifest,null,2)}
function readRelease(){return run(async()=>{await load(releaseId.value);message.value='已读取固定版本及原始摘要。'})}
function stage(url,body){if(pending.value)throw Error('请先核对原请求');pending.value={key:crypto.randomUUID(),identity:identity(),url,body};retain()}
async function perform(){const p=pending.value;if(!p||p.identity!==identity()||!uuid(p.key)||!['/api/management-bindings/drafts','/api/management-bindings/approvals','/api/management-bindings/revocations'].includes(p.url))throw Error('原请求上下文或地址无效，禁止重放');const result=await call(p.url,'post',p.body,p.key);pending.value=null;retain();releaseId.value=result.release_id;message.value='提交已确认：'+result.status;await load(result.release_id)}
function saveDraft(){return run(async()=>{stage('/api/management-bindings/drafts',{manifest:readBoundedJson(editor.value)});await perform()})}
function approve(){return run(async()=>{if(!uuid(artifactId.value)||isAuthor.value)throw Error('需要真实制品 ID 和不同的批准者');await ElMessageBox.confirm('将批准此固定摘要。此决定会登记为正式绑定发布，后续仍需部署配置。','独立批准',{type:'warning'});stage('/api/management-bindings/approvals',{release_id:record.value.release_id,manifest_sha256:record.value.manifest_sha256,manifest_artifact_id:artifactId.value,reason:reason.value});await perform()})}
function revoke(){return run(async()=>{await ElMessageBox.confirm('撤销后，此发布的受控操作将被拒绝，历史不会删除。','撤销绑定',{type:'warning'});stage('/api/management-bindings/revocations',{release_id:record.value.release_id,reason:reason.value});await perform()})}
function retry(){return run(perform)}
function download(){const url=URL.createObjectURL(new Blob([record.value.manifest_canonical],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='framework-binding-'+record.value.release_id+'.json';a.click();URL.revokeObjectURL(url)}
restore();watch(()=>identity(),()=>{catalogue.value=null;record.value=null;pending.value=null;editor.value='';artifactId.value='';restore();message.value='会话上下文变化，请重新读取。'})
</script>
<style scoped>.binding-publisher{padding:24px;max-width:1100px}.binding-publisher label{display:block;margin:16px 0}.binding-publisher pre{max-height:420px;overflow:auto;white-space:pre-wrap}.binding-publisher p{overflow-wrap:anywhere}</style>
