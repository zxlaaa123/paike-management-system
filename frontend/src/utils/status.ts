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
    SOLVER_V8: '智能求解',
    CUSTOM: '自定义',
  }
  return map[strategy] || strategy
}

/** 周次类型文本映射（V9 单双周） */
export function weekTypeText(weekType: string | null | undefined): string {
  const map: Record<string, string> = {
    ALL: '全周',
    ODD: '单周',
    EVEN: '双周',
  }
  if (!weekType) return '全周'
  return map[weekType] || weekType
}

/** 周次类型 Tag 颜色映射（V9 单双周） */
export function weekTypeTagType(weekType: string | null | undefined): string {
  if (weekType === 'ODD') return 'warning'
  if (weekType === 'EVEN') return 'success'
  return 'info'
}

/** 排课方案状态文本映射 */
export function schedulePlanStatusText(status: string): string {
  const map: Record<string, string> = { DRAFT: '草稿', APPLIED: '已应用', ABANDONED: '已废弃' }
  return map[status] || status
}

/** 排课方案状态 Tag 类型映射 */
export function schedulePlanStatusTagType(status: string): string {
  const map: Record<string, string> = { DRAFT: 'primary', APPLIED: 'success', ABANDONED: 'info' }
  return map[status] || 'info'
}

/** 排课生成日志级别 Tag 类型映射 */
export function logLevelTagType(level: string): string {
  const map: Record<string, string> = { INFO: 'primary', WARN: 'warning', ERROR: 'danger' }
  return map[level] || 'info'
}

/** 排课生成日志类型文本映射 */
export function logTypeText(type: string): string {
  const map: Record<string, string> = {
    START_GENERATE: '开始生成',
    LOAD_TASK: '读取任务',
    CHECK_TEACHER: '检查教师',
    CHECK_CLASSROOM: '检查教室',
    CHECK_CLASS: '检查班级',
    CALCULATE_SCORE: '计算评分',
    ASSIGN_SUCCESS: '排课成功',
    ASSIGN_FAILED: '排课失败',
    GENERATE_SCORE: '生成评分',
    FINISH_GENERATE: '生成完成',
  }
  return map[type] || type
}

/** 评分等级 Tag 类型映射 */
export function scoreLevelTagType(level: string): string {
  const map: Record<string, string> = { 优秀: 'success', 良好: 'primary', 一般: 'warning', 较差: 'danger', 不推荐: 'danger' }
  return map[level] || 'info'
}

export const repairTaskStatusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '已创建', value: 'CREATED' },
  { label: '分析中', value: 'ANALYZING' },
  { label: '已生成建议', value: 'SUGGESTED' },
  { label: '已试算', value: 'SIMULATED' },
  { label: '已应用', value: 'APPLIED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '失败', value: 'FAILED' },
] as const

/** V5 修复任务状态文本映射 */
export function repairTaskStatusText(status: string): string {
  return repairTaskStatusOptions.find((item) => item.value === status)?.label || status
}

/** V5 修复任务状态 Tag 类型映射 */
export function repairTaskStatusTagType(status: string): string {
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  if (status === 'APPLIED') return 'success'
  if (status === 'SIMULATED' || status === 'SUGGESTED') return 'warning'
  return 'primary'
}
