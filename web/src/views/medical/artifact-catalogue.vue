<template>
  <main class="medical-page artifact-catalogue">
    <h1>任务成果</h1>
    <p v-if="loading" role="status">正在读取当前任务成果</p>
    <session-panel />
    <nav class="actions" aria-label="任务成果导航">
      <router-link to="/library"><el-button>返回资料中心</el-button></router-link>
    </nav>
    <el-form :inline="true" aria-label="成果筛选" @submit.prevent="applyFilters">
      <el-form-item label="项目编号"><el-input v-model="filters.project_id" clearable /></el-form-item>
      <el-form-item label="成果类型"><el-input v-model="filters.artifact_kind" clearable maxlength="32" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" :loading="loading">查询</el-button></el-form-item>
    </el-form>
    <p v-if="page">共 {{ page.total }} 项当前可读取成果</p>
    <el-table v-if="page" :data="page.items" row-key="artifact_id">
      <el-table-column prop="artifact_kind" label="成果类型" width="150" />
      <el-table-column prop="artifact_id" label="成果编号" min-width="230" />
      <el-table-column prop="project_id" label="项目编号" min-width="230" />
      <el-table-column prop="run_id" label="运行编号" min-width="230" />
      <el-table-column prop="document_revision" label="文稿版本" min-width="230" />
      <el-table-column prop="media_type" label="媒体类型" min-width="160" />
      <el-table-column prop="size_bytes" label="字节数" width="120" />
      <el-table-column prop="sha256" label="摘要" min-width="300" />
      <el-table-column prop="created_at" label="建立时间" min-width="190" />
      <el-table-column label="操作" fixed="right" width="130">
        <template #default="scope">
          <el-button :disabled="!canSubmit(scope.row) || user.medicalWriting" @click="openSubmission(scope.row)">转为资料</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="page && page.items.length === 0" description="当前条件没有可读取成果" />
    <el-button v-if="page?.next_after" class="more" :loading="loading" @click="readMore">读取下一页</el-button>
    <el-dialog v-model="dialog" title="将任务成果转为资料" width="860px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="submit">
        <dl v-if="selected" class="artifact-context">
          <dt>成果编号</dt><dd>{{ selected.artifact_id }}</dd>
          <dt>来源项目</dt><dd>{{ selected.project_id ?? '企业范围' }}</dd>
          <dt>来源运行</dt><dd>{{ selected.run_id ?? '未关联' }}</dd>
          <dt>文稿版本</dt><dd>{{ selected.document_revision ?? '未关联' }}</dd>
        </dl>
        <div class="form-grid">
          <el-form-item label="归属范围">
            <el-select v-model="form.ownership" @change="changeOwnership">
              <el-option label="企业资料" value="ENTERPRISE" /><el-option label="项目资料" value="PROJECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标项目编号">
            <el-input v-model="targetProject" :disabled="form.ownership === 'ENTERPRISE'" />
          </el-form-item>
          <el-form-item label="资料类别"><el-input v-model="form.category" maxlength="32" /></el-form-item>
          <el-form-item label="资料标题"><el-input v-model="form.title" maxlength="512" /></el-form-item>
          <el-form-item label="维护人员编号"><el-input v-model="form.maintainer_id" /></el-form-item>
          <el-form-item label="审核人员编号"><el-input v-model="form.reviewer_id" /></el-form-item>
        </div>
        <section aria-label="原始证据">
          <div class="section-title"><h2>原始证据</h2><el-button @click="addEvidence">增加证据</el-button></div>
          <div v-for="(item, index) in form.original_evidence" :key="index" class="row-fields">
            <el-input v-model="item.source_version" placeholder="资料版本编号" />
            <el-input v-model="item.rights_revision" placeholder="权利版本编号" />
            <el-button :disabled="form.original_evidence.length === 1" @click="form.original_evidence.splice(index, 1)">移除</el-button>
          </div>
        </section>
        <section aria-label="适用项目">
          <div class="section-title"><h2>适用项目</h2><el-button @click="form.applicable_project_ids.push('')">增加项目</el-button></div>
          <div v-for="(_, index) in form.applicable_project_ids" :key="index" class="row-fields project-row">
            <el-input v-model="form.applicable_project_ids[index]" placeholder="项目编号" />
            <el-button @click="form.applicable_project_ids.splice(index, 1)">移除</el-button>
          </div>
        </section>
        <section aria-label="使用权利">
          <h2>使用权利</h2>
          <div class="form-grid">
            <el-form-item label="使用目的"><el-input v-model="form.rights.purpose" maxlength="128" /></el-form-item>
            <el-form-item label="许可说明"><el-input v-model="form.rights.license" type="textarea" maxlength="4096" /></el-form-item>
            <el-form-item label="允许操作">
              <el-select v-model="form.rights.allowed_actions" multiple>
                <el-option v-for="action in rightsActions" :key="action" :label="action" :value="action" />
              </el-select>
            </el-form-item>
            <el-form-item label="服务提供方（每行一项）"><el-input v-model="providersText" type="textarea" /></el-form-item>
            <el-form-item label="适用地区（每行一项）"><el-input v-model="regionsText" type="textarea" /></el-form-item>
            <el-form-item label="有效期开始（ISO 8601，可不填）"><el-input v-model="validFrom" /></el-form-item>
            <el-form-item label="有效期结束（ISO 8601，可不填）"><el-input v-model="validTo" /></el-form-item>
          </div>
        </section>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="user.medicalWriting" @click="submit">提交待核验资料</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="receiptDialog" title="资料提交完成" width="620px">
      <dl v-if="receipt" class="receipt">
        <dt>提交编号</dt><dd>{{ receipt.submission_id }}</dd>
        <dt>资料编号</dt><dd>{{ receipt.source_id }}</dd>
        <dt>资料版本</dt><dd>{{ receipt.source_version }}</dd>
        <dt>权利版本</dt><dd>{{ receipt.rights_revision }}</dd>
        <dt>当前状态</dt><dd>{{ receipt.status }}</dd>
      </dl>
      <template #footer>
        <router-link v-if="receipt" :to="'/library/versions/' + receipt.source_version"><el-button type="primary">查看资料</el-button></router-link>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { artifactSubmissionAllowed, readArtifactCatalogue, submitArtifactAsSource } from '@/api/medical/source-governance'
import type { ArtifactCatalogueItem, ArtifactCataloguePage, ArtifactCatalogueQuery,
  ArtifactSubmissionInput, ArtifactSubmissionReceipt } from '@/api/medical/source-governance'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { useMedicalRouteRead } from '@/api/medical/route-read'

const user = useUserStore(); const page = ref<ArtifactCataloguePage | null>(null); const loading = ref(false)
const filters = reactive({ project_id: '', artifact_kind: '' }); let controller: AbortController | null = null; let generation = 0
const active = useMedicalRouteRead('MedicalArtifactCatalogue', clear)
const dialog = ref(false); const receiptDialog = ref(false); const selected = ref<ArtifactCatalogueItem | null>(null)
const receipt = ref<ArtifactSubmissionReceipt | null>(null); const targetProject = ref(''); const providersText = ref('')
const regionsText = ref('CN'); const validFrom = ref(''); const validTo = ref(''); const formError = ref('')
const rightsActions: ArtifactSubmissionInput['rights']['allowed_actions'] = ['READ', 'STORE', 'ENTERPRISE_USE', 'DISTRIBUTE', 'EXTERNAL_PROCESS']
const form = reactive<ArtifactSubmissionInput>({ schema_version: '1.0.0', ownership: 'PROJECT', target_project_id: null,
  category: '', title: '', original_evidence: [{ source_version: '', rights_revision: '' }], rights: {
    purpose: 'SOURCE_REVIEW', license: '', allowed_actions: ['READ', 'STORE'], providers: [], regions: ['CN'],
    valid_from: null, valid_to: null
  }, maintainer_id: '', applicable_project_ids: [], reviewer_id: '' })
function query(after?: string): ArtifactCatalogueQuery {
  const result: ArtifactCatalogueQuery = { limit: 50 }
  if (after) result.after = after
  if (filters.project_id) result.project_id = filters.project_id
  if (filters.artifact_kind) result.artifact_kind = filters.artifact_kind
  return result
}
function clear(): void {
  generation += 1; controller?.abort(); controller = null; page.value = null; loading.value = false
  filters.project_id = ''; filters.artifact_kind = ''; dialog.value = false; receiptDialog.value = false
  selected.value = null; receipt.value = null; resetForm(null)
}
async function read(after?: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!active.value || !session?.current_context || document.hidden) { clear(); return }
  controller?.abort(); const reading = new AbortController(); controller = reading; const current = ++generation; loading.value = true
  try {
    const result = await readArtifactCatalogue(session, query(after), reading.signal)
    if (current !== generation || !active.value || user.medicalSession !== session || document.hidden || reading.signal.aborted) return
    if (after && (result.next_after === after || result.items.some(item => page.value?.items.some(previous => previous.artifact_id === item.artifact_id)))) {
      fail('CONTENT_MISMATCH', '成果分页游标或条目没有前进')
    }
    if (after && page.value) page.value = { ...result, items: [...page.value.items, ...result.items] }; else page.value = result
  } finally {
    if (current === generation && controller === reading) { controller = null; loading.value = false }
  }
}
function applyFilters(): Promise<void> { return read() }
function readMore(): Promise<void> | void { if (page.value?.next_after && !loading.value) return read(page.value.next_after) }
function canSubmit(item: ArtifactCatalogueItem): boolean {
  return artifactSubmissionAllowed(user.medicalSession, item.artifact_id, item.project_id, item.project_id)
}
function resetForm(item: ArtifactCatalogueItem | null): void {
  Object.assign(form, { schema_version: '1.0.0', ownership: item?.project_id === null ? 'ENTERPRISE' : 'PROJECT',
    target_project_id: item?.project_id ?? null, category: '', title: '',
    original_evidence: [{ source_version: '', rights_revision: '' }], maintainer_id: '',
    applicable_project_ids: item?.project_id ? [item.project_id] : [], reviewer_id: '' })
  Object.assign(form.rights, { purpose: 'SOURCE_REVIEW', license: '', allowed_actions: ['READ', 'STORE'], providers: [],
    regions: ['CN'], valid_from: null, valid_to: null })
  targetProject.value = item?.project_id ?? ''; providersText.value = ''; regionsText.value = 'CN'
  validFrom.value = ''; validTo.value = ''; formError.value = ''
}
function openSubmission(item: ArtifactCatalogueItem): void {
  selected.value = item; receipt.value = null; resetForm(item); dialog.value = true
}
function addEvidence(): void { form.original_evidence.push({ source_version: '', rights_revision: '' }) }
function changeOwnership(): void {
  if (form.ownership === 'ENTERPRISE') targetProject.value = ''
  else if (!targetProject.value && selected.value?.project_id) targetProject.value = selected.value.project_id
}
function lines(value: string): string[] {
  return value.split(/\r?\n/u).map(item => item.trim()).filter(Boolean)
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const artifact = selected.value
  if (!active.value || !session?.current_context || !artifact || user.medicalWriting || document.hidden) return
  formError.value = ''; form.target_project_id = form.ownership === 'PROJECT' ? targetProject.value : null
  form.rights.providers = lines(providersText.value); form.rights.regions = lines(regionsText.value)
  form.rights.valid_from = validFrom.value || null; form.rights.valid_to = validTo.value || null
  if (!artifactSubmissionAllowed(session, artifact.artifact_id, artifact.project_id, form.target_project_id)) {
    formError.value = '当前身份缺少成果读取、提交、共享或资料写入许可'; return
  }
  user.medicalWriting = true
  try {
    const body: ArtifactSubmissionInput = { ...form,
      original_evidence: form.original_evidence.map(item => ({ ...item })),
      applicable_project_ids: [...form.applicable_project_ids],
      rights: { ...form.rights, allowed_actions: [...form.rights.allowed_actions],
        providers: [...form.rights.providers], regions: [...form.rights.regions] } }
    const result = await submitArtifactAsSource(session, artifact.artifact_id, body)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== session.user.user_id
        || fresh.current_context.tenant_id !== session.current_context.tenant_id) return
    if (!active.value || document.hidden) return
    receipt.value = result; dialog.value = false; receiptDialog.value = true; await read()
  } finally { user.medicalWriting = false }
}
watch(() => [user.medicalSession, active.value], () => { clear(); if (active.value) return read() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>

<style scoped>
.actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 20px 0; }
.artifact-catalogue .el-form { margin: 16px 0; }
.artifact-catalogue .el-input { width: 240px; }
.more { margin-top: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.form-grid .el-input, .form-grid .el-select { width: 100%; }
.section-title { display: flex; align-items: center; justify-content: space-between; }
.section-title h2, section h2 { font-size: 16px; }
.row-fields { display: grid; grid-template-columns: 1fr 1fr auto; gap: 12px; margin-bottom: 12px; }
.row-fields .el-input { width: 100%; }
.project-row { grid-template-columns: 1fr auto; }
.form-error { color: var(--el-color-danger); }
.receipt { display: grid; grid-template-columns: 100px 1fr; gap: 10px 16px; }
.artifact-context { display: grid; grid-template-columns: 90px 1fr; gap: 8px 16px; }
.artifact-context dt { font-weight: 600; }
.artifact-context dd { margin: 0; overflow-wrap: anywhere; }
.receipt dt { font-weight: 600; }
.receipt dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr; } .row-fields { grid-template-columns: 1fr; } }
</style>
