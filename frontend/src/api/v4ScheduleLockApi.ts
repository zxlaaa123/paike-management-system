import request from '../utils/request'
import type { ApiResponse } from './types'

export type ScheduleLockTargetType = 'PLAN' | 'SCHEDULE'

export interface ScheduleLockItem {
  lockId: number
  targetType: ScheduleLockTargetType
  planId: number | null
  planItemId: number | null
  scheduleId: number | null
  teachingTaskId: number | null
  courseName: string | null
  teacherName: string | null
  className: string | null
  weekDay: number | null
  period: string | null
  roomName: string | null
  lockReason: string
  createdAt: string
}

export interface ScheduleLockList {
  planId: number
  planName: string
  lockedCount: number
  items: ScheduleLockItem[]
}

export interface ScheduleLockActionResult {
  locked: boolean
  unlocked: boolean
  lockId: number | null
  planId: number | null
  planItemId: number | null
  scheduleId: number | null
  message: string
}

export interface ScheduleLockRequest {
  targetType: ScheduleLockTargetType
  planId?: number
  planItemId?: number
  scheduleId?: number
  lockReason?: string
}

export function getScheduleLockList(planId: number) {
  return request.get<ApiResponse<ScheduleLockList>>(`/v4/schedule-locks/plans/${planId}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function lockScheduleItem(data: ScheduleLockRequest) {
  return request.post<ApiResponse<ScheduleLockActionResult>>('/v4/schedule-locks/lock', data).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function unlockScheduleItem(data: ScheduleLockRequest) {
  return request.post<ApiResponse<ScheduleLockActionResult>>('/v4/schedule-locks/unlock', data).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
