import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'paike_admin_token'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      if (router.currentRoute.value.path !== '/login') {
        router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    const message = error?.response?.data?.message || error?.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
