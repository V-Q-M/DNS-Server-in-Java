import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  Chart as ChartJS,
  ArcElement,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Filler,
  Tooltip,
  Legend,
} from 'chart.js'

import App from './App.vue'
import { router } from './router'
import { STATS_SERVICE_KEY } from './api/StatsService'
import { HttpStatsService } from './api/HttpStatsService'
import './assets/main.css'

ChartJS.register(
  ArcElement,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Filler,
  Tooltip,
  Legend,
)

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.provide(STATS_SERVICE_KEY, new HttpStatsService(import.meta.env.VITE_API_BASE_URL ?? ''))

app.mount('#app')
