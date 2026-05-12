export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
