<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  getRegressionTestById,
  getRegressionTestList,
  type RegressionTestRecord,
} from '../../api/regressionTest'

const loading = ref(false)
const detailLoading = ref(false)
const records = ref<RegressionTestRecord[]>([])
const currentRecord = ref<RegressionTestRecord | null>(null)
const detailVisible = ref(false)
const total = ref(0)

const searchForm = reactive({
  testStage: '',
  testSuite: '',
  status: '',
  semesterId: undefined as number | undefined,
  planId: undefined as number | undefined,
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const statusOptions = [
  { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '失败' },
  { value: 'BLOCKED', label: '阻塞' },
  { value: 'RUNNING', label: '运行中' },
]

function statusText(status?: string | null) {
  const found = statusOptions.find((item) => item.value === status)
  return found?.label || status || '-'
}

function statusTagType(status?: string | null) {
  if (status === 'PASS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'BLOCKED') return 'warning'
  return 'info'
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function formatDuration(value?: number | null) {
  if (value === undefined || value === null) return '-'
  if (value < 1000) return `${value} ms`
  return `${(value / 1000).toFixed(2)} s`
}

function emptyToUndefined(value?: number) {
  return value === undefined || value === null ? undefined : value
}

async function fetchRecords() {
  loading.value = true
  try {
    const res = await getRegressionTestList({
      page: pagination.page,
      size: pagination.size,
      testStage: searchForm.testStage || undefined,
      testSuite: searchForm.testSuite || undefined,
      status: searchForm.status || undefined,
      semesterId: emptyToUndefined(searchForm.semesterId),
      planId: emptyToUndefined(searchForm.planId),
    })
    records.value = res.records || []
    total.value = res.total || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchRecords()
}

function handleReset() {
  searchForm.testStage = ''
  searchForm.testSuite = ''
  searchForm.status = ''
  searchForm.semesterId = undefined
  searchForm.planId = undefined
  pagination.page = 1
  fetchRecords()
}

function handleSizeChange() {
  pagination.page = 1
  fetchRecords()
}

async function openDetail(row: RegressionTestRecord) {
  detailVisible.value = true
  detailLoading.value = true
  currentRecord.value = null
  try {
    currentRecord.value = await getRegressionTestById(row.id)
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchRecords)
</script>

<template>
  <div class="regression-page">
    <el-card>
      <div class="page-header">
        <div class="page-title">回归测试中心</div>
        <el-button type="primary" :loading="loading" @click="fetchRecords">刷新</el-button>
      </div>

      <el-form class="search-form" :model="searchForm" inline>
        <el-form-item label="测试阶段">
          <el-input v-model="searchForm.testStage" clearable placeholder="全部" style="width: 180px" />
        </el-form-item>
        <el-form-item label="测试套件">
          <el-input v-model="searchForm.testSuite" clearable placeholder="全部" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期ID">
          <el-input-number v-model="searchForm.semesterId" :min="1" :controls="false" placeholder="全部" style="width: 120px" />
        </el-form-item>
        <el-form-item label="方案ID">
          <el-input-number v-model="searchForm.planId" :min="1" :controls="false" placeholder="全部" style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-title">测试记录（共 {{ total }} 条）</div>
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="测试信息" min-width="220">
          <template #default="{ row }">
            <div>{{ row.testSuite }}</div>
            <div class="sub-text">{{ row.testCase || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="阶段" prop="testStage" min-width="140" />
        <el-table-column label="关联对象" min-width="140">
          <template #default="{ row }">
            <div>学期：{{ row.semesterId ?? '-' }}</div>
            <div class="sub-text">方案：{{ row.planId ?? '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="执行者" prop="executedBy" width="120" />
        <el-table-column label="执行时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.executedAt) }}</template>
        </el-table-column>
        <el-table-column label="失败信息" min-width="180">
          <template #default="{ row }">
            <span class="summary-text">{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="fetchRecords"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="回归测试详情" width="760px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-descriptions v-else :column="2" border>
        <el-descriptions-item label="记录ID">{{ currentRecord?.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="currentRecord" :type="statusTagType(currentRecord.status)" size="small">
            {{ statusText(currentRecord.status) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="测试套件">{{ currentRecord?.testSuite || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试用例">{{ currentRecord?.testCase || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试阶段">{{ currentRecord?.testStage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatDuration(currentRecord?.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="学期ID">{{ currentRecord?.semesterId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="方案ID">{{ currentRecord?.planId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源方案ID">{{ currentRecord?.sourcePlanId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行者">{{ currentRecord?.executedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="构建版本">{{ currentRecord?.buildVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ formatDateTime(currentRecord?.executedAt) }}</el-descriptions-item>
        <el-descriptions-item label="失败信息" :span="2">{{ currentRecord?.errorMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="附加信息" :span="2">{{ currentRecord?.extraJson || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.regression-page {
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
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

