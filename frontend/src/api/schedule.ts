import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface Schedule {
  id: number
  teachingTaskId: number
  timeSlotId: number
  classroomId: number
  courseName: string
  teacherName: string
  className: string
  timeLabel: string
  dayOfWeek: number
  periodNo: number
  roomName: string
  building: string
  sourceType?: string
  sourceTypeName?: string
  batchId?: number
  batchNo?: string
  createTime: string
  updateTime: string
}

export interface ScheduleForm {
  teachingTaskId: number
  timeSlotId: number
  classroomId: number
}

export function getScheduleList(params: {
  courseName?: string
  teacherName?: string
  className?: string
  roomName?: string
  dayOfWeek?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Schedule>>>('/schedules', { params }).then((r) => r.data.data)
}

export function getScheduleById(id: number) {
  return request.get<ApiResponse<Schedule>>(`/schedules/${id}`).then((r) => r.data.data)
}

export function createSchedule(data: ScheduleForm) {
  return request.post<ApiResponse<Schedule>>('/schedules', data).then((r) => r.data.data)
}

export function deleteSchedule(id: number) {
  return request.delete(`/schedules/${id}`)
}

export function getSchedulesByClass(classId: number) {
  return request.get<ApiResponse<Schedule[]>>(`/schedules/class/${classId}`).then((r) => r.data.data)
}

export function getSchedulesByTeacher(teacherId: number) {
  return request.get<ApiResponse<Schedule[]>>(`/schedules/teacher/${teacherId}`).then((r) => r.data.data)
}

export function getSchedulesByClassroom(classroomId: number) {
  return request.get<ApiResponse<Schedule[]>>(`/schedules/classroom/${classroomId}`).then((r) => r.data.data)
}

export function checkConflict(data: ScheduleForm) {
  return request.post<
    ApiResponse<{ hasConflict: boolean; message: string }>
  >('/schedules/check-conflict', data).then((r) => r.data.data)
}
