<script setup lang="ts">
import { ref, watch, inject } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { STATS_SERVICE_KEY } from '@/api/StatsService'
import type { StatsService } from '@/api/StatsService'
import type { TimelinePoint, LatencyBucket, DomainStat } from '@/api/types'
import type { RRType, ResponseSource, DNSRcode, Protocol } from '@/types/dns'
import { startTimeFor, timelineGranularityFor } from '@/utils/timeRange'
import Card from '@/components/ui/Card.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import RequestsTimelineChart from '@/components/charts/RequestsTimelineChart.vue'
import RcodeDonut from '@/components/charts/RcodeDonut.vue'
import ProtocolDonut from '@/components/charts/ProtocolDonut.vue'
import RRTypeDonut from '@/components/charts/RRTypeDonut.vue'
import ResponseSourceDonut from '@/components/charts/ResponseSourceDonut.vue'
import LatencyHistogram from '@/components/charts/LatencyHistogram.vue'
import TopDomainsChart from '@/components/charts/TopDomainsChart.vue'

const settings = useSettingsStore()
const service  = inject<StatsService>(STATS_SERVICE_KEY)!

const timeline      = ref<TimelinePoint[]>([])
const rcodeBreak    = ref<Partial<Record<DNSRcode, number>>>({})
const protocolBreak = ref<Partial<Record<Protocol, number>>>({})
const rrBreak       = ref<Partial<Record<RRType, number>>>({})
const sourceBreak   = ref<Partial<Record<ResponseSource, number>>>({})
const latencyDist   = ref<LatencyBucket[]>([])
const topDomains    = ref<DomainStat[]>([])
const loading       = ref(false)

async function load() {
  loading.value = true
  const startTime = startTimeFor(settings.timeRange)
  const [t, rc, proto, r, s, ld, td] = await Promise.all([
    service.getTimeline(startTime, timelineGranularityFor(settings.timeRange)),
    service.getRcodeBreakdown(startTime),
    service.getProtocolBreakdown(startTime),
    service.getRRTypeBreakdown(startTime),
    service.getResponseSourceBreakdown(startTime),
    service.getLatencyDistribution(startTime),
    service.getTopDomains(startTime),
  ])
  timeline.value      = t
  rcodeBreak.value    = rc
  protocolBreak.value = proto
  rrBreak.value       = r
  sourceBreak.value   = s
  latencyDist.value   = ld
  topDomains.value    = td.slice(0, 10)
  loading.value       = false
}

watch(() => settings.timeRange, load, { immediate: true })
useAutoRefresh(load)
</script>

<template>
  <div class="space-y-4">
    <div class="grid gap-4 lg:grid-cols-2">
      <Card class="p-5 lg:col-span-2">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Requests over time</p>
        <div class="h-56">
          <Skeleton v-if="loading && !timeline.length" class="h-full w-full" />
          <RequestsTimelineChart v-else :data="timeline" />
        </div>
      </Card>

      <Card class="p-5">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Response code breakdown</p>
        <div class="h-52">
          <Skeleton v-if="loading" class="h-full w-full" />
          <RcodeDonut v-else :data="rcodeBreak" />
        </div>
      </Card>

      <Card class="p-5">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Query type (RR type)</p>
        <div class="h-52">
          <Skeleton v-if="loading" class="h-full w-full" />
          <RRTypeDonut v-else :data="rrBreak" />
        </div>
      </Card>

      <Card class="p-5">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Response source</p>
        <div class="h-52">
          <Skeleton v-if="loading" class="h-full w-full" />
          <ResponseSourceDonut v-else :data="sourceBreak" />
        </div>
      </Card>

      <Card class="p-5">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Protocol</p>
        <div class="h-52">
          <Skeleton v-if="loading" class="h-full w-full" />
          <ProtocolDonut v-else :data="protocolBreak" />
        </div>
      </Card>

      <Card class="p-5">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Latency distribution</p>
        <div class="h-52">
          <Skeleton v-if="loading" class="h-full w-full" />
          <LatencyHistogram v-else :data="latencyDist" />
        </div>
      </Card>

      <Card class="p-5 lg:col-span-2">
        <p class="mb-4 text-sm font-semibold text-neutral-700 dark:text-neutral-300">Top 10 queried domains</p>
        <div class="h-72">
          <Skeleton v-if="loading" class="h-full w-full" />
          <TopDomainsChart v-else :data="topDomains" />
        </div>
      </Card>
    </div>
  </div>
</template>
