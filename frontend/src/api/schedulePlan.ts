import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface SchedulePlan {
  id: number
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

export function compareSchedulePlans(data: {
  semesterId?: number
  planIds: number[]
}) {
  return request.post<ApiResponse<{
    semesterId: number
    plans: Array<{
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
    }>
    bestPlanId: number
    summary: string
  }>>('/v3/schedule-plans/compare', data).then((r) => {
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
