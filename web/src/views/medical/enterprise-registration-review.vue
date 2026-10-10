<template>
  <main class="medical-page">
    <h1>企业申请人工审查</h1>
    <session-panel />
    <router-link to="/enterprises/registrations">返回企业登记</router-link>
    <p v-if="!permitted">企业审查需要当前平台管理员身份</p>
    <template v-if="permitted">
      <el-skeleton v-if="loading" :rows="4" animated />
      <section v-if="ready && !registrationId" data-enterprise-review-list aria-label="企业申请审查列表">
        <el-form label-position="top" @submit.prevent="reloadList">
          <el-form-item label="审查状态">
            <el-select v-model="status" :disabled="disabled" @change="reloadList">
              <el-option label="等待审查" value="PENDING_REVIEW" />
              <el-option label="已经批准" value="APPROVED" />
              <el-option label="已经拒绝" value="REJECTED" />
            </el-select>
          </el-form-item>
          <el-button native-type="submit" :disabled="disabled">重新读取审查列表</el-button>
        </el-form>
        <el-table v-if="items.length" :data="items" row-key="application.registration_id">
          <el-table-column prop="application.application.legal_name" label="企业法定名称" />
          <el-table-column prop="application.application.jurisdiction" label="登记地区" />
          <el-table-column prop="application.submitted_by" label="提交人员" />
          <el-table-column label="当前审查状态"><template #default="scope">{{ statusName(scope.row.status) }}</template></el-table-column>
          <el-table-column label="审查操作"><template #default="scope"><router-link :to="'/enterprises/registration-reviews/' + scope.row.application.registration_id">查看申请并审查</router-link></template></el-table-column>
        </el-table>
        <el-empty v-else description="当前筛选没有企业申请" />
        <p v-if="cursor">当前显示已读取的申请，后续记录尚未读取。</p>
        <el-button v-if="cursor" :disabled="disabled" @click="nextPage">读取后续申请</el-button>
      </section>
      <section v-if="ready && detail" data-enterprise-review-detail aria-label="固定企业申请与审查结果">
        <router-link to="/enterprises/registration-reviews">返回审查列表</router-link>
        <h2>原企业申请</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="企业法定名称">{{ detail.application.application.legal_name }}</el-descriptions-item>
          <el-descriptions-item label="登记地区">{{ detail.application.application.jurisdiction }}</el-descriptions-item>
          <el-descriptions-item label="企业登记标识">{{ detail.application.application.legal_identifier }}</el-descriptions-item>
          <el-descriptions-item label="首位管理员">{{ detail.application.application.first_admin_user_id }}</el-descriptions-item>
          <el-descriptions-item label="授权依据">{{ detail.application.application.authorization_basis }}</el-descriptions-item>
          <el-descriptions-item label="原申请授权声明">{{ detail.application.application.authorization_attested ? '申请人员已经确认' : '未确认' }}</el-descriptions-item>
          <el-descriptions-item label="申请编号">{{ detail.application.registration_id }}</el-descriptions-item>
          <el-descriptions-item label="原登记操作">{{ detail.application.operation_id }}</el-descriptions-item>
          <el-descriptions-item label="提交人员">{{ detail.application.submitted_by }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.application.created_at }}</el-descriptions-item>
          <el-descriptions-item label="管理员账户版本">{{ detail.application.first_admin_revision }}</el-descriptions-item>
          <el-descriptions-item label="原申请固定状态">{{ detail.application.status }}，修订{{ detail.application.revision }}</el-descriptions-item>
          <el-descriptions-item label="原申请内容摘要">{{ detail.application.content_sha256 }}</el-descriptions-item>
          <el-descriptions-item label="当前审查状态">{{ statusName(detail.status) }}，修订{{ detail.revision }}</el-descriptions-item>
        </el-descriptions>
        <el-collapse>
          <el-collapse-item title="完整原申请与固定身份绑定" name="application"><pre>{{ canonicalTemplateValue(detail.application) }}</pre></el-collapse-item>
          <el-collapse-item v-if="detail.review" title="完整固定审查回执、治理授权及批准依据" name="receipt"><pre>{{ canonicalTemplateValue(detail.review) }}</pre></el-collapse-item>
        </el-collapse>
        <section v-if="detail.review" aria-label="人工审查决定">
          <h2>已保存的人工决定</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="审查决定">{{ detail.review.decision === 'APPROVE' ? '批准企业申请' : '拒绝企业申请' }}</el-descriptions-item>
            <el-descriptions-item label="人工审查理由">{{ detail.review.request.reason }}</el-descriptions-item>
            <el-descriptions-item label="决定人员">{{ detail.review.decided_by }}</el-descriptions-item>
            <el-descriptions-item label="决定时间">{{ detail.review.decided_at }}</el-descriptions-item>
            <el-descriptions-item label="决定操作编号">{{ detail.review.operation_id }}</el-descriptions-item>
            <el-descriptions-item label="决定内容摘要">{{ detail.review.content_sha256 }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.review.tenant_id" label="批准企业编号">{{ detail.review.tenant_id }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.review.member_id" label="首位成员编号">{{ detail.review.member_id }}</el-descriptions-item>
          </el-descriptions>
          <p>固定回执保留原决定。当前成员、治理许可及企业选择需要分别重新核查。</p>
          <el-button v-if="pending" :disabled="disabled" @click="restore">核对原请求摘要并恢复决定参数</el-button>
        </section>
        <el-alert v-if="pending" title="原企业审查请求待核查" type="warning" :closable="false">
          <p>原请求编号：{{ pending.key }}。完整原参数一致后，由人员确认使用原请求核查结果。</p>
          <p>读取已保存决定不能确认原请求键。离开页面不会取消已经发送的操作。</p>
          <el-button :disabled="disabled" @click="openAbandon">明确核查并清除原审查请求身份</el-button>
        </el-alert>
        <el-form v-if="detail.status === 'PENDING_REVIEW' || pending" ref="formElement" data-enterprise-review-form label-position="top" @submit.prevent="submit">
          <h2>{{ detail.review ? '核查完整原请求' : '提交人工审查决定' }}</h2>
          <p>批准依据、范围和期限由获授权人员明确填写。批准不会自动选择企业、发布岗位或授予专业文档权限。</p>
          <el-form-item label="人工审查决定" :error="errors.decision">
            <el-radio-group v-model="decision" :disabled="disabled"><el-radio value="APPROVE">批准企业申请</el-radio><el-radio value="REJECT">拒绝企业申请</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item label="人工审查理由" :error="errors.reason"><el-input v-model="reason" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" :disabled="disabled" /></el-form-item>
          <template v-if="decision === 'APPROVE'">
            <el-form-item label="明确治理授权" :error="errors.grants">
              <el-checkbox-group v-model="selected" :disabled="disabled"><el-checkbox v-for="action in actions" :key="action.value" :value="action.value">{{ action.label }}</el-checkbox></el-checkbox-group>
            </el-form-item>
            <section v-for="action in selected" :key="action" :aria-label="actionName(action) + '完整治理条件'">
              <h3>{{ actionName(action) }}完整治理条件</h3>
              <el-form-item :label="actionName(action) + '身份模式'" :error="errors[action + ':identity']">
                <el-checkbox-group v-model="grants[action].identity_modes" :disabled="disabled"><el-checkbox value="local">独立本地账户</el-checkbox><el-checkbox value="oidc">OIDC身份</el-checkbox></el-checkbox-group>
              </el-form-item>
              <el-form-item :label="actionName(action) + '管理用途'" :error="errors[action + ':purpose']">
                <el-checkbox v-model="grants[action].allPurposes" :disabled="disabled">明确允许全部用途</el-checkbox>
                <el-select v-model="grants[action].purposes" multiple filterable allow-create default-first-option :disabled="disabled || grants[action].allPurposes" placeholder="明确选择或输入用途标识">
                  <el-option v-for="purpose in purposeOptions" :key="purpose" :value="purpose" :label="purpose" />
                </el-select>
              </el-form-item>
              <el-form-item :label="actionName(action) + '可委派动作'"><el-select v-model="grants[action].delegatedActions" multiple filterable allow-create :disabled="disabled" placeholder="逐项输入批准的动作标识" /></el-form-item>
              <el-form-item :label="actionName(action) + '可委派岗位标识'">
                <el-select v-model="grants[action].roleIds" multiple filterable allow-create :disabled="disabled" placeholder="逐项输入批准范围内的岗位UUID" />
                <el-button :disabled="disabled" @click="generateRole(action)">生成待批准岗位标识</el-button>
                <p>生成标识只填写本次委派范围；岗位仍需要实际人员发布和批准。</p>
              </el-form-item>
              <el-form-item :label="actionName(action) + '可委派资源类型'">
                <el-select v-model="grants[action].resourceKinds" multiple :disabled="disabled"><el-option v-for="kind in resourceKinds" :key="kind" :value="kind" :label="kind" /></el-select>
              </el-form-item>
              <el-form-item :label="actionName(action) + '可委派项目范围'">
                <el-checkbox v-model="grants[action].allProjects" :disabled="disabled">明确允许全部项目范围</el-checkbox>
                <el-select v-model="grants[action].projectIds" multiple filterable allow-create :disabled="disabled || grants[action].allProjects" placeholder="逐项输入批准的项目UUID；空集合表示没有项目范围" />
              </el-form-item>
              <el-form-item :label="actionName(action) + '可委派资源范围'">
                <el-checkbox v-model="grants[action].allResources" :disabled="disabled">明确允许全部资源范围</el-checkbox>
                <el-select v-model="grants[action].resourceIds" multiple filterable allow-create :disabled="disabled || grants[action].allResources" placeholder="逐项输入批准的资源UUID；空集合表示没有资源范围" />
              </el-form-item>
              <el-form-item :label="actionName(action) + '有效期限'" :error="errors[action + ':time']">
                <el-checkbox v-model="grants[action].unlimited" :disabled="disabled">明确批准没有截止期限</el-checkbox>
                <el-date-picker v-model="grants[action].validTo" type="datetime" :disabled="disabled || grants[action].unlimited" placeholder="选择明确批准的截止时间" />
                <p>选择器使用本机时区，提交使用UTC；服务器按实际提交时间核查期限。</p>
              </el-form-item>
              <p v-if="errors[action + ':condition']" role="alert">{{ errors[action + ':condition'] }}</p>
            </section>
          </template>
          <el-form-item label="人工审查确认" :error="errors.attestation"><el-checkbox v-model="attested" :disabled="disabled">确认已核查原申请、批准依据及本次完整审查条件</el-checkbox></el-form-item>
          <el-button native-type="submit" type="primary" :disabled="disabled">{{ pending ? '使用原请求核查企业审查结果' : '保存人工审查决定' }}</el-button>
        </el-form>
        <el-button :disabled="disabled" @click="requestReload">重新读取当前申请</el-button>
      </section>
    </template>
    <el-dialog v-model="discardDialog" title="离开企业审查" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>未保存的理由、条件和声明将被清除。服务器已经收到的请求继续处理，原请求身份继续保留。</p>
      <template #footer><el-button @click="discardDialog = false">继续填写</el-button><el-button @click="discard">放弃未保存输入并继续</el-button></template>
    </el-dialog>
    <el-dialog v-model="abandonDialog" title="清除原企业审查请求身份" :close-on-click-modal="false">
      <p>清除只影响浏览器请求身份，不取消或者修改服务器决定及治理许可。</p>
      <el-checkbox v-model="abandonAttested">确认已经核查原申请及审查结果</el-checkbox>
      <template #footer><el-button :disabled="checking" @click="abandonDialog = false">保留原请求</el-button><el-button :disabled="disabled || !abandonAttested" @click="abandon">确认清除原请求身份</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance } from 'element-plus'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'
import SessionPanel from './session-panel.vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { registrationAllowed, registrationTextIssue } from '@/api/medical/enterprise-registration'
import type { PendingRegistration } from '@/api/medical/enterprise-registration'
import { abandonReview, confirmReview, listReviews, prepareReview, readPendingReview, readReview, restoreReviewRequest, reviewBody, submitReview } from '@/api/medical/enterprise-registration-review'
import type { GovernanceAction, GovernanceCondition, GovernanceInput, ReviewBody, ReviewDetail, ReviewStatus } from '@/api/medical/enterprise-registration-review'
import { canonicalTemplateValue } from '@/api/medical/template-approval'

interface GrantForm {
  identity_modes: ('local' | 'oidc')[]; allPurposes: boolean; purposes: string[]; delegatedActions: string[]; roleIds: string[]
  resourceKinds: string[]; allProjects: boolean; projectIds: string[]; allResources: boolean; resourceIds: string[]
  unlimited: boolean; validTo: Date | null; originalValidTo: string | null
}
const actions: { value: GovernanceAction; label: string; purpose: string }[] = [
  { value: 'member.manage', label: '成员管理', purpose: 'PERMISSION_ADMINISTRATION' },
  { value: 'role.manage', label: '岗位管理', purpose: 'ROLE_ADMINISTRATION' },
  { value: 'project.create', label: '项目建立', purpose: 'PROJECT_ADMINISTRATION' }
]
const resourceKinds = ['ENTERPRISE', 'PROJECT', 'MEMBER', 'SOURCE', 'TASK', 'DOCUMENT', 'ARTIFACT', 'CONFIGURATION', 'TEMPLATE', 'RULE', 'BUDGET', 'EVALUATION', 'AUDIT', 'REPORT']
const purposeOptions = ['PERMISSION_ADMINISTRATION', 'INVITATION_ADMINISTRATION', 'ROLE_ADMINISTRATION', 'PROJECT_ADMINISTRATION']
const user = useUserStore(); const router = useRouter(); const route = useRoute(); const formElement = ref<FormInstance>()
const registrationId = computed(() => typeof route.params.registrationId === 'string' ? route.params.registrationId : null)
const status = ref<ReviewStatus>('PENDING_REVIEW'); const items = ref<ReviewDetail[]>([]); const detail = ref<ReviewDetail | null>(null)
const cursor = ref<string | null>(null); const pending = ref<PendingRegistration | null>(null)
const decision = ref<'' | 'APPROVE' | 'REJECT'>(''); const reason = ref(''); const attested = ref(false); const selected = ref<GovernanceAction[]>([])
function emptyGrant(): GrantForm { return { identity_modes: [], allPurposes: false, purposes: [], delegatedActions: [], roleIds: [], resourceKinds: [], allProjects: false, projectIds: [], allResources: false, resourceIds: [], unlimited: false, validTo: null, originalValidTo: null } }
const grants = reactive<Record<GovernanceAction, GrantForm>>({ 'member.manage': emptyGrant(), 'role.manage': emptyGrant(), 'project.create': emptyGrant() })
const errors = reactive<Record<string, string>>({}); const loading = ref(false); const checking = ref(false); const ready = ref(false)
const permitted = computed(() => registrationAllowed(user.medicalSession))
const disabled = computed(() => !permitted.value || !ready.value || loading.value || checking.value || user.medicalWriting)
const discardDialog = ref(false); const abandonDialog = ref(false); const abandonAttested = ref(false)
let controller = new AbortController(); let generation = 0; let nextTarget: string | null = null; let reloadRequested = false; let leaveApproved = false
function actionName(action: GovernanceAction): string { return actions.find(item => item.value === action)!.label }
function statusName(value: ReviewStatus): string { return { PENDING_REVIEW: '等待审查', APPROVED: '已经批准', REJECTED: '已经拒绝' }[value] }
function clearForm(): void {
  decision.value = ''; reason.value = ''; attested.value = false; selected.value = []
  for (const action of actions) grants[action.value] = emptyGrant()
  for (const key of Object.keys(errors)) delete errors[key]
}
function active(session: MedicalSession, original: number): boolean { return original === generation && user.medicalSession === session && !document.hidden }
async function load(): Promise<void> {
  controller.abort(); controller = new AbortController(); const signal = controller.signal; const original = ++generation
  clearForm(); items.value = []; detail.value = null; cursor.value = null; pending.value = null
  loading.value = false; checking.value = false; ready.value = false; discardDialog.value = false; abandonDialog.value = false; abandonAttested.value = false
  nextTarget = null; reloadRequested = false
  const session: MedicalSession | null = user.medicalSession
  if (!session || !registrationAllowed(session) || document.hidden) return
  loading.value = true
  try {
    const id = registrationId.value
    if (id === null) {
      const page = await listReviews(session, status.value, null, signal)
      if (!active(session, original)) return
      items.value = page.items; cursor.value = page.next_cursor
    } else {
      const value = await readReview(session, id, signal)
      if (!active(session, original)) return
      detail.value = value; pending.value = readPendingReview(session, id)
    }
    ready.value = true
  } finally { if (original === generation) loading.value = false }
}
async function page(append: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation
  if (!session || disabled.value || document.hidden || append && cursor.value === null) return
  checking.value = true; if (!append) { items.value = []; cursor.value = null }
  try {
    const result = await listReviews(session, status.value, append ? cursor.value : null, controller.signal)
    if (!active(session, original)) return
    if (append && result.items.some(item => items.value.some(existing => existing.application.registration_id === item.application.registration_id))) fail('CONTENT_MISMATCH', '审查申请列表重复，请重新读取')
    items.value = append ? [...items.value, ...result.items] : result.items; cursor.value = result.next_cursor
  } finally { if (original === generation) checking.value = false }
}
async function reloadList(): Promise<void> { await page(false) }
async function nextPage(): Promise<void> { await page(true) }
function generateRole(action: GovernanceAction): void { if (!disabled.value) grants[action].roleIds.push(crypto.randomUUID()) }
function condition(row: GrantForm): GovernanceCondition {
  return { schema_version: '1.0.0', identity_modes: row.identity_modes, purposes: row.allPurposes ? null : row.purposes,
    delegation: { actions: row.delegatedActions, role_ids: row.roleIds, resource_kinds: row.resourceKinds,
      project_ids: row.allProjects ? null : row.projectIds, resource_ids: row.allResources ? null : row.resourceIds } }
}
async function body(): Promise<ReviewBody | null> {
  for (const key of Object.keys(errors)) delete errors[key]
  errors.decision = decision.value ? '' : '请明确选择人工审查决定'
  errors.reason = reason.value ? registrationTextIssue(reason.value, 4096) : '请填写人工审查理由'
  errors.attestation = attested.value ? '' : '请确认已经核查原申请及完整审查条件'
  const values: GovernanceInput[] = []
  if (decision.value === 'APPROVE') {
    errors.grants = selected.value.includes('member.manage') ? '' : '批准企业需要明确成员管理授权'
    for (const action of selected.value) {
      const row = grants[action]; const value = condition(row)
      errors[action + ':identity'] = row.identity_modes.includes('local') ? '' : '请明确允许独立本地账户'
      errors[action + ':purpose'] = row.allPurposes || row.purposes.includes(actions.find(item => item.value === action)!.purpose) ? '' : '请明确批准当前动作的管理用途'
      errors[action + ':condition'] = schemaIssues('urn:medical:permission-decisions:1.0.0#/$defs/Condition', value).length ? '请核查全部治理条件、唯一标识及集合上限' : ''
      const valid = row.validTo instanceof Date && Number.isFinite(row.validTo.getTime())
      errors[action + ':time'] = row.unlimited || valid ? '' : '请选择明确截止时间或确认没有截止期限'
      let validTo: string | null = null
      if (!row.unlimited && valid) {
        const date = row.validTo as Date
        validTo = row.originalValidTo !== null && Date.parse(row.originalValidTo) === date.getTime() ? row.originalValidTo : date.toISOString()
        if (!pending.value && date.getTime() <= Date.now()) errors[action + ':time'] = '截止时间必须晚于当前提交时刻'
      }
      values.push({ action, condition: value, valid_to: validTo })
    }
  }
  const input = { schema_version: '1.0.0' as const, decision: decision.value as 'APPROVE' | 'REJECT', expected_revision: '1' as const,
    reason: reason.value, approval_attested: true as const, governance_grants: values }
  if (new TextEncoder().encode(JSON.stringify(input)).byteLength > 65536) errors.grants = '完整审查请求超过接口字节上限，请核查治理条件'
  if (Object.values(errors).some(Boolean)) {
    await nextTick()
    const input = (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea')
    input?.focus(); return null
  }
  return reviewBody(input)
}
async function restore(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation; const value = detail.value; const identity = pending.value
  if (!session || !value || !identity || disabled.value || document.hidden) return
  checking.value = true
  try {
    const input = await restoreReviewRequest(session, value, identity, controller.signal)
    if (!active(session, original)) return
    clearForm(); decision.value = input.decision; reason.value = input.reason
    selected.value = input.governance_grants.map(grant => grant.action)
    for (const grant of input.governance_grants) {
      const row = grants[grant.action]; const permission = grant.condition
      row.identity_modes = [...permission.identity_modes]; row.allPurposes = permission.purposes === null; row.purposes = [...(permission.purposes ?? [])]
      row.delegatedActions = [...permission.delegation.actions]; row.roleIds = [...permission.delegation.role_ids]; row.resourceKinds = [...permission.delegation.resource_kinds]
      row.allProjects = permission.delegation.project_ids === null; row.projectIds = [...(permission.delegation.project_ids ?? [])]
      row.allResources = permission.delegation.resource_ids === null; row.resourceIds = [...(permission.delegation.resource_ids ?? [])]
      row.unlimited = grant.valid_to === null; row.validTo = grant.valid_to === null ? null : new Date(grant.valid_to); row.originalValidTo = grant.valid_to
    }
    // 恢复原参数后仍由实际人员重新确认，不自动发送或者勾选审查声明。
    attested.value = false
  } finally { if (original === generation) checking.value = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation; const value = detail.value
  if (!session || !value || disabled.value || document.hidden) return
  const input = await body(); if (!input || !active(session, original)) return
  user.medicalWriting = true
  try {
    const identity = await prepareReview(session, value.application.registration_id, input); pending.value = identity
    if (!active(session, original)) fail('CONTEXT_CHANGED', '人工审查尚未发送，当前身份需要重新核查')
    const result = await submitReview(session, value, identity, input, controller.signal)
    if (!active(session, original)) return
    await listReviews(session, result.status, null, controller.signal)
    if (!active(session, original)) return
    await confirmReview(session, identity, result, controller.signal)
    if (!active(session, original)) return
    clearForm(); pending.value = null; detail.value = result
  } finally { user.medicalWriting = false }
}
function openAbandon(): void { abandonAttested.value = false; abandonDialog.value = true }
async function abandon(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = generation; const identity = pending.value; const value = detail.value
  if (!session || !identity || !value || disabled.value || !abandonAttested.value || document.hidden) return
  checking.value = true
  try {
    await abandonReview(session, value.application.registration_id, identity, controller.signal)
    if (active(session, original)) { clearForm(); pending.value = null; abandonDialog.value = false; abandonAttested.value = false }
  } finally { if (original === generation) checking.value = false }
}
function dirty(): boolean { return Boolean(decision.value || reason.value || attested.value || selected.value.length) }
async function requestReload(): Promise<void> { if (dirty()) { reloadRequested = true; nextTarget = null; discardDialog.value = true } else await load() }
async function discard(): Promise<void> {
  const destination = nextTarget; const reload = reloadRequested; clearForm(); discardDialog.value = false; nextTarget = null; reloadRequested = false
  if (reload) await load()
  else if (destination !== null) { leaveApproved = true; try { await router.push(destination) } finally { leaveApproved = false } }
}
function guard(to: RouteLocationNormalized): boolean {
  if (user.medicalWriting) return false
  if (leaveApproved || !dirty()) return true
  nextTarget = to.fullPath; reloadRequested = false; discardDialog.value = true; return false
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty() || user.medicalWriting) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(guard); onBeforeRouteUpdate(guard)
watch([() => user.medicalSession, registrationId], load, { immediate: true, flush: 'sync' })
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); controller.abort(); generation += 1; clearForm(); items.value = []; detail.value = null; pending.value = null })
</script>

<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 16px; }
.el-select { width: 100%; }
</style>
