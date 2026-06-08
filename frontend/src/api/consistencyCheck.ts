import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface ConsistencyCheckRecord {
  id: number
  semesterId: number | null
  planId: number | null
  sourcePlanId: number | null
  scheduleId: number | null
  checkType: string | null
  checkScope: string | null
  status: string
  issueCount: number | null
  blockingIssueCount: number | null
  resultSummary: string | null
  detailJson: string | null
  checkedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ConsistencyIssue {
  code: string | null
  severity: string | null
  category: string | null
  name: string | null
  message: string | null
  suggestion: string | null
  planItemId: number | null
  teachingTaskId: number | null
  courseName: string | null
  teacherName: string | null
  className: string | null
  classroomName: string | null
  weekday: number | null
  startPeriod: number | null
  endPeriod: number | null
}

export interface ConsistencyCheckReport {
  reportId: number | null
  taskId: number | null
  planId: number | null
  sourcePlanId: number | null
  semesterId: number | null
  status: string | null
  passed: boolean | null
  blockingIssueCount: number | null
  warningIssueCount: number | null
  infoIssueCount: number | null
  summary: string | null
  recommendation: string | null
  issues: ConsistencyIssue[] | null
  checkedAt: string | null
}

export interface ConsistencyCheckDetail {
  record: ConsistencyCheckRecord | null
  report: ConsistencyCheckReport | null
  issues: ConsistencyIssue[]
}

export interface ConsistencyCheckQuery {
  status?: string
  checkType?: string
  semesterId?: number
  planId?: number
  page?: number
  size?: number
}

export function getConsistencyCheckList(params: ConsistencyCheckQuery) {
  return request.get<ApiResponse<PageResult<ConsistencyCheckRecord>>>('/v6/consistency-checks', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getConsistencyCheckById(id: number) {
  return request.get<ApiResponse<ConsistencyCheckDetail>>(`/v6/consistency-checks/${id}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function runConsistencyCheck(taskId: number, planId: number) {
  return request.post<ApiResponse<ConsistencyCheckReport>>('/v6/consistency-checks/run', null, {
    params: { taskId, planId },
  }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

