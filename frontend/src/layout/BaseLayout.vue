<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { House, DataLine, Calendar, Reading, PieChart, School, ArrowDown, Warning, Loading, Collection } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getCurrentSemester, setCurrentSemester, getAllSemesters, type Semester } from '../api/semester'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const currentSemester = ref<Semester | null>(null)
const allSemesterList = ref<Semester[]>([])
const semesterLoading = ref(false)

async function fetchCurrentSemester() {
  semesterLoading.value = true
  try {
    currentSemester.value = await getCurrentSemester()
  } catch (_e) {
    currentSemester.value = null
  } finally {
    semesterLoading.value = false
  }
}

async function fetchAllSemesters() {
  try {
    allSemesterList.value = await getAllSemesters()
  } catch (_e) {
    allSemesterList.value = []
  }
}

async function handleSemesterChange(semesterId: number) {
  if (semesterId === currentSemester.value?.id) return
  try {
    await setCurrentSemester(semesterId)
    await fetchCurrentSemester()
    await fetchAllSemesters()
    ElMessage.success('已切换当前学期')
  } catch (_e) {
    console.error(_e)
  }
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

onMounted(() => {
  fetchCurrentSemester()
  fetchAllSemesters()
})
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="logo">高校排课管理系统</div>
      <el-menu router :default-active="route.path">
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/semesters">
          <el-icon><School /></el-icon>
          <span>学期管理</span>
        </el-menu-item>
        <el-sub-menu index="basic-data">
          <template #title>
            <el-icon><Reading /></el-icon>
            <span>基础数据管理</span>
          </template>
          <el-menu-item index="/teachers">教师管理</el-menu-item>
          <el-menu-item index="/classes">班级管理</el-menu-item>
          <el-menu-item index="/classrooms">教室管理</el-menu-item>
          <el-menu-item index="/courses">课程管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="teaching">
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>教学管理</span>
          </template>
          <el-menu-item index="/teaching-tasks">教学任务管理</el-menu-item>
          <el-menu-item index="/teacher-unavailable-times">教师禁排时间</el-menu-item>
          <el-menu-item index="/schedule-rules">排课规则配置</el-menu-item>
          <el-menu-item index="/auto-schedule">自动排课</el-menu-item>
          <el-menu-item index="/schedule">手动排课</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="v3-schedule">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>V3 排课优化</span>
          </template>
          <el-menu-item index="/v3/schedule-generate">多方案生成</el-menu-item>
          <el-menu-item index="/v3/schedule-plans">排课方案管理</el-menu-item>
          <el-menu-item index="/v3/schedule-compare">方案对比</el-menu-item>
          <el-menu-item index="/v3/schedule-rules">规则权重配置</el-menu-item>
          <el-menu-item index="/v3/statistics">统计分析</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="v4-analysis">
          <template #title>
            <el-icon><Calendar /></el-icon>
            <span>V4 质量分析</span>
          </template>
          <el-menu-item index="/v4/schedule-analysis">分析总览</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="v5-repair">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>V5 修复流程</span>
          </template>
          <el-menu-item index="/v5/repair-tasks">修复任务管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="analysis">
          <template #title>
            <el-icon><PieChart /></el-icon>
            <span>排课分析</span>
          </template>
          <el-menu-item index="/unscheduled-tasks">未排任务</el-menu-item>
          <el-menu-item index="/schedule-conflict-reports">冲突报告</el-menu-item>
          <el-menu-item index="/schedule-score">课表评分</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="timetable">
          <template #title>
            <el-icon><Calendar /></el-icon>
            <span>课表查询</span>
          </template>
          <el-menu-item index="/timetable/class">班级课表</el-menu-item>
          <el-menu-item index="/timetable/teacher">教师课表</el-menu-item>
          <el-menu-item index="/timetable/classroom">教室课表</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <span>当前登录：{{ authStore.userInfo?.realName || '管理员' }}</span>
          <span class="header-divider">|</span>
          <span v-if="semesterLoading" class="semester-info">
            <el-icon class="is-loading"><Loading /></el-icon>
            加载学期中...
          </span>
          <span v-else-if="currentSemester" class="semester-info">
            当前学期：
            <el-dropdown trigger="click" @command="handleSemesterChange">
              <span class="semester-select">
                {{ currentSemester.name }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="s in allSemesterList"
                    :key="s.id"
                    :command="s.id"
                    :disabled="s.id === currentSemester?.id"
                  >
                    {{ s.name }}
                    <el-tag v-if="s.isCurrent === 1" type="success" size="small" style="margin-left: 8px">当前</el-tag>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
          <span v-else class="semester-info semester-warning">
            <el-icon><Warning /></el-icon>
            当前未设置学期，请先在「学期管理」中设置
          </span>
        </div>
        <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
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

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-divider {
  color: #dcdfe6;
}

.semester-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #606266;
}

.semester-select {
  cursor: pointer;
  color: #409eff;
  display: flex;
  align-items: center;
  gap: 2px;
}

.semester-warning {
  color: #e6a23c;
}

.app-main {
  background: #f5f7fa;
}
</style>
