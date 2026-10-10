<template>
  <main class="medical-page">
    <h1>{{ title }}</h1>
    <session-panel />
    <p v-if="!permitted">{{ create ? '创建预算需要当前企业的明确批准授权' : '追加预算批准需要当前账户的读取及批准授权' }}</p>
    <p v-if="loading" role="status">正在核查当前预算、批准版本与动作许可</p>
    <p v-if="writing" role="status">正在提交原预算请求，关闭页面不会取消已发送的操作</p>
    <section v-if="pending && user.medicalSession?.current_context" aria-label="原预算请求" data-pending-budget-write>
      <p>原预算请求待核查。操作定义：{{ pending.operation }}；请求编号：{{ pending.key }}；原上下文版本：{{ pending.context.context_revision }}</p>
      <p v-if="pending.expected_revision">原账户版本：{{ pending.expected_revision }}；原批准版本：{{ pending.based_on_budget_revision }}</p>
      <p>批准输入仅保存在当前页面。刷新以后需要重新输入完整原参数，原请求键继续保留。</p>
      <el-button :disabled="writing || checking" @click="lookup">查询原预算操作</el-button>
      <template v-if="lookupResult">
        <p v-if="lookupResult.lookup_status === 'NOT_FOUND'">尚未查询到原操作，不能据此判定请求从未发送。核查后可以使用完整原参数和原请求键提交。</p>
        <p v-else-if="lookupResult.lookup_status === 'EXPIRED'">原查询保存期限已过，需要人工核对。原请求继续保留，禁止重发。</p>
        <template v-else-if="lookupResult.operation">
          <p>原操作状态：{{ operationStatuses[lookupResult.operation.status] }}；操作编号：{{ lookupResult.operation.operation_id }}</p>
          <p v-if="lookupResult.operation.status === 'PROCESSING'">原操作仍在执行，请重新查询实际结果。</p>
          <p v-if="lookupResult.operation.status === 'FAILED'">原操作失败，原请求继续保留，需要核查问题处理结果。</p>
          <el-button v-if="lookupResult.operation.status === 'COMPLETED'" :disabled="writing || checking" @click="confirmLookup">核查原操作及当前预算</el-button>
        </template>
      </template>
    </section>
    <section v-if="receipt && user.medicalSession?.current_context" aria-label="预算操作回执" data-budget-write-receipt>
      <p>原操作已完成并经过状态重读。预算内容读取由当前独立许可决定。</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="操作编号">{{ receipt.operation_id }}</el-descriptions-item>
        <el-descriptions-item label="预算标识">{{ receipt.budget_id }}</el-descriptions-item>
        <el-descriptions-item label="账户版本">{{ receipt.revision }}</el-descriptions-item>
        <el-descriptions-item label="批准版本">{{ receipt.budget_revision }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ receipt.currency }}</el-descriptions-item>
        <el-descriptions-item label="批准内容摘要">{{ receipt.content_sha256 }}</el-descriptions-item>
      </el-descriptions>
      <p>本次批准保持原累计与预留，不表示运行已经重新启动或未知费用已经核对。</p>
    </section>
    <budget-account v-if="confirmedAccount && user.medicalSession?.current_context" :account="confirmedAccount" />
    <el-button v-if="permitted" :disabled="loading || writing || checking" @click="openDialog">填写预算请求</el-button>
    <router-link to="/agents/budgets">返回模型预算</router-link>
    <el-dialog v-model="dialog" class="medical-budget-write-dialog" :title="title" width="min(780px, 94vw)" :before-close="closeDialog" destroy-on-close>
      <el-form v-if="permitted" ref="formElement" data-budget-write-form label-position="top" @submit.prevent="submit(false)">
        <p v-if="!create">原账户版本：{{ pending?.expected_revision ?? account?.revision }}；原批准版本：{{ pending?.based_on_budget_revision ?? account?.budget_revision }}</p>
        <p>全部限制均为批准的总上限。零值禁止对应动作，缺少数值不能表示无限。追加批准不会释放原预留。</p>
        <el-form-item v-if="create" label="项目标识（留空表示本企业）" :error="errors.project" for="budget-write-project">
          <el-input id="budget-write-project" v-model="form.project" :disabled="writing || checking || pending !== null" />
        </el-form-item>
        <p v-else>预算范围：{{ account?.project_id ?? '本企业' }}</p>
        <el-form-item label="币种" :error="errors.currency" for="budget-write-currency">
          <el-input id="budget-write-currency" v-model="form.currency" maxlength="3" :disabled="writing || checking || !create || pending !== null" />
          <p>使用批准的三位币种标识；账户创建以后保持币种。</p>
        </el-form-item>
        <el-form-item v-for="field in limitFields" :key="field.key" :label="field.label" :error="errors[field.key]" :for="'budget-write-' + field.key">
          <el-input :id="'budget-write-' + field.key" v-model="form[field.key]" :inputmode="field.key === 'amount' ? 'decimal' : 'numeric'" :disabled="writing || checking" />
        </el-form-item>
        <el-form-item label="截止时间（UTC，以Z结尾）" :error="errors.deadline_at" for="budget-write-deadline">
          <el-input id="budget-write-deadline" v-model="form.deadline_at" :disabled="writing || checking" />
          <p>填写明确的UTC日期与时间，最多六位小数；服务端按数据库时间核查有效期。</p>
        </el-form-item>
        <el-form-item label="批准依据版本" :error="errors.basis" for="budget-write-basis">
          <el-input id="budget-write-basis" v-model="form.basis" :disabled="writing || checking" />
        </el-form-item>
        <el-form-item label="批准依据内容摘要（SHA-256）" :error="errors.hash" for="budget-write-hash">
          <el-input id="budget-write-hash" v-model="form.hash" :disabled="writing || checking" />
          <p>依据必须属于当前企业，项目依据须属于当前项目。服务端重新核查实际版本及内容摘要。</p>
        </el-form-item>
        <el-form-item label="批准原因" :error="errors.reason" for="budget-write-reason">
          <el-input id="budget-write-reason" v-model="form.reason" type="textarea" maxlength="2000" :disabled="writing || checking" />
        </el-form-item>
        <el-form-item :error="errors.attested" for="budget-write-attested">
          <el-checkbox id="budget-write-attested" v-model="form.attested" :disabled="writing || checking">本人已核查本次预算的实际依据并获准批准</el-checkbox>
        </el-form-item>
        <el-alert v-if="errors.schema" :title="errors.schema" type="error" :closable="false" />
        <el-button type="primary" native-type="submit" :disabled="loading || writing || checking || pending !== null && !mayReplay">{{ pending ? '使用完整原参数核查原请求' : '确认提交预算批准' }}</el-button>
        <el-button :disabled="writing || checking" @click="closeDialog()">返回</el-button>
      </el-form>
    </el-dialog>
    <el-dialog v-model="discardDialog" title="处理未保存的预算输入" width="min(540px, 94vw)" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>未提交输入只保存在当前页面。已发送请求继续保留原请求身份，关闭页面不会取消该请求。</p>
      <el-button :disabled="writing || checking || !permitted || pending !== null && !mayReplay" @click="submit(true)">保存后返回</el-button>
      <el-button :disabled="writing || checking" @click="discard">放弃输入并返回</el-button>
      <el-button :disabled="writing || checking" @click="discardDialog = false">继续编辑</el-button>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'
import { budgetActionAllowed, budgetBodyReference, budgetCreationAllowed, clearCompletedBudgetWrite, confirmBudgetOperation,
  prepareBudgetWrite, readBudget, readPendingBudgetWrite, submitBudgetWrite, verifyBudgetWrite } from '@/api/medical/budgets'
import type { BudgetAccount as Account, BudgetLimits, BudgetOperation, BudgetReceipt, BudgetWriteBody, PendingBudgetWrite } from '@/api/medical/budgets'
import type { MedicalSession } from '@/api/medical/protocol'
import { fail, schemaIssues } from '@/api/medical/protocol'
import { projectTextIssue } from '@/api/medical/project-write'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'
import useUserStore from '@/store/modules/user'
import BudgetAccount from './budget-account.vue'
import SessionPanel from './session-panel.vue'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const create = computed(() => route.meta.budgetWrite === 'CREATE')
const title = computed(() => create.value ? '创建预算' : '追加预算批准')
const operation = computed<BudgetOperation>(() => create.value ? 'BD-02' : 'BD-04')
const target = computed<string | null>(() => {
  if (create.value) return null
  if (typeof route.params.budgetId !== 'string') throw new Error('预算请求路由缺少实际目标')
  return route.params.budgetId
})
const account = ref<Account | null>(null); const confirmedAccount = ref<Account | null>(null)
const pending = ref<PendingBudgetWrite | null>(null); const receipt = ref<BudgetReceipt | null>(null)
const lookupResult = ref<OperationLookup | null>(null); const loading = ref(false); const checking = ref(false)
const dialog = ref(false); const discardDialog = ref(false); const writing = computed(() => user.medicalWriting)
const formElement = ref<{ $el: HTMLElement } | null>(null)
const emptyForm = () => ({ project: '', currency: '', amount: '', tokens: '', calls: '', research_rounds: '', revision_rounds: '', deadline_at: '', basis: '', hash: '', reason: '', attested: false })
const form = reactive(emptyForm())
const errors = reactive({ project: '', currency: '', amount: '', tokens: '', calls: '', research_rounds: '', revision_rounds: '', deadline_at: '', basis: '', hash: '', reason: '', attested: '', schema: '' })
const limitFields: { key: Exclude<keyof BudgetLimits, 'deadline_at'>; label: string }[] = [
  { key: 'amount', label: '批准金额总上限' }, { key: 'tokens', label: '批准Token总上限' }, { key: 'calls', label: '批准调用次数总上限' },
  { key: 'research_rounds', label: '批准研究轮次总上限' }, { key: 'revision_rounds', label: '批准修订轮次总上限' }
]
const operationStatuses = { PROCESSING: '执行中', COMPLETED: '已完成', FAILED: '失败' }
const permitted = computed(() => (pending.value === null || pending.value.operation === operation.value) && (create.value
  ? budgetCreationAllowed(user.medicalSession)
  : account.value !== null && ['budget.read', 'budget.approve'].every(action => account.value!.allowed_actions.includes(action)
    && budgetActionAllowed(user.medicalSession, action, account.value!))))
const mayReplay = computed(() => lookupResult.value?.lookup_status === 'NOT_FOUND')
let generation = 0; let controller: AbortController | null = null; let baseline = JSON.stringify(form)
let leaveApproved = false; let nextTarget: string | null = null; let dialogDone: (() => void) | null = null
function clearErrors(): void { for (const key of Object.keys(errors) as (keyof typeof errors)[]) errors[key] = '' }
function clearForm(): void {
  Object.assign(form, emptyForm()); clearErrors(); baseline = JSON.stringify(form)
  // 企业身份失效时同步清除原生输入，Vue更新之前不保留批准内容。
  formElement.value?.$el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => {
    input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false
  })
}
function dirty(): boolean { return JSON.stringify(form) !== baseline }
function openDialog(): void {
  if (!permitted.value || loading.value || writing.value || checking.value) return
  if (!dirty()) {
    clearForm()
    if (pending.value !== null) { form.project = pending.value.project_id ?? ''; form.currency = pending.value.currency }
    else if (account.value !== null) { Object.assign(form, account.value.approval.limits); form.currency = account.value.currency; form.project = account.value.project_id ?? '' }
    baseline = JSON.stringify(form)
  }
  dialog.value = true
}
function clear(): void {
  generation += 1; controller?.abort(); controller = null
  account.value = null; confirmedAccount.value = null; pending.value = null; receipt.value = null; lookupResult.value = null
  loading.value = false; checking.value = false; dialog.value = false; discardDialog.value = false; clearForm()
}
async function read(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || document.hidden || writing.value) return
  const current = generation; const id = target.value
  pending.value = readPendingBudgetWrite(session, id)
  if (id !== null && budgetActionAllowed(session, 'budget.read', { budget_id: id })) {
    controller = new AbortController(); loading.value = true
    try {
      const value = await readBudget(session, id, controller.signal)
      if (current !== generation || user.medicalSession !== session || target.value !== id) return
      account.value = value
    } finally { if (current === generation) loading.value = false }
  }
  if (current === generation) openDialog()
}
async function focusError(): Promise<void> {
  await nextTick()
  const element = formElement.value?.$el.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea,.el-form-item.is-error [tabindex]')
  element?.scrollIntoView({ block: 'nearest' }); element?.focus()
}
async function body(): Promise<BudgetWriteBody | null> {
  clearErrors()
  const reference = 'urn:medical:budgets:1.0.0#/$defs/'
  const project = create.value ? form.project || null : account.value?.project_id
  if (project === undefined) throw new Error('预算批准缺少已核查账户')
  if (project !== null && schemaIssues(reference + 'Id', project).length) errors.project = '请填写规范项目标识'
  errors.currency = schemaIssues(reference + 'Currency', form.currency).length ? '请填写批准的三位大写币种标识' : ''
  errors.amount = schemaIssues(reference + 'Money', form.amount).length ? '请填写非负十进制金额，最多16位整数和8位小数' : ''
  for (const key of ['tokens', 'calls', 'research_rounds', 'revision_rounds'] as const) {
    errors[key] = !/^(0|[1-9][0-9]{0,19})$/.test(form[key]) || BigInt(form[key]) > 18446744073709551615n ? '请填写完整U64非负整数字符串' : ''
  }
  errors.deadline_at = schemaIssues(reference + 'Time', form.deadline_at).length ? '请填写有效UTC日期与时间，以Z结尾并保留最多六位小数' : ''
  errors.basis = schemaIssues(reference + 'Id', form.basis).length ? '请填写实际批准依据的规范版本标识' : ''
  errors.hash = /^[0-9a-f]{64}$/.test(form.hash) ? '' : '请填写原件的64位SHA-256内容摘要'
  errors.reason = projectTextIssue(form.reason, 2000); errors.attested = form.attested ? '' : '请核查实际依据并确认本次批准授权'
  if (Object.values(errors).some(Boolean)) { await focusError(); return null }
  if (!budgetActionAllowed(user.medicalSession, 'budget.approve', { budget_id: target.value, project_id: project })) {
    errors.project = '当前主体没有此预算范围的批准许可'; errors.schema = errors.project; await focusError(); return null
  }
  const base = { schema_version: '1.0.0' as const, currency: form.currency,
    limits: { amount: form.amount, tokens: form.tokens, calls: form.calls, research_rounds: form.research_rounds, revision_rounds: form.revision_rounds, deadline_at: form.deadline_at },
    approval_basis: { version_id: form.basis, content_sha256: form.hash }, reason: form.reason, approval_attested: true as const }
  const value: BudgetWriteBody = create.value ? { ...base, project_id: project } : { ...base,
    expected_revision: pending.value?.expected_revision ?? account.value!.revision,
    based_on_budget_revision: pending.value?.based_on_budget_revision ?? account.value!.budget_revision }
  if (schemaIssues(budgetBodyReference(operation.value), value).length) { errors.schema = '预算请求未通过已发布字段规范，请核查全部必填字段及条件'; await focusError(); return null }
  return value
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session?.current_context || !original || writing.value || checking.value || document.hidden) return
  const current = generation; controller?.abort(); controller = new AbortController(); checking.value = true
  try {
    const value = await lookupOperation(session, original.operation, original.key, controller.signal)
    if (current === generation && user.medicalSession === session && pending.value?.key === original.key) lookupResult.value = value
  } finally { if (current === generation) checking.value = false }
}
async function actualAccount(session: MedicalSession, id: string, projectId: string | null): Promise<Account | null> {
  if (!session.current_context) fail('CONTEXT_CHANGED', '预算操作完成以后需要重新核查企业')
  if (!budgetActionAllowed(session, 'budget.read', { budget_id: id, project_id: projectId })) return null
  return readBudget(session, id, new AbortController().signal)
}
async function confirmLookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const result = lookupResult.value?.operation
  if (!session?.current_context || !original || !result?.resource_id || checking.value || writing.value || document.hidden) return
  const current = generation; checking.value = true
  try {
    const status = await confirmBudgetOperation(session, original, result.operation_id, result.resource_id, new AbortController().signal)
    if (current !== generation || user.medicalSession !== session || document.hidden) return
    const actual = await actualAccount(session, result.resource_id, original.project_id)
    if (actual !== null && (actual.project_id !== original.project_id || actual.currency !== original.currency)) fail('CONTENT_MISMATCH', '原预算操作与当前账户范围不一致')
    if (current !== generation || user.medicalSession !== session) return
    clearCompletedBudgetWrite(session, original, status)
    pending.value = null; lookupResult.value = null; confirmedAccount.value = actual; account.value = actual; clearForm(); dialog.value = false
  } finally { if (current === generation) checking.value = false }
}
async function submit(thenLeave: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || !permitted.value || writing.value || checking.value || document.hidden || pending.value !== null && !mayReplay.value) return
  const current = generation; const path = route.fullPath; const id = target.value; const definition = operation.value; const scope = account.value?.project_id
  const value = await body()
  if (value === null || current !== generation || user.medicalSession !== session || writing.value || checking.value || document.hidden) return
  user.medicalWriting = true
  try {
    const original = await prepareBudgetWrite(session, definition, id, value, scope)
    pending.value = original
    if (current !== generation || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '原预算请求尚未发送，请重新核查当前身份')
    const completed = await submitBudgetWrite(session, original, value)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) fail('CONTEXT_CHANGED', '原预算操作已返回，当前主体或企业需要重新核查')
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    const status = await confirmBudgetOperation(fresh, original, completed.operation_id, completed.budget_id, new AbortController().signal)
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    const actual = await actualAccount(fresh, completed.budget_id, original.project_id)
    if (actual !== null) verifyBudgetWrite(actual, original, value, completed)
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearCompletedBudgetWrite(fresh, original, status)
    pending.value = null; lookupResult.value = null; receipt.value = completed; confirmedAccount.value = actual; account.value = actual
    clearForm(); dialog.value = false; discardDialog.value = false; window.dispatchEvent(new Event('medical-context-invalidated'))
    if (thenLeave) await finishLeave()
  } finally {
    user.medicalWriting = false
    if (route.fullPath === path && user.medicalSession?.current_context && !document.hidden && receipt.value === null
        && readPendingBudgetWrite(user.medicalSession, id) !== null) await read()
  }
}
function closeDialog(done?: () => void): void {
  if (writing.value || checking.value) return
  if (dirty()) { dialogDone = done ?? null; nextTarget = null; discardDialog.value = true; return }
  dialog.value = false; done?.()
}
async function finishLeave(): Promise<void> {
  const destination = nextTarget
  dialog.value = false; discardDialog.value = false; dialogDone?.(); dialogDone = null; nextTarget = null
  if (destination !== null) { leaveApproved = true; await router.push(destination); leaveApproved = false }
}
async function discard(): Promise<void> { clearForm(); await finishLeave() }
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
function guardRoute(to: RouteLocationNormalized): boolean {
  if (leaveApproved) return true
  if (writing.value || checking.value) return false
  if (!dirty()) return true
  nextTarget = to.fullPath; dialogDone = null; discardDialog.value = true; return false
}
onBeforeRouteLeave(guardRoute)
onBeforeRouteUpdate(guardRoute)
window.addEventListener('beforeunload', beforeUnload)
watch(() => [user.medicalSession, route.fullPath], read, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); clear() })
</script>

<style scoped>
:deep(.medical-budget-write-dialog .el-dialog__body) { max-height: 66vh; overflow-y: auto; }
</style>
