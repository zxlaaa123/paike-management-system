import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

/** 周次类型：ALL 全周 / ODD 单周 / EVEN 双周（与后端 WeekType 对齐，V9 单双周支持） */
export type WeekType = 'ALL' | 'ODD' | 'EVEN'

export interface TeachingTask {
  id: number
  courseId: number
  courseName: string
  teacherId: number
  teacherName: string
  classId: number
  className: string
  weeklyHours: number
  weekType: WeekType
  /** 连续周段起始周（闭区间，默认1，V10 连续周段支持） */
  startWeek: number
  /** 连续周段结束周（闭区间，默认20，V10 连续周段支持） */
  endWeek: number
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
  weekType: WeekType
  /** 连续周段起始周（闭区间，默认1，V10 连续周段支持） */
  startWeek?: number
  /** 连续周段结束周（闭区间，默认20，V10 连续周段支持） */
  endWeek?: number
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
  pageNum?: number
  pageSize?: number
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
