import request from '../utils/request'
import type { ApiResponse } from './types'

// ==================== 教师工作量统计 ====================

export interface TeacherWorkloadItem {
  teacherId: number
  teacherName: string
  department: string | null
  totalPeriods: number
  maxDailyPeriods: number
  maxContinuousPeriods: number
  courseCount: number
  classCount: number
  dailyPeriods: Record<number, number>
  evaluation: string
}

export function getTeacherWorkload(params?: { semesterId?: number; planId?: number }) {
  return request.get<ApiResponse<TeacherWorkloadItem[]>>('/v3/statistics/teacher-workload', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

// ==================== 教室利用率统计 ====================

export interface ClassroomUtilizationItem {
  roomId: number
  roomName: string
  building: string | null
  capacity: number | null
  roomType: string | null
  usedPeriods: number
  totalPeriods: number
  utilizationRate: number
  evaluation: string
}

export function getClassroomUtilization(params?: { semesterId?: number; planId?: number }) {
  return request.get<ApiResponse<ClassroomUtilizationItem[]>>('/v3/statistics/classroom-utilization', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

// ==================== 班级均衡度统计 ====================

export interface ClassBalanceItem {
  classId: number
  className: string
  studentCount: number
  totalPeriods: number
  balanceScore: number
  evaluation: string
  day1Periods: number
  day2Periods: number
  day3Periods: number
  day4Periods: number
  day5Periods: number
}

export function getClassBalance(params?: { semesterId?: number; planId?: number }) {
  return request.get<ApiResponse<ClassBalanceItem[]>>('/v3/statistics/class-balance', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

// ==================== 方案统计总览 ====================

export interface PlanOverview {
  semesterId: number
  totalPlans: number
  draftPlans: number
  appliedPlans: number
  abandonedPlans: number
  bestPlanId: number | null
  bestPlanName: string | null
  bestPlanScore: number | null
  bestPlanStrategy: string | null
  hasAppliedPlan: boolean
  appliedPlanId: number | null
  appliedPlanName: string | null
  appliedPlanScore: number | null
  appliedPlanAppliedAt: string | null
  formalScheduleCount: number
  totalUnassignedTasks: number
  totalConflicts: number
}

export function getPlanOverview(params?: { semesterId?: number }) {
  return request.get<ApiResponse<PlanOverview>>('/v3/statistics/plan-overview', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

// ==================== 首页统计 ====================

export interface DashboardStats {
  teacherCount: number
  classCount: number
  classroomCount: number
  courseCount: number
  teachingTaskCount: number
  totalUnassignedTasks: number
  totalConflicts: number
  hasAppliedPlan: boolean
  governanceSummary: string
  v3Overview: PlanOverview
}

export function getDashboardStats(params?: { semesterId?: number }) {
  return request.get<ApiResponse<DashboardStats>>('/v3/statistics/dashboard', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
