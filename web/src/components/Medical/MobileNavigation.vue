<template>
  <el-menu mode="horizontal" :default-active="route.meta.activeMenu || route.path" :ellipsis="false" class="medical-mobile-menu">
    <el-sub-menu index="medical-navigation">
      <template #title>导航</template>
      <el-menu-item-group v-for="group in groups" :key="group.title" :title="group.title">
        <sidebar-item v-for="item in group.routes" :key="item.path" :item="item" :base-path="item.path" />
      </el-menu-item-group>
    </el-sub-menu>
  </el-menu>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import usePermissionStore from '@/store/modules/permission'
import SidebarItem from '@/layout/components/Sidebar/SidebarItem.vue'
import { medicalNavigationGroups } from './navigation'
const route = useRoute(); const permission = usePermissionStore()
const groups = computed(() => medicalNavigationGroups(permission.sidebarRouters, permission.frameworkRoutes))
</script>
<style scoped>
.medical-mobile-menu { border: 0; flex-shrink: 0; --el-menu-horizontal-height: 68px; }
</style>
