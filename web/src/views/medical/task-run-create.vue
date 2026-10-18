<template>
<section ref="element" class="run-launch" aria-label="首次启动任务运行">
 <div class="launch-heading"><h3>首次启动运行</h3><el-tag v-if="ready" type="success" effect="plain">任务输入就绪</el-tag><el-tag v-else type="info" effect="plain">等待启动条件</el-tag></div>
 <p>使用本任务的固定计划、来源快照与已批准预算。提交后先进入排队，实际执行状态由运行服务返回。</p>
 <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/>
 <p v-if="!mayRun" role="status">当前身份缺少此任务的执行许可。</p><p v-if="!mayRead" role="status">需要运行读取许可才能核查排队回执及原请求结果。</p>
 <section v-if="pending" class="pending" aria-label="原启动请求恢复"><h4>原启动请求待核查</h4><p>请求标识：{{ pending.key }}</p><p>原请求会保留，即使离开页面或授权变化也不会生成新的请求替代它。</p><el-button :disabled="busy || !mayRead" @click="recover">查询原启动请求</el-button><p v-if="lookup" role="status">{{ recoveryMessage }}</p><el-button v-if="lookup?.lookup_status==='NOT_FOUND'" :disabled="busy || !mayRun || !mayRead || !ready" @click="openConfirm(true)">核对原参数后重试</el-button></section>
 <template v-else>
  <ul v-if="!ready" class="conditions"><li v-if="current.detail.status!=='READY'">任务状态为 {{ taskStatusLabels[current.detail.status] }}，须先完成输入及计划确认。</li><li v-if="!current.detail.plan_revision">尚无已确认计划。</li><li v-if="!current.detail.snapshot_id">尚无来源快照。</li><li v-if="current.detail.missing_requirements.length">仍有 {{ current.detail.missing_requirements.length }} 项输入缺项。</li><li v-if="current.detail.active_run_ids.length">当前已有活动运行，请先查看其状态。</li></ul>
  <el-form label-position="top" @submit.prevent="openConfirm(false)">
   <el-form-item label="已批准预算账户"><el-select v-model="selectedBudget" filterable :disabled="busy || !ready" placeholder="选择当前可读取预算"><el-option v-for="item in availableBudgets" :key="item.budget_id" :value="item.budget_id" :label="(item.project_id?'项目':'企业')+'预算 · '+item.currency+' '+item.approval.limits.amount+' · '+item.budget_id.slice(0,8)"/></el-select></el-form-item>
   <div class="launch-actions"><el-button :disabled="busy || !mayBudget" :loading="loadingBudgets" @click="loadBudgets()">读取预算账户</el-button><el-button v-if="nextBudget" :disabled="busy || !mayBudget" @click="loadBudgets(nextBudget)">更多预算</el-button><router-link to="/agents/budgets">管理预算</router-link></div>
   <p v-if="!mayBudget">当前没有预算读取许可。</p><p v-else-if="budgetLoaded && availableBudgets.length===0">此页没有适用于本项目的可读预算{{ nextBudget?'，可继续读取后续页':'；请先核查预算批准与授权' }}。</p>
   <dl v-if="budget" class="budget-facts"><dt>当前批准额度</dt><dd>{{ budget.approval.limits.amount }} {{ budget.currency }}</dd><dt>已用 / 预留</dt><dd>{{ budget.spent }} / {{ budget.reserved }} {{ budget.currency }}</dd><dt>Token 已用 / 预留 / 上限</dt><dd>{{ budget.spent_tokens }} / {{ budget.reserved_tokens }} / {{ budget.approval.limits.tokens }}</dd><dt>调用已用 / 预留 / 上限</dt><dd>{{ budget.spent_calls }} / {{ budget.reserved_calls }} / {{ budget.approval.limits.calls }}</dd><dt>批准截止（UTC）</dt><dd>{{ budget.approval.limits.deadline_at }}</dd></dl>
   <el-button class="launch-submit" type="primary" native-type="submit" :disabled="busy || !ready || !mayRun || !mayRead || !budget">核对并启动首次运行</el-button>
  </el-form>
 </template>
 <p v-if="notice" class="launch-notice" role="status">{{ notice }}</p>
 <section v-if="confirmedRun" class="confirmed"><h4>已核查的运行</h4><p>{{ runStatusLabels[confirmedRun.status] }} · {{ confirmedRun.run_id }}</p><p>此状态来自服务端回读，不代表生成成果已经完成。</p><run-detail :session="session" :project-id="current.detail.project_id" :task-id="current.detail.task_id" :run-id="confirmedRun.run_id" :active="active"/></section>
 <p class="boundary">本入口仅支持首次运行。失败后的继续或重试需要独立接口；当前不会重新启动已有尝试。</p>
 <el-dialog v-model="confirming" title="确认首次启动任务运行" width="min(92vw,620px)" :close-on-click-modal="false" :close-on-press-escape="!writing" :show-close="!writing">
  <p>将按以下固定输入提交运行。预算上限、可用余额、模型政策与执行条件由服务端重新核验；接收请求不保证开始或完成。</p>
  <dl v-if="confirmBody" class="budget-facts"><dt>任务固定版本</dt><dd>{{ confirmBody.task_revision }}</dd><dt>已确认计划</dt><dd>{{ confirmBody.plan_revision }}</dd><dt>来源快照</dt><dd>{{ confirmBody.snapshot_id }}</dd><dt>预算批准版本</dt><dd>{{ confirmBody.budget_revision }}</dd><dt>任务记录修订</dt><dd>{{ confirmBody.expected_revision }}</dd></dl>
  <p v-if="confirmBudget">批准上限 {{ confirmBudget.approval.limits.amount }} {{ confirmBudget.currency }}；当前已用 {{ confirmBudget.spent }}，预留 {{ confirmBudget.reserved }}。截止 {{ confirmBudget.approval.limits.deadline_at }}。</p>
  <p v-if="pending">继续使用原请求标识 {{ pending.key }}，不替换任何固定输入。</p>
  <el-checkbox v-model="acknowledged" :disabled="writing">确认使用以上固定输入与预算启动首次运行</el-checkbox>
  <template #footer><el-button :disabled="writing" @click="confirming=false">返回核查</el-button><el-button type="primary" :loading="writing" :disabled="busy || !acknowledged || !mayRun || !mayRead" @click="submit">提交启动请求</el-button></template>
 </el-dialog>
</section>
</template>
<script setup lang="ts">
import { computed, effectScope, onBeforeUnmount, ref, watch } from 'vue';
import useUserStore from '@/store/modules/user';
import type { MedicalSession } from '@/api/medical/protocol';
import { fail } from '@/api/medical/protocol';
import type { CurrentTask } from '@/api/medical/task-detail';
import { taskActionAllowed, readCurrentTask } from '@/api/medical/task-detail';
import { taskStatusLabels } from '@/api/medical/tasks';
import { budgetActionAllowed, listBudgets, readBudget } from '@/api/medical/budgets';
import type { BudgetAccount } from '@/api/medical/budgets';
import { pendingRunCreate, prepareRunCreate, recoverRunCreate, runCreateInput, runCreateReady, submitRunCreate } from '@/api/medical/run-create';
import type { PendingRunCreate, RunCreateBody } from '@/api/medical/run-create';
import type { OperationLookup } from '@/api/medical/operations';
import { runStatusLabels } from '@/api/medical/runs';
import type { RunView } from '@/api/medical/runs';
import RunDetail from './run-detail.vue';
const props = defineProps<{
    session: MedicalSession;
    current: CurrentTask;
    active: boolean;
}>();
const emit = defineEmits<{
    accepted: [
    ];
}>();
const user = useUserStore();
const element = ref<HTMLElement | null>(null), budgets = ref<BudgetAccount[]>([]), selectedBudget = ref(''), nextBudget = ref<string | null>(null), budgetLoaded = ref(false), loadingBudgets = ref(false);
const pending = ref<PendingRunCreate | null>(null), lookup = ref<OperationLookup | null>(null), confirmedRun = ref<RunView | null>(null), error = ref(''), notice = ref(''), reading = ref(false), writing = ref(false), confirming = ref(false), acknowledged = ref(false), confirmBody = ref<RunCreateBody | null>(null), confirmBudget = ref<BudgetAccount | null>(null);
const ready = computed(() => runCreateReady(props.current)), mayRun = computed(() => taskActionAllowed(props.session, props.current.detail.project_id, props.current.detail
    .task_id, 'task.run')), mayRead = computed(() => taskActionAllowed(props.session, props.current.detail.project_id, props.current.detail
    .task_id, 'run.read')), mayBudget = computed(() => budgetActionAllowed(props.session, 'budget.read'));
const busy = computed(() => reading.value || writing.value || user.medicalWriting), availableBudgets = computed(() => budgets.value.filter(b => b.project_id === null || b.project_id === props.current
    .detail.project_id)), budget = computed(() => availableBudgets.value.find(b => b.budget_id === selectedBudget.value) ?? null);
const recoveryMessage = computed(() => lookup.value?.lookup_status === 'EXPIRED' ? '原请求记录已过期，禁止重新发送；请由授权人员核查原运行。' :
    lookup.value?.lookup_status === 'NOT_FOUND' ? '当前未查到原操作。重试前仍须核对原任务、计划、快照与批准版本。' : lookup.value?.operation
        ?.status === 'PROCESSING' ? '服务端已登记原启动请求，仍在处理。请继续读取运行状态，勿重复提交。' : lookup.value?.operation?.status === 'FAILED' ?
        '启动操作处理失败。原运行状态已回读；这里不提供新的执行尝试。' : '原启动操作已处理，实际执行状态以下方运行记录为准。');
let controller = new AbortController(), generation = 0, mounted = true;
let writeOwner: symbol | null = null;
function clear() {
    generation++;
    reading.value = false;
    loadingBudgets.value = false;
    controller.abort();
    controller = new AbortController();
    budgets.value = [];
    selectedBudget.value = '';
    nextBudget.value = null;
    budgetLoaded.value = false;
    pending.value = null;
    lookup.value = null;
    confirmedRun.value = null;
    error.value = '';
    notice.value = '';
    confirming.value = false;
    acknowledged.value = false;
    confirmBody.value = null;
    confirmBudget.value = null;
    element.value?.querySelectorAll<HTMLInputElement>('input').forEach(input => {
        input.value = '';
        input.checked = false;
    });
}
function valid(g: number, s: MedicalSession) {
    return mounted && props.active && !document.hidden && generation === g && props.session === s;
}
function restore() {
    clear();
    if (!props.active || document.hidden || !props.session.current_context)
        return;
    try {
        pending.value = pendingRunCreate(props.session, props.current.detail.project_id, props.current.detail.task_id);
    }
    catch (e) {
        error.value = e instanceof Error ? e.message : '原启动请求无法读取';
    }
}
async function loadBudgets(after?: string) {
    if (busy.value || !mayBudget.value || !props.active || document.hidden)
        return;
    const s = props.session, g = generation;
    reading.value = true;
    loadingBudgets.value = true;
    error.value = '';
    try {
        const page = await listBudgets(s, {
            limit: '20',
            ...(after ? {
                after
            } : {})
        }, controller.signal);
        if (valid(g, s)) {
            budgets.value = after ? [...budgets.value, ...page.budgets] : page.budgets;
            nextBudget.value = page.next_after;
            budgetLoaded.value = true;
            if (!after)
                selectedBudget.value = '';
        }
    }
    catch (e) {
        if (valid(g, s))
            error.value = e instanceof Error ? e.message : '预算读取失败';
    }
    finally {
        if (g === generation) {
            reading.value = false;
            loadingBudgets.value = false;
        }
    }
}
async function openConfirm(retry: boolean) {
    if (busy.value || !ready.value || !mayRun.value || !mayRead.value || !props.active || document.hidden)
        return;
    if (retry && (!pending.value || lookup.value?.lookup_status !== 'NOT_FOUND'))
        return;
    if (!retry && !budget.value) {
        error.value = '请选择实际预算账户';
        return;
    }
    const s = props.session, g = generation;
    reading.value = true;
    error.value = '';
    try {
        const latest = await readCurrentTask(s, props.current.detail.project_id, props.current.detail.task_id, controller
            .signal);
        if (!valid(g, s))
            return;
        if (latest.revision !== props.current.revision)
            fail('REVISION_CONFLICT', '任务记录已变化，请先重新读取任务');
        let b: BudgetAccount | null = null;
        if (retry) {
            const p = pending.value!;
            const original = runCreateInput(latest, p.body.budget_revision);
            if (JSON.stringify(original) !== JSON.stringify(p.body))
                fail('IDEMPOTENCY_CONFLICT', '当前固定输入与原启动请求不同，不能替换原参数');
            b = await readBudget(s, p.budget_id, controller.signal);
            if (!valid(g, s))
                return;
            if (b.budget_revision !== p.body.budget_revision)
                fail('REVISION_CONFLICT', '原预算批准已变化，禁止用新批准替换原请求');
            if (b.project_id !== null && b.project_id !== latest.detail.project_id)
                fail('ACCESS_DENIED', '原预算不属于当前项目');
            confirmBody.value = p.body;
        }
        else {
            const selected = budget.value;
            if (!selected) {
                error.value = '请选择实际预算账户';
                return;
            }
            b = await readBudget(s, selected.budget_id, controller.signal);
            if (!valid(g, s))
                return;
            if (b.budget_revision !== selected.budget_revision)
                fail('REVISION_CONFLICT', '预算批准已更新，请重新读取账户后选择');
            if (b.project_id !== null && b.project_id !== latest.detail.project_id)
                fail('ACCESS_DENIED', '预算不属于当前项目');
            confirmBody.value = runCreateInput(latest, b.budget_revision);
        }
        if (valid(g, s)) {
            confirmBudget.value = b;
            acknowledged.value = false;
            confirming.value = true;
        }
    }
    catch (e) {
        if (valid(g, s))
            error.value = e instanceof Error ? e.message : '启动条件核查失败';
    }
    finally {
        if (g === generation)
            reading.value = false;
    }
}
async function submit() {
    if (pending.value && lookup.value?.lookup_status !== 'NOT_FOUND')
        return;
    if (busy.value || !acknowledged.value || !confirmBudget.value || !confirmBody.value || !props.active || document
        .hidden || !mayRun.value || !mayRead.value)
        return;
    const s = props.session, g = generation;
    const body = confirmBody.value;
    const owner = Symbol('run-create-write');
    writeOwner = owner;
    // Keep observing ownership until the dispatched write settles, even after unmount.
    const ownershipScope = effectScope(true);
    ownershipScope.run(() => watch(() => user.medicalWriting, value => {
        if (!value) writeOwner = null;
    }, { flush: 'sync' }));
    let accepted = false;
    writing.value = true;
    user.medicalWriting = true;
    error.value = '';
    try {
        if (JSON.stringify(runCreateInput(props.current, body.budget_revision)) !== JSON.stringify(body))
            fail('REVISION_CONFLICT', '确认后任务已变化，请重新核查');
        const p = await prepareRunCreate(s, props.current, body.budget_revision, controller.signal, confirmBudget.value!
            .budget_id);
        if (!valid(g, s))
            return;
        if (JSON.stringify(body) !== JSON.stringify(p.body))
            fail('REVISION_CONFLICT', '确认后任务固定输入已变化，请重新核查');
        pending.value = p;
        const receipt = await submitRunCreate(s, p, controller.signal);
        if (!valid(g, s))
            return;
        confirming.value = false;
        notice.value = '服务端已接收首次运行请求并排队。正在核查原操作与实际运行。';
        const result = await recoverRunCreate(s, p, controller.signal, receipt);
        if (valid(g, s)) {
            lookup.value = result.lookup;
            confirmedRun.value = result.run;
            pending.value = pendingRunCreate(s, p.project_id, p.task_id);
            notice.value = '已确认运行登记。实际状态：' + (result.run ? runStatusLabels[result.run.status] : '待核查') + '。';
            accepted = true;
        }
    }
    catch (e) {
        if (valid(g, s)) {
            error.value = e instanceof Error ? e.message : '启动结果尚未确认，请查询原请求';
            pending.value = pendingRunCreate(s, props.current.detail.project_id, props.current.detail.task_id);
            if (pending.value)
                confirming.value = false;
        }
    }
    finally {
        writing.value = false;
        if (writeOwner === owner) {
            writeOwner = null;
            user.medicalWriting = false;
        }
        ownershipScope.stop();
        if (accepted && valid(g, s))
            emit('accepted');
    }
}
async function recover() {
    const p = pending.value;
    if (!p || busy.value || !mayRead.value || !props.active || document.hidden)
        return;
    const s = props.session, g = generation;
    reading.value = true;
    error.value = '';
    try {
        const result = await recoverRunCreate(s, p, controller.signal);
        if (valid(g, s)) {
            lookup.value = result.lookup;
            confirmedRun.value = result.run;
            pending.value = pendingRunCreate(s, p.project_id, p.task_id);
            if (result.run)
                notice.value = '原运行已核查：' + runStatusLabels[result.run.status] + '。';
        }
    }
    catch (e) {
        if (valid(g, s))
            error.value = e instanceof Error ? e.message : '原请求尚未确认';
    }
    finally {
        if (g === generation)
            reading.value = false;
    }
}
function visibility() {
    if (document.hidden)
        clear();
    else
        restore();
}
watch(() => [props.session, props.current.detail.project_id, props.current.detail.task_id, props.active], restore, {
    immediate: true,
    flush: 'sync'
});
watch(() => props.current.revision, () => {
    if (!writing.value) {
        confirming.value = false;
        acknowledged.value = false;
        confirmBody.value = null;
        confirmBudget.value = null;
    }
});
document.addEventListener('visibilitychange', visibility);
onBeforeUnmount(() => {
    mounted = false;
    clear();
    document.removeEventListener('visibilitychange', visibility);
});
</script>
<style scoped>
.run-launch {
  padding: 20px;
  margin: 20px 0;
  border: 1px solid #DCE3EC;
  border-radius: 8px;
  background: #F8FAFC;
  color: #243247
}

.launch-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px
}

.launch-heading h3 {
  margin: 0;
  font-size: 17px
}

.run-launch p,
.conditions {
  font-size: 14px;
  color: #526279;
  line-height: 1.65;
  overflow-wrap: anywhere
}

.launch-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap
}

.launch-submit {
  margin-top: 16px
}

.pending,
.confirmed {
  background: #fff;
  border: 1px solid #DCE3EC;
  padding: 16px;
  margin: 16px 0;
  border-radius: 6px
}

.budget-facts {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 2fr;
  gap: 10px;
  font-size: 14px
}

.budget-facts dt {
  color: #526279
}

.budget-facts dd {
  margin: 0;
  overflow-wrap: anywhere
}

.boundary {
  border-top: 1px solid #DCE3EC;
  padding-top: 12px
}

.launch-notice {
  padding: 12px;
  background: #EDF4FF
}

.run-launch :deep(.el-select) {
  width: 100%
}

.run-launch a {
  color: #175FAE
}

@media(max-width:650px) {
  .run-launch {
    padding: 14px
  }

  .launch-heading {
    align-items: flex-start;
    flex-direction: column
  }

  .budget-facts {
    grid-template-columns: 1fr;
    gap: 6px
  }

  .budget-facts dd {
    margin-bottom: 8px
  }
}
</style>
