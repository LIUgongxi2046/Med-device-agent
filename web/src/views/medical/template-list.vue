<template>
  <section class="template-directory" data-template-list aria-label="已授权模板目录">
    <div class="template-center-layout"><nav class="template-type-nav" aria-label="模板类型"><h2>文档模板</h2><button type="button" :aria-pressed="selectedType === ''" @click="selectType('')">全部模板</button><button v-for="(label, value) in templateDocumentLabels" :key="value" type="button" :aria-pressed="selectedType === value" @click="selectType(value)">{{ label }}</button></nav>
    <el-card shadow="never">
      <template #header><div class="template-directory__heading"><h2>{{ templateDocumentLabels[selectedType] ?? '全部模板' }}</h2><el-tag effect="plain">{{ visibleItems.length }} 项已读取</el-tag></div></template>
      <el-alert v-if="!permitted" title="当前组织尚未授予模板读取或审查权限，请联系组织管理员。" type="info" :closable="false" show-icon />
      <template v-else>
        <el-form label-position="top" @submit.prevent="search">
          <el-form-item label="查找模板"><el-input v-model="query" clearable placeholder="输入版本说明或模板名称" :disabled="loading || user.medicalWriting" /></el-form-item>
          <el-form-item><el-button native-type="submit" type="primary" :loading="loading" :disabled="user.medicalWriting">查询模板</el-button><el-button :disabled="loading || user.medicalWriting" @click="reset">重置筛选</el-button></el-form-item>
        </el-form>
        <el-table :data="visibleItems" row-key="template_version" v-loading="loading">
          <el-table-column label="文档类型" min-width="170"><template #default="scope">{{ templateDocumentLabels[scope.row.document_type] ?? scope.row.document_type }}</template></el-table-column>
          <el-table-column label="当前状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'APPROVED' ? 'success' : scope.row.status === 'DISABLED' ? 'info' : 'warning'">{{ templateStatusLabels[scope.row.status as TemplateStatus] }}</el-tag></template></el-table-column>
          <el-table-column prop="reason" label="版本说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ new Date(row.created_at).toLocaleString('zh-CN', { hour12: false }) }}</template></el-table-column>
          <el-table-column label="操作" width="130" fixed="right"><template #default="scope"><router-link :to="'/templates/' + scope.row.template_version + '/approval'"><el-button link type="primary">{{ templateApprovalAllowed(user.medicalSession,scope.row.template_version) ? '审查模板' : '查看模板' }}</el-button></router-link></template></el-table-column>
          <template #empty><el-empty :description="loaded ? next ? '本次读取没有符合条件的模板，可以继续读取目录' : '当前条件下没有获授权模板' : '正在核查模板目录'" :image-size="80" /></template>
        </el-table>
        <div class="template-directory__footer"><p>目录显示当前组织与本人具有读取或审查权限的模板，打开后重新核查全部依赖。</p><el-button v-if="next" :loading="loading" :disabled="query !== applied || user.medicalWriting" @click="load(true)">继续读取模板</el-button></div>
      </template>
    </el-card></div>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { fail } from '@/api/medical/protocol'
import { templateApprovalAllowed, templateReadAllowed, templateStatusLabels } from '@/api/medical/template-approval'
import type { TemplateStatus } from '@/api/medical/template-approval'
import { readTemplateDirectory, templateDocumentLabels } from '@/api/medical/template-directory'
import type { TemplateSummary } from '@/api/medical/template-directory'
const user = useUserStore()
const permitted = computed(() => templateApprovalAllowed(user.medicalSession)||templateReadAllowed(user.medicalSession))
const items = shallowRef<TemplateSummary[]>([]), query = ref(''), applied = ref(''), next = ref<string | null>(null)
const selectedType = ref('')
const visibleItems = computed(() => items.value.filter(item => (!selectedType.value || item.document_type === selectedType.value) && (!selectedType.value || !query.value || item.reason.includes(query.value) || item.template_version.includes(query.value))))
const loading = ref(false), loaded = ref(false)
let generation = 0, controller: AbortController | null = null
function clear() { generation++; controller?.abort(); controller = null; items.value = []; next.value = null; loading.value = false; loaded.value = false }
const routeActive = useMedicalRouteRead('MedicalTemplateApprovalEntry', clear)
async function load(more = false) {
  const session = user.medicalSession
  if (!session?.current_context || !permitted.value || !routeActive.value || loading.value || user.medicalWriting || document.hidden || more && !next.value) return
  const current = generation, request = new AbortController(); controller = request; loading.value = true
  try {
    const value = await readTemplateDirectory(session, { limit: 25, ...(selectedType.value || applied.value ? { q: selectedType.value || applied.value } : {}), ...(more && next.value ? { after: next.value } : {}) }, request.signal)
    if (current !== generation || session !== user.medicalSession || document.hidden) return
    const previous = more ? items.value : [], last = previous.at(-1)?.template_version
    if (last && value.items.some(item => item.template_version <= last)) fail('CONTENT_MISMATCH', '模板目录分页顺序需要重新核查')
    items.value = [...previous, ...value.items]; next.value = value.next_after; loaded.value = true
  } finally { if (current === generation) loading.value = false }
}
async function search() { if (loading.value || user.medicalWriting) return; clear(); applied.value = query.value; await load() }
async function reset() { query.value = ''; await search() }
async function selectType(value:string){if(user.medicalWriting)return;clear();selectedType.value=value;query.value='';applied.value='';await load()}
watch(() => [user.medicalSession, routeActive.value], async () => { clear(); query.value = ''; applied.value = ''; await load() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>
<style scoped>
.template-directory { margin: 24px 0; }
.template-center-layout{display:grid;grid-template-columns:210px minmax(0,1fr);gap:24px}.template-type-nav{background:white;border:1px solid var(--el-border-color-light);border-radius:8px;padding:16px 10px;align-self:start}.template-type-nav h2{font-size:17px;padding:0 12px;margin:0 0 16px}.template-type-nav button{display:block;width:100%;border:0;border-radius:6px;background:transparent;text-align:left;color:var(--el-text-color-regular);padding:12px;cursor:pointer;line-height:1.5}.template-type-nav button[aria-pressed=true]{color:var(--el-color-primary);background:var(--el-color-primary-light-9);font-weight:600}.template-type-nav button:hover{background:var(--el-fill-color-light)}
.template-directory__heading, .template-directory__footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.template-directory__heading h2 { margin: 0 0 8px; font-size: 18px; }
.template-directory__heading p, .template-directory__footer p { margin: 0; color: var(--el-text-color-secondary); line-height: 1.6; }
.template-directory__footer { margin-top: 16px; }
.template-directory .el-select { width: min(100%, 480px); }
@media (max-width: 600px) { .template-directory__heading, .template-directory__footer { align-items: flex-start; flex-direction: column; } }
@media(max-width:860px){.template-center-layout{grid-template-columns:1fr}.template-type-nav{display:flex;gap:4px;overflow:auto}.template-type-nav h2{display:none}.template-type-nav button{width:auto;white-space:nowrap}}
</style>
