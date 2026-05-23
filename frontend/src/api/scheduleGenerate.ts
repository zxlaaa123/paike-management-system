import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ScheduleGenerateResult {
  planId: number
  planName: string
  strategyType: string
  totalScore: number | null
  scheduledCount: number
  unscheduledCount: number
  conflictCount: number
}

export function generateSchedulePlan(data: {
  semesterId?: number
  strategyType: string
  planName?: string
  overwriteDraft?: boolean
}) {
  return request.post<ApiResponse<ScheduleGenerateResult>>('/v3/schedule-generate', data, { timeout: 120_000 }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function generateMultipleSchedulePlans(data: {
  semesterId?: number
  strategyTypes: string[]
  overwriteDraft?: boolean
}) {
  return request.post<ApiResponse<ScheduleGenerateResult[]>>('/v3/schedule-generate/multiple', data, { timeout: 120_000 }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}
