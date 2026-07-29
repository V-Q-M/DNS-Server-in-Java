<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import type { ChartData, ChartOptions } from 'chart.js'
import { RCODE_COLORS } from '@/utils/chartColors'
import type { DNSRcode } from '@/types/dns'

const props = defineProps<{ data: Partial<Record<DNSRcode, number>> }>()

const CHART_TICK_COLOR = 'rgba(115,115,115,0.8)'

const chartData = computed<ChartData<'doughnut'>>(() => {
  const entries = Object.entries(props.data).filter(([, v]) => (v ?? 0) > 0) as [DNSRcode, number][]
  return {
    labels: entries.map(([k]) => k),
    datasets: [{
      data: entries.map(([, v]) => v),
      backgroundColor: entries.map(([k]) => RCODE_COLORS[k]),
      borderWidth: 0,
      hoverOffset: 6,
    }],
  }
})

const options: ChartOptions<'doughnut'> = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '68%',
  animation: false,
  plugins: {
    legend: {
      position: 'right',
      labels: {
        color: CHART_TICK_COLOR,
        font: { size: 11 },
        padding: 12,
        boxWidth: 12,
        boxHeight: 12,
      },
    },
    tooltip: {
      backgroundColor: '#1e1e2e',
      titleColor: '#e2e8f0',
      bodyColor: '#94a3b8',
      borderColor: '#334155',
      borderWidth: 1,
      padding: 10,
    },
  },
}
</script>

<template>
  <Doughnut :data="chartData" :options="options" />
</template>
