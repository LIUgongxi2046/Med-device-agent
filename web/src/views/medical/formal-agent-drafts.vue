<template>
 <main class="medical-page draft-page">
  <nav><router-link to="/agents">Agent 管理</router-link> / 候选与运行能力草稿</nav>
  <header><h1>候选与运行能力草稿</h1><p>登记真实固定依赖，保存可追溯版本。候选登记不产生发布、专业检查通过或执行资格。</p></header>
  <session-panel/>
  <p v-if="error" role="alert" class="error">{{ error }}</p>
  <p v-if="notice" role="status">{{ notice }}</p>
  <p v-if="available===false" role="status">当前部署未启用正式候选作者服务。部署状态与业务权限分别核验。</p>
  <section class="card controls">
   <label>类型<select v-model="kind" :disabled="busy || !!pending"><option value="AGENT">Agent 候选</option><option value="TOOL">工具 TOOL</option><option value="SKILL">Skill</option></select></label>
   <label>归属范围<select v-model="project" :disabled="busy || !!pending"><option value="">当前企业</option><option v-for="item in projects" :key="item.project_id" :value="item.project_id">{{ item.name }}</option></select></label>
   <button :disabled="busy" @click="bootstrap">核查服务与身份</button><button :disabled="busy" @click="loadProjects(false)">读取项目目录</button><button v-if="projectCursor" :disabled="busy" @click="loadProjects(true)">更多项目</button>
  </section>
  <section v-if="pending" class="card" aria-label="原请求恢复"><h2>原请求待核查</h2><p>完整原声明、摘要与请求键已保留。切换范围或离开页面不会撤销服务器操作。</p><p class="identifier">{{ pending.key }}</p><button :disabled="busy || available!==true" @click="recover">只读核查原操作与固定回执</button><p>未查到不代表未执行；本页不会换键重发。当前接口没有安全取消能力，需保留原记录继续核查。</p></section>
  <section v-if="available===true" class="card" aria-label="真实草稿目录"><h2>已登记版本</h2><button :disabled="busy" @click="list(false)">读取本范围</button><button v-if="cursor" :disabled="busy" @click="list(true)">下一页</button><p>每页只读取有界元数据。当前可见页不代表全量数量或完整授权集合。</p>
   <p v-if="listed&&!items.length">本页没有可读记录。<span v-if="cursor">仍可继续下一页。</span></p>
   <ul><li v-for="item in items" :key="item.record_id"><strong>{{ item.name||item.kind+' 固定草稿' }}</strong> · {{ item.status }} · {{ item.created_at }} <button :disabled="busy" @click="open(item)">读取固定详情</button><small class="identifier">{{ item.version_id }}</small></li></ul>
  </section>
  <section v-if="detail" class="card" aria-label="固定草稿详情"><h2>固定版本详情</h2><p>状态 {{ detail.status }} · 生产资格未由此读接口授予</p><p class="identifier">{{ fixedDetail?.version_id }}</p><p>登记时间：{{ fixedDetail?.created_at }}</p><p v-if="fixedDetail?.name">名称：{{ fixedDetail.name }}</p>
   <dl v-if="fixedDetail?.manifest"><template v-for="field in manifestGroups" :key="field"><dt>{{ manifestLabel(field) }}</dt><dd>{{ fixedDetail.manifest[field]?.length||0 }} 个固定版本</dd></template></dl>
   <p v-if="fixedDetail?.request">能力：{{ fixedDetail.request.kind }} · 模式 {{ fixedDetail.request.execution_mode }}；固定来源 {{ fixedDetail.request.source_artifact.version_id }}</p>
   <details><summary>核验所需完整固定声明</summary><pre>{{ JSON.stringify(fixedDetail,null,2) }}</pre></details>
  </section>
  <form v-if="available===true&&!pending" class="card" @submit.prevent="submit">
   <h2>{{ kind==='AGENT'?'登记 Agent 候选':'登记 '+kind+' 草稿' }}</h2>
   <p v-if="!mayCreate">当前范围没有 {{ kind==='AGENT'?'agent.configure':'capability.configure' }} 权限。</p>
   <fieldset :disabled="busy"><legend>真实依赖选择</legend>
    <div class="actions"><button type="button" @click="loadArtifacts(false)">读取来源成果</button><button v-if="artifactCursor" type="button" @click="loadArtifacts(true)">更多来源成果</button></div>
    <p>成果目录仅提供固定元数据；完整原件、来源权利与 AGENT_PUBLICATION 用途由服务端提交时核验。</p>
    <template v-if="kind!=='AGENT'">
     <label>来源成果<select v-model="artifactId"><option value="">请选择真实成果</option><option v-for="a in artifacts" :key="a.artifact_id" :value="a.artifact_id">{{ a.artifact_kind }} · {{ a.created_at }} · {{ a.artifact_id }}</option></select></label>
     <label>输入契约<select v-model="inputSchema"><option value="">请选择</option><option v-for="s in capabilitySchemas" :key="s.schema_ref" :value="s.schema_ref">{{ s.label }}</option></select></label>
     <label>输出契约<select v-model="outputSchema"><option value="">请选择</option><option v-for="s in capabilitySchemas" :key="s.schema_ref" :value="s.schema_ref">{{ s.label }}</option></select></label>
     <h3>精确权限声明</h3><label>当前已授权动作<select v-model="scopeIndex"><option value="">请选择</option><option v-for="(s,i) in scopeCandidates" :key="i" :value="String(i)">{{ s.action }} · {{ s.scope.resource_kind }} · {{ s.scope.resource_id }}</option></select></label><label>实际业务用途<input v-model="purpose" maxlength="128" placeholder="填写真实受支持用途，不扩大授权"/></label><button type="button" @click="addPermission">加入声明</button><ul><li v-for="(p,i) in permissions" :key="i">{{ p.action }} · {{ p.purpose }}<button type="button" @click="permissions.splice(i,1)">移除</button></li></ul>
    </template>
    <template v-else>
     <label>候选名称<input v-model="name" maxlength="200"/></label>
     <button type="button" @click="loadPolicies(false)">读取执行策略</button><button v-if="policyCursor" type="button" @click="loadPolicies(true)">更多策略</button>
     <label>固定执行策略<select v-model="policyId"><option value="">请选择真实已批准策略</option><option v-for="p in policies" :key="p.fixed.version_id" :value="p.fixed.version_id" :disabled="p.status!=='APPROVED'">{{ p.status }} · {{ p.fixed.created_at }} · {{ p.fixed.version_id }}</option></select></label>
     <button type="button" :disabled="!policyId" @click="applyPolicy">从所选固定策略填入依赖绑定</button><p v-if="selectedPolicy && !selectedPolicy.fixed.definition.bindings.some(b=>b.model_version)">所选策略没有模型节点，而当前候选契约要求非空模型依赖；不能据此登记完整 Agent 候选。请先准备真实完整策略。</p>
     <p v-if="!policies.some(p=>p.status==='APPROVED')">尚未读取可选择的已批准策略。策略批准本身不等于 Agent 发布；完整专业运行依赖仍需实际具备。</p>
     <div class="actions"><button v-for="group in dependencyGroups" :key="group" type="button" @click="loadDependencies(group,false)">读取 {{ group }}</button></div>
     <div class="actions"><button v-for="group in dependencyGroups.filter(g=>dependencyCursors[g])" :key="group" type="button" @click="loadDependencies(group,true)">更多 {{ group }}</button></div>
     <p>ROLE/PROMPT、TOOL/SKILL、规则与模型分别来自真实目录。未发现的固定运行组件不能自动补齐。缺依赖时保存预检会明确拒绝。</p>
     <label>模板适用项目<select v-model="templateProject"><option value="">选择已读取项目</option><option v-for="p in projects" :key="p.project_id" :value="p.project_id">{{ p.name }}</option></select></label><label>文稿类型<select v-model="documentType"><option v-for="(label,value) in taskDocumentLabels" :key="value" :value="value">{{ label }}</option></select></label><button type="button" @click="loadTemplates(false)">读取适用模板</button><button v-if="templateHasNext" type="button" @click="loadTemplates(true)">更多适用模板</button>
     <p>模板目录依赖真实项目档案与文稿类型；企业级候选也须显式选择适用项目进行查询。</p>
     <formal-agent-manifest-field :schema="draftManifestSchema" path="" v-model="manifest" :choices="choices" :disabled="busy"/>
    </template>
    <label>登记理由<textarea v-model="reason" maxlength="2000" rows="3"/></label>
    <label class="attestation"><input type="checkbox" v-model="attested"/>我已核查真实依赖与用途，确认仅保存 DRAFT，不取得发布或执行资格。</label>
   </fieldset>
   <button type="submit" :disabled="busy||!mayCreate||!attested">保存固定草稿</button>
  </form>
 </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import useUserStore from '@/store/modules/user';
import SessionPanel from './session-panel.vue';
import FormalAgentManifestField from './formal-agent-manifest-field.vue';
import { draftAvailability, queryDrafts, readDraft, createDraft, recoverDraft, pendingDraft, draftAllowed, draftIssue, draftCopy, draftCanonical, capabilityScopeCandidates, capabilitySchemas, draftManifestSchema } from '@/api/medical/formal-agent-drafts';
import type { DraftKind, DraftBody, DraftSummary, PendingDraft } from '@/api/medical/formal-agent-drafts';
import { emptyManifestValue, manifestLabel } from '@/api/medical/formal-agent-manifest-form';
import type { ManifestChoice } from '@/api/medical/formal-agent-manifest-form';
import { capabilityArtifactCandidates, capabilityProjectCandidates } from '@/api/medical/formal-capability-candidates';
import { queryCapabilities } from '@/api/medical/formal-capabilities';
import { queryPolicies } from '@/api/medical/execution-policies';
import type { PolicyView } from '@/api/medical/execution-policy-domain';
import { readRuleList } from '@/api/medical/rule-versions';
import { listModelConfigurations } from '@/api/medical/model-configurations';
import { listApplicableTemplates } from '@/api/medical/template-applicability';
import type { ApplicableTemplatePage, DocumentType } from '@/api/medical/template-applicability';
import { listGovernance } from '@/api/medical/member-governance';
import { listRoles } from '@/api/medical/enterprise-roles';
import { taskDocumentLabels } from '@/api/medical/tasks';
import type { MedicalSession, Project } from '@/api/medical/protocol';
import type { ArtifactCatalogueItem } from '@/api/medical/source-governance';
const user = useUserStore(), kind = ref<DraftKind>('AGENT'), project = ref(''), available = ref<boolean | null>(null), error = ref(''), notice = ref(''), loading = ref(false);
const pending = ref<PendingDraft | null>(null), items = ref<DraftSummary[]>([]), cursor = ref<string | null>(null), listed = ref(false), detail = ref<DraftBody | null>(null);
const projects = ref<Project[]>([]), projectCursor = ref<string | null>(null), artifacts = ref<ArtifactCatalogueItem[]>([]), artifactCursor = ref<string | null>(null);
const artifactId = ref(''), inputSchema = ref(''), outputSchema = ref(''), scopeIndex = ref(''), purpose = ref(''), permissions = ref<any[]>([]), reason = ref(''), attested = ref(false), name = ref('');
const policies = ref<PolicyView[]>([]), policyCursor = ref<string | null>(null), policyId = ref(''), manifest = ref<DraftBody>(emptyManifestValue(draftManifestSchema)), versionChoices = ref<ManifestChoice[]>([]), dependencyCursors = ref<Record<string, string | null>>({});
const memberWatermark = ref<string | null>(null);
const templateProject = ref(''), documentType = ref<DocumentType>('CLINICAL_EVALUATION'), templatePage = ref<ApplicableTemplatePage | null>(null);
const dependencyGroups = ['ROLE/PROMPT', 'TOOL', 'SKILL', 'RULE', 'MODEL', 'MEMBER_ID', 'ROLE_ID'], manifestGroups = ['role_versions', 'skill_versions', 'tool_versions', 'prompt_revisions', 'template_versions', 'rule_versions', 'retrieval_profiles', 'model_config_revisions'];
let controller = new AbortController(), generation = 0, mounted = true, writeOwner: object | null = null;
const busy = computed(() => loading.value || user.medicalWriting), mayCreate = computed(() => !!user.medicalSession && draftAllowed(user.medicalSession, kind.value, project.value || null));
const selectedPolicy = computed(() => policies.value.find(p => p.fixed.version_id === policyId.value) || null);
const fixedDetail = computed(() => detail.value?.fixed || detail.value?.content), scopeCandidates = computed(() => user.medicalSession?.current_context ? capabilityScopeCandidates(user.medicalSession, project.value || null) : []);
const templateHasNext = computed(() => !!templatePage.value && BigInt(templatePage.value.page_info.page * templatePage.value.page_info.page_size) < BigInt(templatePage.value.page_info.total));
const choices = computed<ManifestChoice[]>(() => [...versionChoices.value, ...projects.value.map(p => ({ label: p.name, value: p.project_id, group: 'PROJECT_ID' })), ...(user.medicalSession?.current_context ? [{ label: '当前企业', value: user.medicalSession.current_context.tenant_id, group: 'TENANT_ID' }] : []), ...artifacts.value.map(a => ({ label: a.artifact_kind + ' 固定版本 · ' + a.artifact_id, value: { version_id: a.artifact_id, content_sha256: a.sha256 }, group: 'REGISTERED_ARTIFACT' })), ...capabilitySchemas.map(s => ({ label: s.label, value: { schema_ref: s.schema_ref, schema_sha256: s.schema_sha256 }, group: 'SCHEMA' })), ...artifacts.value.map(a => ({ label: a.artifact_kind + ' · ' + a.artifact_id, value: { artifact_id: a.artifact_id, sha256: a.sha256, size_bytes: a.size_bytes, media_type: a.media_type }, group: 'ARTIFACT' })), ...policies.value.flatMap(p => [{ label: '来源策略 · ' + p.fixed.version_id, value: p.fixed.definition.source_selection.version, group: 'SOURCE_SCOPE' }, ...p.fixed.definition.retrieval_profiles.map(r => ({ label: '检索策略 · ' + r.version.version_id, value: r.version, group: 'RETRIEVAL' }))])]);
function reader(): MedicalSession { if (!user.medicalSession?.current_context)
    throw new Error('请先恢复身份并选择企业'); return user.medicalSession; }
function live(g: number, s: MedicalSession) { return mounted && generation === g && user.medicalSession === s && !document.hidden; }
async function run(action: (s: MedicalSession, signal: AbortSignal, live: () => boolean) => Promise<void>, write = false) { if (busy.value)
    return; if (!user.medicalSession?.current_context) {
    error.value = '请先恢复身份并选择企业';
    return;
} const s = reader(), g = generation, signal = controller.signal, owner = {}; loading.value = true; error.value = ''; if (write) {
    writeOwner = owner;
    user.medicalWriting = true;
} try {
    await action(s, signal, () => live(g, s));
}
catch (e) {
    if (live(g, s) && !signal.aborted)
        error.value = e instanceof Error ? e.message : '请求未完成，请核查原操作';
}
finally {
    if (live(g, s))
        loading.value = false;
    if (writeOwner === owner) {
        writeOwner = null;
        user.medicalWriting = false;
        if (mounted && generation !== g && user.medicalSession?.current_context && !document.hidden)
            void bootstrap();
    }
} }
function clear() { project.value = ''; controller.abort(); controller = new AbortController(); generation++; loading.value = false; available.value = null; error.value = ''; notice.value = ''; pending.value = null; items.value = []; cursor.value = null; listed.value = false; detail.value = null; projects.value = []; projectCursor.value = null; clearForm(); }
function clearForm() { artifacts.value = []; artifactCursor.value = null; artifactId.value = ''; inputSchema.value = ''; outputSchema.value = ''; permissions.value = []; scopeIndex.value = ''; purpose.value = ''; reason.value = ''; attested.value = false; name.value = ''; policies.value = []; policyCursor.value = null; policyId.value = ''; manifest.value = emptyManifestValue(draftManifestSchema); versionChoices.value = []; dependencyCursors.value = {}; templatePage.value = null; templateProject.value = ''; memberWatermark.value = null; }
async function bootstrap() { return run(async (s, signal, valid) => { const original = pendingDraft(s); if (valid())
    pending.value = original; const a = await draftAvailability(reader, signal); if (valid())
    available.value = a.features.formal_agent_authoring; }); }
async function loadProjects(more: boolean) { return run(async (_s, signal, valid) => { const p = await capabilityProjectCandidates(reader, more ? projectCursor.value : null, signal); if (valid()) {
    projects.value = more ? [...projects.value, ...p.projects] : p.projects;
    projectCursor.value = p.next_after;
} }); }
async function list(more: boolean) { if (available.value !== true)
    return; return run(async (_s, signal, valid) => { const p = await queryDrafts(reader, kind.value, project.value || null, more ? cursor.value : null, signal); if (valid()) {
    items.value = p.items;
    cursor.value = p.next_cursor;
    listed.value = true;
    detail.value = null;
} }); }
async function open(item: DraftSummary) { return run(async (_s, signal, valid) => { const v = await readDraft(reader, item.kind, item.record_id, signal); if (valid())
    detail.value = v; }); }
async function loadArtifacts(more: boolean) { return run(async (_s, signal, valid) => { const p = await capabilityArtifactCandidates(reader, project.value || null, more ? artifactCursor.value : null, signal); if (valid()) {
    artifacts.value = more ? [...artifacts.value, ...p.items] : p.items;
    artifactCursor.value = p.next_after;
} }); }
async function loadPolicies(more: boolean) { return run(async (_s, signal, valid) => { const p = await queryPolicies(reader, project.value || null, more ? policyCursor.value : null, signal); if (valid()) {
    policies.value = more ? [...policies.value, ...p.items] : p.items;
    policyCursor.value = p.next_cursor;
} }); }
function addChoices(group: string, next: ManifestChoice[], more: boolean) { const previous = more ? versionChoices.value : versionChoices.value.filter(x => group === 'ROLE/PROMPT' ? !['ROLE', 'PROMPT'].includes(x.group) : x.group !== group); const combined = [...previous, ...next]; versionChoices.value = combined.filter((x, i) => combined.findIndex(y => y.group === x.group && draftCanonical(y.value) === draftCanonical(x.value)) === i); }
async function loadDependencies(group: string, more: boolean) {
    return run(async (s, signal, valid) => {
        const after = more ? dependencyCursors.value[group] || null : null;
        let next: string | null = null, options: ManifestChoice[] = [];
        if (group === 'ROLE/PROMPT') {
            const p = await queryCapabilities(reader, after, signal);
            next = p.next_cursor;
            options = p.items.filter(x => x.content.request.project_id === null || x.content.request.project_id === (project.value || null)).map(x => ({ label: x.content.request.kind + ' · ' + x.content.created_at + ' · ' + x.resource_id, value: { version_id: x.resource_id, content_sha256: x.content_sha256 }, group: x.content.request.kind }));
        }
        else if (group === 'TOOL' || group === 'SKILL') {
            const p = await queryDrafts(reader, group, project.value || null, after, signal);
            next = p.next_cursor;
            options = p.items.map(x => ({ label: x.kind + ' · ' + x.created_at + ' · ' + x.version_id, value: { version_id: x.version_id, content_sha256: x.content_sha256 }, group }));
        }
        else if (group === 'RULE') {
            const p = await readRuleList(s, { limit: 20, ...(after ? { after } : {}) }, signal);
            next = p.next_after;
            options = p.items.map(x => ({ label: x.rule.status + ' · ' + x.rule.rule_version, value: { version_id: x.rule.rule_version, content_sha256: x.rule.content_sha256 }, group }));
        }
        else if (group === 'MEMBER_ID') {
            const p = await listGovernance(s, 'Members', { limit: 20, ...(after ? { after, dataset_watermark: memberWatermark.value! } : {}) }, signal);
            next = p.next_after;
            options = p.items.filter(x => 'member_id' in x && x.status === 'ACTIVE').map(x => ({ label: '真实成员用户 · ' + ('user_id' in x ? x.user_id : ''), value: 'user_id' in x ? x.user_id : '', group }));
            if (valid())
                memberWatermark.value = p.dataset_watermark;
        }
        else if (group === 'ROLE_ID') {
            const p = await listRoles(s, after, signal);
            next = p.next_after;
            options = p.roles.filter(x => x.status === 'ACTIVE').map(x => ({ label: x.name, value: x.role_id, group }));
        }
        else if (group === 'MODEL') {
            const p = await listModelConfigurations(s, { limit: 20, ...(after ? { after } : {}) }, signal);
            next = p.next_after;
            options = p.items.filter(x => x.project_id === null || x.project_id === (project.value || null)).map(x => ({ label: x.definition.model_id + ' · ' + x.model_config_revision, value: { version_id: x.model_config_revision, content_sha256: x.content_sha256 }, group }));
        }
        if (valid()) {
            addChoices(group, options, more);
            dependencyCursors.value = { ...dependencyCursors.value, [group]: next };
            notice.value = options.length ? '已读取 ' + options.length + ' 个 ' + group + ' 固定候选；提交时仍核验资格。' : '本页没有 ' + group + ' 候选，请补齐真实依赖。';
        }
    });
}
async function loadTemplates(more: boolean) { const p = projects.value.find(x => x.project_id === templateProject.value); if (!p) {
    error.value = '请从真实项目目录选择模板适用项目';
    return;
} return run(async (s, signal, valid) => { const old = more ? templatePage.value : null; const result = await listApplicableTemplates(s, p, { document_type: documentType.value, profile_revision: p.profile.profile_revision, page: old ? old.page_info.page + 1 : 1, page_size: 20, ...(old ? { dataset_watermark: old.page_info.watermark, filter_sha256: old.page_info.filter_sha256 } : {}) }, old, signal); if (valid()) {
    templatePage.value = result;
    addChoices('TEMPLATE', result.items.map(x => ({ label: x.template.template_version + ' · ' + x.applicability, value: { version_id: x.template.template_version, content_sha256: x.template.content_sha256 }, group: 'TEMPLATE' })), more);
} }); }
function addPermission() { const c = scopeCandidates.value[Number(scopeIndex.value)]; if (scopeIndex.value === '' || !c || !purpose.value.trim()) {
    error.value = '请先选择真实动作并填写用途';
    return;
} const p = { ...draftCopy(c), purpose: purpose.value.trim() }; if (!permissions.value.some(x => draftCanonical(x) === draftCanonical(p)))
    permissions.value.push(p); purpose.value = ''; }
function applyPolicy() { const p = selectedPolicy.value; if (!p || p.status !== 'APPROVED') {
    error.value = '请选择真实已批准策略';
    return;
} const d = p.fixed.definition, m = draftCopy(manifest.value); const distinct = (values: any[]) => values.filter((x, i) => values.findIndex(y => draftCanonical(x) === draftCanonical(y)) === i); m.role_versions = distinct(d.bindings.map(b => b.role_version)); m.model_config_revisions = distinct(d.bindings.flatMap(b => b.model_version ? [b.model_version] : [])); m.retrieval_profiles = d.retrieval_profiles.map(r => r.version); m.release_scope = { ...m.release_scope, tenant_id: reader().current_context!.tenant_id, project_mode: project.value ? 'PROJECTS' : 'ENTERPRISE', project_ids: project.value ? [project.value] : [], source_scope_policy: d.source_selection.version, allowed_classifications: [...d.allowed_classifications] }; manifest.value = m; addChoices('ROLE', m.role_versions.map((v: any) => ({ label: '所选策略固定角色 · ' + v.version_id, value: v, group: 'ROLE' })), true); addChoices('MODEL', m.model_config_revisions.map((v: any) => ({ label: '所选策略固定模型 · ' + v.version_id, value: v, group: 'MODEL' })), true); }
function body(): DraftBody {
    if (kind.value === 'AGENT') {
        const p = policies.value.find(x => x.fixed.version_id === policyId.value && x.status === 'APPROVED');
        return { schema_version: '1.0.0', project_id: project.value || null, name: name.value, execution_policy: p ? { version_id: p.fixed.version_id, content_sha256: p.fixed.definition_sha256 } : {}, manifest: draftCopy(manifest.value), reason: reason.value };
    }
    const a = artifacts.value.find(x => x.artifact_id === artifactId.value), input = capabilitySchemas.find(x => x.schema_ref === inputSchema.value), output = capabilitySchemas.find(x => x.schema_ref === outputSchema.value);
    return { schema_version: '1.0.0', project_id: project.value || null, kind: kind.value, source_artifact: a ? { version_id: a.artifact_id, content_sha256: a.sha256 } : {}, input_schema_ref: input ? { schema_ref: input.schema_ref, schema_sha256: input.schema_sha256 } : {}, output_schema_ref: output ? { schema_ref: output.schema_ref, schema_sha256: output.schema_sha256 } : {}, tool_scope: { schema_version: '1.0.0', permissions: draftCopy(permissions.value) }, execution_mode: 'PRODUCTION', reason: reason.value };
}
async function submit() { if (busy.value || available.value !== true || !attested.value || !mayCreate.value || pending.value)
    return; const request = body(), issue = draftIssue(kind.value, request); if (issue) {
    error.value = issue;
    return;
} return run(async (_s, signal, valid) => { await createDraft(reader, kind.value, request, signal, p => { if (valid())
    pending.value = p; }); if (valid())
    notice.value = '已取得创建回执，请只读核查完整固定内容。'; }, true); }
async function recover() { if (!pending.value || available.value !== true)
    return; const original = draftCopy(pending.value); return run(async (_s, signal, valid) => { const result = await recoverDraft(reader, original, signal); if (valid()) {
    if (result.view) {
        detail.value = result.view;
        pending.value = null;
        notice.value = '原操作、类型回执与完整固定声明已核验。草稿不具备发布资格。';
    }
    else
        notice.value = '原操作状态：' + result.lookup.lookup_status + '。原请求继续保留，不自动重发。';
} }); }
watch(() => [user.medicalSession, user.medicalSession?.current_context?.tenant_id, user.medicalSession?.current_context?.context_revision, user.medicalSession?.current_context?.auth_epoch, user.medicalSession?.current_context?.governance_epoch], () => { clear(); if (user.medicalSession?.current_context && !document.hidden) {
    pending.value = pendingDraft(user.medicalSession);
    void bootstrap();
} }, { immediate: true, flush: 'sync' });
watch([kind, project], () => { controller.abort(); controller = new AbortController(); generation++; loading.value = false; items.value = []; cursor.value = null; listed.value = false; detail.value = null; clearForm(); }, { flush: 'sync' });
watch([templateProject, documentType], () => { templatePage.value = null; versionChoices.value = versionChoices.value.filter(x => x.group !== 'TEMPLATE'); });
watch([kind, project, artifactId, inputSchema, outputSchema, permissions, reason, name, policyId, manifest], () => { attested.value = false; }, { deep: true, flush: 'sync' });
function visibility() { clear(); if (!document.hidden && user.medicalSession?.current_context) {
    pending.value = pendingDraft(user.medicalSession);
    void bootstrap();
} }
document.addEventListener('visibilitychange', visibility);
onBeforeUnmount(() => { mounted = false; controller.abort(); generation++; document.removeEventListener('visibilitychange', visibility); });
</script>
<style scoped>
.draft-page{max-width:1200px;margin:auto;padding:1.5rem;display:grid;gap:1rem}.card{padding:1.3rem;border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base);background:var(--el-fill-color-blank)}.controls,.actions{display:flex;gap:.7rem;flex-wrap:wrap;align-items:end}label{display:grid;gap:.4rem;margin:.7rem 0}select,input,textarea{padding:.55rem;border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base);color:var(--el-text-color-primary);background:var(--el-fill-color-blank);max-width:100%}button{padding:.5rem .8rem;cursor:pointer}button:disabled{cursor:not-allowed;opacity:.55}fieldset{border:0;padding:0;min-width:0}.identifier,pre{overflow-wrap:anywhere;white-space:pre-wrap;font-size:.85rem}.identifier{display:block;color:var(--el-text-color-secondary)}pre{max-height:30rem;overflow:auto}.error{color:var(--el-color-danger)}li{margin:.7rem 0}.attestation{display:flex;align-items:center}p{line-height:1.6}dt{font-weight:600}dd{margin-bottom:.6rem}@media(max-width:680px){.draft-page{padding:.8rem}.controls{display:grid}}
</style>
