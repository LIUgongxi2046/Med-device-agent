<template>
  <section aria-label="明确字段发生位置与来源映射">
    <p>逐个选择实际字段定义，再明确重复行、型号、条件及 SourceExact 原值。条目标识只是提议映射键，实际发生字段 ID 仅由服务端登记目标时生成。</p>
    <label>新增映射的实际字段定义<select v-model="selectedField" :disabled="disabled">
      <option value="">请选择字段及其实际章节／节点</option>
      <option v-for="item in preparation.inventory" :key="item.field_definition_id" :value="item.field_definition_id" :disabled="item.value_type !== 'TEXT' || item.section_id === null">{{ item.name }} · {{ item.required ? '必填' : '可选' }} · {{ item.value_type }} · {{ item.section_id ?? '尚未分配章节' }}</option>
    </select></label>
    <button type="button" :disabled="disabled || !selectedField" @click="add">为所选字段新增提议映射</button>
    <p v-if="!mappings.length">当前没有发生位置映射。可以明确保存空映射，完整字段清单仍保留且不会自动完成。</p>
    <section v-for="(entry,index) in mappings" :key="entry.entry_key" class="field-target-card" :aria-label="'映射 ' + (index+1)">
      <header><h3>{{ field(entry)?.name ?? '原字段当前不可见' }} · 发生位置 {{ index+1 }}</h3><button type="button" :disabled="disabled" @click="mappings.splice(index,1)">移除此提议映射</button></header>
      <fieldset :disabled="disabled">
        <details class="field-target-identifiers"><summary>已明确选择的实际模板与计划身份</summary><dl>
          <dt>字段定义</dt><dd>{{ entry.field_definition_id }}</dd><dt>章节</dt><dd>{{ entry.section_id }}</dd><dt>要求节点</dt><dd>{{ entry.node_id }}</dd><dt>提议映射键</dt><dd>{{ entry.entry_key }}</dd>
        </dl></details>
        <div class="field-target-grid">
          <label>实际行身份（UUID）<input :value="entry.row_id ?? ''" autocomplete="off" @input="entry.row_id = inputValue($event) || null" /><small>{{ field(entry)?.row_id_required ? '此重复字段必须明确实际行身份，不会自动生成或借用其他字段的行。' : '仅在确有实际行身份时填写；留空表示没有行身份。' }}</small></label>
          <label>实际研究问题<select v-model="entry.question_ids" multiple><option v-for="id in field(entry)?.question_ids ?? []" :key="id" :value="id">{{ id }}</option></select><small>只列出当前章节与节点的真实持久化问题。空选择不会制造问题身份。</small></label>
          <label>型号上下文<select v-model="entry.model_context"><option v-for="(label,state) in contextLabels" :key="state" :value="state">{{ label }}</option></select></label>
          <label>型号身份（每行一个 UUID）<textarea :value="entry.model_ids.join('\n')" rows="3" autocomplete="off" @input="entry.model_ids = identifiers($event)" /><small>不能根据型号显示文字推断身份。</small></label>
          <label>条件上下文<select v-model="entry.condition_context"><option v-for="(label,state) in contextLabels" :key="state" :value="state">{{ label }}</option></select></label>
          <label>条件身份（每行一个 UUID）<textarea :value="entry.condition_ids.join('\n')" rows="3" autocomplete="off" @input="entry.condition_ids = identifiers($event)" /><small>未知条件保持未明确，不能当作空条件成功。</small></label>
          <label>适用性<select v-model="entry.applicability"><option value="UNKNOWN">尚未明确</option><option value="APPLICABLE">明确适用</option></select></label>
          <label>原值来源<select :value="entry.source_exact ? sourceChoiceKey(entry.source_exact) : ''" @change="chooseSource(entry,$event)"><option value="">来源待补，不选择替代值</option><option v-for="choice in preparation.source_choices" :key="sourceChoiceKey(choice.source_exact)" :value="sourceChoiceKey(choice.source_exact)">{{ sourceKindLabels[choice.source_exact.field_kind] }} · 原序号 {{ choice.source_exact.ordinal }} · {{ choice.raw_value }}</option></select></label>
        </div>
        <template v-if="source(entry)"><p>未转换、未使用别名的原始 TEXT 值：</p><div class="field-target-raw">{{ source(entry)?.raw_value }}</div></template>
        <p v-else-if="entry.source_exact" class="field-target-warning">此原修订目前不在可读来源目录中，请核查来源权限或重新明确来源。不会从旧缓存补出原值。</p>
        <details v-if="entry.source_exact" class="field-target-identifiers"><summary>SourceExact 固定修订、摘要与原序号</summary><dl><dt>来源版本</dt><dd>{{ entry.source_exact.source_version }}</dd><dt>字段修订</dt><dd>{{ entry.source_exact.field_revision }}</dd><dt>清单摘要</dt><dd>{{ entry.source_exact.manifest_sha256 }}</dd><dt>原序号／种类</dt><dd>{{ entry.source_exact.ordinal }} / {{ entry.source_exact.field_kind }}</dd></dl></details>
      </fieldset>
    </section>
  </section>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { contextLabels, sourceKindLabels, sourceChoiceKey, detached } from '@/api/medical/task-field-target-domain'
import type { MappingEntry, Preparation } from '@/api/medical/task-field-target-domain'
const props = defineProps<{ preparation: Preparation; disabled: boolean }>()
const mappings = defineModel<MappingEntry[]>({ required: true }), selectedField = ref('')
const inputValue = (event: Event) => (event.target as HTMLInputElement).value
const identifiers = (event: Event) => inputValue(event) === '' ? [] : inputValue(event).split(/\r?\n/).map(value => value.trim())
const field = (entry: MappingEntry) => props.preparation.inventory.find(item => item.field_definition_id === entry.field_definition_id)
const source = (entry: MappingEntry) => entry.source_exact ? props.preparation.source_choices.find(item => sourceChoiceKey(item.source_exact) === sourceChoiceKey(entry.source_exact!)) : undefined
function chooseSource(entry: MappingEntry, event: Event) {
  const key = inputValue(event), choice = props.preparation.source_choices.find(item => sourceChoiceKey(item.source_exact) === key)
  entry.source_exact = choice ? detached(choice.source_exact) : null
}
function add() {
  const item = props.preparation.inventory.find(item => item.field_definition_id === selectedField.value)
  if (props.disabled || !item || item.value_type !== 'TEXT' || item.section_id === null) return
  mappings.value.push({ entry_key: crypto.randomUUID(), field_definition_id: item.field_definition_id, section_id: item.section_id, node_id: item.node_id,
    row_id: null, model_ids: [], condition_ids: [], model_context: 'UNRESOLVED', condition_context: 'UNRESOLVED', applicability: 'UNKNOWN', source_exact: null, question_ids: [] })
  selectedField.value = ''
}
</script>
