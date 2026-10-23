<template>
  <nav class="operations-navigation" aria-label="评测与运营分区">
    <router-link v-for="item in items" :key="item.path" :to="item.path" :aria-current="route.path.startsWith(item.path) ? 'page' : undefined">{{ item.label }}</router-link>
  </nav>
</template>
<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
const items = [{ path: '/evaluation', label: '质量评测' }, { path: '/feedback', label: '反馈改进' }, { path: '/operations', label: '用量与成本' }, { path: '/document-save-audit', label: '审计记录' }]
</script>
<style scoped>
.operations-navigation{display:flex;gap:28px;border-bottom:1px solid var(--el-border-color-light);margin:0 0 24px;overflow:auto;white-space:nowrap}.operations-navigation a{padding:12px 0;color:var(--el-text-color-regular);border-bottom:2px solid transparent;font-size:14px}.operations-navigation a[aria-current=page]{color:var(--el-color-primary);border-bottom-color:var(--el-color-primary);font-weight:600}
</style>
