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
  planId?: number
  batchId?: number
  batchNo?: string
  /** 乐观锁版本号（V25 并发编辑保护），编辑提交时需原样回传。 */
  version?: number
  createTime: string
  updateTime: string
}

export interface ScheduleCurrentSource {
  termId: number
  termName: string
  sourcePlanId: number | null
  sourcePlanName: string | null
  strategyCode: string | null
  totalScore: number | null
  appliedAt: string | null
  hasManualAdjustments: boolean
  manualAdjustmentCount: number
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
  semesterId?: number
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Schedule>>>('/schedules', { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getScheduleById(id: number) {
  return request.get<ApiResponse<Schedule>>(`/schedules/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function createSchedule(data: ScheduleForm) {
  return request.post<ApiResponse<Schedule>>('/schedules', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function deleteSchedule(id: number) {
  return request.delete(`/schedules/${id}`)
}

export function getSchedulesByClass(classId: number) {
  return request.get<ApiResponse<Schedule[]>>(`/schedules/class/${classId}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getSchedulesByTeacher(teacherId: number) {
  return request.get<ApiResponse<Schedule[]>>(`/schedules/teacher/${teacherId}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getSchedulesByClassroom(classroomId: number) {
  return request.get<ApiResponse<Schedule[]>>(`/schedules/classroom/${classroomId}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function checkConflict(data: ScheduleForm) {
  return request.post<
    ApiResponse<{ hasConflict: boolean; message: string }>
  >('/schedules/check-conflict', data).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getCurrentScheduleSource(termId?: number) {
  return request.get<ApiResponse<ScheduleCurrentSource>>('/v4/schedules/current/source', { params: { termId } }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}
