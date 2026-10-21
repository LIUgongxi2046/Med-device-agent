<template>
  <Teleport to="body">
    <section v-show="open" ref="panel" class="gru-panel" role="region" :aria-label="label" tabindex="-1" @keydown.esc.stop="emit('close')">
      <header class="gru-panel-bar"><strong>{{ label }}</strong><el-button aria-label="关闭 Gru 并返回触发位置" @click="emit('close')">关闭</el-button></header>
      <slot />
    </section>
  </Teleport>
</template>
<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
const props = defineProps<{ open: boolean; label: string }>()
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement | null>(null)
const route=useRoute()
let trigger: HTMLElement | null = null
let triggerRoute='',generation=0,alive=true
function restoreFocus() {
  if (!document.hidden && triggerRoute===route.fullPath && trigger?.isConnected && !trigger.closest('[disabled], [aria-disabled="true"], [inert], [hidden]') && trigger.getClientRects().length) trigger.focus()
  trigger = null
}
watch(() => props.open, async (open) => {
  const current=++generation
  if (open) {
    triggerRoute=route.fullPath
    trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    if (alive && current===generation && props.open && triggerRoute===route.fullPath && !document.hidden) panel.value?.focus()
  } else restoreFocus()
}, { flush: 'sync' })
watch(()=>route.fullPath,()=>{generation++;emit('close')},{flush:'sync'})
onBeforeUnmount(()=>{alive=false;generation++;restoreFocus()})
</script>
<style scoped>
.gru-panel{position:fixed;z-index:2000;right:16px;top:76px;bottom:16px;width:min(440px,calc(100vw - 32px));overflow:auto;overscroll-behavior:contain;background:#fff;border:1px solid #dbe4ee;border-radius:14px;box-shadow:0 12px 40px #18334b29;color:#25364a;padding:0 14px 14px;box-sizing:border-box;overflow-wrap:anywhere}.gru-panel:focus-visible{outline:2px solid #416c9e}.gru-panel-bar{position:sticky;top:0;z-index:1;display:flex;align-items:center;justify-content:space-between;gap:12px;background:#fff;padding:12px 0;border-bottom:1px solid #e4eaf2}@media(max-width:650px){.gru-panel{inset:0;width:100%;border-radius:0;max-width:100vw}}
</style>
