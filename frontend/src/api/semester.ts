import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface Semester {
  id: number
  name: string
  schoolYear: string
  term: string
  startDate: string | null
  endDate: string | null
  isCurrent: number
  status: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface SemesterForm {
  name: string
  schoolYear: string
  term: string
  startDate?: string
  endDate?: string
  status?: string
  remark?: string
}

export function getSemesterList(params: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Semester>>>('/v3/semesters', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getAllSemesters() {
  return request.get<ApiResponse<Semester[]>>('/v3/semesters/all').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getSemesterById(id: number) {
  return request.get<ApiResponse<Semester>>(`/v3/semesters/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getCurrentSemester() {
  return request.get<ApiResponse<Semester>>('/v3/semesters/current').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function createSemester(data: SemesterForm) {
  return request.post<ApiResponse<Semester>>('/v3/semesters', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function updateSemester(id: number, data: SemesterForm) {
  return request.put<ApiResponse<Semester>>(`/v3/semesters/${id}`, data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function deleteSemester(id: number) {
  return request.delete(`/v3/semesters/${id}`)
}

export function setCurrentSemester(id: number) {
  return request.put(`/v3/semesters/${id}/current`)
}
