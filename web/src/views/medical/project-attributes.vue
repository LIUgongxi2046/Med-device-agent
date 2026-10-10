<template>
  <section aria-label="项目属性及声明依据">
    <p>未知属性保留“尚未确认”和未知原因。已有声明需要原始值及实际依据版本，专业核准由相应流程处理。</p>
    <el-button :disabled="disabled || rows.length >= 1000" @click="add">添加项目属性</el-button>
    <section v-for="(row, index) in rows" :key="row.id" class="medical-attribute-row">
      <h3>属性{{ index + 1 }}</h3>
      <el-form-item label="属性名称" :error="row.errors.key" :for="row.id + '-key'">
        <el-input :id="row.id + '-key'" v-model="row.key" maxlength="128" :disabled="disabled" />
      </el-form-item>
      <el-form-item label="声明状态" :for="row.id + '-status'">
        <el-select :id="row.id + '-status'" v-model="row.status" :disabled="disabled" @change="statusChanged(row)">
          <el-option label="尚未确认" value="UNKNOWN" /><el-option label="已有声明依据" value="KNOWN" />
        </el-select>
      </el-form-item>
      <template v-if="row.status === 'KNOWN'">
        <el-form-item label="属性原始值（JSON）" :error="row.errors.value" :for="row.id + '-value'">
          <el-input :id="row.id + '-value'" v-model="row.value" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" :disabled="disabled" />
        </el-form-item>
        <p>数字按完整原值填写；文本使用JSON字符串表示。</p>
        <el-alert v-if="row.errors.bases" :title="row.errors.bases" type="error" :closable="false" />
        <section v-for="basis in row.bases" :key="basis.id" aria-label="属性声明依据">
          <el-form-item label="依据版本" :error="basis.errors.version" :for="basis.id + '-version'">
            <el-input :id="basis.id + '-version'" v-model="basis.version" :disabled="disabled" />
          </el-form-item>
          <el-form-item label="依据内容摘要（SHA-256）" :error="basis.errors.hash" :for="basis.id + '-hash'">
            <el-input :id="basis.id + '-hash'" v-model="basis.hash" :disabled="disabled" />
          </el-form-item>
          <el-button :disabled="disabled" @click="row.bases.splice(row.bases.indexOf(basis), 1)">移除此项依据</el-button>
        </section>
        <el-button :disabled="disabled || row.bases.length >= 1000" @click="addBasis(row)">添加声明依据</el-button>
      </template>
      <el-form-item :label="row.status === 'UNKNOWN' ? '未知原因' : '声明原因'" :error="row.errors.reason" :for="row.id + '-reason'">
        <el-input :id="row.id + '-reason'" v-model="row.reason" type="textarea" maxlength="2000" :disabled="disabled" />
      </el-form-item>
      <el-button :disabled="disabled" @click="rows.splice(index, 1)">移除此项属性</el-button>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Attribute } from '@/api/medical/protocol'
import { formatProjectJson, projectJsonIssue, readProjectJson } from '@/api/medical/project-json'
import { projectTextIssue } from '@/api/medical/project-write'

const props = defineProps<{ attributes: Attribute[]; disabled: boolean }>()
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
function add(): void {
  rows.value.push({ id: crypto.randomUUID(), key: '', status: 'UNKNOWN', value: '', reason: '', bases: [],
    errors: { key: '', value: '', reason: '', bases: '' } })
}
function addBasis(row: AttributeRow): void { row.bases.push(basis()) }
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
