import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface AutoScheduleBatch {
  batchId: number
  batchNo: string
  totalTaskCount: number
  successTaskCount: number
  failedTaskCount: number
  generatedScheduleCount: number
  status: string
  message: string
}

export function getBatchList(params: {
  batchNo?: string
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<AutoScheduleBatch>>>('/auto-schedule/batches', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getBatchById(id: number) {
  return request.get<ApiResponse<AutoScheduleBatch>>(`/auto-schedule/batches/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function runAutoSchedule(data: {
  taskIds?: number[]
  clearOldAutoSchedule?: boolean
  clearAllSchedule?: boolean
}) {
  return request.post<ApiResponse<AutoScheduleBatch>>('/auto-schedule/run', data, { timeout: 120_000 }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function clearBatchSchedules(batchId: number) {
  return request.delete(`/auto-schedule/batches/${batchId}/schedules`)
}
