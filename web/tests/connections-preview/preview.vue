<template><div class="preview-shell"><nav aria-label="预览状态切换"><strong>隔离 Vue 预览 · 模拟数据 / 未连接后端</strong><label>页面状态 <select v-model="state"><option v-for="(copy, key) in connectionStateCopy" :key="key" :value="key">{{ copy.title }}</option></select></label><label>权限状态 <select v-model="access"><option value="unknown">权限未知</option><option value="allowed">模拟可管理（仍不可提交）</option><option value="denied">无权限</option></select></label><button @click="version++">重置预览</button></nav><Connections :key="version" :access="access" :view-state="state" preview/></div></template>
<script setup lang="ts">
import { ref } from 'vue'
import Connections from '../../src/views/medical/connections.vue'
import { connectionStateCopy } from '../../src/views/medical/connections-presentation'
import type { ConnectionAccess, ConnectionViewState } from '../../src/views/medical/connections-presentation'
const state = ref<ConnectionViewState>('blocked'), access = ref<ConnectionAccess>('unknown'), version = ref(0)
</script>
<style>body{margin:0;font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif;background:#f4f6fa}*{box-sizing:border-box}.preview-shell>nav{display:flex;gap:16px;align-items:center;flex-wrap:wrap;padding:14px 28px;background:#142c4c;color:#fff;font-size:13px}.preview-shell>nav label{display:flex;gap:8px;align-items:center}.preview-shell>nav select,.preview-shell>nav button{font:inherit;border-radius:4px;padding:6px;max-width:100%}@media(max-width:600px){.preview-shell>nav{padding:14px}.preview-shell>nav label{flex-wrap:wrap}}</style>
