<template>
  <section class="chapter-candidates" aria-label="章节候选比较" data-chapter-candidates>
    <header><div><small>章节工作区 / 候选审阅</small><h3>比较后，再决定采用范围</h3><p>候选不会直接覆盖正文。选择只保留在本页，不代表已采用、专业核验或审查通过。</p></div><span class="mode">只读比较</span></header>
    <p v-if="!active" role="status">当前工作区未激活；候选内容已隐藏。</p>
    <template v-else>
      <p v-if="candidates === undefined" class="empty" role="status">章节候选清单尚未接入或未提供。当前不能推断有无候选，也不会以模拟结果代替。</p>
      <p v-else-if="!candidates.length" class="empty" role="status">当前提供的章节候选清单为空。</p>
      <div v-else class="layout">
        <nav aria-label="章节候选列表"><h4>候选列表 · {{ candidates.length }}</h4><button v-for="(item, index) in candidates" :key="candidateKey(item.body_object_ref)" type="button" :aria-pressed="selectedKey === candidateKey(item.body_object_ref)" @click="choose(item)"><strong>候选 {{ index + 1 }}</strong><span>{{ item.target_sections.join('、') }}</span><small>{{ item.base_document_revision === currentRevision ? '基准与当前版本相同' : currentRevision ? '正文版本已变化' : '当前版本未知' }}</small></button></nav>
        <article v-if="candidate" class="preview">
          <div class="revision"><span>候选基准：{{ candidate.base_document_revision }}</span><span>当前正文：{{ currentRevision || '未知' }}</span></div>
          <p>直接影响章节：{{ candidate.target_sections.join('、') }}。依赖章节影响尚未提供，不能推断无影响。</p>
          <section aria-label="选择候选节点"><h4>选择明确的节点 <small>{{ selectedIds.length }} 个已选</small></h4><p>只选择勾选的节点，不自动展开为整章、子节点或全文。</p><div class="actions"><button type="button" @click="setSelection(selectableNodes(candidate))">选择全部可选节点</button><button type="button" @click="setSelection([])">清除选择</button></div><label v-for="node in candidate.node_manifest" :key="node.node_id" class="node-choice"><input type="checkbox" :checked="selectedIds.includes(node.node_id)" :disabled="!selectableNodes(candidate).includes(node.node_id)" @change="toggleNode(node.node_id, ($event.target as HTMLInputElement).checked)"><span>{{ node.kind }} · {{ node.node_id }}<small>章节 {{ node.section_id }}</small></span></label></section>
          <section aria-label="所选节点修改前后"><h4>当前内容与拟采用内容</h4><p v-if="!selectedIds.length">勾选节点后逐项比较。</p><div v-for="id in selectedIds" :key="id" class="node-comparison"><h5>{{ id }}</h5><div class="compare-columns"><section><strong>当前已保存内容</strong><pre>{{ currentBody ? nodeText(currentBody.nodes.find(n => n.node_id === id)) : '当前完整正文未提供。' }}</pre></section><section><strong>拟采用内容</strong><pre>{{ proposed ? nodeText(proposed.nodes.find(n => n.node_id === id)) : '候选完整正文尚未提供，仅有节点清单不能还原正文。' }}</pre></section></div></div></section>
          <section aria-label="引用变化"><h4>所选节点的引用变化</h4><p v-if="!references">缺少完整正文，引用变化尚不能比较。候选声明不等同于已经核准的正文引用。</p><template v-else><p>新增 {{ references.added.length }} · 移除 {{ references.removed.length }} · 同标识内容变化 {{ references.changed.length }}</p><div class="compare-columns"><details><summary>当前引用（{{ references.before.length }}）</summary><pre>{{ JSON.stringify(references.before, null, 2) }}</pre></details><details><summary>拟采用引用（{{ references.after.length }}）</summary><pre>{{ JSON.stringify(references.after, null, 2) }}</pre></details></div></template></section>
          <section aria-label="完整候选预览"><h4>完整候选预览</h4><p>以下内容由调用方提供；对象摘要是候选元数据，不代表本界面已完成字节校验、来源核准或专业检查。</p><p v-if="!proposed">候选正文对象尚未载入。已提供元数据、节点、声明和问题可在下方查看；不会用标题或摘要冒充全文。</p><template v-else><article v-for="node in proposed.nodes" :key="node.node_id" class="full-node"><small>{{ node.content.kind }} · {{ node.node_id }}</small><pre>{{ nodeText(node) }}</pre></article><details><summary>完整结构化正文（含字段和引用）</summary><pre>{{ JSON.stringify(proposed, null, 2) }}</pre></details></template><details><summary>全部候选元数据、声明与待解决问题（{{ candidate.issue_proposals.length }}）</summary><pre>{{ JSON.stringify(candidate, null, 2) }}</pre></details></section>
        </article><p v-else class="empty">从左侧选择候选，查看完整内容与节点差异。</p>
      </div>
    </template>
    <footer><h4>采用前需要完成</h4>
      <button ref="reviewTrigger" type="button" :disabled="!active || !candidate || !selectedIds.length" @click="openReview">下一步：核对采用范围（本地预览）</button>
      <section v-if="reviewOpen && reviewSummary" ref="reviewPanel" tabindex="-1" class="adoption-review" aria-label="采用范围本地核对">
        <h4>核对采用范围 · 未提交</h4><p>这是提交前的本地预览，不是业务申请、人工审查或采用记录。所有服务端阻塞仍然有效。</p>
        <dl><dt>候选对象键</dt><dd>{{ reviewSummary.body_object_ref.object_key }}</dd><dt>对象版本</dt><dd>{{ reviewSummary.body_object_ref.object_version }}</dd><dt>候选对象摘要（尚未校验）</dt><dd>{{ reviewSummary.body_object_ref.sha256 }}</dd><dt>候选基准正文版本</dt><dd>{{ reviewSummary.base_document_revision }}</dd><dt>当前正文版本</dt><dd>{{ reviewSummary.current_document_revision || '未知，需要重新读取' }}</dd></dl>
        <h5>准确选择的节点 · {{ reviewSummary.selected_node_ids.length }}</h5><ul><li v-for="id in reviewSummary.selected_node_ids" :key="id">{{ id }}</li></ul><p>不自动包括子节点。直接章节：{{ reviewSummary.direct_section_ids.join('、') }}；依赖章节影响未知。</p>
        <h5>所选范围的引用变化</h5><p v-if="reviewSummary.reference_changes">新增 {{ reviewSummary.reference_changes.added.length }} · 移除 {{ reviewSummary.reference_changes.removed.length }} · 同标识变化 {{ reviewSummary.reference_changes.changed.length }}</p><p v-else>缺少完整正文，引用变化未知。</p>
        <details><summary>查看准确对象及引用变化明细</summary><pre>{{ JSON.stringify(reviewSummary, null, 2) }}</pre></details>
        <h5>当前仍未满足的条件</h5><ul><li v-for="reason in blockers" :key="reason">{{ reason }}</li></ul>
        <label class="review-reason">本地采用理由（不发送、不保存）<textarea v-model="reviewReason" rows="3" maxlength="2000" :readonly="reviewConfirmed" placeholder="说明为何选择这些节点，仅用于本次本地核对" /></label>
        <label class="node-choice"><input v-model="reviewAcknowledged" type="checkbox" :disabled="reviewConfirmed"><span>我已核对上述范围，并知悉这只是本地预览，不能提交采用或替代缺失的服务端核验。</span></label>
        <div class="actions"><button type="button" :disabled="!reviewReason.trim() || !reviewAcknowledged || reviewConfirmed" @click="confirmReview">确认本地预览</button><button v-if="reviewConfirmed" type="button" @click="reviewConfirmed = false">继续修改理由</button><button type="button" @click="cancelReview">{{ reviewConfirmed ? '关闭预览并清除本地记录' : '取消预览' }}</button></div>
        <p v-if="reviewConfirmed" role="status">本地预览已确认，尚未提交、未保存、未采用。下方正式采用按钮仍不可用。</p>
      </section><ul aria-live="polite"><li v-for="reason in blockers" :key="reason">{{ reason }}</li></ul><button type="button" disabled aria-describedby="chapter-adopt-unavailable">采用所选节点（未接入）</button><p id="chapter-adopt-unavailable">此操作不可用，不会生成采用记录或触发保存。</p></footer>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { SectionCandidate } from '../../../../contracts/typescript/src/artifacts'
import type { DocumentBody } from '../../../../contracts/typescript/src/interfaces'
import type { CandidateBody, CandidateSelection } from '@/api/medical/document-chapter-candidates'
import { candidateKey, candidateBody, selectableNodes, exactSelection, nodeText, referenceChanges, candidateBlockers, adoptionPreview } from '@/api/medical/document-chapter-candidates'
const props = withDefaults(defineProps<{ candidates?: SectionCandidate[] | undefined; currentBody?: DocumentBody | null | undefined; currentRevision?: string | null | undefined; active?: boolean | undefined; dirty?: boolean | undefined; adoptPermission?: boolean | null | undefined; candidateBodies?: CandidateBody[] | undefined }>(), { currentBody: null, currentRevision: null, active: true, dirty: false, adoptPermission: null, candidateBodies: () => [] })
const emit = defineEmits<{ selectionChanged: [selection: CandidateSelection | null] }>()
const selectedKey = ref(''), selectedIds = ref<string[]>([])
const reviewOpen = ref(false), reviewReason = ref(''), reviewAcknowledged = ref(false), reviewConfirmed = ref(false)
const reviewPanel = ref<HTMLElement | null>(null), reviewTrigger = ref<HTMLButtonElement | null>(null)
const reviewSummary = ref<ReturnType<typeof adoptionPreview> | null>(null)
function clearReview(): void { reviewOpen.value = false; reviewReason.value = ''; reviewAcknowledged.value = false; reviewConfirmed.value = false; reviewSummary.value = null }
function openReview(): void {
  if (!props.active || !candidate.value || !selectedIds.value.length) return
  clearReview()
  reviewSummary.value = adoptionPreview(candidate.value, selectedIds.value, props.currentRevision, props.currentBody, proposed.value)
  reviewOpen.value = true
  void nextTick(() => reviewPanel.value?.focus())
}
function confirmReview(): void { if (reviewOpen.value && reviewSummary.value && reviewReason.value.trim() && reviewAcknowledged.value) reviewConfirmed.value = true }
function cancelReview(): void { clearReview(); void nextTick(() => reviewTrigger.value?.focus()) }
const candidate = computed(() => props.active ? props.candidates?.find(item => candidateKey(item.body_object_ref) === selectedKey.value) ?? null : null)
const proposed = computed(() => candidateBody(candidate.value, props.candidateBodies))
const references = computed(() => referenceChanges(props.currentBody, proposed.value, selectedIds.value))
const blockers = computed(() => candidateBlockers({candidate:candidate.value,currentRevision:props.currentRevision,body:proposed.value,active:props.active,dirty:props.dirty,permission:props.adoptPermission,selected:selectedIds.value}))
function setSelection(ids: string[]): void { clearReview(); selectedIds.value = candidate.value ? exactSelection(candidate.value, ids) : []; emit('selectionChanged', candidate.value ? {body_object_ref: {...candidate.value.body_object_ref}, node_ids:[...selectedIds.value]} : null) }
function choose(item: SectionCandidate): void { selectedKey.value = candidateKey(item.body_object_ref); setSelection([]) }
function toggleNode(id: string, checked: boolean): void { setSelection(checked ? [...selectedIds.value, id] : selectedIds.value.filter(value => value !== id)) }
watch(() => [props.active, props.currentRevision, props.candidates], () => { clearReview(); selectedKey.value = ''; selectedIds.value = []; emit('selectionChanged', null) }, {deep:true})
watch(() => [props.dirty, props.adoptPermission, props.currentBody, props.candidateBodies], clearReview, {deep:true})
</script>
<style scoped>
.chapter-candidates{color:#243b50;border:1px solid #d8e2e9;border-radius:12px;background:#fff;margin:24px 0;overflow:hidden}header,footer{padding:22px;background:#f5f8fb}header{display:flex;justify-content:space-between;gap:16px}h3{margin:8px 0;font-size:22px}p{line-height:1.65;color:#627487;font-size:14px}.mode{align-self:flex-start;background:#e5eef9;color:#275a86;padding:6px 12px;border-radius:20px;white-space:nowrap;font-size:12px}.layout{display:grid;grid-template-columns:240px minmax(0,1fr)}nav{padding:18px;background:#f8fafc;border-right:1px solid #dde5ec}button{padding:9px 13px;background:white;color:#34536d;border:1px solid #bbcddd;border-radius:7px;cursor:pointer}nav button{display:flex;flex-direction:column;gap:8px;text-align:left;width:100%;margin:10px 0;overflow-wrap:anywhere}nav button[aria-pressed=true]{border:2px solid #326baa;background:#edf4ff}button:focus-visible,input:focus-visible,summary:focus-visible{outline:3px solid #4a8bcd;outline-offset:3px}button:disabled{cursor:not-allowed;background:#e8edf2;color:#788592}small{font-size:12px;color:#6d8193}.preview{padding:22px;min-width:0}.preview>section{padding:14px 0;border-bottom:1px solid #e5ebf0}.revision{display:flex;gap:16px;flex-wrap:wrap;font-size:12px;overflow-wrap:anywhere;background:#edf4ff;padding:12px;border-radius:7px}.actions{display:flex;gap:10px;margin-bottom:12px}.node-choice{display:flex;gap:10px;align-items:flex-start;padding:10px 0;overflow-wrap:anywhere}.node-choice small{display:block;margin-top:5px}.compare-columns{display:grid;grid-template-columns:1fr 1fr;gap:14px}.compare-columns>section,.compare-columns>details{background:#f6f8fa;padding:14px;min-width:0;border-radius:6px}.compare-columns>section+section{background:#edf5fd}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:14px/1.7 system-ui,sans-serif;max-height:420px;overflow:auto}.node-comparison h5{overflow-wrap:anywhere}.full-node{border-left:3px solid #c3d8ed;padding:8px 14px;margin:12px 0}summary{cursor:pointer;padding:10px 0;font-size:13px}footer ul{font-size:13px;line-height:1.8;color:#775729}.empty{padding:24px}footer p{font-size:12px}@media(max-width:760px){.layout,.compare-columns{grid-template-columns:1fr}nav{border-right:0;border-bottom:1px solid #dde5ec}.preview,header,footer{padding:16px}header{flex-wrap:wrap}}
.adoption-review{margin-top:18px;padding:18px;border:1px solid #bfd0e0;border-radius:8px;background:white}.adoption-review dd{margin:4px 0 12px;overflow-wrap:anywhere;font-size:13px}.adoption-review dt{font-weight:600;font-size:13px}.adoption-review li{overflow-wrap:anywhere}.review-reason{display:block;font-size:13px}.review-reason textarea{display:block;box-sizing:border-box;width:100%;margin:8px 0;padding:10px;font:inherit;border:1px solid #bacddd;border-radius:6px}.adoption-review:focus{outline:3px solid #8ab3dc}.adoption-review .actions{flex-wrap:wrap}
</style>
