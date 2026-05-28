import request from '../utils/request'
import type { ApiResponse } from './types'

export interface LoginRequest {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: 'ADMIN' | 'USER' | string
}

export interface LoginResponse {
  userInfo: UserInfo
}

export async function loginApi(payload: LoginRequest) {
  const { data } = await request.post<ApiResponse<LoginResponse>>('/auth/login', payload)
  return data.data
}

export async function getCurrentUserApi() {
  const { data } = await request.get<ApiResponse<UserInfo>>('/auth/me')
  return data.data
}

export async function logoutApi() {
  await request.post('/auth/logout')
}
