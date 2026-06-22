import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '../api/types'
import router from '../router'

/** 从浏览器 Cookie 中读取指定名称的值（非正则实现，避免 name 注入） */
function getCookie(name: string): string | null {
  const prefix = name + '='
  const cookies = document.cookie.split(';')
  for (const raw of cookies) {
    const trimmed = raw.trimStart()
    if (trimmed.startsWith(prefix)) {
      const value = trimmed.substring(prefix.length)
      try {
        return decodeURIComponent(value)
      } catch {
        return value
      }
    }
  }
  return null
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
})

function isJsonBlob(data: unknown): data is Blob {
  return data instanceof Blob && data.type.includes('application/json')
}

function redirectToLogin() {
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

function handleBusinessUnauthorized(code?: number) {
  if (code === 401) {
    redirectToLogin()
    return true
  }
  return false
}

function formatErrorMessage(message?: string, code?: number) {
  const fallback = message || '请求失败'
  return typeof code === 'number' ? `[${code}] ${fallback}` : fallback
}

async function parseJsonBlob(data: Blob): Promise<Partial<ApiResponse> | null> {
  try {
    const text = await data.text()
    return JSON.parse(text) as Partial<ApiResponse>
  } catch {
    return null
  }
}

request.interceptors.request.use(
  (config) => {
    // CSRF 防护：状态变更请求携带 X-CSRF-Token 头（与后端 AuthInterceptor 的 stateChanging 集合一致）
    if (config.method && ['post', 'put', 'delete', 'patch'].includes(config.method.toLowerCase())) {
      const csrfToken = getCookie('XSRF-TOKEN')
      if (csrfToken) {
        config.headers['X-CSRF-Token'] = csrfToken
      }
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  async (response) => {
    if (isJsonBlob(response.data)) {
      const payload = await parseJsonBlob(response.data)
      if (payload && typeof payload.message === 'string' && payload.message) {
        if (handleBusinessUnauthorized(payload.code)) {
          return Promise.reject(new Error(payload.message))
        }
        const message = formatErrorMessage(payload.message, payload.code)
        ElMessage.error(message)
        return Promise.reject(new Error(message))
      }
      return response
    }
    const payload = response.data as ApiResponse
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      if (handleBusinessUnauthorized(payload.code)) {
        return Promise.reject(new Error(payload.message || '未登录或登录已过期'))
      }
      const message = formatErrorMessage(payload.message, payload.code)
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return response
  },
  async (error) => {
    if (error?.response?.status === 401) {
      redirectToLogin()
      return Promise.reject(error)
    }
    const blobPayload = isJsonBlob(error?.response?.data) ? await parseJsonBlob(error.response.data) : null
    if (handleBusinessUnauthorized(blobPayload?.code)) {
      return Promise.reject(error)
    }
    const blobMessage = blobPayload?.message
    const message = formatErrorMessage(
      blobMessage || error?.response?.data?.message || error?.message || '网络异常',
      blobPayload?.code || error?.response?.data?.code,
    )
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default request
