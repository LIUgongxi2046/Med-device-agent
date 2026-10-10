<template>
  <quill-editor content-type="delta" :read-only="readonly" :toolbar="toolbar" :options="{ formats }"
    @ready="ready" @text-change="changed" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { QuillEditor } from '@vueup/vue-quill'
import type { Quill } from '@vueup/vue-quill'
import type { TextContent } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue } from '@/api/medical/document-body'
import { fail } from '@/api/medical/protocol'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const props = defineProps<{ content: TextContent; readonly: boolean }>()
const emit = defineEmits<{ changed: [contents: TextContent[]] }>()
const toolbar = [['bold', 'italic', 'underline'], [{ script: 'super' }, { script: 'sub' }], ['clean']]
const formats = ['bold', 'italic', 'underline', 'script']
let editor: Quill | null = null; let supplying = false
function supply(): void {
  if (editor === null) return
  const marks = props.content.marks
  const attributes: Record<string, string | boolean> = {}
  if (marks.includes('BOLD')) attributes.bold = true
  if (marks.includes('ITALIC')) attributes.italic = true
  if (marks.includes('UNDERLINE')) attributes.underline = true
  if (marks.includes('SUPERSCRIPT')) attributes.script = 'super'
  if (marks.includes('SUBSCRIPT')) attributes.script = 'sub'
  supplying = true
  const selection = editor.getSelection()
  try {
    editor.setContents([{ insert: props.content.text, attributes }, { insert: '\n' }], 'silent')
    if (selection !== null) editor.setSelection(selection, 'silent')
  }
  finally { supplying = false }
}
function ready(value: Quill): void { editor = value; editor.enable(!props.readonly); supply() }
function changed(event: { source: string }): void {
  if (supplying || event.source !== 'user' || props.readonly || editor === null) return
  const contents: TextContent[] = []
  for (const operation of editor.getContents(0, editor.getLength() - 1).ops) {
    if (typeof operation.insert !== 'string') fail('SCHEMA_INVALID', '正文文本编辑器只能产生已定义的文本内容')
    const attributes = operation.attributes ?? {}; const marks: TextContent['marks'] = []
    if (Object.keys(attributes).some(key => !formats.includes(key))) fail('SCHEMA_INVALID', '正文文本格式未通过定义校验')
    if (attributes.bold === true) marks.push('BOLD')
    if (attributes.italic === true) marks.push('ITALIC')
    if (attributes.underline === true) marks.push('UNDERLINE')
    if (attributes.script === 'super') marks.push('SUPERSCRIPT')
    if (attributes.script === 'sub') marks.push('SUBSCRIPT')
    contents.push({ kind: 'TEXT', text: operation.insert, marks })
  }
  if (contents.length === 0) contents.push({ kind: 'TEXT', text: '', marks: [] })
  if (contents.length !== 1 || canonicalDocumentValue(contents[0]) !== canonicalDocumentValue(props.content)) emit('changed', contents)
}
watch(() => props.readonly, value => editor?.enable(!value))
watch(() => props.content, supply)
onBeforeUnmount(() => { editor?.enable(false); editor = null })
</script>
