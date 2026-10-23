<template>
  <main class="medical-page">
    <h1>{{ title }}</h1>
    <session-panel />
    <p v-if="!permitted">{{ mode === 'CREATE' ? '创建项目需要当前企业的明确授权' : '当前项目动作需要明确读取及管理授权' }}</p>
    <p v-if="loading" role="status">正在核查当前项目、版本与动作许可</p>
    <p v-if="writing" role="status">正在提交原项目请求，关闭页面不会取消已发送的操作</p>
    <section v-if="pending && user.medicalSession?.current_context" aria-label="原项目请求" data-pending-project-write>
      <p>原项目请求待核查。操作定义：{{ pending.operation }}；请求编号：{{ pending.key }}；原上下文版本：{{ pending.context.context_revision }}</p>
      <p v-if="pending.expected_revision">原项目版本：{{ pending.expected_revision }}</p>
      <p>客户输入仅保存在当前页面。刷新以后，需要重新输入完整原参数，原请求键继续保留。</p>
      <router-link v-if="pending.operation !== operation" :to="pending.operation === 'PJ-04' ? '/projects/' + pending.project_id + '/profile' : '/projects/' + pending.project_id + '/status'">返回原项目请求入口</router-link>
      <el-button :disabled="writing || checking" @click="lookup">查询原项目操作</el-button>
      <template v-if="lookupResult">
        <p v-if="lookupResult.lookup_status === 'NOT_FOUND'">尚未查询到原操作，不能据此判定请求从未发送。核查后可以使用完整原参数和原请求键提交。</p>
        <p v-else-if="lookupResult.lookup_status === 'EXPIRED'">原查询保存期限已过，需要人工核对。原请求继续保留，禁止自动重发。</p>
        <template v-else-if="lookupResult.operation">
          <p>原操作状态：{{ operationStatuses[lookupResult.operation.status] }}；操作编号：{{ lookupResult.operation.operation_id }}</p>
          <p v-if="lookupResult.operation.status === 'PROCESSING'">原操作仍在执行，请重新查询实际结果。</p>
          <p v-if="lookupResult.operation.status === 'FAILED'">原操作失败，原请求继续保留，需要核查问题处理结果。</p>
          <el-button v-if="lookupResult.operation.status === 'COMPLETED'" :disabled="writing || checking" @click="confirmLookup">核查原操作及当前项目</el-button>
        </template>
      </template>
    </section>
    <section v-if="receipt && user.medicalSession?.current_context" aria-label="项目操作回执" data-project-write-receipt>
      <p>原操作已完成并经过状态重读。项目内容读取由当前独立许可决定。</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="操作编号">{{ receipt.operation_id }}</el-descriptions-item>
        <el-descriptions-item label="项目标识">{{ receipt.project_id }}</el-descriptions-item>
        <el-descriptions-item label="项目版本">{{ receipt.revision }}</el-descriptions-item>
        <el-descriptions-item label="属性版本">{{ receipt.profile_revision }}</el-descriptions-item>
        <el-descriptions-item label="项目状态">{{ statuses[receipt.status] }}</el-descriptions-item>
        <el-descriptions-item label="关联任务数量">{{ receipt.impact.dependent_tasks ?? '未获准读取' }}</el-descriptions-item>
        <el-descriptions-item label="活动运行数量">{{ receipt.impact.active_runs ?? '未获准读取' }}</el-descriptions-item>
        <el-descriptions-item label="影响集合">{{ receipt.impact.complete ? '本次返回范围完整' : '本次返回范围不完整' }}</el-descriptions-item>
      </el-descriptions>
      <p>已发送的模型调用需要按照原调用记录核对实际执行及费用。项目状态变更不表示外部取消或恢复完成。</p>
      <router-link v-if="projectActionAllowed(user.medicalSession, 'project.read', receipt.project_id)" :to="'/projects/' + receipt.project_id">读取当前项目</router-link>
    </section>
    <section v-if="confirmedProject && user.medicalSession?.current_context" aria-label="当前项目核查结果">
      <p>已读取当前项目版本：{{ confirmedProject.revision }}；属性版本：{{ confirmedProject.profile.profile_revision }}；状态：{{ statuses[confirmedProject.status] }}</p>
      <router-link :to="'/projects/' + confirmedProject.project_id">进入当前项目</router-link>
    </section>
    <el-button v-if="permitted" :disabled="loading || writing" @click="openDialog">填写项目请求</el-button>
    <router-link to="/projects">返回项目中心</router-link>
    <el-dialog v-model="dialog" class="medical-project-write-dialog" :title="title" width="min(780px, 94vw)" :before-close="closeDialog" destroy-on-close>
      <el-form v-if="permitted" ref="formElement" data-project-write-form label-position="top" @submit.prevent="submit(false)">
        <p v-if="mode !== 'CREATE'">{{ project?.name }}；原项目版本：{{ pending?.expected_revision ?? project?.revision }}；原属性版本：{{ pending?.based_on_profile_revision ?? project?.profile.profile_revision }}</p>
        <template v-if="mode === 'CREATE'">
          <el-form-item label="项目名称" :error="errors.name" for="project-write-name">
            <el-input id="project-write-name" v-model="form.name" maxlength="256" :disabled="writing" />
          </el-form-item>
          <el-form-item label="项目负责人" :error="errors.responsible" for="project-write-responsible">
            <enterprise-member-selector v-model="form.responsible" input-id="project-write-responsible" label="项目负责人" :disabled="writing" />
          </el-form-item>
          <rule-basis-editor v-model="creationBases" label="建档依据" resource-kind="SOURCE" :max-items="1" :disabled="writing" />
          <el-alert v-if="errors.basis || errors.hash" :title="errors.basis || errors.hash" type="error" :closable="false" />
          <p>选择本企业实际公共资料作为建档依据，专业核准由相应流程处理。</p>
          <el-collapse><el-collapse-item title="高级：负责人账户标识" name="responsible"><el-form-item label="负责人账户标识" for="project-write-responsible-id"><el-input id="project-write-responsible-id" v-model="form.responsible" :disabled="writing" /></el-form-item></el-collapse-item></el-collapse>
        </template>
        <el-form-item v-if="mode === 'DECIDE'" label="项目状态决定" :error="errors.decision" for="project-write-decision">
          <el-select id="project-write-decision" v-model="form.decision" :disabled="writing">
            <el-option v-for="choice in decisions" :key="choice" :value="choice" :label="decisionLabels[choice]" />
          </el-select>
        </el-form-item>
        <project-attributes v-if="mode !== 'DECIDE'" ref="attributesElement" :attributes="initialAttributes" :project-id="target ?? undefined" :disabled="writing" />
        <el-form-item :label="mode === 'CREATE' ? '建档原因' : '决定原因'" :error="errors.reason" for="project-write-reason">
          <el-input id="project-write-reason" v-model="form.reason" type="textarea" maxlength="2000" :disabled="writing" />
        </el-form-item>
        <el-form-item :error="errors.attested" for="project-write-attested">
          <el-checkbox id="project-write-attested" v-model="form.attested" :disabled="writing">本人已核查本次项目请求的实际依据并获准执行</el-checkbox>
        </el-form-item>
        <el-alert v-if="errors.schema" :title="errors.schema" type="error" :closable="false" />
        <el-button type="primary" native-type="submit" :disabled="loading || writing || checking || pending !== null && !mayReplay">{{ pending ? '使用完整原参数核查原请求' : '确认提交项目请求' }}</el-button>
        <el-button :disabled="writing" @click="closeDialog()">返回</el-button>
      </el-form>
    </el-dialog>
    <el-dialog v-model="discardDialog" title="处理未保存的项目输入" width="min(540px, 94vw)" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>未提交输入只保存在当前页面。已发送请求继续保留原请求身份，关闭页面不会取消该请求。</p>
      <el-button :disabled="writing || !permitted || pending !== null && !mayReplay" @click="submit(true)">保存后返回</el-button>
      <el-button :disabled="writing" @click="discard">放弃输入并返回</el-button>
      <el-button :disabled="writing" @click="discardDialog = false">继续编辑</el-button>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import type { Attribute, Basis, MedicalSession, Project } from '@/api/medical/protocol'
import { fail, schemaIssues } from '@/api/medical/protocol'
import { readProject } from '@/api/medical/projects'
import { clearCompletedProjectWrite, confirmProjectOperation, prepareProjectWrite, projectActionAllowed,
  projectBodyReference, projectTextIssue, readPendingProjectWrite, submitProjectWrite } from '@/api/medical/project-write'
import type { PendingProjectWrite, ProjectDecision, ProjectReceipt, ProjectWriteBody, ProjectWriteOperation } from '@/api/medical/project-write'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'
import useUserStore from '@/store/modules/user'
import ProjectAttributes from './project-attributes.vue'
import EnterpriseMemberSelector from './enterprise-member-selector.vue'
import RuleBasisEditor from './rule-basis-editor.vue'
import SessionPanel from './session-panel.vue'

const user = useUserStore(); const route = useRoute(); const router = useRouter()
const mode = computed(() => route.meta.projectWrite as 'CREATE' | 'REVISE' | 'DECIDE')
const operation = computed<ProjectWriteOperation>(() => mode.value === 'CREATE' ? 'PJ-02' : mode.value === 'REVISE' ? 'PJ-04' : 'PJ-05')
const title = computed(() => mode.value === 'CREATE' ? '创建项目' : mode.value === 'REVISE' ? '修订项目属性' : '决定项目状态')
const target = computed<string | null>(() => {
  if (mode.value === 'CREATE') return null
  if (typeof route.params.projectId !== 'string') throw new Error('项目请求路由缺少实际对象')
  return route.params.projectId
})
const project = ref<Project | null>(null); const confirmedProject = ref<Project | null>(null)
const pending = ref<PendingProjectWrite | null>(null); const receipt = ref<ProjectReceipt | null>(null)
const lookupResult = ref<OperationLookup | null>(null); const loading = ref(false); const checking = ref(false)
const dialog = ref(false); const discardDialog = ref(false)
const writing = computed(() => user.medicalWriting)
const initialAttributes = ref<Attribute[]>([])
const attributesElement = ref<InstanceType<typeof ProjectAttributes> | null>(null)
const formElement = ref<{ $el: HTMLElement } | null>(null)
const form = reactive({ name: '', responsible: '', basis: '', hash: '', reason: '', decision: '' as ProjectDecision | '', attested: false })
const creationBases=computed<Basis[]>({get:()=>form.basis || form.hash ? [{version_id:form.basis,content_sha256:form.hash}] : [],set:values=>{if(values.length>1)throw new Error('项目建档只允许一项实际依据版本');form.basis=values[0]?.version_id??'';form.hash=values[0]?.content_sha256??''}})
const errors = reactive({ name: '', responsible: '', basis: '', hash: '', reason: '', decision: '', attested: '', schema: '' })
const statuses = { PENDING: '建档待确认', ACTIVE: '使用中', PAUSED: '已暂停', ARCHIVED: '已归档' }
const operationStatuses = { PROCESSING: '执行中', COMPLETED: '已完成', FAILED: '失败' }
const decisionLabels = { ACTIVATE: '启用项目', PAUSE: '暂停项目', ARCHIVE: '归档项目', RESTORE: '恢复项目' }
const permitted = computed(() => {
  const action = mode.value === 'CREATE' ? 'project.create' : mode.value === 'REVISE' ? 'profile.confirm' : 'project.manage'
  return (pending.value === null || pending.value.operation === operation.value)
    && projectActionAllowed(user.medicalSession, action, target.value) && (mode.value === 'CREATE'
    || project.value !== null && project.value.allowed_actions.includes(action) && project.value.allowed_actions.includes('project.read')
      && (mode.value !== 'REVISE' || ['PENDING', 'ACTIVE'].includes(project.value.status)))
})
const decisions = computed<ProjectDecision[]>(() => {
  if (pending.value?.operation === 'PJ-05') return [pending.value.decision as ProjectDecision]
  const state = project.value?.status
  if (state === 'PENDING') return ['ACTIVATE', 'ARCHIVE']
  if (state === 'ACTIVE') return ['PAUSE', 'ARCHIVE']
  if (state === 'PAUSED') return ['RESTORE', 'ARCHIVE']
  if (state === 'ARCHIVED') return ['RESTORE']
  return []
})
const mayReplay = computed(() => lookupResult.value?.lookup_status === 'NOT_FOUND')
let generation = 0; let controller: AbortController | null = null; let leaveApproved = false
let nextTarget: string | null = null; let dialogDone: (() => void) | null = null
function clearForm(): void {
  Object.assign(form, { name: '', responsible: '', basis: '', hash: '', reason: '', decision: '', attested: false })
  for (const name of Object.keys(errors) as (keyof typeof errors)[]) errors[name] = ''
  initialAttributes.value = []; attributesElement.value?.clear()
  // 当前企业失效时同步清除原生输入，Vue完成更新前不保留客户内容。
  formElement.value?.$el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => {
    input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false
  })
}
function dirty(): boolean { return Object.values(form).some(Boolean) || (attributesElement.value?.dirty() ?? false) }
function openDialog(): void {
  if (!permitted.value || loading.value || writing.value) return
  if (!dirty()) initialAttributes.value = pending.value === null && mode.value === 'REVISE' && project.value !== null
    ? project.value.profile.attributes : []
  dialog.value = true
}
function clear(): void {
  generation += 1; controller?.abort(); controller = null
  project.value = null; confirmedProject.value = null; pending.value = null; lookupResult.value = null
  loading.value = false; checking.value = false; dialog.value = false; discardDialog.value = false
  receipt.value = null; clearForm()
}
async function read(): Promise<void> {
  clear()
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || document.hidden || writing.value) return
  const current = generation; const id = target.value
  pending.value = readPendingProjectWrite(session, id)
  if (id !== null && projectActionAllowed(session, 'project.read', id)) {
    controller = new AbortController(); loading.value = true
    try {
      const value = await readProject(session.current_context, id, controller.signal)
      if (generation !== current || user.medicalSession !== session || target.value !== id) return
      project.value = value
      initialAttributes.value = pending.value === null ? value.profile.attributes : []
    } finally { if (current === generation) loading.value = false }
  }
  if (current === generation) openDialog()
}
async function focusError(): Promise<void> {
  await nextTick()
  const element = formElement.value?.$el.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea,.el-form-item.is-error [tabindex]')
  element?.scrollIntoView({ block: 'nearest' }); element?.focus()
}
async function body(): Promise<ProjectWriteBody | null> {
  for (const name of Object.keys(errors) as (keyof typeof errors)[]) errors[name] = ''
  errors.reason = projectTextIssue(form.reason, 2000)
  errors.attested = form.attested ? '' : '请核查实际依据并确认本次授权声明'
  let attributes: Attribute[] = []
  if (mode.value !== 'DECIDE') {
    if (!attributesElement.value) throw new Error('项目属性表单缺少实际实例')
    if (!attributesElement.value.validate()) { await focusError(); return null }
    attributes = attributesElement.value.value()
  }
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
  if (mode.value === 'CREATE') {
    errors.name = projectTextIssue(form.name, 256)
    errors.responsible = uuid.test(form.responsible) ? '' : '请填写本企业真实成员的规范账户标识'
    errors.basis = uuid.test(form.basis) ? '' : '请填写实际建档依据的规范版本标识'
    errors.hash = /^[0-9a-f]{64}$/.test(form.hash) ? '' : '请填写原件的64位SHA-256内容摘要'
  }
  if (mode.value === 'DECIDE') errors.decision = form.decision && decisions.value.includes(form.decision) ? '' : '请选择当前状态允许的项目决定'
  if (Object.values(errors).some(Boolean)) { await focusError(); return null }
  const base = { schema_version: '1.0.0' as const, reason: form.reason, approval_attested: true as const }
  let value: ProjectWriteBody
  if (mode.value === 'CREATE') value = { ...base, name: form.name, responsible_user_id: form.responsible,
    attributes, creation_basis: { version_id: form.basis, content_sha256: form.hash } }
  else {
    if (!project.value) throw new Error('项目变更缺少已核查原版本')
    const expected = pending.value?.expected_revision ?? project.value.revision
    if (mode.value === 'REVISE') value = { ...base, expected_revision: expected,
      based_on_profile_revision: pending.value?.based_on_profile_revision ?? project.value.profile.profile_revision, attributes }
    else {
      if (!form.decision) throw new Error('项目状态决定缺少实际动作')
      value = { ...base, expected_revision: expected, decision: form.decision }
    }
  }
  if (schemaIssues(projectBodyReference(operation.value), value).length) {
    errors.schema = '项目请求未通过已发布字段规范，请核查全部必填字段及条件'; await focusError(); return null
  }
  return value
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session?.current_context || !original || writing.value || checking.value || document.hidden) return
  const current = generation; controller?.abort(); controller = new AbortController(); checking.value = true
  try {
    const value = await lookupOperation(session, original.operation, original.key, controller.signal)
    if (generation === current && user.medicalSession === session && pending.value?.key === original.key) lookupResult.value = value
  } finally { if (current === generation) checking.value = false }
}
async function verifyResource(session: MedicalSession, id: string, expected: ProjectReceipt | null): Promise<Project | null> {
  if (!session.current_context) fail('CONTEXT_CHANGED', '项目操作完成以后需要重新核查企业')
  if (!projectActionAllowed(session, 'project.read', id)) return null
  const value = await readProject(session.current_context, id, new AbortController().signal)
  if (expected !== null && (value.revision !== expected.revision || value.profile.profile_revision !== expected.profile_revision || value.status !== expected.status)) {
    fail('SCOPE_STALE', '原操作完成以后项目版本已变化，请核查原操作与当前项目')
  }
  return value
}
async function confirmLookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  const result = lookupResult.value?.operation
  if (!session?.current_context || !original || !result?.resource_id || checking.value || writing.value || document.hidden) return
  const current = generation; checking.value = true
  try {
    const status = await confirmProjectOperation(session, original, result.operation_id, result.resource_id, new AbortController().signal)
    const actual = await verifyResource(session, result.resource_id, null)
    if (current !== generation || user.medicalSession !== session) return
    clearCompletedProjectWrite(session, original, status)
    pending.value = null; lookupResult.value = null; confirmedProject.value = actual; project.value = actual; clearForm(); dialog.value = false
  } finally { if (current === generation) checking.value = false }
}
async function submit(thenLeave: boolean): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || !permitted.value || writing.value || document.hidden || pending.value !== null && !mayReplay.value) return
  const current = generation; const path = route.fullPath; const id = target.value; const definition = operation.value
  const value = await body()
  if (value === null || current !== generation || user.medicalSession !== session) return
  user.medicalWriting = true
  try {
    const original = await prepareProjectWrite(session, definition, id, value)
    pending.value = original
    if (current !== generation || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '原项目请求尚未发送，请重新核查当前身份')
    const completed = await submitProjectWrite(session, original, value)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) {
      fail('CONTEXT_CHANGED', '原项目操作已返回，当前主体或企业需要重新核查')
    }
    const status = await confirmProjectOperation(fresh, original, completed.operation_id, completed.project_id, new AbortController().signal)
    const actual = await verifyResource(fresh, completed.project_id, completed)
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearCompletedProjectWrite(fresh, original, status)
    pending.value = null; lookupResult.value = null; receipt.value = completed; confirmedProject.value = actual
    project.value = actual; clearForm(); dialog.value = false; discardDialog.value = false
    window.dispatchEvent(new Event('medical-context-invalidated'))
    if (thenLeave) await finishLeave()
  } finally {
    user.medicalWriting = false
    // 会话重读期间清除受限内容；结果核查未完成时按当前身份恢复原请求入口。
    if (route.fullPath === path && user.medicalSession?.current_context && !document.hidden && receipt.value === null
        && readPendingProjectWrite(user.medicalSession, id) !== null) await read()
  }
}
function closeDialog(done?: () => void): void {
  if (writing.value) return
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
onBeforeRouteLeave(to => {
  if (leaveApproved || !dirty()) return true
  nextTarget = to.fullPath; dialogDone = null; discardDialog.value = true; return false
})
window.addEventListener('beforeunload', beforeUnload)
watch(() => [user.medicalSession, route.fullPath], read, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); clear() })
</script>

<style scoped>
:deep(.medical-project-write-dialog .el-dialog__body) {
  max-height: 66vh;
  overflow-y: auto;
}
</style>
