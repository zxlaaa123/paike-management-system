<template>
  <div class="dashboard-page">
    <el-card shadow="never">
      <template #header>
        <div class="header">首页统计</div>
      </template>
      <el-alert v-if="loadError" type="warning" show-icon :closable="false" :title="loadError" />
      <el-alert v-if="!hasCurrentSemester" type="warning" show-icon :closable="false" title="当前未设置学期，请先创建或设置当前学期" style="margin-bottom: 12px" />

      <!-- 基础数据统计 -->
      <div class="cards">
        <el-card v-for="item in statsCards" :key="item.label" shadow="hover">
          <div class="label">{{ item.label }}</div>
          <div class="value">{{ item.value }}</div>
        </el-card>
      </div>

      <!-- V3 排课概览 -->
      <template v-if="hasCurrentSemester && dashboardStats">
        <el-divider />
        <div class="section-title">V3 排课概览</div>
        <el-alert :title="`当前学期：${currentSemesterName}`" type="info" show-icon :closable="false" style="margin-bottom: 12px" />

        <el-row :gutter="16" style="margin-bottom: 16px">
          <el-col :span="4">
            <el-statistic title="方案总数" :value="v3Overview.totalPlans" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="草稿方案" :value="v3Overview.draftPlans" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="已应用方案" :value="v3Overview.appliedPlans" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="正式课表课程" :value="v3Overview.formalScheduleCount" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="未排任务" :value="v3Overview.totalUnassignedTasks" />
          </el-col>
          <el-col :span="4">
            <el-statistic title="冲突数量" :value="v3Overview.totalConflicts" />
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-descriptions title="最优方案" :column="2" border>
              <el-descriptions-item label="方案名称">{{ v3Overview.bestPlanName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="评分">
                <el-tag v-if="v3Overview.bestPlanScore" type="success" size="small">{{ v3Overview.bestPlanScore }}</el-tag>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="策略">{{ v3Overview.bestPlanStrategy || '—' }}</el-descriptions-item>
            </el-descriptions>
          </el-col>
          <el-col :span="12">
            <el-descriptions title="当前已应用方案" :column="2" border>
              <el-descriptions-item label="方案名称">
                <span v-if="v3Overview.hasAppliedPlan">{{ v3Overview.appliedPlanName || '—' }}</span>
                <el-tag v-else type="warning" size="small">暂无</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="评分">
                <span v-if="v3Overview.appliedPlanScore">{{ v3Overview.appliedPlanScore }}</span>
                <span v-else>—</span>
              </el-descriptions-item>
              <el-descriptions-item label="应用时间">{{ v3Overview.appliedPlanAppliedAt || '—' }}</el-descriptions-item>
            </el-descriptions>
          </el-col>
        </el-row>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTeacherList } from '../../api/teacher'
import { getClassList } from '../../api/classInfo'
import { getClassroomList } from '../../api/classroom'
import { getCourseList } from '../../api/course'
import { getCurrentSemester, type Semester } from '../../api/semester'
import { getDashboardStats, type DashboardStats } from '../../api/scheduleStatistics'

const loadError = ref('')
const stats = reactive({
  teacherCount: 0,
  classCount: 0,
  classroomCount: 0,
  courseCount: 0,
})

const currentSemester = ref<Semester | null>(null)
const dashboardStats = ref<DashboardStats | null>(null)

const hasCurrentSemester = computed(() => currentSemester.value !== null)
const currentSemesterName = computed(() => currentSemester.value?.name || '—')
const v3Overview = computed(() => dashboardStats.value?.v3Overview ?? {
  semesterId: 0, totalPlans: 0, draftPlans: 0, appliedPlans: 0, abandonedPlans: 0,
  bestPlanId: null, bestPlanName: null, bestPlanScore: null, bestPlanStrategy: null,
  hasAppliedPlan: false, appliedPlanId: null, appliedPlanName: null, appliedPlanScore: null,
  appliedPlanAppliedAt: null, formalScheduleCount: 0, totalUnassignedTasks: 0, totalConflicts: 0,
})

const statsCards = computed(() => [
  { label: '教师数量', value: stats.teacherCount },
  { label: '班级数量', value: stats.classCount },
  { label: '教室数量', value: stats.classroomCount },
  { label: '课程数量', value: stats.courseCount },
])

async function fetchStats() {
  loadError.value = ''
  try {
    const [teacherRes, classRes, classroomRes, courseRes] = await Promise.all([
      getTeacherList({ page: 1, size: 1 }),
      getClassList({ page: 1, size: 1 }),
      getClassroomList({ page: 1, size: 1 }),
      getCourseList({ page: 1, size: 1 }),
    ])
    stats.teacherCount = teacherRes.total
    stats.classCount = classRes.total
    stats.classroomCount = classroomRes.total
    stats.courseCount = courseRes.total
  } catch {
    loadError.value = '统计数据加载失败，请稍后重试。'
    ElMessage.error(loadError.value)
  }
}

async function fetchDashboard() {
  try {
    currentSemester.value = await getCurrentSemester()
    if (currentSemester.value) {
      dashboardStats.value = await getDashboardStats({ semesterId: currentSemester.value.id })
    }
  } catch {
    // 无当前学期时不报错
  }
}

onMounted(async () => {
  await Promise.all([fetchStats(), fetchDashboard()])
})
</script>

<style scoped>
.dashboard-page {
  padding: 8px;
}

.header {
  font-weight: 600;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 8px 0 12px;
  color: var(--el-text-color-primary);
}

.cards {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 1fr));
  gap: 12px;
}

.label {
  color: #909399;
  font-size: 14px;
}

.value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
}
</style>
