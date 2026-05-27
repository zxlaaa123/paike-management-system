import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface ScheduleScoreReport {
  id: number
  semesterId: number
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

export function generateScheduleScore(semesterId?: number) {
  return request.post<ApiResponse<ScheduleScoreResult>>('/schedule-score/generate', {}, { params: { semesterId } })
    .then((r) => {
      if (!r.data) {
        throw new Error('响应数据为空')
      }
      return r.data.data
    })
}

export function getLatestScheduleScore(semesterId?: number) {
  return request.get<ApiResponse<ScheduleScoreReport>>('/schedule-score/latest', { params: { semesterId } })
    .then((r) => {
      if (!r.data) {
        throw new Error('响应数据为空')
      }
      return r.data.data
    })
}

export function getScheduleScoreHistory(params: {
  semesterId?: number
  grade?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<ScheduleScoreReport>>>('/schedule-score/reports', { params })
    .then((r) => {
      if (!r.data) {
        throw new Error('响应数据为空')
      }
      return r.data.data
    })
}
