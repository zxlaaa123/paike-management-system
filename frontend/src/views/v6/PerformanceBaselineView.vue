<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  getPerformanceBaselineList,
  getPerformanceSummary,
  type PerformanceBaselineRecord,
  type PerformanceSummary,
} from '../../api/performanceBaseline'

const loading = ref(false)
const records = ref<PerformanceBaselineRecord[]>([])
const summaries = ref<PerformanceSummary[]>([])
const total = ref(0)

const operationOptions = [
  { value: 'AUTO_SCHEDULE', label: '自动排课' },
  { value: 'V4_LOCAL_REPLAN', label: 'V4 局部重排' },
  { value: 'V5_GENERATE_SIMULATION', label: 'V5 生成试算' },
  { value: 'V5_LOCAL_REPLAN', label: 'V5 局部重排' },
  { value: 'V5_APPLY_SIMULATION', label: 'V5 应用试算' },
]

const searchForm = reactive({
  operationType: '',
  semesterId: undefined as number | undefined,
  planId: undefined as number | undefined,
  success: undefined as boolean | undefined,
})

const pagination = reactive({
  page: 1,
  size: 10,
})

function operationText(value?: string | null) {
  const found = operationOptions.find((item) => item.value === value)
  return found?.label || value || '-'
}

function statusText(success?: number | null) {
  return success === 1 ? '成功' : '失败'
}

function statusTagType(success?: number | null) {
  return success === 1 ? 'success' : 'danger'
}

function formatDuration(value?: number | null) {
  if (value === undefined || value === null) return '-'
  if (value < 1000) return `${value} ms`
  return `${(value / 1000).toFixed(2)} s`
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function emptyToUndefined(value?: number) {
  return value === undefined || value === null ? undefined : value
}

async function fetchData() {
  loading.value = true
  try {
    const [list, summary] = await Promise.all([
      getPerformanceBaselineList({
        page: pagination.page,
        size: pagination.size,
        operationType: searchForm.operationType || undefined,
        semesterId: emptyToUndefined(searchForm.semesterId),
        planId: emptyToUndefined(searchForm.planId),
        success: searchForm.success,
      }),
      getPerformanceSummary(),
    ])
    records.value = list.records || []
    total.value = list.total || 0
    summaries.value = summary || []
  } catch {
    records.value = []
    total.value = 0
    summaries.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchData()
}

function handleReset() {
  searchForm.operationType = ''
  searchForm.semesterId = undefined
  searchForm.planId = undefined
  searchForm.success = undefined
  pagination.page = 1
  fetchData()
}

function handleSizeChange() {
  pagination.page = 1
  fetchData()
}

onMounted(fetchData)
</script>

<template>
  <div class="performance-page">
    <el-card>
      <div class="page-header">
        <div class="page-title">性能基线中心</div>
        <el-button type="primary" :loading="loading" @click="fetchData">刷新</el-button>
      </div>

      <el-form class="search-form" :model="searchForm" inline>
        <el-form-item label="操作类型">
          <el-select v-model="searchForm.operationType" clearable placeholder="全部" style="width: 190px">
            <el-option v-for="item in operationOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期ID">
          <el-input-number v-model="searchForm.semesterId" :min="1" :controls="false" placeholder="全部" style="width: 120px" />
        </el-form-item>
        <el-form-item label="方案ID">
          <el-input-number v-model="searchForm.planId" :min="1" :controls="false" placeholder="全部" style="width: 120px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="searchForm.success" clearable placeholder="全部" style="width: 120px">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="summary-grid">
      <el-card v-for="item in summaries" :key="item.operationType" class="summary-card">
        <div class="summary-title">{{ operationText(item.operationType) }}</div>
        <div class="summary-value">{{ formatDuration(item.avgDurationMs) }}</div>
        <div class="summary-meta">
          总数 {{ item.totalCount }} / 成功 {{ item.successCount }} / 失败 {{ item.failureCount }} / 最大 {{ formatDuration(item.maxDurationMs) }}
        </div>
      </el-card>
    </div>

    <el-card class="table-card">
      <div class="table-title">性能记录（共 {{ total }} 条）</div>
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.success)" size="small">{{ statusText(row.success) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" min-width="170">
          <template #default="{ row }">
            <div>{{ operationText(row.operationType) }}</div>
            <div class="sub-text">{{ row.operationType }}</div>
          </template>
        </el-table-column>
        <el-table-column label="关联对象" min-width="150">
          <template #default="{ row }">
            <div>学期：{{ row.semesterId ?? '-' }}</div>
            <div class="sub-text">方案：{{ row.planId ?? '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="规模" min-width="130">
          <template #default="{ row }">
            <div>任务：{{ row.taskCount ?? '-' }}</div>
            <div class="sub-text">排课：{{ row.scheduleCount ?? '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="120">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="错误" min-width="180">
          <template #default="{ row }">
            <template v-if="row.errorCode || row.errorMessage">
              <div class="error-code">{{ row.errorCode || '-' }}</div>
              <div class="summary-text">{{ row.errorMessage || '-' }}</div>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="记录时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="fetchData"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.performance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title,
.table-title {
  font-size: 18px;
  font-weight: 600;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.summary-title {
  font-size: 14px;
  color: #606266;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
}

.summary-meta {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
}

.table-card {
  min-height: 420px;
}

.table-title {
  margin-bottom: 12px;
}

.sub-text {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.summary-text {
  display: block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.error-code {
  max-width: 320px;
  overflow: hidden;
  color: #cf1322;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
