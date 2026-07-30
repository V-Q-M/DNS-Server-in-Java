<script setup lang="ts">
import { ref, watch, inject, computed } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { STATS_SERVICE_KEY } from '@/api/StatsService'
import type { StatsService } from '@/api/StatsService'
import type { OverviewStats, DomainStat } from '@/api/types'
import { startTimeFor } from '@/utils/timeRange'
import Card from '@/components/ui/Card.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { formatPercent } from '@/utils/format'

const settings = useSettingsStore()
const service  = inject<StatsService>(STATS_SERVICE_KEY)!

const stats       = ref<OverviewStats | null>(null)
const topDomains  = ref<DomainStat[]>([])
const loading     = ref(false)

async function load() {
  loading.value = true
  const startTime = startTimeFor(settings.timeRange)
  const [s, td] = await Promise.all([
    service.getOverview(startTime),
    service.getTopDomains(startTime),
  ])
  stats.value      = s
  topDomains.value = td.slice(0, 15)
  loading.value    = false
}

watch(() => settings.timeRange, load, { immediate: true })
useAutoRefresh(load)

const hitRate = computed(() => {
  if (!stats.value) return 0
  const eligible = stats.value.cacheHits + stats.value.cacheMisses
  return eligible > 0 ? stats.value.cacheHits / eligible : 0
})

const hitRateColor = computed(() => {
  const r = hitRate.value
  if (r >= 0.7) return 'text-green-600 dark:text-green-400'
  if (r >= 0.4) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})
</script>

<template>
  <div class="space-y-4">
    <div class="grid gap-4 sm:grid-cols-3">
      <Card class="p-5 sm:col-span-1">
        <p class="text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Cache Hit Rate</p>
        <Skeleton v-if="loading && !stats" class="mt-2 h-12 w-24" />
        <p v-else :class="['mt-1 text-4xl font-bold tracking-tight', hitRateColor]">
          {{ formatPercent(hitRate) }}
        </p>
        <p class="mt-1 text-xs text-neutral-400">of cache-eligible queries</p>
      </Card>

      <Card class="p-5">
        <p class="text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Total Cache Hits</p>
        <Skeleton v-if="loading && !stats" class="mt-2 h-8 w-20" />
        <p v-else class="mt-1 text-3xl font-bold text-neutral-900 dark:text-neutral-50">
          {{ (stats?.cacheHits ?? 0).toLocaleString() }}
        </p>
      </Card>

      <Card class="p-5">
        <p class="text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Cache Errors</p>
        <Skeleton v-if="loading && !stats" class="mt-2 h-8 w-20" />
        <p v-else :class="['mt-1 text-3xl font-bold', (stats?.errors ?? 0) > 0 ? 'text-red-600 dark:text-red-400' : 'text-neutral-900 dark:text-neutral-50']">
          {{ (stats?.errors ?? 0).toLocaleString() }}
        </p>
      </Card>
    </div>

    <Card class="overflow-hidden p-0">
      <div class="border-b border-neutral-200 px-5 py-4 dark:border-neutral-800">
        <p class="text-sm font-semibold text-neutral-700 dark:text-neutral-300">Most queried domains</p>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-neutral-200 bg-neutral-50 dark:border-neutral-800 dark:bg-neutral-900">
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500">#</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500">Domain</th>
              <th class="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-neutral-500">Queries</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="loading && !topDomains.length">
              <tr v-for="i in 8" :key="i" class="border-b border-neutral-100 dark:border-neutral-800">
                <td v-for="j in 3" :key="j" class="px-4 py-3"><Skeleton class="h-4 w-full" /></td>
              </tr>
            </template>
            <tr
              v-else
              v-for="(d, i) in topDomains"
              :key="d.domain"
              class="border-b border-neutral-100 last:border-0 dark:border-neutral-800"
            >
              <td class="px-4 py-3 text-xs text-neutral-400">{{ i + 1 }}</td>
              <td class="px-4 py-3 font-mono text-xs text-neutral-900 dark:text-neutral-100">{{ d.domain }}</td>
              <td class="px-4 py-3 text-right text-xs text-neutral-700 dark:text-neutral-300">{{ d.count.toLocaleString() }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Card>
  </div>
</template>
