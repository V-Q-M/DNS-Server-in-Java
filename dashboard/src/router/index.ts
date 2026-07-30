import { createRouter, createWebHistory } from 'vue-router'
import OverviewPage   from '@/pages/OverviewPage.vue'
import AnalyticsPage  from '@/pages/AnalyticsPage.vue'
import CachePage      from '@/pages/CachePage.vue'
import RequestsPage   from '@/pages/RequestsPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',           name: 'overview',   component: OverviewPage,  meta: { title: 'Overview'  } },
    { path: '/analytics',  name: 'analytics',  component: AnalyticsPage, meta: { title: 'Analytics' } },
    { path: '/cache',      name: 'cache',       component: CachePage,     meta: { title: 'Cache'     } },
    { path: '/requests',   name: 'requests',   component: RequestsPage,  meta: { title: 'Requests'  } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
