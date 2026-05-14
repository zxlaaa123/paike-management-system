import request from '../utils/request'
import type { ApiResponse } from './types'

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

function resolveDownloadFileName(contentDisposition?: string) {
  if (!contentDisposition) {
    return 'timetable.xlsx'
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  if (plainMatch?.[1]) {
    return plainMatch[1]
  }
  return 'timetable.xlsx'
}

function triggerBrowserDownload(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

async function exportTimetable(url: string) {
  const response = await request.get<Blob>(url, { responseType: 'blob' })
  const fileName = resolveDownloadFileName(response.headers['content-disposition'])
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
