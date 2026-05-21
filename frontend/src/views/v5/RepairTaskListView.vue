<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCurrentSemester } from '../../api/semester'
import {
  cancelRepairTask,
  createRepairTask,
  listRepairTasks,
  type V5RepairTaskCreatePayload,
  type V5RepairTaskStatus,
  type V5RepairTaskSummary,
} from '../../api/v5RepairTaskApi'
import { extractMessage } from '../../utils/errors'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const currentSemesterId = ref<number | null>(null)
const tasks = ref<V5RepairTaskSummary[]>([])
const statusFilter = ref<V5RepairTaskStatus | ''>('')
const createDialogVisible = ref(false)
const form = ref({
  taskType: 'SMART_FIX',
  title: '',
  planId: undefined as number | undefined,
})

const statusOptions: Array<{ label: string; value: V5RepairTaskStatus }> = [
  { label: '已创建', value: 'CREATED' },
  { label: '分析中', value: 'ANALYZING' },
  { label: '已生成建议', value: 'SUGGESTED' },
  { label: '已试算', value: 'SIMULATED' },
  { label: '已应用', value: 'APPLIED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '失败', value: 'FAILED' },
]

const filteredTasks = computed(() => {
  if (!statusFilter.value) return tasks.value
  return tasks.value.filter((t) => t.status === statusFilter.value)
})

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
    const semester = await getCurrentSemester()
    currentSemesterId.value = semester.id
    tasks.value = await listRepairTasks({ semesterId: semester.id })
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载修复任务失败'))
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!currentSemesterId.value) {
    ElMessage.warning('当前学期不存在，无法创建修复任务')
    return
  }
  creating.value = true
  try {
    const payload: V5RepairTaskCreatePayload = {
      semesterId: currentSemesterId.value,
      taskType: form.value.taskType,
      title: form.value.title || undefined,
      planId: form.value.planId,
      triggerSource: 'MANUAL',
    }
    const created = await createRepairTask(payload)
    ElMessage.success('修复任务已创建')
    createDialogVisible.value = false
    await fetchData()
    router.push(`/v5/repair-tasks/${created.id}`)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '创建失败'))
  } finally {
    creating.value = false
  }
}

async function handleCancel(row: V5RepairTaskSummary) {
  let reason: string | undefined
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因（可选）', '取消修复任务', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '如：范围变更，重新建任务',
      inputValue: '',
    })
    reason = value || undefined
  } catch {
    return
  }
  try {
    await cancelRepairTask(row.id, reason)
    ElMessage.success('任务已取消')
    await fetchData()
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '取消失败'))
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header-row">
          <div>
            <div class="title">V5 修复任务管理</div>
            <div class="sub">学期 {{ currentSemesterId ?? '—' }} · 任务总数 {{ tasks.length }}</div>
          </div>
          <div class="actions">
            <el-select v-model="statusFilter" clearable placeholder="按状态筛选" style="width: 180px">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button @click="fetchData">刷新</el-button>
            <el-button type="primary" @click="createDialogVisible = true">新建修复任务</el-button>
          </div>
        </div>
      </template>

      <el-table :data="filteredTasks" stripe>
        <el-table-column prop="taskCode" label="任务编码" min-width="170" />
        <el-table-column prop="title" label="任务标题" min-width="220" />
        <el-table-column prop="taskType" label="任务类型" width="130" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="planId" label="方案ID" width="90" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/v5/repair-tasks/${row.id}`)">详情</el-button>
            <el-button
              v-if="row.status !== 'CANCELLED' && row.status !== 'FAILED' && row.status !== 'APPLIED'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建修复任务" width="560px">
      <el-form label-width="110px">
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType">
            <el-option label="智能修复" value="SMART_FIX" />
            <el-option label="局部重排" value="LOCAL_REPLAN" />
            <el-option label="风险修复" value="RISK_REPAIR" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务标题">
          <el-input v-model="form.title" placeholder="例如：修复教师冲突与未排任务" />
        </el-form-item>
        <el-form-item label="方案ID">
          <el-input-number v-model="form.planId" :min="1" :controls="false" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.title { font-size: 20px; font-weight: 700; color: #213547; }
.sub { margin-top: 4px; color: #667085; font-size: 13px; }
.actions { display: flex; gap: 10px; align-items: center; }
</style>
