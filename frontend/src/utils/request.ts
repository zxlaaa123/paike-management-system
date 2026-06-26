import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
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

/** 判断是否为取消请求（AbortController 触发），取消的请求不应弹错误提示 */
function isCancelledError(error: unknown): boolean {
  if (error instanceof axios.Cancel || (error as { code?: string })?.code === 'ERR_CANCELED') {
    return true
  }
  if (error instanceof DOMException && error.name === 'AbortError') {
    return true
  }
  return false
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
    // 取消的请求静默处理，不弹错误提示
    if (isCancelledError(error)) {
      return Promise.reject(error)
    }
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

// ---------------------------------------------------------------------------
// P2 #1: 请求取消机制
// ---------------------------------------------------------------------------

/**
 * 创建一个可取消的请求控制器，适用于快速切换筛选/分页/路由时取消上一个未完成请求。
 *
 * 用法：
 * ```ts
 * const ctrl = createCancellable()
 * ctrl.cancel()          // 取消上一个
 * const data = await ctrl.get('/v3/schedule-plans', { params })
 * ```
 */
export function createCancellable() {
  let controller: AbortController | null = null

  function cancel() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  function withSignal(config?: AxiosRequestConfig): AxiosRequestConfig {
    cancel()
    controller = new AbortController()
    return { ...config, signal: controller.signal }
  }

  return {
    cancel,
    /** GET 请求，自动管理 AbortController，调用前取消上一个未完成请求 */
    async get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
      const response = await request.get<unknown, AxiosResponse<ApiResponse<T>>>(url, withSignal(config))
      if (!response.data) throw new Error('响应数据为空')
      return response.data.data
    },
    /** POST 请求，同上 */
    async post<T = unknown>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
      const response = await request.post<unknown, AxiosResponse<ApiResponse<T>>>(url, body, withSignal(config))
      if (!response.data) throw new Error('响应数据为空')
      return response.data.data
    },
    /** PUT 请求，同上 */
    async put<T = unknown>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
      const response = await request.put<unknown, AxiosResponse<ApiResponse<T>>>(url, body, withSignal(config))
      if (!response.data) throw new Error('响应数据为空')
      return response.data.data
    },
    /** DELETE 请求，同上 */
    async delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
      const response = await request.delete<unknown, AxiosResponse<ApiResponse<T>>>(url, withSignal(config))
      if (!response.data) throw new Error('响应数据为空')
      return response.data.data
    },
  }
}

// ---------------------------------------------------------------------------
// P2 #3: API 响应 unwrap 统一
// ---------------------------------------------------------------------------

/**
 * 统一 unwrap API 响应，替代各 API 函数中重复的 `.then((r) => { if (!r.data) throw... return r.data.data })`。
 */
export async function unwrap<T>(promise: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  const response = await promise
  if (!response.data) throw new Error('响应数据为空')
  return response.data.data
}

/** 带分页参数的 unwrap 快捷方法 */
export async function unwrapPage<T>(promise: Promise<AxiosResponse<ApiResponse<{ records: T[]; total: number; current: number; size: number }>>>) {
  return unwrap(promise)
}

export default request
