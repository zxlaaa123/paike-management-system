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

export function getClassTimetable(classId: number) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/classes/${classId}`).then((r) => r.data.data)
}

export function getTeacherTimetable(teacherId: number) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/teachers/${teacherId}`).then((r) => r.data.data)
}

export function getClassroomTimetable(classroomId: number) {
  return request.get<ApiResponse<TimetableItem[]>>(`/timetables/classrooms/${classroomId}`).then((r) => r.data.data)
}
