<template>
  <el-button class="gru-global-entry" aria-label="打开 Gru 文稿助手，快捷键 Command 或 Control 加 J" aria-keyshortcuts="Control+J Meta+J" @click="open">Gru · ⌘/Ctrl J</el-button>
  <gru-panel :open="emptyOpen" label="Gru 文稿助手" @close="emptyOpen=false">
    <p role="status">请先选择获授权的项目并打开已保存的文稿，再围绕固定正文版本创建会话。</p>
    <p>这里仅提供消息记录，不会调用模型或自动执行需求。当前没有固定文稿，未发起项目会话。</p>
  </gru-panel>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { watch } from 'vue'
import GruPanel from './gru-panel.vue'
import { openRegisteredGru } from '@/api/medical/gru-keyboard'
const emptyOpen=ref(false)
const route=useRoute()
function open(){if(openRegisteredGru())emptyOpen.value=false;else emptyOpen.value=true}
function keyboard(event:KeyboardEvent){
  if(event.defaultPrevented||event.isComposing||event.keyCode===229||event.repeat||event.altKey||event.shiftKey)return
  if((event.metaKey||event.ctrlKey)&&event.key.toLowerCase()==='j'){event.preventDefault();open()}
}
function close(){emptyOpen.value=false}
watch(()=>route.fullPath,close)
onMounted(()=>{window.addEventListener('keydown',keyboard);window.addEventListener('medical-context-invalidated',close)})
onBeforeUnmount(()=>{window.removeEventListener('keydown',keyboard);window.removeEventListener('medical-context-invalidated',close)})
</script>
<style scoped>
.gru-global-entry{position:fixed;right:20px;bottom:20px;z-index:1000;border-radius:24px;box-shadow:0 3px 12px #18334b20}@media(max-width:650px){.gru-global-entry{right:12px;bottom:12px}}
</style>
