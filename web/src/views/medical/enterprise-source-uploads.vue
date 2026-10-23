<template>
  <main class="medical-page">
    <header class="upload-entry__heading"><div><h1>上传企业资料</h1><p>选择使用项目，上传并登记当前组织的企业资料。</p></div><el-tag effect="plain">企业资料库</el-tag></header>
    <session-panel />
    <el-alert title="资料归属当前企业，所选项目用于上传处理。上传完成后可以查看处理进度及人工核准结果。" type="info" :closable="false" show-icon />
    <el-alert v-if="!allowed" title="需要当前企业身份、项目读取及企业来源维护权限。" type="warning" :closable="false" show-icon />
    <template v-else>
      <el-card shadow="never" class="upload-entry__projects"><template #header><div class="upload-entry__heading"><h2>选择使用项目</h2><el-button :loading="loading" @click="read()">刷新项目</el-button></div></template>
      <p v-if="loading" role="status">正在读取当前获授权项目</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <el-table :data="listing?.projects ?? []" row-key="project_id" v-loading="loading" border>
        <el-table-column prop="name" label="项目名称" min-width="240" show-overflow-tooltip />
        <el-table-column label="项目状态" width="130"><template #default="scope"><el-tag type="success" effect="plain">{{ scope.row.status === 'ACTIVE' ? '使用中' : scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right"><template #default="scope"><router-link :to="{ path: '/projects/' + scope.row.project_id + '/uploads', query: { ownership: 'ENTERPRISE' } }"><el-button type="primary" plain>上传企业资料</el-button></router-link></template></el-table-column>
        <template #empty><el-empty description="当前页没有获授权的使用中项目" :image-size="88" /></template>
      </el-table>
      <div class="upload-entry__footer"><span>项目目录根据当前组织及上传权限读取</span><el-button v-if="listing?.next_after" :loading="loading" @click="read(listing?.next_after)">读取下一页</el-button></div></el-card>
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
<style scoped>
.upload-entry__heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.upload-entry__heading h1, .upload-entry__heading h2 { margin: 0 0 8px; }
.upload-entry__heading h2 { font-size: 18px; }
.upload-entry__heading p, .upload-entry__footer { color: var(--el-text-color-secondary); line-height: 1.6; }
.upload-entry__projects { margin-top: 24px; }
.upload-entry__footer { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
@media (max-width: 600px) { .upload-entry__heading, .upload-entry__footer { align-items: flex-start; flex-direction: column; } }
</style>
