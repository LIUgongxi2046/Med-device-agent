import { computed, onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

export function useMedicalRouteRead(name: string, clear: () => void) {
  const route = useRoute(); const router = useRouter(); const transitioning = ref(false)
  const active = computed(() => !transitioning.value && route.name === name)
  function begin(): void { transitioning.value = true; clear() }
  onBeforeRouteLeave(begin)
  onBeforeRouteUpdate(begin)
  const remove = router.afterEach(() => {
    if (route.name === name) transitioning.value = false
  })
  onBeforeUnmount(remove)
  return active
}
