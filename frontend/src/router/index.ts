import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BaseLayout from '../layout/BaseLayout.vue'
import { useAuthStore } from '../stores/auth'

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
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
      },
      {
        path: 'teachers',
        name: 'Teachers',
        component: () => import('../views/teacher/TeacherView.vue'),
      },
      {
        path: 'classes',
        name: 'Classes',
        component: () => import('../views/classInfo/ClassInfoView.vue'),
      },
      {
        path: 'classrooms',
        name: 'Classrooms',
        component: () => import('../views/classroom/ClassroomView.vue'),
      },
      {
        path: 'courses',
        name: 'Courses',
        component: () => import('../views/course/CourseView.vue'),
      },
      {
        path: 'teaching-tasks',
        name: 'TeachingTasks',
        component: () => import('../views/teachingTask/TeachingTaskView.vue'),
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

router.beforeEach(async (to, _from) => {
  const authStore = useAuthStore()
  const needAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (!needAuth) {
    if (to.path === '/login' && authStore.isLoggedIn) {
      return '/dashboard'
    }
    return
  }

  if (!authStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!authStore.userInfo) {
    try {
      await authStore.fetchCurrentUser()
    } catch (_error) {
      await authStore.logout()
      return '/login'
    }
  }
})

export default router
