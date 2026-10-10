<template>
  <main class="medical-page">
    <h1>任务当前状态</h1>
    <session-panel />
    <router-link to="/projects">返回项目中心</router-link>
    <router-link v-if="canList" :to="'/projects/' + projectId + '/tasks'">返回项目任务</router-link>
    <p v-if="!validRoute" role="alert">项目或任务标识未通过接口校验</p>
    <p v-else-if="!permitted">读取任务需要当前企业身份及任务读取权限</p>
    <el-button :disabled="!permitted || loading || user.medicalWriting" @click="reload">重新读取任务</el-button>
    <p v-if="loading" role="status">正在核查当前任务修订及获授权关联对象</p>
    <section v-if="current" data-task-detail>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="项目标识">{{ current.detail.project_id }}</el-descriptions-item>
        <el-descriptions-item label="任务标识">{{ current.detail.task_id }}</el-descriptions-item>
        <el-descriptions-item label="固定任务版本">{{ current.detail.task_revision }}</el-descriptions-item>
        <el-descriptions-item label="当前记录修订">{{ current.revision }}</el-descriptions-item>
        <el-descriptions-item label="责任人员">{{ current.detail.owner_id }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">{{ taskStatusLabels[current.detail.status] }}</el-descriptions-item>
        <el-descriptions-item label="已确认计划版本">{{ current.detail.plan_revision ?? '尚未确认计划' }}</el-descriptions-item>
        <el-descriptions-item label="固定来源快照">{{ current.detail.snapshot_id ?? '尚未形成来源快照' }}</el-descriptions-item>
      </el-descriptions>
      <task-plan-editor v-if="session" :session="session" :current="current" :active="routeActive" @saved="reload" />
      <h2>完整缺项与当前许可</h2>
      <el-table :data="current.detail.missing_requirements" row-key="requirement_id" empty-text="当前任务没有输入缺项">
        <el-table-column prop="requirement_id" label="缺项标识" min-width="180" />
        <el-table-column label="关联目录节点" min-width="220"><template #default="scope">{{ scope.row.node_id ?? '未关联目录节点' }}</template></el-table-column>
        <el-table-column label="关联字段" min-width="220"><template #default="scope">{{ scope.row.field_id ?? '未关联字段' }}</template></el-table-column>
        <el-table-column prop="reason_code" label="缺项原因代码" min-width="180" />
        <el-table-column label="当前获准处理动作" min-width="220"><template #default="scope">{{ scope.row.allowed_actions.join('、') || '当前没有获准处理动作' }}</template></el-table-column>
      </el-table>
      <h2>获授权文稿</h2>
      <p v-if="current.detail.document_ids.length === 0">当前没有获授权文稿</p>
      <el-collapse v-else>
        <el-collapse-item v-for="id in current.detail.document_ids" :key="id" :name="id" :title="'文稿 ' + id">
          <document-editor v-if="session" :session="session" :project-id="current.detail.project_id" :document-id="id" :active="routeActive" />
        </el-collapse-item>
      </el-collapse>
      <h2>获授权活动运行</h2>
      <p v-if="current.detail.active_run_ids.length === 0">当前没有获授权活动运行</p>
      <el-collapse v-else>
        <el-collapse-item v-for="id in current.detail.active_run_ids" :key="id" :name="id" :title="'运行 ' + id">
          <run-detail v-if="session" :session="session" :project-id="current.detail.project_id" :task-id="current.detail.task_id" :run-id="id" :active="routeActive" />
        </el-collapse-item>
      </el-collapse>
      <h2>获授权待办</h2>
      <p v-if="current.detail.waiting_item_ids.length === 0">当前没有获授权待办</p>
      <ul v-else><li v-for="id in current.detail.waiting_item_ids" :key="id">{{ id }}</li></ul>
      <p>页面显示本次读取的任务与许可。处理缺项、确认计划和启动运行分别需要对应操作及当前授权。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { projectActionAllowed } from '@/api/medical/project-write'
import { taskIdReference, taskStatusLabels } from '@/api/medical/tasks'
import { readCurrentTask, taskReadAllowed } from '@/api/medical/task-detail'
import type { CurrentTask } from '@/api/medical/task-detail'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import SessionPanel from './session-panel.vue'
import TaskPlanEditor from './task-plan-editor.vue'
import DocumentEditor from './document-editor.vue'
import RunDetail from './run-detail.vue'

const route = useRoute(); const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const taskId = computed(() => typeof route.params.taskId === 'string' ? route.params.taskId : '')
const validRoute = computed(() => schemaIssues(taskIdReference, projectId.value).length === 0 && schemaIssues(taskIdReference, taskId.value).length === 0)
const permitted = computed(() => routeActive.value && validRoute.value && taskReadAllowed(session.value, projectId.value, taskId.value))
const canList = computed(() => validRoute.value && projectActionAllowed(session.value, 'project.task.read', projectId.value))
const current = ref<CurrentTask | null>(null); const loading = ref(false)
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation += 1; controller?.abort(); controller = null; current.value = null; loading.value = false }
const routeActive = useMedicalRouteRead('MedicalTaskDetail', clear)
async function reload(): Promise<void> {
  const actual = session.value; const project = projectId.value; const task = taskId.value; const path = route.fullPath
  if (!actual || !permitted.value || loading.value || user.medicalWriting || document.hidden) return
  clear()
  const reading = new AbortController(); const requested = generation; controller = reading; loading.value = true
  try {
    const result = await readCurrentTask(actual, project, task, reading.signal)
    if (generation === requested && session.value === actual && route.fullPath === path && !document.hidden) current.value = result
  } finally { if (generation === requested) { loading.value = false; controller = null } }
}
watch(() => [session.value, route.fullPath, routeActive.value], () => {
  clear()
  if (permitted.value && !document.hidden) void reload()
}, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility)
window.addEventListener('pagehide', clear); window.addEventListener('offline', clear); window.addEventListener('medical-failure', clear)
onBeforeUnmount(() => {
  clear(); document.removeEventListener('visibilitychange', visibility)
  window.removeEventListener('pagehide', clear); window.removeEventListener('offline', clear); window.removeEventListener('medical-failure', clear)
})
</script>
