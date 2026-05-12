import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface Classroom {
  id: number
  roomName: string
  building: string
  capacity: number
  roomType: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

export interface ClassroomForm {
  roomName: string
  building?: string
  capacity: number
  roomType?: string
  status?: number
  remark?: string
}

export function getClassroomList(params: {
  roomName?: string
  building?: string
  roomType?: string
  status?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Classroom>>>('/classrooms', { params }).then((r) => r.data.data)
}

export function getClassroomById(id: number) {
  return request.get<ApiResponse<Classroom>>(`/classrooms/${id}`).then((r) => r.data.data)
}

export function createClassroom(data: ClassroomForm) {
  return request.post<ApiResponse<Classroom>>('/classrooms', data).then((r) => r.data.data)
}

export function updateClassroom(id: number, data: ClassroomForm) {
  return request.put<ApiResponse<Classroom>>(`/classrooms/${id}`, data).then((r) => r.data.data)
}

export function deleteClassroom(id: number) {
  return request.delete(`/classrooms/${id}`)
}

export function updateClassroomStatus(id: number, status: number) {
  return request.put(`/classrooms/${id}/status`, { status })
}

export function getAllClassrooms() {
  return request.get<ApiResponse<Classroom[]>>('/classrooms/all').then((r) => r.data.data)
}
