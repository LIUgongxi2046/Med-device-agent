<template>
  <section aria-label="N01 专业规划输入准备" class="node-input">
    <h3>3. 准备 N01 规划输入</h3>
    <p>使用原计划制品、核准模板、固定属性与原资料编译规划输入。需要单独批准的新规划编译器、上下文和候选；旧 DOCUMENT_ONLY 绑定不能升级替换。这里只登记输入，不调用模型、不启动运行，也不确认负责人、规则或未知条件。</p>
    <p>企业内可读的模板不等于允许发送给模型。模板和属性的全部来源还须进入当前项目的合格资料选择，具有已批准的解析、索引与使用权，并形成 EVALUATION_RESULTS 固定范围；角色须明确允许项目读取、来源读取及对外处理。缺少这些依据时只会拒绝准备，不会自动审批或扩大权限。</p>
    <button :disabled="busy" @click="refresh">核查输入准备服务与原请求</button>
    <p v-if="availability && !availability.features.preparation">当前部署尚不具备写入依赖。已有原记录仍可按独立读取权限查询。</p>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <template v-if="!pending">
      <label><input v-model="attested" type="checkbox" :disabled="busy || !mayWrite" />确认仅准备此固定单元的规划输入，原待补充项保持未解决</label>
      <button :disabled="busy || !attested || !mayWrite || !availability?.features.preparation" @click="prepare">登记规划输入</button>
    </template>
    <template v-else>
      <p>原键：{{ pending.key }}；样本：{{ pending.body.sample_id }}；重复轮次：{{ pending.body.repeat_index + 1 }}</p>
      <p v-if="status">{{ status.state === 'PREPARED' ? '原输入登记身份已核验；尚无专业完成证明。' : status.state === 'PENDING' ? '原物理尝试待核对，不会再次上传。' : '未找到结果不代表从未发送；不会自动重发。' }}</p>
      <p v-if="receipt">输入制品：{{ receipt.artifact_id }} · {{ receipt.artifact_sha256 }}</p>
      <button :disabled="busy || !availability?.features.status" @click="query('status')">只读查询原状态</button>
      <button :disabled="busy || !receipt || !availability?.features.content" @click="query('content')">重新读取原输入</button>
      <label><input v-model="recoveryAttested" type="checkbox" :disabled="busy || !mayWrite" />确认仅用原键和原参数核查或继续，不新增其他尝试</label>
      <button :disabled="busy || !mayWrite || !recoveryAttested || !availability?.features.recovery" @click="query('recovery')">核查原对象并恢复登记</button>
      <button v-if="status?.state === 'NOT_FOUND'" :disabled="busy || !mayWrite || !recoveryAttested || !availability?.features.preparation" @click="continueOriginal">明确继续原请求</button>
      <section v-if="content" aria-label="重新读取的规划输入">
        <p>已重新读取原对象。以下待补充项没有获得自动确认。</p>
        <ul><li v-for="blocker in content.planning.blockers" :key="blocker.code">{{ blocker.code }}（{{ blocker.target_ids.length }} 项）</li></ul>
        <details><summary>完整原输入消息</summary><article v-for="(message,index) in content.exact_input.messages" :key="index"><strong>{{ message.role }}</strong><pre>{{ message.content }}</pre></article></details>
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue';
import useUserStore from '@/store/modules/user';
import {n01Allowed,n01Canonical,n01Copy,n01FailureMessage} from '@/api/medical/n01-plan-preparation';
import type {N01Pending,N01Identity} from '@/api/medical/n01-plan-preparation';
import type {MedicalSession} from '@/api/medical/protocol';
import {readNodeAvailability,readNodePending,verifyNodePending,prepareNodeInput,queryNodeInput,continueNodeInput} from '@/api/medical/n01-node-input';
import type {NodeInputAvailability,NodeInputPending,NodeInputStatus} from '@/api/medical/n01-node-input';
const props=defineProps<{plan:N01Pending;planIdentity:N01Identity;disabled?:boolean}>();const user=useUserStore();
const loading=ref(false),attested=ref(false),recoveryAttested=ref(false),error=ref(''),notice=ref('');
const availability=ref<NodeInputAvailability|null>(null),pending=ref<NodeInputPending|null>(null),status=ref<NodeInputStatus|null>(null),receipt=ref<N01Identity|null>(null),content=ref<any>(null);
let controller=new AbortController(),generation=0,mounted=true,writeOwner:object|null=null;
const busy=computed(()=>loading.value||props.disabled||user.medicalWriting),mayWrite=computed(()=>n01Allowed(user.medicalSession,props.plan.project_id,props.plan.evaluation_id,'evaluation.run')&&n01Allowed(user.medicalSession,props.plan.project_id,props.plan.evaluation_id,'artifact.register'));
function reader():MedicalSession{if(!user.medicalSession?.current_context)throw new Error('请先选择企业');return user.medicalSession;}
function identity():N01Identity{const p=props.planIdentity;return {schema_version:p.schema_version,state:p.state,runnable:false,professional_completion:false,operation_id:p.operation_id,write_id:p.write_id,attempt_id:p.attempt_id,execution_id:p.execution_id,artifact_id:p.artifact_id,artifact_sha256:p.artifact_sha256,object_version:p.object_version,receipt_sha256:p.receipt_sha256};}
function clear(){controller.abort();controller=new AbortController();generation++;availability.value=null;pending.value=null;receipt.value=null;status.value=null;content.value=null;error.value='';notice.value='';attested.value=false;recoveryAttested.value=false;loading.value=false;}
async function run(work:(signal:AbortSignal,live:()=>boolean)=>Promise<void>,write=false){if(busy.value||document.hidden||!user.medicalSession?.current_context)return;const session=reader(),g=generation,signal=controller.signal,owner={};const live=()=>mounted&&g===generation&&session===user.medicalSession&&!signal.aborted&&!document.hidden;loading.value=true;error.value='';notice.value='';if(write){writeOwner=owner;user.medicalWriting=true;}try{await work(signal,live);}catch(e){if(live())error.value=n01FailureMessage(e);}finally{if(live())loading.value=false;if(writeOwner===owner){writeOwner=null;user.medicalWriting=false;}}}
async function refresh(){await run(async(signal,live)=>{const p=readNodePending(reader(),props.plan.evaluation_id);if(p){await verifyNodePending(reader,p,signal);if(p.project_id!==props.plan.project_id)throw new Error('原请求项目不一致');}const a=await readNodeAvailability(reader,signal);if(live()){pending.value=p;availability.value=a;}});}
async function prepare(){if(!attested.value||!mayWrite.value)return;await run(async(signal,live)=>{const r=await prepareNodeInput(reader,n01Copy(props.plan),identity(),signal,p=>{if(live())pending.value=p;});if(live()){receipt.value=r;notice.value='输入已登记，专业执行尚未开始。';}},true);}
async function query(mode:'status'|'content'|'recovery'){if(!pending.value||mode==='recovery'&&(!recoveryAttested.value||!mayWrite.value))return;const original=n01Copy(pending.value);await run(async(signal,live)=>{const result=await queryNodeInput(reader,original,mode,signal);if(live()){if(mode==='status'){status.value=result;receipt.value=result.receipt;content.value=null;}else if(mode==='content'){content.value=result;receipt.value=result.receipt;}else receipt.value=result;}},mode==='recovery');}
async function continueOriginal(){if(!pending.value||status.value?.state!=='NOT_FOUND'||!recoveryAttested.value||!mayWrite.value)return;const original=n01Copy(pending.value);await run(async(signal,live)=>{const r=await continueNodeInput(reader,original,signal);if(live()){receipt.value=r;notice.value='原请求已核查，未创建新键。';}},true);}
watch(()=>[user.medicalSession,props.plan.evaluation_id,n01Canonical(props.plan.body),n01Canonical(props.planIdentity)],()=>{clear();void refresh();},{immediate:true});
function visibility(){clear();if(!document.hidden)void refresh();}document.addEventListener('visibilitychange',visibility);
onBeforeUnmount(()=>{mounted=false;controller.abort();document.removeEventListener('visibilitychange',visibility);if(writeOwner){writeOwner=null;user.medicalWriting=false;}});
</script>
<style scoped>.node-input{margin-top:1rem;padding:1rem;border:1px solid #cbd5e1;border-radius:.5rem}.node-input button{margin:.35rem}.node-input label{display:block;margin:.7rem 0}.node-input pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:28rem;overflow:auto}</style>
