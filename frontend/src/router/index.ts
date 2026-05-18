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
        path: 'semesters',
        name: 'Semesters',
        component: () => import('../views/semester/SemesterView.vue'),
      },
      {
        path: 'teacher-unavailable-times',
        name: 'TeacherUnavailableTimes',
        component: () => import('../views/teacher/TeacherUnavailableTimeView.vue'),
      },
      {
        path: 'schedule-rules',
        name: 'ScheduleRules',
        component: () => import('../views/schedule/ScheduleRuleView.vue'),
      },
      {
        path: 'auto-schedule',
        name: 'AutoSchedule',
        component: () => import('../views/schedule/AutoScheduleView.vue'),
      },
      {
        path: 'unscheduled-tasks',
        name: 'UnscheduledTasks',
        component: () => import('../views/schedule/UnscheduledTaskView.vue'),
      },
      {
        path: 'schedule-conflict-reports',
        name: 'ScheduleConflictReports',
        component: () => import('../views/schedule/ScheduleConflictReportView.vue'),
      },
      {
        path: 'schedule-score',
        name: 'ScheduleScore',
        component: () => import('../views/schedule/ScheduleScoreReportView.vue'),
      },
      {
        path: 'schedule',
        name: 'Schedule',
        component: () => import('../views/schedule/ScheduleView.vue'),
      },
      {
        path: 'v3/schedule-plans',
        name: 'SchedulePlans',
        component: () => import('../views/schedule/SchedulePlanView.vue'),
      },
      {
        path: 'v3/schedule-generate',
        name: 'ScheduleGenerate',
        component: () => import('../views/schedule/ScheduleGenerateView.vue'),
      },
      {
        path: 'v3/schedule-plans/:id',
        name: 'SchedulePlanDetail',
        component: () => import('../views/schedule/SchedulePlanDetailView.vue'),
      },
      {
        path: 'v3/schedule-rules',
        name: 'ScheduleRuleWeights',
        component: () => import('../views/schedule/ScheduleRuleWeightView.vue'),
      },
      {
        path: 'v3/schedule-compare',
        name: 'ScheduleCompare',
        component: () => import('../views/schedule/ScheduleCompareView.vue'),
      },
      {
        path: 'v3/statistics',
        name: 'ScheduleStatistics',
        component: () => import('../views/schedule/ScheduleStatisticsView.vue'),
      },
      {
        path: 'v4/schedule-analysis',
        name: 'V4ScheduleAnalysisOverview',
        component: () => import('../views/v4/ScheduleAnalysisOverview.vue'),
      },
      {
        path: 'v4/schedule-analysis/:planId',
        name: 'V4ScheduleAnalysisDetail',
        component: () => import('../views/v4/ScheduleAnalysisDetail.vue'),
      },
      {
        path: 'v4/schedule-analysis/:planId/score',
        name: 'V4ScheduleScoreDetail',
        component: () => import('../views/v4/ScheduleScoreDetail.vue'),
      },
      {
        path: 'v4/schedule-analysis/:planId/risks',
        name: 'V4ScheduleRiskCenter',
        component: () => import('../views/v4/ScheduleRiskCenter.vue'),
      },
      {
        path: 'v4/schedule-analysis/:planId/charts',
        name: 'V4ScheduleCharts',
        component: () => import('../views/v4/ScheduleCharts.vue'),
      },
      {
        path: 'timetable/class',
        name: 'ClassTimetable',
        component: () => import('../views/timetable/ClassTimetableView.vue'),
      },
      {
        path: 'timetable/teacher',
        name: 'TeacherTimetable',
        component: () => import('../views/timetable/TeacherTimetableView.vue'),
      },
      {
        path: 'timetable/classroom',
        name: 'ClassroomTimetable',
        component: () => import('../views/timetable/ClassroomTimetableView.vue'),
      },
      {
        path: 'placeholder/:module',
        name: 'Placeholder',
        component: () => import('../views/PlaceholderView.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/dashboard',
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
