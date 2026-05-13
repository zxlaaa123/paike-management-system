<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  clearScheduleConflictReports,
  generateScheduleConflictReport,
  getScheduleConflictReportList,
  type ScheduleConflictReport,
} from '../../api/scheduleConflictReport'

const loading = ref(false)
const generating = ref(false)
const reportList = ref<ScheduleConflictReport[]>([])
const total = ref(0)
const latestReportNo = ref('')

const searchForm = reactive({
  reportNo: '',
  conflictType: '',
  objectType: '',
  objectName: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const conflictTypeOptions = [
  { value: 'TEACHER_CONFLICT', label: '教师冲突' },
  { value: 'CLASS_CONFLICT', label: '班级冲突' },
  { value: 'ROOM_CONFLICT', label: '教室冲突' },
  { value: 'CLASSROOM_CAPACITY_NOT_ENOUGH', label: '容量不足' },
  { value: 'ROOM_TYPE_MISMATCH', label: '教室类型不匹配' },
  { value: 'TEACHER_UNAVAILABLE', label: '教师禁排冲突' },
  { value: 'TASK_NOT_FULLY_SCHEDULED', label: '任务未排满' },
  { value: 'TEACHER_DAILY_LIMIT', label: '教师当天课程过多' },
  { value: 'CLASS_DAILY_LIMIT', label: '班级当天课程过多' },
]

const objectTypeOptions = [
  { value: 'TEACHER', label: '教师' },
  { value: 'CLASS', label: '班级' },
  { value: 'CLASSROOM', label: '教室' },
  { value: 'TASK', label: '教学任务' },
  { value: 'SCHEDULE', label: '排课记录' },
]

function conflictTypeText(type?: string) {
  const map: Record<string, string> = {}
  conflictTypeOptions.forEach((item) => { map[item.value] = item.label })
  return type ? (map[type] || type) : '-'
}

function conflictTypeTagType(type?: string) {
  const map: Record<string, string> = {
    TEACHER_CONFLICT: 'danger',
    CLASS_CONFLICT: 'danger',
    ROOM_CONFLICT: 'danger',
    CLASSROOM_CAPACITY_NOT_ENOUGH: 'warning',
    ROOM_TYPE_MISMATCH: 'warning',
    TEACHER_UNAVAILABLE: 'warning',
    TASK_NOT_FULLY_SCHEDULED: 'info',
    TEACHER_DAILY_LIMIT: 'info',
    CLASS_DAILY_LIMIT: 'info',
  }
  return type ? (map[type] || '') : ''
}

function objectTypeText(type?: string) {
  const map: Record<string, string> = {}
  objectTypeOptions.forEach((item) => { map[item.value] = item.label })
  return type ? (map[type] || type) : '-'
}

async function fetchReports() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size,
    }
    if (searchForm.reportNo.trim()) params.reportNo = searchForm.reportNo.trim()
    if (searchForm.conflictType) params.conflictType = searchForm.conflictType
    if (searchForm.objectType) params.objectType = searchForm.objectType
    if (searchForm.objectName.trim()) params.objectName = searchForm.objectName.trim()

    const res = await getScheduleConflictReportList(params as any)
    reportList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchReports()
}

function handleReset() {
  searchForm.reportNo = ''
  searchForm.conflictType = ''
  searchForm.objectType = ''
  searchForm.objectName = ''
  pagination.page = 1
  fetchReports()
}

async function handleGenerate() {
  generating.value = true
  try {
    const result = await generateScheduleConflictReport()
    latestReportNo.value = result.reportNo
    searchForm.reportNo = result.reportNo
    pagination.page = 1
    ElMessage.success(result.message)
    await fetchReports()
  } finally {
    generating.value = false
  }
}

async function handleClear() {
  const targetText = searchForm.reportNo.trim() ? `报告 ${searchForm.reportNo.trim()}` : '全部冲突报告'
  await ElMessageBox.confirm(`确定清空${targetText}吗？此操作不可恢复。`, '确认清空', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
  try {
    await clearScheduleConflictReports(searchForm.reportNo.trim() || undefined)
    ElMessage.success('清空成功')
    if (searchForm.reportNo.trim()) {
      latestReportNo.value = ''
    }
    await fetchReports()
  } catch (_e) { /* 错误由拦截器处理 */ }
}

function handleSizeChange() {
  pagination.page = 1
  fetchReports()
}

onMounted(() => {
  fetchReports()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <template #header>
        <div class="card-header">
          <span>排课冲突报告</span>
          <div>
            <el-button type="primary" :loading="generating" @click="handleGenerate">生成冲突报告</el-button>
            <el-button type="danger" plain @click="handleClear" style="margin-left: 8px">清空报告</el-button>
          </div>
        </div>
      </template>

      <div v-if="latestReportNo" class="latest-report-tip">
        最近生成报告：{{ latestReportNo }}
      </div>

      <el-form :model="searchForm" inline>
        <el-form-item label="报告编号">
          <el-input v-model="searchForm.reportNo" placeholder="如 CR20260513093000" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="冲突类型">
          <el-select v-model="searchForm.conflictType" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="item in conflictTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="searchForm.objectType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in objectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象名称">
          <el-input v-model="searchForm.objectName" placeholder="教师/班级/教室/任务" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>冲突明细（共 {{ total }} 条）</span>
      </template>

      <el-table :data="reportList" v-loading="loading" stripe>
        <el-table-column prop="reportNo" label="报告编号" min-width="170" />
        <el-table-column label="冲突类型" width="170">
          <template #default="{ row }">
            <el-tag :type="conflictTypeTagType(row.conflictType)" size="small">
              {{ conflictTypeText(row.conflictType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="冲突对象" min-width="180">
          <template #default="{ row }">
            <div>{{ row.objectName || '-' }}</div>
            <div class="sub-text">{{ objectTypeText(row.objectType) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="冲突时间" width="130">
          <template #default="{ row }">
            {{ row.timeSlotName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="冲突说明" min-width="300" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="处理建议" min-width="300" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
      </el-table>

      <el-empty v-if="!loading && reportList.length === 0" description="暂无冲突报告记录" />

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchReports"
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

.search-card {
  margin-top: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.latest-report-tip {
  margin-bottom: 12px;
  color: #409eff;
  font-size: 13px;
}

.sub-text {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
</style>
