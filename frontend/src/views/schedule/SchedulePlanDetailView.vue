<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getSchedulePlanById,
  getSchedulePlanItems,
  type SchedulePlan,
  type SchedulePlanItem,
} from '../../api/schedulePlan'
import {
  getScoreDetails,
  getScoreSummary,
  rescore,
  type ScheduleScoreDetail,
} from '../../api/scheduleScore'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.id))

const loading = ref(false)
const scoring = ref(false)
const plan = ref<SchedulePlan | null>(null)
const items = ref<SchedulePlanItem[]>([])
const scoreDetails = ref<ScheduleScoreDetail[]>([])
const scoreSummary = ref<any>(null)

const activeTab = ref('overview')

async function fetchData() {
  loading.value = true
  try {
    const [planData, itemsData] = await Promise.all([
      getSchedulePlanById(planId.value),
      getSchedulePlanItems(planId.value),
    ])
    plan.value = planData
    items.value = itemsData
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function fetchScoreData() {
  try {
    const [details, summary] = await Promise.all([
      getScoreDetails(planId.value),
      getScoreSummary(planId.value),
    ])
    scoreDetails.value = details
    scoreSummary.value = summary
  } catch (e) {
    console.error(e)
  }
}

async function handleRescore() {
  scoring.value = true
  try {
    const result = await rescore(planId.value)
    ElMessage.success(`重新评分完成，总分：${result.totalScore}`)
    await fetchScoreData()
    await fetchData() // 刷新方案总分
  } catch (e) {
    console.error(e)
  } finally {
    scoring.value = false
  }
}

function statusText(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', APPLIED: '已应用', ABANDONED: '已废弃' }
  return map[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = { DRAFT: 'primary', APPLIED: 'success', ABANDONED: 'info' }
  return map[status] || 'info'
}

function strategyText(type: string) {
  const map: Record<string, string> = {
    TEACHER_PRIORITY: '教师优先',
    CLASS_BALANCE: '班级均衡',
    CLASSROOM_UTILIZATION: '教室利用率',
    COMPREHENSIVE: '综合最优',
    CUSTOM: '自定义',
  }
  return map[type] || type
}

function scoreLevelType(level: string) {
  const map: Record<string, string> = { 优秀: 'success', 良好: 'primary', 一般: 'warning', 较差: 'danger', 不推荐: 'danger' }
  return map[level] || 'info'
}

function goBack() {
  router.push('/v3/schedule-plans')
}

onMounted(() => {
  fetchData()
  fetchScoreData()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-page-header @back="goBack" content="排课方案详情" />

    <template v-if="plan">
      <!-- 方案概览卡片 -->
      <el-card shadow="never" style="margin-top: 16px">
        <template #header>
          <div class="card-header">
            <span>{{ plan.name }}</span>
            <el-tag :type="statusTagType(plan.status)">{{ statusText(plan.status) }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="所属学期">{{ plan.semesterName || `ID:${plan.semesterId}` }}</el-descriptions-item>
          <el-descriptions-item label="策略类型">{{ strategyText(plan.strategyType) }}</el-descriptions-item>
          <el-descriptions-item label="方案状态">{{ statusText(plan.status) }}</el-descriptions-item>
          <el-descriptions-item label="总分">
            <span v-if="plan.totalScore !== null">{{ plan.totalScore }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="已排任务">{{ plan.scheduledCount }}</el-descriptions-item>
          <el-descriptions-item label="未排任务">{{ plan.unscheduledCount }}</el-descriptions-item>
          <el-descriptions-item label="冲突数量">{{ plan.conflictCount }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ plan.generatedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="应用时间">{{ plan.appliedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="方案说明" :span="3">{{ plan.description || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Tabs -->
      <el-card shadow="never" style="margin-top: 16px">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="课表明细" name="items">
            <el-table :data="items" stripe>
              <el-table-column prop="courseName" label="课程" width="120" />
              <el-table-column prop="teacherName" label="教师" width="100" />
              <el-table-column prop="className" label="班级" width="120" />
              <el-table-column label="时间" width="120">
                <template #default="{ row }">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</template>
              </el-table-column>
              <el-table-column prop="roomName" label="教室" width="120" />
              <el-table-column label="来源" width="80">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.sourceType === 'AUTO' ? '自动' : '手动' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="冲突" width="70">
                <template #default="{ row }">
                  <el-tag v-if="row.conflictFlag === 1" type="danger" size="small">有冲突</el-tag>
                  <span v-else>无</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="items.length === 0" description="暂无方案明细" />
          </el-tab-pane>

          <el-tab-pane label="评分明细" name="score">
            <div style="margin-bottom: 16px">
              <el-button type="primary" @click="handleRescore" :loading="scoring">重新评分</el-button>
            </div>
            <!-- 评分摘要 -->
            <el-row :gutter="16" v-if="scoreSummary" style="margin-bottom: 16px">
              <el-col :span="6">
                <el-statistic title="总分" :value="scoreSummary.totalScore">
                  <template #suffix>
                    <el-tag :type="scoreLevelType(scoreSummary.scoreLevel)" size="small">{{ scoreSummary.scoreLevel }}</el-tag>
                  </template>
                </el-statistic>
              </el-col>
              <el-col :span="6">
                <el-statistic title="硬约束违规" :value="scoreSummary.hardViolationCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="软约束扣分项" :value="scoreSummary.softViolationCount" />
              </el-col>
            </el-row>
            <!-- 评分明细表 -->
            <el-table :data="scoreDetails" stripe size="small">
              <el-table-column prop="ruleName" label="评分项" width="150" />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.score < 0 ? '扣分' : '正常' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="得分" width="80" />
              <el-table-column prop="violationCount" label="违规次数" width="90" />
              <el-table-column prop="detailMessage" label="说明" min-width="200" />
            </el-table>
            <el-empty v-if="scoreDetails.length === 0" description="暂无评分数据，请点击「重新评分」" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
