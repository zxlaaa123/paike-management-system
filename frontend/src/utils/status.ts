/** 通用启用/停用状态文本映射 */
export function statusText(status: number): string {
  return status === 1 ? '启用' : '停用'
}

/** 通用启用/停用状态 Tag 类型映射 */
export function statusTagType(status: number): string {
  return status === 1 ? 'success' : 'danger'
}

/** 自动排课批次状态文本映射 */
export function batchStatusText(status: string): string {
  const map: Record<string, string> = {
    RUNNING: '执行中',
    SUCCESS: '完成',
    PARTIAL: '部分成功',
    FAILED: '失败',
  }
  return map[status] || status
}

/** 自动排课批次状态 Tag 类型映射 */
export function batchStatusTagType(status: string): string {
  const map: Record<string, string> = {
    RUNNING: 'warning',
    SUCCESS: 'success',
    PARTIAL: 'warning',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

/** 排课策略文本映射 */
export function strategyText(strategy: string): string {
  const map: Record<string, string> = {
    TEACHER_PRIORITY: '教师优先',
    CLASS_BALANCE: '班级均衡',
    CLASSROOM_UTILIZATION: '教室利用率',
    COMPREHENSIVE: '综合最优',
    CUSTOM: '自定义',
  }
  return map[strategy] || strategy
}
