<template><main><p class="banner">模拟数据 · 未连接后端 · 无医疗事实确认</p><header><p>文稿工作区 / 结构化字段</p><h1>原值与证据，一处核对</h1><p>真实 Vue 组件预览。搜索、筛选、字段选择与处理意见草稿可交互；可预览理由，所有正式提交保持禁用。</p></header><DocumentFieldVerification :body="body" @select-field="lastSelected = $event" /><p v-if="lastSelected">当前选中模拟字段：{{ lastSelected }}</p></main></template>
<script setup lang="ts">
import { ref } from 'vue'
import DocumentFieldVerification from '../../src/views/medical/document-field-verification.vue'
import { body } from './fixture'
const lastSelected = ref('')
</script>
<style>body{margin:0;background:#edf2f8;font-family:system-ui,'PingFang SC',sans-serif;color:#23354a}main{max-width:1220px;margin:28px auto;padding:28px;background:white;border:1px solid #dce4ef;border-radius:12px}.banner{background:#fff3d6;color:#77541c;padding:12px 16px;border-radius:6px;font-size:13px}header{margin-bottom:28px}h1{font-size:27px}header p{color:#65768b}@media(max-width:760px){main{margin:0;padding:16px}}</style>
