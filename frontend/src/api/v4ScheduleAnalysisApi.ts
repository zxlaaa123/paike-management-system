import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ScheduleAnalysisSummary {
  planId: number
  planName: string
  termId: number
  termName: string
  strategyCode: string
  planStatus: string
  isCurrent: boolean
  totalScore: number | null
  scheduledCount: number
  unscheduledCount: number
  conflictCount: number
  teacherCount: number
  classCount: number
  roomCount: number
  courseCount: number
  teacherAverageHours: number
  teacherMaxHours: number
  teacherMinHours: number
  roomUtilizationRate: number
  classAverageDailyLessons: number
  highRiskCount: number
  mediumRiskCount: number
  lowRiskCount: number
  qualityLevel: string
  qualitySummary: string
  suggestions: string[]
  createdAt: string | null
  appliedAt: string | null
}

export interface ScheduleScoreItem {
  scoreKey: string
  scoreName: string
  scoreValue: number
  maxScore: number
  weight: number
  description: string
  violationCount: number
  detailMessage: string
}

export interface ScheduleScoreExplanation {
  planId: number
  planName: string
  strategyCode: string
  totalScore: number | null
  calculationSource: string
  scoreItems: ScheduleScoreItem[]
}

export interface ScheduleRiskIssue {
  id: number
  riskType: string
  riskTypeName: string
  level: string
  title: string
  description: string
  relatedTeacherId: number | null
  relatedTeacherName: string | null
  relatedClassId: number | null
  relatedClassName: string | null
  relatedRoomId: number | null
  relatedRoomName: string | null
  relatedCourseId: number | null
  relatedCourseName: string | null
  weekDay: number | null
  period: string | null
  suggestion: string
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

export interface ScheduleTeacherHoursChartItem {
  teacherId: number
  teacherName: string
  totalHours: number
  courseCount: number
}

export interface ScheduleTeacherHoursChart {
  planId: number
  items: ScheduleTeacherHoursChartItem[]
}

export interface ScheduleRoomUtilizationChartItem {
  roomId: number
  roomName: string
  roomType: string
  capacity: number | null
  usedPeriods: number
  totalPeriods: number
  utilizationRate: number
}

export interface ScheduleRoomUtilizationChart {
  planId: number
  items: ScheduleRoomUtilizationChartItem[]
}

export interface ScheduleClassDailyLoadChartItem {
  classId: number
  className: string
  weekDay: number
  lessonCount: number
}

export interface ScheduleClassDailyLoadChart {
  planId: number
  items: ScheduleClassDailyLoadChartItem[]
}

export interface ScheduleTimeDensityChartItem {
  weekDay: number
  period: number
  courseCount: number
}

export interface ScheduleTimeDensityChart {
  planId: number
  items: ScheduleTimeDensityChartItem[]
}

export interface ScheduleScoreRadarChartItem {
  name: string
  value: number
  description: string
}

export interface ScheduleScoreRadarChart {
  planId: number
  items: ScheduleScoreRadarChartItem[]
}

export function getScheduleAnalysisSummary(planId: number) {
  return request.get<ApiResponse<ScheduleAnalysisSummary>>(`/v4/schedule-analysis/plans/${planId}/summary`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function refreshScheduleAnalysisSummary(planId: number) {
  return request.post<ApiResponse<{ planId: number; refreshed: boolean; message: string }>>(`/v4/schedule-analysis/plans/${planId}/refresh`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleScoreExplanation(planId: number) {
  return request.get<ApiResponse<ScheduleScoreExplanation>>(`/v4/schedule-analysis/plans/${planId}/score-details`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleRiskList(
  planId: number,
  params?: {
    riskType?: string
    level?: string
    onlyUnresolved?: boolean
  },
) {
  return request.get<ApiResponse<ScheduleRiskList>>(`/v4/schedule-risks/plans/${planId}`, { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function refreshScheduleRiskList(planId: number) {
  return request.post<ApiResponse<{ planId: number; riskCount: number; message: string }>>(`/v4/schedule-risks/plans/${planId}/refresh`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleTeacherHoursChart(planId: number) {
  return request.get<ApiResponse<ScheduleTeacherHoursChart>>(`/v4/schedule-charts/plans/${planId}/teacher-hours`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleRoomUtilizationChart(planId: number) {
  return request.get<ApiResponse<ScheduleRoomUtilizationChart>>(`/v4/schedule-charts/plans/${planId}/room-utilization`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleClassDailyLoadChart(planId: number) {
  return request.get<ApiResponse<ScheduleClassDailyLoadChart>>(`/v4/schedule-charts/plans/${planId}/class-daily-load`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleTimeDensityChart(planId: number) {
  return request.get<ApiResponse<ScheduleTimeDensityChart>>(`/v4/schedule-charts/plans/${planId}/time-density`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getScheduleScoreRadarChart(planId: number) {
  return request.get<ApiResponse<ScheduleScoreRadarChart>>(`/v4/schedule-charts/plans/${planId}/score-radar`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
