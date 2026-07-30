<script setup lang="ts">
import Sheet from '@/components/ui/Sheet.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import RcodeBadge from './RcodeBadge.vue'
import { formatDate, formatLatency } from '@/utils/format'
import type { DNSRequestDetail } from '@/api/types'

defineProps<{
  open: boolean
  request: DNSRequestDetail | null
  loading?: boolean
}>()
defineEmits<{ close: [] }>()

function field(label: string) {
  return { label }
}
</script>

<template>
  <Sheet :open="open" title="Request Detail" @close="$emit('close')">
    <div v-if="loading" class="space-y-4">
      <Skeleton v-for="i in 6" :key="i" class="h-10 w-full" />
    </div>

    <div v-else-if="request" class="space-y-5">
      <div class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-sm">
        <span class="font-medium text-neutral-500 dark:text-neutral-400">ID</span>
        <span class="break-all font-mono text-xs text-neutral-700 dark:text-neutral-300">{{ request.id }}</span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">Timestamp</span>
        <span class="text-neutral-900 dark:text-neutral-100">{{ formatDate(request.timestamp) }}</span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">Domain</span>
        <span class="font-mono text-neutral-900 dark:text-neutral-100">{{ request.domain }}</span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">Query Type</span>
        <span>
          <span class="rounded bg-neutral-100 px-1.5 py-0.5 font-mono text-xs text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300">
            {{ request.qType }}
          </span>
        </span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">RCode</span>
        <span><RcodeBadge :rcode="request.rcode" /></span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">Source</span>
        <span class="text-neutral-900 dark:text-neutral-100">{{ request.source }}</span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">Protocol</span>
        <span class="text-neutral-900 dark:text-neutral-100">{{ request.protocol }}</span>

        <span class="font-medium text-neutral-500 dark:text-neutral-400">Latency</span>
        <span class="font-semibold text-neutral-900 dark:text-neutral-100">{{ formatLatency(request.latencyMs) }}</span>
      </div>

      <div class="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
        <p class="mb-2 text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Latency</p>
        <div class="flex items-end gap-1">
          <div
            class="h-4 rounded-sm bg-blue-500"
            :style="{ width: `${request.latencyMs === null ? 0 : Math.min(100, (request.latencyMs / 5000) * 100)}%`, minWidth: '4px' }"
          />
          <span class="text-sm font-semibold text-neutral-900 dark:text-neutral-100">{{ formatLatency(request.latencyMs) }}</span>
        </div>
        <div class="mt-1 flex justify-between text-xs text-neutral-400">
          <span>0</span><span>5 s</span>
        </div>
      </div>

      <div v-if="request.resolutionTrace" class="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
        <p class="mb-2 text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Resolution Trace</p>
        <pre class="whitespace-pre-wrap break-all font-mono text-xs text-neutral-700 dark:text-neutral-300">{{ request.resolutionTrace }}</pre>
      </div>
    </div>

    <p v-else class="text-sm text-neutral-500">No request selected.</p>
  </Sheet>
</template>
