<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  cancelRepairTask,
  getRepairTaskDetail,
  updateRepairTaskStatus,
  type V5RepairTaskDetail,
  type V5RepairTaskStatus,
} from '../../api/v5RepairTaskApi'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.taskId))
const loading = ref(false)
const updating = ref(false)
const task = ref<V5RepairTaskDetail | null>(null)

const statusOptions: Array<{ label: string; value: V5RepairTaskStatus }> = [
  { label: '已创建', value: 'CREATED' },
  { label: '分析中', value: 'ANALYZING' },
  { label: '已生成建议', value: 'SUGGESTED' },
  { label: '已试算', value: 'SIMULATED' },
  { label: '已应用', value: 'APPLIED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '失败', value: 'FAILED' },
]

function statusText(status: V5RepairTaskStatus) {
  return statusOptions.find((s) => s.value === status)?.label || status
}

function statusTagType(status: V5RepairTaskStatus) {
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  if (status === 'APPLIED') return 'success'
  if (status === 'SIMULATED' || status === 'SUGGESTED') return 'warning'
  return 'primary'
}

async function fetchData() {
  loading.value = true
  try {
    task.value = await getRepairTaskDetail(taskId.value)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载任务详情失败')
  } finally {
    loading.value = false
  }
}

async function changeStatus(status: V5RepairTaskStatus) {
  if (!task.value) return
  updating.value = true
  try {
    task.value = await updateRepairTaskStatus(task.value.id, { status })
    ElMessage.success('状态已更新')
  } catch (error: any) {
    ElMessage.error(error?.message || '状态更新失败')
  } finally {
    updating.value = false
  }
}

async function cancelTask() {
  if (!task.value) return
  updating.value = true
  try {
    task.value = await cancelRepairTask(task.value.id, '用户手动取消')
    ElMessage.success('任务已取消')
  } catch (error: any) {
    ElMessage.error(error?.message || '取消失败')
  } finally {
    updating.value = false
  }
}

const canOperate = computed(() => {
  return !!task.value && !['CANCELLED', 'FAILED', 'APPLIED'].includes(task.value.status)
})

onMounted(fetchData)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-page-header content="修复任务详情" @back="router.push('/v5/repair-tasks')" />

    <el-card v-if="task" shadow="never" class="main-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="title">{{ task.title || task.taskCode }}</div>
            <div class="sub">任务编码：{{ task.taskCode }} · 学期：{{ task.semesterId }}</div>
          </div>
          <el-tag :type="statusTagType(task.status)">{{ statusText(task.status) }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务类型">{{ task.taskType }}</el-descriptions-item>
        <el-descriptions-item label="触发来源">{{ task.triggerSource }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ task.status }}</el-descriptions-item>
        <el-descriptions-item label="关联方案">{{ task.planId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源方案">{{ task.sourcePlanId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源课表">{{ task.sourceScheduleId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="试算结果方案">{{ task.resultPlanId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ task.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ task.updatedAt }}</el-descriptions-item>
        <el-descriptions-item label="关联风险类型" :span="3">{{ task.riskTypes.join(', ') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联风险项ID" :span="3">{{ task.riskItemIds.join(', ') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="修复范围项ID" :span="3">{{ task.scopePlanItemIds.join(', ') || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="12" class="stats">
        <el-col :span="4"><el-statistic title="范围课程数" :value="task.targetItemCount" /></el-col>
        <el-col :span="4"><el-statistic title="锁定课程数" :value="task.lockedItemCount" /></el-col>
        <el-col :span="4"><el-statistic title="已处理" :value="task.processedItemCount" /></el-col>
        <el-col :span="4"><el-statistic title="成功" :value="task.successItemCount" /></el-col>
        <el-col :span="4"><el-statistic title="失败" :value="task.failureItemCount" /></el-col>
      </el-row>

      <div class="actions" v-if="canOperate">
        <el-button :loading="updating" @click="changeStatus('ANALYZING')">标记分析中</el-button>
        <el-button :loading="updating" @click="changeStatus('SUGGESTED')">标记已建议</el-button>
        <el-button :loading="updating" @click="changeStatus('SIMULATED')">标记已试算</el-button>
        <el-button type="success" :loading="updating" @click="changeStatus('APPLIED')">标记已应用</el-button>
        <el-button type="danger" :loading="updating" @click="cancelTask">取消任务</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.main-card { border-radius: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.title { font-size: 22px; font-weight: 700; color: #243447; }
.sub { margin-top: 6px; color: #667085; font-size: 13px; }
.stats { margin-top: 16px; }
.actions { margin-top: 16px; display: flex; gap: 10px; flex-wrap: wrap; }
</style>

