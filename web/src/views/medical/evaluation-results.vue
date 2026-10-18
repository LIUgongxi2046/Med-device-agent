<template>
  <main class="medical-page">
    <h1>评测与结果</h1><session-panel />
    <p>登记评测资料、记录评分并查看结果。</p>
    <el-alert title="当前处理已有输出，候选运行需另行完成。" type="info" :closable="false" />
    <p v-if="!session?.current_context">请先选择获授权企业空间。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" data-evaluation-error />
    <p v-if="busy" role="status" aria-live="polite">正在核查当前评测与操作结果…</p>
    <el-alert v-if="completed" title="记录已保存" type="success" :closable="false" data-evaluation-completed><p>操作编号：{{ completed }}</p></el-alert>
    <section v-if="pending" aria-label="原评测请求恢复" data-evaluation-pending>
      <h2>待确认的提交</h2>
      <p>类型：{{ operationLabels[pending.operation] }}；请求标识：{{ pending.key }}；原修订：{{ pending.expected_revision ?? '新建' }}</p>
      <p>提交结果尚待确认。刷新后如需重试，请重新填写原参数。</p>
      <el-button :disabled="busy" @click="lookup">查询原评测操作</el-button>
      <p v-if="lookupResult">{{ lookupLabels[lookupResult.lookup_status] }}</p>
      <p v-if="lookupResult?.operation">操作编号：{{ lookupResult.operation.operation_id }}；状态：{{ lookupResult.operation.status }}</p>
      <el-button v-if="lookupResult?.operation?.status === 'COMPLETED'" :disabled="busy" @click="recover">核查原操作并回读结果</el-button>
      <p v-if="pendingMismatch">此提交与当前批次或企业不一致，请先查询原操作。</p>
    </section>
    <section v-if="!target && !creating" aria-label="评测批次入口">
      <h2>打开已有评测批次</h2>
      <p>使用批次编号查看已保存的定义、评分和结果。当前接口没有评测批次列表；尚未打开批次时，不显示估算指标。</p>
      <el-form label-position="top" @submit.prevent="open">
        <el-form-item label="评测批次标识"><el-input id="evaluation-entry" v-model="entry" :disabled="busy || !mayRead" /></el-form-item>
        <el-button native-type="submit" :disabled="busy || !mayRead">打开批次</el-button>
        <el-button :disabled="busy || !mayCreate" @click="router.push('/evaluation/new')">登记固定定义</el-button>
      </el-form>
      <p v-if="!mayRead">当前身份没有明确的评测读取权限。</p>
      <p v-if="!mayCreate">登记需要当前企业或项目的评测创建与读取权限。</p>
    </section>
    <section v-if="creating" aria-label="登记固定评测定义">
      <h2>登记固定评测定义</h2>
      <evaluation-definition-form :key="formKey" ref="definitionForm" :disabled="busy" @preview="previewForm" @change="invalidatePreview" />
      <el-collapse><el-collapse-item title="高级：导入完整定义 JSON">
      <p>导入已有评测定义，核查完整样本和依据后保存。</p>
      <el-collapse><el-collapse-item title="导入格式与准备要求"><p>使用完整Create请求JSON，包含数据集、样本清单、预期项目、候选、评分规则、授权用途及来源环境。UUID和SHA-256须对应已有记录。</p><p>仅填写固定引用和评测元数据，不要粘贴原文、凭据或个人敏感信息。原始文本只保留在当前页面内存。</p></el-collapse-item></el-collapse>
      <el-form label-position="top" @submit.prevent="previewCreate">
        <el-form-item label="固定评测定义（JSON）"><el-input id="evaluation-create-json" v-model="definitionText" type="textarea" :rows="10" :disabled="busy" /></el-form-item>
        <el-button native-type="submit" :disabled="busy || !definitionText">校验并核查固定定义</el-button>
      </el-form>
      </el-collapse-item></el-collapse>
      <template v-if="createPreview">
        <evaluation-definition :definition="createPreview" />
        <el-checkbox v-model="createAttested" :disabled="busy">已核查完整样本、来源分组和授权依据。</el-checkbox>

        <el-button type="primary" :disabled="busy || !createAttested || !canWrite('EV-01')" @click="create">{{ pending ? '按原参数重试登记' : '确认登记固定定义' }}</el-button>
      </template>
    </section>
    <section v-if="view && target" aria-label="评测批次详情" data-evaluation-detail>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="批次">{{ view.evaluation_id }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ evaluationStatusLabels[view.status] }}</el-descriptions-item>
        <el-descriptions-item label="当前修订">{{ view.revision }}</el-descriptions-item>
        <el-descriptions-item label="定义SHA-256">{{ view.content_sha256 }}</el-descriptions-item>
        <el-descriptions-item label="登记人员/时间">{{ view.definition.created_by }} / {{ view.definition.created_at }}</el-descriptions-item>
        <el-descriptions-item label="生产准入">尚未批准</el-descriptions-item>
      </el-descriptions>
      <div class="evaluation-actions"><el-button :disabled="busy || dirty()" @click="refresh(false)">重新读取完整批次</el-button><el-button :disabled="busy || dirty()" @click="refresh(true)">刷新评分历史与结果</el-button></div>
      <el-tabs v-model="tab">
        <el-tab-pane label="总览、比较与审计" name="overview"><evaluation-insights :view="view" /></el-tab-pane>
        <el-tab-pane label="固定定义与来源" name="definition">
          <evaluation-definition :definition="view.definition.request" :approved-samples="view.definition.approved_expectation_samples" />
          <el-collapse><el-collapse-item title="实际候选依赖与限制快照"><pre>{{ full(view.definition.candidate_snapshots) }}</pre></el-collapse-item></el-collapse>
        </el-tab-pane>
        <el-tab-pane label="评分历史与修订" name="scores">
          <p>共{{ view.score_history.length }}条历史记录，{{ effectiveCount }}个有效评分。</p>
          <el-table :data="historyPage" row-key="score_id" border data-evaluation-history>
            <el-table-column prop="score_id" label="评分标识" min-width="290" /><el-table-column prop="observation.cell.dimension" label="维度" width="110" />
            <el-table-column prop="observation.status" label="观察状态" width="120" />
            <el-table-column label="分子 / 分母" width="150"><template #default="scope">{{ scope.row.observation.numerator ?? '未知' }} / {{ scope.row.observation.denominator ?? '未知' }}</template></el-table-column>
            <el-table-column prop="scorer_id" label="评分人员" min-width="290" />
            <el-table-column label="修订关系" min-width="290"><template #default="scope">{{ scope.row.observation.supersedes ? '修订 ' + scope.row.observation.supersedes : '初始观察' }}</template></el-table-column>
            <el-table-column prop="observation.reason" label="理由" min-width="200" /><el-table-column type="expand"><template #default="scope"><pre>{{ full(scope.row) }}</pre></template></el-table-column>
          </el-table>
          <p v-if="!view.score_history.length">尚无评分。</p>
          <el-pagination v-if="view.score_history.length > 20" v-model:current-page="historyPageNumber" :page-size="20" :total="view.score_history.length" layout="prev, pager, next" />
          <evaluation-score-editor v-if="!view.result && allowed('evaluation.score')" :key="view.revision" ref="scoreEditor" :view="view" :actor="session!.user.user_id" :disabled="busy || !canWrite('EV-02')" @submit="append" />
          <p v-else-if="!view.result">当前身份没有此批次的评分权限。</p><p v-else>批次结果已固定，评分历史只读。</p>
        </el-tab-pane>
        <el-tab-pane label="固定结果与缺项" name="result">
          <template v-if="view.result">
            <h2>{{ evaluationVerdictLabels[view.result.verdict] }}</h2>
            <p>结果SHA-256：{{ view.result_sha256 }}；有效评分集SHA-256：{{ view.result.score_set_sha256 }}</p>
            <h3>缺失要求</h3><ul v-if="view.result.missing_requirements.length"><li v-for="item in view.result.missing_requirements" :key="item">{{ evaluationRequirementLabel(item) }}</li></ul><p v-else>本次结果未列出证据缺项。</p>
            <el-table :data="view.result.dimensions" row-key="dimension" border><el-table-column prop="dimension" label="维度" /><el-table-column prop="expected_cells" label="应有单元" /><el-table-column prop="observed_cells" label="已观察" /><el-table-column prop="passed_cells" label="通过" /><el-table-column prop="failed_cells" label="未通过" /><el-table-column prop="insufficient_cells" label="证据不足" /></el-table>
            <h3>原始结果单元</h3>
            <el-table :data="resultPage" border><el-table-column prop="cell.sample_id" label="固定样本" min-width="290" /><el-table-column prop="cell.agent_version_id" label="候选版本" min-width="290" /><el-table-column prop="cell.dimension" label="维度" width="110" /><el-table-column prop="cell.repeat_index" label="重复索引" width="110" /><el-table-column label="结论" width="150"><template #default="scope">{{ evaluationVerdictLabels[scope.row.status as EvaluationVerdict] }}</template></el-table-column><el-table-column label="实际值" width="100"><template #default="scope">{{ scope.row.value ?? '无可确认值' }}</template></el-table-column><el-table-column label="原因" min-width="250"><template #default="scope">{{ evaluationRequirementLabel(scope.row.reason) }}</template></el-table-column><el-table-column prop="score_id" label="对应评分" min-width="290" /></el-table>
            <el-pagination v-if="view.result.cells.length > 20" v-model:current-page="resultPageNumber" :page-size="20" :total="view.result.cells.length" layout="prev, pager, next" />
          </template>
          <template v-else>
            <p>尚无固定结果。缺失或未核准的证据将标记为证据不足。</p>
            <p>终结会固定当前结果，之后不能继续追加或修订评分。请先核查完整历史。</p>
            <el-checkbox v-model="finalizeAttested" :disabled="busy || !canWrite('EV-03')">已核查定义与评分，确认固定结果并将此批次评分转为只读。</el-checkbox>
            <div class="evaluation-actions"><el-button type="primary" :disabled="busy || !finalizeAttested || !canWrite('EV-03')" @click="finalize">确认计算并固定结果</el-button></div>
            <p v-if="!allowed('evaluation.finalize')">当前身份没有此批次的终结权限。</p>
          </template>
        </el-tab-pane>
      </el-tabs>
    </section>
    <router-link v-if="target || creating" to="/evaluation">返回评测入口</router-link>
    <el-dialog v-model="discardDialog" title="尚未保存的评测输入" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>离开会清除当前表单。已发送请求的身份继续保留，离开不会取消服务端操作。</p>
      <template #footer><el-button @click="discardDialog = false">继续填写</el-button><el-button :disabled="busy" @click="discard">放弃输入并离开</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from '@/api/medical/protocol'
import { schemaIssues, validate } from '@/api/medical/protocol'
import { evaluationCreateInputIssue, evaluationJsonInputIssue } from './evaluation-input'
import { lookupOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'
import { canonicalEvaluation, evaluationAllowed, evaluationFailureMessage, evaluationStatusLabels, evaluationVerdictLabels, evaluationViewSchema,
  effectiveEvaluationScores, evaluationRequirementLabel, parseEvaluationJson, prepareEvaluation, readPendingEvaluation, validateEvaluationCreate } from '@/api/medical/evaluation-domain'
import type { EvaluationAction, EvaluationAppend, EvaluationCreate, EvaluationFinalize, EvaluationRead, EvaluationVerdict, PendingEvaluation } from '@/api/medical/evaluation-domain'
import { readEvaluation, readEvaluationScores, recoverEvaluation, submitEvaluation } from '@/api/medical/evaluations'
import SessionPanel from './session-panel.vue'
import EvaluationDefinition from './evaluation-definition.vue'
import EvaluationScoreEditor from './evaluation-score-editor.vue'
import EvaluationDefinitionForm from './evaluation-definition-form.vue'
import EvaluationInsights from './evaluation-insights.vue'
const user = useUserStore(), route = useRoute(), router = useRouter()
const session = computed<MedicalSession | null>(() => user.medicalSession)
const target = computed(() => typeof route.params.evaluationId === 'string' ? route.params.evaluationId : null)
const creating = computed(() => route.meta.evaluationCreate === true)
const view = shallowRef<EvaluationRead | null>(null), createPreview = shallowRef<EvaluationCreate | null>(null), pending = ref<PendingEvaluation | null>(null)
const lookupResult = ref<OperationLookup | null>(null), entry = ref(''), definitionText = ref(''), createAttested = ref(false), finalizeAttested = ref(false)
const error = ref(''), completed = ref(''), working = ref(false), tab = ref('overview'), historyPageNumber = ref(1), resultPageNumber = ref(1)
const formKey = ref(0), definitionForm = ref<InstanceType<typeof EvaluationDefinitionForm>>()
const scoreEditor = ref<InstanceType<typeof EvaluationScoreEditor>>(), discardDialog = ref(false)
const busy = computed(() => working.value || user.medicalWriting)
const mayRead = computed(() => evaluationAllowed(session.value, 'evaluation.read'))
const mayCreate = computed(() => evaluationAllowed(session.value, 'evaluation.create') && mayRead.value)
const effectiveCount = computed(() => view.value ? effectiveEvaluationScores(view.value.definition, view.value.content_sha256, view.value.score_history).size : 0)
const historyPage = computed(() => view.value?.score_history.slice((historyPageNumber.value - 1) * 20, historyPageNumber.value * 20) ?? [])
const resultPage = computed(() => view.value?.result?.cells.slice((resultPageNumber.value - 1) * 20, resultPageNumber.value * 20) ?? [])
const operationLabels = { 'EV-01': '登记固定定义', 'EV-02': '追加评分/修订', 'EV-03': '计算并固定结果' }
const lookupLabels = { FOUND: '找到原操作。请按实际状态核查。', NOT_FOUND: '当前未找到原操作，不能据此判定请求没有发送；只能使用完全相同原参数重试。', EXPIRED: '原查询记录已过期，需要人工核查，不能自动重发。' }
const pendingMismatch = computed(() => !!pending.value && (!sameContext() || (pending.value.operation === 'EV-01' ? !creating.value : pending.value.target_id !== target.value)))
let generation = 0, active = true, allowLeave = false, nextTarget = ''; let controller = new AbortController()
function full(value: unknown): string { return JSON.stringify(value, null, 2) }
function sameContext(): boolean { return !!pending.value && canonicalEvaluation(pending.value.context) === canonicalEvaluation(session.value?.current_context ?? null) }
function allowed(action: EvaluationAction): boolean { return !!view.value && evaluationAllowed(session.value, action, view.value.definition.request.project_id, view.value.evaluation_id) }
function canWrite(operation: PendingEvaluation['operation']): boolean {
  if (!session.value?.current_context) return false
  if (operation === 'EV-01' ? !createPreview.value || !evaluationAllowed(session.value, 'evaluation.create', createPreview.value.project_id, null)
      || !evaluationAllowed(session.value, 'evaluation.read', createPreview.value.project_id, null) : !view.value || !!view.value.result || !allowed(operation === 'EV-02' ? 'evaluation.score' : 'evaluation.finalize') || !allowed('evaluation.read')) return false
  if (!pending.value) return true
  return sameContext() && pending.value.operation === operation && pending.value.target_id === (operation === 'EV-01' ? null : target.value)
    && (operation === 'EV-01' || pending.value.expected_revision === view.value?.revision && pending.value.definition_sha256 === view.value?.content_sha256)
    && lookupResult.value?.lookup_status === 'NOT_FOUND'
}
function current(token: number, original: MedicalSession): boolean { return active && token === generation && session.value === original && !document.hidden }
function dirty(): boolean { return Boolean(definitionForm.value?.dirty() || definitionText.value || createAttested.value || finalizeAttested.value || scoreEditor.value?.dirty()) }
function reset(): void {
  formKey.value++
  controller.abort(); controller = new AbortController(); generation++; view.value = null; createPreview.value = null; definitionText.value = ''; createAttested.value = false; finalizeAttested.value = false
  pending.value = null; lookupResult.value = null; error.value = ''; completed.value = ''; entry.value = ''; tab.value = 'overview'; historyPageNumber.value = 1; resultPageNumber.value = 1; working.value = false
}
async function load(): Promise<void> {
  reset(); const original = session.value, token = generation
  if (!original?.current_context || document.hidden) return
  try {
    pending.value = readPendingEvaluation(original)
    if (!target.value) return
    if (!evaluationAllowed(original, 'evaluation.read', undefined, target.value)) { error.value = '当前身份没有此批次的明确读取权限。'; return }
    working.value = true; const actual = await readEvaluation(original, target.value, controller.signal)
    if (current(token, original)) view.value = actual
  } catch (failure) { if (current(token, original)) error.value = evaluationFailureMessage(failure) }
  finally { if (current(token, original)) working.value = false }
}
async function open(): Promise<void> {
  if (busy.value || !mayRead.value) return
  if (schemaIssues(evaluationViewSchema + 'Id', entry.value).length) { error.value = '请填写有效的评测批次标识'; return }
  try { validate(evaluationViewSchema + 'Id', entry.value); await router.push('/evaluation/' + entry.value) } catch (failure) { error.value = evaluationFailureMessage(failure) }
}
function invalidatePreview(): void { createPreview.value = null; createAttested.value = false }
async function previewForm(value: EvaluationCreate): Promise<void> { await previewValue(value) }
async function previewCreate(): Promise<void> {
  if (busy.value || document.hidden) return
  invalidatePreview()
  const issue = evaluationJsonInputIssue(definitionText.value)
  if (issue) { error.value = issue; return }
  try { await previewValue(parseEvaluationJson(definitionText.value)) } catch (failure) { error.value = evaluationFailureMessage(failure) }
}
async function previewValue(input: unknown): Promise<void> {
  const original = session.value, token = generation; if (!original || busy.value || document.hidden) return
  working.value = true; error.value = ''; createPreview.value = null; createAttested.value = false
  try {
    const issue = evaluationCreateInputIssue(input)
    if (issue) { error.value = issue; return }
    const value = await validateEvaluationCreate(input); if (current(token, original)) createPreview.value = value }
  catch (failure) { if (current(token, original)) error.value = evaluationFailureMessage(failure) }
  finally { if (current(token, original)) working.value = false }
}
async function refresh(scores: boolean): Promise<void> {
  const original = session.value, previous = view.value, token = generation
  if (!original || !previous || busy.value || dirty() || document.hidden) return
  working.value = true; error.value = ''
  try { const actual = await (scores ? readEvaluationScores(original, previous, controller.signal) : readEvaluation(original, previous.evaluation_id, controller.signal)); if (current(token, original)) { view.value = actual; historyPageNumber.value = 1; resultPageNumber.value = 1 } }
  catch (failure) { if (current(token, original)) { view.value = null; error.value = evaluationFailureMessage(failure) } }
  finally { if (current(token, original)) working.value = false }
}
async function lookup(): Promise<void> {
  const original = session.value, saved = pending.value, token = generation; if (!original || !saved || busy.value || document.hidden) return
  working.value = true; error.value = ''
  try { const found = await lookupOperation(original, saved.operation, saved.key, controller.signal); if (current(token, original)) lookupResult.value = found }
  catch (failure) { if (current(token, original)) error.value = evaluationFailureMessage(failure) }
  finally { if (current(token, original)) working.value = false }
}
async function showCompleted(actual: EvaluationRead, operationId: string, original: MedicalSession): Promise<void> {
  formKey.value++; definitionText.value = ''; createPreview.value = null; createAttested.value = false; finalizeAttested.value = false; pending.value = null; lookupResult.value = null
  if (target.value !== actual.evaluation_id) { allowLeave = true; await router.push('/evaluation/' + actual.evaluation_id); allowLeave = false }
  if (!active || session.value !== original || document.hidden || target.value !== actual.evaluation_id) return
  view.value = actual; completed.value = operationId; tab.value = actual.result ? 'result' : 'scores'; historyPageNumber.value = 1; resultPageNumber.value = 1
}
async function recover(): Promise<void> {
  const original = session.value, saved = pending.value, found = lookupResult.value?.operation, token = generation
  if (!original || !saved || found?.status !== 'COMPLETED' || busy.value || document.hidden) return
  working.value = true; error.value = ''
  try { const actual = await recoverEvaluation(original, saved, found.operation_id, null, controller.signal); if (current(token, original)) await showCompleted(actual, found.operation_id, original) }
  catch (failure) { if (current(token, original)) error.value = evaluationFailureMessage(failure) }
  finally { if (active) working.value = false }
}
async function write(operation: PendingEvaluation['operation'], body: EvaluationCreate | EvaluationAppend | EvaluationFinalize): Promise<void> {
  const original = session.value, token = generation, previous = view.value
  if (!original || busy.value || !canWrite(operation) || document.hidden) return
  user.medicalWriting = true; error.value = ''; completed.value = ''
  try {
    // Re-read current revision before creating a write identity. Never silently rebase.
    const latest = operation === 'EV-01' ? null : await readEvaluation(original, previous!.evaluation_id, controller.signal)
    if (!current(token, original)) return
    if (latest && (latest.revision !== previous!.revision || latest.content_sha256 !== previous!.content_sha256 || latest.result)) {
      view.value = latest; finalizeAttested.value = false; error.value = '当前批次或评分已变化，本次没有提交。请核查最新历史后重新确认。'; return
    }
    const saved = await prepareEvaluation(original, operation, body, latest, controller.signal)
    if (!current(token, original)) return
    pending.value = saved; lookupResult.value = null
    const receipt = await submitEvaluation(original, saved, body, controller.signal)
    if (!current(token, original)) return
    const actual = await recoverEvaluation(original, saved, receipt.operation_id, receipt, controller.signal)
    if (!current(token, original)) return
    user.medicalWriting = false; await showCompleted(actual, receipt.operation_id, original)
  } catch (failure) { if (current(token, original)) { error.value = evaluationFailureMessage(failure); pending.value = readPendingEvaluation(original) } }
  finally { user.medicalWriting = false }
}
async function create(): Promise<void> { if (createPreview.value && createAttested.value) await write('EV-01', createPreview.value) }
async function append(body: EvaluationAppend): Promise<void> { await write('EV-02', body) }
async function finalize(): Promise<void> { if (view.value && finalizeAttested.value) await write('EV-03', { schema_version: '1.0.0', expected_revision: view.value.revision, definition_sha256: view.value.content_sha256 }) }
function guard(to: { fullPath: string }): boolean { if (user.medicalWriting) return false; if (allowLeave || !dirty()) return true; nextTarget = to.fullPath; discardDialog.value = true; return false }
async function discard(): Promise<void> { discardDialog.value = false; allowLeave = true; await router.push(nextTarget); allowLeave = false }
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty() || user.medicalWriting) { event.preventDefault(); event.returnValue = '' } }
function visibility(): void { if (document.hidden) reset(); else void load() }
watch(definitionText, invalidatePreview)
watch(() => [user.medicalSession, route.fullPath], load, { immediate: true, flush: 'sync' })
onBeforeRouteLeave(guard); onBeforeRouteUpdate(guard)
window.addEventListener('beforeunload', beforeUnload); document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { active = false; reset(); window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; padding: 12px; } p { overflow-wrap: anywhere; } .evaluation-actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 20px 0; } .el-pagination { margin: 20px 0; } .el-tabs { margin-top: 24px; } h3 { font-size: 15px; } .el-checkbox { height: auto; white-space: normal; margin: 12px 0; } :deep(.el-checkbox__label) { white-space: normal; }
</style>
