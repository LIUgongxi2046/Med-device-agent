<template>
  <main class="medical-page source-catalogue">
    <h1>资料库</h1>
    <p v-if="loading" role="status">正在读取当前资料目录</p>
    <session-panel />
    <div class="actions">
      <router-link :to="{ path: '/library/collections/new', query: route.query.project_id ? { project_id: route.query.project_id } : {} }"><el-button>创建资料集</el-button></router-link>
      <router-link v-if="filters.project_id && sourceUploadAllowed(user.medicalSession, filters.project_id)" :to="'/projects/' + filters.project_id + '/uploads'"><el-button type="primary">上传项目资料</el-button></router-link>
      <router-link v-else-if="!filters.project_id" to="/projects"><el-button>选择项目上传资料</el-button></router-link>
    </div>
    <section class="catalogue-layout">
      <aside class="folder-panel" aria-label="资料文件夹">
        <h2>常用视图</h2>
        <el-button text :type="commonView === 'ALL' ? 'primary' : ''" @click="applyCommonView('ALL')">全部资料</el-button>
        <el-button text :type="commonView === 'BOOKMARKED' ? 'primary' : ''" @click="applyCommonView('BOOKMARKED')">收藏</el-button>
        <el-button text :type="commonView === 'DISABLED' ? 'primary' : ''" @click="applyCommonView('DISABLED')">已停用</el-button>
        <el-button text :type="commonView === 'RECYCLED' ? 'primary' : ''" @click="applyCommonView('RECYCLED')">回收区</el-button>
        <el-divider />
        <h2>文件夹</h2>
        <p v-if="!mayManageFolders" class="folder-permission">当前组织未授予此范围的文件夹维护权限。</p>
        <template v-else>
        <el-button v-for="folder in folders" :key="folder.folder_id" text
          :type="selectedFolder === folder.folder_id ? 'primary' : ''" @click="selectFolder(folder.folder_id)">
          {{ folder.name }}
        </el-button>
        <el-divider />
        <el-input v-model="folderDraft.name" placeholder="文件夹名称" maxlength="256" />
        <el-input v-model="folderDraft.category" placeholder="类别" maxlength="32" />
        <el-button :loading="writing" :disabled="!folderDraft.name || !folderDraft.category" @click="createFolder">创建文件夹</el-button>
        <template v-if="activeFolder">
          <el-divider />
          <el-button :loading="writing" @click="renameFolder">重命名</el-button>
          <el-select v-model="folderTarget" clearable placeholder="移动到根目录">
            <el-option v-for="folder in folderTargets" :key="folder.folder_id" :label="folder.name" :value="folder.folder_id" />
          </el-select>
          <el-button :loading="writing" @click="moveFolder">移动文件夹</el-button>
          <el-button type="danger" plain :loading="writing" @click="deleteFolder">删除文件夹</el-button>
        </template>
        </template>
      </aside>
      <div class="catalogue-main">
    <el-form :inline="true" aria-label="资料筛选" @submit.prevent="applyFilters">
      <el-form-item label="项目范围"><project-scope-select v-model="filters.project_id" /></el-form-item>
      <el-form-item label="归属">
        <el-select v-model="filters.ownership" clearable><el-option label="企业" value="ENTERPRISE" /><el-option label="项目" value="PROJECT" /><el-option label="会话" value="SESSION" /></el-select>
      </el-form-item>
      <el-form-item label="核准状态">
        <el-select v-model="filters.approval_status" clearable><el-option label="待核准" value="PENDING" /><el-option label="已核准" value="APPROVED" /><el-option label="已停用" value="DISABLED" /></el-select>
      </el-form-item>
      <el-form-item label="生命周期">
        <el-select v-model="filters.lifecycle" clearable><el-option label="使用中" value="ACTIVE" /><el-option label="回收区" value="RECYCLED" /><el-option label="清理待确认" value="PURGE_PENDING" /><el-option label="已清理" value="PURGED" /></el-select>
      </el-form-item>
      <el-form-item label="收藏状态">
        <el-select v-model="filters.bookmarked" clearable><el-option label="仅收藏" value="true" /></el-select>
      </el-form-item>
      <el-form-item label="类别"><el-input v-model="filters.category" clearable maxlength="32" /></el-form-item>
      <el-form-item label="标签"><el-input v-model="filters.tag" clearable maxlength="64" /></el-form-item>
      <el-form-item label="资料集版本"><el-input v-model="filters.collection_version" clearable /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :loading="loading">查询</el-button></el-form-item>
    </el-form>
    <div v-if="selected.length" class="batch-toolbar" aria-label="批量资料维护">
      <span>已选择 {{ selected.length }} 项</span>
      <el-select v-model="batchFolder" clearable placeholder="根目录">
        <el-option v-for="folder in folders" :key="folder.folder_id" :label="folder.name" :value="folder.folder_id" />
      </el-select>
      <el-button :loading="writing" @click="organize('MOVE')">移动资料</el-button>
      <el-input v-model="batchLabels" placeholder="标签，以逗号分隔" maxlength="512" />
      <el-button :loading="writing" @click="organize('LABEL')">更新标签</el-button>
      <el-button :loading="writing" @click="organize('BOOKMARK')">收藏</el-button>
      <el-button :loading="writing" @click="organize('UNBOOKMARK')">取消收藏</el-button>
    </div>
    <p v-if="page">共 {{ page.total }} 个当前可读取资料版本</p>
    <el-table v-if="page" :data="page.items" row-key="source_version" @selection-change="changeSelection">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="ownership" label="归属" width="100" />
      <el-table-column prop="category" label="类别" width="120" />
      <el-table-column prop="processing_status" label="处理" width="130" />
      <el-table-column prop="approval_status" label="核准" width="120" />
      <el-table-column prop="lifecycle" label="生命周期" width="130" />
      <el-table-column prop="rights_status" label="权利" width="110" />
      <el-table-column label="标签" min-width="180"><template #default="scope">{{ labelText(scope.row.labels) }}</template></el-table-column>
      <el-table-column label="收藏" width="80"><template #default="scope">{{ scope.row.bookmarked ? '已收藏' : '' }}</template></el-table-column>
      <el-table-column label="版本" min-width="240"><template #default="scope"><router-link :to="'/library/versions/' + scope.row.source_version">{{ scope.row.source_version }}</router-link></template></el-table-column>
      <el-table-column label="权利审查" min-width="140"><template #default="scope">
        <router-link v-if="sourceApprovalAllowed(user.medicalSession, scope.row.source_id, scope.row.owner_project_id)"
          :to="'/library/versions/' + scope.row.source_version + '/rights'">审查当前来源权利</router-link>
      </template></el-table-column>
    </el-table>
    <el-empty v-if="page && page.items.length === 0" description="当前条件没有可读取资料" />
    <el-button v-if="page?.next_after" :loading="loading" class="more" @click="readMore">读取下一页</el-button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { sourceUploadAllowed } from '@/api/medical/source-uploads'
import { projectActionAllowed } from '@/api/medical/project-write'
import ProjectScopeSelect from '@/components/Medical/ProjectScopeSelect.vue'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { createSourceFolder, decideSourceFolder, organizeSources, readSourceCatalogue, readSourceFolders, sourceApprovalAllowed } from '@/api/medical/source-governance'
import type { SourceCatalogueItem, SourceCataloguePage, SourceCatalogueQuery, SourceFolder,
  SourceOrganizationItemInput } from '@/api/medical/source-governance'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { useMedicalRouteRead } from '@/api/medical/route-read'

const route = useRoute(); const user = useUserStore(); const page = ref<SourceCataloguePage | null>(null); const loading = ref(false); const writing = ref(false)
const filters = reactive({ project_id: '', ownership: '', approval_status: '', lifecycle: '', bookmarked: '',
  category: '', tag: '', collection_version: '' })
const folders = ref<SourceFolder[]>([]); const selectedFolder = ref<string | null>(null); const selected = ref<SourceCatalogueItem[]>([])
type CommonView = 'ALL' | 'BOOKMARKED' | 'DISABLED' | 'RECYCLED'
const commonView = ref<CommonView | null>('ALL')
const folderDraft = reactive({ name: '', category: '' }); const folderTarget = ref<string | null>(null)
const batchFolder = ref<string | null>(null); const batchLabels = ref('')
let controller: AbortController | null = null; let folderController: AbortController | null = null; let generation = 0
let contextGeneration = 0
const active = useMedicalRouteRead('MedicalSourceCatalogue', clear)
const activeFolder = computed(() => folders.value.find(item => item.folder_id === selectedFolder.value) ?? null)
const mayManageFolders = computed(() => projectActionAllowed(user.medicalSession, 'source.write', filters.project_id || null))
const folderTargets = computed(() => folders.value.filter(item => item.folder_id !== selectedFolder.value))
function labelText(value: string[]): string { return value.join('、') }
function query(after?: string): SourceCatalogueQuery {
  const result: SourceCatalogueQuery = { limit: 50 }
  if (after) result.after = after
  if (filters.project_id) result.project_id = filters.project_id
  if (filters.ownership) result.ownership = filters.ownership as 'ENTERPRISE' | 'PROJECT' | 'SESSION'
  if (filters.approval_status) result.approval_status = filters.approval_status as 'PENDING' | 'APPROVED' | 'DISABLED'
  if (filters.lifecycle) result.lifecycle = filters.lifecycle as 'ACTIVE' | 'RECYCLED' | 'PURGE_PENDING' | 'PURGED'
  if (filters.bookmarked) result.bookmarked = true
  if (filters.category) result.category = filters.category
  if (filters.tag) result.tag = filters.tag
  if (selectedFolder.value) result.folder_id = selectedFolder.value
  if (filters.collection_version) result.collection_version = filters.collection_version
  return result
}
function clear(): void {
  generation += 1; contextGeneration += 1; controller?.abort(); folderController?.abort(); controller = null; folderController = null
  page.value = null; folders.value = []; selected.value = []; selectedFolder.value = null; commonView.value = 'ALL'
  Object.assign(filters, { project_id: '', ownership: '', approval_status: '', lifecycle: '', bookmarked: '', category: '', tag: '', collection_version: '' })
  Object.assign(folderDraft, { name: '', category: '' }); folderTarget.value = null; batchFolder.value = null; batchLabels.value = ''
  loading.value = false; writing.value = false
}
async function read(after?: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; if (!active.value || !session?.current_context || document.hidden) { clear(); return }
  controller?.abort(); const reading = new AbortController(); controller = reading; const current = ++generation; loading.value = true
  try {
    const result = await readSourceCatalogue(session, query(after), reading.signal)
    if (current !== generation || !active.value || user.medicalSession !== session || document.hidden || reading.signal.aborted) return
    if (after && (result.next_after === after || result.items.some(item => page.value?.items.some(previous => previous.source_version === item.source_version)))) {
      fail('CONTENT_MISMATCH', '资料分页游标或条目没有前进')
    }
    if (after && page.value) page.value = { ...result, items: [...page.value.items, ...result.items] }; else page.value = result
  } finally { if (current === generation && controller === reading) { controller = null; loading.value = false } }
}
async function readFolders(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; if (!active.value || !session?.current_context || document.hidden) return
  if (!mayManageFolders.value) { folders.value = []; selectedFolder.value = null; return }
  folderController?.abort(); const reading = new AbortController(); folderController = reading
  const result = await readSourceFolders(session, filters.project_id || null, reading.signal)
  if (folderController !== reading || !active.value || user.medicalSession !== session || document.hidden || reading.signal.aborted) return
  folders.value = result.items
  if (selectedFolder.value && !folders.value.some(item => item.folder_id === selectedFolder.value)) selectedFolder.value = null
  folderController = null
}
async function readCurrent(): Promise<void> { await Promise.all([readFolders(), read()]) }
function applyFilters(): Promise<void> { selectedFolder.value = null; commonView.value = null; return readCurrent() }
function readMore(): Promise<void> | void { if (page.value?.next_after && !loading.value) return read(page.value.next_after) }
function clearCommonFilters(): void { filters.approval_status = ''; filters.lifecycle = ''; filters.bookmarked = '' }
function applyCommonView(view: CommonView): Promise<void> {
  clearCommonFilters(); commonView.value = view; selectedFolder.value = null; selected.value = []
  if (view === 'BOOKMARKED') filters.bookmarked = 'true'
  if (view === 'DISABLED') filters.approval_status = 'DISABLED'
  if (view === 'RECYCLED') filters.lifecycle = 'RECYCLED'
  return read()
}
function selectFolder(folderId: string): Promise<void> {
  clearCommonFilters(); commonView.value = null; selectedFolder.value = folderId; selected.value = []; return read()
}
function changeSelection(rows: SourceCatalogueItem[]): void { selected.value = rows }
async function createFolder(): Promise<void> {
  const session = user.medicalSession; if (!active.value || !session?.current_context || document.hidden || writing.value) return
  const version = contextGeneration; const draft = { ...folderDraft }; writing.value = true
  try {
    await createSourceFolder(session, { schema_version: '1.0.0', ownership: filters.project_id ? 'PROJECT' : 'ENTERPRISE',
      owner_project_id: filters.project_id || null, parent_id: selectedFolder.value,
      category: draft.category, name: draft.name })
    if (!currentWrite(session, version)) return
    if (folderDraft.name === draft.name) folderDraft.name = ''
    await readFolders()
  } finally { if (version === contextGeneration) writing.value = false }
}
function currentWrite(session: MedicalSession, version: number): boolean {
  return version === contextGeneration && active.value && user.medicalSession === session && !document.hidden
}
async function renameFolder(): Promise<void> {
  const session = user.medicalSession; const folder = activeFolder.value; if (!active.value || !session?.current_context || !folder || document.hidden || writing.value) return
  const version = contextGeneration
  const answer = await ElMessageBox.prompt('输入新的文件夹名称', '重命名文件夹', { inputValue: folder.name, inputPattern: /\S/,
    inputErrorMessage: '请输入文件夹名称' })
  if (!currentWrite(session, version)) return
  writing.value = true
  try {
    await decideSourceFolder(session, folder.folder_id, { schema_version: '1.0.0', decision: 'RENAME', name: answer.value,
      target_parent_id: null, expected_revision: folder.revision })
    if (currentWrite(session, version)) await readFolders()
  } finally { if (version === contextGeneration) writing.value = false }
}
async function moveFolder(): Promise<void> {
  const session = user.medicalSession; const folder = activeFolder.value; if (!active.value || !session?.current_context || !folder || document.hidden || writing.value) return
  const version = contextGeneration; writing.value = true
  try {
    await decideSourceFolder(session, folder.folder_id, { schema_version: '1.0.0', decision: 'MOVE', name: null,
      target_parent_id: folderTarget.value, expected_revision: folder.revision })
    if (currentWrite(session, version)) await readFolders()
  } finally { if (version === contextGeneration) writing.value = false }
}
async function deleteFolder(): Promise<void> {
  const session = user.medicalSession; const folder = activeFolder.value; if (!active.value || !session?.current_context || !folder || document.hidden || writing.value) return
  const version = contextGeneration
  await ElMessageBox.confirm('确认删除当前空文件夹？', '删除文件夹', { type: 'warning' })
  if (!currentWrite(session, version)) return
  writing.value = true
  try {
    await decideSourceFolder(session, folder.folder_id, { schema_version: '1.0.0', decision: 'DELETE', name: null,
      target_parent_id: null, expected_revision: folder.revision })
    if (!currentWrite(session, version)) return
    if (selectedFolder.value === folder.folder_id) selectedFolder.value = null
    await Promise.all([readFolders(), read()])
  } finally { if (version === contextGeneration) writing.value = false }
}
async function organize(decision: SourceOrganizationItemInput['decision']): Promise<void> {
  const session = user.medicalSession; if (!active.value || !session?.current_context || document.hidden || !selected.value.length || writing.value) return
  const version = contextGeneration
  const labels = [...new Set(batchLabels.value.split(/[,，]/).map(value => value.trim()).filter(Boolean))].sort()
  const items: SourceOrganizationItemInput[] = selected.value.map(item => ({ item_id: crypto.randomUUID(),
    source_version: item.source_version, decision, folder_id: decision === 'MOVE' ? batchFolder.value : null,
    labels: decision === 'LABEL' ? labels : null,
    expected_revision: decision === 'MOVE' || decision === 'LABEL' ? item.organization_revision : null }))
  writing.value = true
  try {
    await organizeSources(session, { schema_version: '1.0.0', items })
    if (!currentWrite(session, version)) return
    selected.value = []; await read()
  } finally { if (version === contextGeneration) writing.value = false }
}
watch(() => [user.medicalSession, active.value, route.query.project_id], () => { clear(); filters.project_id = typeof route.query.project_id === 'string' ? route.query.project_id : ''; if (active.value) return readCurrent() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 20px 0; }
.source-catalogue .el-form { margin: 16px 0; }
.source-catalogue .el-input, .source-catalogue .el-select { width: 220px; }
.more { margin-top: 16px; }
.catalogue-layout { display: grid; grid-template-columns: 208px minmax(0, 1fr); gap: 24px; }
.folder-panel { display: flex; flex-direction: column; gap: 8px; }
.folder-panel h2 { margin: 0 0 8px; }
.folder-permission { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.folder-panel .el-button { margin-left: 0; justify-content: flex-start; }
.folder-panel .el-input, .folder-panel .el-select { width: 100%; }
.batch-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
@media (max-width: 860px) {
  .catalogue-layout { grid-template-columns: 1fr; }
  .folder-panel { border-bottom: 1px solid var(--el-border-color); padding-bottom: 16px; }
}
</style>
