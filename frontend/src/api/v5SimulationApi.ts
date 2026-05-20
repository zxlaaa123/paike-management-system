import request from '../utils/request'
import type { ApiResponse } from './types'
import type { SchedulePlan, SchedulePlanItem } from './schedulePlan'

export interface ScheduleScoreDetail {
  id: number
  planId: number
  semesterId: number
  ruleCode: string
  ruleType: string
  ruleName: string
  score: number
  maxScore: number | null
  violationCount: number
  detailMessage: string
  createdAt: string
}

export interface ScheduleRiskIssue {
  id: number
  riskType: string
  riskTypeName: string
  level: string
  title: string
  description: string
  relatedTeacherName: string | null
  relatedClassName: string | null
  relatedRoomName: string | null
  relatedCourseName: string | null
  weekDay: number | null
  period: string | null
  suggestion: string | null
  resolved: boolean
  affectedObjects: string | null
  relatedItemIds: number[]
  detailLines: string[]
}

export interface ScheduleRiskList {
  planId: number
  riskCount: number
  highRiskCount: number
  mediumRiskCount: number
  lowRiskCount: number
  unresolvedCount: number
  risks: ScheduleRiskIssue[]
}

export interface V5SimulationItemChange {
  sourceItemId: number
  simulationItemId: number
  courseName: string | null
  teacherName: string | null
  className: string | null
  beforeWeekday: number
  beforeStartPeriod: number
  beforeEndPeriod: number
  beforeClassroomId: number
  beforeClassroomName: string | null
  afterWeekday: number
  afterStartPeriod: number
  afterEndPeriod: number
  afterClassroomId: number
  afterClassroomName: string | null
  conflictFlag: number
  conflictReason: string | null
}

export interface ScheduleAdjustLog {
  id: number
  planId: number
  scheduleId: number | null
  semesterId: number
  teachingTaskId: number | null
  oldClassroomId: number | null
  oldWeekday: number | null
  oldStartPeriod: number | null
  oldEndPeriod: number | null
  newClassroomId: number | null
  newWeekday: number | null
  newStartPeriod: number | null
  newEndPeriod: number | null
  beforeScore: number | null
  afterScore: number | null
  conflictFlag: number | null
  adjustReason: string | null
  createdAt: string
  courseName: string | null
  teacherName: string | null
  className: string | null
  oldClassroomName: string | null
  newClassroomName: string | null
}

export interface V5LocalReplanPayload {
  newPlanName?: string
  classIds?: number[]
  teacherIds?: number[]
  classroomIds?: number[]
  weekdays?: number[]
  periodNos?: number[]
  riskItemIds?: number[]
  selectedPlanItemIds?: number[]
  candidateLimit?: number
}

export interface V5LocalReplanSummary {
  scopeItemCount: number
  lockedCount: number
  replanableCount: number
  movedCount: number
  failedCount: number
  movedItemIds: number[]
  failedItemIds: number[]
  logs: string[]
}

export interface V5SimulationLoadChange {
  entityId: number | null
  entityName: string
  baselineLoad: number
  simulationLoad: number
  delta: number
}

export interface V5SimulationRoomUtilizationChange {
  classroomId: number | null
  classroomName: string | null
  baselineUsedPeriods: number
  simulationUsedPeriods: number
  deltaPeriods: number
  baselineUtilizationRate: number
  simulationUtilizationRate: number
  utilizationDelta: number
}

export interface V5SimulationCompare {
  baselineSemesterId: number | null
  simulationSemesterId: number
  baselinePlanId: number | null
  simulationPlanId: number
  baselineSourceScheduleId: number | null
  baselinePlanName: string
  simulationPlanName: string
  baselineScore: number
  simulationScore: number
  scoreDelta: number
  baselineScheduledCount: number
  simulationScheduledCount: number
  scheduledDelta: number
  baselineUnscheduledCount: number
  simulationUnscheduledCount: number
  unscheduledDelta: number
  baselineRiskCount: number
  simulationRiskCount: number
  riskDelta: number
  baselineHighRiskCount: number
  simulationHighRiskCount: number
  highRiskDelta: number
  baselineMediumRiskCount: number
  simulationMediumRiskCount: number
  mediumRiskDelta: number
  baselineLowRiskCount: number
  simulationLowRiskCount: number
  lowRiskDelta: number
  baselineConflictCount: number
  simulationConflictCount: number
  conflictDelta: number
  courseChangeCount: number
  lockedCoursesPreserved: boolean
  changedLockedCourseNames: string[]
  hasNewHardConflicts: boolean
  newHardConflictCount: number
  recommended: boolean
  recommendationMessage: string
  newRisks: ScheduleRiskIssue[]
  resolvedRisks: ScheduleRiskIssue[]
  teacherLoadChanges: V5SimulationLoadChange[]
  classLoadChanges: V5SimulationLoadChange[]
  roomUtilizationChanges: V5SimulationRoomUtilizationChange[]
  changedItems: V5SimulationItemChange[]
  summary: string
}

export interface V5SimulationPlanDetail {
  plan: SchedulePlan
  items: SchedulePlanItem[]
  scoreDetails: ScheduleScoreDetail[]
  adjustLogs: ScheduleAdjustLog[]
  risks: ScheduleRiskList
  compare: V5SimulationCompare
  localReplanSummary: V5LocalReplanSummary | null
  latestConsistencyReport: V5ConsistencyCheckReport | null
}

export interface V5ConsistencyIssue {
  code: string
  severity: 'BLOCKING' | 'WARNING' | 'INFO'
  category: string
  name: string
  message: string
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

export interface V5ConsistencyCheckReport {
  reportId: number | null
  taskId: number
  planId: number
  sourcePlanId: number | null
  semesterId: number | null
  status: 'PASS' | 'WARN' | 'FAIL'
  passed: boolean
  blockingIssueCount: number
  warningIssueCount: number
  infoIssueCount: number
  summary: string
  recommendation: string
  issues: V5ConsistencyIssue[]
  checkedAt: string
}

export function generateLocalReplan(taskId: number, payload?: V5LocalReplanPayload) {
  return request.post<ApiResponse<V5SimulationPlanDetail>>(`/v5/repair-tasks/${taskId}/simulations/local-replan`, payload ?? {}).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getSimulationDetail(taskId: number, planId: number) {
  return request.get<ApiResponse<V5SimulationPlanDetail>>(`/v5/repair-tasks/${taskId}/simulations/${planId}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function confirmSimulation(taskId: number, planId: number) {
  return request.post<ApiResponse<V5SimulationPlanDetail>>(`/v5/repair-tasks/${taskId}/simulations/${planId}/confirm`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function applySimulation(taskId: number, planId: number) {
  return request.post<ApiResponse<{ planId: number; semesterId: number; appliedCount: number; appliedAt: string }>>(
    `/v5/repair-tasks/${taskId}/simulations/${planId}/apply`
  ).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function discardSimulation(taskId: number, planId: number) {
  return request.post<ApiResponse<V5SimulationPlanDetail>>(`/v5/repair-tasks/${taskId}/simulations/${planId}/discard`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function runConsistencyCheck(taskId: number, planId: number) {
  return request.post<ApiResponse<V5ConsistencyCheckReport>>(`/v5/repair-tasks/${taskId}/simulations/${planId}/consistency-check`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getLatestConsistencyReport(taskId: number, planId: number) {
  return request.get<ApiResponse<V5ConsistencyCheckReport | null>>(`/v5/repair-tasks/${taskId}/simulations/${planId}/consistency-check/latest`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export interface V5RepairExplanation {
  taskId: number
  planId: number
  remote: boolean
  generatedAt: string
  overallEvaluation: string
  recommendationReason: string
  improvedMetrics: string[]
  remainingIssues: string[]
  applyAdvice: string
  recommendApply: boolean | null
  defenseSummary: string
  disclaimer: string
}

export function generateRepairExplanation(taskId: number, planId: number) {
  return request.post<ApiResponse<V5RepairExplanation>>(`/v5/repair-tasks/${taskId}/simulations/${planId}/ai-explanation`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
