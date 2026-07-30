<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useSettingsStore } from '@/stores/settingsStore'
import DSelect from '@/components/ui/DSelect.vue'
import type { TimeRange } from '@/api/types'
import type { RefreshInterval } from '@/stores/settingsStore'

const route    = useRoute()
const settings = useSettingsStore()

const timeRangeOptions = [
  { value: '15m', label: 'Last 15 min' },
  { value: '1h',  label: 'Last 1 hour' },
  { value: '6h',  label: 'Last 6 hours' },
  { value: '24h', label: 'Last 24 hours' },
  { value: '7d',  label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
]

const refreshOptions = [
  { value: 'off', label: 'No refresh' },
  { value: '10s', label: 'Every 10 s' },
  { value: '30s', label: 'Every 30 s' },
  { value: '1m',  label: 'Every 1 min' },
  { value: '5m',  label: 'Every 5 min' },
]

const title = () => (route.meta.title as string) ?? ''
</script>

<template>
  <header class="flex h-14 shrink-0 items-center justify-between border-b border-neutral-200 bg-white px-6 dark:border-neutral-800 dark:bg-neutral-950">
    <h1 class="text-base font-semibold text-neutral-900 dark:text-neutral-100">{{ title() }}</h1>

    <div class="flex items-center gap-3">
      <div class="w-36">
        <DSelect
          :model-value="settings.timeRange"
          :options="timeRangeOptions"
          @update:model-value="settings.setTimeRange($event as TimeRange)"
        />
      </div>
      <div class="w-36">
        <DSelect
          :model-value="settings.refreshInterval"
          :options="refreshOptions"
          @update:model-value="settings.setRefreshInterval($event as RefreshInterval)"
        />
      </div>

      <button
        :title="settings.darkMode ? 'Switch to light mode' : 'Switch to dark mode'"
        class="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-200 text-neutral-500 transition-colors hover:bg-neutral-100 hover:text-neutral-700 dark:border-neutral-700 dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-neutral-200"
        @click="settings.toggleDarkMode()"
      >
        <svg v-if="!settings.darkMode" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75">
          <path stroke-linecap="round" stroke-linejoin="round" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
        </svg>
        <svg v-else class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
        </svg>
      </button>
    </div>
  </header>
</template>
