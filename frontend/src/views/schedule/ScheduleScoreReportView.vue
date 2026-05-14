<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  generateScheduleScore,
  getLatestScheduleScore,
  getScheduleScoreHistory,
  type ScheduleScoreReport,
  type ScheduleScoreResult,
} from '../../api/scheduleScoreReport'

const generating = ref(false)
// currentScore 表示本次刚生成的新结果，latestReport 表示后端持久化的最近一次结果。
// 页面优先展示 currentScore，这样用户点击“重新评分”后无需等待下一次刷新就能看到新结果。
const currentScore = ref<ScheduleScoreResult | null>(null)
const latestReport = ref<ScheduleScoreReport | null>(null)

const historyList = ref<ScheduleScoreReport[]>([])
const historyTotal = ref(0)
const historyLoading = ref(false)

const historySearch = reactive({
  grade: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const gradeOptions = [
  { value: 'EXCELLENT', label: '优秀' },
  { value: 'GOOD', label: '良好' },
  { value: 'AVERAGE', label: '一般' },
  { value: 'POOR', label: '较差' },
  { value: 'BAD', label: '需要调整' },
]

function gradeTagType(grade?: string) {
  const map: Record<string, string> = {
    EXCELLENT: 'success',
    GOOD: '',
    AVERAGE: 'warning',
    POOR: 'danger',
    BAD: 'danger',
  }
  return grade ? (map[grade] || '') : ''
}

function scoreColor(score?: number) {
  if (score == null) return '#909399'
  if (score >= 90) return '#67c23a'
  if (score >= 80) return '#409eff'
  if (score >= 70) return '#e6a23c'
  if (score >= 60) return '#f56c6c'
  return '#ff0000'
}

async function handleGenerate() {
  generating.value = true
  try {
    currentScore.value = await generateScheduleScore()
    ElMessage.success('评分生成成功')
    await fetchLatest()
    await fetchHistory()
  } finally {
    generating.value = false
  }
}

async function fetchLatest() {
  try {
    latestReport.value = await getLatestScheduleScore()
  } catch (_e) {
    // 首次进入系统时可能还没有任何评分记录，这里按“无数据”处理，不打断页面展示。
    latestReport.value = null
  }
}

async function fetchHistory() {
  historyLoading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size,
    }
    if (historySearch.grade) params.grade = historySearch.grade
    const res = await getScheduleScoreHistory(params as any)
    historyList.value = res.records
    historyTotal.value = res.total
  } finally {
    historyLoading.value = false
  }
}

function handleHistorySearch() {
  pagination.page = 1
  fetchHistory()
}

function handleHistoryReset() {
  historySearch.grade = ''
  pagination.page = 1
  fetchHistory()
}

function handleSizeChange() {
  pagination.page = 1
  fetchHistory()
}

onMounted(() => {
  fetchLatest()
  fetchHistory()
})
</script>

<template>
  <div class="page-container">
    <!-- 评分总览 -->
    <el-card shadow="never" class="score-overview-card">
      <template #header>
        <div class="card-header">
          <span>课表评分</span>
          <el-button type="primary" :loading="generating" @click="handleGenerate">重新评分</el-button>
        </div>
      </template>

      <div v-if="currentScore || latestReport" class="score-content">
        <div class="score-main">
          <div class="score-circle" :style="{ borderColor: scoreColor(currentScore?.score ?? latestReport?.score) }">
            <span class="score-number" :style="{ color: scoreColor(currentScore?.score ?? latestReport?.score) }">
              {{ currentScore?.score ?? latestReport?.score ?? '-' }}
            </span>
            <span class="score-label">分</span>
          </div>
          <div class="score-info">
            <el-tag :type="gradeTagType(currentScore?.grade ?? latestReport?.grade)" size="large" effect="dark">
              {{ currentScore?.gradeName ?? latestReport?.gradeName ?? '-' }}
            </el-tag>
            <div class="score-time" v-if="latestReport?.createTime">
              最近评分时间：{{ latestReport.createTime }}
            </div>
          </div>
        </div>

        <div class="score-stats">
          <div class="stat-item">
            <div class="stat-value danger">{{ currentScore?.conflictCount ?? latestReport?.conflictCount ?? 0 }}</div>
            <div class="stat-label">硬冲突数量</div>
          </div>
          <div class="stat-item">
            <div class="stat-value warning">{{ currentScore?.unfinishedTaskCount ?? latestReport?.unfinishedTaskCount ?? 0 }}</div>
            <div class="stat-label">未排满任务</div>
          </div>
          <div class="stat-item">
            <div class="stat-value warning">{{ currentScore?.teacherOverloadCount ?? latestReport?.teacherOverloadCount ?? 0 }}</div>
            <div class="stat-label">教师超负荷</div>
          </div>
          <div class="stat-item">
            <div class="stat-value warning">{{ currentScore?.classOverloadCount ?? latestReport?.classOverloadCount ?? 0 }}</div>
            <div class="stat-label">班级超负荷</div>
          </div>
          <div class="stat-item">
            <div class="stat-value info">{{ currentScore?.fridayAfternoonCount ?? latestReport?.fridayAfternoonCount ?? 0 }}</div>
            <div class="stat-label">周五下午课程</div>
          </div>
        </div>
      </div>

      <el-empty v-else description="暂无评分数据，请点击「重新评分」生成评分" />
    </el-card>

    <!-- 扣分详情 -->
    <el-card shadow="never" style="margin-top: 16px" v-if="currentScore?.deductionDetail?.length || latestReport?.deductionDetail">
      <template #header>
        <span>扣分详情</span>
      </template>
      <!-- 实时评分返回数组，历史记录里存的是换行拼接字符串，这里统一折算成列表渲染。 -->
      <div class="deduction-list">
        <div
          v-for="(item, index) in (currentScore?.deductionDetail ?? latestReport?.deductionDetail?.split('\n') ?? [])"
          :key="index"
          class="deduction-item"
        >
          <el-tag type="danger" size="small" effect="plain" style="margin-right: 8px">
            -{{ item.match(/-(\d+) 分/)?.[1] ?? '?' }}
          </el-tag>
          <span>{{ item }}</span>
        </div>
      </div>
    </el-card>

    <!-- 优化建议 -->
    <el-card shadow="never" style="margin-top: 16px" v-if="currentScore?.suggestion?.length || latestReport?.suggestion">
      <template #header>
        <span>优化建议</span>
      </template>
      <div class="suggestion-list">
        <div
          v-for="(item, index) in (currentScore?.suggestion ?? latestReport?.suggestion?.split('\n') ?? [])"
          :key="index"
          class="suggestion-item"
        >
          <span class="suggestion-index">{{ index + 1 }}.</span>
          <span>{{ item }}</span>
        </div>
      </div>
    </el-card>

    <!-- 评分历史 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>评分历史（共 {{ historyTotal }} 条）</span>
      </template>

      <el-form :model="historySearch" inline>
        <el-form-item label="评分等级">
          <el-select v-model="historySearch.grade" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in gradeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleHistorySearch">搜索</el-button>
          <el-button @click="handleHistoryReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="historyList" v-loading="historyLoading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="分数" width="100">
          <template #default="{ row }">
            <span :style="{ color: scoreColor(row.score), fontWeight: 'bold' }">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="120">
          <template #default="{ row }">
            <el-tag :type="gradeTagType(row.grade)" size="small">{{ row.gradeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conflictCount" label="硬冲突" width="90" />
        <el-table-column prop="unfinishedTaskCount" label="未排满" width="90" />
        <el-table-column prop="teacherOverloadCount" label="教师超负荷" width="110" />
        <el-table-column prop="classOverloadCount" label="班级超负荷" width="110" />
        <el-table-column prop="fridayAfternoonCount" label="周五下午" width="100" />
        <el-table-column prop="createTime" label="评分时间" width="170" />
      </el-table>

      <el-empty v-if="!historyLoading && historyList.length === 0" description="暂无评分历史" />

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="historyTotal"
        :page-sizes="[10, 20]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchHistory"
        @size-change="handleSizeChange"
      />
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

.score-overview-card {
  margin-top: 0;
}

.score-content {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  align-items: center;
}

.score-main {
  display: flex;
  align-items: center;
  gap: 24px;
}

.score-circle {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 4px solid #409eff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.score-number {
  font-size: 36px;
  font-weight: bold;
  line-height: 1;
}

.score-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.score-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.score-time {
  color: #909399;
  font-size: 13px;
}

.score-stats {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.stat-item {
  text-align: center;
  min-width: 80px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-value.warning {
  color: #e6a23c;
}

.stat-value.info {
  color: #909399;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.deduction-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.deduction-item {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  font-size: 14px;
  line-height: 1.6;
}

.suggestion-index {
  font-weight: bold;
  color: #409eff;
  margin-right: 8px;
  white-space: nowrap;
}
</style>
