import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUserApi, loginApi, logoutApi, type UserInfo } from '../api/auth'
import { TOKEN_KEY } from '../utils/constants'

/**
 * 认证 Store
 *
 * 安全架构说明：
 * - JWT token 现在通过后端 httpOnly Cookie (paike_token) 传递，前端无法通过 JS 读取
 * - localStorage 中的 token 仅作为 UI 登录状态标志（用于页面刷新后判断是否需要调 /me）
 * - 实际鉴权由后端 AuthInterceptor 读取 Cookie 完成
 * - XSRF-TOKEN Cookie 由前端读取后放入 X-CSRF-Token 请求头，防 CSRF
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => Boolean(token.value))

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem(TOKEN_KEY, newToken)
  }

  function clearToken() {
    token.value = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  async function login(username: string, password: string) {
    const data = await loginApi({ username, password })
    setToken(data.token)
    userInfo.value = data.userInfo
  }

  async function fetchCurrentUser() {
    if (!token.value) {
      userInfo.value = null
      return null
    }
    const data = await getCurrentUserApi()
    userInfo.value = data
    return data
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      userInfo.value = null
      clearToken()
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setToken,
    clearToken,
    login,
    logout,
    fetchCurrentUser,
  }
})
