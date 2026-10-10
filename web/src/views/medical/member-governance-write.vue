<template>
  <section aria-label="成员管理操作">
    <nav v-if="permitted" aria-label="成员写入操作">
      <el-button v-if="memberAllowed" :disabled="busy || pending !== null" @click="open('INVITE')">邀请企业成员</el-button>
      <el-button :disabled="busy || pending !== null" @click="open('GRANT')">授予独立权限</el-button>
      <el-button v-if="selected && 'grant_id' in selected && selected.status === 'ACTIVE'" :disabled="busy || pending !== null" @click="open('REVOKE')">撤销当前授权</el-button>
      <el-button v-if="memberAllowed && selected && 'member_id' in selected && selected.status !== 'DISABLED'" :disabled="busy || pending !== null" @click="open('DISABLE_MEMBER')">停用当前成员</el-button>
    </nav>
    <section v-if="pending" data-pending-governance aria-label="原成员管理请求">
      <p>原请求：{{ titles[pending.decision] }}；主体：{{ pending.subject_id }}；原版本：{{ pending.expected_revision }}。</p>
      <p>请求结果需要核查。请保留完整原参数；页面刷新后需要重新填写。</p>
      <el-button :disabled="busy" @click="lookup">查询原成员管理操作</el-button>
      <p v-if="lookupResult" role="status">{{ lookupLabels[lookupResult.lookup_status] }}{{ lookupResult.operation ? '；' + operationLabels[lookupResult.operation.status] : '' }}</p>
      <el-button :disabled="busy || !mayReplay" @click="resume">填写原管理参数</el-button>
    </section>
    <section v-if="completed" data-completed-governance aria-label="已核查的成员管理结果">
      <h2>成员管理结果已经重新读取</h2>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="原操作标识">{{ completed.operation_id }}</el-descriptions-item>
        <el-descriptions-item label="当前对象标识">{{ completedTarget ? governanceId(completedTarget) : '' }}</el-descriptions-item>
        <el-descriptions-item label="当前对象版本">{{ completedTarget?.revision }}</el-descriptions-item>
        <el-descriptions-item label="当前对象状态">{{ completedTarget?.status }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="credential">
        <el-form label-position="top"><el-form-item label="本次邀请凭据" for="governance-credential">
          <el-input id="governance-credential" :model-value="credential" readonly autocomplete="off" />
        </el-form-item></el-form>
        <p>邀请凭据仅保存在当前页面内存，请通过获准渠道交付给对应账户。页面刷新、隐藏或离开后清除。</p>
        <el-button @click="credential = ''">清除邀请凭据</el-button>
      </template>
    </section>
    <el-dialog v-model="dialog" :title="titles[mode]" class="medical-governance-dialog" width="min(780px, 94vw)" :before-close="closeDialog" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-form ref="formElement" label-position="top" @submit.prevent="submit(false)">
        <template v-if="mode === 'INVITE'">
          <el-form-item label="完整认证绑定（JSON）" for="governance-binding" :error="errors.binding"><el-input id="governance-binding" v-model="form.binding" type="textarea" :rows="4" :disabled="busy" /></el-form-item>
          <el-form-item label="固定岗位引用（JSON）" for="governance-roles" :error="errors.roles"><el-input id="governance-roles" v-model="form.roles" type="textarea" :rows="4" :disabled="busy" /></el-form-item>
          <el-form-item label="邀请截止时间（UTC）" for="governance-expires" :error="errors.expires"><el-input id="governance-expires" v-model="form.expires" :disabled="busy" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="授权主体标识" for="governance-write-subject" :error="errors.subject"><el-input id="governance-write-subject" v-model="form.subject" :readonly="mode !== 'GRANT'" :disabled="busy" /></el-form-item>
          <el-form-item label="原对象版本" for="governance-expected"><el-input id="governance-expected" v-model="form.expected" readonly :disabled="busy" /></el-form-item>
          <el-form-item v-if="mode === 'REVOKE'" label="原授权标识" for="governance-grant"><el-input id="governance-grant" v-model="form.grant" readonly :disabled="busy" /></el-form-item>
          <el-form-item v-if="mode !== 'DISABLE_MEMBER'" label="独立动作" for="governance-action" :error="errors.action"><el-input id="governance-action" v-model="form.action" :readonly="mode === 'REVOKE' && !restoring" :disabled="busy" /></el-form-item>
          <template v-if="mode === 'GRANT'">
            <el-form-item label="完整授权条件（JSON）" for="governance-condition" :error="errors.condition"><el-input id="governance-condition" v-model="form.condition" type="textarea" :rows="6" :disabled="busy" /></el-form-item>
            <el-checkbox v-model="form.serverFrom" :disabled="busy">使用服务端当前时间作为有效起始时间</el-checkbox>
            <el-form-item v-if="!form.serverFrom" label="授权起始时间（UTC）" for="governance-from" :error="errors.from"><el-input id="governance-from" v-model="form.from" :disabled="busy" /></el-form-item>
            <el-checkbox v-model="form.unlimited" :disabled="busy">明确不设置授权截止时间</el-checkbox>
            <el-form-item v-if="!form.unlimited" label="授权截止时间（UTC）" for="governance-to" :error="errors.to"><el-input id="governance-to" v-model="form.to" :disabled="busy" /></el-form-item>
          </template>
          <el-form-item label="决定原因" for="governance-reason" :error="errors.reason"><el-input id="governance-reason" v-model="form.reason" type="textarea" :rows="3" :disabled="busy" /></el-form-item>
        </template>
        <el-form-item label="完整管理范围（JSON）" for="governance-scope" :error="errors.scope"><el-input id="governance-scope" v-model="form.scope" type="textarea" :rows="4" :readonly="(mode === 'REVOKE' || mode === 'DISABLE_MEMBER') && !restoring" :disabled="busy" /></el-form-item>
        <el-form-item label="决定依据版本" for="governance-basis" :error="errors.basis"><el-input id="governance-basis" v-model="form.basis" :disabled="busy" /></el-form-item>
        <el-form-item label="决定依据摘要" for="governance-hash" :error="errors.hash"><el-input id="governance-hash" v-model="form.hash" :disabled="busy" /></el-form-item>
        <p v-if="errors.schema" role="alert">{{ errors.schema }}</p>
        <p v-if="pending">保存将使用完整原参数及同一原请求键，当前状态仍由服务器核查。</p>
        <el-button native-type="submit" :disabled="busy || pending !== null && !mayReplay">保存成员管理决定</el-button>
        <el-button :disabled="busy" @click="closeDialog()">关闭编辑</el-button>
      </el-form>
    </el-dialog>
    <el-dialog v-model="discardDialog" title="处理未保存的成员管理输入" width="min(560px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>未提交的完整输入保存在当前页面。已经发送的请求继续保留原请求身份。</p>
      <el-button :disabled="busy || !permitted || pending !== null && !mayReplay" @click="submit(true)">保存后返回</el-button>
      <el-button :disabled="busy" @click="discard">放弃输入并返回</el-button>
      <el-button :disabled="busy" @click="discardDialog = false">继续编辑</el-button>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { formatProjectJson, projectJsonIssue, readBoundedJson } from '@/api/medical/project-json'
import { governanceAllowed, governanceId } from '@/api/medical/member-governance'
import type { GovernanceItem, GovernanceKind, GovernancePage, GovernanceScope } from '@/api/medical/member-governance'
import { checkGovernanceBody, clearCompletedGovernance, confirmGovernance, invitationReference, permissionReference, prepareGovernance, readPendingGovernance, submitGovernance } from '@/api/medical/member-governance-write'
import type { DecidePermission, GovernanceBody, GovernanceDecision, GovernanceReceipt, InviteMember, PendingGovernance } from '@/api/medical/member-governance-write'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'

const props = defineProps<{ selected: GovernanceItem | null }>()
const emit = defineEmits<{ verified: [value: { kind: GovernanceKind; page: GovernancePage; target: GovernanceItem }] }>()
const user = useUserStore(); const route = useRoute(); const router = useRouter()
const permitted = computed(() => governanceAllowed(user.medicalSession)); const memberAllowed = computed(() => governanceAllowed(user.medicalSession, 'Members'))
const busy = computed(() => user.medicalWriting || user.medicalLoading || checking.value)
const mode = ref<GovernanceDecision>('INVITE'); const pending = ref<PendingGovernance | null>(null)
const completed = ref<GovernanceReceipt | null>(null); const completedTarget = ref<GovernanceItem | null>(null); const credential = ref('')
const lookupResult = ref<OperationLookup | null>(null); const checking = ref(false); const restoring = ref(false)
const dialog = ref(false); const discardDialog = ref(false); const formElement = ref<{ $el: HTMLElement } | null>(null)
const titles = { INVITE: '邀请企业成员', GRANT: '授予独立权限', REVOKE: '撤销当前授权', DISABLE_MEMBER: '停用当前成员' }
const lookupLabels = { FOUND: '找到原操作', NOT_FOUND: '尚未找到原操作，保持原请求键', EXPIRED: '原请求已经过期，禁止重新提交' }
const operationLabels = { PROCESSING: '执行中，请继续查询', COMPLETED: '操作完成，仍需原参数及目标核查', FAILED: '操作失败，需要核查' }
const mayReplay = computed(() => lookupResult.value?.lookup_status === 'NOT_FOUND' || lookupResult.value?.lookup_status === 'FOUND' && lookupResult.value.operation?.status === 'COMPLETED')
const empty = () => ({ binding: '', roles: '', expires: '', subject: '', expected: '', grant: '', action: '', condition: '', from: '', to: '',
  serverFrom: false, unlimited: false, reason: '', scope: '', basis: '', hash: '' })
const form = reactive(empty()); const errors = reactive({ binding: '', roles: '', expires: '', subject: '', action: '', condition: '', from: '', to: '', reason: '', scope: '', basis: '', hash: '', schema: '' })
let epoch = 0; let controller: AbortController | null = null; let approvedLeave = false
let destination: string | null = null; let dialogDone: (() => void) | null = null
function clearForm(): void {
  Object.assign(form, empty()); for (const key of Object.keys(errors) as (keyof typeof errors)[]) errors[key] = ''
  formElement.value?.$el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => {
    input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false
  })
}
function clearContent(): void {
  clearForm(); credential.value = ''; completed.value = null; completedTarget.value = null
  pending.value = null; lookupResult.value = null; dialog.value = false; discardDialog.value = false; restoring.value = false
}
function invalidate(): void { epoch++; controller?.abort(); controller = null; checking.value = false; clearContent() }
function dirty(): boolean { return dialog.value && Object.values(form).some(Boolean) }
function open(decision: GovernanceDecision): void {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || busy.value || pending.value || !permitted.value || document.hidden) return
  if ((decision === 'INVITE' || decision === 'DISABLE_MEMBER') && !memberAllowed.value) return
  clearForm(); mode.value = decision; restoring.value = false; completed.value = null; credential.value = ''; completedTarget.value = null
  if (decision === 'GRANT') form.expected = '0'
  if (decision === 'REVOKE') {
    const target = props.selected
    if (!target || !('grant_id' in target) || target.status !== 'ACTIVE') return
    Object.assign(form, { subject: target.subject_id, expected: target.revision, grant: target.grant_id, action: target.action, scope: formatProjectJson(target.scope) })
  }
  if (decision === 'DISABLE_MEMBER') {
    const target = props.selected
    if (!target || !('member_id' in target) || target.status === 'DISABLED') return
    Object.assign(form, { subject: target.user_id, expected: target.revision, scope: formatProjectJson({ tenant_id: session.current_context.tenant_id,
      project_id: null, resource_kind: 'MEMBER', resource_id: target.member_id }) })
  }
  dialog.value = true
}
function resume(): void {
  if (!pending.value || !mayReplay.value || busy.value) return
  clearForm(); restoring.value = true; mode.value = pending.value.decision
  Object.assign(form, { subject: pending.value.subject_id, expected: pending.value.expected_revision, grant: pending.value.decision === 'REVOKE' ? pending.value.target_id : '' })
  dialog.value = true
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session || !original || busy.value || document.hidden) return
  const previous = epoch; checking.value = true; controller?.abort(); controller = new AbortController()
  try {
    const value = await lookupOperation(session, original.operation, original.key, controller.signal)
    if (previous === epoch && user.medicalSession === session && !document.hidden) lookupResult.value = value
  } finally { if (previous === epoch) checking.value = false }
}
async function body(): Promise<GovernanceBody | null> {
  for (const key of Object.keys(errors) as (keyof typeof errors)[]) errors[key] = ''
  const issue = (reference: string, value: unknown, message: string): string => schemaIssues(reference, value).length ? message : ''
  function json(field: 'binding' | 'roles' | 'scope' | 'condition', reference: string): unknown {
    errors[field] = projectJsonIssue(form[field]); if (errors[field]) return null
    const value = readBoundedJson(form[field]); errors[field] = issue(reference, value, '请填写符合完整 Schema 的 JSON'); return value
  }
  const scope = json('scope', permissionReference('Scope')) as GovernanceScope
  errors.basis = issue(permissionReference('Id'), form.basis, '请填写规范决定依据版本')
  errors.hash = /^[0-9a-f]{64}$/.test(form.hash) ? '' : '请填写完整 SHA-256 决定依据摘要'
  const basis = { version_id: form.basis, content_sha256: form.hash }
  let value: GovernanceBody
  if (mode.value === 'INVITE') {
    const binding = json('binding', 'urn:medical:identity-binding:1.0.0') as InviteMember['identity_binding']
    const roles = json('roles', invitationReference('Roles')) as InviteMember['roles']
    errors.expires = issue(permissionReference('Time'), form.expires, '请填写完整 UTC 邀请截止时间')
    value = { schema_version: '1.0.0', identity_binding: binding, roles, scope, expires_at: form.expires, basis }
  } else {
    errors.subject = issue(permissionReference('Id'), form.subject, '请填写规范授权主体标识')
    errors.reason = form.reason.trim() && form.reason.length <= 2000 ? '' : '请填写完整决定原因，最多2000字符'
    if (mode.value !== 'DISABLE_MEMBER') errors.action = issue(permissionReference('Action'), form.action, '请填写规范独立动作')
    const condition = mode.value === 'GRANT' ? json('condition', permissionReference('Condition')) as DecidePermission['condition'] : null
    if (mode.value === 'GRANT') {
      if (!form.serverFrom) errors.from = issue(permissionReference('Time'), form.from, '请填写完整 UTC 授权起始时间')
      if (!form.unlimited) errors.to = issue(permissionReference('Time'), form.to, '请填写完整 UTC 授权截止时间')
    }
    value = { schema_version: '1.0.0', decision: mode.value, subject_id: form.subject, scope, action: mode.value === 'DISABLE_MEMBER' ? null : form.action,
      grant_id: mode.value === 'REVOKE' ? form.grant : null, condition, valid_from: mode.value === 'GRANT' && !form.serverFrom ? form.from : null,
      valid_to: mode.value === 'GRANT' && !form.unlimited ? form.to : null, basis, expected_revision: form.expected, reason: form.reason }
  }
  if (Object.values(errors).some(Boolean)) { await nextTick(); formElement.value?.$el.querySelector<HTMLElement>('.is-error input,.is-error textarea')?.focus(); return null }
  if (schemaIssues(mode.value === 'INVITE' ? invitationReference('Create') : permissionReference('Request'), value).length) {
    errors.schema = '管理决定的完整字段及对象版本未通过 Schema 校验'; return null
  }
  checkGovernanceBody(value); return value
}
async function submit(thenLeave: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session || !permitted.value || busy.value || document.hidden || pending.value !== null && !mayReplay.value) return
  const previous = epoch; const path = route.fullPath; const value = await body()
  if (!value || previous !== epoch || user.medicalSession !== session) return
  user.medicalWriting = true
  try {
    const original = await prepareGovernance(session, value); pending.value = original
    if (previous !== epoch || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '管理请求尚未发送，请核查当前身份')
    const receipt = await submitGovernance(session, original, value)
    if (previous !== epoch || route.fullPath !== path || document.hidden) return
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) fail('CONTEXT_CHANGED', '管理保存后当前主体或企业需要核查')
    const verified = await confirmGovernance(fresh, original, value, receipt, new AbortController().signal)
    if (previous !== epoch || route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearCompletedGovernance(fresh, original); pending.value = null; lookupResult.value = null; clearForm(); dialog.value = false
    completedTarget.value = verified.target; completed.value = receipt
    credential.value = 'credential' in receipt ? receipt.credential : ''; emit('verified', verified)
    if (thenLeave) await finishLeave()
  } finally {
    user.medicalWriting = false
    if (route.fullPath === path && user.medicalSession && permitted.value && !document.hidden && completed.value === null) pending.value = readPendingGovernance(user.medicalSession)
  }
}
function closeDialog(done?: () => void): void {
  if (busy.value) return
  if (dirty()) { dialogDone = done ?? null; destination = null; discardDialog.value = true; return }
  dialog.value = false; done?.()
}
async function finishLeave(): Promise<void> {
  const next = destination; destination = null; dialog.value = false; discardDialog.value = false; dialogDone?.(); dialogDone = null
  if (next !== null) { approvedLeave = true; await router.push(next); approvedLeave = false }
}
async function discard(): Promise<void> { clearForm(); await finishLeave() }
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
function visibility(): void { if (document.hidden) invalidate() }
onBeforeRouteLeave(to => { if (approvedLeave || !dirty()) return true; destination = to.fullPath; dialogDone = null; discardDialog.value = true; return false })
watch(() => user.medicalSession, () => {
  if (user.medicalWriting) clearContent(); else invalidate()
  if (user.medicalSession && permitted.value && !document.hidden) pending.value = readPendingGovernance(user.medicalSession)
}, { immediate: true, flush: 'sync' })
window.addEventListener('medical-failure', invalidate); window.addEventListener('offline', invalidate)
window.addEventListener('pagehide', invalidate); window.addEventListener('beforeunload', beforeUnload); document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => {
  invalidate(); window.removeEventListener('medical-failure', invalidate); window.removeEventListener('offline', invalidate)
  window.removeEventListener('pagehide', invalidate); window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('visibilitychange', visibility)
})
</script>

<style scoped>
:deep(.medical-governance-dialog .el-dialog__body) { max-height: 66vh; overflow-y: auto; }
</style>
