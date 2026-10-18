<template>
 <main ref="root" class="policy-page"><nav aria-label="位置"><router-link to="/agents">Agent 管理</router-link> / <router-link to="/agents/execution-policies">执行策略</router-link> / 固定详情</nav>
  <header><div><h1>执行策略固定详情</h1><p>核对不可变策略声明，再对当前状态作出独立决定。</p></div><el-tag v-if="view" :type="view.status==='APPROVED'?'success':'warning'">{{ view.status }}</el-tag></header>
  <session-panel/><el-alert type="info" :closable="false" title="批准策略不会发布 Agent" description="运行仍需真实 Agent 发布证明、当前角色与来源权利、Worker 身份以及所有运行时依赖。"/>
  <el-alert v-if="error" class="spaced" type="error" :title="error" :closable="false" role="alert"/>
  <div class="actions"><el-button :disabled="busy" @click="restart">重新读取固定版本与当前状态</el-button><router-link to="/agents/execution-policies">返回策略目录</router-link></div><p v-if="loading" role="status">正在核验固定策略与当前状态…</p>
  <p v-if="availability?.features.policy_authoring === false" role="status">当前部署未启用执行策略读取与审批。</p>
  <execution-policy-recovery v-if="pending" :pending="pending" :lookup="lookup" :disabled="busy || !availability?.features.policy_authoring" @recover="recover" />
  <section v-if="completed" class="card result" role="status"><h2>原决定请求已核验</h2><p>原键、原记录和类型回执一致。下方显示当前状态；若其他决定已发生，当前状态可能不同于原决定。</p></section>
  <template v-if="view"><section class="card spaced"><h2>固定身份与当前状态</h2><dl><dt>策略标识</dt><dd>{{ view.fixed.policy_id }}</dd><dt>固定版本</dt><dd>{{ view.fixed.version_id }}</dd><dt>定义 SHA-256</dt><dd>{{ view.fixed.definition_sha256 }}</dd><dt>记录分类</dt><dd>{{ view.fixed.classification }}</dd><dt>作者</dt><dd>{{ view.fixed.created_by }}</dd><dt>创建时间（UTC）</dt><dd>{{ view.fixed.created_at }}</dd><dt>作者原记录</dt><dd>{{ view.fixed.author_record_id }}</dd><dt>当前状态 / 修订</dt><dd>{{ view.status }} / {{ view.revision }}</dd></dl><p v-if="view.missing_input_reasons.length">当前缺项或限制：{{ view.missing_input_reasons.join('、') }}</p><p v-else>当前读取没有报告缺项；仍需在审批和实际执行时重新核验。</p></section>
   <section class="card spaced"><h2>完整策略声明</h2><execution-policy-definition :definition="view.fixed.definition"/></section>
   <section v-if="view.latest_decision" class="card spaced"><h2>当前最新决定</h2><dl><dt>决定</dt><dd>{{ view.latest_decision.decision }}</dd><dt>决定人</dt><dd>{{ view.latest_decision.actor_id }}</dd><dt>决定时间（UTC）</dt><dd>{{ view.latest_decision.decided_at }}</dd><dt>原因</dt><dd>{{ view.latest_decision.reason }}</dd><dt>原状态 / 修订</dt><dd>{{ view.latest_decision.previous_status }} / {{ view.latest_decision.previous_revision }}</dd><dt>原记录</dt><dd>{{ view.latest_decision.decision_record_id }}</dd><dt>批准授权 / 修订</dt><dd>{{ view.latest_decision.authority_grant_id }} / {{ view.latest_decision.authority_revision }}</dd></dl></section>
   <el-form v-if="!pending && (view.status==='DRAFT'||view.status==='APPROVED')" label-position="top" class="card spaced" :disabled="busy" @submit.prevent="submit"><h2>对当前修订作出决定</h2><p>绑定上方固定版本、定义摘要与修订 {{ view.revision }}。若状态变化，服务端拒绝旧修订；本页不会自动刷新后重试。</p><el-form-item label="审批决定"><el-select v-model="decision" aria-label="审批决定"><el-option v-if="view.status==='DRAFT'" value="APPROVE" label="批准固定策略"/><el-option v-if="view.status==='DRAFT'" value="REJECT" label="拒绝策略草稿"/><el-option v-if="view.status==='APPROVED'" value="DISABLE" label="停用已批准策略"/></el-select></el-form-item><el-form-item label="决定原因"><el-input v-model="reason" type="textarea" :rows="3" maxlength="2000" show-word-limit/></el-form-item><p v-if="issue" role="status">{{ issue }}</p><el-checkbox v-model="attested">已审阅完整声明与当前修订，确认提交此决定；批准须由非作者独立完成</el-checkbox><div class="actions"><el-button type="primary" native-type="submit" :disabled="busy || !attested || !!issue || !reason">提交当前修订决定</el-button></div></el-form>
  </template>
  <el-dialog v-model="leaveDialog" title="离开未提交的决定？" width="min(94vw,480px)" :close-on-click-modal="false"><p>未提交的决定原因将清除。已保存的原请求继续保留，可从策略页面读取恢复。</p><template #footer><el-button @click="leaveDialog=false">继续审核</el-button><el-button type="danger" @click="confirmLeave">清除并离开</el-button></template></el-dialog>
 </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import SessionPanel from './session-panel.vue'
import ExecutionPolicyDefinition from './execution-policy-definition.vue'
import ExecutionPolicyRecovery from './execution-policy-recovery.vue'
import { usePolicyPage } from '@/api/medical/use-execution-policy-page'
import { decisionIssue, policyInputIssue, readPendingPolicy } from '@/api/medical/execution-policy-domain'
import type { DecisionKind, PendingPolicy, PolicyAvailability, PolicyDecide, PolicyView } from '@/api/medical/execution-policy-domain'
import { decidePolicy, readPolicy, readPolicyAvailability, recoverPolicy } from '@/api/medical/execution-policies'
import type { OperationLookup } from '@/api/medical/operations'
const route=useRoute(),router=useRouter(),root=ref<HTMLElement|null>(null),view=ref<PolicyView|null>(null),availability=ref<PolicyAvailability|null>(null),pending=ref<PendingPolicy|null>(null),lookup=ref<OperationLookup|null>(null),completed=ref(false),decision=ref<DecisionKind>('APPROVE'),reason=ref(''),attested=ref(false),leaveDialog=ref(false)
let leaveApproved=false,leavePath=''
const page=usePolicyPage(reset,bootstrap),{user,loading,error,busy,reader}=page
const issue=computed(()=>view.value&&availability.value&&user.medicalSession?decisionIssue(user.medicalSession,view.value,decision.value,availability.value):'请先读取当前策略与部署可用性')
function reset(){root.value?.querySelectorAll<HTMLInputElement|HTMLTextAreaElement>('input,textarea').forEach(el=>{el.value='';if(el instanceof HTMLInputElement)el.checked=false});view.value=null;availability.value=null;pending.value=null;lookup.value=null;completed.value=false;reason.value='';decision.value='APPROVE';attested.value=false;leaveDialog.value=false}
function install(v:PolicyView){view.value=v;decision.value=v.status==='APPROVED'?'DISABLE':'APPROVE';reason.value='';attested.value=false}
async function bootstrap(){await page.run(async(signal,current)=>{pending.value=readPendingPolicy(reader());const a=await readPolicyAvailability(reader,signal);if(!current())return;availability.value=a;if(!a.features.policy_authoring)return;const id=route.params.versionId;if(typeof id!=='string')throw new Error('策略固定版本地址无效');const v=await readPolicy(reader,id,signal);if(current())install(v)})}
function restart(){if(busy.value)return;page.clear();void bootstrap()}
async function submit(){const target=view.value;if(!target||busy.value||pending.value||!attested.value||issue.value)return;const body:PolicyDecide={schema_version:'1.0.0',policy_version:{version_id:target.fixed.version_id,content_sha256:target.fixed.definition_sha256},expected_revision:target.revision,decision:decision.value,reason:reason.value};const invalid=policyInputIssue('EXECUTION_POLICY_DECISION',body);if(invalid){error.value=invalid;return}
 await page.run(async(signal,current)=>{try{const p=await decidePolicy(reader,target,body,signal,value=>{if(current())pending.value=value});if(!current())return;attested.value=false;const result=await recoverPolicy(reader,p,signal);if(current()){lookup.value=result.lookup;if(result.view){pending.value=null;install(result.view);completed.value=true}}}finally{if(current())pending.value=readPendingPolicy(reader())}},true)
}
async function recover(){const p=pending.value;if(!p)return;await page.run(async(signal,current)=>{const result=await recoverPolicy(reader,p,signal);if(current()){lookup.value=result.lookup;if(result.view){pending.value=null;completed.value=true;if(result.view.fixed.version_id===route.params.versionId)install(result.view);else await router.push('/agents/execution-policies/'+result.view.fixed.version_id)}}})}
function dirty(){return !pending.value&&(!!reason.value||attested.value)}
function unload(e:BeforeUnloadEvent){if(dirty()||page.writing()){e.preventDefault();e.returnValue=''}}
async function confirmLeave(){reason.value='';attested.value=false;leaveDialog.value=false;leaveApproved=true;try{await router.push(leavePath)}finally{leaveApproved=false;leavePath=''}}
function allowRoute(to:{fullPath:string}){if(user.medicalWriting)return false;if(leaveApproved||!dirty())return true;leavePath=to.fullPath;leaveDialog.value=true;return false}
onBeforeRouteLeave(allowRoute);onBeforeRouteUpdate(allowRoute)
watch([reason,decision],()=>{attested.value=false;completed.value=false},{flush:'sync'})
watch(()=>route.params.versionId,()=>{page.clear();void bootstrap()},{flush:'sync'})
window.addEventListener('beforeunload',unload);onBeforeUnmount(()=>window.removeEventListener('beforeunload',unload));page.start()
</script>
<style scoped src="./execution-policy.css"></style>
