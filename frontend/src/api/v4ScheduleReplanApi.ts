import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ScheduleReplanRequest {
  newPlanName: string
  keepLocked?: boolean
  strategyCode?: string
  forceGenerate?: boolean
}

export interface ScheduleReplanResult {
  sourcePlanId: number
  sourcePlanName: string
  newPlanId: number
  newPlanName: string
  lockedCount: number
  replanableCount: number
  scheduledCount: number
  unscheduledCount: number
  conflictCount: number
  totalScore: number | null
  keepLocked: boolean
  strategyCode: string
  minimalMode: boolean
  message: string
}

export function createLocalReplanPlan(planId: number, data: ScheduleReplanRequest) {
  return request.post<ApiResponse<ScheduleReplanResult>>(`/v4/schedule-replan/plans/${planId}`, data, { timeout: 120000 }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
