<template>
  <main class="medical-page audit-page">
    <header><h1>文稿保存审计</h1><p>仅查看当前获授权范围内的文稿保存记录。保存成功不代表审批、签发或全部业务审计覆盖。</p></header>
    <session-panel />
    <p v-if="!allowed" role="alert">请使用当前企业身份及项目读取权限。</p>
    <template v-else>
      <el-button :disabled="bootLoading" @click="refresh">重新核查服务及项目</el-button>
      <p v-if="bootLoading" role="status">正在核查真实服务状态与获授权项目…</p>
      <p v-if="bootError" role="alert">{{ bootError }}</p>
      <p v-if="availability && !availability.features.business_audit">当前服务器尚未启用文稿保存审计。这不表示没有审计记录。</p>
      <template v-if="availability?.features.business_audit">
        <form @submit.prevent="load()">
          <label>项目<select v-model="projectId" :disabled="projectLoading"><option value="">请选择当前页项目</option><option v-for="project in projects?.projects ?? []" :key="project.project_id" :value="project.project_id">{{ project.name }}（{{ project.status }}）</option></select></label>
          <p v-if="projectLoading" role="status">正在读取项目…</p>
          <p v-if="projectError" role="alert">{{ projectError }}</p>
          <p v-if="projects && !projects.projects.length">当前项目页没有获授权项目。</p>
          <div><el-button :disabled="projectLoading" @click="loadProjects()">项目第一页</el-button><el-button :disabled="projectLoading || !projects?.next_after" @click="loadProjects(projects?.next_after ?? undefined)">下一页项目</el-button></div>
          <fieldset><legend>明确时间范围（UTC，含开始、不含结束）</legend>
            <label>开始<input v-model="from" type="text" placeholder="2026-10-18T00:00:00Z" /></label>
            <label>结束<input v-model="to" type="text" placeholder="2026-10-19T00:00:00Z" /></label>
            <p>时间须以 Z 结尾，最多六位小数。当前服务器允许最长 {{ availability.maximum_window_seconds }} 秒。</p>
          </fieldset>
          <details><summary>可选已知标识筛选</summary><p>只填写已知的完整标识，不推测人员名称或文稿归属。</p>
            <label>保存人员 UUID<input v-model="actorId" type="text" autocomplete="off" /></label>
            <label>文稿 UUID<input v-model="documentId" type="text" autocomplete="off" /></label>
          </details>
          <label>每页数量<input v-model.number="pageSize" type="number" min="1" :max="availability.maximum_page_size ?? 1" /></label>
          <el-button native-type="submit" :disabled="loading || !canQuery">查询此范围</el-button>
        </form>
        <p v-if="loading" role="status">正在读取获授权保存记录…</p>
        <p v-if="error" role="alert">{{ error }}</p>
        <template v-if="page">
          <p>本页 {{ page.items.length }} 条文稿保存记录<span v-if="page.complete">；本轮指定条件扫描结束</span>。</p>
          <p>这是实时授权扫描。权限、来源许可和新保存可能变化；刷新可读取最新结果，没有全业务总数或完整历史水位。</p>
          <p v-if="!page.items.length">本页没有可显示的匹配记录<span v-if="page.next_cursor">，仍可继续下一页</span>。不能据此判断全部业务没有日志。</p>
          <ol><li v-for="entry in page.items" :key="entry.audit_id">
            <strong>文稿保存成功</strong> <time :datetime="entry.occurred_at">{{ entry.occurred_at }}（UTC）</time>
            <p>保存人员标识：{{ entry.actor_id }}</p><p>文稿：{{ entry.target.document_id }} · 固定版本：{{ entry.target.document_revision }}</p>
            <el-button :disabled="loading || bodyLoading" @click="openRevision(entry)">读取此固定文稿版本</el-button>
          </li></ol>
          <el-button v-if="page.next_cursor" :disabled="loading || bodyLoading" @click="load(page.next_cursor)">下一页</el-button>
          <el-button :disabled="loading || bodyLoading" @click="load()">相同条件从首页刷新</el-button>
        </template>
        <p v-if="bodyLoading" role="status">正在重新核查原版本及来源权限并读取完整正文…</p>
        <p v-if="bodyError" role="alert">{{ bodyError }}</p>
        <section v-if="saved" aria-label="固定版本只读正文">
          <h2>已保存文稿版本 · 只读</h2><p>{{ saved.snapshot.document_revision }}。此次读取不是审批或当前草稿。</p>
          <el-button @click="closeRevision">关闭正文</el-button>
          <document-body-reader :body="saved.body" :document-revision="saved.snapshot.document_revision" :active="routeActive && !loading" :dirty="false" />
        </section>
      </template>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import DocumentBodyReader from './document-body-reader.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { projectActionAllowed } from '@/api/medical/project-write'
import { listProjects } from '@/api/medical/projects'
import { canonicalDocumentValue as canonical } from '@/api/medical/document-body'
import { readSavedDocumentRevision } from '@/api/medical/documents'
import type { SavedDocument } from '@/api/medical/documents'
import type { Listing, MedicalSession } from '@/api/medical/protocol'
import { AuditInputError, AuditCapacityError, prepareAuditQuery, readAuditAvailability, readAuditPage } from '@/api/medical/business-audit'
import type { AuditAvailability, AuditEntry, AuditPage, AuditQuery } from '@/api/medical/business-audit'
const user=useUserStore(),session=computed(()=>user.medicalSession)
const allowed=computed(()=>projectActionAllowed(session.value,'project.read'))
const availability=ref<AuditAvailability|null>(null),projects=ref<Listing|null>(null),page=ref<AuditPage|null>(null),saved=ref<SavedDocument|null>(null)
const projectId=ref(''),from=ref(''),to=ref(''),actorId=ref(''),documentId=ref(''),pageSize=ref<number>(1)
const bootLoading=ref(false),projectLoading=ref(false),loading=ref(false),bodyLoading=ref(false)
const bootError=ref(''),projectError=ref(''),error=ref(''),bodyError=ref('')
let generation=0,projectGeneration=0,queryGeneration=0,bodyGeneration=0,alive=true
let bootController:AbortController|null=null,projectController:AbortController|null=null,queryController:AbortController|null=null,bodyController:AbortController|null=null
let fixedQuery:AuditQuery|null=null,lastEntry:AuditEntry|undefined;const cursors=new Set<string>()
function closeRevision(){bodyGeneration++;bodyController?.abort();bodyController=null;saved.value=null;bodyLoading.value=false;bodyError.value=''}
function clearRows(){queryGeneration++;queryController?.abort();queryController=null;page.value=null;loading.value=false;error.value='';fixedQuery=null;lastEntry=undefined;cursors.clear();closeRevision()}
function clearAll(){generation++;projectGeneration++;bootController?.abort();projectController?.abort();bootController=null;projectController=null;availability.value=null;projects.value=null;projectId.value='';bootLoading.value=false;projectLoading.value=false;bootError.value='';projectError.value='';clearRows()}
const routeActive=useMedicalRouteRead('MedicalDocumentSaveAudit',clearAll)
const scope=computed(()=>session.value?canonical({actor:session.value.user.user_id,context:session.value.current_context,actions:session.value.action_summary}):'')
const canQuery=computed(()=>!!projectId.value&&!!projects.value?.projects.some(p=>p.project_id===projectId.value)&&projectActionAllowed(session.value,'project.read',projectId.value)&&!!availability.value?.features.business_audit&&routeActive.value)
function live(g:number,s:MedicalSession,identity:string){return alive&&g===generation&&session.value===s&&scope.value===identity&&allowed.value&&routeActive.value&&!document.hidden}
async function refresh(){
 clearAll();const s=session.value;if(!s?.current_context||!allowed.value||!routeActive.value||document.hidden)return
 const g=generation,identity=scope.value,c=new AbortController();bootController=c;bootLoading.value=true
 try{const a=await readAuditAvailability(s,c.signal);if(!live(g,s,identity))return;availability.value=a
  if(a.features.business_audit){pageSize.value=Math.min(20,a.maximum_page_size!);const now=Date.now();to.value=new Date(now).toISOString();from.value=new Date(now-Math.min(3600,a.maximum_window_seconds!)*1000).toISOString();await loadProjects()}
 }catch{if(live(g,s,identity))bootError.value='服务状态读取失败，请重新核查。'}finally{if(g===generation){bootLoading.value=false;bootController=null}}
}
async function loadProjects(after?:string){
 if(after!==undefined&&(projectLoading.value||after!==projects.value?.next_after))return
 const s=session.value;if(!s?.current_context||!availability.value?.features.business_audit||!allowed.value||!routeActive.value||document.hidden)return
 projectGeneration++;projectController?.abort();const p=projectGeneration,g=generation,identity=scope.value,c=new AbortController();projectController=c;projectLoading.value=true;projectError.value='';projects.value=null;projectId.value='';clearRows()
 try{const value=await listProjects(s.current_context,after?{after}:{},c.signal);if(live(g,s,identity)&&p===projectGeneration)projects.value=value}
 catch{if(live(g,s,identity)&&p===projectGeneration)projectError.value='项目读取失败，请从项目第一页重新读取。'}finally{if(p===projectGeneration){projectLoading.value=false;projectController=null}}
}
function query():AuditQuery{return {project_id:projectId.value,action:'document.revision.saved',from:from.value,to:to.value,actor_id:actorId.value.trim()||null,document_id:documentId.value.trim()||null,limit:pageSize.value}}
async function load(cursor?:string){
 if(!canQuery.value||loading.value||bodyLoading.value||document.hidden)return
 const s=session.value,a=availability.value;if(!s||!a)return
 if(cursor!==undefined&&(cursor!==page.value?.next_cursor||fixedQuery===null))return
 let q:AuditQuery;try{q=prepareAuditQuery(query(),a)}catch(e){clearRows();error.value=e instanceof AuditInputError?e.message:'查询条件无效。';return}
 if(cursor!==undefined&&(canonical(q)!==canonical(fixedQuery)||cursors.has(cursor))){clearRows();error.value='分页条件或游标已变化，请从首页重新读取。';return}
 const previous=cursor===undefined?undefined:lastEntry
 if(cursor===undefined)clearRows();else closeRevision()
 const g=generation,n=++queryGeneration,identity=scope.value,c=new AbortController();queryController?.abort();queryController=c;loading.value=true;error.value='';page.value=null;fixedQuery=q
 try{const result=await readAuditPage(s,q,a,c.signal,cursor,previous);if(!live(g,s,identity)||n!==queryGeneration)return
  if(result.kind==='CURSOR_STALE'){clearRows();error.value=result.code==='CURSOR_EXPIRED'?'分页已过期，旧结果已清除。请按原条件从首页重新读取。':'分页条件或身份已失效，旧结果已清除。请重新查询。';return}
  page.value=result.page;if(cursor!==undefined)cursors.add(cursor);if(result.page.items.length)lastEntry=result.page.items[result.page.items.length-1]
 }catch(e){if(live(g,s,identity)&&n===queryGeneration){clearRows();error.value=e instanceof AuditCapacityError||e instanceof AuditInputError?e.message:'审计读取失败，旧分页已清除。请确认当前授权后重新查询。'}}finally{if(n===queryGeneration){loading.value=false;queryController=null}}
}
async function openRevision(entry:AuditEntry){
 const s=session.value;if(!s||!canQuery.value||loading.value||bodyLoading.value||!page.value?.items.some(item=>item===entry)||document.hidden)return
 closeRevision();const g=generation,n=bodyGeneration,identity=scope.value,c=new AbortController();bodyController=c;bodyLoading.value=true
 try{const value=await readSavedDocumentRevision(s,entry.target.project_id,entry.target.document_id,entry.target.document_revision,{maximumPages:1000,maximumBytes:104857600,pageSize:100},c.signal)
  if(live(g,s,identity)&&n===bodyGeneration)saved.value=value
 }catch{if(live(g,s,identity)&&n===bodyGeneration)bodyError.value='固定版本读取失败。请重新核查文稿及来源权限；审计摘要不等于正文读取授权。'}finally{if(n===bodyGeneration){bodyLoading.value=false;bodyController=null}}
}
watch([projectId,from,to,actorId,documentId,pageSize],clearRows,{flush:'sync'})
watch(()=>[session.value,scope.value,allowed.value,routeActive.value],()=>{void refresh()},{immediate:true,flush:'sync'})
function visibility(){if(document.hidden)clearAll()}
document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{alive=false;clearAll();document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>
.audit-page{max-width:1080px;margin:auto;padding:24px}.audit-page form{display:grid;gap:16px;margin:24px 0}.audit-page label{display:grid;gap:6px}.audit-page input,.audit-page select{padding:10px;max-width:100%;border:1px solid #b7c3cf;border-radius:6px}.audit-page li{padding:18px 0;border-bottom:1px solid #dbe3eb;overflow-wrap:anywhere}.audit-page p{line-height:1.6;color:#405168}.audit-page [role=alert]{color:#9d2424}.audit-page fieldset{border:1px solid #dbe3eb;border-radius:8px;padding:16px;display:grid;gap:12px}.audit-page time{display:block;color:#526777}.audit-page section{margin-top:24px;padding:20px;border:1px solid #dbe3eb;border-radius:8px}
</style>
