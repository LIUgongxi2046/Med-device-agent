<template>
  <section class="field-verification" aria-label="严格字段核验工作区">
    <header class="fv-heading"><div><span class="fv-eyebrow">N03 · 严格字段</span><h3>字段核验工作区</h3><p>先看原值与依据，再区分提议、正文值与正式确认。</p></div><el-tag type="info">专业结果未提供</el-tag></header>
    <el-alert title="当前支持只读核对与本地意见准备，不产生医疗事实确认" description="正文编辑、PROPOSED 提议及来源引用均不代表核验通过。尚未接入 N03 专业结果与 DC-04/05 确认契约，确认、不适用及补证提交均不可执行。" type="warning" :closable="false" show-icon />
    <p v-if="preparation && preparation.template_version !== body.template_version" role="alert">模板版本不匹配：已排除该提议清单与来源，未合并到当前正文。</p>
    <div class="fv-actions"><el-button :disabled="!mappingDestination" @click="requestNavigation({ kind: 'mapping' }, $event)">打开当前任务字段目标准备</el-button><small v-if="!mappingDestination">当前任务身份或读取权限不可用。</small></div>
    <aside v-if="pendingNavigation" ref="navigationConfirmation" tabindex="-1" @keydown.esc.prevent.stop="cancelNavigation" role="alert" aria-label="确认离开本地意见" class="fv-reasons"><p>此操作可能离开当前字段。本地处理意见尚未保存，继续会清空草稿。</p><button type="button" class="fv-draft-button" @click="confirmNavigation">丢弃本地意见并继续</button> <button type="button" class="fv-draft-button secondary" @click="cancelNavigation">保留意见，暂不跳转</button></aside>
    <div class="fv-metrics"><span>当前正文 <strong>{{ body.fields.length }}</strong></span><span>冲突引用 <strong>{{ rows.filter(r => r.state === 'CONFLICT').length }}</strong></span><span>待补内容 <strong>{{ rows.filter(r => r.state === 'MISSING').length }}</strong></span><span>正式确认 <strong>未知</strong></span></div>
    <p v-if="!preparation" class="fv-muted">未提供完整固定模板清单，以下仅展示当前正文已有字段；不能据此判断必填覆盖完整。</p>
    <div class="fv-toolbar"><el-input v-model="query" aria-label="搜索字段" placeholder="搜索字段、原值、型号或条件" clearable /><el-select v-model="filter" aria-label="按核对状态筛选"><el-option label="全部核对状态" value="ALL" /><el-option v-for="(label, value) in verificationLabels" :key="value" :label="label" :value="value" /></el-select></div>
    <div v-if="rows.length" class="fv-layout">
      <nav class="fv-list" aria-label="字段清单"><p class="fv-muted">显示 {{ visible.length }} / {{ rows.length }} 项</p><el-empty v-if="!visible.length" description="没有匹配字段，请调整搜索或筛选" :image-size="50" /><button v-for="row in visible" :key="row.key" type="button" :class="['fv-item', { active: selected?.key === row.key }]" :aria-pressed="selected?.key === row.key" @click="select(row)"><span class="fv-item-title">{{ row.name }}</span><span :class="['fv-state', row.state.toLowerCase()]">{{ verificationLabels[row.state] }}</span><span class="fv-raw-preview">{{ row.field?.original_value || '尚无正文原值' }}</span><small>{{ row.field?.model ?? '型号未提供' }} · {{ row.field?.condition ?? '条件未提供' }}</small></button></nav>
      <article v-if="selected" class="fv-detail" aria-label="所选字段详情">
        <header class="fv-heading"><h4>{{ selected.name }}</h4><el-tag :type="selected.state === 'CONFLICT' ? 'danger' : 'warning'">{{ verificationLabels[selected.state] }}</el-tag></header>
        <ul v-if="selected.reasons.length" class="fv-reasons"><li v-for="reason in selected.reasons" :key="reason">{{ reason }}</li></ul>
        <section><h4>当前正文值 <span class="fv-muted">不是已确认事实</span></h4><div class="fv-value">{{ selected.field?.original_value ?? '未提供' }}</div><dl class="fv-values"><dt>精确数值</dt><dd>{{ display(selected.field?.decimal_value) }}</dd><dt>单位</dt><dd>{{ display(selected.field?.unit) }}</dd><dt>适用型号</dt><dd>{{ display(selected.field?.model) }}</dd><dt>适用条件</dt><dd>{{ display(selected.field?.condition) }}</dd></dl><p class="fv-muted">保留原始字符串，不进行单位换算、数字舍入或型号合并。</p></section>
        <section><h4>来源与证据 <span class="fv-muted">{{ selected.evidence.length }} 条已关联引用</span></h4><p v-if="!selected.evidence.length" class="fv-muted">没有可展示的字段证据。正文值不作为自身的权威依据。</p><div v-for="link in selected.evidence" :key="link.evidence_link_id" class="fv-evidence"><div class="fv-heading"><el-tag :type="link.relation === 'CONFLICT' ? 'danger' : 'info'">{{ relationLabels[link.relation] }}</el-tag><el-button :disabled="!canOpenEvidence" size="small" @click="requestNavigation({ kind: 'evidence', link }, $event)">阅读固定原文</el-button></div><dl><dt>来源版本</dt><dd>{{ link.evidence.source_version }}</dd><dt>解析版本</dt><dd>{{ link.evidence.parse_version }}</dd><dt>位置类型</dt><dd>{{ link.evidence.locator_kind }}</dd></dl><details><summary>固定位置与引用摘要</summary><pre>{{ JSON.stringify(link.evidence.locator, null, 2) }}</pre><p>{{ link.evidence.quote_hash }}</p></details></div><p v-if="selected.missingEvidenceIds.length">不可用引用：{{ selected.missingEvidenceIds.join('、') }}</p></section>
        <section><h4>提议映射 <el-tag size="small" type="info">PROPOSED</el-tag></h4><p class="fv-muted">按字段定义与节点展示相关映射，不自动认定为当前重复行的核验结果。</p><p v-if="!selected.mappings.length">未提供相关提议映射。</p><div v-for="entry in selected.mappings" :key="entry.entry_key" class="fv-evidence"><el-button size="small" :disabled="!exactDestination(entry)" @click="requestNavigation({ kind: 'source', entry }, $event)">打开来源字段工作台</el-button><p class="fv-muted">进入现有来源权限核查流程；不代表已读取此固定字段修订或核验通过。</p><p>原值：{{ source(entry)?.raw_value ?? '当前可读来源中未找到固定原值' }}</p><dl><dt>提议映射键</dt><dd>{{ entry.entry_key }}</dd><dt>实际行身份</dt><dd>{{ entry.row_id ?? '未提供' }}</dd><dt>型号上下文</dt><dd>{{ entry.model_context }}</dd><dt>条件与适用性</dt><dd>{{ entry.condition_context }} / {{ entry.applicability }}</dd><dt>来源版本</dt><dd>{{ entry.source_exact?.source_version ?? '未提供' }}</dd><dt>字段修订</dt><dd>{{ entry.source_exact?.field_revision ?? '未提供' }}</dd></dl></div></section>
        <section class="fv-local-draft" aria-label="字段处理意向草稿">
          <h4>准备处理意见 <el-tag size="small" type="info">仅本地草稿</el-tag></h4>
          <p class="fv-muted">可先填写处理意向和理由，不修改正文、提议状态或正式决定。切换字段、依据更新或离开页面后草稿清空，不会保存到服务器。</p>
          <button v-if="!draftOpen" ref="draftTrigger" type="button" class="fv-draft-button" :disabled="!selected.field && !selected.mappings.length" @click="beginDraft">填写处理意见</button>
          <p v-if="!selected.field && !selected.mappings.length" class="fv-muted">尚无实际字段或当前提议映射，不能为尚未发生的字段制作处理意见。</p>
          <form v-if="draftOpen" @submit.prevent="previewDraft" @keydown.esc.prevent.stop="cancelDraft">
            <label>处理对象<select ref="draftFirstInput" v-model="draft.target"><option value="" disabled>请选择实际对象</option><option v-if="selected.field" :value="selected.field.field_id">当前正文字段 · {{ selected.field.field_id }}</option><option v-for="entry in selected.mappings" :key="entry.entry_key" :value="entry.entry_key">PROPOSED 提议映射 · {{ entry.entry_key }}</option></select></label>
            <label>处理意向<select v-model="draft.intent"><option value="" disabled>请选择处理意向</option><option v-for="(label, intent) in handlingIntentLabels" :key="intent" :value="intent">{{ label }}</option></select></label>
            <label>处理理由（必填，最多 2000 字）<textarea v-model="draft.reason" rows="3" maxlength="2000" placeholder="说明准备如何处理、需要核查什么，不将尚未确认的内容写成结论。" /></label>
            <label v-if="draft.intent === 'NOT_APPLICABLE'">适用条件与人工依据说明（必填）<textarea v-model="draft.applicabilityBasis" rows="3" maxlength="2000" placeholder="说明拟不适用的条件、依据及待核查事项。有效条件、规则与批准状态仍未知。" /></label>
            <label>补充备注（选填，最多 4000 字）<textarea v-model="draft.notes" rows="3" maxlength="4000" /></label>
            <ul v-if="draftAttempted && draftIssues.length" role="alert" class="fv-reasons"><li v-for="issue in draftIssues" :key="issue">{{ issue }}</li></ul>
            <div class="fv-actions"><button class="fv-draft-button" type="submit">预览本地意见</button><button class="fv-draft-button secondary" type="button" @click="resetDraft">重填</button><button class="fv-draft-button secondary" type="button" @click="cancelDraft">取消（Esc）</button></div>
            <aside v-if="draftPreview" class="fv-draft-preview" aria-label="本地处理意见预览" aria-live="polite"><h4>处理意见预览 · 未提交</h4><dl><dt>对象身份</dt><dd>{{ draft.target }}</dd><dt>处理意向</dt><dd>{{ draft.intent ? handlingIntentLabels[draft.intent] : '未选择' }}</dd><dt>处理理由</dt><dd>{{ draft.reason }}</dd><dt v-if="draft.intent === 'NOT_APPLICABLE'">拟不适用依据</dt><dd v-if="draft.intent === 'NOT_APPLICABLE'">{{ draft.applicabilityBasis }}</dd><dt>补充备注</dt><dd>{{ draft.notes || '未填写' }}</dd><dt>固定模板版本</dt><dd>{{ body.template_version }}</dd><dt>正文修订</dt><dd>未知，当前输入未提供正式修订身份</dd><dt>关联来源版本</dt><dd>{{ selected.evidence.map(link => link.evidence.source_version).join('、') || '未知，未提供证据来源' }}</dd><dt>提议固定来源</dt><dd>{{ selected.mappings.find(entry => entry.entry_key === draft.target)?.source_exact?.source_version ?? '未选择带固定来源的提议映射' }}</dd><dt>专业结果／规则／批准</dt><dd>均未知，未提供真实结果与有效批准依据</dd></dl><p>这只是本地输入检查与预览，不代表来源权威性、专业核验通过、事实确认或不适用批准。</p><button type="button" disabled class="fv-draft-button">正式提交不可用</button></aside>
          </form>
        </section>
        <section class="fv-decision"><h4>正式核验与人工决定</h4><dl><dt>N03 专业比对结果</dt><dd>未知，未提供真实专业结果</dd><dt>共享事实确认</dt><dd>未知，正文值与事实引用不能代替确认记录</dd><dt>不适用决定</dt><dd>未提供有效条件与人工决定依据</dd></dl><div class="fv-actions"><el-button disabled>确认字段事实</el-button><el-button disabled>标记不适用</el-button><el-button disabled>提交补证请求</el-button></div><small>待接入真实结果、当前权限、固定版本与有效人工依据后开放。</small></section>
        <details><summary>字段与节点身份</summary><dl><dt>字段</dt><dd>{{ selected.field?.field_id ?? '正文未发生' }}</dd><dt>字段定义</dt><dd>{{ selected.field?.field_definition_id ?? selected.definition?.field_definition_id }}</dd><dt>节点</dt><dd>{{ selected.field?.node_id ?? selected.definition?.node_id }}</dd></dl></details>
      </article>
    </div>
    <el-empty v-else description="当前正文没有结构化字段；不代表必填覆盖或专业核验通过" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, nextTick, onScopeDispose } from 'vue'
import type { DocumentBody, EvidenceLink } from '../../../../contracts/typescript/src/interfaces'
import type { MappingEntry, Preparation } from '@/api/medical/task-field-target-types'
import { verificationRows, filterVerificationRows, verificationLabels, sourceForMapping, type VerificationState, type VerificationRow, handlingIntentLabels, emptyFieldHandlingDraft, handlingDraftIssues, fieldTargetsDestination, sourceExactDestination, hasFieldHandlingEdits, type VerificationNavigationContext } from '@/api/medical/document-field-verification'
const props = defineProps<{ body: DocumentBody; preparation?: Preparation | null; canOpenEvidence?: boolean; navigationContext?: VerificationNavigationContext | null }>()
const emit = defineEmits<{ 'select-field': [fieldId: string]; 'open-evidence': [link: EvidenceLink]; 'open-field-targets': [destination: NonNullable<ReturnType<typeof fieldTargetsDestination>>]; 'open-source-exact': [destination: NonNullable<ReturnType<typeof sourceExactDestination>>, exact: NonNullable<MappingEntry['source_exact']>] }>()
const query = ref(''), filter = ref<VerificationState | 'ALL'>('ALL'), selectedKey = ref('')
const rows = computed(() => verificationRows(props.body, props.preparation))
const visible = computed(() => filterVerificationRows(rows.value, query.value, filter.value))
const selected = computed(() => visible.value.find(row => row.key === selectedKey.value) ?? visible.value[0] ?? null)
const draft = ref(emptyFieldHandlingDraft()), draftOpen = ref(false), draftAttempted = ref(false), draftPreview = ref(false)
const draftTrigger = ref<HTMLButtonElement | null>(null), draftFirstInput = ref<HTMLSelectElement | null>(null)
const draftIssues = computed(() => handlingDraftIssues(draft.value, selected.value))
function resetDraft() {
  draft.value = emptyFieldHandlingDraft(); draft.value.target = selected.value?.field?.field_id ?? selected.value?.mappings[0]?.entry_key ?? ''
  draftAttempted.value = false; draftPreview.value = false
}
function beginDraft() { resetDraft(); draftOpen.value = true; void nextTick(() => draftFirstInput.value?.focus()) }
function cancelDraft() { draftOpen.value = false; resetDraft(); void nextTick(() => draftTrigger.value?.focus()) }
function previewDraft() { draftAttempted.value = true; draftPreview.value = draftIssues.value.length === 0 }
watch(() => draft.value.target, () => { draft.value.intent = ''; draft.value.reason = ''; draft.value.notes = ''; draft.value.applicabilityBasis = ''; draftAttempted.value = false; draftPreview.value = false }, { flush: 'sync' })
watch(draft, () => { draftPreview.value = false }, { deep: true, flush: 'sync' })
watch([() => selected.value?.key, () => props.body, () => props.preparation], () => { draftOpen.value = false; resetDraft() }, { deep: true, flush: 'sync' })
type NavigationRequest = { kind: 'mapping' } | { kind: 'evidence'; link: EvidenceLink } | { kind: 'source'; entry: MappingEntry }
const pendingNavigation = ref<NavigationRequest | null>(null)
const navigationConfirmation = ref<HTMLElement | null>(null)
let navigationTrigger: HTMLElement | null = null, navigationFocusGeneration = 0
function clearNavigation() { navigationFocusGeneration++; pendingNavigation.value = null; navigationTrigger = null }
function cancelNavigation() {
  const trigger = navigationTrigger
  clearNavigation()
  const generation = navigationFocusGeneration
  void nextTick(() => {
    if (generation === navigationFocusGeneration && trigger?.isConnected && !trigger.hasAttribute('disabled')) trigger.focus()
  })
}
onScopeDispose(clearNavigation)
const mappingDestination = computed(() => fieldTargetsDestination(props.navigationContext))
const exactDestination = (entry: MappingEntry) => sourceExactDestination(entry, props.body, props.preparation, props.navigationContext)
function navigationAllowed(request: NavigationRequest) {
  return request.kind === 'mapping' ? Boolean(mappingDestination.value) : request.kind === 'source' ? Boolean(exactDestination(request.entry))
    : Boolean(props.canOpenEvidence && selected.value?.evidence.includes(request.link))
}
function requestNavigation(request: NavigationRequest, event?: MouseEvent) {
  if (!navigationAllowed(request)) return
  if (draftOpen.value && hasFieldHandlingEdits(draft.value)) {
    navigationTrigger = event?.currentTarget as HTMLElement | null ?? null
    pendingNavigation.value = request
    const generation = ++navigationFocusGeneration
    void nextTick(() => { if (generation === navigationFocusGeneration && pendingNavigation.value) navigationConfirmation.value?.focus() })
    return
  }
  navigate(request)
}
function navigate(request: NavigationRequest) {
  if (!navigationAllowed(request)) return
  if (request.kind === 'mapping') emit('open-field-targets', mappingDestination.value!)
  else if (request.kind === 'evidence') emit('open-evidence', request.link)
  else emit('open-source-exact', exactDestination(request.entry)!, request.entry.source_exact!)
}
function confirmNavigation() {
  const request = pendingNavigation.value
  clearNavigation()
  if (!request || !navigationAllowed(request)) return
  cancelDraft(); navigate(request)
}
watch([() => props.body, () => props.preparation, () => props.navigationContext, () => props.canOpenEvidence, () => selected.value?.key], clearNavigation, { deep: true, flush: 'sync' })
defineExpose({ hasLocalDraft: () => draftOpen.value && hasFieldHandlingEdits(draft.value) })
const relationLabels = { SUPPORT: '支持引用（未核验）', CONFLICT: '冲突引用', BACKGROUND: '背景引用' }
const display = (value: string | null | undefined) => value === null || value === undefined ? '未提供（不推断为不适用）' : value === '' ? '空字符串' : value
const source = (entry: MappingEntry) => sourceForMapping(entry, props.preparation?.source_choices ?? [])
function select(row: VerificationRow) { selectedKey.value = row.key; if (row.field) emit('select-field', row.field.field_id) }
watch(() => props.body.template_version, () => { selectedKey.value = ''; query.value = ''; filter.value = 'ALL' })
</script>
<style scoped>
.field-verification{color:#23354a;line-height:1.6}.fv-heading{display:flex;justify-content:space-between;align-items:center;gap:14px}.fv-heading h3,.fv-heading h4{margin:5px 0}.fv-heading p{margin:0 0 15px}.fv-eyebrow{font-size:12px;letter-spacing:1px;color:#496a96}.fv-muted,small{color:#65768b;font-size:12px;font-weight:400}.fv-metrics{display:flex;flex-wrap:wrap;gap:14px;padding:18px 0}.fv-metrics span{padding:8px 14px;background:#f2f5fa;border-radius:6px}.fv-metrics strong{margin-left:12px}.fv-toolbar{display:grid;grid-template-columns:minmax(150px,1fr) 200px;gap:12px;margin:10px 0 18px}.fv-layout{display:grid;grid-template-columns:280px minmax(0,1fr);border:1px solid #dce4ef;border-radius:8px;overflow:hidden}.fv-list{background:#f7f9fc;padding:14px;border-right:1px solid #dce4ef}.fv-item{width:100%;display:flex;flex-direction:column;align-items:flex-start;text-align:left;gap:6px;margin:8px 0;padding:14px;border:1px solid #dce4ef;border-radius:6px;background:white;color:inherit;cursor:pointer;overflow-wrap:anywhere}.fv-item.active{border-color:#4778c4;box-shadow:inset 3px 0 #4778c4;background:#eff5ff}.fv-item:focus-visible{outline:3px solid #87aff0;outline-offset:2px}.fv-item-title{font-weight:650}.fv-state{font-size:12px;padding:1px 6px;border-radius:4px;background:#edf0f5}.fv-state.conflict{background:#fff0f0;color:#ac3434}.fv-state.missing{background:#fff4df;color:#946418}.fv-raw-preview{white-space:pre-wrap;max-width:100%;overflow-wrap:anywhere}.fv-detail{padding:22px;min-width:0}.fv-detail section{border-bottom:1px solid #e3e9f1;padding:8px 0 20px;margin-bottom:16px}.fv-value{white-space:pre-wrap;background:#f5f7fb;padding:14px;border-radius:5px;overflow-wrap:anywhere;font-family:monospace;font-size:15px}.fv-values,dl{display:grid;grid-template-columns:125px minmax(0,1fr);gap:7px 12px;font-size:13px}dt{color:#65768b}dd{margin:0;overflow-wrap:anywhere;white-space:pre-wrap}.fv-reasons{padding:12px 12px 12px 30px;background:#fff8eb;color:#805b22;border-radius:5px;font-size:13px}.fv-evidence{padding:14px;margin:10px 0;background:#f7f9fc;border:1px solid #e2e8f1;border-radius:5px}.fv-decision{background:#f7f9fc;padding:16px!important;border-radius:6px}.fv-actions{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:10px}.fv-actions .el-button{margin:0}pre{white-space:pre-wrap;overflow-wrap:anywhere}details{font-size:12px;overflow-wrap:anywhere}summary{cursor:pointer;color:#40648f}@media(max-width:760px){.fv-layout{grid-template-columns:1fr}.fv-list{border-right:0;border-bottom:1px solid #dce4ef;max-height:350px;overflow:auto}.fv-toolbar{grid-template-columns:1fr}.fv-heading{align-items:flex-start;flex-wrap:wrap}.fv-detail{padding:14px}}
.fv-local-draft{background:#f5f8fe;padding:16px!important;border:1px solid #d8e5f7;border-radius:6px}.fv-local-draft label{display:flex;flex-direction:column;gap:6px;margin:14px 0;font-size:13px}.fv-local-draft select,.fv-local-draft textarea{box-sizing:border-box;width:100%;padding:9px;border:1px solid #b7c6db;border-radius:5px;font:inherit;color:inherit;background:white}.fv-local-draft :focus-visible{outline:3px solid #8cb5ed;outline-offset:2px}.fv-draft-button{padding:9px 14px;border:1px solid #356cb7;background:#356cb7;color:white;border-radius:5px;cursor:pointer;font:inherit;font-size:13px}.fv-draft-button.secondary{background:white;color:#365b8e}.fv-draft-button:disabled{opacity:.55;cursor:not-allowed;background:#e8edf4;color:#68758a;border-color:#b9c4d3}.fv-draft-preview{padding:16px;border:1px dashed #a6bcd9;background:white;border-radius:6px}.fv-draft-preview p{font-size:12px;color:#7b5b2b}
.fv-reasons:focus-visible,.fv-draft-button:focus-visible{outline:3px solid #8cb5ed;outline-offset:2px}
</style>
