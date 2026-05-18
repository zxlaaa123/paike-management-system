<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ScheduleAdjustDialog from '../../components/v4/ScheduleAdjustDialog.vue'
import LocalReplanDialog from '../../components/v4/LocalReplanDialog.vue'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import {
  getScheduleRiskList,
  refreshScheduleRiskList,
  type ScheduleRiskIssue,
  type ScheduleRiskList,
} from '../../api/v4ScheduleAnalysisApi'
import type { ScheduleAdjustmentApplyResult } from '../../api/v4ScheduleAdjustmentApi'
import type { ScheduleReplanResult } from '../../api/v4ScheduleReplanApi'
import { strategyText } from '../../utils/status'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const refreshing = ref(false)
const plan = ref<SchedulePlan | null>(null)
const riskData = ref<ScheduleRiskList | null>(null)
const selectedRisk = ref<ScheduleRiskIssue | null>(null)
const detailVisible = ref(false)
const adjustVisible = ref(false)
const adjustingRisk = ref<ScheduleRiskIssue | null>(null)
const localReplanVisible = ref(false)

const filters = ref({
  riskType: '',
  level: '',
  onlyUnresolved: true,
  keyword: '',
})

const riskTypeOptions = [
  { value: 'TEACHER_CONFLICT', label: '教师冲突' },
  { value: 'CLASS_CONFLICT', label: '班级冲突' },
  { value: 'ROOM_CONFLICT', label: '教室冲突' },
  { value: 'ROOM_CAPACITY', label: '容量不足' },
  { value: 'ROOM_TYPE', label: '类型不匹配' },
  { value: 'TEACHER_UNAVAILABLE', label: '教师禁排' },
  { value: 'UNSCHEDULED_TASK', label: '未排任务' },
  { value: 'TEACHER_OVERLOAD', label: '教师负载过高' },
  { value: 'CLASS_DAILY_OVERLOAD', label: '班级单日过载' },
  { value: 'ROOM_LOW_UTILIZATION', label: '教室低利用率' },
  { value: 'ROOM_HIGH_UTILIZATION', label: '教室高利用率' },
]

const displayedRisks = computed(() => {
  const risks = riskData.value?.risks ?? []
  const keyword = filters.value.keyword.trim().toLowerCase()
  if (!keyword) {
    return risks
  }
  return risks.filter((risk) =>
    [
      risk.title,
      risk.description,
      risk.suggestion,
      risk.affectedObjects ?? '',
      risk.relatedTeacherName ?? '',
      risk.relatedClassName ?? '',
      risk.relatedRoomName ?? '',
      risk.relatedCourseName ?? '',
      ...(risk.detailLines ?? []),
    ]
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})

async function fetchData() {
  loading.value = true
  try {
    const [planData, risks] = await Promise.all([
      getSchedulePlanById(planId.value),
      getScheduleRiskList(planId.value, {
        riskType: filters.value.riskType || undefined,
        level: filters.value.level || undefined,
        onlyUnresolved: filters.value.onlyUnresolved,
      }),
    ])
    plan.value = planData
    riskData.value = risks
  } catch (error: any) {
    ElMessage.error(error?.message || '加载风险诊断失败')
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  try {
    await refreshScheduleRiskList(planId.value)
    await fetchData()
    ElMessage.success('风险诊断已刷新')
  } catch (error: any) {
    ElMessage.error(error?.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

function levelTagType(level: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

function levelText(level: string) {
  if (level === 'HIGH') return '高风险'
  if (level === 'MEDIUM') return '中风险'
  return '低风险'
}

function openDetail(risk: ScheduleRiskIssue) {
  selectedRisk.value = risk
  detailVisible.value = true
}

function canAdjust(risk: ScheduleRiskIssue) {
  return !!risk.relatedItemIds?.length
}

function openAdjust(risk: ScheduleRiskIssue) {
  if (!canAdjust(risk)) {
    ElMessage.warning('当前风险没有可定位的方案明细，暂时无法直接调整')
    return
  }
  adjustingRisk.value = risk
  adjustVisible.value = true
}

async function handleAdjustSuccess(_result: ScheduleAdjustmentApplyResult) {
  adjustVisible.value = false
  detailVisible.value = false
  await fetchData()
}

function handleLocalReplanSuccess(result: ScheduleReplanResult) {
  localReplanVisible.value = false
  router.push(`/v3/schedule-plans/${result.newPlanId}`)
}

const adjustContext = computed(() => {
  if (!adjustingRisk.value) return null
  return {
    targetType: 'PLAN_ITEM' as const,
    planId: planId.value,
    planItemId: adjustingRisk.value.relatedItemIds[0] ?? null,
    courseName: adjustingRisk.value.relatedCourseName,
    teacherName: adjustingRisk.value.relatedTeacherName,
    className: adjustingRisk.value.relatedClassName,
    currentRoomName: adjustingRisk.value.relatedRoomName,
    currentTimeLabel: adjustingRisk.value.weekDay
      ? `周${adjustingRisk.value.weekDay} ${adjustingRisk.value.period || ''}`
      : adjustingRisk.value.period || '—',
  }
})

onMounted(fetchData)
</script>

<template>
  <div class="risk-page" v-loading="loading">
    <el-page-header content="V4 冲突风险诊断" @back="router.push(`/v4/schedule-analysis/${planId}`)" />

    <el-card v-if="plan" shadow="never" class="hero-card">
      <div class="hero-row">
        <div>
          <div class="eyebrow">V4 阶段 3</div>
          <h1>{{ plan.name }}</h1>
          <p>{{ strategyText(plan.strategyType) }} · 学期 ID {{ plan.semesterId }} · 当前状态 {{ plan.status }}</p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" plain :loading="refreshing" @click="handleRefresh">刷新风险</el-button>
          <el-button @click="router.push(`/v4/schedule-analysis/${planId}`)">回到质量分析</el-button>
          <el-button type="success" plain @click="router.push(`/v4/schedule-analysis/${planId}/charts`)">查看图表分析</el-button>
          <el-button
            type="success"
            :disabled="plan?.status === 'ABANDONED' || plan?.status === 'FAILED'"
            @click="localReplanVisible = true"
          >
            局部重排
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row v-if="riskData" :gutter="16">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card danger">
          <span>高风险数量</span>
          <strong>{{ riskData.highRiskCount }}</strong>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card warning">
          <span>中风险数量</span>
          <strong>{{ riskData.mediumRiskCount }}</strong>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card success">
          <span>低风险数量</span>
          <strong>{{ riskData.lowRiskCount }}</strong>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card neutral">
          <span>未处理风险</span>
          <strong>{{ riskData.unresolvedCount }}</strong>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="filter-card">
      <div class="filter-grid">
        <el-select v-model="filters.riskType" clearable placeholder="风险类型" @change="fetchData">
          <el-option v-for="item in riskTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.level" clearable placeholder="风险等级" @change="fetchData">
          <el-option label="高风险" value="HIGH" />
          <el-option label="中风险" value="MEDIUM" />
          <el-option label="低风险" value="LOW" />
        </el-select>
        <el-switch
          v-model="filters.onlyUnresolved"
          inline-prompt
          active-text="仅未处理"
          inactive-text="全部"
          @change="fetchData"
        />
        <el-input v-model="filters.keyword" clearable placeholder="关键词搜索标题、说明、对象" />
      </div>
    </el-card>

    <el-empty
      v-if="!loading && riskData && displayedRisks.length === 0"
      description="暂无风险，当前方案未检测到明显冲突或异常。"
    />

    <el-card v-else-if="riskData" shadow="never" class="table-card">
      <template #header>
        <div class="table-header">
          <span class="card-title">风险列表</span>
          <span class="table-meta">共 {{ displayedRisks.length }} 项</span>
        </div>
      </template>
      <el-table :data="displayedRisks" stripe>
        <el-table-column label="风险等级" width="110">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)">{{ levelText(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskTypeName" label="风险类型" width="140" />
        <el-table-column prop="title" label="风险标题" min-width="240" />
        <el-table-column prop="affectedObjects" label="影响对象" min-width="220" />
        <el-table-column label="时间段" width="120">
          <template #default="{ row }">
            <span>{{ row.weekDay ? `周${row.weekDay}` : '—' }} {{ row.period ?? '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
            <el-button v-if="canAdjust(row)" link type="warning" @click="openAdjust(row)">尝试调整</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="风险详情" size="520px">
      <div v-if="selectedRisk" class="drawer-body">
        <el-tag :type="levelTagType(selectedRisk.level)">{{ levelText(selectedRisk.level) }}</el-tag>
        <h3>{{ selectedRisk.title }}</h3>
        <p class="drawer-desc">{{ selectedRisk.description }}</p>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="风险类型">{{ selectedRisk.riskTypeName }}</el-descriptions-item>
          <el-descriptions-item label="相关教师">{{ selectedRisk.relatedTeacherName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="相关班级">{{ selectedRisk.relatedClassName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="相关教室">{{ selectedRisk.relatedRoomName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="相关课程">{{ selectedRisk.relatedCourseName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="时间段">
            {{ selectedRisk.weekDay ? `周${selectedRisk.weekDay}` : '—' }} {{ selectedRisk.period || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理建议">{{ selectedRisk.suggestion }}</el-descriptions-item>
        </el-descriptions>

        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-title">详细说明</div>
          </template>
          <ul class="detail-list">
            <li v-for="line in selectedRisk.detailLines" :key="line">{{ line }}</li>
          </ul>
        </el-card>

        <div v-if="canAdjust(selectedRisk)" class="drawer-actions">
          <el-button type="warning" plain @click="openAdjust(selectedRisk)">尝试局部调整</el-button>
        </div>
      </div>
    </el-drawer>

    <ScheduleAdjustDialog v-model="adjustVisible" :context="adjustContext" @success="handleAdjustSuccess" />
    <LocalReplanDialog v-model="localReplanVisible" :plan-id="planId" @success="handleLocalReplanSuccess" />
  </div>
</template>

<style scoped>
.risk-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card,
.stat-card,
.filter-card,
.table-card,
.detail-card {
  border-radius: 18px;
}

.hero-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
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

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.eyebrow {
  color: #b85c38;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.stat-card {
  min-height: 116px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-card span {
  color: #677489;
  font-size: 13px;
}

.stat-card strong {
  color: #1f3045;
  font-size: 34px;
}

.stat-card.danger {
  background: linear-gradient(135deg, #fff3f0 0%, #ffffff 100%);
}

.stat-card.warning {
  background: linear-gradient(135deg, #fff9eb 0%, #ffffff 100%);
}

.stat-card.success {
  background: linear-gradient(135deg, #edf9f0 0%, #ffffff 100%);
}

.stat-card.neutral {
  background: linear-gradient(135deg, #f3f6fb 0%, #ffffff 100%);
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-meta {
  color: #667085;
  font-size: 13px;
}

.card-title {
  color: #223047;
  font-weight: 700;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-body h3 {
  margin: 0;
  color: #223047;
  font-size: 22px;
}

.drawer-desc {
  margin: -8px 0 0;
  color: #5f6978;
  line-height: 1.8;
}

.detail-list {
  margin: 0;
  padding-left: 18px;
  color: #475467;
  line-height: 1.9;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero-row {
    flex-direction: column;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
