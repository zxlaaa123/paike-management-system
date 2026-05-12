import request from '../utils/request'
import type { ApiResponse, PageResult } from './types'

export interface Course {
  id: number
  courseNo: string
  courseName: string
  courseType: string
  courseNature: string
  totalHours: number
  weeklyHours: number
  remark: string
  createTime: string
  updateTime: string
}

export interface CourseForm {
  courseNo: string
  courseName: string
  courseType?: string
  courseNature?: string
  totalHours: number
  weeklyHours: number
  remark?: string
}

export function getCourseList(params: {
  courseName?: string
  courseNo?: string
  courseType?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<PageResult<Course>>>('/courses', { params }).then((r) => r.data.data)
}

export function getCourseById(id: number) {
  return request.get<ApiResponse<Course>>(`/courses/${id}`).then((r) => r.data.data)
}

export function createCourse(data: CourseForm) {
  return request.post<ApiResponse<Course>>('/courses', data).then((r) => r.data.data)
}

export function updateCourse(id: number, data: CourseForm) {
  return request.put<ApiResponse<Course>>(`/courses/${id}`, data).then((r) => r.data.data)
}

export function deleteCourse(id: number) {
  return request.delete(`/courses/${id}`)
}

export function getAllCourses() {
  return request.get<ApiResponse<Course[]>>('/courses/all').then((r) => r.data.data)
}
