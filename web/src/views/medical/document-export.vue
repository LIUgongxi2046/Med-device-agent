<template>
  <section aria-label="文稿导出" data-document-export>
    <h4>文稿导出</h4>
    <el-button :disabled="!active || loading || writing" @click="loadBasis">读取导出依据</el-button>
    <p v-if="loading" role="status">正在读取审查状态和格式版本</p>
    <section v-if="pendingRequests.length > 0" aria-label="原导出请求">
      <p v-if="recoveryMessage" role="status">{{ recoveryMessage }}</p>
      <div v-for="pending in pendingRequests" :key="pending.key">
        <p>{{ pending.operation === 'EX-01' ? '创建导出' : '重试格式' }}请求：{{ pending.key }}；{{ pending.state }}</p>
        <el-button :disabled="!active || loading || writing || pending.state === 'EXPIRED'" @click="recover(pending)">查询原导出请求</el-button>
        <el-button v-if="pending.state === 'EXPIRED'" :disabled="!active || loading || writing" @click="startNew(pending)">开始新的请求</el-button>
      </div>
    </section>
    <template v-if="basis">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="固定文稿版本">{{ basis.document_revision }}</el-descriptions-item>
        <el-descriptions-item label="模板版本">{{ basis.template_version }}</el-descriptions-item>
        <el-descriptions-item label="格式版本">{{ basis.format_profile_revision }}</el-descriptions-item>
        <el-descriptions-item label="审查状态">{{ reviewLabels[basis.review_status] }}</el-descriptions-item>
        <el-descriptions-item label="正式签发">{{ basis.formal_signature_ready ? '已准备' : '尚未准备' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top" :disabled="writing">
        <el-form-item label="导出用途">
          <el-select v-model="purpose">
            <el-option label="正式正文" value="FORMAL_BODY" :disabled="!basis.formal_signature_ready || basis.review_status !== 'CONFIRMED'" />
            <el-option label="审查材料" value="REVIEW_PACKAGE" :disabled="basis.review_status === 'UNREVIEWED'" />
            <el-option label="内部交换" value="INTERNAL_EXCHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件格式">
          <el-checkbox-group v-model="formats">
            <el-checkbox v-for="format in selectableFormats" :key="format" :value="format">{{ format }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="渠道版本（可选）"><el-input v-model="channelProfile" maxlength="36" /></el-form-item>
      </el-form>
      <el-button type="primary" :disabled="!active || loading || writing || formats.length === 0" @click="submit">提交导出</el-button>
    </template>
    <template v-if="history.length > 0">
      <h4>导出记录</h4>
      <el-table :data="history" row-key="export_id">
        <el-table-column prop="purpose" label="用途" width="150" />
        <el-table-column prop="status" label="状态" width="150" />
        <el-table-column prop="created_at" label="创建时间" min-width="210" />
        <el-table-column label="操作" width="110"><template #default="scope">
          <el-button :disabled="!active || loading || writing" @click="current = scope.row">查看</el-button>
        </template></el-table-column>
      </el-table>
    </template>
    <template v-if="current">
      <h4>导出状态</h4>
      <p>导出编号：{{ current.export_id }}；文稿版本：{{ current.document_revision }}；状态：{{ current.status }}；修订：{{ current.revision }}</p>
      <el-button :disabled="!active || loading || writing" @click="refresh">刷新状态</el-button>
      <el-form label-position="top"><el-form-item label="重试理由"><el-input v-model="retryReason" maxlength="1000" /></el-form-item></el-form>
      <el-table :data="current.items" row-key="export_item_id">
        <el-table-column prop="format" label="格式" width="110" />
        <el-table-column prop="attempt_no" label="尝试次数" width="110" />
        <el-table-column prop="status" label="状态" width="150" />
        <el-table-column label="渠道核验" width="130"><template #default="scope">{{ scope.row.validation?.channel_check ?? '等待核验' }}</template></el-table-column>
        <el-table-column label="文件核验" width="150"><template #default="scope">{{ scope.row.validation === null ? '等待核验' : '文件内容已核验' }}</template></el-table-column>
        <el-table-column label="版面检查" width="150"><template #default="scope">{{ scope.row.validation?.visual_check === 'PASSED' ? '已通过' : '尚未检查' }}</template></el-table-column>
        <el-table-column prop="validation_artifact_id" label="核验制品" min-width="220" />
        <el-table-column label="操作" width="270"><template #default="scope">
          <el-button :disabled="!active || loading || scope.row.status !== 'QUEUED' || writing" @click="run(scope.row)">执行</el-button>
          <el-button :disabled="!active || loading || !scope.row.downloadable || writing" @click="download(scope.row)">下载</el-button>
          <el-button :disabled="!active || loading || !['FAILED', 'CANCELLED'].includes(scope.row.status) || retryReason.trim().length === 0 || writing" @click="retry(scope.row)">重试</el-button>
        </template></el-table-column>
      </el-table>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { createExport, downloadExportItem, executeExportItem, readExport, readExportBasis, readExportHistory, recoverExportRequest, retryExportItem } from '@/api/medical/exports'
import type { ExportBasis, ExportFormat, ExportItem, ExportPurpose, ExportView } from '@/api/medical/exports'
import { clearExpiredRequestMetadata, confirmRequestMetadata, listRequestMetadata } from '@/api/medical/request-metadata'
import type { RequestMetadata } from '@/api/medical/request-metadata'

const props = defineProps<{ session: MedicalSession; documentId: string; documentRevision: string; active: boolean }>()
const basis = shallowRef<ExportBasis | null>(null); const current = shallowRef<ExportView | null>(null)
const history = shallowRef<ExportView[]>([])
const purpose = ref<ExportPurpose>('INTERNAL_EXCHANGE'); const formats = ref<ExportFormat[]>([])
const channelProfile = ref(''); const retryReason = ref(''); const loading = ref(false); const writing = ref(false)
const pendingRequests = shallowRef<RequestMetadata[]>([]); const recoveryMessage = ref('')
const reviewLabels = { UNREVIEWED: '尚未审查', CONFIRMED: '已确认', RETURNED: '已退回' }
const selectableFormats = computed(() => basis.value?.allowed_formats.filter(value => value !== 'MARKDOWN' || purpose.value === 'INTERNAL_EXCHANGE') ?? [])
let generation = 0; let controller: AbortController | null = null
let downloadUrl: string | null = null
function clear(): void {
  generation++; controller?.abort(); controller = null; basis.value = null; current.value = null; history.value = []
  purpose.value = 'INTERNAL_EXCHANGE'; formats.value = []; channelProfile.value = ''; retryReason.value = ''; loading.value = false; writing.value = false
  pendingRequests.value = []; recoveryMessage.value = ''
  if (downloadUrl !== null) URL.revokeObjectURL(downloadUrl)
  downloadUrl = null
}
function syncPending(): void {
  pendingRequests.value = props.active && props.session.current_context && !document.hidden
    ? listRequestMetadata(props.session).filter(value => value.scope_id === props.documentId && (value.operation === 'EX-01' || value.operation === 'EX-04')) : []
}
async function loadBasis(): Promise<void> {
  if (!props.active || document.hidden || loading.value || writing.value) return
  const request = new AbortController(); controller?.abort(); controller = request; const version = generation; loading.value = true
  try {
    const [value, records] = await Promise.all([
      readExportBasis(props.session, props.documentId, props.documentRevision, request.signal),
      readExportHistory(props.session, props.documentId, props.documentRevision, request.signal)
    ])
    if (version !== generation || !props.active || document.hidden || request.signal.aborted) return
    basis.value = value; formats.value = value.allowed_formats.filter(format => format !== 'MARKDOWN')
    history.value = records.items; current.value = records.items[0] ?? null
  } finally { if (version === generation) loading.value = false }
}
async function submit(): Promise<void> {
  const source = basis.value
  if (!props.active || document.hidden || source === null || formats.value.length === 0 || writing.value || loading.value) fail('ACTION_STALE', '当前导出输入不完整')
  const version = generation; const session = props.session; writing.value = true
  const request = new AbortController(); controller?.abort(); controller = request
  try {
    const value = await createExport(session, props.documentId, { schema_version: '1.0.0', document_revision: source.document_revision,
      purpose: purpose.value, formats: [...formats.value], template_version: source.template_version,
      review_manifest_sha256: source.review_manifest_sha256, format_profile_revision: source.format_profile_revision,
      channel_profile_revision: channelProfile.value.trim() || null }, request.signal)
    if (!validWrite(version, session)) return
    current.value = value; remember(value); await processQueued(value, version, session, request.signal)
  } finally { if (version === generation) { writing.value = false; controller = null; syncPending() } }
}
async function refresh(): Promise<void> {
  const value = current.value; if (!props.active || document.hidden || value === null || loading.value || writing.value) return
  const request = new AbortController(); controller?.abort(); controller = request; const version = generation; loading.value = true
  try { const result = await readExport(props.session, value.export_id, request.signal); if (version === generation && props.active && !document.hidden && !request.signal.aborted) { current.value = result; remember(result) } }
  finally { if (version === generation) loading.value = false }
}
async function retry(item: ExportItem): Promise<void> {
  const parent = current.value
  if (!props.active || document.hidden || parent === null || loading.value || writing.value || retryReason.value.trim().length === 0) return
  const version = generation; const session = props.session; writing.value = true
  const request = new AbortController(); controller?.abort(); controller = request
  try {
    const value = await retryExportItem(session, parent, item, retryReason.value.trim(), request.signal)
    if (!validWrite(version, session)) return
    current.value = value; remember(value); retryReason.value = ''; await processQueued(value, version, session, request.signal)
  } finally { if (version === generation) { writing.value = false; controller = null; syncPending() } }
}
function validWrite(version: number, session: MedicalSession): boolean {
  return version === generation && props.session === session && props.active && !document.hidden
}
async function processQueued(value: ExportView, version: number, session: MedicalSession, signal: AbortSignal): Promise<void> {
  for (const item of value.items.filter(candidate => candidate.status === 'QUEUED')) {
    if (!validWrite(version, session)) return
    const updated = await executeExportItem(session, item, signal)
    if (!validWrite(version, session)) return
    current.value = updated; remember(updated)
  }
}
async function run(item: ExportItem): Promise<void> {
  if (!props.active || document.hidden || loading.value || writing.value || item.status !== 'QUEUED') return
  const version = generation; const session = props.session; writing.value = true
  const request = new AbortController(); controller?.abort(); controller = request
  try {
    const value = await executeExportItem(session, item, request.signal)
    if (!validWrite(version, session)) return
    current.value = value; remember(value)
  } finally { if (version === generation) { writing.value = false; controller = null } }
}
async function recover(pending: RequestMetadata): Promise<void> {
  if (!props.active || document.hidden || loading.value || writing.value) return
  const request = new AbortController(); controller?.abort(); controller = request
  const version = generation; const session = props.session; loading.value = true; recoveryMessage.value = ''
  try {
    const result = await recoverExportRequest(session, pending, request.signal)
    if (!validWrite(version, session) || request.signal.aborted) return
    recoveryMessage.value = result.lookup.lookup_status === 'NOT_FOUND' ? '尚未找到原操作。保留原参数后再次提交，将使用同一请求编号。'
      : result.lookup.lookup_status === 'EXPIRED' ? '原请求已经过期，请明确开始新的请求。' : '原操作状态：' + result.lookup.operation!.status
    if (result.view !== null && result.lookup.operation?.resource_id !== null && result.lookup.operation !== null) {
      if (result.view.document_id !== props.documentId) fail('CONTENT_MISMATCH', '原导出结果所属文稿不一致')
      confirmRequestMetadata(session, pending, result.lookup.operation, result.lookup.operation.resource_id)
      current.value = result.view; remember(result.view)
    }
    syncPending()
  } finally { if (version === generation) { loading.value = false; controller = null } }
}
function startNew(pending: RequestMetadata): void {
  if (!props.active || document.hidden || loading.value || writing.value) return
  clearExpiredRequestMetadata(props.session, pending)
  if (pending.operation === 'EX-01') { purpose.value = 'INTERNAL_EXCHANGE'; formats.value = []; channelProfile.value = '' }
  else retryReason.value = ''
  recoveryMessage.value = ''; syncPending()
}
async function download(item: ExportItem): Promise<void> {
  if (!props.active || writing.value || loading.value || document.hidden) return
  const request = new AbortController(); controller?.abort(); controller = request; const version = generation; loading.value = true
  try {
    const blob = await downloadExportItem(props.session, item, request.signal)
    if (version !== generation || !props.active || document.hidden) return
    if (downloadUrl !== null) URL.revokeObjectURL(downloadUrl)
    downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = downloadUrl; link.download = item.export_item_id + '.' + item.format.toLowerCase()
    document.body.appendChild(link); link.click(); link.remove()
  } finally { if (version === generation) { controller = null; loading.value = false } }
}
function remember(value: ExportView): void {
  history.value = [value, ...history.value.filter(item => item.export_id !== value.export_id)]
    .sort((left, right) => right.created_at.localeCompare(left.created_at)).slice(0, 20)
}
watch(() => [props.active, props.session, props.session.user.user_id, props.session.current_context?.tenant_id, props.session.current_context?.context_revision,
  props.session.current_context?.auth_epoch, props.session.current_context?.governance_epoch, props.documentId, props.documentRevision], () => { clear(); syncPending() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(clear)
</script>
