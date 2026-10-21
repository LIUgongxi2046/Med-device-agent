<template>
  <main class="medical-page field-target-page" data-task-node-producer>
    <nav><router-link :to="basePath + '/config'">返回任务与计划</router-link></nav>
    <header><h1>研究节点生产授权</h1><button :disabled="busy" @click="bootstrap">重新读取</button></header>
    <p class="field-target-notice">登记草稿不会批准或启动研究。原授权、企业预算和每项消费操作均须明确选择；草稿作者不能审批自己的授权。</p>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p><p v-if="loading">正在读取当前授权…</p>
    <section v-if="pending" class="field-target-card"><h2>原请求待核查</h2><p>原请求：{{ pending.key }}</p><p>摘要：{{ pending.body_sha256 }}</p><p>结果未知时不会另建写入。查询未发现回执也不会自动重发。</p><button :disabled="busy" @click="recover">只查询原请求结果</button></section>
    <section v-if="receipt" class="field-target-card"><h2>已核验原操作回执</h2><p>{{ receipt.rule_id }} · {{ receipt.status }} · 原修订 {{ receipt.revision }}</p><p>这是原操作结果，当前状态以重新读取为准。</p></section>
    <section class="field-target-card"><h2>1. 明确登记生产边界</h2>
      <label>已批准的原任务授权<select v-model="originalId" :disabled="busy || !!pending"><option value="">请选择，不自动使用任何授权</option><option v-for="item in originals" :key="item.original_rule_id" :value="item.original_rule_id">{{ item.reason }} · {{ item.original_rule_id }}</option></select></label>
      <button :disabled="busy || !!pending || !originalId" @click="prepare">读取所选授权的实际固定依据</button>
      <form v-if="preparation" @submit.prevent="author">
        <p>项目预算与企业预算分别核查，选择企业预算不等于预留或允许调用模型。</p>
        <label>企业预算<select v-model="budgetId" :disabled="busy || !!pending"><option value="">请选择当前可读企业预算</option><option v-for="b in budgets.filter(b => b.project_id === null && b.currency === preparation!.pins.currency)" :key="b.budget_id" :value="b.budget_id">{{ b.budget_id }} · {{ b.currency }} · 修订 {{ b.revision }}</option></select></label>
        <button v-if="budgetAfter" type="button" :disabled="busy" @click="moreBudgets">读取下一页预算</button>
        <fieldset :disabled="busy || !!pending">
          <label v-for="field in limits" :key="field.key">{{ field.label }}<input v-model="values[field.key]" type="text" inputmode="decimal" autocomplete="off" /></label>
          <p>原上限：制品 {{ preparation.ceilings.maximum_artifacts }}；Token {{ preparation.ceilings.maximum_tokens }}；调用 {{ preparation.ceilings.maximum_calls }}；金额 {{ preparation.ceilings.maximum_amount }} {{ preparation.pins.currency }}</p>
          <label>生效时间（含时区的 ISO 时间）<input v-model="values.valid_from" placeholder="2026-10-20T10:00:00+08:00" /></label><label>失效时间（含时区的 ISO 时间）<input v-model="values.valid_until" /></label>
          <p>原有效期：{{ preparation.ceilings.valid_from }} 至 {{ preparation.ceilings.valid_until }}</p>
          <fieldset><legend>允许生产的制品，不默认全选</legend><label v-for="kind in preparation.choices.artifact_kinds" :key="kind"><input v-model="artifactKinds" type="checkbox" :value="kind" />{{ kind }}</label></fieldset>
          <fieldset><legend>独立消费与准备操作，不隐含授予读取或模型发送</legend><label v-for="op in preparation.choices.operations" :key="op"><input v-model="operations" type="checkbox" :value="op" />{{ operationLabels[op] || op }}</label></fieldset>
          <label>理由<textarea v-model="reason" maxlength="2000" rows="3" /></label>
          <details><summary>核查完整固定依据</summary><dl><template v-for="(value,key) in preparation.pins" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl></details>
          <p v-if="authorProblem">{{ authorProblem }}</p><label><input v-model="authorConfirmed" type="checkbox" />我已核查固定依据、企业预算和全部边界，仅登记未批准草稿。</label>
        </fieldset>
        <button :disabled="busy || !!pending || !canAuthor || !authorConfirmed || !!authorProblem">登记草稿，不批准</button>
      </form>
    </section>
    <section class="field-target-card"><h2>2. 读取与独立审批</h2>
      <label>当前可读授权<select v-model="ruleId" :disabled="busy || !!pending"><option value="">请选择实际记录</option><option v-for="item in rules" :key="item.rule_id" :value="item.rule_id">{{ item.status }} · {{ item.reason }} · {{ item.rule_id }}</option></select></label><button :disabled="busy || !ruleId" @click="readSelected">读取所选固定授权</button>
      <template v-if="selected"><p>{{ selected.status }} · 修订 {{ selected.revision }} · 作者 {{ selected.manifest.created_by }}</p><p>固定摘要：{{ selected.rule_sha256 }}</p><details open><summary>完整固定授权</summary><dl><template v-for="(value,key) in selected.manifest" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl></details>
      <p v-if="selected.manifest.created_by === reader().user.user_id">不能审批本人创作的授权。</p>
      <form @submit.prevent="decide"><fieldset :disabled="busy || !!pending"><label>明确决定<select v-model="decision"><option value="">不默认批准</option><option v-for="d in decisions" :key="d" :value="d">{{ d === 'APPROVE' ? '批准这项固定授权' : '停用这项固定授权' }}</option></select></label><label>理由<textarea v-model="decisionReason" maxlength="2000" /></label><label><input v-model="decisionConfirmed" type="checkbox" />我已独立核查完整固定声明，确认上述决定。</label></fieldset><button :disabled="busy || !!pending || !decisionConfirmed || !decision || !!decisionProblem">提交明确决定</button></form></template>
    </section>
  </main>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useFieldTargetPage } from '@/api/medical/use-task-field-target-page'
import { listBudgets } from '@/api/medical/budgets'
import type { BudgetAccount } from '@/api/medical/budgets'
import { taskActionAllowed } from '@/api/medical/task-detail'
import { projectTextIssue } from '@/api/medical/project-text'
import { canonical, listProducerRules, readProducerPreparation, readProducerRule, readPendingProducer, submitProducer, recoverProducer, producerIssue } from '@/api/medical/task-node-producers'
import type { Preparation, OriginalRule, RuleItem, PendingProducer, InputView, InputReceipt, InputAuthor, InputDecide } from '@/api/medical/task-node-producers'
import './task-field-target.css'
const route = useRoute(), originals = ref<OriginalRule[]>([]), rules = ref<RuleItem[]>([]), budgets = ref<BudgetAccount[]>([]), budgetAfter = ref<string | null>(null)
const preparation = ref<Preparation | null>(null), pending = ref<PendingProducer | null>(null), selected = ref<InputView | null>(null), receipt = ref<InputReceipt | null>(null)
const originalId = ref(''), budgetId = ref(''), ruleId = ref(''), reason = ref(''), notice = ref(''), decisionReason = ref(''), decision = ref<'' | 'APPROVE' | 'DISABLE'>(''), authorConfirmed = ref(false), decisionConfirmed = ref(false)
const artifactKinds = ref<InputAuthor['artifact_kinds']>([]), operations = ref<InputAuthor['operations']>([])
const values = reactive({ maximum_bytes: '', maximum_artifacts: '', maximum_tokens: '', maximum_calls: '', maximum_amount: '', valid_from: '', valid_until: '' })
const limits = [{ key: 'maximum_bytes', label: '总字节上限' }, { key: 'maximum_artifacts', label: '制品数量上限' }, { key: 'maximum_tokens', label: 'Token 上限' }, { key: 'maximum_calls', label: '调用次数上限' }, { key: 'maximum_amount', label: '金额上限' }] as const
const operationLabels: Record<string,string> = { NODE_PRODUCE: '生产指定节点制品', HOST_INPUT_CONSUME: '主机消费本节点固定输入', HOST_PREDECESSOR_CONSUME: '主机消费确切前驱证据', HOST_ENVELOPE_CONSUME: '主机消费固定执行信封', SUPERVISOR_PREPARE_READ: '独立监督器读取启动准备依据', MODEL_INPUT_CONSUME: '模型调用消费确切固定输入' }
function resetAuthor() { preparation.value = null; budgetId.value = ''; artifactKinds.value = []; operations.value = []; reason.value = ''; authorConfirmed.value = false; for (const k of Object.keys(values) as Array<keyof typeof values>) values[k] = '' }
function reset() { resetAuthor(); originals.value = []; rules.value = []; budgets.value = []; budgetAfter.value = null; pending.value = null; selected.value = null; receipt.value = null; originalId.value = ''; ruleId.value = ''; notice.value = ''; decision.value = ''; decisionReason.value = ''; decisionConfirmed.value = false }
const { user, projectId, taskId, basePath, busy, loading, error, reader, run, start } = useFieldTargetPage('MedicalTaskNodeProducerRules', reset, bootstrap)
const canAuthor = computed(() => taskActionAllowed(user.medicalSession, projectId.value, taskId.value, 'task.node_producer.write'))
const authorBody = computed<InputAuthor | null>(() => { const p = preparation.value, b = budgets.value.find(v => v.budget_id === budgetId.value); if (!p || !b) return null; return { ...p.pins, ...values, expected_revision: '0', artifact_kinds: artifactKinds.value, operations: operations.value, reason: reason.value, enterprise_budget_basis: { version_id: b.budget_revision, content_sha256: b.content_sha256 } } })
const authorProblem = computed(() => authorBody.value && preparation.value ? producerIssue(authorBody.value, preparation.value) : '请明确选择企业预算并填写完整边界')
const decisions = computed(() => selected.value?.allowed_actions.filter((v): v is 'APPROVE' | 'DISABLE' => v === 'DISABLE' || v === 'APPROVE' && selected.value?.manifest.created_by !== reader().user.user_id) ?? [])
const decisionProblem = computed(() => projectTextIssue(decisionReason.value, 2000))
watch(originalId, resetAuthor, { flush: 'sync' }); watch(ruleId, () => { selected.value = null; decision.value = ''; decisionReason.value = ''; decisionConfirmed.value = false }, { flush: 'sync' })
watch(() => canonical([values, artifactKinds.value, operations.value, reason.value, budgetId.value, preparation.value]), () => { authorConfirmed.value = false }, { flush: 'sync' })
watch([decision, decisionReason, selected], () => { decisionConfirmed.value = false }, { flush: 'sync' })
async function bootstrap() { await run(async (signal,current) => { const p = readPendingProducer(reader(), projectId.value, taskId.value); if (current()) pending.value = p; const r = await listProducerRules(reader, projectId.value, taskId.value, false, signal); if (!current()) return; rules.value = r as RuleItem[]; const target = route.query.rule; if (typeof target === 'string' && rules.value.some(v => v.rule_id === target)) ruleId.value = target; if (canAuthor.value) { const o = await listProducerRules(reader, projectId.value, taskId.value, true, signal); if (current()) originals.value = o as OriginalRule[] } }) }
async function moreBudgets() { await run(async (signal,current) => { if (!budgetAfter.value) return; const b = await listBudgets(reader(), { limit: '50', after: budgetAfter.value }, signal); if (!current()) return; budgets.value = [...new Map([...budgets.value,...b.budgets].map(v => [v.budget_id,v])).values()]; budgetAfter.value = b.next_after }) }
async function prepare() { await run(async (signal,current) => { const id = originalId.value; const p = await readProducerPreparation(reader, projectId.value, taskId.value, id, signal); if (!current() || id !== originalId.value) return; preparation.value = p; const b = await listBudgets(reader(), { limit: '50' }, signal); if (current() && id === originalId.value) { budgets.value = b.budgets; budgetAfter.value = b.next_after } }) }
async function readSelected() { await run(async (signal,current) => { const id = ruleId.value; const v = await readProducerRule(reader, projectId.value, taskId.value, id, signal); if (current() && id === ruleId.value) selected.value = v }) }
async function author() { if (!authorConfirmed.value || authorProblem.value || !authorBody.value || !canAuthor.value) return; const body = JSON.parse(canonical(authorBody.value)) as InputAuthor; await run(async (signal,current) => { const r = await submitProducer(reader, projectId.value, taskId.value, body, null, budgetId.value, signal, p => { if (current()) pending.value = p }); if (current()) { receipt.value = r; pending.value = null; authorConfirmed.value = false; notice.value = '草稿已登记，尚未批准。重新读取目录后由独立审批人核查。' } }, true) }
async function decide() { const v = selected.value; if (!v || !decision.value || !decisions.value.includes(decision.value) || !decisionConfirmed.value || decisionProblem.value) return; const body: InputDecide = { schema_version: '1.0.0', project_id: projectId.value, expected_revision: v.revision, rule_sha256: v.rule_sha256, decision: decision.value, reason: decisionReason.value }; await run(async (signal,current) => { const r = await submitProducer(reader, projectId.value, taskId.value, body, v.manifest.rule_id, null, signal, p => { if (current()) pending.value = p }); if (current()) { receipt.value = r; pending.value = null; selected.value = null; decisionConfirmed.value = false; notice.value = '原决定回执已核验，请重新读取当前状态。' } }, true) }
async function recover() { const p = pending.value; if (!p) return; await run(async (signal,current) => { const r = await recoverProducer(reader, p, signal); if (current()) { receipt.value = r; if (r) pending.value = null; notice.value = r ? '原请求回执已核验；没有重新执行写入。' : '尚未发现原请求回执，保留原请求，不自动重发。' } }, true) }
start()
</script>
