import request from '../utils/request'

export function healthCheck() {
  return request.get('/health')
}

export * from './types'
export * from './auth'
export * from './teacher'
export * from './classInfo'
export * from './classroom'
export * from './course'
export * from './teachingTask'
export * from './timeSlot'
export * from './schedule'
export * from './timetable'
export * from './teacherUnavailableTime'
export * from './scheduleRule'
export * from './autoSchedule'
export * from './unscheduledTask'
