<template>
  <section class="saved-comparison" aria-label="两个保存版本的结构比较">
    <header><h3>比较两个已保存版本</h3><p>当前未保存草稿不参与比较。比较不会替换正文，也不产生审查或医学结论。</p></header>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="loading" role="status">正在核查比较请求或读取固定结果…</p>
    <template v-if="checked && !availability?.features.document_comparison"><p>当前服务器尚未启用固定版本比较。</p><el-button :disabled="busy" @click="reset">重新核查</el-button></template>
    <template v-if="availability?.features.document_comparison">
      <template v-if="pending">
        <p>已保存原比较请求。{{ recoveryState === 'PROCESSING' ? '操作尚未确定完成；这不表示后台正在继续执行。' : recoveryState === 'NOT_FOUND' ? '尚未查到原操作，仍保留原请求，请稍后只读核查。' : '可只读核查原操作与固定结果。' }}</p>
        <el-button :disabled="busy" @click="recover">核查原比较</el-button>
        <details><summary>原请求核验信息</summary><dl><dt>左版本</dt><dd>{{ pending.body.left_revision }}</dd><dt>右版本</dt><dd>{{ pending.body.right_revision }}</dd><dt>原操作键</dt><dd>{{ pending.key }}</dd><dt>请求摘要</dt><dd>{{ pending.request_body_sha256 }}</dd></dl></details>
      </template>
      <template v-else>
        <div class="selection"><el-button :disabled="busy" @click="side = 'left'">选择左版本</el-button><span>{{ left || '尚未选择' }}</span><el-button :disabled="busy" @click="side = 'right'">选择右版本</el-button><span>{{ right || '尚未选择' }}</span></div>
        <p>正在选择{{ side === 'left' ? '左' : '右' }}版本。左右顺序由你指定，均为实际保存版本。</p>
        <document-revision-directory :session="session" :snapshot="snapshot" :active="active" :disabled="busy" selection-only @select="select" />
        <el-button :disabled="busy || !left || !right" type="primary" @click="create">创建这两个固定版本的比较</el-button>
      </template>
      <template v-if="result">
        <h4>固定结构差异</h4><p>服务端已对两个完整固定输入重新计算核验。结构覆盖完整，共 {{ result.result.record_count }} 条记录；医学与监管语义未评估。</p>
        <p>左侧{{ result.left_is_current ? '是当前保存版' : '为历史保存版' }} · 右侧{{ result.right_is_current ? '是当前保存版' : '为历史保存版' }}。此状态对应本次读取水位。</p>
        <div class="filters"><label>类别 <select v-model="category"><option value="ALL">全部类别</option><option v-for="(label, key) in categoryLabels" :key="key" :value="key">{{ label }}</option></select></label><label>变化 <select v-model="status"><option value="ALL">全部变化</option><option v-for="(label, key) in statusLabels" :key="key" :value="key">{{ label }}</option></select></label></div>
        <p>本地筛选 {{ displayed.filtered }} / 完整 {{ displayed.total }} 条；显示第 {{ displayed.page }} / {{ displayed.pages }} 页。筛选与分页不改变完整覆盖结果。</p>
        <p v-if="!displayed.filtered">当前筛选没有匹配记录。</p>
        <article v-for="record in displayed.items" :key="record.category + ':' + record.identity" class="difference">
          <h4>{{ categoryLabels[record.category] }} · {{ statusLabels[record.status] }}</h4>
          <div class="sides"><section><h5>左版本</h5><dl v-for="field in displayFields(record.before_json)" :key="field.label"><dt>{{ field.label }}</dt><dd>{{ field.value }}</dd></dl></section><section><h5>右版本</h5><dl v-for="field in displayFields(record.after_json)" :key="field.label"><dt>{{ field.label }}</dt><dd>{{ field.value }}</dd></dl></section></div>
          <div v-if="record.text_change" class="sides"><p>左侧变化文字：{{ record.text_change.left.quote || '（空）' }}</p><p>右侧变化文字：{{ record.text_change.right.quote || '（空）' }}</p></div>
          <details><summary>固定身份与摘要</summary><dl><dt>身份</dt><dd>{{ record.identity }}</dd><dt>左摘要</dt><dd>{{ record.before_sha256 || '无' }}</dd><dt>右摘要</dt><dd>{{ record.after_sha256 || '无' }}</dd></dl></details>
        </article>
        <el-button :disabled="displayed.page <= 1" @click="page--">上一页</el-button><el-button :disabled="displayed.page >= displayed.pages" @click="page++">下一页</el-button>
        <details><summary>完整比较核验详情</summary><dl><dt>比较标识</dt><dd>{{ result.comparison.comparison_id }}</dd><dt>算法</dt><dd>{{ result.result.algorithm_id }}</dd><dt>输入摘要</dt><dd>{{ result.result.input_sha256 }}</dd><dt>完整记录摘要</dt><dd>{{ result.result.coverage.delivered_sha256 }}</dd><dt>制品摘要</dt><dd>{{ result.comparison.differences.sha256 }}</dd></dl><p>浏览器检查回执、摘要及内容自一致性；完整来源与实际两版重算由服务器核验。</p></details>
        <el-button :disabled="busy" @click="finish">已读结果，选择另一组版本</el-button>
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import useUserStore from '@/store/modules/user'
import type { MedicalSession } from '@/api/medical/protocol'
import type { DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue as canonical } from '@/api/medical/document-body'
import DocumentRevisionDirectory from './document-revision-directory.vue'
import { categoryLabels, statusLabels, displayFields, differencePage } from '@/api/medical/document-comparison-display'
import type { DifferenceCategory, DifferenceStatus } from '@/api/medical/document-comparison-display'
import { comparisonAvailability, prepareComparison, verifyComparisonPending, createComparison, recoverComparison, readComparison, retainComparison, restoreComparison, forgetComparison } from '@/api/medical/saved-document-comparison'
import type { ComparisonAvailability, ComparisonPending, ComparisonRead, Comparison } from '@/api/medical/saved-document-comparison'
const props = defineProps<{ session: MedicalSession; snapshot: DocumentSnapshot; active: boolean; disabled?: boolean }>()
const user = useUserStore()
const availability = shallowRef<ComparisonAvailability | null>(null)
const pending = shallowRef<ComparisonPending | null>(null)
const result = shallowRef<ComparisonRead | null>(null)
const checked = ref(false), loading = ref(false), error = ref(''), recoveryState = ref('')
const left = ref(''), right = ref(''), side = ref<'left' | 'right'>('left')
const category = ref<DifferenceCategory | 'ALL'>('ALL'), status = ref<DifferenceStatus | 'ALL'>('ALL'), page = ref(1)
const displayed = computed(() => differencePage(result.value?.result.records ?? [], category.value, status.value, page.value))
const busy = computed(() => loading.value || !!props.disabled || user.medicalWriting)
let generation = 0, alive = true, controller = new AbortController()
let ownWrite: object | null = null
const live = (g: number, session: MedicalSession) => alive && props.active && !document.hidden && generation === g && props.session === session
function clear() {
  generation++; controller.abort(); controller = new AbortController()
  availability.value = null; pending.value = null; result.value = null; checked.value = false; loading.value = false
  left.value = ''; right.value = ''; error.value = ''; recoveryState.value = ''; page.value = 1
}
async function reset() {
  clear()
  if (!props.active || document.hidden || !props.session.current_context) return
  const g = generation, session = props.session
  loading.value = true
  try {
    const restored = restoreComparison(session, props.snapshot.document_id)
    if (restored) await verifyComparisonPending(session, restored)
    if (!live(g, session)) return
    pending.value = restored
    const value = await comparisonAvailability(session, controller.signal)
    if (!live(g, session)) return
    availability.value = value; checked.value = true
  } catch (failure) { if (live(g, session)) error.value = failure instanceof Error ? failure.message : '比较状态读取失败' }
  finally { if (live(g, session)) loading.value = false }
}
function select(revision: string) {
  if (busy.value || pending.value || !props.active) return
  if (side.value === 'left') { left.value = revision; side.value = 'right' } else right.value = revision
}
async function show(receipt: Comparison, original: ComparisonPending, g: number, session: MedicalSession) {
  if (!live(g, session) || !availability.value) return
  const value = await readComparison(session, original, receipt, availability.value, controller.signal)
  if (live(g, session)) { result.value = value; recoveryState.value = 'COMPLETED'; page.value = 1 }
}
async function create() {
  if (busy.value || pending.value || !props.active || !left.value || !right.value || !availability.value?.features.document_comparison) return
  const g = generation, session = props.session, body = { document_id: props.snapshot.document_id, left_revision: left.value, right_revision: right.value }
  loading.value = true; error.value = ''
  let token: object | null = null
  try {
    const original = await prepareComparison(session, body)
    if (!live(g, session) || user.medicalWriting) return
    if (new TextEncoder().encode(canonical(body)).byteLength > availability.value!.maximum_request_bytes!) { error.value = '请求超过当前部署容量'; return }
    retainComparison(session, original); pending.value = original
    token = {}; ownWrite = token; user.medicalWriting = true
    const receipt = await createComparison(session, original, controller.signal)
    if (live(g, session)) await show(receipt, original, g, session)
  } catch (failure) { if (live(g, session)) error.value = failure instanceof Error ? failure.message : '比较结果未知，请核查原操作' }
  finally {
    if (token && ownWrite === token) { ownWrite = null; user.medicalWriting = false }
    if (live(g, session)) loading.value = false
  }
}
async function recover() {
  if (busy.value || !pending.value || !props.active || !availability.value?.features.document_comparison) return
  const original = pending.value, g = generation, session = props.session
  loading.value = true; error.value = ''
  try {
    const recovered = await recoverComparison(session, original, controller.signal)
    if (!live(g, session)) return
    recoveryState.value = recovered.state
    if (recovered.comparison) await show(recovered.comparison, original, g, session)
  } catch (failure) { if (live(g, session)) error.value = failure instanceof Error ? failure.message : '原操作核查失败' }
  finally { if (live(g, session)) loading.value = false }
}
function finish() {
  if (busy.value || !result.value || !pending.value || recoveryState.value !== 'COMPLETED') return
  forgetComparison(props.session, pending.value); pending.value = null; result.value = null; recoveryState.value = ''; left.value = ''; right.value = ''
}
watch(() => user.medicalWriting, value => { if (!value) ownWrite = null }, { flush: 'sync' })
watch([category, status], () => { page.value = 1 })
watch(() => [props.session, canonical(props.session.current_context), props.session.user.user_id, canonical(props.session.action_summary), props.snapshot, props.snapshot.document_id, props.snapshot.document_revision, props.snapshot.revision, props.active], reset, { immediate: true })
function visibility() { if (document.hidden) clear(); else void reset() }
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { alive = false; clear(); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.saved-comparison { border: 1px solid var(--el-border-color); border-radius: 12px; padding: 20px; margin-block: 16px; background: var(--el-bg-color); color: var(--el-text-color-primary); }
header p, .saved-comparison > p { color: var(--el-text-color-secondary); line-height: 1.7; }
.selection, .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-block: 16px; }
.selection span { overflow-wrap: anywhere; font-size: 12px; }
.filters select { padding: 7px; margin-left: 8px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); color: inherit; }
.difference { border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 16px; margin-block: 12px; }
.sides { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
dl { margin-block: 8px; } dt { color: var(--el-text-color-secondary); font-size: 12px; } dd { margin: 4px 0 12px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; }
details { margin-block: 12px; } summary { cursor: pointer; color: var(--el-color-primary); } .error { color: var(--el-color-danger) !important; }
@media (max-width: 720px) { .sides { grid-template-columns: 1fr; } .saved-comparison { padding: 12px; } }
</style>
