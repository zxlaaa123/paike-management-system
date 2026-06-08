import { expect, type APIRequestContext, type Page } from '@playwright/test'

export interface AuthState {
  cookieHeader: string
  csrfToken: string
}

function cookieHeaderFromSetCookie(setCookie: string | string[] | undefined) {
  const values = Array.isArray(setCookie) ? setCookie : setCookie ? [setCookie] : []
  return values
    .flatMap((value) => value.split(/,\s*(?=[^=;,]+=)/))
    .map((value) => value.split(';')[0])
    .filter((value) => value.startsWith('paike_token=') || value.startsWith('XSRF-TOKEN='))
    .join('; ')
}

function cookieValue(cookieHeader: string, name: string) {
  const found = cookieHeader.split('; ').find((pair) => pair.startsWith(`${name}=`))
  return found ? found.slice(name.length + 1) : ''
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

export async function loginAsAdmin(request: APIRequestContext, apiUrl: string): Promise<AuthState> {
  const res = await request.post(`${apiUrl}/api/auth/login`, {
    data: { username: 'admin', password: '123456' },
  })
  const body = await res.json()
  expect(body.code).toBe(200)

  const cookieHeader = cookieHeaderFromSetCookie(res.headers()['set-cookie'])
  expect(cookieHeader).toContain('paike_token=')
  expect(cookieHeader).toContain('XSRF-TOKEN=')

  return {
    cookieHeader,
    csrfToken: decodeURIComponent(cookieValue(cookieHeader, 'XSRF-TOKEN')),
  }
}

export function apiHeaders(auth: AuthState) {
  return {
    Cookie: auth.cookieHeader,
    'X-CSRF-Token': auth.csrfToken,
  }
}

export async function loginAndGoTo(page: Page, path: string, auth: AuthState, baseUrl: string) {
  await page.context().addCookies(cookiesForBrowser(auth.cookieHeader))
  await page.goto(`${baseUrl}${path}`)
  await page.waitForTimeout(1000)
}
