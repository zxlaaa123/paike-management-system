import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface UnscheduledTask {
  id: number
  batchId: number
  batchNo?: string
  taskId: number
  courseId?: number
  teacherId?: number
  classId?: number
  courseName?: string
  teacherName?: string
  className?: string
  requiredSlots: number
  scheduledSlots: number
  remainingSlots: number
  reasonType?: string
  reasonMessage?: string
  createTime: string
}

export function getUnscheduledTaskList(params: {
  batchId?: number
  courseName?: string
  teacherName?: string
  className?: string
  reasonType?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<UnscheduledTask>>>('/unscheduled-tasks', { params }).then((r) => r.data.data)
}

export function clearUnscheduledTasks(batchId?: number) {
  const params: Record<string, string> = {}
  if (batchId != null) params.batchId = String(batchId)
  return request.delete('/unscheduled-tasks', { params })
}
