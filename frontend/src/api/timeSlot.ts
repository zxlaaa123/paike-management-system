import request from '../utils/request'
import type { ApiResponse } from './types'

export interface TimeSlot {
  id: number
  dayOfWeek: number
  periodNo: number
  timeLabel: string
  sortOrder: number
}

export function getAllTimeSlots() {
  return request.get<ApiResponse<TimeSlot[]>>('/time-slots').then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getTimeSlotById(id: number) {
  return request.get<ApiResponse<TimeSlot>>(`/time-slots/${id}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}

export function getTimeSlotsByDay(dayOfWeek: number) {
  return request.get<ApiResponse<TimeSlot[]>>(`/time-slots/day/${dayOfWeek}`).then((r) => {
    if (!r.data) {
      throw new Error('响应数据为空')
    }
    return r.data.data
  })
}
