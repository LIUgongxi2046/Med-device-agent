<template><main><p class="banner">模拟数据 · 未连接后端 · 仅记录界面</p><header><small>文稿工作区 / Gru 助手</small><h1>让每条需求都有明确上下文</h1><p>真实 Vue 组件的只读预览。不会保存消息、调用模型、检索资料或采纳建议。</p></header><nav aria-label="模拟状态"><button @click="dirty=!dirty">{{ dirty ? '恢复已保存正文' : '模拟未保存正文' }}</button><button @click="selected=!selected">{{ selected ? '取消模拟选区' : '显示模拟选区' }}</button></nav><p class="guide">展开下方 Gru 后，点“刷新我的会话”，再选择模拟会话，可查看两条用户记录及禁用的输入区。</p><DocumentGruRecords :session="session" :snapshot="snapshot" :saved-body="body" :selection="selected ? selection : null" :dirty="dirty" active /></main></template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DocumentGruRecords from '../../src/views/medical/document-gru-records.vue'
import { session,snapshot,body,selection } from './fixtures'
const dirty=ref(false),selected=ref(true)
onMounted(()=>{const panel=document.querySelector<HTMLDetailsElement>('.gru-records');if(panel)panel.open=true})
</script>
<style>body{margin:0;background:#edf2f6;font-family:system-ui,sans-serif;color:#233b50}main{max-width:850px;margin:auto;padding:24px}.banner{background:#fff2cf;border:1px solid #dfbd67;padding:12px 16px;border-radius:8px;font-size:13px;font-weight:600}header{padding:20px 0 10px}header small{color:#718096}h1{font-size:30px;margin:10px 0}header p,.guide{color:#6d7e90}nav{display:flex;gap:10px}nav button{padding:8px 14px;border:1px solid #cbd9e3;border-radius:6px;background:white;color:#385670;cursor:pointer}@media(max-width:600px){main{padding:12px}h1{font-size:23px}nav{flex-wrap:wrap}}</style>
