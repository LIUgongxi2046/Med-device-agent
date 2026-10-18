<template>
  <fieldset :disabled="disabled" class="rule-bases">
    <legend>{{ label }}</legend>
    <p v-if="!modelValue.length">尚未填写依据</p>
    <div v-for="(item, index) in modelValue" :key="index" class="basis-row">
      <el-form-item :label="label + ' ' + (index + 1) + ' 固定版本'">
        <el-input :model-value="item.version_id" :disabled="disabled" @update:model-value="change(index, 'version_id', $event)" />
      </el-form-item>
      <el-form-item :label="label + ' ' + (index + 1) + ' SHA-256摘要'">
        <el-input :model-value="item.content_sha256" :disabled="disabled" @update:model-value="change(index, 'content_sha256', $event)" />
      </el-form-item>
      <el-button :disabled="disabled" @click="emit('update:modelValue', modelValue.filter((_, i) => i !== index))">移除{{ label }} {{ index + 1 }}</el-button>
    </div>
    <el-button :disabled="disabled" @click="emit('update:modelValue', [...modelValue, { version_id: '', content_sha256: '' }])">添加{{ label }}</el-button>
  </fieldset>
</template>
<script setup lang="ts">
import type { Basis } from '@/api/medical/protocol'
const props = defineProps<{ modelValue: Basis[]; label: string; disabled: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Basis[]] }>()
function change(index: number, field: keyof Basis, value: string): void {
  emit('update:modelValue', props.modelValue.map((item, i) => i === index ? { ...item, [field]: value } : item))
}
</script>
<style scoped>
.rule-bases { border: 1px solid var(--el-border-color); padding: 16px; margin: 16px 0; min-width: 0; }
.basis-row { margin-bottom: 20px; }
</style>
