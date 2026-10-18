<template>
  <main class="app-container source-rights">
    <h1>来源权利审查</h1>
    <session-panel />
    <p>固定来源版本：{{ sourceVersion }}</p>
    <router-link :to="'/library/versions/' + sourceVersion">读取来源版本及处理状态</router-link>
    <p v-if="!canReview" role="status">当前企业身份及来源审批许可齐备后才能读取权利审查依据。</p>
    <section v-if="pending" aria-label="原权利请求恢复">
      <h2>原权利请求</h2>
      <p>请求编号：{{ pending.key }}；状态：{{ pending.state === 'EXPIRED' ? '已过期' : '结果待核查' }}</p>
      <p>原上下文版本：{{ pending.context.context_revision }}；原请求摘要：{{ pending.body_sha256 }}</p>
      <el-button :disabled="busy || !user.medicalSession?.current_context" @click="queryOriginal">查询原请求</el-button>
      <p v-if="lookupState" role="status">原操作查询：{{ lookupState }}</p>
      <p v-if="currentSource">当前读取权利版本：{{ currentSource.rights_revision }}；状态：{{ statusLabels[currentSource.rights_status as RightsStatus] }}</p>
      <el-button v-if="lookupState === 'COMPLETED' && currentSource" :disabled="busy" @click="confirmOriginal">确认已读取原操作与当前来源</el-button>
      <el-popconfirm v-if="pending.state === 'EXPIRED'" title="确认结束已过期的原权利请求并开始新的审查？" @confirm="discardExpired">
        <template #reference><el-button :disabled="busy">结束已过期请求</el-button></template>
      </el-popconfirm>
      <details v-if="pending.state === 'PENDING'">
        <summary>使用完整原始参数恢复决定</summary>
        <p>填写原DecisionInput完整JSON。原理由及许可正文没有保存在浏览器存储中；恢复时必须与原摘要完全一致。</p>
        <el-input v-model="originalJson" type="textarea" :rows="8" :disabled="busy" aria-label="原权利决定完整JSON" />
        <p v-if="recoveryIssues.length" role="status">原请求尚未发送，请修正以下输入。原文及原请求编号已保留。</p>
        <ul v-if="recoveryIssues.length" role="alert"><li v-for="(issue, index) in recoveryIssues" :key="index">{{ issue.path || '原请求 JSON' }}：{{ issue.code }}</li></ul>
        <el-button :disabled="busy || !originalJson || !mayRecover" @click="recoverOriginal">{{ lookupState === 'COMPLETED' ? '读取原决定回执' : '重新发送原请求' }}</el-button>
        <p v-if="lookupState === 'NOT_FOUND' && !sameOriginalContext">当前上下文已经变化，原请求保持原版本；不能重新发送这份未找到的原请求。</p>
      </details>
    </section>
    <el-form v-if="!pending" aria-label="来源权利决定表单" :disabled="!canReview || busy" label-position="top" @submit.prevent="review">
      <el-form-item label="权利决定"><el-select v-model="decision" aria-label="权利决定">
        <el-option v-for="(label, value) in decisionLabels" :key="value" :label="label" :value="value" />
      </el-select></el-form-item>
      <fieldset v-if="decision === 'APPROVE'">
        <legend>完整许可范围</legend>
        <el-form-item label="用途代码"><el-input v-model="proposal.purpose" maxlength="128" aria-label="用途代码" /></el-form-item>
        <el-form-item label="允许动作"><el-checkbox-group v-model="proposal.allowed_actions" aria-label="允许动作">
          <el-checkbox v-for="(label, value) in actionLabels" :key="value" :value="value">{{ label }}</el-checkbox>
        </el-checkbox-group></el-form-item>
        <el-form-item label="模型或处理提供方"><el-select v-model="proposal.providers" multiple filterable allow-create default-first-option aria-label="模型或处理提供方" /></el-form-item>
        <el-form-item label="允许区域"><el-select v-model="proposal.regions" multiple filterable allow-create default-first-option aria-label="允许区域" /></el-form-item>
        <el-form-item label="生效UTC时间，空值表示未指定"><el-input v-model="validFrom" placeholder="YYYY-MM-DDTHH:mm:ss.SSSSSSZ" aria-label="生效UTC时间" /></el-form-item>
        <el-form-item label="结束UTC时间，空值表示未指定"><el-input v-model="validTo" placeholder="YYYY-MM-DDTHH:mm:ss.SSSSSSZ" aria-label="结束UTC时间" /></el-form-item>
        <el-form-item label="权利人"><el-input v-model="proposal.evidence_manifest.owner" maxlength="512" aria-label="权利人" /></el-form-item>
        <el-form-item label="完整许可说明"><el-input v-model="proposal.evidence_manifest.license" type="textarea" :rows="4" maxlength="4096" aria-label="完整许可说明" /></el-form-item>
        <el-form-item label="允许环境"><el-select v-model="proposal.evidence_manifest.environments" multiple filterable allow-create default-first-option aria-label="允许环境" /></el-form-item>
        <el-form-item label="允许分类"><el-checkbox-group v-model="proposal.evidence_manifest.classifications" aria-label="允许分类">
          <el-checkbox v-for="(label, value) in classificationLabels" :key="value" :value="value">{{ label }}</el-checkbox>
        </el-checkbox-group></el-form-item>
        <section aria-label="固定授权文件">
          <h2>固定授权文件</h2>
          <div v-for="(item, index) in evidence" :key="index" class="evidence-row">
            <el-form-item :label="'授权文件版本 ' + (index + 1)"><el-input v-model="item.version_id" :aria-label="'授权文件版本 ' + (index + 1)" /></el-form-item>
            <el-form-item :label="'授权文件摘要 ' + (index + 1)"><el-input v-model="item.content_sha256" :aria-label="'授权文件摘要 ' + (index + 1)" /></el-form-item>
            <el-button :disabled="busy" @click="removeEvidence(index)">移除授权文件 {{ index + 1 }}</el-button>
          </div>
          <el-button :disabled="busy || evidence.length >= 64" @click="evidence.push({ version_id: '', content_sha256: '' })">新增授权文件</el-button>
        </section>
      </fieldset>
      <p v-if="inputIssues.length" role="status">完整范围仍有 {{ inputIssues.length }} 项待填写或修正，请核查必需动作、许可、期限和授权文件。</p>
      <el-button native-type="submit" :disabled="!draft || inputIssues.length > 0 || busy || !canReview" :loading="reading">读取固定权利审查</el-button>
    </el-form>
    <section v-if="preview" aria-label="固定权利审查">
      <h2>固定权利审查</h2>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="来源身份">{{ preview.source_id }}</el-descriptions-item>
        <el-descriptions-item label="原件摘要">{{ preview.original_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="归属与分类">{{ preview.ownership }} · {{ preview.classification }}</el-descriptions-item>
        <el-descriptions-item label="原权利版本">{{ preview.base_rights.version_id }}</el-descriptions-item>
        <el-descriptions-item label="原权利摘要">{{ preview.base_rights.content_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="原权利状态">{{ statusLabels[preview.rights_status] }}</el-descriptions-item>
        <el-descriptions-item label="审查修订">{{ preview.revision }}</el-descriptions-item>
        <el-descriptions-item label="本次决定">{{ decisionLabels[preview.decision] }}</el-descriptions-item>
        <el-descriptions-item label="固定审查摘要">{{ preview.reviewed_manifest_sha256 }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="preview.evidence.length" :data="preview.evidence" aria-label="服务端授权文件清单">
        <el-table-column label="固定授权文件"><template #default="scope">{{ scope.row.artifact.version_id }}</template></el-table-column>
        <el-table-column label="原始摘要"><template #default="scope">{{ scope.row.artifact.content_sha256 }}</template></el-table-column>
        <el-table-column prop="artifact_kind" label="文件类别" /><el-table-column prop="classification" label="分类" />
        <el-table-column prop="media_type" label="媒体类型" /><el-table-column prop="size_bytes" label="完整字节数" />
        <el-table-column prop="schema_ref" label="固定内容定义" /><el-table-column prop="schema_document_sha256" label="定义摘要" />
      </el-table>
      <el-form aria-label="提交固定权利决定" :disabled="busy || !canReview" label-position="top" @submit.prevent="submit">
        <el-form-item label="决定理由"><el-input v-model="reason" type="textarea" :rows="3" maxlength="2000" aria-label="决定理由" /></el-form-item>
        <el-checkbox v-model="attested">已核查来源版本、完整许可范围及授权文件</el-checkbox>
        <el-button native-type="submit" :disabled="!maySubmit" :loading="writing">提交固定权利决定</el-button>
      </el-form>
    </section>
    <section v-if="receipt" aria-label="权利决定完成回执">
      <h2>权利决定完成回执</h2>
      <p>{{ decisionLabels[receipt.decision] }} · {{ statusLabels[receipt.rights_status] }}</p>
      <p>决定编号：{{ receipt.decision_id }}；原操作：{{ receipt.operation_id }}</p>
      <p>权利版本：{{ receipt.rights.version_id }}；权利摘要：{{ receipt.rights.content_sha256 }}</p>
      <p>当前修订：{{ receipt.revision }}；决定时间：{{ receipt.decided_at }}</p>
      <p>当前来源权利指针及原操作完成状态已经重新读取并核查。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { canonicalizeEx } from 'json-canonicalize'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { fail, schemaIssues } from '@/api/medical/protocol'
import { parseSourceInput } from '@/api/medical/source-input-issues'
import type { SourceInputIssue } from '@/api/medical/source-input-issues'
import type { Basis, MedicalSession } from '@/api/medical/protocol'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import { readSourceGovernance, sourceApprovalAllowed } from '@/api/medical/source-governance'
import type { SourceGovernanceView } from '@/api/medical/source-governance'
import { previewSourceRights, rightsDecisionBody, rightsInputIssues, submitSourceRights } from '@/api/medical/source-rights'
import type { RightsDecision, RightsDecisionInput, RightsPreview, RightsPreviewInput, RightsProposal, RightsReceipt, RightsStatus } from '@/api/medical/source-rights'
import { clearExpiredRequestMetadata, confirmRequestMetadata, expireRequestMetadata, prepareRequestMetadata, readRequestMetadata, requestBodyDigest } from '@/api/medical/request-metadata'
import type { RequestMetadata } from '@/api/medical/request-metadata'
import { lookupOperation, readOperation } from '@/api/medical/operations'
import type { Operation } from '@/api/medical/operations'

const route = useRoute(); const user = useUserStore()
const sourceVersion = computed(() => String(route.params.sourceVersion))
const decision = ref<RightsDecision | ''>(''); const proposal = reactive<RightsProposal>(emptyProposal())
const evidence = ref<Basis[]>([]); const validFrom = ref(''); const validTo = ref(''); const reason = ref(''); const attested = ref(false)
const preview = shallowRef<RightsPreview | null>(null); const receipt = shallowRef<RightsReceipt | null>(null)
const pending = shallowRef<RequestMetadata | null>(null); const currentSource = shallowRef<SourceGovernanceView | null>(null)
const lookupState = ref(''); const operation = shallowRef<Operation | null>(null); const originalJson = ref('')
const recoveryIssues = ref<SourceInputIssue[]>([])
const reading = ref(false); const writing = ref(false); let generation = 0; let controller: AbortController | null = null
const active = useMedicalRouteRead('MedicalSourceRights', clear)
const busy = computed(() => reading.value || writing.value || user.medicalWriting)
const canReview = computed(() => active.value && Boolean(user.medicalSession?.current_context)
  && user.medicalSession.action_summary.some((item: { action: string }) => item.action === 'source.approve'))
const decisionLabels = { APPROVE: '批准完整权利范围', RETURN: '退回待审权利', REVOKE: '撤销已批准权利' }
const statusLabels = { PENDING_REVIEW: '待审查', APPROVED: '已批准', REVOKED: '已撤销', EXPIRED: '已过期' }
const actionLabels = { READ: '读取', STORE: '保存', ENTERPRISE_USE: '企业使用', DISTRIBUTE: '分发', EXTERNAL_PROCESS: '外部处理' }
const classificationLabels = { PUBLIC: '公开', INTERNAL: '内部', RESTRICTED: '受限', PERSONAL_HEALTH: '个人健康信息' }
const draft = computed<RightsPreviewInput | null>(() => decision.value ? { schema_version: '1.0.0', decision: decision.value,
  proposal: decision.value === 'APPROVE' ? { ...proposal, allowed_actions: [...proposal.allowed_actions], providers: [...proposal.providers], regions: [...proposal.regions],
    valid_from: validFrom.value || null, valid_to: validTo.value || null, evidence_manifest: { ...proposal.evidence_manifest,
      environments: [...proposal.evidence_manifest.environments], classifications: [...proposal.evidence_manifest.classifications],
      authorization_evidence: evidence.value.map(item => ({ ...item })) } } : null } : null)
const inputIssues = computed(() => draft.value ? rightsInputIssues(draft.value) : [{ path: '/decision', code: 'REQUIRED' }])
const maySubmit = computed(() => canReview.value && !busy.value && preview.value !== null && attested.value && pending.value === null
  && sourceApprovalAllowed(user.medicalSession, preview.value.source_id, preview.value.owner_project_id)
  && reason.value.trim() === reason.value && reason.value.normalize('NFC') === reason.value && Array.from(reason.value).length > 0
  && Array.from(reason.value).length <= 2000 && inputIssues.value.length === 0)
const sameOriginalContext = computed(() => pending.value !== null && user.medicalSession?.current_context
  && canonicalizeEx(pending.value.context) === canonicalizeEx(user.medicalSession.current_context))
const mayRecover = computed(() => pending.value?.state === 'PENDING'
  && (lookupState.value === 'COMPLETED' || lookupState.value === 'NOT_FOUND' && sameOriginalContext.value))
function emptyProposal(): RightsProposal {
  return { purpose: '', allowed_actions: [], providers: [], regions: [], valid_from: null, valid_to: null,
    evidence_manifest: { owner: '', license: '', environments: [], classifications: [], authorization_evidence: [] } }
}
function invalidateReview(): void {
  generation += 1; controller?.abort(); controller = null; preview.value = null; receipt.value = null; attested.value = false; reading.value = false
}
function clear(): void {
  invalidateReview(); decision.value = ''; Object.assign(proposal, emptyProposal()); evidence.value = []; validFrom.value = ''; validTo.value = ''
  reason.value = ''; recoveryIssues.value = []; pending.value = null; currentSource.value = null; lookupState.value = ''; operation.value = null; originalJson.value = ''; writing.value = false
}
function removeEvidence(index: number): void { evidence.value.splice(index, 1) }
function begin(): { session: MedicalSession; target: string; signal: AbortSignal; requested: number } {
  const session: MedicalSession | null = user.medicalSession
  if (!active.value || !session?.current_context || document.hidden) fail('ACCESS_DENIED', '当前页面需要有效企业身份')
  controller?.abort(); controller = new AbortController()
  return { session, target: sourceVersion.value, signal: controller.signal, requested: ++generation }
}
function valid(work: ReturnType<typeof begin>): boolean {
  return work.requested === generation && active.value && user.medicalSession === work.session && sourceVersion.value === work.target
    && !document.hidden && !work.signal.aborted
}
async function review(): Promise<void> {
  if (!draft.value || busy.value || !canReview.value || pending.value || inputIssues.value.length) return
  const body = structuredClone(draft.value); invalidateReview(); const work = begin(); reading.value = true
  try {
    const result = await previewSourceRights(work.session, work.target, body, work.signal)
    if (valid(work) && canonicalizeEx(draft.value) === canonicalizeEx(body)) preview.value = result
  } finally { if (work.requested === generation) reading.value = false }
}
async function verifyCompleted(result: RightsReceipt, metadata: RequestMetadata, original: MedicalSession, target: string): Promise<void> {
  if (!active.value || sourceVersion.value !== target || user.medicalSession !== original || document.hidden) return
  const fresh = await user.readMedical() as MedicalSession | null
  if (!active.value || sourceVersion.value !== target || !fresh?.current_context || document.hidden
      || fresh.user.user_id !== original.user.user_id || fresh.current_context.tenant_id !== metadata.tenant_id) return
  const work = begin()
  const actualOperation = await readOperation(fresh, result.operation_id, work.signal)
  const actual = await readSourceGovernance(fresh, target, work.signal)
  if (!valid(work)) return
  if (actual.source_id !== metadata.scope_id || actual.source_version !== target || actual.rights_revision !== result.rights.version_id
      || actual.rights_status !== result.rights_status || actualOperation.operation_id !== result.operation_id) {
    fail('CONTENT_MISMATCH', '权利决定的原操作及当前来源权利重读不一致')
  }
  confirmRequestMetadata(fresh, metadata, actualOperation, target)
  pending.value = null; currentSource.value = actual; receipt.value = result
}
async function submit(): Promise<void> {
  if (!maySubmit.value || !preview.value) return
  const selected = preview.value; const body = rightsDecisionBody(selected, reason.value); const work = begin()
  user.medicalWriting = true; writing.value = true
  try {
    const metadata = await prepareRequestMetadata(work.session, 'KN-RIGHTS-01', work.target, work.target, selected.source_id, body, work.signal)
    if (!valid(work)) return
    pending.value = metadata; originalJson.value = JSON.stringify(body, null, 2); preview.value = null
    const result = await submitSourceRights(work.session, work.target, metadata, body, work.signal)
    await verifyCompleted(result, metadata, work.session, work.target)
  } finally { user.medicalWriting = false; writing.value = false }
}
async function queryOriginal(): Promise<void> {
  if (busy.value || !pending.value) return
  const metadata = pending.value; const work = begin(); reading.value = true; lookupState.value = ''; operation.value = null; currentSource.value = null
  try {
    const result = await lookupOperation(work.session, 'KN-RIGHTS-01', metadata.key, work.signal)
    if (!valid(work)) return
    if (result.lookup_status === 'EXPIRED') { expireRequestMetadata(work.session, metadata); pending.value = readRequestMetadata(work.session, 'KN-RIGHTS-01', work.target) }
    if (result.operation !== null && result.operation.resource_id !== work.target) fail('CONTENT_MISMATCH', '原权利操作返回了其他来源版本')
    operation.value = result.operation; lookupState.value = result.operation?.status ?? result.lookup_status
    if (result.operation?.status === 'COMPLETED') {
      const actual = await readSourceGovernance(work.session, work.target, work.signal)
      if (!valid(work)) return
      if (actual.source_version !== work.target || actual.source_id !== metadata.scope_id) fail('CONTENT_MISMATCH', '原权利操作与当前来源归属不一致')
      currentSource.value = actual
    }
  } finally { if (work.requested === generation) reading.value = false }
}
function confirmOriginal(): void {
  const session: MedicalSession | null = user.medicalSession
  if (busy.value || !session?.current_context || !pending.value || !operation.value || !currentSource.value) return
  confirmRequestMetadata(session, pending.value, operation.value, sourceVersion.value)
  pending.value = null; originalJson.value = ''; lookupState.value = ''; operation.value = null
}
function discardExpired(): void {
  const session: MedicalSession | null = user.medicalSession
  if (busy.value || !session?.current_context || !pending.value) return
  clearExpiredRequestMetadata(session, pending.value); clear()
}
async function recoverOriginal(): Promise<void> {
  if (busy.value || !mayRecover.value || !pending.value) return
  const parsed = parseSourceInput(originalJson.value)
  recoveryIssues.value = parsed.issues
  if (recoveryIssues.value.length) return
  recoveryIssues.value = schemaIssues('urn:medical:source-rights-decisions:1.0.0#/$defs/DecisionInput', parsed.value)
  if (recoveryIssues.value.length) return
  const body = parsed.value as RightsDecisionInput
  recoveryIssues.value = rightsInputIssues(body)
  if (recoveryIssues.value.length) return
  const metadata = pending.value; const work = begin(); user.medicalWriting = true; writing.value = true
  try {
    if (metadata.body_sha256 !== await requestBodyDigest(body)) fail('IDEMPOTENCY_CONFLICT', '恢复输入与完整原请求摘要不一致')
    if (!valid(work)) return
    const result = await submitSourceRights(work.session, work.target, metadata, body, work.signal)
    await verifyCompleted(result, metadata, work.session, work.target)
  } finally { user.medicalWriting = false; writing.value = false }
}
watch(() => canonicalizeEx(draft.value), invalidateReview, { flush: 'sync' })
watch(() => [user.medicalSession, sourceVersion.value, active.value], () => {
  clear()
  const session: MedicalSession | null = user.medicalSession
  if (active.value && session?.current_context && !document.hidden) pending.value = readRequestMetadata(session, 'KN-RIGHTS-01', sourceVersion.value)
}, { flush: 'sync', immediate: true })
function visibility(): void { if (document.hidden) clear() }
window.addEventListener('medical-context-invalidated', clear); document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { clear(); window.removeEventListener('medical-context-invalidated', clear); document.removeEventListener('visibilitychange', visibility) })
</script>

<style scoped>
.source-rights section, .source-rights form { margin-top: 24px; max-width: 1100px; }
.source-rights .el-select { width: 100%; }
.source-rights .el-button { margin-top: 16px; }
.source-rights fieldset { border: 1px solid var(--el-border-color); padding: 20px; }
.evidence-row { margin-bottom: 20px; }
</style>
