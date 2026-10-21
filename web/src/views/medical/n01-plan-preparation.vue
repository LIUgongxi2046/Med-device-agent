<template>
  <section class="n01-preparation" aria-label="准备 N01 计划" data-n01-preparation>
    <header>
      <div>
        <h2>准备 N01 计划</h2>
        <p>从已登记的完整定义准备一个固定单元的目录计划。准备记录不代表评测完成，也没有专业执行结果。当前仅支持只含 N01 的固定执行策略。</p>
      </div><button :disabled="busy" @click="refreshAvailability">刷新服务状态</button>
    </header>
    <section class="coverage" aria-label="原始完整评测矩阵">
      <h3>原始完整矩阵</h3>
      <p>{{ coverage.samples }} 个样本 × {{ coverage.candidates }} 个候选 × {{ coverage.dimensions }} 个维度 × {{ coverage.repeats }} 次重复，共
        {{ coverage.scoring_cells }} 个评分单元。</p>
      <p>下方选择仅定位一个 N01 准备单元，不缩小原定义的覆盖要求。</p>
    </section>
    <p v-if="prerequisite" class="warning" role="status">{{ prerequisite }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="loading" role="status" aria-live="polite">正在核查固定依据与原操作…</p>
    <section v-if="pendingExecution" class="recovery" aria-label="原执行准备恢复">
      <h3>原执行准备记录</h3>
      <p>{{ registration ? '完整执行矩阵已登记。尚未运行候选。' : '提交结果待核查。仅查询原请求，不会自动重发。' }}</p>
      <p v-if="registration">所选候选的完整运行矩阵：{{ registration.expected_cells }} 个样本／重复单元，{{ registration.expected_nodes }} 个 N01 节点。专业完成单元：0。</p>
      <details>
        <summary>原键与固定执行标识</summary>
        <p>原键：{{ pendingExecution.key }}</p>
        <p v-if="registration">执行标识：{{ registration.execution_id }}</p>
      </details><button :disabled="busy || !availability?.features.execution_recovery" @click="recoverExecution">只读核查原执行准备</button>
    </section>
    <form v-else-if="available && !evaluationIssue" class="card" @submit.prevent="startExecution">
      <h3>1. 明确选择原执行依据</h3>
      <p>从当前批次的固定候选和真实目录选择；不会默认批准、生成预算或授予权限。</p>
      <fieldset :disabled="busy || !mayStart">
        <label>批次内候选<select v-model="candidateId">
            <option value="">请选择固定候选</option>
            <option v-for="(item,index) in evaluation.definition.request.candidates" :key="item.version_id" :value="item.version_id">候选
              {{ index+1 }} · {{ short(item.version_id) }}</option>
          </select></label>
        <div class="actions"><button type="button" :disabled="!candidate" @click="loadContexts(false)">读取已批准上下文绑定</button><button
            v-if="contextCursor" type="button" @click="loadContexts(true)">更多上下文绑定</button></div>
        <label>与候选匹配的已批准上下文<select v-model="contextId" :disabled="!candidate">
            <option value="">请选择已批准的固定绑定</option>
            <option v-for="item in matchingContexts" :key="item.binding.version_id" :value="item.binding.version_id">{{ item.created_at }} ·
              {{ short(item.binding.version_id) }}</option>
          </select></label>
        <button type="button" :disabled="!contextId" @click="loadContext">核查所选绑定</button>
        <p v-if="contextDetail">已核查当前批准修订 {{ contextDetail.revision }}；执行策略取自此固定绑定。</p>
        <p v-else-if="contextsListed && !matchingContexts.length">本页没有与候选匹配的已批准绑定。可继续翻页，或前往上下文绑定治理补齐依据。</p>
        <div class="actions"><button type="button" @click="loadBudgets(false)">读取真实预算目录</button><button v-if="budgetCursor" type="button"
            @click="loadBudgets(true)">更多预算</button></div>
        <label>企业预算固定修订<select v-model="enterpriseBudgetId">
            <option value="">请选择企业预算</option>
            <option v-for="item in enterpriseBudgets" :key="item.budget_id" :value="item.budget_id">{{ budgetLabel(item) }}</option>
          </select></label>
        <label>当前项目预算固定修订<select v-model="parentBudgetId">
            <option value="">请选择项目预算</option>
            <option v-for="item in projectBudgets" :key="item.budget_id" :value="item.budget_id">{{ budgetLabel(item) }}</option>
          </select></label>
        <p v-if="budgetsListed && (!enterpriseBudgets.length || !projectBudgets.length)">已读取页面中缺少企业或本项目预算。可继续读取下一页，或前往预算治理核查；不会使用其他项目预算。</p>
        <label class="attestation"><input v-model="startAttested" type="checkbox" />已核查固定候选、已批准上下文与真实预算，确认登记完整原执行矩阵</label>
      </fieldset><button type="submit" :disabled="busy || !mayStart || !canStart || !startAttested">登记原执行准备</button>
    </form>
    <section v-if="registration && bound && !pendingPlan && available" class="card">
      <h3>2. 预览一个固定 N01 单元</h3>
      <fieldset :disabled="busy || !mayRead">
        <label>原清单内样本<select v-model="sampleId">
            <option value="">请选择固定样本</option>
            <option v-for="(sample,index) in evaluation.definition.request.samples" :key="sample.sample_id" :value="sample.sample_id">样本
              {{ index+1 }} · {{ sample.document_type }} · {{ short(sample.sample_id) }}</option>
          </select></label>
        <label>原定义内重复次数<select v-model.number="repeatIndex">
            <option v-for="index in coverage.repeats" :key="index" :value="index-1">第 {{ index }} 次（固定索引 {{ index-1 }}）</option>
          </select></label>
        <button type="button" :disabled="!sampleId" @click="previewPlan">核查候选固定模板并预览</button>
      </fieldset>
    </section>
    <section v-if="preview" class="card" aria-label="N01 计划准备预览">
      <h3>{{ prepared ? '已登记的不可变计划准备' : '3. 核查完整目录与待补充项' }}</h3>
      <p>本次准备覆盖 {{ preview.coverage.selected_cells }} / {{ preview.coverage.expected_cells }} 个原执行单元。专业完成单元
        {{ preview.coverage.professional_completed_cells }}，完整评测尚未完成。</p>
      <template v-if="eligibleTemplates.length > 1 && !pendingPlan">
        <p class="warning">有多个兼容模板，请明确选择并预览；系统不会默认采用其中一个。</p><label>候选内已批准模板<select v-model="templateId" :disabled="busy">
            <option value="">请选择一个兼容模板</option>
            <option v-for="(item,index) in eligibleTemplates" :key="item.template.version_id" :value="item.template.version_id">模板
              {{ index+1 }} · {{ item.document_type }} · {{ applicabilityLabel(item.applicability) }} ·
              {{ short(item.template.version_id) }}</option>
          </select></label><button :disabled="busy || !templateId" @click="previewSelectedTemplate">预览所选模板的完整目录</button>
      </template>
      <p v-if="preview.selection_state === 'NO_COMPATIBLE_TEMPLATE'" class="warning">候选固定依赖中没有兼容且已批准的模板。请在模板治理和 Agent 候选中补齐真实依据后重新准备。</p>
      <ul v-if="preview.blockers.length" class="blockers" aria-label="真实待补充项">
        <li v-for="blocker in preview.blockers" :key="blocker.code">{{ blockerLabels[blocker.code] }}<span
            v-if="blocker.target_ids.length">（{{ blocker.target_ids.length }} 项）</span></li>
      </ul>
      <plan-candidate-detail v-if="preview.plan_candidate" :plan="preview.plan_candidate" />
      <section v-if="preview.conditions.length">
        <h4>实际适用条件</h4>
        <ul>
          <li v-for="condition in preview.conditions" :key="condition.scope+':'+condition.target_id">
            {{ conditionLabels[condition.scope] }}：{{ applicabilityLabel(condition.status) }}<span
              v-if="condition.unknown_input_keys.length">；待确认属性 {{ condition.unknown_input_keys.join('、') }}</span>
            <details>
              <summary>条件固定对象</summary>{{ condition.target_id }}
            </details>
          </li>
        </ul>
      </section>
      <details>
        <summary>模板选择、固定项目属性与技术身份</summary>
        <dl>
          <dt>固定项目属性</dt>
          <dd>{{ preview.profile_revision }} · {{ preview.profile_sha256 }}</dd>
          <dt>来源快照</dt>
          <dd>{{ preview.source_snapshot.version_id }}</dd>
          <dt>选中模板</dt>
          <dd>{{ preview.selected_template?.version_id || '尚未选择' }}</dd>
        </dl>
        <ul>
          <li v-for="item in preview.templates" :key="item.template.version_id">{{ item.template.version_id }} ·
            {{ applicabilityLabel(item.applicability) }} · {{ item.eligible ? '可选' : '不可选' }}</li>
        </ul>
      </details>
      <template v-if="!pendingPlan && preview.plan_candidate">
        <p>登记后保留当前计划和全部待补充项，不建立业务任务、不保存任务计划，也不启动模型或专业执行。</p>
        <p v-if="!mayPrepare" class="warning">
          {{ !availability?.features.plan_preparation ? '当前部署尚不具备计划制品登记依赖；已读取的预览仍可核查。' : templateId !== preview.selected_template?.version_id ? '模板选择已变化，请先预览所选模板。' : '当前身份没有此固定单元的实际计划制品登记授权，请核查评测资源和 N01 角色许可。' }}
        </p><label class="attestation"><input v-model="planAttested" type="checkbox"
            :disabled="busy || !mayPrepare" />已核查完整目录和待补充项，确认仅登记不可变的 N01 计划准备</label><button
          :disabled="busy || !mayPrepare || !planAttested" @click="preparePlan">登记 N01 计划准备</button>
      </template>
    </section>
    <section v-if="pendingPlan" class="recovery" aria-label="原计划准备恢复">
      <h3>{{ historicalContent ? '原计划内容已重新核验' : prepared ? '准备制品已核验' : preparationIdentity ? '原准备身份已核验' : '原计划准备结果待核查' }}</h3>
      <p v-if="historicalContent">已按当前读取权限重新读取原对象，完整计划内容见下方。</p>
      <p v-else-if="prepared">已通过真实对象传输保存固定计划；仍不可运行，也不是专业完成证据。</p>
      <p v-else-if="preparationIdentity">已核验原准备身份。本次状态查询没有重新读取计划内容，不能据此确认当前内容读取权限或可运行状态。</p>
      <p v-else>保留原键和完整原请求。未找到或仍在处理中都不会自动重发。</p>
      <p v-if="status && !prepared">{{ statusLabels[status] }}</p>
      <details>
        <summary>原请求与制品身份</summary>
        <p>原键：{{ pendingPlan.key }}</p><template v-if="identity">
          <p>制品：{{ identity.artifact_id }}</p>
          <p>内容摘要：{{ identity.artifact_sha256 }}</p>
          <p>对象版本：{{ identity.object_version }}</p>
          <p>操作：{{ identity.operation_id }}</p>
        </template>
      </details>
      <div class="actions"><button :disabled="busy || !mayRead || !availability?.features.status"
          @click="checkPlanStatus">只读查询原准备状态</button><button :disabled="busy || !mayRead || !identity || !bound || !registration"
          @click="reopenPlanContent">重新读取原计划内容</button></div>
      <section v-if="status === 'NOT_FOUND' && !pendingPlan.receipt" class="card" aria-label="继续原计划准备">
        <p>未找到记录不表示原请求从未发送。继续仅使用原键和完整原参数；服务端若已有物理尝试，只核查原对象，不会再次上传。</p>
        <button :disabled="busy || !mayRecover || !bound || !registration || !availability?.features.plan_preparation"
          @click="previewContinuation">重新核查原参数与计划预览</button>
        <template v-if="continuationPreview">
          <plan-candidate-detail v-if="continuationPreview.plan_candidate" :plan="continuationPreview.plan_candidate" />
          <ul><li v-for="blocker in continuationPreview.blockers" :key="blocker.code">{{ blockerLabels[blocker.code] }}（{{ blocker.target_ids.length }} 项）</li></ul>
          <ul><li v-for="condition in continuationPreview.conditions" :key="condition.scope+':'+condition.target_id">{{ conditionLabels[condition.scope] }}：{{ applicabilityLabel(condition.status) }}<span v-if="condition.unknown_input_keys.length">；待确认属性 {{ condition.unknown_input_keys.join('、') }}</span></li></ul>
          <details><summary>原请求的固定依据</summary><p>模板：{{ continuationPreview.selected_template?.version_id }}</p><p>来源快照：{{ continuationPreview.source_snapshot.version_id }}</p><p>项目属性：{{ continuationPreview.profile_revision }} · {{ continuationPreview.profile_sha256 }}</p><p>原键：{{ pendingPlan.key }}</p></details>
          <label class="attestation"><input v-model="continuationAttested" type="checkbox" :disabled="busy || !mayRecover" />已核查当前预览，确认用原键和原参数继续准备，不建立业务任务或启动专业执行</label>
          <button :disabled="busy || !mayRecover || !continuationAttested" @click="continuePlan">使用原键继续计划准备</button>
        </template>
      </section>
      <section v-if="historicalContent" class="card" aria-label="重新核验的原计划内容">
        <h4>已重新读取原计划内容</h4>
        <p>已重新核验当前读取权限并读取原对象的完整内容。以下保留原有待补充项，仍不可运行，也不是专业完成证据。</p>
        <ul class="blockers"><li v-for="blocker in historicalContent.original_preview.blockers" :key="blocker.code">{{ blockerLabels[blocker.code] }}（{{ blocker.target_ids.length }} 项）</li></ul>
        <plan-candidate-detail :plan="historicalContent.plan_candidate" />
        <ul><li v-for="condition in historicalContent.original_preview.conditions" :key="condition.scope+':'+condition.target_id">{{ conditionLabels[condition.scope] }}：{{ applicabilityLabel(condition.status) }}<span v-if="condition.unknown_input_keys.length">；待确认属性 {{ condition.unknown_input_keys.join('、') }}</span></li></ul>
        <details><summary>原固定模板与项目属性</summary><p>模板：{{ historicalContent.original_preview.selected_template?.version_id }}</p><p>项目属性：{{ historicalContent.original_preview.profile_revision }} · {{ historicalContent.original_preview.profile_sha256 }}</p><p>来源快照：{{ historicalContent.original_preview.source_snapshot.version_id }}</p></details>
      </section><template v-if="!prepared && !historicalContent">
        <p>恢复操作只核查原对象，可能完成原登记；不会重新上传。此操作需要当前写入资格。</p><label class="attestation"><input v-model="recoveryAttested" type="checkbox"
            :disabled="busy || !mayRecover" />确认核查原对象，并在原请求范围内完成尚未完成的登记</label><button
          :disabled="busy || !registration || !availability?.features.object_recovery || !mayRecover || !recoveryAttested"
          @click="recoverPlan">核查原对象并完成原登记</button>
      </template>
    </section>
    <n01-node-input v-if="pendingPlan && identity" :plan="pendingPlan" :plan-identity="identity" :disabled="busy" />
    <nav aria-label="准备依赖治理入口"><router-link to="/agents/context-bindings">上下文绑定治理</router-link><router-link
        to="/agents/budgets">预算治理</router-link><router-link to="/templates">模板治理</router-link><router-link to="/agents/drafts">Agent
        候选</router-link><router-link v-if="project" :to="'/projects/'+project+'/profile'">项目属性</router-link></nav>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import N01NodeInput from './n01-node-input.vue';
import useUserStore from '@/store/modules/user';
import type { MedicalSession } from '@/api/medical/protocol';
import type { EvaluationRead } from '@/api/medical/evaluation-domain';
import { listBudgets, readBudget } from '@/api/medical/budgets';
import type { BudgetAccount } from '@/api/medical/budgets';
import { queryContexts, readContext } from '@/api/medical/formal-evaluation-context';
import type { ContextSummary, ContextView } from '@/api/medical/formal-evaluation-context';
import { n01OriginalCoverage, n01ContextChoices, n01ContextIssue, n01BudgetChoices, n01BudgetBasis, n01EvaluationIssue } from '@/api/medical/n01-plan-selection';
import { n01Canonical, n01Copy, n01Allowed, n01MayPrepare, readN01Availability, readN01Pending, verifyN01Pending, n01VerifyRegistration, startN01Execution, recoverN01Execution, previewN01Plan, prepareN01Plan, readN01PlanStatus, readN01PlanContent, recoverN01Plan, previewN01PlanContinuation, continueN01Plan, n01FailureMessage } from '@/api/medical/n01-plan-preparation';
import type { N01Availability, N01BoundStart, N01Request, N01Preview, N01Registration, N01Prepared, N01Identity, N01Pending, N01Status, N01Content, N01BlockerCode } from '@/api/medical/n01-plan-preparation';
import PlanCandidateDetail from './plan-candidate-detail.vue';
const props = defineProps<{
    evaluation: EvaluationRead;
    disabled?: boolean;
}>(), user = useUserStore();
const availability = ref<N01Availability | null>(null), pendingExecution = ref<N01Pending | null>(null), pendingPlan = ref<N01Pending | null>(null), registration = ref<N01Registration | null>(null), prepared = ref<N01Prepared | null>(null), preparationIdentity = ref<N01Identity | null>(null), preview = ref<N01Preview | null>(null);
const historicalContent = ref<N01Content | null>(null);
const contexts = ref<ContextSummary[]>([]), contextCursor = ref<string | null>(null), contextDetail = ref<ContextView | null>(null), contextsListed = ref(false), budgets = ref<BudgetAccount[]>([]), budgetCursor = ref<string | null>(null), budgetsListed = ref(false);
const candidateId = ref(''), contextId = ref(''), enterpriseBudgetId = ref(''), parentBudgetId = ref(''), sampleId = ref(''), repeatIndex = ref(0), templateId = ref(''), startAttested = ref(false), planAttested = ref(false), recoveryAttested = ref(false), status = ref<N01Status['state'] | ''>(''), loading = ref(false), error = ref(''), notice = ref('');
const continuationPreview = ref<N01Preview | null>(null), continuationAttested = ref(false);
let controller = new AbortController(), generation = 0, mounted = true, writeOwner: object | null = null;
const project = computed(() => props.evaluation.definition.request.project_id), coverage = computed(() => n01OriginalCoverage(props.evaluation)), evaluationIssue = computed(() => n01EvaluationIssue(props.evaluation));
const busy = computed(() => loading.value || user.medicalWriting || props.disabled === true), available = computed(() => availability.value?.features.plan_preview === true), candidate = computed(() => props.evaluation.definition.request.candidates.find(item => item.version_id === candidateId.value) ?? null);
const matchingContexts = computed(() => n01ContextChoices(contexts.value, props.evaluation, candidate.value)), enterpriseBudgets = computed(() => n01BudgetChoices(budgets.value, user.medicalSession?.current_context?.tenant_id ?? '', null)), projectBudgets = computed(() => n01BudgetChoices(budgets.value, user.medicalSession?.current_context?.tenant_id ?? '', project.value));
const mayRead = computed(() => n01Allowed(user.medicalSession, project.value, props.evaluation.evaluation_id, 'evaluation.read')), mayStart = computed(() => mayRead.value && n01Allowed(user.medicalSession, project.value, props.evaluation.evaluation_id, 'evaluation.run'));
const canStart = computed(() => !!candidate.value && !!contextDetail.value && enterpriseBudgets.value.some(item => item.budget_id === enterpriseBudgetId.value) && projectBudgets.value.some(item => item.budget_id === parentBudgetId.value));
const bound = computed(() => pendingExecution.value?.kind === 'EXECUTION' ? pendingExecution.value.body as N01BoundStart : null), eligibleTemplates = computed(() => preview.value?.templates.filter(item => item.eligible) ?? []);
const identity = computed(() => prepared.value ?? preparationIdentity.value), mayRecover = computed(() => mayStart.value && n01Allowed(user.medicalSession, project.value, props.evaluation.evaluation_id, 'artifact.register'));
const mayPrepare = computed(() => !!project.value && !!preview.value && templateId.value === preview.value.selected_template?.version_id && availability.value?.features.plan_preparation === true && n01MayPrepare(user.medicalSession, project.value, preview.value)), prerequisite = computed(() => !user.medicalSession?.current_context ? '请先恢复身份并选择企业。' : !availability.value ? '请先核查 N01 准备服务。' : !available.value ? availability.value.unavailable_reason === 'DEPENDENCY_UNAVAILABLE' ? '当前部署缺少 N01 准备所需依赖。' : '当前部署尚未启用 N01 计划准备。' : evaluationIssue.value || (!mayRead.value ? '当前批次没有明确读取权限。' : !mayStart.value ? '当前批次没有原执行准备权限，可按已有读取权限核查原记录。' : ''));
const blockerLabels: Record<N01BlockerCode, string> = { TEMPLATE_CHOICE_REQUIRED: '需要明确选择兼容模板', NO_COMPATIBLE_TEMPLATE: '没有兼容且已批准的候选固定模板', APPLICABILITY_UNKNOWN: '存在未知适用条件，仍需核查', OWNER_UNASSIGNED: '责任人员尚未分配', COMPLETION_RULE_MISSING: '缺少完成规则', METHOD_REVIEW_REQUIRED: '处理方法仍需人工核查，尚不能执行' };
const conditionLabels = { PROFILE: '项目属性', TEMPLATE: '模板', NODE: '目录节点', FIELD: '字段', ATTACHMENT: '附件' }, statusLabels = { NOT_FOUND: '当前未找到已登记结果。原请求保留，不能据此推断从未发送。', PENDING: '原准备仍待核查。可以继续查询状态或核查原对象，不会重复上传。', PREPARED: '原请求与不可变准备身份已核验；状态查询不包含计划内容。' };
const short = (id: string) => id.slice(0, 8) + '…' + id.slice(-6), applicabilityLabel = (value: string) => ({ APPLICABLE: '适用', NOT_APPLICABLE: '不适用', UNKNOWN: '未知，待核查' }[value] ?? value), budgetLabel = (item: BudgetAccount) => item.currency + ' · 批准额度 ' + item.approval.limits.amount + ' · ' + short(item.budget_revision);
function reader(): MedicalSession { if (!user.medicalSession?.current_context)
    throw new Error('请先选择企业'); return user.medicalSession; }
function live(g: number, s: MedicalSession) { return mounted && g === generation && s === user.medicalSession && !document.hidden; }
async function run(work: (signal: AbortSignal, current: () => boolean) => Promise<void>, write = false) { if (busy.value || !user.medicalSession?.current_context || document.hidden)
    return; const s = reader(), g = generation, signal = controller.signal, owner = {}; loading.value = true; error.value = ''; notice.value = ''; if (write) {
    writeOwner = owner;
    user.medicalWriting = true;
} try {
    await work(signal, () => live(g, s));
}
catch (failure) {
    if (live(g, s) && !signal.aborted)
        error.value = n01FailureMessage(failure);
}
finally {
    if (live(g, s))
        loading.value = false;
    if (writeOwner === owner) {
        writeOwner = null;
        user.medicalWriting = false;
        if (mounted && generation !== g && !document.hidden)
            void bootstrap();
    }
} }
function invalidate() { continuationPreview.value = null; continuationAttested.value = false; controller.abort(); controller = new AbortController(); generation++; loading.value = false; error.value = ''; notice.value = ''; }
function clearPreview() { historicalContent.value = null; preview.value = null; templateId.value = ''; planAttested.value = false; status.value = ''; }
function clear() { invalidate(); availability.value = null; contexts.value = []; contextCursor.value = null; contextDetail.value = null; contextsListed.value = false; budgets.value = []; budgetCursor.value = null; budgetsListed.value = false; candidateId.value = ''; contextId.value = ''; enterpriseBudgetId.value = ''; parentBudgetId.value = ''; sampleId.value = ''; repeatIndex.value = 0; pendingExecution.value = null; pendingPlan.value = null; registration.value = null; prepared.value = null; preparationIdentity.value = null; startAttested.value = false; recoveryAttested.value = false; clearPreview(); }
async function bootstrap() {
    if (!user.medicalSession?.current_context || document.hidden)
        return;
    try {
        pendingExecution.value = readN01Pending(reader(), props.evaluation.evaluation_id, 'EXECUTION');
        pendingPlan.value = readN01Pending(reader(), props.evaluation.evaluation_id, 'PLAN');
    }
    catch (failure) {
        error.value = n01FailureMessage(failure);
        return;
    }
    return run(async (signal, current) => {
        const a = await readN01Availability(reader, signal);
        if (!current())
            return;
        availability.value = a;
        if (pendingExecution.value && a.features.execution_recovery) {
            try {
                await verifyN01Pending(reader(), pendingExecution.value);
                if (!current())
                    return;
                const restored = await recoverN01Execution(reader, pendingExecution.value, props.evaluation, signal);
                if (current()) {
                    pendingExecution.value = restored;
                    registration.value = restored.receipt as N01Registration;
                }
            }
            catch (failure) {
                if (current())
                    error.value = n01FailureMessage(failure);
            }
        }
        if (pendingPlan.value && a.features.status) {
            const result = await readN01PlanStatus(reader, pendingPlan.value, props.evaluation, signal);
            if (current())
                installStatus(result);
        }
    });
}
function refreshAvailability() { if (busy.value) return; clear(); return bootstrap(); }
function loadContexts(more: boolean) { if (!available.value || !project.value || !candidate.value || !mayStart.value)
    return; const selected = project.value; return run(async (signal, current) => { const page = await queryContexts(reader, selected, more ? contextCursor.value : null, signal); if (current()) {
    contexts.value = more ? [...contexts.value, ...page.items] : page.items;
    contextCursor.value = page.next_cursor;
    contextsListed.value = true;
} }); }
function loadContext() { const selected = matchingContexts.value.find(item => item.binding.version_id === contextId.value), c = candidate.value; if (!selected || !c)
    return; return run(async (signal, current) => { const value = await readContext(reader, selected.binding.version_id, signal), issue = n01ContextIssue(value, selected, props.evaluation, c); if (issue)
    throw new Error(issue); if (current())
    contextDetail.value = value; }); }
function loadBudgets(more: boolean) { if (!available.value || !mayStart.value)
    return; return run(async (signal, current) => { const after = more ? budgetCursor.value : null, page = await listBudgets(reader(), { limit: '20', ...(after ? { after } : {}) }, signal); if (current()) {
    budgets.value = more ? [...budgets.value, ...page.budgets] : page.budgets;
    budgetCursor.value = page.next_after;
    budgetsListed.value = true;
} }); }
async function startExecution() { if (!available.value || evaluationIssue.value || !mayStart.value || !canStart.value || !startAttested.value || pendingExecution.value)
    return; const c = candidate.value!, binding = contextDetail.value!, enterprise = enterpriseBudgets.value.find(item => item.budget_id === enterpriseBudgetId.value)!, parent = projectBudgets.value.find(item => item.budget_id === parentBudgetId.value)!, view = props.evaluation; return run(async (signal, current) => { const freshBinding = await readContext(reader, binding.binding.version_id, signal), freshEnterprise = await readBudget(reader(), enterprise.budget_id, signal), freshParent = await readBudget(reader(), parent.budget_id, signal); if (!current())
    return; if (n01Canonical(freshBinding) !== n01Canonical(binding) || n01Canonical(freshEnterprise) !== n01Canonical(enterprise) || n01Canonical(freshParent) !== n01Canonical(parent))
    throw new Error('上下文或预算已变化，请重新读取并确认'); const body: N01BoundStart = { schema_version: '1.0.0', start: { schema_version: '1.0.0', evaluation_id: view.evaluation_id, definition_sha256: view.content_sha256, expected_evaluation_revision: view.revision, candidate: n01Copy(c), execution_policy: n01Copy(binding.fixed.definition.execution_policy), enterprise_budget: n01BudgetBasis(enterprise), parent_budget: n01BudgetBasis(parent) }, context_binding: n01Copy(binding.binding) }; const saved = await startN01Execution(reader, body, view, signal, p => { if (current())
    pendingExecution.value = p; }); if (current()) {
    pendingExecution.value = saved;
    registration.value = saved.receipt as N01Registration;
    startAttested.value = false;
    notice.value = '完整原执行矩阵已登记。现在可以预览一个固定 N01 单元。';
} }, true); }
function recoverExecution() { const p = pendingExecution.value; if (!p)
    return; return run(async (signal, current) => { const saved = await recoverN01Execution(reader, p, props.evaluation, signal); if (current()) {
    pendingExecution.value = saved;
    registration.value = saved.receipt as N01Registration;
    notice.value = '原键、完整请求与执行矩阵已核验。';
} }); }
function requestBody(selectedTemplate = false): N01Request { if (!bound.value || !registration.value || !sampleId.value)
    throw new Error('请先读取原执行并选择原清单内样本'); const template = selectedTemplate ? eligibleTemplates.value.find(item => item.template.version_id === templateId.value)?.template : preview.value?.selected_template ?? null; if (selectedTemplate && !template)
    throw new Error('请明确选择候选内的兼容模板'); return { schema_version: '1.0.0', execution_id: registration.value.execution_id, start: n01Copy(bound.value.start), sample_id: sampleId.value, repeat_index: repeatIndex.value, node_kind: 'N01', template: template ? n01Copy(template) : null }; }
function previewPlan() { if (!bound.value || !registration.value || !mayRead.value || pendingPlan.value || !sampleId.value)
    return; const body = requestBody(); return run(async (signal, current) => { const value = await previewN01Plan(reader, body, bound.value!, registration.value!, props.evaluation, signal); if (current()) {
    preview.value = value;
    templateId.value = value.selected_template?.version_id ?? '';
    planAttested.value = false;
} }); }
function previewSelectedTemplate() { if (!bound.value || !registration.value || !templateId.value || pendingPlan.value)
    return; let body: N01Request; try {
    body = requestBody(true);
}
catch (failure) {
    error.value = n01FailureMessage(failure);
    return;
} return run(async (signal, current) => { const value = await previewN01Plan(reader, body, bound.value!, registration.value!, props.evaluation, signal); if (current()) {
    preview.value = value;
    templateId.value = value.selected_template?.version_id ?? '';
    planAttested.value = false;
} }); }
function preparePlan() { if (!bound.value || !registration.value || !preview.value || !mayPrepare.value || !planAttested.value || pendingPlan.value)
    return; const body = requestBody(), original = preview.value, b = bound.value, r = registration.value; return run(async (signal, current) => { const saved = await prepareN01Plan(reader, body, original, b, r, props.evaluation, signal, p => { if (current())
    pendingPlan.value = p; }); if (current()) {
    pendingPlan.value = saved;
    prepared.value = saved.receipt as N01Prepared;
    preview.value = prepared.value.preview;
    planAttested.value = false;
    status.value = 'PREPARED';
} }, true); }
function installStatus(value: {
    pending: N01Pending;
    status: N01Status;
}) { continuationPreview.value = null; continuationAttested.value = false; historicalContent.value = null; pendingPlan.value = value.pending; status.value = value.status.state; preparationIdentity.value = value.status.receipt; prepared.value = null; preview.value = null; recoveryAttested.value = false; }
function checkPlanStatus() { const p = pendingPlan.value; if (!p || !mayRead.value)
    return; continuationPreview.value = null; continuationAttested.value = false; historicalContent.value = null; preview.value = null; prepared.value = null; return run(async (signal, current) => { const value = await readN01PlanStatus(reader, p, props.evaluation, signal); if (current())
    installStatus(value); }); }
function reopenPlanContent() {
    const p = pendingPlan.value, b = bound.value, r = registration.value;
    if (!p || !b || !r || !mayRead.value || busy.value) return;
    historicalContent.value = null; preview.value = null; prepared.value = null;
    return run(async (signal, current) => {
        const value = await readN01PlanContent(reader, p, b, r, props.evaluation, signal);
        if (current()) {
            historicalContent.value = value;
            preparationIdentity.value = value.receipt;
            status.value = 'PREPARED';
        }
    });
}
function previewContinuation() {
    const p = pendingPlan.value, b = bound.value, r = registration.value;
    if (!p || !b || !r || status.value !== 'NOT_FOUND' || !mayRecover.value) return;
    continuationPreview.value = null; continuationAttested.value = false;
    return run(async (signal, current) => {
        const value = await previewN01PlanContinuation(reader, p, b, r, props.evaluation, signal);
        if (current()) continuationPreview.value = value;
    });
}
function continuePlan() {
    const p = pendingPlan.value, b = bound.value, r = registration.value, confirmed = continuationPreview.value;
    if (!p || !b || !r || !confirmed || !continuationAttested.value || !mayRecover.value || status.value !== 'NOT_FOUND') return;
    continuationAttested.value = false;
    return run(async (signal, current) => {
        const value = await continueN01Plan(reader, p, confirmed, b, r, props.evaluation, signal);
        if (current()) {
            pendingPlan.value = value; prepared.value = value.receipt as N01Prepared; preview.value = prepared.value.preview;
            continuationPreview.value = null; status.value = 'PREPARED';
            notice.value = '已使用原键核验计划准备。未启动专业执行。';
        }
    }, true);
}
function recoverPlan() { const p = pendingPlan.value, b = bound.value, r = registration.value; if (!p || !b || !r || !mayRecover.value || !recoveryAttested.value)
    return; return run(async (signal, current) => { const value = await recoverN01Plan(reader, p, b, r, props.evaluation, signal); if (current()) {
    pendingPlan.value = value;
    prepared.value = value.receipt as N01Prepared;
    preview.value = prepared.value.preview;
    status.value = 'PREPARED';
    recoveryAttested.value = false;
} }, true); }
watch(candidateId, () => { invalidate(); contexts.value = []; contextCursor.value = null; contextsListed.value = false; contextId.value = ''; contextDetail.value = null; startAttested.value = false; }, { flush: 'sync' });
watch(contextId, () => { invalidate(); contextDetail.value = null; startAttested.value = false; }, { flush: 'sync' });
watch([enterpriseBudgetId, parentBudgetId], () => { startAttested.value = false; }, { flush: 'sync' });
watch([sampleId, repeatIndex], () => { invalidate(); clearPreview(); }, { flush: 'sync' });
watch(templateId, () => { planAttested.value = false; }, { flush: 'sync' });
watch([contextDetail, budgets], () => { startAttested.value = false; }, { deep: true, flush: 'sync' });
watch(() => [user.medicalSession, props.evaluation], () => { clear(); if (!document.hidden)
    void bootstrap(); }, { deep: true, immediate: true, flush: 'sync' });
watch(() => props.disabled, disabled => { if (!disabled && !availability.value && !loading.value && mounted && !document.hidden)
    void bootstrap(); }, { flush: 'sync' });
watch(() => user.medicalWriting, writing => { if (!writing && !availability.value && !loading.value && mounted && !document.hidden)
    void bootstrap(); }, { flush: 'sync' });
function visibility() { clear(); if (!document.hidden)
    void bootstrap(); }
function unload(event: BeforeUnloadEvent) { if (writeOwner) {
    event.preventDefault();
    event.returnValue = '';
} }
document.addEventListener('visibilitychange', visibility);
window.addEventListener('beforeunload', unload);
onBeforeUnmount(() => { mounted = false; invalidate(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('beforeunload', unload); });
</script>
<style scoped>
.n01-preparation {
  margin: 1.5rem 0;
  padding: 1.3rem;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  display: grid;
  gap: 1rem;
  background: var(--el-fill-color-blank)
}

header {
  display: flex;
  gap: 1rem;
  align-items: start;
  justify-content: space-between
}

h2,
h3 {
  margin: .3rem 0 .7rem
}

h2 {
  font-size: 1.3rem
}

h3 {
  font-size: 1.05rem
}

p,
li {
  line-height: 1.6
}

.card {
  padding: 1rem;
  border: 1px solid var(--el-border-color);
  border-radius: 8px
}

.coverage {
  padding: 1rem;
  background: var(--el-fill-color-light);
  border-radius: 8px
}

.warning,
.recovery {
  background: var(--el-color-warning-light-9);
  border-left: 3px solid var(--el-color-warning);
  padding: 1rem
}

.error {
  color: var(--el-color-danger)
}

label {
  display: grid;
  gap: .5rem;
  margin: 1rem 0
}

.attestation {
  display: flex;
  align-items: start
}

input,
select,
button {
  font: inherit
}

select,
button {
  padding: .6rem .8rem;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-primary)
}

button {
  cursor: pointer
}

button:disabled {
  opacity: .5;
  cursor: not-allowed
}

button[type=submit] {
  background: var(--el-color-primary);
  color: white
}

fieldset {
  border: 0;
  padding: 0;
  min-width: 0
}

.actions,
nav {
  display: flex;
  gap: .8rem;
  flex-wrap: wrap
}

details {
  margin: .7rem 0
}

summary {
  cursor: pointer;
  color: var(--el-color-primary)
}

dt {
  font-weight: 600
}

dd {
  margin: .3rem 0;
  overflow-wrap: anywhere
}

.blockers {
  padding: 1rem 2rem;
  background: var(--el-color-warning-light-9)
}

@media(max-width:680px) {
  .n01-preparation {
    padding: .8rem
  }

  header {
    flex-direction: column
  }

  select {
    max-width: 100%
  }
}
</style>
