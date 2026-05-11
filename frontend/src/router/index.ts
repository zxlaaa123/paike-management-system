import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BaseLayout from '../layout/BaseLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/LoginView.vue'),
  },
  {
    path: '/',
    component: BaseLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
      },
      {
        path: 'placeholder/:module',
        name: 'Placeholder',
        component: () => import('../views/PlaceholderView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
