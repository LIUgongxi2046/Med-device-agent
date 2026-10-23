<template>
  <main class="app-container medical-page workbench">
    <header class="workbench__header">
      <div class="workbench__brand">
        <img :src="brand.logo" :alt="brand.chineseName + ' Logo'" width="56" height="56">
        <div>
          <p class="workbench__eyebrow">{{ brand.englishName }}</p>
          <h1>{{ brand.chineseName }}</h1>
          <p>查看当前企业的待办事项、重点项目、任务进度和场景入口。</p>
        </div>
      </div>
      <el-button :loading="loading" :disabled="loading || !session?.current_context" @click="load()">刷新工作台</el-button>
    </header>

    <el-card shadow="never" class="workspace-summary">
      <template #header><div class="section-heading"><h2>{{ user.nickName }}，欢迎使用</h2><el-tag :type="isAdministrator ? 'danger' : 'info'">{{ isAdministrator ? '超级管理员' : '当前账户' }}</el-tag></div></template>
      <el-row :gutter="20">
        <el-col :xs="12" :sm="6"><el-statistic title="可用功能" :value="functionCount" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="有效企业空间" :value="activeEnterpriseCount" /></el-col>
        <el-col :xs="24" :sm="12"><p class="workspace-summary__label">当前工作空间</p><p class="workspace-summary__context">{{ session?.current_context?.tenant_id ?? '请选择企业空间，查看项目与任务数据' }}</p></el-col>
      </el-row>
    </el-card>

    <session-panel />

    <section data-workspace-navigation class="workspace-navigation" aria-labelledby="workspace-navigation-title">
      <div class="section-heading"><h2 id="workspace-navigation-title">功能导航</h2><el-tag effect="plain">{{ functionCount }} 项功能</el-tag></div>
      <el-card v-for="group in functionGroups" :key="group.title" shadow="never" class="workspace-navigation__group">
        <template #header><h3>{{ group.title }}</h3></template>
        <el-row :gutter="16">
          <el-col v-for="entry in group.entries" :key="entry.path" :xs="12" :sm="8" :lg="6">
            <router-link :to="entry.path" class="workspace-navigation__link"><el-button text><svg-icon :icon-class="entry.icon" /><span>{{ entry.title }}</span><el-icon><ArrowRight /></el-icon></el-button></router-link>
          </el-col>
        </el-row>
      </el-card>
    </section>

    <el-alert v-if="!session?.current_context" title="请选择企业空间后查看业务工作台" type="info" :closable="false" show-icon />
    <template v-else>
      <p v-if="loading" role="status">正在读取当前项目、待办和场景目录</p>

      <el-row :gutter="16" class="workbench__metrics">
        <el-col v-for="metric in metrics" :key="metric.label" :xs="12" :sm="6">
          <el-card shadow="never" class="metric-card">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </el-card>
        </el-col>
      </el-row>

      <section class="workbench__section" aria-labelledby="today-title">
        <div class="section-heading">
          <div>
            <h2 id="today-title">我的待处理事项</h2>
            <p>仅展示当前人员的待处理事项；每页最多 50 项，不限更新时间。</p>
          </div>
        </div>
        <p v-if="!canReadWorkItems">当前账户没有待办读取许可。</p>
        <el-alert v-else-if="errors.items" :title="errors.items" type="error" :closable="false" />
        <p v-else-if="loading" role="status">正在读取待办</p>
        <el-table v-else-if="workItems?.items.length" :data="workItems.items" row-key="waiting_item_id" data-workbench-items>
          <el-table-column prop="question" label="待办内容" min-width="300" />
          <el-table-column label="项目" min-width="180">
            <template #default="scope">
              <router-link v-if="scope.row.project_id && canReadProject(scope.row.project_id)" :to="'/projects/' + scope.row.project_id">
                {{ projectName(scope.row.project_id) }}
              </router-link>
              <span v-else>{{ scope.row.project_id ? '项目事项' : '企业事项' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="150"><template #default="scope">{{ reasonLabel(scope.row.reason_code) }}</template></el-table-column>
          <el-table-column label="更新时间" min-width="180"><template #default="scope">{{ formatTime(scope.row.updated_at) }}</template></el-table-column>
          <el-table-column label="下一步" min-width="150"><template #default="scope">
            <router-link v-if="scope.row.project_id && canReadTasks(scope.row.project_id)" :to="waitingTaskLocation(scope.row.project_id, scope.row.waiting_item_id, scope.row.run_id)">定位关联任务</router-link>
            <span v-else>请联系企业管理员确认处理范围</span>
          </template></el-table-column>
        </el-table>
        <el-empty v-else-if="workItems" description="本页没有待处理事项，可刷新首页核查最新队列" />
        <div v-if="workItems" class="queue-footer">
          <span>同范围共 {{ workItems.total }} 项；本页 {{ workItems.items.length }} 项</span>
          <el-button :disabled="loading || !workItems.next_after" @click="load(workItems.next_after)">下一页待办</el-button>
          <el-button :disabled="loading" @click="load()">回到首页</el-button>
        </div>
      </section>

      <document-revision-assignment-queue v-if="session?.current_context" :session="session" :active="routeActive" />

      <section class="workbench__section" aria-labelledby="project-title">
        <div class="section-heading">
          <div>
            <h2 id="project-title">重点项目</h2>
            <p>展示当前授权查询首批项目中最近更新的最多 6 个项目。</p>
          </div>
          <router-link to="/projects">查看全部项目</router-link>
        </div>
        <p v-if="!canReadProjects">当前账户没有项目读取许可。</p>
        <el-alert v-else-if="errors.projects" :title="errors.projects" type="error" :closable="false" />
        <p v-else-if="loading" role="status">正在读取项目</p>
        <el-row v-else-if="projectDetails.length" :gutter="16">
          <el-col v-for="project in projectDetails" :key="project.project_id" :xs="24" :md="12" :lg="8">
            <el-card shadow="hover" class="project-card">
              <template #header>
                <div class="project-card__title">
                  <router-link :to="'/projects/' + project.project_id">{{ project.name }}</router-link>
                  <el-tag :type="projectTag(project.status)" effect="plain">{{ projectStatuses[project.status] }}</el-tag>
                </div>
              </template>
              <dl>
                <div><dt>任务</dt><dd>{{ project.children.tasks ?? '未获准读取' }}</dd></div>
                <div><dt>运行中</dt><dd>{{ project.children.active_runs ?? '未获准读取' }}</dd></div>
                <div><dt>待办</dt><dd>{{ project.children.waiting_items ?? '未获准读取' }}</dd></div>
              </dl>
              <p>最近更新：{{ formatTime(project.updated_at) }}</p>
              <router-link v-if="canReadTasks(project.project_id) && project.allowed_actions.includes('project.task.read')" :to="'/projects/' + project.project_id + '/tasks'">查看项目任务</router-link>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else-if="projects" description="当前授权范围没有可展示项目" />
      </section>

      <section class="workbench__section" aria-labelledby="tasks-title">
        <div class="section-heading"><div><h2 id="tasks-title">项目任务进度</h2><p>选择一个获授权项目，查看最近更新的 5 项任务。状态来自实际任务响应。</p></div></div>
        <el-select v-model="selectedProject" aria-label="选择任务预览项目" placeholder="选择项目" :disabled="loading || taskProjects.length === 0" clearable>
          <el-option v-for="project in taskProjects" :key="project.project_id" :value="project.project_id" :label="project.name" />
        </el-select>
        <p v-if="!selectedProject">{{ taskProjects.length ? '请选择项目后读取任务。' : '当前首批项目没有可读取的任务范围。' }}</p>
        <p v-else-if="tasksLoading" role="status">正在读取所选项目任务</p>
        <el-alert v-else-if="taskError" :title="taskError" type="error" :closable="false"><el-button @click="loadTasks">重试任务读取</el-button></el-alert>
        <template v-else-if="tasks">
          <el-table :data="tasks.items" row-key="detail.task_id" empty-text="所选项目还没有任务">
            <el-table-column prop="goal" label="任务目标" min-width="220" />
            <el-table-column label="状态" min-width="140"><template #default="scope">{{ taskStatusLabels[scope.row.detail.status] }}</template></el-table-column>
            <el-table-column label="下一步" min-width="140"><template #default="scope"><router-link v-if="taskReadAllowed(session, selectedProject, scope.row.detail.task_id)" :to="'/projects/' + selectedProject + '/tasks/' + scope.row.detail.task_id + '/config'">查看任务与处理入口</router-link><span v-else>当前没有任务详情读取许可</span></template></el-table-column>
          </el-table>
          <p>所选项目共 {{ tasks.page_info.total }} 项任务；<router-link :to="'/projects/' + selectedProject + '/tasks'">查看全部任务</router-link></p>
        </template>
      </section>

      <section class="workbench__section" aria-labelledby="scenario-title">
        <div class="section-heading">
          <div>
            <h2 id="scenario-title">场景 Agent</h2>
            <p>注册申报资料为当前主要业务入口，其余场景按照目录中的规划状态展示。</p>
          </div>
          <router-link to="/scenarios">查看场景目录</router-link>
        </div>
        <el-alert v-if="errors.scenarios" :title="errors.scenarios" type="error" :closable="false" />
        <el-row :gutter="16">
          <el-col v-for="scenario in scenarioEntries" :key="scenario.id" :xs="24" :sm="12" :lg="8">
            <el-card shadow="never" class="scenario-card">
              <div class="scenario-card__heading">
                <h3>{{ scenario.name }}</h3>
                <el-tag :type="scenario.available ? 'success' : 'info'" effect="plain">{{ scenario.available ? '可进入' : '规划中' }}</el-tag>
              </div>
              <p>{{ scenario.purpose }}</p>
              <router-link :to="scenario.path">{{ scenario.available ? '进入项目中心' : '查看规划要求' }}</router-link>
            </el-card>
          </el-col>
        </el-row>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts" name="Index">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { listProjects, listWorkItems, readProject } from '@/api/medical/projects'
import type { WorkItemListing } from '@/api/medical/projects'
import { listScenarios } from '@/api/medical/scenarios'
import type { PlannedScenario, ScenarioPage } from '@/api/medical/scenarios'
import type { Listing, MedicalSession, Project } from '@/api/medical/protocol'
import { projectActionAllowed } from '@/api/medical/project-write'
import { listTasks, taskStatusLabels } from '@/api/medical/tasks'
import { taskReadAllowed } from '@/api/medical/task-detail'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import type { TaskPage } from '@/api/medical/tasks'
import { workbenchCount, recentWorkbenchProjects } from '@/utils/medical-workbench'
import { waitingTaskLocation } from '@/utils/medical-task-context'
import DocumentRevisionAssignmentQueue from '@/views/medical/document-revision-assignment-queue.vue'
import SessionPanel from '@/views/medical/session-panel.vue'
import { brand } from '../../branding.mjs'
import { ArrowRight } from '@element-plus/icons-vue'
import usePermissionStore from '@/store/modules/permission'
import { medicalNavigationEntries } from '@/components/Medical/navigation'

const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const permission = usePermissionStore()
const isAdministrator = computed(() => user.roles.includes('admin') && user.permissions.includes('*:*:*'))
const activeEnterpriseCount = computed(() => session.value?.memberships.filter(member => member.status === 'ACTIVE').length ?? 0)
const functionGroups = computed(() => medicalNavigationEntries(permission.routes))
const functionCount = computed(() => functionGroups.value.reduce((total: number, group: { entries: unknown[] }) => total + group.entries.length, 0))
const loading = ref(false)
const projects = ref<Listing | null>(null)
const projectDetails = ref<Project[]>([])
const workItems = ref<WorkItemListing | null>(null)
const scenarios = ref<ScenarioPage | null>(null)
const projectStatuses: Record<Project['status'], string> = { PENDING: '建档待确认', ACTIVE: '使用中', PAUSED: '已暂停', ARCHIVED: '已归档' }
const reasonLabels: Record<string, string> = {
  SOURCE_MISSING: '资料待补充', HUMAN_DECISION_REQUIRED: '需要人工决定', BUDGET_EXCEEDED: '需要追加预算',
  REVIEW_REQUIRED: '等待人工审查', APPROVAL_REQUIRED: '等待核准', SIGNATURE_REQUIRED: '等待签发'
}

let generation = 0
let controller: AbortController | null = null
let queueSession: MedicalSession | null = null

const errors = ref({ projects: '', items: '', scenarios: '' })
const canReadProjects = computed(() => projectActionAllowed(session.value, 'project.read'))
const canReadWorkItems = computed(() => projectActionAllowed(session.value, 'work_item.read'))
const canReadProject = (id: string) => projectActionAllowed(session.value, 'project.read', id)
const canReadTasks = (id: string) => projectActionAllowed(session.value, 'project.task.read', id)
const selectedProject = ref('')
const tasks = ref<TaskPage | null>(null)
const tasksLoading = ref(false)
const taskError = ref('')
let taskController: AbortController | null = null
const taskProjects = computed(() => (projects.value?.projects ?? []).filter(project => canReadTasks(project.project_id) && project.allowed_actions.includes('project.task.read')))
const metrics = computed(() => [
  { label: '获授权项目', value: projects.value?.total ?? '未读取' },
  { label: '我的待处理事项', value: workItems.value?.total ?? '未读取' },
  { label: '展示项目任务数', value: projects.value ? workbenchCount(projectDetails.value, 'tasks') : '未读取' },
  { label: '展示项目运行数', value: projects.value ? workbenchCount(projectDetails.value, 'active_runs') : '未读取' }
])
const scenarioEntries = computed(() => {
  const primary = { id: 'registration-dossier', name: '注册申报资料', purpose: '创建项目任务，组织核准资料并形成注册申报文稿。', path: '/projects', available: true }
  const planned = (scenarios.value?.planned_requirements ?? []).map((item: PlannedScenario) => ({
    id: item.scenario_id, name: item.name, purpose: item.purpose, path: '/scenarios', available: false
  }))
  return [primary, ...planned]
})

function projectName(projectId: string): string {
  return projectDetails.value.find(project => project.project_id === projectId)?.name ?? '查看关联项目'
}
function reasonLabel(reason: string): string { return reasonLabels[reason] ?? reason }
function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
function projectTag(status: Project['status']): 'success' | 'warning' | 'info' | 'danger' {
  return status === 'ACTIVE' ? 'success' : status === 'PENDING' ? 'warning' : status === 'PAUSED' ? 'danger' : 'info'
}
function clear(): void {
  generation += 1
  controller?.abort()
  controller = null
  loading.value = false
  projects.value = null
  projectDetails.value = []
  taskController?.abort()
  taskController = null
  selectedProject.value = ''
  tasks.value = null
  tasksLoading.value = false
  taskError.value = ''
  errors.value = { projects: '', items: '', scenarios: '' }
  queueSession = null
  workItems.value = null
  scenarios.value = null
}
const routeActive = useMedicalRouteRead('Index', clear)
async function load(after?: string | null): Promise<void> {
  const actual = session.value
  // A queued click must never carry a cursor across session changes or newer reads.
  if (after && (loading.value || queueSession !== actual || workItems.value?.next_after !== after)) return
  clear()
  if (!actual?.current_context || !routeActive.value || document.hidden) return
  const currentGeneration = generation
  const reading = new AbortController()
  controller = reading
  loading.value = true
  const current = () => routeActive.value && currentGeneration === generation && session.value === actual && !reading.signal.aborted && !document.hidden
  async function section<T>(key: keyof typeof errors.value, fetch: () => Promise<T>, apply: (value: T) => void): Promise<void> {
    try { const value = await fetch(); if (current()) apply(value) }
    catch { if (current()) errors.value[key] = '读取未完成，可能是权限或连接已变化。请刷新工作台或重新核查会话。' }
  }
  try {
    await Promise.all([
      canReadProjects.value ? section('projects', async () => {
        const listing = await listProjects(actual.current_context!, {}, reading.signal)
        const details = await Promise.all(recentWorkbenchProjects(listing.projects).map(project => readProject(actual.current_context!, project.project_id, reading.signal)))
        return { listing, details }
      }, value => { projects.value = value.listing; projectDetails.value = value.details }) : Promise.resolve(),
      canReadWorkItems.value ? section('items', () => listWorkItems(actual, { limit: 50, status: 'WAITING_INPUT', assignee_id: actual.user.user_id, ...(after ? { after } : {}) }, reading.signal), value => { workItems.value = value; queueSession = actual }) : Promise.resolve(),
      section('scenarios', () => listScenarios(actual, { limit: 50 }, null, reading.signal), value => { scenarios.value = value })
    ])
  } finally {
    if (currentGeneration === generation) { loading.value = false; controller = null }
  }
}
async function loadTasks(): Promise<void> {
  taskController?.abort()
  const reading = new AbortController(); taskController = reading
  tasks.value = null; taskError.value = ''; tasksLoading.value = false
  const actual = session.value, id = selectedProject.value
  if (!actual?.current_context || !routeActive.value || !id || !taskProjects.value.some(project => project.project_id === id) || document.hidden) return
  tasksLoading.value = true
  const current = () => routeActive.value && taskController === reading && !reading.signal.aborted && session.value === actual && selectedProject.value === id && !document.hidden
  try {
    const page = await listTasks(actual, id, { page: 1, page_size: 5 }, null, reading.signal)
    if (current()) tasks.value = page
  } catch { if (current()) taskError.value = '任务读取未完成，请核查当前项目权限或重试。' }
  finally { if (taskController === reading) tasksLoading.value = false }
}
watch(selectedProject, loadTasks)
async function visibilityChanged(): Promise<void> {
  if (document.hidden) clear()
  else await load()
}

watch([session, routeActive], () => load(), { immediate: true, flush: 'sync' })
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => { document.removeEventListener('visibilitychange', visibilityChanged); clear() })
</script>

<style scoped lang="scss">
.queue-footer { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 12px; }
.workbench {
  max-width: none;
  padding: 20px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
}
.workspace-summary, .workspace-context, .workspace-navigation__group { margin-bottom: 20px; }
.workspace-summary h2, .workspace-context h2, .workspace-navigation h2, .workspace-navigation h3 { margin: 0; }
.workspace-summary .section-heading { margin-bottom: 0; }
.workspace-summary__label { margin: 0 0 8px; color: var(--el-text-color-regular); }
.workspace-summary__context { margin: 0; overflow-wrap: anywhere; }
.workspace-summary :deep(.el-col) { margin-top: 8px; margin-bottom: 8px; }
.workspace-navigation__link { display: block; margin-bottom: 8px; }
.workspace-navigation__link .el-button { width: 100%; height: auto; justify-content: flex-start; min-height: 44px; white-space: normal; text-align: left; color: var(--el-text-color-primary); }
.workspace-navigation__link :deep(.el-button > span) { display: flex; width: 100%; gap: 10px; align-items: center; }
.workspace-navigation__link :deep(.el-button > span > span) { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.workspace-navigation__link :deep(.svg-icon), .workspace-navigation__link :deep(.el-icon) { flex-shrink: 0; }
.workspace-navigation__link :deep(.el-icon) { margin-left: auto; color: var(--el-text-color-secondary); }
.workbench__header,
.section-heading,
.project-card__title,
.scenario-card__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.workbench__header {
  margin-bottom: 20px;
}
.workbench__brand { display: flex; align-items: center; gap: 16px; min-width: 0; }
.workbench__brand > img { flex: 0 0 56px; object-fit: contain; border-radius: 12px; background: #fff; }
.workbench__brand > div { min-width: 0; overflow-wrap: anywhere; }
.workbench__header h1,
.workbench__section h2,
.scenario-card h3 {
  margin: 0;
}
.workbench__eyebrow {
  margin: 0 0 6px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.workbench__metrics {
  margin-top: 20px;
}
.metric-card,
.project-card,
.scenario-card {
  margin-bottom: 16px;
}
.metric-card span,
.metric-card strong {
  display: block;
}
.metric-card span {
  color: var(--el-text-color-regular);
}
.metric-card strong {
  margin-top: 8px;
  font-size: 28px;
}
.workbench__section {
  margin-top: 28px;
}
.section-heading {
  margin-bottom: 14px;
}
.section-heading p,
.scenario-card p,
.project-card p {
  color: var(--el-text-color-regular);
}
.project-card dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
}
.project-card dl div {
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.project-card dt {
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.project-card dd {
  margin: 4px 0 0;
  font-weight: 700;
}
@media (max-width: 767px) {
  .workbench__header,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
