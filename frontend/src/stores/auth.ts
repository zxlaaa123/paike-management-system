import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUserApi, loginApi, logoutApi, type UserInfo } from '../api/auth'

const TOKEN_KEY = 'paike_admin_token'

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
