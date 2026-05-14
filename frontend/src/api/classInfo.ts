import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface ClassInfo {
  id: number
  className: string
  major: string
  grade: string
  studentCount: number
  headTeacher: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

export interface ClassForm {
  className: string
  major?: string
  grade?: string
  studentCount: number
  headTeacher?: string
  status?: number
  remark?: string
}

export function getClassList(params: {
  className?: string
  major?: string
  grade?: string
  status?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<ClassInfo>>>('/classes', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getClassById(id: number) {
  return request.get<ApiResponse<ClassInfo>>(`/classes/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function createClass(data: ClassForm) {
  return request.post<ApiResponse<ClassInfo>>('/classes', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function updateClass(id: number, data: ClassForm) {
  return request.put<ApiResponse<ClassInfo>>(`/classes/${id}`, data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function deleteClass(id: number) {
  return request.delete(`/classes/${id}`)
}

export function updateClassStatus(id: number, status: number) {
  return request.put(`/classes/${id}/status`, { status })
}

export function getAllClasses() {
  return request.get<ApiResponse<ClassInfo[]>>('/classes/all').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}
