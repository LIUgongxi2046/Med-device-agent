<template><main><p class="banner">界面预览 · 模拟数据 · 未连接后端 · 无审查或签发效力</p><header><small>研发项目 / 产品技术说明 / 文稿审查</small><h1>核对每一个决定的依据</h1><p>真实组件预览：固定版本、审查清单、退回修订与签发门槛。</p></header><nav aria-label="预览状态"><button @click="dirty = !dirty">{{ dirty ? '取消模拟未保存修改' : '模拟未保存修改' }}</button><button @click="current = !current">{{ current ? '模拟版本过期' : '恢复模拟当前版本' }}</button></nav><DocumentReviewWorkspace :snapshot="snapshot" :saved-body="savedBody" :dirty="dirty" :is-current="current" active @navigate-write="notice = '预览导航：应返回 S09 撰写页核验。此独立预览不连接真实路由。'"/><p v-if="notice" role="status">{{ notice }}</p></main></template>
<script setup lang="ts">
import { ref } from 'vue'
import DocumentReviewWorkspace from '../../src/views/medical/document-review-workspace.vue'
import type { DocumentSnapshot, DocumentBody } from '../../../contracts/typescript/src/interfaces'
const dirty = ref(false), current = ref(true), notice = ref('')
const snapshot: DocumentSnapshot = {
 document_id:'00000000-0000-0000-0000-000000000001',project_id:'00000000-0000-0000-0000-000000000002',document_revision:'00000000-0000-0000-0000-000000000003',revision:'12',plan_revision:'00000000-0000-0000-0000-000000000004',template_version:'00000000-0000-0000-0000-000000000005',body_schema_revision:'00000000-0000-0000-0000-000000000006',
 body:{artifact_id:'00000000-0000-0000-0000-000000000007',sha256:'a'.repeat(64),schema_ref:'preview-only',coverage:{selection_sha256:'a'.repeat(64),delivered_sha256:'a'.repeat(64),complete:true,next_cursor:null}},fields:{artifact_id:'00000000-0000-0000-0000-000000000008',sha256:'b'.repeat(64),schema_ref:'preview-only',coverage:{selection_sha256:'a'.repeat(64),delivered_sha256:'a'.repeat(64),complete:true,next_cursor:null}},evidence_links:{artifact_id:'00000000-0000-0000-0000-000000000009',sha256:'c'.repeat(64),schema_ref:'preview-only',coverage:{selection_sha256:'a'.repeat(64),delivered_sha256:'a'.repeat(64),complete:true,next_cursor:null}},
 check_ids:[],review_ids:[],signature_ids:[],invalidation_reasons:['模拟失效原因：适用条件尚需补充原件依据'],issue_watermark:'8',evidence_watermark:'16',auth_epoch:'1',governance_epoch:'1',allowed_actions:[],stream_cursors:[]
}
const body: DocumentBody = { schema_version: '1.0.0', template_version: snapshot.template_version, fields: [], evidence_links: [], fact_usages: [], nodes: [
 { node_id: '00000000-0000-0000-0000-000000000010', parent_id: null, ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'DOCUMENT', title: '模拟文稿' } },
 { node_id: '00000000-0000-0000-0000-000000000011', parent_id: '00000000-0000-0000-0000-000000000010', ordinal: 0, field_ids: [], evidence_link_ids: [], content: { kind: 'SECTION', title: '模拟章节：适用范围' } },
 { node_id: '00000000-0000-0000-0000-000000000012', parent_id: '00000000-0000-0000-0000-000000000010', ordinal: 1, field_ids: [], evidence_link_ids: [], content: { kind: 'SECTION', title: '模拟章节：资料依据' } }
] }
const savedBody = { documentRevision: snapshot.document_revision, body }

</script>
<style>body{margin:0;background:#edf2f6;font-family:system-ui,sans-serif;color:#233b50}main{max-width:1030px;margin:auto;padding:24px}.banner{background:#fff2cf;border:1px solid #dfbd67;padding:12px 16px;border-radius:8px;font-size:13px;font-weight:600}header{padding:20px 0 10px}header small{color:#718096}h1{font-size:30px;margin:10px 0}header p{color:#6d7e90}nav{display:flex;gap:10px}nav button{padding:8px 14px;border:1px solid #cbd9e3;border-radius:6px;background:white;color:#385670;cursor:pointer}@media(max-width:600px){main{padding:12px}h1{font-size:23px}nav{flex-wrap:wrap}}</style>
