<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import { getScheduleScoreExplanation, type ScheduleScoreExplanation } from '../../api/v4ScheduleAnalysisApi'
import { strategyText } from '../../utils/status'
import { extractMessage } from '../../utils/errors'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const plan = ref<SchedulePlan | null>(null)
const scoreExplanation = ref<ScheduleScoreExplanation | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const [planData, scoreData] = await Promise.all([
      getSchedulePlanById(planId.value),
      getScheduleScoreExplanation(planId.value),
    ])
    plan.value = planData
    scoreExplanation.value = scoreData
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载评分详情失败'))
  } finally {
    loading.value = false
  }
}

function scoreTagType(value: number) {
  if (value >= 90) return 'success'
  if (value >= 75) return 'primary'
  if (value >= 60) return 'warning'
  return 'danger'
}

onMounted(fetchData)
</script>

<template>
  <div class="score-page" v-loading="loading">
    <el-page-header content="V4 评分详情解释" @back="router.push(`/v4/schedule-analysis/${planId}`)" />

    <el-card v-if="plan && scoreExplanation" shadow="never" class="hero-card">
      <div class="hero-row">
        <div>
          <div class="eyebrow">V4 阶段 2</div>
          <h1>{{ scoreExplanation.planName }}</h1>
          <p>{{ strategyText(plan.strategyType) }} · 总分 {{ scoreExplanation.totalScore ?? '—' }}</p>
        </div>
        <el-tag size="large" :type="scoreTagType(scoreExplanation.totalScore ?? 0)">
          {{ scoreExplanation.totalScore ?? '—' }}
        </el-tag>
      </div>
      <el-alert
        type="info"
        :closable="false"
        title="评分计算来源"
        :description="scoreExplanation.calculationSource"
      />
    </el-card>

    <el-empty
      v-if="!loading && scoreExplanation && scoreExplanation.scoreItems.length === 0"
      description="当前方案暂无评分明细"
    />

    <el-row v-else-if="scoreExplanation" :gutter="16">
      <el-col :span="24">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-title">评分维度总览</div>
          </template>
          <el-table :data="scoreExplanation.scoreItems" stripe>
            <el-table-column prop="scoreName" label="评分维度" min-width="150" />
            <el-table-column prop="scoreKey" label="维度编码" width="180" />
            <el-table-column label="当前得分" width="120">
              <template #default="{ row }">
                <el-tag :type="scoreTagType(row.scoreValue)">{{ row.scoreValue }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="maxScore" label="满分" width="100" />
            <el-table-column prop="weight" label="权重" width="100" />
            <el-table-column prop="violationCount" label="违规/偏差值" width="120" />
            <el-table-column prop="description" label="评分说明" min-width="220" />
            <el-table-column prop="detailMessage" label="计算结果说明" min-width="260" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row v-if="scoreExplanation" :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="tip-card">
          <template #header>
            <div class="card-title">怎么看这张表</div>
          </template>
          <ul class="tip-list">
            <li>`当前得分` 是把单项扣分换算成 0-100 的展示分，方便横向比较。</li>
            <li>`权重` 来自 V3 规则权重配置，不会在这个页面被修改。</li>
            <li>`计算结果说明` 直接来自后端评分逻辑或评分明细记录。</li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="tip-card">
          <template #header>
            <div class="card-title">后续阶段入口</div>
          </template>
          <div class="link-row">
            <el-button @click="router.push(`/v4/schedule-analysis/${planId}`)">回到质量分析</el-button>
            <el-button type="warning" plain @click="router.push(`/v4/schedule-analysis/${planId}/risks`)">风险诊断中心</el-button>
            <el-button type="success" plain @click="router.push(`/v4/schedule-analysis/${planId}/charts`)">可视化图表</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.score-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card,
.table-card,
.tip-card {
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
  color: #223047;
  font-size: 28px;
}

.hero-row p {
  margin: 0;
  color: #667085;
}

.eyebrow {
  color: #b85c38;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.card-title {
  color: #223047;
  font-weight: 700;
}

.tip-list {
  margin: 0;
  padding-left: 18px;
  color: #475467;
  line-height: 1.9;
}

.link-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .hero-row {
    flex-direction: column;
  }
}
</style>
