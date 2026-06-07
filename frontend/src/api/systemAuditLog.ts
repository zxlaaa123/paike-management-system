import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface SystemAuditLog {
  id: number
  operatorId: number | null
  operatorName: string | null
  actionType: string
  targetType: string
  targetId: number | null
  semesterId: number | null
  planId: number | null
  success: number
  beforeSummary: string | null
  afterSummary: string | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
}

export interface SystemAuditLogQuery {
  actionType?: string
  semesterId?: number
  planId?: number
  success?: boolean
  page?: number
  size?: number
}

export function getSystemAuditLogList(params: SystemAuditLogQuery) {
  return request.get<ApiResponse<PageResult<SystemAuditLog>>>('/v6/audit-logs', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSystemAuditLogById(id: number) {
  return request.get<ApiResponse<SystemAuditLog>>(`/v6/audit-logs/${id}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
