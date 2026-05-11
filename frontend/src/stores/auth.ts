import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const TOKEN_KEY = 'paike_admin_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')

  const isLoggedIn = computed(() => Boolean(token.value))

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem(TOKEN_KEY, newToken)
  }

  function clearToken() {
    token.value = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    isLoggedIn,
    setToken,
    clearToken,
  }
})
