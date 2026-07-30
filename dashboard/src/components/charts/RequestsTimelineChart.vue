<script setup lang="ts">
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import type { ChartData, ChartOptions } from 'chart.js'
import { formatDateShort } from '@/utils/format'
import { CHART_GRID_COLOR, CHART_TICK_COLOR, alpha } from '@/utils/chartColors'
import type { TimelinePoint } from '@/api/types'

const props = defineProps<{ data: TimelinePoint[] }>()

const chartData = computed<ChartData<'line'>>(() => ({
  labels: props.data.map(p => formatDateShort(p.timestamp)),
  datasets: [
    {
      label: 'Requests',
      data: props.data.map(p => p.count),
      borderColor: '#3b82f6',
      backgroundColor: alpha('#3b82f6', 0.12),
      fill: true,
      tension: 0.4,
      pointRadius: 0,
      pointHoverRadius: 4,
      borderWidth: 2,
    },
  ],
}))

const options: ChartOptions<'line'> = {
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  interaction: { mode: 'index', intersect: false },
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
      grid: { color: CHART_GRID_COLOR },
      ticks: { color: CHART_TICK_COLOR, maxTicksLimit: 8, font: { size: 11 } },
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
  <Line :data="chartData" :options="options" />
</template>
