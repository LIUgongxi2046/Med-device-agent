<template>
  <section aria-label="文稿正文" data-document-editor>
    <h3>文稿正文</h3>
    <el-button :disabled="!active || loading || writing || user.medicalWriting || hasUnsaved" @click="load">读取完整正文</el-button>
    <p v-if="loading" role="status">正在读取当前正文、字段与证据</p>
    <template v-if="saved && body">
      <p>当前版本：{{ saved.document_revision }}；记录修订：{{ saved.revision }}</p>
      <p v-if="hasUnsaved" role="status">当前正文包含尚未保存的编辑</p>
      <p v-if="currentSnapshot && currentSnapshot.document_revision !== saved.document_revision" role="status">服务器文稿已经更新；当前编辑保留原版本，请比较最新内容。</p>
      <p v-if="!editable">当前文稿允许读取；编辑需要有效权限与未签发状态。</p>
      <el-button :disabled="!editable || !draft?.canUndo || unconfirmedFields > 0" @click="undo">撤销编辑</el-button>
      <el-button :disabled="!editable || !draft?.canRedo || unconfirmedFields > 0" @click="redo">重做编辑</el-button>
      <p>当前会话保留最近100次正文编辑，保存后的撤销内容通过新修订保存。</p>
      <el-collapse><el-collapse-item title="全文阅读、目录与正文选区" name="full-reader">
        <document-body-reader :body="body" :document-revision="saved.document_revision" :active="active" :dirty="hasUnsaved" />
      </el-collapse-item></el-collapse>
      <document-node-editor v-for="node in body.nodes.filter(value => value.parent_id === null)" :key="node.node_id" :node="node" :body="body" :readonly="!editable" @changed="replace" />
      <h4>字段原始值与适用条件</h4>
      <el-card v-for="field in body.fields" :key="field.field_id" shadow="never">
        <p>字段标识：{{ field.field_id }}</p>
        <document-field-editor :field="field" :input="fieldInputs[field.field_id]" :readonly="!editable" @changed="editField" @input-changed="retainFieldInput" />
      </el-card>
      <document-evidence-panel :session="session" :links="body.evidence_links" :document-revision="saved.document_revision" :active="active" />
      <el-form :disabled="!editable || writing" label-position="top">
        <el-form-item label="本次修订理由"><el-input v-model="reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <p v-if="writing" role="status">正在保存正文；已传输 {{ sentBytes }} / {{ totalBytes }} 字节</p>
      <p v-if="unconfirmedFields > 0" role="status">请确认 {{ unconfirmedFields }} 项字段修改后保存正文</p>
      <el-button type="primary" :disabled="!editable || writing || !draft?.dirty || unconfirmedFields > 0 || reason.trim().length === 0" @click="save">{{ pending ? '继续原保存请求' : '保存正文修订' }}</el-button>
      <el-button :disabled="!active || writing || loading || !draft?.dirty || unconfirmedFields > 0 || pending !== null" @click="refreshKeepingEdits">读取最新版本并保留当前编辑</el-button>
      <document-export :session="session" :document-id="documentId" :document-revision="saved.document_revision" :active="active && !hasUnsaved" />
    </template>
    <el-form v-if="saved" label-position="top">
      <el-form-item label="历史正文版本"><el-input v-model="historyRevision" placeholder="输入固定正文版本标识" /></el-form-item>
      <el-button :disabled="!active || writing || loading || historyLoading || historyRevision.trim().length === 0" @click="readHistory">读取完整历史正文</el-button>
    </el-form>
    <el-dialog v-model="historyVisible" title="固定历史正文" width="90%" :close-on-click-modal="false">
      <template v-if="historyDocument">
        <p>固定正文版本：{{ historyDocument.snapshot.document_revision }}；内容摘要：{{ historyDocument.snapshot.body.sha256 }}</p>
        <document-body-reader :body="historyDocument.body" :document-revision="historyDocument.snapshot.document_revision" :active="active && historyVisible" :dirty="false" />
        <el-card v-for="field in historyDocument.body.fields" :key="field.field_id" shadow="never">
          <document-field-editor :field="field" :input="undefined" readonly />
        </el-card>
        <pre v-for="link in historyDocument.body.evidence_links" :key="link.evidence_link_id">{{ comparisonText(link) }}</pre>
      </template>
    </el-dialog>
    <el-dialog v-model="comparing" title="比较原版本、服务器版本与当前编辑" width="90%" :close-on-click-modal="false">
      <el-table v-if="comparison" :data="comparison.rows.filter(row => row.changed)" row-key="key">
        <el-table-column prop="kind" label="内容类型" width="110" />
        <el-table-column label="原版本"><template #default="{ row }"><el-input :model-value="comparisonText(row.base)" type="textarea" readonly autosize /></template></el-table-column>
        <el-table-column label="服务器版本"><template #default="{ row }"><el-input :model-value="comparisonText(row.server)" type="textarea" readonly autosize /></template></el-table-column>
        <el-table-column label="本地编辑"><template #default="{ row }"><el-input :model-value="comparisonText(row.local)" type="textarea" readonly autosize /></template></el-table-column>
        <el-table-column label="采用内容" width="170"><template #default="{ row }">
          <el-select v-model="row.choice" placeholder="请选择处理方式">
            <el-option label="原版本" value="BASE" /><el-option label="服务器版本" value="SERVER" /><el-option label="本地编辑" value="LOCAL" />
          </el-select>
        </template></el-table-column>
      </el-table>
      <template #footer><el-button @click="comparing = false">继续编辑当前草稿</el-button><el-button type="primary" @click="confirmComparison">确认内容并更新编辑依据</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { DocumentBody, DocumentSnapshot, FieldValue } from '../../../../contracts/typescript/src/interfaces'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { canonicalDocumentValue, DocumentEditBuffer } from '@/api/medical/document-body'
import { compareDocumentBodies, comparisonText, mergeDocumentComparison } from '@/api/medical/document-comparison'
import type { DocumentComparison } from '@/api/medical/document-comparison'
import type { SavedDocument } from '@/api/medical/documents'
import { confirmDocumentField } from '@/api/medical/document-editing'
import { prepareDocumentDraftSave, readDocumentDependency, readSavedDocument, readSavedDocumentRevision } from '@/api/medical/documents'
import { documentDraftKey, retainDocumentDraft, resumeDocumentDraft } from '@/api/medical/document-session-drafts'
import useUserStore from '@/store/modules/user'
import DocumentNodeEditor from './document-node-editor.vue'
import DocumentFieldEditor from './document-field-editor.vue'
import DocumentExport from './document-export.vue'
import DocumentEvidencePanel from './document-evidence-panel.vue'
import DocumentBodyReader from './document-body-reader.vue'

const props = defineProps<{ session: MedicalSession; projectId: string; documentId: string; active: boolean }>()
const user = useUserStore(); const saved = shallowRef<DocumentSnapshot | null>(null); const body = shallowRef<DocumentBody | null>(null)
const draft = shallowRef<DocumentEditBuffer | null>(null); const loading = ref(false); const writing = ref(false); const reason = ref('')
const currentSnapshot = shallowRef<DocumentSnapshot | null>(null)
const historyRevision = ref(''); const historyDocument = shallowRef<SavedDocument | null>(null)
const historyVisible = ref(false); const historyLoading = ref(false); let historyController: AbortController | null = null
const fieldInputs = shallowRef<Record<string, FieldValue>>({})
const unconfirmedFields = computed(() => Object.keys(fieldInputs.value).length)
const hasUnsaved = computed(() => draft.value?.dirty === true || unconfirmedFields.value > 0)
const baseline = shallowRef<DocumentBody | null>(null); const comparison = shallowRef<DocumentComparison | null>(null); const comparing = ref(false)
let comparisonTarget: SavedDocument | null = null; let comparisonDependency: string | null = null; let comparisonEdited: string | null = null
const sentBytes = ref(0); const totalBytes = ref(0)
let pending: Awaited<ReturnType<typeof prepareDocumentDraftSave>> | null = null; let dependency: string | null = null
let controller: AbortController | null = null; let generation = 0
let ownedKey: string | null = null
const editable = computed(() => props.active && !loading.value && currentSnapshot.value?.allowed_actions.includes('document.edit') === true)
const capacity = { maximumPages: 1000, maximumBytes: 104857600, pageSize: 100 }
function remember(): boolean {
  if (ownedKey === null || saved.value === null || baseline.value === null || draft.value === null || dependency === null || !hasUnsaved.value && pending === null) return false
  return retainDocumentDraft(ownedKey, { snapshot: saved.value, baseline: baseline.value, draft: draft.value, dependency, pending,
    reason: reason.value, fieldInputs: fieldInputs.value })
}
function clear(invalidate = true): void {
  generation++; controller?.abort(); controller = null
  historyController?.abort(); historyController = null; historyRevision.value = ''; historyDocument.value = null; historyVisible.value = false; historyLoading.value = false
  if (invalidate) { pending?.invalidate(); draft.value?.invalidate() }
  pending = null; ownedKey = null
  saved.value = null; body.value = null; draft.value = null; dependency = null; loading.value = false
  currentSnapshot.value = null
  fieldInputs.value = {}
  baseline.value = null; comparison.value = null; comparing.value = false; comparisonTarget = null; comparisonDependency = null; comparisonEdited = null
  reason.value = ''; sentBytes.value = 0; totalBytes.value = 0
}
async function load(): Promise<void> {
  if (!props.active || loading.value || writing.value || user.medicalWriting || hasUnsaved.value) return
  clear(); const request = new AbortController(); controller = request; const current = generation; loading.value = true
  try {
    const document = await readSavedDocument(props.session, props.projectId, props.documentId, capacity, request.signal)
    const accessSet = document.snapshot.allowed_actions.includes('document.edit')
      ? await readDocumentDependency(props.session, document.snapshot, request.signal) : null
    if (generation !== current || !props.active) fail('CONTEXT_CHANGED', '文稿读取期间当前页面已经变化')
    saved.value = document.snapshot; body.value = document.body; draft.value = new DocumentEditBuffer(document.body, 100); dependency = accessSet
    currentSnapshot.value = document.snapshot
    baseline.value = document.body
    ownedKey = documentDraftKey(props.session, props.projectId, props.documentId)
  } finally { if (generation === current) loading.value = false }
}
function replace(value: DocumentBody): void {
  if (!editable.value || draft.value === null) fail('ACTION_STALE', '当前文稿无法编辑')
  draft.value.edit(value); body.value = draft.value.body
}
function undo(): void {
  if (!editable.value || draft.value === null || unconfirmedFields.value > 0) fail('ACTION_STALE', '当前正文无法撤销编辑')
  draft.value.undo(); body.value = draft.value.body
}
function redo(): void {
  if (!editable.value || draft.value === null || unconfirmedFields.value > 0) fail('ACTION_STALE', '当前正文无法重做编辑')
  draft.value.redo(); body.value = draft.value.body
}
async function readHistory(): Promise<void> {
  if (!props.active || writing.value || loading.value || historyLoading.value) fail('ACTION_STALE', '当前文稿无法读取历史正文')
  const request = new AbortController(); historyController?.abort(); historyController = request
  const current = generation; historyLoading.value = true
  try {
    const result = await readSavedDocumentRevision(props.session, props.projectId, props.documentId, historyRevision.value.trim(), capacity, request.signal)
    if (generation !== current || !props.active) fail('CONTEXT_CHANGED', '历史正文读取期间当前页面已经变化')
    historyDocument.value = result; historyVisible.value = true
  } finally { if (generation === current) historyLoading.value = false }
}
function editField(value: FieldValue): void {
  if (body.value === null) fail('ACTION_STALE', '正文尚未读取'); replace(confirmDocumentField(body.value, value))
  const next = { ...fieldInputs.value }; delete next[value.field_id]; fieldInputs.value = next
}
function retainFieldInput(value: FieldValue): void {
  const original = body.value?.fields.find(item => item.field_id === value.field_id)
  if (original === undefined) fail('ACTION_STALE', '字段输入对应的正文已经变化')
  const next = { ...fieldInputs.value }
  if (JSON.stringify(original) === JSON.stringify(value)) delete next[value.field_id]
  else next[value.field_id] = value
  fieldInputs.value = next
}
async function save(): Promise<void> {
  if (!editable.value || writing.value || user.medicalWriting || unconfirmedFields.value > 0 || saved.value === null || draft.value === null || dependency === null) fail('ACTION_STALE', '当前正文无法保存')
  if (pending === null && currentSnapshot.value?.document_revision !== saved.value.document_revision) fail('REVISION_CONFLICT', '请比较最新文稿并确认本次编辑依据')
  const request = new AbortController(); controller = request; const current = generation; writing.value = true; user.medicalWriting = true
  try {
    if (pending === null) {
      const prepared = await prepareDocumentDraftSave(() => props.session, saved.value, draft.value, dependency, reason.value, capacity)
      if (generation !== current || !props.active) { prepared.invalidate(); fail('CONTEXT_CHANGED', '正文保存准备期间当前页面已经变化') }
      pending = prepared
    }
    const result = await pending.advance(request.signal, (sent, total) => { sentBytes.value = sent; totalBytes.value = total })
    if (generation !== current || !props.active) fail('CONTEXT_CHANGED', '保存期间当前文稿已经变化')
    if (result.kind === 'SAVED') { saved.value = result.document.snapshot; currentSnapshot.value = result.document.snapshot; baseline.value = result.document.body; body.value = draft.value.body; pending = null; reason.value = '' }
  } finally { writing.value = false; user.medicalWriting = false }
}
async function refreshKeepingEdits(): Promise<void> {
  if (!props.active || writing.value || loading.value || pending !== null || baseline.value === null || body.value === null || draft.value === null) fail('ACTION_STALE', '当前正文无法更新读取依据')
  const edited = body.value; const original = baseline.value; const currentGeneration = generation
  const request = new AbortController(); controller?.abort(); controller = request; loading.value = true
  try {
    const current = await readSavedDocument(props.session, props.projectId, props.documentId, capacity, request.signal)
    const accessSet = current.snapshot.allowed_actions.includes('document.edit')
      ? await readDocumentDependency(props.session, current.snapshot, request.signal) : null
    if (generation !== currentGeneration || !props.active || body.value === null || canonicalDocumentValue(body.value) !== canonicalDocumentValue(edited)) {
      fail('CONTEXT_CHANGED', '版本比较读取期间当前正文已经变化')
    }
    comparison.value = compareDocumentBodies(original, current.body, edited); comparisonTarget = current
    comparisonDependency = accessSet; comparisonEdited = canonicalDocumentValue(edited); currentSnapshot.value = current.snapshot; comparing.value = true
  } finally { if (generation === currentGeneration) loading.value = false }
}
function confirmComparison(): void {
  if (!props.active || comparison.value === null || comparisonTarget === null || draft.value === null || body.value === null
      || comparisonEdited !== canonicalDocumentValue(body.value) || pending !== null) fail('ACTION_STALE', '版本比较对应的正文已经变化')
  const chosen = mergeDocumentComparison(comparison.value)
  draft.value.replaceBaseline(comparisonTarget.body, chosen); saved.value = comparisonTarget.snapshot; currentSnapshot.value = comparisonTarget.snapshot
  baseline.value = comparisonTarget.body; body.value = draft.value.body; dependency = comparisonDependency; comparing.value = false
  comparison.value = null; comparisonTarget = null; comparisonDependency = null; comparisonEdited = null
}
async function restore(): Promise<void> {
  if (!props.active || props.session.current_context === null) return
  const key = documentDraftKey(props.session, props.projectId, props.documentId); const value = resumeDocumentDraft(key)
  if (value === null) return
  const request = new AbortController(); controller = request; const current = generation; loading.value = true; let admitted = false
  try {
    const latest = await readSavedDocument(props.session, props.projectId, props.documentId, capacity, request.signal)
    if (generation !== current || !props.active) fail('CONTEXT_CHANGED', '恢复草稿期间当前页面已经变化')
    ownedKey = key; saved.value = value.snapshot; baseline.value = value.baseline; currentSnapshot.value = latest.snapshot; draft.value = value.draft; body.value = value.draft.body
    dependency = value.dependency; pending = value.pending; reason.value = value.reason; fieldInputs.value = value.fieldInputs; admitted = true
  } finally {
    if (!admitted) { value.pending?.invalidate(); value.draft.invalidate() }
    if (generation === current) loading.value = false
  }
}
watch(() => [props.active, props.session.user.user_id, props.session.current_context?.context_revision,
  props.session.current_context?.auth_epoch, props.session.current_context?.governance_epoch, props.projectId, props.documentId], () => {
  const retained = remember(); clear(!retained)
  void restore()
}, { immediate: true })
function unloading(event: BeforeUnloadEvent): void {
  if (hasUnsaved.value || pending !== null) { event.preventDefault(); event.returnValue = '' }
}
function invalidated(): void { clear() }
window.addEventListener('beforeunload', unloading)
window.addEventListener('medical-context-invalidated', invalidated)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', unloading); window.removeEventListener('medical-context-invalidated', invalidated)
  const retained = remember(); clear(!retained)
})
</script>
