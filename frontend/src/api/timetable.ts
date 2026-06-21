import request from '../utils/request'
import type { ApiResponse } from './types'
import { resolveDownloadFileName, triggerBrowserDownload } from '../utils/download'

export interface TimetableItem {
  scheduleId: number
  timeSlotId: number
  dayOfWeek: number
  period: number
  timeSlotName: string
  courseName: string
  courseType: string
  teacherName: string
  className: string
  classroomName: string
  building: string
  /** 周次类型：ALL全周、ODD单周、EVEN双周（V9 单双周支持）。历史数据可能缺省，按 ALL 处理 */
  weekType?: string
  /** V10 起始周（默认 1，全学期） */
  startWeek?: number
  /** V10 结束周（默认 20，全学期） */
  endWeek?: number
}

interface TimetableParams {
  semesterId?: number
}

async function exportTimetable(url: string, params?: TimetableParams) {
  const response = await request.get<Blob>(url, { params, responseType: 'blob' })
  const fileName = resolveDownloadFileName(response.headers['content-disposition'], 'timetable.xlsx')
  triggerBrowserDownload(response.data, fileName)
}

export function getClassTimetable(classId: number, params?: TimetableParams) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/classes/${classId}`, { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getTeacherTimetable(teacherId: number, params?: TimetableParams) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/teachers/${teacherId}`, { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getClassroomTimetable(classroomId: number, params?: TimetableParams) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/classrooms/${classroomId}`, { params }).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function exportClassTimetable(classId: number, params?: TimetableParams) {
  return exportTimetable(`/timetables/classes/${classId}/export`, params)
}

export function exportTeacherTimetable(teacherId: number, params?: TimetableParams) {
  return exportTimetable(`/timetables/teachers/${teacherId}/export`, params)
}

export function exportClassroomTimetable(classroomId: number, params?: TimetableParams) {
  return exportTimetable(`/timetables/classrooms/${classroomId}/export`, params)
}
