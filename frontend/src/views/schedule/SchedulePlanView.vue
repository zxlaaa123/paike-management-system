<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getSchedulePlanList,
  deleteSchedulePlan,
  abandonSchedulePlan,
  type SchedulePlan,
} from '../../api/schedulePlan'
import { getAllSemesters, getCurrentSemester, type Semester } from '../../api/semester'
import { schedulePlanStatusTagType as statusTagType, schedulePlanStatusText as statusText, strategyText } from '../../utils/status'
import { isCancel } from '../../utils/errors'
import { fallback } from '../../utils/async'

const router = useRouter()

const loading = ref(false)
const tableData = ref<SchedulePlan[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = reactive({
  semesterId: undefined as number | undefined,
  status: '',
  strategyType: '',
  keyword: '',
})

const semesterList = ref<Semester[]>([])
const currentSemester = ref<Semester | null>(null)

const hasCurrentSemester = computed(() => currentSemester.value !== null)

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { ...searchForm, page: currentPage.value, size: pageSize.value }
    if (!params.semesterId && currentSemester.value) {
      params.semesterId = currentSemester.value.id
    }
    const res = await getSchedulePlanList(params)
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  currentPage.value = 1
  fetchData()
}

async function fetchOptions() {
  const [semesters, current] = await Promise.all([
    fallback(getAllSemesters(), []),
    fallback(getCurrentSemester(), null),
  ])
  semesterList.value = semesters
  currentSemester.value = current
  if (current) {
    searchForm.semesterId = current.id
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.status = ''
  searchForm.strategyType = ''
  searchForm.keyword = ''
  searchForm.semesterId = currentSemester.value?.id
  handleSearch()
}

function viewDetail(row: SchedulePlan) {
  router.push(`/v3/schedule-plans/${row.id}`)
}

function viewV4Analysis(row: SchedulePlan) {
  router.push(`/v4/schedule-analysis/${row.id}`)
}

async function handleDelete(row: SchedulePlan) {
  if (row.status !== 'DRAFT') {
    ElMessage.warning('只能删除草稿方案')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除方案「${row.name}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
    await deleteSchedulePlan(row.id)
    ElMessage.success('删除成功')
    await fetchData()
  } catch (err: unknown) {
    if (isCancel(err)) return
    await fetchData()
  }
}

async function handleAbandon(row: SchedulePlan) {
  try {
    await ElMessageBox.confirm(`确定废弃方案「${row.name}」吗？`, '提示', { type: 'warning' })
    await abandonSchedulePlan(row.id)
    ElMessage.success('已废弃')
    await fetchData()
  } catch (err: unknown) {
    if (isCancel(err)) return
    await fetchData()
  }
}

function rowClass({ row }: { row: SchedulePlan }): string {
  if (row.conflictCount > 0) return 'row-conflict'
  if (row.unscheduledCount > 0) return 'row-warning'
  return ''
}

onMounted(() => {
  void (async () => {
    await fetchOptions()
    await fetchData()
  })()
})
</script>

<template>
  <div class="page-container">
    <!-- 无当前学期提示 -->
    <el-alert
      v-if="!hasCurrentSemester"
      title="当前未设置学期，部分功能无法使用。请先在「学期管理」中创建并设置当前学期。"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 当前学期信息条 -->
    <el-alert
      v-if="currentSemester"
      :title="`当前学期：${currentSemester.name}`"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="学期">
          <el-select v-model="searchForm.semesterId" placeholder="选择学期" clearable style="width: 220px">
            <el-option v-for="s in semesterList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已应用" value="APPLIED" />
            <el-option label="已废弃" value="ABANDONED" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略">
          <el-select v-model="searchForm.strategyType" placeholder="全部" clearable>
            <el-option label="教师优先" value="TEACHER_PRIORITY" />
          <el-option label="班级均衡" value="CLASS_BALANCE" />
          <el-option label="教室利用率" value="CLASSROOM_UTILIZATION" />
          <el-option label="综合最优" value="COMPREHENSIVE" />
          <el-option label="智能求解" value="SOLVER_V8" />
        </el-select>
      </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="方案名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>排课方案列表</span>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe :row-class-name="rowClass">
        <el-table-column prop="name" label="方案名称" min-width="160" />
        <el-table-column label="学期" min-width="150">
          <template #default="{ row }">
            <div>{{ row.semesterName || '—' }}</div>
            <div class="sub-text">ID：{{ row.semesterId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="策略类型" width="150">
          <template #default="{ row }">
            <div>{{ row.strategyName || strategyText(row.strategyType) }}</div>
            <div class="sub-text">{{ row.strategyType || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column label="已排/未排" width="100">
          <template #default="{ row }">{{ row.scheduledCount }}/{{ row.unscheduledCount }}</template>
        </el-table-column>
        <el-table-column prop="conflictCount" label="冲突" width="70" />
        <el-table-column prop="description" label="说明" min-width="120" />
        <el-table-column label="生成时间" width="160">
          <template #default="{ row }">{{ row.generatedAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
            <el-button type="warning" link @click="viewV4Analysis(row)">V4分析</el-button>
            <el-button type="success" link @click="router.push('/v3/schedule-compare')">对比</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="danger"
              link
              @click="handleDelete(row)"
            >删除</el-button>
            <el-button
              v-if="row.status !== 'ABANDONED'"
              type="warning"
              link
              @click="handleAbandon(row)"
            >废弃</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchData" @size-change="handleSizeChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  padding: 4px 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sub-text {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.2;
}
:deep(.row-conflict) {
  --el-table-tr-bg-color: #fef0f0;
}
:deep(.row-warning) {
  --el-table-tr-bg-color: #fdf6ec;
}
</style>
