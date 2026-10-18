import { computed, onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

export function useMedicalRouteRead(name: string | readonly string[], clear: () => void) {
  const route = useRoute(); const router = useRouter(); const transitioning = ref(false)
  const matches = () => typeof name === 'string' ? route.name === name : name.some(item => route.name === item)
  const active = computed(() => !transitioning.value && matches())
  function begin(): void { transitioning.value = true; clear() }
  onBeforeRouteLeave(begin)
  onBeforeRouteUpdate(begin)
  const remove = router.afterEach(() => {
    if (matches()) transitioning.value = false
  })
  onBeforeUnmount(remove)
  return active
}
