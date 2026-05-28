import request from '../utils/request'
import type { ApiResponse } from './types'
import { resolveDownloadFileName, triggerBrowserDownload } from '../utils/download'

export type ScheduleReportType = 'ANALYSIS' | 'COMPARE' | 'RISK' | 'TEACHER_LOAD' | 'ROOM_USAGE'
export type ScheduleReportFormat = 'HTML' | 'EXCEL'

export interface ScheduleReportGenerateRequest {
  reportType: ScheduleReportType
  format: ScheduleReportFormat
  includeCharts: boolean
  includeRisks: boolean
  includeSuggestions: boolean
}

export interface ScheduleReportItem {
  reportId: number
  planId: number
  semesterId: number | null
  reportType: ScheduleReportType
  format: ScheduleReportFormat
  status: string
  downloadUrl: string
  createdAt: string
}

export interface ScheduleReportList {
  planId: number
  semesterId: number | null
  items: ScheduleReportItem[]
}

function normalizeDownloadUrl(url: string) {
  return url.startsWith('/api/') ? url.slice(4) : url
}

export function generateScheduleReport(planId: number, payload: ScheduleReportGenerateRequest) {
  return request.post<ApiResponse<ScheduleReportItem>>(`/v4/schedule-reports/plans/${planId}/generate`, payload, { timeout: 120_000 }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleReportList(planId: number) {
  return request.get<ApiResponse<ScheduleReportList>>(`/v4/schedule-reports/plans/${planId}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export async function downloadScheduleReport(item: ScheduleReportItem) {
  const response = await request.get<Blob>(normalizeDownloadUrl(item.downloadUrl), { responseType: 'blob' })
  const fileName = resolveDownloadFileName(response.headers['content-disposition'], `schedule-report-${item.reportId}`)
  triggerBrowserDownload(response.data, fileName)
}
