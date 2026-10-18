<template>
  <main class="medical-page">
    <h1>创建模板草稿</h1>
    <session-panel />
    <el-alert v-if="errors.import" :title="errors.import" type="error" :closable="false" role="alert" />
    <p v-if="!permitted">创建模板需要当前企业的明确维护权限</p>
    <el-alert v-if="pending" title="原模板创建请求待核查" type="warning" :closable="false" data-template-create-pending>
      <p>请求标识：{{ pending.key }}；原上下文修订：{{ pending.context.context_revision }}</p>
      <p>正文和理由只保存在当前页面。刷新以后需要重新填写完整原参数，继续使用原请求标识。</p>
      <el-button :disabled="user.medicalWriting || checking" @click="lookup">查询原模板创建操作</el-button>
      <p v-if="lookupResult">{{ lookupLabels[lookupResult.lookup_status] }}</p>
      <p v-if="lookupResult?.operation">操作编号：{{ lookupResult.operation.operation_id }}；状态：{{ operationLabels[lookupResult.operation.status] }}</p>
      <p v-if="lookupResult?.operation?.resource_id">原模板版本：{{ lookupResult.operation.resource_id }}</p>
      <p v-if="lookupResult?.operation?.status === 'COMPLETED'">请填写完整原参数取得固定创建回执。模板当前治理状态需要独立读取。</p>
    </el-alert>
    <section v-if="created && permitted" data-template-created aria-label="模板草稿创建回执">
      <el-alert title="原模板草稿已保存并核查完成操作" type="success" :closable="false" />
      <p>创建回执保持原草稿状态；后续人工决定及项目采用由各自权限和版本检查处理。</p>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="操作编号">{{ created.operation_id }}</el-descriptions-item>
        <el-descriptions-item label="固定模板版本">{{ created.template.template_version }}</el-descriptions-item>
        <el-descriptions-item label="创建内容摘要">{{ created.template.content_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="创建人员">{{ created.template.created_by }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ created.template.created_at }}</el-descriptions-item>
      </el-descriptions>
      <el-collapse><el-collapse-item title="完整原创建回执" name="created"><pre>{{ full(created) }}</pre></el-collapse-item></el-collapse>
      <router-link v-if="templateApprovalAllowed(user.medicalSession, created.template.template_version)" :to="'/templates/' + created.template.template_version + '/approval'">打开人工审查</router-link>
    </section>
    <el-form v-if="permitted && !created" ref="formElement" label-position="top" data-template-create-form @submit.prevent="submit">
      <p>全部定义需要真实依据版本和原文摘要。条件未知时保留待确认，报告、证书、签名及原始数据只接收获授权原件。</p>
      <el-upload :auto-upload="false" :show-file-list="false" accept=".json,application/json" :disabled="user.medicalWriting || checking" :on-change="importDefinition">
        <el-button :disabled="user.medicalWriting || checking">导入企业完整模板定义</el-button>
        <template #tip><p>选择企业获授权的JSON模板定义，导入只进入当前编辑页面；保存以后服务器重新核查全部依据和许可。</p></template>
      </el-upload>
      <el-form-item label="文档类型" :error="errors.document_type">
        <el-select v-model="form.document_type" :disabled="disabled" @change="invalidatePreview"><el-option v-for="(label, value) in documentLabels" :key="value" :label="label" :value="value" /></el-select>
      </el-form-item>
      <el-form-item label="申请路径" :error="errors.application_path">
        <el-select v-model="form.application_path" :disabled="disabled" @change="invalidatePreview"><el-option v-for="(label, value) in pathLabels" :key="value" :label="label" :value="value" /></el-select>
      </el-form-item>
      <el-form-item label="IVD类别" :error="errors.ivd">
        <el-select v-model="form.ivd" :disabled="disabled" @change="invalidatePreview"><el-option label="属于IVD" value="IVD" /><el-option label="非IVD" value="NON_IVD" /></el-select>
      </el-form-item>
      <el-form-item label="目录规范" :error="errors.directory_profile">
        <el-select v-model="form.directory_profile" :disabled="disabled" @change="invalidatePreview">
          <el-option label="非IVD首次注册完整六章目录" value="NON_IVD_FIRST_REGISTRATION_2021_121" />
          <el-option label="该文档类型的专属目录" value="TYPE_SPECIFIC" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本依据" :error="errors.base_mode">
        <el-radio-group v-model="form.base_mode" :disabled="disabled" @change="invalidatePreview"><el-radio value="NEW">独立建立模板</el-radio><el-radio value="REVISION">根据原模板建立新版本</el-radio></el-radio-group>
      </el-form-item>
      <template v-if="form.base_mode === 'REVISION'">
        <el-form-item label="原模板版本" :error="errors.base_version"><el-input v-model="form.base_id" :disabled="disabled" @input="invalidatePreview" /></el-form-item>
        <el-form-item label="原模板内容摘要（SHA-256）" :error="errors.base_version"><el-input v-model="form.base_hash" :disabled="disabled" @input="invalidatePreview" /></el-form-item>
      </template>
      <section v-for="section in sections" :key="section.key" :aria-label="section.label">
        <h2>{{ section.label }}</h2>
        <p>{{ section.description }}</p>
        <template-definition-editor v-if="definitionKinds[section.key]" :kind="definitionKinds[section.key]!" :value="form.sections[section.key]" :disabled="disabled" @changed="form.sections[section.key] = $event; invalidatePreview()" />
        <el-form-item :label="section.label + '完整定义（JSON）'" :error="errors[section.key]">
          <el-input v-model="form.sections[section.key]" type="textarea" :autosize="{ minRows: 5, maxRows: 18 }" :disabled="disabled" @input="invalidatePreview" />
        </el-form-item>
      </section>
      <el-form-item label="创建或修订理由" :error="errors.reason"><el-input v-model="form.reason" type="textarea" maxlength="2000" :disabled="disabled" @input="invalidatePreview" /></el-form-item>
      <el-form-item label="完整定义确认" :error="errors.attested"><el-checkbox v-model="attested" :disabled="disabled">已经核查全部目录、字段、附件、条件、依据及版式；本次只保存草稿</el-checkbox></el-form-item>
      <el-alert v-if="errors.schema" :title="errors.schema" type="error" :closable="false" />
      <el-button :disabled="disabled" @click="review">校验完整定义</el-button>
      <el-button native-type="submit" type="primary" :disabled="disabled">{{ pending ? '使用原请求保存模板草稿' : '保存模板草稿' }}</el-button>
      <p v-if="pending && !mayReplay">原请求的上下文或查询状态需要核查，不能创建新的请求代替原操作。</p>
      <section v-if="candidate" aria-label="本次完整草稿预览">
        <el-descriptions title="本次完整草稿" :column="1" border>
          <el-descriptions-item label="目录节点数量">{{ candidate.content.nodes.length }}</el-descriptions-item>
          <el-descriptions-item label="字段数量">{{ candidate.content.field_definitions.length }}</el-descriptions-item>
          <el-descriptions-item label="重复表格数量">{{ candidate.content.repeat_definitions.length }}</el-descriptions-item>
          <el-descriptions-item label="附件要求数量">{{ candidate.content.attachment_requirements.length }}</el-descriptions-item>
        </el-descriptions>
        <el-collapse><el-collapse-item title="完整定义及固定依据" name="candidate"><pre>{{ full(candidate) }}</pre></el-collapse-item></el-collapse>
      </section>
    </el-form>
    <el-dialog v-model="discardDialog" title="离开模板编辑" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p>未保存定义和理由会清除。已发送请求的身份继续保留，离开页面不会取消服务器操作。</p>
      <template #footer><el-button @click="discardDialog = false">继续编辑</el-button><el-button :disabled="user.medicalWriting" @click="discard">放弃未保存编辑并离开</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance, UploadFile } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import SessionPanel from './session-panel.vue'
import TemplateDefinitionEditor from './template-definition-editor.vue'
import useUserStore from '@/store/modules/user'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { readBoundedJson } from '@/api/medical/project-json'
import { draftJsonIssue, templateCreateInputIssue } from './template-collection-input'
import { projectTextIssue } from '@/api/medical/project-write'
import { canonicalTemplateValue, templateApprovalAllowed } from '@/api/medical/template-approval'
import { confirmTemplateCreate, prepareTemplateCreate, readPendingTemplateCreate, submitTemplateCreate, templateCreateAllowed, validateTemplateCreate } from '@/api/medical/template-create'
import type { PendingTemplateCreate, TemplateCreateBody, TemplateCreated } from '@/api/medical/template-create'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'

type SectionKey = 'nodes' | 'field_definitions' | 'repeat_definitions' | 'attachment_requirements' | 'cross_document_rules' | 'condition_schema' | 'basis_manifest' | 'format_profile'
const definitionKinds: Partial<Record<SectionKey, 'Node' | 'Field' | 'Repeat' | 'Attachment' | 'CrossDocumentRule'>> = {
  nodes: 'Node', field_definitions: 'Field', repeat_definitions: 'Repeat', attachment_requirements: 'Attachment', cross_document_rules: 'CrossDocumentRule'
}
const sections: { key: SectionKey; label: string; description: string }[] = [
  { key: 'nodes', label: '完整目录', description: '保留稳定节点标识、官方编号、标题、父节点、顺序、条件、证据规则、依据及必需内容。企业补充节点标明来源及性质。' },
  { key: 'field_definitions', label: '全部字段', description: '保留十一类字段、长度、枚举、精度、单位、范围依据、条件必填、证据规则及输出位置；不得填写无依据的产品默认值。' },
  { key: 'repeat_definitions', label: '重复表格', description: '保存稳定表格和字段标识、完整列关系、最少及最多次数和依据。各型号和配置不得合并为单一文本。' },
  { key: 'attachment_requirements', label: '证据附件', description: '保存完整附件类型、节点、条件、数量及依据、媒体类型、证据规则和顺序。附件只能使用获授权原件。' },
  { key: 'cross_document_rules', label: '跨文档关系', description: '保存固定规则版本、字段、全部关联文档类型及问题等级。' },
  { key: 'condition_schema', label: '适用条件', description: '保存完整属性集合、固定适用规则与引擎版本。未知结果必须保持UNKNOWN，项目人工适用决定单独处理。' },
  { key: 'basis_manifest', label: '全部依据', description: '保存实际依据版本和SHA-256摘要，服务器核查当前授权、核准及来源权利。' },
  { key: 'format_profile', label: '版式和输出', description: '保存固定版式与编号规则、DOCX或PDF类型、获授权字体、各节点显示规则及原附件顺序。' }
]
const documentLabels: Record<string, string> = { PRODUCT_TECHNICAL_REQUIREMENTS: '产品技术要求', REGISTRATION_DIRECTORY: '注册文档目录', OVERVIEW: '综述资料',
  INSTRUCTIONS_FOR_USE: '说明书', LABEL: '标签样稿', ESSENTIAL_PRINCIPLES: '基本原则清单', RISK_MANAGEMENT: '风险管理', RESEARCH_REPORT: '研究报告',
  CLINICAL_EVALUATION: '临床评价', QUALITY_SYSTEM: '质量体系', CHANGE_REGISTRATION: '变更资料', RENEWAL_REGISTRATION: '延续资料', REGISTRATION_DOSSIER: '注册资料总稿' }
const pathLabels: Record<string, string> = { FIRST_REGISTRATION: '首次注册', CHANGE_REGISTRATION: '变更注册', RENEWAL_REGISTRATION: '延续注册' }
const lookupLabels = { FOUND: '已经找到原操作', NOT_FOUND: '当前未找到原操作，不能据此判定请求没有发送', EXPIRED: '原查询记录已过期，需要人工核查；禁止自动重发' }
const operationLabels = { PROCESSING: '正在执行', COMPLETED: '已经完成', FAILED: '执行失败，原请求继续保留并需要核查' }
const user = useUserStore(); const route = useRoute(); const router = useRouter(); const formElement = ref<FormInstance>()
const form = reactive({ document_type: '', application_path: '', ivd: '', directory_profile: '', base_mode: '', base_id: '', base_hash: '', reason: '',
  sections: { nodes: '', field_definitions: '', repeat_definitions: '', attachment_requirements: '', cross_document_rules: '', condition_schema: '', basis_manifest: '', format_profile: '' } })
const errors = reactive<Record<string, string>>({}); const attested = ref(false); const candidate = ref<TemplateCreateBody | null>(null)
const pending = ref<PendingTemplateCreate | null>(null); const created = ref<TemplateCreated | null>(null); const lookupResult = ref<OperationLookup | null>(null)
const checking = ref(false); const discardDialog = ref(false); const permitted = computed(() => templateCreateAllowed(user.medicalSession))
const mayReplay = computed(() => pending.value === null || user.medicalSession?.current_context
  && canonicalTemplateValue(user.medicalSession.current_context) === canonicalTemplateValue(pending.value.context)
  && lookupResult.value?.lookup_status !== 'EXPIRED' && lookupResult.value?.operation?.status !== 'PROCESSING' && lookupResult.value?.operation?.status !== 'FAILED')
const disabled = computed(() => !permitted.value || user.medicalWriting || checking.value || !mayReplay.value)
let generation = 0; let nextTarget: string | null = null; let leaveApproved = false
function full(value: unknown): string { return JSON.stringify(value, null, 2) }
function invalidatePreview(): void { candidate.value = null }
function clearForm(): void {
  form.document_type = ''; form.application_path = ''; form.ivd = ''; form.directory_profile = ''; form.base_mode = ''; form.base_id = ''; form.base_hash = ''; form.reason = ''
  for (const section of sections) form.sections[section.key] = ''
  for (const key of Object.keys(errors)) delete errors[key]
  attested.value = false; candidate.value = null
}
function read(): void {
  generation += 1; clearForm(); pending.value = null; created.value = null; lookupResult.value = null; checking.value = false; discardDialog.value = false
  const session: MedicalSession | null = user.medicalSession
  if (session?.current_context && !document.hidden) pending.value = readPendingTemplateCreate(session)
}
async function focusError(): Promise<void> {
  await nextTick(); const element = (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea,.el-form-item.is-error [tabindex]')
  element?.scrollIntoView({ block: 'nearest' }); element?.focus()
}
async function body(): Promise<TemplateCreateBody | null> {
  candidate.value = null; for (const key of Object.keys(errors)) delete errors[key]
  errors.document_type = schemaIssues('urn:medical:templates:1.0.0#/$defs/DocumentType', form.document_type).length ? '请选择实际文档类型' : ''
  errors.application_path = schemaIssues('urn:medical:templates:1.0.0#/$defs/ApplicationPath', form.application_path).length ? '请选择实际申请路径' : ''
  errors.ivd = ['IVD', 'NON_IVD'].includes(form.ivd) ? '' : '请确认实际IVD类别'
  errors.directory_profile = ['TYPE_SPECIFIC', 'NON_IVD_FIRST_REGISTRATION_2021_121'].includes(form.directory_profile) ? '' : '请选择对应目录规范'
  errors.base_mode = ['NEW', 'REVISION'].includes(form.base_mode) ? '' : '请选择版本依据'
  const base = form.base_mode === 'REVISION' ? { version_id: form.base_id, content_sha256: form.base_hash } : null
  errors.base_version = base && schemaIssues('urn:medical:templates:1.0.0#/$defs/Basis', base).length ? '请填写原模板固定版本和完整摘要' : ''
  errors.reason = projectTextIssue(form.reason, 2000)
  for (const section of sections) errors[section.key] = draftJsonIssue(form.sections[section.key])
  if (Object.values(errors).some(Boolean)) { await focusError(); return null }
  const value = { schema_version: '1.0.0', base_version: base, schema_ref: 'urn:medical:templates:1.0.0#/$defs/Content', reason: form.reason,
    content: { schema_version: '1.0.0', document_type: form.document_type, application_path: form.application_path, ivd: form.ivd === 'IVD',
      directory_profile: form.directory_profile === 'TYPE_SPECIFIC' ? null : form.directory_profile,
      ...Object.fromEntries(sections.map(section => [section.key, readBoundedJson(form.sections[section.key])])) } }
  const issues = schemaIssues('urn:medical:templates:1.0.0#/$defs/VersionInput', value)
  for (const issue of issues) {
    const section = sections.find(item => issue.path === '/content/' + item.key || issue.path.startsWith('/content/' + item.key + '/'))
    if (section) errors[section.key] = '请核查完整定义：' + issue.path + '（' + issue.code + '）'
  }
  if (issues.length) { errors.schema = '完整模板定义未通过字段规范，请核查全部错误位置和必需内容'; await focusError(); return null }
  const inputIssue = templateCreateInputIssue(value)
  if (inputIssue) { errors.schema = inputIssue; await focusError(); return null }
  const validated = validateTemplateCreate(value); candidate.value = validated; return validated
}
async function review(): Promise<void> { if (!disabled.value) await body() }
async function importDefinition(file: UploadFile): Promise<void> {
  const session = user.medicalSession; const current = generation
  if (disabled.value || !session || document.hidden) return
  errors.import = ''
  if (!file.raw) { errors.import = '导入模板缺少实际文件'; return }
  let text: string
  try { text = await file.raw.text() } catch { if (current === generation && user.medicalSession === session && !document.hidden) errors.import = '文件读取失败，请重新选择；现有输入已保留'; return }
  if (current !== generation || user.medicalSession !== session || document.hidden) return
  const syntax = draftJsonIssue(text)
  if (syntax) { errors.import = syntax; return }
  const input = readBoundedJson(text), issue = templateCreateInputIssue(input)
  if (issue) { errors.import = issue; return }
  const value = validateTemplateCreate(input)
  if (current !== generation || user.medicalSession !== session || document.hidden) return
  clearForm(); form.document_type = value.content.document_type; form.application_path = value.content.application_path
  form.ivd = value.content.ivd ? 'IVD' : 'NON_IVD'; form.directory_profile = value.content.directory_profile ?? 'TYPE_SPECIFIC'
  form.base_mode = value.base_version ? 'REVISION' : 'NEW'; form.base_id = value.base_version?.version_id ?? ''; form.base_hash = value.base_version?.content_sha256 ?? ''; form.reason = value.reason
  for (const section of sections) form.sections[section.key] = full(value.content[section.key])
  candidate.value = value
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const current = generation
  if (!session?.current_context || !original || checking.value || user.medicalWriting || document.hidden) return
  checking.value = true
  try {
    const result = await lookupOperation(session, original.operation, original.key, new AbortController().signal)
    if (current === generation && user.medicalSession === session && !document.hidden) lookupResult.value = result
  } finally { if (current === generation) checking.value = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const current = generation; const path = route.fullPath
  if (!session?.current_context || disabled.value || document.hidden) return
  const value = await body(); errors.attested = attested.value ? '' : '请核查完整定义并确认本次只保存草稿'
  if (!value || errors.attested) { await focusError(); return }
  if (generation !== current || user.medicalSession !== session) return
  user.medicalWriting = true
  try {
    const original = await prepareTemplateCreate(session, value); pending.value = original
    if (generation !== current || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '模板创建尚未发送，请核查当前身份')
    const result = await submitTemplateCreate(session, original, value)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) fail('CONTEXT_CHANGED', '模板创建已返回，当前主体或企业需要核查')
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    // 原key取得固定创建回执，同时由服务器重新核查当前完整依赖许可。
    const reloaded = await submitTemplateCreate(fresh, original, value)
    if (canonicalTemplateValue(result) !== canonicalTemplateValue(reloaded)) fail('CONTENT_MISMATCH', '原模板创建回执在重新核查以后不一致')
    await confirmTemplateCreate(fresh, original, reloaded)
    if (route.fullPath !== path || user.medicalSession !== fresh || document.hidden) return
    clearForm(); pending.value = null; lookupResult.value = null; created.value = reloaded
  } finally { user.medicalWriting = false }
}
function dirty(): boolean { return Boolean(form.document_type || form.application_path || form.ivd || form.directory_profile || form.base_mode || form.base_id || form.base_hash || form.reason || attested.value || Object.values(form.sections).some(Boolean)) }
async function discard(): Promise<void> {
  const destination = nextTarget; clearForm(); discardDialog.value = false; nextTarget = null
  if (destination !== null) { leaveApproved = true; await router.push(destination); leaveApproved = false }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty()) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(to => {
  if (leaveApproved || !dirty()) return true
  if (user.medicalWriting) return false
  nextTarget = to.fullPath; discardDialog.value = true; return false
})
watch(() => user.medicalSession, read, { immediate: true, flush: 'sync' })
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); generation += 1; clearForm(); created.value = null; pending.value = null; lookupResult.value = null })
</script>

<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 16px; }
</style>
