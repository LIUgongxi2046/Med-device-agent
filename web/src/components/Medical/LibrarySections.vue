<template>
  <section class="library-sections" aria-label="资料中心分区">
    <div class="library-context"><span>资料中心</span><router-link v-if="projectId" :to="'/projects/' + projectId">返回项目</router-link><span v-else class="scope-note">按各分区的当前授权范围查询</span></div>
    <el-tabs :model-value="activeLibrarySection(route.path)" @tab-click="pane => navigate(pane.paneName)">
      <el-tab-pane v-for="section in librarySections" :key="section.path" :label="section.label" :name="section.path" />
    </el-tabs>
    <div v-if="route.path.startsWith('/library/search')" class="search-modes" aria-label="检索方式">
      <router-link :to="libraryLocation('/library/search', route.query)">全文检索</router-link>
      <router-link :to="libraryLocation('/library/search/exact', route.query)">结构化字段等值检索</router-link>
    </div>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { activeLibrarySection, librarySections, libraryLocation } from './navigation'
const route = useRoute(); const router = useRouter()
const projectId = computed(() => typeof route.query.project_id === 'string' && /^[0-9a-f-]{36}$/.test(route.query.project_id) ? route.query.project_id : '')
function navigate(path) { if (path !== route.path) router.push(libraryLocation(path, route.query)) }
</script>
<style scoped>
.library-sections { padding: 20px 28px 0; max-width: 1480px; margin: 0 auto; background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-light); }
.library-context { display: flex; align-items: center; gap: 16px; font-weight: 600; color: #243247; }
.library-context a, .search-modes a { color: #175FAE; font-size: 13px; }
.scope-note { font-size: 12px; font-weight: 400; color: #526279; }
.library-sections :deep(.el-tabs__header) { margin: 8px 0 0; }
.library-sections :deep(.el-tabs__content) { display: none; }
.search-modes { display: flex; gap: 20px; flex-wrap: wrap; padding: 12px 0; }
.search-modes .router-link-exact-active { font-weight: 600; }
@media(max-width: 650px) { .library-sections { padding: 16px 16px 0; } .library-context { flex-wrap: wrap; } }
</style>
