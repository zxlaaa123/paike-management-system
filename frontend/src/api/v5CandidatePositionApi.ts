import request from '../utils/request'
import type { ApiResponse } from './types'

export interface V5CandidatePositionGeneratePayload {
  scheduleId?: number
  planItemId?: number
  includeUnavailable?: boolean
  limit?: number
}

export interface V5CandidatePosition {
  weekday: number
  startPeriod: number
  endPeriod: number
  classroomId: number
  classroomName: string
  available: boolean
  hardConflictCount: number
  softScore: number
  totalScore: number
  reason: string
  affectedItems: number[]
}

export interface V5CandidatePositionResult {
  semesterId: number
  planId: number
  planItemId: number
  scheduleId: number | null
  sourceWeekday: number
  sourceStartPeriod: number
  sourceEndPeriod: number
  sourceClassroomId: number
  sourceClassroomName: string
  totalCount: number
  availableCount: number
  candidates: V5CandidatePosition[]
}

export function generateCandidatePositions(payload: V5CandidatePositionGeneratePayload) {
  return request.post<ApiResponse<V5CandidatePositionResult>>('/v5/candidate-positions/generate', payload).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

