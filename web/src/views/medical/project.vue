<template>
  <main class="medical-page">
    <h1>项目详情</h1>
    <session-panel />
    <p v-if="loading" role="status">正在核查当前项目读取许可</p>
    <section v-if="project && user.medicalSession" data-project-detail>
      <h2>{{ project.name }}</h2>
      <el-button v-if="project.allowed_actions.includes('project.read')" :disabled="user.medicalWriting" @click="router.push('/projects/' + project.project_id + '/templates')">查询适用模板</el-button>
      <el-button v-if="project.status === 'ACTIVE' && sourceUploadAllowed(user.medicalSession, project.project_id)" :disabled="user.medicalWriting" @click="router.push('/projects/' + project.project_id + '/uploads')">上传项目资料</el-button>
      <el-button v-if="project.allowed_actions.includes('project.task.read')" :disabled="user.medicalWriting" @click="router.push('/projects/' + project.project_id + '/tasks')">阅读项目任务</el-button>
      <el-button v-if="project.allowed_actions.includes('profile.confirm') && ['PENDING', 'ACTIVE'].includes(project.status)" :disabled="user.medicalWriting" @click="router.push('/projects/' + project.project_id + '/profile')">修订项目属性</el-button>
      <el-button v-if="project.allowed_actions.includes('project.manage')" :disabled="user.medicalWriting" @click="router.push('/projects/' + project.project_id + '/status')">决定项目状态</el-button>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="项目状态">{{ statuses[project.status] }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ project.responsible_user_id }}</el-descriptions-item>
        <el-descriptions-item label="项目版本">{{ project.revision }}</el-descriptions-item>
        <el-descriptions-item label="属性版本">{{ project.profile.profile_revision }}</el-descriptions-item>
        <el-descriptions-item label="任务数量">{{ project.children.tasks ?? '未获准读取' }}</el-descriptions-item>
        <el-descriptions-item label="活动运行数量">{{ project.children.active_runs ?? '未获准读取' }}</el-descriptions-item>
        <el-descriptions-item label="待办数量">{{ project.children.waiting_items ?? '未获准读取' }}</el-descriptions-item>
        <el-descriptions-item label="建档原因">{{ project.creation_reason }}</el-descriptions-item>
        <el-descriptions-item label="建档依据版本">{{ project.creation_basis.version_id }}</el-descriptions-item>
        <el-descriptions-item label="依据内容摘要">{{ project.creation_basis.content_sha256 }}</el-descriptions-item>
      </el-descriptions>
      <h2>当前项目属性</h2>
      <el-table :data="project.profile.attributes" row-key="key">
        <el-table-column prop="key" label="属性名称" min-width="160" />
        <el-table-column label="核查状态"><template #default="scope">{{ scope.row.status === 'UNKNOWN' ? '尚未确认' : '已有声明依据' }}</template></el-table-column>
        <el-table-column label="原始值" min-width="220"><template #default="scope"><span v-if="scope.$index >= 0">{{ scope.row.status === 'UNKNOWN' ? '尚未确认' : formatProjectJson(scope.row.value) }}</span></template></el-table-column>
        <el-table-column prop="reason" label="说明" min-width="220" />
      </el-table>
      <p>属性内容摘要：{{ project.profile.content_sha256 }}</p>
      <el-table :data="project.profile.authority_refs" row-key="version_id">
        <el-table-column prop="version_id" label="属性依据版本" min-width="260" />
        <el-table-column prop="content_sha256" label="内容摘要" min-width="320" />
      </el-table>
      <p>当前项目动作：{{ project.allowed_actions.join('、') }}</p>
    </section>
  </main>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { readProject } from '@/api/medical/projects'
import { sourceUploadAllowed } from '@/api/medical/source-uploads'
import { formatProjectJson } from '@/api/medical/project-json'
import SessionPanel from './session-panel.vue'

const user = useUserStore()
const route = useRoute()
const router = useRouter()
const project = ref(null); const loading = ref(false)
const statuses = { PENDING: '建档待确认', ACTIVE: '使用中', PAUSED: '已暂停', ARCHIVED: '已归档' }
let generation = 0; let controller = null
function clear() {
  generation += 1
  if (controller) controller.abort()
  controller = null; project.value = null; loading.value = false
}
async function read() {
  clear()
  const context = user.medicalSession?.current_context
  if (!context || document.hidden) return
  const current = generation
  const id = route.params.projectId
  if (typeof id !== 'string') throw new Error('项目路由标识无效')
  controller = new AbortController(); loading.value = true
  try {
    const result = await readProject(context, id, controller.signal)
    if (current === generation && user.medicalSession?.current_context === context && route.params.projectId === id) project.value = result
  } finally { if (current === generation) loading.value = false }
}
watch(() => [user.medicalSession, route.params.projectId], read, { immediate: true })
onBeforeUnmount(clear)
</script>
