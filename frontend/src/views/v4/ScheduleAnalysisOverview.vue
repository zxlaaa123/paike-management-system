<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentSemester, type Semester } from '../../api/semester'
import { getSchedulePlanList, type SchedulePlan } from '../../api/schedulePlan'
import { schedulePlanStatusText as statusText, strategyText } from '../../utils/status'

const router = useRouter()

const loading = ref(false)
const currentSemester = ref<Semester | null>(null)
const plans = ref<SchedulePlan[]>([])

const query = reactive({
  page: 1,
  size: 50,
})

const hasCurrentSemester = computed(() => currentSemester.value !== null)

async function fetchData() {
  loading.value = true
  try {
    currentSemester.value = await getCurrentSemester().catch(() => null)
    if (!currentSemester.value) {
      plans.value = []
      return
    }
    const page = await getSchedulePlanList({
      semesterId: currentSemester.value.id,
      page: query.page,
      size: query.size,
    })
    plans.value = page.records || []
  } finally {
    loading.value = false
  }
}

function goDetail(planId: number) {
  router.push(`/v4/schedule-analysis/${planId}`)
}

function goV3Plan(planId: number) {
  router.push(`/v3/schedule-plans/${planId}`)
}

function scoreLevel(score?: number | null) {
  if (score == null) return '待分析'
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '可用'
  return '需优化'
}

onMounted(fetchData)
</script>

<template>
  <div class="v4-page">
    <el-card shadow="never" class="hero-card">
      <div class="hero-row">
        <div>
          <div class="eyebrow">V4 阶段 0</div>
          <h1>排课质量分析入口已就绪</h1>
          <p>
            当前页面先复用 V3 方案数据作为 V4 分析入口。下一阶段会在这个入口上接入
            `/api/v4/schedule-analysis/*` 的真实分析接口。
          </p>
        </div>
        <el-button type="primary" @click="fetchData" :loading="loading">刷新基线数据</el-button>
      </div>
      <el-alert
        v-if="currentSemester"
        type="info"
        :closable="false"
        :title="`当前学期：${currentSemester.name}`"
        description="V4 只读分析会继续沿用当前学期作为默认数据边界。"
      />
      <el-alert
        v-else
        type="warning"
        :closable="false"
        title="暂无当前学期"
        description="请先在 V3 学期管理中设置当前学期，再进入 V4 分析阶段。"
      />
    </el-card>

    <el-row v-loading="loading" :gutter="16" class="metric-row">
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">方案数量</div>
          <div class="metric-value">{{ plans.length }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">已应用方案</div>
          <div class="metric-value">{{ plans.filter((item) => item.status === 'APPLIED').length }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">含未排任务方案</div>
          <div class="metric-value">{{ plans.filter((item) => item.unscheduledCount > 0).length }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">含冲突方案</div>
          <div class="metric-value">{{ plans.filter((item) => item.conflictCount > 0).length }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty
      v-if="hasCurrentSemester && !loading && plans.length === 0"
      description="当前学期还没有可供分析的排课方案"
    />

    <div v-else class="plan-grid">
      <el-card v-for="plan in plans" :key="plan.id" shadow="hover" class="plan-card">
        <template #header>
          <div class="plan-header">
            <div>
              <div class="plan-name">{{ plan.name }}</div>
              <div class="plan-meta">{{ strategyText(plan.strategyType) }} · {{ statusText(plan.status) }}</div>
            </div>
            <el-tag :type="plan.status === 'APPLIED' ? 'success' : 'info'">
              {{ plan.status === 'APPLIED' ? '当前正式方案' : '候选方案' }}
            </el-tag>
          </div>
        </template>

        <div class="score-block">
          <div class="score-value">{{ plan.totalScore ?? '—' }}</div>
          <div class="score-label">当前总分 / {{ scoreLevel(plan.totalScore) }}</div>
        </div>

        <div class="stat-list">
          <div class="stat-item">
            <span>已排任务</span>
            <strong>{{ plan.scheduledCount }}</strong>
          </div>
          <div class="stat-item">
            <span>未排任务</span>
            <strong>{{ plan.unscheduledCount }}</strong>
          </div>
          <div class="stat-item">
            <span>冲突数量</span>
            <strong>{{ plan.conflictCount }}</strong>
          </div>
          <div class="stat-item">
            <span>最近更新时间</span>
            <strong>{{ plan.updatedAt || '—' }}</strong>
          </div>
        </div>

        <div class="action-row">
          <el-button type="primary" @click="goDetail(plan.id)">进入 V4 分析</el-button>
          <el-button @click="goV3Plan(plan.id)">查看 V3 详情</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.v4-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card,
.metric-card,
.plan-card {
  border-radius: 18px;
}

.hero-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.hero-row h1 {
  margin: 6px 0 10px;
  font-size: 28px;
}

.hero-row p {
  margin: 0;
  max-width: 760px;
  color: #5b6472;
  line-height: 1.7;
}

.eyebrow {
  color: #b85c38;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.metric-row {
  margin: 0;
}

.metric-card {
  background: linear-gradient(160deg, #fff9f2 0%, #ffffff 100%);
}

.metric-label {
  color: #7b8594;
  font-size: 13px;
}

.metric-value {
  margin-top: 10px;
  color: #223047;
  font-size: 30px;
  font-weight: 700;
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.plan-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.plan-name {
  font-size: 18px;
  font-weight: 700;
  color: #223047;
}

.plan-meta {
  margin-top: 6px;
  color: #6a7380;
  font-size: 13px;
}

.score-block {
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f3f8ff 0%, #eef7f1 100%);
}

.score-value {
  color: #173a63;
  font-size: 32px;
  font-weight: 700;
}

.score-label {
  margin-top: 4px;
  color: #64758b;
  font-size: 13px;
}

.stat-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}

.stat-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-item span {
  color: #7a828d;
  font-size: 12px;
}

.stat-item strong {
  color: #253246;
  font-size: 16px;
}

.action-row {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 768px) {
  .hero-row {
    flex-direction: column;
  }

  .stat-list {
    grid-template-columns: 1fr;
  }

  .action-row {
    flex-direction: column;
  }
}
</style>
