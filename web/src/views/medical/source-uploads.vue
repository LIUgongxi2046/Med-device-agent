<template>
  <main class="medical-page">
    <h1>上传项目资料</h1>
    <session-panel />
    <router-link v-if="validProject" :to="'/projects/' + projectId">返回项目详情</router-link>
    <p v-if="!validProject" role="alert">项目标识未通过接口校验</p>
    <p v-else-if="!permitted">上传资料需要当前企业身份及来源维护权限</p>
    <section v-if="permitted" data-source-upload-queue>
      <p>支持 PDF、DOCX、XLSX、CSV、PPTX、Markdown 和 TXT。文件分别填写来源和许可，服务器逐项核查容量、格式与内容。</p>
      <file-upload :selection-only="true" :file-type="Object.keys(sourceMediaTypes)" :file-size="0" :limit="100" :drag="false"
        :disabled="busy || user.medicalWriting" @select-file="selectFile" />
      <p v-if="reselect">请选择原文件：{{ reselect.id }}。继续前将核查原文件大小和摘要。</p>
      <el-button :disabled="busy || user.medicalWriting" @click="recover">查询原上传记录</el-button>
      <el-button :disabled="busy || user.medicalWriting || !items.some(item => item.confirmed && item.file)" @click="sendConfirmed">接收已确认文件</el-button>
      <el-button v-if="busy" @click="stop">停止发送</el-button>
      <p role="status">{{ notice }}</p>
      <el-table :data="items" row-key="id" empty-text="尚未选择文件">
        <el-table-column label="文件" min-width="240"><template #default="scope">{{ scope.row.body?.file_name ?? '待恢复原声明' }}<p>{{ scope.row.id }}</p></template></el-table-column>
        <el-table-column label="文件大小" min-width="130"><template #default="scope">{{ scope.row.file?.size ?? scope.row.journal?.size_bytes }} 字节</template></el-table-column>
        <el-table-column label="接收状态" min-width="150"><template #default="scope">{{ scope.row.state ? uploadStatusLabels[scope.row.state.registration.upload_status as keyof typeof uploadStatusLabels] : '尚未核查' }}</template></el-table-column>
        <el-table-column label="传输状态" min-width="170"><template #default="scope">{{ scope.row.state ? transferStatusLabels[scope.row.state.registration.transfer_state as keyof typeof transferStatusLabels] : '尚未登记' }}</template></el-table-column>
        <el-table-column label="接收进度" min-width="180"><template #default="scope">
          <p>服务器确认：{{ scope.row.state?.registration.received_size ?? '0' }} 字节</p><p>当前分段已发送：{{ scope.row.sent }} 字节</p>
        </template></el-table-column>
        <el-table-column label="操作结果" min-width="240"><template #default="scope">{{ scope.row.message }}</template></el-table-column>
        <el-table-column label="解析状态" min-width="150"><template #default>尚未取得处理结果</template></el-table-column>
        <el-table-column label="索引状态" min-width="150"><template #default>尚未取得处理结果</template></el-table-column>
        <el-table-column label="人工核准" min-width="150"><template #default>尚未取得核准记录</template></el-table-column>
        <el-table-column label="逐文件操作" min-width="260"><template #default="scope">
          <el-button link :disabled="busy || user.medicalWriting || !scope.row.body" @click="selected = scope.row">来源与许可</el-button>
          <el-button link :disabled="busy || user.medicalWriting" @click="reselect = scope.row">选择原文件</el-button>
          <el-button link :disabled="busy || user.medicalWriting || !scope.row.file || !scope.row.body" @click="send(scope.row)">核查并继续接收</el-button>
          <el-button v-if="!scope.row.journal" link :disabled="busy" @click="remove(scope.row)">移除待传文件</el-button>
        </template></el-table-column>
      </el-table>
      <p>解析状态：尚未取得处理结果。索引状态：尚未取得处理结果。人工核准状态：尚未取得核准记录。</p>
      <p>接收完成后仍需核查解析、索引和人工核准，当前页面不提供正式资料选用入口。</p>
      <p>关闭页面或停止发送会终止本地发送。重新进入页面后，使用“查询原上传记录”核查服务器结果；继续发送需要重新选择原文件。</p>
    </section>
    <el-dialog :model-value="selected !== null" title="逐文件来源与许可声明" width="80%" destroy-on-close @close="selected = null">
      <template v-if="selected?.body">
        <p>{{ selected.body.file_name }} · {{ selected.body.size_bytes }} 字节</p>
        <p>文件摘要：{{ selected.body.sha256 }}</p>
        <source-declaration :value="selected.body" :disabled="busy || selected.journal !== null && selected.state !== null" />
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
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { byteDigest, completeSourceUpload, createSourceUpload, lookupUploadRequest, prepareSourceUpload, prepareUploadPart,
  readSourceUpload, readUploadJournals, saveUploadJournal, sendUploadPart, sourceMediaType, sourceMediaTypes, sourceUploadAllowed,
  transferStatusLabels, uploadIdentity, uploadStatusLabels } from '@/api/medical/source-uploads'
import type { SourceUploadCreate, SourceUploadState, UploadJournal } from '@/api/medical/source-uploads'

interface UploadItem {
  id: string; file: File | null; body: SourceUploadCreate | null; journal: UploadJournal | null; state: SourceUploadState | null
  message: string; sent: number; errors: string[]; confirmed: boolean
}
const route = useRoute(); const user = useUserStore()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const validProject = computed(() => schemaIssues('urn:medical:source-uploads:1.0.0#/$defs/Id', projectId.value).length === 0)
const permitted = computed(() => validProject.value && sourceUploadAllowed(session.value, projectId.value))
const items = ref<UploadItem[]>([]); const selected = ref<UploadItem | null>(null); const reselect = ref<UploadItem | null>(null)
const busy = ref(false); const notice = ref(''); let generation = 0; let controller: AbortController | null = null; let batch = crypto.randomUUID()
function blank(file: File, hash: string, actor: string, batchId: string): SourceUploadCreate {
  return { schema_version: '1.0.0', batch_id: batchId, project_id: projectId.value, ownership: 'PROJECT', session_id: null,
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
  reselect.value = null; notice.value = ''; batch = crypto.randomUUID()
}
function stop(): void {
  generation += 1; controller?.abort(); controller = null; busy.value = false
  for (const item of items.value) { item.file = null; item.sent = 0; item.confirmed = false }
  selected.value = null; reselect.value = null; notice.value = '本地发送已经停止，请查询原上传记录核查服务器结果'
}
function remove(item: UploadItem): void {
  if (busy.value || item.journal) return
  items.value = items.value.filter(value => value.id !== item.id)
  if (selected.value?.id === item.id) selected.value = null
  if (reselect.value?.id === item.id) reselect.value = null
}
async function selectFile(file: File): Promise<void> {
  const actual = session.value; const current = generation; const replacement = reselect.value
  if (!actual || !permitted.value || busy.value || user.medicalWriting || document.hidden) return
  sourceMediaType(file)
  const hash = await byteDigest(await file.arrayBuffer())
  if (current !== generation || session.value !== actual || document.hidden) return
  if (replacement) {
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
    state: null, message: '请逐文件核查完整来源与许可', sent: 0, errors: [], confirmed: false })
  items.value.push(item); selected.value = item
}
function validateItem(item: UploadItem): boolean {
  item.errors = schemaIssues('urn:medical:source-uploads:1.0.0#/$defs/Create', item.body).map(issue => issue.path + '：' + issue.code)
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
        item = reactive<UploadItem>({ id: journal.item_id, file: null, body: null, journal, state: null, message: '正在查询原上传', sent: 0, errors: [], confirmed: false })
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
    item.state = state
    if (state.registration.transfer_state !== 'ACTIVE') { item.message = transferStatusLabels[state.registration.transfer_state]; return }
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
    if (active(actual, current, sending.signal)) item.state = completedState
  } finally { if (current === generation) { busy.value = false; controller = null; item.sent = 0 } }
}
watch(() => [session.value, route.fullPath], reset, { immediate: true, flush: 'sync' })
watch(() => selected.value?.body, () => { if (selected.value) selected.value.confirmed = false }, { deep: true, flush: 'sync' })
function visibility(): void { if (document.hidden) reset() }
document.addEventListener('visibilitychange', visibility)
window.addEventListener('pagehide', reset); window.addEventListener('offline', reset); window.addEventListener('medical-failure', reset)
onBeforeUnmount(() => {
  reset(); document.removeEventListener('visibilitychange', visibility)
  window.removeEventListener('pagehide', reset); window.removeEventListener('offline', reset); window.removeEventListener('medical-failure', reset)
})
</script>
