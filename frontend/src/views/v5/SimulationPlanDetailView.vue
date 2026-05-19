<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applySimulation,
  confirmSimulation,
  discardSimulation,
  getSimulationDetail,
  type V5SimulationPlanDetail,
} from '../../api/v5SimulationApi'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.taskId))
const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const acting = ref(false)
const detail = ref<V5SimulationPlanDetail | null>(null)

function statusType(status?: string) {
  if (status === 'APPLIED') return 'success'
  if (status === 'DISCARDED') return 'info'
  if (status === 'CONFIRMED') return 'warning'
  return 'primary'
}

function deltaText(value?: number | null) {
  if (value == null) return '0'
  return value > 0 ? `+${value}` : `${value}`
}

async function fetchData() {
  loading.value = true
  try {
    detail.value = await getSimulationDetail(taskId.value, planId.value)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载试算方案失败')
  } finally {
    loading.value = false
  }
}

async function confirmPlan() {
  acting.value = true
  try {
    detail.value = await confirmSimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已确认')
  } catch (error: any) {
    ElMessage.error(error?.message || '确认失败')
  } finally {
    acting.value = false
  }
}

async function applyPlan() {
  try {
    await ElMessageBox.confirm('应用后会写入正式课表，并替换当前已应用方案。', '应用试算方案', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    if (detail.value?.plan.status === 'SIMULATION') {
      detail.value = await confirmSimulation(taskId.value, planId.value)
    }
    await applySimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已应用')
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error?.message || '应用失败')
  } finally {
    acting.value = false
  }
}

async function discardPlan() {
  try {
    await ElMessageBox.confirm('放弃后该试算方案不会进入正式课表。', '放弃试算方案', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await discardSimulation(taskId.value, planId.value)
    ElMessage.success('试算方案已放弃')
  } catch (error: any) {
    ElMessage.error(error?.message || '放弃失败')
  } finally {
    acting.value = false
  }
}

const canOperate = computed(() => ['SIMULATION', 'CONFIRMED'].includes(detail.value?.plan.status || ''))

onMounted(fetchData)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header content="试算方案详情" @back="router.push(`/v5/repair-tasks/${taskId}`)" />

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="title">{{ detail.plan.name }}</div>
            <div class="sub">方案ID：{{ detail.plan.id }} · 任务ID：{{ taskId }}</div>
          </div>
          <el-tag :type="statusType(detail.plan.status)">{{ detail.plan.status }}</el-tag>
        </div>
      </template>

      <el-row :gutter="12">
        <el-col :span="6"><el-statistic title="试算评分" :value="detail.plan.totalScore ?? 0" /></el-col>
        <el-col :span="6"><el-statistic title="风险数" :value="detail.risks.riskCount" /></el-col>
        <el-col :span="6"><el-statistic title="冲突数" :value="detail.plan.conflictCount" /></el-col>
        <el-col :span="6"><el-statistic title="课程数" :value="detail.plan.scheduledCount" /></el-col>
      </el-row>

      <el-descriptions :column="3" border class="block">
        <el-descriptions-item label="来源方案">{{ detail.plan.sourcePlanId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源课表">{{ detail.plan.sourceScheduleId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="绑定任务">{{ detail.plan.repairTaskId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="生成方式">{{ detail.plan.generatedBy }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ detail.plan.generatedAt }}</el-descriptions-item>
        <el-descriptions-item label="说明">{{ detail.plan.description }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions" v-if="canOperate">
        <el-button type="warning" :loading="acting" @click="confirmPlan" v-if="detail.plan.status === 'SIMULATION'">确认试算方案</el-button>
        <el-button type="success" :loading="acting" @click="applyPlan">应用试算方案</el-button>
        <el-button type="danger" :loading="acting" @click="discardPlan">放弃试算方案</el-button>
      </div>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header><div class="title">优化前后对比</div></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="评分变化">{{ detail.compare.baselineScore }} → {{ detail.compare.simulationScore }}（{{ deltaText(detail.compare.scoreDelta) }}）</el-descriptions-item>
        <el-descriptions-item label="风险变化">{{ detail.compare.baselineRiskCount }} → {{ detail.compare.simulationRiskCount }}（{{ deltaText(detail.compare.riskDelta) }}）</el-descriptions-item>
        <el-descriptions-item label="冲突变化">{{ detail.compare.baselineConflictCount }} → {{ detail.compare.simulationConflictCount }}（{{ deltaText(detail.compare.conflictDelta) }}）</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="3">{{ detail.compare.summary }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.compare.changedItems" border stripe class="block">
        <el-table-column label="课程" prop="courseName" min-width="140" />
        <el-table-column label="教师" prop="teacherName" min-width="120" />
        <el-table-column label="班级" prop="className" min-width="120" />
        <el-table-column label="调整前" min-width="180">
          <template #default="{ row }">周{{ row.beforeWeekday }} {{ row.beforeStartPeriod }}-{{ row.beforeEndPeriod }} {{ row.beforeClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="调整后" min-width="180">
          <template #default="{ row }">周{{ row.afterWeekday }} {{ row.afterStartPeriod }}-{{ row.afterEndPeriod }} {{ row.afterClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="冲突" width="100">
          <template #default="{ row }"><el-tag :type="row.conflictFlag ? 'danger' : 'success'">{{ row.conflictFlag ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="原因" prop="conflictReason" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header><div class="title">试算课表</div></template>
      <el-table :data="detail.items" border stripe>
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column prop="teacherName" label="教师" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="120" />
        <el-table-column prop="roomName" label="教室" min-width="120" />
        <el-table-column label="时间" min-width="140">
          <template #default="{ row }">周{{ row.weekday }} 第{{ row.startPeriod }}-{{ row.endPeriod }}节</template>
        </el-table-column>
        <el-table-column label="冲突" width="100">
          <template #default="{ row }"><el-tag :type="row.conflictFlag ? 'danger' : 'success'">{{ row.conflictFlag ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="conflictReason" label="冲突原因" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="detail" shadow="never" class="main-card">
      <template #header><div class="title">评分与风险</div></template>
      <el-table :data="detail.scoreDetails" border stripe>
        <el-table-column prop="ruleName" label="规则" min-width="180" />
        <el-table-column prop="ruleType" label="类型" width="100" />
        <el-table-column prop="score" label="得分/扣分" width="120" />
        <el-table-column prop="violationCount" label="违规数" width="100" />
        <el-table-column prop="detailMessage" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
      <el-table :data="detail.risks.risks" border stripe class="block">
        <el-table-column prop="level" label="等级" width="100" />
        <el-table-column prop="riskTypeName" label="风险类型" min-width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.main-card { border-radius: 8px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.title { font-size: 18px; font-weight: 700; color: #243447; }
.sub { margin-top: 6px; color: #667085; font-size: 13px; }
.block { margin-top: 16px; }
.actions { margin-top: 16px; display: flex; gap: 10px; flex-wrap: wrap; }
</style>
