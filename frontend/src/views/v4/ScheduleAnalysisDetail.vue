<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LocalReplanDialog from '../../components/v4/LocalReplanDialog.vue'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import { getScheduleAnalysisSummary, refreshScheduleAnalysisSummary, type ScheduleAnalysisSummary } from '../../api/v4ScheduleAnalysisApi'
import type { ScheduleReplanResult } from '../../api/v4ScheduleReplanApi'
import { strategyText } from '../../utils/status'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const refreshing = ref(false)
const plan = ref<SchedulePlan | null>(null)
const summary = ref<ScheduleAnalysisSummary | null>(null)
const localReplanVisible = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const [planData, summaryData] = await Promise.all([
      getSchedulePlanById(planId.value),
      getScheduleAnalysisSummary(planId.value),
    ])
    plan.value = planData
    summary.value = summaryData
  } catch (error: any) {
    ElMessage.error(error?.message || '加载分析数据失败')
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  try {
    await refreshScheduleAnalysisSummary(planId.value)
    await fetchData()
    ElMessage.success('分析结果已刷新')
  } catch (error: any) {
    ElMessage.error(error?.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

function handleLocalReplanSuccess(result: ScheduleReplanResult) {
  localReplanVisible.value = false
  router.push(`/v3/schedule-plans/${result.newPlanId}`)
}

onMounted(fetchData)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-page-header content="V4 质量分析详情入口" @back="router.push('/v4/schedule-analysis')" />

    <el-card v-if="plan" shadow="never" class="summary-card">
      <div class="summary-top">
        <div>
          <div class="summary-title">{{ plan.name }}</div>
          <div class="summary-meta">
            {{ strategyText(plan.strategyType) }} · 学期 ID {{ plan.semesterId }} · 当前状态 {{ plan.status }}
          </div>
        </div>
        <div class="summary-actions">
          <el-button type="primary" plain :loading="refreshing" @click="handleRefresh">刷新分析</el-button>
          <el-button @click="router.push(`/v3/schedule-plans/${plan.id}`)">回到 V3 方案详情</el-button>
        </div>
      </div>

      <el-row v-if="summary" :gutter="16" class="summary-grid">
        <el-col :span="6">
          <div class="summary-box">
            <span>总分</span>
            <strong>{{ summary.totalScore ?? '—' }}</strong>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-box">
            <span>已排任务</span>
            <strong>{{ summary.scheduledCount }}</strong>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-box">
            <span>未排任务</span>
            <strong>{{ summary.unscheduledCount }}</strong>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-box">
            <span>冲突数量</span>
            <strong>{{ summary.conflictCount }}</strong>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="summary" shadow="never" class="check-card">
      <template #header>
        <div class="card-title">核心指标卡片</div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="教师数量">{{ summary.teacherCount }}</el-descriptions-item>
        <el-descriptions-item label="班级数量">{{ summary.classCount }}</el-descriptions-item>
        <el-descriptions-item label="教室数量">{{ summary.roomCount }}</el-descriptions-item>
        <el-descriptions-item label="课程数量">{{ summary.courseCount }}</el-descriptions-item>
        <el-descriptions-item label="教师平均负载">{{ summary.teacherAverageHours }}</el-descriptions-item>
        <el-descriptions-item label="教师最大负载">{{ summary.teacherMaxHours }}</el-descriptions-item>
        <el-descriptions-item label="教师最小负载">{{ summary.teacherMinHours }}</el-descriptions-item>
        <el-descriptions-item label="教室利用率">{{ summary.roomUtilizationRate }}%</el-descriptions-item>
        <el-descriptions-item label="班级日均课时">{{ summary.classAverageDailyLessons }}</el-descriptions-item>
        <el-descriptions-item label="高风险">{{ summary.highRiskCount }}</el-descriptions-item>
        <el-descriptions-item label="中风险">{{ summary.mediumRiskCount }}</el-descriptions-item>
        <el-descriptions-item label="低风险">{{ summary.lowRiskCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row v-if="summary" :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="check-card">
          <template #header>
            <div class="card-title">质量结论</div>
          </template>
          <div class="quality-panel">
            <el-tag size="large" :type="summary.qualityLevel === '优秀' ? 'success' : summary.qualityLevel === '良好' ? 'primary' : summary.qualityLevel === '可用' ? 'warning' : 'danger'">
              {{ summary.qualityLevel }}
            </el-tag>
            <p>{{ summary.qualitySummary }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="check-card">
          <template #header>
            <div class="card-title">建议动作</div>
          </template>
          <el-empty v-if="summary.suggestions.length === 0" description="暂无建议" />
          <ul v-else class="suggestion-list">
            <li v-for="item in summary.suggestions" :key="item">{{ item }}</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="summary" shadow="never" class="check-card">
      <template #header>
        <div class="card-title">快捷入口</div>
      </template>
      <div class="quick-links">
        <el-button @click="router.push(`/v3/schedule-plans/${planId}`)">查看 V3 方案详情</el-button>
        <el-button type="primary" plain @click="router.push(`/v4/schedule-analysis/${planId}/score`)">评分详情解释</el-button>
        <el-button type="warning" plain @click="router.push(`/v4/schedule-analysis/${planId}/risks`)">风险诊断中心</el-button>
        <el-button type="success" plain @click="router.push(`/v4/schedule-analysis/${planId}/charts`)">图表分析</el-button>
        <el-button type="info" plain @click="router.push(`/v4/schedule-analysis/${planId}/locks`)">课程锁定管理</el-button>
        <el-button
          type="success"
          :disabled="plan?.status === 'ABANDONED' || plan?.status === 'FAILED'"
          @click="localReplanVisible = true"
        >
          局部重排
        </el-button>
      </div>
      <div class="safe-note">当前阶段所有数据均来自只读分析接口，不会修改正式课表或方案明细。</div>
    </el-card>

    <el-empty v-if="!loading && !summary" description="未找到对应分析结果" />
    <LocalReplanDialog v-model="localReplanVisible" :plan-id="planId" @success="handleLocalReplanSuccess" />
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-card,
.check-card {
  border-radius: 18px;
}

.summary-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.summary-title {
  color: #223047;
  font-size: 26px;
  font-weight: 700;
}

.summary-meta {
  margin-top: 8px;
  color: #69717d;
}

.summary-grid {
  margin: 0;
}

.summary-box {
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(145deg, #fbf8f3 0%, #ffffff 100%);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-box span {
  color: #717b87;
  font-size: 13px;
}

.summary-box strong {
  color: #1f3045;
  font-size: 28px;
}

.card-title {
  font-weight: 700;
  color: #223047;
}

.quality-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.quality-panel p {
  margin: 0;
  color: #5f6978;
  line-height: 1.7;
}

.suggestion-list {
  margin: 0;
  padding-left: 18px;
  color: #475467;
  line-height: 1.8;
}

.quick-links {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.safe-note {
  margin-top: 14px;
  color: #69717d;
  font-size: 13px;
}

@media (max-width: 768px) {
  .summary-top {
    flex-direction: column;
  }
}
</style>
