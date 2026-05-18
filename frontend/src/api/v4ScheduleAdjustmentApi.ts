import request from '../utils/request'
import type { ApiResponse } from './types'

export type ScheduleAdjustmentTargetType = 'PLAN_ITEM' | 'SCHEDULE'

export interface ScheduleAdjustmentIssue {
  issueType: string
  issueName: string
  blocking: boolean
  message: string
}

export interface ScheduleAdjustmentCheckResult {
  targetType: ScheduleAdjustmentTargetType
  planId: number | null
  planItemId: number | null
  scheduleId: number | null
  courseName: string | null
  teacherName: string | null
  className: string | null
  currentRoomId: number | null
  currentRoomName: string | null
  currentWeekDay: number | null
  currentPeriodStart: number | null
  currentPeriodEnd: number | null
  currentTimeLabel: string | null
  newRoomId: number
  newRoomName: string | null
  newWeekDay: number
  newPeriodStart: number
  newPeriodEnd: number
  newTimeLabel: string | null
  hasConflict: boolean
  issueCount: number
  blockingIssueCount: number
  canApply: boolean
  issues: ScheduleAdjustmentIssue[]
}

export interface ScheduleAdjustmentApplyResult {
  saved: boolean
  requiresConfirmation: boolean
  syncFormalSchedule: boolean
  syncPlanItem: boolean
  message: string
  planId: number | null
  planItemId: number | null
  scheduleId: number | null
  checkResult: ScheduleAdjustmentCheckResult
}

export interface ScheduleAdjustmentPayload {
  targetType: ScheduleAdjustmentTargetType
  planId?: number
  planItemId?: number
  scheduleId?: number
  newWeekDay: number
  newPeriodStart: number
  newPeriodEnd: number
  newRoomId: number
  adjustReason?: string
  forceAdjust?: boolean
}

export function checkScheduleAdjustment(payload: ScheduleAdjustmentPayload) {
  return request.post<ApiResponse<ScheduleAdjustmentCheckResult>>('/v4/schedule-adjustments/check', payload).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function applyScheduleAdjustment(payload: ScheduleAdjustmentPayload) {
  return request.post<ApiResponse<ScheduleAdjustmentApplyResult>>('/v4/schedule-adjustments/apply', payload, { timeout: 120000 }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
