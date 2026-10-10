<template>
  <h1>原始文件选择检查</h1>
  <file-upload :selection-only="true" :file-type="['md']" :file-size="0" :limit="100" :drag="false" :disabled="disabled" @select-file="selected" />
  <button @click="disabled = true; files = []">清理选择</button>
  <output data-selected-files>{{ JSON.stringify(files) }}</output>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import FileUpload from '@/components/FileUpload/index.vue'
const files = ref<{ name: string; size: number; sha256: string }[]>([])
const disabled = ref(false)
async function selected(file: File): Promise<void> {
  if (!(file instanceof File)) throw new Error('文件选择事件没有返回实际File')
  const hash = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  files.value.push({ name: file.name, size: file.size, sha256: Array.from(new Uint8Array(hash), value => value.toString(16).padStart(2, '0')).join('') })
}
</script>
