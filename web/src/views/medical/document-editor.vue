<template>
  <section aria-label="文稿正文" data-document-editor>
    <h3>{{ reviewMode ? '已保存正文 · 只读审查' : '文稿正文' }}</h3>
    <p v-if="reviewMode" role="status">审查仅使用已保存版本；撰写阶段的未保存草稿不会载入此页。</p>
    <el-button :disabled="!active || loading || writing || annotationBusy || user.medicalWriting || hasUnsaved" @click="restore">读取完整正文</el-button>
    <p v-if="requestedHistory.issue" role="alert">{{ requestedHistory.issue }}</p>
    <section v-if="requestedHistory.revision">
      <p>退回意见指定了一个原固定版本。点击后核验当前读取权限，并单独打开历史正文；当前正文与草稿保持不变。</p>
      <el-button :disabled="!active || loading || writing || annotationBusy || user.medicalWriting || historyLoading" @click="openRequestedHistory">读取退回原固定版本</el-button>
    </section>
    <p v-if="historyError" role="alert">{{ historyError }}</p>
    <p v-if="loading" role="status">正在读取当前正文、字段与证据</p>
    <template v-if="saved && body">
      <p>当前版本：{{ saved.document_revision }}；记录修订：{{ saved.revision }}</p>
      <p v-if="hasUnsaved" role="status">当前正文包含尚未保存的编辑</p>
      <p v-if="currentSnapshot && currentSnapshot.document_revision !== saved.document_revision" role="status">服务器文稿已经更新；当前编辑保留原版本，请比较最新内容。</p>
      <p v-if="currentSnapshot && !currentSnapshot.allowed_actions.includes('document.edit') && !reviewMode">当前文稿允许读取；保存需要正文写入、保存后检查权限及未签发状态。</p>
      <el-button v-if="!reviewMode" :disabled="!editable || !draft?.canUndo || unconfirmedFields > 0" @click="undo">撤销编辑</el-button>
      <el-button v-if="!reviewMode" :disabled="!editable || !draft?.canRedo || unconfirmedFields > 0" @click="redo">重做编辑</el-button>
      <p v-if="!reviewMode">当前会话保留最近100次正文编辑，保存后的撤销内容通过新修订保存。</p>
      <document-body-reader ref="documentReader" :body="body" :document-revision="saved.document_revision" :active="active" :dirty="hasUnsaved" :editable="editable" @selected="selectedForAnnotation" @changed="replace" @open-evidence="openChapterEvidence">
        <template #inspector>
          <nav class="workspace-links" aria-label="文稿核验与保存导航">
            <h4>核验与修订</h4>
            <button type="button" @click="locatePanel('verification')">字段核验工作台</button>
            <button v-if="!reviewMode" type="button" @click="locatePanel('candidates')">章节候选比较</button>
            <button type="button" @click="locatePanel('fields')">字段与适用条件（{{ body.fields.length }}）<span v-if="unconfirmedFields"> · {{ unconfirmedFields }} 项待确认编辑</span></button>
            <button type="button" @click="locatePanel('evidence')">引用来源（{{ body.evidence_links.length }}）</button>
            <button v-if="!reviewMode" type="button" @click="locatePanel('save')">修订理由与保存<span v-if="hasUnsaved"> · 有未保存修改</span></button>
            <button type="button" @click="locatePanel('check')">技术结构检查</button>
            <button type="button" @click="locatePanel('professional')">专业检查与总稿</button>
          </nav>
          <p class="scope-note">字段编辑确认仅替换正文值；读取来源不代表其支持当前陈述。技术结构检查不等于专业核验、审查确认或签发。</p>
          <p class="scope-note">候选采用使用服务端预览及明确确认；专业检查、人工审查和签发分别核验。</p>
        </template>
      </document-body-reader>
      <p v-if="sourceReturnNotice" role="status">{{ sourceReturnNotice }}</p>
      <p v-if="locationNotice" role="status">{{ locationNotice }}</p>
      <details ref="verificationPanel" class="document-inspector" tabindex="-1"><summary>字段核验工作台 · 当前正文值与来源线索</summary><div class="fv-preparation-actions"><el-button :disabled="!fieldNavigationContext.canReadMapping || fieldPreparationLoading || fieldVerification?.hasLocalDraft()" @click="loadFieldPreparation">{{ fieldPreparationLoading ? '正在读取当前提议映射' : '读取当前任务提议映射与固定来源' }}</el-button><p v-if="fieldPreparationNotice" role="status">{{ fieldPreparationNotice }}</p></div><document-field-verification ref="fieldVerification" :body="body" :preparation="fieldPreparation" :navigation-context="fieldNavigationContext" :can-open-evidence="fieldEvidenceAvailable" @open-field-targets="openFieldTargets" @open-source-exact="openFieldSource" @open-evidence="openFieldEvidence" /></details>
      <details v-if="!reviewMode" ref="candidatesPanel" class="document-inspector" tabindex="-1" @toggle="candidatesOpen = ($event.target as HTMLDetailsElement).open"><summary>章节候选 · 预览与比较</summary><document-candidate-adoption :session="session" :snapshot="saved" :active="active && candidatesOpen && !historyVisible && !loading && !writing && !annotationBusy && !comparing" :dirty="hasUnsaved" :is-current="currentSnapshot?.document_revision === saved.document_revision" @adopted="load()" /></details>
      <details ref="fieldsPanel" class="document-inspector" tabindex="-1"><summary>字段原始值与适用条件 · {{ body.fields.length }} 项</summary>
      <el-card v-for="field in body.fields" :key="field.field_id" shadow="never">
        <p>字段标识：{{ field.field_id }}</p>
        <document-field-editor :field="field" :input="fieldInputs[field.field_id]" :readonly="!editable" @changed="editField" @input-changed="retainFieldInput" />
      </el-card>
      </details>
      <details ref="evidencePanel" class="document-inspector" tabindex="-1"><summary>引用来源与原文核验 · {{ body.evidence_links.length }} 项</summary>
        <document-evidence-panel ref="fieldEvidenceReader" :session="session" :links="body.evidence_links" :document-revision="saved.document_revision" :active="active" :return-origin="sourceReturnOrigin" :body="body" @locate-chapter="locateEvidenceChapter" />
      </details>
      <document-annotations ref="mainAnnotations" @selected-thread="selectedAnnotationThread = $event" :effective="!reviewMode" :lifecycle="reviewMode" v-if="baseline" :session="session" :snapshot="saved" :baseline="baseline" :selection="annotationSelection"
        :active="active && !historyVisible" :dirty="hasUnsaved" :busy="loading || writing || comparing || reanchorMainBusy" :is-current="currentSnapshot?.document_revision === saved.document_revision"
        :refresh-document="refreshAfterAnnotation" @busy="annotationMainBusy = $event" @read-original="openAnnotationOriginal" />
      <document-reanchors v-if="baseline" :session="session" :snapshot="saved" :baseline="baseline" :selection="annotationSelection"
        :active="active && !historyVisible" :dirty="hasUnsaved" :busy="loading || writing || comparing || annotationMainBusy" :is-current="currentSnapshot?.document_revision === saved.document_revision"
        :refresh-document="refreshAfterAnnotation" @busy="reanchorMainBusy = $event" @read-original="openAnnotationOriginal" />

      <section v-if="!reviewMode" ref="savePanel" tabindex="-1" aria-label="保存正文修订">
      <el-form :disabled="!editable || writing" label-position="top">
        <el-form-item label="本次修订理由"><el-input v-model="reason" type="textarea" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <p v-if="writing" role="status">正在保存正文；已传输 {{ sentBytes }} / {{ totalBytes }} 字节</p>
      <p v-if="unconfirmedFields > 0" role="status">请确认 {{ unconfirmedFields }} 项字段修改后保存正文</p>
      <el-button v-if="!reviewMode" type="primary" :disabled="!editable || writing || !draft?.dirty || unconfirmedFields > 0 || reason.trim().length === 0" @click="save">{{ pending ? '继续原保存请求' : '保存正文修订' }}</el-button>
      <el-button v-if="!reviewMode" :disabled="!active || writing || loading || annotationBusy || user.medicalWriting || !draft?.dirty || unconfirmedFields > 0 || pending !== null" @click="refreshKeepingEdits">读取最新版本并保留当前编辑</el-button>
      </section>
      <section ref="checkPanel" tabindex="-1" aria-label="技术结构检查入口">
      <document-technical-check :session="session" :snapshot="saved" :active="active && !historyVisible && !loading && !writing && !annotationBusy && !comparing" :dirty="hasUnsaved" :is-current="currentSnapshot?.document_revision === saved.document_revision" />
      </section>
      <details ref="professionalPanel" class="document-inspector" tabindex="-1"><summary>专业检查与总稿 · 来源、规则、跨文件及当前正文</summary><document-professional-checks :snapshot="saved" :dirty="hasUnsaved" :is-current="currentSnapshot?.document_revision === saved.document_revision" :active="active" @navigate-write="locateProfessionalIssue" /></details>
      <document-review-workspace v-if="reviewMode" :return-context-key="reviewReturnContextKey" @navigate-return="openReviewReturn" :saved-body="baseline ? { documentRevision: saved.document_revision, body: baseline } : null" :snapshot="saved" :dirty="hasUnsaved" :is-current="currentSnapshot?.document_revision === saved.document_revision" :active="active && !historyVisible" @navigate-write="emit('navigateWrite')" />
      <document-review-return v-if="reviewMode" ref="reviewReturnPanel" :session="session" :snapshot="saved" :active="active && !historyVisible" :dirty="hasUnsaved" v-bind="taskId ? { taskId } : {}" @returned="emit('returned', $event)" />
      <document-gru-records v-bind="baseline && !hasUnsaved ? { savedBody: baseline } : {}" :session="session" :snapshot="saved" :active="active && !historyVisible" :dirty="hasUnsaved" :selection="annotationSelection" />
      <document-export :session="session" :project-id="projectId" :document-id="documentId" :document-revision="saved.document_revision" :active="active && !hasUnsaved" />
    </template>
    <saved-document-comparison v-if="saved" :session="session" :snapshot="saved" :active="active && !historyVisible"
      :disabled="writing || loading || comparing || annotationBusy || user.medicalWriting || historyLoading" />
    <document-revision-directory v-if="saved" :session="session" :snapshot="saved" :active="active && !historyVisible"
      :disabled="writing || loading || comparing || annotationBusy || user.medicalWriting || historyLoading" @select="openAnnotationOriginal" />
    <details v-if="saved" class="document-inspector"><summary>按已知版本标识读取</summary>
    <el-form label-position="top">
      <el-form-item label="历史正文版本"><el-input v-model="historyRevision" placeholder="输入固定正文版本标识" /></el-form-item>
      <el-button :disabled="!active || writing || loading || annotationBusy || user.medicalWriting || historyLoading || historyRevision.trim().length === 0" @click="readHistory">读取完整历史正文</el-button>
    </el-form>
    </details>
    <el-dialog v-model="historyVisible" title="固定历史正文" width="90%" :close-on-click-modal="false">
      <template v-if="historyDocument">
        <p>固定正文版本：{{ historyDocument.snapshot.document_revision }}；内容摘要：{{ historyDocument.snapshot.body.sha256 }}</p>
        <document-body-reader :body="historyDocument.body" :document-revision="historyDocument.snapshot.document_revision" :active="active && historyVisible" :dirty="false" :evidence-navigable="false" @selected="historySelection = $event" />
        <document-annotations lifecycle :session="session" :snapshot="historyDocument.snapshot" :baseline="historyDocument.body" :selection="historySelection"
          :active="active && historyVisible" :dirty="false" :busy="loading || writing || historyLoading || reanchorHistoryBusy" history :is-current="false" @busy="annotationHistoryBusy = $event" />
        <document-reanchors :session="session" :snapshot="historyDocument.snapshot" :baseline="historyDocument.body" :selection="historySelection"
          :active="active && historyVisible" :dirty="false" :busy="loading || writing || historyLoading || annotationHistoryBusy" history :is-current="false" @busy="reanchorHistoryBusy = $event" />
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
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { readFieldTargetPreparation } from '@/api/medical/task-field-targets'
import { taskReadAllowed } from '@/api/medical/task-detail'
import { fieldTargetsDestination, sourceExactDestination } from '@/api/medical/document-field-verification'
import type { Preparation, SourceExactRef } from '@/api/medical/task-field-target-types'
import type { EvidenceLink } from '../../../../contracts/typescript/src/interfaces'
import type { DocumentBody, DocumentSnapshot, FieldValue, Selection } from '../../../../contracts/typescript/src/interfaces'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail } from '@/api/medical/protocol'
import { canonicalDocumentValue, DocumentEditBuffer } from '@/api/medical/document-body'
import { compareDocumentBodies, comparisonText, mergeDocumentComparison } from '@/api/medical/document-comparison'
import type { DocumentComparison } from '@/api/medical/document-comparison'
import { consumeSourceReturn, sourceReturnScope } from '@/api/medical/source-return-navigation'
import type { SourceReturnOrigin } from '@/api/medical/source-return-navigation'
import { workspaceHistoryRevision } from '@/api/medical/task-workspace'
import type { SavedDocument } from '@/api/medical/documents'
import { confirmDocumentField } from '@/api/medical/document-editing'
import { prepareDocumentDraftSave, readDocumentDependency, readSavedDocument, readSavedDocumentRevision } from '@/api/medical/documents'
import { documentDraftKey, retainDocumentDraft, resumeDocumentDraft, returnDocumentDraft, acceptDocumentDraft } from '@/api/medical/document-session-drafts'
import useUserStore from '@/store/modules/user'
import DocumentFieldEditor from './document-field-editor.vue'
import DocumentFieldVerification from './document-field-verification.vue'
import DocumentCandidateAdoption from './document-candidate-adoption.vue'
import DocumentExport from './document-export.vue'
import DocumentGruRecords from './document-gru-records.vue'
import DocumentTechnicalCheck from './document-technical-check.vue'
import DocumentReviewReturn from './document-review-return.vue'
import DocumentReviewWorkspace from './document-review-workspace.vue'
import DocumentProfessionalChecks from './document-professional-checks.vue'
import DocumentRevisionDirectory from './document-revision-directory.vue'
import SavedDocumentComparison from './saved-document-comparison.vue'
import type { LocalReturnHandoff } from '@/api/medical/document-review-workspace'
import type { ReviewReceipt } from '@/api/medical/document-review-return'
import DocumentEvidencePanel from './document-evidence-panel.vue'
import { chapterEvidence } from '@/api/medical/document-evidence-navigation'
import DocumentBodyReader from './document-body-reader.vue'
import DocumentAnnotations from './document-annotations.vue'
import DocumentReanchors from './document-reanchors.vue'

const props = withDefaults(defineProps<{ session: MedicalSession; projectId: string; documentId: string; active: boolean; reviewMode?: boolean; taskId?: string; historyRevisionRequest?: unknown }>(), { reviewMode: false })
const emit = defineEmits<{ returned: [receipt: ReviewReceipt]; navigateWrite: [] }>()
const reviewReturnPanel = ref<{ openFromReview: (handoff?: LocalReturnHandoff, contextKey?: string) => boolean }>()
const reviewReturnContextKey = computed(() => canonicalDocumentValue([props.session.user.user_id, props.session.current_context]))
function openReviewReturn(handoff?: LocalReturnHandoff): boolean {
  if (!props.reviewMode || !props.active || document.hidden || historyVisible.value || !saved.value ||
      loading.value || writing.value || annotationBusy.value || user.medicalWriting || hasUnsaved.value ||
      currentSnapshot.value?.document_revision !== saved.value.document_revision) return false
  return reviewReturnPanel.value?.openFromReview(handoff, reviewReturnContextKey.value) ?? false
}
const professionalPanel = ref<HTMLDetailsElement>(); const verificationPanel = ref<HTMLDetailsElement>(); const candidatesPanel = ref<HTMLDetailsElement>()
const candidatesOpen = ref(false)
const fieldsPanel = ref<HTMLDetailsElement>(); const evidencePanel = ref<HTMLDetailsElement>()
const savePanel = ref<HTMLElement>(); const checkPanel = ref<HTMLElement>()
function locatePanel(panel: 'fields' | 'evidence' | 'save' | 'check' | 'verification' | 'candidates' | 'professional'): void {
  const target = { professional: professionalPanel.value, verification: verificationPanel.value, candidates: candidatesPanel.value, fields: fieldsPanel.value, evidence: evidencePanel.value, save: savePanel.value, check: checkPanel.value }[panel]
  if (!target) return
  if (target.tagName === 'DETAILS') (target as HTMLDetailsElement).open = true
  target.scrollIntoView({ block: 'start' }); target.focus({ preventScroll: true })
}
const documentReader = ref<{ locate: (id: string) => void }>(); const locationNotice = ref('')
function locateProfessionalIssue(nodeId?: string): void {
  locationNotice.value = ''
  if (!props.active || document.hidden) return
  if (!nodeId) { emit('navigateWrite'); return }
  if (!saved.value || historyVisible.value || hasUnsaved.value || currentSnapshot.value?.document_revision !== saved.value.document_revision) { locationNotice.value = '当前正文或版本已变化，请先读取对应的已保存正文再定位。'; return }
  if (!body.value?.nodes.some(node => node.node_id === nodeId)) { locationNotice.value = '当前正文中没有对应节点，未执行定位。'; return }
  documentReader.value?.locate(nodeId)
}
const user = useUserStore(); const saved = shallowRef<DocumentSnapshot | null>(null); const body = shallowRef<DocumentBody | null>(null)
const draft = shallowRef<DocumentEditBuffer | null>(null); const loading = ref(false); const writing = ref(false); const reason = ref('')
const currentSnapshot = shallowRef<DocumentSnapshot | null>(null)
const annotationSelection = shallowRef<Selection | null>(null)
const annotationMainBusy = ref(false); const annotationHistoryBusy = ref(false)
const reanchorMainBusy = ref(false); const reanchorHistoryBusy = ref(false)
const annotationBusy = computed(() => annotationMainBusy.value || annotationHistoryBusy.value || reanchorMainBusy.value || reanchorHistoryBusy.value)
const historySelection = shallowRef<Selection | null>(null)
const requestedHistory = computed(() => workspaceHistoryRevision(props.historyRevisionRequest))
const historyError = ref('')
const historyRevision = ref(''); const historyDocument = shallowRef<SavedDocument | null>(null)
const historyVisible = ref(false); const historyLoading = ref(false); let historyController: AbortController | null = null
watch(() => props.historyRevisionRequest, () => {
  historyController?.abort(); historyController = null
  historyDocument.value = null; historySelection.value = null; historyVisible.value = false; historyLoading.value = false; historyError.value = ''
}, { flush: 'sync' })
const fieldInputs = shallowRef<Record<string, FieldValue>>({})
const unconfirmedFields = computed(() => Object.keys(fieldInputs.value).length)
const hasUnsaved = computed(() => draft.value?.dirty === true || unconfirmedFields.value > 0)
watch(() => [saved.value?.document_revision, props.active], () => { locationNotice.value = '' })
const baseline = shallowRef<DocumentBody | null>(null); const comparison = shallowRef<DocumentComparison | null>(null); const comparing = ref(false)
let comparisonTarget: SavedDocument | null = null; let comparisonDependency: string | null = null; let comparisonEdited: string | null = null
const sentBytes = ref(0); const totalBytes = ref(0)
let pending: Awaited<ReturnType<typeof prepareDocumentDraftSave>> | null = null; let dependency: string | null = null
let controller: AbortController | null = null; let generation = 0
let ownedKey: string | null = null
const editable = computed(() => !props.reviewMode && props.active && !loading.value && !annotationBusy.value && !user.medicalWriting && currentSnapshot.value?.allowed_actions.includes('document.edit') === true)
const capacity = { maximumPages: 1000, maximumBytes: 104857600, pageSize: 100 }
const fieldRouter = useRouter(); const sourceRoute = useRoute()
const sourceReturnNotice = ref('')
const mainAnnotations = ref<{ restoreSelected: (annotationId: string, documentRevision: string) => Promise<boolean> }>()
const selectedAnnotationThread = shallowRef<{ annotationId: string; documentRevision: string } | null>(null)
let sourceChapter: { linkId: string; chapterId: string } | null = null
async function openChapterEvidence(link: EvidenceLink, chapterId: string): Promise<void> {
  if (!props.active || document.hidden || historyVisible.value || loading.value || writing.value || !body.value || !saved.value) return
  if (hasUnsaved.value || currentSnapshot.value?.document_revision !== saved.value.document_revision) { locationNotice.value = '请先保存并读取当前正文版本，再定位固定引用。'; return }
  if (!body.value.evidence_links.some(item => canonicalDocumentValue(item) === canonicalDocumentValue(link)) ||
      !chapterEvidence(body.value, chapterId).some(item => item.evidence_link_id === link.evidence_link_id)) return
  sourceChapter = { linkId: link.evidence_link_id, chapterId }
  locatePanel('evidence'); await fieldEvidenceReader.value?.read(link)
}
function locateEvidenceChapter(link: EvidenceLink, chapterId: string): void {
  if (!props.active || document.hidden || historyVisible.value || loading.value || writing.value || !body.value || !saved.value ||
      currentSnapshot.value?.document_revision !== saved.value.document_revision) return
  if (!chapterEvidence(body.value, chapterId).some(item => canonicalDocumentValue(item) === canonicalDocumentValue(link))) return
  locationNotice.value = hasUnsaved.value ? '已定位当前草稿章节，未保存修改保持不变。' : ''
  documentReader.value?.locate(chapterId)
}
let processedSourceReturn: unknown = null
function sourceReturnOrigin(link: EvidenceLink): SourceReturnOrigin | null {
  if (!props.active || hasUnsaved.value || loading.value || writing.value || !saved.value || !body.value ||
      currentSnapshot.value?.document_revision !== saved.value.document_revision || !props.taskId) return null
  const path = `/projects/${props.projectId}/tasks/${props.taskId}/documents/${props.documentId}/${props.reviewMode ? 'review' : 'write'}`
  if (sourceRoute.path !== path) return null
  const related = body.value.nodes.filter(node => link.node_ids.includes(node.node_id) || node.evidence_link_ids.includes(link.evidence_link_id))
  const selectedChapter = sourceChapter?.linkId === link.evidence_link_id ? sourceChapter.chapterId : null
  const inSelectedChapter = (id: string): boolean => {
    const visited = new Set<string>(); let node = body.value?.nodes.find(item => item.node_id === id)
    while (node && !visited.has(node.node_id)) {
      if (node.node_id === selectedChapter) return true
      visited.add(node.node_id); const parentId = node.parent_id
      node = body.value?.nodes.find(item => item.node_id === parentId)
    }
    return false
  }
  const focus = (selectedChapter ? related.find(node => inSelectedChapter(node.node_id)) : null) ?? related[0]
  if (!focus) return null
  let chapter = focus; let cursor = focus
  const visited = new Set<string>()
  while (cursor.parent_id && !visited.has(cursor.node_id)) {
    visited.add(cursor.node_id)
    const parent = body.value.nodes.find(node => node.node_id === cursor.parent_id)
    if (!parent) break
    if (parent.content.kind === 'SECTION') { chapter = parent; break }
    cursor = parent
  }
  const originChapter = sourceChapter?.linkId === link.evidence_link_id
    ? body.value.nodes.find(node => node.node_id === sourceChapter?.chapterId) ?? chapter : chapter
  return { path, documentRevision: saved.value.document_revision, chapterId: originChapter.node_id, focusNodeId: focus.node_id,
    ...(selectedAnnotationThread.value?.documentRevision === saved.value.document_revision ? { annotationId: selectedAnnotationThread.value.annotationId } : {}) }
}
async function restoreSourceReturn(): Promise<void> {
  const token = sourceRoute.query.source_return
  if (!token || token === processedSourceReturn || !saved.value || !body.value || loading.value) return
  const path = `/projects/${props.projectId}/tasks/${props.taskId}/documents/${props.documentId}/${props.reviewMode ? 'review' : 'write'}`
  const currentScope = sourceReturnScope(props.session); const revision = saved.value.document_revision
  const result = consumeSourceReturn(props.session, token, { path: sourceRoute.path === path ? path : '',
    documentRevision: revision, currentRevision: currentSnapshot.value?.document_revision ?? null,
    active: props.active && !writing.value && !historyVisible.value, dirty: hasUnsaved.value,
    nodeIds: body.value.nodes.map(node => node.node_id) })
  if (result.status === 'blocked') { sourceReturnNotice.value = '当前有未保存编辑或页面暂不可用；未恢复来源返回位置，也未更改正文。'; return }
  processedSourceReturn = token
  if (result.status !== 'ready') { sourceReturnNotice.value = result.status === 'stale'
    ? '文稿版本或引用位置已经变化，未跳转到最新正文中的相似位置。请核对原固定版本。'
    : '来源返回位置已过期或上下文已改变；未恢复位置。'; return }
  await nextTick()
  if (currentScope !== sourceReturnScope(props.session) || !props.active || hasUnsaved.value ||
      sourceRoute.query.source_return !== token || sourceRoute.path !== path || saved.value?.document_revision !== revision ||
      currentSnapshot.value?.document_revision !== revision) return
  try { documentReader.value?.locate(result.focusNodeId); sourceReturnNotice.value = '已返回同一固定文稿版本的原章节与引用位置。' }
  catch { sourceReturnNotice.value = '原引用位置当前不可定位；未改变正文。' }
  if (result.annotationId) {
    let restored = false
    try { restored = await mainAnnotations.value?.restoreSelected(result.annotationId, revision) ?? false } catch { /* Keep navigation failures local; never retry a write. */ }
    if (currentScope === sourceReturnScope(props.session) && sourceRoute.query.source_return === token && props.active &&
        !hasUnsaved.value && !writing.value && !loading.value && !historyVisible.value &&
        saved.value?.document_revision === revision && currentSnapshot.value?.document_revision === revision) {
      sourceReturnNotice.value += restored ? ' 已重新核验并恢复原选中标注。' : ' 原选中标注未恢复，请核查当前权限与固定版本。'
    }
  }
}
watch(() => [sourceRoute.query.source_return, saved.value, body.value, loading.value, props.active, hasUnsaved.value,
  sourceReturnScope(props.session)], () => { void restoreSourceReturn() }, { flush: 'post' })
const fieldVerification = ref<{ hasLocalDraft: () => boolean }>()
const fieldEvidenceReader = ref<{ read: (link: EvidenceLink) => Promise<void> }>()
const fieldPreparation = shallowRef<Preparation | null>(null)
const fieldPreparationLoading = ref(false), fieldPreparationNotice = ref('')
let fieldPreparationController: AbortController | null = null
let fieldPreparationGeneration = 0
const fieldEvidenceAvailable = computed(() => Boolean(props.active && props.session.current_context && saved.value && body.value))
const fieldNavigationContext = computed(() => {
  const context = props.session.current_context
  const ready = Boolean(props.active && context && saved.value?.project_id === props.projectId && body.value && props.taskId)
  return { projectId: props.projectId, taskId: props.taskId ?? '',
    canReadMapping: ready && taskReadAllowed(props.session, props.projectId, props.taskId!),
    // Exact refs identify source versions, not source IDs. A SOURCE-specific grant cannot be safely matched here.
    canReadSourceExact: ready && props.session.action_summary.some(grant => grant.action === 'source.read' && (
      grant.resource_kind === 'ENTERPRISE' && grant.project_id === null && grant.resource_id === context?.tenant_id
      || grant.resource_kind === 'PROJECT' && grant.project_id === props.projectId && grant.resource_id === props.projectId)) }
})
function clearFieldPreparation(): void {
  fieldPreparationGeneration++; fieldPreparationController?.abort(); fieldPreparationController = null
  fieldPreparation.value = null; fieldPreparationLoading.value = false; fieldPreparationNotice.value = ''
}
async function loadFieldPreparation(): Promise<void> {
  if (!fieldNavigationContext.value.canReadMapping || fieldPreparationLoading.value || fieldVerification.value?.hasLocalDraft() || document.hidden) return
  clearFieldPreparation()
  const request = new AbortController(); fieldPreparationController = request
  const current = fieldPreparationGeneration; fieldPreparationLoading.value = true
  try {
    const result = await readFieldTargetPreparation(() => props.session, props.projectId, props.taskId!, request.signal)
    if (request.signal.aborted || current !== fieldPreparationGeneration || !fieldNavigationContext.value.canReadMapping || document.hidden) return
    if (result.project_id !== props.projectId || result.task_id !== props.taskId || result.tenant_id !== props.session.current_context?.tenant_id
      || result.template_version !== body.value?.template_version || result.plan_revision !== saved.value?.plan_revision || !result.allowed_actions.includes('READ')) {
      fieldPreparationNotice.value = '当前提议映射与正文版本、任务或读取权限不匹配，未合并。'; return
    }
    if (fieldVerification.value?.hasLocalDraft()) { fieldPreparationNotice.value = '读取期间已有本地意见输入，未替换依据。请先处理本地意见后重试。'; return }
    fieldPreparation.value = result
    fieldPreparationNotice.value = '已读取当前任务提议映射；PROPOSED 不代表字段事实已确认。'
  } catch (error) {
    if (!request.signal.aborted && current === fieldPreparationGeneration) fieldPreparationNotice.value = '读取未完成，请核查当前权限或稍后重试。'
  } finally {
    if (current === fieldPreparationGeneration) { fieldPreparationLoading.value = false; fieldPreparationController = null }
  }
}
function openFieldTargets(destination: NonNullable<ReturnType<typeof fieldTargetsDestination>>): void {
  const current = fieldTargetsDestination(fieldNavigationContext.value)
  if (!current || document.hidden || fieldVerification.value?.hasLocalDraft() || canonicalDocumentValue(destination) !== canonicalDocumentValue(current)) return
  void fieldRouter.push(current)
}
function openFieldSource(destination: NonNullable<ReturnType<typeof sourceExactDestination>>, exact: SourceExactRef): void {
  if (!body.value || !fieldPreparation.value || document.hidden || fieldVerification.value?.hasLocalDraft()) return
  const entry = fieldPreparation.value.current_mapping?.manifest.mappings.find(item => canonicalDocumentValue(item.source_exact) === canonicalDocumentValue(exact))
  const current = entry && sourceExactDestination(entry, body.value, fieldPreparation.value, fieldNavigationContext.value)
  if (current && canonicalDocumentValue(current) === canonicalDocumentValue(destination)) void fieldRouter.push(current)
}
async function openFieldEvidence(link: EvidenceLink): Promise<void> {
  if (!fieldEvidenceAvailable.value || document.hidden || fieldVerification.value?.hasLocalDraft()
    || link.evidence.tenant_id !== props.session.current_context?.tenant_id
    || !body.value?.evidence_links.some(item => canonicalDocumentValue(item) === canonicalDocumentValue(link))) return
  locatePanel('evidence'); await fieldEvidenceReader.value?.read(link)
}
watch(() => [props.taskId, props.session, saved.value?.document_revision], clearFieldPreparation, { flush: 'sync' })
function remember(): boolean {
  if (props.reviewMode || ownedKey === null || saved.value === null || baseline.value === null || draft.value === null || dependency === null || !hasUnsaved.value && pending === null) return false
  return retainDocumentDraft(ownedKey, { snapshot: saved.value, baseline: baseline.value, draft: draft.value, dependency, pending,
    reason: reason.value, fieldInputs: fieldInputs.value })
}
function clear(invalidate = true): void {
  sourceChapter = null; selectedAnnotationThread.value = null
  clearFieldPreparation()
  generation++; controller?.abort(); controller = null
  historyController?.abort(); historyController = null; historyRevision.value = ''; historyError.value = ''; historyDocument.value = null; historySelection.value = null; historyVisible.value = false; historyLoading.value = false
  if (invalidate) { pending?.invalidate(); draft.value?.invalidate() }
  pending = null; ownedKey = null
  saved.value = null; body.value = null; draft.value = null; dependency = null; loading.value = false
  currentSnapshot.value = null; annotationSelection.value = null; annotationMainBusy.value = false; annotationHistoryBusy.value = false
  fieldInputs.value = {}
  baseline.value = null; comparison.value = null; comparing.value = false; comparisonTarget = null; comparisonDependency = null; comparisonEdited = null
  reason.value = ''; sentBytes.value = 0; totalBytes.value = 0
}
async function load(): Promise<void> {
  if (!props.active || loading.value || writing.value || annotationBusy.value || user.medicalWriting || hasUnsaved.value) return
  clear(); const request = new AbortController(); controller = request; const current = generation; loading.value = true
  try {
    const document = await readSavedDocument(props.session, props.projectId, props.documentId, capacity, request.signal)
    const accessSet = !props.reviewMode && document.snapshot.allowed_actions.includes('document.edit')
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
async function openAnnotationOriginal(revision: string): Promise<void> {
  historyRevision.value = revision; await readHistory()
}
async function openRequestedHistory(): Promise<void> {
  const value = requestedHistory.value
  if (!value.revision) { historyError.value = value.issue; return }
  historyRevision.value = value.revision
  await readHistory()
}
async function readHistory(): Promise<void> {
  if (!props.active || writing.value || loading.value || annotationBusy.value || user.medicalWriting || historyLoading.value) return
  historyDocument.value = null; historySelection.value = null; historyVisible.value = false; historyError.value = ''
  const requested = workspaceHistoryRevision(historyRevision.value)
  if (!requested.revision) { historyError.value = requested.issue; return }
  const request = new AbortController(); historyController?.abort(); historyController = request
  const current = generation; historyLoading.value = true
  try {
    const result = await readSavedDocumentRevision(props.session, props.projectId, props.documentId, requested.revision, capacity, request.signal)
    if (generation !== current || !props.active || historyController !== request || request.signal.aborted) return
    historySelection.value = null; historyDocument.value = result; historyVisible.value = true
  } finally { if (generation === current && historyController === request) historyLoading.value = false }
}
function editField(value: FieldValue): void {
  if (body.value === null) fail('ACTION_STALE', '正文尚未读取'); replace(confirmDocumentField(body.value, value))
  const next = { ...fieldInputs.value }; delete next[value.field_id]; fieldInputs.value = next
}
function retainFieldInput(value: FieldValue): void {
  if (!editable.value) fail('ACTION_STALE', '当前文稿无法编辑字段')
  const original = body.value?.fields.find(item => item.field_id === value.field_id)
  if (original === undefined) fail('ACTION_STALE', '字段输入对应的正文已经变化')
  const next = { ...fieldInputs.value }
  if (JSON.stringify(original) === JSON.stringify(value)) delete next[value.field_id]
  else next[value.field_id] = value
  fieldInputs.value = next
}
function selectedForAnnotation(value: Selection | null, dirty: boolean): void {
  annotationSelection.value = dirty ? null : value
}
async function refreshAfterAnnotation(signal: AbortSignal): Promise<void> {
  if (!props.active || writing.value || loading.value || pending !== null || saved.value === null || baseline.value === null || draft.value === null) {
    fail('ACTION_STALE', '标注已提交；当前正文暂不能刷新保存依据，请保留编辑并稍后重读')
  }
  const original = saved.value; const originalBody = baseline.value; const current = generation
  const request = new AbortController(); controller?.abort(); controller = request; loading.value = true
  const abort = () => request.abort(); signal.addEventListener('abort', abort, { once: true })
  if (signal.aborted) request.abort()
  try {
    const latest = await readSavedDocument(props.session, props.projectId, props.documentId, capacity, request.signal)
    const accessSet = !props.reviewMode && latest.snapshot.allowed_actions.includes('document.edit')
      ? await readDocumentDependency(props.session, latest.snapshot, request.signal) : null
    request.signal.throwIfAborted()
    if (generation !== current || !props.active || saved.value !== original || baseline.value !== originalBody) fail('CONTEXT_CHANGED', '标注刷新期间当前文稿已经变化')
    currentSnapshot.value = latest.snapshot
    if (latest.snapshot.document_revision !== original.document_revision || latest.snapshot.body.sha256 !== original.body.sha256
        || canonicalDocumentValue(latest.body) !== canonicalDocumentValue(originalBody)) {
      fail('REVISION_CONFLICT', '服务器正文已变化；当前正文和字段输入完整保留，请比较最新版本')
    }
    // Annotation creation advances the document head, not its body. Never replace draft/field inputs/undo history.
    saved.value = latest.snapshot; dependency = accessSet
  } finally {
    signal.removeEventListener('abort', abort)
    if (generation === current) loading.value = false
  }
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
  if (!props.active || writing.value || loading.value || annotationBusy.value || user.medicalWriting || pending !== null || baseline.value === null || body.value === null || draft.value === null) fail('ACTION_STALE', '当前正文无法更新读取依据')
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
  if (!props.active || annotationBusy.value || user.medicalWriting || comparison.value === null || comparisonTarget === null || draft.value === null || body.value === null
      || comparisonEdited !== canonicalDocumentValue(body.value) || pending !== null) fail('ACTION_STALE', '版本比较对应的正文已经变化')
  const chosen = mergeDocumentComparison(comparison.value)
  draft.value.replaceBaseline(comparisonTarget.body, chosen); saved.value = comparisonTarget.snapshot; currentSnapshot.value = comparisonTarget.snapshot
  baseline.value = comparisonTarget.body; body.value = draft.value.body; dependency = comparisonDependency; comparing.value = false
  comparison.value = null; comparisonTarget = null; comparisonDependency = null; comparisonEdited = null
}
async function restore(): Promise<void> {
  if (!props.active || props.session.current_context === null || loading.value || writing.value || annotationBusy.value || user.medicalWriting || hasUnsaved.value) return
  if (props.reviewMode) { await load(); return }
  const key = documentDraftKey(props.session, props.projectId, props.documentId); const value = resumeDocumentDraft(key)
  if (value === null) { await load(); return }
  const request = new AbortController(); controller = request; const current = generation; loading.value = true; let admitted = false
  try {
    const latest = await readSavedDocument(props.session, props.projectId, props.documentId, capacity, request.signal)
    if (generation !== current || !props.active || !acceptDocumentDraft(key, value)) fail('CONTEXT_CHANGED', '恢复草稿期间当前页面已经变化')
    ownedKey = key; saved.value = value.snapshot; baseline.value = value.baseline; currentSnapshot.value = latest.snapshot; draft.value = value.draft; body.value = value.draft.body
    dependency = value.dependency; pending = value.pending; reason.value = value.reason; fieldInputs.value = value.fieldInputs; admitted = true
  } finally {
    if (!admitted) returnDocumentDraft(key, value)
    if (generation === current) loading.value = false
  }
}
watch(() => [props.active, props.session.user.user_id, props.session.current_context?.context_revision,
  props.session.current_context?.auth_epoch, props.session.current_context?.governance_epoch, props.session.current_context?.tenant_id, sourceReturnScope(props.session), props.projectId, props.documentId, props.reviewMode], () => {
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

<style scoped>
.workspace-links { display: flex; flex-direction: column; gap: 8px; }
.workspace-links button { padding: 10px; border: 1px solid #d6e0ed; border-radius: 5px; background: white; color: #15559c; text-align: start; cursor: pointer; overflow-wrap: anywhere; }
.workspace-links button:focus-visible { outline: 2px solid #1768c4; outline-offset: 3px; }
.scope-note { font-size: 13px; line-height: 1.7; color: #536176; }
[data-document-editor] section[tabindex], .document-inspector { scroll-margin-top: 24px; }
.document-inspector { border-top: 1px solid #e9edf3; margin: 16px 0; padding-top: 8px; }
.document-inspector > summary { cursor: pointer; min-height: 38px; padding: 8px 0; color: #243247; font-weight: 600; }
.document-inspector > summary:focus-visible { outline: 2px solid #1768c4; outline-offset: 3px; }
@media (max-width: 650px) { .document-inspector > summary { min-height: 44px; } }
</style>
