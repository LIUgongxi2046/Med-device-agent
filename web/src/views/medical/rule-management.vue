<template>
  <main class="medical-page">
    <h1>规则管理与人工审查</h1>
    <session-panel />
    <template v-if="!target">
      <rule-list />
      <h2>打开已知固定版本</h2>
      <p>也可从模板引用或保存的版本标识进入。</p>
      <el-form label-position="top" @submit.prevent="open">
        <el-form-item label="固定规则版本" :error="entryError"><el-input v-model="entry" :disabled="busy || !entryAllowed" /></el-form-item>
        <el-button native-type="submit" :disabled="busy || !entryAllowed">打开规则版本</el-button>
        <el-button :disabled="busy || !ruleAllowed(user.medicalSession, 'rule.write')" @click="router.push('/rules/new')">创建规则草稿</el-button>
      </el-form>
    </template>
    <p v-if="!permitted">规则读取与审查需要当前企业的明确规则权限</p>
    <p v-if="loading" role="status">正在读取固定规则与当前治理状态</p>
    <el-alert v-if="notice" :title="notice" type="warning" :closable="false" />
    <el-alert v-if="pending" title="原规则决定待核查" type="warning" :closable="false" data-rule-pending>
      <p>请求标识：{{ pending.key }}；原治理修订：{{ pending.expected_revision }}；原决定：{{ pending.decision ? ruleDecisionLabels[pending.decision] : '' }}</p>
      <p>未知结果不会显示为成功。不要建立新请求替代原操作；刷新后原理由需要重新填写。</p>
      <el-button :disabled="busy" @click="lookup">查询原规则决定</el-button>
      <p v-if="lookupResult">{{ lookupLabels[lookupResult.lookup_status] }}</p>
      <p v-if="lookupResult?.operation">操作编号：{{ lookupResult.operation.operation_id }}；状态：{{ lookupResult.operation.status }}</p>
      <el-button v-if="lookupResult?.operation?.status === 'COMPLETED'" :disabled="busy" @click="recover">核查完成操作与当前规则</el-button>
    </el-alert>
    <el-alert v-if="completed" title="原规则决定操作已核查完成" type="success" :closable="false" data-rule-completed>
      <p>操作编号：{{ completed.operation_id }}。下方显示独立读取的当前状态。</p>
      <p v-if="receipt">原决定：{{ ruleDecisionLabels[receipt.decision] }}；原回执治理修订：{{ receipt.revision }}；决定人员：{{ receipt.actor_id }}</p>
    </el-alert>
    <section v-if="stored && permitted" data-rule-preview aria-label="固定规则完整内容">
      <el-descriptions :column="1" border title="固定规则版本">
        <el-descriptions-item label="规则版本">{{ stored.rule_version }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ ruleStatusLabels[stored.status] }}</el-descriptions-item>
        <el-descriptions-item label="治理修订">{{ stored.revision }}</el-descriptions-item>
        <el-descriptions-item label="固定引擎">{{ stored.content.engine_revision }}</el-descriptions-item>
        <el-descriptions-item label="创建理由">{{ stored.reason }}</el-descriptions-item>
        <el-descriptions-item label="创建人员">{{ stored.created_by }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ stored.created_at }}</el-descriptions-item>
        <el-descriptions-item label="内容摘要">{{ stored.content_sha256 }}</el-descriptions-item>
      </el-descriptions>
      <h2>规则表达式</h2><pre>{{ stored.content.expression.expression }}</pre>
      <p>本页未执行项目适用判断。缺失或未知输入继续保持UNKNOWN，不能替代人工决定。</p>
      <h2>输入属性</h2>
      <ul><li v-for="key in stored.content.expression.input_keys" :key="key">{{ key }}</li></ul>
      <p v-if="!stored.content.expression.input_keys.length">此固定规则未声明输入属性</p>
      <h2>有据常量</h2>
      <el-table :data="stored.content.expression.constants" row-key="key" border>
        <el-table-column prop="key" label="常量标识" min-width="160" />
        <el-table-column label="实际值"><template #default="scope"><pre>{{ full(scope.row.value) }}</pre></template></el-table-column>
        <el-table-column type="expand"><template #default="scope"><pre>{{ full(scope.row.basis_refs) }}</pre></template></el-table-column>
      </el-table>
      <h2>全部固定依据</h2>
      <el-table :data="stored.content.basis_manifest" row-key="version_id" border>
        <el-table-column prop="version_id" label="依据固定版本" min-width="280" />
        <el-table-column prop="content_sha256" label="SHA-256摘要" min-width="300" />
      </el-table>
      <el-collapse>
        <el-collapse-item title="原版本与完整依赖"><pre>{{ full({ base_version: stored.base_version, dependency_refs: stored.dependency_refs }) }}</pre></el-collapse-item>
        <el-collapse-item v-if="preview" title="当前审查基线"><pre>{{ full(preview.baseline_manifest) }}</pre></el-collapse-item>
        <el-collapse-item title="完整规则定义"><pre>{{ full(stored.content) }}</pre></el-collapse-item>
      </el-collapse>
      <el-button v-if="decisions.length" :disabled="busy || !mayWrite" data-rule-decide @click="openDecision">登记规则人工决定</el-button>
      <p v-if="pending && !mayWrite">当前规则或上下文与原决定不一致，请查询原操作；不会覆盖原请求。</p>
      <p v-if="stored.status === 'DISABLED'">规则已停用；此版本不可继续作出人工决定。</p>
      <el-button :disabled="busy || dirty()" @click="read">重新读取当前规则</el-button>
    </section>
    <router-link v-if="target" to="/rules">返回规则管理入口</router-link>
    <el-dialog v-model="dialog" title="确认规则人工决定" :close-on-click-modal="false" :close-on-press-escape="!user.medicalWriting" :before-close="closeDialog">
      <el-form v-if="preview" ref="formElement" label-position="top" @submit.prevent="submit">
        <el-form-item label="规则人工决定" :error="errors.decision">
          <el-select v-model="decision" :disabled="busy || pending !== null"><el-option v-for="value in decisions" :key="value" :label="ruleDecisionLabels[value]" :value="value" /></el-select>
        </el-form-item>
        <el-form-item label="决定理由" :error="errors.reason"><el-input v-model="reason" type="textarea" maxlength="2000" :rows="4" :disabled="busy" /></el-form-item>
        <el-form-item label="完整规则审查确认" :error="errors.attested"><el-checkbox v-model="attested" :disabled="busy">已经核查规则表达式、输入属性、全部常量及获授权依据</el-checkbox></el-form-item>
        <p>固定规则：{{ preview.rule.rule_version }}；治理修订：{{ preview.rule.revision }}</p>
        <p>确认提交前重新读取当前基线。如内容、修订或依据授权变化，需要重新审查确认。</p>
        <el-button native-type="submit" type="primary" :disabled="busy || !mayWrite">{{ pending ? '使用原请求确认规则决定' : '确认登记规则决定' }}</el-button>
      </el-form>
    </el-dialog>
    <el-dialog v-model="discardDialog" title="未保存的规则决定" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p>未保存理由会清除。原请求身份继续保留，离开不会取消已发送的操作。</p>
      <template #footer><el-button @click="discardDialog = false">继续填写</el-button><el-button :disabled="user.medicalWriting" @click="discard">放弃未保存输入</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import RuleList from './rule-list.vue'
import { fail, schemaIssues } from '@/api/medical/protocol'
import type { MedicalSession } from '@/api/medical/protocol'
import { projectTextIssue } from '@/api/medical/project-write'
import { canonicalRule, clearRuleOperation, confirmRuleOperation, prepareRuleWrite, readPendingRule, readRule, readRulePreview, ruleAllowed,
  ruleDecisions, ruleDecisionLabels, ruleSchema, ruleStatusLabels, submitRuleApproval } from '@/api/medical/rule-versions'
import type { PendingRuleWrite, RuleApprovalBody, RuleDecision, RulePreview, RuleReceipt, StoredRule } from '@/api/medical/rule-versions'
import { lookupOperation } from '@/api/medical/operations'
import type { Operation, OperationLookup } from '@/api/medical/operations'

const user = useUserStore(); const route = useRoute(); const router = useRouter(); const formElement = ref<FormInstance>()
const target = computed(() => typeof route.params.ruleId === 'string' ? route.params.ruleId : null)
const entryAllowed = computed(() => ruleAllowed(user.medicalSession, 'rule.read') || ruleAllowed(user.medicalSession, 'rule.approve'))
const permitted = computed(() => ruleAllowed(user.medicalSession, 'rule.read', target.value ?? undefined) || ruleAllowed(user.medicalSession, 'rule.approve', target.value ?? undefined))
const stored = shallowRef<StoredRule | null>(null); const preview = shallowRef<RulePreview | null>(null); const loading = ref(false); const checking = ref(false)
const pending = ref<PendingRuleWrite | null>(null); const lookupResult = ref<OperationLookup | null>(null); const receipt = ref<RuleReceipt | null>(null); const completed = ref<Operation | null>(null)
const entry = ref(''); const entryError = ref(''); const notice = ref(''); const dialog = ref(false); const discardDialog = ref(false)
const decision = ref<RuleDecision | ''>(''); const reason = ref(''); const attested = ref(false); const errors = reactive({ reason: '', decision: '', attested: '' })
const busy = computed(() => user.medicalWriting || checking.value || loading.value)
const decisions = computed(() => preview.value ? ruleDecisions(preview.value) : [])
const mayWrite = computed(() => !!preview.value && ruleAllowed(user.medicalSession, 'rule.approve', target.value ?? undefined) && (!pending.value
  || pending.value.expected_revision === preview.value.rule.revision && pending.value.content_sha256 === preview.value.rule.content_sha256
    && canonicalRule(user.medicalSession?.current_context ?? null) === canonicalRule(pending.value.context)
    && lookupResult.value?.lookup_status !== 'EXPIRED' && !['PROCESSING', 'FAILED', 'COMPLETED'].includes(lookupResult.value?.operation?.status ?? '')))
const lookupLabels = { FOUND: '已找到原操作', NOT_FOUND: '当前未找到原操作，不能判定请求没有发送', EXPIRED: '原查询记录已过期，需要人工核查，不能自动重发' }
let generation = 0; let controller: AbortController | null = null; let nextTarget: string | null = null; let dialogDone: (() => void) | null = null; let leaveApproved = false; let active = true
function full(value: unknown): string { return JSON.stringify(value, null, 2) }
function clearForm(): void { decision.value = ''; reason.value = ''; attested.value = false; errors.reason = ''; errors.decision = ''; errors.attested = '' }
function clear(): void {
  generation += 1; controller?.abort(); controller = null; stored.value = null; preview.value = null; pending.value = null; lookupResult.value = null
  receipt.value = null; completed.value = null; loading.value = false; checking.value = false; dialog.value = false; discardDialog.value = false; notice.value = ''; entry.value = ''; entryError.value = ''; clearForm()
}
async function read(): Promise<void> {
  clear(); const session: MedicalSession | null = user.medicalSession; const id = target.value
  if (!session?.current_context || !id || !permitted.value || document.hidden || user.medicalWriting) return
  const expectedHash = route.query.content_sha256
  if (expectedHash !== undefined && schemaIssues(ruleSchema + 'Hash', expectedHash).length) { notice.value = '引用的规则摘要无效，未读取规则'; return }
  pending.value = readPendingRule(session, id); decision.value = pending.value?.decision ?? ''
  const current = generation; controller = new AbortController(); loading.value = true
  try {
    const result = ruleAllowed(session, 'rule.approve', id) ? await readRulePreview(session, id, controller.signal) : await readRule(session, id, controller.signal)
    if (current !== generation || user.medicalSession !== session || document.hidden || target.value !== id) return
    if (expectedHash !== undefined && result.rule.content_sha256 !== expectedHash) fail('CONTENT_MISMATCH', '规则内容与引用的固定摘要不一致')
    stored.value = result.rule; preview.value = 'baseline_manifest' in result ? result : null
  } finally { if (current === generation) loading.value = false }
}
async function open(): Promise<void> {
  if (!entryAllowed.value || busy.value) return
  entryError.value = schemaIssues(ruleSchema + 'Id', entry.value).length ? '请填写规范的固定规则版本标识' : ''
  if (!entryError.value) await router.push('/rules/' + entry.value)
}
async function openDecision(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const id = target.value; const current = generation
  if (!session?.current_context || !id || busy.value || !mayWrite.value || document.hidden) return
  checking.value = true
  try {
    const value = await readRulePreview(session, id, new AbortController().signal)
    if (current !== generation || user.medicalSession !== session || document.hidden) return
    preview.value = value; stored.value = value.rule; attested.value = false; notice.value = ''
    if (mayWrite.value && ruleDecisions(value).length) dialog.value = true
    else notice.value = '规则当前状态或基线已经变化，请核查原操作和最新内容。'
  } finally { if (current === generation) checking.value = false }
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const current = generation
  if (!session?.current_context || !original || busy.value || document.hidden) return
  checking.value = true
  try { const result = await lookupOperation(session, original.operation, original.key, new AbortController().signal)
    if (current === generation && user.medicalSession === session && !document.hidden) lookupResult.value = result
  } finally { if (current === generation) checking.value = false }
}
async function recover(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const found = lookupResult.value?.operation; const current = generation
  if (!session?.current_context || !original?.target_id || found?.status !== 'COMPLETED' || busy.value || document.hidden) return
  checking.value = true
  try {
    const operation = await confirmRuleOperation(session, original, found.operation_id, new AbortController().signal)
    const result = ruleAllowed(session, 'rule.approve', original.target_id) ? await readRulePreview(session, original.target_id, new AbortController().signal) : await readRule(session, original.target_id, new AbortController().signal)
    if (current !== generation || user.medicalSession !== session || document.hidden) return
    clearRuleOperation(session, original, operation, result.rule); pending.value = null; lookupResult.value = null; completed.value = operation
    stored.value = result.rule; preview.value = 'baseline_manifest' in result ? result : null; clearForm(); dialog.value = false
  } finally { if (current === generation) checking.value = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const value = preview.value; const id = target.value; const current = generation; const path = route.fullPath
  if (!session?.current_context || !value || !id || busy.value || !mayWrite.value || document.hidden) return
  errors.reason = projectTextIssue(reason.value, 2000); errors.decision = decision.value && decisions.value.includes(decision.value) ? '' : '请选择当前状态允许的人工决定'
  errors.attested = attested.value ? '' : '请确认已核查完整规则与依据'
  if (Object.values(errors).some(Boolean)) { await nextTick(); (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea,.el-form-item.is-error [tabindex]')?.focus(); return }
  if (!decision.value) return
  const body: RuleApprovalBody = { schema_version: '1.0.0', target_revision: id, expected_revision: value.rule.revision,
    decision: decision.value, reason: reason.value, baseline_manifest: value.baseline_manifest }
  user.medicalWriting = true
  try {
    const latest = await readRulePreview(session, id, new AbortController().signal)
    if (current !== generation || user.medicalSession !== session || document.hidden) return
    if (latest.rule.revision !== value.rule.revision || canonicalRule(latest.baseline_manifest) !== canonicalRule(value.baseline_manifest)
        || !ruleDecisions(latest).includes(body.decision)) {
      preview.value = latest; stored.value = latest.rule; attested.value = false
      notice.value = '当前规则或依据授权基线已变化，本次没有提交决定。请重新审查并确认。'; return
    }
    const original = await prepareRuleWrite(session, body); pending.value = original
    if (current !== generation || user.medicalSession !== session || document.hidden) fail('CONTEXT_CHANGED', '规则决定尚未发送，请重新核查身份')
    const result = await submitRuleApproval(session, original, body)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id) fail('CONTEXT_CHANGED', '原规则决定已返回，当前主体或企业需要核查')
    if (!active || route.fullPath !== path || document.hidden || user.medicalSession !== fresh) return
    const operation = await confirmRuleOperation(fresh, original, result.operation_id, new AbortController().signal)
    const actual = await readRulePreview(fresh, id, new AbortController().signal)
    if (actual.rule.content_sha256 !== result.content_sha256 || BigInt(actual.rule.revision) < BigInt(result.revision)
        || actual.rule.revision === result.revision && actual.rule.status !== result.status) fail('CONTENT_MISMATCH', '原规则决定回执与当前治理状态不一致')
    if (!active || route.fullPath !== path || document.hidden || user.medicalSession !== fresh) return
    clearRuleOperation(fresh, original, operation, actual.rule); pending.value = null; lookupResult.value = null
    receipt.value = result; completed.value = operation; stored.value = actual.rule; preview.value = actual; clearForm(); dialog.value = false
  } finally {
    user.medicalWriting = false
    if (active && user.medicalSession !== session && route.fullPath === path && user.medicalSession?.current_context && !document.hidden && completed.value === null) await read()
  }
}
function dirty(): boolean { return Boolean(reason.value || attested.value || dialog.value && decision.value) }
function closeDialog(done: () => void): void {
  if (user.medicalWriting) return
  if (dirty()) { nextTarget = null; dialogDone = done; discardDialog.value = true; return }; done()
}
async function discard(): Promise<void> {
  const destination = nextTarget; clearForm(); dialog.value = false; discardDialog.value = false; dialogDone?.(); dialogDone = null; nextTarget = null
  if (destination !== null) { leaveApproved = true; await router.push(destination); leaveApproved = false }
}
function guard(to: { fullPath: string }): boolean {
  if (user.medicalWriting) return false
  if (leaveApproved || !dirty()) return true
  nextTarget = to.fullPath; dialogDone = null; discardDialog.value = true; return false
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty() || user.medicalWriting) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(guard); onBeforeRouteUpdate(guard)
watch(() => [user.medicalSession, route.fullPath], read, { immediate: true, flush: 'sync' })
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { active = false; window.removeEventListener('beforeunload', beforeUnload); clear() })
</script>
<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; }
section { margin: 20px 0; }
</style>
