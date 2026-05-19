import request from '../utils/request'
import type { ApiResponse } from './types'
import type { V5SimulationPlanDetail } from './v5SimulationApi'

export type V5SuggestionType =
  | 'KEEP_TIME_CHANGE_ROOM'
  | 'KEEP_ROOM_CHANGE_TIME'
  | 'CHANGE_TIME_AND_ROOM'
  | 'MANUAL_REVIEW'
  | 'PARTIAL_RESCHEDULE'

export type V5SuggestionLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'MANUAL'

export interface V5RepairSuggestion {
  id: number
  repairTaskId: number
  suggestionCode: string
  suggestionType: V5SuggestionType
  recommendationLevel: V5SuggestionLevel
  status: string
  riskItemId: number | null
  riskType: string | null
  sourcePlanItemId: number | null
  sourceWeekday: number | null
  sourceStartPeriod: number | null
  sourceEndPeriod: number | null
  sourceClassroomId: number | null
  sourceClassroomName: string | null
  targetWeekday: number | null
  targetStartPeriod: number | null
  targetEndPeriod: number | null
  targetClassroomId: number | null
  targetClassroomName: string | null
  resolvesOriginalRisk: boolean | null
  introducesNewRisk: boolean | null
  affectedItems: number[]
  expectedScoreDelta: number | null
  reasonSummary: string
  description: string
  createdAt: string
}

export function generateRepairSuggestions(taskId: number, payload?: { includeUnavailable?: boolean; candidateLimit?: number }) {
  return request.post<ApiResponse<V5RepairSuggestion[]>>(`/v5/repair-tasks/${taskId}/suggestions/generate`, payload || {}).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function listRepairSuggestions(taskId: number) {
  return request.get<ApiResponse<V5RepairSuggestion[]>>(`/v5/repair-tasks/${taskId}/suggestions`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getRepairSuggestionDetail(taskId: number, suggestionId: number) {
  return request.get<ApiResponse<V5RepairSuggestion>>(`/v5/repair-tasks/${taskId}/suggestions/${suggestionId}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function chooseSuggestionForSimulation(taskId: number, suggestionId: number) {
  return request.post<ApiResponse<V5SimulationPlanDetail>>(`/v5/repair-tasks/${taskId}/suggestions/${suggestionId}/simulate`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
