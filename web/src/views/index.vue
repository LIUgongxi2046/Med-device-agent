<template>
  <main class="medical-page workbench">
    <header class="workbench__header">
      <div>
        <p class="workbench__eyebrow">MEDICAL DEVICE AGENT</p>
        <h1>医疗业务工作台</h1>
        <p>查看当前企业的待办事项、重点项目、任务进度和场景入口。</p>
      </div>
      <el-button :loading="loading" :disabled="loading || !session?.current_context" @click="load">刷新工作台</el-button>
    </header>

    <session-panel />

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
            <h2 id="today-title">今日更新待办</h2>
            <p>当前人员共有{{ workItems?.total ?? '0' }}项待处理事项，其中{{ todayItems.length }}项今天更新。</p>
          </div>
        </div>
        <el-table v-if="todayItems.length" :data="todayItems" row-key="waiting_item_id" data-workbench-items>
          <el-table-column prop="question" label="待办内容" min-width="300" />
          <el-table-column label="项目" min-width="180">
            <template #default="scope">
              <router-link v-if="scope.row.project_id" :to="'/projects/' + scope.row.project_id">
                {{ projectName(scope.row.project_id) }}
              </router-link>
              <span v-else>企业事项</span>
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="150"><template #default="scope">{{ reasonLabel(scope.row.reason_code) }}</template></el-table-column>
          <el-table-column label="更新时间" min-width="180"><template #default="scope">{{ formatTime(scope.row.updated_at) }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="今天没有新增或更新的待办事项" />
      </section>

      <section class="workbench__section" aria-labelledby="project-title">
        <div class="section-heading">
          <div>
            <h2 id="project-title">重点项目</h2>
            <p>按照最近更新时间展示当前获授权项目。</p>
          </div>
          <router-link to="/projects">查看全部项目</router-link>
        </div>
        <el-row v-if="projectDetails.length" :gutter="16">
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
              <router-link v-if="project.allowed_actions.includes('project.task.read')" :to="'/projects/' + project.project_id + '/tasks'">查看项目任务</router-link>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="当前授权范围没有可展示项目" />
      </section>

      <section class="workbench__section" aria-labelledby="scenario-title">
        <div class="section-heading">
          <div>
            <h2 id="scenario-title">场景 Agent</h2>
            <p>注册申报资料为当前主要业务入口，其余场景按照目录中的规划状态展示。</p>
          </div>
          <router-link to="/scenarios">查看场景目录</router-link>
        </div>
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
import type { Project, WorkItem, WorkItemListing } from '@/api/medical/projects'
import { listScenarios } from '@/api/medical/scenarios'
import type { PlannedScenario, ScenarioPage } from '@/api/medical/scenarios'
import type { Listing, MedicalSession } from '@/api/medical/protocol'
import { projectActionAllowed } from '@/api/medical/project-write'
import SessionPanel from '@/views/medical/session-panel.vue'

const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
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

const todayItems = computed<WorkItem[]>(() => {
  const now = new Date()
  return (workItems.value?.items ?? []).filter(item => {
    const updated = new Date(item.updated_at)
    return updated.getFullYear() === now.getFullYear() && updated.getMonth() === now.getMonth() && updated.getDate() === now.getDate()
  })
})
const metrics = computed(() => [
  { label: '获授权项目', value: projects.value?.total ?? '0' },
  { label: '当前人员待办', value: workItems.value?.total ?? '0' },
  { label: '重点项目任务', value: sumProjectCount('tasks') },
  { label: '重点项目运行中', value: sumProjectCount('active_runs') }
])
const scenarioEntries = computed(() => {
  const primary = { id: 'registration-dossier', name: '注册申报资料', purpose: '创建项目任务，组织核准资料并形成注册申报文稿。', path: '/projects', available: true }
  const planned = (scenarios.value?.planned_requirements ?? []).map((item: PlannedScenario) => ({
    id: item.scenario_id, name: item.name, purpose: item.purpose, path: '/scenarios', available: false
  }))
  return [primary, ...planned]
})

function sumProjectCount(field: keyof Project['children']): string {
  let total = 0n
  for (const project of projectDetails.value) {
    const value = project.children[field]
    if (value !== null) total += BigInt(value)
  }
  return total.toString()
}
function projectName(projectId: string): string {
  return projectDetails.value.find(project => project.project_id === projectId)?.name ?? projectId
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
  workItems.value = null
  scenarios.value = null
}
async function load(): Promise<void> {
  clear()
  const actual = session.value
  if (!actual?.current_context || document.hidden) return
  const currentGeneration = generation
  const reading = new AbortController()
  controller = reading
  loading.value = true
  const canReadProjects = projectActionAllowed(actual, 'project.read')
  const canReadWorkItems = projectActionAllowed(actual, 'work_item.read')
  try {
    const [projectListing, itemListing, scenarioPage] = await Promise.all([
      canReadProjects ? listProjects(actual.current_context, {}, reading.signal) : Promise.resolve(null),
      canReadWorkItems ? listWorkItems(actual, { limit: 50, status: 'WAITING_INPUT' }, reading.signal) : Promise.resolve(null),
      listScenarios(actual, { limit: 50 }, null, reading.signal)
    ])
    const details = projectListing === null ? [] : await Promise.all(projectListing.projects.slice(0, 6)
      .map(project => readProject(actual.current_context!, project.project_id, reading.signal)))
    if (currentGeneration === generation && session.value === actual && !document.hidden) {
      projects.value = projectListing
      projectDetails.value = details
      workItems.value = itemListing
      scenarios.value = scenarioPage
    }
  } finally {
    if (currentGeneration === generation) {
      loading.value = false
      controller = null
    }
  }
}
async function visibilityChanged(): Promise<void> {
  if (document.hidden) clear()
  else await load()
}

watch(session, load, { immediate: true })
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => { document.removeEventListener('visibilitychange', visibilityChanged); clear() })
</script>

<style scoped lang="scss">
.workbench {
  color: #263445;
}
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
  color: #606266;
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
  color: #606266;
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
  background: #f5f7fa;
}
.project-card dt {
  color: #606266;
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
