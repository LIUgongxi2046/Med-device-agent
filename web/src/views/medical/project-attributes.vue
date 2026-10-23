<template>
  <section aria-label="项目属性及声明依据" class="project-attributes">
    <div class="attribute-heading"><h2>产品与注册信息</h2><el-button :disabled="disabled || rows.length >= 1000" @click="add()">添加其他属性</el-button></div>
    <p>未知属性保留“尚未确认”和未知原因。已有声明需要原始值及实际依据版本，专业核准由相应流程处理。</p>
    <div class="attribute-shortcuts"><el-button v-for="field in fields" :key="field.key" :disabled="disabled || rows.length >= 1000 || rows.some(row => row.key === field.key)" @click="add(field.key)">添加{{ field.label }}</el-button></div>
    <div v-for="(row, index) in rows" :key="row.id" class="medical-attribute-row" :data-attribute-key="row.key">
      <div class="attribute-heading"><h3>{{ fieldLabel(row.key) }}</h3><el-button link type="danger" :disabled="disabled" @click="rows.splice(index, 1)">移除此项属性</el-button></div>
      <div class="attribute-fields">
      <el-form-item label="属性名称" :error="row.errors.key" :for="row.id + '-key'">
        <el-select :id="row.id + '-key'" v-model="row.key" aria-label="属性名称" filterable allow-create default-first-option :disabled="disabled" placeholder="选择业务属性或输入其他属性名称"><el-option v-for="field in fields" :key="field.key" :label="field.label" :value="field.key" /></el-select>
      </el-form-item>
      <el-form-item label="声明状态" :for="row.id + '-status'">
        <el-select :id="row.id + '-status'" v-model="row.status" aria-label="声明状态" :disabled="disabled" @change="statusChanged(row)">
          <el-option label="尚未确认" value="UNKNOWN" /><el-option label="已有声明依据" value="KNOWN" />
        </el-select>
      </el-form-item>
      </div>
      <template v-if="row.status === 'KNOWN'">
        <el-form-item v-if="inputKind(row) !== 'JSON'" :label="fieldLabel(row.key)" :error="row.errors.value" :for="row.id + '-value'">
          <el-select v-if="inputKind(row) === 'DEVICE_CLASS' || inputKind(row) === 'APPLICATION_PATH'" :id="row.id + '-value'" :model-value="businessValue(row)" :aria-label="fieldLabel(row.key)" :disabled="disabled" @update:model-value="setBusinessValue(row, $event)"><el-option v-for="(label, value) in choices(row)" :key="value" :label="label" :value="value" /></el-select>
          <el-select v-else-if="inputKind(row) === 'BOOLEAN'" :id="row.id + '-value'" :model-value="businessValue(row)" :aria-label="fieldLabel(row.key)" :disabled="disabled" @update:model-value="setBusinessValue(row, $event)"><el-option label="体外诊断试剂" :value="true" /><el-option label="医疗器械（非体外诊断试剂）" :value="false" /></el-select>
          <el-input v-else :id="row.id + '-value'" :model-value="businessText(row)" :aria-label="fieldLabel(row.key)" :disabled="disabled" @update:model-value="setBusinessValue(row, $event)" />
        </el-form-item>
        <el-form-item v-else label="属性原始值（JSON）" :error="row.errors.value" :for="row.id + '-value'">
          <el-input :id="row.id + '-value'" v-model="row.value" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" :disabled="disabled" />
        </el-form-item>
        <el-collapse v-if="inputKind(row) !== 'JSON'"><el-collapse-item title="高级：查看或编辑属性原始值" name="value"><el-input v-model="row.value" type="textarea" :disabled="disabled" aria-label="属性原始值（JSON）" /></el-collapse-item></el-collapse>
        <el-alert v-if="row.errors.bases" :title="row.errors.bases" type="error" :closable="false" />
        <rule-basis-editor :model-value="basisValues(row)" label="属性声明依据" resource-kind="SOURCE" :source-project-id="projectId" :disabled="disabled" :max-items="1000" :basis-names="basisNames(row)" @update:model-value="setBases(row, $event)" />
        <p v-for="item in row.bases.filter(item => item.errors.version || item.errors.hash)" :key="item.id" role="alert">{{ item.errors.version || item.errors.hash }}</p>
      </template>
      <el-form-item :label="row.status === 'UNKNOWN' ? '未知原因' : '声明原因'" :error="row.errors.reason" :for="row.id + '-reason'">
        <el-input :id="row.id + '-reason'" v-model="row.reason" :aria-label="row.status === 'UNKNOWN' ? '未知原因' : '声明原因'" type="textarea" maxlength="2000" :disabled="disabled" />
      </el-form-item>
      <el-collapse><el-collapse-item title="版本字段说明" name="key"><p>属性字段：{{ row.key }}。保存保留原始属性值、声明状态、原因及完整依据版本。</p></el-collapse-item></el-collapse>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Attribute, Basis } from '@/api/medical/protocol'
import { formatProjectJson, projectJsonIssue, readProjectJson } from '@/api/medical/project-json'
import { projectTextIssue } from '@/api/medical/project-write'
import { projectDeviceClassLabels, projectPathLabels } from '@/api/medical/project-workspace'
import RuleBasisEditor from './rule-basis-editor.vue'

const props = defineProps<{ attributes: Attribute[]; disabled: boolean; projectId?: string | undefined }>()
const fields = [{ key: 'product_name', label: '产品名称' }, { key: 'device_class', label: '器械类别' }, { key: 'application_path', label: '注册路径' }, { key: 'ivd', label: '产品类型' }]
interface BasisRow { id: string; version: string; hash: string; errors: { version: string; hash: string } }
interface AttributeRow {
  id: string; key: string; status: 'KNOWN' | 'UNKNOWN'; value: string; reason: string; bases: BasisRow[]
  errors: { key: string; value: string; reason: string; bases: string }
}
const rows = ref<AttributeRow[]>([])
let original = '[]'
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const hash = /^[0-9a-f]{64}$/
function basis(version = '', content = ''): BasisRow {
  return { id: crypto.randomUUID(), version, hash: content, errors: { version: '', hash: '' } }
}
function fieldLabel(key: string): string { return fields.find(field => field.key === key)?.label ?? (key || '其他项目属性') }
function inputKind(row: AttributeRow): 'TEXT' | 'DEVICE_CLASS' | 'APPLICATION_PATH' | 'BOOLEAN' | 'JSON' {
  if (row.value !== '' && projectJsonIssue(row.value)) return 'JSON'
  const current = row.value === '' ? undefined : readProjectJson(row.value)
  if (row.key === 'ivd' && (current === undefined || typeof current === 'boolean')) return 'BOOLEAN'
  if (current !== undefined && typeof current !== 'string') return 'JSON'
  if (row.key === 'product_name') return 'TEXT'
  if (row.key === 'device_class') return 'DEVICE_CLASS'
  if (row.key === 'application_path') return 'APPLICATION_PATH'
  return 'JSON'
}
function businessValue(row: AttributeRow): string | boolean | undefined {
  if (row.value === '') return undefined
  const current = readProjectJson(row.value)
  if (typeof current !== 'string' && typeof current !== 'boolean') throw new Error('业务属性控件需要文本或布尔值')
  return current
}
function setBusinessValue(row: AttributeRow, current: string | boolean): void { row.value = formatProjectJson(current) }
function businessText(row: AttributeRow): string | undefined {
  const current = businessValue(row)
  if (current !== undefined && typeof current !== 'string') throw new Error('文本属性控件需要文本值')
  return current
}
function choices(row: AttributeRow): Record<string, string> {
  const values: Record<string, string> = { ...(row.key === 'device_class' ? projectDeviceClassLabels : projectPathLabels) }
  const current = businessValue(row)
  if (typeof current === 'string' && !(current in values)) values[current] = '现有值：' + current
  return values
}
function basisValues(row: AttributeRow): Basis[] { return row.bases.map(item => ({ version_id: item.version, content_sha256: item.hash })) }
function basisNames(row: AttributeRow): Record<string, string> { return Object.fromEntries(row.bases.map((item, index) => [item.version, '依据 ' + (index + 1)])) }
function setBases(row: AttributeRow, values: Basis[]): void {
  row.bases = values.map(item => {
    const current = row.bases.find(value => value.version === item.version_id)
    return current ? { ...current, hash: item.content_sha256 } : basis(item.version_id, item.content_sha256)
  })
}
function add(key = ''): void {
  rows.value.push({ id: crypto.randomUUID(), key, status: 'UNKNOWN', value: '', reason: '', bases: [],
    errors: { key: '', value: '', reason: '', bases: '' } })
}
function statusChanged(row: AttributeRow): void {
  if (row.status === 'UNKNOWN') { row.value = ''; row.bases = [] }
}
function validate(): boolean {
  const keys = new Set<string>()
  let valid = true
  for (const row of rows.value) {
    row.errors.key = projectTextIssue(row.key, 128) || (keys.has(row.key) ? '属性名称不能重复' : '')
    keys.add(row.key)
    row.errors.reason = projectTextIssue(row.reason, 2000)
    row.errors.value = row.status === 'KNOWN' ? projectJsonIssue(row.value) : ''
    if (row.status === 'KNOWN' && !row.errors.value && readProjectJson(row.value) === null) row.errors.value = '已有声明的原始值不能为空值'
    row.errors.bases = row.status === 'KNOWN' && row.bases.length === 0 ? '已有声明需要至少一项实际依据版本' : ''
    const versions = new Set<string>()
    for (const item of row.bases) {
      item.errors.version = uuid.test(item.version) && !versions.has(item.version) ? '' : '请填写规范且不重复的依据版本'
      item.errors.hash = hash.test(item.hash) ? '' : '请填写实际原件的64位SHA-256内容摘要'
      versions.add(item.version)
      if (item.errors.version || item.errors.hash) valid = false
    }
    if (Object.values(row.errors).some(Boolean)) valid = false
  }
  return valid
}
function value(): Attribute[] {
  return rows.value.map(row => ({ key: row.key, status: row.status,
    value: row.status === 'UNKNOWN' ? null : readProjectJson(row.value), reason: row.reason,
    authority_refs: row.status === 'UNKNOWN' ? [] : row.bases.map(item => ({ version_id: item.version, content_sha256: item.hash })) }))
}
function representation(): string {
  return JSON.stringify(rows.value.map(row => ({ key: row.key, status: row.status, value: row.value, reason: row.reason,
    bases: row.bases.map(item => ({ version: item.version, hash: item.hash })) })))
}
function clear(): void { rows.value = []; original = '[]' }
function dirty(): boolean { return representation() !== original }
watch(() => props.attributes, attributes => {
  rows.value = attributes.map(attribute => ({ id: crypto.randomUUID(), key: attribute.key, status: attribute.status,
    value: attribute.status === 'UNKNOWN' ? '' : formatProjectJson(attribute.value), reason: attribute.reason,
    bases: attribute.authority_refs.map(item => basis(item.version_id, item.content_sha256)),
    errors: { key: '', value: '', reason: '', bases: '' } }))
  original = representation()
}, { immediate: true })
defineExpose({ validate, value, clear, dirty })
</script>

<style scoped>
.attribute-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.attribute-heading h2,.attribute-heading h3{margin:0}.attribute-shortcuts{display:flex;flex-wrap:wrap;gap:8px;margin:16px 0}.attribute-shortcuts .el-button+.el-button{margin-left:0}.medical-attribute-row{margin:16px 0;padding:20px;border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-bg-color)}.attribute-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-top:20px}.project-attributes p{color:var(--el-text-color-regular);font-size:12px;line-height:1.7}.project-attributes :deep(.el-select){width:100%}@media(max-width:650px){.attribute-fields{grid-template-columns:minmax(0,1fr)}.medical-attribute-row{padding:16px}.attribute-heading{align-items:flex-start}}
</style>
