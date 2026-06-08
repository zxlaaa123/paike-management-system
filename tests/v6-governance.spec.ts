import { expect, test } from '@playwright/test'

const API_URL = process.env.API_URL || 'http://127.0.0.1:8090'
const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:5173'

function cookieHeaderFromSetCookie(setCookie: string | string[] | undefined) {
  const values = Array.isArray(setCookie) ? setCookie : setCookie ? [setCookie] : []
  return values
    .flatMap((value) => value.split(/,\s*(?=[^=;,]+=)/))
    .map((value) => value.split(';')[0])
    .filter((value) => value.startsWith('paike_token=') || value.startsWith('XSRF-TOKEN='))
    .join('; ')
}

function cookiesForBrowser(cookieHeader: string) {
  return cookieHeader.split('; ')
    .filter(Boolean)
    .map((pair) => {
      const [name, ...valueParts] = pair.split('=')
      return {
        name,
        value: valueParts.join('='),
        domain: '127.0.0.1',
        path: '/',
      }
    })
}

async function login(request: any) {
  const res = await request.post(`${API_URL}/api/auth/login`, {
    data: { username: 'admin', password: '123456' },
  })
  const body = await res.json()
  expect(body.code).toBe(200)
  const cookieHeader = cookieHeaderFromSetCookie(res.headers()['set-cookie'])
  expect(cookieHeader).toContain('paike_token=')
  return cookieHeader
}

test.describe.serial('V6 系统治理 smoke', () => {
  let cookieHeader = ''

  test.beforeAll(async ({ request }) => {
    cookieHeader = await login(request)
  })

  test('V6 只读接口返回 200', async ({ request }) => {
    const headers = { Cookie: cookieHeader }
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
    await page.context().addCookies(cookiesForBrowser(cookieHeader))

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
      await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'networkidle' })
      await expect(page.getByText(item.title).first(), item.path).toBeVisible()
      await expect(page.getByText('V6 系统治理').first(), item.path).toBeVisible()
    }

    expect(errors).toEqual([])
  })
})
