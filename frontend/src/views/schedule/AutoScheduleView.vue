<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getBatchList,
  runAutoSchedule,
  clearBatchSchedules,
  type AutoScheduleBatch,
} from '../../api/autoSchedule'
import { getAllTeachingTasks, type TeachingTask } from '../../api/teachingTask'
import { batchStatusText, batchStatusTagType } from '../../utils/status'
import { extractMessage } from '../../utils/errors'

const router = useRouter()
const loading = ref(false)
const running = ref(false)

// 批次列表
const batchList = ref<AutoScheduleBatch[]>([])
const batchTotal = ref(0)
const batchPage = ref(1)
const batchSize = ref(10)

// 待排教学任务
const teachingTasks = ref<TeachingTask[]>([])
const selectedTaskIds = ref<number[]>([])
const taskRange = ref<'all' | 'selected'>('all')

// 自动排课设置
const autoScheduleForm = reactive({
  clearOldAutoSchedule: true,
  clearAllSchedule: false,
})

// 最新批次结果
const latestBatch = ref<AutoScheduleBatch | null>(null)

// 计算待排教学任务的剩余大节数
function getRemainingSlots(task: TeachingTask): number {
  const required = Math.ceil((task.weeklyHours || 0) / 2)
  const scheduled = task.scheduledSlots || 0
  return Math.max(0, required - scheduled)
}

// 过滤出有剩余大节数的任务
const pendingTasks = computed(() => {
  return teachingTasks.value.filter(t => getRemainingSlots(t) > 0)
})

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

async function fetchTeachingTasks() {
  try {
    teachingTasks.value = await getAllTeachingTasks()
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载待排教学任务失败'))
  }
}

async function handleRunAutoSchedule() {
  if (taskRange.value === 'selected' && selectedTaskIds.value.length === 0) {
    ElMessage.warning('请选择至少一个教学任务')
    return
  }

  // 危险操作二次确认
  if (autoScheduleForm.clearAllSchedule) {
    try {
      await ElMessageBox.confirm(
        '该操作会清空全部排课记录（含手动排课），是否继续？',
        '危险操作确认',
        { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }

  running.value = true
  try {
    const taskIds = taskRange.value === 'selected' ? selectedTaskIds.value : undefined
    const result = await runAutoSchedule({
      taskIds,
      clearOldAutoSchedule: autoScheduleForm.clearOldAutoSchedule,
      clearAllSchedule: autoScheduleForm.clearAllSchedule,
    })
    latestBatch.value = result

    if (result.status === 'SUCCESS') {
      ElMessage.success(`自动排课完成！批次号：${result.batchNo}，全部 ${result.totalTaskCount} 个任务已安排`)
    } else if (result.status === 'PARTIAL') {
      ElMessage.warning(`自动排课完成，${result.successTaskCount}/${result.totalTaskCount} 个任务已安排，${result.failedTaskCount} 个未排满`)
    } else {
      ElMessage.error(`自动排课完成，所有任务均未安排`)
    }

    fetchBatches()
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '自动排课失败'))
  } finally {
    running.value = false
  }
}

async function handleClearBatchSchedules(batch: AutoScheduleBatch) {
  await ElMessageBox.confirm(`确定清空批次 ${batch.batchNo} 的自动排课结果吗？`, '提示', { type: 'warning' })
  try {
    await clearBatchSchedules(batch.batchId)
    ElMessage.success('清空成功')
    fetchBatches()
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '清空批次排课结果失败'))
  }
}

function gotoTimetable() {
  router.push('/timetable/class')
}

onMounted(() => {
  fetchBatches()
  fetchTeachingTasks()
})
</script>

<template>
  <div class="page-container">
    <!-- 执行结果区 -->
    <el-card v-if="latestBatch" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>执行结果</span>
          <el-button type="primary" link @click="latestBatch = null">关闭</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次号">{{ latestBatch.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="batchStatusTagType(latestBatch.status)">{{ batchStatusText(latestBatch.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="参与任务">{{ latestBatch.totalTaskCount }}</el-descriptions-item>
        <el-descriptions-item label="成功任务">{{ latestBatch.successTaskCount }}</el-descriptions-item>
        <el-descriptions-item label="失败任务">{{ latestBatch.failedTaskCount }}</el-descriptions-item>
        <el-descriptions-item label="生成记录">{{ latestBatch.generatedScheduleCount }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="latestBatch.message" style="margin-top: 8px; color: #606266">{{ latestBatch.message }}</div>
      <!-- 快捷操作 -->
      <div style="margin-top: 12px">
        <el-button size="small" @click="router.push('/unscheduled-tasks')">查看未排任务</el-button>
        <el-button size="small" @click="gotoTimetable">查看课表</el-button>
      </div>
    </el-card>

    <!-- 自动排课设置区 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>自动排课设置</span>
          <el-button type="primary" :loading="running" @click="handleRunAutoSchedule">
            一键自动排课
          </el-button>
        </div>
      </template>

      <!-- 参与任务范围 -->
      <div style="margin-bottom: 16px">
        <div style="margin-bottom: 8px; font-weight: 500">参与任务范围</div>
        <el-radio-group v-model="taskRange">
          <el-radio value="all">全部未排满任务（{{ pendingTasks.length }} 个）</el-radio>
          <el-radio value="selected">手动选择任务</el-radio>
        </el-radio-group>
      </div>

      <!-- 待排教学任务表格 -->
      <div v-if="taskRange === 'selected'" style="margin-bottom: 16px">
        <div style="margin-bottom: 8px; color: #909399; font-size: 13px">
          已选择 {{ selectedTaskIds.length }} 个任务
        </div>
        <el-table :data="pendingTasks" height="250" stripe @selection-change="selectedTaskIds = ($event as TeachingTask[]).map(t => t.id)">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="courseName" label="课程名称" min-width="140" />
          <el-table-column prop="teacherName" label="教师" width="100" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column prop="weeklyHours" label="每周课时" width="90" />
          <el-table-column label="需要大节" width="90">
            <template #default="{ row }">{{ Math.ceil((row.weeklyHours || 0) / 2) }}</template>
          </el-table-column>
          <el-table-column label="已排大节" width="90">
            <template #default="{ row }">{{ row.scheduledSlots || 0 }}</template>
          </el-table-column>
          <el-table-column label="剩余大节" width="90">
            <template #default="{ row }">
              <span :style="{ color: getRemainingSlots(row) > 0 ? '#e6a23c' : '#67c23a' }">
                {{ getRemainingSlots(row) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 待排任务预览（全部模式） -->
      <div v-else style="margin-bottom: 16px">
        <el-table :data="pendingTasks.slice(0, 5)" height="200" stripe>
          <el-table-column prop="courseName" label="课程名称" min-width="140" />
          <el-table-column prop="teacherName" label="教师" width="100" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="剩余大节" width="90">
            <template #default="{ row }">
              <span :style="{ color: getRemainingSlots(row) > 0 ? '#e6a23c' : '#67c23a' }">
                {{ getRemainingSlots(row) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="pendingTasks.length > 5" style="margin-top: 8px; color: #909399; font-size: 13px">
          还有 {{ pendingTasks.length - 5 }} 个任务...
        </div>
      </div>

      <!-- 清空选项 -->
      <div style="margin-bottom: 8px">
        <el-checkbox v-model="autoScheduleForm.clearOldAutoSchedule">清空旧自动排课结果</el-checkbox>
        <el-checkbox v-model="autoScheduleForm.clearAllSchedule" style="margin-left: 16px">
          <span style="color: #f56c6c">清空全部排课结果（含手动排课）</span>
        </el-checkbox>
      </div>
    </el-card>

    <!-- 最近批次记录区 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>最近批次记录</span>
      </template>
      <el-table :data="batchList" v-loading="loading" stripe>
        <el-table-column prop="batchNo" label="批次号" min-width="180" />
        <el-table-column prop="totalTaskCount" label="参与任务" width="90" />
        <el-table-column prop="successTaskCount" label="成功" width="80" />
        <el-table-column prop="failedTaskCount" label="失败" width="80" />
        <el-table-column prop="generatedScheduleCount" label="生成记录" width="90" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="batchStatusTagType(row.status)">{{ batchStatusText(row.status) }}</el-tag>
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
        style="margin-top: 16px; justify-content: flex-end" @current-change="fetchBatches" @size-change="fetchBatches" />
    </el-card>
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
