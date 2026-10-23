<template>
  <main class="medical-page source-upload-workspace">
    <h1>{{ enterpriseMode ? '上传企业资料' : '上传项目资料' }}</h1>
    <p v-if="enterpriseMode">资料归属当前企业，此项目仅用于上传处理。</p>
    <session-panel />
    <router-link v-if="validProject" :to="'/projects/' + projectId">返回项目详情</router-link>
    <p v-if="!validProject" role="alert">项目标识未通过接口校验</p>
    <p v-else-if="!permitted">上传资料需要当前企业身份及来源维护权限</p>
    <section v-if="permitted" data-source-upload-queue>
      <p>支持 PDF、DOCX、XLSX、CSV、PPTX、Markdown、TXT 和 PNG/JPEG/TIFF 图片。文件分别填写来源和许可，服务器逐项核查容量、格式与内容。</p>
      <file-upload :selection-only="true" :dropzone="true" :file-type="Object.keys(sourceMediaTypes)" :file-size="0" :limit="100" :drag="false"
        :disabled="busy || user.medicalWriting" @select-file="selectFile" />
      <p v-if="reselect">请选择原文件：{{ reselect.id }}。继续前将核查原文件大小和摘要。</p>
      <el-button :disabled="busy || user.medicalWriting" @click="recover">查询原上传记录</el-button>
      <el-button :disabled="busy || user.medicalWriting || !items.some(item => item.confirmed && item.file)" @click="sendConfirmed">接收已确认文件</el-button>
      <el-button v-if="busy" @click="stop">停止发送</el-button>
      <p role="status">{{ notice }}</p>
      <div class="upload-workspace">
        <section class="upload-file-list" aria-label="逐文件上传队列">
          <div class="queue-heading"><h2>本页文件</h2><span>{{ items.length }} 项</span></div>
          <el-empty v-if="items.length === 0" description="选择文件后，逐项填写来源与许可" :image-size="80" />
          <button v-for="item in items" :key="item.id" type="button" class="upload-file-item"
            :class="{ 'is-current': inspected?.id === item.id }" :aria-pressed="inspected?.id === item.id" @click="inspectedId = item.id">
            <strong>{{ item.body?.file_name ?? '待恢复原声明' }}</strong>
            <span>{{ item.file?.size ?? item.journal?.size_bytes ?? '尚未读取' }} 字节</span>
            <span>{{ item.state ? uploadStatusLabels[item.state.registration.upload_status] : '尚未核查接收状态' }}</span>
            <span class="file-local-state">{{ item.confirmed ? '本地声明已确认' : '本地声明待确认' }}</span>
          </button>
        </section>
        <section v-if="inspected" class="upload-detail" aria-label="当前文件详情">
          <header class="file-detail-heading"><div><p class="section-kicker">逐文件核查</p><h2>{{ inspected.body?.file_name ?? '待恢复原声明' }}</h2></div>
            <router-link v-if="inspected.result?.source" :to="'/library/versions/' + inspected.result.source.source_version">打开固定来源</router-link>
          </header>
          <p class="file-id">队列编号：{{ inspected.id }}</p>
          <div class="file-status-grid">
            <div><span>服务端接收</span><strong>{{ inspected.state ? uploadStatusLabels[inspected.state.registration.upload_status] : '尚未核查' }}</strong></div>
            <div><span>传输状态</span><strong>{{ inspected.state ? transferStatusLabels[inspected.state.registration.transfer_state] : '尚未登记' }}</strong></div>
            <div><span>来源与许可</span><strong>{{ inspected.confirmed ? '本地已确认' : '待逐项确认' }}</strong></div>
          </div>
          <div class="file-progress">
            <p>服务器确认：{{ inspected.state ? inspected.state.registration.received_size + ' 字节' : '尚未核查' }}</p>
            <el-progress v-if="receivedPercent(inspected) !== null" :percentage="receivedPercent(inspected)!" :stroke-width="8" :show-text="false" aria-label="服务器已确认接收比例" />
            <p>当前分段已发送：{{ inspected.sent }} 字节。发送数量不代表服务器已接收。</p>
          </div>
          <p v-if="inspected.message" class="file-notice" role="status">{{ inspected.message }}</p>
          <div class="file-actions">
            <el-button :disabled="busy || user.medicalWriting || !inspected.body" @click="selected = inspected">来源与许可</el-button>
            <el-button :disabled="busy || user.medicalWriting" @click="reselect = inspected">选择原文件</el-button>
            <el-button type="primary" :disabled="busy || user.medicalWriting || !inspected.file || !inspected.body" @click="send(inspected)">核查并继续接收</el-button>
            <el-button :disabled="busy || user.medicalWriting || !inspected.journal?.upload_id" @click="queryResult(inspected)">查询固定处理结果</el-button>
            <el-button v-if="!inspected.journal" :disabled="busy" @click="remove(inspected)">移除待传文件</el-button>
          </div>
          <p class="upload-help">继续接收前核查本文件的完整声明；不确定的操作结果先查询原上传记录。</p>
          <ul v-if="inspected.errors.length" class="file-errors" role="alert"><li v-for="error in inspected.errors" :key="error">{{ error }}</li></ul>
          <source-upload-result v-if="inspected.result" :value="inspected.result" />
          <div v-else class="processing-unread" role="status"><h3>处理结果尚未读取</h3><p>解析、索引及人工核准状态由服务端逐版本返回。登记上传后可查询固定处理结果。</p></div>
        </section>
      </div>
      <p>选择左侧文件可以读取固定来源、完整处理尝试、失败清单、当前权利及核准依据。接收、解析、索引和人工核准分别读取服务器保存的结果。</p>
      <p>关闭页面或停止发送会终止本地发送。重新进入页面后，使用“查询原上传记录”核查服务器结果；继续发送需要重新选择原文件。</p>
    </section>
    <el-dialog :model-value="selected !== null" title="逐文件来源与许可声明" width="80%" destroy-on-close @close="selected = null">
      <template v-if="selected?.body">
        <p>{{ selected.body.file_name }} · {{ selected.body.size_bytes }} 字节</p>
        <p>文件摘要：{{ selected.body.sha256 }}</p>
        <p v-if="selected.journal">恢复记录沿用原始归属和声明，不会因企业上传入口而改写。</p>
        <p v-if="selected.journal && !selected.state" role="status">原登记结果仍待核查。恢复只能填写与原摘要完全一致的完整声明，不能用新声明覆盖原请求。</p>
        <source-declaration :value="selected.body" :ownership-locked="enterpriseMode && !selected.journal" :disabled="busy || selected.journal !== null && selected.state !== null" />
        <ul v-if="selected.errors.length" role="alert"><li v-for="error in selected.errors" :key="error">{{ error }}</li></ul>
        <el-checkbox v-model="selected.confirmed" :disabled="busy">已经按实际资料核查本文件的归属、版本意图、来源及许可</el-checkbox>
        <el-button :disabled="busy" @click="validateItem(selected)">核查声明格式</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import useUserStore from '@/store/modules/user'
import FileUpload from '@/components/FileUpload/index.vue'
import SessionPanel from './session-panel.vue'
import SourceDeclaration from './source-declaration.vue'
import SourceUploadResult from './source-upload-result.vue'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { byteDigest, completeSourceUpload, createSourceUpload, lookupUploadRequest, prepareSourceUpload, prepareUploadPart,
  readSourceUpload, readSourceUploadResult, readUploadJournals, saveUploadJournal, sendUploadPart, sourceMediaType, sourceMediaTypes, sourceUploadAllowed,
  transferStatusLabels, uploadIdentity, uploadStatusLabels } from '@/api/medical/source-uploads'
import type { SourceUploadCreate, SourceUploadResult as UploadResult, SourceUploadState, UploadJournal } from '@/api/medical/source-uploads'

interface UploadItem {
  id: string; file: File | null; body: SourceUploadCreate | null; journal: UploadJournal | null; state: SourceUploadState | null
  message: string; sent: number; errors: string[]; confirmed: boolean
  result: UploadResult | null
}
const route = useRoute(); const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const enterpriseMode = computed(() => route.query.ownership === 'ENTERPRISE')
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const validProject = computed(() => schemaIssues('urn:medical:source-uploads:1.0.0#/$defs/Id', projectId.value).length === 0)
const permitted = computed(() => validProject.value && sourceUploadAllowed(session.value, enterpriseMode.value ? '' : projectId.value))
const items = ref<UploadItem[]>([]); const selected = ref<UploadItem | null>(null); const reselect = ref<UploadItem | null>(null)
const inspectedId = ref('')
const inspected = computed(() => items.value.find(item => item.id === inspectedId.value) ?? items.value[0] ?? null)
function receivedPercent(item: UploadItem): number | null {
  if (!item.state) return null
  const received = BigInt(item.state.registration.received_size)
  const total = BigInt(item.state.registration.request.size_bytes)
  if (total <= 0n || received < 0n || received > total) return null
  return Number(received * 10000n / total) / 100
}
const busy = ref(false); const notice = ref(''); let generation = 0; let controller: AbortController | null = null; let batch = crypto.randomUUID()
function blank(file: File, hash: string, actor: string, batchId: string): SourceUploadCreate {
  return { schema_version: '1.0.0', batch_id: batchId, project_id: projectId.value, ownership: enterpriseMode.value ? 'ENTERPRISE' : 'PROJECT', session_id: null,
    file_name: file.name, media_type: sourceMediaType(file), size_bytes: String(file.size), sha256: hash, source_id: null,
    version_intent: 'NEW_SOURCE', classification: 'INTERNAL', provenance: {
      owner: '', publisher: null, maintainer_id: actor, original_url: null, final_url: null, publisher_version: null,
      published_at: null, issued_on: null, effective_from: null, effective_to: null, observed_status: null, verified_at: null,
      status_evidence: [], acquired_at: null, acquired_by: null, acquisition_method: null, update_frequency: null,
      next_check_at: null, retention_policy_id: null, legal_hold: null
    }, rights_declaration: { purpose: '', license: '', allowed_actions: [], providers: [], regions: [], valid_from: null, valid_to: null, authorization_evidence: [] } }
}
function reset(): void {
  generation += 1; controller?.abort(); controller = null; busy.value = false; items.value = []; selected.value = null
  inspectedId.value = ''; reselect.value = null; notice.value = ''; batch = crypto.randomUUID()
}
function stop(): void {
  generation += 1; controller?.abort(); controller = null; busy.value = false
  for (const item of items.value) { item.file = null; item.sent = 0; item.confirmed = false }
  selected.value = null; reselect.value = null; notice.value = '本地发送已经停止，请查询原上传记录核查服务器结果'
}
function remove(item: UploadItem): void {
  if (busy.value || item.journal) return
  items.value = items.value.filter(value => value.id !== item.id)
  if (inspectedId.value === item.id) inspectedId.value = ''
  if (selected.value?.id === item.id) selected.value = null
  if (reselect.value?.id === item.id) reselect.value = null
}
async function selectFile(file: File): Promise<void> {
  const actual = session.value; const current = generation; const replacement = reselect.value
  if (!actual || !permitted.value || busy.value || user.medicalWriting || document.hidden) return
  const extension = file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
  if (!Object.prototype.hasOwnProperty.call(sourceMediaTypes, extension)) {
    notice.value = '文件格式不支持。请选择 PDF、DOCX、XLSX、CSV、PPTX、Markdown、TXT 或 PNG/JPEG/TIFF 图片。'
    return
  }
  if (file.size <= 0 || !Number.isSafeInteger(file.size)) {
    notice.value = '文件必须包含内容并具有可核验的大小，请重新选择非空文件。'
    return
  }
  notice.value = ''
  sourceMediaType(file)
  const hash = await byteDigest(await file.arrayBuffer())
  if (current !== generation || session.value !== actual || document.hidden) return
  if (replacement) {
    if (!items.value.some(item => item.id === replacement.id)) return
    const expected = replacement.journal
    if (expected && (expected.file_sha256 !== hash || expected.size_bytes !== String(file.size))) fail('CONTENT_MISMATCH', '所选文件与原上传的大小或内容摘要不一致')
    if (replacement.body && (replacement.body.sha256 !== hash || replacement.body.file_name !== file.name)) fail('CONTENT_MISMATCH', '所选文件与原声明中的名称或内容摘要不一致')
    replacement.file = file
    replacement.body ??= blank(file, hash, actual.user.user_id, expected?.batch_id ?? batch)
    replacement.confirmed = false; selected.value = replacement; reselect.value = null; return
  }
  const duplicate = items.value.find(value => value.body?.sha256 === hash || value.journal?.file_sha256 === hash)
  if (duplicate) { notice.value = '当前队列已经包含相同内容，请处理原文件记录：' + duplicate.id; return }
  const item = reactive<UploadItem>({ id: crypto.randomUUID(), file, body: blank(file, hash, actual.user.user_id, batch), journal: null,
    state: null, result: null, message: '请逐文件核查完整来源与许可', sent: 0, errors: [], confirmed: false })
  items.value.push(item); selected.value = item
}
function validateItem(item: UploadItem): boolean {
  item.errors = schemaIssues('urn:medical:source-uploads:1.0.0#/$defs/Create', item.body).map(issue => issue.path + '：' + issue.code)
  if (enterpriseMode.value && !item.journal && item.body?.ownership !== 'ENTERPRISE') item.errors.push('此入口仅登记企业资料，请保留企业归属')
  if (!item.confirmed) item.errors.push('请确认本文件的完整来源及许可声明')
  if (!sourceUploadAllowed(session.value, projectId.value, item.body ?? undefined)) item.errors.push('当前资料归属或版本没有来源维护权限')
  if (item.errors.length) selected.value = item
  return item.errors.length === 0
}
function active(actual: MedicalSession, current: number, signal: AbortSignal): boolean {
  return generation === current && session.value === actual && !signal.aborted && permitted.value && !document.hidden
}
async function recover(): Promise<void> {
  const actual = session.value
  if (!actual || !permitted.value || busy.value || user.medicalWriting || document.hidden) return
  const current = generation; const reading = new AbortController(); controller = reading; busy.value = true
  try {
    for (const journal of readUploadJournals(actual, projectId.value)) {
      if (!active(actual, current, reading.signal)) return
      let item = items.value.find(value => value.id === journal.item_id)
      if (!item) {
        item = reactive<UploadItem>({ id: journal.item_id, file: null, body: null, journal, state: null, result: null, message: '正在查询原上传', sent: 0, errors: [], confirmed: false })
        items.value.push(item)
      }
      const result = await lookupUploadRequest(actual, journal, uploadIdentity(journal, 'KN-01'), reading.signal)
      if (!active(actual, current, reading.signal)) return
      if (result.lookup_status !== 'FOUND') { item.message = result.lookup_status === 'EXPIRED' ? '原操作记录已过期，停止重新发送' : '未查到登记结果，继续需要原文件及完整原声明'; continue }
      if (!result.operation?.resource_id) fail('CONTENT_MISMATCH', '原登记操作缺少上传标识')
      journal.upload_id = result.operation.resource_id; saveUploadJournal(actual, journal); item.journal = journal
      const state = await readSourceUpload(actual, journal, reading.signal)
      if (!active(actual, current, reading.signal)) return
      item.state = state; item.body = state.registration.request; item.message = '已经核查服务器记录，继续接收需要重新选择原文件并确认声明'
      const processing = await readSourceUploadResult(actual, journal, state, reading.signal)
      if (!active(actual, current, reading.signal)) return
      item.result = processing
      const completion = journal.requests.find(value => value.operation === 'KN-03')
      if (completion) {
        const result = await lookupUploadRequest(actual, journal, completion, reading.signal)
        if (!active(actual, current, reading.signal)) return
        item.message = result.operation ? '完成命令：' + operationLabel(result.operation.status) : result.lookup_status === 'EXPIRED' ? '完成命令记录已过期，停止重新发送' : '完成命令尚未查到，继续需要原文件及完整原声明'
      }
    }
    if (active(actual, current, reading.signal)) notice.value = '原上传记录查询完成，逐文件结果见队列'
  } finally { if (generation === current) { busy.value = false; controller = null } }
}
async function queryResult(item: UploadItem): Promise<void> {
  const actual = session.value; const journal = item.journal
  if (!actual || !journal?.upload_id || !permitted.value || busy.value || user.medicalWriting || document.hidden) return
  const current = generation; const reading = new AbortController(); controller = reading; busy.value = true; item.result = null
  try {
    const state = await readSourceUpload(actual, journal, reading.signal)
    if (!active(actual, current, reading.signal)) return
    const result = await readSourceUploadResult(actual, journal, state, reading.signal)
    if (!active(actual, current, reading.signal)) return
    item.state = state; item.body = state.registration.request; item.result = result; item.message = '已核查固定处理结果及原完成操作'
  } finally { if (current === generation) { busy.value = false; controller = null } }
}
function operationLabel(status: string): string { return status === 'COMPLETED' ? '操作完成' : status === 'PROCESSING' ? '服务器正在处理' : '操作失败' }
async function sendConfirmed(): Promise<void> {
  if (busy.value || user.medicalWriting || !permitted.value || document.hidden) return
  const current = generation
  for (const item of [...items.value]) {
    if (generation !== current || document.hidden) return
    if (item.file && item.body && item.confirmed) await send(item)
  }
}
async function send(item: UploadItem): Promise<void> {
  const actual = session.value; const file = item.file; const body = item.body
  if (!actual || !file || !body || !permitted.value || busy.value || user.medicalWriting || document.hidden || !validateItem(item)) return
  const current = generation; const sending = new AbortController(); controller = sending; busy.value = true; selected.value = null
  try {
    const hash = await byteDigest(await file.arrayBuffer())
    if (!active(actual, current, sending.signal)) return
    const journal = await prepareSourceUpload(actual, item.id, body, hash, sending.signal)
    if (!active(actual, current, sending.signal)) return
    item.journal = journal; item.message = '正在核查原登记操作'
    const original = await lookupUploadRequest(actual, journal, uploadIdentity(journal, 'KN-01'), sending.signal)
    if (!active(actual, current, sending.signal)) return
    if (original.lookup_status === 'EXPIRED') { item.message = '原操作记录已过期，停止重新发送'; return }
    if (original.operation && original.operation.status !== 'COMPLETED') { item.message = '原登记：' + operationLabel(original.operation.status); return }
    if (original.operation) {
      if (!original.operation.resource_id) fail('CONTENT_MISMATCH', '原登记缺少上传对象')
      journal.upload_id = original.operation.resource_id; saveUploadJournal(actual, journal)
    }
    let state = original.lookup_status === 'NOT_FOUND' ? await createSourceUpload(actual, journal, body, sending.signal) : await readSourceUpload(actual, journal, sending.signal)
    if (!active(actual, current, sending.signal)) return
    item.state = state; item.result = null
    if (state.registration.transfer_state !== 'ACTIVE') {
      const result = await readSourceUploadResult(actual, journal, state, sending.signal)
      if (!active(actual, current, sending.signal)) return
      item.result = result; item.message = transferStatusLabels[state.registration.transfer_state]; return
    }
    const size = Number(state.registration.max_part_bytes)
    for (let start = 0, partNo = 1; start < file.size; start += size, partNo += 1) {
      if (!active(actual, current, sending.signal)) return
      const blob = file.slice(start, Math.min(start + size, file.size))
      const identity = await prepareUploadPart(actual, journal, partNo, blob, sending.signal)
      if (!active(actual, current, sending.signal)) return
      const result = await lookupUploadRequest(actual, journal, identity, sending.signal)
      if (!active(actual, current, sending.signal)) return
      if (result.lookup_status === 'EXPIRED') { item.message = '原分段操作记录已过期，停止重新发送'; return }
      if (result.operation?.status === 'FAILED') { item.message = '原分段操作失败，请查询服务器记录'; return }
      if (result.operation?.status === 'COMPLETED') {
        state = await readSourceUpload(actual, journal, sending.signal)
        if (!active(actual, current, sending.signal)) return
        item.state = state
      }
      const saved = state.registration.parts.find(part => part.part_no === partNo)
      if (saved?.state === 'STORED') {
        if (saved.sha256 !== identity.sha256 || saved.size_bytes !== identity.size_bytes || result.operation?.status !== 'COMPLETED') fail('CONTENT_MISMATCH', '服务器已保存分段与原文件或原操作不一致')
        continue
      }
      if (result.operation?.status === 'COMPLETED') fail('CONTENT_MISMATCH', '原分段操作已经完成，但分段记录尚未匹配')
      item.message = '正在接收分段 ' + partNo; item.sent = 0
      const receipt = await sendUploadPart(actual, journal, identity, blob, sending.signal, bytes => { if (active(actual, current, sending.signal)) item.sent = bytes })
      if (!active(actual, current, sending.signal)) return
      if ('operation_id' in receipt) { item.message = '分段：' + operationLabel(receipt.status); return }
      state = await readSourceUpload(actual, journal, sending.signal)
      if (!active(actual, current, sending.signal)) return
      item.state = state; item.sent = 0
    }
    const previous = journal.requests.find(value => value.operation === 'KN-03')
    if (previous) {
      const result = await lookupUploadRequest(actual, journal, previous, sending.signal)
      if (!active(actual, current, sending.signal)) return
      if (result.lookup_status !== 'NOT_FOUND') { item.message = result.operation ? '完成命令：' + operationLabel(result.operation.status) : '完成命令记录已过期，停止重新发送'; return }
    }
    const completion = await completeSourceUpload(actual, journal, state, sending.signal)
    if (!active(actual, current, sending.signal)) return
    item.message = '完成命令：' + operationLabel(completion.status); item.file = null
    const completedState = await readSourceUpload(actual, journal, sending.signal)
    if (!active(actual, current, sending.signal)) return
    const result = await readSourceUploadResult(actual, journal, completedState, sending.signal)
    if (active(actual, current, sending.signal)) { item.state = completedState; item.result = result }
  } finally { if (current === generation) { busy.value = false; controller = null; item.sent = 0 } }
}
watch(() => [session.value, route.fullPath], reset, { immediate: true, flush: 'sync' })
watch(() => items.value.map(item => ({ id: item.id, declaration: JSON.stringify(item.body) })), (current, previous) => {
  const before = new Map(previous?.map(item => [item.id, item.declaration]) ?? [])
  for (const item of current) {
    if (before.has(item.id) && before.get(item.id) !== item.declaration) {
      const changed = items.value.find(value => value.id === item.id)
      if (changed) changed.confirmed = false
    }
  }
}, { flush: 'sync' })
function visibility(): void { if (document.hidden) reset() }
document.addEventListener('visibilitychange', visibility)
window.addEventListener('pagehide', reset); window.addEventListener('offline', reset); window.addEventListener('medical-failure', reset)
onBeforeUnmount(() => {
  reset(); document.removeEventListener('visibilitychange', visibility)
  window.removeEventListener('pagehide', reset); window.removeEventListener('offline', reset); window.removeEventListener('medical-failure', reset)
})
</script>

<style scoped>
.source-upload-workspace { container-type: inline-size; }
.upload-workspace { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 20px; margin-top: 20px; align-items: start; }
.upload-file-list, .upload-detail { border: 1px solid var(--el-border-color-light); border-radius: 12px; background: var(--el-bg-color); min-width: 0; }
.upload-file-list { padding: 12px; max-height: 75vh; overflow: auto; }
.queue-heading { display: flex; align-items: center; justify-content: space-between; padding: 0 4px; }
.queue-heading h2 { font-size: 16px; }
.queue-heading span, .section-kicker, .file-id, .upload-help, .file-progress p { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
.upload-file-item { display: grid; gap: 7px; width: 100%; padding: 14px; margin-bottom: 8px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); text-align: left; color: var(--el-text-color-regular); cursor: pointer; overflow-wrap: anywhere; }
.upload-file-item strong { font-size: 14px; line-height: 1.5; }
.upload-file-item span { font-size: 12px; }
.upload-file-item.is-current { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.upload-file-item:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.file-local-state { color: var(--el-text-color-secondary); }
.upload-detail { padding: 22px; }
.file-detail-heading { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: center; }
.file-detail-heading h2 { font-size: 20px; margin: 6px 0; overflow-wrap: anywhere; }
.file-detail-heading a { color: var(--el-color-primary); font-size: 13px; }
.section-kicker { margin: 0; }
.file-id { overflow-wrap: anywhere; }
.file-status-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 20px 0; }
.file-status-grid > div { display: grid; gap: 8px; padding: 12px; background: var(--el-fill-color-extra-light); border-radius: 8px; }
.file-status-grid span { font-size: 12px; color: var(--el-text-color-secondary); }
.file-status-grid strong { font-size: 14px; font-weight: 500; }
.file-actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; }
.file-actions .el-button { margin-left: 0; }
.file-notice, .processing-unread { padding: 14px; background: var(--el-fill-color-light); border-radius: 8px; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.processing-unread h3 { font-size: 14px; margin-top: 0; }
.file-errors { color: var(--el-color-danger); overflow-wrap: anywhere; }
@container (max-width: 800px) { .upload-workspace { grid-template-columns: minmax(0, 1fr); } .upload-file-list { max-height: 300px; } .upload-detail { padding: 14px; } }
@container (max-width: 480px) { .file-status-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
