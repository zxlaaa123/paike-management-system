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
import {
  chooseSuggestionForSimulation,
  generateRepairSuggestions,
  getRepairSuggestionDetail,
  listRepairSuggestions,
  type V5RepairSuggestion,
} from '../../api/v5RepairSuggestionApi'
import { extractMessage } from '../../utils/errors'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.taskId))
const loading = ref(false)
const updating = ref(false)
const generatingSuggestions = ref(false)
const task = ref<V5RepairTaskDetail | null>(null)
const suggestions = ref<V5RepairSuggestion[]>([])
const selectedSuggestions = ref<V5RepairSuggestion[]>([])
const detailVisible = ref(false)
const currentSuggestion = ref<V5RepairSuggestion | null>(null)

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
    suggestions.value = await listRepairSuggestions(taskId.value)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载任务详情失败'))
  } finally {
    loading.value = false
  }
}

async function generateSuggestions() {
  generatingSuggestions.value = true
  try {
    suggestions.value = await generateRepairSuggestions(taskId.value, { includeUnavailable: true, candidateLimit: 24 })
    ElMessage.success('修复建议已生成')
    if (task.value && task.value.status !== 'SUGGESTED') {
      task.value = await getRepairTaskDetail(taskId.value)
    }
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '生成修复建议失败'))
  } finally {
    generatingSuggestions.value = false
  }
}

async function openSuggestionDetail(row: V5RepairSuggestion) {
  try {
    currentSuggestion.value = await getRepairSuggestionDetail(taskId.value, row.id)
    detailVisible.value = true
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载建议详情失败'))
  }
}

async function chooseForSimulation(row: V5RepairSuggestion) {
  try {
    const simulation = await chooseSuggestionForSimulation(taskId.value, row.id)
    ElMessage.success('试算方案已生成')
    router.push(`/v5/repair-tasks/${taskId.value}/simulations/${simulation.plan.id}`)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '生成试算方案失败'))
  }
}

function suggestionTypeText(type: string) {
  const map: Record<string, string> = {
    KEEP_TIME_CHANGE_ROOM: '保时换教室',
    KEEP_ROOM_CHANGE_TIME: '保教室换时间',
    CHANGE_TIME_AND_ROOM: '换时间和教室',
    MANUAL_REVIEW: '人工处理',
    PARTIAL_RESCHEDULE: '局部重排',
  }
  return map[type] || type
}

function levelTagType(level: string) {
  if (level === 'HIGH') return 'success'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'info'
  return 'danger'
}

async function changeStatus(status: V5RepairTaskStatus) {
  if (!task.value) return
  updating.value = true
  try {
    task.value = await updateRepairTaskStatus(task.value.id, { status })
    ElMessage.success('状态已更新')
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '状态更新失败'))
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
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '取消失败'))
  } finally {
    updating.value = false
  }
}

const canOperate = computed(() => {
  return !!task.value && !['CANCELLED', 'FAILED', 'APPLIED'].includes(task.value.status)
})

function openCandidates() {
  if (!task.value) return
  const planItemId = task.value.scopePlanItemIds?.[0]
  const scheduleId = task.value.sourceScheduleId ?? undefined
  if (!planItemId && !scheduleId) {
    ElMessage.warning('当前任务缺少课程定位信息，无法查看候选位置')
    return
  }
  const query = new URLSearchParams()
  if (planItemId) query.set('planItemId', String(planItemId))
  if (scheduleId) query.set('scheduleId', String(scheduleId))
  router.push(`/v5/candidate-positions?${query.toString()}`)
}

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
        <el-button type="info" :loading="updating" @click="openCandidates">查看候选位置</el-button>
        <el-button type="primary" :loading="generatingSuggestions" @click="generateSuggestions">生成修复建议</el-button>
        <el-button :loading="updating" @click="changeStatus('ANALYZING')">标记分析中</el-button>
        <el-button :loading="updating" @click="changeStatus('SUGGESTED')">标记已建议</el-button>
        <el-button :loading="updating" @click="changeStatus('SIMULATED')">标记已试算</el-button>
        <el-button type="success" :loading="updating" @click="changeStatus('APPLIED')">标记已应用</el-button>
        <el-button type="danger" :loading="updating" @click="cancelTask">取消任务</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="main-card">
      <template #header>
        <div class="header-row">
          <div class="title">修复建议列表</div>
          <div class="sub">支持多选对比与生成试算方案</div>
        </div>
      </template>
      <el-table :data="suggestions" stripe border @selection-change="(rows:any)=>selectedSuggestions=rows">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="suggestionCode" label="建议编码" width="150" />
        <el-table-column label="建议类型" width="170">
          <template #default="{ row }">{{ suggestionTypeText(row.suggestionType) }}</template>
        </el-table-column>
        <el-table-column label="推荐等级" width="120">
          <template #default="{ row }"><el-tag :type="levelTagType(row.recommendationLevel)">{{ row.recommendationLevel }}</el-tag></template>
        </el-table-column>
        <el-table-column label="原安排" min-width="180">
          <template #default="{ row }">周{{ row.sourceWeekday ?? '-' }} {{ row.sourceStartPeriod ?? '-' }}-{{ row.sourceEndPeriod ?? '-' }} {{ row.sourceClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="建议安排" min-width="180">
          <template #default="{ row }">周{{ row.targetWeekday ?? '-' }} {{ row.targetStartPeriod ?? '-' }}-{{ row.targetEndPeriod ?? '-' }} {{ row.targetClassroomName || '-' }}</template>
        </el-table-column>
        <el-table-column label="解决原风险" width="110">
          <template #default="{ row }"><el-tag :type="row.resolvesOriginalRisk ? 'success' : 'info'">{{ row.resolvesOriginalRisk ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="新风险" width="100">
          <template #default="{ row }"><el-tag :type="row.introducesNewRisk ? 'warning' : 'success'">{{ row.introducesNewRisk ? '有' : '无' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="expectedScoreDelta" label="评分变化" width="110" />
        <el-table-column prop="reasonSummary" label="建议说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSuggestionDetail(row)">详情</el-button>
            <el-button link type="success" @click="chooseForSimulation(row)">生成试算方案</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert
        v-if="selectedSuggestions.length > 1"
        type="info"
        show-icon
        :closable="false"
        :title="`已选择 ${selectedSuggestions.length} 条建议，可横向对比原安排/新安排/评分变化后再选择试算`"
        style="margin-top:12px"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="建议详情" size="520px">
      <el-descriptions v-if="currentSuggestion" :column="1" border>
        <el-descriptions-item label="建议编码">{{ currentSuggestion.suggestionCode }}</el-descriptions-item>
        <el-descriptions-item label="建议类型">{{ suggestionTypeText(currentSuggestion.suggestionType) }}</el-descriptions-item>
        <el-descriptions-item label="推荐等级">{{ currentSuggestion.recommendationLevel }}</el-descriptions-item>
        <el-descriptions-item label="关联风险">{{ currentSuggestion.riskType || '-' }} / {{ currentSuggestion.riskItemId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原课程安排">
          周{{ currentSuggestion.sourceWeekday ?? '-' }} {{ currentSuggestion.sourceStartPeriod ?? '-' }}-{{ currentSuggestion.sourceEndPeriod ?? '-' }} {{ currentSuggestion.sourceClassroomName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="建议后安排">
          周{{ currentSuggestion.targetWeekday ?? '-' }} {{ currentSuggestion.targetStartPeriod ?? '-' }}-{{ currentSuggestion.targetEndPeriod ?? '-' }} {{ currentSuggestion.targetClassroomName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="影响对象">{{ (currentSuggestion.affectedItems || []).join(', ') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评分变化预估">{{ currentSuggestion.expectedScoreDelta ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="建议说明">{{ currentSuggestion.description || currentSuggestion.reasonSummary }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
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
