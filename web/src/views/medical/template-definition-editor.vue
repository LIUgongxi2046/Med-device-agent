<template>
  <section aria-label="结构化模板定义">
    <el-alert v-if="errors.open" :title="errors.open" type="error" :closable="false" role="alert" />
    <el-button :disabled="disabled" @click="open">编辑{{ labels[kind] }}定义</el-button>
    <el-dialog v-model="visible" :title="labels[kind] + '定义编辑'" width="90%" :close-on-click-modal="false" destroy-on-close @closed="clear">
      <p>当前修改保存在模板编辑页面。全部模板保存后由服务器核查版本、依据、许可和关系。</p>
      <el-table :data="items" :row-key="identities[kind]" empty-text="当前没有定义">
        <el-table-column :prop="identities[kind]" label="稳定标识" min-width="250" />
        <el-table-column :prop="kind === 'Node' ? 'title' : kind === 'CrossDocumentRule' ? 'severity' : 'name'" label="定义名称或等级" min-width="180" />
        <el-table-column label="操作" width="130"><template #default="{ $index }"><el-button @click="edit($index)">编辑本项定义</el-button></template></el-table-column>
      </el-table>
      <el-button :disabled="editing" @click="create">新增{{ labels[kind] }}定义</el-button>
      <p v-if="errors.schema" role="alert">{{ errors.schema }}</p>
      <el-form v-if="editing" label-position="top" @submit.prevent="confirmItem" data-template-definition-form>
        <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :error="errors[field.key]">
          <el-checkbox v-if="field.nullable" v-model="nulls[field.key]">明确设置为空值</el-checkbox>
          <el-radio-group v-if="field.type === 'boolean'" v-model="texts[field.key]" :aria-label="field.label">
            <el-radio value="true">确认需要</el-radio><el-radio value="false">确认无需</el-radio>
          </el-radio-group>
          <el-select v-else-if="field.options" v-model="texts[field.key]" :aria-label="field.label" :disabled="nulls[field.key]">
            <el-option v-for="option in field.options" :key="option" :label="optionLabels[option] ?? option" :value="option" />
          </el-select>
          <el-input v-else v-model="texts[field.key]" :aria-label="field.label" :disabled="nulls[field.key]" :type="field.type === 'json' ? 'textarea' : 'text'" :autosize="field.type === 'json' ? { minRows: 3, maxRows: 14 } : undefined" />
          <p v-if="field.description">{{ field.description }}</p>
        </el-form-item>
        <el-button native-type="submit" type="primary">确认当前定义</el-button>
        <el-button @click="cancelItem">放弃当前定义编辑</el-button>
        <template v-if="editingIndex !== null">
          <el-checkbox v-model="removeConfirmed">确认移除当前定义</el-checkbox>
          <el-button type="danger" :disabled="!removeConfirmed" @click="remove">移除当前定义</el-button>
        </template>
      </el-form>
      <template #footer><el-button @click="visible = false">放弃本次定义修改</el-button><el-button type="primary" :disabled="editing || disabled" @click="apply">采用本次定义修改</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import schema from '../../../../business/medical-persistence/src/main/resources/templates.schema.json'
import { fail, schemaIssues, validate } from '@/api/medical/protocol'
import { readBoundedJson } from '@/api/medical/project-json'
import { draftJsonIssue, templateDefinitionInputIssue } from './template-collection-input'
import type { JsonObject } from '@/api/medical/template-approval'

type DefinitionKind = 'Node' | 'Field' | 'Repeat' | 'Attachment' | 'CrossDocumentRule'
type Control = { key: string; label: string; type: 'string' | 'integer' | 'boolean' | 'json'; nullable?: boolean; options?: readonly string[]; description?: string }
const props = defineProps<{ kind: DefinitionKind; value: string; disabled: boolean }>()
const emit = defineEmits<{ changed: [value: string] }>()
const labels = { Node: '目录', Field: '字段', Repeat: '重复表格', Attachment: '附件', CrossDocumentRule: '跨文档规则' }
const identities = { Node: 'node_id', Field: 'field_id', Repeat: 'repeat_id', Attachment: 'attachment_requirement_id', CrossDocumentRule: 'rule_id' }
const optionLabels: Record<string, string> = { TEXT: '单行文本', LONG_TEXT: '多行文本', DATE: '日期', NUMBER: '精确数值', ENUM: '单项枚举',
  MULTI_SELECT: '多项选择', RELATION: '关系', TABLE: '表格', IMAGE: '图片', FORMULA: '公式', FILE: '文件',
  OFFICIAL: '规定目录', ENTERPRISE_SUPPLEMENT: '企业补充目录', DIRECT_REFERENCE: '直接引用', APPROVED_CALCULATION: '批准计算',
  NARRATIVE: '文字说明', AUTHORIZED_ORIGINAL_ONLY: '获授权原件', DIRECTORY: '目录', CRITICAL: '关键问题', MAJOR: '主要问题', MINOR: '次要问题' }
const condition: Control = { key: 'condition', label: '完整适用条件（JSON）', type: 'json', description: '使用ALWAYS或固定RULE，规则依据、引擎版本及全部输入属性按原Schema填写。UNKNOWN继续保持待确认。' }
const evidence: Control = { key: 'evidence_rule', label: '完整证据规则（JSON）', type: 'json', description: '保留权威来源类型、验证规则、计算依据及输入字段；原件定位和人工核验保持必需。' }
const basis: Control = { key: 'basis_refs', label: '全部依据版本（JSON）', type: 'json', description: '每项包含version_id及原始content_sha256，不填入产品默认值。' }
const required: Control = { key: 'required', label: '是否必需', type: 'boolean' }
const definitions: Record<DefinitionKind, Control[]> = {
  Node: [
    { key: 'node_id', label: '目录稳定标识', type: 'string' }, { key: 'parent_id', label: '父目录标识', type: 'string', nullable: true },
    { key: 'official_number', label: '原目录编号', type: 'string' }, { key: 'title', label: '目录标题', type: 'string' },
    { key: 'ordinal', label: '目录顺序', type: 'integer' }, condition, required,
    { key: 'content_mode', label: '内容方式', type: 'string', options: schema.$defs.Node.properties.content_mode.enum }, evidence, basis,
    { key: 'nature', label: '目录性质', type: 'string', options: schema.$defs.Node.properties.nature.enum },
    { key: 'content_requirements', label: '全部内容要求（JSON）', type: 'json' }
  ],
  Field: [
    { key: 'field_id', label: '字段稳定标识', type: 'string' }, { key: 'node_id', label: '所属目录标识', type: 'string' },
    { key: 'key', label: '字段English键名', type: 'string' }, { key: 'name', label: '字段名称', type: 'string' },
    { key: 'description', label: '字段说明', type: 'string' }, { key: 'value_type', label: '字段类型', type: 'string', options: schema.$defs.ValueType.enum },
    { key: 'constraints', label: '完整字段限制（JSON）', type: 'json', description: '完整填写长度、精度、最小值、最大值、单位、范围依据、枚举选项及可空理由。数值上下限使用十进制字符串。' },
    condition, required, evidence,
    { key: 'output_mapping', label: '完整输出位置（JSON）', type: 'json', description: '固定node_id、repeat_id、column_key、internal_only和ordinal，所有字段保持原身份。' },
    { key: 'repeat_id', label: '重复表格标识', type: 'string', nullable: true },
    { key: 'attachment_requirement_id', label: '附件要求标识', type: 'string', nullable: true }, basis
  ],
  Repeat: [
    { key: 'repeat_id', label: '重复表格稳定标识', type: 'string' }, { key: 'node_id', label: '所属目录标识', type: 'string' },
    { key: 'name', label: '重复表格名称', type: 'string' }, { key: 'field_ids', label: '全部列字段标识（JSON）', type: 'json' },
    { key: 'min_count', label: '最少行数', type: 'string' }, { key: 'max_count', label: '最多行数', type: 'string', nullable: true },
    { key: 'count_basis', label: '行数依据版本（JSON）', type: 'json' }, { key: 'row_id_required', label: '是否要求稳定行标识', type: 'boolean' }
  ],
  Attachment: [
    { key: 'attachment_requirement_id', label: '附件要求稳定标识', type: 'string' }, { key: 'node_id', label: '所属目录标识', type: 'string' },
    { key: 'name', label: '附件名称', type: 'string' }, { key: 'kind', label: '附件类型', type: 'string', options: schema.$defs.Attachment.properties.kind.enum },
    { key: 'mode', label: '附件来源方式', type: 'string', options: [schema.$defs.AttachmentMode.const] }, condition, required,
    { key: 'min_count', label: '最少附件数量', type: 'string' }, { key: 'max_count', label: '最多附件数量', type: 'string', nullable: true },
    { key: 'count_basis', label: '附件数量依据（JSON）', type: 'json' }, { key: 'allowed_media_types', label: '全部允许媒体类型（JSON）', type: 'json' },
    evidence, { key: 'ordinal', label: '附件顺序', type: 'integer' }
  ],
  CrossDocumentRule: [
    { key: 'rule_id', label: '跨文档规则稳定标识', type: 'string' }, { key: 'rule_revision', label: '固定规则依据（JSON）', type: 'json' },
    { key: 'field_ids', label: '全部关联字段（JSON）', type: 'json' }, { key: 'related_document_types', label: '全部关联文档类型（JSON）', type: 'json' },
    { key: 'severity', label: '问题等级', type: 'string', options: schema.$defs.CrossDocumentRule.properties.severity.enum }
  ]
}
const fields = computed(() => definitions[props.kind])
const items = ref<JsonObject[]>([]); const visible = ref(false); const editing = ref(false); const editingIndex = ref<number | null>(null)
const texts = reactive<Record<string, string>>({}); const nulls = reactive<Record<string, boolean>>({}); const errors = reactive<Record<string, string>>({})
const removeConfirmed = ref(false); let baseline = ''
const reference = computed(() => 'urn:medical:templates:1.0.0#/$defs/' + props.kind)
function checked(value: unknown): JsonObject[] {
  if (!Array.isArray(value)) fail('SCHEMA_INVALID', '模板定义需要完整数组')
  const result = value.map(item => validate<JsonObject>(reference.value, item))
  const ids = result.map(item => item[identities[props.kind]])
  if (new Set(ids).size !== ids.length) fail('SCHEMA_INVALID', '模板定义的稳定标识重复')
  return result
}
function cancelItem(): void {
  editing.value = false; editingIndex.value = null; removeConfirmed.value = false
  for (const target of [texts, nulls, errors]) for (const key of Object.keys(target)) delete target[key]
}
function clear(): void { cancelItem(); visible.value = false; items.value = []; baseline = '' }
function open(): void {
  if (props.disabled || document.hidden) return
  clear(); baseline = props.value
  const text = props.value.trim().length === 0 ? '[]' : props.value
  const syntax = draftJsonIssue(text)
  if (syntax) { errors.open = syntax; return }
  const value = readBoundedJson(text), issue = templateDefinitionInputIssue(value, props.kind, identities[props.kind])
  if (issue) { errors.open = issue; return }
  items.value = checked(value); visible.value = true
}
function edit(index: number): void {
  const value = items.value[index]
  if (!value || props.disabled) fail('ACTION_STALE', '当前模板定义已经变化')
  cancelItem(); editing.value = true; editingIndex.value = index
  for (const field of fields.value) {
    const original = value[field.key]; nulls[field.key] = original === null
    texts[field.key] = original === null ? '' : field.type === 'json' ? JSON.stringify(original, null, 2) : String(original)
  }
}
function create(): void {
  cancelItem(); editing.value = true
  for (const field of fields.value) { texts[field.key] = field.key === identities[props.kind] ? crypto.randomUUID() : ''; nulls[field.key] = false }
}
function confirmItem(): void {
  if (!editing.value || props.disabled || document.hidden) fail('ACTION_STALE', '当前模板定义无法编辑')
  for (const key of Object.keys(errors)) delete errors[key]
  const value: JsonObject = {}
  for (const field of fields.value) {
    const text = texts[field.key] ?? ''
    if (nulls[field.key]) { value[field.key] = null; continue }
    if (field.type === 'json' || field.type === 'boolean' || field.type === 'integer') {
      const issue = draftJsonIssue(text)
      if (issue) { errors[field.key] = issue; continue }
      value[field.key] = readBoundedJson(text) as JsonObject[string]
    } else value[field.key] = text
  }
  if (Object.values(errors).some(Boolean)) return
  const issues = schemaIssues(reference.value, value)
  for (const issue of issues) {
    const key = fields.value.find(field => issue.path === '/' + field.key || issue.path.startsWith('/' + field.key + '/'))?.key
    if (key) errors[key] = '请核查完整定义：' + issue.path + '（' + issue.code + '）'
  }
  if (issues.length) { errors.schema = '当前定义没有通过全部字段校验'; return }
  const next = [...items.value]
  if (editingIndex.value === null) next.push(value); else next[editingIndex.value] = value
  const issue = templateDefinitionInputIssue(next, props.kind, identities[props.kind])
  if (issue) { errors.schema = issue; return }
  items.value = checked(next); cancelItem()
}
function remove(): void {
  if (!removeConfirmed.value || editingIndex.value === null || props.disabled) fail('ACTION_STALE', '移除定义需要明确确认当前对象')
  items.value = items.value.filter((_, index) => index !== editingIndex.value); cancelItem()
}
function apply(): void {
  if (props.disabled || editing.value || baseline !== props.value || document.hidden) fail('ACTION_STALE', '原模板内容或编辑上下文已经变化')
  const issue = templateDefinitionInputIssue(items.value, props.kind, identities[props.kind])
  if (issue) { errors.schema = issue; return }
  const value = JSON.stringify(checked(items.value), null, 2); clear(); emit('changed', value)
}
watch(() => [props.value, props.disabled, props.kind], clear, { flush: 'sync' })
function visibility(): void { if (document.hidden) clear() }
document.addEventListener('visibilitychange', visibility); window.addEventListener('medical-context-invalidated', clear)
onBeforeUnmount(() => { clear(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('medical-context-invalidated', clear) })
</script>
