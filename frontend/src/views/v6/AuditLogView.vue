<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  getSystemAuditLogById,
  getSystemAuditLogList,
  type SystemAuditLog,
} from '../../api/systemAuditLog'

const loading = ref(false)
const detailLoading = ref(false)
const logList = ref<SystemAuditLog[]>([])
const currentLog = ref<SystemAuditLog | null>(null)
const detailVisible = ref(false)
const total = ref(0)

const searchForm = reactive({
  actionType: '',
  semesterId: undefined as number | undefined,
  planId: undefined as number | undefined,
  success: undefined as boolean | undefined,
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const actionOptions = [
  { value: 'APPLY_PLAN', label: '应用方案' },
  { value: 'ROLLBACK_PLAN', label: '回滚方案' },
  { value: 'LOCK_PLAN_ITEM', label: '锁定方案课程' },
  { value: 'UNLOCK_PLAN_ITEM', label: '解除方案课程锁定' },
  { value: 'LOCK_SCHEDULE', label: '锁定正式课表' },
  { value: 'UNLOCK_SCHEDULE', label: '解除正式课表锁定' },
  { value: 'CREATE_SCHEDULE', label: '手动排课' },
  { value: 'DELETE_SCHEDULE', label: '删除排课' },
  { value: 'ADJUST_SCHEDULE', label: '局部调整' },
  { value: 'APPLY_SIMULATION_PLAN', label: '应用试算方案' },
  { value: 'CREATE_LOCAL_REPLAN_PLAN', label: '创建局部重排方案' },
  { value: 'GENERATE_SIMULATION_PLAN', label: '生成试算方案' },
  { value: 'GENERATE_LOCAL_REPLAN_SIMULATION', label: '生成局部重排试算' },
  { value: 'RUN_AUTO_SCHEDULE', label: '自动排课批次' },
]

const targetTypeMap: Record<string, string> = {
  SCHEDULE_PLAN: '排课方案',
  SCHEDULE_PLAN_ITEM: '方案课程',
  SCHEDULE: '正式课表',
  AUTO_SCHEDULE_BATCH: '自动排课批次',
}

function actionText(actionType?: string) {
  const found = actionOptions.find((item) => item.value === actionType)
  return found?.label || actionType || '-'
}

function targetTypeText(targetType?: string) {
  return targetType ? (targetTypeMap[targetType] || targetType) : '-'
}

function statusTagType(success?: number) {
  return success === 1 ? 'success' : 'danger'
}

function statusText(success?: number) {
  return success === 1 ? '成功' : '失败'
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function emptyToUndefined(value?: number) {
  return value === undefined || value === null ? undefined : value
}

async function fetchLogs() {
  loading.value = true
  try {
    const res = await getSystemAuditLogList({
      page: pagination.page,
      size: pagination.size,
      actionType: searchForm.actionType || undefined,
      semesterId: emptyToUndefined(searchForm.semesterId),
      planId: emptyToUndefined(searchForm.planId),
      success: searchForm.success,
    })
    logList.value = res.records || []
    total.value = res.total || 0
  } catch {
    logList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchLogs()
}

function handleReset() {
  searchForm.actionType = ''
  searchForm.semesterId = undefined
  searchForm.planId = undefined
  searchForm.success = undefined
  pagination.page = 1
  fetchLogs()
}

function handleSizeChange() {
  pagination.page = 1
  fetchLogs()
}

async function openDetail(row: SystemAuditLog) {
  detailVisible.value = true
  detailLoading.value = true
  currentLog.value = row
  try {
    currentLog.value = await getSystemAuditLogById(row.id)
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchLogs()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
          <el-button type="primary" :loading="loading" @click="fetchLogs">刷新</el-button>
        </div>
      </template>

      <el-form :model="searchForm" inline>
        <el-form-item label="操作类型">
          <el-select v-model="searchForm.actionType" placeholder="全部" clearable style="width: 220px">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期ID">
          <el-input-number v-model="searchForm.semesterId" :min="1" :controls="false" placeholder="全部" style="width: 140px" />
        </el-form-item>
        <el-form-item label="方案ID">
          <el-input-number v-model="searchForm.planId" :min="1" :controls="false" placeholder="全部" style="width: 140px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="searchForm.success" placeholder="全部" clearable style="width: 120px">
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

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>日志明细（共 {{ total }} 条）</span>
      </template>

      <el-table :data="logList" v-loading="loading" stripe>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.success)" size="small">{{ statusText(row.success) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" min-width="180">
          <template #default="{ row }">
            <div>{{ actionText(row.actionType) }}</div>
            <div class="sub-text">{{ row.actionType }}</div>
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="170">
          <template #default="{ row }">
            <div>{{ targetTypeText(row.targetType) }}</div>
            <div class="sub-text">ID：{{ row.targetId ?? '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="范围" min-width="150">
          <template #default="{ row }">
            <div>学期：{{ row.semesterId ?? '-' }}</div>
            <div class="sub-text">方案：{{ row.planId ?? '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作人" min-width="130">
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="240">
          <template #default="{ row }">
            <span class="summary-text">{{ row.success === 1 ? row.afterSummary || '-' : row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @current-change="fetchLogs"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="审计日志详情" width="720px">
      <el-descriptions v-loading="detailLoading" :column="2" border>
        <el-descriptions-item label="日志ID">{{ currentLog?.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag v-if="currentLog" :type="statusTagType(currentLog.success)" size="small">
            {{ statusText(currentLog.success) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ currentLog ? actionText(currentLog.actionType) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象类型">{{ targetTypeText(currentLog?.targetType) }}</el-descriptions-item>
        <el-descriptions-item label="对象ID">{{ currentLog?.targetId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="学期ID">{{ currentLog?.semesterId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="方案ID">{{ currentLog?.planId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog?.operatorName || currentLog?.operatorId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDateTime(currentLog?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ currentLog?.errorCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成功摘要" :span="2">
          {{ currentLog?.afterSummary || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="失败信息" :span="2">
          {{ currentLog?.errorMessage || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-card,
.table-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sub-text {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
}

.summary-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
