<template>
  <main class="medical-page projects-workspace">
    <header class="projects-heading"><div><h1>项目中心</h1><p>按企业授权查看医疗器械项目、注册路径、资料状态和任务进度。</p></div><div class="heading-actions"><el-dropdown v-if="management.length" @command="manage"><el-button :disabled="busy">组织管理</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in management" :key="item.path" :command="item.path">{{ item.label }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button type="primary" :disabled="!projectActionAllowed(user.medicalSession,'project.create',null) || user.medicalWriting" @click="router.push('/projects/new')">新建项目</el-button></div></header>
    <session-panel />
    <el-empty v-if="user.medicalSession?.current_context && !permitted" description="当前账户没有项目目录读取权限" />
    <el-card v-if="permitted" shadow="never" class="projects-card">
      <el-form label-position="top" class="project-filters" @submit.prevent="search">
        <el-form-item label="搜索项目" :error="queryError"><el-input v-model="name" clearable maxlength="256" placeholder="项目名称或产品名称" :disabled="busy" /></el-form-item>
        <el-form-item label="筛选项目阶段" for="project-stage-filter"><el-select id="project-stage-filter" v-model="status" aria-label="筛选项目阶段" clearable placeholder="全部阶段" :disabled="busy"><el-option v-for="(label,value) in projectStageLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <div class="filter-actions"><el-button native-type="submit" type="primary" :loading="loading" :disabled="user.medicalWriting">查询</el-button><el-button :disabled="busy" @click="reset">重置</el-button></div>
      </el-form>
      <p v-if="loading" role="status">正在读取获授权项目与资料状态</p>
      <el-alert v-if="changed" title="筛选条件已修改，请重新查询项目" type="info" :closable="false" />
      <el-table v-if="listing" :data="listing.items" :row-key="rowKey" data-project-table>
        <el-table-column label="项目名称" min-width="220"><template #default="{ row }"><router-link :to="'/projects/'+row.project.project_id" class="project-name">{{ row.project.name }}</router-link><p class="project-caption">负责人：{{ projectResponsibleName(row.responsible) }}</p></template></el-table-column>
        <el-table-column label="产品名称" min-width="170"><template #default="{ row }"><span :class="{unconfirmed:!known(row.project,'product_name')}">{{ projectAttributeText(row.project,'product_name') }}</span></template></el-table-column>
        <el-table-column label="器械类别" min-width="120"><template #default="{ row }"><span :class="{unconfirmed:!known(row.project,'device_class')}">{{ projectAttributeText(row.project,'device_class') }}</span></template></el-table-column>
        <el-table-column label="注册路径" min-width="140"><template #default="{ row }"><span :class="{unconfirmed:!known(row.project,'application_path')}">{{ projectAttributeText(row.project,'application_path') }}</span></template></el-table-column>
        <el-table-column label="项目阶段" width="110"><template #default="{ row }"><el-tag :type="stageType(row.project.status)">{{ projectStageLabels[row.project.status as keyof typeof projectStageLabels] }}</el-tag></template></el-table-column>
        <el-table-column label="资料状态" min-width="180"><template #default="{ row }"><el-popover trigger="click" width="340"><template #reference><el-button link type="primary">{{ row.materials.registered==='0'?'暂无可见资料':'审查核准 '+row.materials.approved+' / '+row.materials.registered }}</el-button></template><p>统计当前账户可读取的企业公共及本项目资料版本。</p><p>已核准：{{ row.materials.approved }}；未核准：{{ row.materials.not_approved }}。</p><p>项目适用性及使用许可需要按照具体资料核查。</p><p>读取时间：{{ localTime(row.materials.checked_at) }}</p><router-link :to="materialsTarget(row.project)">查看资料与核准状态</router-link></el-popover></template></el-table-column>
        <el-table-column label="任务进度" min-width="170"><template #default="{ row }"><span v-if="row.project.children.tasks===null" class="unconfirmed">无任务统计读取授权</span><template v-else><strong>{{ row.project.children.tasks }} 项任务</strong><p class="project-caption"><span v-if="row.project.children.active_runs!==null">{{ row.project.children.active_runs }} 项活动运行</span><span v-if="row.project.children.waiting_items!==null"> · {{ row.project.children.waiting_items }} 项待办</span></p></template></template></el-table-column>
        <el-table-column label="最近更新" min-width="170"><template #default="{ row }">{{ localTime(row.project.updated_at) }}</template></el-table-column>
        <el-table-column label="操作" min-width="190" fixed="right"><template #default="{ row }"><div class="project-row-actions"><router-link :to="'/projects/'+row.project.project_id">查看项目</router-link><router-link v-if="row.project.allowed_actions.includes('profile.confirm')" :to="'/projects/'+row.project.project_id+'/profile'">完善属性</router-link><el-popover trigger="click" width="400"><template #reference><el-button link type="primary">版本与依据</el-button></template><dl class="project-version"><dt>项目标识</dt><dd>{{ row.project.project_id }}</dd><dt>项目版本</dt><dd>{{ row.project.revision }}</dd><dt>属性版本</dt><dd>{{ row.project.profile.profile_revision }}</dd><dt>属性内容摘要</dt><dd>{{ row.project.profile.content_sha256 }}</dd></dl><router-link :to="'/projects/'+row.project.project_id">查看完整属性与来源依据</router-link></el-popover></div></template></el-table-column>
      </el-table>
      <el-empty v-if="listing && !listing.items.length" description="当前授权及筛选范围没有项目" />
      <div class="project-pagination"><span>{{ listing ? '共 '+listing.total+' 条' : '查询后显示完整筛选范围总数' }}</span><div class="pagination-controls"><label for="project-page-size">每页</label><el-select id="project-page-size" v-model="pageSize" aria-label="项目每页条数" :disabled="busy" @change="changePageSize"><el-option v-for="size in [10,20,50]" :key="size" :value="size" :label="size+' 条'" /></el-select><el-button aria-label="上一页" :disabled="busy || changed || !listing || pageIndex===0" @click="previous">上一页</el-button><span>第 {{ pageIndex+1 }} / {{ totalPages }} 页</span><el-button aria-label="下一页" :disabled="busy || changed || !listing?.next_after" @click="next">下一页</el-button></div></div>
    </el-card>
    <p class="project-footer-note">选择获授权项目，查看项目资料、文档任务及人工核准记录。</p>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { projectActionAllowed, projectTextIssue } from '@/api/medical/project-write'
import { governanceAllowed } from '@/api/medical/member-governance'
import { fail,schemaIssues } from '@/api/medical/protocol'
import type { Project } from '@/api/medical/protocol'
import { canonicalTemplateValue } from '@/api/medical/template-approval'
import { projectAttribute,projectAttributeText,projectDirectorySchema,projectResponsibleName,projectStageLabels,readProjectWorkspaceDirectory } from '@/api/medical/project-workspace'
import type { ProjectDirectoryEntry,ProjectDirectoryQuery,ProjectWorkspaceDirectory } from '@/api/medical/project-workspace'
import SessionPanel from './session-panel.vue'

const user=useUserStore(),router=useRouter(),permitted=computed(()=>projectActionAllowed(user.medicalSession,'project.read'))
const name=ref(''),status=ref<keyof typeof projectStageLabels|''>(''),pageSize=ref(10),queryError=ref(''),loading=ref(false)
const listing=shallowRef<ProjectWorkspaceDirectory|null>(null),applied=shallowRef<ProjectDirectoryQuery>({limit:10}),pageIndex=ref(0),cursors=shallowRef<(string|null)[]>([null])
const busy=computed(()=>loading.value||user.medicalWriting),changed=computed(()=>canonicalTemplateValue(filters())!==canonicalTemplateValue(applied.value))
const totalPages=computed(()=>{if(!listing.value||listing.value.total==='0')return '1';return ((BigInt(listing.value.total)+BigInt(pageSize.value)-1n)/BigInt(pageSize.value)).toString()})
const management=computed(()=>[...(governanceAllowed(user.medicalSession)?[{path:'/enterprises/members',label:'管理企业成员与授权'}]:[]),...(projectActionAllowed(user.medicalSession,'role.manage',null)?[{path:'/enterprises/roles',label:'管理企业岗位'}]:[]),...(user.medicalSession?.user.platform_role==='PLATFORM_ADMIN'?[{path:'/enterprises/registrations',label:'企业登记与审查'}]:[])])
function manage(path:string){if(management.value.some(item=>item.path===path))void router.push(path)}
function filters():ProjectDirectoryQuery{return {limit:pageSize.value,...(name.value?{q:name.value}:{}),...(status.value?{status:status.value}:{})}}
function rowKey(row:ProjectDirectoryEntry){return row.project.project_id}
function known(project:Project,key:string){return projectAttribute(project,key)?.status==='KNOWN'}
function stageType(value:keyof typeof projectStageLabels){return value==='ACTIVE'?'success':value==='ARCHIVED'?'info':'warning'}
function localTime(value:string){return new Date(value).toLocaleString('zh-CN',{hour12:false})}
function materialsTarget(project:Project){return {path:'/library',query:{project_id:project.project_id}}}
let controller:AbortController|null=null,generation=0
function clear(){generation++;controller?.abort();controller=null;listing.value=null;loading.value=false;pageIndex.value=0;cursors.value=[null]}
async function read(index:number,after:string|null):Promise<void>{
 const session=user.medicalSession
 if(!session?.current_context||!permitted.value||busy.value||document.hidden||changed.value)return
 const current=++generation;controller?.abort();controller=new AbortController();listing.value=null;loading.value=true
 try{
  const result=await readProjectWorkspaceDirectory(session,{...applied.value,...(after?{after}:{})},controller.signal)
  if(current!==generation||user.medicalSession!==session||document.hidden)return
  listing.value=result;pageIndex.value=index;cursors.value=[...cursors.value.slice(0,index),after]
 }finally{if(current===generation)loading.value=false}
}
async function search():Promise<void>{
 if(busy.value)return
 const query=filters();queryError.value=query.q?projectTextIssue(query.q,256):''
 if(schemaIssues(projectDirectorySchema+'Query',query).length)queryError.value='请填写有效的项目查询条件'
 if(queryError.value)return
 clear();applied.value=query;await read(0,null)
}
async function reset():Promise<void>{name.value='';status.value='';await search()}
async function changePageSize():Promise<void>{await search()}
async function next():Promise<void>{const after=listing.value?.next_after;if(after&&!busy.value&&!changed.value)await read(pageIndex.value+1,after)}
async function previous():Promise<void>{if(pageIndex.value>0&&!busy.value&&!changed.value){const index=pageIndex.value-1,after=cursors.value[index];if(after===undefined)fail('CONTENT_MISMATCH','项目上一页的原查询标识缺失，请重新查询');await read(index,after)}}
function visibility(){if(document.hidden)clear();else if(!listing.value&&!changed.value&&!busy.value)void read(0,null)}
watch([name,status],()=>{if(changed.value)clear()},{flush:'sync'})
watch(()=>user.medicalSession,async()=>{clear();name.value='';status.value='';pageSize.value=10;queryError.value='';applied.value={limit:10};await read(0,null)},{immediate:true,flush:'sync'})
document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{document.removeEventListener('visibilitychange',visibility);clear()})
</script>
<style scoped>
.projects-workspace{padding:28px;--el-color-primary:#1768c4;--el-color-primary-dark-2:#1256a4;--el-color-primary-light-9:#edf4ff;--el-text-color-primary:#243247;--el-text-color-regular:#526279;--el-border-color:#dce3ec;--el-border-color-light:#dce3ec;--el-border-color-lighter:#e9edf3}.projects-heading,.heading-actions,.filter-actions,.project-pagination,.pagination-controls{display:flex;align-items:center;gap:12px}.projects-heading{justify-content:space-between;gap:24px;margin-bottom:24px}.projects-heading h1{font-size:24px;margin:0;color:#243247}.projects-heading p{font-size:14px;color:#526279;margin:8px 0 0}.heading-actions{flex-shrink:0}.projects-workspace :deep(.el-button),.projects-workspace :deep(.el-input__wrapper),.projects-workspace :deep(.el-select__wrapper){border-radius:6px;min-height:38px}.projects-workspace :deep(.el-button.is-link){min-height:24px}.projects-card{border-radius:8px}.projects-card :deep(.el-card__body){padding:22px}.project-filters{display:grid;grid-template-columns:minmax(240px,2fr) minmax(180px,1fr) auto;gap:16px;align-items:end}.project-filters :deep(.el-form-item){margin-bottom:16px}.filter-actions{margin-bottom:16px}.filter-actions .el-button+.el-button,.heading-actions .el-button+.el-button{margin-left:0}.project-name{font-weight:600;color:#175fae}.project-caption{font-size:12px;color:#526279;line-height:1.7;margin:6px 0 0}.unconfirmed{color:#5c6d82}.project-row-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px 12px}.project-version{font-size:13px;line-height:1.8}.project-version dt{color:#526279;margin-top:12px}.project-version dd{margin:0;overflow-wrap:anywhere}.project-pagination{justify-content:space-between;flex-wrap:wrap;gap:16px;padding-top:20px;margin-top:8px;font-size:13px;color:#526279}.pagination-controls{flex-wrap:wrap}.pagination-controls .el-select{width:100px}.project-footer-note{font-size:12px;color:#526279;margin:16px 0}.projects-card :deep(.el-table){border-radius:6px;--el-table-header-bg-color:#f8fafc}.projects-workspace a{color:#175fae}@media(max-width:1000px){.project-filters{grid-template-columns:minmax(0,1fr) minmax(160px,1fr)}.filter-actions{grid-column:1/-1}}@media(max-width:650px){.projects-workspace{padding:16px}.projects-heading{flex-direction:column;align-items:flex-start}.project-filters{grid-template-columns:minmax(0,1fr)}.projects-workspace :deep(.el-button),.projects-workspace :deep(.el-input__wrapper),.projects-workspace :deep(.el-select__wrapper){min-height:44px}.projects-card :deep(.el-card__body){padding:16px}.pagination-controls{gap:8px}}
</style>
