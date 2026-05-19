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
  risks: ScheduleRiskList
  compare: V5SimulationCompare
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
