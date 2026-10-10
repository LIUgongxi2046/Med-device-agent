<template>
  <main class="medical-page">
    <h1>项目中心</h1>
    <session-panel />
    <router-link v-if="governanceAllowed(user.medicalSession)" to="/enterprises/members">管理企业成员与授权</router-link>
    <router-link v-if="projectActionAllowed(user.medicalSession, 'role.manage', null)" to="/enterprises/roles">管理企业岗位</router-link>
    <router-link v-if="user.medicalSession?.user.platform_role === 'PLATFORM_ADMIN'" to="/enterprises/registrations">企业登记与审查</router-link>
    <el-button :disabled="!projectActionAllowed(user.medicalSession, 'project.create', null) || user.medicalWriting" @click="router.push('/projects/new')">创建项目</el-button>
    <p v-if="user.medicalSession?.current_context && !projectActionAllowed(user.medicalSession, 'project.read')">当前账户没有项目列表读取许可。创建权限不会自动授予项目读取权限。</p>
    <template v-if="projectActionAllowed(user.medicalSession, 'project.read')">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="项目名称"><el-input v-model="name" maxlength="256" clearable /></el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="status" clearable placeholder="全部获授权状态">
            <el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-button native-type="submit" :disabled="loading">查询项目</el-button>
      </el-form>
      <p v-if="loading" role="status">正在读取获授权项目</p>
      <template v-if="listing">
        <p>当前筛选及游标范围内共有{{ listing.total }}个获授权项目</p>
        <el-table :data="listing.projects" row-key="project_id" data-project-table>
          <el-table-column prop="name" label="项目名称" min-width="220">
            <template #default="scope"><router-link :to="'/projects/' + scope.row.project_id">{{ scope.row.name }}</router-link></template>
          </el-table-column>
          <el-table-column label="状态" min-width="120"><template #default="scope">{{ statuses[scope.row.status] }}</template></el-table-column>
          <el-table-column prop="responsible_user_id" label="负责人" min-width="220" />
          <el-table-column prop="revision" label="项目版本" min-width="100" />
          <el-table-column prop="updated_at" label="最近更新（UTC）" min-width="220" />
        </el-table>
        <el-empty v-if="listing.projects.length === 0" description="当前授权及筛选范围没有项目" />
        <el-button :disabled="!listing.next_after || loading" @click="next">读取下一页</el-button>
      </template>
    </template>
  </main>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { listProjects } from '@/api/medical/projects'
import { projectActionAllowed } from '@/api/medical/project-write'
import { governanceAllowed } from '@/api/medical/member-governance'
import SessionPanel from './session-panel.vue'

const user = useUserStore()
const router = useRouter()
const statuses = { PENDING: '建档待确认', ACTIVE: '使用中', PAUSED: '已暂停', ARCHIVED: '已归档' }
const name = ref(''); const status = ref(''); const listing = ref(null); const loading = ref(false)
let controller = null
let generation = 0
function clear() {
  generation += 1
  if (controller) controller.abort()
  controller = null; listing.value = null; loading.value = false
}
async function read(after = null) {
  clear()
  const context = user.medicalSession?.current_context
  if (!context || document.hidden || !projectActionAllowed(user.medicalSession, 'project.read')) return
  const current = generation
  controller = new AbortController()
  loading.value = true
  try {
    const filters = {}
    if (name.value) filters.name = name.value
    if (status.value) filters.status = status.value
    if (after) filters.after = after
    const result = await listProjects(context, filters, controller.signal)
    if (current === generation && user.medicalSession?.current_context === context) listing.value = result
  } finally { if (current === generation) loading.value = false }
}
function search() { return read() }
function next() { return read(listing.value?.next_after) }
watch(() => user.medicalSession, async () => { name.value = ''; status.value = ''; await read() }, { immediate: true })
onBeforeUnmount(clear)
</script>
