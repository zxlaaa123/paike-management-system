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
}

async function exportTimetable(url: string) {
  const response = await request.get<Blob>(url, { responseType: 'blob' })
  const fileName = resolveDownloadFileName(response.headers['content-disposition'], 'timetable.xlsx')
  triggerBrowserDownload(response.data, fileName)
}

export function getClassTimetable(classId: number) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/classes/${classId}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getTeacherTimetable(teacherId: number) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/teachers/${teacherId}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getClassroomTimetable(classroomId: number) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/classrooms/${classroomId}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function exportClassTimetable(classId: number) {
  return exportTimetable(`/timetables/classes/${classId}/export`)
}

export function exportTeacherTimetable(teacherId: number) {
  return exportTimetable(`/timetables/teachers/${teacherId}/export`)
}

export function exportClassroomTimetable(classroomId: number) {
  return exportTimetable(`/timetables/classrooms/${classroomId}/export`)
}
