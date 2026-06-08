import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface PerformanceBaselineRecord {
  id: number
  operationType: string
  semesterId: number | null
  planId: number | null
  targetId: number | null
  taskCount: number | null
  scheduleCount: number | null
  durationMs: number
  success: number
  errorCode: string | null
  errorMessage: string | null
  extraJson: string | null
  createdAt: string
}

export interface PerformanceSummary {
  operationType: string
  totalCount: number
  successCount: number
  failureCount: number
  avgDurationMs: number
  maxDurationMs: number
}

export interface PerformanceBaselineQuery {
  operationType?: string
  semesterId?: number
  planId?: number
  success?: boolean
  page?: number
  size?: number
}

export function getPerformanceBaselineList(params: PerformanceBaselineQuery) {
  return request.get<ApiResponse<PageResult<PerformanceBaselineRecord>>>('/v6/performance/baselines', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getPerformanceSummary() {
  return request.get<ApiResponse<PerformanceSummary[]>>('/v6/performance/summary').then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

