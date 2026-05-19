import request from '../utils/request'
import type { ApiResponse } from './types'

export type ScheduleAiAnalysisType = 'SUMMARY' | 'RISK' | 'OPTIMIZATION' | 'DEFENSE' | 'REPORT_SUMMARY'

export interface ScheduleAiAnalysisRequest {
  analysisType: ScheduleAiAnalysisType
  includeRisks: boolean
  includeSuggestions: boolean
}

export interface ScheduleAiAnalysisResult {
  planId: number
  analysisType: ScheduleAiAnalysisType
  analysisText: string
  suggestions: string[]
}

export function generateScheduleAiAnalysis(planId: number, payload: ScheduleAiAnalysisRequest) {
  return request.post<ApiResponse<ScheduleAiAnalysisResult>>(`/v4/ai/schedule-analysis/plans/${planId}`, payload).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

