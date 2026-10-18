<template>
  <main ref="pageElement" class="medical-page simulation-model-configurations">
    <h1>模拟模型配置草稿</h1><session-panel />
    <el-alert type="warning" :closable="false" title="SIMULATION 模拟配置 · 仅保存模拟DRAFT草稿" description="案例标识和摘要仅为声明，尚未核验案例实际字节。保存不执行模拟、不证明运行成功，也不授予生产资格。" />
    <template v-if="user.medicalSession?.current_context">
      <section class="simulation-section" aria-label="当前模拟模型配置目录">
        <h2>当前模拟模型配置</h2>
        <el-form :inline="true" @submit.prevent="searchList">
          <el-form-item label="模拟模型、版本或配置标识" for="simulation-list-search" :error="listError"><el-input id="simulation-list-search" v-model="query" clearable maxlength="256" :disabled="busy" /></el-form-item>
          <el-form-item label="每页数量" for="simulation-list-limit"><el-select id="simulation-list-limit" v-model="limit" :disabled="busy"><el-option v-for="size in ['10','20','50','100']" :key="size" :label="size" :value="size" /></el-select></el-form-item>
          <el-button native-type="submit" :disabled="busy || !mayRead" :loading="listingLoading">查询配置</el-button>
        </el-form>
        <p v-if="listingLoading" role="status">正在读取当前获授权模拟模型配置</p>
        <template v-if="listing">
          <el-table :data="listing.items" row-key="config_id" data-simulation-list>
            <el-table-column label="执行方式" width="130"><template #default><el-tag>模拟</el-tag></template></el-table-column><el-table-column prop="definition.model_id" label="模拟模型" min-width="180" />
            <el-table-column prop="definition.model_version" label="版本" min-width="140" /><el-table-column label="范围" min-width="180"><template #default="entry">{{ entry.row.project_id ?? '本企业' }}</template></el-table-column>
            <el-table-column label="状态" min-width="230"><template #default><el-tag type="warning">模拟 · DRAFT · 不可生产使用</el-tag></template></el-table-column>
            <el-table-column label="固定草稿" width="150"><template #default="entry"><el-button :disabled="busy || dirty()" @click="readFixed(entry.row.model_config_revision)">查看固定版本</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="listing.items.length === 0 && listing.next_after === null" description="当前授权与筛选范围没有模拟模型配置" />
          <p v-if="listing.items.length === 0 && listing.next_after !== null">此扫描页没有可显示的授权项，可继续读取下一页。</p>
          <el-button :disabled="busy || !listing.next_after || listChanged" @click="nextList">读取下一页</el-button>
          <p v-if="listChanged">筛选或每页数量已修改，请重新查询配置。</p>
        </template>
      </section>
      <section v-if="pending" class="simulation-section" aria-label="原请求恢复">
        <h2>先核查原保存请求</h2><p>请求标识：{{ pending.key }}</p>
        <p>原目标：{{ pending.config_id ?? '新配置' }}；原维护修订：{{ pending.expected_revision }}。请求内容未存入浏览器。</p>
        <el-button :disabled="busy" :loading="checking" @click="lookup">查询原操作</el-button>
        <el-button v-if="lookupResult?.lookup_status === 'FOUND' && lookupResult.operation?.status === 'COMPLETED'" :disabled="busy" @click="recover">回读原固定草稿并核对</el-button>
        <el-button v-if="mayReplay" :disabled="busy" @click="resume">填写完全相同的原请求</el-button>
        <p v-if="lookupResult" role="status">{{ lookupMessage }}</p>
      </section>
      <section class="simulation-section" aria-label="读取固定模拟模型草稿">
        <h2>读取固定版本</h2>
        <p>也可填写保存回执中的固定修订标识，读取历史草稿。</p>
        <el-form :inline="true" @submit.prevent="readSelected">
          <el-form-item label="固定模拟模型修订标识（UUID）" for="simulation-read-id" :error="readError"><el-input id="simulation-read-id" v-model="readId" class="simulation-id-input" autocomplete="off" :disabled="busy" /></el-form-item>
          <el-button native-type="submit" :disabled="busy || !mayRead" :loading="reading">读取草稿</el-button>
          <el-button :disabled="busy || pending !== null || !mayCreate" @click="requestStart('NEW')">新建草稿</el-button>
        </el-form>
        <p v-if="!mayRead">当前身份缺少配置读取授权。</p>
        <p v-if="!mayCreate">新建需要对应企业或项目的model.configure授权；具体配置权限不能替代新建权限。</p>
        <p v-if="reading" role="status">正在读取当前授权下的固定版本</p>
      </section>
      <section v-if="view" class="simulation-section" aria-label="固定草稿详情">
        <div class="simulation-section-heading"><h2>固定草稿详情</h2><el-tag type="warning">模拟 · DRAFT · 不可生产使用</el-tag></div>
        <el-descriptions :column="1" border><el-descriptions-item v-for="row in detailRows" :key="row.label" :label="row.label"><span class="simulation-wrap">{{ row.value }}</span></el-descriptions-item></el-descriptions>
        <p>案例仅完成声明绑定（DECLARED_ONLY），实际案例字节由后续运行组件核验。内容摘要对应包含服务端授权元数据的完整固定记录，本页不能重算该完整摘要。</p>
        <p v-if="view.model_config_revision !== view.current_model_revision">这是历史固定版本。修订前须读取当前维护头，原固定版本仍保留。</p>
        <el-button v-if="view.model_config_revision !== view.current_model_revision" :disabled="busy || dirty()" @click="readFixed(view.current_model_revision)">读取当前维护头</el-button>
        <el-button :disabled="busy || pending !== null || !mayRevise" @click="requestStart('REVISE')">基于此固定版本修订草稿</el-button>
      </section>
      <p v-if="completed" role="status" class="simulation-complete">已确认原操作完成，并回读固定草稿{{ completed }}。状态仍为模拟DRAFT，未执行模拟，未获得生产资格。</p>
      <section v-if="editorOpen" class="simulation-section" aria-label="模拟模型草稿表单">
        <h2>{{ editingConfig ? '修订模拟模型草稿' : '新建模拟模型草稿' }}</h2>
        <p v-if="editingConfig">配置：{{ editingConfig }}；原维护修订：{{ expectedRevision }}；固定依据：{{ basedOn?.version_id }}</p>
        <el-form label-position="top" :disabled="busy" @submit.prevent>
          <el-form-item label="配置范围" :error="issue('/project_id')"><el-radio-group v-model="scope" :disabled="editingConfig !== null || pending !== null" aria-label="配置范围"><el-radio value="ENTERPRISE">本企业</el-radio><el-radio value="PROJECT">指定项目</el-radio></el-radio-group></el-form-item>
          <el-form-item v-if="scope === 'PROJECT'" label="项目标识（UUID）" for="simulation-project-id" :error="issue('/project_id')"><el-input id="simulation-project-id" v-model="projectId" :disabled="editingConfig !== null || pending !== null" /></el-form-item>
        </el-form>
        <simulation-model-configuration-fields ref="fields" :disabled="busy" :issues="issues" />
        <el-form label-position="top" :disabled="busy" @submit.prevent="submit">
          <el-form-item label="本次保存理由" for="simulation-reason" :error="issue('/reason')"><el-input id="simulation-reason" v-model="reason" type="textarea" :rows="3" maxlength="2000" autocomplete="off" /></el-form-item>
          <el-form-item label="保存范围确认"><el-checkbox v-model="attested">我确认以上为模拟配置及声明的案例绑定；本次仅保存草稿，不代表案例验证、执行成功或生产批准。</el-checkbox></el-form-item>
          <el-alert v-if="issues.length" type="error" :closable="false" :title="issues[0]!.message" />
          <p v-if="!maySave">当前范围缺少保存权限，或原请求尚未完成核查。</p>
          <div class="simulation-form-actions"><el-button type="primary" native-type="submit" :disabled="busy || !maySave || !attested" :loading="user.medicalWriting">{{ pending ? '沿用原请求标识保存' : '保存模拟DRAFT草稿' }}</el-button><el-button :disabled="busy" @click="requestStart('CLOSE')">清除表单</el-button></div>
        </el-form>
      </section>
    </template>
    <el-dialog v-model="discardDialog" title="清除未保存的模拟模型配置？" width="min(92vw,480px)" :close-on-click-modal="false">
      <p>表单只在当前页面内存中保存。清除后需要重新填写。已有待核查请求标识会保留。</p>
      <template #footer><el-button @click="cancelDiscard">继续编辑</el-button><el-button type="danger" @click="confirmDiscard">清除并继续</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import type { Basis, MedicalSession } from '@/api/medical/protocol'
import { fail, MedicalFailure, schemaIssues } from '@/api/medical/protocol'
import { canonicalSimulation, confirmSimulationResult, simulationAllowed, simulationInputIssues, simulationSchema, prepareSimulationSave, readPendingSimulation } from '@/api/medical/simulation-model-configuration-domain'
import type { SimulationIssue, SimulationReceipt, SimulationSave, SimulationView, PendingSimulationSave } from '@/api/medical/simulation-model-configuration-domain'
import { listSimulationConfigurations, readSimulationConfiguration, saveSimulationConfiguration } from '@/api/medical/simulation-model-configurations'
import { lookupOperation, readOperation } from '@/api/medical/operations'
import type { OperationLookup } from '@/api/medical/operations'
import type { SimulationListing } from '@/api/medical/simulation-model-configurations'
import SessionPanel from './session-panel.vue'
import SimulationModelConfigurationFields from './simulation-model-configuration-fields.vue'
const user = useUserStore(); const route = useRoute(); const router = useRouter()
const fields = ref<InstanceType<typeof SimulationModelConfigurationFields> | null>(null); const pageElement = ref<HTMLElement | null>(null)
const listing = ref<SimulationListing | null>(null); const listingLoading = ref(false); const query = ref(''); const limit = ref('20'); const listError = ref('')
const applied = ref<{ query: string; limit: string } | null>(null)
const listChanged = computed(() => applied.value !== null && (query.value !== applied.value.query || limit.value !== applied.value.limit))
const readId = ref(''); const readError = ref(''); const view = ref<SimulationView | null>(null)
const pending = ref<PendingSimulationSave | null>(null); const lookupResult = ref<OperationLookup | null>(null)
const reading = ref(false); const checking = ref(false); const editorOpen = ref(false); const completed = ref('')
const scope = ref<'ENTERPRISE' | 'PROJECT'>('ENTERPRISE'); const projectId = ref(''); const reason = ref(''); const attested = ref(false)
const editingConfig = ref<string | null>(null); const expectedRevision = ref('0'); const basedOn = ref<Basis | null>(null)
const issues = ref<SimulationIssue[]>([]); const discardDialog = ref(false)
const operationStatuses = { PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败，请保留原请求标识核查原因' }
const lookupMessage = computed(() => lookupResult.value?.lookup_status === 'NOT_FOUND' ? '暂未查到原操作。只有原上下文仍一致且完整参数相同，才能沿用原请求标识重试。'
  : lookupResult.value?.lookup_status === 'EXPIRED' ? '原请求查询已过期，无法安全确认保存结果。请联系管理员核查，不能重新生成请求覆盖结果。'
  : lookupResult.value?.operation ? '已找到原操作：' + operationStatuses[lookupResult.value.operation.status] : '')
const busy = computed(() => user.medicalWriting || reading.value || checking.value || listingLoading.value)
const mayRead = computed(() => simulationAllowed(user.medicalSession, 'configuration.read'))
const mayCreate = computed(() => simulationAllowed(user.medicalSession, 'model.configure', undefined, null))
const mayRevise = computed(() => view.value !== null && view.value.model_config_revision === view.value.current_model_revision
  && view.value.allowed_actions.includes('model.configure') && view.value.allowed_actions.includes('configuration.read')
  && simulationAllowed(user.medicalSession, 'model.configure', view.value.project_id, view.value.config_id))
const mayReplay = computed(() => pending.value !== null && lookupResult.value?.lookup_status === 'NOT_FOUND'
  && canonicalSimulation(pending.value.context) === canonicalSimulation(user.medicalSession?.current_context))
const maySave = computed(() => simulationAllowed(user.medicalSession, 'model.configure', scope.value === 'PROJECT' ? projectId.value : null, editingConfig.value)
  && (pending.value !== null ? mayReplay.value : editingConfig.value === null || mayRevise.value))
const detailRows = computed(() => {
  const value = view.value; if (!value) return []; const d = value.definition
  return [
    ['配置标识', value.config_id], ['当前展示的固定修订', value.model_config_revision], ['当前维护头', value.current_model_revision], ['维护修订号', value.revision],
    ['范围', value.project_id ?? '本企业'], ['内容SHA-256', value.content_sha256], ['创建人 / UTC时间', value.created_by + ' / ' + value.created_at],
    ['执行方式 / 生产资格', 'SIMULATION（模拟） / 不具备生产资格'],
    ['模拟器 / 固定版本', d.simulator_id + ' / ' + d.simulator_version], ['模拟模型 / 固定版本', d.model_id + ' / ' + d.model_version],
    ['声明案例标识', d.case_binding.case_id], ['声明案例SHA-256', d.case_binding.case_sha256], ['案例核验状态', '仅声明（DECLARED_ONLY）；未核验实际字节'],
    ['上下文 / 输出上限', d.limits.context_window + ' / ' + d.limits.max_output_tokens + ' tokens'], ['并发 / 每分钟请求 / 每分钟tokens', [d.limits.max_concurrency, d.limits.requests_per_minute, d.limits.tokens_per_minute].join(' / ')],
    ['时限 / 停止词上限', d.limits.deadline_seconds + '秒 / ' + d.limits.stop_limit], ['推理 / temperature声明', d.limits.reasoning_efforts.join('、') + ' / ' + (d.limits.temperature_enabled ? '允许' : '不允许')],
    ['来源记录Schema / SHA-256', d.provenance_schema_ref + '\n' + d.provenance_schema_sha256],
    ['供应商请求计费标记', d.provider_charge_amount + '（本地模拟不发送供应商请求；不代表基础设施免费或预算结算）'], ['计费依据', d.charge_basis]
  ].map(([label, value]) => ({ label: label!, value: value! }))
})
let listingController: AbortController | null = null
let generation = 0; let controller: AbortController | null = null; let mounted = true; let leaveApproved = false
let nextAction: 'NEW' | 'REVISE' | 'CLOSE' | null = null; let nextPath: string | null = null
function issue(path: string): string { return issues.value.find(value => value.path === path)?.message ?? '' }
function dirty(): boolean { return editorOpen.value && (Boolean(reason.value) || attested.value || projectId.value !== '' || (fields.value?.dirty() ?? false)) }
function clearEditor(): void {
  fields.value?.clear(); editorOpen.value = false; scope.value = 'ENTERPRISE'; projectId.value = ''; reason.value = ''; attested.value = false
  editingConfig.value = null; expectedRevision.value = '0'; basedOn.value = null; issues.value = []
}
function clear(): void {
  generation += 1; listingController?.abort(); listingController = null; listing.value = null; listingLoading.value = false; applied.value = null; query.value = ''; limit.value = '20'; listError.value = ''; controller?.abort(); controller = null; view.value = null; pending.value = null; lookupResult.value = null
  completed.value = ''; readId.value = ''; readError.value = ''; reading.value = false; checking.value = false
  discardDialog.value = false; nextAction = null; nextPath = null; clearEditor()
  pageElement.value?.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input,textarea').forEach(input => { input.value = ''; if (input instanceof HTMLInputElement && input.type === 'checkbox') input.checked = false })
}
function restore(): void {
  clear(); const session: MedicalSession | null = user.medicalSession
  if (session?.current_context && !document.hidden) { pending.value = readPendingSimulation(session); void readList().catch(() => { /* Medical failures are already dispatched; stale reads are discarded. */ }) }
}
async function readList(after?: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || busy.value || document.hidden || !mayRead.value) return
  const search = query.value; const size = limit.value
  listError.value = search.trim() !== search || search.normalize('NFC') !== search || /[\u0000-\u001f\u007f-\u009f\ud800-\udfff]/u.test(search) ? '查询文本不能含首尾空白或控制字符' : ''
  if (listError.value) return
  listingController?.abort(); listingController = new AbortController(); const current = generation
  listingLoading.value = true; listing.value = null
  try {
    const value = await listSimulationConfigurations(session, { limit: Number(size), ...(search ? { q: search } : {}), ...(after ? { after } : {}) }, listingController.signal)
    if (current === generation && user.medicalSession === session && mounted && !document.hidden && query.value === search && limit.value === size) { listing.value = value; applied.value = { query: search, limit: size } }
  } catch (failure) {
    if (failure instanceof MedicalFailure && failure.code === 'CURSOR_INVALID' && current === generation) { listing.value = null; applied.value = null; listError.value = '分页已失效，请重新查询配置'; return }
    throw failure
  } finally { if (current === generation) listingLoading.value = false }
}
function searchList(): Promise<void> { return readList() }
function nextList(): Promise<void> | undefined { if (!listChanged.value && listing.value?.next_after) return readList(listing.value.next_after) }
async function readFixed(id: string): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || busy.value || document.hidden || !mayRead.value || dirty()) return
  readError.value = schemaIssues(simulationSchema + 'Id', id).length ? '请填写规范的小写UUID固定修订标识' : ''
  if (readError.value) { await nextTick(); pageElement.value?.querySelector<HTMLElement>('#simulation-read-id')?.focus(); return }
  controller?.abort(); controller = new AbortController(); const current = ++generation; reading.value = true; view.value = null; completed.value = ''
  try {
    const result = await readSimulationConfiguration(session, id, controller.signal)
    if (generation !== current || user.medicalSession !== session || !mounted || document.hidden) return
    view.value = result; readId.value = result.model_config_revision
  } finally { if (generation === current) reading.value = false }
}
async function readSelected(): Promise<void> { if (dirty()) { readError.value = '请先保存或清除当前表单，再读取其他固定版本'; return }; await readFixed(readId.value) }
async function start(action: 'NEW' | 'REVISE' | 'CLOSE'): Promise<void> {
  if (busy.value || pending.value !== null && action !== 'CLOSE') return
  if (action === 'CLOSE') { clearEditor(); return }
  if (action === 'NEW' && !mayCreate.value || action === 'REVISE' && !mayRevise.value) return
  const current = generation; const source = view.value
  clearEditor(); editorOpen.value = true; completed.value = ''
  if (action === 'REVISE' && source) {
    editingConfig.value = source.config_id; expectedRevision.value = source.revision
    basedOn.value = { version_id: source.model_config_revision, content_sha256: source.content_sha256 }
    scope.value = source.project_id === null ? 'ENTERPRISE' : 'PROJECT'; projectId.value = source.project_id ?? ''
    await nextTick(); if (generation !== current || !mounted || !editorOpen.value) return
    fields.value?.load(source.definition)
  }
  await nextTick(); pageElement.value?.querySelector<HTMLElement>('#simulation-model_id')?.focus()
}
function requestStart(action: 'NEW' | 'REVISE' | 'CLOSE'): void { if (busy.value) return; if (dirty()) { nextAction = action; nextPath = null; discardDialog.value = true; return }; void start(action) }
function cancelDiscard(): void { discardDialog.value = false; nextAction = null; nextPath = null }
async function confirmDiscard(): Promise<void> {
  const action = nextAction; const path = nextPath; clearEditor(); cancelDiscard()
  if (path !== null) { leaveApproved = true; try { await router.push(path) } finally { leaveApproved = false } } else if (action !== null) await start(action)
}
async function lookup(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value
  if (!session?.current_context || !original || busy.value || document.hidden) return
  const current = generation; controller?.abort(); controller = new AbortController(); checking.value = true; lookupResult.value = null
  try { const value = await lookupOperation(session, 'CP-08', original.key, controller.signal)
    if (generation === current && user.medicalSession === session && pending.value?.key === original.key && !document.hidden) lookupResult.value = value
  } finally { if (current === generation) checking.value = false }
}
async function resume(): Promise<void> {
  const original = pending.value; if (!original || !mayReplay.value || busy.value || document.hidden || editorOpen.value) return
  clearEditor(); editorOpen.value = true; editingConfig.value = original.config_id; expectedRevision.value = original.expected_revision
  basedOn.value = original.based_on_revision; scope.value = original.project_id === null ? 'ENTERPRISE' : 'PROJECT'; projectId.value = original.project_id ?? ''
  await nextTick(); pageElement.value?.querySelector<HTMLElement>('#simulation-model_id')?.focus()
}
async function finish(session: MedicalSession, original: PendingSimulationSave, operationId: string, receipt: SimulationReceipt | null, signal: AbortSignal): Promise<SimulationView> {
  const operation = await readOperation(session, operationId, signal)
  if (operation.status !== 'COMPLETED' || !operation.resource_id) fail('CONTENT_MISMATCH', '原模拟模型操作尚未完成，请继续查询原请求')
  const value = await readSimulationConfiguration(session, operation.resource_id, signal); signal.throwIfAborted()
  if (user.medicalSession !== session || !mounted || document.hidden) fail('CONTEXT_CHANGED', '固定草稿回读期间会话发生变化')
  await confirmSimulationResult(session, original, operation, value, receipt, signal); return value
}
function showCompleted(value: SimulationView): void { clearEditor(); pending.value = null; lookupResult.value = null; view.value = value; readId.value = value.model_config_revision; completed.value = value.model_config_revision; listing.value = null; applied.value = null }
async function recover(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession; const original = pending.value; const operation = lookupResult.value?.operation
  if (!session?.current_context || !original || !operation || operation.status !== 'COMPLETED' || busy.value || document.hidden) return
  const current = generation; controller?.abort(); controller = new AbortController(); checking.value = true
  try { const result = await finish(session, original, operation.operation_id, null, controller.signal); if (current === generation && user.medicalSession === session) showCompleted(result) }
  finally { if (current === generation) checking.value = false }
}
async function submit(): Promise<void> {
  const session: MedicalSession | null = user.medicalSession
  if (!session?.current_context || !fields.value || busy.value || !maySave.value || !attested.value || document.hidden) return
  const value: SimulationSave = { schema_version: '1.0.0', project_id: scope.value === 'PROJECT' ? projectId.value : null, config_id: editingConfig.value,
    expected_revision: expectedRevision.value, based_on_revision: basedOn.value, definition: fields.value.value(), reason: reason.value }
  issues.value = simulationInputIssues(value)
  if (issues.value.length) { await nextTick(); fields.value?.focusIssue(); pageElement.value?.querySelector<HTMLElement>('.is-error input,.is-error textarea')?.focus(); return }
  const path = route.fullPath; const current = generation; controller?.abort(); controller = new AbortController(); const signal = controller.signal; user.medicalWriting = true
  try {
    const original = await prepareSimulationSave(session, value, signal); pending.value = original
    if (current !== generation || user.medicalSession !== session || document.hidden || !mounted) fail('CONTEXT_CHANGED', '模拟模型请求尚未发送，请核查原请求及当前身份')
    const receipt = await saveSimulationConfiguration(session, original, value, signal)
    const fresh = await user.readMedical() as MedicalSession | null
    if (!fresh?.current_context || fresh.user.user_id !== original.actor || fresh.current_context.tenant_id !== original.tenant_id || !mounted || route.fullPath !== path || document.hidden) fail('CONTEXT_CHANGED', '保存已返回，当前主体或企业需要重新核查')
    controller = new AbortController(); const result = await finish(fresh, original, receipt.operation_id, receipt, controller.signal)
    if (user.medicalSession === fresh && mounted && route.fullPath === path && !document.hidden) showCompleted(result)
  } finally {
    user.medicalWriting = false
    if (mounted && completed.value !== '' && !document.hidden) await readList()
    if (mounted && user.medicalSession?.current_context && !document.hidden && route.fullPath === path && completed.value === '') pending.value = readPendingSimulation(user.medicalSession)
  }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty() || user.medicalWriting) { event.preventDefault(); event.returnValue = '' } }
function visibility(): void { if (document.hidden) clear(); else restore() }
onBeforeRouteLeave(to => { if (user.medicalWriting) return false; if (leaveApproved || !dirty()) return true; nextPath = to.fullPath; nextAction = null; discardDialog.value = true; return false })
watch([query, limit], () => { listingController?.abort(); listing.value = null; applied.value = null; listingLoading.value = false }, { flush: 'sync' })
watch(() => user.medicalSession, restore, { immediate: true, flush: 'sync' })
window.addEventListener('beforeunload', beforeUnload); document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { mounted = false; window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('visibilitychange', visibility); clear() })
</script>
<style scoped>
.simulation-section { margin: 24px 0; padding: 22px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); }
.simulation-section-heading { display: flex; align-items: center; gap: 16px; }
.simulation-section h2 { margin-top: 0; font-size: 20px; }
.simulation-id-input { width: min(82vw,390px); }
.simulation-wrap { overflow-wrap: anywhere; white-space: pre-wrap; }
.simulation-form-actions { display: flex; gap: 12px; margin-top: 18px; }
p { line-height: 1.6; overflow-wrap: anywhere; }.simulation-complete { color: var(--el-color-success); }
@media (max-width: 700px) { .simulation-section { padding: 14px; } :deep(.el-descriptions__content) { word-break: break-word; } }
</style>
