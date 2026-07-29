<script setup lang="ts">
import Skeleton from '@/components/ui/Skeleton.vue'
import DButton from '@/components/ui/DButton.vue'
import RcodeBadge from './RcodeBadge.vue'
import { formatDate, formatLatency } from '@/utils/format'
import type { DNSRequestRow, RequestSortColumn, SortDirection } from '@/api/types'

const props = defineProps<{
  rows: DNSRequestRow[]
  page: number
  pageSize: number
  loading?: boolean
  sortColumn: RequestSortColumn
  sortDirection: SortDirection
}>()

const emit = defineEmits<{
  'row-click': [row: DNSRequestRow]
  'update:page': [n: number]
  'sort': [column: RequestSortColumn]
}>()

function sortIcon(column: RequestSortColumn): string {
  if (props.sortColumn !== column) return ''
  return props.sortDirection === 'asc' ? '↑' : '↓'
}
</script>

<template>
  <div class="overflow-hidden rounded-xl border border-neutral-200 dark:border-neutral-800">
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-neutral-200 bg-neutral-50 dark:border-neutral-800 dark:bg-neutral-900">
            <th
              class="cursor-pointer select-none px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
              @click="emit('sort', 'received_at')"
            >
              Timestamp {{ sortIcon('received_at') }}
            </th>
            <th
              class="cursor-pointer select-none px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
              @click="emit('sort', 'qname')"
            >
              Domain {{ sortIcon('qname') }}
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Type</th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">RCode</th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Source</th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-neutral-500 dark:text-neutral-400">Protocol</th>
            <th
              class="cursor-pointer select-none px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
              @click="emit('sort', 'latency')"
            >
              Latency {{ sortIcon('latency') }}
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-if="loading">
            <tr v-for="i in pageSize" :key="i" class="border-b border-neutral-100 dark:border-neutral-800">
              <td class="px-4 py-3" v-for="j in 7" :key="j">
                <Skeleton class="h-4 w-full" />
              </td>
            </tr>
          </template>

          <template v-else>
            <tr
              v-for="row in rows"
              :key="row.id"
              class="cursor-pointer border-b border-neutral-100 transition-colors last:border-0 hover:bg-neutral-50 dark:border-neutral-800 dark:hover:bg-neutral-800/50"
              @click="emit('row-click', row)"
            >
              <td class="px-4 py-3 text-xs text-neutral-500 dark:text-neutral-400">{{ formatDate(row.timestamp) }}</td>
              <td class="px-4 py-3 font-mono text-xs text-neutral-900 dark:text-neutral-100">{{ row.domain }}</td>
              <td class="px-4 py-3">
                <span class="rounded bg-neutral-100 px-1.5 py-0.5 font-mono text-xs text-neutral-600 dark:bg-neutral-800 dark:text-neutral-400">
                  {{ row.qType }}
                </span>
              </td>
              <td class="px-4 py-3"><RcodeBadge :rcode="row.rcode" /></td>
              <td class="px-4 py-3 text-xs text-neutral-500 dark:text-neutral-400">{{ row.source }}</td>
              <td class="px-4 py-3 text-xs text-neutral-500 dark:text-neutral-400">{{ row.protocol }}</td>
              <td class="px-4 py-3 text-right font-mono text-xs text-neutral-700 dark:text-neutral-300">
                {{ formatLatency(row.latencyMs) }}
              </td>
            </tr>

            <tr v-if="rows.length === 0">
              <td colspan="7" class="px-4 py-12 text-center text-sm text-neutral-400">
                No requests match the current filters.
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div class="flex items-center justify-between border-t border-neutral-200 bg-neutral-50 px-4 py-3 dark:border-neutral-800 dark:bg-neutral-900">
      <p class="text-xs text-neutral-500 dark:text-neutral-400">Page {{ page + 1 }}</p>
      <div class="flex items-center gap-1.5">
        <DButton variant="outline" size="sm" :disabled="page <= 0" @click="emit('update:page', page - 1)">
          ← Prev
        </DButton>
        <DButton variant="outline" size="sm" :disabled="rows.length < pageSize" @click="emit('update:page', page + 1)">
          Next →
        </DButton>
      </div>
    </div>
  </div>
</template>
