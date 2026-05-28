import request from '../utils/request'
import type { ApiResponse } from './types'

export type V5RepairTaskStatus = 'PENDING' | 'CREATED' | 'ANALYZING' | 'SUGGESTED' | 'SIMULATED' | 'APPLIED' | 'CANCELLED' | 'FAILED'

export interface V5RepairTaskCreatePayload {
  semesterId: number
  planId?: number
  sourceScheduleId?: number
  sourcePlanId?: number
  taskType: string
  title?: string
  triggerSource?: string
  riskTypes?: string[]
  riskItemIds?: number[]
  scopePlanItemIds?: number[]
}

export interface V5RepairTaskSummary {
  id: number
  semesterId: number
  planId: number | null
  title: string | null
  taskCode: string
  taskType: string
  status: V5RepairTaskStatus
  resultPlanId: number | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface V5RepairTaskDetail extends V5RepairTaskSummary {
  sourcePlanId: number | null
  sourceScheduleId: number | null
  triggerSource: string
  riskTypes: string[]
  riskItemIds: number[]
  scopePlanItemIds: number[]
  targetItemCount: number
  lockedItemCount: number
  processedItemCount: number
  successItemCount: number
  failureItemCount: number
  errorMessage: string | null
  cancelReason: string | null
  updatedAt: string
}

export interface V5RepairTaskStatusUpdatePayload {
  status: V5RepairTaskStatus
  message?: string
}

export function createRepairTask(payload: V5RepairTaskCreatePayload) {
  return request.post<ApiResponse<V5RepairTaskDetail>>('/v5/repair-tasks', payload).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function listRepairTasks(params?: { semesterId?: number; planId?: number; status?: V5RepairTaskStatus }) {
  return request.get<ApiResponse<V5RepairTaskSummary[]>>('/v5/repair-tasks', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getRepairTaskDetail(taskId: number) {
  return request.get<ApiResponse<V5RepairTaskDetail>>(`/v5/repair-tasks/${taskId}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function updateRepairTaskStatus(taskId: number, payload: V5RepairTaskStatusUpdatePayload) {
  return request.put<ApiResponse<V5RepairTaskDetail>>(`/v5/repair-tasks/${taskId}/status`, payload).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function cancelRepairTask(taskId: number, reason?: string) {
  return request.post<ApiResponse<V5RepairTaskDetail>>(`/v5/repair-tasks/${taskId}/cancel`, { reason }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
