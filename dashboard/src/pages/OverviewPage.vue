<script setup lang="ts">
import { ref, computed, watch, inject } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { STATS_SERVICE_KEY } from '@/api/StatsService'
import type { StatsService } from '@/api/StatsService'
import type { OverviewStats, TimelinePoint } from '@/api/types'
import { startTimeFor, timelineGranularityFor, rangeSeconds } from '@/utils/timeRange'
import StatCard from '@/components/overview/StatCard.vue'
import Card from '@/components/ui/Card.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import RequestsTimelineChart from '@/components/charts/RequestsTimelineChart.vue'
import RcodeDonut from '@/components/charts/RcodeDonut.vue'
import type { DNSRcode } from '@/types/dns'
import { formatNumber, formatLatency, formatPercent, formatQpm } from '@/utils/format'

const settings = useSettingsStore()
const service  = inject<StatsService>(STATS_SERVICE_KEY)!

const stats    = ref<OverviewStats | null>(null)
const timeline = ref<TimelinePoint[]>([])
const rcodeBreakdown = ref<Partial<Record<DNSRcode, number>>>({})
const loading  = ref(false)

async function load() {
  loading.value = true
  const startTime = startTimeFor(settings.timeRange)
  const [s, t, r] = await Promise.all([
    service.getOverview(startTime),
    service.getTimeline(startTime, timelineGranularityFor(settings.timeRange)),
    service.getRcodeBreakdown(startTime),
  ])
  stats.value          = s
  timeline.value       = t
  rcodeBreakdown.value = r
  loading.value        = false
}

watch(() => settings.timeRange, load, { immediate: true })
useAutoRefresh(load)

const cacheHitRate = computed(() => {
  if (!stats.value) return 0
  const eligible = stats.value.cacheHits + stats.value.cacheMisses
  return eligible > 0 ? stats.value.cacheHits / eligible : 0
})

const errorRate = computed(() => {
  if (!stats.value || stats.value.totalQueries === 0) return 0
  return stats.value.errors / stats.value.totalQueries
})

const queriesPerMinute = computed(() => {
  if (!stats.value) return 0
  return (stats.value.totalQueries / rangeSeconds(settings.timeRange)) * 60
})

const ICONS = {
  queries:   `<svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75"><path stroke-linecap="round" stroke-linejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/><path stroke-linecap="round" stroke-linejoin="round" d="M9 10h.01M15 10h.01M9.5 15a3.5 3.5 0 005 0"/></svg>`,
  cache:     `<svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4.03 3-9 3S3 13.66 3 12"/><path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5"/></svg>`,
  latency:   `<svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
  error:     `<svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75"><path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg>`,
  domains:   `<svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75"><path stroke-linecap="round" stroke-linejoin="round" d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9"/></svg>`,
  qps:       `<svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.75"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
}
</script>

<template>
  <div class="space-y-6">
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-3">
      <StatCard
        title="Total Queries"
        :value="stats ? formatNumber(stats.totalQueries) : '—'"
        :loading="loading && !stats"
        :icon="ICONS.queries"
      />
      <StatCard
        title="Cache Hit Rate"
        :value="stats ? formatPercent(cacheHitRate) : '—'"
        :loading="loading && !stats"
        :icon="ICONS.cache"
      />
      <StatCard
        title="Avg Latency"
        :value="stats && stats.avgLatencyMs !== null ? formatLatency(stats.avgLatencyMs) : '—'"
        :loading="loading && !stats"
        :icon="ICONS.latency"
      />
      <StatCard
        title="Error Rate"
        :value="stats ? formatPercent(errorRate) : '—'"
        :loading="loading && !stats"
        :icon="ICONS.error"
      />
      <StatCard
        title="Unique Domains"
        :value="stats ? formatNumber(stats.uniqueDomains) : '—'"
        :loading="loading && !stats"
        :icon="ICONS.domains"
      />
      <StatCard
        title="Queries / min"
        :value="stats ? formatQpm(queriesPerMinute) : '—'"
        :loading="loading && !stats"
        :icon="ICONS.qps"
      />
    </div>

    <div class="grid gap-4 lg:grid-cols-3">
      <Card class="p-5 lg:col-span-2">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Requests over time</p>
        <div class="h-56">
          <Skeleton v-if="loading && !timeline.length" class="h-full w-full" />
          <RequestsTimelineChart v-else :data="timeline" />
        </div>
      </Card>

      <Card class="p-5">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Response codes</p>
        <div class="h-56">
          <Skeleton v-if="loading && !Object.keys(rcodeBreakdown).length" class="h-full w-full" />
          <RcodeDonut v-else :data="rcodeBreakdown" />
        </div>
      </Card>
    </div>
  </div>
</template>
