<template>
  <section class="app-main" :class="{ 'medical-main': userStore.medicalMode }">
    <library-sections v-if="userStore.medicalMode && route.path.startsWith('/library')" />
    <el-alert v-if="route.meta.medicalFramework && !frameworkReady" :title="userStore.medicalLoading ? '正在核查当前账户' : '请重新核查当前账户后访问管理页面'" type="info" :closable="false" show-icon />
    <router-view v-slot="{ Component, route }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive :include="tagsViewStore.cachedViews">
          <component v-if="!route.meta.link && (!route.meta.medicalFramework || frameworkReady)" :is="Component" :key="route.meta.medicalFramework ? route.path + ':' + userStore.medicalGeneration : route.path"/>
        </keep-alive>
      </transition>
    </router-view>
    <iframe-toggle v-if="!userStore.medicalMode" />
    <copyright />
  </section>
</template>

<script setup>
import LibrarySections from "@/components/Medical/LibrarySections.vue"
import copyright from "./Copyright/index"
import iframeToggle from "./IframeToggle/index"
import useTagsViewStore from '@/store/modules/tagsView'
import useUserStore from '@/store/modules/user'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute()
const tagsViewStore = useTagsViewStore()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const frameworkReady = computed(() => userStore.medicalMode && !userStore.medicalLoading && userStore.medicalSession !== null
  && permissionStore.isMedicalFrameworkRoute(route.name))

onMounted(() => {
  addIframe()
})

watchEffect(() => {
  addIframe()
})

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route)
  }
}
</script>

<style lang="scss" scoped>
.app-main.medical-main { min-height: calc(100vh - 68px); }
.fixed-header + .app-main.medical-main {
  margin-top: 68px;
  height: calc(100vh - 68px);
  min-height: 0;
}
.app-main {
  /* 50= navbar  50  */
  min-height: calc(100vh - 50px);
  width: 100%;
  position: relative;
  overflow: hidden;
}

.fixed-header + .app-main {
  overflow-y: auto;
  scrollbar-gutter: auto;
  height: calc(100vh - 50px);
  min-height: 0px;
}

.app-main:has(.copyright) {
  padding-bottom: 36px;
}

.fixed-header + .app-main {
  margin-top: 50px;
}

.hasTagsView {
  .app-main {
    /* 84 = navbar + tags-view = 50 + 34 */
    min-height: calc(100vh - 84px);
  }

  .fixed-header + .app-main {
    margin-top: 84px;
    height: calc(100vh - 84px);
    min-height: 0px;
  }
}

/* 移动端fixed-header优化 */
@media screen and (max-width: 991px) {
  .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }

  .hasTagsView .fixed-header + .app-main {
    padding-bottom: max(60px, calc(constant(safe-area-inset-bottom) + 40px));
    padding-bottom: max(60px, calc(env(safe-area-inset-bottom) + 40px));
    overscroll-behavior-y: none;
  }
}

@supports (-webkit-touch-callout: none) {
  @media screen and (max-width: 991px) {
    .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - 50px);
      height: calc(100dvh - 50px);
    }

    .hasTagsView .fixed-header + .app-main {
      padding-bottom: max(17px, calc(constant(safe-area-inset-bottom) + 10px));
      padding-bottom: max(17px, calc(env(safe-area-inset-bottom) + 10px));
      height: calc(100svh - 84px);
      height: calc(100dvh - 84px);
    }
  }
}
</style>

<style lang="scss">
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background-color: #f1f1f1;
}

::-webkit-scrollbar-thumb {
  background-color: #c0c0c0;
  border-radius: 3px;
}
</style>
