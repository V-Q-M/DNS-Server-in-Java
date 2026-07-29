<script setup lang="ts">
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import type { ChartData, ChartOptions } from 'chart.js'
import { CHART_GRID_COLOR, CHART_TICK_COLOR } from '@/utils/chartColors'
import type { LatencyBucket } from '@/api/types'

const props = defineProps<{ data: LatencyBucket[] }>()

const chartData = computed<ChartData<'bar'>>(() => ({
  labels: props.data.map(b => b.label),
  datasets: [{
    label: 'Queries',
    data: props.data.map(b => b.count),
    backgroundColor: '#3b82f6',
    borderRadius: 5,
    borderSkipped: false,
  }],
}))

const options: ChartOptions<'bar'> = {
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: '#1e1e2e',
      titleColor: '#e2e8f0',
      bodyColor: '#94a3b8',
      borderColor: '#334155',
      borderWidth: 1,
      padding: 10,
    },
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: { color: CHART_TICK_COLOR, font: { size: 11 } },
    },
    y: {
      grid: { color: CHART_GRID_COLOR },
      ticks: { color: CHART_TICK_COLOR, font: { size: 11 } },
      beginAtZero: true,
    },
  },
}
</script>

<template>
  <Bar :data="chartData" :options="options" />
</template>
