import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface TeacherUnavailableTime {
  id: number
  teacherId: number
  teacherName: string
  department?: string
  timeSlotId: number
  timeSlotName: string
  reason: string
  status: number
  remark: string
  createTime: string
  dayOfWeek?: number
  periodNo?: number
}

export interface UnavailableTimeForm {
  teacherId: number
  timeSlotId: number
  reason?: string
  status?: number
  remark?: string
}

export function getUnavailableTimeList(params: {
  teacherId?: number
  teacherName?: string
  timeSlotId?: number
  status?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<TeacherUnavailableTime>>>('/teacher-unavailable-times', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function createUnavailableTime(data: UnavailableTimeForm) {
  return request.post<ApiResponse<TeacherUnavailableTime>>('/teacher-unavailable-times', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function updateUnavailableTime(id: number, data: UnavailableTimeForm) {
  return request.put<ApiResponse<TeacherUnavailableTime>>(`/teacher-unavailable-times/${id}`, data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function deleteUnavailableTime(id: number) {
  return request.delete(`/teacher-unavailable-times/${id}`)
}

export function updateUnavailableTimeStatus(id: number, status: number) {
  return request.put(`/teacher-unavailable-times/${id}/status`, { status })
}
