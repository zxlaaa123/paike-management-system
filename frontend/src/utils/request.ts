import axios from 'axios'
import { ElMessage } from 'element-plus'

interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
})

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('paike_admin_token')
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
    const message = error?.response?.data?.message || error?.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
