import request from '../utils/request'
import type { ApiResponse } from './types'

export interface MigrationScriptStatus {
  scriptName: string
  resourcePath: string
  configuredOrder: number | null
  configured: boolean
  existsOnClasspath: boolean
  status: string
  riskLevel: string
  idempotentHint: string
}

export interface MigrationInitializerStatus {
  name: string
  type: string
  className: string
  status: string
  description: string
}

export interface MigrationStatusOverview {
  migrationTool: string
  totalScriptCount: number
  configuredScriptCount: number
  missingScriptCount: number
  unconfiguredScriptCount: number
  scripts: MigrationScriptStatus[]
  initializers: MigrationInitializerStatus[]
}

export function getMigrationStatus() {
  return request.get<ApiResponse<MigrationStatusOverview>>('/v6/migrations/status').then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
