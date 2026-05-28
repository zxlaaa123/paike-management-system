import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUserApi, loginApi, logoutApi, type UserInfo } from '../api/auth'

let fetchCurrentUserInflight: Promise<UserInfo> | null = null

/**
 * 认证 Store
 *
 * 安全架构说明：
 * - JWT token 现在通过后端 httpOnly Cookie (paike_token) 传递，前端无法通过 JS 读取
 * - 实际鉴权由后端 AuthInterceptor 读取 Cookie 完成
 * - XSRF-TOKEN Cookie 由前端读取后放入 X-CSRF-Token 请求头，防 CSRF
 */
export const useAuthStore = defineStore('auth', () => {
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => Boolean(userInfo.value))

  async function login(username: string, password: string) {
    const data = await loginApi({ username, password })
    userInfo.value = data.userInfo
  }

  async function fetchCurrentUser() {
    if (!fetchCurrentUserInflight) {
      fetchCurrentUserInflight = getCurrentUserApi()
        .then((data) => {
          userInfo.value = data
          return data
        })
        .finally(() => {
          fetchCurrentUserInflight = null
        })
    }
    return fetchCurrentUserInflight
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      userInfo.value = null
    }
  }

  return {
    userInfo,
    isLoggedIn,
    login,
    logout,
    fetchCurrentUser,
  }
})
