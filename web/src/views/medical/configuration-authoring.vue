<template>
<section ref="root" class="authoring" aria-label="模拟配置草稿编写">
 <div class="heading"><div><h2>{{ kind === 'Agent' ? '新建模拟 Agent' : '登记专业角色与提示词' }}</h2><p>固定版本仅保存为 DRAFT。不会发布、执行、批准或调用外部模型。</p></div><el-tag type="warning">SIMULATION · 草稿</el-tag></div>
 <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
 <section v-if="pending" class="pending"><h3>原请求待核查</h3><p>请求标识：{{ pending.key }}。客户输入未持久保存，请先查询结果，避免重复创建。</p><el-button :disabled="busy" @click="checkPending">查询原操作</el-button><el-button v-if="lookup?.operation?.status === 'COMPLETED'" :disabled="busy" @click="recover">回读原固定版本</el-button><p v-if="lookup" role="status">{{ lookupText }}</p></section>
 <el-form label-position="top" :disabled="busy" @submit.prevent="submit">
  <div class="grid">
   <el-form-item label="配置类别"><el-select v-model="kind" :disabled="!!pending"><el-option label="模拟 Agent" value="Agent"/><el-option label="专业角色 / 提示词" value="Capability"/></el-select></el-form-item>
   <el-form-item label="归属范围"><el-select v-model="project" filterable :disabled="!!pending"><el-option label="本企业" value=""/><el-option v-for="p in projects" :key="p.project_id" :label="p.name" :value="p.project_id"/></el-select><el-button link :disabled="busy" @click="loadProjects()">刷新项目</el-button><el-button v-if="projectCursor" link @click="loadProjects(projectCursor)">更多项目</el-button></el-form-item>
  </div>
  <template v-if="kind==='Capability'">
   <el-form-item label="能力类型"><el-radio-group v-model="capabilityKind"><el-radio value="ROLE">专业角色</el-radio><el-radio value="PROMPT">提示词</el-radio></el-radio-group></el-form-item>
   <el-form-item label="已登记来源成果"><el-select v-model="artifactId" filterable placeholder="从当前可读取成果选择"><el-option v-for="item in artifacts" :key="item.artifact_id" :value="item.artifact_id" :label="item.artifact_kind+' · '+item.created_at+' · '+item.artifact_id.slice(0,8)"/></el-select><el-button link @click="loadArtifacts()">读取来源成果</el-button><el-button v-if="artifactCursor" link @click="loadArtifacts(artifactCursor)">更多成果</el-button><p>来源权利、完整依赖和配置用途由服务端逐次核验。可读取成果不代表可用于本次配置。</p></el-form-item>
   <div class="grid"><el-form-item label="输入契约"><el-select v-model="inputSchema" filterable><el-option v-for="s in publishedSchemas" :key="s.schema_ref" :label="s.label" :value="s.schema_ref"/></el-select></el-form-item><el-form-item label="输出契约"><el-select v-model="outputSchema" filterable><el-option v-for="s in publishedSchemas" :key="s.schema_ref" :label="s.label" :value="s.schema_ref"/></el-select></el-form-item></div>
   <p>契约摘要来自本地已发布目录。此受限流程的工具范围固定为空。</p>
  </template>
  <template v-else>
   <el-form-item label="Agent 名称"><el-input v-model="name" maxlength="100" autocomplete="off"/></el-form-item>
   <div class="grid"><el-form-item v-for="field in ['ROLE','PROMPT'] as const" :key="field" :label="field==='ROLE'?'专业角色固定版本':'提示词固定版本'"><el-input v-model="capabilityIds[field]" placeholder="填写已有草稿回执的固定版本编号" autocomplete="off"/><el-button link @click="loadCapability(field)">核验并选入</el-button><p v-if="capabilities[field]">已回读 {{ field }} · {{ capabilities[field]!.definition.created_at }}</p></el-form-item></div>
   <p>专业角色与提示词暂未提供目录接口；仅接受通过本流程登记并已回读核验的固定版本，摘要自动绑定。</p>
   <el-form-item label="模拟模型固定版本"><el-select v-model="modelId" filterable placeholder="选择实际模拟配置"><el-option v-for="m in availableModels" :key="m.model_config_revision" :value="m.model_config_revision" :label="m.definition.model_id+' · '+m.definition.model_version"/></el-select><el-button link @click="loadModels()">读取模拟模型</el-button><el-button v-if="modelCursor" link @click="loadModels(modelCursor)">更多模型</el-button></el-form-item>
   <h3>研究与运行上限</h3><p>所有上限必须显式填写。费用和工具调用固定为 0；不进行付费重试或模型自主派生。</p>
   <div class="grid"><el-form-item v-for="field in limitFields" :key="field.key" :label="limitLabels[field.key] || field.key"><el-input v-model="limits[field.key]" :inputmode="field.key==='currency'?'text':'numeric'" :maxlength="field.key==='currency'?3:20" autocomplete="off"/></el-form-item></div>
  </template>
  <el-checkbox v-model="attested">确认登记实际来源与上限，仅创建不可用于生产的模拟草稿</el-checkbox>
  <p v-if="!mayCreate">当前所选范围没有新建配置授权。</p>
  <div class="actions"><el-button type="primary" native-type="submit" :disabled="!mayCreate || !attested || !!pending && lookup?.lookup_status!=='NOT_FOUND'" :loading="busy">{{ pending?'按原请求内容重试':'保存 DRAFT 草稿' }}</el-button><el-button :disabled="busy" @click="clearForm">清除表单</el-button></div>
 </el-form>
 <section v-if="result" class="result" aria-label="固定配置保存结果"><h3>已回读核验的固定草稿</h3><el-descriptions :column="1" border><el-descriptions-item label="固定版本">{{ result.definition.version_id }}</el-descriptions-item><el-descriptions-item label="摘要">{{ result.content_sha256 }}</el-descriptions-item><el-descriptions-item label="保存状态">DRAFT · 不具备生产资格</el-descriptions-item><el-descriptions-item label="创建时间">{{ result.definition.created_at }}</el-descriptions-item></el-descriptions></section>
<el-dialog v-model="leaveDialog" title="离开未保存的配置？" width="min(92vw,480px)" :close-on-click-modal="false"><p>未保存的表单会清除。已有原请求标识保留，离开不会取消服务器操作。</p><template #footer><el-button @click="leaveDialog=false">继续填写</el-button><el-button type="danger" @click="confirmLeave">清除并离开</el-button></template></el-dialog>
</section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRouter } from 'vue-router';
import useUserStore from '@/store/modules/user';
import { authorAllowed, authorFieldIssue, authorInputIssue, limitFields, lookupAuthor, prepareAuthor, publishedSchemas, readAuthored, readPendingAuthor, recoverAuthor, submitAuthor } from '@/api/medical/configuration-authoring';
import type { AuthorKind, AuthorRequest, AuthorView, PendingAuthor } from '@/api/medical/configuration-authoring';
import type { MedicalSession, Project } from '@/api/medical/protocol';
import { fail } from '@/api/medical/protocol';
import type { OperationLookup } from '@/api/medical/operations';
import { listProjects } from '@/api/medical/projects';
import { readArtifactCatalogue } from '@/api/medical/source-governance';
import type { ArtifactCatalogueItem } from '@/api/medical/source-governance';
import { listSimulationConfigurations } from '@/api/medical/simulation-model-configurations';
import type { SimulationView } from '@/api/medical/simulation-model-configuration-domain';
const router = useRouter(), leaveDialog = ref(false);
let leavePath = '', leaveApproved = false;
const emit = defineEmits<{
    saved: [
        AuthorView
    ];
}>();
const user = useUserStore();
const root = ref<HTMLElement | null>(null);
const kind = ref<AuthorKind>('Agent'), project = ref(''), name = ref(''), capabilityKind = ref<'ROLE' | 'PROMPT'>('ROLE'), artifactId = ref(''), inputSchema = ref(''), outputSchema = ref(''), modelId = ref(''), attested = ref(false), busy = ref(false), error = ref('');
const projects = ref<Project[]>([]), artifacts = ref<ArtifactCatalogueItem[]>([]), models = ref<SimulationView[]>([]);
const projectCursor = ref<string | null>(null), artifactCursor = ref<string | null>(null), modelCursor = ref<string | null>(null);
const capabilityIds = reactive({ ROLE: '', PROMPT: '' });
const capabilities = reactive<{
    ROLE: AuthorView | null;
    PROMPT: AuthorView | null;
}>({ ROLE: null, PROMPT: null });
const limits = reactive<Record<string, string>>({});
const pending = ref<PendingAuthor | null>(null), lookup = ref<OperationLookup | null>(null), result = ref<AuthorView | null>(null);
const limitLabels: Record<string, string> = { activity_start_to_close_seconds: '单步骤最长时限（秒）', heartbeat_interval_seconds: '心跳间隔（秒）', heartbeat_timeout_seconds: '心跳超时（秒）', sdk_initialize_timeout_seconds: 'SDK 初始化超时（秒）', sdk_request_timeout_seconds: 'SDK 请求超时（秒）', model_request_timeout_seconds: '模型请求超时（秒）', max_wall_time_seconds: '总运行时限（秒）', max_model_requests: '模型请求次数', max_research_questions: '研究问题数', max_research_rounds: '研究轮次', max_revision_rounds: '修订轮次', max_parallel_steps: '并行步骤数', max_input_tokens: '输入 Token 上限', max_output_tokens: '输出 Token 上限', max_total_tokens: '总 Token 上限', currency: '币种（大写三字母）', max_consecutive_no_progress: '连续无进展次数', max_repeated_failure_signature: '重复失败次数' };
const availableModels = computed(() => models.value.filter(m => m.project_id === null || m.project_id === (project.value || null)));
const mayCreate = computed(() => authorAllowed(user.medicalSession, kind.value === 'Agent' ? 'agent.configure' : 'capability.configure', project.value || null));
const lookupText = computed(() => lookup.value?.lookup_status === 'EXPIRED' ? '原请求查询已过期，禁止重发。请交由授权管理员核查。' : lookup.value?.lookup_status === 'NOT_FOUND' ? '未发现原操作。仅可用完全相同内容、上下文和请求标识重试。' : lookup.value?.operation?.status === 'COMPLETED' ? '原操作已完成，请回读固定版本。' : '原操作仍在处理或失败，保留请求标识，继续核查。');
let controller = new AbortController(), generation = 0, mounted = true;
function clearForm() { name.value = ''; artifactId.value = ''; inputSchema.value = ''; outputSchema.value = ''; modelId.value = ''; capabilityIds.ROLE = ''; capabilityIds.PROMPT = ''; capabilities.ROLE = null; capabilities.PROMPT = null; attested.value = false; for (const field of limitFields)
    limits[field.key] = ''; }
function clear() { root.value?.querySelectorAll<HTMLInputElement>('input').forEach(input => { input.value = ''; if (input.type === 'checkbox')
    input.checked = false; }); controller.abort(); controller = new AbortController(); generation++; clearForm(); projects.value = []; artifacts.value = []; models.value = []; projectCursor.value = null; artifactCursor.value = null; modelCursor.value = null; result.value = null; pending.value = null; lookup.value = null; error.value = ''; project.value = ''; }
async function run(work: (s: MedicalSession, signal: AbortSignal, valid: () => boolean) => Promise<void>) { const s = user.medicalSession as MedicalSession | null; if (!s?.current_context || busy.value || document.hidden)
    return; const g = generation; const signal = controller.signal; busy.value = true; error.value = ''; const valid = () => mounted && g === generation && user.medicalSession === s && !document.hidden; try {
    await work(s, signal, valid);
}
catch (e) {
    if (mounted && !document.hidden && user.medicalSession?.user.user_id === s.user.user_id && user.medicalSession?.current_context?.tenant_id === s.current_context?.tenant_id) {
        error.value = e instanceof Error ? e.message : '读取或保存失败，请核查当前会话';
        await nextTick();
        root.value?.querySelector<HTMLElement>('input')?.focus();
    }
}
finally {
    busy.value = false;
} }
async function loadProjects(after?: string) { await run(async (s, signal, valid) => { const p = await listProjects(s.current_context!, after ? { after } : {}, signal); if (valid()) {
    projects.value = after ? [...projects.value, ...p.projects] : p.projects;
    projectCursor.value = p.next_after;
} }); }
async function loadArtifacts(after?: string) { await run(async (s, signal, valid) => { const p = await readArtifactCatalogue(s, { limit: 20, ...(project.value ? { project_id: project.value } : {}), ...(after ? { after } : {}) }, signal); if (valid()) {
    artifacts.value = after ? [...artifacts.value, ...p.items] : p.items;
    artifactCursor.value = p.next_after;
} }); }
async function loadModels(after?: string) { await run(async (s, signal, valid) => { const p = await listSimulationConfigurations(s, { limit: 20, ...(after ? { after } : {}) }, signal); if (valid()) {
    models.value = after ? [...models.value, ...p.items] : p.items;
    modelCursor.value = p.next_after;
} }); }
async function loadCapability(field: 'ROLE' | 'PROMPT') { capabilities[field] = null; const issue = authorFieldIssue('Id', capabilityIds[field]); if (issue) {
    error.value = '请填写有效的角色或提示词固定版本编号';
    return;
} await run(async (s, signal, valid) => { const id = capabilityIds[field]; const v = await readAuthored(s, 'Capability', id, signal); if (!('kind' in v.definition.request) || v.definition.request.kind !== field || v.definition.request.project_id !== null && v.definition.request.project_id !== (project.value || null))
    fail('CONTENT_MISMATCH', '所选能力类型或项目范围不匹配'); if (valid() && capabilityIds[field] === id)
    capabilities[field] = v; }); }
function body(): AuthorRequest {
    if (kind.value === 'Capability') {
        const a = artifacts.value.find(a => a.artifact_id === artifactId.value), i = publishedSchemas.find(s => s.schema_ref === inputSchema.value), o = publishedSchemas.find(s => s.schema_ref === outputSchema.value);
        if (!a || !i || !o)
            throw new Error('请选择来源成果、输入契约和输出契约');
        return { schema_version: '1.0.0', execution_mode: 'SIMULATION', project_id: project.value || null, kind: capabilityKind.value, source_artifact: { version_id: a.artifact_id, content_sha256: a.sha256 }, input_schema_ref: { schema_ref: i.schema_ref, schema_sha256: i.schema_sha256 }, output_schema_ref: { schema_ref: o.schema_ref, schema_sha256: o.schema_sha256 }, tool_scope: [] };
    }
    const role = capabilities.ROLE, prompt = capabilities.PROMPT, model = availableModels.value.find(m => m.model_config_revision === modelId.value);
    if (!role || !prompt || !model)
        throw new Error('请先核验专业角色、提示词并选择模拟模型');
    const l: Record<string, string | number> = { paid_request_extra_retries: 0, model_spawned_subagents: 0, max_tool_calls: '0', max_cost_amount: '0' };
    for (const field of limitFields) {
        const v = limits[field.key] ?? '';
        if (field.seconds && !/^[1-9][0-9]*$/.test(v))
            throw new Error('请填写整数秒数：' + limitLabels[field.key]);
        l[field.key] = field.seconds ? Number(v) : v;
    }
    return { schema_version: '1.0.0', execution_mode: 'SIMULATION', project_id: project.value || null, name: name.value, role_version: { version_id: role.definition.version_id, content_sha256: role.content_sha256 }, prompt_revision: { version_id: prompt.definition.version_id, content_sha256: prompt.content_sha256 }, model_config_revision: { version_id: model.model_config_revision, content_sha256: model.content_sha256 }, limits: l };
}
async function submit() { if (!mayCreate.value || !attested.value || pending.value && lookup.value?.lookup_status !== 'NOT_FOUND')
    return; await run(async (s, signal) => { const b = body(); const issue = authorInputIssue(kind.value, b); if (issue) {
    error.value = issue;
    await nextTick();
    root.value?.querySelector<HTMLElement>('input')?.focus();
    return;
} const p = await prepareAuthor(s, kind.value, b, signal); pending.value = p; user.medicalWriting = true; try {
    const r = await submitAuthor(s, p, b, signal);
    const fresh = await user.readMedical() as MedicalSession | null;
    if (!mounted || document.hidden || !fresh?.current_context || fresh.user.user_id !== p.actor || fresh.current_context.tenant_id !== p.tenant_id)
        fail('CONTEXT_CHANGED', '保存后当前身份已变化，请核查原请求');
    const view = await recoverAuthor(fresh, p, r.operation_id, controller.signal, r);
    if (mounted && !document.hidden && user.medicalSession === fresh) {
        result.value = view;
        pending.value = null;
        clearForm();
        emit('saved', view);
    }
}
finally {
    user.medicalWriting = false;
} }); }
async function checkPending() { await run(async (s, signal, valid) => { const p = pending.value; if (!p)
    return; const v = await lookupAuthor(s, p, signal); if (valid())
    lookup.value = v; }); }
async function recover() { await run(async (s, signal, valid) => { const p = pending.value, op = lookup.value?.operation; if (!p || op?.status !== 'COMPLETED')
    return; const v = await recoverAuthor(s, p, op.operation_id, signal); if (valid()) {
    result.value = v;
    pending.value = null;
    lookup.value = null;
    clearForm();
    emit('saved', v);
} }); }
function restore() { clear(); if (user.medicalSession?.current_context && !document.hidden) {
    try {
        pending.value = readPendingAuthor(user.medicalSession);
        if (pending.value) {
            kind.value = pending.value.kind;
            project.value = pending.value.project_id ?? '';
        }
    }
    catch (e) {
        error.value = e instanceof Error ? e.message : '原请求元数据异常';
    }
} }
function visibility() { if (document.hidden)
    clear();
else
    restore(); }
function dirty() { return !!name.value || !!artifactId.value || !!capabilityIds.ROLE || !!capabilityIds.PROMPT || Object.values(limits).some(Boolean); }
function unload(event: BeforeUnloadEvent) { if (dirty() || user.medicalWriting) {
    event.preventDefault();
    event.returnValue = '';
} }
async function confirmLeave() { clearForm(); leaveDialog.value = false; leaveApproved = true; try {
    await router.push(leavePath);
}
finally {
    leaveApproved = false;
    leavePath = '';
} }
onBeforeRouteLeave(to => { if (user.medicalWriting)
    return false; if (leaveApproved || !dirty())
    return true; leavePath = to.fullPath; leaveDialog.value = true; return false; });
watch(() => user.medicalSession, restore, { immediate: true, flush: 'sync' });
watch(project, () => { artifacts.value = []; artifactId.value = ''; artifactCursor.value = null; capabilities.ROLE = null; capabilities.PROMPT = null; });
watch(() => capabilityIds.ROLE, () => capabilities.ROLE = null);
watch(() => capabilityIds.PROMPT, () => capabilities.PROMPT = null);
document.addEventListener('visibilitychange', visibility);
window.addEventListener('beforeunload', unload);
onBeforeUnmount(() => { mounted = false; clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('beforeunload', unload); });
</script>
<style scoped>
.authoring {
  padding: 24px;
  border: 1px solid #DCE3EC;
  border-radius: 8px;
  background: #fff;
  color: #243247;
}

.heading {
  display: flex;
  justify-content: space-between;
  gap: 16px
}

.heading h2 {
  margin-top: 0;
  font-size: 17px
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 20px
}

.pending,
.result {
  margin: 20px 0;
  padding: 16px;
  background: #F8FAFC;
  border: 1px solid #DCE3EC;
  border-radius: 8px
}

p {
  color: #526279;
  line-height: 1.6;
  overflow-wrap: anywhere
}

:deep(.el-select) {
  width: 100%
}

:deep(.el-descriptions__content) {
  overflow-wrap: anywhere
}

@media(max-width:650px) {
  .grid {
    grid-template-columns: 1fr
  }

  .authoring {
    padding: 16px
  }

  .heading {
    flex-direction: column
  }

  .actions {
    flex-wrap: wrap
  }
}
</style>
