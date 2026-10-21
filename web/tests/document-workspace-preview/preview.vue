<template>
  <main>
    <p class="preview-banner" role="status">界面预览 · 模拟数据 · 未连接后端</p>
    <header><p>项目工作区 / 示例文稿</p><h1>产品技术说明 · 撰写与核验</h1><p>此预览使用真实正文组件。正文可在内存编辑，不保存、不执行检查、不产生专业结论。</p></header>
    <DocumentBodyReader :body="body" :document-revision="revision" active :dirty="dirty" :editable="!readonly" @changed="change">
      <template #inspector>
        <h4>工作区状态</h4><p>{{ dirty ? '有未保存的模拟编辑' : '模拟已保存正文' }}</p>
        <el-checkbox v-model="readonly">预览只读模式</el-checkbox>
        <details><summary>字段与适用条件</summary><p>本预览不提供字段核验结果。</p></details>
        <details><summary>引用来源</summary><p>本预览没有后端证据，不能验证专业陈述。</p></details>
        <p class="scope-note">技术结构检查不等于专业核验、审查确认或签发。</p>
      </template>
    </DocumentBodyReader>
  </main>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import type { DocumentBody } from '../../../contracts/typescript/src/interfaces'
import DocumentBodyReader from '../../src/views/medical/document-body-reader.vue'
const revision = crypto.randomUUID()
const dirty = ref(false); const readonly = ref(false)
const root = crypto.randomUUID()
const nodes: DocumentBody['nodes'] = [{ node_id: root, parent_id: null, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'DOCUMENT', title: '产品技术说明（模拟文稿）' } }]
for (const [index, [title, text]] of [
  ['一、文档目的与适用范围', '本文件仅用于展示连续阅读与正文编辑交互，不代表任何真实医疗器械的技术参数或注册结论。正式文稿需要依据已核准资料逐项确认适用范围。'],
  ['二、结构组成与描述', '在同一正文区域浏览章节、段落与文字。右侧目录用于定位内容；点击“编辑正文”可修订当前草稿，再切回连续阅读检查排版。切换模式不会自动保存。'],
  ['三、资料依据与待确认事项', '模拟待办：补充实际产品型号、适用条件和权威来源。读取引用原件仅说明已取得来源内容，不等于来源支持当前陈述。专业核验与审查结论必须由真实业务流程产生。'],
  ['四、修订与版本管理', '保存、版本比较、审查及签发属于不同的操作。在实际工作区内，应先确认字段编辑并填写修订理由；未保存的草稿不得作为已确认或已签发版本。']
].entries()) {
  const section = crypto.randomUUID()
  nodes.push({ node_id: section, parent_id: root, ordinal: index, field_ids: [], evidence_link_ids: [], content: { kind: 'SECTION', title: title! } }, { node_id: crypto.randomUUID(), parent_id: section, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'TEXT', text: text!, marks: [] } })
}
const body = ref<DocumentBody>({ schema_version: '1.0.0', template_version: crypto.randomUUID(), nodes, fields: [], evidence_links: [], fact_usages: [] })
function change(value: DocumentBody): void { body.value = value; dirty.value = true }
</script>
<style>
body { margin: 0; background: #eef2f7; color: #243247; font-family: system-ui, sans-serif; }
main { max-width: 1400px; margin: auto; padding: 20px; }
.preview-banner { background: #fff3cd; border: 1px solid #c99c31; padding: 12px 16px; border-radius: 6px; font-weight: 600; }
header { margin: 24px 0; } h1 { font-size: 26px; } p { line-height: 1.7; }
.scope-note { font-size: 13px; color: #536176; } details { margin: 18px 0; } summary { cursor: pointer; }
@media (max-width: 600px) { main { padding: 12px; } h1 { font-size: 21px; } }
</style>
