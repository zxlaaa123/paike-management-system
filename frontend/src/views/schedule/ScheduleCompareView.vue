<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { compareSchedulePlans, applySchedulePlan, getSchedulePlanList, type SchedulePlan } from '../../api/schedulePlan'
import { getCurrentSemester, getAllSemesters, type Semester } from '../../api/semester'

const router = useRouter()

const loading = ref(false)
const comparing = ref(false)
const applying = ref(false)

const currentSemester = ref<Semester | null>(null)
const semesterList = ref<Semester[]>([])
const allPlans = ref<SchedulePlan[]>([])

const selectedPlanIds = ref<number[]>([])
const compareResult = ref<any>(null)

const hasCurrentSemester = computed(() => currentSemester.value !== null)

// 对比表格列定义
const compareColumns = [
  { prop: 'planName', label: '方案名称', minWidth: 160 },
  { prop: 'strategyName', label: '策略', width: 100 },
  { prop: 'totalScore', label: '总分', width: 80 },
  { prop: 'scheduledCount', label: '已排', width: 70 },
  { prop: 'unscheduledCount', label: '未排', width: 70 },
  { prop: 'conflictCount', label: '冲突', width: 70 },
  { prop: 'hardViolationCount', label: '硬约束违规', width: 100 },
  { prop: 'softViolationCount', label: '软约束扣分', width: 100 },
]

// 过滤可选方案（排除已废弃的）
const availablePlans = computed(() =>
  allPlans.value.filter(p => p.status !== 'ABANDONED')
)

async function fetchOptions() {
  loading.value = true
  try {
    const [semesters, current] = await Promise.all([
      getAllSemesters(),
      getCurrentSemester().catch(() => null),
    ])
    semesterList.value = semesters
    currentSemester.value = current

    if (current) {
      const res = await getSchedulePlanList({ semesterId: current.id, size: 100 })
      allPlans.value = res.records
    }
  } finally {
    loading.value = false
  }
}

async function handleCompare() {
  if (selectedPlanIds.value.length < 2) {
    ElMessage.warning('请至少选择两个方案进行对比')
    return
  }
  if (!currentSemester.value) {
    ElMessage.warning('当前未设置学期')
    return
  }

  comparing.value = true
  try {
    compareResult.value = await compareSchedulePlans({
      semesterId: currentSemester.value.id,
      planIds: selectedPlanIds.value,
    })
  } finally {
    comparing.value = false
  }
}

async function handleApply(row: any) {
  if (row.status === 'ABANDONED') {
    ElMessage.warning('已废弃方案不能应用')
    return
  }

  const warnings: string[] = []
  if (row.unscheduledCount > 0) {
    warnings.push(`存在 ${row.unscheduledCount} 个未排任务`)
  }
  if (row.conflictCount > 0) {
    warnings.push(`存在 ${row.conflictCount} 个冲突`)
  }

  let confirmMsg = `确定将「${row.planName}」应用为当前学期正式课表吗？`
  if (warnings.length > 0) {
    confirmMsg = `该方案${warnings.join('，')}，确定要继续应用吗？`
  }

  await ElMessageBox.confirm(confirmMsg, '应用方案', {
    type: warnings.length > 0 ? 'warning' : 'info',
    confirmButtonText: '确定应用',
    cancelButtonText: '取消',
  })

  applying.value = true
  try {
    const result = await applySchedulePlan(row.planId)
    ElMessage.success(`方案已应用，共写入 ${result.appliedCount} 条课表记录`)
    // 刷新方案列表
    await fetchOptions()
    compareResult.value = null
    selectedPlanIds.value = []
  } finally {
    applying.value = false
  }
}

function isBest(row: any): boolean {
  return compareResult.value?.bestPlanId === row.planId
}

function rowClass({ row }: { row: any }): string {
  if (isBest(row)) return 'row-best'
  if (row.conflictCount > 0) return 'row-conflict'
  if (row.unscheduledCount > 0) return 'row-warning'
  return ''
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

function goBack() {
  router.push('/v3/schedule-plans')
}

onMounted(fetchOptions)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 无当前学期提示 -->
    <el-alert
      v-if="!hasCurrentSemester"
      title="当前未设置学期，部分功能无法使用。请先在「学期管理」中创建并设置当前学期。"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 当前学期信息 -->
    <el-alert
      v-if="currentSemester"
      :title="`当前学期：${currentSemester.name}`"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 方案选择区 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>选择对比方案（至少选择 2 个）</span>
          <div>
            <el-button @click="goBack">返回方案列表</el-button>
            <el-button
              type="primary"
              :loading="comparing"
              :disabled="selectedPlanIds.length < 2"
              @click="handleCompare"
              style="margin-left: 8px"
            >开始对比</el-button>
          </div>
        </div>
      </template>

      <el-checkbox-group v-model="selectedPlanIds">
        <el-table :data="availablePlans" stripe size="small">
          <el-table-column label="选择" width="60">
            <template #default="{ row }">
              <el-checkbox :value="row.id" />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="方案名称" min-width="180" />
          <el-table-column label="策略" width="110">
            <template #default="{ row }">{{ strategyText(row.strategyType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'APPLIED' ? 'success' : row.status === 'DRAFT' ? 'primary' : 'info'">
                {{ row.status === 'APPLIED' ? '已应用' : row.status === 'DRAFT' ? '草稿' : '已废弃' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总分" width="80" />
          <el-table-column label="已排/未排" width="100">
            <template #default="{ row }">{{ row.scheduledCount }}/{{ row.unscheduledCount }}</template>
          </el-table-column>
          <el-table-column prop="conflictCount" label="冲突" width="70" />
        </el-table>
      </el-checkbox-group>

      <el-empty v-if="availablePlans.length === 0" description="当前学期暂无可对比的方案，请先生成排课方案" />
    </el-card>

    <!-- 对比结果 -->
    <el-card v-if="compareResult" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>对比结果</span>
        </div>
      </template>

      <!-- 推荐说明 -->
      <el-alert
        :title="`推荐方案：${compareResult.summary}`"
        type="success"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <!-- 对比表格 -->
      <el-table :data="compareResult.plans" stripe :row-class-name="rowClass">
        <el-table-column label="方案名称" min-width="180">
          <template #default="{ row }">
            <span>{{ row.planName }}</span>
            <el-tag v-if="isBest(row)" type="success" size="small" style="margin-left: 8px">推荐</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="策略" width="110">
          <template #default="{ row }">{{ row.strategyName }}</template>
        </el-table-column>
        <el-table-column label="总分" width="90">
          <template #default="{ row }">
            <strong :style="{ color: isBest(row) ? '#67c23a' : '' }">{{ row.totalScore }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="已排" width="70">
          <template #default="{ row }">{{ row.scheduledCount }}</template>
        </el-table-column>
        <el-table-column label="未排" width="70">
          <template #default="{ row }">
            <span :style="{ color: row.unscheduledCount > 0 ? '#e6a23c' : '' }">{{ row.unscheduledCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="冲突" width="70">
          <template #default="{ row }">
            <span :style="{ color: row.conflictCount > 0 ? '#f56c6c' : '' }">{{ row.conflictCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="硬约束违规" width="110">
          <template #default="{ row }">
            <span :style="{ color: row.hardViolationCount > 0 ? '#f56c6c' : '' }">{{ row.hardViolationCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="软约束扣分" width="110">
          <template #default="{ row }">
            <span :style="{ color: row.softViolationCount > 0 ? '#e6a23c' : '' }">{{ row.softViolationCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="row.status === 'ABANDONED'"
              @click="handleApply(row)"
              :loading="applying"
            >应用该方案</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
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
:deep(.row-best) {
  --el-table-tr-bg-color: #f0f9eb;
}
:deep(.row-conflict) {
  --el-table-tr-bg-color: #fef0f0;
}
:deep(.row-warning) {
  --el-table-tr-bg-color: #fdf6ec;
}
</style>
