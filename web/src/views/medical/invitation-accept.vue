<template>
  <main class="app-container">
    <h1>接受企业邀请</h1>
    <router-link to="/projects">返回项目中心</router-link>
    <el-alert v-if="user.medicalError" :title="user.medicalError.message" type="error" :closable="false" role="alert" />
    <p v-if="loading" role="status">正在核查本人身份和企业成员</p>
    <p v-if="identity" data-invitation-actor>当前本人：<span>{{ identity.user.user_id }}</span></p>
    <p>请填写收到的企业邀请。接受后需要明确选择企业空间。</p>
    <el-button :disabled="loading || user.medicalWriting" @click="read">重新核查邀请身份</el-button>
    <p v-if="pending" data-pending-invitation>原邀请接受请求仍需核查；请求编号：{{ pending.key }}。请再次填写原邀请凭据。</p>
    <el-form ref="element" label-position="top" autocomplete="off" @submit.prevent="submit">
      <el-form-item label="企业标识" for="invitation-tenant" :error="errors.tenant"><el-input id="invitation-tenant" v-model="form.tenant" :disabled="busy || pending !== null || receipt !== null || selectionPending !== null" /></el-form-item>
      <el-form-item label="邀请标识" for="invitation-id" :error="errors.invitation"><el-input id="invitation-id" v-model="form.invitation" :disabled="busy || pending !== null || receipt !== null || selectionPending !== null" /></el-form-item>
      <el-form-item label="邀请原版本" for="invitation-revision" :error="errors.expected"><el-input id="invitation-revision" v-model="form.expected" :disabled="busy || pending !== null || receipt !== null || selectionPending !== null" /></el-form-item>
      <el-form-item label="邀请凭据" for="invitation-credential" :error="errors.credential"><el-input id="invitation-credential" v-model="form.credential" type="password" autocomplete="off" :disabled="busy || receipt !== null || selectionPending !== null" /></el-form-item>
      <el-button type="primary" native-type="submit" :disabled="busy || identity === null || receipt !== null || selectionPending !== null">{{ pending ? '核查原邀请接受结果' : '确认接受邀请' }}</el-button>
    </el-form>
    <el-descriptions v-if="receipt && member" title="邀请已接受，当前成员已核查" :column="1" border data-accepted-invitation>
      <el-descriptions-item label="成员标识">{{ member.member_id }}</el-descriptions-item>
      <el-descriptions-item label="企业标识">{{ member.tenant_id }}</el-descriptions-item>
      <el-descriptions-item label="成员版本">{{ member.revision }}</el-descriptions-item>
      <el-descriptions-item label="已授予动作数量">{{ receipt.grants.length }}</el-descriptions-item>
      <el-descriptions-item label="接受操作">{{ receipt.operation_id }}</el-descriptions-item>
    </el-descriptions>
    <el-button v-if="receipt && member && identity" :disabled="busy" @click="select(member.tenant_id)">选择接受邀请的企业</el-button>
    <p v-if="selectionPending" data-pending-invitation-selection>原企业选择仍需核查；请求编号：{{ selectionPending.key }}</p>
    <el-form v-if="identity" label-position="top" @submit.prevent="select(selectionTarget)">
      <el-form-item label="已获授权企业空间">
        <el-select v-model="selectionTarget" :disabled="busy || selectionPending !== null" placeholder="请选择有效成员所属企业">
          <el-option v-for="available in identity.options.memberships.filter(item => item.status === 'ACTIVE')" :key="available.tenant_id" :label="available.tenant_id" :value="available.tenant_id" />
        </el-select>
      </el-form-item>
      <el-button native-type="submit" :disabled="busy || !selectionTarget">{{ selectionPending ? '核查原企业选择结果' : '确认选择企业空间' }}</el-button>
    </el-form>
    <el-dialog v-model="discardDialog" title="处理未提交的邀请输入" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>邀请凭据只保存在当前页面。已经发送的请求继续保留原请求身份。</p>
      <el-button :disabled="user.medicalWriting" @click="discard">放弃输入并返回</el-button>
      <el-button :disabled="user.medicalWriting" @click="discardDialog = false">继续编辑</el-button>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { Member } from '@/api/medical/protocol'
import { acceptInvitation, confirmInvitation, confirmInvitationSelection, invitationReference, prepareInvitation, prepareInvitationSelection, readInvitationIdentity, readInvitationSelection, readPendingInvitation } from '@/api/medical/invitation-accept'
import type { AcceptanceReceipt, InvitationIdentity, PendingInvitation, PendingInvitationSelection } from '@/api/medical/invitation-accept'
import { readOptions, readSession, selectEnterprise } from '@/api/medical/session'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const identity = ref<InvitationIdentity | null>(null); const pending = ref<PendingInvitation | null>(null)
const selectionPending = ref<PendingInvitationSelection | null>(null); const selectionTarget = ref('')
const receipt = ref<AcceptanceReceipt | null>(null); const member = ref<Member | null>(null)
const loading = ref(false); const discardDialog = ref(false)
const element = ref<{ $el: HTMLElement } | null>(null)
const form = reactive({ tenant: '', invitation: '', expected: '', credential: '' })
const errors = reactive({ tenant: '', invitation: '', expected: '', credential: '' })
const busy = computed(() => loading.value || user.medicalWriting)
let generation = 0; let controller: AbortController | null = null; let destination: string | null = null; let leaveApproved = false
function clearSecret(): void {
  form.credential = ''
  const input = element.value?.$el.querySelector<HTMLInputElement>('#invitation-credential')
  if (input) input.value = ''
}
function clear(): void {
  generation++; controller?.abort(); controller = null; identity.value = null; receipt.value = null; member.value = null
  loading.value = false; clearSecret(); discardDialog.value = false
  Object.assign(form, { tenant: '', invitation: '', expected: '' })
  selectionTarget.value = ''
  element.value?.$el.querySelectorAll<HTMLInputElement>('input').forEach(input => { input.value = '' })
}
async function read(): Promise<void> {
  if (user.medicalWriting || document.hidden) return
  clear(); user.medicalError = null; pending.value = null; selectionPending.value = null
  const current = generation; controller = new AbortController(); loading.value = true
  try {
    const actual = await readInvitationIdentity(controller.signal)
    if (current !== generation || document.hidden) return
    identity.value = actual; pending.value = readPendingInvitation(actual.user.user_id)
    selectionPending.value = readInvitationSelection(actual.user.user_id)
    if (selectionPending.value) selectionTarget.value = selectionPending.value.tenant_id
    if (pending.value) Object.assign(form, { tenant: pending.value.tenant_id, invitation: pending.value.invitation_id, expected: pending.value.expected_revision })
  } finally { if (current === generation) loading.value = false }
}
async function submit(): Promise<void> {
  const currentIdentity = identity.value
  if (busy.value || currentIdentity === null || document.hidden || receipt.value !== null || selectionPending.value !== null) return
  errors.tenant = schemaIssues(invitationReference('Id'), form.tenant).length ? '请填写规范企业标识' : ''
  errors.invitation = schemaIssues(invitationReference('Id'), form.invitation).length ? '请填写规范邀请标识' : ''
  errors.expected = /^(0|[1-9][0-9]{0,19})$/.test(form.expected) && BigInt(form.expected) > 0n && BigInt(form.expected) < 18446744073709551615n ? '' : '请填写仍可增加的邀请原版本'
  errors.credential = schemaIssues(invitationReference('Credential'), form.credential).length ? '请填写完整邀请凭据' : ''
  if (Object.values(errors).some(Boolean)) {
    await nextTick(); element.value?.$el.querySelector<HTMLElement>('.el-form-item.is-error input')?.focus(); return
  }
  const current = generation; const path = route.fullPath; const credential = form.credential
  const original = prepareInvitation(currentIdentity, form.tenant, form.invitation, form.expected); pending.value = original
  user.medicalWriting = true
  try {
    const completed = await acceptInvitation(currentIdentity, original, credential)
    clearSecret()
    const actual = await readInvitationIdentity(new AbortController().signal)
    if (current !== generation || route.fullPath !== path || document.hidden) return
    const saved = confirmInvitation(actual, original, completed)
    identity.value = actual; pending.value = null; receipt.value = completed; member.value = saved
    Object.assign(form, { tenant: '', invitation: '', expected: '' })
    window.dispatchEvent(new Event('medical-context-invalidated'))
  } finally { user.medicalWriting = false; clearSecret() }
}
async function select(target: string): Promise<void> {
  const currentIdentity = identity.value
  if (busy.value || !currentIdentity || !target || document.hidden) return
  const current = generation; const path = route.fullPath
  user.medicalWriting = true
  try {
    const options = await readOptions(new AbortController().signal)
    if (current !== generation || route.fullPath !== path || document.hidden) return
    if (options.user_id !== currentIdentity.user.user_id) fail('CONTEXT_CHANGED', '企业选择前本人身份已经变化')
    const original = prepareInvitationSelection({ ...currentIdentity, options }, target); selectionPending.value = original
    const selected = await selectEnterprise(currentIdentity, options, target, original.key, original.expected_revision)
    const actual = await readSession(new AbortController().signal)
    if (current !== generation || route.fullPath !== path || document.hidden) return
    confirmInvitationSelection(actual, original, selected)
    user.invalidateMedical(); window.dispatchEvent(new Event('medical-context-invalidated'))
    await router.push('/projects')
  } finally { user.medicalWriting = false }
}
function dirty(): boolean { return receipt.value === null && Object.values(form).some(Boolean) }
async function discard(): Promise<void> {
  const next = destination; clear(); destination = null
  if (next !== null) { leaveApproved = true; await router.push(next); leaveApproved = false }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
function visibility(): void { if (document.hidden) clear() }
watch(() => user.medicalGeneration, clear, { flush: 'sync' })
onBeforeRouteLeave(to => {
  if (leaveApproved || !dirty()) return true
  if (user.medicalWriting) return false
  destination = to.fullPath; discardDialog.value = true; return false
})
onMounted(() => {
  if (Object.keys(route.query).length || route.hash) fail('SCHEMA_INVALID', '邀请接受页面不接收地址参数')
  window.addEventListener('beforeunload', beforeUnload); window.addEventListener('offline', clear)
  window.addEventListener('pagehide', clear); window.addEventListener('medical-failure', clear)
  document.addEventListener('visibilitychange', visibility)
  void read()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('offline', clear)
  window.removeEventListener('pagehide', clear); window.removeEventListener('medical-failure', clear)
  document.removeEventListener('visibilitychange', visibility); clear()
})
</script>
