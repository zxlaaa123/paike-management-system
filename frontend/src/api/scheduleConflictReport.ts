import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface ScheduleConflictReport {
  id: number
  semesterId?: number
  reportNo: string
  conflictType: string
  objectType?: string
  objectId?: number
  objectName?: string
  timeSlotId?: number
  timeSlotName?: string
  relatedScheduleIds?: string
  description?: string
  suggestion?: string
  createTime: string
}

export interface ScheduleConflictGenerateResult {
  reportNo: string
  conflictCount: number
  message: string
}

export function generateScheduleConflictReport(semesterId?: number) {
  return request.post<ApiResponse<ScheduleConflictGenerateResult>>('/schedule-conflict-reports/generate', {}, {
    params: semesterId ? { semesterId } : undefined,
  })
    .then((r) => {
      if (!r.data) {
        throw new Error('响应数据为空')
      }
      return r.data.data
    })
}

export function getScheduleConflictReportList(params: {
  semesterId?: number
  reportNo?: string
  conflictType?: string
  objectType?: string
  objectName?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<ScheduleConflictReport>>>('/schedule-conflict-reports', { params })
    .then((r) => {
      if (!r.data) {
        throw new Error('响应数据为空')
      }
      return r.data.data
    })
}

export function clearScheduleConflictReports(reportNo?: string) {
  const params: Record<string, string> = {}
  if (reportNo && reportNo.trim()) params.reportNo = reportNo.trim()
  return request.delete('/schedule-conflict-reports', { params })
}
