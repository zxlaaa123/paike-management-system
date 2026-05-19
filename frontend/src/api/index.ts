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
export * from './schedulePlan'
export * from './scheduleGenerate'
export * from './scheduleScore'
export * from './scheduleRuleWeight'
export * from './semester'
export * from './scheduleStatistics'
export * from './v5RepairTaskApi'
export * from './v5CandidatePositionApi'
export * from './v5RepairSuggestionApi'
