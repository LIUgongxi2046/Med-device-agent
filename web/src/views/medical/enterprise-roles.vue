<template>
  <main class="medical-page">
    <h1>企业岗位</h1>
    <session-panel />
    <p v-if="!permitted">当前企业没有岗位管理许可，请重新核查成员身份与授权。</p>
    <p v-if="loading" role="status">正在读取当前授权范围内的岗位</p>
    <p v-if="user.medicalWriting" role="status">正在保存岗位决定，关闭页面不会取消已发送的请求</p>
    <el-button :disabled="user.medicalWriting || loading" @click="refresh">重新读取岗位</el-button>
    <template v-if="permitted">
      <el-button :disabled="user.medicalWriting || pending !== null" @click="openPublish(null)">发布岗位版本</el-button>
      <section v-if="pending" data-pending-role aria-label="原岗位请求">
        <p>原岗位请求待核查。请求编号：{{ pending.key }}；岗位标识：{{ pending.role_id }}；原上下文版本：{{ pending.context.context_revision }}</p>
        <p>刷新后需要重新填写完整原参数。系统保留请求身份和摘要，未保存的岗位内容只保存在当前页面。</p>
        <el-button :disabled="checking || user.medicalWriting" @click="lookup">查询原岗位操作</el-button>
        <template v-if="lookupResult">
          <p v-if="lookupResult.lookup_status === 'NOT_FOUND'">尚未查询到原操作，请核查后使用完整原参数与原请求键提交。</p>
          <p v-else-if="lookupResult.lookup_status === 'EXPIRED'">原查询保存期限已过，原请求继续保留，需要人工核查。</p>
          <template v-else-if="lookupResult.operation">
            <p>原操作状态：{{ operationLabels[lookupResult.operation.status] }}；操作编号：{{ lookupResult.operation.operation_id }}</p>
            <el-button v-if="lookupResult.operation.status === 'COMPLETED'" :disabled="checking || user.medicalWriting" @click="confirmLookup">核查原操作与当前岗位</el-button>
          </template>
          <el-button v-if="lookupResult.lookup_status === 'NOT_FOUND'" :disabled="checking || user.medicalWriting" @click="openOriginal">填写完整原岗位参数</el-button>
        </template>
      </section>
      <section v-if="receipt" data-role-receipt aria-label="岗位决定回执">
        <p>岗位决定已经保存，原操作和当前岗位已重新读取。</p>
        <p>操作编号：{{ receipt.operation_id }}；决定：{{ receipt.decision === 'PUBLISH' ? '发布版本' : '撤销版本' }}</p>
        <p>撤销授权数量：{{ receipt.revoked_grants.length }}；终止邀请数量：{{ receipt.terminated_invitations.length }}</p>
      </section>
      <section v-if="listing" data-role-list aria-label="当前发布岗位">
        <el-table :data="listing.roles" row-key="role_id">
          <el-table-column prop="name" label="岗位名称" min-width="200" />
          <el-table-column prop="role_id" label="岗位标识" min-width="300" />
          <el-table-column prop="version_id" label="当前发布版本" min-width="300" />
          <el-table-column label="状态"><template #default="scope">{{ statusLabels[scope.row.status] }}</template></el-table-column>
          <el-table-column label="操作" width="160"><template #default="scope">
            <el-button :disabled="loading || checking || user.medicalWriting" @click="select(scope.row.role_id, scope.row.version_id)">读取岗位版本</el-button>
          </template></el-table-column>
        </el-table>
        <el-empty v-if="listing.roles.length === 0" description="当前授权范围没有已发布岗位" />
        <el-button :disabled="loading || user.medicalWriting" @click="readPage(null)">读取首页</el-button>
        <el-button :disabled="!listing.next_after || loading || user.medicalWriting" @click="readPage(listing.next_after)">读取下一页</el-button>
      </section>
      <section v-if="selected" data-current-role aria-label="岗位固定版本">
        <h2>{{ selected.name }}</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="岗位标识">{{ selected.role_id }}</el-descriptions-item>
          <el-descriptions-item label="不可变版本">{{ selected.version_id }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabels[selected.status] }}</el-descriptions-item>
          <el-descriptions-item label="对象版本">{{ selected.revision }}</el-descriptions-item>
          <el-descriptions-item label="当前发布序号">{{ selected.head.revision }}</el-descriptions-item>
          <el-descriptions-item label="当前发布版本">{{ selected.head.current_version }}</el-descriptions-item>
          <el-descriptions-item label="批准人员">{{ selected.approved_by }}</el-descriptions-item>
          <el-descriptions-item label="批准时间（UTC）">{{ selected.approved_at }}</el-descriptions-item>
          <el-descriptions-item label="有效起始时间（UTC）">{{ selected.valid_from }}</el-descriptions-item>
          <el-descriptions-item label="有效截止时间（UTC）">{{ selected.valid_to ?? '已批准没有固定截止时间' }}</el-descriptions-item>
          <el-descriptions-item label="批准依据">{{ selected.approval_basis.version_id }}</el-descriptions-item>
          <el-descriptions-item label="依据摘要（SHA-256）">{{ selected.approval_basis.content_sha256 }}</el-descriptions-item>
          <el-descriptions-item label="岗位内容摘要（SHA-256）">{{ selected.content_sha256 }}</el-descriptions-item>
          <el-descriptions-item label="批准原因">{{ selected.reason }}</el-descriptions-item>
          <el-descriptions-item label="批准动作">{{ formatProjectJson(selected.definition.actions) }}</el-descriptions-item>
          <el-descriptions-item label="完整条件">{{ formatProjectJson(selected.definition.condition) }}</el-descriptions-item>
          <el-descriptions-item label="管理授权来源及版本">{{ selected.authority_grant }}；{{ selected.authority_revision }}</el-descriptions-item>
        </el-descriptions>
        <el-button :disabled="pending !== null || user.medicalWriting || checking" @click="openPublish(selected)">修订岗位发布</el-button>
        <el-button v-if="selected.status === 'ACTIVE'" :disabled="pending !== null || user.medicalWriting || checking" @click="openRevoke">撤销岗位版本</el-button>
        <el-form label-position="top" @submit.prevent="history">
          <el-form-item label="历史岗位版本标识" for="role-history" :error="historyError"><el-input id="role-history" v-model="historyVersion" :disabled="checking || user.medicalWriting" /></el-form-item>
          <el-button native-type="submit" :disabled="checking || user.medicalWriting || !historyVersion">读取历史岗位版本</el-button>
        </el-form>
      </section>
    </template>
    <router-link to="/projects">返回项目中心</router-link>
    <el-dialog v-model="dialog" class="medical-role-dialog" :title="mode === 'PUBLISH' ? '发布岗位版本' : '撤销岗位版本'" width="min(800px,94vw)" :before-close="closeDialog" destroy-on-close>
      <el-form v-if="permitted" ref="formElement" label-position="top" @submit.prevent="submit(false)">
        <template v-if="mode === 'PUBLISH'">
          <el-form-item label="岗位标识" for="role-id" :error="errors.role"><el-input id="role-id" v-model="form.role" :disabled="user.medicalWriting || originalTarget !== null" /></el-form-item>
          <el-form-item label="岗位名称" for="role-name" :error="errors.name"><el-input id="role-name" v-model="form.name" :disabled="user.medicalWriting" /></el-form-item>
          <el-form-item label="原发布序号" for="role-head" :error="errors.expected"><el-input id="role-head" v-model="form.expected" :disabled="user.medicalWriting || originalTarget !== null" />
            <p>首次发布填写0；已有岗位使用重新读取的当前发布序号。</p>
          </el-form-item>
          <el-form-item label="岗位动作（JSON 数组）" for="role-actions" :error="errors.actions"><el-input id="role-actions" v-model="form.actions" type="textarea" :disabled="user.medicalWriting" />
            <p>填写已批准的独立动作。明确填写空数组表示没有业务动作。</p>
          </el-form-item>
          <el-form-item label="岗位条件（JSON 对象）" for="role-condition" :error="errors.condition"><el-input id="role-condition" v-model="form.condition" type="textarea" :rows="7" :disabled="user.medicalWriting" />
            <p>完整填写身份方式、用途和委派范围。purposes、project_ids、resource_ids 中明确的 null 表示已批准全部范围，空数组表示没有相应许可。</p>
          </el-form-item>
          <el-form-item label="有效起始时间（UTC）" for="role-from" :error="errors.from"><el-input id="role-from" v-model="form.from" :disabled="user.medicalWriting" /></el-form-item>
          <el-form-item label="有效截止时间（UTC）" for="role-to" :error="errors.to"><el-input id="role-to" v-model="form.to" :disabled="user.medicalWriting" /></el-form-item>
          <el-checkbox v-model="form.unlimited" :disabled="user.medicalWriting">已批准没有固定截止时间</el-checkbox>
          <el-form-item label="批准依据版本" for="role-basis" :error="errors.basis"><el-input id="role-basis" v-model="form.basis" :disabled="user.medicalWriting" /></el-form-item>
          <el-form-item label="批准依据摘要（SHA-256）" for="role-hash" :error="errors.hash"><el-input id="role-hash" v-model="form.hash" :disabled="user.medicalWriting" /></el-form-item>
        </template>
        <template v-else><p>岗位标识：{{ originalTarget?.role_id }}；不可变版本：{{ originalTarget?.version_id }}；对象版本：{{ form.expected }}</p>
          <p>撤销本版本将终止引用该版本的待接受邀请并撤销关联授权。其他独立授权继续按各自条件生效。</p>
        </template>
        <el-form-item label="决定原因" for="role-reason" :error="errors.reason"><el-input id="role-reason" v-model="form.reason" type="textarea" :disabled="user.medicalWriting" /></el-form-item>
        <el-form-item :error="errors.attested"><el-checkbox v-model="form.attested" :disabled="user.medicalWriting">本人已核查本次岗位的批准依据并获准执行</el-checkbox></el-form-item>
        <el-alert v-if="errors.schema" :title="errors.schema" type="error" :closable="false" />
        <el-button native-type="submit" type="primary" :disabled="user.medicalWriting || checking || pending !== null && !mayReplay">确认岗位决定</el-button>
        <el-button :disabled="user.medicalWriting" @click="closeDialog()">返回</el-button>
      </el-form>
    </el-dialog>
    <el-dialog v-model="discardDialog" title="处理未保存的岗位输入" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>未提交的岗位输入保存在当前页面。已发送的请求继续保留原请求身份。</p>
      <el-button :disabled="user.medicalWriting || !permitted || pending !== null && !mayReplay" @click="submit(true)">保存后返回</el-button>
      <el-button :disabled="user.medicalWriting" @click="discard">放弃输入并返回</el-button>
      <el-button :disabled="user.medicalWriting" @click="discardDialog = false">继续编辑</el-button>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { formatProjectJson, projectJsonIssue, readBoundedJson } from '@/api/medical/project-json'
import { projectTextIssue } from '@/api/medical/project-write'
import { clearCompletedRole, confirmRoleOperation, listRoles, prepareRole, readPendingRole, readRole, roleAllowed, roleReference, submitRole } from '@/api/medical/enterprise-roles'
import type { EnterpriseRole, PendingRole, PublishRole, RevokeRole, RoleCondition, RoleListing, RoleReceipt } from '@/api/medical/enterprise-roles'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const permitted = computed(() => roleAllowed(user.medicalSession))
const listing = ref<RoleListing | null>(null); const selected = ref<EnterpriseRole | null>(null)
const pending = ref<PendingRole | null>(null); const receipt = ref<RoleReceipt | null>(null)
const lookupResult = ref<OperationLookup | null>(null); const historyVersion = ref(''); const historyError = ref('')
const dialog = ref(false); const discardDialog = ref(false); const mode = ref<'PUBLISH' | 'REVOKE'>('PUBLISH')
const loading = ref(false); const checking = ref(false); const originalTarget = ref<EnterpriseRole | null>(null)
const formElement = ref<{ $el: HTMLElement } | null>(null)
const form = reactive({ role: '', name: '', expected: '', actions: '', condition: '', from: '', to: '', unlimited: false, basis: '', hash: '', reason: '', attested: false })
const errors = reactive({ role: '', name: '', expected: '', actions: '', condition: '', from: '', to: '', basis: '', hash: '', reason: '', attested: '', schema: '' })
const statusLabels: Record<string, string> = { ACTIVE: '有效', REVOKED: '已撤销' }
const operationLabels = { PROCESSING: '执行中', COMPLETED: '已完成', FAILED: '失败，需要核查' }
const mayReplay = computed(() => lookupResult.value?.lookup_status === 'NOT_FOUND')
let generation = 0; let controller: AbortController | null = null; let leaveApproved = false
let destination: string | null = null; let dialogDone: (() => void) | null = null
function clearForm(): void {
  Object.assign(form, { role: '', name: '', expected: '', actions: '', condition: '', from: '', to: '', unlimited: false, basis: '', hash: '', reason: '', attested: false })
  for (const key of Object.keys(errors) as (keyof typeof errors)[]) errors[key] = ''
  originalTarget.value = null
  formElement.value?.$el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => {
    input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false
  })
}
function clear(): void {
  generation++; controller?.abort(); controller = null
  listing.value = null; selected.value = null; receipt.value = null; pending.value = null; lookupResult.value = null
  historyVersion.value = ''; historyError.value = ''; loading.value = false; checking.value = false; dialog.value = false; discardDialog.value = false; clearForm()
}
function dirty(): boolean { return Object.values(form).some(Boolean) }
async function readPage(after: string | null): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session || !permitted.value || user.medicalWriting || document.hidden) return
  const current = ++generation; controller?.abort(); controller = new AbortController(); loading.value = true
  listing.value = null; selected.value = null; receipt.value = null; historyVersion.value = ''
  try {
    const value = await listRoles(session, after, controller.signal)
    if (current === generation && user.medicalSession === session) listing.value = value
  } finally { if (current === generation) loading.value = false }
}
async function read(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession
  if (!session || !permitted.value || user.medicalWriting || document.hidden) return
  pending.value = readPendingRole(session); await readPage(null)
}
async function refresh(): Promise<void> { if (!user.medicalWriting && !loading.value) await user.readMedical() }
async function select(roleId: string, versionId: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session || !permitted.value || checking.value || user.medicalWriting || document.hidden) return
  const current = generation; checking.value = true; selected.value = null; receipt.value = null; historyVersion.value = ''
  controller?.abort(); controller = new AbortController()
  try {
    const value = await readRole(session, roleId, versionId, controller.signal)
    if (current === generation && user.medicalSession === session) selected.value = value
  } finally { if (current === generation) checking.value = false }
}
async function history(): Promise<void> {
  historyError.value = ''
  if (!selected.value) return
  if (schemaIssues(roleReference('Id'), historyVersion.value).length) {
    historyError.value = '请填写规范的历史岗位版本标识'
    await nextTick(); document.getElementById('role-history')?.focus(); return
  }
  await select(selected.value.role_id, historyVersion.value)
}
function openPublish(role: EnterpriseRole | null): void {
  if (!permitted.value || user.medicalWriting || pending.value !== null) return
  clearForm(); mode.value = 'PUBLISH'; originalTarget.value = role
  if (role !== null) Object.assign(form, { role: role.role_id, name: role.name, expected: role.head.revision,
    actions: formatProjectJson(role.definition.actions), condition: formatProjectJson(role.definition.condition), from: role.valid_from,
    to: role.valid_to ?? '', unlimited: role.valid_to === null, basis: role.approval_basis.version_id, hash: role.approval_basis.content_sha256 })
  dialog.value = true
}
function openRevoke(): void {
  if (!selected.value || selected.value.status !== 'ACTIVE' || !permitted.value || user.medicalWriting || pending.value !== null) return
  const role = selected.value; clearForm(); mode.value = 'REVOKE'; originalTarget.value = role; form.expected = role.revision; dialog.value = true
}
async function openOriginal(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session || !original || !mayReplay.value || user.medicalWriting || checking.value) return
  clearForm(); mode.value = original.operation === 'ROLE-03' ? 'PUBLISH' : 'REVOKE'; form.role = original.role_id; form.expected = original.expected_revision
  if (original.version_id !== null) {
    const current = generation; const value = await readRole(session, original.role_id, original.version_id, new AbortController().signal)
    if (current !== generation || user.medicalSession !== session) return
    originalTarget.value = value
  }
  dialog.value = true
}
async function focusError(): Promise<void> {
  await nextTick(); formElement.value?.$el.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea')?.focus()
}
async function body(): Promise<PublishRole | RevokeRole | null> {
  for (const key of Object.keys(errors) as (keyof typeof errors)[]) errors[key] = ''
  errors.reason = projectTextIssue(form.reason, 2000)
  errors.attested = form.attested ? '' : '请核查岗位依据并确认授权声明'
  errors.expected = /^(0|[1-9][0-9]{0,19})$/.test(form.expected) && BigInt(form.expected) < 18446744073709551615n ? '' : '请填写仍可增加的原版本'
  let value: PublishRole | RevokeRole
  const base = { schema_version: '1.0.0' as const, reason: form.reason, approval_attested: true as const }
  if (mode.value === 'PUBLISH') {
    errors.role = schemaIssues(roleReference('Id'), form.role).length ? '请填写规范岗位标识' : ''
    errors.name = projectTextIssue(form.name, 120)
    errors.basis = schemaIssues(roleReference('Id'), form.basis).length ? '请填写规范批准依据版本' : ''
    errors.hash = /^[0-9a-f]{64}$/.test(form.hash) ? '' : '请填写完整64位SHA-256摘要'
    errors.actions = projectJsonIssue(form.actions); errors.condition = projectJsonIssue(form.condition)
    errors.from = schemaIssues(roleReference('Time'), form.from).length ? '请填写以Z结尾的UTC时间' : ''
    errors.to = form.unlimited ? form.to ? '没有固定截止时间时请清除截止时间' : ''
      : schemaIssues(roleReference('Time'), form.to).length || Date.parse(form.to) <= Date.parse(form.from) ? '截止时间须为晚于起始时间的UTC时间' : ''
    if (Object.values(errors).some(Boolean)) { await focusError(); return null }
    const actions = readBoundedJson(form.actions); const condition = readBoundedJson(form.condition)
    const definition = { schema_version: '1.0.0' as const, role_id: form.role, actions, condition }
    if (schemaIssues('urn:medical:member-invitations:1.0.0#/$defs/RoleDefinition', definition).length) errors.actions = '动作须为规范且唯一的JSON数组'
    if (schemaIssues('urn:medical:permission-decisions:1.0.0#/$defs/Condition', condition).length) errors.condition = '岗位条件需要完整身份、用途和委派字段，数组内容须规范且唯一'
    if (Object.values(errors).some(Boolean)) { await focusError(); return null }
    value = { ...base, name: form.name, definition: { ...definition, actions: actions as string[], condition: condition as RoleCondition },
      valid_from: form.from, valid_to: form.unlimited ? null : form.to, approval_basis: { version_id: form.basis, content_sha256: form.hash }, expected_head_revision: form.expected }
  } else {
    if (!originalTarget.value) throw new Error('岗位撤销缺少已读取对象')
    if (form.expected === '0') errors.expected = '撤销需要已读取的岗位版本'
    value = { ...base, expected_revision: form.expected }
  }
  if (Object.values(errors).some(Boolean)) { await focusError(); return null }
  if (schemaIssues(roleReference(mode.value === 'PUBLISH' ? 'Publish' : 'Revoke'), value).length) {
    errors.schema = '岗位决定未通过已发布字段规范'; await focusError(); return null
  }
  return value
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session || !original || checking.value || user.medicalWriting || document.hidden) return
  const current = generation; checking.value = true; controller?.abort(); controller = new AbortController()
  try {
    const value = await lookupOperation(session, original.operation, original.key, controller.signal)
    if (current === generation && user.medicalSession === session) lookupResult.value = value
  } finally { if (current === generation) checking.value = false }
}
async function confirmLookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const operation = lookupResult.value?.operation
  if (!session || !original || !operation?.resource_id || operation.status !== 'COMPLETED' || checking.value || user.medicalWriting || document.hidden) return
  const current = generation; checking.value = true
  try {
    const verified = await confirmRoleOperation(session, original, operation.operation_id, operation.resource_id, new AbortController().signal)
    const actual = await readRole(session, original.role_id, operation.resource_id, new AbortController().signal)
    if (current !== generation || user.medicalSession !== session) return
    clearCompletedRole(session, original, verified); pending.value = null; lookupResult.value = null; selected.value = actual; clearForm(); dialog.value = false
  } finally { if (current === generation) checking.value = false }
}
async function submit(thenLeave: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session || !permitted.value || user.medicalWriting || document.hidden || pending.value !== null && !mayReplay.value) return
  const current = generation; const path = route.fullPath; const value = await body()
  if (value === null || current !== generation || user.medicalSession !== session) return
  const roleId = 'definition' in value ? value.definition.role_id : originalTarget.value!.role_id
  const versionId = 'definition' in value ? null : originalTarget.value!.version_id
  user.medicalWriting = true
  try {
    const original = await prepareRole(session, roleId, versionId, value); pending.value = original
    if (current !== generation || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '原岗位请求尚未发送，请重新核查当前身份')
    const completed = await submitRole(session, original, value)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) fail('CONTEXT_CHANGED', '岗位决定返回后当前主体或企业需要重新核查')
    const operation = await confirmRoleOperation(fresh, original, completed.operation_id, completed.role.version_id, new AbortController().signal)
    const actual = await readRole(fresh, original.role_id, completed.role.version_id, new AbortController().signal)
    if (actual.revision !== completed.role.revision || actual.status !== completed.role.status || actual.content_sha256 !== completed.role.content_sha256) fail('SCOPE_STALE', '岗位决定完成后对象已有变化，请核查当前版本')
    const updated = await listRoles(fresh, null, new AbortController().signal)
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearCompletedRole(fresh, original, operation); pending.value = null; lookupResult.value = null
    listing.value = updated; selected.value = actual; receipt.value = completed; clearForm(); dialog.value = false; discardDialog.value = false
    window.dispatchEvent(new Event('medical-context-invalidated'))
    if (thenLeave) await finishLeave()
  } finally {
    user.medicalWriting = false
    if (route.fullPath === path && permitted.value && user.medicalSession && !document.hidden && receipt.value === null
        && readPendingRole(user.medicalSession) !== null) await read()
  }
}
function closeDialog(done?: () => void): void {
  if (user.medicalWriting) return
  if (dirty()) { dialogDone = done ?? null; destination = null; discardDialog.value = true; return }
  dialog.value = false; done?.()
}
async function finishLeave(): Promise<void> {
  const next = destination; dialog.value = false; discardDialog.value = false; dialogDone?.(); dialogDone = null; destination = null
  if (next !== null) { leaveApproved = true; await router.push(next); leaveApproved = false }
}
async function discard(): Promise<void> { clearForm(); await finishLeave() }
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(to => {
  if (leaveApproved || !dirty()) return true
  destination = to.fullPath; dialogDone = null; discardDialog.value = true; return false
})
window.addEventListener('beforeunload', beforeUnload)
watch(() => user.medicalSession, read, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); clear() })
</script>

<style scoped>
:deep(.medical-role-dialog .el-dialog__body) {
  max-height: 66vh;
  overflow-y: auto;
}
</style>
