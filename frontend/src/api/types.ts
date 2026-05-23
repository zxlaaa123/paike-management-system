export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
