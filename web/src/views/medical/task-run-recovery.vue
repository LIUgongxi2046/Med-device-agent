<template>
  <section v-if="active && canRead && visible" class="run-recovery" aria-label="运行恢复核查">
    <header><div><p class="eyebrow">原运行 · 人工核查</p><h3>先确认中断位置，再决定下一步</h3></div><button type="button" :disabled="loading" @click="emit('refresh')">{{ loading ? '正在读取…' : '重新读取运行' }}</button></header>
    <p v-if="!snapshot" role="status">尚未读取获授权的运行记录，不推断恢复资格。</p>
    <template v-else-if="view">
      <div class="status"><strong>{{ view.status }}</strong><span>服务端状态：{{ view.rawStatus }}</span><p>{{ view.summary }}</p></div>
      <dl class="context"><dt>原运行</dt><dd>{{ snapshot.run_id }}</dd><dt>任务 / 固定版本</dt><dd>{{ snapshot.task_id }} / {{ snapshot.task_revision }}</dd><dt>运行记录修订</dt><dd>{{ snapshot.revision }}</dd><dt>预算 / 批准版本</dt><dd>{{ snapshot.budget.budget_id }} / {{ snapshot.budget.budget_revision }}</dd></dl>
      <nav aria-label="恢复核查导航"><button type="button" @click="jump('failure')">查看失败与步骤（{{ view.failures.length }}）</button><button type="button" @click="jump('waiting')">定位缺失输入（{{ snapshot.waiting_items.length }}）</button></nav>
      <section ref="failure" tabindex="-1" class="detail"><h4>失败详情与原步骤</h4><p>运行原因代码：{{ snapshot.reason_code ?? '服务端未提供原因代码' }}</p><p class="muted">仅显示原始状态和原因；完整错误堆栈、检查点及已完成产物未在当前响应提供。</p><p v-if="!snapshot.steps.length">当前没有步骤记录，不能确定恢复位置。</p><ul v-else class="records"><li v-for="step in snapshot.steps" :key="step.step_id"><strong>{{ step.step_id }}</strong><span>{{ step.status }}</span><p>步骤修订 {{ step.revision }} · 租约代数 {{ step.lease_generation }}</p><p>角色版本 {{ step.role_version }} · 章节 {{ step.section_id ?? '未指定' }}</p></li></ul></section>
      <section ref="waiting" tabindex="-1" class="detail"><h4>缺失输入与等待事项</h4><p v-if="!snapshot.waiting_items.length">当前响应没有等待事项；这不证明全部恢复前置条件已满足。</p><ul v-else class="records"><li v-for="item in snapshot.waiting_items" :key="item.waiting_item_id"><strong>{{ item.question }}</strong><p>事项 {{ item.waiting_item_id }} · 状态 {{ item.status }}</p><p>原步骤 {{ item.step_id }} · 动作 {{ item.action_id }} · 目标版本 {{ item.target_revision }} · 事项修订 {{ item.revision }}</p><p>原因 {{ item.reason_code }} · 责任人员 {{ item.assignee_id }}</p><p>需求引用：{{ item.requirement_refs.join('、') || '未提供' }}</p><p>服务端允许的处理动作：{{ item.allowed_resolution_actions.join('、') || '未提供' }}</p><p>处理记录：{{ item.resolution_id ?? '尚无处理记录' }}</p><span class="muted">当前仅定位原事项，不代填输入、不声称已解决；处理提交入口尚未接入。</span></li></ul></section>
      <div class="prerequisites"><h4>继续前必须核查</h4><ul><li v-for="blocker in view.blockers" :key="blocker">{{ blocker }}</li></ul><p>需重新核验授权、任务/计划/来源快照、角色与模型等固定版本、已完成步骤和人工处理点、预算及停止条件。本页不将这些条件标记为已通过。</p></div>
      <p v-if="!canWrite" class="readonly">只读模式：运行读取许可不代表运行执行或取消许可。</p>
      <form @submit.prevent class="decision"><fieldset :disabled="!canWrite || loading || !view.relevant"><legend>明确选择后续处理（仅本地核查，不会执行）</legend><label><input v-model="form.choice" type="radio" value="inspect">只查看，暂不执行</label><label><input v-model="form.choice" type="radio" value="continue">继续原运行：需确认检查点，不重复已完成步骤</label><label><input v-model="form.choice" type="radio" value="retry">重新尝试：需明确新尝试范围与原运行关联</label><label class="reason">处理理由<textarea v-model="form.reason" maxlength="2048" rows="3" placeholder="记录需要处理的问题与理由，仅保留在本页内存"></textarea></label><label><input v-model="form.versionsChecked" type="checkbox">已核对以上原运行、步骤和版本信息</label><label><input v-model="form.effectsChecked" type="checkbox">已核查已完成产物、在途调用与重复执行风险</label></fieldset>
        <div role="status" class="confirmation"><strong>尚未提交任何恢复或重试请求</strong><p>当前选择：{{ choiceLabel }}。原运行 {{ snapshot.run_id }}，修订 {{ snapshot.revision }}。</p><p>上述勾选只是人工声明，不代表服务端核验通过。</p><ul v-if="form.choice !== 'inspect'"><li v-for="issue in issues" :key="issue">{{ issue }}</li></ul><button type="submit" disabled>继续 / 重试暂不可提交</button><button type="button" @click="reset">清空本地选择</button></div>
      </form><p class="boundary">原启动请求或原停止请求的状态核查，仍在对应原请求入口进行，不是新的执行尝试。本页不会启动、取消或自动重试运行。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { RunView } from '@/api/medical/runs'
import { emptyRecoveryForm, recoveryFormIssues, recoveryView } from '@/api/medical/task-run-recovery'
const props = defineProps<{ snapshot: RunView | null; active: boolean; canRead: boolean; canWrite: boolean; loading?: boolean }>()
const emit = defineEmits<{ refresh: [] }>()
const visible = ref(!document.hidden), form = ref(emptyRecoveryForm())
const failure = ref<HTMLElement | null>(null), waiting = ref<HTMLElement | null>(null)
const view = computed(() => props.snapshot ? recoveryView(props.snapshot) : null)
const issues = computed(() => props.snapshot ? recoveryFormIssues(form.value, props.snapshot, props.canWrite) : [])
const choiceLabel = computed(() => ({ inspect: '只查看', continue: '继续原运行', retry: '重新尝试' })[form.value.choice])
function reset() { form.value = emptyRecoveryForm() }
function jump(target: 'failure' | 'waiting') { const element = target === 'failure' ? failure.value : waiting.value; element?.focus(); element?.scrollIntoView({ block: 'nearest' }) }
watch(() => [props.snapshot, props.snapshot?.revision, props.active, props.canRead, props.canWrite, props.loading], reset, { flush: 'sync', deep: true })
function visibility() { visible.value = !document.hidden; reset() }
document.addEventListener('visibilitychange', visibility)
window.addEventListener('medical-failure', reset)
onBeforeUnmount(() => { reset(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('medical-failure', reset) })
</script>
<style scoped>
.run-recovery{margin:24px 0;border:1px solid #cddbe5;border-radius:12px;padding:24px;background:#fff;color:#243c50;overflow-wrap:anywhere}header{display:flex;justify-content:space-between;align-items:center;gap:16px}h3{margin:4px 0 16px;font-size:21px}.eyebrow{font-size:12px;color:#526b7c;margin:0}.status{background:#fff6e8;border-left:4px solid #b97923;padding:16px}.status span{display:block;font-size:12px;margin-top:6px}.status p{margin-bottom:0;line-height:1.7}.context{display:grid;grid-template-columns:150px 1fr;gap:10px;font-size:13px;padding:16px 0}.context dt{color:#526b7c}.context dd{margin:0}nav{display:flex;gap:10px;flex-wrap:wrap}.detail{margin-top:20px}.detail:focus{outline:2px solid #2773a5;outline-offset:5px}.records{list-style:none;padding:0;display:grid;gap:10px}.records li{background:#f5f8fb;border:1px solid #dbe4eb;border-radius:8px;padding:14px;font-size:13px}.records span{display:block;margin-top:5px}.records p{margin:7px 0;line-height:1.6}.muted,.boundary{font-size:12px;color:#53697b;line-height:1.7}.prerequisites{background:#f4f7fa;padding:4px 16px 12px;border-radius:8px;font-size:13px;line-height:1.8}.readonly{color:#714a0c;background:#fff6df;padding:12px}.decision{margin-top:20px}fieldset{border:1px solid #d5e0e8;border-radius:8px;padding:16px}legend{font-weight:600;font-size:14px}fieldset label{display:flex;gap:8px;align-items:flex-start;margin:12px 0;font-size:13px;line-height:1.6}fieldset:disabled{opacity:.65}.reason{flex-direction:column}textarea{box-sizing:border-box;width:100%;border:1px solid #a9bdcc;border-radius:6px;padding:10px;font:inherit;color:inherit;background:white}.confirmation{margin-top:14px;padding:16px;background:#f3f7fb;border-radius:8px;font-size:13px;line-height:1.7}button{background:#fff;border:1px solid #adc2d1;border-radius:6px;color:#26516e;padding:9px 12px;cursor:pointer;font:inherit;font-size:13px}button:disabled{background:#e9eff3;color:#5f707e;cursor:not-allowed}.confirmation button{margin:6px 8px 0 0}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:2px solid #2773a5;outline-offset:3px}@media(max-width:600px){.run-recovery{padding:14px}header{align-items:flex-start;flex-direction:column}.context{grid-template-columns:1fr;gap:5px}.context dd{margin-bottom:8px}h3{font-size:18px}}
</style>
