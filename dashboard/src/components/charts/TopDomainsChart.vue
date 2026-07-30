<script setup lang="ts">
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import type { ChartData, ChartOptions } from 'chart.js'
import { CHART_GRID_COLOR, CHART_TICK_COLOR } from '@/utils/chartColors'
import type { DomainStat } from '@/api/types'

const props = defineProps<{ data: DomainStat[] }>()

const chartData = computed<ChartData<'bar'>>(() => ({
  labels: props.data.map(d => d.domain),
  datasets: [{
    label: 'Queries',
    data: props.data.map(d => d.count),
    backgroundColor: '#8b5cf6',
    borderRadius: 5,
    borderSkipped: false,
  }],
}))

const options: ChartOptions<'bar'> = {
  indexAxis: 'y',
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
      grid: { color: CHART_GRID_COLOR },
      ticks: { color: CHART_TICK_COLOR, font: { size: 11 } },
      beginAtZero: true,
    },
    y: {
      grid: { display: false },
      ticks: { color: CHART_TICK_COLOR, font: { size: 11 } },
    },
  },
}
</script>

<template>
  <Bar :data="chartData" :options="options" />
</template>
