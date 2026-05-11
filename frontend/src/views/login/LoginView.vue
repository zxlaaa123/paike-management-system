<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const form = reactive({
  username: '',
  password: '',
})

function handleMockLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  ElMessage.success('登录页占位：阶段 3 将接入真实登录')
  router.push('/dashboard')
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <h2>高校排课管理系统</h2>
      <p class="tip">管理员登录（占位页）</p>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button type="primary" class="submit-btn" @click="handleMockLogin">
          登录（占位）
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.login-card {
  width: 360px;
}

.tip {
  color: #909399;
  margin: 0 0 18px;
}

.submit-btn {
  width: 100%;
}
</style>
