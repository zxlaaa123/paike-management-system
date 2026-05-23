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
  // TODO: 接入前端监控（Sentry / 自建 errorLogApi）
  console.error('[ErrorBoundary]', err)
  return false
})

function reset() {
  error.value = null
  errorMessage.value = ''
  router.push('/dashboard')
}
</script>

<style scoped>
.error-boundary {
  padding: 40px;
  text-align: center;
}
</style>
