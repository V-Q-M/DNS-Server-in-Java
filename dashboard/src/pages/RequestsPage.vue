<script setup lang="ts">
import { ref, watch, inject } from 'vue'
import { useSettingsStore } from '@/stores/settingsStore'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { STATS_SERVICE_KEY } from '@/api/StatsService'
import type { StatsService } from '@/api/StatsService'
import type { DNSRequestRow, DNSRequestDetail, RequestSortColumn, SortDirection } from '@/api/types'
import type { DNSRcode, RRType, ResponseSource } from '@/types/dns'
import { startTimeFor } from '@/utils/timeRange'
import Card from '@/components/ui/Card.vue'
import RequestFilters from '@/components/requests/RequestFilters.vue'
import RequestsTable from '@/components/requests/RequestsTable.vue'
import RequestDetailSheet from '@/components/requests/RequestDetailSheet.vue'

const settings = useSettingsStore()
const service  = inject<StatsService>(STATS_SERVICE_KEY)!

const rows          = ref<DNSRequestRow[]>([])
const page          = ref(0)
const pageSize      = 50
const sortColumn    = ref<RequestSortColumn>('received_at')
const sortDirection = ref<SortDirection>('desc')
const loading       = ref(false)

const domainSearch    = ref('')
const selectedRcodes  = ref<DNSRcode[]>([])
const selectedRRTypes = ref<RRType[]>([])
const selectedSources = ref<ResponseSource[]>([])

const sheetOpen    = ref(false)
const sheetDetail  = ref<DNSRequestDetail | null>(null)
const sheetLoading = ref(false)

async function load() {
  loading.value = true
  rows.value = await service.getRequests({
    startTime:     startTimeFor(settings.timeRange),
    page:          page.value,
    sortColumn:    sortColumn.value,
    sortDirection: sortDirection.value,
    rcodes:        selectedRcodes.value.length  ? selectedRcodes.value  : undefined,
    rrTypes:       selectedRRTypes.value.length ? selectedRRTypes.value : undefined,
    sources:       selectedSources.value.length ? selectedSources.value : undefined,
    domainSearch:  domainSearch.value || undefined,
  })
  loading.value = false
}

function resetFilters() {
  domainSearch.value    = ''
  selectedRcodes.value  = []
  selectedRRTypes.value = []
  selectedSources.value = []
}

function sortBy(column: RequestSortColumn) {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortColumn.value    = column
    sortDirection.value = 'desc'
  }
}

async function openDetail(row: DNSRequestRow) {
  sheetOpen.value    = true
  sheetLoading.value = true
  sheetDetail.value  = await service.getRequest(row.id)
  sheetLoading.value = false
}

watch(
  [() => settings.timeRange, domainSearch, selectedRcodes, selectedRRTypes, selectedSources, sortColumn, sortDirection],
  () => { page.value = 0; load() },
  { immediate: true, deep: true },
)
watch(page, load)
useAutoRefresh(load)
</script>

<template>
  <div class="space-y-4">
    <Card class="p-4">
      <RequestFilters
        v-model:domain-search="domainSearch"
        v-model:selected-rcodes="selectedRcodes"
        v-model:selected-r-r-types="selectedRRTypes"
        v-model:selected-sources="selectedSources"
        @reset="resetFilters"
      />
    </Card>

    <RequestsTable
      :rows="rows"
      :page="page"
      :page-size="pageSize"
      :loading="loading"
      :sort-column="sortColumn"
      :sort-direction="sortDirection"
      @row-click="openDetail"
      @update:page="page = $event"
      @sort="sortBy"
    />

    <RequestDetailSheet
      :open="sheetOpen"
      :request="sheetDetail"
      :loading="sheetLoading"
      @close="sheetOpen = false"
    />
  </div>
</template>
