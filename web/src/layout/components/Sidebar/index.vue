<template>
  <div :class="['sidebar-theme-wrapper', {'has-logo':showLogo}, sideTheme]" class="sidebar-container">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="getMenuBackground"
        :text-color="getMenuTextColor"
        :unique-opened="true"
        :active-text-color="theme"
        :collapse-transition="false"
        mode="vertical"
        :class="sideTheme"
        @select="handleSelection"
      >
        <template v-if="isMedical">
          <template v-for="group in businessNavigation" :key="group.id">
            <sidebar-item v-if="group.direct" :item="group.routes[0]" :base-path="group.routes[0].path" />
            <el-sub-menu v-else :index="'business-' + group.id" teleported>
              <template #title>
                <svg-icon :icon-class="group.icon" />
                <span class="menu-title">{{ group.title }}</span>
              </template>
              <sidebar-item v-for="item in group.routes" :key="item.path" :item="item" :base-path="item.path" :is-nest="true" class="nest-menu" />
            </el-sub-menu>
          </template>
        </template>
        <template v-else>
          <sidebar-item v-for="(item, index) in sidebarRouters" :key="item.path + index" :item="item" :base-path="item.path" />
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import Logo from './Logo'
import SidebarItem from './SidebarItem'
import variables from '@/assets/styles/variables.module.scss'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'
import { medicalNavigationGroups } from '@/components/Medical/navigation'

const route = useRoute()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const sidebarRouters = computed(() => permissionStore.sidebarRouters)
const isMedical = import.meta.env.VITE_APP_ENV === 'medical'
const businessNavigation = computed(() => isMedical ? medicalNavigationGroups(sidebarRouters.value) : [])
const showLogo = computed(() => settingsStore.sidebarLogo)
const sideTheme = computed(() => settingsStore.sideTheme)
const theme = computed(() => settingsStore.theme)
const isCollapse = computed(() => !appStore.sidebar.opened)

function handleSelection() {
  if (appStore.device === 'mobile' && appStore.sidebar.opened) {
    appStore.closeSideBar({ withoutAnimation: false })
  }
}

// 获取菜单背景色
const getMenuBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

// 获取菜单文字颜色
const getMenuTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuText : variables.menuLightText
})

const activeMenu = computed(() => {
  const { meta, path } = route
  if (isMedical) {
    const current = businessNavigation.value.flatMap(group => group.routes)
      .filter(item => path === item.path || path.startsWith(item.path + '/'))
      .sort((left, right) => right.path.length - left.path.length)[0]
    if (current) return current.path
  }
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})
</script>

<style lang="scss" scoped>
.sidebar-container {
  background-color: v-bind(getMenuBackground);
  
  .scrollbar-wrapper {
    background-color: v-bind(getMenuBackground);
  }

  .el-menu {
    border: none;
    height: 100%;
    width: 100% !important;
    
    .el-menu-item, .el-sub-menu__title {
      &:hover {
        background-color: var(--menu-hover, rgba(0, 0, 0, 0.06)) !important;
      }
    }

    .el-menu-item {
      color: v-bind(getMenuTextColor);
      
      &.is-active {
        color: var(--menu-active-text, #409eff);
        background-color: var(--menu-hover, rgba(0, 0, 0, 0.06)) !important;
      }
    }

    .el-sub-menu__title {
      color: v-bind(getMenuTextColor);
    }
  }
}
</style>
