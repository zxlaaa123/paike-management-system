<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getUnscheduledTaskList, clearUnscheduledTasks, type UnscheduledTask } from '../../api/unscheduledTask'
import { getBatchList, type AutoScheduleBatch } from '../../api/autoSchedule'

const router = useRouter()
const loading = ref(false)
const taskList = ref<UnscheduledTask[]>([])
const total = ref(0)
const batchList = ref<AutoScheduleBatch[]>([])

const searchForm = reactive({
  batchId: undefined as number | undefined,
  courseName: '',
  teacherName: '',
  className: '',
  reasonType: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
})

// reasonType 需要和后端未排原因编码保持一致，前端只负责做可读化展示和颜色区分。
const reasonTypeOptions = [
  { value: 'TEACHER_UNAVAILABLE', label: '教师禁排' },
  { value: 'TEACHER_CONFLICT', label: '教师冲突' },
  { value: 'CLASS_CONFLICT', label: '班级冲突' },
  { value: 'ROOM_CONFLICT', label: '教室冲突' },
  { value: 'CLASSROOM_CAPACITY_NOT_ENOUGH', label: '容量不足' },
  { value: 'ROOM_TYPE_MISMATCH', label: '类型不匹配' },
  { value: 'TASK_NOT_FULLY_SCHEDULED', label: '课时超限' },
  { value: 'TEACHER_DAILY_LIMIT', label: '教师日限' },
  { value: 'CLASS_DAILY_LIMIT', label: '班级日限' },
  { value: 'SAME_COURSE_SAME_DAY', label: '同课同日' },
  { value: 'NO_MATCHED_CLASSROOM', label: '无匹配教室' },
  { value: 'UNKNOWN', label: '未知原因' },
]

function reasonTypeText(type: string) {
  const map: Record<string, string> = {}
  reasonTypeOptions.forEach(o => { map[o.value] = o.label })
  return map[type] || type
}

function reasonTypeTagType(type: string) {
  const map: Record<string, string> = {
    TEACHER_UNAVAILABLE: 'warning',
    TEACHER_CONFLICT: 'danger',
    CLASS_CONFLICT: 'danger',
    ROOM_CONFLICT: 'danger',
    CLASSROOM_CAPACITY_NOT_ENOUGH: 'warning',
    ROOM_TYPE_MISMATCH: 'warning',
    TASK_NOT_FULLY_SCHEDULED: 'info',
    TEACHER_DAILY_LIMIT: 'info',
    CLASS_DAILY_LIMIT: 'info',
    SAME_COURSE_SAME_DAY: 'info',
    NO_MATCHED_CLASSROOM: 'warning',
    UNKNOWN: '',
  }
  return map[type] || ''
}

async function fetchTasks() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      batchId: searchForm.batchId || undefined,
      courseName: searchForm.courseName.trim() || undefined,
      teacherName: searchForm.teacherName.trim() || undefined,
      className: searchForm.className.trim() || undefined,
      reasonType: searchForm.reasonType || undefined,
    }

    const res = await getUnscheduledTaskList(params)
    taskList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchBatches() {
  try {
    // 批次筛选和按批次清空都依赖批次号，这里一次性拉取最近批次列表供页面复用。
    const res = await getBatchList({ page: 1, size: 100 })
    batchList.value = res.records
  } catch (_e) {
    console.error(_e)
    ElMessage.error('加载批次列表失败')
  }
}

function handleSearch() {
  pagination.page = 1
  fetchTasks()
}

function handleReset() {
  searchForm.batchId = undefined
  searchForm.courseName = ''
  searchForm.teacherName = ''
  searchForm.className = ''
  searchForm.reasonType = ''
  pagination.page = 1
  fetchTasks()
}

async function handleClearAll() {
  await ElMessageBox.confirm('确定清空全部未排任务记录吗？此操作不可恢复。', '确认清空', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
  try {
    await clearUnscheduledTasks()
    ElMessage.success('清空成功')
    fetchTasks()
  } catch (_e) {
    console.error(_e)
    ElMessage.error('清空未排任务失败')
  }
}

async function handleClearBatch(batchId: number) {
  // 按批次清理时保留其他批次记录，方便排课失败后只回收本轮自动排课产生的未排项。
  await ElMessageBox.confirm('确定清空该批次的未排任务记录吗？', '确认清空', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
  try {
    await clearUnscheduledTasks(batchId)
    ElMessage.success('清空成功')
    fetchTasks()
  } catch (_e) {
    console.error(_e)
    ElMessage.error('清空未排任务失败')
  }
}

function gotoManualSchedule() {
  // 未排任务最终还是要回到手动排课页处理，这里提供固定跳转入口。
  router.push('/schedule')
}

function handleSizeChange() {
  pagination.page = 1
  fetchTasks()
}

onMounted(() => {
  fetchTasks()
  fetchBatches()
})
</script>

<template>
  <div class="page-container">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <template #header>
        <span>未排任务查询</span>
      </template>
      <el-form :model="searchForm" inline>
        <el-form-item label="批次号">
          <el-select v-model="searchForm.batchId" placeholder="全部批次" clearable style="width: 220px">
            <el-option v-for="batch in batchList" :key="batch.batchId" :label="batch.batchNo" :value="batch.batchId" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-input v-model="searchForm.courseName" placeholder="课程名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="searchForm.teacherName" placeholder="教师姓名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="班级">
          <el-input v-model="searchForm.className" placeholder="班级名称" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="原因类型">
          <el-select v-model="searchForm.reasonType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="opt in reasonTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>未排任务列表（共 {{ total }} 条）</span>
          <div>
            <el-button type="primary" plain @click="gotoManualSchedule">前往手动排课</el-button>
            <el-button type="danger" plain @click="handleClearAll" style="margin-left: 8px">清空未排记录</el-button>
          </div>
        </div>
      </template>

      <el-table :data="taskList" v-loading="loading" stripe>
        <el-table-column prop="batchNo" label="批次号" min-width="180" />
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="teacherName" label="教师姓名" min-width="100" />
        <el-table-column prop="className" label="班级名称" min-width="120" />
        <el-table-column prop="requiredSlots" label="需要大节数" width="100" align="center" />
        <el-table-column prop="scheduledSlots" label="已排大节数" width="100" align="center" />
        <el-table-column label="剩余大节数" width="100" align="center">
          <template #default="{ row }">
            <span class="remaining-highlight">{{ row.remainingSlots }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未排原因类型" width="130">
          <template #default="{ row }">
            <el-tag :type="reasonTypeTagType(row.reasonType || '')" size="small">
              {{ reasonTypeText(row.reasonType || '') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reasonMessage" label="未排原因说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="gotoManualSchedule">手动排课</el-button>
            <el-button type="danger" link size="small" @click="handleClearBatch(row.batchId)">清除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="!loading && taskList.length === 0" description="暂无未排任务记录" />

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchTasks"
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
.remaining-highlight {
  color: #e6a23c;
  font-weight: 600;
}
</style>
