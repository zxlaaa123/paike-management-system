<script setup lang="ts">
import { House, DataLine, Calendar, Reading } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function handleLogout() {
  authStore.clearToken()
  router.push('/login')
}
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="logo">高校排课管理系统</div>
      <el-menu router default-active="/dashboard">
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-sub-menu index="basic-data">
          <template #title>
            <el-icon><Reading /></el-icon>
            <span>基础数据管理</span>
          </template>
          <el-menu-item index="/placeholder/teachers">教师管理</el-menu-item>
          <el-menu-item index="/placeholder/classes">班级管理</el-menu-item>
          <el-menu-item index="/placeholder/classrooms">教室管理</el-menu-item>
          <el-menu-item index="/placeholder/courses">课程管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="teaching">
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>教学管理</span>
          </template>
          <el-menu-item index="/placeholder/tasks">教学任务管理</el-menu-item>
          <el-menu-item index="/placeholder/schedule">手动排课</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="timetable">
          <template #title>
            <el-icon><Calendar /></el-icon>
            <span>课表查询</span>
          </template>
          <el-menu-item index="/placeholder/class-timetable">班级课表</el-menu-item>
          <el-menu-item index="/placeholder/teacher-timetable">教师课表</el-menu-item>
          <el-menu-item index="/placeholder/room-timetable">教室课表</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <span>V1 阶段 2 - 前端基础框架</span>
        <el-button type="danger" plain @click="handleLogout">退出占位</el-button>
      </el-header>
      <el-main class="app-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-aside {
  border-right: 1px solid #ebeef5;
  background: #fff;
}

.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.app-main {
  background: #f5f7fa;
}
</style>
