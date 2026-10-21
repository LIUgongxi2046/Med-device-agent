<template>
 <main class="medical-page publication-page">
  <nav aria-label="当前位置"><router-link to="/agents">Agent 管理</router-link> / 正式验收与发布</nav>
  <header class="page-heading"><div><h1>正式验收与发布</h1><p>从真实候选、评测与人工资格记录继续，逐步核对固定版本与原操作。</p></div><router-link to="/agents/drafts">候选目录</router-link></header>
  <session-panel />
  <p v-if="error" role="alert" class="message error">{{ error }}</p>
  <p v-if="notice" role="status" class="message">{{ notice }}</p>
  <section class="card" aria-labelledby="availability-title">
   <div class="section-heading"><h2 id="availability-title">开始前核查</h2><button :disabled="busy||!hasContext" @click="bootstrap">核查当前服务</button></div>
   <div class="readiness-grid"><div><span>验收与评分绑定接口</span><strong>{{ featureLabel(availability?.features.acceptance_routes) }}</strong></div><div><span>发布与停用接口</span><strong>{{ featureLabel(availability?.features.publication_routes) }}</strong></div><div><span>专业执行证明服务</span><strong>{{ featureLabel(availability?.features.professional_completion_registered) }}</strong></div></div>
   <p v-if="!hasContext" role="status">请先在上方选择企业，随后核查该企业的可用服务。</p>
   <p v-else-if="deploymentNotice" class="warning" role="status">{{ deploymentNotice }}</p>
   <p>服务已启用不代表具有业务权限，也不代表固定样本、评分或专业执行已完成。每一步仍由服务端核验当前授权及完整证据。</p>
   <router-link to="/agents/context-bindings">查看正式评测上下文绑定</router-link>
  </section>
  <section v-if="pending" class="card recovery" aria-labelledby="recovery-title">
   <h2 id="recovery-title">原请求待核查</h2><p>{{ kindLabel(pending.kind) }} · 原请求已保留；离开页面、隐藏窗口或切换身份不会撤销服务器操作。</p>
   <details><summary>查看原请求核验信息</summary><dl><dt>原请求键</dt><dd class="identifier">{{ pending.key }}</dd><dt>完整请求摘要</dt><dd class="identifier">{{ pending.body_sha256 }}</dd><dt>原授权修订</dt><dd>{{ pending.context.context_revision }}</dd><dt>归属范围</dt><dd>{{ pending.project_id?'项目 '+pending.project_id:'当前企业' }}</dd></dl></details>
   <p>只读核查始终使用原键与完整原声明。未查到、权限变化或仍在处理均不代表未执行；本页不会换键重发。</p>
   <button :disabled="busy||!hasContext" @click="recover">只读核查原操作</button>
  </section>
  <section v-if="outcome" class="card" aria-labelledby="outcome-title"><h2 id="outcome-title">已核验的原操作结果</h2>
   <p>{{ kindLabel(outcomeKind) }}已完成原操作核验。<span v-if="outcomeKind==='DISABLE'">发布已停用；重新启用需要新的发布。</span></p>
   <details><summary>查看固定回执信息</summary><dl><dt>操作</dt><dd>{{ kindLabel(outcomeKind) }}</dd><dt>操作编号</dt><dd class="identifier">{{ resultOperation }}</dd><template v-if="'release_id' in outcome"><dt>发布编号</dt><dd class="identifier">{{ outcome.release_id }}</dd><template v-if="'artifact_id' in outcome&&outcome.artifact_id"><dt>发布清单成果</dt><dd class="identifier">{{ outcome.artifact_id }}</dd></template></template><template v-if="'receipt' in outcome&&'record' in outcome.receipt"><dt>正式评分绑定记录</dt><dd class="identifier">{{ outcome.receipt.record.version_id }}</dd><dt>固定摘要</dt><dd class="identifier">{{ outcome.receipt.record.content_sha256 }}</dd></template><template v-else-if="'resource_id' in outcome"><dt>验收记录</dt><dd class="identifier">{{ outcome.resource_id }}</dd><dt>固定摘要</dt><dd class="identifier">{{ outcome.content_sha256 }}</dd></template></dl></details>
   <p>这里只确认上述操作及固定回执。后续任务运行仍需独立验证发布状态和当前调用权限。</p>
  </section>
  <section class="card" aria-labelledby="candidate-title">
   <h2 id="candidate-title">1. 选择真实候选</h2>
   <fieldset :disabled="busy||!!pending"><legend>候选归属范围</legend><div class="form-row"><label>范围<select v-model="project"><option value="">当前企业</option><option v-for="p in projects" :key="p.project_id" :value="p.project_id">{{ p.name }}</option></select></label><button type="button" @click="loadProjects(false)">读取项目</button><button v-if="projectCursor" type="button" @click="loadProjects(true)">更多项目</button><button type="button" @click="loadCandidates(false)">读取候选目录</button></div>
    <p>每次只读取一页真实记录。空页或当前可见页不代表全部候选或全部授权。</p>
    <ul v-if="candidates.length" class="record-list"><li v-for="item in candidates" :key="item.record_id"><div><strong>{{ item.name||'未命名候选' }}</strong><span>{{ item.status }} · {{ item.created_at }}</span><details><summary>固定版本</summary><small class="identifier">{{ item.version_id }}</small></details></div><button type="button" @click="chooseCandidate(item)">读取并选择</button></li></ul><p v-else-if="candidatesRead">本页没有可读候选。</p><button v-if="candidateCursor" type="button" @click="loadCandidates(true)">下一页候选</button>
   </fieldset>
   <div v-if="candidate" class="selected"><h3>{{ candidate.name }}</h3><p>{{ candidate.status }} · 修订 {{ candidate.revision }}</p><details><summary>固定版本核验信息</summary><dl><dt>当前状态</dt><dd>{{ candidate.status }} · 修订 {{ candidate.revision }}</dd><dt>固定版本</dt><dd class="identifier">{{ candidate.basis.version_id }}</dd><dt>固定摘要</dt><dd class="identifier">{{ candidate.basis.content_sha256 }}</dd><dt>执行策略</dt><dd class="identifier">{{ candidate.execution_policy.version_id }}</dd></dl></details><p v-if="candidate.created_by===user.medicalSession?.user.user_id" class="warning">当前用户是此候选的作者，不能替该候选作独立验收或发布。</p><button :disabled="busy||!!pending" @click="refreshCandidate">刷新候选状态与修订</button></div>
  </section>
  <section class="card" aria-labelledby="evidence-title">
   <h2 id="evidence-title">2. 核对固定评测与统计政策</h2>
   <p>使用已有的评测、统计政策或资格记录编号读取，再核对其适用范围与固定内容。</p>
   <fieldset :disabled="busy||!!pending"><legend>读取已有记录</legend><div class="form-grid"><label>评测批次编号<input v-model="evaluationId" autocomplete="off" spellcheck="false" placeholder="真实评测 UUID" /></label><button @click="loadEvaluation" type="button">读取固定评测</button><label>统计政策决策编号<input v-model="policyId" autocomplete="off" spellcheck="false" placeholder="真实已批准政策 UUID" /></label><button @click="loadPolicy" type="button">读取统计政策</button></div></fieldset>
   <div class="summary-grid"><article v-if="evaluation" class="selected"><h3>评测批次</h3><dl><dt>环境</dt><dd>{{ evaluation.definition.request.environment==='PRODUCTION'?'正式环境声明':'模拟环境，不可用于正式发布' }}</dd><dt>状态</dt><dd>{{ evaluation.status }}</dd><dt>评分历史</dt><dd>{{ evaluation.score_history.length }} 条</dd></dl><details><summary>固定版本核验信息</summary><dl><dt>固定版本</dt><dd class="identifier">{{ evaluation.evaluation_id }}</dd><dt>固定摘要</dt><dd class="identifier">{{ evaluation.content_sha256 }}</dd></dl></details><p>公开评测结果不构成专业执行完成证明。</p></article><article v-if="policy" class="selected"><h3>统计政策决策</h3><dl><dt>决策</dt><dd>{{ policy.content.status==='APPROVE'?'已批准':'已拒绝' }}</dd><dt>覆盖维度</dt><dd>{{ policy.content.policy.dimensions.map(d=>d.dimension).join('、') }}</dd><dt>抽样说明</dt><dd>{{ policy.content.sampling_assessment }}</dd></dl><details><summary>固定版本核验信息</summary><dl><dt>固定版本</dt><dd class="identifier">{{ policy.record_id }}</dd><dt>固定摘要</dt><dd class="identifier">{{ policy.content_sha256 }}</dd></dl></details></article></div>
   <p v-if="candidate&&evidenceIssue" class="hint">{{ evidenceIssue }}</p>
  </section>
  <section class="card" aria-labelledby="score-title"><h2 id="score-title">3. 绑定原评分与专家资格</h2><p>只绑定当前用户已有的真实评分。不会新增评分、专业执照或授权。</p>
   <fieldset :disabled="busy||!!pending"><legend>原评分与有效资格</legend><label>专家资格编号<input v-model="qualificationId" autocomplete="off" spellcheck="false" placeholder="真实资格 UUID" /></label><button type="button" @click="loadQualification">读取资格记录</button>
    <dl v-if="qualification"><dt>资格状态</dt><dd>{{ qualification.status }}</dd><dt>专业领域</dt><dd>{{ qualification.definition.request.domain }}</dd><dt>覆盖维度</dt><dd>{{ qualification.definition.request.dimensions.join('、') }}</dd><dt>有效期</dt><dd>{{ qualification.definition.request.valid_from }} 至 {{ qualification.definition.request.valid_to }}</dd><dt>用途</dt><dd>{{ qualification.definition.request.purpose }}</dd></dl>
    <label>当前有效原评分<select v-model="scoreId"><option value="">请选择已读取的原评分</option><option v-for="score in scoreOptions" :key="score.score_id" :value="score.score_id">{{ score.observation.cell.dimension }} · 重复 {{ score.observation.cell.repeat_index+1 }} · {{ score.observation.status }} · {{ score.score_id }}</option></select></label><p v-if="evaluation&&!scoreOptions.length">本次已读取历史中，没有属于当前用户及所选候选的有效评分。</p>
    <label>绑定理由<textarea v-model="scoreReason" rows="3" maxlength="4096" /></label><label class="confirm"><input type="checkbox" v-model="scoreConfirmed" />我已核对原评分、资格领域及适用范围。</label>
   </fieldset><p v-if="showSectionIssue('SCORE_BIND',scoreBlocker)" class="hint">{{ scoreBlocker }}</p><button class="primary" :disabled="busy||!!pending||!scoreConfirmed||!!scoreBlocker" @click="bindScore">提交正式评分绑定</button>
  </section>
  <section class="card" aria-labelledby="acceptance-title"><h2 id="acceptance-title">4. 独立验收决定</h2><p>验收会更新候选修订。拒绝会保留拒绝记录；通过仍须真实专业执行、完整评分与有效统计政策。</p>
   <fieldset :disabled="busy||!!pending"><legend>验收材料与决定</legend><label>决定<select v-model="decision"><option value="REJECT">拒绝验收</option><option value="ACCEPT">通过验收</option></select></label>
    <div v-if="decision==='ACCEPT'"><h3>正式评分绑定引用</h3><p>没有跨专家评分绑定目录。以下引用须来自真实正式绑定回执；输入引用本身不证明资格或正式执行，服务端会逐条核验。</p><div class="form-grid"><label>绑定记录编号<input v-model="bindingId" spellcheck="false" /></label><label>固定 SHA-256 摘要<input v-model="bindingHash" spellcheck="false" /></label></div><button type="button" @click="addBinding">加入真实回执引用</button><ul class="reference-list"><li v-for="(binding,index) in scoreBindings" :key="binding.version_id"><span class="identifier">{{ binding.version_id }}</span><span class="identifier">{{ binding.content_sha256 }}</span><button type="button" @click="scoreBindings.splice(index,1)">移除</button></li></ul></div>
    <label>验收理由<textarea v-model="acceptanceReason" rows="3" maxlength="2000" /></label><label class="confirm"><input type="checkbox" v-model="acceptanceConfirmed" />我已核对候选、固定评测与政策，并确认本次验收决定。</label>
   </fieldset><p v-if="showSectionIssue('ACCEPTANCE',acceptanceBlocker)" class="hint">{{ acceptanceBlocker }}</p><button class="primary" :disabled="busy||!!pending||!acceptanceConfirmed||!!acceptanceBlocker" @click="decide">{{ decision==='ACCEPT'?'提交通过验收':'提交拒绝验收' }}</button>
  </section>
  <section class="card" aria-labelledby="publish-title"><h2 id="publish-title">5. 核对验收并发布</h2><fieldset :disabled="busy||!!pending"><legend>固定验收记录</legend><label>验收记录编号<input v-model="acceptanceId" autocomplete="off" spellcheck="false" /></label><button type="button" @click="loadAcceptance">读取验收记录</button>
    <div v-if="acceptance"><p>{{ acceptance.decision==='ACCEPT'?'通过验收':'拒绝验收' }} · {{ acceptance.accepted_at }}</p><details><summary>验收固定信息</summary><dl><dt>决定</dt><dd>{{ acceptance.decision==='ACCEPT'?'通过验收':'拒绝验收' }}</dd><dt>决定时间</dt><dd>{{ acceptance.accepted_at }}</dd><dt>候选固定版本</dt><dd class="identifier">{{ acceptance.candidate.version_id }}</dd><dt>验收固定摘要</dt><dd class="identifier">{{ acceptanceHash }}</dd><dt v-if="acceptance.manifest_sha256">发布清单摘要</dt><dd v-if="acceptance.manifest_sha256" class="identifier">{{ acceptance.manifest_sha256 }}</dd></dl></details></div>
    <label>发布理由<textarea v-model="publishReason" rows="3" maxlength="2000" /></label><label class="confirm"><input type="checkbox" v-model="publishConfirmed" />我已核对完整验收依据，确认对所选候选执行发布。</label>
   </fieldset><p v-if="showSectionIssue('PUBLISH',publishBlocker)" class="hint">{{ publishBlocker }}</p><button class="primary" :disabled="busy||!!pending||!publishConfirmed||!!publishBlocker" @click="publish">提交正式发布</button>
  </section>
  <section class="card" aria-labelledby="disable-title"><h2 id="disable-title">停用现有发布</h2><p>仅用于已发布候选。停用是单向操作，之后不能恢复原发布；重新启用需要新的发布流程。</p>
   <fieldset :disabled="busy||!!pending"><legend>已核对的发布</legend><p v-if="acceptance?.manifest?.release_id">发布来自上方已读取的通过验收记录。</p><p v-else>请先选择已发布候选，并读取该候选的真实通过验收记录。</p><label>停用理由<textarea v-model="disableReason" rows="3" maxlength="2000" /></label><label class="confirm"><input type="checkbox" v-model="disableConfirmed" />我确认停用此发布，并知晓不能恢复原发布。</label></fieldset>
   <p v-if="showSectionIssue('DISABLE',disableBlocker)" class="hint">{{ disableBlocker }}</p><button :disabled="busy||!!pending||!disableConfirmed||!!disableBlocker" @click="disable">确认停用发布</button>
  </section>
 </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import useUserStore from '@/store/modules/user';
import SessionPanel from './session-panel.vue';
import { publicationAvailability, publicationAllowed, publicationBlocker, publicationIssue, readPendingPublication, readPublicationCandidate, readStatisticsPolicy, readPublicationQualification, readAcceptance, submitPublication, recoverPublication, canonical, detached } from '@/api/medical/formal-publication';
import type { PublicationAvailability, CandidateSelection, StatisticsPolicyView, QualificationView, AcceptanceRecord, PublicationKind, PublicationReceipt, PublicationRequest, PendingPublication, ScoreReceipt } from '@/api/medical/formal-publication';
import { queryDrafts } from '@/api/medical/formal-agent-drafts';
import type { DraftSummary } from '@/api/medical/formal-agent-drafts';
import { capabilityProjectCandidates } from '@/api/medical/formal-capability-candidates';
import { readEvaluation } from '@/api/medical/evaluations';
import { effectiveEvaluationScores } from '@/api/medical/evaluation-domain';
import type { EvaluationRead } from '@/api/medical/evaluation-domain';
import { sourceDigest } from '@/api/medical/content-digest';
import type { Basis, MedicalSession, Project } from '@/api/medical/protocol';
const user = useUserStore(), loading = ref(false), error = ref(''), notice = ref(''), availability = ref<PublicationAvailability | null>(null);
const pending = ref<PendingPublication | null>(null), outcome = ref<PublicationReceipt | null>(null), outcomeKind = ref<PublicationKind>('ACCEPTANCE');
const projects = ref<Project[]>([]), projectCursor = ref<string | null>(null), project = ref(''), candidates = ref<DraftSummary[]>([]), candidateCursor = ref<string | null>(null), candidatesRead = ref(false), candidate = ref<CandidateSelection | null>(null);
const evaluationId = ref(''), policyId = ref(''), qualificationId = ref(''), evaluation = ref<EvaluationRead | null>(null), policy = ref<StatisticsPolicyView | null>(null), qualification = ref<QualificationView | null>(null);
const scoreId = ref(''), scoreReason = ref(''), scoreConfirmed = ref(false), decision = ref<'ACCEPT' | 'REJECT'>('REJECT'), scoreBindings = ref<Basis[]>([]), bindingId = ref(''), bindingHash = ref(''), acceptanceReason = ref(''), acceptanceConfirmed = ref(false);
const disableReason = ref(''), disableConfirmed = ref(false);
const acceptanceId = ref(''), acceptance = ref<AcceptanceRecord | null>(null), acceptanceHash = ref(''), publishReason = ref(''), publishConfirmed = ref(false);
let controller = new AbortController(), generation = 0, mounted = true, writeOwner: object | null = null;
const busy = computed(() => loading.value || user.medicalWriting), hasContext = computed(() => !!user.medicalSession?.current_context);
const deploymentNotice = computed(() => { const a = availability.value; if (!a)
    return ''; const messages: string[] = []; if (!a.features.acceptance_routes || !a.features.publication_routes)
    messages.push('当前部署尚未启用全部验收或发布接口，相关操作暂不可用。'); if (!a.features.professional_completion_registered)
    messages.push('尚未接入真实专业执行完成证明。正式评分绑定、通过验收及发布相关操作暂不可进行。连接测试、模拟结果或技术检查不能代替这一前置条件。'); return messages.join(' '); });
const resultOperation = computed(() => outcome.value ? 'receipt' in outcome.value ? outcome.value.receipt.operation_id : outcome.value.operation_id : '');
const scoreOptions = computed(() => evaluation.value && candidate.value ? [...effectiveEvaluationScores(evaluation.value.definition, evaluation.value.content_sha256, evaluation.value.score_history).values()].filter(s => s.scorer_id === user.medicalSession?.user.user_id && s.observation.cell.agent_version_id === candidate.value!.basis.version_id) : []);
const evidenceIssue = computed(() => {
    if (!candidate.value)
        return '请先读取并选择真实候选';
    if (!evaluation.value || !policy.value)
        return '请读取固定评测与真实统计政策决策';
    const e = evaluation.value, p = policy.value.content.policy, c = candidate.value;
    if (e.definition.request.environment !== 'PRODUCTION')
        return '模拟评测不能用于正式验收或发布';
    if (e.definition.request.project_id !== c.project_id || p.project_id !== c.project_id)
        return '候选、评测与政策归属范围不一致';
    if (!e.definition.request.candidates.some(b => canonical(b) === canonical(c.basis)) || !p.candidate_refs.some(b => canonical(b) === canonical(c.basis)))
        return '评测或统计政策未绑定所选候选的完整固定版本';
    if (policy.value.content.status !== 'APPROVE')
        return '统计政策尚未批准';
    if (canonical(p.dataset) !== canonical(e.definition.request.dataset) || canonical(p.sample_manifest_artifact) !== canonical(e.definition.request.sample_manifest_artifact) || canonical(p.rule_revision) !== canonical(e.definition.request.rule_revision))
        return '评测与统计政策的固定样本或规则不一致';
    return '';
});
function permissionIssue(kind: PublicationKind) { return publicationAllowed(user.medicalSession, project.value || null, kind, evaluation.value?.evaluation_id) ? '' : '当前精确范围没有此操作权限'; }
const scoreBlocker = computed(() => {
    const common = publicationBlocker(availability.value, 'SCORE_BIND') || evidenceIssue.value || permissionIssue('SCORE_BIND');
    if (common)
        return common;
    const q = qualification.value, s = scoreOptions.value.find(s => s.score_id === scoreId.value);
    if (!q || q.status !== 'APPROVED' || q.definition.request.expert_id !== user.medicalSession?.user.user_id)
        return '请选择属于当前评分人的已批准资格';
    if (q.definition.request.project_id !== null && q.definition.request.project_id !== (project.value || null))
        return '资格不适用于当前项目';
    if (!s || !q.definition.request.dimensions.includes(s.observation.cell.dimension))
        return '请选择资格覆盖的当前有效原评分';
    if (!s.observation.output_ref)
        return '原评分尚未引用真实输出';
    return '';
});
const acceptanceBlocker = computed(() => publicationBlocker(availability.value, 'ACCEPTANCE', decision.value) || evidenceIssue.value || permissionIssue('ACCEPTANCE') || (candidate.value?.created_by === user.medicalSession?.user.user_id ? '候选作者不能进行独立验收' : '') || (candidate.value && !['DRAFT', 'VALIDATING', 'EVALUATING', 'REVIEW_PENDING'].includes(candidate.value.status) ? '当前候选状态不允许验收' : '') || (decision.value === 'ACCEPT' && !scoreBindings.value.length ? '通过验收需要真实正式评分绑定引用' : ''));
const publishBlocker = computed(() => {
    const common = publicationBlocker(availability.value, 'PUBLISH') || permissionIssue('PUBLISH');
    if (common)
        return common;
    const c = candidate.value, a = acceptance.value;
    if (!c || !a)
        return '请选择候选并读取真实通过验收记录';
    if (c.created_by === user.medicalSession?.user.user_id)
        return '候选作者不能发布此候选';
    if (c.status !== 'REVIEW_PENDING')
        return '请刷新候选，只有待发布验收状态可继续';
    if (a.decision !== 'ACCEPT' || a.project_id !== c.project_id || canonical(a.candidate) !== canonical(c.basis) || canonical(a.execution_policy) !== canonical(c.execution_policy))
        return '验收未通过或未绑定当前候选及执行策略';
    return '';
});
const disableBlocker = computed(() => {
    const common = publicationBlocker(availability.value, 'DISABLE') || permissionIssue('DISABLE');
    if (common)
        return common;
    const c = candidate.value, a = acceptance.value;
    if (!c || !a)
        return '请读取已发布候选及通过验收记录';
    if (c.status !== 'PUBLISHED')
        return '请刷新候选状态，仅已发布候选可停用';
    if (a.decision !== 'ACCEPT' || !a.manifest?.release_id || a.project_id !== c.project_id || canonical(a.candidate) !== canonical(c.basis))
        return '验收未绑定所选候选的真实发布';
    return '';
});
function showSectionIssue(kind: PublicationKind, issue: string) { return !!issue && hasContext.value && !!candidate.value && !publicationBlocker(availability.value, kind, kind === 'ACCEPTANCE' ? decision.value : undefined) && issue !== evidenceIssue.value; }
function reader(): MedicalSession { if (!user.medicalSession?.current_context)
    throw Error('请先恢复身份并选择企业'); return user.medicalSession; }
function live(g: number, s: MedicalSession) { return mounted && generation === g && user.medicalSession === s && !document.hidden; }
async function run(action: (s: MedicalSession, signal: AbortSignal, valid: () => boolean) => Promise<void>, write = false) { if (busy.value || document.hidden)
    return; if (!hasContext.value) {
    error.value = '请先恢复身份并选择企业';
    return;
} const s = reader(), g = generation, signal = controller.signal, owner = {}; loading.value = true; error.value = ''; notice.value = ''; if (write) {
    writeOwner = owner;
    user.medicalWriting = true;
} try {
    await action(s, signal, () => live(g, s));
}
catch (e) {
    if (live(g, s) && !signal.aborted) {
        const reason = e instanceof Error && e.message ? e.message : '请求未完成';
        error.value = write && !reason.includes('核查原操作')
            ? `${reason}。请先只读核查原操作，确认结果后再决定是否重试。`
            : reason;
    }
}
finally {
    if (live(g, s))
        loading.value = false;
    if (writeOwner === owner) {
        writeOwner = null;
        user.medicalWriting = false;
        if (mounted && generation !== g && hasContext.value && !document.hidden)
            void bootstrap();
    }
} }
function resetSelections() { disableReason.value = ''; disableConfirmed.value = false; candidate.value = null; candidates.value = []; candidateCursor.value = null; candidatesRead.value = false; evaluation.value = null; policy.value = null; qualification.value = null; evaluationId.value = ''; policyId.value = ''; qualificationId.value = ''; scoreId.value = ''; scoreReason.value = ''; scoreConfirmed.value = false; scoreBindings.value = []; bindingId.value = ''; bindingHash.value = ''; acceptanceReason.value = ''; acceptanceConfirmed.value = false; decision.value = 'REJECT'; acceptanceId.value = ''; acceptance.value = null; acceptanceHash.value = ''; publishReason.value = ''; publishConfirmed.value = false; }
function clear() { controller.abort(); controller = new AbortController(); generation++; loading.value = false; error.value = ''; notice.value = ''; availability.value = null; pending.value = null; outcome.value = null; projects.value = []; projectCursor.value = null; project.value = ''; resetSelections(); }
async function bootstrap() { return run(async (s, signal, valid) => { const original = readPendingPublication(s); if (valid())
    pending.value = original; const result = await publicationAvailability(reader, signal); if (valid())
    availability.value = result; }); }
async function loadProjects(more: boolean) { return run(async (_s, signal, valid) => { const result = await capabilityProjectCandidates(reader, more ? projectCursor.value : null, signal); if (valid()) {
    projects.value = more ? [...projects.value, ...result.projects] : result.projects;
    projectCursor.value = result.next_after;
} }); }
async function loadCandidates(more: boolean) { return run(async (_s, signal, valid) => { const page = await queryDrafts(reader, 'AGENT', project.value || null, more ? candidateCursor.value : null, signal); if (valid()) {
    candidates.value = page.items;
    candidateCursor.value = page.next_cursor;
    candidatesRead.value = true;
} }); }
async function chooseCandidate(item: DraftSummary) { return run(async (_s, signal, valid) => { const result = await readPublicationCandidate(reader, item.record_id, signal); if (result.basis.version_id !== item.version_id || result.basis.content_sha256 !== item.content_sha256 || result.project_id !== (project.value || null))
    throw Error('候选目录与固定详情不一致，请重新读取'); if (valid()) {
    candidate.value = result;
    scoreBindings.value = [];
    acceptance.value = null;
    acceptanceHash.value = '';
} }); }
async function refreshCandidate() { const selected = candidate.value; if (!selected)
    return; return run(async (_s, signal, valid) => { const result = await readPublicationCandidate(reader, selected.record_id, signal); if (result.project_id !== (project.value || null) || canonical(result.basis) !== canonical(selected.basis))
    throw Error('候选固定身份已变化'); if (valid())
    candidate.value = result; }); }
function recordInput(id: string): boolean { if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) { error.value = '请填写已有记录的完整 UUID 编号'; return false; } return true; }
async function loadEvaluation() { const id = evaluationId.value; if (!recordInput(id)) return; return run(async (s, signal, valid) => { const result = await readEvaluation(s, id, signal); if (valid() && evaluationId.value === id)
    evaluation.value = result; }); }
async function loadPolicy() { const id = policyId.value; if (!recordInput(id)) return; return run(async (_s, signal, valid) => { const result = await readStatisticsPolicy(reader, id, signal); if (valid() && policyId.value === id)
    policy.value = result; }); }
async function loadQualification() { const id = qualificationId.value; if (!recordInput(id)) return; return run(async (_s, signal, valid) => { const result = await readPublicationQualification(reader, id, signal); if (valid() && qualificationId.value === id)
    qualification.value = result; }); }
async function loadAcceptance() { const id = acceptanceId.value; if (!recordInput(id)) return; return run(async (_s, signal, valid) => { const result = await readAcceptance(reader, id, signal), hash = await sourceDigest(result); if (valid() && acceptanceId.value === id) {
    acceptance.value = result;
    acceptanceHash.value = hash;
} }); }
function addBinding() { if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(bindingId.value) || !/^[0-9a-f]{64}$/.test(bindingHash.value)) {
    error.value = '请原样填写真实回执中的 UUID 与 SHA-256 摘要';
    return;
} const b = { version_id: bindingId.value, content_sha256: bindingHash.value }; if (scoreBindings.value.some(x => x.version_id === b.version_id && x.content_sha256 !== b.content_sha256)) {
    error.value = '同一绑定记录不能具有不同摘要';
    return;
} if (!scoreBindings.value.some(x => canonical(x) === canonical(b)))
    scoreBindings.value.push(b); bindingId.value = ''; bindingHash.value = ''; }
async function write(kind: PublicationKind, body: PublicationRequest) { if (busy.value || pending.value)
    return; const issue = publicationIssue(kind, body); if (issue) {
    error.value = issue;
    return;
} return run(async (_s, signal, valid) => { await submitPublication(reader, kind, body, project.value || null, signal, p => { if (valid())
    pending.value = p; }); if (valid())
    notice.value = '已取得操作回执。请只读核查原操作，确认固定结果。'; }, true); }
async function bindScore() { if (!scoreConfirmed.value || scoreBlocker.value)
    return; const e = evaluation.value!, p = policy.value!, q = qualification.value!, score = scoreOptions.value.find(s => s.score_id === scoreId.value)!; const snapshot = detached(score), g = generation, s = reader(), hash = await sourceDigest(snapshot); if (!live(g, s) || !scoreConfirmed.value || scoreBlocker.value)
    return; return write('SCORE_BIND', { schema_version: '1.0.0', evaluation: { version_id: e.evaluation_id, content_sha256: e.content_sha256 }, statistics_policy: { version_id: p.record_id, content_sha256: p.content_sha256 }, binding: { schema_version: '1.0.0', evaluation_id: e.evaluation_id, score_id: snapshot.score_id, definition_sha256: e.content_sha256, score_sha256: hash, qualification: detached(q.qualification), domain: q.definition.request.domain, reason: scoreReason.value } }); }
async function decide() { if (!acceptanceConfirmed.value || acceptanceBlocker.value)
    return; const c = candidate.value!, e = evaluation.value!, p = policy.value!; return write('ACCEPTANCE', { schema_version: '1.0.0', candidate: detached(c.basis), evaluation: { version_id: e.evaluation_id, content_sha256: e.content_sha256 }, statistics_policy: { version_id: p.record_id, content_sha256: p.content_sha256 }, decision: decision.value, reason: acceptanceReason.value, score_bindings: decision.value === 'ACCEPT' ? detached(scoreBindings.value) : [], expected_revision: c.revision }); }
async function publish() { if (!publishConfirmed.value || publishBlocker.value)
    return; const c = candidate.value!, a = acceptance.value!; return write('PUBLISH', { schema_version: '1.0.0', candidate: detached(c.basis), expected_revision: c.revision, acceptance: { version_id: a.acceptance_id, content_sha256: acceptanceHash.value }, reason: publishReason.value }); }
async function disable() { if (!disableConfirmed.value || disableBlocker.value)
    return; return write('DISABLE', { schema_version: '1.0.0', release_id: acceptance.value!.manifest!.release_id, expected_revision: candidate.value!.revision, reason: disableReason.value }); }
async function recover() { const original = pending.value; if (!original)
    return; return run(async (_s, signal, valid) => { const result = await recoverPublication(reader, original, signal); if (!valid())
    return; if (!result.complete) {
    pending.value = readPendingPublication(reader()) || original;
    notice.value = result.lookup?.lookup_status === 'NOT_FOUND' ? '原键尚未查到。保留原请求继续核查，不能据此重发。' : '原操作尚未确认完成。保留原键与完整声明继续核查。';
    return;
} pending.value = null; outcome.value = result.receipt; outcomeKind.value = original.kind; notice.value = '已核验原键、完整请求与固定回执。'; if (result.acceptance) {
    acceptanceId.value = result.acceptance.acceptance_id;
    acceptance.value = result.acceptance;
    acceptanceHash.value = (result.receipt as {
        content_sha256: string;
    }).content_sha256;
} if (original.kind === 'SCORE_BIND' && result.receipt) {
    const basis = (result.receipt as ScoreReceipt).receipt.record;
    if (!scoreBindings.value.some(x => canonical(x) === canonical(basis)))
        scoreBindings.value.push(detached(basis));
} if (original.kind !== 'SCORE_BIND')
    candidate.value = null; scoreConfirmed.value = false; acceptanceConfirmed.value = false; publishConfirmed.value = false; disableConfirmed.value = false; }); }
function featureLabel(value: boolean | undefined) { return value === undefined ? '待核查' : value ? '已接入' : '未接入'; }
function kindLabel(kind: PublicationKind) { return { SCORE_BIND: '正式评分绑定', ACCEPTANCE: '独立验收', PUBLISH: '正式发布', DISABLE: '停用发布' }[kind]; }
watch([evaluationId, policyId, qualificationId, acceptanceId], ([e, p, q, a], [oldE, oldP, oldQ, oldA]) => { if (e !== oldE)
    evaluation.value = null; if (p !== oldP)
    policy.value = null; if (q !== oldQ)
    qualification.value = null; if (a !== oldA) {
    acceptance.value = null;
    acceptanceHash.value = '';
} }, { flush: 'sync' });
watch([candidate, evaluation, policy, qualification, scoreId, scoreReason, decision, scoreBindings, acceptanceReason, acceptance, acceptanceHash, publishReason, disableReason], () => { scoreConfirmed.value = false; acceptanceConfirmed.value = false; publishConfirmed.value = false; disableConfirmed.value = false; }, { deep: true, flush: 'sync' });
watch(project, () => { controller.abort(); controller = new AbortController(); generation++; loading.value = false; resetSelections(); outcome.value = null; }, { flush: 'sync' });
watch(() => user.medicalSession, () => { clear(); if (hasContext.value && !document.hidden)
    void bootstrap(); }, { deep: true, flush: 'sync', immediate: true });
function visibility() { if (document.hidden)
    clear();
else if (hasContext.value)
    void bootstrap(); }
document.addEventListener('visibilitychange', visibility);
onBeforeUnmount(() => { mounted = false; clear(); document.removeEventListener('visibilitychange', visibility); });
</script>
<style scoped>
.publication-page{padding:28px;background:#f4f6fa;color:#243247;min-height:100%}nav{font-size:12px;margin-bottom:18px}.page-heading,.section-heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.page-heading{margin-bottom:24px}h1{font-size:24px;margin:0 0 10px}h2{font-size:18px;margin:0 0 16px}h3{font-size:15px;margin-top:0}p{line-height:1.65;color:#526279}.card{padding:24px;background:white;border:1px solid #dce3ec;border-radius:8px;margin-top:20px}.readiness-grid,.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.readiness-grid>div,.selected{background:#f8fafc;border:1px solid #e2e8f0;padding:16px;border-radius:6px}.readiness-grid span,.readiness-grid strong{display:block}.readiness-grid strong{margin-top:10px}.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr));margin-top:16px}.selected{margin-top:16px}.warning{padding:12px 14px;border-left:3px solid #bf8614;background:#fff8e7;color:#76520b}.message{padding:14px;background:#edf4ff;border-radius:6px}.error{background:#fff0f0;color:#b42318}.recovery{border-color:#c49431}fieldset{border:0;padding:0;margin:0;min-width:0}legend{font-weight:600;margin-bottom:14px}.form-row{display:flex;gap:12px;align-items:end;flex-wrap:wrap}.form-grid{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:end;gap:12px}label{display:flex;flex-direction:column;gap:8px;margin:12px 0;font-size:14px}input,select,textarea{min-width:0;padding:10px 12px;border:1px solid #bac6d5;border-radius:5px;background:#fff;color:#243247;font:inherit}textarea{resize:vertical}label.confirm{flex-direction:row;align-items:flex-start;line-height:1.6}.confirm input{margin-top:4px}button{border:1px solid #aebed1;border-radius:5px;padding:9px 14px;background:white;color:#175fae;cursor:pointer;font:inherit;font-size:14px}button.primary{background:#1768c4;color:white;border-color:#1768c4}button:disabled{opacity:.5;cursor:not-allowed}button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,a:focus-visible{outline:2px solid #1768c4;outline-offset:3px}a{color:#175fae}details{margin:12px 0}summary{cursor:pointer;color:#175fae;font-size:13px}.hint{font-size:13px}dl{display:grid;grid-template-columns:130px minmax(0,1fr);gap:10px;line-height:1.5;font-size:14px}dt{color:#526279}dd{margin:0;overflow-wrap:anywhere}.identifier{font-family:ui-monospace,monospace;overflow-wrap:anywhere}.record-list,.reference-list{list-style:none;padding:0}.record-list li{display:flex;justify-content:space-between;gap:16px;padding:16px 0;border-bottom:1px solid #e2e8f0}.record-list span,.record-list small{display:block;margin-top:7px;color:#526279}.reference-list li{display:grid;grid-template-columns:1fr;gap:8px;padding:12px;border-bottom:1px solid #e2e8f0}.reference-list button{justify-self:start}@media(max-width:850px){.readiness-grid,.summary-grid{grid-template-columns:1fr}}@media(max-width:600px){.publication-page{padding:16px}.card{padding:16px}.page-heading,.section-heading{align-items:flex-start;flex-direction:column}.form-grid{grid-template-columns:1fr}dl{grid-template-columns:1fr;gap:6px}dd{margin-bottom:10px}.record-list li{flex-direction:column}.record-list button{align-self:start}}
</style>
