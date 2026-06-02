<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  generateCandidatePositions,
  type V5CandidatePosition,
  type V5CandidatePositionResult,
} from '../../api/v5CandidatePositionApi'
import { extractMessage } from '../../utils/errors'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const result = ref<V5CandidatePositionResult | null>(null)
const selected = ref<V5CandidatePosition | null>(null)
const includeUnavailable = ref(false)

const filters = ref({
  onlyAvailable: false,
  weekday: undefined as number | undefined,
  classroomKeyword: '',
})

const planItemId = computed(() => {
  const raw = route.query.planItemId
  const value = Array.isArray(raw) ? raw[0] : raw
  return value ? Number(value) : undefined
})

const scheduleId = computed(() => {
  const raw = route.query.scheduleId
  const value = Array.isArray(raw) ? raw[0] : raw
  return value ? Number(value) : undefined
})

const displayedRows = computed(() => {
  let rows = result.value?.candidates ?? []
  if (filters.value.onlyAvailable) {
    rows = rows.filter((row) => row.available)
  }
  if (filters.value.weekday != null) {
    rows = rows.filter((row) => row.weekday === filters.value.weekday)
  }
  const keyword = filters.value.classroomKeyword.trim().toLowerCase()
  if (keyword) {
    rows = rows.filter((row) => (row.classroomName || '').toLowerCase().includes(keyword))
  }
  return rows
})

async function loadData() {
  if (!planItemId.value && !scheduleId.value) {
    ElMessage.warning('缺少 planItemId 或 scheduleId')
    return
  }
  loading.value = true
  try {
    result.value = await generateCandidatePositions({
      planItemId: planItemId.value,
      scheduleId: scheduleId.value,
      includeUnavailable: includeUnavailable.value,
      limit: 80,
    })
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '生成候选位置失败'))
  } finally {
    loading.value = false
  }
}

function weekdayText(weekday: number) {
  return `周${weekday}`
}

function jumpToRepairFlow() {
  if (!selected.value || !result.value) return
  const params = {
    planId: result.value.planId,
    planItemId: result.value.planItemId,
    weekday: selected.value.weekday,
    start: selected.value.startPeriod,
    end: selected.value.endPeriod,
    roomId: selected.value.classroomId,
  }
  if (Object.values(params).some((value) => value == null)) {
    ElMessage.error('候选位置数据不完整')
    return
  }
  router.push(`/v5/repair-tasks?${new URLSearchParams(
    Object.fromEntries(Object.entries(params).map(([key, value]) => [key, String(value)])),
  ).toString()}`)
}

function handleCurrentChange(row: V5CandidatePosition | null) {
  selected.value = row
}

onMounted(loadData)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header content="V5 候选位置生成" @back="router.back()" />

    <el-card v-if="result" shadow="never">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="学期">{{ result.semesterId }}</el-descriptions-item>
        <el-descriptions-item label="方案">{{ result.planId }}</el-descriptions-item>
        <el-descriptions-item label="方案明细">{{ result.planItemId }}</el-descriptions-item>
        <el-descriptions-item label="来源课表">{{ result.scheduleId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="原始位置" :span="2">
          {{ weekdayText(result.sourceWeekday) }} {{ result.sourceStartPeriod }}-{{ result.sourceEndPeriod }}
        </el-descriptions-item>
        <el-descriptions-item label="原教室" :span="2">{{ result.sourceClassroomName || result.sourceClassroomId }}</el-descriptions-item>
        <el-descriptions-item label="候选总数">{{ result.totalCount }}</el-descriptions-item>
        <el-descriptions-item label="可用数量">{{ result.availableCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <div class="filter-row">
        <el-switch v-model="includeUnavailable" active-text="包含不可用位置" @change="loadData" />
        <el-switch v-model="filters.onlyAvailable" active-text="仅看可用位置" />
        <el-select v-model="filters.weekday" clearable placeholder="筛选星期" style="width: 120px">
          <el-option v-for="day in [1, 2, 3, 4, 5, 6, 7]" :key="day" :label="`周${day}`" :value="day" />
        </el-select>
        <el-input v-model="filters.classroomKeyword" clearable placeholder="教室关键字" style="width: 200px" />
      </div>

      <el-table :data="displayedRows" border stripe @current-change="handleCurrentChange" highlight-current-row>
        <el-table-column type="index" width="60" label="#" />
        <el-table-column label="星期" width="90">
          <template #default="{ row }">{{ weekdayText(row.weekday) }}</template>
        </el-table-column>
        <el-table-column label="节次" width="120">
          <template #default="{ row }">{{ row.startPeriod }}-{{ row.endPeriod }}</template>
        </el-table-column>
        <el-table-column prop="classroomName" label="教室" min-width="140" />
        <el-table-column label="可用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.available ? 'success' : 'danger'">{{ row.available ? '可用' : '不可用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hardConflictCount" label="硬冲突数" width="100" sortable />
        <el-table-column prop="softScore" label="软约束分" width="110" sortable />
        <el-table-column prop="totalScore" label="综合分" width="110" sortable />
        <el-table-column prop="reason" label="原因说明" min-width="360" show-overflow-tooltip />
        <el-table-column label="受影响课程" min-width="160">
          <template #default="{ row }">{{ row.affectedItems?.join(', ') || '—' }}</template>
        </el-table-column>
      </el-table>

      <div class="actions">
        <el-button type="primary" :disabled="!selected || !selected.available" @click="jumpToRepairFlow">进入修复/试算流程</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.filter-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.actions { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
