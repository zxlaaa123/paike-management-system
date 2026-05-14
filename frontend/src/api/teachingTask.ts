import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface TeachingTask {
  id: number
  courseId: number
  courseName: string
  teacherId: number
  teacherName: string
  classId: number
  className: string
  weeklyHours: number
  needContinuous: number
  status: number
  remark: string
  scheduledSlots: number
  requiredSlots: number
  createTime: string
  updateTime: string
}

export interface TeachingTaskForm {
  courseId: number
  teacherId: number
  classId: number
  weeklyHours: number
  needContinuous?: number
  status?: number
  remark?: string
}

export function getTeachingTaskList(params: {
  courseName?: string
  teacherName?: string
  className?: string
  status?: number
  semesterId?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<TeachingTask>>>('/teaching-tasks', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getTeachingTaskById(id: number) {
  return request.get<ApiResponse<TeachingTask>>(`/teaching-tasks/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function createTeachingTask(data: TeachingTaskForm) {
  return request.post<ApiResponse<TeachingTask>>('/teaching-tasks', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function updateTeachingTask(id: number, data: TeachingTaskForm) {
  return request.put<ApiResponse<TeachingTask>>(`/teaching-tasks/${id}`, data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function deleteTeachingTask(id: number) {
  return request.delete(`/teaching-tasks/${id}`)
}

export function getAllTeachingTasks() {
  return request.get<ApiResponse<TeachingTask[]>>('/teaching-tasks/all').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}
