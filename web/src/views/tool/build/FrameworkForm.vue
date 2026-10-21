<template>
  <section class="controlled-form">
    <h2>受控表单 Schema 发布</h2>
    <p>仅保存声明式字段，不执行脚本或 HTML。草稿不是发布；发布需要另一位管理员与真实制品摘要。</p>
    <p role="status">{{ message }}</p>
    <el-button :disabled="busy" @click="refresh">刷新最近 100 条</el-button>
    <ul><li v-for="item in items" :key="item.form_id"><el-button link :disabled="busy" @click="load(item.form_id, false)">{{ item.form_id }} · {{ item.status }}</el-button></li></ul>
    <label>声明式表单 JSON<el-input v-model="editor" type="textarea" :rows="14" :disabled="busy || !!pending" /></label>
    <label>变更理由<el-input v-model="reason" maxlength="1000" :disabled="busy || !!pending" /></label>
    <el-button :disabled="busy || !!pending || !reason.trim()" @click="draft">保存新版本草稿</el-button>
    <div v-if="record">
      <p>版本：{{ record.form_id }} · 状态：{{ record.status }}</p>
      <p>内容 SHA-256：{{ record.content_sha256 }}</p>
      <el-button :disabled="busy" @click="download">下载规范化 JSON，供真实制品登记</el-button>
      <label>已登记的企业级 JSON 制品 ID<el-input v-model="artifact" :disabled="busy || !!pending" /></label>
      <p>下载不等于制品登记。请通过现有制品登记流程获得真实 ID；作者不能批准自己的版本。</p>
      <el-button :disabled="busy || !!pending || isAuthor || !artifact || !reason.trim()" @click="approve">独立批准并发布</el-button>
      <el-button :disabled="busy" @click="load(record.form_id, true)">验证并读取发布版本</el-button>
      <div v-if="record.status === 'PUBLISHED'" class="preview">
        <h3>{{ record.form.title }}</h3><p>{{ record.form.description }}</p>
        <el-form label-position="top"><el-form-item v-for="field in record.form.fields" :key="field.key" :label="field.label" :required="field.required">
          <el-input v-if="field.type === 'TEXT'" disabled placeholder="发布预览，不收集数据" />
          <el-input-number v-else-if="field.type === 'NUMBER'" disabled />
          <el-checkbox v-else-if="field.type === 'BOOLEAN'" disabled />
          <el-date-picker v-else-if="field.type === 'DATE'" disabled />
          <el-select v-else-if="field.type === 'SELECT'" disabled><el-option v-for="option in field.options" :key="option" :value="option" :label="option" /></el-select>
        </el-form-item></el-form>
      </div>
    </div>
    <div v-if="pending"><p>原请求 {{ pending.key }}。结果未知时仅重试原请求，不创建新版本。</p><el-button :disabled="busy" @click="submitPending">重试原请求</el-button></div>
  </section>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import request from '@/utils/request'
import useUserStore from '@/store/modules/user'
import { envelope } from '@/api/medical/protocol'
import { canonicalizeEx } from 'json-canonicalize'
import { byteDigest } from '@/api/medical/content-digest'
import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import contractText from './FrameworkForm.schema.json?raw'
const user = useUserStore(), busy = ref(false), message = ref('需要当前企业上下文、原工具权限及已批准功能绑定。')
const items = ref([]), record = ref(null), reason = ref(''), artifact = ref(''), pending = ref(null)
const editor = ref(JSON.stringify({schema_version:'1.0.0',title:'示例表单',description:'',fields:[{key:'name',label:'名称',type:'TEXT',required:true,options:[]}]},null,2))
const isAuthor = computed(() => record.value?.created_by === user.medicalSession?.user.user_id)
const ajv = new Ajv2020({allErrors:true});addFormats(ajv);ajv.addSchema(JSON.parse(contractText));const valid = (kind,value) => {if(!ajv.validate('urn:medical:framework-form:1.0.0#/$defs/'+kind,value))throw Error('表单合同不匹配：'+kind)}
const canonical = v => canonicalizeEx(v,{strictUndefined:true})
const identity = () => canonical({actor:user.medicalSession?.user.user_id??null,context:user.medicalSession?.current_context??null,generation:user.medicalGeneration??null})
function headers(){const s=user.medicalSession;if(!s?.current_context)throw Error('需要有效企业上下文');return {'X-Schema-Version':'1.0.0','X-Context-Revision':s.current_context.context_revision,[s.csrf.header_name]:s.csrf.token}}
async function run(fn){busy.value=true;try{await fn()}catch(e){if(e.definitive){pending.value=null;retain()}message.value=e.message || '请求失败，未确认发布。'}finally{busy.value=false}}
async function call(url,method='get',body=null,key=null){const origin=identity();const r=await request({url,method,data:body,medical:'envelope',headers:{...headers(),...(key?{'Idempotency-Key':key}:{})}});if(origin!==identity()||document.hidden)throw Error('上下文已变化，请重新读取');let e;try{e=envelope(r)}catch(error){if(r.status>=400&&r.status<500&&error.requestId)error.definitive=true;throw error}valid('Response',e);return e.data}
async function checked(v){valid('View',v);if(!v || typeof v.form_id!=='string'||!['DRAFT','DRAFT_VIEW','PUBLISHED'].includes(v.status)||!v.form||!Array.isArray(v.form.fields))throw Error('响应结构无效');if(await byteDigest(new TextEncoder().encode(contractText))!==v.schema_sha256||await byteDigest(new TextEncoder().encode(canonical(v.form)))!==v.content_sha256)throw Error('Schema 或内容摘要不匹配');for(const f of v.form.fields)if(!['TEXT','NUMBER','BOOLEAN','DATE','SELECT'].includes(f.type)||!Array.isArray(f.options))throw Error('不支持的表单控件');return v}
function refresh(){return run(async()=>{const v=await call('/api/tool/build/schemas');if(!Array.isArray(v.items))throw Error('列表无效');items.value=v.items;message.value='已读取最近版本。'})}
function load(id,published){return run(async()=>{if(!/^[0-9a-f-]{36}$/.test(id))throw Error('版本 ID 无效');record.value=null;const v=await checked(await call('/api/tool/build/schemas/'+id+(published?'/published':'')));record.value=v;editor.value=JSON.stringify(v.form,null,2);message.value=published?'已验证独立批准及固定摘要。':'草稿视图，不代表已发布。'})}
function draft(){return run(async()=>{const form=JSON.parse(editor.value);valid('Draft',{form,reason:reason.value});pending.value={key:crypto.randomUUID(),identity:identity(),url:'/api/tool/build/schemas',body:{form,reason:reason.value}};retain();await perform()})}
function approve(){return run(async()=>{valid('Approve',{content_sha256:record.value.content_sha256,schema_artifact_id:artifact.value,reason:reason.value});pending.value={key:crypto.randomUUID(),identity:identity(),url:'/api/tool/build/schemas/'+record.value.form_id+'/approvals',body:{content_sha256:record.value.content_sha256,schema_artifact_id:artifact.value,reason:reason.value}};retain();await perform()})}
async function perform(){const p=pending.value;if(!p||p.identity!==identity())throw Error('原请求上下文已变化，禁止自动重放');if(!/^[0-9a-f-]{36}$/.test(p.key)||!/^\/api\/tool\/build\/schemas(?:\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/approvals)?$/.test(p.url))throw Error('原请求地址或键无效');valid(p.url.endsWith('/approvals')?'Approve':'Draft',p.body);const result=await checked(await call(p.url,'post',p.body,p.key));record.value=result;pending.value=null;retain();message.value=result.status==='PUBLISHED'?'独立批准已登记，固定版本已发布。':'新版本已保存，尚未发布。'}
const storageKey=()=> 'medical.framework.form:'+user.medicalSession?.user.user_id+':'+user.medicalSession?.current_context?.tenant_id
function retain(){if(pending.value)sessionStorage.setItem(storageKey(),JSON.stringify(pending.value));else sessionStorage.removeItem(storageKey())}
function restore(){try{pending.value=JSON.parse(sessionStorage.getItem(storageKey())||'null')}catch{message.value='原请求恢复记录损坏，请人工核对版本。'}}
restore()
function submitPending(){return run(perform)}
function download(){const blob=new Blob([canonical(record.value.form)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='form-'+record.value.form_id+'.json';a.click();URL.revokeObjectURL(url)}
watch(()=>identity(),()=>{items.value=[];record.value=null;pending.value=null;restore();artifact.value='';message.value='会话上下文变化，请重新读取。'})
</script>
<style scoped>.controlled-form{padding:24px;max-width:960px}.controlled-form label{display:block;margin:16px 0}.controlled-form p{overflow-wrap:anywhere}.preview{padding:20px;border:1px solid #dcdfe6;margin-top:20px}</style>
