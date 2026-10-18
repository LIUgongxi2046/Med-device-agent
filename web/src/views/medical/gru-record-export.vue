<template>
  <section class="gru-export" aria-label="导出用户会话记录">
    <h4>导出用户会话记录</h4>
    <p>仅导出此会话完整固定版本的 USER 文字记录（RECORD_ONLY / NOT_EXECUTED）。不包含模型回答、来源原文、私有授权或正文草稿。</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="loading" role="status">正在核查导出依据或原操作…</p>
    <p v-if="checked && !enabled">当前服务器尚未启用用户记录导出。</p>
    <el-button v-if="checked && !enabled" :disabled="busy" @click="reset">重新核查部署状态</el-button>
    <template v-if="enabled">
      <p v-if="!canExport">需要独立的 conversation.export 和当前 conversation.read 权限；读取或使用会话不代表可以导出。</p>
      <template v-if="pending">
        <p>原导出请求已保留，固定会话修订 {{ pending.body.basis.conversation_revision }}，共 {{ pending.body.basis.message_count }} 条用户记录。</p>
        <p v-if="recoveryState === 'NOT_FOUND'">尚未查到原操作。仍保留原 key 与请求，不自动重试或创建替代导出。</p>
        <p v-if="recoveryState === 'PROCESSING'">原操作尚未闭合。此状态不表示后台正在继续执行。</p>
        <el-button :disabled="busy || !canExport" @click="recover">只读核查原导出</el-button>
        <details><summary>原请求核验信息</summary><dl><dt>操作键</dt><dd>{{ pending.key }}</dd><dt>完整请求摘要</dt><dd>{{ pending.body_sha256 }}</dd><dt>有序全部消息清单摘要</dt><dd>{{ pending.body.basis.message_manifest_sha256 }}</dd></dl></details>
      </template>
      <template v-else>
        <el-button :disabled="busy || !canExport" @click="loadBasis">读取完整固定导出依据</el-button>
        <section v-if="basis">
          <p>完整清单已读取：修订 {{ basis.conversation_revision }}，{{ basis.message_count }} 条用户记录。导出覆盖全部清单，与当前消息分页无关。</p>
          <details><summary>固定文稿与清单摘要</summary><dl><dt>文稿版本</dt><dd>{{ basis.document_revision }}</dd><dt>消息清单摘要</dt><dd>{{ basis.message_manifest_sha256 }}</dd><dt>正文摘要</dt><dd>{{ basis.body_sha256 }}</dd></dl></details>
          <label>导出理由<el-input v-model="reason" type="textarea" :disabled="busy" maxlength="2000" /></label>
          <el-checkbox v-model="confirmed" :disabled="busy">确认用途为用户记录导出；文件中的 CHECK 等意图不代表已执行检查或形成医学结论。</el-checkbox>
          <el-button type="primary" :disabled="busy || !canExport || !confirmed" @click="create">创建固定 JSON 导出</el-button>
        </section>
      </template>
      <section v-if="receipt">
        <h5>导出已登记</h5><p>真实 JSON 文件 {{ receipt.size_bytes }} 字节。创建成功与下载权限分别核验。</p>
        <p v-if="!canDownload">当前授权摘要未包含此制品的 artifact.read。保留原回执，可在获得实际读取授权后核查下载。</p>
        <el-button :disabled="busy || !canExport || !canDownload" @click="download">核验并下载 JSON</el-button>
        <p v-if="downloaded" role="status">文件字节、长度、摘要及完整消息清单已核验，下载已发起。请确认浏览器保存结果。</p>
        <el-button v-if="downloaded" :disabled="busy" @click="finish">已保存文件，准备新的导出</el-button>
        <details><summary>导出核验详情</summary><dl><dt>导出标识</dt><dd>{{ receipt.export_id }}</dd><dt>制品标识</dt><dd>{{ receipt.artifact_id }}</dd><dt>文件 SHA-256</dt><dd>{{ receipt.content_sha256 }}</dd></dl></details>
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from '@/api/medical/protocol'
import type { GruView } from '@/api/medical/gru-records'
import { canonicalDocumentValue as canonical } from '@/api/medical/document-body'
import { gruExportAllowed, gruExportDownloadAllowed, gruExportAvailability, readGruExportBasis, gruExportReasonIssue, prepareGruExport, verifyGruExportPending, createGruExport, recoverGruExport, readGruExport, downloadGruExport, retainGruExport, restoreGruExport, finishGruExport } from '@/api/medical/gru-record-export'
import type { GruExportBasis, GruExportPending, GruExportReceipt } from '@/api/medical/gru-record-export'
const props = defineProps<{ session: MedicalSession; selected: GruView; active: boolean; disabled?: boolean }>()
const user = useUserStore()
const enabled = ref(false), checked = ref(false), loading = ref(false), error = ref(''), reason = ref(''), confirmed = ref(false), recoveryState = ref(''), downloaded = ref(false)
const basis = shallowRef<GruExportBasis | null>(null), pending = shallowRef<GruExportPending | null>(null), receipt = shallowRef<GruExportReceipt | null>(null)
const busy = computed(() => loading.value || !!props.disabled || user.medicalWriting)
const canExport = computed(() => props.active && enabled.value && gruExportAllowed(props.session, props.selected, 'conversation.export') && gruExportAllowed(props.session, props.selected, 'conversation.read'))
const canDownload = computed(() => !!receipt.value && gruExportDownloadAllowed(props.session, props.selected.project_id, receipt.value))
let alive = true, generation = 0, controller = new AbortController(), ownWrite: object | null = null
let objectUrl: string | null = null, cleanupTimer: ReturnType<typeof setTimeout> | null = null
function releaseUrl() {
  if (cleanupTimer !== null) clearTimeout(cleanupTimer)
  cleanupTimer = null
  if (objectUrl !== null) URL.revokeObjectURL(objectUrl)
  objectUrl = null
}
const live = (g: number, s: MedicalSession) => alive && props.active && !document.hidden && generation === g && props.session === s
function clear() {
  generation++; controller.abort(); controller = new AbortController(); releaseUrl()
  enabled.value = false; checked.value = false; loading.value = false; basis.value = null; pending.value = null; receipt.value = null
  confirmed.value = false; reason.value = ''; error.value = ''; recoveryState.value = ''; downloaded.value = false
}
async function reset() {
  clear()
  if (!props.active || document.hidden || !props.session.current_context) return
  const g = generation, s = props.session
  loading.value = true
  try {
    const original = restoreGruExport(s, props.selected.conversation_id)
    if (original) await verifyGruExportPending(s, original)
    if (!live(g, s)) return
    pending.value = original
    const value = await gruExportAvailability(s, controller.signal)
    if (live(g, s)) { enabled.value = value; checked.value = true }
  } catch (failure) { if (live(g, s)) error.value = failure instanceof Error ? failure.message : '导出状态读取失败' }
  finally { if (live(g, s)) loading.value = false }
}
async function loadBasis() {
  if (busy.value || !canExport.value || pending.value) return
  const g = generation, s = props.session; basis.value = null; confirmed.value = false; loading.value = true; error.value = ''
  try { const value = await readGruExportBasis(s, props.selected, controller.signal); if (live(g, s)) basis.value = value }
  catch (failure) { if (live(g, s)) error.value = failure instanceof Error ? failure.message : '完整导出依据读取失败' }
  finally { if (live(g, s)) loading.value = false }
}
async function create() {
  if (busy.value || !canExport.value || !basis.value || pending.value || !confirmed.value) return
  const issue = gruExportReasonIssue(reason.value)
  if (issue) { error.value = issue; return }
  const g = generation, s = props.session, fixedBasis = basis.value, originalReason = reason.value
  loading.value = true; error.value = ''; let token: object | null = null
  try {
    const original = await prepareGruExport(s, fixedBasis, originalReason)
    if (!live(g, s) || user.medicalWriting || !canExport.value) return
    retainGruExport(s, original); pending.value = original
    token = {}; ownWrite = token; user.medicalWriting = true
    const value = await createGruExport(s, original, controller.signal)
    if (live(g, s)) { receipt.value = value; recoveryState.value = 'COMPLETED' }
  } catch (failure) { if (live(g, s)) error.value = failure instanceof Error ? failure.message : '导出结果未知，请只读核查原操作' }
  finally { if (token && ownWrite === token) { ownWrite = null; user.medicalWriting = false }; if (live(g, s)) loading.value = false }
}
async function recover() {
  if (busy.value || !canExport.value || !pending.value) return
  const g = generation, s = props.session, original = pending.value
  loading.value = true; error.value = ''
  try {
    const value = await recoverGruExport(s, original, controller.signal)
    if (live(g, s)) { recoveryState.value = value.status; receipt.value = value.receipt }
  } catch (failure) { if (live(g, s)) error.value = failure instanceof Error ? failure.message : '原导出核查失败' }
  finally { if (live(g, s)) loading.value = false }
}
async function download() {
  if (busy.value || !canExport.value || !canDownload.value || !pending.value || !receipt.value) return
  const g = generation, s = props.session, original = pending.value, fixedReceipt = receipt.value
  releaseUrl(); loading.value = true; error.value = ''; downloaded.value = false
  try {
    await readGruExport(s, original, fixedReceipt, controller.signal)
    if (!live(g, s)) return
    const blob = await downloadGruExport(s, original, fixedReceipt, controller.signal)
    if (!live(g, s)) return
    objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = objectUrl; link.download = 'gru-records-' + fixedReceipt.export_id + '.json'
    document.body.appendChild(link)
    try { link.click(); downloaded.value = true } finally { link.remove() }
    cleanupTimer = setTimeout(releaseUrl, 30000)
  } catch (failure) { releaseUrl(); if (live(g, s)) error.value = failure instanceof Error ? failure.message : '下载未完成，保留原回执' }
  finally { if (live(g, s)) loading.value = false }
}
async function finish() {
  if (busy.value || !downloaded.value || !pending.value || !receipt.value || recoveryState.value !== 'COMPLETED') return
  const g = generation, s = props.session, original = pending.value, completed = receipt.value
  loading.value = true
  try {
    await finishGruExport(s, original, completed)
    if (live(g, s)) { releaseUrl(); pending.value = null; receipt.value = null; basis.value = null; downloaded.value = false; confirmed.value = false; recoveryState.value = ''; reason.value = '' }
  } catch (failure) { if (live(g, s)) error.value = failure instanceof Error ? failure.message : '原回执保留失败，请勿创建新请求' }
  finally { if (live(g, s)) loading.value = false }
}
watch(() => user.medicalWriting, value => { if (!value) ownWrite = null }, { flush: 'sync' })
watch(() => [props.session, canonical(props.session.current_context), props.session.user.user_id, canonical(props.session.action_summary), props.selected, props.selected.conversation_id, props.selected.revision, props.selected.project_id, props.selected.document_id, props.selected.document_revision, props.selected.scope_snapshot_id, props.selected.owner_id, canonical(props.selected.scope), props.active], reset, { immediate: true })
function visibility() { if (document.hidden) clear(); else void reset() }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { alive = false; clear(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.gru-export { margin-block: 16px; padding: 18px; border: 1px solid var(--el-border-color); border-radius: 10px; background: var(--el-bg-color); }
p { color: var(--el-text-color-secondary); line-height: 1.65; } h4, h5 { color: var(--el-text-color-primary); }
label { display: block; margin-block: 14px; } .el-checkbox { height: auto; white-space: normal; margin-block: 14px; }
details { margin-block: 12px; } summary { cursor: pointer; color: var(--el-color-primary); } dt { color: var(--el-text-color-secondary); font-size: 12px; } dd { margin: 4px 0 12px; overflow-wrap: anywhere; }
.error { color: var(--el-color-danger); } @media (max-width: 720px) { .gru-export { padding: 12px; } }
</style>
