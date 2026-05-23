import type { V5RepairExplanation } from '../../../api/v5SimulationApi'
import type { V5SimulationCompare } from '../../../api/v5SimulationApi'

type StatusTagType = '' | 'success' | 'warning' | 'info' | 'danger'
type TrendTagType = '' | 'success' | 'danger' | 'info'
type AlertType = 'success' | 'warning' | 'error' | 'info'

export function statusType(status?: string): 'success' | 'info' | 'warning' | 'primary' {
  if (status === 'APPLIED') return 'success'
  if (status === 'DISCARDED') return 'info'
  if (status === 'CONFIRMED') return 'warning'
  return 'primary'
}

export function deltaText(value?: number | null): string {
  if (value == null) return '0'
  return value > 0 ? `+${value}` : `${value}`
}

export function decimalDeltaText(value?: number | null): string {
  if (value == null) return '0'
  const normalized = Number(value).toFixed(2)
  return value > 0 ? `+${normalized}` : normalized
}

export function trendType(delta: number, improveWhenLower = false): TrendTagType {
  if (delta === 0) return 'info'
  const improved = improveWhenLower ? delta < 0 : delta > 0
  return improved ? 'success' : 'danger'
}

export function trendText(delta: number, improveWhenLower = false): string {
  if (delta === 0) return '无变化'
  const improved = improveWhenLower ? delta < 0 : delta > 0
  return improved ? '提升' : '下降'
}

export function riskLevelTag(level?: string): StatusTagType {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'info'
  return ''
}

export function formatPercent(value?: number | null): string {
  return `${Number(value ?? 0).toFixed(2)}%`
}

export function severityTag(severity?: string): StatusTagType {
  if (severity === 'BLOCKING') return 'danger'
  if (severity === 'WARNING') return 'warning'
  if (severity === 'INFO') return 'info'
  return ''
}

export function consistencyStatusTag(status?: string): StatusTagType {
  if (status === 'PASS') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'FAIL') return 'danger'
  return 'info'
}

export function consistencyAlertType(status?: string): AlertType {
  if (status === 'PASS') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'FAIL') return 'error'
  return 'info'
}

export function buildExplanationCopyText(e: V5RepairExplanation | null): string {
  if (!e) return ''
  const lines = [
    `修复任务 ${e.taskId} · 试算方案 ${e.planId}`,
    `生成方式：${e.remote ? '远程 AI' : '本地模板'}`,
    `生成时间：${e.generatedAt}`,
    '',
    `【总体评价】${e.overallEvaluation}`,
    `【推荐理由】${e.recommendationReason}`,
  ]
  if (e.improvedMetrics?.length) {
    lines.push('【改善指标】')
    e.improvedMetrics.forEach((m, i) => lines.push(`  ${i + 1}. ${m}`))
  }
  if (e.remainingIssues?.length) {
    lines.push('【仍存在问题】')
    e.remainingIssues.forEach((m, i) => lines.push(`  ${i + 1}. ${m}`))
  }
  lines.push(`【应用建议】${e.applyAdvice}`)
  lines.push(`【答辩摘要】${e.defenseSummary}`)
  lines.push('')
  lines.push(e.disclaimer)
  return lines.join('\n')
}

export type MetricRow = {
  key: string
  label: string
  before: number
  after: number
  delta: number
  lowerBetter: boolean
  formatter: (value?: number | null) => string
}

export function buildMetricRows(c: V5SimulationCompare | null): MetricRow[] {
  if (!c) return []
  return [
    { key: 'score', label: '总评分', before: c.baselineScore, after: c.simulationScore, delta: c.scoreDelta, lowerBetter: false, formatter: decimalDeltaText },
    { key: 'scheduled', label: '已排任务数量', before: c.baselineScheduledCount, after: c.simulationScheduledCount, delta: c.scheduledDelta, lowerBetter: false, formatter: deltaText },
    { key: 'unscheduled', label: '未排任务数量', before: c.baselineUnscheduledCount, after: c.simulationUnscheduledCount, delta: c.unscheduledDelta, lowerBetter: true, formatter: deltaText },
    { key: 'high', label: '高风险数量', before: c.baselineHighRiskCount, after: c.simulationHighRiskCount, delta: c.highRiskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'medium', label: '中风险数量', before: c.baselineMediumRiskCount, after: c.simulationMediumRiskCount, delta: c.mediumRiskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'low', label: '低风险数量', before: c.baselineLowRiskCount, after: c.simulationLowRiskCount, delta: c.lowRiskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'risk', label: '总风险数量', before: c.baselineRiskCount, after: c.simulationRiskCount, delta: c.riskDelta, lowerBetter: true, formatter: deltaText },
    { key: 'conflict', label: '硬冲突数量', before: c.baselineConflictCount, after: c.simulationConflictCount, delta: c.conflictDelta, lowerBetter: true, formatter: deltaText },
    { key: 'changed', label: '课程变动数量', before: 0, after: c.courseChangeCount, delta: c.courseChangeCount, lowerBetter: true, formatter: deltaText },
  ]
}
