<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getConsistencyCheckById,
  getConsistencyCheckList,
  runConsistencyCheck,
  type ConsistencyCheckDetail,
  type ConsistencyCheckRecord,
  type ConsistencyIssue,
} from '../../api/consistencyCheck'

const loading = ref(false)
const running = ref(false)
const detailLoading = ref(false)
const records = ref<ConsistencyCheckRecord[]>([])
const currentDetail = ref<ConsistencyCheckDetail | null>(null)
const detailVisible = ref(false)
const total = ref(0)

const searchForm = reactive({
  status: '',
  checkType: '',
  semesterId: undefined as number | undefined,
  planId: undefined as number | undefined,
})

const runForm = reactive({
  taskId: undefined as number | undefined,
  planId: undefined as number | undefined,
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const statusOptions = [
  { value: 'PASS', label: '通过' },
  { value: 'WARN', label: '警告' },
  { value: 'FAIL', label: '失败' },
]

function statusText(status?: string | null) {
  const found = statusOptions.find((item) => item.value === status)
  return found?.label || status || '-'
}

function statusTagType(status?: string | null) {
  if (status === 'PASS') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'FAIL') return 'danger'
  return 'info'
}

function severityTagType(severity?: string | null) {
  if (severity === 'BLOCKING') return 'danger'
  if (severity === 'WARNING') return 'warning'
  return 'info'
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function emptyToUndefined(value?: number) {
  return value === undefined || value === null ? undefined : value
}

async function fetchRecords() {
  loading.value = true
  try {
    const res = await getConsistencyCheckList({
      page: pagination.page,
      size: pagination.size,
      status: searchForm.status || undefined,
      checkType: searchForm.checkType || undefined,
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
  searchForm.status = ''
  searchForm.checkType = ''
  searchForm.semesterId = undefined
  searchForm.planId = undefined
  pagination.page = 1
  fetchRecords()
}

function handleSizeChange() {
  pagination.page = 1
  fetchRecords()
}

async function handleRun() {
  if (!runForm.taskId || !runForm.planId) {
    ElMessage.warning('请填写修复任务ID和方案ID')
    return
  }
  running.value = true
  try {
    const report = await runConsistencyCheck(runForm.taskId, runForm.planId)
    ElMessage.success(`一致性检查完成：${statusText(report.status)}`)
    pagination.page = 1
    await fetchRecords()
  } finally {
    running.value = false
  }
}

async function openDetail(row: ConsistencyCheckRecord) {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  try {
    currentDetail.value = await getConsistencyCheckById(row.id)
  } finally {
    detailLoading.value = false
  }
}

function issueTime(issue: ConsistencyIssue) {
  if (!issue.weekday && !issue.startPeriod) return '-'
  return `周${issue.weekday ?? '-'} ${issue.startPeriod ?? '-'}-${issue.endPeriod ?? '-'}节`
}

onMounted(fetchRecords)
</script>

<template>
  <div class="consistency-page">
    <el-card>
      <div class="page-header">
        <div class="page-title">一致性检查中心</div>
        <el-button type="primary" :loading="loading" @click="fetchRecords">刷新</el-button>
      </div>

      <el-form class="search-form" :model="searchForm" inline>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查类型">
          <el-input v-model="searchForm.checkType" clearable placeholder="全部" style="width: 180px" />
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

      <el-divider />

      <el-form class="search-form" :model="runForm" inline>
        <el-form-item label="修复任务ID">
          <el-input-number v-model="runForm.taskId" :min="1" :controls="false" placeholder="必填" style="width: 140px" />
        </el-form-item>
        <el-form-item label="方案ID">
          <el-input-number v-model="runForm.planId" :min="1" :controls="false" placeholder="必填" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" :loading="running" @click="handleRun">执行检查</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-title">检查记录（共 {{ total }} 条）</div>
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检查类型" prop="checkType" min-width="150" />
        <el-table-column label="范围" prop="checkScope" width="90" />
        <el-table-column label="关联对象" min-width="150">
          <template #default="{ row }">
            <div>学期：{{ row.semesterId ?? '-' }}</div>
            <div class="sub-text">方案：{{ row.planId ?? '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="问题数" width="110">
          <template #default="{ row }">
            <div>{{ row.issueCount ?? 0 }} 个</div>
            <div class="sub-text">阻塞：{{ row.blockingIssueCount ?? 0 }}</div>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="220">
          <template #default="{ row }">
            <span class="summary-text">{{ row.resultSummary || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="检查时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.checkedAt) }}</template>
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

    <el-dialog v-model="detailVisible" title="一致性检查详情" width="920px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录ID">{{ currentDetail?.record?.id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="currentDetail?.record" :type="statusTagType(currentDetail.record.status)" size="small">
              {{ statusText(currentDetail.record.status) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="检查类型">{{ currentDetail?.record?.checkType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检查范围">{{ currentDetail?.record?.checkScope || '-' }}</el-descriptions-item>
          <el-descriptions-item label="学期ID">{{ currentDetail?.record?.semesterId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="方案ID">{{ currentDetail?.record?.planId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="阻塞问题">{{ currentDetail?.record?.blockingIssueCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="问题总数">{{ currentDetail?.record?.issueCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ currentDetail?.record?.resultSummary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="建议" :span="2">{{ currentDetail?.report?.recommendation || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="issue-title">问题清单</div>
        <el-table :data="currentDetail?.issues || []" border>
          <el-table-column label="严重级别" width="110">
            <template #default="{ row }">
              <el-tag :type="severityTagType(row.severity)" size="small">{{ row.severity || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规则" min-width="170">
            <template #default="{ row }">
              <div>{{ row.name || row.code || '-' }}</div>
              <div class="sub-text">{{ row.category || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="对象" min-width="180">
            <template #default="{ row }">
              <div>{{ row.courseName || '-' }}</div>
              <div class="sub-text">{{ row.teacherName || row.className || row.classroomName || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="120">
            <template #default="{ row }">{{ issueTime(row) }}</template>
          </el-table-column>
          <el-table-column label="描述" min-width="240" prop="message" />
          <el-table-column label="建议" min-width="220" prop="suggestion" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.consistency-page {
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
.table-title,
.issue-title {
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

.table-title,
.issue-title {
  margin-bottom: 12px;
}

.issue-title {
  margin-top: 18px;
}

.sub-text {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.summary-text {
  display: inline-block;
  max-width: 360px;
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

