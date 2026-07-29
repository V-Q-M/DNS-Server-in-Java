import { watch, onUnmounted } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'

export function useAutoRefresh(callback: () => void) {
  const settings = useSettingsStore()
  let timer: ReturnType<typeof setInterval> | null = null

  function reset() {
    if (timer) clearInterval(timer)
    timer = null
    const ms = settings.getRefreshMs()
    if (ms) timer = setInterval(callback, ms)
  }

  watch(() => settings.refreshInterval, reset, { immediate: true })
  onUnmounted(() => { if (timer) clearInterval(timer) })
}
