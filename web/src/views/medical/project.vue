<template>
  <main class="medical-page project-overview">
    <div class="project-breadcrumb">
      <el-breadcrumb separator="/"><el-breadcrumb-item :to="{ path: '/projects' }">项目中心</el-breadcrumb-item><el-breadcrumb-item>项目详情</el-breadcrumb-item></el-breadcrumb>
      <div class="project-actions"><el-button v-if="canConfigurePackage" :disabled="user.medicalWriting" @click="go('/task-professional-packages')">登记研究配置来源包</el-button><el-button @click="router.push('/projects')">返回项目</el-button><el-button v-if="project && canCreateTask" type="primary" :disabled="user.medicalWriting || project.status !== 'ACTIVE'" @click="go('/tasks/new')">创建文档任务</el-button></div>
    </div>
    <session-panel />
    <p v-if="loading" role="status">正在核查当前项目读取许可</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="read">重新读取项目</el-button></el-alert>
    <section v-if="project && user.medicalSession" data-project-detail>
      <header class="project-heading">
        <div><span class="eyebrow">项目概览</span><h1>{{ project.name }}</h1><p>{{ project.creation_reason }}</p></div>
        <el-tag :type="project.status === 'ACTIVE' ? 'success' : 'info'" effect="light">{{ statuses[project.status] }}</el-tag>
      </header>
      <div class="project-metrics" aria-label="项目当前统计">
        <el-card shadow="never"><span>文档任务</span><strong>{{ project.children.tasks ?? '未获准读取' }}</strong><router-link v-if="canReadTasks" :to="basePath + '/tasks'">查看任务</router-link></el-card>
        <el-card shadow="never"><span>活动运行</span><strong>{{ project.children.active_runs ?? '未获准读取' }}</strong><span class="metric-note">当前获授权运行数量</span></el-card>
        <el-card shadow="never"><span>项目待办</span><strong>{{ project.children.waiting_items ?? '未获准读取' }}</strong><a v-if="canReadWaiting" href="#project-waiting">查看待办</a></el-card>
      </div>
      <section class="project-stages" aria-label="项目办理入口">
        <div class="section-heading"><h2>项目办理</h2><span>各阶段状态以实际任务与人工核验结果为准</span></div>
        <div class="stage-grid">
          <el-card v-for="(stage, index) in stages" :key="stage.title" shadow="never">
            <span class="stage-number">{{ index + 1 }}</span><h3>{{ stage.title }}</h3><p>{{ stage.description }}</p>
            <el-button :disabled="stage.disabled || user.medicalWriting" @click="router.push(stage.target)">{{ stage.action }}</el-button>
          </el-card>
        </div>
      </section>
      <div class="project-columns">
        <div>
          <el-card shadow="never" class="project-panel">
            <template #header><div class="section-heading"><h2>项目属性</h2><el-button v-if="project.allowed_actions.includes('profile.confirm') && ['PENDING', 'ACTIVE'].includes(project.status)" text type="primary" :disabled="user.medicalWriting" @click="go('/profile')">修订属性</el-button></div></template>
            <el-table :data="project.profile.attributes" row-key="key" empty-text="当前项目尚未返回属性">
              <el-table-column prop="key" label="属性名称" min-width="150" />
              <el-table-column label="核查状态" min-width="120"><template #default="scope"><el-tag :type="scope.row.status === 'UNKNOWN' ? 'warning' : 'info'">{{ scope.row.status === 'UNKNOWN' ? '尚未确认' : '已有声明依据' }}</el-tag></template></el-table-column>
              <el-table-column label="原始值" min-width="200"><template #default="scope">{{ scope.row.status === 'UNKNOWN' ? '尚未确认' : formatProjectJson(scope.row.value) }}</template></el-table-column>
              <el-table-column prop="reason" label="说明" min-width="180" />
            </el-table>
          </el-card>
          <el-card id="project-waiting" shadow="never" class="project-panel">
            <template #header><div class="section-heading"><h2>项目待办</h2><el-button v-if="canReadWaiting" text :loading="waitingLoading" @click="readWaiting()">刷新</el-button></div></template>
            <p v-if="!canReadWaiting">当前账户没有本项目待办读取许可。</p>
            <p v-else-if="waitingLoading" role="status">正在读取项目待办</p>
            <el-alert v-if="waitingError" :title="waitingError" type="error" :closable="false" />
            <template v-if="waiting">
              <el-table :data="waiting.items" row-key="waiting_item_id" empty-text="当前项目没有待处理事项">
                <el-table-column prop="question" label="待处理事项" min-width="240" />
                <el-table-column prop="reason_code" label="原因" min-width="140" />
                <el-table-column prop="updated_at" label="更新时间（UTC）" min-width="200" />
                <el-table-column label="处理入口" min-width="170"><template #default="scope"><el-button v-if="canReadTasks" :disabled="user.medicalWriting || waitingLoading" @click="locateWaiting(scope.row)">定位关联任务</el-button><span v-else>未获准读取关联任务</span></template></el-table-column>
              </el-table>
              <p class="metric-note">当前查询共 {{ waiting.total }} 项；处理入口位于关联任务的运行工作区。</p>
              <el-button v-if="canReadTasks" @click="go('/tasks')">进入项目任务处理</el-button>
              <el-button :disabled="!waiting.next_after || waitingLoading" @click="readWaiting(waiting.next_after)">下一页</el-button>
            </template>
          </el-card>
        </div>
        <aside aria-label="资料与项目管理">
          <el-card shadow="never" class="project-panel"><template #header><h2>资料与证据</h2></template>
            <p>先核查资料来源、版本与使用权利，再用于文档任务。项目接口尚未提供完整度统计。</p>
            <div class="resource-links"><router-link :to="libraryTarget">查看项目资料</router-link><router-link :to="{ path: '/library/search', query: { project_id: project.project_id } }">检索与核验</router-link><router-link :to="{ path: '/library/artifacts', query: { project_id: project.project_id } }">查看任务成果</router-link></div>
            <el-button v-if="project.status === 'ACTIVE' && sourceUploadAllowed(user.medicalSession, project.project_id)" type="primary" plain :disabled="user.medicalWriting" @click="go('/uploads')">上传项目资料</el-button>
          </el-card>
          <el-card shadow="never" class="project-panel"><template #header><h2>申请资料清单</h2></template><p>清单应由适用模板及项目条件确定。当前项目概览接口未提供逐节点完整度，不推算完成比例。</p><el-button v-if="project.allowed_actions.includes('project.read')" :disabled="user.medicalWriting" @click="go('/templates')">查询适用模板</el-button></el-card>
          <el-card shadow="never" class="project-panel"><template #header><h2>项目管理</h2></template><p>最近更新：{{ project.updated_at }}</p><el-button v-if="project.allowed_actions.includes('project.manage')" :disabled="user.medicalWriting" @click="go('/status')">决定项目状态</el-button></el-card>
        </aside>
      </div>
      <el-collapse class="project-technical"><el-collapse-item title="版本、依据与授权详情" name="provenance">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="项目标识">{{ project.project_id }}</el-descriptions-item>
          <el-descriptions-item label="负责人标识">{{ project.responsible_user_id }}</el-descriptions-item>
          <el-descriptions-item label="项目版本">{{ project.revision }}</el-descriptions-item>
          <el-descriptions-item label="属性版本">{{ project.profile.profile_revision }}</el-descriptions-item>
          <el-descriptions-item label="建档依据版本">{{ project.creation_basis.version_id }}</el-descriptions-item>
          <el-descriptions-item label="依据内容摘要">{{ project.creation_basis.content_sha256 }}</el-descriptions-item>
          <el-descriptions-item label="属性内容摘要">{{ project.profile.content_sha256 }}</el-descriptions-item>
          <el-descriptions-item label="当前项目动作">{{ project.allowed_actions.join('、') }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="project.profile.authority_refs" row-key="version_id"><el-table-column prop="version_id" label="属性依据版本" min-width="260" /><el-table-column prop="content_sha256" label="内容摘要" min-width="320" /></el-table>
      </el-collapse-item></el-collapse>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { readProject, listWorkItems } from '@/api/medical/projects'
import { capabilityAllowed } from '@/api/medical/formal-capability-domain'
import { projectActionAllowed } from '@/api/medical/project-write'
import { sourceUploadAllowed } from '@/api/medical/source-uploads'
import { formatProjectJson } from '@/api/medical/project-json'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { waitingTaskLocation, readWaitingTaskContext } from '@/utils/medical-task-context'
import SessionPanel from './session-panel.vue'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const project = ref(null); const loading = ref(false); const error = ref('')
const waiting = ref(null); const waitingLoading = ref(false); const waitingError = ref('')
const statuses = { PENDING: '建档待确认', ACTIVE: '使用中', PAUSED: '已暂停', ARCHIVED: '已归档' }
const basePath = computed(() => '/projects/' + project.value?.project_id)
const canReadTasks = computed(() => project.value?.allowed_actions.includes('project.task.read') && projectActionAllowed(user.medicalSession, 'project.task.read', project.value.project_id))
const canConfigurePackage = computed(() => project.value && capabilityAllowed(user.medicalSession, project.value.project_id))
const canCreateTask = computed(() => project.value && projectActionAllowed(user.medicalSession, 'task.create', project.value.project_id))
const canReadWaiting = computed(() => project.value && projectActionAllowed(user.medicalSession, 'work_item.read', project.value.project_id))
const libraryTarget = computed(() => ({ path: '/library', query: { project_id: project.value?.project_id } }))
const stages = computed(() => [
  { title: '确认项目', description: '核查产品属性与建档依据', action: '查看项目属性', target: basePath.value + '/profile', disabled: !project.value?.allowed_actions.includes('profile.confirm') || !['PENDING', 'ACTIVE'].includes(project.value?.status) },
  { title: '准备资料', description: '整理原件、核验来源与权利', action: '进入资料库', target: libraryTarget.value, disabled: false },
  { title: '文档任务', description: '配置任务、撰写并核验正文', action: '进入任务列表', target: basePath.value + '/tasks', disabled: !canReadTasks.value },
  { title: '审查与交付', description: '在具体任务中审查、签发及导出', action: '选择文档任务', target: basePath.value + '/tasks', disabled: !canReadTasks.value }
])
let generation = 0; let controller = null; let waitingController = null
function go(suffix) { router.push(basePath.value + suffix) }
function locateWaiting(item) {
  const id = project.value?.project_id
  if (!active.value || document.hidden || user.medicalWriting || waitingLoading.value || !id
      || !canReadWaiting.value || !canReadTasks.value || !waiting.value?.items.includes(item)
      || item.project_id !== id || route.params.projectId !== id) return
  const context = readWaitingTaskContext({ waiting_item_id: item.waiting_item_id, run_id: item.run_id })
  if (context.invalid || !context.waitingItemId || !context.runId) return
  router.push(waitingTaskLocation(id, context.waitingItemId, context.runId))
}
function clear() {
  generation += 1; controller?.abort(); waitingController?.abort()
  controller = null; waitingController = null; project.value = null; loading.value = false; error.value = ''
  waiting.value = null; waitingLoading.value = false; waitingError.value = ''
}
const active = useMedicalRouteRead('MedicalProject', clear)
async function read() {
  clear()
  const context = user.medicalSession?.current_context; const id = route.params.projectId
  if (!active.value || !context || document.hidden) return
  const current = generation; const reading = new AbortController(); controller = reading; loading.value = true
  try {
    const result = await readProject(context, id, reading.signal)
    if (current !== generation || !active.value || user.medicalSession?.current_context !== context || route.params.projectId !== id || document.hidden) return
    project.value = result
    if (canReadWaiting.value) await readWaiting()
  } catch (cause) { if (current === generation && !reading.signal.aborted) error.value = cause?.message || '项目读取失败，请重新核查会话后重试' }
  finally { if (current === generation) loading.value = false }
}
async function readWaiting(after) {
  const session = user.medicalSession; const id = project.value?.project_id; const current = generation
  if (!active.value || !session || !id || !canReadWaiting.value || document.hidden) return
  waitingController?.abort(); const reading = new AbortController(); waitingController = reading; waitingLoading.value = true; waitingError.value = ''; waiting.value = null
  try {
    const result = await listWorkItems(session, { limit: 10, project_id: id, status: 'WAITING_INPUT', ...(after ? { after } : {}) }, reading.signal)
    if (current === generation && waitingController === reading && !reading.signal.aborted && active.value && user.medicalSession === session && !document.hidden) waiting.value = result
  } catch (cause) { if (current === generation && !reading.signal.aborted) waitingError.value = cause?.message || '待办读取失败，请重试' }
  finally { if (waitingController === reading) waitingLoading.value = false }
}
watch(() => [user.medicalSession, route.params.projectId, active.value], read, { immediate: true })
onBeforeUnmount(clear)
</script>
<style scoped>
.project-overview { container-type: inline-size; }
.project-breadcrumb, .project-heading, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.project-breadcrumb { margin-bottom: 20px; }
.project-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.project-actions .el-button + .el-button { margin-left: 0; }
.project-heading { margin: 24px 0; align-items: flex-start; }
.project-heading h1 { margin: 6px 0 8px; }
.project-heading p { color: #526279; max-width: 780px; margin: 0; }
.eyebrow, .metric-note, .section-heading > span { color: #526279; font-size: 12px; }
.project-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }
.project-metrics :deep(.el-card__body) { display: flex; flex-direction: column; gap: 10px; }
.project-metrics strong { font-size: 26px; font-weight: 600; overflow-wrap: anywhere; }
.project-stages { background: #F8FAFC; padding: 22px; border: 1px solid #DCE3EC; border-radius: 8px; }
.section-heading h2, .project-panel h2 { margin: 0; font-size: 17px; }
.stage-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
.stage-number { display: inline-grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: #EDF4FF; color: #175FAE; font-weight: 600; }
.stage-grid h3 { margin: 12px 0 6px; font-size: 15px; }
.stage-grid p, .project-panel p { color: #526279; font-size: 13px; line-height: 1.7; }
.project-columns { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 24px; }
.project-columns > * { min-width: 0; }
.project-panel { margin-bottom: 20px; }
.resource-links { display: flex; flex-direction: column; gap: 14px; margin: 16px 0; }
.project-technical { margin-top: 8px; }
@container (max-width: 860px) { .project-columns { grid-template-columns: 1fr; } .stage-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@container (max-width: 620px) { .project-metrics, .stage-grid { grid-template-columns: 1fr; } .project-stages { padding: 16px; } }
</style>
