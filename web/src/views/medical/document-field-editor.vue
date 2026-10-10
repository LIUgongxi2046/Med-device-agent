<template>
  <el-form :disabled="readonly" label-position="top">
    <el-form-item label="原始值"><el-input v-model="original" /></el-form-item>
    <el-form-item v-for="key in keys" :key="key" :label="labels[key]">
      <el-checkbox v-model="enabled[key]">提供此项内容</el-checkbox>
      <el-input v-if="enabled[key]" v-model="values[key]" />
    </el-form-item>
    <p v-if="dirty" role="status">字段输入尚未确认</p>
    <el-button :disabled="readonly || !dirty" @click="confirm">确认字段修改</el-button>
  </el-form>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FieldValue } from '../../../../contracts/typescript/src/interfaces'
import { documentReference } from '@/api/medical/document-body'
import { validate } from '@/api/medical/protocol'

const props = defineProps<{ field: FieldValue; input: FieldValue | undefined; readonly: boolean }>()
const emit = defineEmits<{ changed: [value: FieldValue]; inputChanged: [value: FieldValue] }>()
const keys = ['decimal_value', 'unit', 'model', 'condition'] as const
const labels = { decimal_value: '精确数值', unit: '计量单位', model: '适用型号', condition: '适用条件' }
const original = ref('')
const enabled = reactive({ decimal_value: false, unit: false, model: false, condition: false })
const values = reactive({ decimal_value: '', unit: '', model: '', condition: '' })
function chosen(): FieldValue {
  const value = structuredClone(props.field); value.original_value = original.value
  for (const key of keys) value[key] = enabled[key] ? values[key] : null
  return value
}
const dirty = computed(() => JSON.stringify(chosen()) !== JSON.stringify(props.field))
function confirm(): void {
  const value = chosen(); validate(documentReference + 'FieldValue', value); emit('changed', value)
}
watch(() => [props.field, props.input] as const, ([field, input]) => {
  field = input ?? field
  original.value = field.original_value
  for (const key of keys) { enabled[key] = field[key] !== null; values[key] = field[key] ?? '' }
}, { immediate: true })
watch([original, enabled, values], () => emit('inputChanged', chosen()), { deep: true })
</script>
