import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { TimeRange } from '@/api/types'

export type RefreshInterval = 'off' | '10s' | '30s' | '1m' | '5m'

const REFRESH_MS: Record<RefreshInterval, number | null> = {
  'off': null, '10s': 10_000, '30s': 30_000, '1m': 60_000, '5m': 300_000,
}

export const useSettingsStore = defineStore('settings', () => {
  const timeRange       = ref<TimeRange>('1h')
  const refreshInterval = ref<RefreshInterval>('off')
  const darkMode        = ref(false)

  function setTimeRange(r: TimeRange)             { timeRange.value = r }
  function setRefreshInterval(i: RefreshInterval) { refreshInterval.value = i }
  function toggleDarkMode()                       { darkMode.value = !darkMode.value }

  function getRefreshMs(): number | null {
    return REFRESH_MS[refreshInterval.value]
  }

  watch(darkMode, dark => {
    document.documentElement.classList.toggle('dark', dark)
  }, { immediate: true })

  return { timeRange, refreshInterval, darkMode, setTimeRange, setRefreshInterval, toggleDarkMode, getRefreshMs }
})
