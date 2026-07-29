<script setup lang="ts">
import DButton from '@/components/ui/DButton.vue'
import { DNS_RCODES, RR_TYPES, RESPONSE_SOURCES } from '@/types/dns'
import type { DNSRcode, RRType, ResponseSource } from '@/types/dns'

const props = defineProps<{
  domainSearch: string
  selectedRcodes: DNSRcode[]
  selectedRRTypes: RRType[]
  selectedSources: ResponseSource[]
}>()

const emit = defineEmits<{
  'update:domainSearch': [v: string]
  'update:selectedRcodes': [v: DNSRcode[]]
  'update:selectedRRTypes': [v: RRType[]]
  'update:selectedSources': [v: ResponseSource[]]
  reset: []
}>()

function toggleItem<T>(arr: T[], item: T): T[] {
  return arr.includes(item) ? arr.filter(x => x !== item) : [...arr, item]
}

const RCODE_LABELS: Record<DNSRcode, string> = {
  NOERROR: 'NoError', FORMERR: 'FormErr', SERVFAIL: 'ServFail', NXDOMAIN: 'NXDomain',
  NOTIMP: 'NotImp', REFUSED: 'Refused', OTHER: 'Other', NO_RESPONSE: 'No Response',
}

const hasFilters = () =>
  props.domainSearch.length > 0 ||
  props.selectedRcodes.length > 0 ||
  props.selectedRRTypes.length > 0 ||
  props.selectedSources.length > 0
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center gap-3">
      <div class="relative flex-1">
        <svg class="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-neutral-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input
          :value="domainSearch"
          placeholder="Search domain…"
          class="h-9 w-full rounded-lg border border-neutral-200 bg-white pl-9 pr-3 text-sm text-neutral-900 placeholder:text-neutral-400 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100 dark:placeholder:text-neutral-500"
          @input="emit('update:domainSearch', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <DButton v-if="hasFilters()" variant="ghost" size="sm" @click="emit('reset')">
        Clear filters
      </DButton>
    </div>

    <div class="flex flex-wrap gap-2">
      <button
        v-for="rc in DNS_RCODES"
        :key="rc"
        :class="[
          'rounded-full border px-2.5 py-1 text-xs font-medium transition-colors',
          selectedRcodes.includes(rc)
            ? 'border-blue-600 bg-blue-600 text-white'
            : 'border-neutral-200 bg-white text-neutral-600 hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400 dark:hover:bg-neutral-800',
        ]"
        @click="emit('update:selectedRcodes', toggleItem(selectedRcodes, rc))"
      >
        {{ RCODE_LABELS[rc] }}
      </button>
    </div>

    <div class="flex flex-wrap gap-2">
      <button
        v-for="rt in RR_TYPES"
        :key="rt"
        :class="[
          'rounded-full border px-2.5 py-1 text-xs font-medium transition-colors',
          selectedRRTypes.includes(rt)
            ? 'border-purple-600 bg-purple-600 text-white'
            : 'border-neutral-200 bg-white text-neutral-600 hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400 dark:hover:bg-neutral-800',
        ]"
        @click="emit('update:selectedRRTypes', toggleItem(selectedRRTypes, rt))"
      >
        {{ rt }}
      </button>

      <div class="mx-1 w-px bg-neutral-200 dark:bg-neutral-700" />

      <button
        v-for="src in RESPONSE_SOURCES"
        :key="src"
        :class="[
          'rounded-full border px-2.5 py-1 text-xs font-medium transition-colors',
          selectedSources.includes(src)
            ? 'border-green-600 bg-green-600 text-white'
            : 'border-neutral-200 bg-white text-neutral-600 hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400 dark:hover:bg-neutral-800',
        ]"
        @click="emit('update:selectedSources', toggleItem(selectedSources, src))"
      >
        {{ src }}
      </button>
    </div>
  </div>
</template>
