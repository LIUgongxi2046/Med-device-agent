<template>
  <section v-if="active" aria-label="当前有效位置与人工重新定位" data-document-reanchors>
    <h4>跨版本标注位置</h4>
    <p>原始意见和引用原文永久保留。这里只按人工确认的有效位置展示，不匹配相似文字。</p>
    <el-button :disabled="locked || !canRead" @click="load">刷新有效位置</el-button>
    <p v-if="!canRead">当前身份没有标注读取权限。</p>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在核验固定正文与位置历史</p>
    <el-button v-if="busy" @click="stopWaiting">停止等待并保留输入</el-button>
    <p v-if="listing && listing.revision !== snapshot.revision">文稿记录修订已变化，请刷新保存依据后再重新定位。</p>
    <el-button v-if="listing && listing.revision !== snapshot.revision && refreshDocument" :disabled="locked" @click="refreshBasis">刷新保存依据</el-button>
    <p v-if="listing?.items.length === 0">没有可展示的跨版本标注。</p>
    <nav aria-label="选择跨版本标注">
      <button v-for="item in listing?.items ?? []" :key="item.annotation.annotation_id" type="button" :disabled="locked"
        :aria-pressed="selectedId === item.annotation.annotation_id" @click="select(item.annotation.annotation_id)">
        {{ annotationStatusText[item.annotation.status] }} · {{ item.location_status === 'CURRENT_VERSION' ? '当前有效位置' : item.location_status === 'REANCHOR_REQUIRED' ? '需要人工选择新位置' : '历史位置' }} · {{ item.annotation.content.slice(0, 80) }}
      </button>
    </nav>
    <article v-if="selected">
      <p class="verbatim">{{ selected.annotation.content }}</p>
      <p>原作者 {{ selected.annotation.actor_id }} · {{ selected.annotation.created_at }}</p>
      <p>原始引用：{{ originalSummary || '尚未完成原固定正文核验' }}</p>
      <el-button :disabled="locked" @click="emit('read-original', selected.annotation.document_revision)">查看原版本、意见与回复</el-button>
      <p>有效位置版本：{{ selected.effective_anchor.document_revision }}</p>
      <p>{{ selected.location_status === 'CURRENT_VERSION' ? '当前有效引用' : '最后有效引用（不是当前正文位置）' }}：{{ effectiveSummary || '尚未完成对应固定正文核验' }}</p>
      <details><summary>完整位置历史</summary>
        <article v-for="anchor in anchorHistory?.items ?? []" :key="anchor.anchor_id">
          <p>版本 {{ anchor.document_revision }} · 标注修订 {{ anchor.annotation_revision }}</p>
          <p>{{ anchor.reason === null ? '原始位置' : '人工重新定位：' + anchor.reason }}</p>
          <p>{{ anchor.actor_id }} · {{ anchor.created_at }}</p>
          <el-button :disabled="locked" @click="emit('read-original', anchor.document_revision)">读取该固定正文</el-button>
          <pre>{{ JSON.stringify(anchor.selection, null, 2) }}</pre>
        </article>
      </details>
      <p v-if="history || !isCurrent || snapshot.signature_ids.length">历史或已签发正文只供核查，不能提交新位置。</p>
      <p v-else-if="dirty">请先保存正文，再人工选择新位置。</p>
      <p v-else-if="!canReview">当前身份没有人工审查决定权限。</p>
      <p v-else-if="legacyEligible">此批注仍保留原实际状态。请先明确核对旧位置，再人工选择新位置。</p>
      <p v-else-if="selected.annotation.status !== 'REANCHOR_REQUIRED'">仅待重新定位的标注可选择新位置。</p>
      <p v-if="anchorHistory && anchorHistory.items.length >= 100">已达到 100 个位置记录上限，历史可完整查看，不能再提交新位置。</p>
      <el-form v-if="legacyEligible || reconciliationPending?.annotation_id === selectedId" label-position="top" @submit.prevent="reconcile">
        <p>当前实际状态：{{ annotationStatusText[selected.annotation.status] }}（{{ selected.annotation.status }}）。原始位置属于旧正文；本次核对只记录当前操作者和时间。</p>
        <el-form-item label="旧位置核对原因"><el-input v-model="reconciliationReason" type="textarea" :rows="3" :disabled="busy || reconciliationRequest !== null" /></el-form-item>
        <el-checkbox v-model="reconciliationConfirmed" :disabled="busy || reconciliationRequest !== null">我已核对当前已保存正文，确认此旧位置需要重新定位；原始意见和历史保留</el-checkbox>
        <el-button type="warning" native-type="submit" :disabled="!canReconcile || !reconciliationConfirmed || !reconciliationReason">确认标记为待重新定位</el-button>
      </el-form>
      <el-form label-position="top" @submit.prevent="reanchor">
        <p>在上方已保存正文阅读区选择文字、图片或表格单元格，再点击使用当前选区。</p>
        <el-button :disabled="!canChoose || !selection || pending !== null" @click="chooseSelection">使用当前选区作为新位置</el-button>
        <p v-if="chosenSelection">新位置预览：{{ selectionSummary(baseline, chosenSelection) }}</p>
        <el-form-item label="重新定位原因"><el-input v-model="reason" type="textarea" :rows="3" :disabled="busy" /></el-form-item>
        <p>{{ Array.from(reason).length }} / 16000 字符</p>
        <el-checkbox v-model="confirmed" :disabled="busy">我已核对新位置；提交后状态变为待处理（OPEN），原始意见、引用和历史保留</el-checkbox>
        <el-button type="primary" native-type="submit" :disabled="!canReanchor || !confirmed || !reason || !chosenSelection || pending !== null">确认人工重新定位</el-button>
      </el-form>
    </article>
    <section v-if="reconciliationPending" aria-label="旧位置核对原请求恢复">
      <p>原旧位置核对请求尚待核查，不会创建替代请求。原因仅在本标签页内存保留。</p>
      <p v-if="reconciliationPending.document_revision !== snapshot.document_revision">请读取原请求的目标固定正文后恢复。</p>
      <el-button v-if="reconciliationPending.document_revision !== snapshot.document_revision" :disabled="locked" @click="emit('read-original', reconciliationPending.document_revision)">读取原核对目标版本</el-button>
      <template v-if="!reconciliationRequest">
        <el-input v-model="reconciliationReason" type="textarea" :disabled="busy" placeholder="输入与原请求完全相同的核对原因" />
        <el-checkbox v-model="reconciliationConfirmed" :disabled="busy">确认恢复原旧位置核对请求</el-checkbox>
      </template>
      <el-button :disabled="locked || !canRead" @click="recoverReconciliation">核查并继续原旧位置核对请求</el-button>
    </section>
    <section v-if="pending" aria-label="重新定位原请求恢复">
      <p>原重新定位请求尚待核查，不会创建替代请求。理由和选区只在本标签页内存保留。</p>
      <p v-if="pending.document_revision !== snapshot.document_revision">请读取原请求的目标固定正文后恢复。</p>
      <el-button v-if="pending.document_revision !== snapshot.document_revision" :disabled="locked" @click="emit('read-original', pending.document_revision)">读取原请求目标版本</el-button>
      <p v-if="!originalRequest">请重新输入完全相同的理由，并在原目标正文选择相同选区，再勾选人工确认。</p>
      <el-button :disabled="locked || !canRead" @click="recover">核查并继续原重新定位请求</el-button>
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { DocumentBody, DocumentSnapshot, Selection } from '../../../../contracts/typescript/src/interfaces'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { canonicalDocumentValue, immutableDocumentValue } from '@/api/medical/document-body'
import { annotationAllowed, annotationDraftKey, annotationStatusText, readPendingAnnotation, selectionSummary } from '@/api/medical/annotation-domain'
import { annotationSources } from '@/api/medical/annotation-sources'
import { lookupReanchor, prepareReanchor, readAnchorHistory, readAnnotationAnchors, readPendingReanchor, retainReanchorDraft, resumeReanchorDraft,
  settleReanchor, submitReanchor, verifyReanchorRequest } from '@/api/medical/annotation-reanchors'
import type { AnchorHistory, AnchorListing, PendingReanchor, ReanchorCreate } from '@/api/medical/annotation-reanchors'
import { legacyAnchorEligible, lookupReconciliation, prepareReconciliation, readPendingReconciliation, retainReconciliationDraft, resumeReconciliationDraft,
  settleReconciliation, submitReconciliation, verifyReconciliationRequest } from '@/api/medical/annotation-anchor-reconciliations'
import type { PendingReconciliation, ReconciliationCreate } from '@/api/medical/annotation-anchor-reconciliations'
import useUserStore from '@/store/modules/user'
const props = withDefaults(defineProps<{ session: MedicalSession; snapshot: DocumentSnapshot; baseline: DocumentBody; selection: Selection | null;
  active: boolean; dirty: boolean; busy?: boolean; history?: boolean; isCurrent: boolean; refreshDocument?: (signal: AbortSignal) => Promise<void> }>(), { busy: false, history: false })
const emit = defineEmits<{ busy: [value: boolean]; 'read-original': [revision: string] }>()
const user = useUserStore(), session = () => props.session
const listing = shallowRef<AnchorListing | null>(null), anchorHistory = shallowRef<AnchorHistory | null>(null), selectedId = ref('')
const reasons = shallowRef<Record<string, string>>({}), selections = shallowRef<Record<string, Selection>>({}), confirmed = ref(false)
const pending = shallowRef<PendingReanchor | null>(null), originalRequest = shallowRef<ReanchorCreate | null>(null)
const reconciliationPending = shallowRef<PendingReconciliation | null>(null), reconciliationRequest = shallowRef<ReconciliationCreate | null>(null)
const reconciliationReasons = shallowRef<Record<string, string>>({}), reconciliationConfirmed = ref(false)
const busy = ref(false), error = ref(''), notice = ref(''), originalSummary = ref(''), effectiveSummary = ref('')
let controller: AbortController | null = null, generation = 0, ownedDraft: string | null = null, ownsWrite = false
const selected = computed(() => listing.value?.items.find(item => item.annotation.annotation_id === selectedId.value) ?? null)
const reason = computed({ get: () => reasons.value[selectedId.value] ?? '', set: value => { reasons.value = { ...reasons.value, [selectedId.value]: value }; confirmed.value = false } })
const reconciliationReason = computed({ get: () => reconciliationReasons.value[reconciliationPending.value?.annotation_id ?? selectedId.value] ?? '', set: value => { reconciliationReasons.value = { ...reconciliationReasons.value, [reconciliationPending.value?.annotation_id ?? selectedId.value]: value }; reconciliationConfirmed.value = false } })
const chosenSelection = computed(() => selections.value[selectedId.value] ?? null)
const locked = computed(() => busy.value || props.busy || user.medicalWriting)
const canRead = computed(() => props.active && annotationAllowed(props.session, props.snapshot, 'annotation.read'))
const canReview = computed(() => props.active && annotationAllowed(props.session, props.snapshot, 'review.decide'))
const canChoose = computed(() => !locked.value && canRead.value && canReview.value && !props.dirty && !props.history && props.isCurrent && !props.snapshot.signature_ids.length)
const legacyEligible = computed(() => selected.value !== null && legacyAnchorEligible(selected.value, props.snapshot, anchorHistory.value))
const canReconcile = computed(() => canChoose.value && legacyEligible.value && listing.value?.revision === props.snapshot.revision && !pending.value && !reconciliationPending.value)
const canReanchor = computed(() => canChoose.value && !reconciliationPending.value && selected.value?.annotation.status === 'REANCHOR_REQUIRED' && listing.value?.revision === props.snapshot.revision
  && anchorHistory.value?.annotation_id === selectedId.value && anchorHistory.value.items.length < 100 && anchorHistory.value.revision === selected.value.annotation.revision)
function retain(): void { if (ownedDraft) { retainReanchorDraft(ownedDraft, { reasons: reasons.value, selections: selections.value, request: originalRequest.value }); retainReconciliationDraft(ownedDraft, { reasons: reconciliationReasons.value, request: reconciliationRequest.value }) } }
function release(): void { busy.value = false; emit('busy', false); if (ownsWrite) { user.medicalWriting = false; ownsWrite = false } }
function stopWaiting(): void { generation++; controller?.abort(); controller = null; retain(); release(); notice.value = pending.value || reconciliationPending.value ? '已停止等待，服务器可能仍在处理。请核查原请求。' : '已停止读取，输入保留。' }
function current(token: number, signal: AbortSignal): void { signal.throwIfAborted(); if (token !== generation || !props.active) fail('CONTEXT_CHANGED', '重新定位期间页面已变化') }
async function run(write: boolean, action: (signal: AbortSignal, token: number) => Promise<void>): Promise<void> {
  if (locked.value || !props.active) return
  const request = new AbortController(), token = ++generation; controller = request; busy.value = true; error.value = ''; notice.value = ''; emit('busy', true)
  if (write) { ownsWrite = true; user.medicalWriting = true }
  try { await action(request.signal, token); current(token, request.signal) }
  catch (failure) { if (token === generation && !request.signal.aborted) error.value = failure instanceof Error ? failure.message : '重新定位失败，原输入保留。' }
  finally { if (token === generation) { controller = null; release() } }
}
async function loadSelected(signal: AbortSignal, token: number): Promise<void> {
  const item = selected.value; if (!item) return
  const history = await readAnchorHistory(session, item.annotation.annotation_id, signal); current(token, signal)
  if (history.document_id !== props.snapshot.document_id || history.revision !== item.annotation.revision || history.effective_anchor_id !== item.effective_anchor.anchor_id
      || history.anchor_revision !== item.anchor_revision || history.status !== item.annotation.status
      || canonicalDocumentValue(history.items.at(-1)) !== canonicalDocumentValue(item.effective_anchor)) fail('REVISION_CONFLICT', '标注位置已变化，请刷新')
  const first = history.items[0]
  if (!first || first.document_revision !== item.annotation.document_revision || first.selection_artifact_id !== item.annotation.selection_artifact_id || first.selection_sha256 !== item.annotation.selection_sha256) fail('CONTENT_MISMATCH', '位置历史原始引用与批注意见不一致')
  const source = annotationSources(session, props.snapshot, props.baseline, signal)
  const original = await source(item.annotation.selection); current(token, signal)
  const effective = await source(item.effective_anchor.selection); current(token, signal)
  for (const anchor of history.items) { await source(anchor.selection); current(token, signal) }
  originalSummary.value = selectionSummary(original.body, item.annotation.selection); effectiveSummary.value = selectionSummary(effective.body, item.effective_anchor.selection)
  anchorHistory.value = history
}
async function load(): Promise<void> {
  if (!canRead.value) return
  await run(false, async (signal, token) => {
    const value = await readAnnotationAnchors(session, props.snapshot, props.baseline, signal); current(token, signal)
    listing.value = value; anchorHistory.value = null; originalSummary.value = ''; effectiveSummary.value = ''
    selectedId.value = value.items.some(item => item.annotation.annotation_id === selectedId.value) ? selectedId.value : reconciliationPending.value?.annotation_id ?? pending.value?.annotation_id ?? value.items[0]?.annotation.annotation_id ?? ''
    await loadSelected(signal, token)
  })
}
async function select(id: string): Promise<void> {
  if (locked.value) return
  selectedId.value = id; anchorHistory.value = null; confirmed.value = false; reconciliationConfirmed.value = false; originalSummary.value = ''; effectiveSummary.value = ''
  await run(false, loadSelected)
}
function chooseSelection(): void { if (canChoose.value && props.selection && props.selection.document_revision === props.snapshot.document_revision) { selections.value = { ...selections.value, [selectedId.value]: immutableDocumentValue(props.selection) }; confirmed.value = false; retain() } }
async function complete(input: ReanchorCreate, signal: AbortSignal, token: number): Promise<void> {
  const original = pending.value; if (!original) fail('ACTION_STALE', '没有待恢复的重新定位请求')
  await verifyReanchorRequest(session, original, props.snapshot, props.baseline, input, signal); current(token, signal)
  originalRequest.value = immutableDocumentValue(input); retain()
  const receipt = await submitReanchor(session, original, props.snapshot, props.baseline, input, signal); current(token, signal)
  const value = await settleReanchor(session, original, props.snapshot, props.baseline, input, receipt, signal); current(token, signal)
  listing.value = value; pending.value = null; originalRequest.value = null; confirmed.value = false
  if (reasons.value[original.annotation_id] === input.reason) { const next = { ...reasons.value }; delete next[original.annotation_id]; reasons.value = next }
  const next = { ...selections.value }; delete next[original.annotation_id]; selections.value = next; retain()
  notice.value = '人工重新定位原回执已核验：提交时状态为待处理（OPEN）。正在刷新当前状态。'
  if (props.refreshDocument) await props.refreshDocument(signal)
  current(token, signal); await loadSelected(signal, token)
  notice.value = '人工重新定位与不可变位置历史已核验；页面展示当前状态，原始意见及引用保留。'
}
async function reanchor(): Promise<void> {
  if (!canReanchor.value || !selected.value || !chosenSelection.value || !confirmed.value || pending.value) return
  if (readPendingAnnotation(props.session, props.snapshot.document_id)) { error.value = '请先核查原标注写入请求'; return }
  const item = selected.value
  const input: ReanchorCreate = immutableDocumentValue({ schema_version: '1.0.0', document_revision: props.snapshot.document_revision,
    expected_revision: item.annotation.revision, expected_document_revision: props.snapshot.revision, expected_anchor_id: item.effective_anchor.anchor_id,
    expected_anchor_revision: item.anchor_revision, selection: chosenSelection.value, reason: reason.value, human_confirmed: true })
  await run(true, async (signal, token) => { const prepared = await prepareReanchor(session, props.snapshot, props.baseline, item, input, signal); current(token, signal); pending.value = prepared; await complete(input, signal, token) })
}
async function recover(): Promise<void> {
  if (!pending.value) return
  await run(true, async (signal, token) => {
    const original = pending.value!, result = await lookupReanchor(session, original, signal); current(token, signal)
    if (original.document_revision !== props.snapshot.document_revision) { notice.value = '请先读取原请求的目标固定正文。'; return }
    if (result.lookup_status === 'EXPIRED' || result.operation?.status === 'FAILED') { notice.value = '原操作结果无法确认，请保留原请求并联系管理员核查。'; return }
    if ((props.dirty || props.history || !props.isCurrent || props.snapshot.signature_ids.length) && result.lookup_status !== 'FOUND') { notice.value = '此页面只允许核查已登记原操作，不会创建新写入。'; return }
    let input = originalRequest.value
    if (!input) {
      if (!confirmed.value || !props.selection || props.selection.document_revision !== original.document_revision) { notice.value = '请选择原目标正文的相同选区、输入相同理由，并勾选人工确认。'; return }
      input = { schema_version: '1.0.0', document_revision: original.document_revision, expected_revision: original.expected_revision,
        expected_document_revision: original.expected_document_revision, expected_anchor_id: original.expected_anchor_id, expected_anchor_revision: original.expected_anchor_revision,
        selection: props.selection, reason: reasons.value[original.annotation_id] ?? '', human_confirmed: true }
    }
    await complete(input, signal, token)
  })
}
async function completeReconciliation(input: ReconciliationCreate, signal: AbortSignal, token: number): Promise<void> {
  const original = reconciliationPending.value; if (!original) fail('ACTION_STALE', '没有待恢复的旧位置核对请求')
  await verifyReconciliationRequest(session, original, props.snapshot, props.baseline, input, signal); current(token, signal)
  reconciliationRequest.value = immutableDocumentValue(input); retain()
  const receipt = await submitReconciliation(session, original, props.snapshot, props.baseline, input, signal); current(token, signal)
  await settleReconciliation(session, original, props.snapshot, props.baseline, input, receipt, signal); current(token, signal)
  reconciliationPending.value = null; reconciliationRequest.value = null; reconciliationConfirmed.value = false; confirmed.value = false
  // Never project the historical receipt as current state. Re-read the displayed snapshot/list/history.
  listing.value = null; anchorHistory.value = null; retain()
  if (props.refreshDocument) await props.refreshDocument(signal)
  current(token, signal)
  const value = await readAnnotationAnchors(session, props.snapshot, props.baseline, signal); current(token, signal); listing.value = value
  await loadSelected(signal, token)
  notice.value = '本次旧位置核对的真实操作者、时间和原回执已核验。当前状态已刷新；可继续人工选择新位置，提交后变为待处理（OPEN）。'
}
async function reconcile(): Promise<void> {
  if (!canReconcile.value || !selected.value || !reconciliationConfirmed.value || !reconciliationReason.value) return
  const item = selected.value
  const input: ReconciliationCreate = immutableDocumentValue({ schema_version: '1.0.0', document_revision: props.snapshot.document_revision,
    expected_revision: item.annotation.revision, expected_document_revision: props.snapshot.revision, expected_anchor_id: item.effective_anchor.anchor_id,
    expected_anchor_revision: item.anchor_revision, reason: reconciliationReason.value, human_confirmed: true })
  await run(true, async (signal, token) => {
    const prepared = await prepareReconciliation(session, props.snapshot, props.baseline, item, input, signal); current(token, signal)
    reconciliationPending.value = prepared; await completeReconciliation(input, signal, token)
  })
}
async function recoverReconciliation(): Promise<void> {
  if (!reconciliationPending.value) return
  await run(true, async (signal, token) => {
    const original = reconciliationPending.value!, result = await lookupReconciliation(session, original, signal); current(token, signal)
    if (original.document_revision !== props.snapshot.document_revision) { notice.value = '请先读取原请求的目标固定正文。'; return }
    if (result.lookup_status === 'EXPIRED' || result.operation?.status === 'FAILED') { notice.value = '原操作结果无法确认，请保留原请求并联系管理员核查。'; return }
    if ((props.dirty || props.history || !props.isCurrent || props.snapshot.signature_ids.length) && result.lookup_status !== 'FOUND') { notice.value = '此页面只允许核查已登记原操作，不会创建新写入。'; return }
    let input = reconciliationRequest.value
    if (!input) {
      if (!reconciliationConfirmed.value) { notice.value = '请填写相同原因并确认恢复原请求。'; return }
      input = { schema_version: '1.0.0', document_revision: original.document_revision, expected_revision: original.expected_revision,
        expected_document_revision: original.expected_document_revision, expected_anchor_id: original.expected_anchor_id, expected_anchor_revision: original.expected_anchor_revision,
        reason: reconciliationReason.value, human_confirmed: true }
    }
    await completeReconciliation(input, signal, token)
  })
}
async function refreshBasis(): Promise<void> { if (props.refreshDocument) await run(true, async (signal, token) => { await props.refreshDocument!(signal); current(token, signal) }) }
function reset(): void {
  generation++; controller?.abort(); controller = null; release(); listing.value = null; anchorHistory.value = null; selectedId.value = ''; reasons.value = {}; selections.value = {}
  confirmed.value = false; reconciliationConfirmed.value = false; reconciliationPending.value = null; reconciliationRequest.value = null; reconciliationReasons.value = {}; pending.value = null; originalRequest.value = null; originalSummary.value = ''; effectiveSummary.value = ''; error.value = ''; notice.value = ''; ownedDraft = null
}
watch(() => [props.active, props.session.user.user_id, props.session.current_context?.tenant_id, props.session.current_context?.context_revision,
  props.session.current_context?.auth_epoch, props.session.current_context?.governance_epoch, props.snapshot.document_id, props.snapshot.document_revision], () => {
  retain(); reset(); if (!props.active || !props.session.current_context) return
  try { ownedDraft = annotationDraftKey(props.session, props.snapshot.document_id, props.snapshot.document_revision)
    const draft = resumeReanchorDraft(ownedDraft); if (draft) { reasons.value = draft.reasons; selections.value = draft.selections; originalRequest.value = draft.request }
    pending.value = readPendingReanchor(props.session, props.snapshot.document_id); if (!pending.value) originalRequest.value = null
    const reconciliationDraft = resumeReconciliationDraft(ownedDraft); if (reconciliationDraft) { reconciliationReasons.value = reconciliationDraft.reasons; reconciliationRequest.value = reconciliationDraft.request }
    reconciliationPending.value = readPendingReconciliation(props.session, props.snapshot.document_id); if (!reconciliationPending.value) reconciliationRequest.value = null
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '无法读取恢复记录'; return }
  void load()
}, { immediate: true, flush: 'sync' })
watch(() => [props.busy, canRead.value, user.medicalWriting, props.snapshot.revision], () => { if (!props.busy && canRead.value && (!listing.value || BigInt(listing.value.revision) < BigInt(props.snapshot.revision)) && !busy.value && !error.value) void load() })
watch(() => [props.dirty, props.history, props.isCurrent, props.snapshot.signature_ids.join(',')], () => { confirmed.value = false; reconciliationConfirmed.value = false; if (busy.value) stopWaiting() }, { flush: 'sync' })
function invalidate(): void { reset() }
function unloading(event: BeforeUnloadEvent): void { if (pending.value || reconciliationPending.value || Object.values(reconciliationReasons.value).some(Boolean) || Object.values(reasons.value).some(Boolean)) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('medical-context-invalidated', invalidate); window.addEventListener('beforeunload', unloading)
onBeforeUnmount(() => { retain(); generation++; controller?.abort(); release(); window.removeEventListener('medical-context-invalidated', invalidate); window.removeEventListener('beforeunload', unloading) })
</script>
<style scoped>
nav { display: flex; flex-wrap: wrap; gap: 8px; margin-block: 12px; }
.verbatim, pre { white-space: pre-wrap; overflow-wrap: anywhere; }
article { border-top: 1px solid #dcdfe6; padding-block: 12px; }
</style>
