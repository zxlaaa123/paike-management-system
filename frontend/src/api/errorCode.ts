import request from '../utils/request'
import type { ApiResponse } from './types'

export interface ErrorCodeInfo {
  code: string
  numericCode: number
  category: string
  httpStatus: number
  defaultMessage: string
  frontendPrompt: string
  handlingSuggestion: string
}

export function getErrorCodes(category?: string) {
  return request.get<ApiResponse<ErrorCodeInfo[]>>('/v6/error-codes', { params: { category } }).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}

export function getErrorCodeDetail(code: string) {
  return request.get<ApiResponse<ErrorCodeInfo>>(`/v6/error-codes/${code}`).then((r) => {
    if (!r.data) throw new Error('响应数据为空')
    return r.data.data
  })
}
