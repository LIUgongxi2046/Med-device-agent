<template>
  <main class="medical-page">
    <header class="template-center-heading"><h1>{{ target ? permitted ? '模板人工审查' : '模板详情' : '模板中心' }}</h1><div><el-button v-if="preview" :disabled="!templateCreateAllowed(user.medicalSession) || !templateReadAllowed(user.medicalSession,preview.template.template_version) || user.medicalWriting" @click="router.push({path:'/templates/new',query:{from:preview.template.template_version}})">修改章节配置</el-button><el-button type="primary" :disabled="!templateCreateAllowed(user.medicalSession) || user.medicalWriting" @click="router.push('/templates/new')">创建模板草稿</el-button></div></header>
    <template-list v-if="!target" />
    <p v-if="!readable">模板读取需要当前企业的明确读取或审查权限</p>
    <el-alert v-if="target && readable && !permitted" title="当前为只读模板。创建修订需要模板维护权限，人工核准需要独立授权。" type="info" :closable="false" />
    <el-collapse v-if="!target"><el-collapse-item title="高级：打开已知模板版本" name="version"><el-form label-position="top" @submit.prevent="open">
      <el-form-item label="模板版本" :error="entryError">
        <el-input v-model="entry" placeholder="填写需要读取的固定模板版本标识" :disabled="!readable || user.medicalWriting" />
      </el-form-item>
      <el-button native-type="submit" :disabled="!readable || user.medicalWriting">打开模板版本</el-button>
    </el-form></el-collapse-item></el-collapse>
    <p v-if="loading" role="status">正在读取完整模板定义并核查当前依赖权限</p>
    <el-alert v-if="pending" title="原模板决定待核查" type="warning" :closable="false" data-template-pending>
      <p>决定：{{ templateDecisionLabels[pending.decision] }}；原治理修订：{{ pending.expected_revision }}</p>
      <p>请求标识：{{ pending.key }}</p>
      <p>本次操作使用原请求标识。刷新后需要重新填写原理由；只有完整参数一致才能再次提交。</p>
      <el-button :disabled="user.medicalWriting || checking || !permitted" @click="lookup">查询原决定</el-button>
      <p v-if="lookupResult">原请求查询状态：{{ lookupLabels[lookupResult.lookup_status] }}</p>
      <p v-if="lookupResult?.operation">操作编号：{{ lookupResult.operation.operation_id }}；状态：{{ lookupResult.operation.status }}</p>
      <el-button v-if="lookupResult?.operation?.status === 'COMPLETED'" :disabled="user.medicalWriting || checking" @click="confirmLookup">核查已完成的原决定</el-button>
    </el-alert>
    <el-alert v-if="completed" title="原模板决定已完成并核查" type="success" :closable="false">
      <p>操作编号：{{ completed.operation_id }}</p>
      <p v-if="receipt">决定编号：{{ receipt.decision_id }}；结果：{{ templateStatusLabels[receipt.status] }}；治理修订：{{ receipt.revision }}</p>
      <p>当前模板状态及版本以本页面重新读取的结果为准。</p>
    </el-alert>
    <section v-if="preview" data-template-preview aria-label="完整模板定义">
      <el-descriptions title="模板信息" :column="3" border>
        <el-descriptions-item label="状态">{{ templateStatusLabels[preview.template.status] }}</el-descriptions-item>
        <el-descriptions-item label="文档类型">{{ documentLabels[preview.template.content.document_type] }}</el-descriptions-item>
        <el-descriptions-item label="申请路径">{{ pathLabels[preview.template.content.application_path] }}</el-descriptions-item>
        <el-descriptions-item label="IVD">{{ preview.template.content.ivd ? '属于IVD' : '非IVD' }}</el-descriptions-item>
        <el-descriptions-item label="创建理由">{{ preview.template.reason }}</el-descriptions-item>
        <el-descriptions-item label="创建人员">{{ preview.template.created_by }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ preview.template.created_at }}</el-descriptions-item>
        <el-descriptions-item label="目录规范">{{ preview.template.content.directory_profile ?? '使用本类型的核准目录' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs v-model="reviewTab" class="template-review-tabs"><el-tab-pane label="章节配置" name="chapters"><template-chapter-summary :template="preview.template" /></el-tab-pane><el-tab-pane label="字段与附件" name="fields">
      <h2>全部字段</h2>
      <el-table :data="preview.template.content.field_definitions" row-key="field_id" border>
        <el-table-column prop="name" label="字段名称" min-width="160" />
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column prop="value_type" label="字段类型" width="160" />
        <el-table-column label="必需" width="90"><template #default="scope">{{ scope.row.required ? '必需' : '按条件确认' }}</template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><pre class="template-definition">{{ full(scope.row) }}</pre></template></el-table-column>
      </el-table>
      <h2>重复行</h2>
      <el-table :data="preview.template.content.repeat_definitions" row-key="repeat_id" border>
        <el-table-column prop="name" label="表格名称" min-width="200" />
        <el-table-column prop="min_count" label="最少行数" width="120" />
        <el-table-column label="最多行数" width="120"><template #default="scope">{{ scope.row.max_count ?? '未设上限' }}</template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><pre class="template-definition">{{ full(scope.row) }}</pre></template></el-table-column>
      </el-table>
      <h2>附件要求</h2>
      <p>附件必须使用获授权原件，正文生成不能代替附件。</p>
      <el-table :data="preview.template.content.attachment_requirements" row-key="attachment_requirement_id" border>
        <el-table-column prop="name" label="附件名称" min-width="200" />
        <el-table-column prop="kind" label="附件类型" width="180" />
        <el-table-column prop="min_count" label="最少数量" width="120" />
        <el-table-column label="最多数量" width="120"><template #default="scope">{{ scope.row.max_count ?? '未设上限' }}</template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><pre class="template-definition">{{ full(scope.row) }}</pre></template></el-table-column>
      </el-table>
      </el-tab-pane><el-tab-pane label="适用范围与规则" name="rules"><el-descriptions :column="2" border><el-descriptions-item label="文档类型">{{ documentLabels[preview.template.content.document_type] }}</el-descriptions-item><el-descriptions-item label="申请路径">{{ pathLabels[preview.template.content.application_path] }}</el-descriptions-item><el-descriptions-item label="器械类别">{{ preview.template.content.ivd ? 'IVD' : '非IVD' }}</el-descriptions-item><el-descriptions-item label="适用确认">由各项目结合固定属性和条件规则分别确认</el-descriptions-item></el-descriptions>
      <section v-if="referencedRules.length" aria-label="模板引用的固定规则">
        <h2>模板引用的固定规则</h2>
        <p>打开已授权规则并核对模板引用的内容摘要；读取不会改变模板或规则状态。</p>
        <ul><li v-for="rule in referencedRules" :key="rule.version_id">
          <router-link v-if="ruleAllowed(user.medicalSession, 'rule.read', rule.version_id) || ruleAllowed(user.medicalSession, 'rule.approve', rule.version_id)"
            :to="{ path: '/rules/' + rule.version_id, query: { content_sha256: rule.content_sha256 } }">{{ rule.version_id }}</router-link>
          <span v-else>{{ rule.version_id }}（当前没有规则读取或审查权限）</span>
          <p>SHA-256：{{ rule.content_sha256 }}</p>
        </li></ul>
      </section>
      </el-tab-pane><el-tab-pane label="版式与版本记录" name="versions"><el-descriptions :column="1" border><el-descriptions-item label="固定模板版本">{{ preview.template.template_version }}</el-descriptions-item><el-descriptions-item label="当前修订">{{ preview.template.revision }}</el-descriptions-item><el-descriptions-item label="原模板版本">{{ preview.template.base_version?.version_id ?? '独立创建' }}</el-descriptions-item><el-descriptions-item label="创建说明">{{ preview.template.reason }}</el-descriptions-item></el-descriptions><el-collapse>
        <el-collapse-item title="条件规则与输入属性" name="conditions"><pre class="template-definition">{{ full(preview.template.content.condition_schema) }}</pre></el-collapse-item>
        <el-collapse-item title="全部跨文档规则" name="cross"><pre class="template-definition">{{ full(preview.template.content.cross_document_rules) }}</pre></el-collapse-item>
        <el-collapse-item title="版式、编号、字体与附件顺序" name="format"><pre class="template-definition">{{ full(preview.template.content.format_profile) }}</pre></el-collapse-item>
        <el-collapse-item title="完整依据及固定原始版本" name="basis"><pre class="template-definition">{{ full({ basis_manifest: preview.template.content.basis_manifest, base_version: preview.template.base_version, dependency_refs: preview.template.dependency_refs }) }}</pre></el-collapse-item>
        <el-collapse-item title="本次固定审查清单" name="baseline"><pre class="template-definition">{{ full(preview.baseline_manifest) }}</pre></el-collapse-item>
        <el-collapse-item title="完整模板定义" name="content"><pre class="template-definition">{{ full(preview.template.content) }}</pre></el-collapse-item>
      </el-collapse>
      </el-tab-pane></el-tabs>
      <el-button v-if="decisions.length" data-template-decide :disabled="user.medicalWriting || checking || !mayWrite" @click="dialog = true">登记人工决定</el-button>
      <p v-if="pending && !mayWrite">当前模板或审查上下文已经变化，原决定需要通过原操作查询核查。</p>
      <p v-if="preview.template.status === 'DISABLED'">模板已停用；内容修改需要创建新的模板版本。</p>
    </section>
    <router-link v-if="target" to="/templates">返回模板中心</router-link>
    <el-dialog v-model="dialog" title="确认模板人工决定" :close-on-click-modal="false" :close-on-press-escape="!user.medicalWriting" :before-close="closeDialog">
      <el-form v-if="preview" ref="formElement" label-position="top" @submit.prevent="submit">
        <el-form-item label="人工决定" :error="errors.decision">
          <el-select v-model="decision" :disabled="user.medicalWriting || pending !== null">
            <el-option v-for="value in decisions" :key="value" :label="templateDecisionLabels[value]" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="决定理由" :error="errors.reason"><el-input v-model="reason" type="textarea" :rows="5" :disabled="user.medicalWriting" /></el-form-item>
        <el-form-item :error="errors.attested"><el-checkbox v-model="attested" :disabled="user.medicalWriting">已经核查完整目录、字段、附件、规则、版式和获授权依据</el-checkbox></el-form-item>
        <p>模板版本：{{ preview.template.template_version }}；治理修订：{{ preview.template.revision }}</p>
        <p>本次决定固定当前审查清单，服务器提交时重新核查全部权限和依赖。</p>
        <el-button native-type="submit" :disabled="user.medicalWriting || checking || !mayWrite">{{ pending ? '使用原请求标识提交' : '确认登记决定' }}</el-button>
      </el-form>
    </el-dialog>
    <el-dialog v-model="discardDialog" title="未保存的人工决定" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>决定理由尚未保存。原请求标识继续保留，需要通过原操作查询核查提交结果。</p>
      <el-button @click="discardDialog = false">继续填写</el-button>
      <el-button :disabled="user.medicalWriting" @click="discard">放弃未保存输入</el-button>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { useMedicalRouteRead } from '@/api/medical/route-read'
import type { FormInstance } from 'element-plus'
import { approvalDecisions, clearCompletedApproval, confirmTemplateApproval, prepareTemplateApproval, readPendingApproval,
  readTemplateDefinition, readTemplatePreview, submitTemplateApproval, templateApprovalAllowed, templateReadAllowed, templateDecisionLabels, templateStatusLabels } from '@/api/medical/template-approval'
import type { ApprovalBody, ApprovalReceipt, PendingApproval, TemplateDecision, TemplatePreview } from '@/api/medical/template-approval'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { projectTextIssue } from '@/api/medical/project-write'
import { templateCreateAllowed } from '@/api/medical/template-create'
import { lookupOperation } from '@/api/medical/operations'
import type { Operation, OperationLookup } from '@/api/medical/operations'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import TemplateList from './template-list.vue'
import TemplateChapterSummary from './template-chapter-summary.vue'
import { referencedTemplateRules, ruleAllowed } from '@/api/medical/rule-versions'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const target = computed(() => typeof route.params.templateId === 'string' ? route.params.templateId : null)
const reviewTab=ref('chapters')
const permitted = computed(() => templateApprovalAllowed(user.medicalSession, target.value ?? undefined))
const readable=computed(()=>permitted.value||templateReadAllowed(user.medicalSession,target.value??undefined))
const preview = ref<TemplatePreview | null>(null); const loading = ref(false); const checking = ref(false)
const pending = ref<PendingApproval | null>(null); const lookupResult = ref<OperationLookup | null>(null)
const receipt = ref<ApprovalReceipt | null>(null); const completed = ref<Operation | null>(null)
const decisions = computed(() => preview.value ? approvalDecisions(preview.value) : [])
const referencedRules = computed(() => preview.value ? referencedTemplateRules(preview.value.template.content) : [])
const mayWrite = computed(() => permitted.value && preview.value !== null && (pending.value === null
  || pending.value.expected_revision === preview.value.template.revision
    && pending.value.content_sha256 === preview.value.template.content_sha256
    && user.medicalSession?.current_context?.context_revision === pending.value.context.context_revision))
const entry = ref(''); const entryError = ref(''); const dialog = ref(false); const discardDialog = ref(false)
const decision = ref<TemplateDecision | ''>(''); const reason = ref(''); const attested = ref(false)
const formElement = ref<FormInstance>(); const errors = reactive({ decision: '', reason: '', attested: '' })
const lookupLabels = { FOUND: '已经找到原操作', NOT_FOUND: '当前未找到原操作', EXPIRED: '原查询记录已过期，需要人工核查' }
const documentLabels: Record<string, string> = { PRODUCT_TECHNICAL_REQUIREMENTS: '产品技术要求', REGISTRATION_DIRECTORY: '注册文档目录',
  OVERVIEW: '综述资料', INSTRUCTIONS_FOR_USE: '说明书', LABEL: '标签样稿', ESSENTIAL_PRINCIPLES: '基本原则清单', RISK_MANAGEMENT: '风险管理',
  RESEARCH_REPORT: '研究报告', CLINICAL_EVALUATION: '临床评价', QUALITY_SYSTEM: '质量体系', CHANGE_REGISTRATION: '变更资料',
  RENEWAL_REGISTRATION: '延续资料', REGISTRATION_DOSSIER: '注册资料总稿' }
const pathLabels: Record<string, string> = { FIRST_REGISTRATION: '首次注册', CHANGE_REGISTRATION: '变更注册', RENEWAL_REGISTRATION: '延续注册' }
let generation = 0; let controller: AbortController | null = null; let nextTarget: string | null = null
let dialogDone: (() => void) | null = null; let leaveApproved = false
function full(value: unknown): string { return JSON.stringify(value, null, 2) }
function clearForm(): void { decision.value = ''; reason.value = ''; attested.value = false; errors.decision = ''; errors.reason = ''; errors.attested = '' }
function clear(): void {
  generation += 1; controller?.abort(); controller = null; preview.value = null; pending.value = null; lookupResult.value = null
  receipt.value = null; completed.value = null; loading.value = false; checking.value = false; dialog.value = false; discardDialog.value = false
  entry.value = ''; entryError.value = ''; clearForm()
}
async function read(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession; const id = target.value
  if (!session?.current_context || !readable.value || !id || !routeActive.value || document.hidden || user.medicalWriting) return
  const current = generation; controller = new AbortController(); loading.value = true
  pending.value = permitted.value?readPendingApproval(session, id):null; decision.value = pending.value?.decision ?? ''
  try {
    const value = permitted.value?await readTemplatePreview(session,id,controller.signal):await readTemplateDefinition(session,id,controller.signal)
    if (current === generation && user.medicalSession === session && target.value === id && routeActive.value && !document.hidden) preview.value = value
  } finally { if (current === generation) loading.value = false }
}
async function open(): Promise<void> {
  if (!readable.value || user.medicalWriting) return
  entryError.value = schemaIssues('urn:medical:template-approval:1.0.0#/$defs/Id', entry.value).length ? '请填写规范的固定模板版本标识' : ''
  if (!entryError.value) await router.push('/templates/' + entry.value + '/approval')
}
async function focusError(): Promise<void> {
  await nextTick()
  const element = (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea,.el-form-item.is-error [tabindex]')
  element?.scrollIntoView({ block: 'nearest' }); element?.focus()
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session?.current_context || !original || checking.value || user.medicalWriting || document.hidden) return
  const current = generation; checking.value = true
  try {
    const value = await lookupOperation(session, original.operation, original.key, new AbortController().signal)
    if (current === generation && user.medicalSession === session && pending.value?.key === original.key && !document.hidden) lookupResult.value = value
  } finally { if (current === generation) checking.value = false }
}
async function confirmLookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const found = lookupResult.value?.operation
  if (!session?.current_context || !original || !found || found.status !== 'COMPLETED' || checking.value || user.medicalWriting || document.hidden) return
  const current = generation; checking.value = true
  try {
    const operation = await confirmTemplateApproval(session, original, found.operation_id, new AbortController().signal)
    const value = await readTemplatePreview(session, original.template_version, new AbortController().signal)
    if (value.template.content_sha256 !== original.content_sha256 || BigInt(value.template.revision) <= BigInt(original.expected_revision)) {
      fail('CONTENT_MISMATCH', '原决定已完成，当前模板版本需要核查')
    }
    if (current !== generation || user.medicalSession !== session || document.hidden) return
    clearCompletedApproval(session, original, operation); pending.value = null; lookupResult.value = null
    completed.value = operation; preview.value = value; dialog.value = false; clearForm()
  } finally { if (current === generation) checking.value = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const value = preview.value; const id = target.value
  if (!session?.current_context || !value || !id || !mayWrite.value || user.medicalWriting || checking.value || document.hidden) return
  errors.reason = projectTextIssue(reason.value, 2000); errors.attested = attested.value ? '' : '请确认已经核查全部模板要求及依据'
  errors.decision = decision.value && decisions.value.includes(decision.value) ? '' : '请选择当前状态允许的人工决定'
  if (Object.values(errors).some(Boolean)) { await focusError(); return }
  if (!decision.value) throw new Error('人工决定缺少实际动作')
  const body: ApprovalBody = { schema_version: '1.0.0', target_revision: id, expected_revision: value.template.revision,
    decision: decision.value, reason: reason.value, baseline_manifest: value.baseline_manifest }
  const path = route.fullPath; const current = generation
  user.medicalWriting = true
  try {
    const original = await prepareTemplateApproval(session, id, body); pending.value = original
    if (generation !== current || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '模板决定尚未发送，请重新核查当前身份')
    const result = await submitTemplateApproval(session, original, body)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) {
      fail('CONTEXT_CHANGED', '原模板决定已经返回，当前主体或企业需要重新核查')
    }
    const operation = await confirmTemplateApproval(fresh, original, result.operation_id, new AbortController().signal)
    const actual = await readTemplatePreview(fresh, id, new AbortController().signal)
    if (actual.template.content_sha256 !== result.content_sha256 || BigInt(actual.template.revision) < BigInt(result.revision)
        || actual.template.revision === result.revision && actual.template.status !== result.status) fail('CONTENT_MISMATCH', '决定回执与当前模板内容或治理修订不一致')
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearCompletedApproval(fresh, original, operation); pending.value = null; lookupResult.value = null
    receipt.value = result; completed.value = operation; preview.value = actual; clearForm(); dialog.value = false
  } finally {
    user.medicalWriting = false
    if (user.medicalSession !== session && route.fullPath === path && user.medicalSession?.current_context && !document.hidden && completed.value === null
        && readPendingApproval(user.medicalSession, id) !== null) await read()
  }
}
function dirty(): boolean { return Boolean(decision.value || reason.value || attested.value) }
function closeDialog(done: () => void): void {
  if (user.medicalWriting) return
  if (dirty()) { nextTarget = null; dialogDone = done; discardDialog.value = true; return }
  done()
}
async function discard(): Promise<void> {
  const destination = nextTarget; clearForm(); dialog.value = false; discardDialog.value = false
  dialogDone?.(); dialogDone = null; nextTarget = null
  if (destination !== null) { leaveApproved = true; await router.push(destination); leaveApproved = false }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
function leave(to: { fullPath: string }): boolean {
  if (leaveApproved || !dirty()) return true
  if (user.medicalWriting) return false
  nextTarget = to.fullPath; dialogDone = null; discardDialog.value = true; return false
}
onBeforeRouteLeave(leave)
onBeforeRouteUpdate(leave)
const routeActive = useMedicalRouteRead(['MedicalTemplateApprovalEntry', 'MedicalTemplateApproval'], clear)
window.addEventListener('beforeunload', beforeUnload)
watch(() => [user.medicalSession, target.value, routeActive.value], () => { if (routeActive.value) return read() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); clear() })
</script>

<style scoped>
.template-definition { white-space: pre-wrap; overflow-wrap: anywhere; margin: 16px; }
.template-center-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:24px}.template-center-heading h1{margin:0;font-size:24px}.template-review-tabs{margin:24px 0}.template-review-tabs h2{font-size:17px;margin:24px 0 16px}.template-review-tabs section{margin:16px 0}.medical-page{padding:28px;max-width:1480px;margin:auto}@media(max-width:650px){.medical-page{padding:16px}.template-center-heading{align-items:flex-start;flex-direction:column}}
</style>
