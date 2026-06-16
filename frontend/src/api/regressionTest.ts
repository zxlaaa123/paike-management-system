import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface RegressionTestRecord {
  id: number
  semesterId: number | null
  planId: number | null
  sourcePlanId: number | null
  testSuite: string
  testCase: string | null
  testStage: string | null
  status: string
  durationMs: number | null
  executedBy: string | null
  buildVersion: string | null
  errorMessage: string | null
  extraJson: string | null
  executedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface RegressionTestQuery {
  testStage?: string
  testSuite?: string
  status?: string
  semesterId?: number
  planId?: number
  page?: number
  size?: number
}

export function getRegressionTestList(params: RegressionTestQuery) {
  return request.get<ApiResponse<PageResult<RegressionTestRecord>>>('/v6/regression-tests', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getRegressionTestById(id: number) {
  return request.get<ApiResponse<RegressionTestRecord>>(`/v6/regression-tests/${id}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export interface RegressionRunResult {
  semesterId: number | null
  total: number
  passed: number
  failed: number
  durationMs: number | null
  summary: string
  records: RegressionTestRecord[]
}

export function runRegressionSelfCheck(semesterId?: number) {
  const params = semesterId != null ? { semesterId } : {}
  return request.post<ApiResponse<RegressionRunResult>>('/v6/regression-tests/run', null, { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

