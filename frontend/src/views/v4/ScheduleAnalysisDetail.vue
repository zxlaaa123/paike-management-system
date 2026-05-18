<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSchedulePlanById, type SchedulePlan } from '../../api/schedulePlan'
import { strategyText } from '../../utils/status'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const plan = ref<SchedulePlan | null>(null)

async function fetchData() {
  loading.value = true
  try {
    plan.value = await getSchedulePlanById(planId.value)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载方案信息失败')
  } finally {
    loading.value = false
  }
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
          <el-button @click="router.push(`/v3/schedule-plans/${plan.id}`)">回到 V3 方案详情</el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        title="V4 阶段 0 骨架页面"
        description="当前页面先确认 V3 方案能正常进入 V4 入口。阶段 1 会在这里接入真实质量分析接口。"
      />
    </el-card>

    <el-card v-if="plan" shadow="never" class="check-card">
      <template #header>
        <div class="card-title">当前承接信息</div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总分">{{ plan.totalScore ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="已排任务">{{ plan.scheduledCount }}</el-descriptions-item>
        <el-descriptions-item label="未排任务">{{ plan.unscheduledCount }}</el-descriptions-item>
        <el-descriptions-item label="冲突数量">{{ plan.conflictCount }}</el-descriptions-item>
      </el-descriptions>
      <div class="safe-note">当前阶段只读展示 V3 基础信息，不修改正式课表，不修改方案明细。</div>
    </el-card>
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

.card-title {
  font-weight: 700;
  color: #223047;
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
