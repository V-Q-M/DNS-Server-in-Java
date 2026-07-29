<script setup lang="ts">
import Card from '@/components/ui/Card.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { formatTrend } from '@/utils/format'

defineProps<{
  title: string
  value: string
  trend?: number
  trendInverted?: boolean
  loading?: boolean
  icon?: string
}>()

function trendClass(trend: number, inverted = false): string {
  const good = inverted ? trend < 0 : trend > 0
  if (trend === 0) return 'text-neutral-400'
  return good ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400'
}
</script>

<template>
  <Card class="p-5">
    <div class="flex items-start justify-between">
      <div class="flex-1">
        <p class="text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">{{ title }}</p>

        <Skeleton v-if="loading" class="mt-2 h-8 w-32" />
        <p v-else class="mt-1 text-3xl font-bold tracking-tight text-neutral-900 dark:text-neutral-50">
          {{ value }}
        </p>

        <div v-if="trend !== undefined && !loading" class="mt-1.5 flex items-center gap-1">
          <span :class="trendClass(trend, trendInverted)" class="flex items-center gap-0.5 text-xs font-medium">
            <svg v-if="trend > 0" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 10l7-7m0 0l7 7m-7-7v18" />
            </svg>
            <svg v-else-if="trend < 0" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
            </svg>
            {{ formatTrend(trend) }}
          </span>
          <span class="text-xs text-neutral-400">vs prev period</span>
        </div>
      </div>

      <div
        v-if="icon"
        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400"
        v-html="icon"
      />
    </div>
  </Card>
</template>
