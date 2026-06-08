import { expect, test } from '@playwright/test'
import { apiHeaders, loginAndGoTo, loginAsAdmin, type AuthState } from './helpers/auth'

const API_URL = process.env.API_URL || 'http://127.0.0.1:8090'
const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:5173'

test.describe.serial('V6 系统治理 smoke', () => {
  let authState: AuthState

  test.beforeAll(async ({ request }) => {
    authState = await loginAsAdmin(request, API_URL)
  })

  test('V6 只读接口返回 200', async ({ request }) => {
    const headers = apiHeaders(authState)
    const endpoints = [
      '/api/v6/audit-logs?page=1&size=10',
      '/api/v6/regression-tests?page=1&size=10',
      '/api/v6/consistency-checks?page=1&size=10',
      '/api/v6/performance/baselines?page=1&size=10',
      '/api/v6/performance/summary',
      '/api/v6/migrations/status',
      '/api/v6/error-codes',
      '/api/v6/error-codes/SYSTEM_ERROR',
    ]

    for (const endpoint of endpoints) {
      const res = await request.get(`${API_URL}${endpoint}`, { headers })
      expect(res.status(), endpoint).toBe(200)
      const body = await res.json()
      expect(body.code, endpoint).toBe(200)
    }
  })

  test('V6 页面可进入且无控制台错误', async ({ page }) => {
    const errors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') {
        errors.push(message.text())
      }
    })

    const pages = [
      { path: '/v6/audit-logs', title: '审计日志' },
      { path: '/v6/regression-tests', title: '回归测试' },
      { path: '/v6/consistency-checks', title: '一致性检查' },
      { path: '/v6/performance-baselines', title: '性能基线' },
      { path: '/v6/migrations', title: '数据库迁移' },
      { path: '/v6/error-codes', title: '错误码' },
    ]

    for (const item of pages) {
      await loginAndGoTo(page, item.path, authState, BASE_URL)
      await expect(page.getByText(item.title).first(), item.path).toBeVisible()
      await expect(page.getByText('V6 系统治理').first(), item.path).toBeVisible()
    }

    expect(errors).toEqual([])
  })
})
