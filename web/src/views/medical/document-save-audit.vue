<template>
  <main class="medical-page audit-page">
    <header class="audit-heading"><h1>文稿保存审计</h1><el-button :loading="bootLoading" @click="refresh">刷新记录</el-button></header>
    <operations-navigation />
    <p v-if="!allowed" role="alert">请使用当前企业身份及项目读取权限。</p>
    <template v-else>
      <p v-if="bootLoading" role="status">正在核查真实服务状态与获授权项目…</p>
      <p v-if="bootError" role="alert">{{ bootError }}</p>
      <p v-if="availability && !availability.features.business_audit">当前服务器尚未启用文稿保存审计。这不表示没有审计记录。</p>
      <template v-if="availability?.features.business_audit">
        <el-card shadow="never" class="audit-query">
        <el-form label-position="top" @submit.prevent="load()">
          <div class="audit-filter-grid">
          <el-form-item label="项目"><el-select v-model="projectId" filterable clearable :loading="projectLoading" placeholder="请选择项目"><el-option v-for="project in projects?.projects ?? []" :key="project.project_id" :value="project.project_id" :label="project.name" /></el-select></el-form-item>
          <p v-if="projectLoading" role="status">正在读取项目…</p>
          <p v-if="projectError" role="alert">{{ projectError }}</p>
          <p v-if="projects && !projects.projects.length">当前项目页没有获授权项目。</p>
          <el-form-item label="保存时间范围"><el-date-picker v-model="dateRange" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" range-separator="至" /></el-form-item>
          <el-form-item label="文稿"><el-select v-model="documentId" clearable filterable placeholder="全部文稿" :loading="documentDirectoryLoading"><el-option v-for="item in documentChoices" :key="item.id" :label="item.label" :value="item.id" /><template #footer><el-button v-if="hasMoreDocuments" text :loading="documentDirectoryLoading" @click="loadDocuments(true)">加载更多文稿</el-button></template></el-select><p v-if="documentDirectoryError" role="alert">{{ documentDirectoryError }}</p></el-form-item>
          <el-form-item label="保存人员"><el-select v-model="actorId" clearable filterable :loading="memberLoading" placeholder="全部人员"><el-option :value="session!.user.user_id" :label="session!.user.display_name ?? session!.user.username ?? '当前人员'" /><el-option v-for="member in memberLabels.filter(item=>item.user_id!==session!.user.user_id)" :key="member.user_id" :value="member.user_id" :label="memberLabelName(member)" /><template #footer><el-button v-if="membersAfter" text :loading="memberLoading" @click="loadMembers(membersAfter)">加载更多保存人员</el-button></template></el-select><span v-if="!memberLabelsAllowed(session)" class="audit-help">当前仅可读取本人名称，其他成员名称需要企业成员读取权限。</span></el-form-item>
          </div>
          <div class="audit-query-actions"><span>时间按照当前时区显示。</span><el-button native-type="submit" type="primary" :loading="loading" :disabled="!canQuery">查询保存记录</el-button></div>
          <el-collapse><el-collapse-item title="高级查询与版本标识" name="filters"><el-row :gutter="20"><el-col :xs="24" :md="12"><el-form-item label="保存人员 UUID"><el-input v-model="actorId" clearable autocomplete="off" /></el-form-item></el-col><el-col :xs="24" :md="12"><el-form-item label="文稿 UUID"><el-input v-model="documentId" clearable autocomplete="off" /></el-form-item></el-col></el-row><el-form-item label="每页数量"><el-input-number v-model="pageSize" :min="1" :max="availability.maximum_page_size ?? 1" :precision="0" /></el-form-item><el-button v-if="projects?.next_after" :disabled="projectLoading" @click="loadProjects(projects.next_after)">读取更多项目</el-button><p class="audit-help">最长可查询 {{ Math.floor((availability.maximum_window_seconds ?? 0) / 86400) }} 天。保存成功表示正文版本已经保存；审批与签发分别记录。</p></el-collapse-item></el-collapse>
        </el-form></el-card>
        <p v-if="loading" role="status">正在读取获授权保存记录…</p>
        <p v-if="error" role="alert">{{ error }}</p>
        <template v-if="page">
          <div class="audit-result-heading"><h2>保存记录</h2><span>当前页面 {{ page.items.length }} 条</span></div>
          <el-table :data="page.items" row-key="audit_id" border data-audit-records>
            <el-table-column label="保存时间" min-width="180"><template #default="{ row }">{{ localTime(row.occurred_at) }}</template></el-table-column>
            <el-table-column label="保存人员" min-width="150"><template #default="{ row }">{{ actorName(row.actor_id) }}</template></el-table-column>
            <el-table-column label="操作" width="120"><template #default><el-tag type="success">保存正文</el-tag></template></el-table-column>
            <el-table-column label="文稿" min-width="220"><template #default="{ row }">{{ documentChoices.find(item => item.id === row.target.document_id)?.label ?? row.target.document_id }}</template></el-table-column>
            <el-table-column label="版本信息" min-width="140"><template #default="{ row }"><el-popover trigger="click" width="360"><template #reference><el-button link type="primary">查看版本</el-button></template><p>文稿：{{ row.target.document_id }}</p><p>固定版本：{{ row.target.document_revision }}</p><p>保存人员：{{ row.actor_id }}</p></el-popover></template></el-table-column>
            <el-table-column label="阅读" width="150" fixed="right"><template #default="scope"><el-button link type="primary" :disabled="loading || bodyLoading" @click="openRevision(scope.row)">读取固定版本</el-button></template></el-table-column>
            <template #empty><el-empty description="当前项目与时间范围没有匹配的保存记录" :image-size="80" /></template>
          </el-table>
          <el-button v-if="page.next_cursor" :disabled="loading || bodyLoading" @click="load(page.next_cursor)">下一页</el-button>
          <el-button :disabled="loading || bodyLoading" @click="load()">刷新当前查询</el-button>
        </template>
        <p v-if="bodyLoading" role="status">正在重新核查原版本及来源权限并读取完整正文…</p>
        <p v-if="bodyError" role="alert">{{ bodyError }}</p>
        <el-drawer v-model="readerVisible" title="已保存文稿版本" size="min(1120px, 96vw)" @closed="closeRevision"><section v-if="saved" aria-label="固定版本只读正文">
          <h2>已保存文稿版本 · 只读</h2><p>固定版本：{{ saved.snapshot.document_revision }}</p>
          <el-button @click="closeRevision">关闭正文</el-button>
          <document-body-reader :body="saved.body" :document-revision="saved.snapshot.document_revision" :active="routeActive && !loading" :dirty="false" />
        </section></el-drawer>
      </template>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import OperationsNavigation from '@/components/Medical/OperationsNavigation.vue'
import { listTasks, taskDocumentLabels } from '@/api/medical/tasks'
import type { TaskPage } from '@/api/medical/tasks'
import DocumentBodyReader from './document-body-reader.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { projectActionAllowed } from '@/api/medical/project-write'
import { listProjects } from '@/api/medical/projects'
import { canonicalDocumentValue as canonical } from '@/api/medical/document-body'
import { readSavedDocumentRevision } from '@/api/medical/documents'
import type { SavedDocument } from '@/api/medical/documents'
import type { Listing, MedicalSession } from '@/api/medical/protocol'
import { memberLabelName, memberLabelsAllowed, readMemberLabels } from '@/api/medical/member-labels'
import type { MemberLabel } from '@/api/medical/member-labels'
import { AuditInputError, AuditCapacityError, prepareAuditQuery, readAuditAvailability, readAuditPage } from '@/api/medical/business-audit'
import type { AuditAvailability, AuditEntry, AuditPage, AuditQuery } from '@/api/medical/business-audit'
const user=useUserStore(),session=computed(()=>user.medicalSession)
const allowed=computed(()=>projectActionAllowed(session.value,'project.read'))
const availability=ref<AuditAvailability|null>(null),projects=ref<Listing|null>(null),page=ref<AuditPage|null>(null),saved=ref<SavedDocument|null>(null)
const projectId=ref(''),from=ref(''),to=ref(''),actorId=ref(''),documentId=ref(''),pageSize=ref<number>(1)
const dateRange=computed<[Date,Date]|null>({get:():[Date,Date]|null=>from.value&&to.value?[new Date(from.value),new Date(to.value)]:null,set:(value:[Date,Date]|null)=>{from.value=value?value[0].toISOString():'';to.value=value?value[1].toISOString():''}})
const bootLoading=ref(false),projectLoading=ref(false),loading=ref(false),bodyLoading=ref(false)
const readerVisible=ref(false),documentChoices=ref<{id:string;label:string}[]>([])
const memberLabels=shallowRef<MemberLabel[]>([]),membersAfter=ref<string|null>(null),memberLoading=ref(false)
const documentDirectoryLoading=ref(false),documentDirectoryError=ref(''),taskDirectoryPage=shallowRef<TaskPage|null>(null)
const hasMoreDocuments=computed(()=>taskDirectoryPage.value!==null&&BigInt(taskDirectoryPage.value.page_info.page*taskDirectoryPage.value.page_info.page_size)<BigInt(taskDirectoryPage.value.page_info.total))
const bootError=ref(''),projectError=ref(''),error=ref(''),bodyError=ref('')
let generation=0,projectGeneration=0,queryGeneration=0,bodyGeneration=0,alive=true
let bootController:AbortController|null=null,projectController:AbortController|null=null,queryController:AbortController|null=null,bodyController:AbortController|null=null,documentController:AbortController|null=null,memberController:AbortController|null=null
let fixedQuery:AuditQuery|null=null,lastEntry:AuditEntry|undefined;const cursors=new Set<string>()
function localTime(value:string){return new Date(value).toLocaleString('zh-CN',{hour12:false})}
function closeRevision(){bodyGeneration++;bodyController?.abort();bodyController=null;saved.value=null;readerVisible.value=false;bodyLoading.value=false;bodyError.value=''}
function clearRows(){queryGeneration++;queryController?.abort();queryController=null;page.value=null;loading.value=false;error.value='';fixedQuery=null;lastEntry=undefined;cursors.clear();closeRevision()}
function clearAll(){generation++;projectGeneration++;bootController?.abort();projectController?.abort();documentController?.abort();memberController?.abort();bootController=null;projectController=null;documentController=null;memberController=null;memberLabels.value=[];membersAfter.value=null;memberLoading.value=false;documentChoices.value=[];taskDirectoryPage.value=null;documentDirectoryLoading.value=false;documentDirectoryError.value='';availability.value=null;projects.value=null;projectId.value='';actorId.value='';documentId.value='';bootLoading.value=false;projectLoading.value=false;bootError.value='';projectError.value='';clearRows()}
const routeActive=useMedicalRouteRead('MedicalDocumentSaveAudit',clearAll)
const scope=computed(()=>session.value?canonical({actor:session.value.user.user_id,context:session.value.current_context,actions:session.value.action_summary}):'')
const canQuery=computed(()=>!!projectId.value&&!!projects.value?.projects.some(p=>p.project_id===projectId.value)&&projectActionAllowed(session.value,'project.read',projectId.value)&&!!availability.value?.features.business_audit&&routeActive.value)
function live(g:number,s:MedicalSession,identity:string){return alive&&g===generation&&session.value===s&&scope.value===identity&&allowed.value&&routeActive.value&&!document.hidden}
async function refresh(){
 clearAll();const s=session.value;if(!s?.current_context||!allowed.value||!routeActive.value||document.hidden)return
 const g=generation,identity=scope.value,c=new AbortController();bootController=c;bootLoading.value=true
 try{const a=await readAuditAvailability(s,c.signal);if(!live(g,s,identity))return;availability.value=a
  if(a.features.business_audit){pageSize.value=Math.min(20,a.maximum_page_size!);const now=Date.now(),today=new Date(now);today.setHours(0,0,0,0);to.value=new Date(now).toISOString();from.value=new Date(Math.max(today.getTime(),now-a.maximum_window_seconds!*1000)).toISOString();await loadMembers(null);if(!live(g,s,identity))return;await loadProjects()}
 }catch{if(live(g,s,identity))bootError.value='服务状态读取失败，请重新核查。'}finally{if(g===generation){bootLoading.value=false;bootController=null}}
}
function actorName(id:string){if(id===session.value?.user.user_id)return session.value.user.display_name??session.value.user.username??'当前人员';const value=memberLabels.value.find(item=>item.user_id===id);return value?memberLabelName(value):'成员名称尚未读取'}
async function loadMembers(after:string|null){
 const s=session.value;if(!s||!memberLabelsAllowed(s)||memberLoading.value||!routeActive.value)return
 const g=generation,identity=scope.value,c=new AbortController();memberController?.abort();memberController=c;memberLoading.value=true
 try{const result=await readMemberLabels(s,after,c.signal);if(!live(g,s,identity)||memberController!==c)return;memberLabels.value=after?[...memberLabels.value,...result.items]:result.items;membersAfter.value=result.next_after}finally{if(memberController===c)memberLoading.value=false}
}
async function loadProjects(after?:string){
 if(after!==undefined&&(projectLoading.value||after!==projects.value?.next_after))return
 const s=session.value;if(!s?.current_context||!availability.value?.features.business_audit||!allowed.value||!routeActive.value||document.hidden)return
 projectGeneration++;projectController?.abort();const p=projectGeneration,g=generation,identity=scope.value,c=new AbortController();projectController=c;projectLoading.value=true;projectError.value='';projects.value=null;projectId.value='';clearRows()
 try{const value=await listProjects(s.current_context,after?{after}:{},c.signal);if(live(g,s,identity)&&p===projectGeneration){projects.value=value;if(value.projects.length===1){projectId.value=value.projects[0]!.project_id;await load()}}}
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
  if(live(g,s,identity)&&n===bodyGeneration){saved.value=value;readerVisible.value=true}
 }catch{if(live(g,s,identity)&&n===bodyGeneration)bodyError.value='固定版本读取失败。请重新核查文稿及来源权限；审计摘要不等于正文读取授权。'}finally{if(n===bodyGeneration){bodyLoading.value=false;bodyController=null}}
}
watch([projectId,from,to,actorId,documentId,pageSize],clearRows,{flush:'sync'})
async function loadDocuments(more=false){
 const s=session.value,id=projectId.value,previous=more?taskDirectoryPage.value:null;if(!s||!id||documentDirectoryLoading.value||!routeActive.value||!projectActionAllowed(s,'project.task.read',id)||more&&!hasMoreDocuments.value)return
 const g=generation,identity=scope.value,c=new AbortController();documentController?.abort();documentController=c;documentDirectoryLoading.value=true;documentDirectoryError.value=''
 try{const tasks=await listTasks(s,id,{page:previous?previous.page_info.page+1:1,page_size:100,...(previous?{dataset_watermark:previous.page_info.watermark,filter_sha256:previous.page_info.filter_sha256}:{})},previous,c.signal)
  if(!live(g,s,identity)||projectId.value!==id||documentController!==c)return
  const additions=tasks.items.flatMap(item=>item.detail.document_ids.map(document=>({id:document,label:taskDocumentLabels[item.document_type]+' · '+item.goal})));documentChoices.value=[...new Map([...(more?documentChoices.value:[]),...additions].map(item=>[item.id,item])).values()];taskDirectoryPage.value=tasks
 }catch(failure){if(live(g,s,identity)&&documentController===c&&!c.signal.aborted)documentDirectoryError.value=failure instanceof Error?failure.message:'文稿目录读取失败'}
 finally{if(documentController===c)documentDirectoryLoading.value=false}
}
watch(projectId,()=>{documentController?.abort();documentController=null;documentId.value='';documentChoices.value=[];taskDirectoryPage.value=null;documentDirectoryLoading.value=false;documentDirectoryError.value='';void loadDocuments()})
watch(()=>[session.value,scope.value,allowed.value,routeActive.value],()=>{void refresh()},{immediate:true,flush:'sync'})
function visibility(){if(document.hidden)clearAll()}
document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{alive=false;clearAll();document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>
.audit-page{max-width:1480px;margin:auto;padding:28px}.audit-heading,.audit-result-heading,.audit-query-actions{display:flex;align-items:center;justify-content:space-between;gap:16px}.audit-heading{margin-bottom:12px}.audit-heading h1{font-size:24px;margin:0}.audit-result-heading h2{font-size:17px;margin:0}.audit-query{margin:24px 0}.audit-filter-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:24px}.audit-page p{line-height:1.6;color:var(--el-text-color-secondary)}.audit-page [role=alert]{color:var(--el-color-danger)}.audit-page .el-select,.audit-page :deep(.el-date-editor){width:100%}.audit-help{width:100%;margin:8px 0 0}.audit-page .el-collapse{margin-top:16px}.audit-page .el-table{margin:20px 0}.audit-query-actions span,.audit-result-heading span{font-size:13px;color:var(--el-text-color-secondary)}@media(max-width:860px){.audit-filter-grid{grid-template-columns:1fr}}@media(max-width:650px){.audit-page{padding:16px}}
</style>
