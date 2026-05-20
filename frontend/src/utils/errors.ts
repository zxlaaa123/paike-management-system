import type { AxiosError } from 'axios'

export interface ApiErrorPayload {
  code?: number
  message?: string
}

export function isCancel(err: unknown): boolean {
  return err === 'cancel' || err === 'close'
}

export function extractMessage(err: unknown, fallback = '操作失败'): string {
  if (err instanceof Error && err.message) return err.message
  if (typeof err === 'string' && err) return err
  const axiosErr = err as AxiosError<ApiErrorPayload>
  return axiosErr.response?.data?.message || fallback
}
