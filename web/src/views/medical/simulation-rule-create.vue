<template>
  <main class="medical-page">
    <h1>创建 SIM 评估规则草稿</h1>
    <p>用途固定为 SIMULATION_EVALUATION，production_eligible=false。仅接受当前企业级依赖；项目资料不能作为此企业规则的依据。保存或核准不赋予生产使用资格。</p>
    <session-panel />
    <router-link to="/agents/simulation-rules">返回规则管理</router-link>
    <el-alert v-if="writeError" :title="writeError" type="warning" :closable="false" />
    <p v-if="!permitted">创建规则需要当前企业的明确维护权限</p>
    <el-alert v-if="pending" title="原规则创建请求待核查" type="warning" :closable="false" data-rule-pending>
      <p>请求标识：{{ pending.key }}；原上下文修订：{{ pending.context.context_revision }}</p>
      <p>刷新只保留请求身份与摘要。正文和理由需要重新填写完整原参数，不会自动创建替代请求。</p>
      <el-button :disabled="busy" @click="lookup">查询原规则创建操作</el-button>
      <p v-if="lookupResult">{{ lookupLabels[lookupResult.lookup_status] }}</p>
      <p v-if="lookupResult?.operation">操作编号：{{ lookupResult.operation.operation_id }}；状态：{{ lookupResult.operation.status }}</p>
      <p v-if="lookupResult?.operation?.resource_id">规则版本：{{ lookupResult.operation.resource_id }}</p>
      <el-button v-if="lookupResult?.operation?.status === 'COMPLETED'" :disabled="busy || !mayRecover" @click="recover">读取原操作的当前规则</el-button>
    </el-alert>
    <section v-if="completedRule && permitted" data-rule-created aria-label="规则保存核查结果">
      <el-alert title="原规则保存操作已经核查完成" type="success" :closable="false" />
      <p>规则版本：{{ completedRule.rule_version }}；{{ creationReceipt ? '原创建状态：草稿' : '当前读取状态：' + ruleStatusLabels[completedRule.status] }}</p>
      <p>内容摘要：{{ completedRule.content_sha256 }}</p>
      <p>保存草稿不表示已核准，也不产生项目适用结论。</p>
      <router-link v-if="ruleAllowed(user.medicalSession, 'rule.read', completedRule.rule_version) || ruleAllowed(user.medicalSession, 'rule.approve', completedRule.rule_version)" :to="'/agents/simulation-rules/' + completedRule.rule_version">打开固定规则版本</router-link>
      <el-collapse><el-collapse-item title="完整保存核查内容"><pre>{{ full(creationReceipt ?? completedRule) }}</pre></el-collapse-item></el-collapse>
    </section>
    <el-form v-if="permitted && !completedRule" ref="formElement" label-position="top" data-rule-create-form @submit.prevent="submit">
      <p>规则只引用实际输入属性及获授权依据。此页不执行规则；未知属性保持UNKNOWN，不能据此判定适用或不适用。</p>
      <el-form-item label="固定规则引擎"><el-input :model-value="ruleEngine" readonly /></el-form-item>
      <el-form-item label="版本依据" :error="errors.base_mode">
        <el-radio-group v-model="form.base_mode" :disabled="disabled"><el-radio value="NEW">独立建立规则</el-radio><el-radio value="REVISION">基于已核准规则建立新版本</el-radio></el-radio-group>
      </el-form-item>
      <template v-if="form.base_mode === 'REVISION'">
        <el-form-item label="原规则固定版本"><el-input v-model="form.base_id" :disabled="disabled" /></el-form-item>
        <el-form-item label="原规则SHA-256摘要"><el-input v-model="form.base_hash" readonly /></el-form-item>
      <el-button :disabled="disabled" @click="loadBase">读取并核对已核准 SIM 原版本</el-button>
      </template>
      <el-form-item label="规则表达式" :error="errors.expression">
        <el-input v-model="form.expression" type="textarea" :rows="4" maxlength="10000" :disabled="disabled" />
      </el-form-item>
      <p>属性使用 #attributes['属性键']，常量使用 #constants['常量键']；布尔逻辑与比较由服务器按固定引擎核验。</p>
      <section aria-label="规则输入属性">
        <h2>输入属性</h2>
        <p>逐项声明表达式实际使用的属性键。未读取到输入时不能用默认值代替。</p>
        <div v-for="(_, index) in form.input_keys" :key="index">
          <el-form-item :label="'输入属性 ' + (index + 1)"><el-input v-model="form.input_keys[index]" :disabled="disabled" /></el-form-item>
          <el-button :disabled="disabled" @click="form.input_keys.splice(index, 1)">移除输入属性 {{ index + 1 }}</el-button>
        </div>
        <el-button :disabled="disabled" @click="form.input_keys.push('')">添加输入属性</el-button>
      </section>
      <rule-basis-editor v-model="form.basis_manifest" label="规则依据" :disabled="disabled" />
      <section aria-label="规则有据常量">
        <h2>有据常量</h2>
        <p>常量值必须有真实依据，常量依据必须同时出现在完整规则依据清单。</p>
        <fieldset v-for="(constant, index) in form.constants" :key="index" :disabled="disabled">
          <legend>常量 {{ index + 1 }}</legend>
          <el-form-item :label="'常量 ' + (index + 1) + ' 标识'"><el-input v-model="constant.key" :disabled="disabled" /></el-form-item>
          <el-form-item :label="'常量 ' + (index + 1) + ' 实际值（JSON）'" :error="errors['constant_' + index]">
            <el-input v-model="constant.value" type="textarea" :rows="2" :disabled="disabled" />
          </el-form-item>
          <rule-basis-editor v-model="constant.basis_refs" :label="'常量 ' + (index + 1) + ' 依据'" :disabled="disabled" />
          <el-button :disabled="disabled" @click="form.constants.splice(index, 1)">移除常量 {{ index + 1 }}</el-button>
        </fieldset>
        <el-button :disabled="disabled" @click="form.constants.push({ key: '', value: '', basis_refs: [] })">添加有据常量</el-button>
      </section>
      <el-form-item label="创建或修订理由" :error="errors.reason"><el-input v-model="form.reason" type="textarea" maxlength="2000" :disabled="disabled" /></el-form-item>
      <el-form-item label="草稿保存确认" :error="errors.attested"><el-checkbox v-model="attested" :disabled="disabled">已经核查表达式、输入属性、全部常量及固定依据，本次只保存草稿</el-checkbox></el-form-item>
      <el-alert v-if="errors.schema" :title="errors.schema" type="error" :closable="false" />
      <el-button :disabled="disabled" @click="review">校验规则定义</el-button>
      <el-button type="primary" native-type="submit" :disabled="disabled">{{ pending ? '使用原请求保存规则草稿' : '保存规则草稿' }}</el-button>
      <p v-if="pending && !mayReplay">原操作正在执行、已失败、已过期或上下文已变化，继续保留原请求并核查。</p>
      <el-collapse v-if="candidate"><el-collapse-item title="完整草稿定义预览"><pre>{{ full(candidate) }}</pre></el-collapse-item></el-collapse>
    </el-form>
    <el-dialog v-model="discardDialog" title="离开规则编辑" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p>未保存表达式、依据和理由会清除。离开页面不会取消已发送的服务器操作，原请求身份仍保留。</p>
      <template #footer><el-button @click="discardDialog = false">继续编辑</el-button><el-button :disabled="user.medicalWriting" @click="discard">放弃未保存编辑并离开</el-button></template>
    </el-dialog>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import useUserStore from '@/store/modules/user';
import SessionPanel from './session-panel.vue';
import { simulationRuleIssues } from '@/api/medical/simulation-rule-versions';
import { acquireSimulationRuleWrite } from '@/api/medical/simulation-rule-ui';
import RuleBasisEditor from './rule-basis-editor.vue';
import { fail } from '@/api/medical/protocol';
import type { Basis, MedicalSession } from '@/api/medical/protocol';
import { ruleConstantJsonIssue, readRuleConstantJson } from '@/api/medical/rule-values';
import { projectTextIssue } from '@/api/medical/project-write';
import { canonicalRule, clearRuleOperation, confirmRuleOperation, prepareRuleWrite, readPendingRule, readRule, readRulePreview, ruleAllowed, ruleEngine, ruleSchema, ruleStatusLabels, submitRuleCreate, validateRuleCreate } from '@/api/medical/simulation-rule-versions';
import type { PendingRuleWrite, RuleCreateBody, RuleCreated, StoredRule } from '@/api/medical/simulation-rule-versions';
import { lookupOperation } from '@/api/medical/operations';
import type { OperationLookup } from '@/api/medical/operations';
const user = useUserStore();
const route = useRoute();
const router = useRouter();
const formElement = ref<FormInstance>();
const form = reactive({ base_mode: '', base_id: '', base_hash: '', expression: '', input_keys: [] as string[], basis_manifest: [] as Basis[],
    constants: [] as {
        key: string;
        value: string;
        basis_refs: Basis[];
    }[], reason: '' });
const writeError = ref('');
const attested = ref(false);
const candidate = shallowRef<RuleCreateBody | null>(null);
const pending = ref<PendingRuleWrite | null>(null);
const errors = reactive<Record<string, string>>({});
const checking = ref(false);
const lookupResult = ref<OperationLookup | null>(null);
const completedRule = shallowRef<StoredRule | null>(null);
const creationReceipt = shallowRef<RuleCreated | null>(null);
const discardDialog = ref(false);
const permitted = computed(() => ruleAllowed(user.medicalSession, 'rule.write'));
const busy = computed(() => user.medicalWriting || checking.value);
const mayReplay = computed(() => !pending.value || canonicalRule(user.medicalSession?.current_context ?? null) === canonicalRule(pending.value.context)
    && lookupResult.value?.lookup_status === 'NOT_FOUND');
const mayRecover = computed(() => { const id = lookupResult.value?.operation?.resource_id; return !!id && (ruleAllowed(user.medicalSession, 'rule.read', id) || ruleAllowed(user.medicalSession, 'rule.approve', id)); });
const disabled = computed(() => !permitted.value || busy.value || !mayReplay.value);
const lookupLabels = { FOUND: '已找到原操作', NOT_FOUND: '当前未找到原操作，不能判定请求没有发送', EXPIRED: '原查询记录已过期，需要人工核查，不能自动重发' };
let lifecycleController = new AbortController();
let generation = 0;
let nextTarget: string | null = null;
let leaveApproved = false;
function full(value: unknown): string { return JSON.stringify(value, null, 2); }
function clearForm(): void {
    form.base_mode = '';
    form.base_id = '';
    form.base_hash = '';
    form.expression = '';
    form.input_keys = [];
    form.basis_manifest = [];
    form.constants = [];
    form.reason = '';
    attested.value = false;
    candidate.value = null;
    for (const key of Object.keys(errors))
        delete errors[key];
}
function reset(): void {
    lifecycleController.abort();
    lifecycleController = new AbortController();
    generation += 1;
    writeError.value = '';
    clearForm();
    pending.value = null;
    lookupResult.value = null;
    completedRule.value = null;
    creationReceipt.value = null;
    checking.value = false;
    discardDialog.value = false;
    const session: MedicalSession | null = user.medicalSession;
    if (session?.current_context && !document.hidden)
        pending.value = readPendingRule(session, null);
}
async function focusError(): Promise<void> {
    await nextTick();
    (formElement.value?.$el as HTMLElement | undefined)?.querySelector<HTMLElement>('.el-form-item.is-error input,.el-form-item.is-error textarea,.el-form-item.is-error [tabindex]')?.focus();
}
async function body(): Promise<RuleCreateBody | null> {
    candidate.value = null;
    for (const key of Object.keys(errors))
        delete errors[key];
    errors.base_mode = ['NEW', 'REVISION'].includes(form.base_mode) ? '' : '请确认本次版本依据';
    errors.expression = projectTextIssue(form.expression, 10000);
    errors.reason = projectTextIssue(form.reason, 2000);
    form.constants.forEach((constant, index) => { errors['constant_' + index] = ruleConstantJsonIssue(constant.value); });
    if (Object.values(errors).some(Boolean)) {
        await focusError();
        return null;
    }
    const value = { schema_version: '1.0.0', dependency_use: 'SIMULATION_EVALUATION', production_eligible: false, content_schema_ref: 'urn:medical:rule-expressions:1.0.0#/$defs/Content',
        base_version: form.base_mode === 'REVISION' ? { version_id: form.base_id, content_sha256: form.base_hash } : null, reason: form.reason,
        content: { schema_version: '1.0.0', engine_revision: ruleEngine, expression: { schema_version: '1.0.0', engine_revision: ruleEngine,
                expression: form.expression, input_keys: [...form.input_keys], constants: form.constants.map(item => ({ key: item.key, value: readRuleConstantJson(item.value), basis_refs: item.basis_refs.map(ref => ({ ...ref })) })) },
            basis_manifest: form.basis_manifest.map(ref => ({ ...ref })) } };
    const issues = [...simulationRuleIssues(ruleSchema + 'VersionInput', value), ...simulationRuleIssues('urn:medical:rule-expressions:1.0.0#/$defs/Content', value.content)];
    const manifest = new Map(value.content.basis_manifest.map(ref => [ref.version_id, ref.content_sha256]));
    if (issues.length || manifest.size !== value.content.basis_manifest.length || new Set(form.constants.map(item => item.key)).size !== form.constants.length
        || new Set([...(value.base_version ? [value.base_version] : []), ...value.content.basis_manifest].map(ref => ref.version_id)).size !== new Map([...(value.base_version ? [value.base_version] : []), ...value.content.basis_manifest].map(ref => [ref.version_id + ':' + ref.content_sha256, ref])).size
        || value.content.expression.constants.some(item => item.basis_refs.some(ref => manifest.get(ref.version_id) !== ref.content_sha256))) {
        errors.schema = '请核查完整表达式、唯一属性/常量标识、固定依据和摘要；常量依据必须包含在完整规则依据清单。' + issues.slice(0, 4).join('；');
        return null;
    }
    const result = validateRuleCreate(value);
    candidate.value = result;
    return result;
}
async function loadBase(): Promise<void> {
    const session: MedicalSession | null = user.medicalSession;
    const current = generation;
    const id = form.base_id;
    if (!session?.current_context || disabled.value || document.hidden)
        return;
    if (simulationRuleIssues(ruleSchema + 'Id', id).length) {
        errors.base_mode = '请填写规范的原版本标识';
        return;
    }
    checking.value = true;
    try {
        const result = await readRule(session, id, new AbortController().signal);
        if (current !== generation || user.medicalSession !== session || form.base_id !== id || document.hidden)
            return;
        if (result.rule.status !== 'APPROVED') {
            errors.base_mode = '原 SIM 规则尚未核准，不能作为修订依据';
            return;
        }
        form.base_hash = result.rule.content_sha256;
    }
    finally {
        if (current === generation)
            checking.value = false;
    }
}
async function review(): Promise<void> { if (!disabled.value)
    await body(); }
async function lookup(): Promise<void> {
    const session: MedicalSession | null = user.medicalSession;
    const original = pending.value;
    const current = generation;
    if (!session?.current_context || !original || busy.value || document.hidden)
        return;
    checking.value = true;
    try {
        const result = await lookupOperation(session, original.operation, original.key, new AbortController().signal);
        if (current === generation && user.medicalSession === session && !document.hidden)
            lookupResult.value = result;
    }
    finally {
        if (current === generation)
            checking.value = false;
    }
}
async function recover(): Promise<void> {
    const session: MedicalSession | null = user.medicalSession;
    const original = pending.value;
    const found = lookupResult.value?.operation;
    const current = generation;
    if (!session?.current_context || !original || !found?.resource_id || found.status !== 'COMPLETED' || busy.value || !mayRecover.value || document.hidden)
        return;
    checking.value = true;
    try {
        const operation = await confirmRuleOperation(session, original, found.operation_id, new AbortController().signal);
        const result = ruleAllowed(session, 'rule.read', found.resource_id) ? await readRule(session, found.resource_id, new AbortController().signal) : await readRulePreview(session, found.resource_id, new AbortController().signal);
        if (current !== generation || user.medicalSession !== session || document.hidden)
            return;
        clearRuleOperation(session, original, operation, result.rule);
        clearForm();
        pending.value = null;
        lookupResult.value = null;
        completedRule.value = result.rule;
    }
    finally {
        if (current === generation)
            checking.value = false;
    }
}
async function submit(): Promise<void> {
    const lifecycleSignal = lifecycleController.signal;
    const session: MedicalSession | null = user.medicalSession;
    const current = generation;
    if (!session?.current_context || disabled.value || document.hidden)
        return;
    const value = await body();
    errors.attested = attested.value ? '' : '请核查并确认本次只保存草稿';
    if (!value || errors.attested) {
        await focusError();
        return;
    }
    if (current !== generation || user.medicalSession !== session)
        return;
    writeError.value = '';
    const releaseWrite = acquireSimulationRuleWrite(user);
    try {
        if (value.base_version) {
            const base = await readRule(session, value.base_version.version_id, new AbortController().signal);
            if (current !== generation || user.medicalSession !== session || document.hidden)
                return;
            if (base.rule.status !== 'APPROVED' || base.rule.content_sha256 !== value.base_version.content_sha256)
                fail('CONTENT_MISMATCH', '原 SIM 规则状态或固定摘要已变化');
        }
        const original = await prepareRuleWrite(session, value);
        if (current !== generation || user.medicalSession !== session || document.hidden)
            return;
        pending.value = original;
        if (current !== generation || user.medicalSession !== session || document.hidden)
            fail('CONTEXT_CHANGED', '规则创建尚未发送，请核查当前身份');
        const result = await submitRuleCreate(session, original, value, lifecycleSignal);
        if (current !== generation || user.medicalSession !== session || document.hidden)
            return;
        const operation = await confirmRuleOperation(session, original, result.operation_id, new AbortController().signal);
        if (current !== generation || user.medicalSession !== session || document.hidden)
            return;
        clearRuleOperation(session, original, operation, result.rule);
        clearForm();
        pending.value = null;
        lookupResult.value = null;
        creationReceipt.value = result;
        completedRule.value = result.rule;
    }
    catch (error) {
        if (current === generation && user.medicalSession === session && !document.hidden) {
            writeError.value = error instanceof Error ? error.message : '提交结果未知，请查询原请求';
            pending.value = readPendingRule(session, null);
        }
    }
    finally {
        releaseWrite();
    }
}
function dirty(): boolean { return Boolean(form.base_mode || form.expression || form.reason || form.input_keys.length || form.basis_manifest.length || form.constants.length || attested.value); }
async function discard(): Promise<void> {
    const destination = nextTarget;
    clearForm();
    discardDialog.value = false;
    nextTarget = null;
    if (destination !== null) {
        leaveApproved = true;
        await router.push(destination);
        leaveApproved = false;
    }
}
function beforeUnload(event: BeforeUnloadEvent): void { if (dirty() || user.medicalWriting) {
    event.preventDefault();
    event.returnValue = '';
} }
onBeforeRouteLeave(to => { if (user.medicalWriting)
    return false; if (leaveApproved || !dirty())
    return true; nextTarget = to.fullPath; discardDialog.value = true; return false; });
watch(() => form.base_id, () => { form.base_hash = ''; }, { flush: 'sync' });
watch(form, () => { candidate.value = null; attested.value = false; }, { deep: true, flush: 'sync' });
watch(() => user.medicalSession, reset, { immediate: true, flush: 'sync' });
window.addEventListener('beforeunload', beforeUnload);
onBeforeUnmount(() => { lifecycleController.abort(); window.removeEventListener('beforeunload', beforeUnload); generation += 1; clearForm(); completedRule.value = null; creationReceipt.value = null; });
</script>
<style scoped>
pre { white-space: pre-wrap; overflow-wrap: anywhere; }
fieldset { min-width: 0; margin: 16px 0; padding: 16px; border: 1px solid var(--el-border-color); }
section { margin: 20px 0; }
</style>
