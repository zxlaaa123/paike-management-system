import request from '../utils/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
}

export interface LoginResponse {
  token: string
  userInfo: UserInfo
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
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
