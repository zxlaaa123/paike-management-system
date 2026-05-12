<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBatchList,
  getBatchById,
  runAutoSchedule,
  clearBatchSchedules,
  type AutoScheduleBatch,
} from '../../api/autoSchedule'
import { getUnscheduledTaskList, clearUnscheduledTasks, type UnscheduledTask } from '../../api/unscheduledTask'
import { getAllTeachingTasks, type TeachingTask } from '../../api/teachingTask'

const activeTab = ref('batches')
const loading = ref(false)
const running = ref(false)

// 批次列表
const batchList = ref<AutoScheduleBatch[]>([])
const batchTotal = ref(0)
const batchPage = ref(1)
const batchSize = ref(10)

// 未排任务列表
const taskList = ref<UnscheduledTask[]>([])
const taskTotal = ref(0)
const taskPage = ref(1)
const taskSize = ref(10)
const taskFilter = reactive({
  batchId: undefined as number | undefined,
  reasonType: '',
})

// 待排教学任务
const teachingTasks = ref<TeachingTask[]>([])
const selectedTaskIds = ref<number[]>([])

// 自动排课设置
const autoScheduleForm = reactive({
  clearOldAutoSchedule: true,
  clearAllSchedule: false,
})

// 最新批次结果
const latestBatch = ref<AutoScheduleBatch | null>(null)

async function fetchBatches() {
  loading.value = true
  try {
    const res = await getBatchList({ page: batchPage.value, size: batchSize.value })
    batchList.value = res.records
    batchTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchTasks() {
  loading.value = true
  try {
    const res = await getUnscheduledTaskList({
      ...taskFilter,
      page: taskPage.value,
      size: taskSize.value,
    })
    taskList.value = res.records
    taskTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchTeachingTasks() {
  try {
    teachingTasks.value = await getAllTeachingTasks()
  } catch (_e) { /* 错误由拦截器处理 */ }
}

async function handleRunAutoSchedule() {
  if (selectedTaskIds.value.length === 0) {
    ElMessage.warning('请选择至少一个教学任务')
    return
  }
  running.value = true
  try {
    const result = await runAutoSchedule({
      taskIds: selectedTaskIds.value,
      clearOldAutoSchedule: autoScheduleForm.clearOldAutoSchedule,
      clearAllSchedule: autoScheduleForm.clearAllSchedule,
    })
    latestBatch.value = result
    ElMessage.success(`自动排课批次已创建，批次号：${result.batchNo}`)
    fetchBatches()
  } catch (_e) { /* 错误由拦截器处理 */ } finally {
    running.value = false
  }
}

async function handleClearBatchSchedules(batch: AutoScheduleBatch) {
  await ElMessageBox.confirm(`确定清空批次 ${batch.batchNo} 的自动排课结果吗？`, '提示', { type: 'warning' })
  try {
    await clearBatchSchedules(batch.id)
    ElMessage.success('清空成功')
    fetchBatches()
  } catch (_e) { /* 错误由拦截器处理 */ }
}

async function handleClearUnscheduledTasks() {
  await ElMessageBox.confirm('确定清空未排任务记录吗？', '提示', { type: 'warning' })
  try {
    await clearUnscheduledTasks()
    ElMessage.success('清空成功')
    fetchTasks()
  } catch (_e) { /* 错误由拦截器处理 */ }
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    RUNNING: 'warning',
    SUCCESS: 'success',
    PARTIAL: 'warning',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

function statusText(status: string) {
  const map: Record<string, string> = {
    RUNNING: '执行中',
    SUCCESS: '完成',
    PARTIAL: '部分成功',
    FAILED: '失败',
  }
  return map[status] || status
}

onMounted(() => {
  fetchBatches()
  fetchTeachingTasks()
})
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <!-- 自动排课批次 -->
      <el-tab-pane label="自动排课批次" name="batches">
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-header">
              <span>自动排课</span>
              <el-button type="primary" :loading="running" @click="handleRunAutoSchedule">
                一键自动排课
              </el-button>
            </div>
          </template>

          <!-- 执行结果 -->
          <el-alert v-if="latestBatch" :title="`批次号：${latestBatch.batchNo} | 状态：${statusText(latestBatch.status)}`"
            type="info" :closable="true" @close="latestBatch = null" style="margin-bottom: 16px">
            <template #default>
              <div>参与任务：{{ latestBatch.totalTaskCount }} | 成功：{{ latestBatch.successTaskCount }} | 失败：{{ latestBatch.failedTaskCount }} | 生成记录：{{ latestBatch.generatedScheduleCount }}</div>
              <div v-if="latestBatch.message">{{ latestBatch.message }}</div>
            </template>
          </el-alert>

          <!-- 待排教学任务选择 -->
          <div style="margin-bottom: 16px">
            <div style="margin-bottom: 8px; font-weight: 500">选择教学任务</div>
            <el-select v-model="selectedTaskIds" multiple placeholder="请选择教学任务（留空=全部未排满）"
              filterable style="width: 100%">
              <el-option v-for="task in teachingTasks" :key="task.id"
                :label="`${task.courseName} / ${task.teacherName} / ${task.className} / 每周${task.weeklyHours}节`"
                :value="task.id" />
            </el-select>
          </div>

          <!-- 清空选项 -->
          <div style="margin-bottom: 16px">
            <el-checkbox v-model="autoScheduleForm.clearOldAutoSchedule">清空旧自动排课结果</el-checkbox>
            <el-checkbox v-model="autoScheduleForm.clearAllSchedule" style="margin-left: 16px">清空全部排课结果（含手动排课）</el-checkbox>
          </div>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header>
            <span>批次记录</span>
          </template>
          <el-table :data="batchList" v-loading="loading" stripe>
            <el-table-column prop="batchNo" label="批次号" min-width="180" />
            <el-table-column prop="totalTaskCount" label="参与任务" width="90" />
            <el-table-column prop="successTaskCount" label="成功" width="80" />
            <el-table-column prop="failedTaskCount" label="失败" width="80" />
            <el-table-column prop="generatedScheduleCount" label="生成记录" width="90" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="170" />
            <el-table-column prop="endTime" label="结束时间" width="170" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" link @click="handleClearBatchSchedules(row)">清空结果</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="batchPage" v-model:page-size="batchSize" :total="batchTotal"
            :page-sizes="[10, 20]" layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end" @change="fetchBatches" />
        </el-card>
      </el-tab-pane>

      <!-- 未排任务 -->
      <el-tab-pane label="未排任务" name="tasks">
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-header">
              <span>未排任务列表</span>
              <el-button type="danger" plain @click="handleClearUnscheduledTasks">清空记录</el-button>
            </div>
          </template>
          <el-table :data="taskList" v-loading="loading" stripe>
            <el-table-column prop="batchNo" label="批次号" min-width="160" />
            <el-table-column prop="courseName" label="课程名称" min-width="140" />
            <el-table-column prop="teacherName" label="教师" width="100" />
            <el-table-column prop="className" label="班级" width="120" />
            <el-table-column prop="requiredSlots" label="需要" width="70" />
            <el-table-column prop="scheduledSlots" label="已排" width="70" />
            <el-table-column prop="remainingSlots" label="剩余" width="70" />
            <el-table-column prop="reasonType" label="原因类型" width="120" />
            <el-table-column prop="reasonMessage" label="原因说明" min-width="200" />
            <el-table-column prop="createTime" label="创建时间" width="170" />
          </el-table>
          <el-pagination v-model:current-page="taskPage" v-model:page-size="taskSize" :total="taskTotal"
            :page-sizes="[10, 20]" layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end" @change="fetchTasks" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
