<template>
  <main>
    <h1>固定原件阅读检查</h1>
    <label>选择获准原件<input type="file" aria-label="选择获准原件" @change="select" /></label>
    <el-button @click="active = !active">{{ active ? '清理阅读内容' : '恢复阅读内容' }}</el-button>
    <source-file-preview v-if="file" :blob="file" :active="active" />
    <label>模拟公式原文<textarea v-model="formula" aria-label="模拟公式原文" /></label>
    <el-button @click="formulaHtml = renderDocumentFormula(formula)">预览模拟公式</el-button>
    <div class="formula-check" v-html="formulaHtml" />
  </main>
</template>

<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import SourceFilePreview from '../../src/views/medical/source-file-preview.vue'
import { renderDocumentFormula } from '../../src/api/medical/content-rendering'
import 'katex/dist/katex.min.css'

const file = shallowRef<File | null>(null); const active = ref(true)
const formula = ref(''); const formulaHtml = ref('')
watch(formula, () => { formulaHtml.value = '' }, { flush: 'sync' })
function select(event: Event): void {
  const input = event.target as HTMLInputElement
  const original = input.files?.item(0)
  if (!original) throw new Error('请使用获准原件执行检查')
  const mediaType = original.name.endsWith('.md') ? 'text/markdown' : original.type
  file.value = new File([original], original.name, { type: mediaType }); active.value = true
}
</script>
