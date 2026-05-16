import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ScheduleScoreDetail {
  id: number
  planId: number
  semesterId: number
  ruleCode: string
  ruleName: string
  score: number
  maxScore: number | null
  violationCount: number
  detailMessage: string
  createdAt: string
}

export function getScoreDetails(planId: number) {
  return request.get<ApiResponse<ScheduleScoreDetail[]>>(`/v3/schedule-plans/${planId}/score-details`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScoreSummary(planId: number) {
  return request.get<ApiResponse<{
    planId: number
    totalScore: number
    hardViolationCount: number
    softViolationCount: number
    conflictCount: number
    scoreLevel: string
  }>>(`/v3/schedule-plans/${planId}/score-summary`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function rescore(planId: number) {
  return request.post<ApiResponse<{
    planId: number
    totalScore: number
    conflictCount: number
    scoreLevel: string
  }>>(`/v3/schedule-plans/${planId}/rescore`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
