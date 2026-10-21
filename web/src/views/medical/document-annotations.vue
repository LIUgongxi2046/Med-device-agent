<template>
  <section v-if="active" class="document-annotations" aria-label="文稿标注" data-document-annotations>
    <div class="annotation-heading"><h4>标注与讨论</h4><el-button :disabled="locked || !canRead" @click="load">刷新标注</el-button></div>
    <p v-if="!canRead" role="status">当前身份没有此文稿的标注读取权限。</p>
    <p v-if="busy" role="status">{{ busyText }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <el-button v-if="busy" @click="stopWaiting">停止等待并保留输入</el-button>
    <p v-if="listing && listing.revision !== snapshot.revision" role="status">文稿记录修订已变化。请刷新保存依据后再新建或处理标注，当前正文编辑会保留。</p>
    <el-button v-if="listing && listing.revision !== snapshot.revision && refreshDocument" :disabled="locked" @click="refreshBasis">刷新保存依据</el-button>
    <p v-if="listing && listing.items.length === 0">这个固定正文版本还没有标注。</p>
    <div v-if="listing && listing.items.length" class="annotation-layout">
      <nav aria-label="选择标注">
        <button v-for="item in listing.items" :key="item.annotation_id" type="button" :disabled="busy" :aria-pressed="selectedId === item.annotation_id"
          class="thread-button" @click="selectThread(item.annotation_id)">
          <span>{{ annotationStatusText[item.status] }} · {{ item.location_status === 'CURRENT_VERSION' ? '当前版本位置' : '原版本位置' }}</span>
          <span class="thread-content">{{ excerpt(item.content) }}</span>
          <span>{{ item.reply_count }} 条回复</span>
        </button>
      </nav>
      <article v-if="selected" aria-label="选中标注">
        <p>{{ annotationStatusText[replies?.status ?? selected.status] }} · {{ (replies?.location_status ?? selected.location_status) === 'CURRENT_VERSION' ? '当前版本位置' : '原版本位置' }}</p>
        <p v-if="(replies?.location_status ?? selected.location_status) === 'ORIGINAL_VERSION'" role="status">此选区只在原固定正文版本中核验，未声称已定位到最新正文。</p>
        <p v-if="(replies?.status ?? selected.status) === 'REANCHOR_REQUIRED'" role="status">{{ effective ? '该标注需要重新定位：先重新定位，再回复或处理；原始意见和历史保留。' : '该标注需要重新定位；回复不会改变定位或处理状态。' }}</p>
        <p class="selection-summary">{{ selected.effective_anchor?.document_revision === snapshot.document_revision ? '有效位置：' + selectionSummary(baseline, selected.effective_anchor.selection) : selected.document_revision === snapshot.document_revision ? '原始引用：' + selectionSummary(baseline, selected.selection) : '原始引用属于其他固定正文，请查看原版本。' }}</p>
        <el-button v-if="selected.document_revision !== snapshot.document_revision" :disabled="locked" @click="emit('read-original', selected.document_revision)">查看原始引用与意见</el-button>
        <p class="comment-text">{{ selected.content }}</p>
        <small>{{ selected.created_at }} · 作者 {{ selected.actor_id }}</small>
        <details><summary>版本与选区详情</summary><p>正文版本 {{ selected.document_revision }}；标注修订 {{ replies?.revision ?? selected.revision }}</p><pre>{{ JSON.stringify(selected.selection, null, 2) }}</pre></details>
        <div v-if="replies" aria-label="完整回复">
          <p v-if="replies.items.length === 0">还没有回复。</p>
          <article v-for="reply in replies.items" :key="reply.reply_id" class="reply">
            <p class="comment-text">{{ reply.content }}</p><small>{{ reply.created_at }} · 作者 {{ reply.actor_id }}</small>
          </article>
        </div>
        <section aria-label="人工处理标注">
          <h4>人工处理与不可变历史</h4>
          <p v-if="!canReview">当前身份没有此文稿的人工审查决定权限。</p>
          <p v-else-if="dirty">请先保存正文，再处理标注。</p>
          <p v-else-if="history || !isCurrent || snapshot.signature_ids.length > 0">只允许处理当前未签发的已保存正文。</p>
          <p v-else-if="!decisionTransition(decisions?.status ?? selected.status, 'RESOLVE') && !decisionTransition(decisions?.status ?? selected.status, 'REOPEN')">当前标注状态不支持解决或重开。</p>
          <p v-if="decisions && decisions.items.length >= 100" role="status">该标注已达到 100 条人工处理记录上限，历史仍可完整查看，不能再提交处理决定。</p>
          <p v-if="decisions && decisions.items.length === 0">尚无人工处理记录。</p>
          <article v-for="item in decisions?.items ?? []" :key="item.decision_id" class="reply">
            <p>{{ item.decision === 'RESOLVE' ? '人工解决' : '人工重开' }} · {{ annotationStatusText[item.previous_status] }} → {{ annotationStatusText[item.status] }}</p>
            <p class="comment-text">{{ item.reason }}</p>
            <small>{{ item.created_at }} · 处理人 {{ item.actor_id }} · 修订 {{ item.previous_revision }} → {{ item.revision }}</small>
          </article>
          <el-form label-position="top" @submit.prevent>
            <el-form-item label="人工处理理由"><el-input v-model="decisionReason" type="textarea" :rows="3" :disabled="busy" placeholder="填写解决或重开的具体理由，原文会永久保留" /></el-form-item>
            <p>{{ Array.from(decisionReason).length }} / 16000 字符</p>
            <el-button v-if="decisions?.status === 'OPEN'" :disabled="!canDecide || !decisionReason || pending !== null" @click="decide('RESOLVE')">确认解决</el-button>
            <el-button v-if="decisions?.status === 'RESOLVED'" :disabled="!canDecide || !decisionReason || pending !== null" @click="decide('REOPEN')">确认重开</el-button>
          </el-form>
        </section>
        <p v-if="effective && replies && replies.document_revision !== snapshot.document_revision">此标注仍在旧有效正文位置。请先人工重新定位到当前已保存正文，再回复或处理；历史仍可查看。</p>
        <el-form label-position="top" @submit.prevent="appendReply">
          <el-form-item label="回复这条标注"><el-input v-model="replyText" type="textarea" :rows="3" :disabled="busy" placeholder="保留原文、空格与换行" /></el-form-item>
          <p>{{ Array.from(replyText).length }} / 16000 字符</p>
          <el-button type="primary" native-type="submit" :disabled="!canReply || !replyText || pending !== null">提交回复</el-button>
        </el-form>
      </article>
    </div>
    <section aria-label="新建标注">
      <h4>为选中内容添加标注</h4>
      <p v-if="dirty">请先保存正文，再重新选择需要标注的内容。</p>
      <p v-else-if="history || !isCurrent">当前是原固定正文版本，不能在这里新建标注。</p>
      <p v-else-if="snapshot.signature_ids.length > 0">该正文已有签发记录，不能新建标注。</p>
      <p v-else-if="!canWrite">当前身份没有此文稿的标注写入权限。</p>
      <p v-else-if="!selection">在上方全文阅读区选择文字、图片或表格单元格。</p>
      <p v-else class="selection-summary">{{ selectionSummary(baseline, selection) }}</p>
      <el-form label-position="top" @submit.prevent="createThread">
        <el-form-item label="标注内容"><el-input v-model="content" type="textarea" :rows="3" :disabled="busy" placeholder="描述需要讨论或核验的问题" /></el-form-item>
        <p>{{ Array.from(content).length }} / 16000 字符</p>
        <el-button type="primary" native-type="submit" :disabled="!canCreate || !content || pending !== null">添加标注</el-button>
      </el-form>
    </section>
    <section v-if="pending" aria-label="原标注请求恢复" class="pending-operation">
      <p>有一笔{{ pending.operation === 'RV-04-THREAD-1' ? '新建标注' : pending.operation === 'RV-06-DECISION-1' ? '人工处理' : '回复' }}请求待核查。输入已保留；结果确认前不会创建替代请求。</p>
      <p v-if="pending.document_revision !== snapshot.document_revision" role="status">原请求属于其他固定正文版本。请先读取该原版本后恢复，不能用当前正文替换。</p>
      <el-button v-if="pending.document_revision !== snapshot.document_revision" :disabled="locked" @click="emit('read-original', pending.document_revision)">读取原请求正文版本</el-button>
      <p v-if="!originalRequest">刷新页面后不会保存原文。请重新输入完全相同的内容{{ pending.operation === 'RV-04-THREAD-1' ? '并选择原选区' : '' }}，才能使用原请求继续。</p>
      <el-button :disabled="locked || !canRead" @click="recover">核查并继续原请求</el-button>
    </section>
    <p class="annotation-note">标注仅关联已保存的固定正文。回复会保留标注原状态。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { DocumentBody, DocumentSnapshot, Selection } from '../../../../contracts/typescript/src/interfaces'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { immutableDocumentValue } from '@/api/medical/document-body'
import { annotationAllowed, verifyAnnotationRequest, decisionTransition, annotationDraftKey, annotationStatusText, prepareAnnotation, readPendingAnnotation,
  resumeAnnotationDraft, retainAnnotationDraft, selectionSummary } from '@/api/medical/annotation-domain'
import type { AnnotationDecisionKind, DecisionCreate, DecisionListing, AnnotationCreate, AnnotationListing, AnnotationReceipt, AnnotationRequest, PendingAnnotation, ReplyCreate, ReplyListing } from '@/api/medical/annotation-domain'
import { lookupAnnotation, readAnnotationDecisions, readAnnotationReplies, readAnnotationThreads, settleAnnotation, submitAnnotation } from '@/api/medical/annotations'
import useUserStore from '@/store/modules/user'

const props = withDefaults(defineProps<{ session: MedicalSession; snapshot: DocumentSnapshot; baseline: DocumentBody; selection: Selection | null;
  active: boolean; dirty: boolean; busy?: boolean; history?: boolean; effective?: boolean; lifecycle?: boolean; isCurrent: boolean; refreshDocument?: (signal: AbortSignal) => Promise<void> }>(), { busy: false, history: false, effective: false, lifecycle: false })
const emit = defineEmits<{ busy: [value: boolean]; 'read-original': [revision: string]; 'selected-thread': [selection: { annotationId: string; documentRevision: string } | null] }>()
const user = useUserStore(); const listing = shallowRef<AnnotationListing | null>(null); const replies = shallowRef<ReplyListing | null>(null)
const decisions = shallowRef<DecisionListing | null>(null); const reasonDrafts = shallowRef<Record<string, string>>({})
const selectedId = ref(''); const content = ref(''); const replyDrafts = shallowRef<Record<string, string>>({})
const pending = shallowRef<PendingAnnotation | null>(null); const originalRequest = shallowRef<AnnotationRequest | null>(null)
const busy = ref(false); const busyText = ref(''); const notice = ref(''); const error = ref(''); const lookupState = ref('')
let automaticRead: Promise<void> | null = null
let restoringSelection = false
let controller: AbortController | null = null; let generation = 0; let ownedDraft: string | null = null; let ownsWrite = false
const selected = computed(() => listing.value?.items.find(item => item.annotation_id === selectedId.value) ?? null)
const replyText = computed({ get: () => replyDrafts.value[selectedId.value] ?? '', set: value => { replyDrafts.value = { ...replyDrafts.value, [selectedId.value]: value } } })
const decisionReason = computed({ get: () => reasonDrafts.value[selectedId.value] ?? '', set: value => { reasonDrafts.value = { ...reasonDrafts.value, [selectedId.value]: value } } })
const locked = computed(() => busy.value || props.busy || user.medicalWriting)
const canRead = computed(() => props.active && annotationAllowed(props.session, props.snapshot, 'annotation.read'))
const canWrite = computed(() => props.active && annotationAllowed(props.session, props.snapshot, 'annotation.write'))
const canReview = computed(() => props.active && annotationAllowed(props.session, props.snapshot, 'review.decide'))
const canDecide = computed(() => !locked.value && canRead.value && canReview.value && !props.dirty && !props.history && props.isCurrent
  && props.snapshot.signature_ids.length === 0 && listing.value?.revision === props.snapshot.revision && selected.value !== null
  && decisions.value !== null && decisions.value.items.length < 100 && decisions.value.annotation_id === selectedId.value && decisions.value.location_status === 'CURRENT_VERSION'
  && replies.value?.revision === decisions.value.revision && replies.value?.status === decisions.value.status
  && (decisions.value.status === 'OPEN' || decisions.value.status === 'RESOLVED'))
const canCreate = computed(() => !locked.value && canWrite.value && canRead.value && !props.dirty && !props.history && props.isCurrent
  && props.snapshot.signature_ids.length === 0 && props.selection !== null && listing.value !== null && listing.value.revision === props.snapshot.revision
  && listing.value.items.length < 100)
const canReply = computed(() => !locked.value && !props.dirty && !props.history && props.isCurrent && props.snapshot.signature_ids.length === 0 && canWrite.value && canRead.value && selected.value !== null && replies.value !== null
  && replies.value.annotation_id === selectedId.value && replies.value.items.length < 1000 && (!props.effective || replies.value.document_revision === props.snapshot.document_revision))
const session = () => props.session
function excerpt(value: string): string { const points = Array.from(value); return points.slice(0, 80).join('') + (points.length > 80 ? '…' : '') }
function retain(): void {
  if (ownedDraft !== null) retainAnnotationDraft(ownedDraft, { content: content.value, replies: replyDrafts.value, reasons: reasonDrafts.value, request: originalRequest.value })
}
function release(): void { busy.value = false; busyText.value = ''; emit('busy', false); if (ownsWrite) { user.medicalWriting = false; ownsWrite = false } }
function stopWaiting(): void {
  generation++; controller?.abort(); controller = null; release()
  notice.value = pending.value ? '已停止等待。服务器可能仍在处理，请核查原请求后再继续。' : '已停止读取，输入已保留。'
}
function current(token: number, signal: AbortSignal): void {
  signal.throwIfAborted(); if (generation !== token || !props.active) fail('CONTEXT_CHANGED', '标注处理期间当前页面已经变化')
}
async function run(text: string, write: boolean, action: (signal: AbortSignal, token: number) => Promise<void>): Promise<void> {
  if (locked.value || !props.active) return
  const request = new AbortController(); controller = request; const token = ++generation
  busy.value = true; busyText.value = text; error.value = ''; notice.value = ''; emit('busy', true)
  if (write) { ownsWrite = true; user.medicalWriting = true }
  try { await action(request.signal, token); current(token, request.signal) }
  catch (failure) { if (token === generation && !request.signal.aborted) error.value = failure instanceof Error ? failure.message : '标注处理失败，输入已保留。' }
  finally { if (token === generation) { controller = null; release() } }
}
async function load(): Promise<void> {
  if (!canRead.value) return
  await run('正在读取固定正文的完整标注', false, async (signal, token) => {
    const value = await readAnnotationThreads(session, props.snapshot, props.baseline, signal, props.effective); current(token, signal)
    listing.value = value; replies.value = null; decisions.value = null
    const id = value.items.some(item => item.annotation_id === selectedId.value) ? selectedId.value : value.items[0]?.annotation_id ?? ''
    selectedId.value = id
    if (id) { await loadThread(id, signal, token) }
  })
}
async function loadThread(id: string, signal: AbortSignal, token: number): Promise<void> {
  const replyValue = await readAnnotationReplies(session, props.snapshot, id, signal, props.history || props.effective); current(token, signal)
  const decisionValue = await readAnnotationDecisions(session, props.snapshot, id, signal, props.history || props.effective, props.effective || props.lifecycle); current(token, signal)
  replies.value = replyValue; decisions.value = decisionValue
}
async function selectThread(id: string): Promise<void> {
  if (locked.value || !listing.value?.items.some(item => item.annotation_id === id)) return
  selectedId.value = id; replies.value = null; decisions.value = null
  await run('正在读取完整回复', false, async (signal, token) => {
    await loadThread(id, signal, token)
  })
}
// Navigation restoration is read-only and never replays an annotation request or decision.
async function restoreSelected(annotationId: string, documentRevision: string): Promise<boolean> {
  const scope = JSON.stringify([props.session.user.user_id, props.session.current_context, props.session.action_summary])
  if (automaticRead) await automaticRead
  if (scope !== JSON.stringify([props.session.user.user_id, props.session.current_context, props.session.action_summary])) return false
  const hasDraft = () => pending.value !== null || originalRequest.value !== null || !!content.value ||
    Object.values(replyDrafts.value).some(Boolean) || Object.values(reasonDrafts.value).some(Boolean)
  if (hasDraft() || !canRead.value || locked.value || props.dirty || props.history || !props.isCurrent ||
      props.snapshot.document_revision !== documentRevision || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(annotationId)) return false
  let restored = false; restoringSelection = true
  try { await run('正在重新核验原选中标注的读取权限', false, async (signal, token) => {
    const value = await readAnnotationThreads(session, props.snapshot, props.baseline, signal, props.effective); current(token, signal)
    if (hasDraft() || props.dirty || !props.isCurrent || props.snapshot.document_revision !== documentRevision || !canRead.value) return
    const target = value.items.find(item => item.annotation_id === annotationId &&
      (item.document_revision === documentRevision || item.effective_anchor?.document_revision === documentRevision))
    if (!target) { selectedId.value = ''; replies.value = null; decisions.value = null; listing.value = value; notice.value = '原选中标注已不可读取或不属于此固定版本，未改选其他标注。'; return }
    const replyValue = await readAnnotationReplies(session, props.snapshot, annotationId, signal, props.effective); current(token, signal)
    const decisionValue = await readAnnotationDecisions(session, props.snapshot, annotationId, signal, props.effective, props.effective || props.lifecycle); current(token, signal)
    if (hasDraft() || props.dirty || !props.isCurrent || props.snapshot.document_revision !== documentRevision || !canRead.value) return
    listing.value = value; replies.value = replyValue; decisions.value = decisionValue
    selectedId.value = annotationId; restored = true; notice.value = '已恢复原选中标注；未提交回复或处理决定。'
  })
  } finally { restoringSelection = false }
  return restored
}
defineExpose({ restoreSelected })
watch(() => [selected.value, props.active, canRead.value, props.snapshot.document_revision], () => {
  const item = selected.value; const revision = props.snapshot.document_revision
  emit('selected-thread', props.active && canRead.value && item &&
    (item.document_revision === revision || item.effective_anchor?.document_revision === revision)
    ? { annotationId: item.annotation_id, documentRevision: revision } : null)
}, { flush: 'sync' })
async function complete(input: AnnotationRequest, signal: AbortSignal, token: number): Promise<void> {
  const original = pending.value
  if (!original) fail('ACTION_STALE', '没有可继续的原标注请求')
  // A mistyped reconstruction must not replace the original in-memory request and trap recovery.
  await verifyAnnotationRequest(session, original, props.snapshot, props.baseline, input, signal); current(token, signal)
  originalRequest.value = immutableDocumentValue(input); retain()
  const receipt = await submitAnnotation(session, original, props.snapshot, props.baseline, input, signal); current(token, signal)
  const result = await settleAnnotation(session, original, props.snapshot, props.baseline, input, receipt, signal, props.effective || props.lifecycle); current(token, signal)
  listing.value = result.threads; pending.value = null; originalRequest.value = null; lookupState.value = ''
  if (original.operation === 'RV-04-THREAD-1') {
    selectedId.value = (receipt as AnnotationReceipt).annotation.annotation_id
    if (content.value === (input as AnnotationCreate).content) content.value = ''
    // Parent owns a conflict-preserving refresh and keeps the shared write lock throughout it.
    notice.value = '标注已创建并核验，正在刷新正文保存依据。'
    if (props.refreshDocument) await props.refreshDocument(signal)
    current(token, signal)
    await loadThread(selectedId.value, signal, token)
    notice.value = '标注已创建并核验。'
  } else if (original.operation === 'RV-06-DECISION-1') {
    selectedId.value = original.annotation_id!; replies.value = result.replies; decisions.value = result.decisions
    if (reasonDrafts.value[selectedId.value] === (input as DecisionCreate).reason) { const next = { ...reasonDrafts.value }; delete next[selectedId.value]; reasonDrafts.value = next }
    notice.value = '人工处理已核验，正在刷新保存依据。'
    if (props.refreshDocument) await props.refreshDocument(signal)
    current(token, signal); notice.value = '人工处理与完整历史已核验。'
  } else {
    selectedId.value = original.annotation_id!; replies.value = result.replies
    decisions.value = await readAnnotationDecisions(session, props.snapshot, selectedId.value, signal, props.history, props.effective || props.lifecycle); current(token, signal)
    if (replyDrafts.value[selectedId.value] === (input as ReplyCreate).content) { const next = { ...replyDrafts.value }; delete next[selectedId.value]; replyDrafts.value = next }
    notice.value = '回复已追加并核验，标注状态保持不变。'
  }
  retain()
}
async function createThread(): Promise<void> {
  if (!canCreate.value || !props.selection || pending.value) return
  const input: AnnotationCreate = immutableDocumentValue({ document_revision: props.snapshot.document_revision, expected_revision: props.snapshot.revision,
    selection: props.selection, content: content.value })
  await run('正在核验选区并创建标注', true, async (signal, token) => {
    const prepared = await prepareAnnotation(session, props.snapshot, props.baseline, input, null, signal); current(token, signal); pending.value = prepared
    await complete(input, signal, token)
  })
}
async function appendReply(): Promise<void> {
  if (!canReply.value || !replies.value || pending.value) return
  const parent = replies.value; const input: ReplyCreate = { document_revision: parent.document_revision, expected_revision: parent.revision, content: replyText.value }
  await run('正在追加回复', true, async (signal, token) => {
    const prepared = await prepareAnnotation(session, props.snapshot, props.baseline, input, parent, signal); current(token, signal); pending.value = prepared
    await complete(input, signal, token)
  })
}
async function decide(decision: AnnotationDecisionKind): Promise<void> {
  if (!canDecide.value || !decisions.value || pending.value || !decisionTransition(decisions.value.status, decision)) return
  const parent = decisions.value
  const input: DecisionCreate = immutableDocumentValue({ document_revision: props.snapshot.document_revision, expected_revision: parent.revision,
    expected_document_revision: props.snapshot.revision, decision, reason: decisionReason.value })
  await run('正在核验人工处理决定', true, async (signal, token) => {
    const prepared = await prepareAnnotation(session, props.snapshot, props.baseline, input, parent, signal); current(token, signal); pending.value = prepared
    await complete(input, signal, token)
  })
}
async function recover(): Promise<void> {
  if (!pending.value) return
  await run('正在核查原请求', true, async (signal, token) => {
    const original = pending.value!; const result = await lookupAnnotation(session, original, signal); current(token, signal)
    lookupState.value = result.lookup_status
    if (original.document_revision !== props.snapshot.document_revision) { notice.value = '请先读取原请求对应的固定正文版本。'; return }
    if ((props.history || !props.isCurrent || props.snapshot.signature_ids.length > 0 || props.dirty) && result.lookup_status !== 'FOUND') { notice.value = '原版本或已签发正文只允许核查已登记的原操作，当前没有可确认的操作；恢复记录继续保留。'; return }
    if (result.lookup_status === 'EXPIRED') { notice.value = '原请求记录已过期，结果不能确认。请保留输入并联系管理员核查；不会创建替代请求。'; return }
    if (result.operation?.status === 'FAILED') { notice.value = '原操作报告失败。请保留原请求与输入，核查失败原因后再处理。'; return }
    let input = originalRequest.value
    if (input === null) {
      if (original.operation === 'RV-04-THREAD-1') {
        if (!props.selection) { notice.value = '请重新选择原固定正文的相同选区，并输入原内容后继续。'; return }
        input = { document_revision: original.document_revision, expected_revision: original.expected_revision, selection: props.selection, content: content.value }
      } else if (original.operation === 'RV-06-DECISION-1') input = { document_revision: original.document_revision, expected_revision: original.expected_revision, expected_document_revision: original.expected_document_revision!, decision: original.decision!, reason: reasonDrafts.value[original.annotation_id!] ?? '' }
      else input = { document_revision: original.document_revision, expected_revision: original.expected_revision, content: replyDrafts.value[original.annotation_id!] ?? '' }
    }
    // Every retry uses the original operation name, key, revision and byte-for-byte verified request.
    await complete(input, signal, token)
  })
}
async function refreshBasis(): Promise<void> {
  if (!props.refreshDocument) return
  await run('正在刷新保存依据并保留编辑', true, async (signal, token) => { await props.refreshDocument!(signal); current(token, signal) })
}
watch(() => [props.active, props.session.user.user_id, props.session.current_context?.tenant_id, props.session.current_context?.context_revision,
  props.session.current_context?.auth_epoch, props.session.current_context?.governance_epoch, JSON.stringify(props.session.action_summary), props.snapshot.document_id, props.snapshot.document_revision], () => {
  retain(); generation++; controller?.abort(); controller = null; release(); listing.value = null; replies.value = null; selectedId.value = ''
  content.value = ''; replyDrafts.value = {}; reasonDrafts.value = {}; decisions.value = null; originalRequest.value = null; pending.value = null; ownedDraft = null; error.value = ''; notice.value = ''; lookupState.value = ''
  if (!props.active || !props.session.current_context) return
  try {
    ownedDraft = annotationDraftKey(props.session, props.snapshot.document_id, props.snapshot.document_revision)
    const value = resumeAnnotationDraft(ownedDraft)
    if (value) { content.value = value.content; replyDrafts.value = value.replies; reasonDrafts.value = value.reasons ?? {}; originalRequest.value = value.request }
    pending.value = readPendingAnnotation(props.session, props.snapshot.document_id)
    if (pending.value === null) originalRequest.value = null
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '无法读取原请求恢复记录'; return }
  automaticRead = load()
}, { immediate: true, flush: 'sync' })
watch(() => [props.busy, canRead.value, user.medicalWriting, props.snapshot.revision], () => {
  if (!restoringSelection && !props.busy && canRead.value && (listing.value === null || props.effective && BigInt(listing.value.revision) < BigInt(props.snapshot.revision)) && !busy.value && !error.value) void load()
})
watch(() => [props.dirty, props.history, props.isCurrent, props.snapshot.signature_ids.join(',')], () => { if (busy.value) stopWaiting() }, { flush: 'sync' })
function invalidate(): void { ownedDraft = null; generation++; controller?.abort(); controller = null; release(); content.value = ''; replyDrafts.value = {}; reasonDrafts.value = {}; decisions.value = null; originalRequest.value = null; listing.value = null; replies.value = null; pending.value = null }
function unloading(event: BeforeUnloadEvent): void {
  if (pending.value || content.value || Object.values(replyDrafts.value).some(Boolean) || Object.values(reasonDrafts.value).some(Boolean)) { event.preventDefault(); event.returnValue = '' }
}
window.addEventListener('medical-context-invalidated', invalidate); window.addEventListener('beforeunload', unloading)
onBeforeUnmount(() => { retain(); generation++; controller?.abort(); release(); window.removeEventListener('medical-context-invalidated', invalidate); window.removeEventListener('beforeunload', unloading) })
</script>

<style scoped>
.annotation-heading { display: flex; align-items: center; justify-content: space-between; }
.annotation-layout { display: grid; grid-template-columns: minmax(180px, 30%) minmax(0, 1fr); gap: 20px; }
.thread-button { display: grid; gap: 8px; width: 100%; text-align: start; background: white; border: 1px solid #dcdfe6; border-radius: 6px; padding: 12px; margin-bottom: 8px; cursor: pointer; color: #303133; }
.thread-button[aria-pressed="true"] { border-color: #409eff; background: #ecf5ff; }
.thread-content, .comment-text, .selection-summary { white-space: pre-wrap; overflow-wrap: anywhere; }
.selection-summary { color: #606266; border-inline-start: 3px solid #dcdfe6; padding-inline-start: 12px; }
.reply { border-top: 1px solid #ebeef5; padding-block: 8px; }
.pending-operation { border: 1px solid #e6a23c; padding: 12px; margin-top: 12px; }
.annotation-note, small { color: #606266; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 760px) { .annotation-layout { grid-template-columns: 1fr; } }
</style>
