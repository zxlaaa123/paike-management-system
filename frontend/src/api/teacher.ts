import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface Teacher {
  id: number
  teacherNo: string
  name: string
  department: string
  phone: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

export interface TeacherForm {
  teacherNo: string
  name: string
  department?: string
  phone?: string
  status?: number
  remark?: string
}

export function getTeacherList(params: {
  name?: string
  teacherNo?: string
  department?: string
  status?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Teacher>>>('/teachers', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getTeacherById(id: number) {
  return request.get<ApiResponse<Teacher>>(`/teachers/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function createTeacher(data: TeacherForm) {
  return request.post<ApiResponse<Teacher>>('/teachers', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function updateTeacher(id: number, data: TeacherForm) {
  return request.put<ApiResponse<Teacher>>(`/teachers/${id}`, data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function deleteTeacher(id: number) {
  return request.delete(`/teachers/${id}`)
}

export function updateTeacherStatus(id: number, status: number) {
  return request.put(`/teachers/${id}/status`, { status })
}

export function getAllTeachers() {
  return request.get<ApiResponse<Teacher[]>>('/teachers/all').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}
