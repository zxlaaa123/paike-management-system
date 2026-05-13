import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface ScheduleScoreReport {
  id: number
  score: number
  grade: string
  gradeName: string
  conflictCount: number
  unfinishedTaskCount: number
  teacherOverloadCount: number
  classOverloadCount: number
  fridayAfternoonCount: number
  deductionDetail: string
  suggestion: string
  createTime: string
}

export interface ScheduleScoreResult {
  score: number
  grade: string
  gradeName: string
  conflictCount: number
  unfinishedTaskCount: number
  teacherOverloadCount: number
  classOverloadCount: number
  fridayAfternoonCount: number
  deductionDetail: string[]
  suggestion: string[]
}

export function generateScheduleScore() {
  return request.post<ApiResponse<ScheduleScoreResult>>('/schedule-score/generate', {})
    .then((r) => r.data.data)
}

export function getLatestScheduleScore() {
  return request.get<ApiResponse<ScheduleScoreReport>>('/schedule-score/latest')
    .then((r) => r.data.data)
}

export function getScheduleScoreHistory(params: {
  grade?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<ScheduleScoreReport>>>('/schedule-score/reports', { params })
    .then((r) => r.data.data)
}
