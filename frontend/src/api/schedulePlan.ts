import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface SchedulePlan {
  id: number
  sourcePlanId?: number | null
  sourceScheduleId?: number | null
  repairTaskId?: number | null
  semesterId: number
  semesterName?: string
  name: string
  strategyType: string
  strategyName?: string
  status: string
  totalScore: number | null
  scheduledCount: number
  unscheduledCount: number
  conflictCount: number
  description: string
  generatedBy: string
  generatedAt: string | null
  appliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SchedulePlanItem {
  id: number
  planId: number
  teachingTaskId: number
  teacherId: number
  classId: number
  courseId: number
  classroomId: number
  weekday: number
  startPeriod: number
  endPeriod: number
  weekType: string
  score: number | null
  conflictFlag: number
  conflictReason: string
  sourceType: string
  courseName?: string
  teacherName?: string
  className?: string
  roomName?: string
  timeLabel?: string
  createdAt: string
}

export interface ScheduleGenerateLog {
  id: number
  planId: number
  semesterId: number
  teachingTaskId: number | null
  logLevel: string
  logType: string
  message: string
  stepNo: number | null
  createdAt: string
}

export interface ScheduleUnassignedTask {
  id: number
  planId: number
  semesterId: number
  teachingTaskId: number
  reasonCode: string
  reasonMessage: string
  suggestion: string | null
  courseName?: string
  teacherName?: string
  className?: string
  createdAt: string
}

export interface ScheduleAdjustLog {
  id: number
  planId: number | null
  scheduleId: number | null
  semesterId: number
  teachingTaskId: number
  oldClassroomId: number | null
  oldWeekday: number | null
  oldStartPeriod: number | null
  oldEndPeriod: number | null
  newClassroomId: number | null
  newWeekday: number | null
  newStartPeriod: number | null
  newEndPeriod: number | null
  beforeScore: number | null
  afterScore: number | null
  conflictFlag: number
  adjustReason: string | null
  createdAt: string
  courseName?: string
  teacherName?: string
  className?: string
  oldClassroomName?: string
  newClassroomName?: string
}

export interface UnassignedSummaryItem {
  reasonCode: string
  reasonName: string
  count: number
}

export function getSchedulePlanList(params: {
  semesterId?: number
  status?: string
  strategyType?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<SchedulePlan>>>('/v3/schedule-plans', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSchedulePlanById(id: number) {
  return request.get<ApiResponse<SchedulePlan>>(`/v3/schedule-plans/${id}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSchedulePlanItems(planId: number) {
  return request.get<ApiResponse<SchedulePlanItem[]>>(`/v3/schedule-plans/${planId}/items`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function deleteSchedulePlan(id: number) {
  return request.delete(`/v3/schedule-plans/${id}`)
}

export function abandonSchedulePlan(id: number) {
  return request.put(`/v3/schedule-plans/${id}/abandon`)
}

export interface ComparePlan {
  planId: number
  planName: string
  strategyType: string
  strategyName: string
  status: string
  totalScore: number
  scheduledCount: number
  unscheduledCount: number
  conflictCount: number
  hardViolationCount: number
  softViolationCount: number
  generatedAt: string | null
}

export interface CompareResult {
  semesterId: number
  plans: ComparePlan[]
  bestPlanId: number
  summary: string
}

export function compareSchedulePlans(data: {
  semesterId?: number
  planIds: number[]
}) {
  return request.post<ApiResponse<CompareResult>>('/v3/schedule-plans/compare', data).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function applySchedulePlan(id: number) {
  return request.post<ApiResponse<{
    planId: number
    semesterId: number
    appliedCount: number
    appliedAt: string
  }>>(`/v3/schedule-plans/${id}/apply`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function rollbackSchedulePlan(id: number) {
  return request.post<ApiResponse<{
    planId: number
    semesterId: number
    appliedCount: number
    appliedAt: string
  }>>(`/v3/schedule-plans/${id}/rollback`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSchedulePlanLogs(
  planId: number,
  params?: { logLevel?: string; logType?: string; teachingTaskId?: number }
) {
  return request.get<ApiResponse<ScheduleGenerateLog[]>>(`/v3/schedule-plans/${planId}/logs`, { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSchedulePlanTaskLogs(planId: number, taskId: number) {
  return request.get<ApiResponse<ScheduleGenerateLog[]>>(`/v3/schedule-plans/${planId}/tasks/${taskId}/logs`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSchedulePlanUnassignedTasks(planId: number) {
  return request.get<ApiResponse<ScheduleUnassignedTask[]>>(`/v3/schedule-plans/${planId}/unassigned-tasks`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSchedulePlanUnassignedSummary(planId: number) {
  return request.get<ApiResponse<UnassignedSummaryItem[]>>(`/v3/schedule-plans/${planId}/unassigned-summary`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function adjustSchedulePlanItem(
  itemId: number,
  payload: { classroomId: number; weekday: number; startPeriod: number; endPeriod: number; adjustReason: string }
) {
  return request.put<ApiResponse<{
    itemId: number
    planId: number
    beforeScore: number | null
    afterScore: number | null
    conflictFlag: number
    conflictReason: string | null
    syncFormalSchedule: boolean
    scheduleId: number | null
    message: string
  }>>(`/v3/schedule-plan-items/${itemId}/adjust`, payload, { timeout: 120000 }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleAdjustLogs(params?: {
  semesterId?: number
  planId?: number
  teachingTaskId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request.get<ApiResponse<PageResult<ScheduleAdjustLog>>>('/v3/schedule-adjust-logs', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
