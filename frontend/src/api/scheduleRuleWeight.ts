import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ScheduleRuleWeight {
  id: number
  semesterId: number
  strategyType: string
  ruleCode: string
  ruleName: string
  ruleType: string
  weight: number
  enabled: number
  description: string
  createdAt: string
  updatedAt: string
}

export function getRuleWeights(params: {
  semesterId?: number
  strategyType?: string
  ruleType?: string
}) {
  return request.get<ApiResponse<ScheduleRuleWeight[]>>('/v3/schedule-rule-weights', { params }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function initDefaultRules(params: {
  semesterId?: number
  strategyType?: string
}) {
  return request.post<ApiResponse<void>>('/v3/schedule-rule-weights/init-default', null, { params })
}

export function updateRuleWeight(id: number, data: {
  weight?: number
  enabled?: number
  description?: string
}) {
  return request.put<ApiResponse<void>>(`/v3/schedule-rule-weights/${id}`, data)
}

export function batchUpdateRuleWeights(rules: ScheduleRuleWeight[]) {
  return request.put<ApiResponse<void>>('/v3/schedule-rule-weights/batch', rules)
}
