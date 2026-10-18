<template>
  <main class="medical-page">
    <h1>上传企业资料</h1>
    <session-panel />
    <p>资料归属企业，所选项目仅用于上传处理。</p>
    <p>上传后可在资料处理和核准页查看进度。</p>
    <p v-if="!allowed" role="alert">需要当前企业身份、项目读取及企业来源维护权限。</p>
    <template v-else>
      <el-button :disabled="loading" @click="read()">重新读取项目</el-button>
      <p v-if="loading" role="status">正在读取当前获授权项目</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <p v-if="listing && !listing.projects.length">当前页没有获授权的使用中项目。</p>
      <ul v-if="listing"><li v-for="project in listing.projects" :key="project.project_id">
        <router-link :to="{ path: '/projects/' + project.project_id + '/uploads', query: { ownership: 'ENTERPRISE' } }">{{ project.name }}：选择此项目</router-link>
      </li></ul>
      <el-button :disabled="loading || !listing?.next_after" @click="read(listing?.next_after)">读取下一页</el-button>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { listProjects } from '@/api/medical/projects'
import { projectActionAllowed } from '@/api/medical/project-write'
import { sourceUploadAllowed } from '@/api/medical/source-uploads'
import type { Listing } from '@/api/medical/protocol'
const user = useUserStore()
const allowed = computed(() => projectActionAllowed(user.medicalSession, 'project.read') && sourceUploadAllowed(user.medicalSession, ''))
const listing = ref<Listing | null>(null); const loading = ref(false); const error = ref('')
let generation = 0; let controller: AbortController | null = null
function clear(): void { generation++; controller?.abort(); controller = null; listing.value = null; loading.value = false; error.value = '' }
const routeActive = useMedicalRouteRead('MedicalEnterpriseSourceUploads', clear)
async function read(after?: string | null): Promise<void> {
  if (after && (loading.value || after !== listing.value?.next_after)) return
  clear()
  const session = user.medicalSession; const context = session?.current_context
  if (!context || !allowed.value || !routeActive.value || document.hidden) return
  const current = generation; const reading = new AbortController(); controller = reading; loading.value = true
  try {
    const value = await listProjects(context, { status: 'ACTIVE', ...(after ? { after } : {}) }, reading.signal)
    if (current !== generation || session !== user.medicalSession || reading.signal.aborted || !routeActive.value || document.hidden) return
    listing.value = value
  } catch { if (current === generation && session === user.medicalSession) error.value = '项目读取失败，请重新读取当前授权范围。' }
  finally { if (current === generation) { loading.value = false; controller = null } }
}
watch(() => [user.medicalSession, allowed.value, routeActive.value], () => { void read() }, { immediate: true, flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility) })
</script>
