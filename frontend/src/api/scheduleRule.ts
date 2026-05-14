import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ScheduleRuleConfig {
  id: number
  ruleKey: string
  ruleValue: string
  ruleName: string
  description: string
  enabled: number
  createTime: string
  updateTime: string
}

export function getScheduleRules() {
  return request.get<ApiResponse<ScheduleRuleConfig[]>>('/schedule-rules').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function updateScheduleRules(rules: { ruleKey: string; ruleValue: string; enabled: number }[]) {
  return request.put<ApiResponse<void>>('/schedule-rules', rules)
}

export function resetScheduleRules() {
  return request.post<ApiResponse<void>>('/schedule-rules/reset-default')
}
