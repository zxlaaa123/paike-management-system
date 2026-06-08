import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BaseLayout from '../layout/BaseLayout.vue'
import { useAuthStore } from '../stores/auth'

type RouteParamGuard = {
  params: string[]
  redirect: (params: Record<string, string>) => string
}

const routeParamGuards: Record<string, RouteParamGuard> = {
  SchedulePlanDetail: {
    params: ['id'],
    redirect: () => '/v3/schedule-plans',
  },
  V4ScheduleAnalysisDetail: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V4ScheduleScoreDetail: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V4ScheduleRiskCenter: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V4ScheduleCharts: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V4ScheduleLocks: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V4ScheduleReports: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V4ScheduleAiAnalysis: {
    params: ['planId'],
    redirect: () => '/v4/schedule-analysis',
  },
  V5RepairTaskDetail: {
    params: ['taskId'],
    redirect: () => '/v5/repair-tasks',
  },
  V5SimulationPlanDetail: {
    params: ['taskId', 'planId'],
    redirect: (params) => (params.taskId ? `/v5/repair-tasks/${params.taskId}` : '/v5/repair-tasks'),
  },
}

function parsePositiveIntegerRouteParam(value: unknown): string | null {
  if (Array.isArray(value)) {
    return null
  }

  const text = String(value ?? '').trim()
  if (!/^[1-9]\d*$/.test(text)) {
    return null
  }

  return text
}

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
        path: 'v5/repair-tasks',
        name: 'V5RepairTaskList',
        component: () => import('../views/v5/RepairTaskListView.vue'),
      },
      {
        path: 'v5/repair-tasks/:taskId',
        name: 'V5RepairTaskDetail',
        component: () => import('../views/v5/RepairTaskDetailView.vue'),
      },
      {
        path: 'v5/repair-tasks/:taskId/simulations/:planId',
        name: 'V5SimulationPlanDetail',
        component: () => import('../views/v5/SimulationPlanDetailView.vue'),
      },
      {
        path: 'v5/candidate-positions',
        name: 'V5CandidatePositions',
        component: () => import('../views/v5/CandidatePositionView.vue'),
      },
      {
        path: 'v6/audit-logs',
        name: 'V6AuditLogs',
        component: () => import('../views/v6/AuditLogView.vue'),
      },
      {
        path: 'v6/regression-tests',
        name: 'V6RegressionTests',
        component: () => import('../views/v6/RegressionTestView.vue'),
      },
      {
        path: 'v6/consistency-checks',
        name: 'V6ConsistencyChecks',
        component: () => import('../views/v6/ConsistencyCheckView.vue'),
      },
      {
        path: 'v6/performance-baselines',
        name: 'V6PerformanceBaselines',
        component: () => import('../views/v6/PerformanceBaselineView.vue'),
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
        path: 'v4/schedule-analysis/:planId/locks',
        name: 'V4ScheduleLocks',
        component: () => import('../views/v4/ScheduleLockManage.vue'),
      },
      {
        path: 'v4/schedule-analysis/:planId/reports',
        name: 'V4ScheduleReports',
        component: () => import('../views/v4/ScheduleReports.vue'),
      },
      {
        path: 'v4/schedule-analysis/:planId/ai',
        name: 'V4ScheduleAiAnalysis',
        component: () => import('../views/v4/ScheduleAiAnalysis.vue'),
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
    redirect: '/login',
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
    if (to.path === '/login' && authStore.userInfo) {
      return '/dashboard'
    }
    return
  }

  if (!authStore.userInfo) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const guard = routeParamGuards[String(to.name)]
  if (guard) {
    const params: Record<string, string> = {}
    for (const paramName of guard.params) {
      const value = parsePositiveIntegerRouteParam(to.params[paramName])
      if (!value) {
        return guard.redirect(params)
      }
      params[paramName] = value
    }
  }
})

export default router
