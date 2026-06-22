<template>
  <div v-if="error" class="error-boundary">
    <h2>页面出错了</h2>
    <p>{{ errorMessage }}</p>
    <el-button type="primary" @click="reset">返回首页</el-button>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { useRouter } from 'vue-router'

const error = ref<Error | null>(null)
const errorMessage = ref('')
const router = useRouter()

onErrorCaptured((err) => {
  error.value = err
  errorMessage.value = err.message || '未知错误'
  recordLocalError(err)
  console.error('[ErrorBoundary]', err)
  return false
})

function reset() {
  error.value = null
  errorMessage.value = ''
  router.push('/dashboard')
}

function recordLocalError(err: Error) {
  try {
    // 安全：不持久化完整错误栈，防止敏感信息（组件 trace、内部路径）留存到 sessionStorage。
    const item = {
      message: err.message || '未知错误',
      path: router.currentRoute.value.fullPath,
      time: new Date().toISOString(),
    }
    const key = 'paike:error-boundary:last'
    sessionStorage.setItem(key, JSON.stringify(item))
  } catch {
    // local storage may be unavailable in restricted browser modes.
  }
}
</script>

<style scoped>
.error-boundary {
  padding: 40px;
  text-align: center;
}
</style>
