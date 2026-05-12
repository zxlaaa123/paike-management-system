import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface AutoScheduleBatch {
  id: number
  batchNo: string
  totalTaskCount: number
  successTaskCount: number
  failedTaskCount: number
  generatedScheduleCount: number
  clearOldSchedule: number
  status: string
  message: string
  startTime: string
  endTime: string
  createTime: string
}

export function getBatchList(params: {
  batchNo?: string
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AutoScheduleBatch>>>('/auto-schedule/batches', { params }).then((r) => r.data.data)
}

export function getBatchById(id: number) {
  return request.get<ApiResponse<AutoScheduleBatch>>(`/auto-schedule/batches/${id}`).then((r) => r.data.data)
}

export function runAutoSchedule(data: {
  taskIds?: number[]
  clearOldAutoSchedule?: boolean
  clearAllSchedule?: boolean
}) {
  return request.post<ApiResponse<AutoScheduleBatch>>('/auto-schedule/run', data).then((r) => r.data.data)
}

export function clearBatchSchedules(batchId: number) {
  return request.delete(`/auto-schedule/batches/${batchId}/schedules`)
}
