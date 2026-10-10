<template>
  <main class="medical-page">
    <h1>企业登记申请</h1>
    <session-panel />
    <router-link to="/projects">返回项目中心</router-link>
    <p v-if="!permitted">企业登记需要当前平台管理员身份</p>
    <template v-if="permitted">
      <router-link to="/enterprises/registration-reviews">审查企业申请</router-link>
      <el-alert v-if="pending" title="原企业登记请求待核查" type="warning" :closable="false">
        <p>原请求编号：{{ pending.key }}。重新填写相同完整参数，使用原请求确认结果。</p>
        <p>请求身份继续保留。离开页面不会取消已经发送的操作。</p>
        <el-button :disabled="disabled" @click="openAbandon">明确核查并清除原请求身份</el-button>
      </el-alert>
      <el-skeleton v-if="loading" :rows="3" animated />
      <el-form v-if="ready" ref="formElement" label-position="top" data-enterprise-registration-form @submit.prevent="submit">
        <p>企业名称、登记信息及授权依据由申请人员填写。登记申请保持原始内容，审查状态通过审查入口读取。</p>
        <el-form-item label="企业法定名称" :error="errors.legal_name"><el-input v-model="form.legal_name" :disabled="disabled" /></el-form-item>
        <el-form-item label="登记地区" :error="errors.jurisdiction"><el-input v-model="form.jurisdiction" :disabled="disabled" /></el-form-item>
        <el-form-item label="企业登记标识" :error="errors.legal_identifier"><el-input v-model="form.legal_identifier" :disabled="disabled" /></el-form-item>
        <el-form-item label="首位管理员" :error="errors.first_admin_user_id">
          <el-select v-model="form.first_admin_user_id" filterable placeholder="请选择实际有效账户" :disabled="disabled">
            <el-option v-for="account in accounts" :key="account.user_id" :value="account.user_id" :label="account.display_name + ' · ' + account.username + ' · ' + account.user_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权依据" :error="errors.authorization_basis"><el-input v-model="form.authorization_basis" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" :disabled="disabled" /></el-form-item>
        <el-form-item label="授权确认" :error="errors.authorization_attested"><el-checkbox v-model="form.authorization_attested" :disabled="disabled">确认已取得企业登记及管理员指定的授权</el-checkbox></el-form-item>
        <el-button native-type="submit" type="primary" :disabled="disabled">{{ pending ? '使用原请求保存企业登记申请' : '保存企业登记申请' }}</el-button>
      </el-form>
      <section v-if="ready" aria-label="本人提交的企业申请">
        <h2>本人提交的申请</h2>
        <el-button :disabled="disabled" @click="reload">重新读取本人申请</el-button>
        <el-table v-if="items.length" :data="items" data-enterprise-registration-table>
          <el-table-column prop="application.legal_name" label="企业法定名称" />
          <el-table-column prop="created_at" label="登记时间" />
          <el-table-column label="登记时状态"><template #default>待审查</template></el-table-column>
          <el-table-column label="操作"><template #default="scope"><el-button :disabled="disabled" @click="openDetail(scope.row.registration_id)">读取申请详情</el-button></template></el-table-column>
        </el-table>
        <el-empty v-else description="尚无本人提交的申请" />
        <el-alert v-if="cursor" title="当前仅显示已读取的申请，后续记录需要继续读取" type="info" :closable="false" />
        <el-button v-if="cursor" :disabled="disabled" @click="nextPage">继续读取申请</el-button>
      </section>
      <section v-if="detail && ready" data-enterprise-registration-detail aria-label="企业登记原申请详情">
        <h2>原申请详情</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请编号">{{ detail.registration_id }}</el-descriptions-item>
          <el-descriptions-item label="操作编号">{{ detail.operation_id }}</el-descriptions-item>
          <el-descriptions-item label="企业法定名称">{{ detail.application.legal_name }}</el-descriptions-item>
          <el-descriptions-item label="登记地区">{{ detail.application.jurisdiction }}</el-descriptions-item>
          <el-descriptions-item label="企业登记标识">{{ detail.application.legal_identifier }}</el-descriptions-item>
          <el-descriptions-item label="首位管理员身份">{{ detail.application.first_admin_user_id }}</el-descriptions-item>
          <el-descriptions-item label="首位管理员账户版本">{{ detail.first_admin_revision }}</el-descriptions-item>
          <el-descriptions-item label="授权依据">{{ detail.application.authorization_basis }}</el-descriptions-item>
          <el-descriptions-item label="申请人员">{{ detail.submitted_by }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ detail.created_at }}</el-descriptions-item>
          <el-descriptions-item label="固定申请摘要">{{ detail.content_sha256 }}</el-descriptions-item>
        </el-descriptions>
        <el-collapse><el-collapse-item title="完整原申请及管理员绑定" name="detail"><pre>{{ JSON.stringify(detail, null, 2) }}</pre></el-collapse-item></el-collapse>
      </section>
    </template>
    <el-dialog v-model="discardDialog" title="离开企业登记" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p>未保存的输入会清除。已经发送的原请求身份继续保留，离开页面不会取消服务器操作。</p>
      <template #footer><el-button @click="discardDialog = false">继续填写</el-button><el-button :disabled="user.medicalWriting" @click="discard">放弃未保存输入并离开</el-button></template>
    </el-dialog>
    <el-dialog v-model="abandonDialog" title="核查原企业申请" :close-on-click-modal="false">
      <p>清除浏览器请求身份不会撤回或删除服务器申请。相同企业登记标识仍受服务器唯一约束限制。</p>
      <p v-if="pending">原请求编号：{{ pending.key }}</p>
      <el-checkbox v-model="abandonAttested" :disabled="checking">已经核查原申请及其结果，明确清除本地请求身份</el-checkbox>
      <template #footer><el-button :disabled="checking" @click="abandonDialog = false">保留原请求</el-button><el-button :disabled="!abandonAttested || disabled" @click="abandon">确认清除原请求身份</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance } from 'element-plus'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { fail } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { abandonRegistration, confirmRegistration, listRegistrations, prepareRegistration, readPendingRegistration, readRegistration, readRegistrationAccounts,
  registrationAllowed, registrationTextIssue, submitRegistration } from '@/api/medical/enterprise-registration'
import type { PendingRegistration, RegistrationAccount, RegistrationBody, RegistrationDetail } from '@/api/medical/enterprise-registration'

const user = useUserStore(); const router = useRouter(); const formElement = ref<FormInstance>()
const form = reactive({ legal_name: '', jurisdiction: '', legal_identifier: '', first_admin_user_id: '', authorization_basis: '', authorization_attested: false })
const errors = reactive<Record<string, string>>({}); const accounts = ref<RegistrationAccount[]>([])
const items = ref<RegistrationDetail[]>([]); const detail = ref<RegistrationDetail | null>(null); const cursor = ref<string | null>(null)
const pending = ref<PendingRegistration | null>(null); const loading = ref(false); const ready = ref(false); const checking = ref(false)
const permitted = computed(() => registrationAllowed(user.medicalSession))
const disabled = computed(() => !permitted.value || !ready.value || loading.value || checking.value || user.medicalWriting)
const discardDialog = ref(false)
const abandonDialog = ref(false); const abandonAttested = ref(false)
let controller = new AbortController(); let generation = 0; let nextTarget: string | null = null; let leaveApproved = false
function clearForm(): void {
  form.legal_name = ''; form.jurisdiction = ''; form.legal_identifier = ''; form.first_admin_user_id = ''; form.authorization_basis = ''; form.authorization_attested = false
  for (const key of Object.keys(errors)) delete errors[key]
}
function active(session: MedicalSession, original: number): boolean { return original === generation && user.medicalSession === session && !document.hidden }
async function load(): Promise<void> {
  controller.abort(); controller = new AbortController(); const signal = controller.signal; const original = ++generation
  clearForm(); accounts.value = []; items.value = []; detail.value = null; cursor.value = null; pending.value = null
  loading.value = false; checking.value = false; ready.value = false; discardDialog.value = false; abandonDialog.value = false; abandonAttested.value = false
  const session: MedicalSession | null = user.medicalSession
  if (!session || !registrationAllowed(session) || document.hidden) return
  pending.value = readPendingRegistration(session); loading.value = true
  try {
    const candidates = await readRegistrationAccounts(session, signal)
    const page = await listRegistrations(session, null, signal)
    if (!active(session, original)) return
    accounts.value = candidates; items.value = page.items; cursor.value = page.next_cursor; ready.value = true
  } finally { if (original === generation) loading.value = false }
}
async function page(append: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation
  if (!session || disabled.value || document.hidden || append && cursor.value === null) return
  checking.value = true
  try {
    const result = await listRegistrations(session, append ? cursor.value : null, controller.signal)
    if (!active(session, original)) return
    if (append && result.items.some(item => items.value.some(existing => existing.registration_id === item.registration_id))) fail('CONTENT_MISMATCH', '申请列表重复，需要重新读取')
    items.value = append ? [...items.value, ...result.items] : result.items; cursor.value = result.next_cursor
  } finally { if (original === generation) checking.value = false }
}
async function reload(): Promise<void> { await page(false) }
async function nextPage(): Promise<void> { await page(true) }
async function openDetail(id: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation
  if (!session || disabled.value || document.hidden) return
  detail.value = null; checking.value = true
  try { const value = await readRegistration(session, id, controller.signal); if (active(session, original)) detail.value = value }
  finally { if (original === generation) checking.value = false }
}
async function body(): Promise<RegistrationBody | null> {
  for (const [key, title, maximum] of [['legal_name', '企业法定名称', 512], ['jurisdiction', '登记地区', 128], ['legal_identifier', '企业登记标识', 256], ['authorization_basis', '授权依据', 4096]] as const) {
    errors[key] = form[key].length === 0 ? '请填写' + title : registrationTextIssue(form[key], maximum)
  }
  errors.first_admin_user_id = accounts.value.some(account => account.user_id === form.first_admin_user_id) ? '' : '请选择实际有效的首位管理员'
  errors.authorization_attested = form.authorization_attested ? '' : '请确认已经取得企业登记及管理员指定的授权'
  if (Object.values(errors).some(Boolean)) {
    await nextTick()
    const input = (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea')
    input?.scrollIntoView({ block: 'nearest' }); input?.focus(); return null
  }
  return { schema_version: '1.0.0', legal_name: form.legal_name, jurisdiction: form.jurisdiction, legal_identifier: form.legal_identifier,
    first_admin_user_id: form.first_admin_user_id, authorization_basis: form.authorization_basis, authorization_attested: true }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation
  if (!session || disabled.value || document.hidden) return
  const value = await body(); if (!value || !active(session, original)) return
  user.medicalWriting = true
  try {
    const requestIdentity = await prepareRegistration(session, value); pending.value = requestIdentity
    if (!active(session, original)) fail('CONTEXT_CHANGED', '企业申请尚未发送，当前身份需要重新核查')
    const result = await submitRegistration(session, requestIdentity, value, controller.signal)
    if (!active(session, original)) return
    const actual = await listRegistrations(session, null, controller.signal)
    if (!active(session, original)) return
    await confirmRegistration(session, requestIdentity, result)
    if (!active(session, original)) return
    clearForm(); pending.value = null; detail.value = result; items.value = actual.items; cursor.value = actual.next_cursor
  } finally { user.medicalWriting = false }
}
function openAbandon(): void { abandonAttested.value = false; abandonDialog.value = true }
async function abandon(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const identity = pending.value; const original = generation
  if (!session || !identity || disabled.value || !abandonAttested.value || document.hidden) return
  checking.value = true
  try {
    await abandonRegistration(session, identity, controller.signal)
    if (active(session, original)) { pending.value = null; abandonDialog.value = false; abandonAttested.value = false }
  } finally { if (original === generation) checking.value = false }
}
function dirty(): boolean { return Boolean(form.legal_name || form.jurisdiction || form.legal_identifier || form.first_admin_user_id || form.authorization_basis || form.authorization_attested) }
async function discard(): Promise<void> {
  const destination = nextTarget; clearForm(); discardDialog.value = false; nextTarget = null
  if (destination !== null) { leaveApproved = true; await router.push(destination); leaveApproved = false }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty() || user.medicalWriting) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(to => {
  if (user.medicalWriting) return false
  if (leaveApproved || !dirty()) return true
  nextTarget = to.fullPath; discardDialog.value = true; return false
})
watch(() => user.medicalSession, load, { immediate: true, flush: 'sync' })
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); controller.abort(); generation += 1; clearForm(); accounts.value = []; items.value = []; detail.value = null })
</script>

<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 16px; }
</style>
