<template>
 <main class="medical-page runtime-page">
  <nav class="breadcrumb"><router-link to="/agents">Agent 管理</router-link><span> / 模型连接与资格</span></nav>
  <header class="page-heading"><div><h1>模型连接与启用资格</h1><p>选择输入，验证连接，然后申请启用。</p></div><router-link to="/model-configurations">管理模型草稿</router-link></header>
  <session-panel/>
  <el-alert v-if="error" type="error" :closable="false" :title="error" role="alert"/>
  <div class="actions"><el-button :disabled="busy" @click="refresh">刷新状态</el-button></div>
  <p v-if="loading" role="status">正在加载…</p>
  <section class="card"><h2>服务状态</h2><p v-if="availability" role="status">{{ availability.model_runtime_enabled ? '模型运行服务已启用' : '当前部署未启用模型运行服务' }}</p><p v-else>尚未读取服务状态。</p><p>支持 DEEPSEEK 项目配置。访问凭据由部署管理员安全配置。</p><details><summary>技术详情：支持范围</summary><p>当前推理档位 reasoning_effort = none，用途 MODEL_VERIFICATION。服务可用不代表已获授权或整条 Agent 执行链可运行。页面不收集密钥或创建权限。</p></details></section>
  <section v-for="p in pendingItems" :key="p.kind" class="card spaced" aria-label="原模型请求恢复">
   <h2>{{ p.kind === 'VERIFY' ? '连接验证' : '启用申请' }}</h2>
   <p>结果未确认时只查询已有请求，不换键、不重发。费用以结算回执为准。</p>
   <p role="status">{{ statusText(p) }}</p><details><summary>技术详情：请求与固定输入</summary><p class="identifier">请求键：{{ p.key }} · 上下文：{{ p.context.context_revision }}</p><p v-if="p.result && 'invocation_id' in p.result" class="identifier">调用：{{ p.result.invocation_id || '尚无已绑定调用编号' }}</p><p v-if="p.result?.status === 'PASSED'" class="identifier">回执摘要：{{ p.result.receipt_sha256 }}</p><pre class="original-body">{{ JSON.stringify(p.body, null, 2) }}</pre></details>
   
   
   <el-button :disabled="busy || !availability?.model_runtime_enabled" @click="recover(p)">查询进度</el-button>
   <el-button v-if="p.kind === 'VERIFY' && p.result?.status === 'RUNNING'" :disabled="busy || !availability?.model_runtime_enabled || !!p.completion_requested || !sameContext(p)" @click="complete(p)">核验结算结果</el-button>
   <p v-if="p.completion_requested">已提交核验，请查询进度；不要重复提交。</p>
   <p v-if="p.result && 'invocation_id' in p.result && p.result.invocation_id">发送后取消不保证供应商终止，也不保证退款或释放未知费用预留。请查看取消回执。</p>
  </section>
  <section v-if="cancellation || cancellable" class="card spaced" aria-label="原调用停止与恢复">
   <h2>取消验证</h2>
   <template v-if="cancellation">
    <details><summary>技术详情：版本与请求标识</summary><p class="identifier">原取消键：{{ cancellation.key }} · 调用：{{ cancellation.body.invocation_id }}</p></details>
    <p>取消结果未确认时，只查询这次请求，不换键重发。</p>
    <p role="status">{{ cancellationStatus }}</p>
    <p v-if="cancellation.receipt">{{ cancellation.receipt.before_dispatch ? '发送前已取消，预留已释放。' : '停止意图已记录；发送后的费用预留尚未释放。' }} 供应商终止未获确认。</p>
    <details><summary>技术详情：取消请求</summary><pre class="original-body">{{ JSON.stringify(cancellation.body, null, 2) }}</pre></details>
    <el-button :disabled="busy || !availability?.model_runtime_enabled" @click="recoverCancel">查询取消结果</el-button>
   </template>
   <el-form v-else-if="cancellable" label-position="top" :disabled="busy" @submit.prevent="cancel">
    <details><summary>技术详情：版本与请求标识</summary><p class="identifier">实际绑定调用：{{ cancellable.result && 'invocation_id' in cancellable.result ? cancellable.result.invocation_id : '' }}</p></details>
    <el-form-item label="取消原因" for="runtime-cancel-reason"><el-input id="runtime-cancel-reason" v-model="cancelReason" type="textarea" maxlength="2000"/></el-form-item>
    <el-checkbox v-model="cancelAttested">确认取消此次验证。发送后取消不保证终止或退款，不会释放未知费用预留。</el-checkbox>
    <div class="actions"><el-button type="warning" native-type="submit" :disabled="busy || !cancelAttested || !cancelReason.trim() || !sameContext(cancellable) || !availability?.model_runtime_enabled">提交取消</el-button></div>
   </el-form>
  </section>
  <section class="card spaced"><h2>选择模型与输入</h2><p>选择可用配置，提交时会再次核验权限。</p>
   <el-form label-position="top" :disabled="busy" @submit.prevent>
    <el-form-item label="项目"><el-select v-model="projectId" filterable placeholder="选择项目"><el-option v-for="p in projects" :key="p.project_id" :value="p.project_id" :label="p.name"/></el-select><el-button v-if="projectCursor" link @click="load('projects',true)">更多项目</el-button></el-form-item>
    <el-form-item label="模型版本"><el-select v-model="modelId" filterable placeholder="选择 DEEPSEEK 项目配置"><el-option v-for="m in eligibleModels" :key="m.model_config_revision" :value="m.model_config_revision" :label="m.definition.model_id + ' · ' + m.definition.model_version"/></el-select><el-button :disabled="busy" link @click="load('models',!!modelCursor)">{{ modelCursor ? '更多模型配置' : '加载模型配置' }}</el-button></el-form-item>
    <details v-if="selectedModel"><summary>技术详情：版本与请求标识</summary><p class="identifier">固定配置 {{ selectedModel.model_config_revision }} · 摘要 {{ selectedModel.content_sha256 }} · DRAFT（尚不代表已启用）</p></details>
    <el-form-item label="验证角色"><el-select v-model="roleId" filterable placeholder="选择允许验证和发送当前模型的角色"><el-option v-for="c in roleCandidates" :key="c.record_id" :value="c.record_id" :label="c.resource_id"/></el-select></el-form-item>
    <el-form-item label="提示词"><el-select v-model="promptId" filterable placeholder="选择提示词版本"><el-option v-for="c in promptCandidates" :key="c.record_id" :value="c.record_id" :label="c.resource_id"/></el-select><el-button link @click="load('capabilities',!!capabilityCursor)">{{ capabilityCursor ? '更多能力' : '加载角色与提示词' }}</el-button></el-form-item>
    <el-form-item label="已批准规则"><el-select v-model="ruleIds" multiple filterable placeholder="选择1至64项规则"><el-option v-for="r in approvedRules" :key="r.rule_version" :value="r.rule_version" :label="r.rule_version+' · '+r.content.expression.expression"/></el-select><el-button link @click="loadRules">{{ ruleCursor ? '更多规则' : '加载规则' }}</el-button></el-form-item>
    <el-form-item label="来源目录"><el-select v-model="sourceId" filterable placeholder="选择项目或企业来源"><el-option v-for="s in scopedSources" :key="s.source_version" :value="s.source_version" :label="s.title"/></el-select><el-button link @click="load('sources',!!sourceCursor)">{{ sourceCursor ? '更多来源' : '加载来源' }}</el-button></el-form-item>
    <el-form-item label="企业预算"><el-select v-model="enterpriseBudgetId" filterable placeholder="选择模型币种一致的企业预算"><el-option v-for="b in enterpriseBudgets" :key="b.budget_revision" :value="b.budget_revision" :label="b.currency+' · '+b.budget_revision"/></el-select></el-form-item>
    <el-form-item label="项目预算"><el-select v-model="projectBudgetId" filterable placeholder="选择当前项目同币种预算"><el-option v-for="b in projectBudgets" :key="b.budget_revision" :value="b.budget_revision" :label="b.currency+' · '+b.budget_revision"/></el-select><el-button link @click="load('budgets',!!budgetCursor)">{{ budgetCursor ? '更多预算' : '加载预算' }}</el-button></el-form-item>
   </el-form>
   <section class="input-preparation" aria-label="验证输入准备">
    <h3>验证输入</h3>
    <template v-if="inputPending">
     <details><summary>技术详情：版本与请求标识</summary><p class="identifier">原输入登记键：{{ inputPending.key }}</p></details><p>输入准备不会调用供应商。结果未知时只查询已有请求。</p>
     <el-button :disabled="busy" @click="recoverPreparation">查询准备结果</el-button>
     <el-button v-if="inputPending.receipt" :disabled="busy" @click="readPreparation">查看输入</el-button>
     <p v-if="inputLookup" role="status">{{ inputLookup }}</p>
     <details v-if="inputPending.receipt"><summary>技术详情：版本与请求标识</summary><p class="identifier">EXACT_INPUT：{{ inputPending.receipt.exact_input.artifact_id }} · 固定范围：{{ inputPending.receipt.scope_snapshot_basis.version_id }}</p></details>
     <details v-if="inputView"><summary>查看输入内容（只读）</summary><pre class="original-body">{{ JSON.stringify(inputView.document, null, 2) }}</pre></details>
    </template>
    <template v-else>
     <el-form label-position="top" :disabled="busy" @submit.prevent="prepareInput">
      <el-form-item label="输入用途" for="runtime-input-purpose"><el-select id="runtime-input-purpose" v-model="inputPurpose"><el-option value="MODEL_VERIFICATION" label="模型验证"/><el-option value="DOCUMENT_BODY" label="文档正文"/></el-select></el-form-item>
      <el-button :disabled="busy || !selectedModel || !inputPurpose" @click="loadInputCandidates">选择输入来源</el-button>
      <p>输入准备服务不可用时，请联系部署管理员。</p>
      <template v-if="inputCandidates">
       <p v-if="!inputCandidates.sources.length" role="status">当前项目没有满足用途、登记权限和大小限制的来源候选。</p>
       <p v-else>准备时将读取并校验来源内容与权限。</p>
       <el-form-item label="来源" for="runtime-input-source"><el-select id="runtime-input-source" v-model="inputSource" placeholder="选择输入来源"><el-option v-for="item in inputCandidates.sources" :key="item.source.version_id" :value="item.source.version_id" :label="item.media_type+' · '+item.size_bytes+' 字节 · '+item.source.version_id"/></el-select></el-form-item>
       <el-checkbox v-model="inputAttested">确认只登记所选来源的验证输入与项目范围，不启动供应商请求。</el-checkbox>
       <div class="actions"><el-button type="primary" native-type="submit" :disabled="busy || !inputSource || !inputAttested">准备输入</el-button></div>
      </template>
     </el-form>
    </template>
   </section>
   <el-form label-position="top" :disabled="busy" @submit.prevent="startVerification">
    <el-form-item label="输入 Token 上限（非实际用量）"><el-input v-model="maximumInputTokens" inputmode="numeric" placeholder="填写允许上限，服务端将核算实际用量"/></el-form-item>
    <el-form-item label="输出 Token 上限"><el-input-number v-model="maximumOutputTokens" :min="1" :precision="0"/></el-form-item>
    <el-form-item label="验证原因"><el-input v-model="verifyReason" type="textarea" maxlength="2000"/></el-form-item>
    <p>实际用量和费用以结算回执为准。</p><details><summary>技术详情：验证参数</summary><p>reasoning_effort=none、temperature=null、stop=[]。分词器必须匹配部署执行文件与 MODEL_CONFIGURATION 来源权限。</p></details>
    <el-button :disabled="busy || !selectedModel" @click="loadTokenizer">检查分词器</el-button>
    <details v-if="tokenizerBasis"><summary>技术详情：分词器版本</summary><p class="identifier">Tokenizer固定版本：{{ tokenizerBasis.version_id }} · SHA-256：{{ tokenizerBasis.content_sha256 }}</p></details>
    <el-alert v-else type="warning" :closable="false" :title="tokenizerMissing" description="需要部署匹配的分词器及来源读取权限。"/>
    <p v-if="verificationMissing.length" role="status">尚需：{{ verificationMissing.join('；') }}</p>
    <el-checkbox v-model="verifyAttested">确认使用所选输入和预算发起验证，可能产生供应商费用；仍须通过权限与额度检查。</el-checkbox>
    <div class="actions"><el-button type="primary" native-type="submit" :disabled="busy || !availability?.model_runtime_enabled || !verifyAttested || !!verificationMissing.length || pendingItems.some(p=>p.kind==='VERIFY')">发起验证</el-button></div>
   </el-form>
  </section>
  <section class="card spaced" aria-label="启用模型"><h2>启用模型</h2>
   <p>验证通过后，准备数据政策依据，再申请启用该模型。</p>
   <template v-if="policyPending">
    <details><summary>技术详情：版本与请求标识</summary><p class="identifier">原政策准备键：{{ policyPending.key }}</p></details><p>准备结果未知时只查询已有请求，不换键重发。</p>
    <el-button :disabled="busy" @click="recoverPolicyPreparation">查询政策准备进度</el-button>
    <el-button v-if="policyPending.receipt" :disabled="busy" @click="readPolicyPreparation">查看政策回执</el-button>
    <p v-if="policyStatus" role="status">{{ policyStatus }}</p>
    <details><summary>技术详情：政策准备请求</summary><pre class="original-body">{{ JSON.stringify(policyPending.body,null,2) }}</pre></details>
    <details v-if="policyPending.receipt"><summary>技术详情：政策依据（只读）</summary><pre class="original-body">{{ JSON.stringify(policyPending.receipt,null,2) }}</pre></details>
   </template>
   <el-form v-else label-position="top" :disabled="busy" @submit.prevent="prepareEnablementPolicy">
    <el-button :disabled="busy || !selectedModel" @click="loadPolicyCandidate">检查数据政策</el-button>
    <p>需要已启用的政策准备服务；仅支持不超过2 MiB、无额外上游依赖的独立政策来源。</p>
    <template v-if="policyCandidate">
     <details><summary>技术详情：版本与请求标识</summary><p class="identifier">政策：{{ policyCandidate.policy_basis.version_id }} · 权利：{{ policyCandidate.rights_revision }} · 解析：{{ policyCandidate.parse_version }}</p></details>
     <p>准备时将读取政策原文并核验启用权限。</p>
     <el-checkbox v-model="policyAttested">确认准备政策依据；准备完成后仍需申请启用。</el-checkbox>
     <div class="actions"><el-button type="primary" native-type="submit" :disabled="busy || !policyAttested">准备政策依据</el-button></div>
    </template>
   </el-form>
   <el-form label-position="top" :disabled="busy" @submit.prevent="enableModel">
    <details v-if="passedVerification"><summary>技术详情：版本与请求标识</summary><p class="identifier">原 PASSED 验证：{{ passedVerification.result && 'verification_id' in passedVerification.result ? passedVerification.result.verification_id : '' }}</p></details>
    <el-form-item label="启用原因"><el-input v-model="enableReason" type="textarea" maxlength="2000"/></el-form-item>
    <p v-if="enableMissing.length" role="status">尚需：{{ enableMissing.join('；') }}</p>
    <el-checkbox v-model="enableAttested">确认申请启用；服务端将核验价格、连接、凭据、数据政策与权限。</el-checkbox>
    <div class="actions"><el-button type="primary" native-type="submit" :disabled="busy || !availability?.model_runtime_enabled || !enableAttested || !!enableMissing.length || pendingItems.some(p=>p.kind==='ENABLE')">申请启用</el-button></div>
   </el-form><p>连接过期或凭据撤销后，模型将无法继续使用。</p><details><summary>技术详情：启用依据</summary><p>MODEL_ENABLEMENT 需独立政策原件、权利、解析及正式范围，验证输入不能替代。当前没有资格续期或撤销入口。</p></details>
  </section>
 </main>
</template>
<script setup lang="ts">
import { readPolicyCandidate, policyRegistration, preparePolicy, readPendingPolicy, verifyPendingPolicy, recoverPolicy, readPreparedPolicy } from '@/api/medical/model-runtime-enablement-preparation'
import type { PolicyCandidate, PendingPolicy } from '@/api/medical/model-runtime-enablement-preparation'
import { recheckEnablementSelection } from '@/api/medical/model-runtime-enablement-selection'
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import SessionPanel from './session-panel.vue'
import { canonical, readPendingRuntime, verifyPendingRuntime } from '@/api/medical/model-runtime-domain'
import type { Availability, PendingRuntime } from '@/api/medical/model-runtime-domain'
import { readInputCandidates, registerInput, readPendingInput, verifyPendingInput, recoverInput, readRegisteredInput } from '@/api/medical/model-runtime-preparation'
import type { InputCandidates, InputPurpose, PendingInput, InputRead } from '@/api/medical/model-runtime-preparation'
import { cancelRuntime, recoverCancellation, readPendingCancellation, verifyPendingCancellation } from '@/api/medical/model-runtime-cancellation'
import type { PendingCancellation } from '@/api/medical/model-runtime-cancellation'
import { readTokenizerCandidates } from '@/api/medical/model-runtime-tokenizer'
import { roleSupportsModel, recheckVerificationSelection } from '@/api/medical/model-runtime-selection'
import type { VerificationSelection } from '@/api/medical/model-runtime-selection'
import { readRuleList } from '@/api/medical/rule-versions'
import type { StoredRule } from '@/api/medical/rule-versions'
import { readRuntimeAvailability, startRuntime, recoverRuntime, completeRuntime } from '@/api/medical/model-runtime'
import { capabilityProjectCandidates } from '@/api/medical/formal-capability-candidates'
import { queryCapabilities } from '@/api/medical/formal-capabilities'
import type { FormalCapabilityRecord } from '@/api/medical/formal-capability-domain'
import { listModelConfigurations } from '@/api/medical/model-configurations'
import type { ModelView } from '@/api/medical/model-configuration-domain'
import { readSourceCatalogue } from '@/api/medical/source-governance'
import type { SourceCatalogueItem } from '@/api/medical/source-governance'
import { listBudgets } from '@/api/medical/budgets'
import type { BudgetAccount } from '@/api/medical/budgets'
import type { Basis, MedicalSession, Project } from '@/api/medical/protocol'
const user=useUserStore(),loading=ref(false),error=ref(''),availability=ref<Availability|null>(null),pendingItems=ref<PendingRuntime[]>([])
const roleId=ref(''),promptId=ref(''),ruleIds=ref<string[]>([]),enterpriseBudgetId=ref(''),projectBudgetId=ref(''),rules=shallowRef<StoredRule[]>([]),ruleCursor=ref<string|null>(null),tokenizerBasis=ref<Basis|null>(null),tokenizerMissing=ref('尚未读取实际部署Tokenizer'),maximumInputTokens=ref(''),maximumOutputTokens=ref<number|undefined>(),verifyReason=ref(''),verifyAttested=ref(false)
const roleCandidates=computed(()=>selectedModel.value?scopedCapabilities.value.filter(c=>roleSupportsModel(c,selectedModel.value!)):[]),promptCandidates=computed(()=>scopedCapabilities.value.filter(c=>c.content.request.kind==='PROMPT')),approvedRules=computed(()=>rules.value.filter(r=>r.status==='APPROVED')),enterpriseBudgets=computed(()=>budgets.value.filter(b=>b.project_id===null&&b.currency===selectedModel.value?.definition.currency)),projectBudgets=computed(()=>budgets.value.filter(b=>b.project_id===projectId.value&&b.currency===selectedModel.value?.definition.currency))
const verificationMissing=computed(()=>[...(!inputPending.value?.receipt?['已登记固定输入']:[]),...(!selectedModel.value||inputPending.value?.receipt?.model_config_revision!==modelId.value?['与输入匹配的项目模型']:[]),...(!roleCandidates.value.some(c=>c.record_id===roleId.value)?['正式验证角色']:[]),...(!promptCandidates.value.some(c=>c.record_id===promptId.value)?['正式提示词']:[]),...(!ruleIds.value.length||ruleIds.value.some(id=>!approvedRules.value.some(r=>r.rule_version===id))?['已批准正式规则']:[]),...(!enterpriseBudgets.value.some(b=>b.budget_revision===enterpriseBudgetId.value)?['企业预算']:[]),...(!projectBudgets.value.some(b=>b.budget_revision===projectBudgetId.value)?['项目预算']:[]),...(!tokenizerBasis.value?['实际部署匹配的Tokenizer依据']:[]),...(!maximumInputTokens.value||!maximumOutputTokens.value?['明确输入/输出上限']:[]),...(!verifyReason.value.trim()?['验证原因']:[])])
const inputCandidates=ref<InputCandidates|null>(null),inputPurpose=ref<InputPurpose|''>(''),inputSource=ref(''),inputAttested=ref(false),inputPending=ref<PendingInput|null>(null),inputView=ref<InputRead|null>(null),inputLookup=ref('')
const policyCandidate=ref<PolicyCandidate|null>(null),policyPending=ref<PendingPolicy|null>(null),policyAttested=ref(false),policyStatus=ref(''),enableReason=ref(''),enableAttested=ref(false)
const passedVerification=computed(()=>pendingItems.value.find(p=>p.kind==='VERIFY'&&p.result?.status==='PASSED'&&p.body.model_config_revision===modelId.value)??null)
const enableMissing=computed(()=>[...(!selectedModel.value?['实际项目模型']:[]),...(!passedVerification.value?['同一模型的原 PASSED 验证']:[]),...(!policyPending.value?.receipt||policyPending.value.receipt.model_config_revision!==modelId.value||policyPending.value.receipt.project_id!==projectId.value?['同一模型独立 MODEL_ENABLEMENT 政策准备']:[]),...(!enableReason.value.trim()?['启用原因']:[])])
const cancellation=ref<PendingCancellation|null>(null),cancelFound=ref<boolean|null>(null),cancelReason=ref(''),cancelAttested=ref(false)
const cancellable=computed(()=>pendingItems.value.find(p=>p.kind==='VERIFY'&&p.result?.status==='RUNNING'&&p.result.invocation_id)??null)
const cancellationStatus=computed(()=>cancellation.value?.receipt ? '已核验原取消回执：'+cancellation.value.receipt.status : cancelFound.value===false ? '尚未找到原取消请求；继续保留原记录，只读核查。' : '原取消结果待确认。')
const projects=ref<Project[]>([]),models=ref<ModelView[]>([]),capabilities=ref<FormalCapabilityRecord[]>([]),sources=ref<SourceCatalogueItem[]>([]),budgets=ref<BudgetAccount[]>([])
const projectCursor=ref<string|null>(null),modelCursor=ref<string|null>(null),capabilityCursor=ref<string|null>(null),sourceCursor=ref<string|null>(null),budgetCursor=ref<string|null>(null)
const projectId=ref(''),modelId=ref(''),capabilityId=ref(''),sourceId=ref(''),budgetId=ref('')
const busy=computed(()=>loading.value||user.medicalWriting),eligibleModels=computed(()=>models.value.filter(m=>!!projectId.value&&m.project_id===projectId.value&&m.definition.provider==='DEEPSEEK'&&m.definition.limits.reasoning_efforts.includes('none'))),selectedModel=computed(()=>eligibleModels.value.find(m=>m.model_config_revision===modelId.value))
const scopedCapabilities=computed(()=>capabilities.value.filter(c=>c.content.request.project_id===null||c.content.request.project_id===projectId.value)),scopedSources=computed(()=>sources.value.filter(s=>s.ownership!=='SESSION'&&(s.owner_project_id===null||s.owner_project_id===projectId.value))),scopedBudgets=computed(()=>budgets.value.filter(b=>b.project_id===null||b.project_id===projectId.value))
let controller=new AbortController(),generation=0,mounted=true,ownsWrite=false
const reader=():MedicalSession=>{if(!user.medicalSession?.current_context)throw new Error('请先选择企业');return user.medicalSession}
const sameContext=(p:PendingRuntime)=>canonical(p.context)===canonical(user.medicalSession?.current_context)
function statusText(p:PendingRuntime){switch(p.result?.status){case 'PASSED':return '已核验 PASSED：存在真实已结算调用证据；尚需单独启用资格。';case 'RUNNING':return '验证已登记，尚未获得 PASSED 证据。';case 'UNKNOWN':return '原意图存在，但调用绑定尚不完整。只读核查，不得重发。';case 'NOT_FOUND':return '未查到原意图。保留原请求，不据此创建新键。';case 'ENABLED':return '原固定模型资格已登记 ENABLED；实际使用仍需当前权限、凭据、政策与连接有效。';default:return '结果待确认，仅允许原键恢复。'}}
function invalidate(){policyCandidate.value=null;policyPending.value=null;policyAttested.value=false;policyStatus.value='';enableReason.value='';enableAttested.value=false;roleId.value='';promptId.value='';ruleIds.value=[];enterpriseBudgetId.value='';projectBudgetId.value='';rules.value=[];ruleCursor.value=null;tokenizerBasis.value=null;maximumInputTokens.value='';maximumOutputTokens.value=undefined;verifyReason.value='';verifyAttested.value=false;inputCandidates.value=null;inputSource.value='';inputAttested.value=false;inputPending.value=null;inputView.value=null;inputLookup.value='';cancellation.value=null;cancelFound.value=null;cancelReason.value='';cancelAttested.value=false;controller.abort();controller=new AbortController();generation++;availability.value=null;pendingItems.value=[];projects.value=[];models.value=[];capabilities.value=[];sources.value=[];budgets.value=[];projectId.value='';modelId.value='';capabilityId.value='';sourceId.value='';budgetId.value='';projectCursor.value=modelCursor.value=capabilityCursor.value=sourceCursor.value=budgetCursor.value=null;error.value='';loading.value=false}
async function run(work:(signal:AbortSignal)=>Promise<void>,write=false){if(busy.value||document.hidden)return;const g=generation,s=reader();loading.value=true;error.value='';if(write){user.medicalWriting=true;ownsWrite=true}try{await work(controller.signal)}catch(e){if(mounted&&g===generation&&user.medicalSession===s&&!controller.signal.aborted)error.value=e instanceof Error?e.message:'读取失败'}finally{if(write&&ownsWrite){ownsWrite=false;user.medicalWriting=false}if(g===generation)loading.value=false}}
async function refresh(){await run(async signal=>{const a=await readRuntimeAvailability(reader,signal);const s=reader(),items:PendingRuntime[]=[];for(const k of ['VERIFY','ENABLE'] as const){const p=readPendingRuntime(s,k);if(p){await verifyPendingRuntime(s,p);signal.throwIfAborted();if(reader()!==s)throw new Error('上下文已变化');items.push(p)}}const c=readPendingCancellation(s);if(c){await verifyPendingCancellation(s,c);signal.throwIfAborted();if(reader()!==s)throw new Error('上下文已变化')}const prep=readPendingInput(s);if(prep){await verifyPendingInput(s,prep);signal.throwIfAborted();if(reader()!==s)throw new Error('上下文已变化')}const policy=readPendingPolicy(s);if(policy){await verifyPendingPolicy(s,policy);signal.throwIfAborted();if(reader()!==s)throw new Error('上下文已变化')}policyPending.value=policy;availability.value=a;pendingItems.value=items;cancellation.value=c;inputPending.value=prep});if(!error.value&&user.medicalSession?.current_context)await load('projects',false)}
async function load(kind:'projects'|'models'|'capabilities'|'sources'|'budgets',more=false){await run(async signal=>{const s=reader(),g=generation;const live=()=>{signal.throwIfAborted();if(g!==generation||reader()!==s)throw new Error('上下文已变化')};if(kind==='projects'){const v=await capabilityProjectCandidates(reader,more?projectCursor.value:null,signal);live();projects.value=more?[...projects.value,...v.projects]:v.projects;projectCursor.value=v.next_after}else if(kind==='models'){const v=await listModelConfigurations(s,{limit:50,...(more&&modelCursor.value?{after:modelCursor.value}:{})},signal);live();models.value=more?[...models.value,...v.items]:v.items;modelCursor.value=v.next_after}else if(kind==='capabilities'){const v=await queryCapabilities(reader,more?capabilityCursor.value:null,signal);live();capabilities.value=more?[...capabilities.value,...v.items]:v.items;capabilityCursor.value=v.next_cursor}else if(kind==='sources'){const v=await readSourceCatalogue(s,{limit:50,...(more&&sourceCursor.value?{after:sourceCursor.value}:{})},signal);live();sources.value=more?[...sources.value,...v.items]:v.items;sourceCursor.value=v.next_after}else {const v=await listBudgets(s,{limit:'50',...(more&&budgetCursor.value?{after:budgetCursor.value}:{})},signal);live();budgets.value=more?[...budgets.value,...v.budgets]:v.budgets;budgetCursor.value=v.next_after}})}
function replace(p:PendingRuntime){pendingItems.value=pendingItems.value.map(x=>x.kind===p.kind?p:x)}
async function recover(p:PendingRuntime){await run(async signal=>replace(await recoverRuntime(reader,p,signal)))}
async function complete(p:PendingRuntime){await run(async signal=>replace(await completeRuntime(reader,p,signal)),true);if(mounted&&!document.hidden&&user.medicalSession?.current_context)pendingItems.value=(['VERIFY','ENABLE'] as const).flatMap(k=>{const p=readPendingRuntime(reader(),k);return p?[p]:[]})}
async function loadPolicyCandidate(){const m=selectedModel.value;if(!m)return;policyCandidate.value=null;policyAttested.value=false;await run(async signal=>{const c=await readPolicyCandidate(reader,m.project_id!,m.model_config_revision,signal);signal.throwIfAborted();if(modelId.value!==m.model_config_revision)return;if(canonical(c.policy_basis)!==canonical(m.definition.data_policy.policy_basis))throw new Error('政策候选与模型固定配置不一致');policyCandidate.value=c})}
async function prepareEnablementPolicy(){const c=policyCandidate.value;if(!c||!policyAttested.value||c.model_config_revision!==modelId.value||c.project_id!==projectId.value)return;await run(async signal=>{const p=await preparePolicy(reader,c,signal,p=>{policyPending.value=p;policyAttested.value=false});signal.throwIfAborted();policyPending.value=p},true)}
async function recoverPolicyPreparation(){const p=policyPending.value;if(!p)return;await run(async signal=>{const v=await recoverPolicy(reader,p,signal);signal.throwIfAborted();policyPending.value=v.pending;policyStatus.value=v.view?'已核验原操作与独立政策回执。':v.lookup.lookup_status==='FOUND'?'原操作尚未完成，只读核查。':v.lookup.lookup_status==='EXPIRED'?'原键期限已过，保留记录供人工核查。':'未找到原操作，保留原键，不重发。'})}
async function readPolicyPreparation(){const p=policyPending.value;if(!p)return;await run(async signal=>{await readPreparedPolicy(reader,p,signal);signal.throwIfAborted();policyStatus.value='独立 MODEL_ENABLEMENT 回执已重新核验。'})}
async function enableModel(){const p=policyPending.value,m=selectedModel.value,v=passedVerification.value;if(!enableAttested.value||enableMissing.value.length||!p?.receipt||!m||!v)return;const reason=enableReason.value;await run(async signal=>{const fresh=await readPolicyCandidate(reader,m.project_id!,m.model_config_revision,signal);if(canonical(policyRegistration(fresh))!==canonical(p.body)||fresh.parse_version!==p.receipt!.data_policy_evidence.parse_version)throw new Error('政策、权利、项目配置或解析已变化，请重新核查');const read=await readPreparedPolicy(reader,p,signal);const body=await recheckEnablementSelection(reader,m,v,read.receipt,reason,signal);signal.throwIfAborted();const next=await startRuntime(reader,'ENABLE',body,signal,next=>{pendingItems.value=[...pendingItems.value.filter(x=>x.kind!=='ENABLE'),next];enableAttested.value=false});signal.throwIfAborted();replace(next)},true)}
watch([()=>canonical(policyPending.value?.receipt??null),()=>canonical(passedVerification.value?.result??null)],()=>{enableAttested.value=false},{flush:'sync'})
watch(enableReason,()=>{enableAttested.value=false;invalidateSelection()},{flush:'sync'})
async function loadTokenizer(){const m=selectedModel.value;if(!m)return;tokenizerBasis.value=null;verifyAttested.value=false;await run(async signal=>{const v=await readTokenizerCandidates(reader,m.project_id!,m.model_config_revision,signal);signal.throwIfAborted();if(modelId.value!==m.model_config_revision)return;tokenizerBasis.value=v.tokenizer_basis;const labels={FORMAL_RUNTIME_UNAVAILABLE:'正式运行服务未就绪',TOKENIZER_EXECUTABLE_UNAVAILABLE:'已部署Tokenizer执行文件不可用',TOKENIZER_SOURCE_UNAVAILABLE:'Tokenizer来源依据当前不可读'};tokenizerMissing.value=v.missing_input_reasons.map(reason=>labels[reason]).join('；')})}
async function loadRules(){await run(async signal=>{const v=await readRuleList(reader(),{limit:50,...(ruleCursor.value?{after:ruleCursor.value}:{})},signal);signal.throwIfAborted();rules.value=[...rules.value,...v.items.map(i=>i.rule)];ruleCursor.value=v.next_after})}
async function startVerification(){if(!verifyAttested.value||verificationMissing.value.length||!inputPending.value||!tokenizerBasis.value)return;const s:VerificationSelection={model:selectedModel.value!,role:roleCandidates.value.find(c=>c.record_id===roleId.value)!,prompt:promptCandidates.value.find(c=>c.record_id===promptId.value)!,rules:ruleIds.value.map(id=>approvedRules.value.find(r=>r.rule_version===id)!),enterpriseBudget:enterpriseBudgets.value.find(b=>b.budget_revision===enterpriseBudgetId.value)!,projectBudget:projectBudgets.value.find(b=>b.budget_revision===projectBudgetId.value)!,tokenizer:tokenizerBasis.value,maximumInputTokens:maximumInputTokens.value,maximumOutputTokens:maximumOutputTokens.value!,reason:verifyReason.value};await run(async signal=>{const body=await recheckVerificationSelection(reader,s,inputPending.value!,signal);signal.throwIfAborted();const p=await startRuntime(reader,'VERIFY',body,signal,p=>{pendingItems.value=[...pendingItems.value.filter(x=>x.kind!=='VERIFY'),p];verifyAttested.value=false});signal.throwIfAborted();replace(p)},true)}
watch([roleId,promptId,ruleIds,enterpriseBudgetId,projectBudgetId,maximumInputTokens,maximumOutputTokens,verifyReason],()=>{verifyAttested.value=false;invalidateSelection()},{deep:true,flush:'sync'})
async function loadInputCandidates(){const model=selectedModel.value,purpose=inputPurpose.value;if(!model||!purpose)return;inputCandidates.value=null;inputSource.value='';inputAttested.value=false;await run(async signal=>{const v=await readInputCandidates(reader,model.project_id!,model.model_config_revision,purpose,signal);signal.throwIfAborted();if(selectedModel.value?.model_config_revision!==model.model_config_revision||inputPurpose.value!==purpose)return;inputCandidates.value=v})}
async function prepareInput(){const c=inputCandidates.value;if(!c||!inputAttested.value||c.model_config_revision!==modelId.value||c.project_id!==projectId.value||c.business_purpose!==inputPurpose.value)return;await run(async signal=>{const next=await registerInput(reader,c,inputSource.value,signal,p=>{inputPending.value=p;inputAttested.value=false});signal.throwIfAborted();inputPending.value=next},true)}
async function recoverPreparation(){const p=inputPending.value;if(!p)return;await run(async signal=>{const v=await recoverInput(reader,p,signal);signal.throwIfAborted();inputPending.value=v.pending;inputView.value=v.view;inputLookup.value=v.view?'原操作、完整原请求、固定回执及原文摘要已核验。':v.lookup.lookup_status==='FOUND'?'原操作尚未完成；只读核查。':v.lookup.lookup_status==='EXPIRED'?'原键查询期限已过，保留原请求供人工核查。':'尚未找到原操作，保留原键，不重发。'})}
async function readPreparation(){const p=inputPending.value;if(!p)return;inputView.value=null;await run(async signal=>{const v=await readRegisteredInput(reader,p,signal);signal.throwIfAborted();inputView.value=v})}
function invalidateSelection(){controller.abort();controller=new AbortController();generation++;loading.value=false}
watch(modelId,()=>{policyCandidate.value=null;policyAttested.value=false;enableAttested.value=false;policyStatus.value=''},{flush:'sync'})
watch([modelId,inputPurpose],()=>{verifyAttested.value=false;tokenizerBasis.value=null;tokenizerMissing.value='尚未读取实际部署Tokenizer';roleId.value='';promptId.value='';ruleIds.value=[];enterpriseBudgetId.value='';projectBudgetId.value='';invalidateSelection();inputCandidates.value=null;inputSource.value='';inputAttested.value=false;inputView.value=null},{flush:'sync'})
watch(inputSource,()=>{invalidateSelection();inputAttested.value=false;inputView.value=null},{flush:'sync'})
async function cancel(){const original=cancellable.value;if(!original||!cancelAttested.value||!sameContext(original))return;await run(async signal=>{const next=await cancelRuntime(reader,original,cancelReason.value,signal,p=>{cancellation.value=p;cancelAttested.value=false});signal.throwIfAborted();cancellation.value=next},true)}
async function recoverCancel(){const p=cancellation.value;if(!p)return;await run(async signal=>{const result=await recoverCancellation(reader,p,signal);signal.throwIfAborted();cancellation.value=result.pending;cancelFound.value=result.found})}
watch([cancelReason,()=>cancellable.value?.key],()=>{cancelAttested.value=false},{flush:'sync'})
watch(projectId,()=>{modelId.value='';capabilityId.value='';sourceId.value='';budgetId.value=''},{flush:'sync'})
watch(()=>[user.medicalSession,canonical(user.medicalSession?.current_context??null)],()=>{invalidate();if(user.medicalSession?.current_context&&!document.hidden)void refresh()},{immediate:true,flush:'sync'})
function visibility(){invalidate();if(!document.hidden&&user.medicalSession?.current_context)void refresh()}
document.addEventListener('visibilitychange',visibility)
onBeforeUnmount(()=>{mounted=false;invalidate();document.removeEventListener('visibilitychange',visibility)})
</script>
<style scoped>.runtime-page{max-width:1100px}.spaced{margin-top:20px}.actions{display:flex;gap:12px;margin:16px 0}.original-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}.identifier{overflow-wrap:anywhere;font-family:monospace}.el-select{width:100%}.card{padding:24px}.card p{line-height:1.6}</style>
